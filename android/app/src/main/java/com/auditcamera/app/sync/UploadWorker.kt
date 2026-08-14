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
        // 電波が弱いと通信が固まったままになるため、20秒で打ち切ってリトライに回す
        .callTimeout(20, TimeUnit.SECONDS)
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

        while (true) {
            val photo = dao.getOldestPendingCapturedBefore(cutoff) ?: break

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
                // 写真は消さずに残したままリトライへ回す(証拠写真の消失防止)
                return Result.retry()
            }

            dao.markSynced(photo.photoId, System.currentTimeMillis())
            // 送信できたのでアプリ内の作業用ファイルは削除。
            // ギャラリー側のバックアップは30日間そのまま残す(万一のための保険)
            runCatching { file.delete() }
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

        /** 送信処理を同時に1つだけに制限するための鍵(アプリ全体で共有) */
        private val uploadMutex = Mutex()
    }
}
