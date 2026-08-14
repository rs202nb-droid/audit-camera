package com.auditcamera.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * 撮影したJPEGの向きを、画像そのものに反映させる。
 *
 * CameraXは「画像の中身は横向きのまま、向きの情報(EXIF)だけを付ける」形で保存する。
 * ギャラリーやドライブはその情報を見て正しく表示してくれるが、
 * ExcelへPythonで貼り付けるときに使うPillowはこの情報を無視するため、
 * そのままだと貼り付けた写真が90度回ってしまう。
 *
 * そこで撮影直後に画像を実際に回転させ、向きの情報を「回転なし」に書き換えておく。
 * こうすればどのソフトで開いても正しい向きで表示される
 * (Web版が映像を直接切り出していたときと同じ状態)。
 */
object ImageOrientationFixer {

    /** JPEGの保存品質。Web版の toDataURL('image/jpeg', 0.95) と揃えている */
    private const val JPEG_QUALITY = 95

    fun normalize(file: File) {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = matrixFor(orientation) ?: return  // 回転不要ならそのまま

            val original = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val rotated = try {
                Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            } finally {
                // 元の画像はもう不要なのでメモリを解放する
                if (!original.isRecycled) original.recycle()
            }

            file.outputStream().use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            rotated.recycle()

            // 画像自体を回したので、向きの情報は「回転なし」に戻しておく
            // (二重に回転されるのを防ぐため)
            ExifInterface(file.absolutePath).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                saveAttributes()
            }
        } catch (e: OutOfMemoryError) {
            // 高画素の写真だと回転処理でメモリが足りなくなることがある。
            // その場合は元のファイル(向き情報付き)をそのまま使う
            Log.w(TAG, "メモリ不足のため向きの補正をスキップしました")
        } catch (e: Exception) {
            Log.w(TAG, "向きの補正に失敗しました: ${e.message}")
        }
    }

    /** 向きの情報から、必要な回転・反転を組み立てる。回転不要ならnull */
    private fun matrixFor(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return null
        }
        return matrix
    }

    private const val TAG = "ImageOrientationFixer"
}
