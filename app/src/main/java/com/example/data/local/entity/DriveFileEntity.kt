package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "drive_files",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["name"]),
        Index(value = ["mimeType"])
    ]
)
data class DriveFileEntity(
    @PrimaryKey val id: String, // Drive file id
    val accountId: String,
    val accountEmail: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val modifiedTime: Long = System.currentTimeMillis(),
    val webViewLink: String? = null,
    val iconLink: String? = null,
    val thumbnailLink: String? = null,
    val isUploadedByApp: Boolean = false,
    val syncStatus: String = "SYNCED" // SYNCED, PENDING_UPLOAD, FAILED
)
