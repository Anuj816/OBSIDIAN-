package com.anuj.obsidian.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.anuj.obsidian.ui.animation.safePerform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.anuj.obsidian.ui.theme.AppThemePalette
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Audiophile Rotary Volume Controller with real-time Android System AudioManager binding.
 * Ideal for devices with broken/worn hardware volume buttons.
 */
@Composable
fun AndroidSystemVolumeController(
    appColors: AppThemePalette,
    modifier: Modifier = Modifier,
    showRotaryDial: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f) }
    val minSystemVolume = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC).toFloat()
        } else {
            0f
        }
    }

    var currentVolumeLevel by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }

    // Register receiver to track volume key events and bluetooth volume changes in real-time
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION" || intent?.action == "android.media.RINGER_MODE_CHANGED") {
                    try {
                        currentVolumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AndroidSystemVolumeController.onReceive - attempt")
            }
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION").apply {
            addAction("android.media.RINGER_MODE_CHANGED")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AndroidSystemVolumeController.onReceive - attempt")
            }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AndroidSystemVolumeController.onReceive - attempt")
            }
        }
    }

    fun applySystemVolume(newVol: Float) {
        val clamped = newVol.coerceIn(minSystemVolume, maxSystemVolume)
        currentVolumeLevel = clamped
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped.roundToInt(), AudioManager.FLAG_SHOW_UI)
        } catch (_: Exception) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped.roundToInt(), 0)
            } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AndroidSystemVolumeController.applySystemVolume - attempt")
            }
        }
    }

    val volumeFraction = (currentVolumeLevel - minSystemVolume) / (maxSystemVolume - minSystemVolume)
    val percentageInt = (volumeFraction * 100).toInt().coerceIn(0, 100)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.surfaceCard)
            .border(1.dp, appColors.surfaceBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        percentageInt == 0 -> Icons.AutoMirrored.Filled.VolumeMute
                        percentageInt < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = "Volume Icon",
                    tint = appColors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ANDROID MEDIA VOLUME",
                    color = appColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Text(
                text = "$percentageInt%",
                color = appColors.primary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (showRotaryDial) {
            // Circular Rotary Dial Knob
            RotaryVolumeDial(
                percentage = volumeFraction,
                onPercentageChange = { fraction ->
                    val target = minSystemVolume + fraction * (maxSystemVolume - minSystemVolume)
                    applySystemVolume(target)
                },
                appColors = appColors,
                modifier = Modifier.size(170.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Linear Slider
        Slider(
            value = currentVolumeLevel,
            onValueChange = { vol ->
                applySystemVolume(vol)
            },
            valueRange = minSystemVolume..maxSystemVolume,
            colors = SliderDefaults.colors(
                thumbColor = appColors.primary,
                activeTrackColor = appColors.primary,
                inactiveTrackColor = appColors.surfaceBorder.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("android_volume_slider")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Preset Steps Buttons (-1 Step, +1 Step, Mute, Max)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickVolumeButton(
                label = "MUTE",
                isSelected = percentageInt == 0,
                onClick = {
                    haptic.safePerform(HapticFeedbackType.LongPress)
                    applySystemVolume(minSystemVolume)
                },
                appColors = appColors,
                modifier = Modifier.weight(1f)
            )
            QuickVolumeButton(
                label = "- 1",
                isSelected = false,
                onClick = {
                    haptic.safePerform(HapticFeedbackType.TextHandleMove)
                    applySystemVolume(currentVolumeLevel - 1f)
                },
                appColors = appColors,
                modifier = Modifier.weight(1f)
            )
            QuickVolumeButton(
                label = "+ 1",
                isSelected = false,
                onClick = {
                    haptic.safePerform(HapticFeedbackType.TextHandleMove)
                    applySystemVolume(currentVolumeLevel + 1f)
                },
                appColors = appColors,
                modifier = Modifier.weight(1f)
            )
            QuickVolumeButton(
                label = "MAX",
                isSelected = percentageInt >= 98,
                onClick = {
                    haptic.safePerform(HapticFeedbackType.LongPress)
                    applySystemVolume(maxSystemVolume)
                },
                appColors = appColors,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Interactive 270-degree Rotary Dial Knob with smooth touch angle detection
 */
@Composable
fun RotaryVolumeDial(
    percentage: Float,
    onPercentageChange: (Float) -> Unit,
    appColors: AppThemePalette,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = appColors.primary
    val trackBgColor = appColors.surfaceBorder
    val activeTrackBrush = Brush.sweepGradient(
        colors = listOf(
            primaryColor.copy(alpha = 0.6f),
            primaryColor,
            appColors.secondary
        )
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val touchPos = change.position
                    val angleRad = atan2(touchPos.y - center.y, touchPos.x - center.x)
                    var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                    if (angleDeg < 0) angleDeg += 360f

                    // Rotary angle from 135 deg (0%) to 405 deg (100%) - sweep of 270 deg
                    // 135 deg = bottom-left, 270 deg = top, 45 deg / 405 deg = bottom-right
                    val normalizedAngle = if (angleDeg >= 135f) {
                        angleDeg - 135f
                    } else {
                        angleDeg + (360f - 135f)
                    }

                    if (normalizedAngle in 0f..270f) {
                        val fraction = (normalizedAngle / 270f).coerceIn(0f, 1f)
                        onPercentageChange(fraction)
                        haptic.safePerform(HapticFeedbackType.TextHandleMove)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val strokeWidth = 14.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            // Inactive arc (270 degrees starting from 135 deg)
            drawArc(
                color = trackBgColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active volume arc
            val activeSweep = (percentage * 270f).coerceIn(0.1f, 270f)
            drawArc(
                brush = activeTrackBrush,
                startAngle = 135f,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Knob Center Circle
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val innerRadius = diameter / 2f - strokeWidth * 0.8f
            drawCircle(
                color = appColors.surfaceElevated,
                radius = innerRadius,
                center = centerOffset
            )
            drawCircle(
                color = trackBgColor,
                radius = innerRadius,
                center = centerOffset,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Indicator indicator dot on dial perimeter
            val currentAngleRad = Math.toRadians((135f + activeSweep).toDouble())
            val dotRadius = diameter / 2f
            val dotX = centerOffset.x + (dotRadius * cos(currentAngleRad)).toFloat()
            val dotY = centerOffset.y + (dotRadius * sin(currentAngleRad)).toFloat()

            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }

        // Center Volume Percentage & Label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(percentage * 100).toInt()}%",
                color = appColors.textPrimary,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "VOLUME",
                color = appColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun QuickVolumeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    appColors: AppThemePalette,
    modifier: Modifier = Modifier
) {
    val isLightPrimary = appColors.primary.red * 0.299f + appColors.primary.green * 0.587f + appColors.primary.blue * 0.114f > 0.5f
    val onPrimaryColor = if (isLightPrimary) Color.Black else Color.White

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) appColors.primary else appColors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) appColors.primary else appColors.surfaceBorder
        ),
        modifier = modifier.height(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (isSelected) onPrimaryColor else appColors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Floating Obsidian Volume Dialog overlay.
 */
@Composable
fun AndroidVolumeDialog(
    onDismiss: () -> Unit
) {
    val appColors = ObsidianTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HARDWARE VOLUME OVERRIDE",
                        color = appColors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AndroidSystemVolumeController(
                    appColors = appColors,
                    showRotaryDial = true
                )
            }
        }
    }
}
