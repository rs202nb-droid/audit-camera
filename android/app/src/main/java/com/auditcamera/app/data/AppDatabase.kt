package com.auditcamera.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PendingPhoto::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingPhotoDao(): PendingPhotoDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** アプリ全体で1つだけデータベースを開いて使い回す */
        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "audit_camera.db"
                ).build().also { instance = it }
            }
        }
    }
}
