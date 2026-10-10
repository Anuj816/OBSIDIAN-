package com.anuj.obsidian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.controller.BatchEditFields
import com.anuj.obsidian.ui.controller.BatchTagController
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchTagEditorBottomSheet(
    tracks: List<DownloadedTrack>,
    batchTagController: BatchTagController,
    onDismiss: () -> Unit
) {
    val appColors = ObsidianTheme.colors
    val scope = rememberCoroutineScope()
    val progress by batchTagController.progress.collectAsStateWithLifecycle()

    var artistText by remember { mutableStateOf("") }
    var albumText by remember { mutableStateOf("") }
    var genreText by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }

    var updateArtist by remember { mutableStateOf(false) }
    var updateAlbum by remember { mutableStateOf(false) }
    var updateGenre by remember { mutableStateOf(false) }
    var updateYear by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!progress.isRunning) {
                batchTagController.resetProgress()
                onDismiss()
            }
        },
        containerColor = appColors.surfaceElevated,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = appColors.textMuted.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Batch Tag Editor",
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Editing ${tracks.size} selected tracks",
                        color = appColors.textMuted,
                        fontSize = 13.sp
                    )
                }
                IconButton(
                    onClick = {
                        if (!progress.isRunning) {
                            batchTagController.resetProgress()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.testTag("batch_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = appColors.textMuted)
                }
            }

            if (progress.isRunning || progress.isDone) {
                // Progress view
                Surface(
                    color = appColors.surfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (progress.isDone) "Batch Tagging Complete" else "Tagging in Progress...",
                            color = if (progress.isDone) appColors.primary else appColors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        val progressFraction = if (progress.totalCount > 0) {
                            progress.completedCount.toFloat() / progress.totalCount.toFloat()
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = appColors.primary,
                            trackColor = appColors.surfaceBorder
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Processed: ${progress.completedCount}/${progress.totalCount}",
                                color = appColors.textMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                "Success: ${progress.succeededIds.size} | Failed: ${progress.failedIds.size}",
                                color = if (progress.failedIds.isNotEmpty()) Color(0xFFEF4444) else appColors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (progress.currentTrackTitle.isNotBlank()) {
                            Text(
                                "Current: ${progress.currentTrackTitle}",
                                color = appColors.textMuted,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }

                        if (progress.isDone) {
                            Button(
                                onClick = {
                                    batchTagController.resetProgress()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                                modifier = Modifier.fillMaxWidth().testTag("batch_done_btn")
                            ) {
                                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Editor Form
                // Artist Field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = updateArtist,
                        onCheckedChange = { updateArtist = it },
                        colors = CheckboxDefaults.colors(checkedColor = appColors.primary)
                    )
                    OutlinedTextField(
                        value = artistText,
                        onValueChange = {
                            artistText = it
                            updateArtist = true
                        },
                        label = { Text("Artist") },
                        placeholder = { Text("Leave unchanged or enter new artist") },
                        enabled = true,
                        modifier = Modifier.fillMaxWidth().testTag("batch_artist_field"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = appColors.primary,
                            unfocusedBorderColor = appColors.surfaceBorder,
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary
                        )
                    )
                }

                // Album Field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = updateAlbum,
                        onCheckedChange = { updateAlbum = it },
                        colors = CheckboxDefaults.colors(checkedColor = appColors.primary)
                    )
                    OutlinedTextField(
                        value = albumText,
                        onValueChange = {
                            albumText = it
                            updateAlbum = true
                        },
                        label = { Text("Album") },
                        placeholder = { Text("Leave unchanged or enter new album") },
                        modifier = Modifier.fillMaxWidth().testTag("batch_album_field"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = appColors.primary,
                            unfocusedBorderColor = appColors.surfaceBorder,
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary
                        )
                    )
                }

                // Genre Field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = updateGenre,
                        onCheckedChange = { updateGenre = it },
                        colors = CheckboxDefaults.colors(checkedColor = appColors.primary)
                    )
                    OutlinedTextField(
                        value = genreText,
                        onValueChange = {
                            genreText = it
                            updateGenre = true
                        },
                        label = { Text("Genre") },
                        placeholder = { Text("e.g. Lossless Master, Rock, Electronic") },
                        modifier = Modifier.fillMaxWidth().testTag("batch_genre_field"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = appColors.primary,
                            unfocusedBorderColor = appColors.surfaceBorder,
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary
                        )
                    )
                }

                // Year Field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = updateYear,
                        onCheckedChange = { updateYear = it },
                        colors = CheckboxDefaults.colors(checkedColor = appColors.primary)
                    )
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = {
                            yearText = it
                            updateYear = true
                        },
                        label = { Text("Year") },
                        placeholder = { Text("e.g. 2024") },
                        modifier = Modifier.fillMaxWidth().testTag("batch_year_field"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = appColors.primary,
                            unfocusedBorderColor = appColors.surfaceBorder,
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary
                        )
                    )
                }

                Button(
                    onClick = {
                        val edits = BatchEditFields(
                            artist = if (updateArtist) artistText else null,
                            album = if (updateAlbum) albumText else null,
                            genre = if (updateGenre) genreText else null,
                            releaseYear = if (updateYear) yearText else null
                        )
                        scope.launch {
                            batchTagController.executeBatchTagUpdate(tracks, edits)
                        }
                    },
                    enabled = updateArtist || updateAlbum || updateGenre || updateYear,
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("batch_apply_btn")
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply to ${tracks.size} Tracks", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
