package com.anuj.obsidian.ui.components.player

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.ui.animation.hardwareAccelerated
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.visualizer.VisualizerLibrary
import com.anuj.obsidian.ui.visualizer.VisualizerScratchPad
import com.anuj.obsidian.ui.visualizer.renderObsidianVisualizer
import kotlinx.coroutines.isActive

/**
 * BackgroundVisualizerLayer
 * Renders smooth 60fps hardware-accelerated visualizer graphics behind album artwork with smooth
 * crossfading between preset transitions.
 */
@Composable
fun BackgroundVisualizerLayer(
    presetId: Int,
    isPlaying: Boolean,
    progressProvider: () -> Float,
    alphaProvider: () -> Float,
    colorMode: String = "ORIGINAL",
    performanceMode: String = "smooth_60",
    fpsLimit: Int = 60,
    isLibraryScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isFullyHidden by remember {
        derivedStateOf { alphaProvider() <= 0.001f }
    }

    if (performanceMode == "disabled" || isFullyHidden) return

    val timeState = remember { mutableFloatStateOf(0f) }
    if (isPlaying) {
        LaunchedEffect(performanceMode, isLibraryScrolling, fpsLimit) {
            val throttleDelayMs = when {
                isLibraryScrolling -> 33L // Throttle to ~30fps while scrolling
                fpsLimit == 30 -> 33L
                performanceMode == "ultra_low_power" || performanceMode == "potato" -> 33L
                performanceMode == "minimalist" -> 40L
                else -> 0L // Native VSYNC 60Hz/90Hz/120Hz via withFrameNanos
            }
            var lastFrameTime = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameTime ->
                    val dt = ((frameTime - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    timeState.floatValue = (timeState.floatValue + dt * 16.6667f) % 10000f
                    lastFrameTime = frameTime
                }
                if (throttleDelayMs > 0L) {
                    kotlinx.coroutines.delay(throttleDelayMs)
                }
            }
        }
    }

    val appColors = ObsidianTheme.colors
    val scratchPad = remember { VisualizerScratchPad() }

    var currentPresetIdState by remember { mutableIntStateOf(presetId) }
    var previousPresetIdState by remember { mutableStateOf<Int?>(null) }
    val crossfadeAnim = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(presetId) {
        if (presetId != currentPresetIdState) {
            previousPresetIdState = currentPresetIdState
            currentPresetIdState = presetId
            crossfadeAnim.snapTo(0f)
            crossfadeAnim.animateTo(
                targetValue = 1f,
                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard
            )
            previousPresetIdState = null
        }
    }

    val livePresets by VisualizerLibrary.presetsStateFlow.collectAsStateWithLifecycle()

    val resolvedCurrentPreset = remember(currentPresetIdState, livePresets, colorMode, appColors.primary, appColors.secondary) {
        val preset = VisualizerLibrary.getPresetById(currentPresetIdState)
        if (preset.category == com.anuj.obsidian.ui.visualizer.VisualizerCategory.OBSIDIAN_MILKDROP) {
            Log.d("BackgroundVisualizerLayer", "Rendering Milkdrop preset: ${preset.name} (ID: ${preset.id})")
        }
        preset.withColorMode(
            colorMode = colorMode,
            themePrimary = appColors.primary,
            themeSecondary = appColors.secondary,
            customPrimary = appColors.primary,
            customSecondary = appColors.secondary
        )
    }

    val resolvedPreviousPreset = remember(previousPresetIdState, livePresets, colorMode, appColors.primary, appColors.secondary) {
        val prevId = previousPresetIdState
        if (prevId != null) {
            val preset = VisualizerLibrary.getPresetById(prevId)
            preset.withColorMode(
                colorMode = colorMode,
                themePrimary = appColors.primary,
                themeSecondary = appColors.secondary,
                customPrimary = appColors.primary,
                customSecondary = appColors.secondary
            )
        } else {
            null
        }
    }

    val parsedCurrentPreset = remember(resolvedCurrentPreset.id) { 
        com.anuj.obsidian.ui.visualizer.VisualizerLibrary.getParsedPreset(resolvedCurrentPreset.id) 
    }
    val currentPresetPath = parsedCurrentPreset?.sourceFile?.absolutePath
    val currentPresetContent = remember(parsedCurrentPreset, resolvedCurrentPreset.id) {
        parsedCurrentPreset?.rawMilkContent 
            ?: parsedCurrentPreset?.sourceFile?.let { if (it.exists() && it.canRead()) runCatching { it.readText() }.getOrNull() else null }
    }
    val currentPresetIR = remember(parsedCurrentPreset, resolvedCurrentPreset.id) {
        parsedCurrentPreset?.presetIR ?: com.anuj.obsidian.ui.visualizer.MilkdropPresetLibrary.getPresetIRById(resolvedCurrentPreset.id)
    }
    val hasRealMilkSource = currentPresetPath != null || !currentPresetContent.isNullOrBlank()

    Box(modifier = modifier.fillMaxSize()) {
        val prevPreset = resolvedPreviousPreset

        if (prevPreset != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .hardwareAccelerated()
                    .graphicsLayer {
                        val base = alphaProvider()
                        val cross = crossfadeAnim.value
                        alpha = (base * (1f - cross)).coerceIn(0f, 1f)
                    }
            ) {
                renderObsidianVisualizer(
                    preset = prevPreset,
                    time = timeState.floatValue,
                    isPlaying = isPlaying,
                    progressFraction = progressProvider(),
                    performanceMode = performanceMode,
                    alpha = 1f,
                    scratchPad = scratchPad
                )
            }
        }

        if (resolvedCurrentPreset.category == com.anuj.obsidian.ui.visualizer.VisualizerCategory.OBSIDIAN_MILKDROP) {
            val ir = currentPresetIR
            com.anuj.obsidian.ui.visualizer.MilkdropGpuCanvas(
                presetIR = ir,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val base = alphaProvider()
                        val cross = crossfadeAnim.value
                        alpha = (base * cross).coerceIn(0f, 1f)
                    },
                telemetryProvider = {
                    val snap = com.anuj.obsidian.ui.visualizer.HardwareVisualizerBuffer.currentSnapshot
                    val audioState = com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.currentState
                    val bands = audioState.frequencyBands
                    scratchPad.telemetry.bass = if (isPlaying) (bands?.bass ?: snap.bassEnergy) else 0.1f
                    scratchPad.telemetry.mid = if (isPlaying) (bands?.mid ?: snap.midEnergy) else 0.1f
                    scratchPad.telemetry.treb = if (isPlaying) (bands?.treble ?: snap.trebleEnergy) else 0.1f
                    scratchPad.telemetry.bassAtt = if (isPlaying) (bands?.bass ?: snap.bassEnergy) * 0.95f else 0.1f
                    scratchPad.telemetry.midAtt = if (isPlaying) (bands?.mid ?: snap.midEnergy) * 0.95f else 0.1f
                    scratchPad.telemetry.trebAtt = if (isPlaying) (bands?.treble ?: snap.trebleEnergy) * 0.95f else 0.1f
                    scratchPad.telemetry.waveformLeft = audioState.waveform
                    scratchPad.telemetry.waveformRight = audioState.waveform
                    scratchPad.telemetry
                }
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .hardwareAccelerated()
                    .graphicsLayer {
                        val base = alphaProvider()
                        val cross = crossfadeAnim.value
                        alpha = (base * cross).coerceIn(0f, 1f)
                    }
            ) {
                renderObsidianVisualizer(
                    preset = resolvedCurrentPreset,
                    time = timeState.floatValue,
                    isPlaying = isPlaying,
                    progressFraction = progressProvider(),
                    performanceMode = performanceMode,
                    alpha = 1f,
                    scratchPad = scratchPad
                )
            }
        }
    }
}
