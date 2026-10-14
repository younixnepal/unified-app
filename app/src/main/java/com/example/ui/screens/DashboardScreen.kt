package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.UploadRecordEntity
import com.example.data.repository.StorageAggregation
import com.example.data.repository.UnifiedCloudRepository
import com.example.ui.components.AccountCard
import com.example.ui.components.StorageOverviewCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val accountPalette = listOf(
    Color(0xFF1A73E8),
    Color(0xFF00B0FF),
    Color(0xFF00C853),
    Color(0xFFFF9100),
    Color(0xFFAA00FF)
)

@Composable
fun DashboardScreen(
    aggregation: StorageAggregation,
    accounts: List<AccountEntity>,
    uploadRecords: List<UploadRecordEntity>,
    isRefreshing: Boolean,
    isSyncingDelta: Boolean,
    lastSyncTimeMillis: Long,
    syncIntervalSeconds: Int,
    isAutoSyncEnabled: Boolean,
    onRefreshAll: () -> Unit,
    onRefreshAccount: (AccountEntity) -> Unit,
    onDisconnectAccount: (String) -> Unit,
    onOpenConnectDialog: () -> Unit,
    onOpenUploadDialog: () -> Unit,
    onNavigateToExplorer: () -> Unit,
    onTriggerDeltaSync: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onSetSyncInterval: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage Overview Card
        item {
            StorageOverviewCard(
                aggregation = aggregation,
                accounts = accounts,
                isRefreshing = isRefreshing,
                onRefreshAll = onRefreshAll
            )
        }

        // Real-Time Sync Control Card
        item {
            com.example.ui.components.SyncControlCard(
                isSyncingDelta = isSyncingDelta,
                lastSyncTimeMillis = lastSyncTimeMillis,
                syncIntervalSeconds = syncIntervalSeconds,
                isAutoSyncEnabled = isAutoSyncEnabled,
                onTriggerDeltaSync = onTriggerDeltaSync,
                onToggleAutoSync = onToggleAutoSync,
                onSetSyncInterval = onSetSyncInterval
            )
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenUploadDialog,
                    modifier = Modifier.weight(1f).testTag("quick_upload_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload File")
                }

                OutlinedButton(
                    onClick = onOpenConnectDialog,
                    modifier = Modifier.weight(1f).testTag("quick_connect_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Account")
                }
            }
        }

        // Connected Accounts Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected Google Accounts (${accounts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onOpenConnectDialog) {
                    Text("+ Connect New")
                }
            }
        }

        // Account Cards or Empty State
        if (accounts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "No Google Accounts Connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connect your Google Drive account with live OAuth credentials to enable real-time file synchronization and pooled capacity management.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(onClick = onOpenConnectDialog) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect Google Account")
                        }
                    }
                }
            }
        } else {
            itemsIndexed(accounts) { index, account ->
                val color = accountPalette[index % accountPalette.size]
                AccountCard(
                    account = account,
                    accentColor = color,
                    onRefresh = { onRefreshAccount(account) },
                    onDisconnect = { onDisconnectAccount(account.id) },
                    onReauthorize = onOpenConnectDialog
                )
            }
        }

        // Recent Upload Activity Section
        if (uploadRecords.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recent Upload & Sync Activity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            itemsIndexed(uploadRecords.take(5)) { _, record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.fileName,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "To: ${record.accountEmail} • ${UnifiedCloudRepository.formatBytes(record.fileSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!record.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = "Error: ${record.errorMessage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val statusColor = when (record.status) {
                                "SUCCESS" -> Color(0xFF2E7D32)
                                "FAILED" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Text(
                                text = record.status,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.uploadTimestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
