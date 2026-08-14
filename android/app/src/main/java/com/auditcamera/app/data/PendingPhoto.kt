package com.auditcamera.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 撮影した写真1枚分の記録。
 *
 * Web版のIndexedDBキューに相当する。撮影した瞬間にここへ書き込むので、
 * 電波が無くても写真の情報は必ず端末に残る。
 */
@Entity(tableName = "pending_photos")
data class PendingPhoto(
    /**
     * 写真ごとに1回だけ発行する一意のID。
     * 再送のたびに作り直さず同じIDを送ることで、GAS側で二重保存を防げる。
     */
    @PrimaryKey val photoId: String,

    val storeName: String,
    val category: String,

    /** 判定記号。"〇" / "×" / "▲" のいずれか */
    val judgement: String,

    /** アプリ内部に保存したJPEGファイルのパス */
    val localFilePath: String,

    /** ギャラリー(監査カメラアルバム)にコピーした写真のURI。失敗した場合はnull */
    val backupMediaUri: String?,

    /** 撮影時刻(ミリ秒) */
    val capturedAtMillis: Long,

    /** GASが保存成功を返した時刻。未送信の間はnull */
    val syncedAtMillis: Long? = null
)
