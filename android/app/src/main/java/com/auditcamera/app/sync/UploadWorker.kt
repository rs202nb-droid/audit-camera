package com.auditcamera.app.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.auditcamera.app.BuildConfig
import com.auditcamera.app.data.AppDatabase
import com.auditcamera.app.data.PendingPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 溜まっている写真をGASへ送るバックグラウンド処理。
 *
 * Web版の autoSync() と同じ流れ(古い順に1件ずつ送り、成功の返事を確認してから
 * キューから消す)を、OSが管理する仕事として実行する。
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val dao = AppDatabase.get(context).pendingPhotoDao()

    private val client = OkHttpClient.Builder()
        // 接続できない(電波なし)ときは早めに諦める
        .connectTimeout(20, TimeUnit.SECONDS)
        // 原寸の写真(数MB)をGASが保存し終えるまで待つ。以前は全体20秒で打ち切っていたが、
        // GASが遅い日は毎回途中で切られて1時間に数枚しか送れなくなった(2026-10-27)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 撮影時の送信ジョブと15分ごとの定期ジョブが同時に走ると、GASへ同時にリクエストが
        // 飛んでドライブの日付フォルダが二重に作られてしまう。
        // ここで鍵をかけて、送信は必ず一度に1つだけ動くようにする。
        uploadMutex.withLock {
            drainQueue()
        }
    }

    private suspend fun drainQueue(): Result {
        val now = System.currentTimeMillis()
        // 撮影直後の写真は見返し画面で消せるよう、猶予が過ぎたものだけを対象にする
        val cutoff = now - SyncScheduler.GRACE_PERIOD_MILLIS

        // 今回の実行で失敗した写真。先頭の1枚が失敗し続けても、後ろの写真を送れるよう飛ばす
        val failedIds = mutableListOf<String>()
        var consecutiveFailures = 0

        while (true) {
            val photo = dao.getOldestPendingCapturedBeforeExcluding(cutoff, failedIds) ?: break

            val file = File(photo.localFilePath)
            if (!file.exists()) {
                // 画像ファイルが見当たらない(見返し画面で削除された等)。記録も片付けて次へ
                Log.w(TAG, "画像ファイルが無いのでキューから除外: ${photo.photoId}")
                dao.deleteById(photo.photoId)
                continue
            }

            val success = runCatching { upload(photo, file) }.getOrElse {
                Log.w(TAG, "送信に失敗(リトライします): ${it.message}")
                false
            }

            if (!success) {
                // 写真は消さずに残す(証拠写真の消失防止)。この写真は飛ばして次へ進む。
                // ただし3連続で失敗したら通信自体がダメとみなし、今回は打ち切る
                failedIds.add(photo.photoId)
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) break
                continue
            }
            consecutiveFailures = 0

            dao.markSynced(photo.photoId, System.currentTimeMillis())
            // 送信できたのでアプリ内の作業用ファイルは削除。
            // ギャラリー側のバックアップは30日間そのまま残す(万一のための保険)
            runCatching { file.delete() }
        }

        // 失敗があった場合は、30秒後の再送を自分で予約する。
        // Result.retry() だと失敗のたびに待ち時間が延びていき(最大5時間)、
        // 原因が直っても再開が遅れるため使わない
        if (failedIds.isNotEmpty()) {
            SyncScheduler.enqueueUpload(
                applicationContext,
                delayMillis = RETRY_DELAY_MILLIS,
                policy = ExistingWorkPolicy.REPLACE
            )
            return Result.success()
        }

        // まだ猶予中の写真が残っていれば、あらためて送信を予約しておく
        if (dao.countPending() > 0) {
            SyncScheduler.enqueueUpload(
                applicationContext,
                policy = ExistingWorkPolicy.REPLACE
            )
        }

        return Result.success()
    }

    /** GASへ1件POSTし、success が返ってきたかどうかを返す */
    private fun upload(photo: PendingPhoto, file: File): Boolean {
        val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)

        // Web版の canvas.toDataURL('image/jpeg') と同じ形式にそろえる
        val imageField = "data:image/jpeg;base64,$base64"

        val payload = JSONObject().apply {
            put("photoId", photo.photoId)
            put("storeName", photo.storeName)
            put("category", photo.category)
            put("judgement", photo.judgement)
            put("image", imageField)
            put("timestamp", photo.capturedAtMillis)
            put("token", BuildConfig.AUTH_TOKEN)
        }

        val request = Request.Builder()
            .url(BuildConfig.GAS_URL)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTPエラー: ${response.code}")
                return false
            }
            val body = response.body?.string().orEmpty()
            val result = runCatching { JSONObject(body).optString("result") }.getOrNull()
            if (result != "success") {
                Log.w(TAG, "GASが成功を返さなかった: $body")
                return false
            }
            return true
        }
    }

    companion object {
        private const val TAG = "UploadWorker"

        /** 連続でこの回数失敗したら、今回の実行は打ち切る */
        private const val MAX_CONSECUTIVE_FAILURES = 3

        /** 失敗後に再送するまでの待ち時間(Web版の「30秒後に再試行」と同じ) */
        private val RETRY_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(30)

        /** 送信処理を同時に1つだけに制限するための鍵(アプリ全体で共有) */
        private val uploadMutex = Mutex()
    }
}
