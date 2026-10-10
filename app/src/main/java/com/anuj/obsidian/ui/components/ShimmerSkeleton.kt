package com.anuj.obsidian.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anuj.obsidian.ui.theme.ObsidianTheme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalShimmerBrush = compositionLocalOf<Brush?> { null }

/**
 * Sweeping subtle shimmer effect brush for loading skeletons.
 */
@Composable
fun shimmerBrush(
    baseColor: Color = ObsidianTheme.colors.surfaceCard,
    highlightColor: Color = ObsidianTheme.colors.surfaceElevated.copy(alpha = 0.85f)
): Brush {
    val transition = rememberInfiniteTransition(label = "obsidian_shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.ShimmerLoopSpec,
        label = "obsidian_shimmer_translate"
    )

    return Brush.linearGradient(
        colors = listOf(
            baseColor.copy(alpha = 0.6f),
            highlightColor,
            baseColor.copy(alpha = 0.6f)
        ),
        start = Offset(translateAnim - 400f, translateAnim - 400f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * Modifier that applies the shimmering background to any placeholder shape.
 * Efficiently uses parent-provided LocalShimmerBrush if available to avoid duplicate transitions.
 */
@Composable
fun Modifier.shimmerPlaceholder(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    customBrush: Brush? = null
): Modifier {
    val localBrush = customBrush ?: LocalShimmerBrush.current
    if (localBrush != null) {
        return this
            .clip(shape)
            .background(localBrush)
    }

    val transition = rememberInfiniteTransition(label = "obsidian_shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.ShimmerLoopSpec,
        label = "obsidian_shimmer_translate"
    )
    val baseColor = ObsidianTheme.colors.surfaceCard
    val highlightColor = ObsidianTheme.colors.surfaceElevated.copy(alpha = 0.85f)

    return this
        .clip(shape)
        .drawWithCache {
            val colors = listOf(
                baseColor.copy(alpha = 0.6f),
                highlightColor,
                baseColor.copy(alpha = 0.6f)
            )
            onDrawBehind {
                val brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(translateAnim - 400f, translateAnim - 400f),
                    end = Offset(translateAnim, translateAnim)
                )
                drawRect(brush)
            }
        }
}

/**
 * Standard track row skeleton placeholder for Library, Queue, and Search.
 */
@Composable
fun TrackRowSkeleton(
    modifier: Modifier = Modifier,
    artSize: Dp = 48.dp
) {
    val appColors = ObsidianTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork square placeholder
        Box(
            modifier = Modifier
                .size(artSize)
                .shimmerPlaceholder(shape = RoundedCornerShape(10.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Title and artist text placeholder bars
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(13.dp)
                    .shimmerPlaceholder(shape = RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(10.dp)
                    .shimmerPlaceholder(shape = RoundedCornerShape(4.dp))
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right action placeholder pill
        Box(
            modifier = Modifier
                .size(26.dp)
                .shimmerPlaceholder(shape = CircleShape)
        )
    }
}

/**
 * Multi-row skeleton list for library and queue screens.
 * Uses a single shared shimmer brush for minimal overhead.
 */
@Composable
fun TrackListSkeleton(
    count: Int = 7,
    modifier: Modifier = Modifier
) {
    val sharedBrush = shimmerBrush()
    CompositionLocalProvider(LocalShimmerBrush provides sharedBrush) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(count) {
                TrackRowSkeleton()
            }
        }
    }
}

/**
 * Shimmer placeholder for Home Screen trending hero cards and charts.
 * Uses a single shared shimmer brush for minimal overhead.
 */
@Composable
fun HomeChartSkeleton(
    modifier: Modifier = Modifier
) {
    val sharedBrush = shimmerBrush()
    CompositionLocalProvider(LocalShimmerBrush provides sharedBrush) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Chart Card Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .shimmerPlaceholder(shape = RoundedCornerShape(16.dp))
            )

            // Track items under the chart
            repeat(5) {
                TrackRowSkeleton(artSize = 44.dp)
            }
        }
    }
}
