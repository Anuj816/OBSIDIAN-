package com.anuj.obsidian.ui.visualizer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Full MilkDrop 3 Double Preset Studio & Mixer Modal Bottom Sheet.
 * Enables combining two .milk presets with 11 blend patterns, real-time procedural plasma,
 * beat reactive triggers, palette modulation, and exporting to standard .milk2 files.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Milkdrop3MixerSheet(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val activeDoublePreset by Milkdrop3Controller.activeDoublePreset.collectAsStateWithLifecycle()
    val currentPattern by Milkdrop3Controller.currentPattern.collectAsStateWithLifecycle()
    val liveRatio by Milkdrop3Controller.liveBlendRatio.collectAsStateWithLifecycle()
    val blendDuration by Milkdrop3Controller.blendDurationSeconds.collectAsStateWithLifecycle()
    val plasmaSeed by Milkdrop3Controller.plasmaSeed.collectAsStateWithLifecycle()
    val isFlipped by Milkdrop3Controller.isFlipped.collectAsStateWithLifecycle()
    val isLocked by Milkdrop3Controller.isDirectionLocked.collectAsStateWithLifecycle()
    val autoBeatSwitch by Milkdrop3Controller.autoBeatSwitch.collectAsStateWithLifecycle()
    val colorMod by Milkdrop3Controller.colorModulation.collectAsStateWithLifecycle()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportName by remember { mutableStateOf(activeDoublePreset?.name ?: "Custom_Double_Mix") }
    var selectedPresetAId by remember { mutableStateOf(activeDoublePreset?.presetA?.id ?: 2001) }
    var selectedPresetBId by remember { mutableStateOf(activeDoublePreset?.presetB?.id ?: 2002) }

    val allSinglePresets = remember {
        MilkdropPresetLibrary.getAllPresets()
    }

    val curatedDoublePresets = remember {
        Milkdrop3Controller.getCuratedDoublePresets()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF0D121D),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF2A85))),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MilkDrop 3 Studio",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Double Preset Mixer & .milk2 Creator",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF88A0C0)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1B2436), thickness = 1.dp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 20.dp)
            ) {
                // Curated Presets Carousel
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "CURATED MILKDROP 3 COMBINATIONS",
                        color = Color(0xFF88A0C0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(curatedDoublePresets) { combo ->
                            val isSelected = activeDoublePreset?.id == combo.id
                            Surface(
                                onClick = {
                                    Milkdrop3Controller.setActiveDoublePreset(combo)
                                    selectedPresetAId = combo.presetA.id
                                    selectedPresetBId = combo.presetB.id
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF1B2E48) else Color(0xFF131A29),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF00E5FF) else Color(0xFF233048)
                                ),
                                modifier = Modifier.width(220.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = combo.blendPattern.displayName,
                                                color = Color(0xFF00E5FF),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Active",
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = combo.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = combo.author,
                                        color = Color(0xFF88A0C0),
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Dual Preset Selector (Layer A & Layer B)
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "LAYER A & LAYER B PRESETS",
                        color = Color(0xFF88A0C0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Preset A Card
                        val presetA = allSinglePresets.firstOrNull { it.id == selectedPresetAId } ?: allSinglePresets.first()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF131A29),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "PRESET A (PRIMARY)",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = presetA.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = presetA.author,
                                    color = Color(0xFF88A0C0),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Swap Button
                        IconButton(
                            onClick = {
                                val temp = selectedPresetAId
                                selectedPresetAId = selectedPresetBId
                                selectedPresetBId = temp
                                Milkdrop3Controller.flipPresetOrder()
                            },
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(36.dp)
                                .background(Color(0xFF1E283C), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Swap Layers",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Preset B Card
                        val presetB = allSinglePresets.firstOrNull { it.id == selectedPresetBId } ?: allSinglePresets[1 % allSinglePresets.size]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF131A29),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF2A85).copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "PRESET B (SECONDARY)",
                                    color = Color(0xFFFF2A85),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = presetB.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = presetB.author,
                                    color = Color(0xFF88A0C0),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset A Selector Chips
                    Text(text = "Change Layer A:", color = Color(0xFF88A0C0), fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allSinglePresets) { p ->
                            FilterChip(
                                selected = p.id == selectedPresetAId,
                                onClick = {
                                    selectedPresetAId = p.id
                                    updateActivePreset(p, allSinglePresets.firstOrNull { it.id == selectedPresetBId } ?: p)
                                },
                                label = { Text(p.name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF00E5FF)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Preset B Selector Chips
                    Text(text = "Change Layer B:", color = Color(0xFF88A0C0), fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allSinglePresets) { p ->
                            FilterChip(
                                selected = p.id == selectedPresetBId,
                                onClick = {
                                    selectedPresetBId = p.id
                                    updateActivePreset(allSinglePresets.firstOrNull { it.id == selectedPresetAId } ?: p, p)
                                },
                                label = { Text(p.name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF2A85).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFFFF2A85)
                                )
                            )
                        }
                    }
                }

                // Blend Pattern Selector
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "BLEND PATTERN MASK",
                        color = Color(0xFF88A0C0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Milkdrop3BlendPattern.values().forEach { pattern ->
                            val isSelected = currentPattern == pattern
                            Surface(
                                onClick = { Milkdrop3Controller.setBlendPattern(pattern) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF1E2E44) else Color(0xFF131A29),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF00E5FF) else Color(0xFF233048)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pattern.displayName,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFC0D0E5),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Sliders & Toggles Section
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "DYNAMIC BLEND CONTROLS",
                        color = Color(0xFF88A0C0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Blend Ratio Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Blend Ratio (A ↔ B)", color = Color.White, fontSize = 12.sp)
                        Text(text = "${(liveRatio * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = liveRatio,
                        onValueChange = { Milkdrop3Controller.setLiveBlendRatio(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF1E283C)
                        )
                    )

                    // Blend Duration Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Morph Cycle Duration", color = Color.White, fontSize = 12.sp)
                        Text(text = "${blendDuration.toInt()}s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = blendDuration,
                        onValueChange = { Milkdrop3Controller.setBlendDuration(it) },
                        valueRange = 1f..30f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF1E283C)
                        )
                    )

                    // Plasma Seed Controller
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Procedural Plasma Seed", color = Color.White, fontSize = 12.sp)
                            Text(text = "Seed: ${String.format("%.2f", plasmaSeed)}", color = Color(0xFF88A0C0), fontSize = 10.sp)
                        }

                        Button(
                            onClick = { Milkdrop3Controller.randomizePlasmaSeed() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283C)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Reroll Seed", color = Color.White, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Switches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Beat Auto-Switch Drop Surge", color = Color.White, fontSize = 12.sp)
                            Text(text = "Surges Layer B on intense audio transients", color = Color(0xFF88A0C0), fontSize = 10.sp)
                        }
                        Switch(
                            checked = autoBeatSwitch,
                            onCheckedChange = { Milkdrop3Controller.toggleBeatAutoSwitch() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF005566))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Lock Blend Direction", color = Color.White, fontSize = 12.sp)
                            Text(text = "Holds current blend ratio static without oscillation", color = Color(0xFF88A0C0), fontSize = 10.sp)
                        }
                        Switch(
                            checked = isLocked,
                            onCheckedChange = { Milkdrop3Controller.toggleDirectionLock() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF005566))
                        )
                    }
                }

                // Palette Modulation
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "PALETTE & COLOR MODULATION",
                        color = Color(0xFF88A0C0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Milkdrop3ColorModulation.values()) { mode ->
                            val isSelected = colorMod == mode
                            Surface(
                                onClick = { Milkdrop3Controller.setColorModulation(mode) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF1E2E44) else Color(0xFF131A29),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF233048))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ColorLens,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFF88A0C0),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = mode.displayName,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFC0D0E5),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Export to .milk2 Section
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF131A29),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3A55)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "EXPORT TO MILKDROP 3 (.MILK2)",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Save this dual preset combination as an official .milk2 file in your device preset storage.",
                                color = Color(0xFF88A0C0),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = exportName,
                                onValueChange = { exportName = it },
                                label = { Text("Preset Title") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color(0xFF2A3A55),
                                    focusedLabelColor = Color(0xFF00E5FF),
                                    unfocusedLabelColor = Color(0xFF88A0C0),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val savedFile = Milkdrop3Controller.exportCurrentAsMilk2(
                                        context = context,
                                        name = exportName.ifBlank { "Obsidian_Double_Preset" }
                                    )
                                    if (savedFile != null) {
                                        onDismissRequest()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5FF),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save .milk2 Preset",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun updateActivePreset(presetA: MilkdropPreset, presetB: MilkdropPreset) {
    val combo = Milkdrop3DoublePreset(
        id = -9999,
        name = "${presetA.name} & ${presetB.name}",
        author = "${presetA.author} x ${presetB.author}",
        description = "Custom dynamic double mix",
        presetA = presetA,
        presetB = presetB,
        blendPattern = Milkdrop3Controller.currentPattern.value,
        blendRatio = Milkdrop3Controller.liveBlendRatio.value,
        blendDurationSeconds = Milkdrop3Controller.blendDurationSeconds.value,
        plasmaSeed = Milkdrop3Controller.plasmaSeed.value,
        isDirectionLocked = Milkdrop3Controller.isDirectionLocked.value,
        isFlipped = Milkdrop3Controller.isFlipped.value,
        autoBeatSwitch = Milkdrop3Controller.autoBeatSwitch.value
    )
    Milkdrop3Controller.setActiveDoublePreset(combo)
}
