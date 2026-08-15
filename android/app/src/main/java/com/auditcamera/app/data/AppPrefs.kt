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

    /**
     * 監査項目の名前(6つ)。設定画面から変更できる。
     * 保存は改行区切り。項目名に改行は入れられないので区切り文字として安全。
     */
    var categories: List<String>
        get() {
            val saved = prefs.getString(KEY_CATEGORIES, null) ?: return DEFAULT_CATEGORIES
            val list = saved.split("\n")
            // 保存内容が壊れていた場合は既定値に戻す
            return if (list.size == DEFAULT_CATEGORIES.size && list.none { it.isBlank() }) {
                list
            } else {
                DEFAULT_CATEGORIES
            }
        }
        set(value) = prefs.edit().putString(KEY_CATEGORIES, value.joinToString("\n")).apply()

    companion object {
        private const val KEY_STORE_NAME = "saved_store_name"
        private const val KEY_SHUTTER_SOUND = "shutter_sound_enabled"
        private const val KEY_CATEGORIES = "categories"

        /** 既定の監査項目。Web版(index.html)のボタンと同じ並び・同じ名前 */
        val DEFAULT_CATEGORIES = listOf(
            "労務管理", "安全衛生", "基本項目", "事務金銭", "組織開発", "クレンリネス"
        )
    }
}
