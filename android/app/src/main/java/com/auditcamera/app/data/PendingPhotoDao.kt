package com.auditcamera.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 写真キューへの読み書きをまとめた窓口 */
@Dao
interface PendingPhotoDao {

    @Insert
    suspend fun insert(photo: PendingPhoto)

    /** 未送信の件数。画面のバッジ表示に使う(値が変わると自動で通知される) */
    @Query("SELECT COUNT(*) FROM pending_photos WHERE syncedAtMillis IS NULL")
    fun observePendingCount(): Flow<Int>

    /** 見返し画面用。新しい順に全件 */
    @Query("SELECT * FROM pending_photos ORDER BY capturedAtMillis DESC")
    fun observeAll(): Flow<List<PendingPhoto>>

    /** 送信対象。古い順に1件ずつ処理する(Web版のgetOldestPhotoと同じ考え方) */
    @Query("SELECT * FROM pending_photos WHERE syncedAtMillis IS NULL ORDER BY capturedAtMillis ASC LIMIT 1")
    suspend fun getOldestPending(): PendingPhoto?

    /**
     * 撮影から一定時間が経った未送信の写真(古い順に1件)。
     * 撮った直後は見返し画面で消せるよう、少し待ってから送信するために使う。
     */
    @Query(
        "SELECT * FROM pending_photos WHERE syncedAtMillis IS NULL AND capturedAtMillis <= :capturedBefore " +
            "ORDER BY capturedAtMillis ASC LIMIT 1"
    )
    suspend fun getOldestPendingCapturedBefore(capturedBefore: Long): PendingPhoto?

    @Query("SELECT COUNT(*) FROM pending_photos WHERE syncedAtMillis IS NULL")
    suspend fun countPending(): Int

    @Query("SELECT * FROM pending_photos WHERE photoId = :photoId")
    suspend fun getById(photoId: String): PendingPhoto?

    @Query("UPDATE pending_photos SET syncedAtMillis = :syncedAt WHERE photoId = :photoId")
    suspend fun markSynced(photoId: String, syncedAt: Long)

    @Query("UPDATE pending_photos SET backupMediaUri = :uri WHERE photoId = :photoId")
    suspend fun setBackupUri(photoId: String, uri: String?)

    @Query("DELETE FROM pending_photos WHERE photoId = :photoId")
    suspend fun deleteById(photoId: String)

    /**
     * 送信済みで、かつ送信から指定時刻より前に経過したもの。
     * ギャラリーのバックアップを消す30日クリーンアップで使う。
     */
    @Query("SELECT * FROM pending_photos WHERE syncedAtMillis IS NOT NULL AND syncedAtMillis < :cutoffMillis")
    suspend fun getPurgeCandidates(cutoffMillis: Long): List<PendingPhoto>
}
