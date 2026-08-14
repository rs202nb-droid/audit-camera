package com.auditcamera.app

import android.app.Application
import com.auditcamera.app.sync.SyncScheduler

class AuditCameraApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 取りこぼし防止の定期送信を登録(既に登録済みなら何もしない)
        SyncScheduler.schedulePeriodicUpload(this)

        // 前回終了時に送り残しがあれば、起動のタイミングでも送信を試みる
        SyncScheduler.enqueueUpload(this, delayMillis = 0)

        // ギャラリーのバックアップ写真の後片付け(1日1回)
        SyncScheduler.scheduleDailyCleanup(this)
    }
}
