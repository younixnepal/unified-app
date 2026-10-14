package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.DriveFileEntity
import com.example.ui.SortOption
import com.example.ui.components.DriveFileItem

private val accountPalette = listOf(
    Color(0xFF1A73E8),
    Color(0xFF00B0FF),
    Color(0xFF00C853),
    Color(0xFFFF9100),
    Color(0xFFAA00FF)
)

@Composable
fun ExplorerScreen(
    files: List<DriveFileEntity>,
    accounts: List<AccountEntity>,
    searchQuery: String,
    selectedAccountId: String?,
    selectedCategory: String,
    currentSort: SortOption,
    isSyncingDelta: Boolean,
    lastSyncTimeMillis: Long,
    onSearchQueryChange: (String) -> Unit,
    onAccountFilterChange: (String?) -> Unit,
    onCategoryFilterChange: (String) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onDeleteFile: (DriveFileEntity) -> Unit,
    onTriggerDeltaSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val accountColorMap = remember(accounts) {
        accounts.mapIndexed { index, acc ->
            acc.id to accountPalette[index % accountPalette.size]
        }.toMap()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("explorer_screen")
    ) {
        // Real-Time Sync Status Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSyncingDelta) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Syncing with Google Drive...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    val diff = ((System.currentTimeMillis() - lastSyncTimeMillis) / 1000).coerceAtLeast(0)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF00C853))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (diff < 5) "Live sync active" else "Synced ${diff}s ago",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onTriggerDeltaSync,
                enabled = !isSyncingDelta,
                modifier = Modifier.size(28.dp).testTag("explorer_sync_now")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync now",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Search & Sort Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("explorer_search_field"),
                    placeholder = { Text("Search files across all accounts...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    IconButton(
                        onClick = { sortMenuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sort_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort files",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (option == currentSort) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSortChange(option)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Account Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedAccountId == null,
                    onClick = { onAccountFilterChange(null) },
                    label = { Text("All Accounts (${accounts.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                accounts.forEach { acc ->
                    val color = accountColorMap[acc.id] ?: MaterialTheme.colorScheme.primary
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = {
                            if (selectedAccountId == acc.id) onAccountFilterChange(null)
                            else onAccountFilterChange(acc.id)
                        },
                        label = { Text(acc.email.substringBefore("@")) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.2f),
                            selectedLabelColor = color
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf(
                    "ALL" to "All Types",
                    "DOCS" to "Documents",
                    "SHEETS" to "Sheets",
                    "IMAGES" to "Images",
                    "MEDIA" to "Media",
                    "ARCHIVES" to "Archives"
                )

                categories.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedCategory == key,
                        onClick = { onCategoryFilterChange(key) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Status count indicator
            Text(
                text = "${files.size} file${if (files.size != 1) "s" else ""} • Sorted by ${currentSort.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // File List
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "No files match '$searchQuery'" else "No files in selected filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Upload files using the (+) button or select 'All Accounts'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    val color = accountColorMap[file.accountId] ?: MaterialTheme.colorScheme.primary
                    DriveFileItem(
                        file = file,
                        accountColor = color,
                        onDelete = { onDeleteFile(file) }
                    )
                }
            }
        }
    }
}
