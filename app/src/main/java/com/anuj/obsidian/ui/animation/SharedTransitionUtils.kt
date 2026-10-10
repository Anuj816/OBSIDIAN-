package com.anuj.obsidian.ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.unit.dp

/**
 * Unified Motion Language Tokens
 * Standardized animation specifications across the entire player referencing MotionSpec.
 */
object ObsidianMotion {
    // Micro Interactions (Tactile Press Feedback) - Instant snap with responsive return
    val MicroPressSpring: SpringSpec<Float> = MotionSpec.SpringMicro
    val MicroTween: TweenSpec<Float> = MotionSpec.TweenQuick

    // Standard UI Transitions
    val StandardSpring: SpringSpec<Float> = MotionSpec.SpringSmooth
    val StandardEnterTween: TweenSpec<Float> = MotionSpec.TweenStandard
    val StandardExitTween: TweenSpec<Float> = tween(
        durationMillis = MotionSpec.DurationQuick,
        easing = MotionSpec.EasingAccelerate
    )

    // Major Surface Transitions (Smooth decelerating spring)
    val SurfaceSpring: SpringSpec<Float> = MotionSpec.SpringSmooth
    val SurfaceBoundsSpring = MotionSpec.SpringSmoothRect
    val SurfaceIntOffsetSpring = MotionSpec.SpringSmoothIntOffset

    // Standardized Player Expand / Collapse Motion Specs (Fluid physics)
    val PlayerExpandSpring: SpringSpec<Float> = MotionSpec.SpringHeroFraction
    val PlayerCollapseSpring: SpringSpec<Float> = MotionSpec.SpringHeroFraction
    val PlayerFractionSpring: SpringSpec<Float> = MotionSpec.SpringHeroFraction
    val PlayerIntOffsetSpring: SpringSpec<androidx.compose.ui.unit.IntOffset> = MotionSpec.SpringSmoothIntOffset
    val PlayerBoundsSpring = MotionSpec.SpringHeroBounds
    val PlayerFadeEnterSpring: FiniteAnimationSpec<Float> = tween(
        durationMillis = MotionSpec.DurationStandard,
        easing = MotionSpec.EasingEmphasized
    )
    val PlayerFadeExitSpring: FiniteAnimationSpec<Float> = tween(
        durationMillis = MotionSpec.DurationQuick,
        easing = MotionSpec.EasingEmphasizedAccelerate
    )

    // Gesture Settling (Velocity & distance responsive)
    val GestureSettlingSpring: SpringSpec<Float> = MotionSpec.SpringSmooth

    // Artwork & Metadata Crossfade Spec
    val ArtworkTransition: ContentTransform = MotionSpec.ContentCrossfade

    // Track Title / Metadata Transition Spec (Ultra-smooth kinetic cross-slide)
    val TitleTransition: ContentTransform =
        (fadeIn(animationSpec = tween(MotionSpec.DurationQuick, easing = MotionSpec.EasingEmphasized)) +
                slideInVertically(initialOffsetY = { it / 3 }, animationSpec = MotionSpec.SpringSmoothIntOffset))
            .togetherWith(
                fadeOut(animationSpec = tween(MotionSpec.DurationQuick, easing = MotionSpec.EasingEmphasizedAccelerate)) +
                        slideOutVertically(targetOffsetY = { -it / 3 }, animationSpec = tween(MotionSpec.DurationQuick))
            )

    // Mini Player Track Transition Spec
    val MiniPlayerTrackTransition: ContentTransform =
        ContentTransform(
            targetContentEnter = fadeIn(animationSpec = tween(MotionSpec.DurationQuick, delayMillis = 40, easing = MotionSpec.EasingEmphasized)) +
                    slideInVertically(animationSpec = MotionSpec.SpringSmoothIntOffset) { height -> height / 2 },
            initialContentExit = fadeOut(animationSpec = tween(MotionSpec.DurationQuick / 2, easing = MotionSpec.EasingAccelerate)) +
                    slideOutVertically(animationSpec = tween(MotionSpec.DurationQuick / 2, easing = MotionSpec.EasingAccelerate)) { height -> -height / 2 },
            sizeTransform = SizeTransform(clip = false)
        )
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Optimizes composable layout nodes by attaching a GPU hardware display list layer,
 * clipping to outline, and eliminating unnecessary redraw passes.
 */
fun Modifier.hardwareAccelerated(): Modifier = this.graphicsLayer {
    compositingStrategy = CompositingStrategy.Auto
}

/**
 * High-performance layer isolation modifier for real-time visualizers and animated meters.
 */
fun Modifier.visualizerLayer(): Modifier = this.graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}

/**
 * Tactile spring bounce effect on button presses with customizable shape clipping.
 */
@Composable
fun Modifier.pressSpring(
    scaleDown: Float = 0.92f,
    shape: Shape? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScaleState = animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1.0f,
        animationSpec = ObsidianMotion.MicroPressSpring,
        label = "press_spring_scale"
    )
    val base = if (shape != null) this.clip(shape) else this
    return base.graphicsLayer {
        val s = animatedScaleState.value
        scaleX = s
        scaleY = s
    }
}

/**
 * Circular clickable with spring physics, haptic feedback, and proper circular ripple clipping.
 */
@Composable
fun Modifier.circularBounceClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.92f,
    onClick: () -> Unit
): Modifier {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScaleState = animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1.0f,
        animationSpec = ObsidianMotion.MicroPressSpring,
        label = "circular_bounce_scale"
    )
    return this
        .clip(CircleShape)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(bounded = true),
            enabled = enabled,
            onClick = {
                haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
        .graphicsLayer {
            val s = animatedScaleState.value
            scaleX = s
            scaleY = s
        }
}

/**
 * Circular clickable with spring physics and haptic feedback supporting both tap and long press.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.circularBounceCombinedClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.92f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScaleState = animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1.0f,
        animationSpec = ObsidianMotion.MicroPressSpring,
        label = "circular_bounce_scale"
    )
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(bounded = true),
            enabled = enabled,
            onLongClick = {
                haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onLongClick()
            },
            onClick = {
                haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(bounded = true),
            enabled = enabled,
            onClick = {
                haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
    }
    return this
        .clip(CircleShape)
        .then(clickModifier)
        .graphicsLayer {
            val s = animatedScaleState.value
            scaleX = s
            scaleY = s
        }
}

/**
 * Clickable with tactile spring physics and haptic feedback for buttons and cards.
 */
@Composable
fun Modifier.bounceClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.92f,
    shape: Shape? = null,
    onClick: () -> Unit
): Modifier {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val base = if (shape != null) this.clip(shape) else this
    val clickableModifier = base.clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.material3.ripple(bounded = true),
        enabled = enabled,
        onClick = {
            haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        }
    )
    return if (scaleDown < 0.99f && enabled) {
        val animatedScaleState = animateFloatAsState(
            targetValue = if (isPressed) scaleDown else 1.0f,
            animationSpec = ObsidianMotion.MicroPressSpring,
            label = "bounce_clickable_scale"
        )
        clickableModifier.graphicsLayer {
            val s = animatedScaleState.value
            scaleX = s
            scaleY = s
        }
    } else {
        clickableModifier
    }
}

/**
 * Dynamic ambient pulsing glow modifier that breathes during active playback.
 */
@Composable
fun Modifier.ambientPulseGlow(
    enabled: Boolean,
    color: Color,
    maxAlpha: Float = 0.40f,
    minScale: Float = 1.0f,
    maxScale: Float = 1.08f
): Modifier {
    if (!enabled || color == Color.Unspecified || color == Color.Transparent) return this

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ambient_pulse")
    val pulseScaleState = infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = MotionSpec.PulseLoopSpec.animation,
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlphaState = infiniteTransition.animateFloat(
        initialValue = maxAlpha * 0.45f,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = MotionSpec.PulseLoopSpec.animation,
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    return this.drawWithCache {
        val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val maxRadius = (size.maxDimension / 2f) * maxScale
        val baseBrush = Brush.radialGradient(
            0.0f to color,
            0.55f to color.copy(alpha = 0.35f),
            1.0f to Color.Transparent,
            center = centerOffset,
            radius = maxRadius
        )
        onDrawBehind {
            val scale = pulseScaleState.value
            val alpha = pulseAlphaState.value
            val currentRadius = (size.maxDimension / 2f) * scale
            drawCircle(
                brush = baseBrush,
                radius = currentRadius,
                center = centerOffset,
                alpha = alpha
            )
        }
    }
}

/**
 * Obsidian Play/Pause morphing animation with punchy scale bounce, rotation, and micro-elevation.
 */
@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color = Color.White,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val rotationState = animateFloatAsState(
        targetValue = if (isPlaying) 0f else -22f,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = 320f
        ),
        label = "play_pause_rotation"
    )

    val punchScale = animateFloatAsState(
        targetValue = if (isPlaying) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 320f
        ),
        label = "play_pause_punch"
    )

    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (fadeIn(animationSpec = MotionSpec.tweenQuick()) +
                    scaleIn(initialScale = 0.68f, animationSpec = MotionSpec.SpringMicro))
                .togetherWith(
                    fadeOut(animationSpec = MotionSpec.tweenQuick()) +
                            scaleOut(targetScale = 0.68f, animationSpec = MotionSpec.tweenQuick())
                )
        },
        label = "play_pause_morph",
        modifier = modifier.graphicsLayer {
            rotationZ = rotationState.value
            scaleX = punchScale.value
            scaleY = punchScale.value
        }
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * Obsidian Favorite Heart pop animation with spring burst scale and vibrant color transition.
 */
@Composable
fun AnimatedFavoriteIcon(
    isFavorite: Boolean,
    activeTint: Color,
    inactiveTint: Color,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    val scaleState = animateFloatAsState(
        targetValue = if (isFavorite) 1.25f else 1.0f,
        animationSpec = MotionSpec.SpringMicro,
        label = "favorite_pop_scale"
    )

    val tintState = animateColorAsState(
        targetValue = if (isFavorite) activeTint else inactiveTint,
        animationSpec = MotionSpec.tweenQuick(),
        label = "favorite_tint"
    )

    AnimatedContent(
        targetState = isFavorite,
        transitionSpec = {
            (fadeIn(animationSpec = MotionSpec.tweenQuick()) +
                    scaleIn(initialScale = 0.60f, animationSpec = MotionSpec.SpringMicro))
                .togetherWith(
                    fadeOut(animationSpec = MotionSpec.tweenQuick()) +
                            scaleOut(targetScale = 0.60f, animationSpec = MotionSpec.tweenQuick())
                )
        },
        label = "favorite_heart_morph",
        modifier = modifier.graphicsLayer {
            scaleX = scaleState.value
            scaleY = scaleState.value
        }
    ) { favorite ->
        Icon(
            imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (favorite) "Favorited" else "Unfavorited",
            tint = tintState.value,
            modifier = Modifier.size(size)
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedAlbumArt(trackId: Long, shape: RoundedCornerShape? = null): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val targetShape = shape ?: RoundedCornerShape(12.dp)
    val clipped = this.clip(targetShape)
    if (sharedScope != null && animatedVisibilityScope != null) {
        with(sharedScope) {
            clipped.sharedBounds(
                rememberSharedContentState(key = "album_art_$trackId"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    MotionSpec.SpringHeroBounds
                },
                clipInOverlayDuringTransition = OverlayClip(targetShape)
            )
        }
    } else {
        clipped
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedTrackTitle(trackId: Long): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    if (sharedScope != null && animatedVisibilityScope != null) {
        with(sharedScope) {
            this@composed.sharedBounds(
                rememberSharedContentState(key = "track_title_$trackId"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    MotionSpec.SpringHeroBounds
                }
            )
        }
    } else {
        this
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedTrackArtist(trackId: Long): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    if (sharedScope != null && animatedVisibilityScope != null) {
        with(sharedScope) {
            this@composed.sharedBounds(
                rememberSharedContentState(key = "track_artist_$trackId"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    MotionSpec.SpringHeroBounds
                }
            )
        }
    } else {
        this
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedQualityBadge(trackId: Long): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    if (sharedScope != null && animatedVisibilityScope != null) {
        with(sharedScope) {
            this@composed.sharedBounds(
                rememberSharedContentState(key = "track_quality_$trackId"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    spring(
                        dampingRatio = 0.80f,
                        stiffness = 320f
                    )
                }
            )
        }
    } else {
        this
    }
}

fun Modifier.drawLayoutBounds(enabled: Boolean): Modifier = if (enabled) {
    this.drawWithCache {
        val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        onDrawWithContent {
            drawContent()
            // Draw a cyan boundary line around the composable
            drawRect(
                color = Color(0xFF00E5FF),
                style = strokeStyle
            )
            val cornerLen = 6.dp.toPx()
            val strokeWidth = 1.5f.dp.toPx()
            // Draw red/pink corner guides
            val cornerColor = Color(0xFFFF5252)
            // Top-left
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(cornerLen, 0f), strokeWidth)
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, cornerLen), strokeWidth)
            // Top-right
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - cornerLen, 0f), strokeWidth)
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, cornerLen), strokeWidth)
            // Bottom-left
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(cornerLen, size.height), strokeWidth)
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - cornerLen), strokeWidth)
            // Bottom-right
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - cornerLen, size.height), strokeWidth)
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - cornerLen), strokeWidth)
        }
    }
} else {
    this
}

/**
 * Applies enter/exit transition (fadeIn + subtle slide-up) for non-art sheet content
 * using the parent [LocalNavAnimatedVisibilityScope].
 */
@Composable
fun Modifier.nonArtContentTransition(): Modifier {
    val navScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(navScope) {
        this@nonArtContentTransition.animateEnterExit(
            enter = fadeIn(animationSpec = ObsidianMotion.PlayerFadeEnterSpring) +
                    slideInVertically(
                        initialOffsetY = { it / 8 },
                        animationSpec = ObsidianMotion.PlayerIntOffsetSpring
                    ),
            exit = fadeOut(animationSpec = ObsidianMotion.PlayerFadeExitSpring) +
                    slideOutVertically(
                        targetOffsetY = { it / 8 },
                        animationSpec = ObsidianMotion.PlayerIntOffsetSpring
                    )
        )
    }
}

/**
 * Photorealistic spinning Vinyl Record Platter with angular momentum, grooves reflection,
 * dynamic center album art sticker, and smooth acceleration/deceleration physics.
 */
@Composable
fun AnimatedVinylRecordPlatter(
    isPlaying: Boolean,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    rpm: Float = 33.3f,
    glowColor: Color = Color(0xFF00E5FF)
) {
    var currentRotation by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var currentVelocity by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val targetVelocity = if (isPlaying) (rpm * 360f / 60f) else 0f

    LaunchedEffect(isPlaying) {
        var lastFrameTime = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                val dt = ((frameTimeNanos - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameTime = frameTimeNanos

                // Smoothly accelerate / decelerate velocity with realistic inertia
                val accel = if (isPlaying) 6.0f else 3.5f
                currentVelocity += (targetVelocity - currentVelocity) * (accel * dt).coerceAtMost(1f)

                if (currentVelocity > 0.1f) {
                    currentRotation = (currentRotation + currentVelocity * dt) % 360f
                }
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .graphicsLayer {
                rotationZ = currentRotation
            }
            .drawWithCache {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f

                // Sweeping vinyl reflection highlights
                val vinylGrooveBrush = Brush.sweepGradient(
                    0.0f to Color(0xFF141416),
                    0.25f to Color(0xFF282A32),
                    0.5f to Color(0xFF0D0E11),
                    0.75f to Color(0xFF2E313A),
                    1.0f to Color(0xFF141416),
                    center = center
                )

                onDrawBehind {
                    // Outer vinyl base
                    drawCircle(brush = vinylGrooveBrush, radius = radius, center = center)

                    // Concentric vinyl micro-grooves
                    val grooveColor = Color.White.copy(alpha = 0.045f)
                    val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    for (step in 1..8) {
                        val r = radius * (0.42f + (step * 0.065f))
                        if (r < radius - 4.dp.toPx()) {
                            drawCircle(color = grooveColor, radius = r, center = center, style = strokeStyle)
                        }
                    }

                    // Outer glossy rim
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = radius - 1.dp.toPx(),
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f.dp.toPx())
                    )
                }
            }
    ) {
        // Center Album Art Sticker (38% of vinyl size)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize(0.38f)
                .clip(CircleShape)
                .background(Color(0xFF1A1C24))
                .border(2.dp, Color(0xFF323644), CircleShape)
        ) {
            if (!coverUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = coverUrl,
                    contentDescription = "Vinyl Label Art",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(glowColor.copy(alpha = 0.5f), Color(0xFF12141C)))
                        )
                )
            }

            // Spindle Center Hole
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF08090C))
                    .border(1.5.dp, Color(0xFF555A6C), CircleShape)
            )
        }
    }
}

/**
 * High-performance 4-Band Reactive Equalizer Spectrum Bar with smooth harmonic sinusoidal oscillation
 * and fluid spring physics.
 */
@Composable
fun EqualizerSpectrumLiveBar(
    isPlaying: Boolean,
    primaryColor: Color = Color(0xFF00E5FF),
    secondaryColor: Color = Color(0xFF7C4DFF),
    barCount: Int = 4,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq_spectrum")

    val phaseA by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phaseA"
    )

    val phaseB by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(310, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phaseB"
    )

    val phaseC by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phaseC"
    )

    val phaseD by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phaseD"
    )

    val playInterpolation by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.15f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f),
        label = "play_interpolate"
    )

    val phases = listOf(phaseA, phaseB, phaseC, phaseD)

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val totalWidth = size.width
        val barHeightMax = size.height
        val barSpacing = 2.5.dp.toPx()
        val totalSpacing = barSpacing * (barCount - 1)
        val barWidth = ((totalWidth - totalSpacing) / barCount).coerceAtLeast(1.5.dp.toPx())

        for (i in 0 until barCount) {
            val rawPhase = phases[i % phases.size]
            val activeFraction = (rawPhase * playInterpolation).coerceIn(0.12f, 1.0f)
            val currentBarHeight = barHeightMax * activeFraction

            val x = i * (barWidth + barSpacing)
            val y = barHeightMax - currentBarHeight

            val brush = Brush.verticalGradient(
                colors = listOf(primaryColor, secondaryColor),
                startY = y,
                endY = barHeightMax
            )

            drawRoundRect(
                brush = brush,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, currentBarHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * Animated Favorite Heart Pop with expanding radial particle burst on like.
 */
@Composable
fun AnimatedFavoriteBurstIcon(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    activeTint: Color = Color(0xFFFF3366),
    inactiveTint: Color = Color(0xFFA0A4B8),
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var burstTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    val burstProgress = animateFloatAsState(
        targetValue = if (burstTrigger > 0) 1.0f else 0f,
        animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
        label = "burst_progress"
    )

    val heartScale = animateFloatAsState(
        targetValue = if (burstProgress.value in 0.01f..0.7f) 1.32f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 600f),
        label = "heart_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size + 16.dp)
            .circularBounceClickable {
                haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                if (!isFavorite) {
                    burstTrigger++
                }
                onToggle()
            }
    ) {
        // Particle burst ring on like
        if (burstProgress.value in 0.01f..0.99f) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val progress = burstProgress.value
                val center = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
                val maxBurstRadius = this.size.minDimension / 2f * 0.95f
                val currentRadius = maxBurstRadius * progress
                val particleAlpha = (1f - progress).coerceIn(0f, 1f)
                val particleSize = (3.dp.toPx() * (1f - progress * 0.5f)).coerceAtLeast(1.dp.toPx())

                val particleCount = 6
                for (i in 0 until particleCount) {
                    val angleRad = (i * (2 * Math.PI / particleCount) + (progress * 0.3)).toFloat()
                    val px = center.x + (currentRadius * kotlin.math.cos(angleRad)).toFloat()
                    val py = center.y + (currentRadius * kotlin.math.sin(angleRad)).toFloat()

                    drawCircle(
                        color = activeTint.copy(alpha = particleAlpha),
                        radius = particleSize,
                        center = androidx.compose.ui.geometry.Offset(px, py)
                    )
                }
            }
        }

        // Heart Icon
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isFavorite) "Favorited" else "Favorite",
            tint = if (isFavorite) activeTint else inactiveTint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = heartScale.value
                    scaleY = heartScale.value
                }
        )
    }
}

/**
 * Cascading staggered entrance modifier for list & grid items with natural vertical slide and fade.
 */
@Composable
fun Modifier.staggeredSlideAndFade(
    index: Int,
    baseDelayMillis: Int = 30,
    maxStaggerCount: Int = 12
): Modifier {
    val clampedIndex = index.coerceIn(0, maxStaggerCount)
    val delay = clampedIndex * baseDelayMillis
    var visible by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing),
        label = "stagger_alpha_$index"
    )

    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "stagger_offset_$index"
    )

    return this.graphicsLayer {
        this.alpha = alpha
        this.translationY = offsetY.dp.toPx()
    }
}

/**
 * Tactile Press Bounce with 3D micro-tilt physics and haptic vibration kick.
 */
@Composable
fun Modifier.tactilePressBounce(
    scaleDown: Float = 0.90f,
    tiltDegrees: Float = 2.0f,
    shape: Shape? = null,
    onClick: () -> Unit
): Modifier {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1.0f,
        animationSpec = ObsidianMotion.MicroPressSpring,
        label = "tactile_scale"
    )

    val animatedTilt by animateFloatAsState(
        targetValue = if (isPressed) tiltDegrees else 0f,
        animationSpec = ObsidianMotion.MicroPressSpring,
        label = "tactile_tilt"
    )

    val base = if (shape != null) this.clip(shape) else this
    return base
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(bounded = true),
            onClick = {
                haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            rotationZ = animatedTilt
        }
}

/**
 * Shimmer wave gradient modifier for loading skeletons and glowing badges.
 */
@Composable
fun Modifier.shimmerGlowWave(
    enabled: Boolean = true,
    highlightColor: Color = Color(0xFF00E5FF)
): Modifier {
    if (!enabled) return this

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer_glow")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -1.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return this.drawWithCache {
        val width = size.width
        val height = size.height
        val startX = width * shimmerTranslate
        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                highlightColor.copy(alpha = 0.28f),
                highlightColor.copy(alpha = 0.65f),
                highlightColor.copy(alpha = 0.28f),
                Color.Transparent
            ),
            start = androidx.compose.ui.geometry.Offset(startX - width * 0.4f, 0f),
            end = androidx.compose.ui.geometry.Offset(startX + width * 0.4f, height)
        )

        onDrawWithContent {
            drawContent()
            drawRect(brush = brush)
        }
    }
}



