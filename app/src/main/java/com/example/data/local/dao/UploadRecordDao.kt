package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.UploadRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadRecordDao {
    @Query("SELECT * FROM upload_records ORDER BY uploadTimestamp DESC")
    fun getAllUploadsFlow(): Flow<List<UploadRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: UploadRecordEntity): Long

    @Update
    suspend fun update(record: UploadRecordEntity)

    @Query("DELETE FROM upload_records")
    suspend fun clearHistory()
}
