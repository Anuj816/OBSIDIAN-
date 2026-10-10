package com.anuj.obsidian.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.DspState
import com.anuj.obsidian.data.model.EQ_BAND_FREQUENCIES
import com.anuj.obsidian.data.model.PREMADE_DSP_PRESETS
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.DarkSurfaceCard
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.FlacLosslessCyan
import com.anuj.obsidian.ui.theme.TextMuted
import com.anuj.obsidian.ui.theme.TextPrimary
import com.anuj.obsidian.ui.theme.TextSecondary
import com.anuj.obsidian.ui.theme.ObsidianTheme

/**
 * =========================================================================================
 * Settings Presets & EQ Section
 * =========================================================================================
 * RESPONSIBILITY:
 * Renders the DSP preset cards catalog, acoustic tone & spatial expansion sliders,
 * and the 10-band floating point equalizer inside Settings.
 * =========================================================================================
 */
fun LazyListScope.settingsPresetsEqSection(
    dspState: DspState,
    viewModel: ObsidianViewModel
) {
    // DSP PRESETS CATALOG
    item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pre-Made Audiophile Presets (${PREMADE_DSP_PRESETS.size})",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Instant switch",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            // Presets Grid / Rows
            val chunked = PREMADE_DSP_PRESETS.chunked(2)
            chunked.forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPresets.forEach { preset ->
                        val isSelected = dspState.activePresetId == preset.id
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.applyDspPreset(preset)
                                }
                                .testTag("preset_${preset.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) ObsidianTheme.colors.primary.copy(alpha = 0.15f) else DarkSurfaceCard
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isSelected) ObsidianTheme.colors.primary else DarkSurfaceBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = preset.iconEmoji,
                                        fontSize = 20.sp
                                    )
                                    if (isSelected) {
                                        Surface(
                                            shape = CircleShape,
                                            color = ObsidianTheme.colors.primary,
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active",
                                                tint = Color.Black,
                                                modifier = Modifier.padding(2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = preset.name,
                                    color = if (isSelected) ObsidianTheme.colors.primary else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = preset.description,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                    if (rowPresets.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // TONE & SOUNDSTAGE SLIDERS
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Acoustic Tone & Spatial Expansion",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                // Sub-Bass Boost
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sub-Harmonic Bass Boost", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "+${"%.1f".format(dspState.bassBoostDb)} dB",
                            color = FlacHiResGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Slider(
                        value = dspState.bassBoostDb,
                        onValueChange = { viewModel.setBassBoost(it) },
                        valueRange = 0f..18f,
                        colors = SliderDefaults.colors(
                            thumbColor = ObsidianTheme.colors.primary,
                            activeTrackColor = ObsidianTheme.colors.primary,
                            inactiveTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Treble Sparkle
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Treble Air & Presence", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "+${"%.1f".format(dspState.trebleBoostDb)} dB",
                            color = FlacLosslessCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Slider(
                        value = dspState.trebleBoostDb,
                        onValueChange = { viewModel.setTrebleBoost(it) },
                        valueRange = 0f..12f,
                        colors = SliderDefaults.colors(
                            thumbColor = FlacLosslessCyan,
                            activeTrackColor = FlacLosslessCyan,
                            inactiveTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Spatial Soundstage
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Binaural Soundstage Width", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "${(dspState.soundstageWidth * 100).toInt()}%",
                            color = ObsidianTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Slider(
                        value = dspState.soundstageWidth,
                        onValueChange = { viewModel.setSoundstageWidth(it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = ObsidianTheme.colors.primary,
                            activeTrackColor = ObsidianTheme.colors.primary,
                            inactiveTrackColor = DarkSurfaceBorder
                        )
                    )
                }
            }
        }
    }

    // 10-BAND GRAPHIC EQUALIZER
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "10-Band Audiophile Equalizer",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "32-bit Floating Point Precision (±12 dB)",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = {
                            PREMADE_DSP_PRESETS.firstOrNull { it.id == "flat" }?.let {
                                viewModel.applyDspPreset(it)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceBorder,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset EQ",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flat", fontSize = 13.sp)
                    }
                }

                // 10 EQ Band Sliders
                for ((bandIndex, freqLabel) in EQ_BAND_FREQUENCIES.withIndex()) {
                    val currentGain = dspState.eqGains.getOrElse(bandIndex) { 0f }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = freqLabel,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(45.dp)
                        )

                        Slider(
                            value = currentGain,
                            onValueChange = { viewModel.setEqGain(bandIndex, it) },
                            valueRange = -12f..12f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = if (currentGain > 0) ObsidianTheme.colors.primary else if (currentGain < 0) FlacHiResGold else TextSecondary,
                                activeTrackColor = if (currentGain >= 0) ObsidianTheme.colors.primary else FlacHiResGold,
                                inactiveTrackColor = DarkSurfaceBorder
                            )
                        )

                        Text(
                            text = if (currentGain > 0) "+${"%.1f".format(currentGain)}" else "%.1f".format(currentGain),
                            color = if (currentGain > 0) ObsidianTheme.colors.primary else if (currentGain < 0) FlacHiResGold else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(42.dp)
                        )
                    }
                }
            }
        }
    }
}
