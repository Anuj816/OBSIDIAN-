package com.anuj.obsidian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
import com.anuj.obsidian.ui.animation.LocalNavAnimatedVisibilityScope
import com.anuj.obsidian.ui.animation.LocalSharedTransitionScope
import com.anuj.obsidian.ui.animation.drawLayoutBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anuj.obsidian.data.local.AppDatabase
import com.anuj.obsidian.data.repository.ObsidianRepository
import com.anuj.obsidian.ui.AppTab
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.animation.safePerform
import com.anuj.obsidian.ui.components.FormatChip
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.ui.components.FullPlayerSheet
import com.anuj.obsidian.ui.components.LyricsSheet
import com.anuj.obsidian.ui.components.player.BackgroundVisualizerLayer
import com.anuj.obsidian.ui.components.MiniPlayerBar
import com.anuj.obsidian.ui.components.TagInspectorDialog
import com.anuj.obsidian.ui.player.ObsidianPlaybackService
import com.anuj.obsidian.ui.screens.HomeScreen
import com.anuj.obsidian.ui.screens.PlayerScreen
import com.anuj.obsidian.ui.screens.LibraryScreen
import com.anuj.obsidian.ui.screens.QueueScreen
import com.anuj.obsidian.ui.screens.SettingsScreen
import com.anuj.obsidian.ui.theme.DarkSurface
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.MyApplicationTheme
import com.anuj.obsidian.ui.theme.ObsidianTheme

class MainActivity : ComponentActivity() {
    private val incomingIntentFlow = kotlinx.coroutines.flow.MutableStateFlow<android.content.Intent?>(null)
    private var isCloseAppReceiverRegistered = false

    private val closeAppReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == ObsidianPlaybackService.ACTION_CLOSE_APP) {
                finishAffinity()
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this

        try {
            val filter = android.content.IntentFilter(ObsidianPlaybackService.ACTION_CLOSE_APP)
            androidx.core.content.ContextCompat.registerReceiver(this, closeAppReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            isCloseAppReceiverRegistered = true
        } catch (e: Exception) {
            isCloseAppReceiverRegistered = false
            if (e is kotlinx.coroutines.CancellationException) throw e
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "MainActivity.registerCloseAppReceiver - attempt")
        }
        // Safe guard enableEdgeToEdge for custom OEM ROMs (MIUI, EMUI, ColorOS)
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "MainActivity.onCreate - attempt")
            
            android.util.Log.w("MainActivity", "Failed to configure edge-to-edge: ${e.message}")
        }

        val requiredPermissions = mutableListOf<String>()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            requiredPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requiredPermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                requiredPermissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        try {
            val missingPermissions = requiredPermissions.filter { perm ->
                androidx.core.content.ContextCompat.checkSelfPermission(this, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (missingPermissions.isNotEmpty()) {
                requestPermissions(missingPermissions, 100)
            }
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "MainActivity.onCreate - attempt")
            
            android.util.Log.w("MainActivity", "Permission verification notice: ${e.message}")
        }

        incomingIntentFlow.value = intent

        val isCrashLoop = intent.getBooleanExtra("crash_loop_detected", false)
        val crashMessage = intent.getStringExtra("crash_message") ?: "An unexpected persistent error occurred"

        setContent {
            if (isCrashLoop) {
                CrashLoopScreen(errorText = crashMessage, onReset = {
                    com.anuj.obsidian.ObsidianCrashLogger.clearLogs()
                    try {
                        val sharedPrefs = getSharedPreferences("obsidian_audio_prefs", android.content.Context.MODE_PRIVATE)
                        sharedPrefs.edit().clear().apply()
                    } catch (e: Throwable) {
                        com.anuj.obsidian.ObsidianCrashLogger.logException(e, "MainActivity.CrashLoopReset - clear prefs")
                    }
                    val startIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (startIntent != null) {
                        startActivity(startIntent)
                    }
                    finish()
                })
            } else {
                ObsidianObsidianApp(incomingIntentFlow = incomingIntentFlow)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntentFlow.value = intent
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                android.util.Log.d("MainActivity", "Permissions granted")
            } else {
                android.widget.Toast.makeText(this, "Permission denied - cannot access music library", android.widget.Toast.LENGTH_SHORT).show()
                android.util.Log.w("MainActivity", "Permissions denied: ${permissions.toList()}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            com.anuj.obsidian.util.BluetoothAudioCodecManager.cleanup()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Bluetooth cleanup failed: ${e.message}")
        }
        if (isCloseAppReceiverRegistered) {
            try {
                unregisterReceiver(closeAppReceiver)
                isCloseAppReceiverRegistered = false
            } catch (e: IllegalArgumentException) {
                ObsidianCrashLogger.logWarning("MainActivity", "closeAppReceiver was not registered: ${e.message}")
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "MainActivity.onDestroy - unregisterReceiver failed")
            }
        }
        if (isFinishing) {
            ObsidianCrashLogger.logMessage("MainActivity is finishing - releasing idle player state")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            val player = ObsidianApplication.playerManager
            val currentTrack = player.getCurrentTrack()
            if (currentTrack != null) {
                val currentPosSec = player.getCurrentPositionMs() / 1000f
                ObsidianApplication.repository.saveLastPlaybackState(currentTrack, currentPosSec)
            }
        } catch (e: Throwable) {
            // Ignore background save errors
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            val player = ObsidianApplication.playerManager
            val currentTrack = player.getCurrentTrack()
            if (currentTrack != null) {
                val currentPosSec = player.getCurrentPositionMs() / 1000f
                ObsidianApplication.repository.saveLastPlaybackState(currentTrack, currentPosSec)
            }
        } catch (e: Throwable) {
            // Ignore background save errors
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ObsidianObsidianApp(
    incomingIntentFlow: kotlinx.coroutines.flow.StateFlow<android.content.Intent?> = kotlinx.coroutines.flow.MutableStateFlow(null)
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val viewModel: ObsidianViewModel = viewModel()

    LaunchedEffect(Unit) {
        val activity = context as? androidx.activity.ComponentActivity
        if (activity?.intent?.getBooleanExtra("recovered_from_crash", false) == true) {
            Toast.makeText(context, "Recovered from an unexpected error", Toast.LENGTH_LONG).show()
            activity.intent.removeExtra("recovered_from_crash")
        }
    }



    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val previousTrack by viewModel.previousTrack.collectAsStateWithLifecycle()
    val nextTrack by viewModel.nextTrack.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isFetchingLyrics by viewModel.isFetchingLyrics.collectAsStateWithLifecycle()

    val primaryTabs = remember { listOf(AppTab.HOME, AppTab.LIBRARY, AppTab.PLAYER, AppTab.QUEUE) }
    val mainPagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = primaryTabs.indexOf(currentTab).coerceAtLeast(0),
        pageCount = { primaryTabs.size }
    )

    // Sync when pager settles on a new page (from user swipe gesture)
    LaunchedEffect(mainPagerState.settledPage) {
        val targetTab = primaryTabs.getOrNull(mainPagerState.settledPage)
        if (targetTab != null && targetTab != currentTab && primaryTabs.contains(currentTab)) {
            viewModel.setTab(targetTab)
        }
    }

    // Sync when currentTab is changed programmatically
    LaunchedEffect(currentTab) {
        val targetIndex = primaryTabs.indexOf(currentTab)
        if (targetIndex >= 0 && targetIndex != mainPagerState.currentPage) {
            mainPagerState.animateScrollToPage(
                page = targetIndex,
                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth
            )
        }
    }

    val backPressedOnce = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val activity = context as? androidx.activity.ComponentActivity
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkAndAutoLinkDefaultFolder(context)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.persistPlaybackContext()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    BackHandler(enabled = true) {
        if (playerState.isDriveModeVisible) {
            viewModel.showDriveMode(false)
        } else if (playerState.isLyricsVisible) {
            viewModel.showLyrics(false)
        } else if (playerState.isFullPlayerVisible) {
            viewModel.showFullPlayer(false)
        } else if (currentTab == com.anuj.obsidian.ui.AppTab.FOLDER_SELECTION || currentTab == com.anuj.obsidian.ui.AppTab.FOLDER_EXPLORER) {
            viewModel.setTab(com.anuj.obsidian.ui.AppTab.SETTINGS)
        } else if (currentTab == com.anuj.obsidian.ui.AppTab.SETTINGS) {
            val fallbackTab = primaryTabs.getOrElse(mainPagerState.currentPage) { AppTab.HOME }
            viewModel.setTab(fallbackTab)
        } else if (mainPagerState.currentPage != 0) {
            coroutineScope.launch {
                mainPagerState.animateScrollToPage(page = 0, animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth)
            }
            viewModel.setTab(AppTab.HOME)
        } else {
            if (backPressedOnce.value) {
                activity?.finish()
            } else {
                backPressedOnce.value = true
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    delay(2000)
                    backPressedOnce.value = false
                }
            }
        }
    }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Handle Custom Songs opened using Obsidian from external file managers or share intents
    LaunchedEffect(incomingIntentFlow) {
        incomingIntentFlow.collect { incoming ->
            val dataUri = incoming?.data ?: if (incoming?.action == android.content.Intent.ACTION_SEND) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    incoming.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    incoming.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
                }
            } else null

            if (dataUri != null) {
                (incomingIntentFlow as? kotlinx.coroutines.flow.MutableStateFlow)?.value = null
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val uriStr = dataUri.toString()
                        val details = com.anuj.obsidian.data.local.JaudiotaggerHelper.readDetailedMetadata(context, uriStr)
                        val fileName = dataUri.lastPathSegment?.substringAfterLast('/') ?: "Custom Song"
                        val customTrack = com.anuj.obsidian.data.model.DownloadedTrack(
                            id = System.currentTimeMillis(),
                            title = details.title.ifBlank { fileName.substringBeforeLast('.') },
                            artist = details.artist.ifBlank { "Custom Local Audio" },
                            album = details.album.ifBlank { "Direct Playback" },
                            genre = details.genre.ifBlank { "External Audio" },
                            durationSeconds = details.durationSeconds.toInt().coerceAtLeast(1),
                            format = details.formatName.ifBlank { "AUDIO" },
                            bitrateKbps = details.bitrateKbps.coerceAtLeast(320),
                            sampleRate = details.sampleRateFormatted.ifBlank { "44.1 kHz" },
                            bitDepth = details.bitDepthFormatted.ifBlank { "16-bit" },
                            localFilePath = uriStr,
                            providerSource = "External Player Intent"
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            viewModel.playTrack(customTrack)
                            viewModel.showFullPlayer(true)
                        }
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        com.anuj.obsidian.ObsidianCrashLogger.logException(e, "MainActivity.ObsidianObsidianApp - attempt")
                        android.util.Log.e("MainActivity", "Failed to play external song: ${e.message}")
                    }
                }
            }
        }
    }

    // Auto-dismiss soft keyboard whenever any track is opened/played or full player is shown
    LaunchedEffect(playerState.currentTrack?.id, playerState.isFullPlayerVisible) {
        if (playerState.currentTrack != null || playerState.isFullPlayerVisible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    androidx.compose.runtime.DisposableEffect(viewModel, context) {
        val act = context as? android.app.Activity
        val pendingAction = act?.intent?.getStringExtra("startup_action")
        if (!pendingAction.isNullOrBlank()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ObsidianPlaybackService.actionCallback?.invoke(pendingAction)
            }, 300)
            act.intent?.removeExtra("startup_action")
        }
        
        ObsidianPlaybackService.actionCallback = { action ->
            when {
                action == ObsidianPlaybackService.ACTION_SHUFFLE -> viewModel.toggleShuffle()
                action == ObsidianPlaybackService.ACTION_REPEAT -> viewModel.cycleAutoplayMode()
                action == ObsidianPlaybackService.ACTION_STOP -> {
                    viewModel.stopPlayback()
                    (context as? android.app.Activity)?.finishAndRemoveTask()
                }
                action == ObsidianPlaybackService.ACTION_FAST_FORWARD -> {
                    val currentPos = viewModel.currentPositionSeconds.value
                    viewModel.seekTo(currentPos + 10f)
                }
                action == ObsidianPlaybackService.ACTION_REWIND -> {
                    val currentPos = viewModel.currentPositionSeconds.value
                    viewModel.seekTo((currentPos - 10f).coerceAtLeast(0f))
                }
                action.startsWith(ObsidianPlaybackService.ACTION_SEEK_TO) -> {
                    val posMs = action.substringAfter(":").toLongOrNull() ?: 0L
                    viewModel.seekTo(posMs / 1000f)
                }
            }
        }

        onDispose {
            ObsidianPlaybackService.actionCallback = null
        }
    }


    MyApplicationTheme(settings = settings) {
        val appColors = ObsidianTheme.colors

            val playerDragState = androidx.compose.runtime.remember { androidx.compose.animation.core.SeekableTransitionState(false) }
            val playerTransition = androidx.compose.animation.core.rememberTransition(playerDragState, label = "PlayerTransition")
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            var dragOverscrollY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

            androidx.compose.runtime.LaunchedEffect(playerState.isFullPlayerVisible) {
                if (playerState.isFullPlayerVisible != playerDragState.currentState) {
                    playerDragState.animateTo(
                        playerState.isFullPlayerVisible,
                        com.anuj.obsidian.ui.animation.ObsidianMotion.PlayerFractionSpring
                    )
                }
            }

        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

        androidx.compose.animation.SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this
            ) {
                val lastActivityEpoch = remember { java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis()) }
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawLayoutBounds(settings.showLayoutBounds)
                ) {
                val maxPx = constraints.maxHeight.toFloat()
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    val now = System.currentTimeMillis()
                                    if (event.changes.any { it.pressed } && (now - lastActivityEpoch.get() > 2000L)) {
                                        lastActivityEpoch.set(now)
                                        viewModel.recordUserActivity()
                                    }
                                }
                            }
                        },
                    containerColor = appColors.background,
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    topBar = {
                        if (currentTab != AppTab.PLAYER) {
                            ObsidianObsidianHeader(
                                currentTab = currentTab,
                                onOpenSettings = { 
                                    haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.setTab(AppTab.SETTINGS) 
                                },
                                onOpenSearch = {
                                    viewModel.showGlobalSearch(true)
                                }
                            )
                        }
                    },
                    bottomBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(appColors.surface)
                                .navigationBarsPadding()
                        ) {
                            // Persistent Mini Player above Navigation Bar
                            playerTransition.AnimatedVisibility(
                                visible = { isFull -> !isFull && playerState.currentTrack != null && currentTab != AppTab.PLAYER },
                                enter = fadeIn(animationSpec = com.anuj.obsidian.ui.animation.ObsidianMotion.PlayerFadeEnterSpring) +
                                        slideInVertically(
                                            animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringHeroIntOffset,
                                            initialOffsetY = { fullHeight -> fullHeight }
                                        ),
                                exit = fadeOut(animationSpec = com.anuj.obsidian.ui.animation.ObsidianMotion.PlayerFadeExitSpring) +
                                        slideOutVertically(
                                            animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringHeroIntOffset,
                                            targetOffsetY = { fullHeight -> fullHeight }
                                        )
                            ) {
                                CompositionLocalProvider(
                                    LocalNavAnimatedVisibilityScope provides this
                                ) {
                                    MiniPlayerBar(
                                        modifier = Modifier.graphicsLayer { translationY = dragOverscrollY },
                                        track = playerState.currentTrack,
                                        isPlaying = playerState.isPlaying,
                                        currentPositionFlow = viewModel.currentPositionSeconds,
                                        durationSeconds = playerState.durationSeconds,
                                        previousTrack = previousTrack,
                                        nextTrack = nextTrack,
                                        loadingType = playerState.loadingType,
                                        onVerticalDrag = { deltaY -> 
                                             coroutineScope.launch {
                                                 if (dragOverscrollY > 0f) {
                                                     val updated = dragOverscrollY + deltaY * 0.35f
                                                     dragOverscrollY = if (updated < 0f) 0f else updated
                                                 } else if (dragOverscrollY < 0f) {
                                                     val updated = dragOverscrollY + deltaY * 0.35f
                                                     dragOverscrollY = if (updated > 0f) 0f else updated
                                                 } else {
                                                     val deltaFrac = -deltaY / maxPx
                                                     val proposed = playerDragState.fraction + deltaFrac
                                                     if (proposed > 1f) {
                                                         playerDragState.seekTo(1f, true)
                                                         val excessPx = (proposed - 1f) * maxPx
                                                         dragOverscrollY -= excessPx * 0.35f
                                                     } else if (proposed < 0f) {
                                                         playerDragState.seekTo(0f, true)
                                                         val excessPx = proposed * maxPx
                                                         dragOverscrollY -= excessPx * 0.35f
                                                     } else {
                                                         playerDragState.seekTo(proposed, true)
                                                     }
                                                 }
                                             }
                                        },
                                        onVerticalDragEnd = { velocityY ->
                                             coroutineScope.launch {
                                                 if (dragOverscrollY != 0f) {
                                                     coroutineScope.launch {
                                                         androidx.compose.animation.core.Animatable(dragOverscrollY).animateTo(
                                                             targetValue = 0f,
                                                             animationSpec = com.anuj.obsidian.ui.animation.ObsidianMotion.GestureSettlingSpring
                                                         ) {
                                                             dragOverscrollY = value
                                                         }
                                                     }
                                                 }
                                                 val target = playerDragState.fraction > 0.5f || velocityY < -500f
                                                 playerDragState.animateTo(target, com.anuj.obsidian.ui.animation.ObsidianMotion.GestureSettlingSpring)
                                                 viewModel.showFullPlayer(target)
                                             }
                                        },
                                        onTogglePlayPause = { viewModel.togglePlayPause() },
                                        onPlayNext = { viewModel.playNext() },
                                        onPlayPrevious = { forceSkip -> viewModel.playPrevious(forceSkip) },
                                        onClosePlayer = { 
                                            viewModel.stopPlayback()
                                            (context as? android.app.Activity)?.finishAndRemoveTask()
                                        },
                                        onClickBar = { viewModel.showFullPlayer(true) }
                                    )
                                }
                            }

                            // Material 3 Persistent Translucent Bottom Navigation Bar with clean icons
                            val activePrimaryIndex = primaryTabs.indexOf(currentTab)
                            val homeIconScale = androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (activePrimaryIndex == 0) 1.12f else 1.0f,
                                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
                                label = "nav_icon_scale_home"
                            )
                            val libIconScale = androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (activePrimaryIndex == 1) 1.12f else 1.0f,
                                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
                                label = "nav_icon_scale_lib"
                            )
                            val playerIconScale = androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (activePrimaryIndex == 2) 1.12f else 1.0f,
                                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
                                label = "nav_icon_scale_player"
                            )
                            val queueIconScale = androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (activePrimaryIndex == 3) 1.12f else 1.0f,
                                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
                                label = "nav_icon_scale_queue"
                            )
                            
                            Surface(
                                color = Color.Black.copy(alpha = 0.88f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                NavigationBar(
                                    containerColor = Color.Transparent,
                                    contentColor = appColors.textPrimary,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    NavigationBarItem(
                                        selected = activePrimaryIndex == 0,
                                        onClick = {
                                            haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            if (currentTab != AppTab.HOME) {
                                                viewModel.setTab(AppTab.HOME)
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (activePrimaryIndex == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                                contentDescription = "Home",
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = homeIconScale.value
                                                    scaleY = homeIconScale.value
                                                }
                                            )
                                        },
                                        label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.Black,
                                            selectedTextColor = appColors.primary,
                                            indicatorColor = appColors.primary,
                                            unselectedIconColor = appColors.textSecondary,
                                            unselectedTextColor = appColors.textMuted
                                        ),
                                        modifier = Modifier.testTag("nav_tab_home")
                                    )

                                    NavigationBarItem(
                                        selected = activePrimaryIndex == 1,
                                        onClick = {
                                            haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            if (currentTab != AppTab.LIBRARY) {
                                                viewModel.setTab(AppTab.LIBRARY)
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (activePrimaryIndex == 1) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic,
                                                contentDescription = "Library",
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = libIconScale.value
                                                    scaleY = libIconScale.value
                                                }
                                            )
                                        },
                                        label = { Text("Library", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.Black,
                                            selectedTextColor = appColors.primary,
                                            indicatorColor = appColors.primary,
                                            unselectedIconColor = appColors.textSecondary,
                                            unselectedTextColor = appColors.textMuted
                                        ),
                                        modifier = Modifier.testTag("nav_tab_library")
                                    )

                                    NavigationBarItem(
                                        selected = activePrimaryIndex == 2,
                                        onClick = {
                                            haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            if (currentTab != AppTab.PLAYER) {
                                                viewModel.setTab(AppTab.PLAYER)
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (activePrimaryIndex == 2) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow,
                                                contentDescription = "Player",
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = playerIconScale.value
                                                    scaleY = playerIconScale.value
                                                }
                                            )
                                        },
                                        label = { Text("Player", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.Black,
                                            selectedTextColor = appColors.primary,
                                            indicatorColor = appColors.primary,
                                            unselectedIconColor = appColors.textSecondary,
                                            unselectedTextColor = appColors.textMuted
                                        ),
                                        modifier = Modifier.testTag("nav_tab_player")
                                    )

                                    NavigationBarItem(
                                        selected = activePrimaryIndex == 3,
                                        onClick = {
                                            haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            if (currentTab != AppTab.QUEUE) {
                                                viewModel.setTab(AppTab.QUEUE)
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (activePrimaryIndex == 3) Icons.AutoMirrored.Filled.QueueMusic else Icons.AutoMirrored.Outlined.QueueMusic,
                                                contentDescription = "Queue",
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = queueIconScale.value
                                                    scaleY = queueIconScale.value
                                                }
                                            )
                                        },
                                        label = { Text("Queue", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.Black,
                                            selectedTextColor = appColors.primary,
                                            indicatorColor = appColors.primary,
                                            unselectedIconColor = appColors.textSecondary,
                                            unselectedTextColor = appColors.textMuted
                                        ),
                                        modifier = Modifier.testTag("nav_tab_queue")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        val isSecondaryTab = currentTab == AppTab.SETTINGS ||
                                currentTab == AppTab.FOLDER_SELECTION ||
                                currentTab == AppTab.FOLDER_EXPLORER

                        val shouldShowBgVisualizer = settings.visualizerMasterEnabled &&
                                settings.backgroundVisualizerEnabled &&
                                playerState.isPlaying &&
                                !isSecondaryTab &&
                                (settings.visualizerShowEverywhere || mainPagerState.currentPage == 2)

                        if (shouldShowBgVisualizer) {
                            ErrorBoundary(screenName = "BackgroundVisualizer") {
                                BackgroundVisualizerLayer(
                                    presetId = settings.lastVisualizerPresetId,
                                    isPlaying = playerState.isPlaying,
                                    progressProvider = { 0f },
                                    alphaProvider = { settings.visualizerAlpha },
                                    colorMode = settings.visualizerColorMode,
                                    performanceMode = settings.visualizerPerformanceMode,
                                    fpsLimit = settings.visualizerFpsLimit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        val pagerFlingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                            state = mainPagerState,
                            snapAnimationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth
                        )

                        // Smooth One-Finger Gesture Scrollable Pager across Primary Tabs with Apple Music Fluidity
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = mainPagerState,
                            beyondViewportPageCount = 1,
                            flingBehavior = pagerFlingBehavior,
                            userScrollEnabled = !isSecondaryTab,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val pageOffset = ((mainPagerState.currentPage - pageIndex) + mainPagerState.currentPageOffsetFraction)
                                        val absOffset = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                                        val pageScale = androidx.compose.ui.util.lerp(
                                            start = 0.94f,
                                            stop = 1.0f,
                                            fraction = 1f - absOffset
                                        )
                                        val pageAlpha = androidx.compose.ui.util.lerp(
                                            start = 0.6f,
                                            stop = 1.0f,
                                            fraction = 1f - absOffset
                                        )
                                        scaleX = pageScale
                                        scaleY = pageScale
                                        alpha = pageAlpha
                                    }
                            ) {
                                when (pageIndex) {
                                    0 -> ErrorBoundary(screenName = "Home") {
                                        HomeScreen(viewModel = viewModel)
                                    }
                                    1 -> ErrorBoundary(screenName = "Library") {
                                        LibraryScreen(
                                            viewModel = viewModel,
                                            onManageFolders = { viewModel.setTab(com.anuj.obsidian.ui.AppTab.FOLDER_EXPLORER) }
                                        )
                                    }
                                    2 -> ErrorBoundary(screenName = "Player") {
                                        PlayerScreen(viewModel = viewModel)
                                    }
                                    3 -> ErrorBoundary(screenName = "Queue") {
                                        QueueScreen(viewModel = viewModel)
                                    }
                                }
                            }
                        }

                        // Seamless Secondary Screen Overlay (Settings & Folder Management)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isSecondaryTab,
                            enter = com.anuj.obsidian.ui.animation.MotionSpec.NavEnterSlide,
                            exit = com.anuj.obsidian.ui.animation.MotionSpec.NavExitSlide
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = DarkSurface
                            ) {
                                when (currentTab) {
                                    AppTab.SETTINGS -> ErrorBoundary(screenName = "Settings") {
                                        SettingsScreen(
                                            viewModel = viewModel,
                                            onManageFolders = { viewModel.setTab(com.anuj.obsidian.ui.AppTab.FOLDER_EXPLORER) }
                                        )
                                    }
                                    AppTab.FOLDER_SELECTION -> com.anuj.obsidian.ui.screens.FolderSelectionScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.setTab(com.anuj.obsidian.ui.AppTab.SETTINGS) }
                                    )
                                    AppTab.FOLDER_EXPLORER -> com.anuj.obsidian.ui.screens.FolderHierarchyScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.setTab(com.anuj.obsidian.ui.AppTab.SETTINGS) }
                                    )
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                // Full Screen Player Sheet with Butter Smooth Expansion Animation
                playerTransition.AnimatedVisibility(
                    visible = { isFull -> isFull && playerState.currentTrack != null },
                    enter = fadeIn(animationSpec = com.anuj.obsidian.ui.animation.ObsidianMotion.PlayerFadeEnterSpring) +
                            slideInVertically(
                                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringHeroIntOffset,
                                initialOffsetY = { fullHeight -> fullHeight }
                            ),
                    exit = fadeOut(animationSpec = com.anuj.obsidian.ui.animation.ObsidianMotion.PlayerFadeExitSpring) +
                            slideOutVertically(
                                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringHeroIntOffset,
                                targetOffsetY = { fullHeight -> fullHeight }
                            )
                ) {
                    CompositionLocalProvider(
                        LocalNavAnimatedVisibilityScope provides this
                    ) {
                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                        val playbackSeq by viewModel.playbackSequence.collectAsStateWithLifecycle()
                        val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
                        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
                        ErrorBoundary(screenName = "Full Player") {
                            FullPlayerSheet(
                                modifier = Modifier.graphicsLayer { translationY = dragOverscrollY },
                                track = playerState.currentTrack,
                                previousTrack = playerState.previousTrack,
                                nextTrack = playerState.nextTrack,
                                isPlaying = playerState.isPlaying,
                                currentPositionFlow = viewModel.currentPositionSeconds,
                                durationSeconds = playerState.durationSeconds,
                                isShuffle = playerState.isShuffle,
                                shuffleMode = playerState.shuffleMode,
                                autoplayMode = playerState.autoplayMode,
                                speed = playerState.speed,
                                loadingType = playerState.loadingType,
                                onVerticalDrag = { deltaY -> 
                                    coroutineScope.launch {
                                        if (dragOverscrollY > 0f) {
                                            val updated = dragOverscrollY + deltaY * 0.35f
                                            dragOverscrollY = if (updated < 0f) 0f else updated
                                        } else if (dragOverscrollY < 0f) {
                                            val updated = dragOverscrollY + deltaY * 0.35f
                                            dragOverscrollY = if (updated > 0f) 0f else updated
                                        } else {
                                            val deltaFrac = -deltaY / maxPx
                                            val proposed = playerDragState.fraction + deltaFrac
                                            if (proposed > 1f) {
                                                playerDragState.seekTo(1f, false)
                                                val excessPx = (proposed - 1f) * maxPx
                                                dragOverscrollY -= excessPx * 0.35f
                                            } else if (proposed < 0f) {
                                                playerDragState.seekTo(0f, false)
                                                val excessPx = proposed * maxPx
                                                dragOverscrollY -= excessPx * 0.35f
                                            } else {
                                                playerDragState.seekTo(proposed, false)
                                            }
                                        }
                                    }
                                },
                                onVerticalDragEnd = { velocityY ->
                                    coroutineScope.launch {
                                        if (dragOverscrollY != 0f) {
                                            coroutineScope.launch {
                                                androidx.compose.animation.core.Animatable(dragOverscrollY).animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = com.anuj.obsidian.ui.animation.ObsidianMotion.GestureSettlingSpring
                                                ) {
                                                    dragOverscrollY = value
                                                }
                                            }
                                        }
                                        val target = playerDragState.fraction > 0.5f && velocityY < 500f
                                        playerDragState.animateTo(target, com.anuj.obsidian.ui.animation.ObsidianMotion.GestureSettlingSpring)
                                        viewModel.showFullPlayer(target)
                                    }
                                },
                                onDismiss = { viewModel.showFullPlayer(false) },
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onPlayNext = { viewModel.playNext() },
                                onPlayPrevious = { forceSkip -> viewModel.playPrevious(forceSkip) },
                                onSkipToNextCategory = { viewModel.skipToNextCategory() },
                                onSkipToPreviousCategory = { viewModel.skipToPreviousCategory() },
                                onSeek = { pos, scrubbing -> viewModel.seekTo(pos, scrubbing) },
                                onToggleShuffle = { viewModel.toggleShuffle() },
                                onSelectShuffleMode = { viewModel.setAdvancedShuffleMode(it) },
                                onLongToggleShuffle = { viewModel.cycleAdvancedShuffleMode() },
                                onToggleRepeat = { viewModel.cycleAutoplayMode() },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onOpenLyrics = { viewModel.showLyrics(true) },
                                onOpenTagInspector = { viewModel.openTagInspector(it) },
                                onSpeedChange = { viewModel.setSpeed(it) },
                                onOpenQueue = { viewModel.setTab(AppTab.QUEUE); viewModel.showFullPlayer(false) },
                                onSelectAutoplayMode = { viewModel.setAutoplayMode(it) },
                                isSheet = true,
                                sleepTimerMinutes = sleepTimerMinutes,
                                onSetSleepTimer = { viewModel.startSleepTimer(it) },
                                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                                onOpenDriveMode = { viewModel.showDriveMode(true) },
                                initialVisualizerPresetId = appSettings.lastVisualizerPresetId,
                                initialBackgroundVisualizer = appSettings.backgroundVisualizerEnabled,
                                initialVisualizerAlpha = appSettings.visualizerAlpha,
                                initialVisualizerCycleMode = appSettings.visualizerCycleMode,
                                initialVisualizerColorMode = appSettings.visualizerColorMode,
                                onSaveVisualizerSettings = { presetId, bg, alpha ->
                                    viewModel.saveVisualizerSettings(presetId, bg, alpha)
                                },
                                onSaveVisualizerCycleMode = { mode ->
                                    viewModel.setVisualizerCycleMode(mode)
                                },
                                onSaveVisualizerColorMode = { mode ->
                                    viewModel.setVisualizerColorMode(mode)
                                },
                                initialDynamicCanvasEnabled = appSettings.dynamicCanvasEnabled,
                                initialDynamicCanvasPreset = appSettings.dynamicCanvasPreset,
                                onSaveDynamicCanvasSettings = { enabled, preset ->
                                    viewModel.setDynamicCanvasSettings(enabled, preset)
                                },
                                onClosePlayer = {
                                    viewModel.stopPlayback()
                                    (context as? android.app.Activity)?.finishAndRemoveTask()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Drive Mode Sheet
        if (playerState.isDriveModeVisible) {
            com.anuj.obsidian.ui.screens.DriveModeScreen(
                viewModel = viewModel,
                onExit = { viewModel.showDriveMode(false) }
            )
        }
        
        // Synced Karaoke Lyrics Sheet
        if (playerState.isLyricsVisible && playerState.currentTrack != null) {
            LyricsSheet(
                track = playerState.currentTrack,
                currentPositionFlow = viewModel.currentPositionSeconds,
                isFetchingOnline = isFetchingLyrics,
                onFetchOnlineLyrics = { viewModel.fetchLyricsForCurrentTrack(forceRefresh = true) },
                onSearchCustomLyrics = { customTitle, customArtist ->
                    viewModel.searchCustomLyricsForCurrentTrack(customTitle, customArtist)
                },
                onSeek = { positionSeconds -> viewModel.seekTo(positionSeconds) },
                onDismiss = { viewModel.showLyrics(false) }
            )
        }

        // Vorbis Comment Tag Inspector Dialog
        if (playerState.isTagInspectorVisible && playerState.tagInspectorTrack != null) {
            TagInspectorDialog(
                track = playerState.tagInspectorTrack,
                onDismiss = { viewModel.closeTagInspector() },
                onSaveTags = { trackId, title, artist, album, genre, year, trackNum, lyrics ->
                    viewModel.saveTags(trackId, title, artist, album, genre, year, trackNum, lyrics)
                }
            )
        }

        val isEnrichmentSheetVisible by viewModel.isEnrichmentSheetVisible.collectAsStateWithLifecycle()
        if (isEnrichmentSheetVisible) {
            com.anuj.obsidian.ui.components.MetadataEnrichmentSheet(
                enricher = viewModel.libraryMetadataEnricher,
                onDismiss = { viewModel.showEnrichmentSheet(false) }
            )
        }

        val isSongRecognitionVisible by viewModel.isSongRecognitionVisible.collectAsStateWithLifecycle()
        if (isSongRecognitionVisible) {
            com.anuj.obsidian.ui.components.SongRecognitionModal(
                onDismiss = { viewModel.showSongRecognition(false) },
                onPlayTrack = { result ->
                    viewModel.playSearchResult(result, openFullPlayer = true)
                    viewModel.showSongRecognition(false)
                },
                onDownloadTrack = { result ->
                    viewModel.downloadSingle(result)
                }
            )
        }

        // Global Search Bar Overlay
        val isGlobalSearchVisible by viewModel.isGlobalSearchVisible.collectAsStateWithLifecycle()
        if (isGlobalSearchVisible) {
            com.anuj.obsidian.ui.components.GlobalSearchBarOverlay(
                viewModel = viewModel,
                onDismiss = { viewModel.showGlobalSearch(false) }
            )
        }

        // Contextual Coach Mark Tooltip for Mini Player
        if (settings.hasCompletedOnboarding && !settings.hasSeenCoachMarkMiniPlayerSwipe && playerState.currentTrack != null && !playerState.isFullPlayerVisible) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.anuj.obsidian.ui.components.CoachMarkTooltip(
                    text = "Tip: Swipe mini-player left or right to skip tracks",
                    isVisible = true,
                    onDismiss = { viewModel.dismissCoachMark("mini_player_swipe") },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, bottom = 120.dp)
                )
            }
        }

        // First-Run Onboarding Overlay
        if (!settings.hasCompletedOnboarding) {
            com.anuj.obsidian.ui.components.OnboardingDialog(
                onComplete = { defaultTab ->
                    viewModel.completeOnboarding(defaultTab)
                    val targetTab = when (defaultTab) {
                        "FOLDERS" -> AppTab.FOLDER_EXPLORER
                        "ALBUMS" -> AppTab.LIBRARY
                        else -> AppTab.HOME
                    }
                    viewModel.setTab(targetTab)
                }
            )
        }
        } // BoxWithConstraints
    } // CompositionLocalProvider
} // ObsidianObsidianApp

@Composable
fun ObsidianObsidianHeader(
    currentTab: AppTab,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val appColors = ObsidianTheme.colors

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(appColors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Obsidian (Made by Anuj)",
                        tint = Color.Black,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(9.dp))
                Column {
                    Text(
                        text = "Obsidian (Made by Anuj)",
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "Made by Anuj • Audiophile Engine",
                        color = appColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Global Search Quick Trigger
                Surface(
                    onClick = onOpenSearch,
                    shape = RoundedCornerShape(18.dp),
                    color = appColors.surfaceBorder,
                    border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder),
                    modifier = Modifier.testTag("header_global_search_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Library",
                            tint = appColors.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Search",
                            color = appColors.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Settings / DSP Audio quick access
                Surface(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(18.dp),
                    color = if (currentTab == AppTab.SETTINGS) appColors.primary.copy(alpha = 0.2f) else appColors.surfaceBorder,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentTab == AppTab.SETTINGS) appColors.primary else appColors.surfaceBorder
                    ),
                    modifier = Modifier.testTag("header_dsp_settings_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "DSP Settings",
                            tint = if (currentTab == AppTab.SETTINGS) appColors.primary else appColors.textPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "DSP",
                            color = if (currentTab == AppTab.SETTINGS) appColors.primary else appColors.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

val LocalErrorBoundaryHandler = androidx.compose.runtime.staticCompositionLocalOf<(Throwable) -> Unit> { {} }

@Composable
fun ErrorBoundary(
    screenName: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var error by remember(screenName) { androidx.compose.runtime.mutableStateOf<Throwable?>(null) }
    val errorHandler: (Throwable) -> Unit = remember(screenName) {
        { t ->
            val isCancellation = t is kotlinx.coroutines.CancellationException ||
                    t.cause is kotlinx.coroutines.CancellationException ||
                    t.message?.contains("coroutine scope left the composition", ignoreCase = true) == true ||
                    t.message?.contains("was cancelled", ignoreCase = true) == true
            if (isCancellation) {
                android.util.Log.d("ErrorBoundary", "Ignored normal coroutine cancellation in $screenName")
            } else {
                android.util.Log.e("ErrorBoundary", "Error caught in $screenName: ${t.message}", t)
                com.anuj.obsidian.ObsidianCrashLogger.logException(t, "ErrorBoundary.$screenName")
                error = t
            }
        }
    }

    if (error != null) {
        var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(error) {
            isVisible = true
        }
        val errorAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isVisible) 1f else 0f,
            animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard,
            label = "error_alpha"
        )
        val iconScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isVisible) 1f else 0.6f,
            animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
            label = "error_icon_scale"
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F0E17))
                .padding(24.dp)
                .graphicsLayer { alpha = errorAlpha },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Screen error indicator",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$screenName screen has hit an error",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error?.localizedMessage ?: "An unexpected layout error occurred.",
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.Button(
                    onClick = { error = null },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                ) {
                    Text("Tap to Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        androidx.compose.runtime.CompositionLocalProvider(LocalErrorBoundaryHandler provides errorHandler) {
            content()
        }
    }
}

@Composable
fun CrashLoopScreen(errorText: String, onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0E17))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "System Warning",
                tint = Color(0xFFFF4E4E),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Persistent Crash Loop Detected",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Obsidian has recovered from multiple sequential failures. To prevent a boot-loop, automatic relaunch is paused.",
                color = Color(0xFFC0C0C0),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E24))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Exception: $errorText",
                    color = Color(0xFFFFA4A4),
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = onReset,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
            ) {
                Text("Reset App Cache & Retry", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
