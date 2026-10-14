package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.DriveFileEntity
import com.example.data.local.entity.UploadRecordEntity
import com.example.data.remote.DriveApiException
import com.example.data.remote.GoogleDriveApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class StorageAggregation(
    val totalLimitBytes: Long?, // null if any account has unlimited quota
    val totalUsedBytes: Long,
    val totalAvailableBytes: Long?,
    val usedPercentage: Float,
    val accountCount: Int
)

data class DeltaSyncSummary(
    val accountEmail: String,
    val createdOrUpdatedCount: Int,
    val deletedCount: Int,
    val hasChanges: Boolean,
    val errorMessage: String? = null
)

class UnifiedCloudRepository(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val apiClient: GoogleDriveApiClient = GoogleDriveApiClient()
) {
    private val accountDao = database.accountDao()
    private val driveFileDao = database.driveFileDao()
    private val uploadRecordDao = database.uploadRecordDao()

    val accountsFlow: Flow<List<AccountEntity>> = accountDao.getAllAccountsFlow()
    val allFilesFlow: Flow<List<DriveFileEntity>> = driveFileDao.getAllFilesFlow()
    val uploadRecordsFlow: Flow<List<UploadRecordEntity>> = uploadRecordDao.getAllUploadsFlow()

    fun getFilesByAccountFlow(accountId: String): Flow<List<DriveFileEntity>> =
        driveFileDao.getFilesByAccountFlow(accountId)

    fun searchFilesFlow(query: String): Flow<List<DriveFileEntity>> =
        driveFileDao.searchFilesFlow(query)

    suspend fun syncAccountDelta(account: AccountEntity): Result<DeltaSyncSummary> = withContext(Dispatchers.IO) {
        // Live Google Drive account synchronization via Changes API
        var currentToken = account.startPageToken
        if (currentToken.isNullOrBlank()) {
            val tokenResult = apiClient.getStartPageToken(account.accessToken)
            if (tokenResult.isFailure) {
                syncAccount(account)
                return@withContext Result.success(
                    DeltaSyncSummary(account.email, 0, 0, false)
                )
            }
            currentToken = tokenResult.getOrThrow()
            accountDao.updateStartPageToken(account.id, currentToken)
        }

        val changesResult = apiClient.getChanges(account.accessToken, currentToken)
        if (changesResult.isFailure) {
            val ex = changesResult.exceptionOrNull()
            if (ex is DriveApiException.TokenExpiredException) {
                accountDao.updateStatus(account.id, "TOKEN_EXPIRED")
            }
            return@withContext Result.failure(ex ?: Exception("Failed to fetch changes"))
        }

        val changesResponse = changesResult.getOrThrow()
        val changesList = changesResponse.changes ?: emptyList()
        var createdUpdated = 0
        var deleted = 0

        for (change in changesList) {
            if (change.removed == true || change.file?.trashed == true) {
                driveFileDao.deleteById(change.fileId)
                deleted++
            } else if (change.file != null) {
                val f = change.file
                val entity = DriveFileEntity(
                    id = f.id ?: change.fileId,
                    accountId = account.id,
                    accountEmail = account.email,
                    name = f.name ?: "Untitled",
                    mimeType = f.mimeType ?: "application/octet-stream",
                    sizeBytes = f.getSizeBytes(),
                    modifiedTime = parseIsoTimeToMillis(f.modifiedTime),
                    webViewLink = f.webViewLink,
                    iconLink = f.iconLink,
                    thumbnailLink = f.thumbnailLink,
                    syncStatus = "SYNCED"
                )
                driveFileDao.insertFile(entity)
                createdUpdated++
            }
        }

        val nextToken = changesResponse.newStartPageToken ?: changesResponse.nextPageToken ?: currentToken
        accountDao.updateStartPageToken(account.id, nextToken)

        // If changes detected, also refresh quota
        if (createdUpdated > 0 || deleted > 0) {
            val aboutResult = apiClient.getAbout(account.accessToken)
            if (aboutResult.isSuccess) {
                val quota = aboutResult.getOrThrow().storageQuota
                accountDao.updateQuota(
                    accountId = account.id,
                    limit = quota?.getLimitBytes(),
                    used = quota?.getUsageBytes() ?: account.usedStorage,
                    driveUsage = quota?.getDriveUsageBytes() ?: account.usageInDrive,
                    trashUsage = quota?.getTrashUsageBytes() ?: account.usageInDriveTrash
                )
            }
        }

        Result.success(
            DeltaSyncSummary(
                accountEmail = account.email,
                createdOrUpdatedCount = createdUpdated,
                deletedCount = deleted,
                hasChanges = createdUpdated > 0 || deleted > 0
            )
        )
    }

    suspend fun syncAllAccountsDelta(): List<DeltaSyncSummary> = withContext(Dispatchers.IO) {
        val accounts = accountDao.getAllAccounts()
        val summaries = mutableListOf<DeltaSyncSummary>()
        for (acc in accounts) {
            val result = syncAccountDelta(acc)
            if (result.isSuccess) {
                summaries.add(result.getOrThrow())
            } else {
                summaries.add(
                    DeltaSyncSummary(
                        accountEmail = acc.email,
                        createdOrUpdatedCount = 0,
                        deletedCount = 0,
                        hasChanges = false,
                        errorMessage = result.exceptionOrNull()?.message
                    )
                )
            }
        }
        summaries
    }

    suspend fun connectAccount(
        accessToken: String,
        refreshToken: String? = null,
        expiresInSeconds: Long = 3600L,
        clientId: String? = null,
        clientSecret: String? = null
    ): Result<AccountEntity> = withContext(Dispatchers.IO) {
        val aboutResult = apiClient.getAbout(accessToken)
        if (aboutResult.isFailure) {
            val exception = aboutResult.exceptionOrNull() ?: Exception("Failed to fetch Google Account info")
            return@withContext Result.failure(exception)
        }

        val about = aboutResult.getOrThrow()
        val user = about.user ?: return@withContext Result.failure(
            Exception("Drive API returned empty user identity. Ensure token has userinfo.email scope.")
        )
        val quota = about.storageQuota

        val email = user.emailAddress ?: "user_${System.currentTimeMillis()}@gmail.com"
        val id = user.permissionId ?: email
        val displayName = user.displayName ?: email.substringBefore("@")
        val photoUrl = user.photoLink

        val totalLimit = quota?.getLimitBytes()
        val used = quota?.getUsageBytes() ?: 0L
        val driveUsage = quota?.getDriveUsageBytes() ?: 0L
        val trashUsage = quota?.getTrashUsageBytes() ?: 0L

        val existingAccounts = accountDao.getAllAccounts()
        val isFirst = existingAccounts.isEmpty()

        val account = AccountEntity(
            id = id,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl,
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenExpiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000),
            totalStorageLimit = totalLimit,
            usedStorage = used,
            usageInDrive = driveUsage,
            usageInDriveTrash = trashUsage,
            isPrimary = isFirst,
            status = "CONNECTED",
            lastSyncedAt = System.currentTimeMillis()
        )

        accountDao.insertOrUpdate(account)

        // Sync initial files
        syncAccountFiles(account)

        Result.success(account)
    }

    suspend fun syncAccount(account: AccountEntity): Result<Unit> = withContext(Dispatchers.IO) {
        // Fetch latest quota
        val aboutResult = apiClient.getAbout(account.accessToken)
        if (aboutResult.isFailure) {
            val ex = aboutResult.exceptionOrNull()
            if (ex is DriveApiException.TokenExpiredException) {
                accountDao.updateStatus(account.id, "TOKEN_EXPIRED")
            } else if (ex is DriveApiException.RateLimitExceededException) {
                accountDao.updateStatus(account.id, "RATE_LIMITED")
            } else {
                accountDao.updateStatus(account.id, "ERROR")
            }
            return@withContext Result.failure(ex ?: Exception("Sync failed"))
        }

        val about = aboutResult.getOrThrow()
        val quota = about.storageQuota
        accountDao.updateQuota(
            accountId = account.id,
            limit = quota?.getLimitBytes(),
            used = quota?.getUsageBytes() ?: account.usedStorage,
            driveUsage = quota?.getDriveUsageBytes() ?: account.usageInDrive,
            trashUsage = quota?.getTrashUsageBytes() ?: account.usageInDriveTrash
        )

        syncAccountFiles(account)
        Result.success(Unit)
    }

    suspend fun syncAllAccounts(): Map<String, Result<Unit>> = withContext(Dispatchers.IO) {
        val accounts = accountDao.getAllAccounts()
        val results = mutableMapOf<String, Result<Unit>>()
        for (acc in accounts) {
            results[acc.email] = syncAccount(acc)
        }
        results
    }

    private suspend fun syncAccountFiles(account: AccountEntity) {
        val filesResult = apiClient.listFiles(account.accessToken)
        if (filesResult.isSuccess) {
            val dtoList = filesResult.getOrThrow()
            val entities = dtoList.map { dto ->
                DriveFileEntity(
                    id = dto.id,
                    accountId = account.id,
                    accountEmail = account.email,
                    name = dto.name,
                    mimeType = dto.mimeType,
                    sizeBytes = dto.getSizeBytes(),
                    modifiedTime = parseIsoTimeToMillis(dto.modifiedTime),
                    webViewLink = dto.webViewLink,
                    iconLink = dto.iconLink,
                    thumbnailLink = dto.thumbnailLink,
                    syncStatus = "SYNCED"
                )
            }
            // Clear and replace files for this account
            driveFileDao.deleteForAccount(account.id)
            driveFileDao.insertAll(entities)
        }
    }

    suspend fun uploadFile(
        targetAccountId: String?,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): Result<DriveFileEntity> = withContext(Dispatchers.IO) {
        val accounts = accountDao.getAllAccounts()
        if (accounts.isEmpty()) {
            return@withContext Result.failure(Exception("No Google accounts connected. Connect an account first."))
        }

        // Determine destination account: user-selected or smart auto-select with largest capacity
        val account = if (!targetAccountId.isNullOrBlank()) {
            accounts.find { it.id == targetAccountId }
                ?: return@withContext Result.failure(Exception("Selected account not found."))
        } else {
            findBestAccountForUpload(accounts, fileBytes.size.toLong())
                ?: return@withContext Result.failure(
                    DriveApiException.InsufficientStorageException(
                        "No connected account has sufficient storage capacity (${formatBytes(fileBytes.size.toLong())} needed)."
                    )
                )
        }

        // Verify account capacity
        val fileSize = fileBytes.size.toLong()
        if (account.totalStorageLimit != null) {
            val freeSpace = account.totalStorageLimit - account.usedStorage
            if (freeSpace < fileSize) {
                val record = UploadRecordEntity(
                    accountId = account.id,
                    accountEmail = account.email,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = fileSize,
                    status = "FAILED",
                    errorMessage = "Insufficient capacity. Needed: ${formatBytes(fileSize)}, Free: ${formatBytes(freeSpace)}"
                )
                uploadRecordDao.insert(record)
                return@withContext Result.failure(
                    DriveApiException.InsufficientStorageException(
                        "Account ${account.email} has insufficient capacity. Available: ${formatBytes(freeSpace)}, Required: ${formatBytes(fileSize)}."
                    )
                )
            }
        }

        // Create in-progress upload record
        val recordId = uploadRecordDao.insert(
            UploadRecordEntity(
                accountId = account.id,
                accountEmail = account.email,
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = fileSize,
                status = "IN_PROGRESS"
            )
        )

        // Perform real Google Drive multipart upload
        val uploadResult = apiClient.uploadFile(
            accessToken = account.accessToken,
            fileName = fileName,
            mimeType = mimeType,
            fileBytes = fileBytes
        )

        if (uploadResult.isFailure) {
            val ex = uploadResult.exceptionOrNull() ?: Exception("Upload failed")
            uploadRecordDao.update(
                UploadRecordEntity(
                    id = recordId,
                    accountId = account.id,
                    accountEmail = account.email,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = fileSize,
                    status = "FAILED",
                    errorMessage = ex.message
                )
            )
            return@withContext Result.failure(ex)
        }

        val uploadedDto = uploadResult.getOrThrow()
        val fileEntity = DriveFileEntity(
            id = uploadedDto.id,
            accountId = account.id,
            accountEmail = account.email,
            name = uploadedDto.name,
            mimeType = uploadedDto.mimeType,
            sizeBytes = uploadedDto.getSizeBytes().takeIf { it > 0 } ?: fileSize,
            modifiedTime = System.currentTimeMillis(),
            webViewLink = uploadedDto.webViewLink ?: "https://drive.google.com/file/d/${uploadedDto.id}/view",
            iconLink = uploadedDto.iconLink,
            thumbnailLink = uploadedDto.thumbnailLink,
            isUploadedByApp = true,
            syncStatus = "SYNCED"
        )

        // Persist in Room database
        driveFileDao.insertFile(fileEntity)

        // Update Account storage usage
        val newUsed = account.usedStorage + fileSize
        accountDao.updateQuota(
            accountId = account.id,
            limit = account.totalStorageLimit,
            used = newUsed,
            driveUsage = account.usageInDrive + fileSize,
            trashUsage = account.usageInDriveTrash
        )

        // Update upload record
        uploadRecordDao.update(
            UploadRecordEntity(
                id = recordId,
                accountId = account.id,
                accountEmail = account.email,
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = fileSize,
                driveFileId = uploadedDto.id,
                status = "SUCCESS"
            )
        )

        Result.success(fileEntity)
    }

    suspend fun deleteFile(file: DriveFileEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val account = accountDao.getAccountById(file.accountId)
        if (account != null) {
            val result = apiClient.deleteFile(account.accessToken, file.id)
            if (result.isFailure) {
                // Return failure or fallback to local deletion if not found on drive
            }
        }
        driveFileDao.deleteById(file.id)
        Result.success(Unit)
    }

    suspend fun disconnectAccount(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        driveFileDao.deleteForAccount(accountId)
        accountDao.deleteById(accountId)
        Result.success(Unit)
    }

    fun calculateStorageAggregation(accounts: List<AccountEntity>): StorageAggregation {
        if (accounts.isEmpty()) {
            return StorageAggregation(
                totalLimitBytes = 0L,
                totalUsedBytes = 0L,
                totalAvailableBytes = 0L,
                usedPercentage = 0f,
                accountCount = 0
            )
        }

        var totalLimit: Long? = 0L
        var totalUsed = 0L

        for (acc in accounts) {
            totalUsed += acc.usedStorage
            if (acc.totalStorageLimit == null) {
                totalLimit = null // At least one account has uncapped storage
            } else if (totalLimit != null) {
                totalLimit += acc.totalStorageLimit
            }
        }

        val totalAvailable = if (totalLimit != null) {
            (totalLimit - totalUsed).coerceAtLeast(0L)
        } else null

        val percentage = if (totalLimit != null && totalLimit > 0) {
            ((totalUsed.toDouble() / totalLimit.toDouble()) * 100).toFloat().coerceIn(0f, 100f)
        } else 0f

        return StorageAggregation(
            totalLimitBytes = totalLimit,
            totalUsedBytes = totalUsed,
            totalAvailableBytes = totalAvailable,
            usedPercentage = percentage,
            accountCount = accounts.size
        )
    }

    private fun findBestAccountForUpload(accounts: List<AccountEntity>, requiredBytes: Long): AccountEntity? {
        val connected = accounts.filter { it.status == "CONNECTED" }
        if (connected.isEmpty()) return null

        // Pick account with highest available space
        return connected.maxByOrNull { acc ->
            if (acc.totalStorageLimit == null) Long.MAX_VALUE
            else (acc.totalStorageLimit - acc.usedStorage)
        }?.takeIf { acc ->
            acc.totalStorageLimit == null || (acc.totalStorageLimit - acc.usedStorage) >= requiredBytes
        }
    }

    suspend fun purgeAnyDemoData() = withContext(Dispatchers.IO) {
        accountDao.deleteDemoAccounts()
        driveFileDao.deleteDemoFiles()
    }

    private fun parseIsoTimeToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            java.time.Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
            val gb = mb / 1024.0
            if (gb < 1024) return String.format(java.util.Locale.US, "%.2f GB", gb)
            val tb = gb / 1024.0
            return String.format(java.util.Locale.US, "%.2f TB", tb)
        }
    }
}
