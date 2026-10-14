package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_records")
data class UploadRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val accountId: String,
    val accountEmail: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val uploadTimestamp: Long = System.currentTimeMillis(),
    val driveFileId: String? = null,
    val status: String = "SUCCESS", // SUCCESS, FAILED, IN_PROGRESS
    val errorMessage: String? = null
)
