package com.auditcamera.app.data

import android.content.Context

/**
 * 店舗名など、次回起動時にも覚えておきたい小さな設定の保存先。
 * Web版の localStorage('saved_store_name') と同じ役割。
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("audit_camera_prefs", Context.MODE_PRIVATE)

    var storeName: String
        get() = prefs.getString(KEY_STORE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STORE_NAME, value).apply()

    /** シャッター音を鳴らすか。静かな店内などで切れるようにしている */
    var shutterSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHUTTER_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SHUTTER_SOUND, value).apply()

    companion object {
        private const val KEY_STORE_NAME = "saved_store_name"
        private const val KEY_SHUTTER_SOUND = "shutter_sound_enabled"
    }
}
