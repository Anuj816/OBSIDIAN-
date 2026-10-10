package com.anuj.obsidian.ui.components
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.anuj.obsidian.ui.animation.safePerform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlin.math.abs
import kotlin.math.sin

enum class TimelineDisplayMode {
    ELAPSED_TOTAL,
    REMAINING_TOTAL,
    ELAPSED_ONLY,
    INTEGRATED_OBSIDIAN
}

@Composable
fun WaveformSeekbar(
    currentPositionFlow: kotlinx.coroutines.flow.StateFlow<Float>,
    durationSeconds: Int,
    onSeek: (Float, Boolean) -> Unit,
    trackId: Any = "default",
    modifier: Modifier = Modifier,
    barCount: Int = 54,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.28f),
    displayMode: TimelineDisplayMode = TimelineDisplayMode.INTEGRATED_OBSIDIAN,
    onToggleDisplayMode: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val duration = if (durationSeconds > 0) durationSeconds.toFloat() else 180f
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionSec by remember { mutableFloatStateOf(0f) }
    var postSeekHoldPosition by remember { mutableStateOf<Float?>(null) }

    val rawCurrentPosState = currentPositionFlow.collectAsStateWithLifecycle(initialValue = 0f)
    
    val context = androidx.compose.ui.platform.LocalContext.current
    var extractedWaveform by remember { mutableStateOf<FloatArray?>(null) }
    
    LaunchedEffect(trackId, barCount) {
        extractedWaveform = null
        if (trackId is com.anuj.obsidian.data.model.DownloadedTrack) {
            val localPath = trackId.localFilePath
            val id = trackId.id
            if (localPath.isNotBlank() && !localPath.startsWith("http")) {
                val realAmps = com.anuj.obsidian.util.AudioWaveformExtractor.extractWaveformAmplitudes(
                    context, id, localPath, barCount
                )
                if (realAmps != null) {
                    extractedWaveform = realAmps
                }
            }
        }
    }
    
    val displayPositionSecProvider: () -> Float = {
        if (isDragging) {
            dragPositionSec
        } else if (postSeekHoldPosition != null) {
            postSeekHoldPosition ?: 0f
        } else {
            val raw = rawCurrentPosState.value
            if (raw.isFinite() && raw >= 0f) raw else 0f
        }
    }

    LaunchedEffect(postSeekHoldPosition) {
        if (postSeekHoldPosition != null) {
            kotlinx.coroutines.delay(1200)
            postSeekHoldPosition = null
        }
    }

    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { rawCurrentPosState.value }.collect { raw ->
            val safePos = if (raw.isFinite() && raw >= 0f) raw else 0f
            val target = postSeekHoldPosition
            if (target != null && kotlin.math.abs(safePos - target) < 1.5f) {
                postSeekHoldPosition = null
            }
        }
    }
    
    val safeCurrentSec by remember {
        derivedStateOf { displayPositionSecProvider().toInt().coerceAtLeast(0) }
    }
    val safeDurationSec = duration.toInt().coerceAtLeast(0)
    val safeRemainingSec by remember {
        derivedStateOf { (safeDurationSec - safeCurrentSec).coerceAtLeast(0) }
    }

    val playheadScaleState = animateFloatAsState(
        targetValue = if (isDragging) 1.55f else 1.0f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
        label = "playhead_scale"
    )

    // Generate deterministic bar heights based on trackId hash or use offline waveform
    val barAmplitudes = remember(trackId, barCount, extractedWaveform) {
        extractedWaveform ?: run {
            val seed = trackId.hashCode()
            FloatArray(barCount) { index ->
                val phase = index * 0.22f
                val base = kotlin.math.sin(phase + seed * 0.1f) * 0.38f + 0.55f
                val harmonic = kotlin.math.sin(index * 0.55f + seed * 0.05f) * 0.2f
                val spike = if (index % 6 == 0) 0.25f else if (index % 4 == 0) -0.15f else 0.05f
                (base + harmonic + spike).coerceIn(0.18f, 0.98f)
            }
        }
    }

    val formattedCurrentTime by remember {
        derivedStateOf { formatSecToTime(safeCurrentSec) }
    }
    val formattedDurationTime = remember(safeDurationSec) {
        formatSecToTime(safeDurationSec)
    }
    val formattedRemainingTime by remember {
        derivedStateOf { "-${formatSecToTime(safeRemainingSec)}" }
    }

    var localDisplayMode by remember { mutableStateOf(displayMode) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("waveform_seekbar")
    ) {
        // Integrated Obsidian Style Timeline Badge Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptic.safePerform(HapticFeedbackType.TextHandleMove)
                    localDisplayMode = when (localDisplayMode) {
                        TimelineDisplayMode.INTEGRATED_OBSIDIAN -> TimelineDisplayMode.REMAINING_TOTAL
                        TimelineDisplayMode.REMAINING_TOTAL -> TimelineDisplayMode.ELAPSED_TOTAL
                        TimelineDisplayMode.ELAPSED_TOTAL -> TimelineDisplayMode.ELAPSED_ONLY
                        TimelineDisplayMode.ELAPSED_ONLY -> TimelineDisplayMode.INTEGRATED_OBSIDIAN
                    }
                    onToggleDisplayMode?.invoke()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Integrated Pill: Current Elapsed / Scrub Indicator
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDragging) activeColor.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(
                    1.dp,
                    if (isDragging) activeColor else Color.White.copy(alpha = 0.22f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isDragging) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = activeColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = if (isDragging) "SEEK $formattedCurrentTime" else formattedCurrentTime,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isDragging) activeColor else Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Center Subtle Progress / Scrub Percentage Indicator
            if (isDragging) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.75f))
                ) {
                    val dragPercent = if (duration > 0f) ((dragPositionSec / duration).coerceIn(0f, 1f) * 100).toInt() else 0
                    Text(
                        text = "$dragPercent%",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = activeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                // Audio Time Indicator Mode Label
                Text(
                    text = when (localDisplayMode) {
                        TimelineDisplayMode.INTEGRATED_OBSIDIAN -> "TIMELINE"
                        TimelineDisplayMode.REMAINING_TOTAL -> "REMAINING"
                        TimelineDisplayMode.ELAPSED_TOTAL -> "STANDARD"
                        TimelineDisplayMode.ELAPSED_ONLY -> "ELAPSED"
                    },
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.2.sp
                )
            }

            // Right Integrated Pill: Total / Remaining Duration
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val rightText = when (localDisplayMode) {
                        TimelineDisplayMode.REMAINING_TOTAL -> formattedRemainingTime
                        TimelineDisplayMode.ELAPSED_ONLY -> formattedDurationTime
                        else -> formattedDurationTime
                    }
                    Text(
                        text = rightText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.95f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val infiniteWave = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave_anim")
        val wavePhase by infiniteWave.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(durationMillis = 2400, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "wave_phase"
        )

        // Interactive Waveform Canvas with Integrated Obsidian Glow Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .pointerInput(trackId, durationSeconds) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val canvasWidth = size.width.toFloat()
                        if (canvasWidth <= 0f || !canvasWidth.isFinite()) return@awaitEachGesture

                        val safeDuration = if (durationSeconds > 0) durationSeconds.toFloat() else 180f
                        val downX = down.position.x
                        val initialFraction = if (canvasWidth > 0f && downX.isFinite()) {
                            (downX / canvasWidth).coerceIn(0f, 1f)
                        } else 0f

                        var isDragInitiated = false
                        val currentPointerId = down.id
                        val touchSlop = viewConfiguration.touchSlop
                        var totalDragDistance = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == currentPointerId } ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                break
                            }

                            val deltaX = change.positionChange().x
                            if (!isDragInitiated) {
                                totalDragDistance += deltaX
                                if (abs(totalDragDistance) > touchSlop) {
                                    isDragInitiated = true
                                    isDragging = true
                                    haptic.safePerform(HapticFeedbackType.LongPress)
                                    val currentX = change.position.x
                                    if (currentX.isFinite() && canvasWidth > 0f) {
                                        val frac = (currentX / canvasWidth).coerceIn(0f, 1f)
                                        val targetPos = (frac * safeDuration).coerceIn(0f, safeDuration)
                                        dragPositionSec = targetPos
                                        onSeek(targetPos, true)
                                    }
                                    change.consume()
                                }
                            } else {
                                change.consume()
                                val currentX = change.position.x
                                if (currentX.isFinite() && canvasWidth > 0f) {
                                    val frac = (currentX / canvasWidth).coerceIn(0f, 1f)
                                    val targetPos = (frac * safeDuration).coerceIn(0f, safeDuration)
                                    val oldPos = dragPositionSec
                                    dragPositionSec = targetPos
                                    if (abs(targetPos - oldPos) >= 0.5f) {
                                        onSeek(targetPos, true)
                                    }
                                }
                            }
                        }

                        if (isDragInitiated) {
                            isDragging = false
                            haptic.safePerform(HapticFeedbackType.TextHandleMove)
                            if (dragPositionSec.isFinite() && safeDuration > 0f) {
                                val target = dragPositionSec.coerceIn(0f, safeDuration)
                                postSeekHoldPosition = target
                                onSeek(target, false)
                            }
                        } else {
                            // Tap event cleanly processed without collision
                            down.consume()
                            haptic.safePerform(HapticFeedbackType.TextHandleMove)
                            val seekTarget = (initialFraction * safeDuration).let {
                                if (it.isFinite() && safeDuration > 0f) it.coerceIn(0f, safeDuration) else 0f
                            }
                            postSeekHoldPosition = seekTarget
                            onSeek(seekTarget, false)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val totalBars = barAmplitudes.size
                if (totalBars == 0) return@Canvas
                val barSpacing = 2.8.dp.toPx()
                val totalSpacing = barSpacing * (totalBars - 1)
                val barWidth = ((canvasWidth - totalSpacing) / totalBars).coerceAtLeast(2.dp.toPx())
                
                val currentPos = displayPositionSecProvider()
                val progressFraction = if (duration > 0f && currentPos.isFinite()) {
                    (currentPos / duration).coerceIn(0f, 1f)
                } else 0f

                val activeBarIndex = (progressFraction * totalBars).toInt().coerceIn(0, totalBars - 1)
                val playheadX = (canvasWidth * progressFraction).coerceIn(0f, canvasWidth)

                // Background subtle track line
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(0f, canvasHeight / 2f),
                    end = Offset(canvasWidth, canvasHeight / 2f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val playheadScale = playheadScaleState.value
                val barCornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                val activeColor85 = activeColor.copy(alpha = 0.85f)

                for (i in 0 until totalBars) {
                    val x = i * (barWidth + barSpacing)
                    val baseAmp = barAmplitudes[i]
                    val isBarActive = i <= activeBarIndex
                    
                    // Liquid sine-wave dynamic undulation on active bars
                    val waveModulation = if (isBarActive) {
                        0.06f * kotlin.math.sin((wavePhase + (i * 0.28f)).toDouble()).toFloat()
                    } else 0f
                    
                    val amplitude = (baseAmp + waveModulation).coerceIn(0.10f, 1.0f)
                    val baseHeight = (canvasHeight * amplitude).coerceAtLeast(4.dp.toPx())
                    val barHeight = if (i == activeBarIndex && isDragging) (baseHeight * playheadScale).coerceAtMost(canvasHeight) else baseHeight
                    val y = (canvasHeight - barHeight) / 2f

                    val color = if (isBarActive) {
                        if (i == activeBarIndex) activeColor else activeColor85
                    } else {
                        inactiveColor
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = barCornerRadius
                    )
                }

                // Obsidian Style Playhead Scrubber Dot with Multi-Layer Glow Halo
                val scrubberRadius = (3.8.dp.toPx() * playheadScale)
                // Outer wide ambient glow halo
                drawCircle(
                    color = activeColor.copy(alpha = if (isDragging) 0.55f else 0.24f),
                    radius = scrubberRadius * 3.0f,
                    center = Offset(playheadX, canvasHeight / 2f)
                )
                // Mid luminous halo ring
                drawCircle(
                    color = activeColor.copy(alpha = if (isDragging) 0.80f else 0.45f),
                    radius = scrubberRadius * 1.6f,
                    center = Offset(playheadX, canvasHeight / 2f)
                )
                // Center core playhead dot
                drawCircle(
                    color = Color.White,
                    radius = scrubberRadius,
                    center = Offset(playheadX, canvasHeight / 2f)
                )
            }
        }
    }
}

