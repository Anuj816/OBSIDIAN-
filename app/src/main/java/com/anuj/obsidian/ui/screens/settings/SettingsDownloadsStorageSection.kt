package com.anuj.obsidian.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.AvailableAudioQualities
import com.anuj.obsidian.data.model.AppSettings
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.components.FormatChip
import com.anuj.obsidian.ui.theme.DarkSurface
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.DarkSurfaceCard
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.TextMuted
import com.anuj.obsidian.ui.theme.TextPrimary
import com.anuj.obsidian.ui.theme.TextSecondary
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.theme.getActiveThemePalette

/**
 * =========================================================================================
 * Settings Downloads & Storage Section
 * =========================================================================================
 * RESPONSIBILITY:
 * Audio download formats, custom output directory, naming template patterns, SAF linked folders,
 * stream quality selection, Vorbis/ID3 tag embedding, companion LRC, storage cleanup tool,
 * and JSON library backup export/import.
 * =========================================================================================
 */
fun LazyListScope.settingsDownloadsStorageSection(
    settings: AppSettings,
    viewModel: ObsidianViewModel,
    context: Context,
    onManageFolders: () -> Unit,
    downloadFolderPickerLauncher: ActivityResultLauncher<Uri?>,
    exportBackupLauncher: ActivityResultLauncher<String>,
    importBackupLauncher: ActivityResultLauncher<Array<String>>
) {
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Downloaded Audio Customization",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // Output Format Picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Output Audio Format",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val formatOptions = listOf("FLAC", "WAV", "MP3", "M4A")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        formatOptions.forEach { fmt ->
                            val isSel = settings.downloadFormat.equals(fmt, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.updateDownloadFormat(fmt) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) ObsidianTheme.colors.primary.copy(alpha = 0.2f) else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    if (isSel) ObsidianTheme.colors.primary else DarkSurfaceBorder
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = fmt,
                                        color = if (isSel) ObsidianTheme.colors.primary else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (fmt) {
                                            "FLAC" -> "24-bit"
                                            "WAV" -> "PCM"
                                            "MP3" -> "320k"
                                            else -> "AAC"
                                        },
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Filename Naming Pattern
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Filename Naming Format",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = settings.filenameFormat,
                        onValueChange = { viewModel.updateFilenamePattern(it) },
                        placeholder = { Text("{artist} - {title}", color = TextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ObsidianTheme.colors.primary,
                            unfocusedBorderColor = DarkSurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Preset Pattern Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf(
                            "{artist} - {title}",
                            "{title} - {artist}",
                            "{album} - {title}",
                            "{trackNumber}. {artist} - {title}"
                        )
                        items(presets) { preset ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                                modifier = Modifier.clickable { viewModel.updateFilenamePattern(preset) }
                            ) {
                                Text(
                                    text = preset,
                                    color = ObsidianTheme.colors.primary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Preview Text
                    val sampleName = settings.filenameFormat
                        .replace("{artist}", "The Weeknd")
                        .replace("{title}", "Blinding Lights")
                        .replace("{album}", "After Hours")
                        .replace("{trackNumber}", "01")
                    Text(
                        text = "Preview: $sampleName.${settings.downloadFormat.lowercase()}",
                        color = FlacHiResGold,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Custom Song Download Directory
                val context = LocalContext.current
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = FlacHiResGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Custom Song Download Directory",
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = if (settings.customDownloadFolderUri.isNotBlank())
                                "Target Folder: ${settings.customDownloadFolderName}\nDownloaded master FLAC & audio files save directly into this directory."
                            else
                                "Target: Default Music Directory (${settings.storagePath})\nTap below to set a custom folder (e.g., SD card or custom lossless library folder).",
                            color = if (settings.customDownloadFolderUri.isNotBlank()) FlacHiResGold else TextSecondary,
                            fontSize = 11.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { downloadFolderPickerLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FlacHiResGold,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (settings.customDownloadFolderUri.isNotBlank()) "Change Folder" else "Set Custom Folder",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }

                            if (settings.customDownloadFolderUri.isNotBlank()) {
                                Button(
                                    onClick = {
                                        viewModel.clearCustomDownloadFolder()
                                        Toast.makeText(context, "Reset to default Music directory", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DarkSurfaceCard,
                                        contentColor = TextSecondary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(text = "Reset", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (settings.customDownloadFolderUri.isBlank()) {
                            OutlinedTextField(
                                value = settings.storagePath,
                                onValueChange = { viewModel.updateStorageDirectory(it) },
                                label = { Text("Public Download Directory Path", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = DarkSurfaceCard,
                                    focusedBorderColor = FlacHiResGold,
                                    unfocusedLabelColor = TextSecondary,
                                    focusedLabelColor = FlacHiResGold,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                        }
                    }
                }

                // SAF Directory Linking & Zero Double Storage
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = ObsidianTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Music Storage Folder (SAF)",
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (settings.linkedMusicFolders.isEmpty()) {
                            Text(
                                text = "No directories linked. Grant SAF directory access to play songs directly from your folders.",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        } else {
                            Text(
                                text = "Linked Folders:",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            settings.linkedMusicFolders.forEach { folder ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ObsidianTheme.colors.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = folder.second,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        viewModel.removeLinkedMusicFolder(folder.first, context)
                                        Toast.makeText(context, "Folder removed", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onManageFolders() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ObsidianTheme.colors.primary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Folder",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Default Download Stream Quality
                Text(
                    text = "Default Download Stream Quality",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                AvailableAudioQualities.forEach { quality ->
                    val isSelected = settings.audioQualityId == quality.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ObsidianTheme.colors.primary.copy(alpha = 0.1f) else DarkSurface)
                            .border(
                                1.dp,
                                if (isSelected) ObsidianTheme.colors.primary else DarkSurfaceBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.updateQualitySetting(quality.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quality.title,
                                color = if (isSelected) ObsidianTheme.colors.primary else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = quality.description,
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FormatChip(
                            text = "${quality.bitDepth} / ${quality.sampleRate}",
                            color = if (quality.id.contains("flac")) FlacHiResGold else ObsidianTheme.colors.primary
                        )
                    }
                }

                // Vorbis Comments switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Embed Full Vorbis & ID3 Metadata",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Writes cover art and lyrics into the audio file so other players can read them",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = settings.embedLyrics,
                        onCheckedChange = { viewModel.toggleEmbedLyrics(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ObsidianTheme.colors.primary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Synced LRC generator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Generate Companion .LRC File",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Saves synchronized timestamped lyrics for desktop audiophile players",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = settings.generateLrcFile,
                        onCheckedChange = { viewModel.toggleGenerateLrc(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ObsidianTheme.colors.primary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Embed maximum resolution cover art
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Embed Max Resolution Cover Art",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Fetches and embeds uncompressed high-res album art",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = settings.embedMaxCoverArt,
                        onCheckedChange = { viewModel.toggleEmbedMaxCoverArt(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ObsidianTheme.colors.primary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Storage & Cache Cleanup Tool
                val activePalette = getActiveThemePalette(settings)
                StorageCleanupToolSection(
                    viewModel = viewModel,
                    context = context,
                    activePalette = activePalette
                )

                // Download History & Completed Queue Cleaning
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Completed Downloads Queue", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Clear completed history items from manager", color = TextSecondary, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.clearCompletedDownloads()
                                Toast.makeText(context, "Completed download items cleared", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Queue", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // BACKUP & RESTORE AUDIOPHILE PROFILE & PLAYLISTS
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LIBRARY BACKUP & EXPORT",
                            color = ObsidianTheme.colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Playlists, Liked Tracks & DSP Presets",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = ObsidianTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Export all your playlists, liked library tracks, custom DSP equalizer parameters, and theme preferences to a portable JSON backup file.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val defaultFileName = "obsidian_backup_${System.currentTimeMillis() / 1000}.json"
                            exportBackupLauncher.launch(defaultFileName)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianTheme.colors.primary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Export Backup",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurface,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Restore Backup",
                            tint = FlacHiResGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
