package com.anuj.obsidian.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.OBSIDIAN_DDC_PROFILES
import com.anuj.obsidian.data.model.OBSIDIAN_DSP_PRESETS
import com.anuj.obsidian.data.model.OBSIDIAN_IMPULSE_RESPONSES
import com.anuj.obsidian.data.model.ObsidianDspPreset
import com.anuj.obsidian.data.model.ObsidianDspState
import com.anuj.obsidian.ui.dsp.ObsidianDspEngine
import com.anuj.obsidian.ui.theme.ObsidianTheme

private val FREQ_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ObsidianDspBottomSheet(
    onDismiss: () -> Unit,
    primaryColor: Color? = null,
    secondaryColor: Color? = null,
    surfaceColor: Color? = null
) {
    val dspState by ObsidianDspEngine.dspState.collectAsStateWithLifecycle()
    val baseColors = ObsidianTheme.colors
    val appColors = remember(baseColors, primaryColor, secondaryColor, surfaceColor) {
        if (primaryColor == null && secondaryColor == null && surfaceColor == null) {
            baseColors
        } else {
            com.anuj.obsidian.ui.theme.AudioPaletteHelper.createHarmonizedThemePalette(
                primary = primaryColor ?: baseColors.primary,
                secondary = secondaryColor ?: baseColors.secondary,
                bg = surfaceColor ?: baseColors.background
            )
        }
    }
    val isLightPrimary = appColors.primary.red * 0.299f + appColors.primary.green * 0.587f + appColors.primary.blue * 0.114f > 0.5f
    val onPrimaryColor = if (isLightPrimary) Color.Black else Color.White

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Equalizer & Presets, 1: Bass & Spatial, 2: Reverb & DDC

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.surfaceElevated,
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = appColors.surfaceBorder
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Header with Master Toggle & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (dspState.isMasterEnabled) appColors.primary.copy(alpha = 0.2f) else appColors.surfaceCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (dspState.isMasterEnabled) appColors.primary else appColors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PARAMETRIC DSP ENGINE",
                            color = appColors.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (dspState.isMasterEnabled) "32-Bit Hardware Accelerated • ACTIVE" else "DSP Bypass • Direct Stream",
                            color = if (dspState.isMasterEnabled) appColors.secondary else appColors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = dspState.isMasterEnabled,
                        onCheckedChange = { isEnabled ->
                            ObsidianDspEngine.updateState { it.copy(isMasterEnabled = isEnabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.surfaceElevated,
                            checkedTrackColor = appColors.primary,
                            uncheckedThumbColor = appColors.textMuted,
                            uncheckedTrackColor = appColors.surfaceCard
                        ),
                        modifier = Modifier.testTag("dsp_master_switch")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dsp_sheet_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close DSP Controls",
                            tint = appColors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

// Tab Navigation (Equalizer, Bass & Spatial, Tube & Reverb, DDC)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = appColors.surfaceCard,
                contentColor = appColors.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = appColors.primary,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Multi-Band EQ", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Bass & Spatial", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Tube & Convolver", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Scrollable Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> EqualizerAndPresetsTab(dspState = dspState, appColors = appColors)
                    1 -> BassAndSpatialTab(dspState = dspState, appColors = appColors)
                    2 -> TubeAndReverbTab(dspState = dspState, appColors = appColors)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EqualizerAndPresetsTab(
    dspState: ObsidianDspState,
    appColors: com.anuj.obsidian.ui.theme.AppThemePalette
) {
    val isLightPrimary = appColors.primary.red * 0.299f + appColors.primary.green * 0.587f + appColors.primary.blue * 0.114f > 0.5f
    val onPrimaryColor = if (isLightPrimary) Color.Black else Color.White

    Column(modifier = Modifier.fillMaxWidth()) {
        // Android System Hardware Media Volume Controller with Rotary Dial
        AndroidSystemVolumeController(
            appColors = appColors,
            showRotaryDial = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Presets Header & Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUDIOPHILE PRESETS",
                color = appColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            TextButton(
                onClick = {
                    ObsidianDspEngine.updateState {
                        it.copy(
                            eqGains = List(10) { 0f },
                            preampGainDb = 0f
                        )
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = appColors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Flat", color = appColors.textMuted, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OBSIDIAN_DSP_PRESETS.forEach { preset ->
                val isSelected = dspState.eqGains == preset.eqGains
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        ObsidianDspEngine.updateState { current ->
                            current.copy(
                                eqGains = preset.eqGains,
                                bassGainDb = preset.bassGainDb,
                                isBassEnabled = preset.isBassEnabled,
                                bassMode = preset.bassMode,
                                clarityGainDb = preset.clarityGainDb,
                                isClarityEnabled = preset.isClarityEnabled,
                                isFieldSurroundEnabled = preset.isFieldSurroundEnabled,
                                surroundWidthPercent = preset.surroundWidthPercent,
                                isConvolverEnabled = preset.isConvolverEnabled,
                                selectedImpulseId = preset.selectedImpulseId,
                                convolverWetPercent = preset.convolverWetPercent
                            )
                        }
                    },
                    label = { Text(preset.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = appColors.primary,
                        selectedLabelColor = onPrimaryColor,
                        containerColor = appColors.surfaceCard,
                        labelColor = appColors.textPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) appColors.primary else appColors.surfaceBorder,
                        borderWidth = 1.dp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preamp Gain Slider
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
            border = BorderStroke(1.dp, appColors.surfaceBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PREAMP MASTER GAIN", color = appColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = (if (dspState.preampGainDb >= 0) "+" else "") + "%.1f dB".format(dspState.preampGainDb),
                        color = appColors.primary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = dspState.preampGainDb,
                    onValueChange = { gain ->
                        ObsidianDspEngine.updateState { it.copy(preampGainDb = gain) }
                    },
                    valueRange = -12f..12f,
                    colors = SliderDefaults.colors(
                        thumbColor = appColors.primary,
                        activeTrackColor = appColors.primary,
                        inactiveTrackColor = appColors.surfaceBorder.copy(alpha = 0.7f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Multi-Band Graphic Equalizer Sliders
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
            border = BorderStroke(1.dp, appColors.surfaceBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dspState.eqBandMode}-BAND PARAMETRIC GRAPHIC EQ",
                        color = appColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(10, 15, 25, 31).forEach { count ->
                            val isSelected = dspState.eqBandMode == count
                            Surface(
                                onClick = {
                                    ObsidianDspEngine.updateState { state ->
                                        val newGains = MutableList(count) { idx ->
                                            state.eqGains.getOrElse(idx) { 0f }
                                        }
                                        state.copy(eqBandMode = count, eqGains = newGains)
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) appColors.primary else appColors.surfaceElevated,
                                contentColor = if (isSelected) onPrimaryColor else appColors.textSecondary,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) appColors.primary else appColors.surfaceBorder
                                ),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${count}B",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val freqs = when (dspState.eqBandMode) {
                    15 -> listOf("25", "40", "63", "100", "160", "250", "400", "630", "1k", "1.6k", "2.5k", "4k", "6.3k", "10k", "16k")
                    25 -> listOf("20", "28", "40", "56", "80", "112", "160", "224", "315", "450", "630", "900", "1.2k", "1.8k", "2.5k", "3.5k", "5k", "7.1k", "10k", "12.5k", "14k", "16k", "18k", "20k", "22k")
                    31 -> listOf("20", "25", "31.5", "40", "50", "63", "80", "100", "125", "160", "200", "250", "315", "400", "500", "630", "800", "1k", "1.25k", "1.6k", "2k", "2.5k", "3.15k", "4k", "5k", "6.3k", "8k", "10k", "12.5k", "16k", "20k")
                    else -> FREQ_LABELS
                }

                val numBands = dspState.eqBandMode
                val gains = dspState.eqGains.toMutableList().ifEmpty { MutableList(numBands) { 0f } }

                for (i in 0 until numBands) {
                    val gain = if (i < gains.size) gains[i] else 0f
                    val freqLabel = (freqs.getOrElse(i) { "$i" }) + " Hz"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = freqLabel,
                            color = appColors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(62.dp)
                        )

                        Slider(
                            value = gain,
                            onValueChange = { newVal ->
                                val updated = gains.toMutableList()
                                while (updated.size <= i) updated.add(0f)
                                updated[i] = newVal
                                ObsidianDspEngine.updateState { it.copy(eqGains = updated) }
                            },
                            valueRange = -15f..15f,
                            colors = SliderDefaults.colors(
                                thumbColor = if (gain > 0f) appColors.primary else if (gain < 0f) appColors.secondary else appColors.textMuted,
                                activeTrackColor = if (gain >= 0f) appColors.primary else appColors.secondary,
                                inactiveTrackColor = appColors.surfaceBorder.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = (if (gain >= 0) "+" else "") + "%.1f dB".format(gain),
                            color = if (gain > 0f) appColors.primary else if (gain < 0f) appColors.secondary else appColors.textMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(58.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BassAndSpatialTab(
    dspState: ObsidianDspState,
    appColors: com.anuj.obsidian.ui.theme.AppThemePalette
) {
    val isLightPrimary = appColors.primary.red * 0.299f + appColors.primary.green * 0.587f + appColors.primary.blue * 0.114f > 0.5f
    val onPrimaryColor = if (isLightPrimary) Color.Black else Color.White

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dynamic Bass Exciter Card
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
            border = BorderStroke(1.dp, if (dspState.isBassEnabled) appColors.primary.copy(alpha = 0.5f) else appColors.surfaceBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Waves,
                            contentDescription = null,
                            tint = appColors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DYNAMIC BASS EXCITER",
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = dspState.isBassEnabled,
                        onCheckedChange = { isEnabled ->
                            ObsidianDspEngine.updateState { it.copy(isBassEnabled = isEnabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.surfaceElevated,
                            checkedTrackColor = appColors.primary
                        )
                    )
                }

                AnimatedVisibility(visible = dspState.isBassEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("BASS GAIN", color = appColors.textSecondary, fontSize = 12.sp)
                            Text("+%.1f dB".format(dspState.bassGainDb), color = appColors.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = dspState.bassGainDb,
                            onValueChange = { gain ->
                                ObsidianDspEngine.updateState { it.copy(bassGainDb = gain) }
                            },
                            valueRange = 0f..18f,
                            colors = SliderDefaults.colors(
                                thumbColor = appColors.primary,
                                activeTrackColor = appColors.primary,
                                inactiveTrackColor = appColors.surfaceBorder.copy(alpha = 0.7f)
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("BASS MODE", color = appColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Natural Bass", "Pure Bass", "Sub-Woofer").forEach { mode ->
                                val isSelected = dspState.bassMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        ObsidianDspEngine.updateState { it.copy(bassMode = mode) }
                                    },
                                    label = { Text(mode, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = appColors.primary,
                                        selectedLabelColor = onPrimaryColor,
                                        containerColor = appColors.surfaceElevated,
                                        labelColor = appColors.textSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Virtualizer & Haas Stereo Expander Card
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
            border = BorderStroke(1.dp, if (dspState.isFieldSurroundEnabled) appColors.secondary.copy(alpha = 0.5f) else appColors.surfaceBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SurroundSound,
                            contentDescription = null,
                            tint = appColors.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VIRTUALIZER & HAAS SURROUND",
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = dspState.isFieldSurroundEnabled,
                        onCheckedChange = { isEnabled ->
                            ObsidianDspEngine.updateState { it.copy(isFieldSurroundEnabled = isEnabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.surfaceElevated,
                            checkedTrackColor = appColors.secondary
                        )
                    )
                }

                AnimatedVisibility(visible = dspState.isFieldSurroundEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("SURROUND SOUNDSTAGE WIDTH", color = appColors.textSecondary, fontSize = 12.sp)
                            Text("%.0f %%".format(dspState.surroundWidthPercent), color = appColors.secondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = dspState.surroundWidthPercent,
                            onValueChange = { width ->
                                ObsidianDspEngine.updateState { it.copy(surroundWidthPercent = width) }
                            },
                            valueRange = 50f..200f,
                            colors = SliderDefaults.colors(
                                thumbColor = appColors.secondary,
                                activeTrackColor = appColors.secondary,
                                inactiveTrackColor = appColors.surfaceBorder.copy(alpha = 0.7f)
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("HAAS DIFFERENTIAL DELAY", color = appColors.textSecondary, fontSize = 12.sp)
                            Text("%.1f ms".format(dspState.haasDelayMs), color = appColors.secondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = dspState.haasDelayMs,
                            onValueChange = { delay ->
                                ObsidianDspEngine.updateState { it.copy(haasDelayMs = delay) }
                            },
                            valueRange = 5f..35f,
                            colors = SliderDefaults.colors(
                                thumbColor = appColors.secondary,
                                activeTrackColor = appColors.secondary,
                                inactiveTrackColor = appColors.surfaceBorder.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        }

        // Clarity Transient Enhancer Card
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
            border = BorderStroke(1.dp, appColors.surfaceBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OBSIDIAN TRANSIENT CLARITY (X-HiFi)",
                        color = appColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Switch(
                        checked = dspState.isClarityEnabled,
                        onCheckedChange = { isEnabled ->
                            ObsidianDspEngine.updateState { it.copy(isClarityEnabled = isEnabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.surfaceElevated,
                            checkedTrackColor = appColors.primary
                        )
                    )
                }

                AnimatedVisibility(visible = dspState.isClarityEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("CLARITY BOOST", color = appColors.textSecondary, fontSize = 12.sp)
                            Text("+%.1f dB".format(dspState.clarityGainDb), color = appColors.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = dspState.clarityGainDb,
                            onValueChange = { gain ->
                                ObsidianDspEngine.updateState { it.copy(clarityGainDb = gain) }
                            },
                            valueRange = 0f..14f,
                            colors = SliderDefaults.colors(
                                thumbColor = appColors.primary,
                                activeTrackColor = appColors.primary,
                                inactiveTrackColor = appColors.surfaceBorder.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TubeAndReverbTab(
    dspState: ObsidianDspState,
    appColors: com.anuj.obsidian.ui.theme.AppThemePalette
) {
    val context = LocalContext.current
    val isLightPrimary = appColors.primary.red * 0.299f + appColors.primary.green * 0.587f + appColors.primary.blue * 0.114f > 0.5f
    val onPrimaryColor = if (isLightPrimary) Color.Black else Color.White

    val customIrLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val success = ObsidianDspEngine.pcmDspProcessor.convolverEngine.loadImpulseFromUri(context, uri)
            if (success) {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Custom Impulse.wav"
                ObsidianDspEngine.updateState {
                    it.copy(
                        isConvolverEnabled = true,
                        customImpulseFilePath = uri.toString(),
                        customImpulseFileName = fileName,
                        selectedImpulseId = "custom_file"
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tube Saturation & Analog Warmth Card
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
            border = BorderStroke(1.dp, if (dspState.isTubeEnabled) appColors.primary.copy(alpha = 0.5f) else appColors.surfaceBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = appColors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TUBE SATURATION & ANALOG WARMTH",
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = dspState.isTubeEnabled,
                        onCheckedChange = { isEnabled ->
                            ObsidianDspEngine.updateState { it.copy(isTubeEnabled = isEnabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.surfaceElevated,
                            checkedTrackColor = appColors.primary
                        )
                    )
                }

                AnimatedVisibility(visible = dspState.isTubeEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("VACUUM TUBE MODEL", color = appColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("6SN7 Triode Master", "12AX7 Vintage Warmth", "300B Audiophile Pure").forEach { tube ->
                                val isSelected = dspState.tubeType == tube
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        ObsidianDspEngine.updateState { it.copy(tubeType = tube) }
                                    },
                                    label = { Text(tube, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = appColors.primary,
                                        selectedLabelColor = onPrimaryColor,
                                        containerColor = appColors.surfaceElevated,
                                        labelColor = appColors.textSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("SATURATION DRIVE", color = appColors.textSecondary, fontSize = 12.sp)
                            Text("%.0f %% Drive".format(dspState.tubeDrivePercent), color = appColors.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = dspState.tubeDrivePercent,
                            onValueChange = { drive ->
                                ObsidianDspEngine.updateState { it.copy(tubeDrivePercent = drive) }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = appColors.primary,
                                activeTrackColor = appColors.primary,
                                inactiveTrackColor = appColors.surfaceBorder.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        }

        // Convolver Impulse Response Reverb Card
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
            border = BorderStroke(1.dp, if (dspState.isConvolverEnabled) appColors.primary.copy(alpha = 0.5f) else appColors.surfaceBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = appColors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONVOLUTION REVERB (IMPULSE RESPONSE)",
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = dspState.isConvolverEnabled,
                        onCheckedChange = { isEnabled ->
                            ObsidianDspEngine.updateState { it.copy(isConvolverEnabled = isEnabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.surfaceElevated,
                            checkedTrackColor = appColors.primary
                        )
                    )
                }

                AnimatedVisibility(visible = dspState.isConvolverEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("IMPULSE RESPONSE PRESETS", color = appColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            ElevatedButton(
                                onClick = { customIrLauncher.launch("audio/*") },
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = appColors.primary,
                                    contentColor = onPrimaryColor
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Load Custom .WAV", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        if (dspState.customImpulseFileName.isNotBlank()) {
                            Surface(
                                color = appColors.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, appColors.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("CUSTOM USER IMPULSE", color = appColors.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(dspState.customImpulseFileName, color = appColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("USER .WAV", color = appColors.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OBSIDIAN_IMPULSE_RESPONSES.forEach { ir ->
                            val isSelected = dspState.selectedImpulseId == ir.id && dspState.customImpulseFileName.isBlank()
                            Surface(
                                color = if (isSelected) appColors.primary.copy(alpha = 0.15f) else appColors.surfaceElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) appColors.primary else appColors.surfaceBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        ObsidianDspEngine.updateState {
                                            it.copy(
                                                selectedImpulseId = ir.id,
                                                customImpulseFileName = ""
                                            )
                                        }
                                        ObsidianDspEngine.pcmDspProcessor.convolverEngine.generateSyntheticImpulse(ir.roomDecayMs)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ir.name, color = if (isSelected) appColors.primary else appColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(ir.description, color = appColors.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${ir.roomDecayMs} ms", color = appColors.secondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("WET / DRY REVERB MIX", color = appColors.textSecondary, fontSize = 12.sp)
                            Text("%.0f %% Wet".format(dspState.convolverWetPercent), color = appColors.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = dspState.convolverWetPercent,
                            onValueChange = { wet ->
                                ObsidianDspEngine.updateState { it.copy(convolverWetPercent = wet) }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = appColors.primary,
                                activeTrackColor = appColors.primary,
                                inactiveTrackColor = appColors.surfaceBorder.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        }

        // Experimental Features (DeepField Sound Effects - Vivo / OEM Audio Engine)
        val deepFieldLaunchIntent = remember(context) {
            val pm = context.packageManager
            val candidates = listOf(
                android.content.Intent("vivo.intent.action.AUDIO_EFFECT"),
                android.content.Intent("com.vivo.audiotuning.ACTION_AUDIO_EFFECT"),
                android.content.Intent().setClassName("com.vivo.audiotuning", "com.vivo.audiotuning.AudioTuningActivity"),
                android.content.Intent().setClassName("com.bbk.audiotuning", "com.bbk.audiotuning.AudioTuningActivity")
            )
            candidates.firstOrNull { intent ->
                try {
                    pm.resolveActivity(intent, 0) != null
                } catch (_: Throwable) { false }
            }
        }

        if (deepFieldLaunchIntent != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
                border = BorderStroke(1.dp, appColors.primary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            deepFieldLaunchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(deepFieldLaunchIntent)
                        } catch (e: Exception) {
                            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "DeepFieldLaunch")
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SpatialAudioOff,
                                contentDescription = null,
                                tint = appColors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "DEEPFIELD SOUND EFFECTS",
                                        color = appColors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = appColors.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Audio Effect",
                                            color = appColors.primary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Fine-tuned by vivo acoustic teams • 360° Surround, Mega Bass & Clear Voice",
                                    color = appColors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = appColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            val systemAudioFxIntent = remember(context) {
                val pm = context.packageManager
                val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                }
                try {
                    if (pm.resolveActivity(intent, 0) != null) intent else null
                } catch (_: Throwable) { null }
            }

            if (systemAudioFxIntent != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
                    border = BorderStroke(1.dp, appColors.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                systemAudioFxIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(systemAudioFxIntent)
                            } catch (e: Exception) {
                                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "SystemAudioFxLaunch")
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SpatialAudioOff,
                                    contentDescription = null,
                                    tint = appColors.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "SYSTEM AUDIO EFFECTS",
                                            color = appColors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = appColors.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "OEM Engine",
                                                color = appColors.primary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Dolby Atmos / Dirac / System Equalizer Audio Enhancement Panel",
                                        color = appColors.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = appColors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Obsidian-DDC Headphone Acoustic Correction Card
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceCard),
            border = BorderStroke(1.dp, if (dspState.isDdcEnabled) appColors.secondary.copy(alpha = 0.5f) else appColors.surfaceBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = appColors.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OBSIDIAN-DDC HEADPHONE PROFILES",
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = dspState.isDdcEnabled,
                        onCheckedChange = { isEnabled ->
                            ObsidianDspEngine.updateState { it.copy(isDdcEnabled = isEnabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.surfaceElevated,
                            checkedTrackColor = appColors.secondary
                        )
                    )
                }

                AnimatedVisibility(visible = dspState.isDdcEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        OBSIDIAN_DDC_PROFILES.forEach { ddc ->
                            val isSelected = dspState.selectedDdcId == ddc.id
                            Surface(
                                color = if (isSelected) appColors.secondary.copy(alpha = 0.15f) else appColors.surfaceElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) appColors.secondary else appColors.surfaceBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        ObsidianDspEngine.updateState { it.copy(selectedDdcId = ddc.id) }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ddc.name, color = if (isSelected) appColors.secondary else appColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(ddc.description, color = appColors.textMuted, fontSize = 10.sp, maxLines = 1)
                                    }
                                    Text(ddc.brand, color = appColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
