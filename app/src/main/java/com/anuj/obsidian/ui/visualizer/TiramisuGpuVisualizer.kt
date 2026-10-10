package com.anuj.obsidian.ui.visualizer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TiramisuGpuVisualizer(
    modifier: Modifier = Modifier,
    presetId: Int,
    isPlaying: Boolean,
    transitionAlpha: Float,
    isLibraryScrolling: Boolean = false,
    fadeOpacity: Float = 1f
) {
    ObsidianGpuVisualizer(
        modifier = modifier,
        presetId = presetId,
        isPlaying = isPlaying,
        transitionAlpha = transitionAlpha,
        isLibraryScrolling = isLibraryScrolling,
        fadeOpacity = fadeOpacity
    )
}
