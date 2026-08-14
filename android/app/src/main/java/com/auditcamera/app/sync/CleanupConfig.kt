package com.auditcamera.app.sync

import java.util.concurrent.TimeUnit

/** ギャラリーのバックアップ写真をどれだけ残すかの設定 */
object CleanupConfig {

    /**
     * 保持期間(送信成功から30日)。
     *
     * ※動作確認のときだけ、ここを一時的に短い値(例: TimeUnit.MINUTES.toMillis(2))に
     * 変えてビルドすると、30日待たずに削除の挙動を確認できる。
     */
    fun retentionMillis(): Long = TimeUnit.DAYS.toMillis(30)
}
