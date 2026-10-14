package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.DriveFileDao
import com.example.data.local.dao.UploadRecordDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.DriveFileEntity
import com.example.data.local.entity.UploadRecordEntity

@Database(
    entities = [
        AccountEntity::class,
        DriveFileEntity::class,
        UploadRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun driveFileDao(): DriveFileDao
    abstract fun uploadRecordDao(): UploadRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "unified_cloud.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
