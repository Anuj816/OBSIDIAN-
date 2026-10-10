package com.anuj.obsidian.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.theme.ObsidianTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSelectionScreen(
    viewModel: ObsidianViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appColors = ObsidianTheme.colors
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var availableFolders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isIndexingCustomFolder by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // SAF Document Tree Folder Picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            isIndexingCustomFolder = true
            Toast.makeText(context, "Scanning selected directory tree...", Toast.LENGTH_SHORT).show()
            viewModel.scanSelectedDirectory(treeUri, context) { count ->
                isIndexingCustomFolder = false
                Toast.makeText(
                    context,
                    if (count > 0) "Indexed $count tracks from folder!" else "Folder added.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        availableFolders = viewModel.getAvailableAudioFolders()
        isLoading = false
    }

    val selectedFolders = settings.linkedMusicFolders.map { it.first }.toSet()

    val filteredFolders = remember(availableFolders, searchQuery) {
        if (searchQuery.isBlank()) availableFolders
        else availableFolders.filter {
            it.first.contains(searchQuery, ignoreCase = true) ||
            File(it.first).name.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Choose Offline Folders", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "${settings.linkedMusicFolders.size} active linked folder(s)",
                            color = appColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = appColors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isLoading = true
                        viewModel.rescanLibrary(context) {
                            scope.launch {
                                availableFolders = viewModel.getAvailableAudioFolders()
                                isLoading = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Folders", tint = appColors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appColors.background,
                    titleContentColor = appColors.textPrimary,
                    navigationIconContentColor = appColors.textPrimary
                )
            )
        },
        containerColor = appColors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header explanation & Custom folder picker action
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Select internal storage folders or browse custom directories to load your offline songs from.",
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Folder Picker Button (SAF)
                Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.surfaceCard,
                        contentColor = appColors.primary
                    ),
                    border = BorderStroke(1.dp, appColors.primary.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = appColors.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isIndexingCustomFolder) "Indexing Custom Folder..." else "+ Choose Folder via System Picker",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search & Quick Select All / Clear Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter folders...", fontSize = 12.sp, color = appColors.textMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = appColors.primary,
                            unfocusedBorderColor = appColors.surfaceBorder,
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedContainerColor = appColors.surfaceCard,
                            unfocusedContainerColor = appColors.surfaceCard
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            val allNew = availableFolders.map { Pair(it.first, File(it.first).name) }
                            viewModel.setLinkedMusicFolders(allNew, context) {
                                Toast.makeText(context, "Selected all folders (${allNew.size})", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Select All", color = appColors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            viewModel.setLinkedMusicFolders(emptyList(), context) {
                                Toast.makeText(context, "Cleared all linked folders", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Clear", color = appColors.textSecondary, fontSize = 12.sp)
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = appColors.primary)
                }
            } else if (availableFolders.isEmpty() && settings.linkedMusicFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOff, contentDescription = null, tint = appColors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No audio folders detected automatically.", color = appColors.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { folderPickerLauncher.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = appColors.primary)
                        ) {
                            Text("Pick Folder Manually", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Display Custom Linked Folders first if any exist
                    if (settings.linkedMusicFolders.isNotEmpty()) {
                        item {
                            Text(
                                text = "ACTIVE LINKED FOLDERS",
                                color = appColors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                        }

                        items(
                            items = settings.linkedMusicFolders,
                            key = { it.first }
                        ) { linked ->
                            val folderUri = linked.first
                            val folderName = linked.second.ifBlank { File(folderUri).name }
                            val isSaf = folderUri.startsWith("content://")

                            Surface(
                                modifier = Modifier.animateItem().fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = appColors.surfaceCard,
                                border = BorderStroke(1.dp, appColors.primary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSaf) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = appColors.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folderName,
                                            color = appColors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isSaf) "System Storage Tree • Linked" else folderUri,
                                            color = appColors.textSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.removeLinkedMusicFolder(folderUri, context)
                                            Toast.makeText(context, "Unlinked $folderName", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove Folder",
                                            tint = Color(0xFFFF5252)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "DETECTED DEVICE FOLDERS",
                            color = appColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = filteredFolders,
                        key = { it.first }
                    ) { folder ->
                        val isSelected = selectedFolders.contains(folder.first) || (selectedFolders.isEmpty() && folder.first.contains("Music", ignoreCase = true))

                        Surface(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isSelected) {
                                        viewModel.removeLinkedMusicFolder(folder.first, context)
                                    } else {
                                        viewModel.addLinkedMusicFolder(folder.first, File(folder.first).name)
                                        viewModel.fullLibraryRescan(context)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = appColors.surfaceCard,
                            border = BorderStroke(1.dp, if (isSelected) appColors.primary.copy(alpha = 0.5f) else appColors.surfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) appColors.primary else appColors.textSecondary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = File(folder.first).name,
                                        color = appColors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${folder.first} • ${folder.second} songs",
                                        color = appColors.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = appColors.primary,
                                        uncheckedColor = appColors.textSecondary,
                                        checkmarkColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.fullLibraryRescan(context)
                                Toast.makeText(context, "Syncing offline files...", Toast.LENGTH_SHORT).show()
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply & Sync Offline Files", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
