package com.anuj.obsidian.ui.components

import android.content.Context
import kotlinx.coroutines.isActive
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import com.anuj.obsidian.ui.animation.EqualizerSpectrumLiveBar
import com.anuj.obsidian.ui.animation.bounceClickable
import com.anuj.obsidian.ui.animation.circularBounceClickable
import com.anuj.obsidian.ui.animation.safePerform
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.PlaybackLoadingType
import com.anuj.obsidian.ui.animation.AnimatedPlayPauseIcon
import com.anuj.obsidian.ui.animation.circularBounceClickable
import com.anuj.obsidian.ui.animation.hardwareAccelerated
import com.anuj.obsidian.ui.animation.sharedAlbumArt
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.theme.AppThemePalette
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun MiniPlayerBar(
    track: DownloadedTrack?,
    isPlaying: Boolean,
    currentPositionFlow: StateFlow<Float>,
    durationSeconds: Int,
    previousTrack: DownloadedTrack? = null,
    nextTrack: DownloadedTrack? = null,
    loadingType: PlaybackLoadingType = PlaybackLoadingType.NONE,
    onVerticalDrag: (Float) -> Unit = {},
    onVerticalDragEnd: (Float) -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: (Boolean) -> Unit = {},
    onClosePlayer: () -> Unit = {},
    onClickBar: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) return
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val appColors = ObsidianTheme.colors
    val coroutineScope = rememberCoroutineScope()

    val dragOffsetState = remember { mutableFloatStateOf(0f) }
    var isTransitioning by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        dragOffsetState.floatValue = 0f
        isTransitioning = false
    }

    val effectiveNextTrack = nextTrack
    val effectivePrevTrack = previousTrack

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        val containerWidth = maxWidth
        val density = androidx.compose.ui.platform.LocalDensity.current
        val containerWidthPx = with(density) { containerWidth.toPx() }
        val swipeThresholdPx = (containerWidthPx * 0.22f).coerceAtLeast(with(density) { 48.dp.toPx() })

        Box(
            modifier = Modifier
                .fillMaxWidth()
                // GESTURE STRATEGY:
                // 1. The parent pointerInput solely tracks active DRAGS exceeding touchSlop:
                //    - Horizontal drag: Smoothly swipes between tracks (prev / next track) with interactive card offset.
                //    - Vertical drag up: Expands the full player bottom sheet.
                // 2. We never consume initial DOWN pointer events or manually dispatch onClickBar on tap release.
                //    Child interactive elements (Play/Pause, Skip, Close buttons and the Track Info Row) receive
                //    tap events directly and reliably without interference or race conditions.
                // 3. Pointer events are only consumed once movement exceeds touchSlop, cleanly canceling child clicks.
                .pointerInput(containerWidthPx, effectivePrevTrack?.id, effectiveNextTrack?.id, isTransitioning) {
                    if (isTransitioning) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val currentPointerId = down.id
                        val touchSlop = viewConfiguration.touchSlop
                        var totalDragX = 0f
                        var totalDragY = 0f
                        var isHorizontalDrag = false
                        var isVerticalDragUp = false
                        var lastTime = System.currentTimeMillis()
                        var velocityX = 0f
                        var velocityY = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == currentPointerId } ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) break

                            val currentTime = System.currentTimeMillis()
                            val dt = (currentTime - lastTime).coerceAtLeast(1L)
                            val delta = change.positionChange()
                            velocityY = (delta.y / (dt / 1000f)).coerceIn(-5000f, 5000f)
                            velocityX = (delta.x / (dt / 1000f)).coerceIn(-5000f, 5000f)
                            lastTime = currentTime

                            if (!isHorizontalDrag && !isVerticalDragUp) {
                                totalDragX += delta.x
                                totalDragY += delta.y
                                if (totalDragY < -touchSlop && abs(totalDragY) > abs(totalDragX)) {
                                    isVerticalDragUp = true
                                    change.consume()
                                } else if (abs(totalDragX) > touchSlop && abs(totalDragX) > abs(delta.y)) {
                                    isHorizontalDrag = true
                                    change.consume()
                                }
                            } else if (isVerticalDragUp) {
                                change.consume()
                                onVerticalDrag(delta.y)
                            } else {
                                change.consume()
                                val currentVal = dragOffsetState.floatValue
                                val proposed = currentVal + delta.x
                                val bounded = if (proposed < 0f && effectiveNextTrack == null) {
                                    currentVal + delta.x * 0.25f
                                } else if (proposed > 0f && effectivePrevTrack == null) {
                                    currentVal + delta.x * 0.25f
                                } else {
                                    proposed
                                }
                                dragOffsetState.floatValue = bounded
                            }
                        }

                        if (isVerticalDragUp) {
                            onVerticalDragEnd(velocityY)
                        } else if (isHorizontalDrag) {
                            coroutineScope.launch {
                                val currentX = dragOffsetState.floatValue
                                val shouldPlayNext = effectiveNextTrack != null && (currentX < -swipeThresholdPx || velocityX < -800f)
                                val shouldPlayPrev = effectivePrevTrack != null && (currentX > swipeThresholdPx || velocityX > 800f)

                                if (shouldPlayNext) {
                                    isTransitioning = true
                                    haptic.safePerform(HapticFeedbackType.LongPress)
                                    val anim = androidx.compose.animation.core.Animatable(currentX)
                                    anim.animateTo(
                                        targetValue = -containerWidthPx,
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSnappy,
                                        initialVelocity = velocityX
                                    ) {
                                        dragOffsetState.floatValue = value
                                    }
                                    onPlayNext()
                                    dragOffsetState.floatValue = 0f
                                    isTransitioning = false
                                } else if (shouldPlayPrev) {
                                    isTransitioning = true
                                    haptic.safePerform(HapticFeedbackType.LongPress)
                                    val anim = androidx.compose.animation.core.Animatable(currentX)
                                    anim.animateTo(
                                        targetValue = containerWidthPx,
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSnappy,
                                        initialVelocity = velocityX
                                    ) {
                                        dragOffsetState.floatValue = value
                                    }
                                    onPlayPrevious(true)
                                    dragOffsetState.floatValue = 0f
                                    isTransitioning = false
                                } else {
                                    val anim = androidx.compose.animation.core.Animatable(currentX)
                                    anim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
                                        initialVelocity = velocityX
                                    ) {
                                        dragOffsetState.floatValue = value
                                    }
                                }
                            }
                        }
                    }
                }
                .testTag("mini_player_bar")
        ) {
            val isSwipingRight by remember { derivedStateOf { dragOffsetState.floatValue > 0f || (isTransitioning && dragOffsetState.floatValue > 0f) } }
            val isSwipingLeft by remember { derivedStateOf { dragOffsetState.floatValue < 0f || (isTransitioning && dragOffsetState.floatValue < 0f) } }

            // PREVIOUS TRACK CARD (revealed on right swipe)
            if (effectivePrevTrack != null && isSwipingRight) {
                MiniPlayerCard(
                    modifier = Modifier
                        .width(containerWidth)
                        .graphicsLayer {
                            val dragX = dragOffsetState.floatValue
                            translationX = dragX - containerWidthPx
                            val progress = (dragX / containerWidthPx).coerceIn(0f, 1f)
                            alpha = (0.7f + progress * 0.3f).coerceIn(0.7f, 1f)
                            val scale = 0.95f + (progress * 0.05f)
                            scaleX = scale
                            scaleY = scale
                        },
                    track = effectivePrevTrack,
                    isPlaying = false,
                    progressProvider = { 0f },
                    durationSeconds = effectivePrevTrack.durationSeconds,
                    loadingType = PlaybackLoadingType.NONE,
                    showProgressBar = false,
                    availableWidth = containerWidth,
                    appColors = appColors,
                    context = context,
                    haptic = haptic,
                    isCurrentTrack = false,
                    onTogglePlayPause = { onPlayPrevious(true) },
                    onClickBar = { onPlayPrevious(true) }
                )
            }

            // CURRENT TRACK CARD
            MiniPlayerCard(
                modifier = Modifier
                    .width(containerWidth)
                    .graphicsLayer {
                        val dragX = dragOffsetState.floatValue
                        translationX = dragX
                        val progress = (abs(dragX) / containerWidthPx).coerceIn(0f, 1f)
                        alpha = (1f - progress * 0.35f).coerceIn(0.65f, 1f)
                        val scale = 1f - (progress * 0.05f)
                        scaleX = scale
                        scaleY = scale
                    },
                track = track,
                isPlaying = isPlaying,
                progressProvider = { if (durationSeconds > 0) (currentPositionFlow.value / durationSeconds).coerceIn(0f, 1f) else 0f },
                durationSeconds = durationSeconds,
                loadingType = loadingType,
                showProgressBar = true,
                availableWidth = containerWidth,
                appColors = appColors,
                context = context,
                haptic = haptic,
                isCurrentTrack = true,
                previousTrack = previousTrack,
                nextTrack = nextTrack,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onClosePlayer = onClosePlayer,
                onClickBar = onClickBar
            )

            // NEXT TRACK CARD (revealed on left swipe)
            if (effectiveNextTrack != null && isSwipingLeft) {
                MiniPlayerCard(
                    modifier = Modifier
                        .width(containerWidth)
                        .graphicsLayer {
                            val dragX = dragOffsetState.floatValue
                            translationX = dragX + containerWidthPx
                            val progress = (-dragX / containerWidthPx).coerceIn(0f, 1f)
                            alpha = (0.7f + progress * 0.3f).coerceIn(0.7f, 1f)
                            val scale = 0.95f + (progress * 0.05f)
                            scaleX = scale
                            scaleY = scale
                        },
                    track = effectiveNextTrack,
                    isPlaying = false,
                    progressProvider = { 0f },
                    durationSeconds = effectiveNextTrack.durationSeconds,
                    loadingType = PlaybackLoadingType.NONE,
                    showProgressBar = false,
                    availableWidth = containerWidth,
                    appColors = appColors,
                    context = context,
                    haptic = haptic,
                    isCurrentTrack = false,
                    onTogglePlayPause = onPlayNext,
                    onClickBar = onPlayNext
                )
            }
        }
    }
}



@Composable
private fun MiniPlayerCard(
    modifier: Modifier = Modifier,
    track: DownloadedTrack,
    isPlaying: Boolean,
    progressProvider: () -> Float,
    durationSeconds: Int,
    loadingType: PlaybackLoadingType,
    showProgressBar: Boolean,
    availableWidth: Dp,
    appColors: AppThemePalette,
    context: Context,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    isCurrentTrack: Boolean,
    previousTrack: DownloadedTrack? = null,
    nextTrack: DownloadedTrack? = null,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit = {},
    onPlayPrevious: (Boolean) -> Unit = {},
    onClosePlayer: () -> Unit = {},
    onClickBar: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateContentSize(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringContentSize)
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.surfaceCard)
            .border(1.dp, appColors.surfaceBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClickBar() }
    ) {
        // High-precision timeline track on top
        if (showProgressBar) {
            if (loadingType.isLocalStorage || loadingType.isNetwork) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = if (loadingType.isLocalStorage) Color(0xFFFFB300) else Color(0xFF29B6F6),
                    trackColor = appColors.surfaceBorder,
                )
            } else {
                LinearProgressIndicator(
                    progress = progressProvider,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = appColors.primary,
                    trackColor = appColors.surfaceBorder,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(appColors.surfaceBorder.copy(alpha = 0.35f))
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            val cardAvailableWidth = maxWidth

            if (cardAvailableWidth >= 580.dp && isCurrentTrack) {
                // WIDE / TABLET / LANDSCAPE LAYOUT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Preview: Previous Track
                    if (previousTrack != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = appColors.surfaceElevated.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .widthIn(max = 140.dp)
                                .bounceClickable(scaleDown = 0.95f, shape = RoundedCornerShape(12.dp)) { onPlayPrevious(false) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(appColors.surface)
                                ) {
                                    if (hasRealArtwork(previousTrack)) {
                                        AsyncImage(
                                            model = remember(previousTrack.id, previousTrack.albumArtUrl, previousTrack.localFilePath) {
                                                ImageRequest.Builder(context)
                                                    .data(previousTrack)
                                                    .bitmapConfig(Bitmap.Config.RGB_565)
                                                    .precision(coil.size.Precision.EXACT)
                                                    .crossfade(true)
                                                    .size(64, 64)
                                                    .build()
                                            },
                                            contentDescription = "Previous Track Art",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        TrackArtworkPlaceholder(cornerRadius = 6.dp, iconSize = 14.dp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = previousTrack.title,
                                        color = appColors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = previousTrack.artist,
                                        color = appColors.textSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Main Center Track Info
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = { com.anuj.obsidian.ui.animation.ObsidianMotion.MiniPlayerTrackTransition },
                        label = "mini_player_wide_track_info",
                        modifier = Modifier.weight(1f)
                    ) { currentTrack ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable(scaleDown = 0.98f, shape = RoundedCornerShape(12.dp)) { onClickBar() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .sharedAlbumArt(currentTrack.id, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(appColors.surfaceElevated)
                            ) {
                                if (hasRealArtwork(currentTrack)) {
                                    AsyncImage(
                                        model = remember(currentTrack.id, currentTrack.albumArtUrl, currentTrack.localFilePath) {
                                            ImageRequest.Builder(context)
                                                .data(currentTrack)
                                                .bitmapConfig(Bitmap.Config.RGB_565)
                                                .precision(coil.size.Precision.EXACT)
                                                .crossfade(true)
                                                .size(96, 96)
                                                .build()
                                        },
                                        contentDescription = "Cover art",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    TrackArtworkPlaceholder(cornerRadius = 10.dp, iconSize = 22.dp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentTrack.title,
                                        color = appColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f, fill = false).basicMarquee()
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (currentTrack.format.isNotBlank()) currentTrack.format.uppercase() else "FLAC",
                                        color = appColors.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val sampleRateText = if (currentTrack.sampleRate.isNotBlank()) " • ${currentTrack.sampleRate}" else ""
                                    Text(
                                        text = "${currentTrack.artist}$sampleRateText",
                                        color = appColors.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee()
                                    )
                                }
                            }
                        }
                    }

                    // Right Preview: Next Track
                    if (nextTrack != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = appColors.surfaceElevated.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .widthIn(max = 140.dp)
                                .bounceClickable(scaleDown = 0.95f, shape = RoundedCornerShape(12.dp)) { onPlayNext() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = nextTrack.title,
                                        color = appColors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = nextTrack.artist,
                                        color = appColors.textSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(appColors.surface)
                                ) {
                                    if (hasRealArtwork(nextTrack)) {
                                        AsyncImage(
                                            model = remember(nextTrack.id, nextTrack.albumArtUrl, nextTrack.localFilePath) {
                                                ImageRequest.Builder(context)
                                                    .data(nextTrack)
                                                    .bitmapConfig(Bitmap.Config.RGB_565)
                                                    .precision(coil.size.Precision.EXACT)
                                                    .crossfade(true)
                                                    .size(64, 64)
                                                    .build()
                                            },
                                            contentDescription = "Next Track Art",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        TrackArtworkPlaceholder(cornerRadius = 6.dp, iconSize = 14.dp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Playback Controls Row
                    MiniControlsCluster(
                        isPlaying = isPlaying,
                        loadingType = loadingType,
                        appColors = appColors,
                        showSkipPrevious = true,
                        showSkipNext = true,
                        showClose = true,
                        onTogglePlayPause = onTogglePlayPause,
                        onPlayPrevious = onPlayPrevious,
                        onPlayNext = onPlayNext,
                        onClosePlayer = onClosePlayer,
                        haptic = haptic
                    )
                }
            } else {
                // NORMAL / COMPACT PHONE LAYOUT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Area: Album Art + Track Info + Equalizer (Dedicated tap target for expanding full player)
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = { com.anuj.obsidian.ui.animation.ObsidianMotion.MiniPlayerTrackTransition },
                        label = "mini_player_portrait_track_info",
                        modifier = Modifier.weight(1f)
                    ) { currentTrack ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable(scaleDown = 0.98f, shape = RoundedCornerShape(12.dp)) { onClickBar() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Album Art thumbnail
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .then(
                                        if (isCurrentTrack) Modifier.sharedAlbumArt(currentTrack.id, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(appColors.surfaceElevated)
                            ) {
                                if (hasRealArtwork(currentTrack)) {
                                    AsyncImage(
                                        model = remember(currentTrack.id, currentTrack.albumArtUrl, currentTrack.localFilePath) {
                                            ImageRequest.Builder(context)
                                                .data(currentTrack)
                                                .bitmapConfig(Bitmap.Config.RGB_565)
                                                .precision(coil.size.Precision.EXACT)
                                                .crossfade(true)
                                                .size(96, 96)
                                                .build()
                                        },
                                        contentDescription = "Cover art",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    TrackArtworkPlaceholder(cornerRadius = 10.dp, iconSize = 22.dp)
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Track title, artist & lossless label
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .animateContentSize(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringContentSize)
                                    .padding(end = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.animateContentSize(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringContentSize),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentTrack.title,
                                        color = appColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .basicMarquee()
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))

                                    if (isCurrentTrack && loadingType.isLocalStorage) {
                                        MiniDiskIoBadge()
                                    } else if (isCurrentTrack && loadingType.isNetwork) {
                                        MiniBufferingBadge()
                                    } else {
                                        Text(
                                            text = if (currentTrack.format.isNotBlank()) currentTrack.format.uppercase() else "FLAC",
                                            color = appColors.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val sampleRateText = if (currentTrack.sampleRate.isNotBlank()) " • ${currentTrack.sampleRate}" else ""
                                    Text(
                                        text = "${currentTrack.artist}$sampleRateText",
                                        color = appColors.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee()
                                    )
                                }
                            }

                            // Equalizer Visualizer (only on active track)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isCurrentTrack && isPlaying && cardAvailableWidth >= 320.dp,
                                enter = fadeIn(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick),
                                exit = fadeOut(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick)
                            ) {
                                EqualizerSpectrumLiveBar(
                                    isPlaying = isPlaying,
                                    primaryColor = appColors.primary,
                                    secondaryColor = Color(0xFF00E5FF),
                                    barCount = 4,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .width(26.dp)
                                        .height(18.dp)
                                )
                            }
                        }
                    }

                    // Playback Controls
                    MiniControlsCluster(
                        isPlaying = isPlaying,
                        loadingType = loadingType,
                        appColors = appColors,
                        showSkipPrevious = isCurrentTrack && cardAvailableWidth >= 360.dp,
                        showSkipNext = isCurrentTrack,
                        showClose = isCurrentTrack,
                        onTogglePlayPause = onTogglePlayPause,
                        onPlayPrevious = onPlayPrevious,
                        onPlayNext = onPlayNext,
                        onClosePlayer = onClosePlayer,
                        haptic = haptic
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniControlsCluster(
    isPlaying: Boolean,
    loadingType: PlaybackLoadingType,
    appColors: AppThemePalette,
    showSkipPrevious: Boolean,
    showSkipNext: Boolean = true,
    showClose: Boolean = true,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: (Boolean) -> Unit,
    onPlayNext: () -> Unit,
    onClosePlayer: () -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showSkipPrevious) {
            Surface(
                shape = CircleShape,
                color = appColors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder),
                modifier = Modifier
                    .size(36.dp)
                    .circularBounceClickable {
                        onPlayPrevious(false)
                    }
                    .testTag("mini_player_previous")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Play/Pause button
        Surface(
            shape = CircleShape,
            color = appColors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder),
            modifier = Modifier
                .size(38.dp)
                .circularBounceClickable(scaleDown = 0.90f) { onTogglePlayPause() }
                .testTag("mini_player_play_pause")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (loadingType.isLocalStorage) {
                    MiniStorageSpinnerIcon()
                } else if (loadingType.isNetwork) {
                    MiniNetworkSpinnerIcon()
                } else {
                    AnimatedPlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = appColors.primary,
                        size = 24.dp
                    )
                }
            }
        }

        if (showSkipNext) {
            Spacer(modifier = Modifier.width(6.dp))

            // Next button
            Surface(
                shape = CircleShape,
                color = appColors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder),
                modifier = Modifier
                    .size(36.dp)
                    .circularBounceClickable {
                        onPlayNext()
                    }
                    .testTag("mini_player_next")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (showClose) {
            Spacer(modifier = Modifier.width(6.dp))

            // Close button
            Surface(
                shape = CircleShape,
                color = appColors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder),
                modifier = Modifier
                    .size(36.dp)
                    .circularBounceClickable {
                        onClosePlayer()
                    }
                    .testTag("mini_player_close")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Player & Notification",
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EqualizerWaveAnimation(
    modifier: Modifier = Modifier,
    color: Color = ObsidianTheme.colors.primary,
    isPlaying: Boolean = true
) {
    val activeMultiplierState = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringMicro,
        label = "eq_active_mult"
    )
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "equalizer_phase")
    val animatedPhaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = com.anuj.obsidian.ui.animation.MotionSpec.EqualizerLoopSpec.animation,
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "eq_phase"
    )

    Canvas(
        modifier = modifier
            .width(22.dp)
            .height(16.dp)
            .hardwareAccelerated()
    ) {
        val phase = animatedPhaseState.value
        val activeMultiplier = activeMultiplierState.value
        val bar1Val = 0.25f + (0.25f + 0.42f * kotlin.math.sin(phase.toDouble()).toFloat()) * activeMultiplier
        val bar2Val = 0.35f + (0.30f + 0.32f * kotlin.math.sin((phase * 1.6f + 1.4f).toDouble()).toFloat()) * activeMultiplier
        val bar3Val = 0.28f + (0.44f + 0.26f * kotlin.math.sin((phase * 1.1f + 2.8f).toDouble()).toFloat()) * activeMultiplier
        val bar4Val = 0.30f + (0.18f + 0.38f * kotlin.math.sin((phase * 2.1f + 0.6f).toDouble()).toFloat()) * activeMultiplier

        val w = 3.2.dp.toPx()
        val spacing = 2.2.dp.toPx()
        val h = size.height
        val corner = CornerRadius(w / 2f, w / 2f)

        val b1H = (h * bar1Val.coerceIn(0.18f, 1.0f)).coerceAtLeast(3.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, h - b1H),
            size = Size(w, b1H),
            cornerRadius = corner
        )

        val b2H = (h * bar2Val.coerceIn(0.18f, 1.0f)).coerceAtLeast(3.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(1f * (w + spacing), h - b2H),
            size = Size(w, b2H),
            cornerRadius = corner
        )

        val b3H = (h * bar3Val.coerceIn(0.18f, 1.0f)).coerceAtLeast(3.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(2f * (w + spacing), h - b3H),
            size = Size(w, b3H),
            cornerRadius = corner
        )

        val b4H = (h * bar4Val.coerceIn(0.18f, 1.0f)).coerceAtLeast(3.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(3f * (w + spacing), h - b4H),
            size = Size(w, b4H),
            cornerRadius = corner
        )
    }
}

@Composable
private fun MiniDiskIoBadge(modifier: Modifier = Modifier) {
    val infiniteRot = rememberInfiniteTransition(label = "storage_io_spin")
    val rotAngle = infiniteRot.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "storage_rot"
    )
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF332600),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFB300)),
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = "Storage I/O",
                tint = Color(0xFFFFB300),
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer { rotationZ = rotAngle.value }
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "DISK I/O",
                color = Color(0xFFFFD54F),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MiniBufferingBadge(modifier: Modifier = Modifier) {
    val infinitePulse = rememberInfiniteTransition(label = "buffering_pulse")
    val pulseAlpha = infinitePulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buffering_alpha"
    )
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF001A33),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF29B6F6)),
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Buffering",
                tint = Color(0xFF29B6F6),
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer { alpha = pulseAlpha.value }
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "BUFFERING",
                color = Color(0xFF81D4FA),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MiniStorageSpinnerIcon() {
    Box(contentAlignment = Alignment.Center) {
        ObsidianLoadingSpinner(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
            color = Color(0xFFFFB300),
            trackColor = Color(0xFFFFB300).copy(alpha = 0.2f),
            size = 24.dp
        )
        val infiniteTrans = rememberInfiniteTransition(label = "mini_storage_pulse")
        val pulseAlpha = infiniteTrans.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "mini_pulse_alpha"
        )
        Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = "Loading local file",
            tint = Color(0xFFFFB300),
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { alpha = pulseAlpha.value }
        )
    }
}

@Composable
private fun MiniNetworkSpinnerIcon() {
    Box(contentAlignment = Alignment.Center) {
        ObsidianLoadingSpinner(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
            color = Color(0xFF29B6F6),
            trackColor = Color(0xFF29B6F6).copy(alpha = 0.2f),
            size = 24.dp
        )
        val infiniteTrans = rememberInfiniteTransition(label = "mini_net_pulse")
        val pulseScale = infiniteTrans.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "mini_pulse_scale"
        )
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = "Network buffering",
            tint = Color(0xFF29B6F6),
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer {
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                }
        )
    }
}
