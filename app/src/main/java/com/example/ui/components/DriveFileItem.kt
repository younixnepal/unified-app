package com.example.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.DriveFileEntity
import com.example.data.repository.UnifiedCloudRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriveFileItem(
    file: DriveFileEntity,
    accountColor: Color,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (icon, iconTint, iconBg) = getFileIconAndColor(file.mimeType)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("file_item_${file.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File Icon container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = file.mimeType,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Origin Account Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accountColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = file.accountEmail.substringBefore("@"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accountColor,
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Text(
                        text = UnifiedCloudRepository.formatBytes(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Text(
                        text = formatDate(file.modifiedTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (file.isUploadedByApp) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "App",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Quick Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!file.webViewLink.isNullOrBlank()) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(file.webViewLink))
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open file in Google Drive",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete file",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getFileIconAndColor(mimeType: String): Triple<ImageVector, Color, Color> {
    return when {
        mimeType.contains("folder") -> Triple(Icons.Default.Folder, Color(0xFF5F6368), Color(0xFFE8EAED))
        mimeType.contains("pdf") -> Triple(Icons.Default.PictureAsPdf, Color(0xFFEA4335), Color(0xFFFCE8E6))
        mimeType.contains("sheet") || mimeType.contains("excel") || mimeType.contains("csv") ->
            Triple(Icons.Default.TableChart, Color(0xFF34A853), Color(0xFFE6F4EA))
        mimeType.contains("word") || mimeType.contains("document") || mimeType.contains("text") ->
            Triple(Icons.Default.Description, Color(0xFF4285F4), Color(0xFFE8F0FE))
        mimeType.startsWith("image/") -> Triple(Icons.Default.Image, Color(0xFFFBBC04), Color(0xFFFEF7E0))
        mimeType.startsWith("video/") -> Triple(Icons.Default.Movie, Color(0xFFEA4335), Color(0xFFFCE8E6))
        mimeType.startsWith("audio/") -> Triple(Icons.Default.MusicNote, Color(0xFFAA00FF), Color(0xFFF3E5F5))
        mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("compressed") ->
            Triple(Icons.Default.Archive, Color(0xFF795548), Color(0xFFEFEBE9))
        else -> Triple(Icons.Default.InsertDriveFile, Color(0xFF5F6368), Color(0xFFF1F3F4))
    }
}

private fun formatDate(millis: Long): String {
    val date = Date(millis)
    val now = System.currentTimeMillis()
    val diffHours = (now - millis) / (1000 * 60 * 60)
    return if (diffHours < 24) {
        if (diffHours < 1) "Just now" else "${diffHours}h ago"
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}
