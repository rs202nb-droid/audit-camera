package com.auditcamera.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 撮影した写真の保存まわりをまとめた置き場。
 *
 * 「まず端末に確実に残す」ことを最優先にしていて、通信は一切ここでは行わない。
 */
class PhotoRepository(private val context: Context) {

    private val dao = AppDatabase.get(context).pendingPhotoDao()

    /** ギャラリー内に作る専用アルバム名。普段の写真と混ざらないようにする */
    private val albumName = "監査カメラ"

    /** 撮影した写真の一時置き場(アプリ専用領域。他アプリからは見えない) */
    fun pendingDir(): File {
        val dir = File(context.filesDir, "pending_photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun pendingFileFor(photoId: String): File = File(pendingDir(), "$photoId.jpg")

    /**
     * 撮影直後の記録。
     * 1) キューに登録(ここまでで写真は確実に残る)
     * 2) ギャラリーにもバックアップをコピー(失敗しても1は取り消さない)
     */
    suspend fun registerCapturedPhoto(
        photoId: String,
        storeName: String,
        category: String,
        judgement: String,
        file: File,
        capturedAtMillis: Long
    ) = withContext(Dispatchers.IO) {
        // 画像そのものを正しい向きに直してから記録する。
        // (キューに入れる前に済ませることで、送信される画像もギャラリーのコピーも正しい向きになる)
        ImageOrientationFixer.normalize(file)

        dao.insert(
            PendingPhoto(
                photoId = photoId,
                storeName = storeName,
                category = category,
                judgement = judgement,
                localFilePath = file.absolutePath,
                backupMediaUri = null,
                capturedAtMillis = capturedAtMillis
            )
        )

        val backupUri = runCatching {
            saveToGallery(file, storeName, category, judgement, capturedAtMillis)
        }.getOrElse {
            // ギャラリー保存に失敗しても撮影自体は成立させる(送信キューには入っている)
            Log.w(TAG, "ギャラリーへのバックアップ保存に失敗: ${it.message}")
            null
        }

        if (backupUri != null) {
            dao.setBackupUri(photoId, backupUri.toString())
        }
    }

    /**
     * ギャラリーの「監査カメラ」アルバムに写真をコピーする。
     * Android 10以降は自分で作った写真なのでストレージ権限は不要。
     */
    private fun saveToGallery(
        source: File,
        storeName: String,
        category: String,
        judgement: String,
        capturedAtMillis: Long
    ): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date(capturedAtMillis))
        val displayName = "${sanitize(storeName)}_${sanitize(category)}_${judgement}_$stamp.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$albumName"
            )
            // コピーが終わるまで他アプリから見えないようにする
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: run {
            resolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** ファイル名に使えない文字を潰す */
    private fun sanitize(text: String): String =
        text.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifEmpty { "未設定" }

    /**
     * 写真を1件削除する(見返し画面から使う)。
     * 端末内のファイル・ギャラリーのバックアップ・キューの記録をまとめて消す。
     */
    suspend fun deletePhoto(photoId: String) = withContext(Dispatchers.IO) {
        val photo = dao.getById(photoId) ?: return@withContext
        runCatching { File(photo.localFilePath).delete() }
        photo.backupMediaUri?.let { uriString ->
            runCatching { context.contentResolver.delete(Uri.parse(uriString), null, null) }
        }
        dao.deleteById(photoId)
    }

    companion object {
        private const val TAG = "PhotoRepository"
    }
}
