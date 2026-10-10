package com.anuj.obsidian.ui.visualizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlinx.coroutines.delay

/**
 * Floating On-Screen Display (HUD) and Control Bar for MilkDrop 3 (milkdrop2077/MilkDrop3).
 * Provides realtime diagnostic statistics (Preset A + B names, Blend Pattern, Ratio, Audio VU, FPS)
 * and direct hotkey action triggers ('p', 'P', 'o', 'u', 'U', 'b', 'c', Save .milk2).
 */
@Composable
fun Milkdrop3HudOverlay(
    modifier: Modifier = Modifier,
    fps: Int = 60
) {
    val context = LocalContext.current
    val hudVisible by Milkdrop3Controller.hudVisible.collectAsStateWithLifecycle()
    val activeDoublePreset by Milkdrop3Controller.activeDoublePreset.collectAsStateWithLifecycle()
    val currentPattern by Milkdrop3Controller.currentPattern.collectAsStateWithLifecycle()
    val liveRatio by Milkdrop3Controller.liveBlendRatio.collectAsStateWithLifecycle()
    val plasmaSeed by Milkdrop3Controller.plasmaSeed.collectAsStateWithLifecycle()
    val isFlipped by Milkdrop3Controller.isFlipped.collectAsStateWithLifecycle()
    val isLocked by Milkdrop3Controller.isDirectionLocked.collectAsStateWithLifecycle()
    val autoBeatSwitch by Milkdrop3Controller.autoBeatSwitch.collectAsStateWithLifecycle()
    val colorMod by Milkdrop3Controller.colorModulation.collectAsStateWithLifecycle()
    val statusToast by Milkdrop3Controller.statusToast.collectAsStateWithLifecycle()

    val snapshot = HardwareVisualizerBuffer.currentSnapshot

    // Auto-dismiss toast
    LaunchedEffect(statusToast) {
        if (statusToast != null) {
            delay(2500L)
            Milkdrop3Controller.clearToast()
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Status Toast Notification
        AnimatedVisibility(
            visible = statusToast != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Surface(
                color = Color(0xEE111520),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusToast ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Main HUD Panel
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            val cur = activeDoublePreset ?: Milkdrop3Controller.getCuratedDoublePresets().first()
            val primaryPreset = if (!isFlipped) cur.presetA else cur.presetB
            val secondaryPreset = if (!isFlipped) cur.presetB else cur.presetA

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF2A85))), RoundedCornerShape(16.dp)),
                color = Color(0xF00A0E17),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Header Row: Title & Diagnostics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .border(0.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "MILKDROP 3.0",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FPS: $fps  •  GRID: 24x18  •  BEAT: ${if (snapshot.bassEnergy > 0.4f) "DROP!" else "SYNC"}",
                                color = Color(0xFF88A0C0),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        IconButton(
                            onClick = { Milkdrop3Controller.toggleHud() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close HUD",
                                tint = Color(0xFF88A0C0),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset A <-> Preset B Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Preset A
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LAYER A",
                                color = Color(0xFF00E5FF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = primaryPreset.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = primaryPreset.author,
                                color = Color(0xFF88A0C0),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        // Swap Button
                        IconButton(
                            onClick = { Milkdrop3Controller.flipPresetOrder() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x22FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Flip Presets (u)",
                                tint = if (isFlipped) Color(0xFFFF2A85) else Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Preset B
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "LAYER B",
                                color = Color(0xFFFF2A85),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = secondaryPreset.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = secondaryPreset.author,
                                color = Color(0xFF88A0C0),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Blend Pattern Selector & Ratio Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            onClick = { Milkdrop3Controller.cycleBlendPattern() },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF161F30),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3A55))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Waves,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentPattern.displayName,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Text(
                            text = "Blend: ${(liveRatio * 100).toInt()}%",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Slider(
                        value = liveRatio,
                        onValueChange = { Milkdrop3Controller.setLiveBlendRatio(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF161F30)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hotkey Action Buttons Bar (MilkDrop 3 Keyboard Equivalents)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HudActionButton(
                            label = "Pattern (p)",
                            icon = Icons.Default.Shuffle,
                            onClick = { Milkdrop3Controller.cycleBlendPattern() }
                        )

                        HudActionButton(
                            label = "Seed (o)",
                            icon = Icons.Default.Refresh,
                            onClick = { Milkdrop3Controller.randomizePlasmaSeed() }
                        )

                        HudActionButton(
                            label = if (isLocked) "Locked (U)" else "Lock (U)",
                            icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            isActive = isLocked,
                            onClick = { Milkdrop3Controller.toggleDirectionLock() }
                        )

                        HudActionButton(
                            label = if (autoBeatSwitch) "Beat ON (b)" else "Beat OFF",
                            icon = Icons.Default.GraphicEq,
                            isActive = autoBeatSwitch,
                            onClick = { Milkdrop3Controller.toggleBeatAutoSwitch() }
                        )

                        HudActionButton(
                            label = "Palette (c)",
                            icon = Icons.Default.ColorLens,
                            onClick = { Milkdrop3Controller.cycleColorModulation() }
                        )

                        HudActionButton(
                            label = "Studio (F9)",
                            icon = Icons.Default.Tune,
                            onClick = { Milkdrop3Controller.openMixerSheet() }
                        )

                        HudActionButton(
                            label = "Save .milk2",
                            icon = Icons.Default.Save,
                            onClick = {
                                Milkdrop3Controller.exportCurrentAsMilk2(
                                    context = context,
                                    name = cur.name
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (isActive) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF161F30),
                    CircleShape
                )
                .border(
                    0.5.dp,
                    if (isActive) Color(0xFF00E5FF) else Color(0xFF2A3A55),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFF00E5FF) else Color(0xFFC0D0E5),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isActive) Color(0xFF00E5FF) else Color(0xFF88A0C0),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
