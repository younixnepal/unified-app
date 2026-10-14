package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DriveFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveFileDao {
    @Query("SELECT * FROM drive_files ORDER BY modifiedTime DESC")
    fun getAllFilesFlow(): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE accountId = :accountId ORDER BY modifiedTime DESC")
    fun getFilesByAccountFlow(accountId: String): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE name LIKE '%' || :query || '%' ORDER BY modifiedTime DESC")
    fun searchFilesFlow(query: String): Flow<List<DriveFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<DriveFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: DriveFileEntity)

    @Query("DELETE FROM drive_files WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    @Query("DELETE FROM drive_files WHERE id = :fileId")
    suspend fun deleteById(fileId: String)

    @Query("DELETE FROM drive_files")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM drive_files")
    suspend fun getFileCount(): Int

    @Query("DELETE FROM drive_files WHERE accountId LIKE 'demo_%' OR id LIKE 'doc_%' OR id LIKE 'sheet_%' OR id LIKE 'slides_%' OR id LIKE 'photo_%' OR id LIKE 'archive_%'")
    suspend fun deleteDemoFiles()
}
