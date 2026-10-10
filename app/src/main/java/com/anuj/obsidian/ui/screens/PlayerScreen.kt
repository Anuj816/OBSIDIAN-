package com.anuj.obsidian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.ui.AppTab
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.components.FullPlayerSheet
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.util.ArtworkPaletteCache

@Composable
fun PlayerScreen(
    viewModel: ObsidianViewModel,
    modifier: Modifier = Modifier
) {
    val errorHandler = com.anuj.obsidian.LocalErrorBoundaryHandler.current
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playbackSeq by viewModel.playbackSequence.collectAsStateWithLifecycle()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val appSettings by viewModel.settings.collectAsStateWithLifecycle()
    val appColors = ObsidianTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentTrack = playerState.currentTrack

    val dynamicPrimaryColor = androidx.compose.runtime.remember(currentTrack?.id) {
        try {
            currentTrack?.let { track ->
                ArtworkPaletteCache.getCachedColors(track)?.primary
            } ?: appColors.primary
        } catch (e: Throwable) {
            appColors.primary
        }
    }

    val hasValidTrackArt = androidx.compose.runtime.remember(currentTrack?.id, currentTrack?.albumArtUrl) {
        try {
            currentTrack != null && com.anuj.obsidian.ui.components.hasRealArtwork(currentTrack)
        } catch (e: Throwable) {
            false
        }
    }

    android.util.Log.d("PlayerScreen", "PlayerScreen composition started: currentTrack=${currentTrack?.id} (${currentTrack?.title})")

    if (currentTrack == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(appColors.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = appColors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, appColors.surfaceBorder),
                    modifier = Modifier.size(90.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "No Track",
                            tint = appColors.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "NO TRACK PLAYING",
                    color = appColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pick a song from your Library or Search to begin listening",
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.setTab(AppTab.LIBRARY) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.primary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to Library", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        android.util.Log.d("PlayerScreen", "PlayerScreen: Rendering FullPlayerSheet for track: ${currentTrack.id} - ${currentTrack.title}")
        FullPlayerSheet(
            track = currentTrack,
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
            onDismiss = { viewModel.setTab(AppTab.HOME) },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onPlayNext = { viewModel.playNext() },
            onPlayPrevious = { forceSkip -> viewModel.playPrevious(forceSkip) },
            onSkipToNextCategory = { viewModel.skipToNextCategory() },
            onSkipToPreviousCategory = { viewModel.skipToPreviousCategory() },
            onSeek = { pos, scrubbing -> viewModel.seekTo(pos, scrubbing) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onSelectShuffleMode = { viewModel.setAdvancedShuffleMode(it) },
            onToggleRepeat = { viewModel.cycleAutoplayMode() },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onOpenLyrics = { viewModel.showLyrics(true) },
            onOpenTagInspector = { viewModel.openTagInspector(it) },
            onSpeedChange = { viewModel.setSpeed(it) },
            onOpenQueue = { viewModel.setTab(AppTab.QUEUE) },
            onSelectAutoplayMode = { viewModel.setAutoplayMode(it) },
            isSheet = false,
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
            },
            modifier = modifier
        )
    }
}
