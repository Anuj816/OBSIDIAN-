package com.anuj.obsidian.ui.visualizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.random.Random

/**
 * Interactive Real-Time Milkdrop Studio & Preset Creator Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomMilkdropEditorSheet(
    initialPreset: MilkdropCustomPresetData? = null,
    onDismiss: () -> Unit,
    onSave: (MilkdropCustomPresetData) -> Unit
) {
    val appColors = ObsidianTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initialPreset?.name ?: "Custom Milkdrop Preset") }
    var author by remember { mutableStateOf(initialPreset?.author ?: "You") }
    var description by remember { mutableStateOf(initialPreset?.description ?: "Handcrafted dynamic Milkdrop preset") }
    
    var zoomBase by remember { mutableFloatStateOf(initialPreset?.zoomBase ?: 1.02f) }
    var zoomBass by remember { mutableFloatStateOf(initialPreset?.zoomBassMultiplier ?: 0.06f) }
    var rotSpeed by remember { mutableFloatStateOf(initialPreset?.rotSpeed ?: 0.02f) }
    var rotMid by remember { mutableFloatStateOf(initialPreset?.rotMidMultiplier ?: 0.02f) }
    var warpAmount by remember { mutableFloatStateOf(initialPreset?.warpAmount ?: 0.45f) }
    var warpBass by remember { mutableFloatStateOf(initialPreset?.warpBassMultiplier ?: 0.5f) }
    var decay by remember { mutableFloatStateOf(initialPreset?.decay ?: 0.96f) }
    
    var waveMode by remember { mutableIntStateOf(initialPreset?.waveMode ?: 0) }
    var waveScale by remember { mutableFloatStateOf(initialPreset?.waveScale ?: 1.2f) }
    var waveThick by remember { mutableStateOf(initialPreset?.waveThick ?: true) }
    
    var shapeSides by remember { mutableIntStateOf(initialPreset?.shapeSides ?: 6) }
    var shapeRadius by remember { mutableFloatStateOf(initialPreset?.shapeRadius ?: 0.20f) }
    var shapeAudioReact by remember { mutableFloatStateOf(initialPreset?.shapeAudioReact ?: 0.14f) }

    var primaryColorHex by remember { mutableStateOf(initialPreset?.primaryColorHex ?: "#00E5FF") }
    var secondaryColorHex by remember { mutableStateOf(initialPreset?.secondaryColorHex ?: "#FF2A85") }
    var accentColorHex by remember { mutableStateOf(initialPreset?.accentColorHex ?: "#FFD700") }

    // Live preview preset
    val livePreset = remember(
        name, author, description, primaryColorHex, secondaryColorHex, accentColorHex,
        decay, waveMode, waveScale, waveThick, zoomBase, zoomBass, rotSpeed, rotMid,
        warpAmount, warpBass, shapeSides, shapeRadius, shapeAudioReact
    ) {
        MilkdropCustomPresetData(
            id = initialPreset?.id ?: 9999,
            name = name,
            author = author,
            description = description,
            primaryColorHex = primaryColorHex,
            secondaryColorHex = secondaryColorHex,
            accentColorHex = accentColorHex,
            decay = decay,
            waveMode = waveMode,
            waveScale = waveScale,
            waveThick = waveThick,
            zoomBase = zoomBase,
            zoomBassMultiplier = zoomBass,
            rotSpeed = rotSpeed,
            rotMidMultiplier = rotMid,
            warpAmount = warpAmount,
            warpBassMultiplier = warpBass,
            shapeSides = shapeSides,
            shapeRadius = shapeRadius,
            shapeAudioReact = shapeAudioReact,
            isUserCreated = true
        )
    }

    val liveEnginePreset = remember(livePreset) { livePreset.toPreset() }
    val scratchPad = remember { VisualizerScratchPad() }
    val timeState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameTime ->
                val dt = ((frameTime - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                timeState.floatValue = (timeState.floatValue + dt * 16.6667f) % 10000f
                last = frameTime
            }
        }
    }

    val colorThemes = listOf(
        Triple("#00E5FF", "#FF2A85", "#FFD700") to "Cyberpunk Glow",
        Triple("#FF6D00", "#FFD700", "#FFFFFF") to "Solar Flare",
        Triple("#00E676", "#18FFFF", "#76FF03") to "Matrix Emerald",
        Triple("#B388FF", "#E040FB", "#00E5FF") to "Cosmic Nebula",
        Triple("#FF1744", "#FF5252", "#FFAB00") to "Supernova Red",
        Triple("#2979FF", "#00B0FF", "#E040FB") to "Deep Ocean Blue",
        Triple("#FFD700", "#651FFF", "#00E5FF") to "Sacred Golden",
        Triple("#E040FB", "#00E5FF", "#FFFF00") to "Acid Trippy"
    )

    fun randomize() {
        zoomBase = Random.nextFloat() * 0.12f + 0.98f
        zoomBass = Random.nextFloat() * 0.10f + 0.02f
        rotSpeed = (Random.nextFloat() - 0.5f) * 0.06f
        rotMid = Random.nextFloat() * 0.04f
        warpAmount = Random.nextFloat() * 1.2f + 0.1f
        warpBass = Random.nextFloat() * 0.8f + 0.2f
        decay = Random.nextFloat() * 0.05f + 0.93f
        waveMode = Random.nextInt(5)
        shapeSides = listOf(3, 4, 5, 6, 8, 12, 32).random()
        shapeRadius = Random.nextFloat() * 0.20f + 0.10f
        val theme = colorThemes.random().first
        primaryColorHex = theme.first
        secondaryColorHex = theme.second
        accentColorHex = theme.third
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.surfaceCard,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(appColors.primary, appColors.secondary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (initialPreset == null) "Milkdrop Studio Creator" else "Edit Milkdrop Preset",
                            color = appColors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time dynamic equation and warp synthesizer",
                            color = appColors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = appColors.textSecondary)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // Live Visualizer Preview Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(1.5.dp, Brush.horizontalGradient(listOf(appColors.primary.copy(alpha = 0.5f), appColors.secondary.copy(alpha = 0.5f))), RoundedCornerShape(16.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f }) {
                        renderMilkdropEngine(
                            preset = liveEnginePreset,
                            time = timeState.floatValue,
                            isPlaying = true,
                            alpha = 1.0f,
                            snapshot = HardwareVisualizerBuffer.currentSnapshot,
                            scratchPad = scratchPad
                        )
                    }

                    // Live Badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(appColors.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LIVE PREVIEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Randomize Button on Preview
                    OutlinedButton(
                        onClick = { randomize() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.65f),
                            contentColor = appColors.secondary
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mutate", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name & Description
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary,
                        unfocusedBorderColor = appColors.surfaceBorder,
                        focusedLabelColor = appColors.primary,
                        unfocusedLabelColor = appColors.textSecondary,
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Color Themes
                Text("Color Palette", color = appColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colorThemes) { (colors, themeName) ->
                        val isSelected = primaryColorHex.equals(colors.first, true) && secondaryColorHex.equals(colors.second, true)
                        val prim = try { Color(android.graphics.Color.parseColor(colors.first)) } catch (_: Throwable) { Color.Cyan }
                        val sec = try { Color(android.graphics.Color.parseColor(colors.second)) } catch (_: Throwable) { Color.Magenta }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) appColors.primary.copy(alpha = 0.2f) else appColors.surface)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) appColors.primary else appColors.surfaceBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    primaryColorHex = colors.first
                                    secondaryColorHex = colors.second
                                    accentColorHex = colors.third
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(prim, sec)))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(themeName, color = if (isSelected) appColors.textPrimary else appColors.textSecondary, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Waveform Modes
                Text("Custom Waveform Mode", color = appColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                val waveModes = listOf(
                    0 to "Oscilloscope Line",
                    1 to "Radial Audio Bloom",
                    2 to "Tri-Band Ribbon",
                    3 to "Lissajous Knot",
                    4 to "Particle Spray"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(waveModes) { (mode, label) ->
                        FilterChip(
                            selected = waveMode == mode,
                            onClick = { waveMode = mode },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = appColors.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = appColors.surface,
                                labelColor = appColors.textSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sliders Grid
                Text("Milkdrop Warp & Camera Parameters", color = appColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Warp Amount
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Spatial Mesh Warp", color = appColors.textSecondary, fontSize = 12.5.sp)
                        Text(String.format(Locale.US, "%.2f", warpAmount), color = appColors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = warpAmount,
                        onValueChange = { warpAmount = it },
                        valueRange = 0.0f..1.8f,
                        colors = SliderDefaults.colors(thumbColor = appColors.primary, activeTrackColor = appColors.primary)
                    )
                }

                // Warp Bass Multiplier
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bass Warp Pulse", color = appColors.textSecondary, fontSize = 12.5.sp)
                        Text(String.format(Locale.US, "%.2f", warpBass), color = appColors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = warpBass,
                        onValueChange = { warpBass = it },
                        valueRange = 0.0f..1.2f,
                        colors = SliderDefaults.colors(thumbColor = appColors.primary, activeTrackColor = appColors.primary)
                    )
                }

                // Zoom Base
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Camera Zoom Velocity", color = appColors.textSecondary, fontSize = 12.5.sp)
                        Text(String.format(Locale.US, "%.3f", zoomBase), color = appColors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = zoomBase,
                        onValueChange = { zoomBase = it },
                        valueRange = 0.92f..1.12f,
                        colors = SliderDefaults.colors(thumbColor = appColors.primary, activeTrackColor = appColors.primary)
                    )
                }

                // Rotational Speed
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Vortex Spin Speed", color = appColors.textSecondary, fontSize = 12.5.sp)
                        Text(String.format(Locale.US, "%.3f", rotSpeed), color = appColors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = rotSpeed,
                        onValueChange = { rotSpeed = it },
                        valueRange = -0.08f..0.08f,
                        colors = SliderDefaults.colors(thumbColor = appColors.primary, activeTrackColor = appColors.primary)
                    )
                }

                // Decay
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Motion Trail Persistence (Decay)", color = appColors.textSecondary, fontSize = 12.5.sp)
                        Text(String.format(Locale.US, "%.2f", decay), color = appColors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = decay,
                        onValueChange = { decay = it },
                        valueRange = 0.88f..0.99f,
                        colors = SliderDefaults.colors(thumbColor = appColors.primary, activeTrackColor = appColors.primary)
                    )
                }

                // Shape Sides & Geometry
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Central Geometric Halo Sides", color = appColors.textSecondary, fontSize = 12.5.sp)
                        Text("$shapeSides Sides", color = appColors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = shapeSides.toFloat(),
                        onValueChange = { shapeSides = it.toInt() },
                        valueRange = 3f..32f,
                        steps = 28,
                        colors = SliderDefaults.colors(thumbColor = appColors.primary, activeTrackColor = appColors.primary)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save Action Button
                Button(
                    onClick = {
                        onSave(livePreset)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Preset to Milkdrop Folder", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
