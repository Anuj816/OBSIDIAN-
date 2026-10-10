package com.anuj.obsidian.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.ui.animation.hardwareAccelerated
import kotlinx.coroutines.isActive

@Composable
fun ObsidianGpuVisualizer(
    modifier: Modifier = Modifier,
    presetId: Int = 1,
    isPlaying: Boolean = false,
    transitionAlpha: Float = 1f,
    isLibraryScrolling: Boolean = false,
    fadeOpacity: Float = 1f
) {
    val scratchPad = remember { VisualizerScratchPad() }
    val timeState = remember { mutableFloatStateOf(0f) }
    val preset = remember(presetId) { VisualizerLibrary.getPresetById(presetId) }

    val playerManager = com.anuj.obsidian.ObsidianApplication.playerManager
    val fadeController = remember { playerManager.getVisualizerFadeController() }
    var animatedOpacity by remember { mutableFloatStateOf(fadeController.calculateCurrentOpacity()) }
    
    val appSettings by com.anuj.obsidian.ObsidianApplication.repository.settings.collectAsState()
    val fpsLimit = appSettings.visualizerFpsLimit
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isAppForeground = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)

    LaunchedEffect(isPlaying, transitionAlpha, isAppForeground) {
        if (isPlaying && transitionAlpha > 0.001f && isAppForeground) {
            var lastFrame = System.nanoTime()
            val minFrameNanos = if (fpsLimit > 0) 1_000_000_000L / fpsLimit else 0L
            
            while (isActive) {
                androidx.compose.runtime.withFrameNanos { frameTime ->
                    val elapsed = frameTime - lastFrame
                    if (elapsed >= minFrameNanos || minFrameNanos == 0L) {
                        val dt = (elapsed / 1_000_000_000f).coerceIn(0f, 0.1f)
                        timeState.floatValue = (timeState.floatValue + dt * 16.6667f) % 10000f
                        lastFrame = frameTime

                        val snap = HardwareVisualizerBuffer.currentSnapshot
                        fadeController.updateAudioLevels(snap.bassEnergy, snap.midEnergy, snap.trebleEnergy)
                        animatedOpacity = fadeController.calculateCurrentOpacity()
                    }
                }
            }
        } else {
            animatedOpacity = fadeController.calculateCurrentOpacity()
        }
    }

    val finalAlpha = (transitionAlpha * fadeOpacity * animatedOpacity).coerceIn(0f, 1f)

    // Check if this is a Custom MilkDrop Preset that has a backing file or IR
    val parsedPreset = remember(presetId) { VisualizerLibrary.getParsedPreset(presetId) }
    val presetIR = remember(presetId, parsedPreset) {
        parsedPreset?.presetIR ?: MilkdropPresetLibrary.getPresetIRById(presetId)
    }
    val presetPath = parsedPreset?.sourceFile?.absolutePath
    val presetContent = parsedPreset?.rawMilkContent ?: parsedPreset?.sourceFile?.let { if (it.exists() && it.canRead()) runCatching { it.readText() }.getOrNull() else null }
    
    // Pre-allocated telemetry object for zero-GC bridging to GLSurfaceView
    val reusableTelemetry = remember { AudioTelemetrySnapshot() }
    var nativeFallbackActive by remember(presetId) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().graphicsLayer { alpha = finalAlpha }) {
        if (preset.category == VisualizerCategory.OBSIDIAN_MILKDROP) {
            val hasProjectMSource = presetPath != null || !presetContent.isNullOrBlank()
            if (hasProjectMSource && ProjectMNative.isLoaded && !nativeFallbackActive) {
                ProjectMVisualizer(
                    modifier = Modifier.fillMaxSize(),
                    isPlaying = isPlaying,
                    presetPath = presetPath,
                    presetContent = presetContent,
                    snapshot = HardwareVisualizerBuffer.currentSnapshot,
                    onFallbackRequired = {
                        android.util.Log.w("ObsidianGpuVisualizer", "Native projectM load issue for preset: $presetPath -> switching to GPU canvas fallback")
                        nativeFallbackActive = true
                    }
                )
            } else {
                MilkdropGpuCanvas(
                    presetIR = presetIR,
                    modifier = Modifier.fillMaxSize(),
                    telemetryProvider = {
                        val snap = HardwareVisualizerBuffer.currentSnapshot
                        val audioState = AudioAnalysisEngine.currentState
                        val bands = audioState.frequencyBands
                        reusableTelemetry.bass = if (isPlaying) (bands?.bass ?: snap.bassEnergy) else 0.1f
                        reusableTelemetry.mid = if (isPlaying) (bands?.mid ?: snap.midEnergy) else 0.1f
                        reusableTelemetry.treb = if (isPlaying) (bands?.treble ?: snap.trebleEnergy) else 0.1f
                        reusableTelemetry.bassAtt = if (isPlaying) (bands?.bass ?: snap.bassEnergy) * 0.95f else 0.1f
                        reusableTelemetry.midAtt = if (isPlaying) (bands?.mid ?: snap.midEnergy) * 0.95f else 0.1f
                        reusableTelemetry.trebAtt = if (isPlaying) (bands?.treble ?: snap.trebleEnergy) * 0.95f else 0.1f
                        reusableTelemetry.waveformLeft = audioState.waveform
                        reusableTelemetry.waveformRight = audioState.waveform
                        reusableTelemetry
                    }
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .hardwareAccelerated()
            ) {
                renderObsidianVisualizer(
                    preset = preset,
                    time = timeState.floatValue,
                    isPlaying = isPlaying,
                    progressFraction = 0f,
                    performanceMode = if (isLibraryScrolling) "low_30" else "smooth_60",
                    alpha = 1f,
                    scratchPad = scratchPad
                )
            }
        }
    }
}

@Composable
fun FallbackVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    transitionAlpha: Float = 1f,
    presetId: Int = 1,
    fadeOpacity: Float = 1f
) {
    ObsidianGpuVisualizer(
        modifier = modifier,
        presetId = presetId,
        isPlaying = isPlaying,
        transitionAlpha = transitionAlpha,
        fadeOpacity = fadeOpacity
    )
}

@Composable
fun ObsidianOverlayVisualizer(
    transitionAlpha: Float,
    isPlaying: Boolean,
    presetIndex: Int,
    isLibraryScrolling: Boolean = false,
    fadeOpacity: Float = 1f,
    modifier: Modifier = Modifier
) {
    ObsidianGpuVisualizer(
        modifier = modifier,
        presetId = presetIndex,
        isPlaying = isPlaying,
        transitionAlpha = transitionAlpha,
        isLibraryScrolling = isLibraryScrolling,
        fadeOpacity = fadeOpacity
    )
}
