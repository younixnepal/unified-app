package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.UnifiedCloudViewModel
import com.example.ui.components.ConnectAccountDialog
import com.example.ui.components.UploadDialog
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExplorerScreen
import com.example.ui.theme.UnifiedCloudTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var viewModelInstance: UnifiedCloudViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UnifiedCloudTheme {
                val vm: UnifiedCloudViewModel = viewModel()
                viewModelInstance = vm

                // Check for OAuth deep link callback
                LaunchedEffect(intent) {
                    handleOAuthIntent(intent, vm)
                }

                UnifiedCloudApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelInstance?.let { handleOAuthIntent(intent, it) }
    }

    private fun handleOAuthIntent(intent: Intent?, vm: UnifiedCloudViewModel) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "unifiedcloud" && data.host == "oauth2callback") {
            val code = data.getQueryParameter("code")
            val token = data.getQueryParameter("access_token") ?: data.fragment?.substringAfter("access_token=")?.substringBefore("&")
            if (!token.isNullOrBlank()) {
                vm.connectAccount(token)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedCloudApp(viewModel: UnifiedCloudViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showUploadDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }

    // Display error/success messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "UnifiedCloud",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Multi-Account Storage Manager",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshAllAccounts() },
                        enabled = !uiState.isRefreshing,
                        modifier = Modifier.testTag("appbar_sync_all")
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync all Google accounts",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = { Icon(Icons.Default.GridView, contentDescription = "Overview") },
                    label = { Text("Overview") },
                    modifier = Modifier.testTag("tab_overview")
                )
                NavigationBarItem(
                    selected = uiState.currentTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Explorer") },
                    label = { Text("Explorer") },
                    modifier = Modifier.testTag("tab_explorer")
                )
                NavigationBarItem(
                    selected = uiState.currentTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = { Icon(Icons.Default.ManageAccounts, contentDescription = "Accounts") },
                    label = { Text("Accounts (${uiState.accounts.size})") },
                    modifier = Modifier.testTag("tab_accounts")
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showUploadDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("fab_upload")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "Upload to Drive")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                0 -> DashboardScreen(
                    aggregation = uiState.storageAggregation,
                    accounts = uiState.accounts,
                    uploadRecords = uiState.uploadRecords,
                    isRefreshing = uiState.isRefreshing,
                    isSyncingDelta = uiState.isSyncingDelta,
                    lastSyncTimeMillis = uiState.lastSyncTimeMillis,
                    syncIntervalSeconds = uiState.syncIntervalSeconds,
                    isAutoSyncEnabled = uiState.isAutoSyncEnabled,
                    onRefreshAll = { viewModel.refreshAllAccounts() },
                    onRefreshAccount = { viewModel.refreshAccount(it) },
                    onDisconnectAccount = { viewModel.disconnectAccount(it) },
                    onOpenConnectDialog = { showConnectDialog = true },
                    onOpenUploadDialog = { showUploadDialog = true },
                    onNavigateToExplorer = { viewModel.setTab(1) },
                    onTriggerDeltaSync = { viewModel.triggerManualSync() },
                    onToggleAutoSync = { viewModel.toggleAutoSync(it) },
                    onSetSyncInterval = { viewModel.setSyncInterval(it) }
                )
                1 -> ExplorerScreen(
                    files = uiState.filteredFiles,
                    accounts = uiState.accounts,
                    searchQuery = uiState.searchQuery,
                    selectedAccountId = uiState.selectedAccountIdFilter,
                    selectedCategory = uiState.selectedCategoryFilter,
                    currentSort = uiState.sortOption,
                    isSyncingDelta = uiState.isSyncingDelta,
                    lastSyncTimeMillis = uiState.lastSyncTimeMillis,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onAccountFilterChange = { viewModel.setAccountFilter(it) },
                    onCategoryFilterChange = { viewModel.setCategoryFilter(it) },
                    onSortChange = { viewModel.setSortOption(it) },
                    onDeleteFile = { viewModel.deleteFile(it) },
                    onTriggerDeltaSync = { viewModel.triggerManualSync() }
                )
                2 -> AccountsScreen(
                    accounts = uiState.accounts,
                    onConnectAccount = { showConnectDialog = true },
                    onRefreshAccount = { viewModel.refreshAccount(it) },
                    onDisconnectAccount = { viewModel.disconnectAccount(it) }
                )
            }

            // In-flight upload banner
            if (uiState.isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(14.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.uploadProgressMessage ?: "Uploading file to Google Drive...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        UploadDialog(
            accounts = uiState.accounts,
            isUploading = uiState.isUploading,
            uploadProgressMessage = uiState.uploadProgressMessage,
            onDismiss = { showUploadDialog = false },
            onUpload = { targetId, name, mime, bytes ->
                viewModel.uploadFile(targetId, name, mime, bytes)
                showUploadDialog = false
            }
        )
    }

    if (showConnectDialog) {
        ConnectAccountDialog(
            onDismiss = { showConnectDialog = false },
            onConnectToken = { token, refresh ->
                viewModel.connectAccount(token, refresh)
            }
        )
    }
}
