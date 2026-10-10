package com.anuj.obsidian.ui.visualizer

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlinx.coroutines.launch

@Composable
fun CustomMilkdropBrowserSheet(
    loaderService: CustomPresetLoaderService,
    onPresetSelected: (ParsedMilkdropPreset) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val customPresets by loaderService.customPresets.collectAsStateWithLifecycle()
    val discoveryInProgress by loaderService.discoveryInProgress.collectAsStateWithLifecycle()
    val discoveryError by loaderService.discoveryError.collectAsStateWithLifecycle()
    val discoveryProgress by loaderService.discoveryProgress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(0) } // 0: All, 1: .milk, 2: .milk2
    var showDirectoryInfo by rememberSaveable { mutableStateOf(false) }
    var selectedPresetForDebug by remember { mutableStateOf<ParsedMilkdropPreset?>(null) }
    var showDebugDialog by remember { mutableStateOf(false) }
    var showMixerStudio by remember { mutableStateOf(false) }
    
    // SAF Importer for single file
    val documentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Throwable) {}
                    val presetsDir = MilkdropFileStructure.getPresetsDirectory(context)
                    val internalDir = MilkdropFileStructure.getInternalPresetsDirectory(context)
                    val contentResolver = context.contentResolver
                    
                    val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex) else "imported_preset.milk"
                    } ?: "imported_preset.milk"
                    
                    val destFile = java.io.File(presetsDir, name)
                    val internalDestFile = java.io.File(internalDir, name)
                    contentResolver.openInputStream(uri)?.use { input ->
                        val text = input.reader(Charsets.UTF_8).readText()
                        destFile.writeText(text, Charsets.UTF_8)
                        internalDestFile.writeText(text, Charsets.UTF_8)
                        MilkdropFileStructure.getNativeCachePresetFile(context, name, text)
                    }
                    // Refresh presets
                    CustomMilkdropManager.initialize(context)
                    loaderService.loadCustomPresets()
                } catch (e: Exception) {
                    android.util.Log.e("MilkDropBrowser", "Error importing preset", e)
                }
            }
        }
    }

    // SAF Importer for folder
    val documentTreeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Throwable) {}
                    val presetsDir = MilkdropFileStructure.getPresetsDirectory(context)
                    val internalDir = MilkdropFileStructure.getInternalPresetsDirectory(context)
                    val contentResolver = context.contentResolver
                    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                        uri, android.provider.DocumentsContract.getTreeDocumentId(uri)
                    )
                    var importedCount = 0
                    
                    contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        
                        while (cursor.moveToNext()) {
                            if (idIndex == -1 || nameIndex == -1) continue
                            val docId = cursor.getString(idIndex)
                            val name = cursor.getString(nameIndex)
                            
                            if (name.lowercase().endsWith(".milk") || name.lowercase().endsWith(".milk2")) {
                                val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                                val destFile = java.io.File(presetsDir, name)
                                val internalDestFile = java.io.File(internalDir, name)
                                try {
                                    contentResolver.openInputStream(docUri)?.use { input ->
                                        val text = input.reader(Charsets.UTF_8).readText()
                                        destFile.writeText(text, Charsets.UTF_8)
                                        internalDestFile.writeText(text, Charsets.UTF_8)
                                        MilkdropFileStructure.getNativeCachePresetFile(context, name, text)
                                    }
                                    importedCount++
                                } catch (e: Exception) {
                                    android.util.Log.e("MilkDropBrowser", "Error reading $name", e)
                                }
                            }
                        }
                    }
                    android.util.Log.d("MilkDropBrowser", "Imported $importedCount presets from folder")
                    if (importedCount > 0) {
                        CustomMilkdropManager.initialize(context)
                        loaderService.loadCustomPresets()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MilkDropBrowser", "Error importing folder", e)
                }
            }
        }
    }
    
    val filteredPresets = remember(customPresets, searchQuery, selectedFilter) {
        val base = if (searchQuery.isEmpty()) {
            customPresets
        } else {
            loaderService.searchPresets(searchQuery)
        }
        when (selectedFilter) {
            1 -> base.filter { !it.isDoublePreset }
            2 -> base.filter { it.isDoublePreset }
            else -> base
        }
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.surface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Custom MilkDrop Presets",
                        style = MaterialTheme.typography.titleLarge,
                        color = appColors.textPrimary
                    )
                    Text(
                        "Supports MilkDrop 1-2 (.milk) & MilkDrop 3 (.milk2)",
                        fontSize = 11.sp,
                        color = Color(0xFF00E5FF)
                    )
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = appColors.textPrimary)
                }
            }
        }

        // MilkDrop 3 Studio Launch Banner
        item(key = "studio_banner") {
            Surface(
                onClick = { showMixerStudio = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF101726),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(Color(0xFF00E5FF), Color(0xFFFF2A85))
                                    ),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "MilkDrop 3 Double Studio",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Mix 2 presets with Plasma, Iris & Wipe masks",
                                fontSize = 10.sp,
                                color = Color(0xFF88A0C0)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "OPEN MIXER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }
        
        // Search bar
        item(key = "search_bar") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search presets...", color = appColors.textMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = appColors.textSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = appColors.surfaceCard,
                    unfocusedContainerColor = appColors.surfaceCard,
                    focusedBorderColor = appColors.primary,
                    unfocusedBorderColor = appColors.surfaceBorder,
                    focusedTextColor = appColors.textPrimary,
                    unfocusedTextColor = appColors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        // Filter Chips (All, .milk, .milk2)
        item(key = "filter_chips") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.FilterChip(
                    selected = selectedFilter == 0,
                    onClick = { selectedFilter = 0 },
                    label = { Text("All Presets", fontSize = 11.sp) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = appColors.primary.copy(alpha = 0.25f),
                        selectedLabelColor = appColors.primary
                    )
                )
                androidx.compose.material3.FilterChip(
                    selected = selectedFilter == 1,
                    onClick = { selectedFilter = 1 },
                    label = { Text(".milk (Single)", fontSize = 11.sp) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFF00E5FF)
                    )
                )
                androidx.compose.material3.FilterChip(
                    selected = selectedFilter == 2,
                    onClick = { selectedFilter = 2 },
                    label = { Text(".milk2 (Double)", fontSize = 11.sp) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF2A85).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFFF2A85)
                    )
                )
            }
        }

        // Import Preset Buttons
        item(key = "import_card") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = appColors.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Import MilkDrop Presets",
                        fontSize = 12.sp,
                        color = appColors.primary
                    )
                    Text(
                        "Select a .milk file or pick a folder to import multiple",
                        fontSize = 11.sp,
                        color = appColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { documentLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.primary,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Import .milk", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { documentTreeLauncher.launch(null) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = appColors.primary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, appColors.primary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Pick folder", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        
        // Error message
        if (discoveryError != null) {
            item(key = "discovery_error") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Red.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        discoveryError ?: "",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6B6B)
                    )
                }
            }
        }
        
        // Directory info toggle button
        item(key = "directory_toggle") {
            OutlinedButton(
                onClick = { showDirectoryInfo = !showDirectoryInfo },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = appColors.primary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showDirectoryInfo) "Hide Directory Paths" else "Show Directory Paths")
            }
        }
        
        // Directory info panel
        if (showDirectoryInfo) {
            item(key = "directory_panel") {
                DirectoryInfoPanel(loaderService = loaderService)
            }
        }
        
        // Loading indicator or presets list
        if (discoveryInProgress) {
            item(key = "loading_progress") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (discoveryProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { discoveryProgress },
                                color = appColors.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = appColors.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            if (discoveryProgress > 0f) {
                                "Scanning & parsing presets... ${(discoveryProgress * 100).toInt()}%"
                            } else {
                                "Scanning & parsing MilkDrop presets..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors.textPrimary
                        )
                        if (discoveryProgress > 0f) {
                            Spacer(modifier = Modifier.height(10.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { discoveryProgress },
                                modifier = Modifier.fillMaxWidth(0.7f),
                                color = appColors.primary,
                                trackColor = appColors.surfaceBorder
                            )
                        }
                    }
                }
            }
        } else {
            if (filteredPresets.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = appColors.textMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No custom presets found",
                                style = MaterialTheme.typography.titleMedium,
                                color = appColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Drop .milk files into Music/Obsidian/Milkdrop/presets folder.\nTap \"Show Directory Paths\" above to view exact file locations.",
                                textAlign = TextAlign.Center,
                                color = appColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                items(filteredPresets, key = { it.preset.id }) { preset ->
                    PresetItemCard(
                        preset = preset,
                        onSelect = {
                            Log.d("CustomMilkdropBrowserSheet", "Selected preset: ${preset.name} (ID: ${preset.preset.id})")
                            if (preset.isDoublePreset && preset.doublePreset != null) {
                                Milkdrop3Controller.setActiveDoublePreset(preset.doublePreset)
                            }
                            onPresetSelected(preset)
                            onDismiss()
                        },
                        onLongPress = {
                            selectedPresetForDebug = preset
                            showDebugDialog = true
                        }
                    )
                }
            }
        }
        
        // Refresh button
        item(key = "refresh_button") {
            Button(
                onClick = {
                    scope.launch {
                        loaderService.forceRefresh()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.primary,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh Presets")
            }
        }
    }

    // MilkDrop 3 Studio Bottom Sheet
    if (showMixerStudio) {
        Milkdrop3MixerSheet(
            onDismissRequest = {
                showMixerStudio = false
                scope.launch { loaderService.forceRefresh() }
            }
        )
    }

    // Debug Info Dialog
    if (showDebugDialog && selectedPresetForDebug != null) {
        val preset = selectedPresetForDebug!!
        val debugInfo = loaderService.getPresetDebugInfo(preset)
        
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text(preset.name, color = appColors.textPrimary) },
            text = {
                Column {
                    Text(
                        debugInfo,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                        color = appColors.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDebugDialog = false }) {
                    Text("Close", color = appColors.primary)
                }
            },
            containerColor = appColors.surfaceCard
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetItemCard(
    preset: ParsedMilkdropPreset,
    onSelect: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val appColors = ObsidianTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = appColors.surfaceCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = appColors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (preset.isDoublePreset) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFF2A85).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, Color(0xFFFF2A85), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "MILKDROP 3 (.MILK2)",
                            color = Color(0xFFFF2A85),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            ".MILK",
                            color = Color(0xFF00E5FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Text(
                "By ${preset.author}",
                style = MaterialTheme.typography.labelMedium,
                color = if (preset.isDoublePreset) Color(0xFFFF2A85) else appColors.primary
            )
            
            val hasUnsupportedFeatures = preset.presetIR?.diagnostics?.any { it.severity == DiagnosticSeverity.WARNING || it.severity == DiagnosticSeverity.ERROR } == true
            if (hasUnsupportedFeatures) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Partial support",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFA726)
                    )
                }
            }

            if (preset.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (preset.referencedTextures.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Textures: ${preset.referencedTextures.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = appColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "(Long-press for debug info)",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = appColors.primary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DirectoryInfoPanel(
    loaderService: CustomPresetLoaderService
) {
    val appColors = ObsidianTheme.colors
    val dirs = loaderService.getDirectoryPaths()
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        color = appColors.surfaceCard,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "MilkDrop Directory Paths",
                style = MaterialTheme.typography.titleSmall,
                color = appColors.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            dirs.forEach { (label, path) ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = appColors.textPrimary
                    )
                    Text(
                        path,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = appColors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(appColors.surfaceElevated, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
