package com.anuj.obsidian.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.anuj.obsidian.ui.animation.safePerform
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.anuj.obsidian.ui.theme.ObsidianTheme

data class AmbientSoundLayer(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

private val AMBIENT_LAYERS = listOf(
    AmbientSoundLayer("rain", "Heavy Rain & Glass", "Relaxing rainfall soothing background noise", Icons.Default.WaterDrop, Color(0xFF00B0FF)),
    AmbientSoundLayer("binaural_432", "432Hz Alpha Waves", "Solfeggio frequency for focus & mental clarity", Icons.Default.Headphones, Color(0xFF7F00FF)),
    AmbientSoundLayer("thunder", "Cozy Thunderstorm", "Distant rolling thunder & storm atmosphere", Icons.Default.Thunderstorm, Color(0xFFFF9100)),
    AmbientSoundLayer("ocean", "Tidal Ocean Waves", "Gentle rhythmic surf breaking on shore", Icons.Default.Pool, Color(0xFF00E676)),
    AmbientSoundLayer("space", "Cosmic Brown Noise", "Sub-bass deep space drone for deep sleep", Icons.Default.NightsStay, Color(0xFFFF007F)),
    AmbientSoundLayer("forest", "Pine Forest Breeze", "Rustling canopy wind and night ambience", Icons.Default.WbSunny, Color(0xFFFFD600))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientSoundscapeSheet(
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val appColors = ObsidianTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var masterEnabled by remember { mutableStateOf(true) }
    val layerVolumes = remember {
        mutableStateMapOf<String, Float>().apply {
            put("rain", 0.4f)
            put("binaural_432", 0.3f)
            put("thunder", 0.0f)
            put("ocean", 0.0f)
            put("space", 0.0f)
            put("forest", 0.0f)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.surfaceElevated,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = null,
        modifier = Modifier.testTag("ambient_soundscape_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF00E676), Color(0xFF00B0FF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Waves,
                            contentDescription = "Organic Soundscapes",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Organic Soundscapes & Sleep Layers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = appColors.textPrimary
                        )
                        Text(
                            text = "Blend organic rain, 432Hz & space noise into playback",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors.textSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = appColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Master switch
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = appColors.surfaceElevated,
                border = BorderStroke(1.dp, appColors.surfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Soundscape Overlay Engine",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary
                    )
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = {
                            haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            masterEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF00E676))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sound Layers List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(AMBIENT_LAYERS) { layer ->
                    val volume = layerVolumes[layer.id] ?: 0f
                    val isActive = masterEnabled && volume > 0f

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) layer.accentColor.copy(alpha = 0.12f) else appColors.surfaceElevated
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) layer.accentColor.copy(alpha = 0.5f) else appColors.surfaceBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = layer.icon,
                                        contentDescription = layer.name,
                                        tint = if (isActive) layer.accentColor else appColors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = layer.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = appColors.textPrimary
                                        )
                                        Text(
                                            text = layer.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = appColors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "${(volume * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) layer.accentColor else appColors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Slider(
                                value = volume,
                                onValueChange = { newVol ->
                                    layerVolumes[layer.id] = newVol
                                },
                                valueRange = 0f..1f,
                                enabled = masterEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = layer.accentColor,
                                    activeTrackColor = layer.accentColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
