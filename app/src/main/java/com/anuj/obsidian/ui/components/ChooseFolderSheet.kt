package com.anuj.obsidian.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.theme.ObsidianTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseFolderSheet(
    viewModel: ObsidianViewModel,
    onDismiss: () -> Unit,
    onOpenFolderExplorer: () -> Unit
) {
    val context = LocalContext.current
    val appColors = ObsidianTheme.colors
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var availableFolders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isIndexingCustomFolder by remember { mutableStateOf(false) }

    // SAF Document Tree Folder Picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            isIndexingCustomFolder = true
            Toast.makeText(context, "Scanning folder tree for offline tracks...", Toast.LENGTH_SHORT).show()
            viewModel.scanSelectedDirectory(treeUri, context) { count ->
                isIndexingCustomFolder = false
                Toast.makeText(
                    context,
                    if (count > 0) "Added $count songs from folder!" else "Folder linked successfully.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.fullLibraryRescan(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        availableFolders = viewModel.getAvailableAudioFolders()
        isLoading = false
    }

    val selectedFolders = settings.linkedMusicFolders.map { it.first }.toSet()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = appColors.surfaceCard,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp),
                shape = CircleShape,
                color = appColors.surfaceBorder
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CHOOSE OFFLINE FOLDERS",
                        color = appColors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = "Music Source Folders",
                        color = appColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = appColors.textSecondary)
                }
            }

            Text(
                text = "Choose which folders on your device to load songs from for offline listening.",
                color = appColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Strip: System Picker & Full Explorer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.primary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isIndexingCustomFolder) "Scanning..." else "+ Custom Folder",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenFolderExplorer()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, appColors.surfaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = appColors.textPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = appColors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Browse Hierarchy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Select All / Deselect All Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEVICE FOLDERS",
                    color = appColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select All",
                        color = appColors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                val allFolders = availableFolders.map { Pair(it.first, File(it.first).name) }
                                viewModel.setLinkedMusicFolders(allFolders, context) {
                                    Toast.makeText(context, "Selected all ${allFolders.size} folders", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp)
                    )
                    Text(
                        text = "•",
                        color = appColors.textMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Clear All",
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable {
                                viewModel.setLinkedMusicFolders(emptyList(), context) {
                                    Toast.makeText(context, "Cleared folders", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = appColors.primary, modifier = Modifier.size(32.dp))
                }
            } else if (availableFolders.isEmpty() && settings.linkedMusicFolders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No default audio folders detected",
                            color = appColors.textSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { folderPickerLauncher.launch(null) }) {
                            Text("Pick folder from Storage", color = appColors.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Show custom linked folders
                    if (settings.linkedMusicFolders.isNotEmpty()) {
                        items(settings.linkedMusicFolders) { linked ->
                            val isSaf = linked.first.startsWith("content://")
                            val displayName = linked.second.ifBlank { File(linked.first).name }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = appColors.background,
                                border = BorderStroke(1.dp, appColors.primary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSaf) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = appColors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayName,
                                            color = appColors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isSaf) "Linked Storage Directory" else linked.first,
                                            color = appColors.textSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.removeLinkedMusicFolder(linked.first, context)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Detected device folders
                    items(availableFolders) { folder ->
                        val isSelected = selectedFolders.contains(folder.first) || (selectedFolders.isEmpty() && folder.first.contains("Music", ignoreCase = true))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isSelected) {
                                        viewModel.removeLinkedMusicFolder(folder.first, context)
                                    } else {
                                        viewModel.addLinkedMusicFolder(folder.first, File(folder.first).name)
                                        viewModel.fullLibraryRescan(context)
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = appColors.background,
                            border = BorderStroke(1.dp, if (isSelected) appColors.primary.copy(alpha = 0.4f) else appColors.surfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) appColors.primary else appColors.textMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = File(folder.first).name,
                                        color = appColors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${folder.second} songs • ${folder.first}",
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
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Done / Apply Button
            Button(
                onClick = {
                    viewModel.fullLibraryRescan(context)
                    Toast.makeText(context, "Offline songs updated from selected folders", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = appColors.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Done & Load Offline Songs", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
