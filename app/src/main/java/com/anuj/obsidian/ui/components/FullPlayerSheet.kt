package com.anuj.obsidian.ui.components

import kotlinx.coroutines.isActive
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.anuj.obsidian.ui.animation.safePerform
import kotlin.math.abs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anuj.obsidian.ui.animation.ambientPulseGlow
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.data.model.PlaybackLoadingType
import com.anuj.obsidian.ui.components.player.BackgroundVisualizerLayer
import com.anuj.obsidian.ui.AutoplayMode
import com.anuj.obsidian.ui.util.ArtworkPaletteCache
import com.anuj.obsidian.ui.animation.AnimatedPlayPauseIcon
import com.anuj.obsidian.ui.animation.AnimatedFavoriteIcon
import com.anuj.obsidian.ui.animation.pressSpring
import com.anuj.obsidian.ui.animation.circularBounceClickable
import com.anuj.obsidian.ui.animation.circularBounceCombinedClickable
import com.anuj.obsidian.ui.animation.hardwareAccelerated
import com.anuj.obsidian.ui.animation.sharedAlbumArt
import com.anuj.obsidian.ui.animation.sharedTrackArtist
import com.anuj.obsidian.ui.animation.sharedTrackTitle
import com.anuj.obsidian.ui.animation.nonArtContentTransition
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.visualizer.VisualizerLibrary
import com.anuj.obsidian.ui.visualizer.renderObsidianVisualizer
import com.anuj.obsidian.ui.visualizer.VisualizerScratchPad
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullPlayerSheet(
    track: DownloadedTrack?,
    previousTrack: DownloadedTrack?,
    nextTrack: DownloadedTrack?,
    isPlaying: Boolean,
    currentPositionFlow: StateFlow<Float>,
    durationSeconds: Int,
    isShuffle: Boolean,
    shuffleMode: AdvancedShuffleMode = AdvancedShuffleMode.SHUFFLE_OFF,
    autoplayMode: AutoplayMode,
    speed: Float,
    loadingType: PlaybackLoadingType = PlaybackLoadingType.NONE,
    onVerticalDrag: (Float) -> Unit = {},
    onVerticalDragEnd: (Float) -> Unit = {},
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: (Boolean) -> Unit,
    onSkipToNextCategory: () -> Unit = {},
    onSkipToPreviousCategory: () -> Unit = {},
    onSeek: (Float, Boolean) -> Unit,
    onToggleShuffle: () -> Unit,
    onSelectShuffleMode: (AdvancedShuffleMode) -> Unit = {},
    onLongToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit,
    onToggleFavorite: (DownloadedTrack) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenTagInspector: (DownloadedTrack) -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    onCycleSpeed: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onSelectAutoplayMode: (AutoplayMode) -> Unit = {},
    isSheet: Boolean = true,
    sleepTimerMinutes: Int? = null,
    onSetSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onOpenDriveMode: () -> Unit = {},
    initialVisualizerPresetId: Int = 3,
    initialBackgroundVisualizer: Boolean = false,
    initialVisualizerAlpha: Float = 1.0f,
    initialVisualizerCycleMode: String = "off",
    initialVisualizerColorMode: String = "ORIGINAL",
    onSaveVisualizerSettings: (presetId: Int, bgEnabled: Boolean, alpha: Float) -> Unit = { _, _, _ -> },
    onSaveVisualizerCycleMode: (String) -> Unit = {},
    onSaveVisualizerColorMode: (String) -> Unit = {},
    initialDynamicCanvasEnabled: Boolean = false,
    initialDynamicCanvasPreset: String = "cosmic_nebula",
    onSaveDynamicCanvasSettings: (enabled: Boolean, preset: String) -> Unit = { _, _ -> },
    onClosePlayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (track == null) return
    android.util.Log.d("FullPlayerSheet", "FullPlayerSheet composition started: track=${track.id} (${track.title}), artUrl=${track.albumArtUrl}")
    val context = LocalContext.current
    val appColors = ObsidianTheme.colors
    val initialCached = remember(track.id) {
        try {
            ArtworkPaletteCache.getCachedColors(track)
        } catch (e: Throwable) {
            null
        }
    }
    var extractedPrimary by remember(track.id) {
        mutableStateOf(
            try {
                initialCached?.primary ?: appColors.primary
            } catch (e: Throwable) {
                appColors.primary
            }
        )
    }
    var extractedSecondary by remember(track.id) {
        mutableStateOf(
            try {
                initialCached?.secondary ?: appColors.secondary
            } catch (e: Throwable) {
                appColors.secondary
            }
        )
    }
    var extractedBackground by remember(track.id, appColors.background) {
        mutableStateOf(appColors.background)
    }

    val dynamicPrimaryColor by animateColorAsState(
        targetValue = extractedPrimary,
        animationSpec = tween(com.anuj.obsidian.ui.animation.MotionSpec.DurationExpressive, easing = com.anuj.obsidian.ui.animation.MotionSpec.EasingEmphasized),
        label = "PrimaryColor"
    )
    val dynamicSecondaryColor by animateColorAsState(
        targetValue = extractedSecondary,
        animationSpec = tween(com.anuj.obsidian.ui.animation.MotionSpec.DurationExpressive, easing = com.anuj.obsidian.ui.animation.MotionSpec.EasingEmphasized),
        label = "SecondaryColor"
    )
    val dynamicBackgroundColor by animateColorAsState(
        targetValue = extractedBackground,
        animationSpec = tween(com.anuj.obsidian.ui.animation.MotionSpec.DurationExpressive, easing = com.anuj.obsidian.ui.animation.MotionSpec.EasingEmphasized),
        label = "BgColor"
    )

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showDspSheet by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showVisualizerSheet by remember { mutableStateOf(false) }
    var showShuffleSheet by remember { mutableStateOf(false) }
    var showCanvasSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showTrimmerSheet by remember { mutableStateOf(false) }
    var showSoundscapeSheet by remember { mutableStateOf(false) }
    var showMoreTools by remember { mutableStateOf(false) }

    var isOverlayVisualizerActive by remember { mutableStateOf(false) }
    val overlayVisualizerTransition by animateFloatAsState(
        targetValue = if (isOverlayVisualizerActive) 1f else 0f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard,
        label = "overlayVisualizerTransition"
    )

    var isCanvasEnabled by remember(initialDynamicCanvasEnabled) { mutableStateOf(initialDynamicCanvasEnabled) }
    var canvasPreset by remember(initialDynamicCanvasPreset) { mutableStateOf(initialDynamicCanvasPreset) }
    var canvasAlpha by remember { mutableFloatStateOf(0.85f) }

    val btAudioInfo by com.anuj.obsidian.util.BluetoothAudioCodecManager.bluetoothAudioInfo.collectAsStateWithLifecycle()
    val settings by com.anuj.obsidian.ObsidianApplication.repository.settings.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(track.id, settings.visualizerBackgroundMode) {
        if (settings.visualizerBackgroundMode == "fixed") {
            extractedPrimary = appColors.primary
            extractedSecondary = appColors.secondary
            extractedBackground = appColors.background
        } else {
            try {
                val colors = ArtworkPaletteCache.extractColors(
                    context = context,
                    track = track,
                    fallbackPrimary = appColors.primary,
                    fallbackSecondary = appColors.secondary,
                    fallbackBackground = Color.Black
                )
                extractedPrimary = colors.primary
                extractedSecondary = colors.secondary
                extractedBackground = appColors.background
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "FullPlayerSheet.extractColors")
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isSwipeSeeking by remember { mutableStateOf(false) }
    var swipeSeekDeltaSec by remember { mutableFloatStateOf(0f) }
    var swipeSeekTargetSec by remember { mutableFloatStateOf(0f) }

    var visualizerPresetId by remember(initialVisualizerPresetId) { mutableIntStateOf(initialVisualizerPresetId) }
    var backgroundVisualizer by remember(initialBackgroundVisualizer) { mutableStateOf(initialBackgroundVisualizer) }
    var visualizerAlpha by remember(initialVisualizerAlpha) { mutableFloatStateOf(initialVisualizerAlpha) }
    var visualizerColorMode by remember(initialVisualizerColorMode) { mutableStateOf(initialVisualizerColorMode) }

    var bassBoostDb by remember { mutableFloatStateOf(6.0f) }
    var trebleBoostDb by remember { mutableFloatStateOf(3.5f) }
    var stereoExpandPercent by remember { mutableFloatStateOf(70f) }
    var selectedPreset by remember { mutableStateOf("STUDIO HI-FI") }

    if (isSheet) {
        BackHandler {
            onDismiss()
        }
    }

    // Modal Sheet for Speed Control
    if (showSpeedSheet) {
        SpeedControlSheet(
            currentSpeed = speed,
            onSpeedChange = onSpeedChange,
            onDismiss = { showSpeedSheet = false }
        )
    }

    // Modal Sheet for Sleep Timer
    if (showSleepTimerSheet) {
        com.anuj.obsidian.ui.player.components.PlayerSleepTimerModal(
            sleepTimerMinutes = sleepTimerMinutes,
            onSetSleepTimer = onSetSleepTimer,
            onCancelSleepTimer = onCancelSleepTimer,
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showShuffleSheet) {
        com.anuj.obsidian.ui.player.components.PlayerShuffleSheet(
            shuffleMode = shuffleMode,
            dynamicPrimaryColor = dynamicPrimaryColor,
            onSelectShuffleMode = onSelectShuffleMode,
            onDismiss = { showShuffleSheet = false }
        )
    }

    // Modal Sheet for Visualizer Selection
    if (showVisualizerSheet) {
        VisualizerSelectorSheet(
            selectedPresetId = visualizerPresetId,
            isBackgroundVisualizerEnabled = backgroundVisualizer,
            visualizerAlpha = visualizerAlpha,
            visualizerCycleMode = initialVisualizerCycleMode,
            visualizerColorMode = visualizerColorMode,
            onSelectPreset = {
                visualizerPresetId = it.id
                onSaveVisualizerSettings(it.id, backgroundVisualizer, visualizerAlpha)
            },
            onToggleBackgroundVisualizer = {
                backgroundVisualizer = it
                onSaveVisualizerSettings(visualizerPresetId, it, visualizerAlpha)
            },
            onAlphaChanged = {
                visualizerAlpha = it
                onSaveVisualizerSettings(visualizerPresetId, backgroundVisualizer, it)
            },
            onCycleModeChanged = {
                onSaveVisualizerCycleMode(it)
            },
            onColorModeChanged = {
                visualizerColorMode = it
                onSaveVisualizerColorMode(it)
            },
            onDismiss = { showVisualizerSheet = false }
        )
    }

    // Modal Sheet for Dynamic Canvas & Animated Album Art
    if (showCanvasSheet) {
        DynamicCanvasSelectorSheet(
            isEnabled = isCanvasEnabled,
            selectedPresetId = canvasPreset,
            canvasAlpha = canvasAlpha,
            onToggleEnabled = {
                isCanvasEnabled = it
                onSaveDynamicCanvasSettings(it, canvasPreset)
            },
            onSelectPreset = {
                canvasPreset = it
                onSaveDynamicCanvasSettings(isCanvasEnabled, it)
            },
            onAlphaChanged = {
                canvasAlpha = it
            },
            onDismiss = { showCanvasSheet = false }
        )
    }

    // Modal Sheet for Parametric DSP Equalizer & Effects
    if (showDspSheet) {
        ObsidianDspBottomSheet(
            onDismiss = { showDspSheet = false },
            primaryColor = dynamicPrimaryColor,
            secondaryColor = dynamicSecondaryColor,
            surfaceColor = dynamicBackgroundColor
        )
    }

    if (showVolumeDialog) {
        AndroidVolumeDialog(
            onDismiss = { showVolumeDialog = false }
        )
    }

    if (showTrimmerSheet) {
        AudioTrimmerSheet(
            track = track,
            onDismiss = { showTrimmerSheet = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .hardwareAccelerated()
    ) {
        if (isSheet) {
            // Static Scrim backdrop for sheet
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { onDismiss() }
                )
            }
        }
        // Sheet Content Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSheet) {
                        Modifier
                                        .graphicsLayer {
                                // Scaling and translation handled by parent transition
                            }
                            .pointerInput(Unit) {
                                val maxPx = size.height.toFloat()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val currentPointerId = down.id
                                    val touchSlop = viewConfiguration.touchSlop
                                    var totalDragY = 0f
                                    var isVerticalDrag = false
                                    var lastY = down.position.y
                                    var lastTime = System.currentTimeMillis()
                                    var velocityY = 0f
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == currentPointerId } ?: event.changes.firstOrNull()
                                        if (change == null || !change.pressed) break
                                        val currentTime = System.currentTimeMillis()
                                        val dt = (currentTime - lastTime).coerceAtLeast(1L)
                                        val delta = change.positionChange()
                                        velocityY = (delta.y / (dt / 1000f)).coerceIn(-5000f, 5000f)
                                        lastY = change.position.y
                                        lastTime = currentTime
                                        if (!isVerticalDrag) {
                                             totalDragY += delta.y
                                             if (totalDragY > touchSlop && totalDragY > abs(delta.x)) {
                                                 isVerticalDrag = true
                                                 change.consume()
                                             }
                                        } else {
                                             change.consume()
                                             onVerticalDrag(delta.y)
                                        }
                                    }
                                    if (isVerticalDrag) {
                                        onVerticalDragEnd(velocityY)
                                    }
                                }
                            }
                    } else {
                        Modifier
                    }
                )
                .background(dynamicBackgroundColor)
                .drawWithCache {
                    if (settings.visualizerBackgroundMode == "fixed") {
                        onDrawBehind {
                            drawRect(color = dynamicBackgroundColor, size = size)
                        }
                    } else {
                        val gradient = Brush.radialGradient(
                            colors = listOf(
                                dynamicPrimaryColor.copy(alpha = 0.50f),
                                dynamicSecondaryColor.copy(alpha = 0.25f),
                                dynamicBackgroundColor.copy(alpha = 0.85f)
                            ),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.25f),
                            radius = size.height * 0.85f
                        )
                        onDrawBehind {
                            drawRect(brush = gradient, size = size)
                        }
                    }
                }
        ) {
            if (isCanvasEnabled) {
                DynamicCanvasBackground(
                    presetId = canvasPreset,
                    primaryColor = dynamicPrimaryColor,
                    isPlaying = isPlaying,
                    alpha = canvasAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            }
            val shouldRenderVisualizer = settings.visualizerMasterEnabled && backgroundVisualizer && settings.visualizerPerformanceMode != "disabled"
            val animatedVisAlphaState = animateFloatAsState(
                targetValue = if (shouldRenderVisualizer) visualizerAlpha else 0f,
                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard,
                label = "bgVisAlpha"
            )
            if (!settings.visualizerAboveChrome) {
                BackgroundVisualizerLayer(
                    presetId = visualizerPresetId,
                    isPlaying = isPlaying,
                    progressProvider = { if (durationSeconds > 0) currentPositionFlow.value / durationSeconds.toFloat() else 0f },
                    alphaProvider = { animatedVisAlphaState.value },
                    colorMode = visualizerColorMode,
                    performanceMode = settings.visualizerPerformanceMode,
                    fpsLimit = settings.visualizerFpsLimit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                val availableHeight = maxHeight
                val availableWidth = maxWidth
                val isLandscape = availableWidth > availableHeight

                if (isLandscape) {
                    // ==========================================
                    // DEDICATED LANDSCAPE PLAYER LAYOUT
                    // ==========================================
                    val isShortLandscape = availableHeight < 400.dp
                    val playBtnSize = if (isShortLandscape) 82.dp else 98.dp
                    val sideBtnSize = if (isShortLandscape) 56.dp else 68.dp
                    val playIconSize = if (isShortLandscape) 38.dp else 46.dp
                    val sideIconSize = if (isShortLandscape) 26.dp else 32.dp
                    val visualizerHeight = if (isShortLandscape) 50.dp else 72.dp
                    val titleSize = if (isShortLandscape) 17.sp else 20.sp
                    val artCorner = if (isShortLandscape) 16.dp else 20.dp

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .nonArtContentTransition(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSheet) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.52f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .circularBounceClickable { onDismiss() }
                                        .testTag("full_player_dismiss")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Collapse Player",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.52f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .circularBounceClickable { onOpenQueue() }
                                        .testTag("full_player_queue_btn")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = "Queue",
                                            tint = dynamicPrimaryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Center Logo: MADE BY ANUJ
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            dynamicPrimaryColor.copy(alpha = 0.85f),
                                            Color.White.copy(alpha = 0.35f),
                                            dynamicPrimaryColor.copy(alpha = 0.85f)
                                        )
                                    )
                                ),
                                modifier = Modifier
                                    .testTag("full_player_made_by_anuj_logo_land")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = dynamicPrimaryColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "MADE BY ANUJ",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.1.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Right Action Icons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.52f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .circularBounceClickable { onOpenTagInspector(track) }
                                        .testTag("full_player_tag_inspector")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Tag Inspector",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Main Split Content: Left (Artwork + Metadata) | Right (Visualizer + Controls + Timeline)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Pane: Artwork + Info
                            Column(
                                modifier = Modifier
                                    .weight(0.40f)
                                    .fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val landArtScale by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (isPlaying) 1.0f else 0.90f,
                                    animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
                                    label = "land_art_scale"
                                )
                                val landArtElevation by androidx.compose.animation.core.animateDpAsState(
                                    targetValue = if (isPlaying) 18.dp else 6.dp,
                                    animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmoothDp,
                                    label = "land_art_elevation"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .aspectRatio(1f)
                                        .padding(4.dp)
                                        .ambientPulseGlow(
                                            enabled = isPlaying,
                                            color = dynamicPrimaryColor,
                                            maxAlpha = 0.35f,
                                            minScale = 1.0f,
                                            maxScale = 1.08f
                                        )
                                        .sharedAlbumArt(track.id, RoundedCornerShape(artCorner))
                                        .graphicsLayer {
                                            scaleX = landArtScale
                                            scaleY = landArtScale
                                            shadowElevation = landArtElevation.toPx()
                                            shape = RoundedCornerShape(artCorner)
                                            clip = true
                                        }
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(artCorner)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hasRealArtwork(track)) {
                                        val imageModel = remember(track.id, track.albumArtUrl, track.localFilePath) {
                                            coil.request.ImageRequest.Builder(context)
                                                .data(track)
                                                .bitmapConfig(Bitmap.Config.ARGB_8888)
                                                .size(800, 800)
                                                .scale(coil.size.Scale.FILL)
                                                .precision(coil.size.Precision.EXACT)
                                                .allowHardware(true)
                                                .crossfade(true)
                                                .build()
                                        }
                                        coil.compose.AsyncImage(
                                            model = imageModel,
                                            contentDescription = "Album Artwork",
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        TrackArtworkPlaceholder(
                                            modifier = Modifier.fillMaxSize(),
                                            cornerRadius = artCorner,
                                            iconSize = 64.dp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Track Title & Artist
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AnimatedContent(
                                        targetState = track,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard) togetherWith fadeOut(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick))
                                                .using(SizeTransform(clip = false))
                                        },
                                        label = "land_track_transition"
                                    ) { currentTrack ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = currentTrack.title,
                                                color = Color.White,
                                                fontSize = titleSize,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .sharedTrackTitle(currentTrack.id)
                                                    .basicMarquee()
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${currentTrack.artist} • ${currentTrack.album}",
                                                color = Color.White.copy(alpha = 0.75f),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .sharedTrackArtist(currentTrack.id)
                                                    .basicMarquee()
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Quick Icons Under Info (Favorite & Queue)
                                    Row(
                                        modifier = Modifier.nonArtContentTransition(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (track.isFavorite) dynamicPrimaryColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (track.isFavorite) dynamicPrimaryColor else Color.White.copy(alpha = 0.2f)),
                                            modifier = Modifier
                                                .size(32.dp)
                                                .circularBounceClickable { onToggleFavorite(track) }
                                                .testTag("land_player_favorite")
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                AnimatedFavoriteIcon(
                                                    isFavorite = track.isFavorite,
                                                    activeTint = dynamicPrimaryColor,
                                                    inactiveTint = Color.White.copy(alpha = 0.8f),
                                                    size = 16.dp
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.45f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                            modifier = Modifier
                                                .size(32.dp)
                                                .circularBounceClickable { onOpenQueue() }
                                                .testTag("land_player_open_queue")
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                    contentDescription = "Queue",
                                                    tint = Color.White.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Right Pane: Visualizer + Big Controls + Waveform Seekbar
                            Column(
                                modifier = Modifier
                                    .weight(0.60f)
                                    .fillMaxSize()
                                    .nonArtContentTransition(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Real-Time Audio Visualizer Surface
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(visualizerHeight)
                                        .padding(horizontal = 4.dp)
                                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BackgroundVisualizerLayer(
                                        presetId = visualizerPresetId,
                                        isPlaying = isPlaying,
                                        progressProvider = { if (durationSeconds > 0) currentPositionFlow.value / durationSeconds.toFloat() else 0f },
                                        alphaProvider = { 0.95f },
                                        colorMode = visualizerColorMode,
                                        performanceMode = settings.visualizerPerformanceMode,
                                        fpsLimit = settings.visualizerFpsLimit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // HUGE PLAYBACK CONTROLS ROW
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Shuffle
                                    val shuffleScaleLand by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isShuffle) 1.12f else 1.0f,
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringMicro,
                                        label = "shuffle_scale_land"
                                    )
                                    val shuffleColorLand by animateColorAsState(
                                        targetValue = if (isShuffle) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "shuffle_color_land"
                                    )
                                    val shuffleBorderColorLand by animateColorAsState(
                                        targetValue = if (isShuffle) dynamicPrimaryColor else Color.White.copy(alpha = 0.2f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "shuffle_border_land"
                                    )
                                    val shuffleTintLand by animateColorAsState(
                                        targetValue = if (isShuffle) dynamicPrimaryColor else Color.White.copy(alpha = 0.85f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "shuffle_tint_land"
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = shuffleColorLand,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, shuffleBorderColorLand),
                                        modifier = Modifier
                                            .size(42.dp)
                                            .graphicsLayer {
                                                scaleX = shuffleScaleLand
                                                scaleY = shuffleScaleLand
                                            }
                                            .circularBounceCombinedClickable(
                                                onLongClick = {
                                                    showShuffleSheet = true
                                                },
                                                onClick = {
                                                    onToggleShuffle()
                                                }
                                            )
                                            .testTag("land_player_shuffle")
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = shuffleMode.icon(),
                                                contentDescription = shuffleMode.title,
                                                tint = shuffleTintLand,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            if (shuffleMode != com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_OFF && shuffleMode != com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_ALL) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(3.dp)
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(dynamicPrimaryColor)
                                                )
                                            }
                                        }
                                    }

                                    // Previous Button
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.55f),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.28f)),
                                        shadowElevation = 8.dp,
                                        modifier = Modifier
                                            .size(sideBtnSize)
                                            .circularBounceCombinedClickable(
                                                onLongClick = {
                                                    onSkipToPreviousCategory()
                                                    android.widget.Toast.makeText(context, "Previous Category", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                onClick = {
                                                    onPlayPrevious(false)
                                                }
                                            )
                                            .testTag("full_player_previous")
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.SkipPrevious,
                                                contentDescription = "Previous",
                                                tint = Color.White,
                                                modifier = Modifier.size(sideIconSize)
                                            )
                                        }
                                    }

                                    // Dominant Big Play / Pause Button
                                    FullPlayerDominantPlayButton(
                                        isPlaying = isPlaying,
                                        loadingType = loadingType,
                                        playBtnSize = playBtnSize,
                                        playIconSize = playIconSize,
                                        dynamicPrimaryColor = dynamicPrimaryColor,
                                        onTogglePlayPause = onTogglePlayPause
                                    )

                                    // Next Button
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.55f),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.28f)),
                                        shadowElevation = 8.dp,
                                        modifier = Modifier
                                            .size(sideBtnSize)
                                            .circularBounceCombinedClickable(
                                                onLongClick = {
                                                    onSkipToNextCategory()
                                                    android.widget.Toast.makeText(context, "Next Category", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                onClick = {
                                                    onPlayNext()
                                                }
                                            )
                                            .testTag("full_player_next")
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.SkipNext,
                                                contentDescription = "Next",
                                                tint = Color.White,
                                                modifier = Modifier.size(sideIconSize)
                                            )
                                        }
                                    }

                                    // Repeat
                                    val isRepeatActive = autoplayMode == AutoplayMode.REPEAT_ONE || autoplayMode == AutoplayMode.REPEAT_ALL
                                    val repeatIcon = if (autoplayMode == AutoplayMode.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                                    val repeatScaleLand by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isRepeatActive) 1.12f else 1.0f,
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringMicro,
                                        label = "repeat_scale_land"
                                    )
                                    val repeatColorLand by animateColorAsState(
                                        targetValue = if (isRepeatActive) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "repeat_color_land"
                                    )
                                    val repeatBorderColorLand by animateColorAsState(
                                        targetValue = if (isRepeatActive) dynamicPrimaryColor else Color.White.copy(alpha = 0.2f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "repeat_border_land"
                                    )
                                    val repeatTintLand by animateColorAsState(
                                        targetValue = if (isRepeatActive) dynamicPrimaryColor else Color.White.copy(alpha = 0.85f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "repeat_tint_land"
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = repeatColorLand,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, repeatBorderColorLand),
                                        modifier = Modifier
                                            .size(42.dp)
                                            .graphicsLayer {
                                                scaleX = repeatScaleLand
                                                scaleY = repeatScaleLand
                                            }
                                            .circularBounceClickable {
                                                onToggleRepeat()
                                            }
                                            .testTag("land_player_repeat")
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = repeatIcon,
                                                contentDescription = "Repeat",
                                                tint = repeatTintLand,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Interactive Timeline Waveform
                                WaveformSeekbar(
                                    currentPositionFlow = currentPositionFlow,
                                    durationSeconds = durationSeconds,
                                    onSeek = onSeek,
                                    trackId = track.id,
                                    activeColor = Color.White,
                                    inactiveColor = Color.White.copy(alpha = 0.28f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isShortLandscape) 38.dp else 46.dp)
                                        .padding(horizontal = 6.dp)
                                )
                            }
                        }

                        // Bottom Tools Strip for Landscape
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Surface(
                                color = if (showDspSheet) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (showDspSheet) dynamicPrimaryColor else Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier
                                    .testTag("full_player_dsp_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showDspSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "DSP",
                                        tint = if (showDspSheet) dynamicPrimaryColor else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "DSP",
                                        color = if (showDspSheet) dynamicPrimaryColor else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = if (showVisualizerSheet || isOverlayVisualizerActive) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (showVisualizerSheet || isOverlayVisualizerActive) dynamicPrimaryColor else Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier
                                    .testTag("full_player_visualizer_button")
                                    .circularBounceCombinedClickable(
                                        scaleDown = 0.94f,
                                        onClick = {
                                            showVisualizerSheet = true
                                            isOverlayVisualizerActive = true
                                        },
                                        onLongClick = { isOverlayVisualizerActive = !isOverlayVisualizerActive }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Waves,
                                        contentDescription = "Visualizer",
                                        tint = if (showVisualizerSheet || isOverlayVisualizerActive) dynamicPrimaryColor else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "VISUALIZER",
                                        color = if (showVisualizerSheet || isOverlayVisualizerActive) dynamicPrimaryColor else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = if (showCanvasSheet || isCanvasEnabled) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (showCanvasSheet || isCanvasEnabled) dynamicPrimaryColor else Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier
                                    .testTag("full_player_canvas_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showCanvasSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Canvas",
                                        tint = if (showCanvasSheet || isCanvasEnabled) dynamicPrimaryColor else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "CANVAS",
                                        color = if (showCanvasSheet || isCanvasEnabled) dynamicPrimaryColor else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .testTag("full_player_lyrics_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { onOpenLyrics() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FormatQuote,
                                        contentDescription = "Lyrics",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "LYRICS",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .testTag("full_player_speed_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showSpeedSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Speed",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${speed}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor else Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier
                                    .testTag("full_player_timer_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showSleepTimerSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = "Sleep Timer",
                                        tint = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else "TIMER",
                                        color = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = if (showTrimmerSheet) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .testTag("full_player_cutter_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showTrimmerSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = "Ringtone Cutter",
                                        tint = Color(0xFFFF8800),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "CUTTER",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .testTag("full_player_share_button")
                                    .circularBounceClickable(scaleDown = 0.94f) {
                                        try {
                                            val shareIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Listening to ${track.title} by ${track.artist} on Obsidian!"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                                        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "FullPlayerSheet.FullPlayerSheet - attempt")
            
                                            android.widget.Toast.makeText(context, "Unable to launch share dialog", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "SHARE",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                    }
                } else {
                    // ==========================================
                    // DEDICATED PORTRAIT PLAYER LAYOUT
                    // ==========================================
                    val isCompactHeight = availableHeight < 680.dp
                    val carouselWeight = if (isCompactHeight) 1.35f else 1.60f
                    val titleFontSize = if (isCompactHeight) 18.sp else 20.sp
                    // Visually dominant large playback controls
                    val playBtnSize = if (isCompactHeight) 68.dp else 78.dp
                    val sideBtnSize = if (isCompactHeight) 50.dp else 58.dp
                    val iconSize = if (isCompactHeight) 24.dp else 28.dp
                    val playIconSize = if (isCompactHeight) 32.dp else 38.dp
                    val waveformControlsHeight = if (isCompactHeight) 105.dp else 125.dp

                    ConstraintLayout(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp)
                            .align(Alignment.Center)
                    ) {
                        val (headerRef, carouselRef, trackInfoRef, waveformRef, playControlsRef, audioSpecRef, toolsStripRef) = createRefs()

                        // Header Bar with collapse chevron, perfectly centered branding, and right action buttons
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .constrainAs(headerRef) {
                                    top.linkTo(parent.top)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                                .nonArtContentTransition(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Left action button (Collapse or Queue)
                            Box(
                                modifier = Modifier.align(Alignment.CenterStart)
                            ) {
                                if (isSheet) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.52f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                        modifier = Modifier
                                            .size(38.dp)
                                            .circularBounceClickable { onDismiss() }
                                            .testTag("full_player_dismiss")
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Collapse Player",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.52f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                        modifier = Modifier
                                            .size(38.dp)
                                            .circularBounceClickable { onOpenQueue() }
                                            .testTag("full_player_queue_btn")
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = "Queue",
                                                tint = dynamicPrimaryColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Center Logo Badge: MADE BY ANUJ
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            dynamicPrimaryColor.copy(alpha = 0.85f),
                                            Color.White.copy(alpha = 0.35f),
                                            dynamicPrimaryColor.copy(alpha = 0.85f)
                                        )
                                    )
                                ),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .testTag("full_player_made_by_anuj_logo")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = dynamicPrimaryColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "MADE BY ANUJ",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.2.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Right action buttons (Tag Inspector)
                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.52f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .circularBounceClickable { onOpenTagInspector(track) }
                                        .testTag("full_player_tag_inspector")
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Tag Inspector",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Artwork Carousel with adjacent peeking cards
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .constrainAs(carouselRef) {
                                    top.linkTo(headerRef.bottom)
                                    bottom.linkTo(trackInfoRef.top)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                    height = Dimension.fillToConstraints
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            ArtworkCarousel(
                                currentTrack = track,
                                previousTrack = previousTrack,
                                nextTrack = nextTrack,
                                onPlayNext = onPlayNext,
                                onPlayPrevious = onPlayPrevious,
                                onOpenQueue = onOpenQueue,
                                onOpenTagInspector = { onOpenTagInspector(it) },
                                onToggleFavorite = { onToggleFavorite(it) },
                                isFavorite = track.isFavorite,
                                appColors = appColors,
                                isPlaying = isPlaying,
                                dynamicPrimaryColor = dynamicPrimaryColor,
                                loadingType = loadingType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Track Title, Artist Info & Quick Action Icons
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp)
                                .constrainAs(trackInfoRef) {
                                    bottom.linkTo(waveformRef.top, margin = 2.dp)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                },
                            horizontalAlignment = Alignment.Start
                        ) {
                            AnimatedContent(
                                targetState = track,
                                transitionSpec = {
                                    (fadeIn(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard) togetherWith fadeOut(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick))
                                        .using(SizeTransform(clip = false))
                                },
                                label = "port_track_transition"
                            ) { currentTrack ->
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = currentTrack.title,
                                        color = Color.White,
                                        fontSize = titleFontSize,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .sharedTrackTitle(currentTrack.id)
                                            .basicMarquee()
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${currentTrack.artist}  •  ${currentTrack.album}",
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .sharedTrackArtist(currentTrack.id)
                                            .basicMarquee()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Quick Action Icons row directly beneath Track info
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .nonArtContentTransition(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (showVolumeDialog) dynamicPrimaryColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .circularBounceClickable { showVolumeDialog = true }
                                            .testTag("full_player_volume_button")
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "System Volume Override",
                                                tint = if (showVolumeDialog) dynamicPrimaryColor else Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = if (showDspSheet) dynamicPrimaryColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .circularBounceClickable { showDspSheet = true }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = "DSP & Equalizer",
                                                tint = if (showDspSheet) dynamicPrimaryColor else Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .circularBounceClickable { showSleepTimerSheet = true }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bedtime,
                                                contentDescription = "Sleep Timer",
                                                tint = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor else Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.45f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .circularBounceClickable { onOpenLyrics() }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FormatQuote,
                                                contentDescription = "Lyrics",
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    val shuffleScale by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isShuffle) 1.12f else 1.0f,
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringMicro,
                                        label = "shuffle_scale_port"
                                    )
                                    val shuffleColor by animateColorAsState(
                                        targetValue = if (isShuffle) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "shuffle_color_port"
                                    )
                                    val shuffleBorderColor by animateColorAsState(
                                        targetValue = if (isShuffle) dynamicPrimaryColor else Color.White.copy(alpha = 0.18f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "shuffle_border_port"
                                    )
                                    val shuffleTint by animateColorAsState(
                                        targetValue = if (isShuffle) dynamicPrimaryColor else Color.White.copy(alpha = 0.85f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "shuffle_tint_port"
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = shuffleColor,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, shuffleBorderColor),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .graphicsLayer {
                                                scaleX = shuffleScale
                                                scaleY = shuffleScale
                                            }
                                            .circularBounceCombinedClickable(
                                                onLongClick = {
                                                    showShuffleSheet = true
                                                },
                                                onClick = {
                                                    onToggleShuffle()
                                                }
                                            )
                                            .testTag("full_player_shuffle")
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = shuffleMode.icon(),
                                                contentDescription = shuffleMode.title,
                                                tint = shuffleTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            if (shuffleMode != com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_OFF && shuffleMode != com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_ALL) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(2.dp)
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(dynamicPrimaryColor)
                                                )
                                            }
                                        }
                                    }

                                    val isRepeatActive = autoplayMode == AutoplayMode.REPEAT_ONE || autoplayMode == AutoplayMode.REPEAT_ALL
                                    val repeatIcon = if (autoplayMode == AutoplayMode.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                                    val repeatScale by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isRepeatActive) 1.12f else 1.0f,
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringMicro,
                                        label = "repeat_scale_port"
                                    )
                                    val repeatColor by animateColorAsState(
                                        targetValue = if (isRepeatActive) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "repeat_color_port"
                                    )
                                    val repeatBorderColor by animateColorAsState(
                                        targetValue = if (isRepeatActive) dynamicPrimaryColor else Color.White.copy(alpha = 0.18f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "repeat_border_port"
                                    )
                                    val repeatTint by animateColorAsState(
                                        targetValue = if (isRepeatActive) dynamicPrimaryColor else Color.White.copy(alpha = 0.85f),
                                        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenQuick(),
                                        label = "repeat_tint_port"
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = repeatColor,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, repeatBorderColor),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .graphicsLayer {
                                                scaleX = repeatScale
                                                scaleY = repeatScale
                                            }
                                            .circularBounceCombinedClickable(
                                                onLongClick = {
                                                    android.widget.Toast.makeText(context, "Repeat Mode: ${autoplayMode.title}", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                onClick = {
                                                    onToggleRepeat()
                                                }
                                            )
                                            .testTag("full_player_repeat")
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = repeatIcon,
                                                contentDescription = "Repeat: ${autoplayMode.title}",
                                                tint = repeatTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Interactive Timeline Waveform
                        WaveformSeekbar(
                            currentPositionFlow = currentPositionFlow,
                            durationSeconds = durationSeconds,
                            onSeek = onSeek,
                            trackId = track.id,
                            activeColor = dynamicPrimaryColor,
                            inactiveColor = Color.White.copy(alpha = 0.28f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .constrainAs(waveformRef) {
                                    bottom.linkTo(playControlsRef.top, margin = 4.dp)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                                .nonArtContentTransition()
                        )

                        // 2. Playback Controls Row (Previous, Play/Pause with Neon Ring, Next)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .constrainAs(playControlsRef) {
                                    bottom.linkTo(audioSpecRef.top, margin = 4.dp)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                                .nonArtContentTransition(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.78f),
                                border = androidx.compose.foundation.BorderStroke(1.6.dp, dynamicPrimaryColor.copy(alpha = 0.7f)),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .circularBounceCombinedClickable(
                                        onLongClick = {
                                            onSkipToPreviousCategory()
                                            android.widget.Toast.makeText(context, "Previous Category", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onClick = {
                                            onPlayPrevious(false)
                                        }
                                    )
                                    .testTag("full_player_previous")
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Box(contentAlignment = Alignment.Center) {
                                // Outer Neon Cyan Glow Ring for Timeline Play/Pause
                                Box(
                                    modifier = Modifier
                                        .size(playBtnSize + 14.dp)
                                        .border(
                                            width = 1.8.dp,
                                            color = dynamicPrimaryColor.copy(alpha = 0.85f),
                                            shape = CircleShape
                                        )
                                )

                                FullPlayerDominantPlayButton(
                                    isPlaying = isPlaying,
                                    loadingType = loadingType,
                                    playBtnSize = playBtnSize,
                                    playIconSize = playIconSize,
                                    dynamicPrimaryColor = dynamicPrimaryColor,
                                    onTogglePlayPause = onTogglePlayPause
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.78f),
                                border = androidx.compose.foundation.BorderStroke(1.6.dp, dynamicPrimaryColor.copy(alpha = 0.7f)),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .circularBounceCombinedClickable(
                                        onLongClick = {
                                            onSkipToNextCategory()
                                            android.widget.Toast.makeText(context, "Next Category", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onClick = {
                                            onPlayNext()
                                        }
                                    )
                                    .testTag("full_player_next")
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        tint = Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Centered Monospace Audio Spec Tag (Capsule matching the screenshot)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 4.dp)
                                .constrainAs(audioSpecRef) {
                                    bottom.linkTo(toolsStripRef.top, margin = 2.dp)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                                .nonArtContentTransition(),
                            contentAlignment = Alignment.Center
                        ) {
                            val formatDisplay = when {
                                track.format.equals("ALAC", true) || track.format.contains("APPLE", true) -> "ALAC"
                                track.format.isNotBlank() -> track.format.uppercase()
                                else -> "M4A"
                            }
                            val sampleRateDisplay = if (track.sampleRate.isNotBlank()) track.sampleRate.uppercase() else "44.1 KHZ"
                            val exactKbps = if (track.bitrateKbps > 0) {
                                track.bitrateKbps
                            } else {
                                val localFile = if (track.localFilePath.isNotBlank() && !track.localFilePath.startsWith("http")) java.io.File(track.localFilePath) else null
                                val bytes = if (localFile != null && localFile.exists()) localFile.length() else (track.fileSizeMb * 1024f * 1024f).toLong()
                                if (bytes > 0 && track.durationSeconds > 0) {
                                    ((bytes * 8L) / track.durationSeconds / 1000L).toInt()
                                } else 320
                            }
                            val bitrateDisplay = "$exactKbps KBPS"
                            val btCodec = btAudioInfo.displayCodec
                            val outputState = remember(track.sampleRate, track.bitrateKbps, track.format) {
                                com.anuj.obsidian.util.AudioOutputQualityEvaluator.evaluate(context, track.sampleRate)
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (showMoreTools || btCodec != null) dynamicPrimaryColor.copy(alpha = 0.5f)
                                    else if (outputState.isBitPerfect) Color(0xFF10B981).copy(alpha = 0.5f)
                                    else Color.White.copy(alpha = 0.20f)
                                ),
                                modifier = Modifier
                                    .circularBounceClickable { showMoreTools = !showMoreTools }
                                    .testTag("full_player_audio_spec_toggle")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    if (btCodec != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BluetoothAudio,
                                                contentDescription = "Bluetooth Codec $btCodec",
                                                tint = dynamicPrimaryColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = btCodec,
                                                color = dynamicPrimaryColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 0.6.sp
                                            )
                                        }
                                        Text(
                                            text = "•",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 10.sp
                                        )
                                    } else {
                                        Surface(
                                            color = if (outputState.isBitPerfect) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (outputState.isBitPerfect) "BIT-PERFECT" else "SRC",
                                                color = if (outputState.isBitPerfect) Color(0xFF10B981) else Color(0xFF38BDF8),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$sampleRateDisplay   $bitrateDisplay   $formatDisplay",
                                        color = Color.White.copy(alpha = 0.92f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.6.sp
                                    )
                                    Text(
                                        text = "•",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 10.sp
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (showMoreTools) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Toggle Tools",
                                            tint = dynamicPrimaryColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (showMoreTools) "TOOLS" else "TOOLS",
                                            color = dynamicPrimaryColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Tools Strip at bottom (Progressive Disclosure)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .constrainAs(toolsStripRef) {
                                    bottom.linkTo(parent.bottom)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                                .nonArtContentTransition()
                        ) {
                            AnimatedVisibility(
                                visible = showMoreTools,
                                enter = expandVertically(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringHeroIntSize) + fadeIn(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard),
                                exit = shrinkVertically(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringHeroIntSize) + fadeOut(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 10.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Surface(
                                color = if (showDspSheet) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (showDspSheet) dynamicPrimaryColor else Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier
                                    .testTag("full_player_dsp_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showDspSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "DSP",
                                        tint = if (showDspSheet) dynamicPrimaryColor else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "DSP",
                                        color = if (showDspSheet) dynamicPrimaryColor else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = if (showVisualizerSheet || isOverlayVisualizerActive) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (showVisualizerSheet || isOverlayVisualizerActive) dynamicPrimaryColor else Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier
                                    .testTag("full_player_visualizer_button")
                                    .circularBounceCombinedClickable(
                                        scaleDown = 0.94f,
                                        onClick = {
                                            showVisualizerSheet = true
                                            isOverlayVisualizerActive = true
                                        },
                                        onLongClick = { isOverlayVisualizerActive = !isOverlayVisualizerActive }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Waves,
                                        contentDescription = "Visualizer",
                                        tint = if (showVisualizerSheet || isOverlayVisualizerActive) dynamicPrimaryColor else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "VISUALIZER",
                                        color = if (showVisualizerSheet || isOverlayVisualizerActive) dynamicPrimaryColor else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = if (showCanvasSheet || isCanvasEnabled) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (showCanvasSheet || isCanvasEnabled) dynamicPrimaryColor else Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier
                                    .testTag("full_player_canvas_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showCanvasSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Canvas",
                                        tint = if (showCanvasSheet || isCanvasEnabled) dynamicPrimaryColor else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "CANVAS",
                                        color = if (showCanvasSheet || isCanvasEnabled) dynamicPrimaryColor else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .testTag("full_player_lyrics_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { onOpenLyrics() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FormatQuote,
                                        contentDescription = "Lyrics",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "LYRICS",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .testTag("full_player_speed_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showSpeedSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Speed",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${speed}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor else Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier
                                    .testTag("full_player_timer_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showSleepTimerSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = "Sleep Timer",
                                        tint = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else "TIMER",
                                        color = if (sleepTimerMinutes != null && sleepTimerMinutes > 0) dynamicPrimaryColor else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = if (showTrimmerSheet) dynamicPrimaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .testTag("full_player_cutter_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { showTrimmerSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = "Ringtone Cutter",
                                        tint = Color(0xFFFF8800),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "CUTTER",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .testTag("full_player_drive_mode_button")
                                    .circularBounceClickable(scaleDown = 0.94f) { onOpenDriveMode() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = "Drive Mode",
                                        tint = dynamicPrimaryColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "DRIVE",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .testTag("full_player_share_button")
                                    .circularBounceClickable(scaleDown = 0.94f) {
                                        try {
                                            val shareIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Listening to ${track.title} by ${track.artist} on Obsidian!"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                                        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "FullPlayerSheet.FullPlayerSheet - attempt")
            
                                            android.widget.Toast.makeText(context, "Unable to launch share dialog", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "SHARE",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (settings.visualizerAboveChrome && shouldRenderVisualizer) {
                BackgroundVisualizerLayer(
                    presetId = visualizerPresetId,
                    isPlaying = isPlaying,
                    progressProvider = { if (durationSeconds > 0) currentPositionFlow.value / durationSeconds.toFloat() else 0f },
                    alphaProvider = { animatedVisAlphaState.value * 0.35f },
                    colorMode = visualizerColorMode,
                    performanceMode = settings.visualizerPerformanceMode,
                    fpsLimit = settings.visualizerFpsLimit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun FullPlayerDominantPlayButton(
    isPlaying: Boolean,
    loadingType: PlaybackLoadingType,
    playBtnSize: androidx.compose.ui.unit.Dp,
    playIconSize: androidx.compose.ui.unit.Dp,
    dynamicPrimaryColor: Color,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        if (isPlaying && !loadingType.isLocalStorage && !loadingType.isNetwork) {
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse_trans_btn")
            val pScale = infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.25f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = com.anuj.obsidian.ui.animation.MotionSpec.EqualizerLoopSpec.animation,
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )
            val pAlpha = infiniteTransition.animateFloat(
                initialValue = 0.45f,
                targetValue = 0.05f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = com.anuj.obsidian.ui.animation.MotionSpec.EqualizerLoopSpec.animation,
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )
            Box(
                modifier = Modifier
                    .size(playBtnSize)
                    .graphicsLayer {
                        scaleX = pScale.value
                        scaleY = pScale.value
                        alpha = pAlpha.value
                    }
                    .background(dynamicPrimaryColor, CircleShape)
            )
        }

        Surface(
            shape = CircleShape,
            color = if (loadingType.isLocalStorage) Color(0xFFFFB300) else if (loadingType.isNetwork) Color(0xFF29B6F6) else Color.Black.copy(alpha = 0.88f),
            contentColor = Color.White,
            border = androidx.compose.foundation.BorderStroke(2.4.dp, Color.White.copy(alpha = 0.65f)),
            shadowElevation = 14.dp,
            modifier = Modifier
                .size(playBtnSize)
                .circularBounceClickable(scaleDown = 0.90f) {
                    onTogglePlayPause()
                }
                .testTag("full_player_play_pause")
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loadingType.isLocalStorage) {
                    Box(
                        modifier = Modifier.size(playBtnSize),
                        contentAlignment = Alignment.Center
                    ) {
                        ObsidianLoadingSpinner(
                            size = playBtnSize * 0.65f,
                            strokeWidth = 3.2.dp,
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Loading local file",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else if (loadingType.isNetwork) {
                    Box(
                        modifier = Modifier.size(playBtnSize),
                        contentAlignment = Alignment.Center
                    ) {
                        ObsidianLoadingSpinner(
                            size = playBtnSize * 0.65f,
                            strokeWidth = 3.2.dp,
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Network buffering",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    AnimatedPlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = Color.White,
                        size = playIconSize
                    )
                }
            }
        }
    }
}
