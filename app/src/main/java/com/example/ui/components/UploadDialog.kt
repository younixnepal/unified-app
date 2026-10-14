package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.AccountEntity
import com.example.data.repository.UnifiedCloudRepository

@Composable
fun UploadDialog(
    accounts: List<AccountEntity>,
    isUploading: Boolean,
    uploadProgressMessage: String?,
    onDismiss: () -> Unit,
    onUpload: (targetAccountId: String?, fileName: String, mimeType: String, bytes: ByteArray) -> Unit
) {
    val context = LocalContext.current
    var uploadMode by remember { mutableIntStateOf(0) } // 0: Pick device file, 1: Quick create document
    var selectedAccountId by remember { mutableStateOf<String?>(null) } // null = auto-select
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    // Pick file state
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedFileName by remember { mutableStateOf("") }
    var pickedMimeType by remember { mutableStateOf("application/octet-stream") }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Create custom document state
    var customDocName by remember { mutableStateOf("Project_Notes.txt") }
    var customDocContent by remember {
        mutableStateOf("UnifiedCloud: Cross-account Google Drive test document.\nGenerated timestamp: ${System.currentTimeMillis()}")
    }
    var customDocType by remember { mutableStateOf("text/plain") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            try {
                val contentResolver = context.contentResolver
                // Extract file name
                var name = "uploaded_file"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
                pickedFileName = name
                pickedMimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val stream = contentResolver.openInputStream(uri)
                pickedBytes = stream?.readBytes()
                stream?.close()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    // Determine target account and capacity check
    val targetAccount = accounts.find { it.id == selectedAccountId }
    val effectiveTargetAccount = targetAccount ?: accounts.filter { it.status == "CONNECTED" }.maxByOrNull {
        if (it.totalStorageLimit == null) Long.MAX_VALUE else (it.totalStorageLimit - it.usedStorage)
    }

    val fileSizeBytes = if (uploadMode == 0) {
        pickedBytes?.size?.toLong() ?: 0L
    } else {
        customDocContent.toByteArray(Charsets.UTF_8).size.toLong()
    }

    val availableBytes = effectiveTargetAccount?.let { acc ->
        if (acc.totalStorageLimit == null) null
        else (acc.totalStorageLimit - acc.usedStorage).coerceAtLeast(0L)
    }

    val hasInsufficientCapacity = availableBytes != null && availableBytes < fileSizeBytes

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload to Google Drive", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Mode selector
                TabRow(selectedTabIndex = uploadMode) {
                    Tab(
                        selected = uploadMode == 0,
                        onClick = { uploadMode = 0 },
                        text = { Text("Pick File") },
                        icon = { Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = uploadMode == 1,
                        onClick = { uploadMode = 1 },
                        text = { Text("Create Note") },
                        icon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Target Account Selector
                Column {
                    Text(
                        text = "Destination Account",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { accountDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth().testTag("select_account_dropdown")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selectedAccountId == null) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Auto-select (Best Capacity)")
                                    } else {
                                        Text(targetAccount?.email ?: "Select Account")
                                    }
                                }
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }

                        DropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Smart Auto-select", fontWeight = FontWeight.Bold)
                                            Text(
                                                "Routes to account with largest available free space",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedAccountId = null
                                    accountDropdownExpanded = false
                                }
                            )
                            accounts.forEach { acc ->
                                val freeStr = acc.totalStorageLimit?.let {
                                    "${UnifiedCloudRepository.formatBytes(it - acc.usedStorage)} free"
                                } ?: "Uncapped"
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(acc.email, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${acc.displayName} • $freeStr",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedAccountId = acc.id
                                        accountDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Target account capacity status
                    effectiveTargetAccount?.let { acc ->
                        val freeStr = availableBytes?.let { UnifiedCloudRepository.formatBytes(it) } ?: "Unlimited"
                        Text(
                            text = "Target: ${acc.email} ($freeStr available)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }

                // Upload Body depending on mode
                if (uploadMode == 0) {
                    // Pick file from device
                    if (pickedBytes == null) {
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth().testTag("pick_file_button")
                        ) {
                            Icon(Icons.Default.AttachFile, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select File From Device")
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = pickedFileName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Size: ${UnifiedCloudRepository.formatBytes(pickedBytes?.size?.toLong() ?: 0L)} • $pickedMimeType",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                    Text("Choose Different File")
                                }
                            }
                        }
                    }
                } else {
                    // Create test document
                    OutlinedTextField(
                        value = customDocName,
                        onValueChange = { customDocName = it },
                        label = { Text("File Name") },
                        modifier = Modifier.fillMaxWidth().testTag("file_name_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customDocContent,
                        onValueChange = { customDocContent = it },
                        label = { Text("Document Content") },
                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("file_content_input"),
                        maxLines = 5
                    )
                }

                // Insufficient capacity warning
                if (hasInsufficientCapacity) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Insufficient storage capacity on this account. Free up space or select another account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (isUploading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uploadProgressMessage ?: "Uploading file...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            val canUpload = !isUploading && !hasInsufficientCapacity && (
                (uploadMode == 0 && pickedBytes != null) ||
                (uploadMode == 1 && customDocName.isNotBlank() && customDocContent.isNotBlank())
            )
            Button(
                onClick = {
                    if (uploadMode == 0 && pickedBytes != null) {
                        onUpload(selectedAccountId, pickedFileName, pickedMimeType, pickedBytes!!)
                    } else if (uploadMode == 1) {
                        val bytes = customDocContent.toByteArray(Charsets.UTF_8)
                        onUpload(selectedAccountId, customDocName, customDocType, bytes)
                    }
                },
                enabled = canUpload,
                modifier = Modifier.testTag("confirm_upload_button")
            ) {
                Text("Start Upload")
            }
        },
        dismissButton = {
            if (!isUploading) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
