package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.DriveFileEntity
import com.example.data.local.entity.UploadRecordEntity
import com.example.data.remote.DriveApiException
import com.example.data.repository.StorageAggregation
import com.example.data.repository.UnifiedCloudRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

data class UnifiedCloudUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val files: List<DriveFileEntity> = emptyList(),
    val filteredFiles: List<DriveFileEntity> = emptyList(),
    val uploadRecords: List<UploadRecordEntity> = emptyList(),
    val storageAggregation: StorageAggregation = StorageAggregation(0L, 0L, 0L, 0f, 0),
    val selectedAccountIdFilter: String? = null,
    val selectedCategoryFilter: String = "ALL",
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val isRefreshing: Boolean = false,
    val isSyncingDelta: Boolean = false,
    val lastSyncTimeMillis: Long = System.currentTimeMillis(),
    val syncIntervalSeconds: Int = 15,
    val isAutoSyncEnabled: Boolean = true,
    val lastSyncSummaryMessage: String? = null,
    val isUploading: Boolean = false,
    val uploadProgressMessage: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val currentTab: Int = 0 // 0: Overview, 1: Explorer, 2: Accounts, 3: Activity
)

class UnifiedCloudViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UnifiedCloudRepository(application)

    private val _uiState = MutableStateFlow(UnifiedCloudUiState())
    val uiState: StateFlow<UnifiedCloudUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null

    init {
        viewModelScope.launch {
            repository.purgeAnyDemoData()
        }

        startRealtimeSyncPoller()

        // Combine repository flows with search, filter, and sort state
        viewModelScope.launch {
            combine(
                repository.accountsFlow,
                repository.allFilesFlow,
                repository.uploadRecordsFlow
            ) { accounts, files, uploads ->
                Triple(accounts, files, uploads)
            }.collect { (accounts, files, uploads) ->
                val aggregation = repository.calculateStorageAggregation(accounts)
                _uiState.update { current ->
                    val filtered = applyFilters(
                        allFiles = files,
                        accountId = current.selectedAccountIdFilter,
                        category = current.selectedCategoryFilter,
                        query = current.searchQuery,
                        sort = current.sortOption
                    )
                    current.copy(
                        accounts = accounts,
                        files = files,
                        uploadRecords = uploads,
                        storageAggregation = aggregation,
                        filteredFiles = filtered
                    )
                }
            }
        }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(currentTab = index) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredFiles = applyFilters(
                    allFiles = current.files,
                    accountId = current.selectedAccountIdFilter,
                    category = current.selectedCategoryFilter,
                    query = query,
                    sort = current.sortOption
                )
            )
        }
    }

    fun setAccountFilter(accountId: String?) {
        _uiState.update { current ->
            current.copy(
                selectedAccountIdFilter = accountId,
                filteredFiles = applyFilters(
                    allFiles = current.files,
                    accountId = accountId,
                    category = current.selectedCategoryFilter,
                    query = current.searchQuery,
                    sort = current.sortOption
                )
            )
        }
    }

    fun setCategoryFilter(category: String) {
        _uiState.update { current ->
            current.copy(
                selectedCategoryFilter = category,
                filteredFiles = applyFilters(
                    allFiles = current.files,
                    accountId = current.selectedAccountIdFilter,
                    category = category,
                    query = current.searchQuery,
                    sort = current.sortOption
                )
            )
        }
    }

    fun setSortOption(sort: SortOption) {
        _uiState.update { current ->
            current.copy(
                sortOption = sort,
                filteredFiles = applyFilters(
                    allFiles = current.files,
                    accountId = current.selectedAccountIdFilter,
                    category = current.selectedCategoryFilter,
                    query = current.searchQuery,
                    sort = sort
                )
            )
        }
    }

    fun connectAccount(accessToken: String, refreshToken: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = repository.connectAccount(
                accessToken = accessToken.trim(),
                refreshToken = refreshToken?.trim()
            )
            _uiState.update { current ->
                if (result.isSuccess) {
                    val acc = result.getOrThrow()
                    current.copy(
                        isRefreshing = false,
                        successMessage = "Connected Google Account: ${acc.email} with ${UnifiedCloudRepository.formatBytes(acc.totalStorageLimit ?: 0L)} quota!"
                    )
                } else {
                    val ex = result.exceptionOrNull()
                    current.copy(
                        isRefreshing = false,
                        errorMessage = "Authentication failed: ${ex?.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun refreshAllAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val results = repository.syncAllAccounts()
            val failures = results.filter { it.value.isFailure }
            _uiState.update { current ->
                if (failures.isEmpty()) {
                    current.copy(
                        isRefreshing = false,
                        successMessage = "Refreshed storage quotas and files across all accounts."
                    )
                } else {
                    val errorDetails = failures.entries.joinToString("; ") { "${it.key}: ${it.value.exceptionOrNull()?.message}" }
                    current.copy(
                        isRefreshing = false,
                        errorMessage = "Some accounts could not sync: $errorDetails"
                    )
                }
            }
        }
    }

    fun refreshAccount(account: AccountEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = repository.syncAccount(account)
            _uiState.update { current ->
                if (result.isSuccess) {
                    current.copy(
                        isRefreshing = false,
                        successMessage = "Updated quota for ${account.email}"
                    )
                } else {
                    current.copy(
                        isRefreshing = false,
                        errorMessage = "Sync error: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun disconnectAccount(accountId: String) {
        viewModelScope.launch {
            val result = repository.disconnectAccount(accountId)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(successMessage = "Account disconnected.")
                }
            }
        }
    }

    fun uploadFile(
        targetAccountId: String?,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    uploadProgressMessage = "Uploading $fileName (${UnifiedCloudRepository.formatBytes(fileBytes.size.toLong())})...",
                    errorMessage = null
                )
            }

            val result = repository.uploadFile(
                targetAccountId = targetAccountId,
                fileName = fileName,
                mimeType = mimeType,
                fileBytes = fileBytes
            )

            _uiState.update { current ->
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    current.copy(
                        isUploading = false,
                        uploadProgressMessage = null,
                        successMessage = "Successfully uploaded '${file.name}' to ${file.accountEmail}!"
                    )
                } else {
                    val ex = result.exceptionOrNull()
                    val errorMsg = when (ex) {
                        is DriveApiException.InsufficientStorageException -> "Upload failed: ${ex.message}"
                        is DriveApiException.TokenExpiredException -> "Upload failed: Token expired. Please re-authenticate the account."
                        is DriveApiException.RateLimitExceededException -> "Upload failed: Drive API rate limit reached."
                        else -> "Upload error: ${ex?.message ?: "Unknown error"}"
                    }
                    current.copy(
                        isUploading = false,
                        uploadProgressMessage = null,
                        errorMessage = errorMsg
                    )
                }
            }
        }
    }

    fun deleteFile(file: DriveFileEntity) {
        viewModelScope.launch {
            val result = repository.deleteFile(file)
            _uiState.update { current ->
                if (result.isSuccess) {
                    current.copy(successMessage = "Deleted '${file.name}'")
                } else {
                    current.copy(errorMessage = "Could not delete file: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun startRealtimeSyncPoller() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (isActive) {
                val interval = _uiState.value.syncIntervalSeconds
                val enabled = _uiState.value.isAutoSyncEnabled
                if (enabled && interval > 0) {
                    delay(interval * 1000L)
                    performDeltaSync(silent = true)
                } else {
                    delay(3000L)
                }
            }
        }
    }

    fun performDeltaSync(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingDelta = true) }
            val summaries = repository.syncAllAccountsDelta()
            val totalCreatedUpdated = summaries.sumOf { it.createdOrUpdatedCount }
            val totalDeleted = summaries.sumOf { it.deletedCount }
            val hasChanges = totalCreatedUpdated > 0 || totalDeleted > 0

            val summaryMsg = if (hasChanges) {
                "⚡ Live sync: +$totalCreatedUpdated updated/created, -$totalDeleted removed"
            } else if (!silent) {
                "All connected accounts are up to date"
            } else null

            _uiState.update { current ->
                current.copy(
                    isSyncingDelta = false,
                    lastSyncTimeMillis = System.currentTimeMillis(),
                    lastSyncSummaryMessage = summaryMsg,
                    successMessage = if (!silent && hasChanges) summaryMsg else current.successMessage
                )
            }
        }
    }

    fun triggerManualSync() {
        performDeltaSync(silent = false)
    }

    fun setSyncInterval(seconds: Int) {
        _uiState.update { it.copy(syncIntervalSeconds = seconds) }
        startRealtimeSyncPoller()
    }

    fun toggleAutoSync(enabled: Boolean) {
        _uiState.update { it.copy(isAutoSyncEnabled = enabled) }
        if (enabled) {
            startRealtimeSyncPoller()
        } else {
            syncJob?.cancel()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun applyFilters(
        allFiles: List<DriveFileEntity>,
        accountId: String?,
        category: String,
        query: String,
        sort: SortOption
    ): List<DriveFileEntity> {
        return allFiles
            .filter { file ->
                accountId == null || file.accountId == accountId
            }
            .filter { file ->
                when (category) {
                    "DOCS" -> file.mimeType.contains("pdf") ||
                            file.mimeType.contains("word") ||
                            file.mimeType.contains("text") ||
                            file.mimeType.contains("document")
                    "SHEETS" -> file.mimeType.contains("sheet") ||
                            file.mimeType.contains("excel") ||
                            file.mimeType.contains("csv")
                    "IMAGES" -> file.mimeType.startsWith("image/")
                    "MEDIA" -> file.mimeType.startsWith("video/") || file.mimeType.startsWith("audio/")
                    "ARCHIVES" -> file.mimeType.contains("zip") ||
                            file.mimeType.contains("tar") ||
                            file.mimeType.contains("rar") ||
                            file.mimeType.contains("compressed")
                    else -> true
                }
            }
            .filter { file ->
                if (query.isBlank()) true
                else file.name.contains(query, ignoreCase = true) || file.accountEmail.contains(query, ignoreCase = true)
            }
            .let { list ->
                when (sort) {
                    SortOption.DATE_DESC -> list.sortedByDescending { it.modifiedTime }
                    SortOption.DATE_ASC -> list.sortedBy { it.modifiedTime }
                    SortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                    SortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                    SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                    SortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes }
                }
            }
    }
}
