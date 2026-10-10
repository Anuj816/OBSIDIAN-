package com.anuj.obsidian.ui.visualizer

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 20 Distinct Milkdrop & Advanced Audio-Synthesized Rendering Algorithms.
 * Zero-allocation, high-performance GPU canvas renderers synchronized to real-time FFT audio telemetry.
 */

// 1. Tunnel / Perspective Zoom Warp Effect (Milkdrop Classic)
fun DrawScope.renderTunnelZoomWarp(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val mid = snapshot.midEnergy
    val maxR = size.width.coerceAtLeast(size.height) * 0.75f
    val tunnelRings = 14
    val speed = (time * 1.5f * preset.motionSpeed) + (if (isPlaying) bass * 0.4f else 0f)

    // Perspective depth rings pulling toward or away from camera
    for (i in 0 until tunnelRings) {
        val progress = ((i + speed) % tunnelRings) / tunnelRings
        val depth = progress * progress // Quadratic depth acceleration
        val r = depth * maxR + 6.dp.toPx()
        val ringAlpha = (progress * alpha * 0.9f).coerceIn(0f, 1f)

        val sides = 8
        scratchPad.scratchPath1.reset()
        val twist = (1f - progress) * 2.2f + (time * 0.5f)
        for (s in 0..sides) {
            val angle = (s * 2 * PI / sides) + twist
            val fftIdx = (s * 3 + i) % maxOf(1, snapshot.fftMagnitudes.size)
            val audioMod = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 18.dp.toPx() * preset.sensitivity else 0f
            val px = center.x + cos(angle).toFloat() * (r + audioMod)
            val py = center.y + sin(angle).toFloat() * (r + audioMod)
            if (s == 0) scratchPad.scratchPath1.moveTo(px, py) else scratchPad.scratchPath1.lineTo(px, py)
        }
        scratchPad.scratchPath1.close()

        val strokeColor = if (i % 2 == 0) preset.primaryColor else preset.secondaryColor
        drawPath(
            path = scratchPad.scratchPath1,
            color = strokeColor.copy(alpha = ringAlpha),
            style = scratchPad.getStroke((1.5f + (1f - progress) * 3f).dp.toPx())
        )
    }

    // Longitudinal perspective ribs
    val ribCount = 8
    val outerR = maxR * 0.95f
    for (r in 0 until ribCount) {
        val angle = (r * 2 * PI / ribCount) + (time * 0.5f)
        val endX = center.x + cos(angle).toFloat() * outerR
        val endY = center.y + sin(angle).toFloat() * outerR
        drawLine(
            color = preset.secondaryColor.copy(alpha = alpha * 0.35f),
            start = center,
            end = Offset(endX, endY),
            strokeWidth = 1.2.dp.toPx()
        )
    }

    // Center Singularity Glow
    val coreR = 16.dp.toPx() + (bass * 20.dp.toPx())
    drawCircle(
        brush = Brush.radialGradient(
            listOf(preset.primaryColor.copy(alpha = alpha * 0.9f * preset.glowIntensity), Color.Transparent),
            center = center,
            radius = coreR * 2.2f
        ),
        radius = coreR * 2.2f,
        center = center
    )
}

// 2. Rotating Starfield / Warp Speed Effect
fun DrawScope.renderRotatingStarfield(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val warpSpeed = (12f + bass * 45f) * preset.motionSpeed
    val rollAngle = time * 0.4f * preset.motionSpeed
    val cosRoll = cos(rollAngle.toDouble()).toFloat()
    val sinRoll = sin(rollAngle.toDouble()).toFloat()

    val starCount = scratchPad.starX.size.coerceAtMost(160)
    for (i in 0 until starCount) {
        scratchPad.starZ[i] -= warpSpeed
        if (scratchPad.starZ[i] <= 8f) {
            scratchPad.starZ[i] = 1000f
            scratchPad.starX[i] = ((Math.random() - 0.5) * 1100).toFloat()
            scratchPad.starY[i] = ((Math.random() - 0.5) * 1100).toFloat()
        }

        val z = scratchPad.starZ[i]
        val rx = scratchPad.starX[i] * cosRoll - scratchPad.starY[i] * sinRoll
        val ry = scratchPad.starX[i] * sinRoll + scratchPad.starY[i] * cosRoll

        val fov = 420f
        val p = fov / z
        val sx = center.x + rx * p
        val sy = center.y + ry * p

        val pPrev = fov / (z + warpSpeed * 2.5f)
        val sxPrev = center.x + rx * pPrev
        val syPrev = center.y + ry * pPrev

        val starAlpha = ((1f - z / 1000f) * alpha).coerceIn(0.1f, 1f)
        val strokeW = (2.8f * p).coerceIn(1f, 5.dp.toPx())

        val starCol = if (i % 3 == 0) preset.primaryColor else if (i % 3 == 1) preset.secondaryColor else Color.White
        drawLine(
            color = starCol.copy(alpha = starAlpha),
            start = Offset(sxPrev, syPrev),
            end = Offset(sx, sy),
            strokeWidth = strokeW
        )

        if (z < 250f) {
            drawCircle(
                color = Color.White.copy(alpha = starAlpha),
                radius = (strokeW * 0.6f).coerceAtLeast(1.5.dp.toPx()),
                center = Offset(sx, sy)
            )
        }
    }
}

// 3. Voronoi / Cellular Pattern Generator
fun DrawScope.renderVoronoiCellular(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val numSites = preset.particleCount.coerceIn(4, 64)
    val maxRadius = minOf(size.width, size.height) * 0.44f

    // Update animated cellular nuclei sites
    for (i in 0 until numSites) {
        val angle = (i * 2 * PI / numSites) + (time * 0.25f * (if (i % 2 == 0) 1f else -0.8f))
        val dist = ((sin(i * 1.7 + time * 1.2) * 0.35f + 0.55f) * maxRadius).toFloat()
        val fftIdx = (i * 2) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioBoost = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 25.dp.toPx() * preset.sensitivity else 0f
        scratchPad.voronoiSitesX[i] = center.x + cos(angle).toFloat() * (dist + audioBoost)
        scratchPad.voronoiSitesY[i] = center.y + sin(angle).toFloat() * (dist + audioBoost)
    }

    // Connect Voronoi triangulation edges
    val connectionDist = (150.dp.toPx() - (numSites * 1.2f).dp.toPx()) * (preset.glowIntensity * 1.5f + 0.2f)
    val maxDistSq = connectionDist * connectionDist
    for (i in 0 until numSites) {
        for (j in i + 1 until numSites) {
            val dx = scratchPad.voronoiSitesX[i] - scratchPad.voronoiSitesX[j]
            val dy = scratchPad.voronoiSitesY[i] - scratchPad.voronoiSitesY[j]
            val distSq = dx * dx + dy * dy
            if (distSq < maxDistSq) {
                val edgeAlpha = (1f - distSq / maxDistSq) * alpha * 0.75f
                drawLine(
                    color = if ((i + j) % 2 == 0) preset.primaryColor.copy(alpha = edgeAlpha) else preset.secondaryColor.copy(alpha = edgeAlpha),
                    start = Offset(scratchPad.voronoiSitesX[i], scratchPad.voronoiSitesY[i]),
                    end = Offset(scratchPad.voronoiSitesX[j], scratchPad.voronoiSitesY[j]),
                    strokeWidth = 1.6.dp.toPx()
                )
            }
        }
    }

    // Draw cell nuclei and harmonic pulses
    for (i in 0 until numSites) {
        val pCenter = Offset(scratchPad.voronoiSitesX[i], scratchPad.voronoiSitesY[i])
        val nodeR = 3.5.dp.toPx() + (if (i % 4 == 0) bass * 6.dp.toPx() else 0f)
        drawCircle(
            color = if (i % 2 == 0) preset.primaryColor.copy(alpha = alpha) else preset.secondaryColor.copy(alpha = alpha),
            radius = nodeR,
            center = pCenter
        )
        drawCircle(
            color = preset.primaryColor.copy(alpha = alpha * 0.25f),
            radius = nodeR * 2.2f,
            center = pCenter
        )
    }
}

// 4. Flame / Fire Fractal
fun DrawScope.renderFlameFireFractal(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val baseY = size.height * 0.90f
    val centerX = size.width / 2f
    val bass = snapshot.bassEnergy * preset.beatResponse
    val tongues = preset.particleCount.coerceIn(5, 32)
    val flameWidth = size.width * 0.65f

    for (t in 0 until tongues) {
        val frac = (t.toFloat() / (tongues - 1) - 0.5f)
        val xRoot = centerX + frac * flameWidth
        val fftIdx = (t * 3) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioHeight = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * (size.height * 0.55f) * preset.sensitivity else size.height * 0.18f
        val flameH = (audioHeight + bass * 60.dp.toPx()).coerceAtLeast(30.dp.toPx())

        scratchPad.scratchPath1.reset()
        scratchPad.scratchPath1.moveTo(xRoot - 14.dp.toPx(), baseY)

        val midY = baseY - flameH * 0.55f
        val sway = sin(time * 3f * preset.motionSpeed + t * 1.5).toFloat() * preset.glowIntensity * 60.dp.toPx()
        val tipX = xRoot + sway
        val tipY = baseY - flameH

        scratchPad.scratchPath1.quadraticTo(xRoot - 20.dp.toPx() + sway * 0.5f, midY, tipX, tipY)
        scratchPad.scratchPath1.quadraticTo(xRoot + 20.dp.toPx() + sway * 0.5f, midY, xRoot + 14.dp.toPx(), baseY)
        scratchPad.scratchPath1.close()

        val flameBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha * 0.9f),
                preset.primaryColor.copy(alpha = alpha * 0.75f),
                preset.secondaryColor.copy(alpha = alpha * 0.35f),
                Color.Transparent
            ),
            startY = tipY,
            endY = baseY
        )
        drawPath(scratchPad.scratchPath1, flameBrush)
    }

    // Glowing base coals
    drawCircle(
        brush = Brush.radialGradient(
            listOf(preset.primaryColor.copy(alpha = alpha * 0.65f * preset.glowIntensity), Color.Transparent),
            center = Offset(centerX, baseY),
            radius = flameWidth * 0.6f
        ),
        radius = flameWidth * 0.6f,
        center = Offset(centerX, baseY)
    )
}

// 5. Rippling Water-Surface Simulation
fun DrawScope.renderWaterSurfaceRipple(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height * 0.58f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val maxRadius = minOf(size.width, size.height) * 0.48f
    val waveCount = preset.particleCount.coerceIn(3, 20)

    for (w in 0 until waveCount) {
        val waveProg = ((w + (time * 1.4f * preset.motionSpeed)) % waveCount) / waveCount
        val rX = waveProg * maxRadius
        val rY = rX * 0.52f // Elliptical perspective on water surface
        val waveAlpha = ((1f - waveProg) * alpha * 0.85f).coerceIn(0f, 1f)

        val fftIdx = (w * 3) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioHeight = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 12.dp.toPx() * preset.sensitivity else 0f
        val strokeWidth = (2.2f + (1f - waveProg) * 3f).dp.toPx()

        drawOval(
            color = if (w % 2 == 0) preset.primaryColor.copy(alpha = waveAlpha) else preset.secondaryColor.copy(alpha = waveAlpha),
            topLeft = Offset(center.x - rX, center.y - (rY + audioHeight)),
            size = Size(rX * 2, (rY + audioHeight) * 2),
            style = scratchPad.getStroke(strokeWidth)
        )
    }

    // Water Drop Impact in Center
    val dropPulse = if (isPlaying) bass * 14.dp.toPx() else 4.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = alpha * 0.8f), preset.primaryColor.copy(alpha = alpha * 0.2f), Color.Transparent),
            center = center,
            radius = 24.dp.toPx() + dropPulse
        ),
        radius = 24.dp.toPx() + dropPulse,
        center = center
    )
}

// 6. Strobing Grid / Tesseract Warp
fun DrawScope.renderStrobingGridTesseract(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val mid = snapshot.midEnergy
    val gridLines = 12
    val span = minOf(size.width, size.height) * 0.42f

    // Nonlinear fish-eye warp factor modulated by bass
    val warpFactor = 1.0f + bass * 0.8f
    val rot = time * 0.35f * preset.motionSpeed

    rotate(degrees = (rot * 180f / PI.toFloat()), pivot = center) {
        // Vertical perspective grid lines
        for (i in -gridLines / 2..gridLines / 2) {
            val norm = i.toFloat() / (gridLines / 2)
            val warpedX = norm * span * (1f + (norm * norm) * 0.4f * warpFactor)
            val fftIdx = (abs(i) * 2) % maxOf(1, snapshot.fftMagnitudes.size)
            val lineAlpha = ((1f - abs(norm) * 0.6f) * alpha).coerceIn(0.1f, 1f)

            drawLine(
                color = preset.primaryColor.copy(alpha = lineAlpha * 0.75f),
                start = Offset(center.x + warpedX, center.y - span),
                end = Offset(center.x + warpedX, center.y + span),
                strokeWidth = (1.5f + if (abs(i) == 0) bass * 3f else 0f).dp.toPx()
            )
        }

        // Horizontal perspective grid lines
        for (j in -gridLines / 2..gridLines / 2) {
            val norm = j.toFloat() / (gridLines / 2)
            val warpedY = norm * span * (1f + (norm * norm) * 0.4f * warpFactor)
            val lineAlpha = ((1f - abs(norm) * 0.6f) * alpha).coerceIn(0.1f, 1f)

            drawLine(
                color = preset.secondaryColor.copy(alpha = lineAlpha * 0.75f),
                start = Offset(center.x - span, center.y + warpedY),
                end = Offset(center.x + span, center.y + warpedY),
                strokeWidth = (1.5f + if (abs(j) == 0) mid * 3f else 0f).dp.toPx()
            )
        }
    }

    // Strobe Pulse Outer Border
    val strobAlpha = if (isPlaying && bass > 0.6f) 0.9f else 0.2f
    drawRect(
        color = Color.White.copy(alpha = strobAlpha * alpha),
        topLeft = Offset(center.x - span, center.y - span),
        size = Size(span * 2, span * 2),
        style = Stroke(width = 2.dp.toPx())
    )
}

// 7. Spiral Galaxy Arm Generator
fun DrawScope.renderSpiralGalaxyArms(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val numArms = preset.particleCount.coerceIn(2, 16)
    val totalStars = scratchPad.galaxyStarsRadius.size.coerceAtMost(180)
    val rotSpeed = time * 0.4f * preset.motionSpeed

    for (i in 0 until totalStars) {
        val arm = scratchPad.galaxyStarsArm[i] % numArms
        val armBaseAngle = (arm * 2 * PI / numArms)
        val r = scratchPad.galaxyStarsRadius[i] * (0.8f + bass * 0.25f)
        val theta = armBaseAngle + (ln(r.toDouble() / 15.0).toFloat() * preset.glowIntensity * 3f) + rotSpeed + (scratchPad.galaxyStarsAngle[i] * 0.15f)

        val sx = center.x + cos(theta).toFloat() * r
        val sy = center.y + sin(theta).toFloat() * r

        val starAlpha = ((1f - (r / 350f).coerceIn(0f, 0.85f)) * alpha).coerceIn(0.1f, 1f)
        val starSize = (1.8f + (1f - r / 350f) * 2.5f).dp.toPx()

        drawCircle(
            color = if (arm % 2 == 0) preset.primaryColor.copy(alpha = starAlpha) else preset.secondaryColor.copy(alpha = starAlpha),
            radius = starSize,
            center = Offset(sx, sy)
        )
    }

    // Supermassive Galactic Core Singularity
    val coreR = 20.dp.toPx() + (bass * 16.dp.toPx())
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = alpha), preset.primaryColor.copy(alpha = alpha * 0.6f), Color.Transparent),
            center = center,
            radius = coreR * 2.5f
        ),
        radius = coreR * 2.5f,
        center = center
    )
}

// 8. Oscilloscope Lissajous with Trailing Persistence
fun DrawScope.renderLissajousPersistence(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val scale = minOf(size.width, size.height) * 0.38f
    val freqX = (preset.particleCount % 7 + 1).toDouble()
    val freqY = (preset.particleCount % 5 + 1).toDouble()
    val phaseDelta = time * 1.2 * preset.motionSpeed
    val steps = preset.particleCount.coerceIn(30, 200)

    scratchPad.scratchPath1.reset()
    val waveLen = snapshot.waveform.size

    for (i in 0..steps) {
        val tNorm = i.toDouble() / steps * 2 * PI
        val sampleIdx = ((i.toFloat() / steps) * (waveLen - 1)).toInt().coerceIn(0, maxOf(0, waveLen - 1))
        val audioMod = if (isPlaying && waveLen > 0) snapshot.waveform[sampleIdx] * 0.25f * preset.sensitivity else 0f

        val x = center.x + (sin(freqX * tNorm + phaseDelta + audioMod) * scale).toFloat()
        val y = center.y + (cos(freqY * tNorm) * scale).toFloat()

        if (i == 0) scratchPad.scratchPath1.moveTo(x, y) else scratchPad.scratchPath1.lineTo(x, y)
    }

    // Main laser beam
    drawPath(
        path = scratchPad.scratchPath1,
        color = preset.primaryColor.copy(alpha = alpha * 0.95f),
        style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)
    )

    // Secondary persistence glow trail
    drawPath(
        path = scratchPad.scratchPath1,
        color = preset.secondaryColor.copy(alpha = alpha * 0.35f * preset.glowIntensity),
        style = Stroke(width = 8.dp.toPx(), join = StrokeJoin.Round)
    )
}

// 9. Mirrored Butterfly / Rorschach Symmetry Generator
fun DrawScope.renderRorschachButterfly(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val wingScale = minOf(size.width, size.height) * 0.16f * (1f + bass * 0.35f)
    val steps = preset.particleCount.coerceIn(40, 250)

    scratchPad.scratchPath1.reset()
    scratchPad.scratchPath2.reset()

    for (i in 0..steps) {
        val theta = (i.toDouble() / steps) * 2 * PI
        val fftIdx = (i % maxOf(1, snapshot.fftMagnitudes.size))
        val audioBoost = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 0.4f * preset.sensitivity else 0f

        // Temple's Butterfly curve: r = exp(cos(theta)) - 2cos(4theta) + sin(theta/12)^5
        val r = (exp(cos(theta)) - 2.0 * cos(4.0 * theta) + sin(theta / 12.0).pow(5.0) + audioBoost) * wingScale
        val x = (sin(theta) * r).toFloat()
        val y = (-cos(theta) * r).toFloat()

        if (i == 0) {
            scratchPad.scratchPath1.moveTo(center.x + x, center.y + y)
            scratchPad.scratchPath2.moveTo(center.x - x, center.y + y) // Perfect Rorschach Bilateral Reflection
        } else {
            scratchPad.scratchPath1.lineTo(center.x + x, center.y + y)
            scratchPad.scratchPath2.lineTo(center.x - x, center.y + y)
        }
    }

    drawPath(
        path = scratchPad.scratchPath1,
        brush = Brush.radialGradient(listOf(preset.primaryColor.copy(alpha = alpha * 0.8f), Color.Transparent), center = center, radius = wingScale * 4f)
    )
    drawPath(
        path = scratchPad.scratchPath2,
        brush = Brush.radialGradient(listOf(preset.secondaryColor.copy(alpha = alpha * 0.8f), Color.Transparent), center = center, radius = wingScale * 4f)
    )

    drawPath(path = scratchPad.scratchPath1, color = preset.primaryColor.copy(alpha = alpha), style = scratchPad.getStroke(2.dp.toPx()))
    drawPath(path = scratchPad.scratchPath2, color = preset.secondaryColor.copy(alpha = alpha), style = scratchPad.getStroke(2.dp.toPx()))
}

// 10. Beat-Triggered Flash Bloom / Supernova
fun DrawScope.renderBeatFlashBloom(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val isFlash = isPlaying && bass > 0.55f
    val maxRadius = minOf(size.width, size.height) * 0.48f

    // Concentric Supernova Shockwaves
    val shockwaves = 5
    for (s in 0 until shockwaves) {
        val sProgress = ((s + time * 2.0f * preset.motionSpeed) % shockwaves) / shockwaves
        val r = sProgress * maxRadius + (bass * 25.dp.toPx())
        val sAlpha = ((1f - sProgress) * (if (isFlash) 0.95f else 0.45f) * alpha).coerceIn(0f, 1f)

        drawCircle(
            color = if (s % 2 == 0) preset.primaryColor.copy(alpha = sAlpha) else preset.secondaryColor.copy(alpha = sAlpha),
            radius = r,
            center = center,
            style = Stroke(width = (3.5f * (1f - sProgress)).coerceAtLeast(1f).dp.toPx())
        )
    }

    // Radial Supernova Light Beams
    val beams = 16
    for (b in 0 until beams) {
        val angle = (b * 2 * PI / beams) + (time * 0.3f)
        val beamLen = maxRadius * (0.6f + bass * 0.65f)
        val endX = center.x + cos(angle).toFloat() * beamLen
        val endY = center.y + sin(angle).toFloat() * beamLen
        drawLine(
            color = Color.White.copy(alpha = (if (isFlash) 0.9f else 0.4f) * alpha),
            start = center,
            end = Offset(endX, endY),
            strokeWidth = (2.dp.toPx() + if (isFlash) 3.dp.toPx() else 0f)
        )
    }

    // Core Flash Singularity
    val coreR = 15.dp.toPx() + (bass * 35.dp.toPx())
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = coreR,
        center = center
    )
}

// 11. Aurora Borealis / Plasma Ribbon Curtains
fun DrawScope.renderAuroraPlasmaCurtains(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val layers = 4
    val steps = preset.particleCount.coerceIn(20, 150)
    val widthStep = size.width / steps

    for (l in 0 until layers) {
        scratchPad.scratchPath1.reset()
        val layerPhase = time * (0.8f + l * 0.25f) * preset.motionSpeed
        val baseY = size.height * (0.35f + l * 0.12f)

        for (i in 0..steps) {
            val x = i * widthStep
            val xNorm = i.toFloat() / steps
            val fftIdx = (i * 2 + l * 4) % maxOf(1, snapshot.fftMagnitudes.size)
            val audioMod = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 45.dp.toPx() * preset.sensitivity else 0f

            val wave = (sin(xNorm * 5.0 + layerPhase) * 25.dp.toPx() + cos(xNorm * 3.0 - layerPhase * 0.7) * 18.dp.toPx()).toFloat()
            val y = baseY + wave + audioMod

            if (i == 0) scratchPad.scratchPath1.moveTo(x, y) else scratchPad.scratchPath1.lineTo(x, y)
        }

        val curtainCol = if (l % 2 == 0) preset.primaryColor else preset.secondaryColor
        drawPath(
            path = scratchPad.scratchPath1,
            color = curtainCol.copy(alpha = alpha * (0.75f - l * 0.12f)),
            style = Stroke(width = (5.dp.toPx() + (3 - l).dp.toPx()), cap = StrokeCap.Round)
        )
    }
}

// 12. Sacred Geometry Mandala / Sri Yantra Morpher
fun DrawScope.renderSacredMandalaMorph(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val maxR = minOf(size.width, size.height) * 0.40f * (1f + bass * 0.15f)
    val petallayers = 3

    for (l in 1..petallayers) {
        val petals = 6 * l
        val layerR = (maxR / petallayers) * l
        val rot = time * (0.2f / l) * preset.motionSpeed

        rotate(degrees = (rot * 180f / PI.toFloat()), pivot = center) {
            for (p in 0 until petals) {
                val angle = (p * 2 * PI / petals)
                val px = center.x + cos(angle).toFloat() * (layerR * 0.6f)
                val py = center.y + sin(angle).toFloat() * (layerR * 0.6f)

                drawCircle(
                    color = if (l % 2 == 0) preset.primaryColor.copy(alpha = alpha * 0.45f) else preset.secondaryColor.copy(alpha = alpha * 0.45f),
                    radius = layerR * 0.4f,
                    center = Offset(px, py),
                    style = scratchPad.getStroke(1.5.dp.toPx())
                )
            }
        }
    }

    // Outer Harmonic Ring
    drawCircle(
        color = preset.primaryColor.copy(alpha = alpha * 0.85f),
        radius = maxR,
        center = center,
        style = scratchPad.getStroke(2.dp.toPx())
    )
}

// 13. Quantum Wave Interference
fun DrawScope.renderQuantumInterference(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val slitSeparation = minOf(size.width, size.height) * 0.22f
    val slit1 = Offset(center.x - slitSeparation / 2f, center.y)
    val slit2 = Offset(center.x + slitSeparation / 2f, center.y)

    val maxR = minOf(size.width, size.height) * 0.44f
    val waveCount = preset.particleCount.coerceIn(3, 20)

    for (w in 1..waveCount) {
        val r = (w.toFloat() / waveCount) * maxR + (time * 15f * preset.motionSpeed) % (maxR / waveCount)
        val wAlpha = ((1f - (w.toFloat() / waveCount)) * alpha * 0.7f).coerceIn(0f, 1f)

        drawCircle(
            color = preset.primaryColor.copy(alpha = wAlpha),
            radius = r,
            center = slit1,
            style = scratchPad.getStroke(1.5.dp.toPx())
        )
        drawCircle(
            color = preset.secondaryColor.copy(alpha = wAlpha),
            radius = r,
            center = slit2,
            style = scratchPad.getStroke(1.5.dp.toPx())
        )
    }
}

// 14. Audio Electric Lightning / Tesla Coil Arcs
fun DrawScope.renderElectricTeslaLightning(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val numBolts = 6
    val maxReach = minOf(size.width, size.height) * 0.42f

    for (b in 0 until numBolts) {
        val baseAngle = (b * 2 * PI / numBolts) + (time * 0.5f)
        val endPoint = Offset(
            center.x + cos(baseAngle).toFloat() * maxReach,
            center.y + sin(baseAngle).toFloat() * maxReach
        )

        scratchPad.scratchPath1.reset()
        scratchPad.scratchPath1.moveTo(center.x, center.y)

        val segments = 6
        var curr = center
        for (s in 1..segments) {
            val frac = s.toFloat() / segments
            val target = Offset(
                center.x + (endPoint.x - center.x) * frac,
                center.y + (endPoint.y - center.y) * frac
            )
            val jitter = if (s < segments) (sin(b * 5.0 + s * 3.0 + time * 12.0) * 18.dp.toPx() * (1f + bass)).toFloat() else 0f
            val next = Offset(target.x - sin(baseAngle).toFloat() * jitter, target.y + cos(baseAngle).toFloat() * jitter)
            scratchPad.scratchPath1.lineTo(next.x, next.y)
            curr = next
        }

        drawPath(
            path = scratchPad.scratchPath1,
            color = if (b % 2 == 0) preset.primaryColor.copy(alpha = alpha) else Color.White.copy(alpha = alpha),
            style = Stroke(width = (2.5.dp.toPx() + bass * 3.dp.toPx()), cap = StrokeCap.Round)
        )
    }

    // Tesla Central Plasma Sphere
    drawCircle(
        color = preset.secondaryColor.copy(alpha = alpha * 0.85f),
        radius = 16.dp.toPx() + bass * 12.dp.toPx(),
        center = center
    )
}

// 15. Crystal Prism & Chromatic Dispersion
fun DrawScope.renderCrystalPrismDispersion(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val prismRadius = minOf(size.width, size.height) * 0.28f
    val sides = 6
    val rot = time * 0.35f * preset.motionSpeed

    rotate(degrees = (rot * 180f / PI.toFloat()), pivot = center) {
        scratchPad.scratchPath1.reset()
        for (i in 0..sides) {
            val angle = i * 2 * PI / sides
            val px = center.x + cos(angle).toFloat() * prismRadius
            val py = center.y + sin(angle).toFloat() * prismRadius
            if (i == 0) scratchPad.scratchPath1.moveTo(px, py) else scratchPad.scratchPath1.lineTo(px, py)
        }
        scratchPad.scratchPath1.close()

        // Crystal Prism Face Refractions
        drawPath(
            path = scratchPad.scratchPath1,
            color = preset.primaryColor.copy(alpha = alpha * 0.25f)
        )
        drawPath(
            path = scratchPad.scratchPath1,
            color = Color.White.copy(alpha = alpha * 0.85f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Internal Dispersion Lines
        for (i in 0 until sides) {
            val angle1 = i * 2 * PI / sides
            val angle2 = ((i + 3) % sides) * 2 * PI / sides
            drawLine(
                color = preset.secondaryColor.copy(alpha = alpha * 0.55f),
                start = Offset(center.x + cos(angle1).toFloat() * prismRadius, center.y + sin(angle1).toFloat() * prismRadius),
                end = Offset(center.x + cos(angle2).toFloat() * prismRadius, center.y + sin(angle2).toFloat() * prismRadius),
                strokeWidth = 1.2.dp.toPx()
            )
        }
    }
}

// 16. Black Hole & Accretion Vortex
fun DrawScope.renderBlackHoleAccretion(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val eventHorizonR = minOf(size.width, size.height) * 0.16f
    val accretionR = eventHorizonR * (2.8f + bass * 0.6f)

    // Swirling Relativistic Accretion Disk
    val rings = preset.particleCount.coerceIn(3, 16)
    for (r in 1..rings) {
        val ringFraction = r.toFloat() / rings
        val rad = eventHorizonR + ringFraction * (accretionR - eventHorizonR)
        val swirlAngle = time * (2.5f / sqrt(r.toFloat())) * preset.motionSpeed

        rotate(degrees = (swirlAngle * 180f / PI.toFloat()), pivot = center) {
            drawOval(
                color = if (r % 2 == 0) preset.primaryColor.copy(alpha = (1f - ringFraction) * alpha * 0.85f) else preset.secondaryColor.copy(alpha = (1f - ringFraction) * alpha * 0.85f),
                topLeft = Offset(center.x - rad, center.y - rad * 0.42f),
                size = Size(rad * 2, rad * 0.84f),
                style = scratchPad.getStroke((2f + (1f - ringFraction) * 3f).dp.toPx())
            )
        }
    }

    // Pitch Black Singularity Event Horizon
    drawCircle(
        color = Color(0xFF050505),
        radius = eventHorizonR,
        center = center
    )
    drawCircle(
        color = preset.primaryColor.copy(alpha = alpha * 0.8f),
        radius = eventHorizonR,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
}

// 17. Möbius Soundwave Ribbon
fun DrawScope.renderMobiusSoundwaveRibbon(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val R = minOf(size.width, size.height) * 0.28f
    val vWidth = 24.dp.toPx()
    val steps = preset.particleCount.coerceIn(30, 150)
    val rot = time * 0.4f * preset.motionSpeed

    scratchPad.scratchPath1.reset()
    scratchPad.scratchPath2.reset()

    for (i in 0..steps) {
        val u = (i.toDouble() / steps) * 2 * PI
        val fftIdx = (i * 2) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioWave = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 16.dp.toPx() * preset.sensitivity else 0f

        val edge1 = vWidth + audioWave
        val edge2 = -vWidth - audioWave

        val x1 = (R + edge1 * cos(u / 2.0)) * cos(u + rot)
        val y1 = (R + edge1 * cos(u / 2.0)) * sin(u + rot)

        val x2 = (R + edge2 * cos(u / 2.0)) * cos(u + rot)
        val y2 = (R + edge2 * cos(u / 2.0)) * sin(u + rot)

        if (i == 0) {
            scratchPad.scratchPath1.moveTo(center.x + x1.toFloat(), center.y + y1.toFloat())
            scratchPad.scratchPath2.moveTo(center.x + x2.toFloat(), center.y + y2.toFloat())
        } else {
            scratchPad.scratchPath1.lineTo(center.x + x1.toFloat(), center.y + y1.toFloat())
            scratchPad.scratchPath2.lineTo(center.x + x2.toFloat(), center.y + y2.toFloat())
        }
    }

    drawPath(scratchPad.scratchPath1, preset.primaryColor.copy(alpha = alpha), style = scratchPad.getStroke(2.dp.toPx()))
    drawPath(scratchPad.scratchPath2, preset.secondaryColor.copy(alpha = alpha), style = scratchPad.getStroke(2.dp.toPx()))
}

// 18. Retro Radar & Sonar Sweep
fun DrawScope.renderSonarRadarSweep(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = minOf(size.width, size.height) * 0.42f
    val sweepAngle = ((time * 2.2f * preset.motionSpeed) % (2f * PI.toFloat()))

    // Concentric Range Rings
    val rings = preset.particleCount.coerceIn(2, 8)
    for (r in 1..rings) {
        val rad = (r.toFloat() / rings) * maxRadius
        drawCircle(
            color = preset.primaryColor.copy(alpha = alpha * 0.4f),
            radius = rad,
            center = center,
            style = scratchPad.getStroke(1.2.dp.toPx())
        )
    }

    // Crosshairs
    drawLine(color = preset.primaryColor.copy(alpha = alpha * 0.35f), start = Offset(center.x - maxRadius, center.y), end = Offset(center.x + maxRadius, center.y), strokeWidth = 1.dp.toPx())
    drawLine(color = preset.primaryColor.copy(alpha = alpha * 0.35f), start = Offset(center.x, center.y - maxRadius), end = Offset(center.x, center.y + maxRadius), strokeWidth = 1.dp.toPx())

    // Rotating Sweep Radar Line
    val sweepEnd = Offset(
        center.x + cos(sweepAngle).toFloat() * maxRadius,
        center.y + sin(sweepAngle).toFloat() * maxRadius
    )
    drawLine(
        color = Color.White.copy(alpha = alpha * 0.9f),
        start = center,
        end = sweepEnd,
        strokeWidth = 2.dp.toPx()
    )

    // Radar Phosphor Trail Sweep Arc
    drawArc(
        brush = Brush.sweepGradient(
            listOf(Color.Transparent, preset.primaryColor.copy(alpha = alpha * 0.4f)),
            center = center
        ),
        startAngle = (sweepAngle * 180f / PI.toFloat()) - 45f,
        sweepAngle = 45f,
        useCenter = true,
        topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
        size = Size(maxRadius * 2, maxRadius * 2)
    )
}

// 19. Cybernetic Laser Spirograph
fun DrawScope.renderCyberSpirographLaser(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val R = minOf(size.width, size.height) * 0.26f
    val r = R * 0.42f
    val d = r * (0.8f + (if (isPlaying) snapshot.bassEnergy * 0.4f else 0f))
    val steps = preset.particleCount.coerceIn(40, 300)
    val rot = time * 0.6f * preset.motionSpeed

    scratchPad.scratchPath1.reset()
    for (i in 0..steps) {
        val theta = (i.toDouble() / steps) * 4 * PI
        val x = (R - r) * cos(theta + rot) + d * cos((R - r) * (theta + rot) / r)
        val y = (R - r) * sin(theta + rot) - d * sin((R - r) * (theta + rot) / r)

        if (i == 0) scratchPad.scratchPath1.moveTo(center.x + x.toFloat(), center.y + y.toFloat())
        else scratchPad.scratchPath1.lineTo(center.x + x.toFloat(), center.y + y.toFloat())
    }

    drawPath(
        path = scratchPad.scratchPath1,
        color = preset.primaryColor.copy(alpha = alpha * 0.9f),
        style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
    )
    drawPath(
        path = scratchPad.scratchPath1,
        color = preset.secondaryColor.copy(alpha = alpha * 0.35f * preset.glowIntensity),
        style = Stroke(width = 6.dp.toPx(), join = StrokeJoin.Round)
    )
}

// 20. Chladni Acoustic Resonance Plate
fun DrawScope.renderChladniAcousticPlate(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val plateSize = minOf(size.width, size.height) * 0.42f
    val m = preset.geometryMode.ordinal.coerceIn(1, 6)
    val n = preset.particleCount.coerceIn(2, 16)
    val bass = snapshot.bassEnergy * preset.beatResponse

    // Draw Chladni Vibrational Plate Boundary
    drawRect(
        color = preset.primaryColor.copy(alpha = alpha * 0.5f),
        topLeft = Offset(center.x - plateSize, center.y - plateSize),
        size = Size(plateSize * 2, plateSize * 2),
        style = scratchPad.getStroke(2.dp.toPx())
    )

    // Chladni Sand Particles Settling Along Nodal Lines
    val particleCount = scratchPad.chladniParticlesX.size.coerceAtMost((preset.glowIntensity * 120).toInt().coerceAtLeast(20))
    for (i in 0 until particleCount) {
        val px = scratchPad.chladniParticlesX[i] * (plateSize / 200f)
        val py = scratchPad.chladniParticlesY[i] * (plateSize / 200f)

        // Nodal line equation: a*sin(n*pi*x)*sin(m*pi*y) - b*sin(m*pi*x)*sin(n*pi*y) = 0
        val nodalVal = (sin(n * PI * px / plateSize) * sin(m * PI * py / plateSize) - cos(m * PI * px / plateSize) * cos(n * PI * py / plateSize)).toFloat()
        val pAlpha = (abs(nodalVal) * alpha).coerceIn(0.2f, 1f)

        drawCircle(
            color = if (i % 2 == 0) preset.primaryColor.copy(alpha = pAlpha) else preset.secondaryColor.copy(alpha = pAlpha),
            radius = (1.8f + bass * 2f).dp.toPx(),
            center = Offset(center.x + px, center.y + py)
        )
    }
}

// 21. Mandelbrot / Julia Fractal Zoom & Morph
fun DrawScope.renderMandelbrotJuliaZoom(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val mid = snapshot.midEnergy * preset.sensitivity
    val branches = preset.particleCount.coerceIn(32, 240)
    val maxRadius = minOf(size.width, size.height) * 0.44f

    // Zoom and morph parameters driven by audio bands
    val zoom = exp(((time * 0.35f * preset.motionSpeed + bass * 0.4f) % 3.2f).toDouble()).toFloat()
    val morph = (0.5f + 0.5f * sin(time * 0.5f + mid * 1.6f)).coerceIn(0f, 1f)
    val jx = -0.72f + 0.15f * cos(time * 0.28f + bass * 0.3f)
    val jy = 0.26f + 0.15f * sin(time * 0.32f + mid * 0.3f)

    // Orbital escape trajectories and fractal dendrite branches
    scratchPad.scratchPath1.reset()
    scratchPad.scratchPath2.reset()

    val arms = 8
    val ptsPerArm = (branches / arms).coerceAtLeast(4)
    for (a in 0 until arms) {
        val baseAngle = (a * 2 * PI / arms) + (time * 0.15f * preset.motionSpeed)
        for (p in 0 until ptsPerArm) {
            val frac = (p + 1).toFloat() / ptsPerArm
            var zx = (cos(baseAngle) * frac * 1.6f / zoom).toFloat()
            var zy = (sin(baseAngle) * frac * 1.6f / zoom).toFloat()

            // Escape iteration loop: z -> z^2 + (1-morph)*z0 + morph*C
            var escapeIter = 0
            val maxIter = 8
            for (it in 0 until maxIter) {
                val nx = (zx * zx - zy * zy) + (1f - morph) * (cos(baseAngle) * frac).toFloat() + morph * jx
                val ny = (2f * zx * zy) + (1f - morph) * (sin(baseAngle) * frac).toFloat() + morph * jy
                zx = nx
                zy = ny
                if (zx * zx + zy * zy > 4f) {
                    escapeIter = it
                    break
                }
            }

            val mod = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) {
                snapshot.fftMagnitudes[(a * 4 + p) % snapshot.fftMagnitudes.size] * 35.dp.toPx() * preset.sensitivity
            } else 0f

            val r = (frac * maxRadius * (1f + escapeIter * 0.12f) + mod).coerceAtMost(maxRadius * 1.4f)
            val angle = baseAngle + (escapeIter * 0.35f)
            val px = center.x + cos(angle).toFloat() * r
            val py = center.y + sin(angle).toFloat() * r

            if (p == 0) scratchPad.scratchPath1.moveTo(px, py)
            else scratchPad.scratchPath1.lineTo(px, py)

            // Conjugate fractal branch
            val px2 = center.x - cos(angle).toFloat() * r
            val py2 = center.y - sin(angle).toFloat() * r
            if (p == 0) scratchPad.scratchPath2.moveTo(px2, py2)
            else scratchPad.scratchPath2.lineTo(px2, py2)

            // Orbit trap node glow
            val nodeAlpha = ((escapeIter.toFloat() / maxIter) * alpha).coerceIn(0.2f, 1f)
            drawCircle(
                color = if ((a + p) % 2 == 0) preset.primaryColor.copy(alpha = nodeAlpha) else preset.secondaryColor.copy(alpha = nodeAlpha),
                radius = (1.5f + (1f - frac) * 2.5f + bass * 2f).dp.toPx(),
                center = Offset(px, py)
            )
        }
    }

    drawPath(
        path = scratchPad.scratchPath1,
        color = preset.primaryColor.copy(alpha = alpha * 0.85f),
        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
    )
    drawPath(
        path = scratchPad.scratchPath2,
        color = preset.secondaryColor.copy(alpha = alpha * 0.85f),
        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
    )

    // Central Cardioid / Julia Singularity Glow
    val coreR = 18.dp.toPx() + (bass * 28.dp.toPx())
    drawCircle(
        brush = Brush.radialGradient(
            listOf(preset.primaryColor.copy(alpha = alpha * 0.8f * preset.glowIntensity), preset.secondaryColor.copy(alpha = alpha * 0.3f), Color.Transparent),
            center = center,
            radius = coreR * 2.2f
        ),
        radius = coreR * 2.2f,
        center = center
    )
}

// 22. DNA Double-Helix 3D Rotation
fun DrawScope.renderDnaHelixRotation(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val rungs = preset.particleCount.coerceIn(16, 64)
    val strandHeight = size.height * 0.82f
    val radius = minOf(size.width, size.height) * 0.28f * preset.sensitivity
    val rotSpeed = time * 1.4f * preset.motionSpeed
    val startY = center.y - strandHeight / 2f

    scratchPad.scratchPath1.reset()
    scratchPad.scratchPath2.reset()

    for (i in 0 until rungs) {
        val frac = i.toFloat() / (rungs - 1)
        val y = startY + frac * strandHeight
        val angle = (frac * 3.5 * PI) + rotSpeed

        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat() // Depth factor: -1 (back) to +1 (front)

        val x1 = center.x + cosA * radius
        val x2 = center.x - cosA * radius

        val fftIdx = (i * 2) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioBoost = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * preset.sensitivity else 0f

        // Base-pair rung connecting Strand 1 and Strand 2
        val rungDepthAlpha = (0.35f + 0.65f * ((sinA + 1f) / 2f)).coerceIn(0.1f, 1f) * alpha
        val rungWidth = (1.5f + (sinA + 1f) * 1.2f + audioBoost * 3f + bass * 2f).dp.toPx()
        val midX = (x1 + x2) / 2f

        // Hydrogen bond half 1 (Adenine / Guanine)
        drawLine(
            color = preset.primaryColor.copy(alpha = rungDepthAlpha),
            start = Offset(x1, y),
            end = Offset(midX, y),
            strokeWidth = rungWidth,
            cap = StrokeCap.Round
        )
        // Hydrogen bond half 2 (Thymine / Cytosine)
        drawLine(
            color = preset.secondaryColor.copy(alpha = rungDepthAlpha),
            start = Offset(midX, y),
            end = Offset(x2, y),
            strokeWidth = rungWidth,
            cap = StrokeCap.Round
        )

        // Center hydrogen bond gap / pulse dot
        if (audioBoost > 0.25f || bass > 0.4f) {
            drawCircle(
                color = Color.White.copy(alpha = (rungDepthAlpha * 1.2f).coerceAtMost(1f)),
                radius = (2.2f + audioBoost * 3f).dp.toPx(),
                center = Offset(midX, y)
            )
        }

        // Nucleotide nodes on backbone
        val node1Alpha = (0.4f + 0.6f * ((sinA + 1f) / 2f)).coerceIn(0.2f, 1f) * alpha
        val node2Alpha = (0.4f + 0.6f * ((-sinA + 1f) / 2f)).coerceIn(0.2f, 1f) * alpha
        val node1Radius = (3.5f + (sinA + 1f) * 1.8f + audioBoost * 2.5f).dp.toPx()
        val node2Radius = (3.5f + (-sinA + 1f) * 1.8f + audioBoost * 2.5f).dp.toPx()

        drawCircle(color = preset.primaryColor.copy(alpha = node1Alpha), radius = node1Radius, center = Offset(x1, y))
        drawCircle(color = preset.secondaryColor.copy(alpha = node2Alpha), radius = node2Radius, center = Offset(x2, y))

        if (i == 0) {
            scratchPad.scratchPath1.moveTo(x1, y)
            scratchPad.scratchPath2.moveTo(x2, y)
        } else {
            scratchPad.scratchPath1.lineTo(x1, y)
            scratchPad.scratchPath2.lineTo(x2, y)
        }
    }

    // Sugar-phosphate backbone continuous ribbons
    drawPath(
        path = scratchPad.scratchPath1,
        color = preset.primaryColor.copy(alpha = alpha * 0.9f),
        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = scratchPad.scratchPath2,
        color = preset.secondaryColor.copy(alpha = alpha * 0.9f),
        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

// 23. Wireframe Terrain Flyover (Classic Retro Synthwave Horizon)
fun DrawScope.renderWireframeTerrainFlyover(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val cols = preset.particleCount.coerceIn(12, 36)
    val rows = 14
    val horizonY = size.height * 0.38f
    val speed = (time * 1.6f * preset.motionSpeed) % 1f

    // Retro Neon Horizon Sun
    val sunRadius = minOf(size.width, size.height) * 0.16f
    drawCircle(
        brush = Brush.verticalGradient(
            listOf(preset.secondaryColor.copy(alpha = alpha * 0.95f), preset.primaryColor.copy(alpha = alpha * 0.7f), Color.Transparent),
            startY = horizonY - sunRadius * 1.5f,
            endY = horizonY
        ),
        radius = sunRadius,
        center = Offset(center.x, horizonY - sunRadius * 0.3f)
    )

    // Sun horizontal scanline bars
    val scanlines = 6
    for (s in 0 until scanlines) {
        val sy = horizonY - (s * (sunRadius / scanlines) * 0.75f)
        val barW = sqrt((sunRadius * sunRadius - (sy - (horizonY - sunRadius * 0.3f)).let { it * it }).coerceAtLeast(0f)) * 1.8f
        drawLine(
            color = Color.Black.copy(alpha = 0.75f),
            start = Offset(center.x - barW / 2f, sy),
            end = Offset(center.x + barW / 2f, sy),
            strokeWidth = 2.5.dp.toPx()
        )
    }

    // 3D Perspective Grid Points Calculation (reusing scratchPad flat buffers with zero allocation)
    val stride = cols + 1
    for (r in 0..rows) {
        val zNorm = ((r.toFloat() + speed) / rows).coerceIn(0.01f, 1f)
        val depth = zNorm * zNorm // Non-linear perspective depth
        if (r < scratchPad.terrainGridAlpha.size) {
            scratchPad.terrainGridAlpha[r] = (depth * alpha).coerceIn(0.05f, 1f)
        }

        val screenYBase = horizonY + depth * (size.height - horizonY)
        val totalWidth = size.width * (0.2f + depth * 1.1f)
        val startX = center.x - totalWidth / 2f
        val stepX = totalWidth / cols

        val rowOffset = r * stride
        for (c in 0..cols) {
            val fftIdx = (c * snapshot.fftMagnitudes.size / (cols + 1)).coerceIn(0, maxOf(0, snapshot.fftMagnitudes.size - 1))
            val audioVal = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] else 0f
            val mountainNoise = sin(c * 0.6f + r * 0.8f + time * 0.5f) * 0.35f
            val edgeBoost = (abs(c - cols / 2f) / (cols / 2f)).pow(1.5f) // Higher mountains on canyon flanks
            val heightOffset = (audioVal * 90.dp.toPx() * preset.sensitivity + bass * 35.dp.toPx()) * (0.25f + edgeBoost * 1.2f + mountainNoise) * depth

            val idx = rowOffset + c
            if (idx < scratchPad.terrainGridX.size) {
                scratchPad.terrainGridX[idx] = startX + c * stepX
                scratchPad.terrainGridY[idx] = screenYBase - heightOffset
            }
        }
    }

    // Transverse Wireframe Latitudes
    for (r in 0..rows) {
        scratchPad.scratchPath1.reset()
        val rowOffset = r * stride
        for (c in 0..cols) {
            val idx = rowOffset + c
            if (idx < scratchPad.terrainGridX.size) {
                if (c == 0) scratchPad.scratchPath1.moveTo(scratchPad.terrainGridX[idx], scratchPad.terrainGridY[idx])
                else scratchPad.scratchPath1.lineTo(scratchPad.terrainGridX[idx], scratchPad.terrainGridY[idx])
            }
        }
        val col = if (r % 2 == 0) preset.primaryColor else preset.secondaryColor
        val gAlpha = if (r < scratchPad.terrainGridAlpha.size) scratchPad.terrainGridAlpha[r] else alpha
        drawPath(
            path = scratchPad.scratchPath1,
            color = col.copy(alpha = gAlpha),
            style = Stroke(width = (1f + (gAlpha * 1.8f)).dp.toPx())
        )
    }

    // Longitudinal Wireframe Longitudes
    for (c in 0..cols) {
        scratchPad.scratchPath1.reset()
        val topIdx = c
        val bottomIdx = rows * stride + c
        if (topIdx < scratchPad.terrainGridX.size && bottomIdx < scratchPad.terrainGridX.size) {
            drawLine(
                color = preset.primaryColor.copy(alpha = alpha * 0.45f),
                start = Offset(scratchPad.terrainGridX[topIdx], scratchPad.terrainGridY[topIdx]),
                end = Offset(scratchPad.terrainGridX[bottomIdx], scratchPad.terrainGridY[bottomIdx]),
                strokeWidth = 1.2.dp.toPx()
            )
        }
    }
}

// 24. Moiré Interference Rings
fun DrawScope.renderMoireInterferenceRings(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val ringCount = preset.particleCount.coerceIn(24, 90)
    val maxRadius = minOf(size.width, size.height) * 0.58f

    // Drift centers producing optical Moiré fringes
    val driftDist = 32.dp.toPx() * preset.sensitivity + (bass * 25.dp.toPx())
    val c1 = Offset(
        center.x + cos(time * 0.9f * preset.motionSpeed) * driftDist,
        center.y + sin(time * 0.7f * preset.motionSpeed) * driftDist
    )
    val c2 = Offset(
        center.x - cos(time * 0.8f * preset.motionSpeed + 1.2f) * driftDist,
        center.y - sin(time * 1.1f * preset.motionSpeed + 0.8f) * driftDist
    )
    val c3 = Offset(
        center.x + sin(time * 0.5f) * (driftDist * 0.6f),
        center.y - cos(time * 0.6f) * (driftDist * 0.6f)
    )

    val spacing1 = (maxRadius / ringCount) * (1f + bass * 0.25f)
    val spacing2 = (maxRadius / ringCount) * (0.95f + (if (isPlaying) snapshot.midEnergy * 0.3f else 0f))

    // Set 1 Rings
    for (i in 1..ringCount) {
        val r = i * spacing1
        if (r > maxRadius * 1.2f) break
        val ringAlpha = ((1f - (r / (maxRadius * 1.2f))) * alpha * 0.85f).coerceIn(0.05f, 1f)
        drawCircle(
            color = preset.primaryColor.copy(alpha = ringAlpha),
            radius = r,
            center = c1,
            style = Stroke(width = 1.4.dp.toPx())
        )
    }

    // Set 2 Rings (Interfering)
    for (i in 1..ringCount) {
        val r = i * spacing2
        if (r > maxRadius * 1.2f) break
        val ringAlpha = ((1f - (r / (maxRadius * 1.2f))) * alpha * 0.85f).coerceIn(0.05f, 1f)
        drawCircle(
            color = preset.secondaryColor.copy(alpha = ringAlpha),
            radius = r,
            center = c2,
            style = Stroke(width = 1.4.dp.toPx())
        )
    }

    // Optional 3rd Harmonic Intersect
    if (preset.glowIntensity > 0.8f) {
        val spacing3 = (maxRadius / (ringCount * 0.75f))
        for (i in 1..(ringCount * 0.6f).toInt()) {
            val r = i * spacing3
            val ringAlpha = ((1f - (r / maxRadius)) * alpha * 0.4f).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = ringAlpha),
                radius = r,
                center = c3,
                style = Stroke(width = 1.0.dp.toPx())
            )
        }
    }

    // Center focal nodes
    drawCircle(color = preset.primaryColor.copy(alpha = alpha), radius = 5.dp.toPx(), center = c1)
    drawCircle(color = preset.secondaryColor.copy(alpha = alpha), radius = 5.dp.toPx(), center = c2)
}

// 25. Liquid Chrome Metaballs Morph
fun DrawScope.renderLiquidChromeMorph(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val mid = snapshot.midEnergy
    val blobCount = preset.particleCount.coerceIn(4, 14)
    val orbitR = minOf(size.width, size.height) * 0.22f

    // Update blob centers in scratchpad
    for (b in 0 until blobCount) {
        val speedMod = 0.8f + (b * 0.22f)
        val phase = time * speedMod * preset.motionSpeed + (b * 2 * PI / blobCount)
        val rOrbit = orbitR * (0.6f + 0.4f * sin(time * 0.5f + b))
        scratchPad.particleX[b] = center.x + cos(phase).toFloat() * rOrbit
        scratchPad.particleY[b] = center.y + sin(phase).toFloat() * rOrbit
    }

    // Render smooth liquid chrome metaball surfaces
    for (b in 0 until blobCount) {
        val bx = scratchPad.particleX[b]
        val by = scratchPad.particleY[b]
        val fftIdx = (b * 3) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioBoost = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 30.dp.toPx() * preset.sensitivity else 0f
        val baseR = (22.dp.toPx() + (b % 3) * 8.dp.toPx() + bass * 26.dp.toPx() + audioBoost)

        // Chrome Specular Radial Gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = alpha * 0.95f),
                    preset.primaryColor.copy(alpha = alpha * 0.85f),
                    preset.secondaryColor.copy(alpha = alpha * 0.75f),
                    Color(0xFF1A1A24).copy(alpha = alpha * 0.9f)
                ),
                center = Offset(bx - baseR * 0.32f, by - baseR * 0.32f),
                radius = baseR * 1.3f
            ),
            radius = baseR,
            center = Offset(bx, by)
        )

        // Chrome Rim Specular Highlight
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.9f),
            radius = baseR,
            center = Offset(bx, by),
            style = Stroke(width = 2.dp.toPx())
        )

        // Connecting liquid bridges between nearby metaballs
        for (other in (b + 1) until blobCount) {
            val ox = scratchPad.particleX[other]
            val oy = scratchPad.particleY[other]
            val dx = ox - bx
            val dy = oy - by
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < baseR * 2.8f && dist > 1f) {
                val bridgeAlpha = ((1f - dist / (baseR * 2.8f)) * alpha * 0.85f).coerceIn(0f, 1f)
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(preset.primaryColor.copy(alpha = bridgeAlpha), Color.White.copy(alpha = bridgeAlpha), preset.secondaryColor.copy(alpha = bridgeAlpha)),
                        start = Offset(bx, by),
                        end = Offset(ox, oy)
                    ),
                    start = Offset(bx, by),
                    end = Offset(ox, oy),
                    strokeWidth = (baseR * 0.55f * (1f - dist / (baseR * 2.8f))).coerceAtLeast(2f.dp.toPx()),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// 26. Beat Fireworks Burst & Pyrotechnics
fun DrawScope.renderBeatFireworksBurst(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val isBeat = isPlaying && bass > 0.48f
    val particleCount = preset.particleCount.coerceIn(36, 180)
    val shells = 4

    for (s in 0 until shells) {
        val shellPhase = ((time * 0.85f * preset.motionSpeed + (s * 0.33f)) % 1f)
        val shellCenter = Offset(
            center.x + sin(s * 2.1f + time * 0.2f) * (size.width * 0.28f),
            size.height * (0.28f + (s % 2) * 0.22f)
        )
        val gravity = 45.dp.toPx() * (shellPhase * shellPhase)
        val explosionRadius = shellPhase * minOf(size.width, size.height) * 0.35f * preset.sensitivity + (bass * 20.dp.toPx())
        val shellAlpha = ((1f - shellPhase) * alpha).coerceIn(0f, 1f)

        val sparksPerShell = particleCount / shells
        for (p in 0 until sparksPerShell) {
            val angle = (p * 2 * PI / sparksPerShell) + (s * 0.7f)
            val speedVar = 0.75f + (p % 4) * 0.12f
            val px = shellCenter.x + cos(angle).toFloat() * (explosionRadius * speedVar)
            val py = shellCenter.y + sin(angle).toFloat() * (explosionRadius * speedVar) + gravity

            val pxPrev = shellCenter.x + cos(angle).toFloat() * (explosionRadius * speedVar * 0.84f)
            val pyPrev = shellCenter.y + sin(angle).toFloat() * (explosionRadius * speedVar * 0.84f) + (gravity * 0.75f)

            val pCol = if ((p + s) % 3 == 0) preset.primaryColor else if ((p + s) % 3 == 1) preset.secondaryColor else Color.White

            // Spark Streamer Trail
            drawLine(
                color = pCol.copy(alpha = shellAlpha),
                start = Offset(pxPrev, pyPrev),
                end = Offset(px, py),
                strokeWidth = (2.2f * (1f - shellPhase)).coerceAtLeast(1f).dp.toPx(),
                cap = StrokeCap.Round
            )

            // Spark Head Glow
            drawCircle(
                color = Color.White.copy(alpha = shellAlpha),
                radius = (1.5f + (1f - shellPhase) * 1.8f).dp.toPx(),
                center = Offset(px, py)
            )
        }

        // Central Flash Shockwave on Detonation
        if (shellPhase < 0.25f || isBeat) {
            val flashAlpha = ((0.25f - shellPhase).coerceAtLeast(0f) * 4f * alpha).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = flashAlpha),
                radius = (12.dp.toPx() + (if (isBeat) bass * 25.dp.toPx() else 0f)),
                center = shellCenter
            )
        }
    }
}

// 27. Cellular Automaton Game of Life
fun DrawScope.renderCellularAutomatonLife(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val bass = snapshot.bassEnergy * preset.beatResponse
    val cols = preset.particleCount.coerceIn(16, 40)
    val rows = (cols * (size.height / size.width)).toInt().coerceIn(12, 28)
    val cellW = size.width / cols
    val cellH = size.height / rows

    val tick = (time * 6.0f * preset.motionSpeed).toInt()

    // Render alive active life cells
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val fftIdx = (c * snapshot.fftMagnitudes.size / cols).coerceIn(0, maxOf(0, snapshot.fftMagnitudes.size - 1))
            val audioVal = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * preset.sensitivity else 0f

            // Procedural Conway-like cellular automaton state rule
            val lifeSeed = sin(c * 1.3f + r * 1.7f + tick * 0.45f) * 0.5f + 0.5f
            val isAlive = lifeSeed > (0.62f - audioVal * 0.35f - bass * 0.25f)

            if (isAlive) {
                val cellAge = (sin(c * 0.8f + r * 0.9f + time * 2f) * 0.5f + 0.5f)
                val color = if ((c + r) % 2 == 0) preset.primaryColor else preset.secondaryColor
                val cellAlpha = (0.35f + cellAge * 0.65f + audioVal * 0.4f).coerceIn(0.1f, 1f) * alpha

                drawRoundRect(
                    color = color.copy(alpha = cellAlpha),
                    topLeft = Offset(c * cellW + 1.5.dp.toPx(), r * cellH + 1.5.dp.toPx()),
                    size = Size(cellW - 3.dp.toPx(), cellH - 3.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                // Neon active cell core
                if (audioVal > 0.35f || bass > 0.45f) {
                    drawCircle(
                        color = Color.White.copy(alpha = cellAlpha),
                        radius = (minOf(cellW, cellH) * 0.22f),
                        center = Offset(c * cellW + cellW / 2f, r * cellH + cellH / 2f)
                    )
                }
            }
        }
    }
}

// 28. Procedural Circuit Board & Pulses
fun DrawScope.renderCircuitBoardPulse(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val traces = preset.particleCount.coerceIn(12, 48)

    // Central Processor Chip
    val chipSize = 54.dp.toPx() + (bass * 20.dp.toPx())
    drawRoundRect(
        color = Color(0xFF0F172A).copy(alpha = alpha * 0.95f),
        topLeft = Offset(center.x - chipSize / 2f, center.y - chipSize / 2f),
        size = Size(chipSize, chipSize),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
    drawRoundRect(
        color = preset.primaryColor.copy(alpha = alpha * 0.85f),
        topLeft = Offset(center.x - chipSize / 2f + 4.dp.toPx(), center.y - chipSize / 2f + 4.dp.toPx()),
        size = Size(chipSize - 8.dp.toPx(), chipSize - 8.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    )

    // Procedural PCB Traces radiating outward with 45/90 degree bends
    for (i in 0 until traces) {
        val angle = (i * 2 * PI / traces)
        val fftIdx = (i * 2) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioBoost = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * preset.sensitivity else 0f

        val startX = center.x + cos(angle).toFloat() * (chipSize / 2f)
        val startY = center.y + sin(angle).toFloat() * (chipSize / 2f)

        val midLen = 40.dp.toPx() + (i % 5) * 20.dp.toPx()
        val midX = startX + cos(angle).toFloat() * midLen
        val midY = startY + sin(angle).toFloat() * midLen

        // 45-degree angle bend
        val bendAngle = angle + (if (i % 2 == 0) PI / 4 else -PI / 4)
        val endLen = 80.dp.toPx() + (i % 4) * 35.dp.toPx() + audioBoost * 40.dp.toPx()
        val endX = (midX + cos(bendAngle).toFloat() * endLen).coerceIn(16.dp.toPx(), size.width - 16.dp.toPx())
        val endY = (midY + sin(bendAngle).toFloat() * endLen).coerceIn(16.dp.toPx(), size.height - 16.dp.toPx())

        val traceColor = if (i % 2 == 0) preset.primaryColor else preset.secondaryColor

        // Copper Trace Path
        scratchPad.scratchPath1.reset()
        scratchPad.scratchPath1.moveTo(startX, startY)
        scratchPad.scratchPath1.lineTo(midX, midY)
        scratchPad.scratchPath1.lineTo(endX, endY)

        drawPath(
            path = scratchPad.scratchPath1,
            color = traceColor.copy(alpha = alpha * 0.45f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Via Terminal Solder Pad
        drawCircle(color = traceColor.copy(alpha = alpha * 0.8f), radius = 3.5.dp.toPx(), center = Offset(endX, endY))
        drawCircle(color = Color.Black.copy(alpha = alpha), radius = 1.8.dp.toPx(), center = Offset(endX, endY))

        // Data Pulse Packet traveling along trace
        val pulseProgress = ((time * 1.6f * preset.motionSpeed + (i * 0.18f)) % 1f)
        val (pulseX, pulseY) = if (pulseProgress < 0.5f) {
            val frac = pulseProgress * 2f
            Pair(startX + (midX - startX) * frac, startY + (midY - startY) * frac)
        } else {
            val frac = (pulseProgress - 0.5f) * 2f
            Pair(midX + (endX - midX) * frac, midY + (endY - midY) * frac)
        }

        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.95f),
            radius = (2.2f + audioBoost * 2.5f + bass * 1.5f).dp.toPx(),
            center = Offset(pulseX, pulseY)
        )
    }
}

// 29. Solar Flare Plasma Sphere & Coronal Loops
fun DrawScope.renderSolarFlarePlasma(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val sunRadius = minOf(size.width, size.height) * 0.22f + (bass * 20.dp.toPx())
    val loops = preset.particleCount.coerceIn(8, 36)

    // Outer Solar Corona Atmosphere
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = alpha * 0.9f),
                preset.primaryColor.copy(alpha = alpha * 0.85f),
                preset.secondaryColor.copy(alpha = alpha * 0.4f),
                Color.Transparent
            ),
            center = center,
            radius = sunRadius * 2.2f
        ),
        radius = sunRadius * 2.2f,
        center = center
    )

    // Thermonuclear Sun Core Disk
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = alpha), preset.primaryColor.copy(alpha = alpha * 0.95f)),
            center = center,
            radius = sunRadius
        ),
        radius = sunRadius,
        center = center
    )

    // Coronal Magnetic Prominence Loops arching off the limb
    for (l in 0 until loops) {
        val baseAngle = (l * 2 * PI / loops) + (time * 0.25f * preset.motionSpeed)
        val fftIdx = (l * 3) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioBoost = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 50.dp.toPx() * preset.sensitivity else 0f
        val loopHeight = 25.dp.toPx() + audioBoost + (bass * 35.dp.toPx())

        val a1 = baseAngle - 0.18
        val a2 = baseAngle + 0.18
        val p1 = Offset(center.x + cos(a1).toFloat() * sunRadius, center.y + sin(a1).toFloat() * sunRadius)
        val p2 = Offset(center.x + cos(a2).toFloat() * sunRadius, center.y + sin(a2).toFloat() * sunRadius)
        val apex = Offset(
            center.x + cos(baseAngle).toFloat() * (sunRadius + loopHeight),
            center.y + sin(baseAngle).toFloat() * (sunRadius + loopHeight)
        )

        scratchPad.scratchPath1.reset()
        scratchPad.scratchPath1.moveTo(p1.x, p1.y)
        scratchPad.scratchPath1.quadraticTo(apex.x, apex.y, p2.x, p2.y)

        val loopCol = if (l % 2 == 0) preset.secondaryColor else Color.White
        drawPath(
            path = scratchPad.scratchPath1,
            color = loopCol.copy(alpha = alpha * 0.85f),
            style = Stroke(width = (2.2f + audioBoost * 0.08f).dp.toPx(), cap = StrokeCap.Round)
        )
    }

    // High-Energy Flare Eruption Jets on heavy bass drops
    if (bass > 0.42f) {
        val flares = 6
        for (f in 0 until flares) {
            val fAngle = (f * 2 * PI / flares) + (time * 0.6f)
            val fLen = sunRadius + (bass * 75.dp.toPx())
            drawLine(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = alpha), preset.primaryColor.copy(alpha = alpha * 0.8f), Color.Transparent),
                    center = center,
                    radius = fLen
                ),
                start = Offset(center.x + cos(fAngle).toFloat() * sunRadius, center.y + sin(fAngle).toFloat() * sunRadius),
                end = Offset(center.x + cos(fAngle).toFloat() * fLen, center.y + sin(fAngle).toFloat() * fLen),
                strokeWidth = (4.dp.toPx() + bass * 4.dp.toPx()),
                cap = StrokeCap.Round
            )
        }
    }
}

// 30. Stained Glass Shatter & Faceted Mosaic
fun DrawScope.renderStainedGlassShatter(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val rings = 4
    val sectors = preset.particleCount.coerceIn(8, 24)
    val maxRadius = minOf(size.width, size.height) * 0.46f

    // Concentric Faceted Jewel Panes
    for (r in 0 until rings) {
        val rInner = (r.toFloat() / rings) * maxRadius
        val rOuter = ((r + 1).toFloat() / rings) * maxRadius
        val rotOffset = (r * 0.15f) + (time * 0.12f * (if (r % 2 == 0) 1f else -1f) * preset.motionSpeed)

        for (s in 0 until sectors) {
            val a1 = (s * 2 * PI / sectors) + rotOffset
            val a2 = ((s + 1) * 2 * PI / sectors) + rotOffset

            val fftIdx = (r * sectors + s) % maxOf(1, snapshot.fftMagnitudes.size)
            val audioVal = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * preset.sensitivity else 0f

            val p1 = Offset(center.x + cos(a1).toFloat() * rInner, center.y + sin(a1).toFloat() * rInner)
            val p2 = Offset(center.x + cos(a2).toFloat() * rInner, center.y + sin(a2).toFloat() * rInner)
            val p3 = Offset(center.x + cos(a2).toFloat() * rOuter, center.y + sin(a2).toFloat() * rOuter)
            val p4 = Offset(center.x + cos(a1).toFloat() * rOuter, center.y + sin(a1).toFloat() * rOuter)

            scratchPad.scratchPath1.reset()
            scratchPad.scratchPath1.moveTo(p1.x, p1.y)
            scratchPad.scratchPath1.lineTo(p2.x, p2.y)
            scratchPad.scratchPath1.lineTo(p3.x, p3.y)
            scratchPad.scratchPath1.lineTo(p4.x, p4.y)
            scratchPad.scratchPath1.close()

            val jewelColor = when ((r * sectors + s) % 7) {
                0 -> preset.primaryColor
                1 -> preset.secondaryColor
                2 -> Color(0xFFE11D48) // Ruby
                3 -> Color(0xFF2563EB) // Sapphire
                4 -> Color(0xFF059669) // Emerald
                5 -> Color(0xFFD97706) // Amber
                else -> Color(0xFF7C3AED) // Amethyst
            }
            val paneAlpha = (0.4f + audioVal * 0.6f + bass * 0.3f).coerceIn(0.15f, 1f) * alpha

            // Glass Pane Fill
            drawPath(
                path = scratchPad.scratchPath1,
                color = jewelColor.copy(alpha = paneAlpha)
            )

            // Heavy Leaded Caming Borders
            drawPath(
                path = scratchPad.scratchPath1,
                color = Color(0xFF111827).copy(alpha = alpha * 0.95f),
                style = Stroke(width = 2.2.dp.toPx(), join = StrokeJoin.Round)
            )
        }
    }

    // Central Rosette Hub
    drawCircle(
        color = Color.White.copy(alpha = alpha * 0.9f),
        radius = 12.dp.toPx() + (bass * 16.dp.toPx()),
        center = center
    )
    drawCircle(
        color = Color(0xFF111827).copy(alpha = alpha),
        radius = 12.dp.toPx() + (bass * 16.dp.toPx()),
        center = center,
        style = Stroke(width = 2.5.dp.toPx())
    )
}

// 31. Wormhole Portal & Vortex Rings
fun DrawScope.renderWormholePortalRing(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val ringCount = preset.particleCount.coerceIn(10, 50)
    val maxRadius = minOf(size.width, size.height) * 0.54f
    val speed = (time * 1.2f * preset.motionSpeed)

    // Inward Precessing Relativistic Rings
    for (i in 0 until ringCount) {
        val progress = ((i + speed) % ringCount) / ringCount
        val depth = (1f - progress).pow(2.2f) // Rapid acceleration into portal throat
        val r = depth * maxRadius + 6.dp.toPx() + (bass * 12.dp.toPx() * (1f - depth))

        val tiltAngle = (time * 0.4f) + (i * 0.15f)
        val ecc = 0.45f + 0.35f * sin(time * 0.5f + i * 0.2f) // Elliptical eccentricity

        scratchPad.scratchPath1.reset()
        val segments = 36
        for (s in 0..segments) {
            val theta = s * 2 * PI / segments
            val rx = cos(theta) * r
            val ry = sin(theta) * (r * ecc)
            val rotX = rx * cos(tiltAngle) - ry * sin(tiltAngle)
            val rotY = rx * sin(tiltAngle) + ry * cos(tiltAngle)

            val px = center.x + rotX.toFloat()
            val py = center.y + rotY.toFloat()
            if (s == 0) scratchPad.scratchPath1.moveTo(px, py)
            else scratchPad.scratchPath1.lineTo(px, py)
        }
        scratchPad.scratchPath1.close()

        val ringAlpha = ((progress) * alpha * 0.9f).coerceIn(0.1f, 1f)
        val color = if (i % 2 == 0) preset.primaryColor else preset.secondaryColor

        drawPath(
            path = scratchPad.scratchPath1,
            color = color.copy(alpha = ringAlpha),
            style = Stroke(width = (1.5f + progress * 3.5f).dp.toPx())
        )
    }

    // Portal Throat Singularity
    drawCircle(
        color = Color.Black.copy(alpha = alpha),
        radius = 16.dp.toPx(),
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = alpha * 0.9f),
        radius = 16.dp.toPx(),
        center = center,
        style = Stroke(width = (2.5f + bass * 3f).dp.toPx())
    )
}

// 32. Neural Network & Synapse Action Pulses
fun DrawScope.renderNeuralNetworkPulse(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val bass = snapshot.bassEnergy * preset.beatResponse
    val layers = 4
    val layerSpacing = size.width / (layers + 1)

    var nodeIdx = 0
    // Position network nodes in scratchpad
    for (l in 0 until layers) {
        val count = when (l) { 0 -> 4; 1 -> 6; 2 -> 6; else -> 4 }
        val x = layerSpacing * (l + 1)
        val nodeSpacing = size.height / (count + 1)
        for (n in 0 until count) {
            val y = nodeSpacing * (n + 1)
            scratchPad.neuralNodeX[nodeIdx] = x
            scratchPad.neuralNodeY[nodeIdx] = y
            scratchPad.neuralNodeLayer[nodeIdx] = l
            nodeIdx++
        }
    }
    val totalNodes = nodeIdx

    // Draw Synaptic Axon Connections between consecutive layers
    for (i in 0 until totalNodes) {
        val l1 = scratchPad.neuralNodeLayer[i]
        val x1 = scratchPad.neuralNodeX[i]
        val y1 = scratchPad.neuralNodeY[i]

        for (j in 0 until totalNodes) {
            val l2 = scratchPad.neuralNodeLayer[j]
            if (l2 == l1 + 1) {
                val x2 = scratchPad.neuralNodeX[j]
                val y2 = scratchPad.neuralNodeY[j]

                drawLine(
                    color = preset.primaryColor.copy(alpha = alpha * 0.28f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 1.2.dp.toPx()
                )

                // Synaptic Action-Potential Pulse Dot
                val pulseProg = ((time * 1.8f * preset.motionSpeed + (i * 0.23f + j * 0.17f)) % 1f)
                val px = x1 + (x2 - x1) * pulseProg
                val py = y1 + (y2 - y1) * pulseProg

                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.85f),
                    radius = (2.dp.toPx() + bass * 1.5.dp.toPx()),
                    center = Offset(px, py)
                )
            }
        }
    }

    // Draw Neural Soma Nodes
    for (i in 0 until totalNodes) {
        val x = scratchPad.neuralNodeX[i]
        val y = scratchPad.neuralNodeY[i]
        val fftIdx = (i * 2) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioVal = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * preset.sensitivity else 0f
        val nodeRadius = (6.dp.toPx() + audioVal * 12.dp.toPx() + bass * 4.dp.toPx())

        drawCircle(
            color = if (i % 2 == 0) preset.primaryColor.copy(alpha = alpha * 0.9f) else preset.secondaryColor.copy(alpha = alpha * 0.9f),
            radius = nodeRadius,
            center = Offset(x, y)
        )
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = nodeRadius * 0.45f,
            center = Offset(x, y)
        )
    }
}

// 33. Organic Ink Drop & Water Marbling Diffusion
fun DrawScope.renderInkDropDiffusion(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val dropCount = preset.particleCount.coerceIn(4, 16)
    val maxRadius = minOf(size.width, size.height) * 0.45f

    for (d in 0 until dropCount) {
        val dropPhase = ((time * 0.45f * preset.motionSpeed + (d.toFloat() / dropCount)) % 1f)
        val rBase = dropPhase * maxRadius + (bass * 25.dp.toPx())
        val dCenter = Offset(
            center.x + sin(d * 2.3f + time * 0.2f) * (45.dp.toPx()),
            center.y + cos(d * 1.7f + time * 0.25f) * (45.dp.toPx())
        )

        val fftIdx = (d * 3) % maxOf(1, snapshot.fftMagnitudes.size)
        val audioBoost = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 28.dp.toPx() * preset.sensitivity else 0f

        scratchPad.scratchPath1.reset()
        val petals = 12
        for (p in 0..petals) {
            val angle = p * 2 * PI / petals
            val turbulence = sin(angle * 3.0 + time * 1.2f + d).toFloat() * 12.dp.toPx() +
                    cos(angle * 5.0 - time * 0.8f).toFloat() * 6.dp.toPx()
            val r = (rBase + turbulence + audioBoost).coerceAtLeast(4f)
            val px = dCenter.x + cos(angle).toFloat() * r
            val py = dCenter.y + sin(angle).toFloat() * r

            if (p == 0) scratchPad.scratchPath1.moveTo(px, py)
            else scratchPad.scratchPath1.lineTo(px, py)
        }
        scratchPad.scratchPath1.close()

        val dropAlpha = ((1f - dropPhase) * alpha * 0.7f).coerceIn(0.05f, 1f)
        val dropColor = if (d % 2 == 0) preset.primaryColor else preset.secondaryColor

        drawPath(
            path = scratchPad.scratchPath1,
            color = dropColor.copy(alpha = dropAlpha)
        )
        drawPath(
            path = scratchPad.scratchPath1,
            color = Color.White.copy(alpha = dropAlpha * 0.6f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

// 34. Vibrating String Physical Harmonics
fun DrawScope.renderVibratingStringHarmonics(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val bass = snapshot.bassEnergy * preset.beatResponse
    val stringCount = preset.particleCount.coerceIn(3, 10)
    val stringSpacing = size.height / (stringCount + 1)
    val steps = 80
    val stepX = size.width / steps

    for (s in 0 until stringCount) {
        val baseY = stringSpacing * (s + 1)
        val fftIdx = (s * snapshot.fftMagnitudes.size / stringCount).coerceIn(0, maxOf(0, snapshot.fftMagnitudes.size - 1))
        val audioAmp = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 40.dp.toPx() * preset.sensitivity else 0f
        val mode = (s % 4) + 1 // Standing wave harmonic mode (fundamental, 2nd, 3rd, 4th harmonic)

        scratchPad.scratchPath1.reset()
        for (i in 0..steps) {
            val x = i * stepX
            val xNorm = i.toFloat() / steps
            // Standing wave physics formula: y(x, t) = A * sin(n * pi * x / L) * cos(omega * t)
            val standingWave = sin(mode * PI * xNorm) * cos(time * (4f + s * 1.5f) * preset.motionSpeed)
            val displacement = standingWave.toFloat() * (12.dp.toPx() + audioAmp + bass * 18.dp.toPx())
            val y = baseY + displacement

            if (i == 0) scratchPad.scratchPath1.moveTo(x, y)
            else scratchPad.scratchPath1.lineTo(x, y)
        }

        val strCol = if (s % 2 == 0) preset.primaryColor else preset.secondaryColor

        // Main Vibrating String
        drawPath(
            path = scratchPad.scratchPath1,
            color = strCol.copy(alpha = alpha * 0.95f),
            style = Stroke(width = (2.2.dp.toPx() + audioAmp * 0.08f), cap = StrokeCap.Round)
        )

        // Antinode Luminous Envelope Glow
        drawPath(
            path = scratchPad.scratchPath1,
            color = Color.White.copy(alpha = alpha * 0.35f * preset.glowIntensity),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// 35. Origami Geometric 3D Polygon Unfolding
fun DrawScope.renderOrigamiGeometricUnfold(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val mid = snapshot.midEnergy
    val facets = preset.particleCount.coerceIn(6, 24)
    val radius = minOf(size.width, size.height) * 0.36f * preset.sensitivity
    val foldAngle = (sin(time * 1.4f * preset.motionSpeed + mid * 2f) * 0.5f + 0.5f) // Crease fold angle

    for (f in 0 until facets) {
        val a1 = (f * 2 * PI / facets) + (time * 0.2f)
        val a2 = ((f + 1) * 2 * PI / facets) + (time * 0.2f)

        val rOuter = radius * (1f + bass * 0.3f)
        val rFold = radius * foldAngle * 0.7f

        val p0 = center
        val p1 = Offset(center.x + cos(a1).toFloat() * rFold, center.y + sin(a1).toFloat() * rFold)
        val p2 = Offset(center.x + cos((a1 + a2) / 2).toFloat() * rOuter, center.y + sin((a1 + a2) / 2).toFloat() * rOuter)
        val p3 = Offset(center.x + cos(a2).toFloat() * rFold, center.y + sin(a2).toFloat() * rFold)

        scratchPad.scratchPath1.reset()
        scratchPad.scratchPath1.moveTo(p0.x, p0.y)
        scratchPad.scratchPath1.lineTo(p1.x, p1.y)
        scratchPad.scratchPath1.lineTo(p2.x, p2.y)
        scratchPad.scratchPath1.close()

        scratchPad.scratchPath2.reset()
        scratchPad.scratchPath2.moveTo(p0.x, p0.y)
        scratchPad.scratchPath2.lineTo(p2.x, p2.y)
        scratchPad.scratchPath2.lineTo(p3.x, p3.y)
        scratchPad.scratchPath2.close()

        val facetCol1 = if (f % 2 == 0) preset.primaryColor else preset.secondaryColor
        val facetCol2 = if (f % 2 == 0) preset.secondaryColor else preset.primaryColor

        // Faceted Origami Shading
        drawPath(path = scratchPad.scratchPath1, color = facetCol1.copy(alpha = alpha * (0.6f + foldAngle * 0.3f)))
        drawPath(path = scratchPad.scratchPath2, color = facetCol2.copy(alpha = alpha * (0.45f + (1f - foldAngle) * 0.3f)))

        // Glowing Crease Lines
        drawPath(path = scratchPad.scratchPath1, color = Color.White.copy(alpha = alpha * 0.9f), style = scratchPad.getStroke(1.5.dp.toPx()))
        drawPath(path = scratchPad.scratchPath2, color = Color.White.copy(alpha = alpha * 0.9f), style = scratchPad.getStroke(1.5.dp.toPx()))
    }
}

// 36. Supernova Radial Shockwave Explosion
fun DrawScope.renderSupernovaRadialBurst(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val shockwaves = 4
    val maxRadius = minOf(size.width, size.height) * 0.62f
    val debrisCount = preset.particleCount.coerceIn(24, 96)

    // Concentric Relativistic Shockwave Fronts
    for (s in 0 until shockwaves) {
        val sPhase = ((time * 1.4f * preset.motionSpeed + (s.toFloat() / shockwaves)) % 1f)
        val r = sPhase * maxRadius + (bass * 30.dp.toPx())
        val sAlpha = ((1f - sPhase) * alpha * 0.9f).coerceIn(0f, 1f)

        drawCircle(
            color = if (s % 2 == 0) preset.primaryColor.copy(alpha = sAlpha) else preset.secondaryColor.copy(alpha = sAlpha),
            radius = r,
            center = center,
            style = Stroke(width = (4f * (1f - sPhase) + 1f).dp.toPx())
        )
    }

    // High-Energy Debris Particles & Plasma Filaments
    for (p in 0 until debrisCount) {
        val angle = (p * 2 * PI / debrisCount)
        val speedVar = 0.6f + (p % 5) * 0.12f
        val pPhase = ((time * 1.6f * preset.motionSpeed + (p * 0.08f)) % 1f)
        val r = pPhase * maxRadius * speedVar + (bass * 25.dp.toPx())
        val pAlpha = ((1f - pPhase) * alpha).coerceIn(0.1f, 1f)

        val px = center.x + cos(angle).toFloat() * r
        val py = center.y + sin(angle).toFloat() * r

        drawCircle(
            color = if (p % 2 == 0) Color.White.copy(alpha = pAlpha) else preset.primaryColor.copy(alpha = pAlpha),
            radius = (1.8f + (1f - pPhase) * 2.5f).dp.toPx(),
            center = Offset(px, py)
        )
    }

    // Central Relativistic Gamma-Ray Polar Jet Beams
    val jetLen = maxRadius * (0.7f + bass * 0.5f)
    drawLine(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = alpha), Color.Transparent), center = center, radius = jetLen),
        start = Offset(center.x, center.y - jetLen),
        end = Offset(center.x, center.y + jetLen),
        strokeWidth = (4.dp.toPx() + bass * 6.dp.toPx()),
        cap = StrokeCap.Round
    )

    // Detonation Stellar Core
    val coreR = 14.dp.toPx() + (bass * 30.dp.toPx())
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = coreR,
        center = center
    )
}
