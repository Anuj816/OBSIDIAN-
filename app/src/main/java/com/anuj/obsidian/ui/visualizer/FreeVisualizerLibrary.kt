package com.anuj.obsidian.ui.visualizer

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class FreeVisualizerType(val id: Int, val displayName: String) {
    SPECTRUM_BARS(1001, "Spectrum Bars"),
    WAVEFORM_FLOW(1002, "Waveform Flow"),
    CIRCULAR_PULSE(1003, "Circular Pulse"),
    PARTICLE_BURST(1004, "Particle Burst"),
    NEON_GRID(1005, "Neon Grid"),
    AUDIO_SPECTRUM(1006, "Audio Spectrum")
}

interface FreeVisualizer {
    fun render(canvas: Canvas, waveform: ByteArray?, spectrum: ByteArray?)
    fun renderInCompose(drawScope: DrawScope, waveform: ByteArray?, spectrum: ByteArray?, primary: Color, secondary: Color, time: Float)
    fun setColors(primary: Color, secondary: Color, tertiary: Color)
    fun setSpeed(speed: Float)
}

class SpectrumBarsVisualizer : FreeVisualizer {
    private var primaryColor: Color = Color(0xFFE040FB)
    private var secondaryColor: Color = Color(0xFF00E5FF)
    private var speed: Float = 1f
    private val paint = Paint().apply { isAntiAlias = true }

    override fun render(canvas: Canvas, waveform: ByteArray?, spectrum: ByteArray?) {
        val data = spectrum ?: waveform ?: return
        if (data.isEmpty()) return
        val count = minOf(data.size, 64)
        val barWidth = canvas.width.toFloat() / count
        paint.setARGB(
            (primaryColor.alpha * 255).toInt(),
            (primaryColor.red * 255).toInt(),
            (primaryColor.green * 255).toInt(),
            (primaryColor.blue * 255).toInt()
        )

        for (i in 0 until count) {
            val raw = data[i].toInt() and 0xFF
            val height = (raw / 255f) * canvas.height * 0.85f * speed
            val left = i * barWidth + barWidth * 0.1f
            val right = (i + 1) * barWidth - barWidth * 0.1f
            val top = canvas.height - height
            val bottom = canvas.height.toFloat()
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }

    override fun renderInCompose(
        drawScope: DrawScope,
        waveform: ByteArray?,
        spectrum: ByteArray?,
        primary: Color,
        secondary: Color,
        time: Float
    ) {
        val w = drawScope.size.width
        val h = drawScope.size.height
        val data = spectrum ?: waveform
        val count = 48
        val barWidth = w / count
        for (i in 0 until count) {
            val raw = if (data != null && data.isNotEmpty()) {
                (data[i % data.size].toInt() and 0xFF) / 255f
            } else {
                (sin(time * 0.05f + i * 0.2f) * 0.5f + 0.5f) * 0.6f
            }
            val barH = (raw * h * 0.75f).coerceIn(4f, h)
            val barColor = if (i % 2 == 0) primary else secondary
            drawScope.drawRect(
                color = barColor,
                topLeft = Offset(i * barWidth + barWidth * 0.12f, h - barH),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.76f, barH)
            )
        }
    }

    override fun setColors(primary: Color, secondary: Color, tertiary: Color) {
        this.primaryColor = primary
        this.secondaryColor = secondary
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
    }
}

class WaveformFlowVisualizer : FreeVisualizer {
    private var primaryColor: Color = Color(0xFF00E676)
    private var secondaryColor: Color = Color(0xFF1DE9B6)
    private var speed: Float = 1f
    private val canvasPath = Path()
    private val composePath = androidx.compose.ui.graphics.Path()
    private val paint = Paint().apply {
        color = AndroidColor.argb(230, 0, 230, 118)
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    override fun render(canvas: Canvas, waveform: ByteArray?, spectrum: ByteArray?) {
        val data = waveform ?: spectrum ?: return
        if (data.isEmpty()) return
        canvasPath.reset()
        val stepX = canvas.width.toFloat() / data.size
        val midY = canvas.height / 2f

        for (i in data.indices) {
            val sample = ((data[i].toInt() and 0xFF) - 128) / 128f
            val x = i * stepX
            val y = midY + sample * (canvas.height * 0.4f) * speed
            if (i == 0) canvasPath.moveTo(x, y) else canvasPath.lineTo(x, y)
        }
        canvas.drawPath(canvasPath, paint)
    }

    override fun renderInCompose(
        drawScope: DrawScope,
        waveform: ByteArray?,
        spectrum: ByteArray?,
        primary: Color,
        secondary: Color,
        time: Float
    ) {
        val w = drawScope.size.width
        val h = drawScope.size.height
        val midY = h / 2f
        composePath.reset()
        val points = 64
        val stepX = w / points

        for (i in 0..points) {
            val sample = if (waveform != null && waveform.isNotEmpty()) {
                ((waveform[i % waveform.size].toInt() and 0xFF) - 128) / 128f
            } else {
                sin(time * 0.08f + i * 0.25f) * 0.4f
            }
            val x = i * stepX
            val y = midY + sample * (h * 0.35f)
            if (i == 0) composePath.moveTo(x, y) else composePath.lineTo(x, y)
        }

        drawScope.drawPath(
            path = composePath,
            color = primary,
            style = Stroke(width = 4f)
        )
    }

    override fun setColors(primary: Color, secondary: Color, tertiary: Color) {
        this.primaryColor = primary
        this.secondaryColor = secondary
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
    }
}

class CircularPulseVisualizer : FreeVisualizer {
    private var primaryColor: Color = Color(0xFF00E5FF)
    private var secondaryColor: Color = Color(0xFFFF4081)
    private var speed: Float = 1f
    private val paint = Paint().apply {
        color = AndroidColor.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    override fun render(canvas: Canvas, waveform: ByteArray?, spectrum: ByteArray?) {
        val centerX = canvas.width / 2f
        val centerY = canvas.height / 2f
        val maxRadius = minOf(centerX, centerY) * 0.8f

        spectrum?.let {
            for (i in 0 until minOf(it.size, 32)) {
                val radius = ((it[i].toInt() and 0xFF) / 255f) * maxRadius * speed
                canvas.drawCircle(centerX, centerY, radius.coerceAtLeast(10f), paint)
            }
        }
    }

    override fun renderInCompose(
        drawScope: DrawScope,
        waveform: ByteArray?,
        spectrum: ByteArray?,
        primary: Color,
        secondary: Color,
        time: Float
    ) {
        val center = Offset(drawScope.size.width / 2f, drawScope.size.height / 2f)
        val maxRadius = minOf(drawScope.size.width, drawScope.size.height) * 0.4f

        for (ring in 1..4) {
            val pulse = if (spectrum != null && spectrum.isNotEmpty()) {
                (spectrum[ring % spectrum.size].toInt() and 0xFF) / 255f
            } else {
                (sin(time * 0.06f + ring * 0.8f) * 0.5f + 0.5f) * 0.7f
            }
            val radius = maxRadius * (ring / 4f) * (0.8f + pulse * 0.4f)
            val ringColor = if (ring % 2 == 0) primary else secondary
            drawScope.drawCircle(
                color = ringColor.copy(alpha = 0.75f - ring * 0.12f),
                radius = radius,
                center = center,
                style = Stroke(width = 3.5f)
            )
        }
    }

    override fun setColors(primary: Color, secondary: Color, tertiary: Color) {
        this.primaryColor = primary
        this.secondaryColor = secondary
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
    }
}

class ParticleBurstVisualizer : FreeVisualizer {
    private var primaryColor: Color = Color(0xFFFFD700)
    private var secondaryColor: Color = Color(0xFFFF5722)
    private var speed: Float = 1f
    private val paint = Paint().apply {
        color = AndroidColor.YELLOW
        isAntiAlias = true
    }

    override fun render(canvas: Canvas, waveform: ByteArray?, spectrum: ByteArray?) {
        val centerX = canvas.width / 2f
        val centerY = canvas.height / 2f
        val count = 36
        for (i in 0 until count) {
            val angle = (i * 2 * PI / count).toFloat()
            val dist = minOf(centerX, centerY) * 0.6f
            val x = centerX + cos(angle) * dist
            val y = centerY + sin(angle) * dist
            canvas.drawCircle(x, y, 6f, paint)
        }
    }

    override fun renderInCompose(
        drawScope: DrawScope,
        waveform: ByteArray?,
        spectrum: ByteArray?,
        primary: Color,
        secondary: Color,
        time: Float
    ) {
        val center = Offset(drawScope.size.width / 2f, drawScope.size.height / 2f)
        val count = 36
        val maxDist = minOf(drawScope.size.width, drawScope.size.height) * 0.42f

        for (i in 0 until count) {
            val angle = (i * 2 * PI / count + time * 0.02f).toFloat()
            val energy = if (spectrum != null && spectrum.isNotEmpty()) {
                (spectrum[i % spectrum.size].toInt() and 0xFF) / 255f
            } else {
                (sin(time * 0.05f + i * 0.3f) * 0.5f + 0.5f)
            }
            val dist = maxDist * (0.3f + energy * 0.7f)
            val pos = Offset(center.x + cos(angle) * dist, center.y + sin(angle) * dist)
            drawScope.drawCircle(
                color = if (i % 2 == 0) primary else secondary,
                radius = 4f + energy * 5f,
                center = pos
            )
        }
    }

    override fun setColors(primary: Color, secondary: Color, tertiary: Color) {
        this.primaryColor = primary
        this.secondaryColor = secondary
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
    }
}

class NeonGridVisualizer : FreeVisualizer {
    private var primaryColor: Color = Color(0xFFFF0055)
    private var secondaryColor: Color = Color(0xFF00FFEE)
    private var speed: Float = 1f
    private val paint = Paint().apply {
        color = AndroidColor.MAGENTA
        strokeWidth = 2f
        isAntiAlias = true
    }

    override fun render(canvas: Canvas, waveform: ByteArray?, spectrum: ByteArray?) {
        val rows = 8
        val cols = 8
        val stepX = canvas.width.toFloat() / cols
        val stepY = canvas.height.toFloat() / rows

        for (r in 0..rows) {
            canvas.drawLine(0f, r * stepY, canvas.width.toFloat(), r * stepY, paint)
        }
        for (c in 0..cols) {
            canvas.drawLine(c * stepX, 0f, c * stepX, canvas.height.toFloat(), paint)
        }
    }

    override fun renderInCompose(
        drawScope: DrawScope,
        waveform: ByteArray?,
        spectrum: ByteArray?,
        primary: Color,
        secondary: Color,
        time: Float
    ) {
        val w = drawScope.size.width
        val h = drawScope.size.height
        val rows = 10
        val cols = 8
        val stepX = w / cols
        val stepY = h / rows
        val alphaBase = 0.25f + (sin(time * 0.04f) * 0.15f)

        for (r in 0..rows) {
            val y = r * stepY
            drawScope.drawLine(
                color = secondary.copy(alpha = alphaBase),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5f
            )
        }

        for (c in 0..cols) {
            val x = c * stepX
            drawScope.drawLine(
                color = primary.copy(alpha = alphaBase),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.5f
            )
        }
    }

    override fun setColors(primary: Color, secondary: Color, tertiary: Color) {
        this.primaryColor = primary
        this.secondaryColor = secondary
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
    }
}

class AudioSpectrumVisualizer : FreeVisualizer {
    private var primaryColor: Color = Color(0xFF7C4DFF)
    private var secondaryColor: Color = Color(0xFFFF5252)
    private var speed: Float = 1f
    private val paint = Paint().apply {
        color = AndroidColor.CYAN
        isAntiAlias = true
    }

    override fun render(canvas: Canvas, waveform: ByteArray?, spectrum: ByteArray?) {
        val data = spectrum ?: return
        val count = minOf(data.size, 32)
        val barWidth = canvas.width.toFloat() / count

        for (i in 0 until count) {
            val height = ((data[i].toInt() and 0xFF) / 255f) * canvas.height * 0.7f
            canvas.drawRect(
                i * barWidth,
                canvas.height - height,
                (i + 1) * barWidth - 2f,
                canvas.height.toFloat(),
                paint
            )
        }
    }

    override fun renderInCompose(
        drawScope: DrawScope,
        waveform: ByteArray?,
        spectrum: ByteArray?,
        primary: Color,
        secondary: Color,
        time: Float
    ) {
        val w = drawScope.size.width
        val h = drawScope.size.height
        val count = 32
        val barWidth = w / count

        for (i in 0 until count) {
            val energy = if (spectrum != null && spectrum.isNotEmpty()) {
                (spectrum[i % spectrum.size].toInt() and 0xFF) / 255f
            } else {
                (cos(time * 0.04f + i * 0.3f) * 0.5f + 0.5f) * 0.7f
            }
            val barHeight = (energy * h * 0.65f).coerceAtLeast(6f)
            drawScope.drawRect(
                color = if (i % 2 == 0) primary else secondary,
                topLeft = Offset(i * barWidth + 2f, h - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - 4f, barHeight)
            )
        }
    }

    override fun setColors(primary: Color, secondary: Color, tertiary: Color) {
        this.primaryColor = primary
        this.secondaryColor = secondary
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
    }
}

class FreeVisualizerFactory {
    fun createVisualizer(type: FreeVisualizerType): FreeVisualizer {
        return when (type) {
            FreeVisualizerType.SPECTRUM_BARS -> SpectrumBarsVisualizer()
            FreeVisualizerType.WAVEFORM_FLOW -> WaveformFlowVisualizer()
            FreeVisualizerType.CIRCULAR_PULSE -> CircularPulseVisualizer()
            FreeVisualizerType.PARTICLE_BURST -> ParticleBurstVisualizer()
            FreeVisualizerType.NEON_GRID -> NeonGridVisualizer()
            FreeVisualizerType.AUDIO_SPECTRUM -> AudioSpectrumVisualizer()
        }
    }

    fun createById(id: Int): FreeVisualizer {
        val type = FreeVisualizerType.entries.find { it.id == id } ?: FreeVisualizerType.SPECTRUM_BARS
        return createVisualizer(type)
    }
}

