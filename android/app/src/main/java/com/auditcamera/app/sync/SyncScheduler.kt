package com.auditcamera.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 送信ジョブの予約をまとめた場所。
 *
 * ここで予約した仕事はAndroid(OS)が覚えていてくれるので、
 * アプリを閉じても、電波が戻った時点で自動的に送信が走る。
 * これがWeb版(タブを開いていないと再開しない)との一番大きな違い。
 */
object SyncScheduler {

    const val UPLOAD_WORK_NAME = "upload_queue_drain"
    private const val PERIODIC_UPLOAD_WORK_NAME = "upload_queue_periodic"
    private const val CLEANUP_WORK_NAME = "backup_cleanup_daily"

    /** 撮影から送信を始めるまでの猶予。この間なら見返し画面で消せばクラウドに上がらない */
    val GRACE_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(60)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * 送信を1回予約する。
     * オフラインならOSが保持し、電波が戻った瞬間に自動で実行される。
     */
    fun enqueueUpload(
        context: Context,
        delayMillis: Long = GRACE_PERIOD_MILLIS,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
    ) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(networkConstraints)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            // 失敗時のリトライ間隔。Web版の「30秒後に再試行」と同じ考え方
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UPLOAD_WORK_NAME, policy, request)
    }

    /**
     * 取りこぼし防止の定期実行(15分ごと。WorkManagerの最短間隔)。
     * アプリ起動時に1回登録しておけば、以後はOSが勝手に回してくれる。
     */
    fun schedulePeriodicUpload(context: Context) {
        val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_UPLOAD_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** ギャラリーのバックアップ写真の後片付け(1日1回)。通信は不要なので条件なし */
    fun scheduleDailyCleanup(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupCleanupWorker>(1, TimeUnit.DAYS).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CLEANUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
