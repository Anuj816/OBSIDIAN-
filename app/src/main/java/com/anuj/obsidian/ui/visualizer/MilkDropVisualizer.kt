package com.anuj.obsidian.ui.visualizer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.anuj.obsidian.ui.animation.hardwareAccelerated

/**
 * Modern Jetpack Compose Milkdrop Visualizer Component.
 * Powered by the Obsidian Milkdrop Hardware OpenGL ES Engine & IR Pipeline.
 */
@Composable
fun MilkDropVisualizerCanvas(
    presetId: Int = 2001,
    isPlaying: Boolean = false,
    alpha: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val presetIR = remember(presetId) {
        MilkdropPresetLibrary.getPresetIRById(presetId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .hardwareAccelerated()
            .graphicsLayer { this.alpha = alpha }
    ) {
        MilkdropGpuCanvas(
            presetIR = presetIR,
            modifier = Modifier.fillMaxSize(),
            telemetryProvider = {
                val snap = HardwareVisualizerBuffer.currentSnapshot
                val audioState = AudioAnalysisEngine.currentState
                val bands = audioState.frequencyBands
                AudioTelemetrySnapshot(
                    bass = if (isPlaying) (bands?.bass ?: snap.bassEnergy) else 0.1f,
                    mid = if (isPlaying) (bands?.mid ?: snap.midEnergy) else 0.1f,
                    treb = if (isPlaying) (bands?.treble ?: snap.trebleEnergy) else 0.1f,
                    bassAtt = if (isPlaying) (bands?.bass ?: snap.bassEnergy) * 0.95f else 0.1f,
                    midAtt = if (isPlaying) (bands?.mid ?: snap.midEnergy) * 0.95f else 0.1f,
                    trebAtt = if (isPlaying) (bands?.treble ?: snap.trebleEnergy) * 0.95f else 0.1f,
                    waveformLeft = audioState.waveform,
                    waveformRight = audioState.waveform
                )
            }
        )
    }
}

