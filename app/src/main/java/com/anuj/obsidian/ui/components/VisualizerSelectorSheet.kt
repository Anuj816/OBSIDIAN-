package com.anuj.obsidian.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.anuj.obsidian.ui.visualizer.ProjectMNative
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.visualizer.CustomMilkdropEditorSheet
import com.anuj.obsidian.ui.visualizer.CustomMilkdropManager
import com.anuj.obsidian.ui.visualizer.FreeVisualizerType
import com.anuj.obsidian.ui.visualizer.MilkdropCustomPresetData
import com.anuj.obsidian.ui.visualizer.MilkdropPresetLibrary
import com.anuj.obsidian.ui.visualizer.ParsedMilkdropPreset
import com.anuj.obsidian.ui.visualizer.VisualizerCategory
import com.anuj.obsidian.ui.visualizer.VisualizerLibrary
import com.anuj.obsidian.ui.visualizer.VisualizerPreset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizerSelectorSheet(
    selectedPresetId: Int,
    isBackgroundVisualizerEnabled: Boolean,
    visualizerAlpha: Float,
    visualizerCycleMode: String = "off",
    visualizerColorMode: String = "ORIGINAL",
    onSelectPreset: (VisualizerPreset) -> Unit,
    onToggleBackgroundVisualizer: (Boolean) -> Unit,
    onAlphaChanged: (Float) -> Unit,
    onCycleModeChanged: (String) -> Unit = {},
    onColorModeChanged: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Shaders", "Free", "MilkDrop", "Custom", "Settings")

    val settings by com.anuj.obsidian.ObsidianApplication.repository.settings.collectAsStateWithLifecycle()
    val initialCategory = remember(settings.visualizerStyleCategory) {
        when (settings.visualizerStyleCategory) {
            "MILKDROP" -> VisualizerCategory.OBSIDIAN_MILKDROP
            "BARS" -> VisualizerCategory.SPECTRUM_BARS
            "CIRCULAR" -> VisualizerCategory.CIRCULAR_RADIAL
            "WAVEFORM" -> VisualizerCategory.WAVEFORMS_CURVES
            "PARTICLES" -> VisualizerCategory.PARTICLES_CONSTELLATIONS
            else -> null
        }
    }
    var selectedCategory by remember { mutableStateOf<VisualizerCategory?>(initialCategory) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showLiveTuning by remember { mutableStateOf(false) }

    // Live Customizer State
    var customSensitivity by remember { mutableFloatStateOf(1.0f) }
    var customSpeed by remember { mutableFloatStateOf(1.0f) }
    var customBeatResponse by remember { mutableFloatStateOf(1.0f) }

    // Custom Milkdrop Subsystem State
    val customPresetsList by CustomMilkdropManager.customPresets.collectAsStateWithLifecycle()
    var showEditorSheet by remember { mutableStateOf(false) }
    var editingPresetData by remember { mutableStateOf<MilkdropCustomPresetData?>(null) }
    var milkdropFilterIndex by rememberSaveable { mutableIntStateOf(0) } // 0: All, 1: Built-in, 2: Custom Folder

    LaunchedEffect(Unit) {
        CustomMilkdropManager.initialize(context)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            coroutineScope.launch {
                var importedCount = 0
                for (uri in uris) {
                    val result = CustomMilkdropManager.importFromUri(context, uri)
                    if (result.isSuccess) importedCount++
                }
                Toast.makeText(context, "Imported $importedCount preset(s) into Milkdrop folder!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val livePresets by VisualizerLibrary.presetsStateFlow.collectAsStateWithLifecycle()
    val allPresets = if (livePresets.isNotEmpty()) livePresets else remember { VisualizerLibrary.presets }
    val categoryCounts = remember(allPresets) { allPresets.groupingBy { it.category }.eachCount() }
    val milkdropPresets = remember(allPresets) {
        val md = allPresets.filter { it.category == VisualizerCategory.OBSIDIAN_MILKDROP }
        if (md.isNotEmpty()) md else allPresets.take(16)
    }

    val filteredPresets = remember(allPresets, selectedCategory, searchQuery) {
        allPresets.filter { preset ->
            (selectedCategory == null || preset.category == selectedCategory) &&
            (searchQuery.isBlank() || preset.name.contains(searchQuery, ignoreCase = true) || preset.category.displayName.contains(searchQuery, ignoreCase = true))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.background,
        dragHandle = null,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = appColors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Obsidian Visualizer Studio",
                            color = appColors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${allPresets.size}+ Shaders & Free Visualizer Presets",
                        color = appColors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            try {
                                val randomPreset = VisualizerLibrary.generateRandomPreset()
                                Log.d("VisualizerSelector", "Shuffled to preset: ${randomPreset.name}")
                                onSelectPreset(randomPreset)
                            } catch (e: Throwable) {
                                Log.e("VisualizerSelector", "Shuffle button crash: ${e.message}", e)
                                ObsidianCrashLogger.logException(e, "VisualizerSelectorSheet.shuffle")
                                Toast.makeText(
                                    context,
                                    "Visualizer shuffle failed: ${e.message ?: "Unknown error"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Randomize",
                            tint = appColors.primary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = appColors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Navigation Tab Bar
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = appColors.background,
                contentColor = appColors.primary,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = appColors.primary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) appColors.primary else appColors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTabIndex) {
                0 -> { // Shaders Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Search Bar
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search GPU shaders...", fontSize = 12.sp, color = appColors.textMuted) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = appColors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = appColors.textMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = appColors.primary,
                                    unfocusedBorderColor = appColors.surfaceBorder,
                                    focusedContainerColor = appColors.surfaceCard,
                                    unfocusedContainerColor = appColors.surfaceCard,
                                    focusedTextColor = appColors.textPrimary,
                                    unfocusedTextColor = appColors.textPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            )
                        }

                        // Category Filter Chips
                        item {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategory == null,
                                        onClick = {
                                            selectedCategory = null
                                            com.anuj.obsidian.ObsidianApplication.repository.persistVisualizerStyleCategory("ALL")
                                        },
                                        label = { Text("All (${allPresets.size})", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = appColors.primary,
                                            selectedLabelColor = Color.Black,
                                            containerColor = appColors.surfaceCard,
                                            labelColor = appColors.textSecondary
                                        )
                                    )
                                }

                                items(VisualizerCategory.entries, key = { it.name }) { cat ->
                                    val count = categoryCounts[cat] ?: 0
                                    FilterChip(
                                        selected = selectedCategory == cat,
                                        onClick = {
                                            selectedCategory = cat
                                            val catCode = when (cat) {
                                                VisualizerCategory.OBSIDIAN_MILKDROP -> "MILKDROP"
                                                VisualizerCategory.SPECTRUM_BARS -> "BARS"
                                                VisualizerCategory.CIRCULAR_RADIAL -> "CIRCULAR"
                                                VisualizerCategory.WAVEFORMS_CURVES -> "WAVEFORM"
                                                VisualizerCategory.PARTICLES_CONSTELLATIONS -> "PARTICLES"
                                                else -> "ALL"
                                            }
                                            com.anuj.obsidian.ObsidianApplication.repository.persistVisualizerStyleCategory(catCode)
                                        },
                                        label = { Text("${cat.displayName} ($count)", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = appColors.primary,
                                            selectedLabelColor = Color.Black,
                                            containerColor = appColors.surfaceCard,
                                            labelColor = appColors.textSecondary
                                        )
                                    )
                                }
                            }
                        }

                        // Presets List
                        items(filteredPresets, key = { it.id }) { preset ->
                            val isSelected = preset.id == selectedPresetId

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPreset(preset) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) appColors.primary.copy(alpha = 0.15f) else appColors.surfaceCard
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) appColors.primary else appColors.surfaceBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(preset.primaryColor, preset.secondaryColor)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "#${preset.id} ${preset.name}",
                                            color = if (isSelected) appColors.primary else appColors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = preset.description,
                                            color = appColors.textSecondary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = appColors.surface,
                                        border = BorderStroke(1.dp, appColors.surfaceBorder)
                                    ) {
                                        Text(
                                            text = preset.category.displayName,
                                            color = appColors.textMuted,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> { // Free Visualizers Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(FreeVisualizerType.entries, key = { it.id }) { freeType ->
                            val isSelected = selectedPresetId == freeType.id
                            val preset = remember(freeType) { VisualizerLibrary.getPresetById(freeType.id) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPreset(preset) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) appColors.primary.copy(alpha = 0.15f) else appColors.surfaceCard
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) appColors.primary else appColors.surfaceBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(preset.primaryColor, preset.secondaryColor)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                                        } else {
                                            Icon(Icons.Default.Waves, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = freeType.displayName,
                                            color = if (isSelected) appColors.primary else appColors.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = preset.description,
                                            color = appColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = appColors.primary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "FREE",
                                            color = appColors.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> { // MilkDrop Tab
                    val milkFolder = remember(context) { CustomMilkdropManager.getMilkdropFolder(context) }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header & Custom Folder Controls
                        item {
                            Surface(
                                color = appColors.surfaceCard,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, appColors.surfaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Brush.linearGradient(listOf(appColors.primary, appColors.secondary))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                        }
                                        Column {
                                            Text("MilkDrop 2.0 Engine", fontWeight = FontWeight.Bold, color = appColors.textPrimary, fontSize = 15.sp)
                                            Text("Dynamic Equations • Warp Meshes • Custom .milk Files", color = appColors.textSecondary, fontSize = 11.5.sp)
                                        }
                                    }

                                    // Folder Directory Indicator Box
                                    Surface(
                                        color = appColors.background.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, appColors.surfaceBorder.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("PRESETS FOLDER", color = appColors.primary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                Text(milkFolder.absolutePath, color = appColors.textSecondary, fontSize = 11.sp, maxLines = 1)
                                            }
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Milkdrop Folder Path", milkFolder.absolutePath)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Folder path copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Path", tint = appColors.textSecondary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    // Action Buttons Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Import Button
                                        Button(
                                            onClick = {
                                                filePickerLauncher.launch(arrayOf("*/*"))
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = appColors.primary, contentColor = Color.Black),
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Import .milk", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Sync Folder Button
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val count = CustomMilkdropManager.scanAndSyncFolder(context)
                                                    Toast.makeText(context, "Folder synced! $count custom preset(s) active.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sync Folder", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Create New Preset Button
                                        OutlinedButton(
                                            onClick = {
                                                editingPresetData = null
                                                showEditorSheet = true
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("New Preset", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Filter Chips Row
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = milkdropFilterIndex == 0,
                                    onClick = { milkdropFilterIndex = 0 },
                                    label = { Text("All (${milkdropPresets.size + customPresetsList.size})", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = appColors.primary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                                FilterChip(
                                    selected = milkdropFilterIndex == 1,
                                    onClick = { milkdropFilterIndex = 1 },
                                    label = { Text("Built-in (${milkdropPresets.size})", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = appColors.primary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                                FilterChip(
                                    selected = milkdropFilterIndex == 2,
                                    onClick = { milkdropFilterIndex = 2 },
                                    label = { Text("Custom Folder (${customPresetsList.size})", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = appColors.primary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        // Custom Presets List
                        if (milkdropFilterIndex == 0 || milkdropFilterIndex == 2) {
                            items(customPresetsList, key = { "custom_${it.id}" }) { customItem ->
                                val isSelected = customItem.id == selectedPresetId
                                var showMenu by remember { mutableStateOf(false) }
                                val p = customItem.toPreset()

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val visualizerPreset = VisualizerPreset(
                                                id = customItem.id,
                                                name = customItem.name,
                                                category = VisualizerCategory.OBSIDIAN_MILKDROP,
                                                description = customItem.description,
                                                primaryColor = p.primaryColor,
                                                secondaryColor = p.secondaryColor
                                            )
                                            onSelectPreset(visualizerPreset)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) appColors.primary.copy(alpha = 0.15f) else appColors.surfaceCard
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) appColors.primary else appColors.surfaceBorder)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Brush.linearGradient(listOf(p.primaryColor, p.secondaryColor))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                                            } else {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(customItem.name, fontWeight = FontWeight.Bold, color = appColors.textPrimary, fontSize = 14.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = appColors.secondary.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "CUSTOM",
                                                        color = appColors.secondary,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            val hasFileOrMilk = customItem.sourceFileName != null || !customItem.rawMilkContent.isNullOrBlank()
                                            val engineBadge = if (ProjectMNative.isLoaded && hasFileOrMilk) "MilkDrop · projectM" else if (!ProjectMNative.isLoaded) "projectM unavailable" else "Built-in demo"
                                            Text(
                                                "by ${customItem.author} • $engineBadge",
                                                color = appColors.textSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                        if (isSelected) {
                                            Surface(
                                                color = appColors.primary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    "ACTIVE",
                                                    color = appColors.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Box {
                                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Preset Options", tint = appColors.textSecondary)
                                            }
                                            DropdownMenu(
                                                expanded = showMenu,
                                                onDismissRequest = { showMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit in Studio") },
                                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                    onClick = {
                                                        showMenu = false
                                                        editingPresetData = customItem
                                                        showEditorSheet = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Duplicate") },
                                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                                    onClick = {
                                                        showMenu = false
                                                        coroutineScope.launch {
                                                            CustomMilkdropManager.duplicatePreset(context, customItem.id)
                                                            Toast.makeText(context, "Duplicated preset!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete", color = Color.Red) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                                                    onClick = {
                                                        showMenu = false
                                                        coroutineScope.launch {
                                                            CustomMilkdropManager.deleteCustomPreset(context, customItem.id)
                                                            Toast.makeText(context, "Deleted preset", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Built-in Presets List
                        if (milkdropFilterIndex == 0 || milkdropFilterIndex == 1) {
                            items(milkdropPresets, key = { "builtin_${it.id}" }) { preset ->
                                val isSelected = preset.id == selectedPresetId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectPreset(preset) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) appColors.primary.copy(alpha = 0.15f) else appColors.surfaceCard
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) appColors.primary else appColors.surfaceBorder)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Brush.linearGradient(listOf(preset.primaryColor, preset.secondaryColor))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                                            } else {
                                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(preset.name, fontWeight = FontWeight.Bold, color = appColors.textPrimary, fontSize = 14.sp)
                                            Text("Obsidian GPU demo", color = appColors.textSecondary, fontSize = 11.5.sp)
                                        }
                                        if (isSelected) {
                                            Surface(
                                                color = appColors.primary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    "ACTIVE",
                                                    color = appColors.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val copy = CustomMilkdropManager.duplicatePreset(context, preset.id)
                                                    if (copy != null) {
                                                        Toast.makeText(context, "Duplicated to custom folder!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Preset", tint = appColors.textSecondary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> { // Custom MilkDrop Browser Tab
                    val loaderService = remember { com.anuj.obsidian.ui.visualizer.CustomPresetLoaderService.getInstance(context) }
                    LaunchedEffect(Unit) {
                        loaderService.initialize()
                    }
                    com.anuj.obsidian.ui.visualizer.CustomMilkdropBrowserSheet(
                        loaderService = loaderService,
                        onPresetSelected = { parsed ->
                            // Ensure preset is registered with VisualizerLibrary
                            VisualizerLibrary.registerCustomPresets(listOf(parsed))
                            val resolvedPreset = VisualizerLibrary.getPresetById(parsed.preset.id)
                            val visualizerPreset = if (resolvedPreset.id == parsed.preset.id) {
                                resolvedPreset
                            } else {
                                VisualizerPreset(
                                    id = parsed.preset.id,
                                    name = parsed.name,
                                    category = VisualizerCategory.OBSIDIAN_MILKDROP,
                                    description = if (parsed.description.isNotEmpty()) "${parsed.description} by ${parsed.author}" else "by ${parsed.author}",
                                    primaryColor = parsed.preset.primaryColor,
                                    secondaryColor = parsed.preset.secondaryColor,
                                    accentColor = parsed.preset.accentColor
                                )
                            }
                            
                            android.util.Log.d("VisualizerSelectorSheet", "User selected custom preset: ${parsed.name} (ID: ${parsed.preset.id})")
                            onSelectPreset(visualizerPreset)
                            Toast.makeText(context, "Activated preset: ${parsed.name}", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { selectedTabIndex = 2 },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                4 -> { // Settings Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Visualizer Fade Controls (PowerRamp-style)
                        item {
                            val playerManager = com.anuj.obsidian.ObsidianApplication.playerManager
                            val fadeController = remember { playerManager.getVisualizerFadeController() }
                            var opacity by remember { mutableFloatStateOf(fadeController.getFadeOpacity()) }
                            var fadeMode by remember { mutableStateOf(fadeController.getFadeMode()) }

                            com.anuj.obsidian.ui.visualizer.VisualizerFadeControls(
                                currentFadeOpacity = opacity,
                                onFadeOpacityChanged = { newOpacity ->
                                    opacity = newOpacity
                                    playerManager.setVisualizerFadeOpacity(newOpacity)
                                },
                                currentFadeMode = fadeMode,
                                onFadeModeChanged = { newMode ->
                                    fadeMode = newMode
                                    playerManager.setVisualizerFadeMode(newMode)
                                }
                            )
                        }

                        // Auto-Cycle Preset Scheduler
                        item {
                            Surface(
                                color = appColors.surfaceCard,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, appColors.surfaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(16.dp))
                                        Text("Auto-Cycle Visualizer Animations", color = appColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    val cycleOptions = listOf(
                                        "off" to "Off",
                                        "seq_10" to "Seq 10s",
                                        "seq_20" to "Seq 20s",
                                        "seq_30" to "Seq 30s",
                                        "seq_60" to "Seq 60s",
                                        "shuf_10" to "Shuffle 10s",
                                        "shuf_20" to "Shuffle 20s",
                                        "shuf_30" to "Shuffle 30s",
                                        "energy_match" to "Energy Match",
                                        "random_wild" to "Wild Dynamic"
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(cycleOptions) { (key, label) ->
                                            val isSelected = visualizerCycleMode == key
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onCycleModeChanged(key) },
                                                label = { Text(label, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = appColors.primary,
                                                    selectedLabelColor = Color.Black,
                                                    containerColor = appColors.surface,
                                                    labelColor = appColors.textSecondary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Color Scheme Selector
                        item {
                            Surface(
                                color = appColors.surfaceCard,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, appColors.surfaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(18.dp))
                                        Text("Visualizer Colors", color = appColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    val colorOptions = listOf(
                                        "ORIGINAL" to "Original (Bespoke)",
                                        "APP_THEME" to "App Theme",
                                        "CUSTOM" to "Custom"
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        colorOptions.forEach { (key, label) ->
                                            val isSelected = visualizerColorMode.equals(key, ignoreCase = true)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onColorModeChanged(key) },
                                                label = { Text(label, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = appColors.primary,
                                                    selectedLabelColor = Color.Black,
                                                    containerColor = appColors.surface,
                                                    labelColor = appColors.textSecondary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Background Visualizer Toggle & Intensity
                        item {
                            Surface(
                                color = appColors.surfaceCard,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, appColors.surfaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                imageVector = if (isBackgroundVisualizerEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                                tint = if (isBackgroundVisualizerEnabled) appColors.primary else appColors.textMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Full Screen Background Visualizer",
                                                color = appColors.textPrimary,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Button(
                                            onClick = { onToggleBackgroundVisualizer(!isBackgroundVisualizerEnabled) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isBackgroundVisualizerEnabled) appColors.primary else appColors.surface,
                                                contentColor = if (isBackgroundVisualizerEnabled) Color.Black else appColors.textSecondary
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isBackgroundVisualizerEnabled) "ON" else "OFF",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (isBackgroundVisualizerEnabled) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("Intensity:", color = appColors.textSecondary, fontSize = 11.5.sp)
                                            Slider(
                                                value = visualizerAlpha,
                                                onValueChange = onAlphaChanged,
                                                valueRange = 0.1f..1.0f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = appColors.primary,
                                                    activeTrackColor = appColors.primary,
                                                    inactiveTrackColor = appColors.surfaceBorder
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${(visualizerAlpha * 100).toInt()}%",
                                                color = appColors.primary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Live Customizer Toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showLiveTuning = !showLiveTuning },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = null,
                                                tint = appColors.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Live Engine Tuning & Response",
                                                color = appColors.textPrimary,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Text(
                                            text = if (showLiveTuning) "Hide ▲" else "Customize ▼",
                                            color = appColors.primary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = showLiveTuning,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Sensitivity", fontSize = 11.sp, color = appColors.textSecondary, modifier = Modifier.width(75.dp))
                                                Slider(
                                                    value = customSensitivity,
                                                    onValueChange = {
                                                        customSensitivity = it
                                                        val cur = VisualizerLibrary.getPresetById(selectedPresetId)
                                                        onSelectPreset(cur.copy(sensitivity = it))
                                                    },
                                                    valueRange = 0.5f..2.0f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text("${String.format(java.util.Locale.US, "%.1f", customSensitivity)}x", fontSize = 11.sp, color = appColors.primary, fontWeight = FontWeight.Bold)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Dynamics", fontSize = 11.sp, color = appColors.textSecondary, modifier = Modifier.width(75.dp))
                                                Slider(
                                                    value = customSpeed,
                                                    onValueChange = {
                                                        customSpeed = it
                                                        val cur = VisualizerLibrary.getPresetById(selectedPresetId)
                                                        onSelectPreset(cur.copy(motionSpeed = it))
                                                    },
                                                    valueRange = 0.4f..2.5f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text("${String.format(java.util.Locale.US, "%.1f", customSpeed)}x", fontSize = 11.sp, color = appColors.primary, fontWeight = FontWeight.Bold)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Bass Beat", fontSize = 11.sp, color = appColors.textSecondary, modifier = Modifier.width(75.dp))
                                                Slider(
                                                    value = customBeatResponse,
                                                    onValueChange = {
                                                        customBeatResponse = it
                                                        val cur = VisualizerLibrary.getPresetById(selectedPresetId)
                                                        onSelectPreset(cur.copy(beatResponse = it))
                                                    },
                                                    valueRange = 0.5f..2.5f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text("${String.format(java.util.Locale.US, "%.1f", customBeatResponse)}x", fontSize = 11.sp, color = appColors.primary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showEditorSheet) {
        CustomMilkdropEditorSheet(
            initialPreset = editingPresetData,
            onDismiss = { showEditorSheet = false },
            onSave = { savedData ->
                coroutineScope.launch {
                    val savedPreset = CustomMilkdropManager.saveCustomPreset(context, savedData)
                    val visualizerPreset = VisualizerPreset(
                        id = savedPreset.id,
                        name = savedPreset.name,
                        category = VisualizerCategory.OBSIDIAN_MILKDROP,
                        description = savedPreset.description,
                        primaryColor = savedPreset.primaryColor,
                        secondaryColor = savedPreset.secondaryColor
                    )
                    onSelectPreset(visualizerPreset)
                    Toast.makeText(context, "Preset '${savedPreset.name}' saved and activated!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
