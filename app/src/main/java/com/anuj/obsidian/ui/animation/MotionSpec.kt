package com.anuj.obsidian.ui.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Central Motion Specification
 * Single source of truth for all animation timing, easing curves, and spring physics.
 */
object MotionSpec {

    // MARK: - Duration Tokens
    const val DurationQuick: Int = 160       // Icon toggles, ripples, small state flips
    const val DurationStandard: Int = 280    // Screen-level fades, list item changes, dialog reveals
    const val DurationExpressive: Int = 420  // Hero / shared-element bounds, sheet expansions, full modals

    // MARK: - Easing Curves
    val EasingStandard: Easing = FastOutSlowInEasing
    val EasingDecelerate: Easing = LinearOutSlowInEasing
    val EasingAccelerate: Easing = FastOutLinearInEasing
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EasingEmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 1f)

    // MARK: - Generic Spec Builders
    fun <T> springSmooth(
        dampingRatio: Float = 0.84f,
        stiffness: Float = 550f
    ): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

    fun <T> springMicro(
        dampingRatio: Float = 0.78f,
        stiffness: Float = 1200f
    ): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

    fun <T> springHero(
        dampingRatio: Float = 0.84f,
        stiffness: Float = 500f
    ): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

    fun <T> tweenQuick(
        durationMillis: Int = DurationQuick,
        easing: Easing = EasingStandard
    ): TweenSpec<T> = tween(durationMillis = durationMillis, easing = easing)

    fun <T> tweenStandard(
        durationMillis: Int = DurationStandard,
        easing: Easing = EasingStandard
    ): TweenSpec<T> = tween(durationMillis = durationMillis, easing = easing)

    fun <T> tweenExpressive(
        durationMillis: Int = DurationExpressive,
        easing: Easing = EasingEmphasized
    ): TweenSpec<T> = tween(durationMillis = durationMillis, easing = easing)

    // MARK: - Standard Tweens
    val TweenQuick: TweenSpec<Float> = tween(durationMillis = DurationQuick, easing = EasingStandard)
    val TweenStandard: TweenSpec<Float> = tween(durationMillis = DurationStandard, easing = EasingStandard)
    val TweenExpressive: TweenSpec<Float> = tween(durationMillis = DurationExpressive, easing = EasingEmphasized)

    // MARK: - Springs (Physics-based motion for draggable, touchable & continuous interactions)
    // Primary fluid spring for gestures, sheets, sliders, seekbars, dismissals
    val SpringSmooth: SpringSpec<Float> = spring(
        dampingRatio = 0.84f,
        stiffness = 550f
    )

    val SpringSmoothIntOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.84f,
        stiffness = 550f
    )

    val SpringSmoothIntSize: SpringSpec<IntSize> = spring(
        dampingRatio = 0.84f,
        stiffness = 550f
    )
    val SpringContentSize: SpringSpec<IntSize> = SpringSmoothIntSize

    val SpringSmoothOffset: SpringSpec<Offset> = spring(
        dampingRatio = 0.84f,
        stiffness = 550f
    )

    val SpringSmoothDp: SpringSpec<Dp> = spring(
        dampingRatio = 0.84f,
        stiffness = 550f
    )

    val SpringSmoothRect: SpringSpec<Rect> = spring(
        dampingRatio = 0.84f,
        stiffness = 550f
    )

    // Snappy micro-interaction spring (buttons, icon pops, toggles)
    val SpringMicro: SpringSpec<Float> = spring(
        dampingRatio = 0.78f,
        stiffness = 1200f
    )

    // Snappy tactile spring for responsive controls & toggles
    val SpringSnappy86: SpringSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = 1000f
    )
    val SpringSnappy: SpringSpec<Float> = SpringSnappy86

    // Playful bouncy spring (favorite burst, badge pops)
    val SpringBouncy: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 650f
    )

    // Ambient loop durations and specs
    const val DurationAmbientShimmer: Int = 1200
    const val DurationAmbientEqualizer: Int = 1100
    const val DurationAmbientPulse: Int = 3000

    val ShimmerLoopSpec: androidx.compose.animation.core.InfiniteRepeatableSpec<Float> = androidx.compose.animation.core.infiniteRepeatable(
        animation = tween(durationMillis = DurationAmbientShimmer, easing = androidx.compose.animation.core.LinearEasing),
        repeatMode = androidx.compose.animation.core.RepeatMode.Restart
    )

    val EqualizerLoopSpec: androidx.compose.animation.core.InfiniteRepeatableSpec<Float> = androidx.compose.animation.core.infiniteRepeatable(
        animation = tween(durationMillis = DurationAmbientEqualizer, easing = androidx.compose.animation.core.LinearEasing),
        repeatMode = androidx.compose.animation.core.RepeatMode.Restart
    )

    val PulseLoopSpec: androidx.compose.animation.core.InfiniteRepeatableSpec<Float> = androidx.compose.animation.core.infiniteRepeatable(
        animation = tween(durationMillis = DurationAmbientPulse, easing = FastOutSlowInEasing),
        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
    )

    // Hero Shared Element & Sheet Expansion Springs
    val SpringHero: SpringSpec<Float> = spring(
        dampingRatio = 0.84f,
        stiffness = 500f
    )
    val SpringHeroDp: SpringSpec<Dp> = spring(
        dampingRatio = 0.84f,
        stiffness = 500f
    )
    val SpringHeroIntOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.84f,
        stiffness = 500f
    )
    val SpringHeroIntSize: SpringSpec<IntSize> = spring(
        dampingRatio = 0.84f,
        stiffness = 500f
    )
    val SpringHeroBounds: SpringSpec<Rect> = spring(
        dampingRatio = 0.84f,
        stiffness = 500f
    )
    val SpringHeroFraction: SpringSpec<Float> = spring(
        dampingRatio = 0.84f,
        stiffness = 500f
    )

    // MARK: - Standard Navigation Transitions (Shared Axis lateral slide + fade)
    val NavEnterSlide: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> (fullWidth * 0.16f).toInt() },
            animationSpec = tween(durationMillis = DurationStandard, easing = EasingEmphasized)
        ) + fadeIn(
            animationSpec = tween(durationMillis = DurationStandard, easing = EasingStandard)
        )

    val NavExitSlide: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> -(fullWidth * 0.12f).toInt() },
            animationSpec = tween(durationMillis = DurationStandard, easing = EasingEmphasized)
        ) + fadeOut(
            animationSpec = tween(durationMillis = DurationQuick, easing = EasingAccelerate)
        )

    val NavPopEnterSlide: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -(fullWidth * 0.12f).toInt() },
            animationSpec = tween(durationMillis = DurationStandard, easing = EasingEmphasized)
        ) + fadeIn(
            animationSpec = tween(durationMillis = DurationStandard, easing = EasingStandard)
        )

    val NavPopExitSlide: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> (fullWidth * 0.16f).toInt() },
            animationSpec = tween(durationMillis = DurationStandard, easing = EasingEmphasized)
        ) + fadeOut(
            animationSpec = tween(durationMillis = DurationQuick, easing = EasingAccelerate)
        )

    // Standard Fade Navigation Transition (e.g. for modal / overlay tabs)
    val NavEnterFade: EnterTransition = fadeIn(animationSpec = tween(DurationStandard, easing = EasingStandard))
    val NavExitFade: ExitTransition = fadeOut(animationSpec = tween(DurationQuick, easing = EasingAccelerate))

    // Content Crossfade Transform (for placeholder -> content or metadata changes)
    val ContentCrossfade: ContentTransform =
        (fadeIn(animationSpec = tween(DurationStandard, easing = EasingStandard)) +
                scaleIn(initialScale = 0.96f, animationSpec = SpringSmooth))
            .togetherWith(
                fadeOut(animationSpec = tween(DurationQuick, easing = EasingAccelerate)) +
                        scaleOut(targetScale = 0.98f, animationSpec = tween(DurationQuick))
            )

    // Subtle List Item Staggered Reveal / Shift
    val ItemEnterTransition: EnterTransition =
        fadeIn(animationSpec = tween(DurationStandard, easing = EasingStandard)) +
                slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 240f)
                )

    val ItemExitTransition: ExitTransition =
        fadeOut(animationSpec = tween(DurationQuick, easing = EasingAccelerate)) +
                slideOutVertically(
                    targetOffsetY = { -it / 4 },
                    animationSpec = tween(DurationQuick, easing = EasingAccelerate)
                )
}

/**
 * Project-Wide Micro-Interaction Modifier.
 * Provides subtle tactile scale-down-on-press (~0.95x) with smooth spring return.
 */
@Composable
fun Modifier.pressScale(
    scaleDown: Float = 0.95f,
    enabled: Boolean = true,
    shape: Shape? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: (() -> Unit)? = null
): Modifier {
    val haptic = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1.0f,
        animationSpec = MotionSpec.SpringMicro,
        label = "press_scale_spring"
    )

    val baseModifier = if (shape != null) this.clip(shape) else this
    val withScale = baseModifier.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
    }

    return if (onClick != null) {
        withScale.clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(bounded = shape != null),
            enabled = enabled,
            onClick = {
                haptic.safePerform(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
    } else {
        withScale
    }
}

fun androidx.compose.ui.hapticfeedback.HapticFeedback.safePerform(
    type: HapticFeedbackType = HapticFeedbackType.TextHandleMove
) {
    try {
        this.performHapticFeedback(type)
    } catch (_: Throwable) {
        // Safe fallback on OEM devices or restricted backgrounds
    }
}

