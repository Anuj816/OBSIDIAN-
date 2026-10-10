package com.anuj.obsidian.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.theme.AppThemePalette
import com.anuj.obsidian.ui.theme.DarkSurface
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.DarkSurfaceCard
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.FlacLosslessCyan
import com.anuj.obsidian.ui.theme.TextMuted
import com.anuj.obsidian.ui.theme.TextPrimary
import com.anuj.obsidian.ui.theme.TextSecondary
import com.anuj.obsidian.util.ObsidianStorageCleanupManager

/**
 * =========================================================================================
 * Storage Cleanup Tool Section
 * =========================================================================================
 * RESPONSIBILITY:
 * Manages cache metrics, 500MB online artwork guard progress, temp buffer cleanup,
 * and quick-clean storage actions with dedicated confirmation dialogs.
 * =========================================================================================
 */
@Composable
fun StorageCleanupToolSection(
    viewModel: ObsidianViewModel,
    context: Context,
    activePalette: AppThemePalette
) {
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val notificationMsg by ObsidianStorageCleanupManager.latestCleanupNotification.collectAsStateWithLifecycle()
    val autoResetCount by ObsidianStorageCleanupManager.autoResetCount.collectAsStateWithLifecycle()

    var showClearArtDialog by remember { mutableStateOf(false) }
    var showClearTempDialog by remember { mutableStateOf(false) }
    var showQuickCleanDialog by remember { mutableStateOf(false) }
    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageStats(context)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storage_cleanup_tool_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(activePalette.primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Cleanup Tool",
                            tint = activePalette.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "STORAGE CLEANUP TOOL",
                            color = activePalette.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Artwork & Download Temp Cleaner",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        viewModel.refreshStorageStats(context)
                        Toast.makeText(context, "Storage metrics updated", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("refresh_storage_stats_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Storage Stats",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Notification / Banner if Auto-Reset was triggered
            if (!notificationMsg.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B382B)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = notificationMsg ?: "",
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        IconButton(
                            onClick = { ObsidianStorageCleanupManager.dismissNotification() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Metric 1: Downloaded Tracks Audio Library
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = FlacHiResGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Downloaded Audio Tracks",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Locally stored audio tracks and albums",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = storageStats.downloadedAudioFormatted,
                        color = FlacHiResGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { showClearDownloadsDialog = true },
                        modifier = Modifier.testTag("clear_downloaded_tracks_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Downloads", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Metric 2: Online Song Artwork Cache with 500 MB Limit
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = activePalette.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Cached Album Art (Online Songs)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "${storageStats.cachedArtworkFormatted} / 500 MB",
                        color = activePalette.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Progress Bar towards 500 MB
                val progressRatio = storageStats.artworkUsageRatio
                val barColor = when {
                    progressRatio > 0.9f -> Color(0xFFFF5252)
                    progressRatio > 0.7f -> FlacHiResGold
                    else -> activePalette.primary
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = barColor,
                        trackColor = DarkSurfaceBorder
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f%% of 500 MB limit used", progressRatio * 100f),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        if (autoResetCount > 0) {
                            Text(
                                text = "Auto-resets: $autoResetCount",
                                color = activePalette.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Cap: 500 MB",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Auto-Reset Policy Tag
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Auto Cache Reset Active: Clears automatically upon reaching 500 MB",
                        color = Color(0xFFC8E6C9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Action row for Artwork Cache
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showClearArtDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_art_cache_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = activePalette.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Art Cache", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.forceCheckOnlineArtworkLimit(context) { triggered, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("check_artwork_limit_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test 500MB Guard", fontSize = 12.sp)
                    }
                }
            }

            // Metric 3: General App Cache & Temp Buffers
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = FlacLosslessCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "General App Cache & Buffers",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Temp streaming chunks (${storageStats.downloadTempFormatted}) & disk cache",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = storageStats.totalAppCacheFormatted,
                        color = FlacLosslessCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showClearTempDialog = true },
                        modifier = Modifier.weight(1f).testTag("clear_download_temp_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FlacLosslessCyan),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Temp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showClearCacheDialog = true },
                        modifier = Modifier.weight(1f).testTag("clear_all_cache_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Cache", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Summary card with Quick Clean
            Card(
                colors = CardDefaults.cardColors(containerColor = activePalette.primary.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, activePalette.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Recoverable Space",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = storageStats.totalRecoverableFormatted,
                            color = activePalette.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Button(
                        onClick = { showQuickCleanDialog = true },
                        modifier = Modifier.testTag("quick_clean_all_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activePalette.primary,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Quick Clean", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Dialogs
    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text("Clear Downloaded Tracks?", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete all ${storageStats.downloadedAudioFormatted} of downloaded audio files from your device storage?\n\nThis action cannot be undone. You will need to re-download songs to listen offline.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDownloadsDialog = false
                        viewModel.clearAllDownloadedMusic(context) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White)
                ) {
                    Text("Delete Downloads", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurfaceCard
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear General App Cache?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will clear ${storageStats.totalAppCacheFormatted} of general application cache files, image caches, and temporary buffers.\n\nDownloaded music files and app settings will NOT be deleted.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearAppCache(context) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activePalette.primary, contentColor = Color.Black)
                ) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurfaceCard
        )
    }

    if (showClearArtDialog) {
        AlertDialog(
            onDismissRequest = { showClearArtDialog = false },
            title = { Text("Clear Artwork Cache?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will delete ${storageStats.cachedArtworkFormatted} of cached album art covers and palette memory. Artwork will automatically re-download as online songs play.\n\nDownloaded audio files and settings will NOT be touched.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearArtDialog = false
                        viewModel.clearCachedAlbumArt(context) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activePalette.primary, contentColor = Color.Black)
                ) {
                    Text("Clear Artwork")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearArtDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurfaceCard
        )
    }

    if (showClearTempDialog) {
        AlertDialog(
            onDismissRequest = { showClearTempDialog = false },
            title = { Text("Clear Download Temp Files?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will delete ${storageStats.downloadTempFormatted} of temporary download files (.downloading) and streaming buffer caches.\n\nCompleted downloaded songs are completely safe and will NOT be deleted.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearTempDialog = false
                        viewModel.clearDownloadTempFiles(context) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlacLosslessCyan, contentColor = Color.Black)
                ) {
                    Text("Clear Temp Files")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTempDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurfaceCard
        )
    }

    if (showQuickCleanDialog) {
        AlertDialog(
            onDismissRequest = { showQuickCleanDialog = false },
            title = { Text("Quick Clean All Temporary Files?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Reclaim ${storageStats.totalRecoverableFormatted} of device storage right now by clearing both cached online album art and download manager temp files.\n\nYour library, playlists, settings, and completed audio downloads are completely preserved.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuickCleanDialog = false
                        viewModel.quickCleanAllStorage(context) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activePalette.primary, contentColor = Color.Black)
                ) {
                    Text("Clean All Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickCleanDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurfaceCard
        )
    }
}
