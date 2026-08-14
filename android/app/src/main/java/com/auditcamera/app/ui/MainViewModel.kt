package com.auditcamera.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auditcamera.app.data.AppDatabase
import com.auditcamera.app.data.AppPrefs
import com.auditcamera.app.data.PendingPhoto
import com.auditcamera.app.data.PhotoRepository
import com.auditcamera.app.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** 表示中の画面 */
enum class Screen { SETUP, CAMERA, REVIEW }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPrefs(app)
    private val repo = PhotoRepository(app)
    private val dao = AppDatabase.get(app).pendingPhotoDao()

    private val _screen = MutableStateFlow(Screen.SETUP)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** 店舗名。前回の値を初期表示する */
    private val _storeName = MutableStateFlow(prefs.storeName)
    val storeName: StateFlow<String> = _storeName.asStateFlow()

    private val _selectedCategory = MutableStateFlow(CATEGORIES.first())
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _shutterSoundEnabled = MutableStateFlow(prefs.shutterSoundEnabled)
    val shutterSoundEnabled: StateFlow<Boolean> = _shutterSoundEnabled.asStateFlow()

    fun setShutterSoundEnabled(enabled: Boolean) {
        _shutterSoundEnabled.value = enabled
        prefs.shutterSoundEnabled = enabled
    }

    /** 未送信件数(画面のバッジ用)。データベースの変化に自動で追従する */
    val pendingCount: StateFlow<Int> = dao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 見返し画面用の一覧 */
    val allPhotos: StateFlow<List<PendingPhoto>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateStoreName(name: String) {
        _storeName.value = name
        prefs.storeName = name
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun goTo(screen: Screen) {
        _screen.value = screen
    }

    /** 撮影前に発行するID。再送しても変わらないよう、この1回だけ作る */
    fun newPhotoId(): String = UUID.randomUUID().toString()

    fun pendingFileFor(photoId: String): File = repo.pendingFileFor(photoId)

    /**
     * 撮影が完了したときの記録処理。
     * 判定記号と項目はシャッターを押した瞬間の値を引数で受け取る
     * (連写したときに後から押した値が混ざらないようにするため)。
     */
    fun onPhotoCaptured(
        photoId: String,
        category: String,
        judgement: String,
        file: File,
        capturedAtMillis: Long
    ) {
        viewModelScope.launch {
            repo.registerCapturedPhoto(
                photoId = photoId,
                storeName = _storeName.value.trim(),
                category = category,
                judgement = judgement,
                file = file,
                capturedAtMillis = capturedAtMillis
            )
            // 送信を予約する。オフラインならOSが保持し、電波が戻った時点で自動実行される
            SyncScheduler.enqueueUpload(getApplication())
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch { repo.deletePhoto(photoId) }
    }

    companion object {
        /** 監査項目。Web版(index.html)のボタンと同じ並び・同じ名前 */
        val CATEGORIES = listOf(
            "労務管理", "安全衛生", "基本項目", "事務金銭", "組織開発", "クレンリネス"
        )
    }
}
