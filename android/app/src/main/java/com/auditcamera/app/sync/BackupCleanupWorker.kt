package com.auditcamera.app.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.auditcamera.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ギャラリーに残してあるバックアップ写真の後片付け。
 *
 * 送信が成功してから一定期間(既定30日)経った写真を、ギャラリーから削除する。
 * 「万一クラウド側で問題があっても、しばらくは端末に実物が残っている」という
 * 保険をかけつつ、際限なくストレージを食わないようにするための仕組み。
 */
class BackupCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val dao = AppDatabase.get(context).pendingPhotoDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - CleanupConfig.retentionMillis()
        val targets = dao.getPurgeCandidates(cutoff)

        for (photo in targets) {
            // 自分で作成した写真なので、追加のストレージ権限なしで削除できる
            photo.backupMediaUri?.let { uriString ->
                runCatching {
                    applicationContext.contentResolver.delete(Uri.parse(uriString), null, null)
                }.onFailure {
                    Log.w(TAG, "ギャラリーの写真削除に失敗: ${it.message}")
                }
            }
            // アプリ内の作業用ファイルが残っていれば併せて削除
            runCatching { File(photo.localFilePath).delete() }
            dao.deleteById(photo.photoId)
        }

        if (targets.isNotEmpty()) {
            Log.i(TAG, "バックアップ写真を${targets.size}件片付けました")
        }
        Result.success()
    }

    companion object {
        private const val TAG = "BackupCleanupWorker"
    }
}
