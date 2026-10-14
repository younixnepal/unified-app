package com.example.data.remote.dto

data class DriveAboutResponse(
    val user: DriveUserDto? = null,
    val storageQuota: StorageQuotaDto? = null
)

data class DriveUserDto(
    val displayName: String? = null,
    val emailAddress: String? = null,
    val photoLink: String? = null,
    val permissionId: String? = null
)

data class StorageQuotaDto(
    val limit: String? = null,
    val usage: String? = null,
    val usageInDrive: String? = null,
    val usageInDriveTrash: String? = null
) {
    fun getLimitBytes(): Long? = limit?.toLongOrNull()
    fun getUsageBytes(): Long = usage?.toLongOrNull() ?: 0L
    fun getDriveUsageBytes(): Long = usageInDrive?.toLongOrNull() ?: 0L
    fun getTrashUsageBytes(): Long = usageInDriveTrash?.toLongOrNull() ?: 0L
}

data class DriveFileListResponse(
    val files: List<DriveFileDto>? = null,
    val nextPageToken: String? = null
)

data class DriveFileDto(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: String? = null,
    val modifiedTime: String? = null,
    val webViewLink: String? = null,
    val iconLink: String? = null,
    val thumbnailLink: String? = null
) {
    fun getSizeBytes(): Long = size?.toLongOrNull() ?: 0L
}

data class OAuthTokenResponse(
    val access_token: String,
    val expires_in: Long? = 3600L,
    val refresh_token: String? = null,
    val scope: String? = null,
    val token_type: String? = null,
    val error: String? = null,
    val error_description: String? = null
)

data class DriveStartPageTokenResponse(
    val startPageToken: String? = null
)

data class DriveChangesResponse(
    val changes: List<DriveChangeDto>? = null,
    val nextPageToken: String? = null,
    val newStartPageToken: String? = null
)

data class DriveChangeDto(
    val fileId: String,
    val removed: Boolean? = false,
    val file: DriveFileChangeDto? = null
)

data class DriveFileChangeDto(
    val id: String? = null,
    val name: String? = null,
    val mimeType: String? = null,
    val size: String? = null,
    val modifiedTime: String? = null,
    val trashed: Boolean? = false,
    val webViewLink: String? = null,
    val iconLink: String? = null,
    val thumbnailLink: String? = null
) {
    fun getSizeBytes(): Long = size?.toLongOrNull() ?: 0L
}

