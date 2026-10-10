package com.anuj.obsidian.ui.components

import android.media.MediaPlayer
import android.widget.Toast
import com.anuj.obsidian.ui.animation.safePerform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.util.AudioCutterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimmerSheet(
    track: DownloadedTrack?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val appColors = ObsidianTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val durationMs = ((track?.durationSeconds ?: 180) * 1000L).toFloat().coerceAtLeast(10000f)
    var startPosNorm by remember { mutableFloatStateOf(0.15f) } // 15% into track
    var endPosNorm by remember { mutableFloatStateOf(0.45f) }   // 45% into track

    var fadeInEnabled by remember { mutableStateOf(true) }
    var fadeOutEnabled by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf("ringtone") } // ringtone, alarm, notification, mp3

    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableFloatStateOf(0f) }

    // Preview Player State
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPosNorm by remember { mutableFloatStateOf(0.15f) }
    val mediaPlayer = remember { MediaPlayer() }
    var isPlayerPrepared by remember { mutableStateOf(false) }

    val startTimeMs = (startPosNorm * durationMs).toLong()
    val endTimeMs = (endPosNorm * durationMs).toLong()
    val sliceDurationSec = ((endTimeMs - startTimeMs) / 1000L).coerceAtLeast(1L)

    fun formatTimeMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(java.util.Locale.US, "%d:%02d", min, sec)
    }

    // Initialize Preview MediaPlayer in background
    LaunchedEffect(track) {
        if (track == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val source = AudioCutterEngine.resolveSourceFile(context, track)
            if (source != null && source.exists()) {
                try {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(source.absolutePath)
                    mediaPlayer.prepare()
                    withContext(Dispatchers.Main) {
                        isPlayerPrepared = true
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("AudioTrimmerSheet", "Preview player init failed: ${e.message}")
                }
            }
        }
    }

    // Loop / Monitor Preview Playback
    LaunchedEffect(isPreviewPlaying, startTimeMs, endTimeMs) {
        if (!isPreviewPlaying) return@LaunchedEffect
        while (isActive && isPreviewPlaying) {
            try {
                if (isPlayerPrepared && mediaPlayer.isPlaying) {
                    val currentPos = mediaPlayer.currentPosition.toLong()
                    previewPosNorm = (currentPos.toFloat() / durationMs).coerceIn(0f, 1f)
                    if (currentPos >= endTimeMs || currentPos < startTimeMs - 500) {
                        // Loop back to start of trim
                        mediaPlayer.seekTo(startTimeMs.toInt())
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Ignore transient player state errors
            }
            delay(50)
        }
    }

    // Cleanup player on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) {
                // Safe cleanup
            }
        }
    }

    fun togglePreviewPlay() {
        if (!isPlayerPrepared) {
            Toast.makeText(context, "Preparing audio preview...", Toast.LENGTH_SHORT).show()
            return
        }
        haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        try {
            if (isPreviewPlaying) {
                mediaPlayer.pause()
                isPreviewPlaying = false
            } else {
                mediaPlayer.seekTo(startTimeMs.toInt())
                mediaPlayer.start()
                isPreviewPlaying = true
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioTrimmerSheet", "Preview toggle error: ${e.message}")
        }
    }

    fun stepStartTime(deltaMs: Long) {
        val newStartMs = (startTimeMs + deltaMs).coerceIn(0L, endTimeMs - 1000L)
        startPosNorm = newStartMs / durationMs
        if (isPreviewPlaying) {
            try { 
                mediaPlayer.seekTo(newStartMs.toInt()) 
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioTrimmerSheet.stepStartTime.seekTo")
            }
        }
    }

    fun stepEndTime(deltaMs: Long) {
        val newEndMs = (endTimeMs + deltaMs).coerceIn(startTimeMs + 1000L, durationMs.toLong())
        endPosNorm = newEndMs / durationMs
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isPreviewPlaying) {
                try { 
                    mediaPlayer.stop() 
                } catch (e: Exception) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioTrimmerSheet.onDismiss.stop")
                }
            }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = appColors.surfaceElevated,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = null,
        modifier = Modifier.testTag("audio_trimmer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF8800), Color(0xFFFF0055))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Audio Trimmer",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Audio Cutter & Ringtone Maker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = appColors.textPrimary
                        )
                        Text(
                            text = track?.title ?: "Select Range to Cut",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors.textSecondary,
                            maxLines = 1
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = appColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Waveform Canvas with interactive drag handles & live playhead
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surfaceElevated),
                border = BorderStroke(1.dp, appColors.surfaceBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val activeColor = appColors.primary
                    val inactiveColor = appColors.textSecondary.copy(alpha = 0.25f)
                    val handleColor = Color(0xFF00E676)
                    val playheadColor = Color(0xFFFFCC00)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val width = size.width.toFloat()
                                    val touchX = (offset.x / width).coerceIn(0f, 1f)
                                    val dStart = kotlin.math.abs(touchX - startPosNorm)
                                    val dEnd = kotlin.math.abs(touchX - endPosNorm)
                                    if (dStart < dEnd) {
                                        startPosNorm = touchX.coerceIn(0f, endPosNorm - 0.05f)
                                        if (isPreviewPlaying) {
                                            try { 
                                                mediaPlayer.seekTo((startPosNorm * durationMs).toInt()) 
                                            } catch (e: Exception) {
                                                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioTrimmerSheet.tapGestures.seekTo")
                                            }
                                        }
                                    } else {
                                        endPosNorm = touchX.coerceIn(startPosNorm + 0.05f, 1f)
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    val width = size.width.toFloat()
                                    val touchX = (change.position.x / width).coerceIn(0f, 1f)
                                    if (kotlin.math.abs(touchX - startPosNorm) < kotlin.math.abs(touchX - endPosNorm)) {
                                        startPosNorm = touchX.coerceIn(0f, endPosNorm - 0.05f)
                                        if (isPreviewPlaying) {
                                            try { 
                                                mediaPlayer.seekTo((startPosNorm * durationMs).toInt()) 
                                            } catch (e: Exception) {
                                                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioTrimmerSheet.dragGestures.seekTo")
                                            }
                                        }
                                    } else {
                                        endPosNorm = touchX.coerceIn(startPosNorm + 0.05f, 1f)
                                    }
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        val barsCount = 60
                        val barW = w / barsCount

                        for (i in 0 until barsCount) {
                            val normPos = i.toFloat() / barsCount
                            val barH = (h * (0.25f + 0.75f * sin((i * 0.45f).toDouble()).toFloat())).coerceAtLeast(6f)
                            val x = i * barW

                            val isSelected = normPos in startPosNorm..endPosNorm
                            drawRoundRect(
                                color = if (isSelected) activeColor else inactiveColor,
                                topLeft = Offset(x, (h - barH) / 2f),
                                size = Size(barW * 0.7f, barH),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }

                        // Drag Handles
                        val startX = startPosNorm * w
                        val endX = endPosNorm * w

                        // Start Cut Handle
                        drawLine(
                            color = handleColor,
                            start = Offset(startX, 0f),
                            end = Offset(startX, h),
                            strokeWidth = 3.dp.toPx()
                        )
                        drawCircle(color = handleColor, radius = 7.dp.toPx(), center = Offset(startX, h / 2f))

                        // End Cut Handle
                        drawLine(
                            color = handleColor,
                            start = Offset(endX, 0f),
                            end = Offset(endX, h),
                            strokeWidth = 3.dp.toPx()
                        )
                        drawCircle(color = handleColor, radius = 7.dp.toPx(), center = Offset(endX, h / 2f))

                        // Live Playback Head
                        if (isPreviewPlaying) {
                            val playheadX = (previewPosNorm * w).coerceIn(startX, endX)
                            drawLine(
                                color = playheadColor,
                                start = Offset(playheadX, 0f),
                                end = Offset(playheadX, h),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time range & Stepper Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start control
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Start: ${formatTimeMs(startTimeMs)}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        fontSize = 12.sp
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        FilledTonalButton(
                            onClick = { stepStartTime(-1000L) },
                            modifier = Modifier.size(width = 44.dp, height = 28.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("-1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { stepStartTime(1000L) },
                            modifier = Modifier.size(width = 44.dp, height = 28.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("+1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Play / Pause Preview Button
                ElevatedButton(
                    onClick = { togglePreviewPlay() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = if (isPreviewPlaying) Color(0xFFFF0055) else appColors.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPreviewPlaying) "Pause Preview" else "Play Preview",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPreviewPlaying) "Pause" else "Preview",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // End control
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "End: ${formatTimeMs(endTimeMs)} (${sliceDurationSec}s)",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        fontSize = 12.sp
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        FilledTonalButton(
                            onClick = { stepEndTime(-1000L) },
                            modifier = Modifier.size(width = 44.dp, height = 28.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("-1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { stepEndTime(1000L) },
                            modifier = Modifier.size(width = 44.dp, height = 28.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("+1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preset Selection
            Text(
                text = "Apply Output As",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    Triple("ringtone", "Ringtone", Icons.Default.NotificationsActive),
                    Triple("alarm", "Alarm", Icons.Default.Alarm),
                    Triple("notification", "Notice", Icons.Default.Notifications),
                    Triple("mp3", "Export WAV", Icons.Default.MusicNote)
                )

                presets.forEach { (id, label, icon) ->
                    val isSelected = selectedPreset == id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) appColors.primary.copy(alpha = 0.2f) else appColors.surfaceElevated,
                        border = BorderStroke(1.dp, if (isSelected) appColors.primary else appColors.surfaceBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                selectedPreset = id
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) appColors.primary else appColors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) appColors.primary else appColors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fade-in/Fade-out controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Smooth Fade In/Out (1.5s)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.textPrimary
                )
                Switch(
                    checked = fadeInEnabled,
                    onCheckedChange = {
                        fadeInEnabled = it
                        fadeOutEnabled = it
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = appColors.primary)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Save Button
            ElevatedButton(
                onClick = {
                    if (track == null || isProcessing) return@ElevatedButton
                    haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (isPreviewPlaying) {
                        try { 
                            mediaPlayer.pause()
                            isPreviewPlaying = false 
                        } catch (e: Exception) {
                            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioTrimmerSheet.saveClick.pause")
                        }
                    }
                    isProcessing = true
                    scope.launch {
                        val result = AudioCutterEngine.cutAndSaveAudio(
                            context = context,
                            track = track,
                            startTimeMs = startTimeMs,
                            endTimeMs = endTimeMs,
                            preset = selectedPreset,
                            fadeIn = fadeInEnabled,
                            fadeOut = fadeOutEnabled,
                            onProgress = { progress -> processingProgress = progress }
                        )
                        isProcessing = false
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        if (result.success) {
                            onDismiss()
                        }
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = appColors.primary,
                    contentColor = Color.White
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Cutting Audio (${(processingProgress * 100).toInt()}%)...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                } else {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cut & Save (${sliceDurationSec}s)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
