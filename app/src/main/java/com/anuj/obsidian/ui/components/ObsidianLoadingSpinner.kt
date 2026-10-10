package com.anuj.obsidian.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anuj.obsidian.ui.animation.hardwareAccelerated
import com.anuj.obsidian.ui.theme.ObsidianTheme

/**
 * High-performance hardware-accelerated loading spinner powered by [rememberInfiniteTransition].
 * Ensures continuous, jank-free 60/120fps rotation and dynamic sweep arc animation
 * without triggering recomposition overhead or layout recalculations.
 */
@Composable
fun ObsidianLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = ObsidianTheme.colors.primary,
    strokeWidth: Dp = 2.5.dp,
    trackColor: Color = color.copy(alpha = 0.18f),
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "obsidian_infinite_spinner")
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rotation"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 45f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spinner_sweep"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .progressSemantics()
                .hardwareAccelerated()
        ) {
            val strokePx = strokeWidth.toPx()
            val diameter = this.size.minDimension
            val radius = (diameter - strokePx) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            if (trackColor != Color.Transparent && trackColor.alpha > 0f) {
                drawCircle(
                    color = trackColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokePx)
                )
            }

            drawArc(
                color = color,
                startAngle = rotationAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}
