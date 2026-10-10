package com.anuj.obsidian.ui.components

import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.anuj.obsidian.ui.visualizer.HardwareVisualizerBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class CanvasPresetInfo(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String
)

val DYNAMIC_CANVAS_PRESETS = listOf(
    CanvasPresetInfo("cosmic_nebula", "Cosmic Nebula", "🌌", "Swirling deep-space stardust and glowing galaxy nebulae pulsing with audio"),
    CanvasPresetInfo("liquid_aurora", "Liquid Aurora", "🌊", "Fluid flowing color ribbons dancing to harmonic audio frequencies"),
    CanvasPresetInfo("vinyl_turntable", "Vinyl Turntable Drift", "💿", "Realistic 33-RPM spinning vinyl disc with dynamic grooves and reflection"),
    CanvasPresetInfo("cyberpunk_grid", "Cyberpunk Grid", "⚡", "Retrowave neon perspective grid with FFT audio reactive terrain"),
    CanvasPresetInfo("vacuum_tube", "Studio Tube Glow", "💡", "Warm analog valve tube thermionic filaments reacting to volume"),
    CanvasPresetInfo("audio_reactive_waves", "Harmonic Waves", "🎚️", "Multi-layered flowing sine ribbons responding to live frequencies")
)

@Composable
fun DynamicCanvasBackground(
    presetId: String,
    primaryColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    alpha: Float = 0.85f
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dynamic_canvas_anim")
    val timeState = if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(durationMillis = 60_000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "canvas_time"
        )
    } else {
        null
    }
    val spinState = if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(durationMillis = 3600, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "canvas_spin"
        )
    } else {
        null
    }
    
    val reusablePath = remember { Path() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentTime = timeState?.value ?: 0f
            val currentSpin = spinState?.value ?: 0f
            val snapshot = HardwareVisualizerBuffer.currentSnapshot
            val bass = if (isPlaying) snapshot.bassEnergy else 0.05f
            val mid = if (isPlaying) snapshot.midEnergy else 0.05f
            val treble = if (isPlaying) snapshot.trebleEnergy else 0.05f
            val fft = snapshot.fftMagnitudes

            when (presetId) {
                "liquid_aurora" -> {
                    drawLiquidAurora(currentTime, primaryColor, bass, mid, treble, alpha, reusablePath)
                }
                "vinyl_turntable" -> {
                    drawVinylTurntable(currentSpin, primaryColor, bass, alpha)
                }
                "cyberpunk_grid" -> {
                    drawCyberpunkGrid(currentTime, primaryColor, fft, bass, alpha, reusablePath)
                }
                "vacuum_tube" -> {
                    drawVacuumTubeGlow(currentTime, primaryColor, bass, treble, alpha, reusablePath)
                }
                "audio_reactive_waves" -> {
                    drawHarmonicWaves(currentTime, primaryColor, fft, bass, alpha, reusablePath)
                }
                else -> { // cosmic_nebula default
                    drawCosmicNebula(currentTime, primaryColor, bass, mid, alpha)
                }
            }
        }
    }
}

private fun DrawScope.drawCosmicNebula(
    time: Float,
    primaryColor: Color,
    bass: Float,
    mid: Float,
    alpha: Float
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = size.width.coerceAtLeast(size.height) * 0.85f

    // Nebula radial glowing clouds
    val nebulaRadius1 = maxR * (0.6f + bass * 0.25f)
    val nebulaRadius2 = maxR * (0.45f + mid * 0.2f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.45f * alpha),
                Color(0xFF6200EA).copy(alpha = 0.25f * alpha),
                Color.Transparent
            ),
            center = Offset(cx + sin(time * 0.8f) * 60f, cy + cos(time * 0.6f) * 60f),
            radius = nebulaRadius1
        ),
        radius = nebulaRadius1
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF00E5FF).copy(alpha = 0.35f * alpha),
                Color(0xFF00B0FF).copy(alpha = 0.15f * alpha),
                Color.Transparent
            ),
            center = Offset(cx - cos(time * 0.7f) * 80f, cy - sin(time * 0.5f) * 80f),
            radius = nebulaRadius2
        ),
        radius = nebulaRadius2
    )

    // Cosmic star particles
    val starCount = 65
    for (i in 0 until starCount) {
        val angle = (i * 137.5f * PI / 180f) + (time * 0.05f * (1f + (i % 3) * 0.5f))
        val dist = (size.width * 0.45f) * ((i.toFloat() / starCount) + 0.1f)
        val px = cx + (cos(angle) * dist).toFloat()
        val py = cy + (sin(angle) * dist).toFloat()

        val starAlpha = ((sin(time * 2f + i) * 0.3f + 0.7f) * alpha * (0.5f + bass * 0.5f)).coerceIn(0f, 1f)
        val starSize = (1.5f + (i % 4) * 0.8f + bass * 2f)

        drawCircle(
            color = if (i % 2 == 0) Color.White.copy(alpha = starAlpha) else primaryColor.copy(alpha = starAlpha),
            radius = starSize,
            center = Offset(px, py)
        )
    }
}

private val AuroraColors = longArrayOf(
    0x00E676,
    0x00B0FF,
    0xAA00FF
)

private fun DrawScope.drawLiquidAurora(
    time: Float,
    primaryColor: Color,
    bass: Float,
    mid: Float,
    treble: Float,
    alpha: Float,
    reusablePath: Path
) {
    val h = size.height
    val w = size.width

    val primAlpha = 0.35f * alpha
    val gAlpha = 0.28f * alpha
    val bAlpha = 0.25f * alpha
    val pAlpha = 0.30f * alpha

    for (idx in 0..3) {
        val color = when (idx) {
            0 -> primaryColor.copy(alpha = primAlpha)
            1 -> Color(0xFF00E676).copy(alpha = gAlpha)
            2 -> Color(0xFF00B0FF).copy(alpha = bAlpha)
            else -> Color(0xFFAA00FF).copy(alpha = pAlpha)
        }
        reusablePath.reset()
        val speed = 0.8f + idx * 0.3f
        val yOffset = h * (0.35f + idx * 0.12f)
        val amplitude = 50f + (bass * 45f) + (idx * 15f)

        reusablePath.moveTo(0f, h)
        reusablePath.lineTo(0f, yOffset)

        val steps = 30
        for (i in 0..steps) {
            val x = w * (i.toFloat() / steps)
            val wavePhase = (x / w) * 3 * PI + time * speed + (idx * 1.5)
            val y = yOffset + sin(wavePhase).toFloat() * amplitude + cos(wavePhase * 0.5f).toFloat() * (amplitude * 0.4f)
            reusablePath.lineTo(x, y)
        }

        reusablePath.lineTo(w, h)
        reusablePath.close()

        drawPath(
            path = reusablePath,
            brush = Brush.verticalGradient(
                colors = listOf(color, Color.Transparent),
                startY = yOffset - amplitude,
                endY = h
            )
        )
    }
}

private fun DrawScope.drawVinylTurntable(
    spinAngle: Float,
    primaryColor: Color,
    bass: Float,
    alpha: Float
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val vinylR = (size.width.coerceAtMost(size.height) * 0.44f) * (1f + bass * 0.04f)

    // Outer vinyl shadow & body
    drawCircle(
        color = Color(0xFF0A0A0A).copy(alpha = alpha),
        radius = vinylR,
        center = Offset(cx, cy)
    )

    // Vinyl grooves
    val grooveCount = 18
    for (i in 1..grooveCount) {
        val r = vinylR * (0.35f + (i.toFloat() / grooveCount) * 0.62f)
        drawCircle(
            color = Color(0xFF222222).copy(alpha = 0.6f * alpha),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1f)
        )
    }

    // Rotating lighting glare reflections across vinyl grooves
    rotate(degrees = spinAngle, pivot = Offset(cx, cy)) {
        // Conical glare reflection simulation
        val glareBrush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.15f * alpha),
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = 0.15f * alpha),
                Color.Transparent
            ),
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = glareBrush,
            radius = vinylR,
            center = Offset(cx, cy)
        )

        // Center vinyl label
        val labelR = vinylR * 0.32f
        drawCircle(
            color = primaryColor.copy(alpha = 0.85f * alpha),
            radius = labelR,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.Black.copy(alpha = alpha),
            radius = labelR * 0.25f,
            center = Offset(cx, cy)
        )
        // Center spindle hole
        drawCircle(
            color = Color(0xFF111111),
            radius = 10f,
            center = Offset(cx, cy)
        )
    }
}

private fun DrawScope.drawCyberpunkGrid(
    time: Float,
    primaryColor: Color,
    fft: FloatArray,
    bass: Float,
    alpha: Float,
    reusablePath: Path
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.52f

    // Retrowave Sun on horizon
    val sunRadius = w * 0.28f * (1f + bass * 0.15f)
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFEE58).copy(alpha = 0.9f * alpha), Color(0xFFFF1744).copy(alpha = 0.8f * alpha)),
            startY = horizonY - sunRadius,
            endY = horizonY
        ),
        radius = sunRadius,
        center = Offset(w / 2f, horizonY)
    )

    // Sun horizontal scanline bars
    val barCount = 7
    for (i in 1..barCount) {
        val y = horizonY - (sunRadius * (i.toFloat() / (barCount + 1)))
        val barH = (i * 2.5f)
        drawRect(
            color = Color.Black,
            topLeft = Offset(w / 2f - sunRadius, y),
            size = Size(sunRadius * 2, barH)
        )
    }

    // Perspective floor vertical grid lines
    val gridLines = 14
    for (i in 0..gridLines) {
        val startX = w * (i.toFloat() / gridLines)
        val endX = w * 0.5f + (startX - w * 0.5f) * 3.5f
        drawLine(
            color = primaryColor.copy(alpha = 0.45f * alpha),
            start = Offset(startX, horizonY),
            end = Offset(endX, h),
            strokeWidth = 1.5f
        )
    }

    // Moving horizontal grid lines
    val hLines = 10
    val offset = (time * 1.5f) % 1f
    for (i in 0 until hLines) {
        val progress = ((i + offset) / hLines).coerceIn(0f, 1f)
        val y = horizonY + (h - horizonY) * (progress * progress)
        val lineAlpha = (progress * 0.6f * alpha).coerceIn(0f, 1f)
        drawLine(
            color = Color(0xFF00E5FF).copy(alpha = lineAlpha),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.5f + progress * 2f
        )
    }

    // Audio-reactive wireframe mountain silhouette on horizon
    reusablePath.reset()
    reusablePath.moveTo(0f, horizonY)
    val points = 24
    val safeFft = if (fft.isNotEmpty()) fft else FloatArray(1) { 0f }
    for (i in 0..points) {
        val x = w * (i.toFloat() / points)
        val fftIdx = (i * safeFft.size / points).coerceIn(0, safeFft.size - 1)
        val amp = safeFft[fftIdx] * 70f + (bass * 25f)
        val my = horizonY - (sin(i * 0.8f) * 20f + amp)
        reusablePath.lineTo(x, my)
    }
    reusablePath.lineTo(w, horizonY)
    reusablePath.close()

    drawPath(
        path = reusablePath,
        color = Color(0xFF1A0033).copy(alpha = 0.85f * alpha)
    )
    drawPath(
        path = reusablePath,
        color = primaryColor.copy(alpha = 0.75f * alpha),
        style = Stroke(width = 2f)
    )
}

private fun DrawScope.drawVacuumTubeGlow(
    time: Float,
    primaryColor: Color,
    bass: Float,
    treble: Float,
    alpha: Float,
    reusablePath: Path
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val tubeW = size.width * 0.45f
    val tubeH = size.height * 0.65f

    // Warm Orange Thermionic Core Glow
    val glowR = tubeW * (0.8f + bass * 0.45f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF9100).copy(alpha = (0.6f + bass * 0.35f) * alpha),
                Color(0xFFFF3D00).copy(alpha = 0.3f * alpha),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = glowR
        ),
        radius = glowR
    )

    // Glass Tube Outline
    val tubeTop = cy - tubeH / 2f
    val tubeLeft = cx - tubeW / 2f
    drawRoundRect(
        color = Color.White.copy(alpha = 0.15f * alpha),
        topLeft = Offset(tubeLeft, tubeTop),
        size = Size(tubeW, tubeH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(tubeW * 0.4f, tubeW * 0.4f),
        style = Stroke(width = 2.5f)
    )

    // Heated Filament Wires
    val filamentCount = 3
    for (i in 0 until filamentCount) {
        val fx = cx + (i - 1) * (tubeW * 0.2f)
        reusablePath.reset()
        reusablePath.moveTo(fx, tubeTop + tubeH * 0.2f)
        val steps = 16
        for (s in 0..steps) {
            val fy = (tubeTop + tubeH * 0.2f) + (tubeH * 0.6f) * (s.toFloat() / steps)
            val zigzag = if (s % 2 == 0) 6f else -6f
            val vibr = sin(time * 10f + s).toFloat() * (treble * 8f)
            reusablePath.lineTo(fx + zigzag + vibr, fy)
        }
        drawPath(
            path = reusablePath,
            color = Color(0xFFFFD54F).copy(alpha = (0.85f + bass * 0.15f) * alpha),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawHarmonicWaves(
    time: Float,
    primaryColor: Color,
    fft: FloatArray,
    bass: Float,
    alpha: Float,
    reusablePath: Path
) {
    val w = size.width
    val h = size.height
    val waveCount = 5

    for (wIdx in 0 until waveCount) {
        reusablePath.reset()
        val centerY = h * (0.4f + wIdx * 0.08f)
        val speed = 1.2f + wIdx * 0.4f
        val baseColor = when (wIdx) {
            0 -> primaryColor
            1 -> Color(0xFF00E5FF)
            2 -> Color(0xFFFF4081)
            3 -> Color(0xFF76FF03)
            else -> Color(0xFFFFD700)
        }

        reusablePath.moveTo(0f, centerY)
        val steps = 40
        val safeFft = if (fft.isNotEmpty()) fft else FloatArray(1) { 0f }
        for (i in 0..steps) {
            val x = w * (i.toFloat() / steps)
            val fftIdx = (i * safeFft.size / steps).coerceIn(0, safeFft.size - 1)
            val fftBoost = safeFft[fftIdx] * 90f
            val phase = (x / w) * 4 * PI + time * speed + (wIdx * 1.2f)
            val y = centerY + sin(phase).toFloat() * (35f + bass * 40f + fftBoost)
            reusablePath.lineTo(x, y)
        }

        drawPath(
            path = reusablePath,
            color = baseColor.copy(alpha = (0.55f - wIdx * 0.08f) * alpha),
            style = Stroke(width = 3f - wIdx * 0.3f, cap = StrokeCap.Round)
        )
    }
}
