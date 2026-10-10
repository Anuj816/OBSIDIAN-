package com.anuj.obsidian.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.anuj.obsidian.ui.animation.safePerform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.PlaybackLoadingType
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.ui.animation.ambientPulseGlow
import com.anuj.obsidian.ui.animation.circularBounceClickable
import com.anuj.obsidian.ui.animation.sharedAlbumArt
import com.anuj.obsidian.ui.theme.AppThemePalette
import kotlin.math.absoluteValue

/**
 * Clean Full Album Artwork Carousel.
 * - Large, prominent album art cards with clean rounded rectangle corners.
 * - Dynamic tactile scale & elevation (expanded when playing, subtly contracted when paused).
 * - Breathing ambient color aura backdrop matching the track's dominant palette.
 * - Natural 3D turntable edge previews showing adjacent album covers with smooth perspective physics.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtworkCarousel(
    currentTrack: DownloadedTrack?,
    previousTrack: DownloadedTrack?,
    nextTrack: DownloadedTrack?,
    onPlayNext: () -> Unit,
    onPlayPrevious: (Boolean) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenTagInspector: (DownloadedTrack) -> Unit,
    onToggleFavorite: (DownloadedTrack) -> Unit,
    isFavorite: Boolean,
    appColors: AppThemePalette,
    isPlaying: Boolean = true,
    dynamicPrimaryColor: Color = Color.Unspecified,
    loadingType: PlaybackLoadingType = PlaybackLoadingType.NONE,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    if (currentTrack == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val initialPage = 1
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 3 }
    )

    // Dynamic scale and elevation responsive to playback state
    val playingScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.89f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
        label = "art_playing_scale"
    )

    val playingElevation by animateDpAsState(
        targetValue = if (isPlaying) 18.dp else 6.dp,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmoothDp,
        label = "art_playing_elevation"
    )

    // Keep pager centered on Page 1 whenever currentTrack changes
    LaunchedEffect(currentTrack.id) {
        if (pagerState.currentPage != 1) {
            pagerState.scrollToPage(1)
        }
    }

    // Trigger song changes when user actively performs a gesture swipe that settles on page 0 or 2
    LaunchedEffect(pagerState, currentTrack.id, nextTrack?.id, previousTrack?.id) {
        snapshotFlow { pagerState.settledPage }
            .collect { page ->
                if (page == 2 && nextTrack != null && nextTrack.id != currentTrack.id) {
                    haptic.safePerform(HapticFeedbackType.TextHandleMove)
                    onPlayNext()
                } else if (page == 0 && previousTrack != null && previousTrack.id != currentTrack.id) {
                    haptic.safePerform(HapticFeedbackType.TextHandleMove)
                    onPlayPrevious(true)
                } else if ((page == 0 && previousTrack == null) || (page == 2 && nextTrack == null)) {
                    if (pagerState.currentPage != 1) {
                        pagerState.animateScrollToPage(1)
                    }
                }
            }
    }

    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = androidx.compose.foundation.pager.PagerSnapDistance.atMost(1),
        snapAnimationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringHero
    )

    var touchTiltX by remember { mutableFloatStateOf(0f) }
    var touchTiltY by remember { mutableFloatStateOf(0f) }
    var isCardPressed by remember { mutableStateOf(false) }
    var doubleTapBurst by remember { mutableIntStateOf(0) }

    val springTiltX by animateFloatAsState(
        targetValue = if (isCardPressed) touchTiltX else 0f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
        label = "spring_tilt_x"
    )
    val springTiltY by animateFloatAsState(
        targetValue = if (isCardPressed) touchTiltY else 0f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
        label = "spring_tilt_y"
    )

    val cardCornerRadius = 18.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val glowColor = if (dynamicPrimaryColor != Color.Unspecified) dynamicPrimaryColor else appColors.primary

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(currentTrack.id) {
                detectTapGestures(
                    onPress = { offset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        touchTiltY = ((offset.x - centerX) / centerX).coerceIn(-1f, 1f) * 8f
                        touchTiltX = -((offset.y - centerY) / centerY).coerceIn(-1f, 1f) * 8f
                        isCardPressed = true
                        tryAwaitRelease()
                        isCardPressed = false
                    },
                    onDoubleTap = {
                        haptic.safePerform(HapticFeedbackType.LongPress)
                        doubleTapBurst++
                        onToggleFavorite(currentTrack)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val maxCardWidth = if (maxWidth > 92.dp) maxWidth - 92.dp else maxWidth
        val maxCardHeight = if (maxHeight > 16.dp) maxHeight - 16.dp else maxHeight
        val cardSquare = if (maxCardWidth > 0.dp && maxCardHeight > 0.dp) minOf(maxCardWidth, maxCardHeight) else 260.dp

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 46.dp),
            flingBehavior = flingBehavior
        ) { page ->
            val isCenter = page == 1
            val isNextPreview = page == 2
            val isPrevPreview = page == 0

            val track = when (page) {
                0 -> previousTrack ?: currentTrack
                1 -> currentTrack
                2 -> nextTrack ?: currentTrack
                else -> currentTrack
            }

            val hasArt = hasRealArtwork(track)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                val cardModifier = Modifier
                    .size(cardSquare)
                    .aspectRatio(1f)
                    .padding(vertical = 4.dp)

                val interactiveModifier = if (isCenter) {
                    cardModifier
                        .ambientPulseGlow(
                            enabled = isPlaying,
                            color = glowColor,
                            maxAlpha = 0.38f,
                            minScale = 1.0f,
                            maxScale = 1.09f
                        )
                        .sharedAlbumArt(track.id, cardShape)
                } else {
                    cardModifier
                        .clip(cardShape)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.safePerform(HapticFeedbackType.LongPress)
                            if (isNextPreview) onPlayNext() else if (isPrevPreview) onPlayPrevious(true)
                        }
                }

                Surface(
                    modifier = interactiveModifier
                        .graphicsLayer {
                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-2.5f, 2.5f)
                            val absOffset = pageOffset.absoluteValue
                            // Seamless continuous interpolation between full playing scale (at center) and side cards
                            val targetScale = (playingScale * (1f - (absOffset * 0.12f))).coerceIn(0.82f, 1.05f)
                            scaleX = targetScale
                            scaleY = targetScale
                            alpha = (1f - (absOffset * 0.30f)).coerceIn(0.58f, 1.0f)
                            cameraDistance = 18f * density
                            rotationY = ((-pageOffset * 10.0f) + (if (isCenter) springTiltY else 0f)).coerceIn(-25f, 25f)
                            rotationX = if (isCenter) springTiltX.coerceIn(-15f, 15f) else 0f
                            shape = cardShape
                            clip = true
                        },
                    shape = cardShape,
                    color = appColors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        if (isCenter) 1.5.dp else 1.dp,
                        if (isCenter) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.15f)
                    ),
                    shadowElevation = if (isCenter) playingElevation else 4.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (hasArt) {
                            AsyncImage(
                                model = remember(track.id, track.albumArtUrl, track.localFilePath) {
                                    ImageRequest.Builder(context)
                                        .data(track)
                                        .memoryCacheKey(track.id.toString())
                                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                                        .size(800, 800)
                                        .scale(coil.size.Scale.FILL)
                                        .precision(coil.size.Precision.EXACT)
                                        .allowHardware(true)
                                        .crossfade(300)
                                        .build()
                                },
                                contentDescription = if (isCenter) "Now Playing Artwork" else if (isNextPreview) "Next Track" else "Previous Track",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            TrackWaveformArt(
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = cardCornerRadius,
                                tint = appColors.primary
                            )
                        }

                        // Subtle bottom gradient and Like/Dislike Pill on center card only
                        if (isCenter) {
                            if (loadingType.isLocalStorage || loadingType.isNetwork) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Black.copy(alpha = 0.70f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, (if (loadingType.isLocalStorage) Color(0xFFFFB300) else Color(0xFF29B6F6)).copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(14.dp)
                                        .testTag("artwork_loading_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ObsidianLoadingSpinner(
                                            size = 14.dp,
                                            strokeWidth = 2.dp,
                                            color = if (loadingType.isLocalStorage) Color(0xFFFFB300) else Color(0xFF29B6F6)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (loadingType.isLocalStorage) "LOADING" else "BUFFERING",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.55f)
                                            )
                                        )
                                    )
                            )

                            // Animated Floating Heart Burst on Double-Tap
                            if (doubleTapBurst > 0) {
                                val burstAnim = remember(doubleTapBurst) { androidx.compose.animation.core.Animatable(0f) }
                                LaunchedEffect(doubleTapBurst) {
                                    burstAnim.snapTo(0f)
                                    burstAnim.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                                    )
                                }
                                if (burstAnim.value in 0.01f..0.99f) {
                                    val progress = burstAnim.value
                                    val scale = (0.5f + (progress * 1.1f)).coerceAtMost(1.6f)
                                    val alpha = (1f - (progress * 0.95f)).coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                                this.alpha = alpha
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color(0xFFFF3366),
                                            modifier = Modifier.size(72.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.Black.copy(alpha = 0.70f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                    contentColor = Color.White,
                                    modifier = Modifier.testTag("artwork_like_dislike_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .circularBounceClickable { onToggleFavorite(currentTrack) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isFavorite) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                                contentDescription = "Like",
                                                tint = if (isFavorite) appColors.primary else Color.White.copy(alpha = 0.9f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(14.dp)
                                                .background(Color.White.copy(alpha = 0.25f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .circularBounceClickable { onPlayNext() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ThumbDown,
                                                contentDescription = "Dislike & Skip",
                                                tint = Color.White.copy(alpha = 0.75f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Clean subtle shade on peeking edge preview cards (No text badges, no circle overlays)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.25f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.40f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
