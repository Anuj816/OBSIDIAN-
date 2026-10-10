package com.anuj.obsidian.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalTransitionApi::class)
@Composable
fun SeekablePlayer() {
    val state = remember { SeekableTransitionState(false) }
    val scope = rememberCoroutineScope()
    
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { },
            onDragEnd = {
                scope.launch { state.animateTo(state.fraction > 0.5f) }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                scope.launch {
                    val newFraction = (state.fraction - dragAmount / 1000f).coerceIn(0f, 1f)
                    state.seekTo(newFraction, true)
                }
            }
        )
    })
}
