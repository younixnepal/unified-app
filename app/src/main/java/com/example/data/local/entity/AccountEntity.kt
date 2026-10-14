package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String, // Google user id or normalized email
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenExpiresAt: Long = 0L,
    val totalStorageLimit: Long? = null, // null means unlimited
    val usedStorage: Long = 0L,
    val usageInDrive: Long = 0L,
    val usageInDriveTrash: Long = 0L,
    val isPrimary: Boolean = false,
    val status: String = "CONNECTED", // CONNECTED, TOKEN_EXPIRED, ERROR, RATE_LIMITED
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val startPageToken: String? = null
)
