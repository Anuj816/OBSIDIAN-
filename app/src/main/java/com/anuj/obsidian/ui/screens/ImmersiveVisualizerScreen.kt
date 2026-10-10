package com.anuj.obsidian.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.visualizer.VisualizerLibrary
import com.anuj.obsidian.ui.visualizer.ObsidianGpuVisualizer
import kotlinx.coroutines.delay

@Composable
fun ImmersiveVisualizerScreen(
    presetId: Int,
    isPlaying: Boolean,
    currentTrack: DownloadedTrack? = null,
    onTogglePlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onOpenSelector: () -> Unit = {},
    onExit: () -> Unit
) {
    val appColors = ObsidianTheme.colors
    var controlsVisible by remember { mutableStateOf(true) }
    val currentPreset = remember(presetId) { VisualizerLibrary.getPresetById(presetId) }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(4000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        // Full screen visualizer canvas
        ObsidianGpuVisualizer(
            modifier = Modifier.fillMaxSize(),
            presetId = presetId,
            isPlaying = isPlaying,
            transitionAlpha = 1f
        )

        // Floating Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentPreset.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val parsedPreset = remember(currentPreset.id) { com.anuj.obsidian.ui.visualizer.VisualizerLibrary.getParsedPreset(currentPreset.id) }
                            val hasProjectMSource = parsedPreset?.sourceFile != null || !parsedPreset?.rawMilkContent.isNullOrBlank()
                            val isProjectMLoaded = com.anuj.obsidian.ui.visualizer.ProjectMNative.isLoaded
                            val engineLabel = if (currentPreset.category == com.anuj.obsidian.ui.visualizer.VisualizerCategory.OBSIDIAN_MILKDROP) {
                                if (isProjectMLoaded && hasProjectMSource) {
                                    "MilkDrop · projectM"
                                } else if (!isProjectMLoaded) {
                                    "projectM unavailable"
                                } else {
                                    "Built-in demo"
                                }
                            } else {
                                "Obsidian GPU Engine"
                            }
                            Text(
                                text = engineLabel,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val hudOn by com.anuj.obsidian.ui.visualizer.Milkdrop3Controller.hudVisible.collectAsState()
                        IconButton(
                            onClick = {
                                com.anuj.obsidian.ui.visualizer.Milkdrop3Controller.toggleHud()
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Toggle HUD",
                                tint = if (hudOn) Color(0xFF00E5FF) else Color.White
                            )
                        }

                        IconButton(
                            onClick = onOpenSelector,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Tune",
                                tint = Color.White
                            )
                        }

                        Button(
                            onClick = onExit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.primary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit",
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = " Exit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // MilkDrop 3 Diagnostic HUD Overlay
                com.anuj.obsidian.ui.visualizer.Milkdrop3HudOverlay(
                    modifier = Modifier.align(Alignment.TopStart)
                )

                // Bottom Floating Playback Bar
                if (currentTrack != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(onClick = onSkipPrevious) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                            }
                            IconButton(
                                onClick = onTogglePlayPause,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(appColors.primary)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black
                                )
                            }
                            IconButton(onClick = onSkipNext) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
