package com.anuj.obsidian.ui.screens

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.FileNodeItem
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.components.FormatChip
import com.anuj.obsidian.ui.theme.ObsidianTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderHierarchyScreen(
    viewModel: ObsidianViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appColors = ObsidianTheme.colors
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    // Default starting paths
    val defaultMusicPath = remember {
        val extMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath
        if (extMusic != null && File(extMusic).exists()) extMusic
        else Environment.getExternalStorageDirectory().absolutePath
    }

    var currentPath by remember { mutableStateOf(defaultMusicPath) }
    val navigationBackStack = remember { mutableStateListOf<String>() }
    var subfolders by remember { mutableStateOf<List<FileNodeItem>>(emptyList()) }
    var audioFiles by remember { mutableStateOf<List<DownloadedTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val currentFolderName = remember(currentPath) {
        if (currentPath.startsWith("content://")) {
            try {
                val uri = Uri.parse(currentPath)
                androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name
                    ?: androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
                    ?: Uri.decode(currentPath.substringAfterLast('/'))
            } catch (e: Exception) {
                "SAF Folder"
            }
        } else {
            File(currentPath).name.ifBlank { "Root Storage" }
        }
    }

    val navigateUp: () -> Unit = {
        if (navigationBackStack.isNotEmpty()) {
            currentPath = navigationBackStack.removeAt(navigationBackStack.lastIndex)
        } else if (currentPath.startsWith("content://")) {
            onBack()
        } else {
            val parent = File(currentPath).parentFile
            if (parent != null && parent.exists() && parent.canRead() && currentPath != "/storage/emulated/0" && currentPath != "/") {
                currentPath = parent.absolutePath
            } else {
                onBack()
            }
        }
    }

    BackHandler(enabled = navigationBackStack.isNotEmpty()) {
        currentPath = navigationBackStack.removeAt(navigationBackStack.lastIndex)
    }

    val reloadDirectory: () -> Unit = {
        isLoading = true
        scope.launch {
            val (dirs, files) = viewModel.getDirectoryContents(currentPath)
            subfolders = dirs
            audioFiles = files
            isLoading = false
        }
    }

    LaunchedEffect(currentPath) {
        isLoading = true
        val (dirs, files) = viewModel.getDirectoryContents(currentPath)
        subfolders = dirs
        audioFiles = files
        isLoading = false
    }

    // Path segments for breadcrumb navigation
    val pathSegments = remember(currentPath) {
        if (currentPath.startsWith("content://")) {
            val breadcrumbs = mutableListOf<Pair<String, String>>()
            breadcrumbs.add(Pair(currentFolderName, currentPath))
            breadcrumbs
        } else {
            val parts = currentPath.trim('/').split('/')
            val breadcrumbs = mutableListOf<Pair<String, String>>()
            var accumulated = ""
            for (part in parts) {
                accumulated += "/$part"
                breadcrumbs.add(Pair(part, accumulated))
            }
            breadcrumbs
        }
    }

    val isFolderLinked = remember(currentPath, settings.linkedMusicFolders) {
        settings.linkedMusicFolders.any { it.first == currentPath }
    }

    val filteredSubfolders = remember(subfolders, searchQuery) {
        if (searchQuery.isBlank()) subfolders
        else subfolders.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredAudioFiles = remember(audioFiles, searchQuery) {
        if (searchQuery.isBlank()) audioFiles
        else audioFiles.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Folder Tree Explorer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = currentFolderName,
                            color = appColors.primary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = appColors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        reloadDirectory()
                        Toast.makeText(context, "Refreshed folder tree", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = appColors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appColors.background,
                    titleContentColor = appColors.textPrimary
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
            // Quick Root Jump Shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickJumpChip("🎵 Music", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath ?: "/storage/emulated/0/Music", currentPath) { 
                    navigationBackStack.clear()
                    currentPath = it 
                }
                QuickJumpChip("📥 Downloads", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "/storage/emulated/0/Download", currentPath) { 
                    navigationBackStack.clear()
                    currentPath = it 
                }
                QuickJumpChip("💾 App Music", context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath ?: "", currentPath) { 
                    navigationBackStack.clear()
                    currentPath = it 
                }
                QuickJumpChip("📱 Storage Root", "/storage/emulated/0", currentPath) { 
                    navigationBackStack.clear()
                    currentPath = it 
                }
                if (settings.customDownloadFolderUri.isNotBlank()) {
                    QuickJumpChip("📁 Custom SAF", settings.customDownloadFolderUri, currentPath) {
                        navigationBackStack.clear()
                        currentPath = it
                    }
                }
            }

            // Breadcrumbs Navigation Bar
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = appColors.surfaceCard,
                border = BorderStroke(1.dp, appColors.surfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (currentPath.startsWith("content://")) Icons.Default.FolderSpecial else Icons.Default.Storage,
                        contentDescription = "Root",
                        tint = appColors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    pathSegments.forEachIndexed { index, pair ->
                        Text(
                            text = pair.first,
                            color = if (pair.second == currentPath) appColors.primary else appColors.textSecondary,
                            fontWeight = if (pair.second == currentPath) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { 
                                    if (pair.second != currentPath) {
                                        navigationBackStack.add(currentPath)
                                        currentPath = pair.second 
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        if (index < pathSegments.size - 1) {
                            Text(" / ", color = appColors.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Quick Actions & Directory Overview Header Card
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = appColors.surfaceCard,
                border = BorderStroke(1.dp, appColors.surfaceBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentFolderName,
                                color = appColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${subfolders.size} subfolders • ${audioFiles.size} tracks in folder",
                                color = appColors.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        // Auto-sync folder toggle
                        FilterChip(
                            selected = isFolderLinked,
                            onClick = {
                                if (isFolderLinked) {
                                    viewModel.removeLinkedMusicFolder(currentPath, null)
                                    Toast.makeText(context, "Unlinked folder from auto-sync", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addLinkedMusicFolder(currentPath, currentFolderName)
                                    Toast.makeText(context, "Linked folder to auto-sync", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = {
                                Text(
                                    text = if (isFolderLinked) "Linked" else "+ Link",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isFolderLinked) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = appColors.primary.copy(alpha = 0.2f),
                                selectedLabelColor = appColors.primary,
                                selectedLeadingIconColor = appColors.primary
                            )
                        )
                    }

                    // Directory Quick Play & Shuffle Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.playFolder(currentPath, shuffle = false)
                                Toast.makeText(context, "Playing folder recursively: $currentFolderName", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.primary,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Folder", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.playFolder(currentPath, shuffle = true)
                                Toast.makeText(context, "Shuffling folder recursively: $currentFolderName", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, appColors.surfaceBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = appColors.background,
                                contentColor = appColors.textPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle Folder", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search filter field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter items in this folder...", color = appColors.textMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = appColors.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = appColors.primary,
                    unfocusedBorderColor = appColors.surfaceBorder,
                    focusedContainerColor = appColors.surfaceCard,
                    unfocusedContainerColor = appColors.surfaceCard,
                    focusedTextColor = appColors.textPrimary,
                    unfocusedTextColor = appColors.textPrimary
                ),
                singleLine = true
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = appColors.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // "Up to Parent" button
                    val hasParent = navigationBackStack.isNotEmpty() || (!currentPath.startsWith("content://") && File(currentPath).parentFile?.canRead() == true && currentPath != "/storage/emulated/0" && currentPath != "/")
                    if (hasParent) {
                        item(key = "parent_nav_item") {
                            Surface(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clickable { navigateUp() },
                                shape = RoundedCornerShape(10.dp),
                                color = appColors.surfaceCard.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, appColors.surfaceBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Up",
                                        tint = appColors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = ".. [Parent Folder]",
                                        color = appColors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Section header: Subfolders
                    if (filteredSubfolders.isNotEmpty()) {
                        item(key = "directories_header") {
                            Text(
                                text = "DIRECTORIES (${filteredSubfolders.size})",
                                color = appColors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }

                        items(
                            items = filteredSubfolders, 
                            key = { "dir_${it.path}" },
                            contentType = { "folder_node" }
                        ) { folder ->
                            Surface(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clickable { 
                                        navigationBackStack.add(currentPath)
                                        currentPath = folder.path 
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = appColors.surfaceCard,
                                border = BorderStroke(1.dp, appColors.surfaceBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = appColors.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = appColors.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            color = appColors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                if (folder.audioCount > 0) append("${folder.audioCount} audio tracks • ")
                                                if (folder.subfolderCount > 0) append("${folder.subfolderCount} subfolders • ")
                                                append(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(folder.lastModified)))
                                            },
                                            color = appColors.textSecondary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1
                                        )
                                    }

                                    // Quick Play folder button
                                    IconButton(
                                        onClick = {
                                            viewModel.playFolder(folder.path, shuffle = false)
                                            Toast.makeText(context, "Playing folder: ${folder.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Folder",
                                            tint = appColors.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section header: Audio Files
                    if (filteredAudioFiles.isNotEmpty()) {
                        item(key = "audio_tracks_header") {
                            Text(
                                text = "AUDIO TRACKS (${filteredAudioFiles.size})",
                                color = appColors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }

                        items(
                            items = filteredAudioFiles, 
                            key = { "track_${it.id}" },
                            contentType = { "track_node" }
                        ) { track ->
                            val isCurrentTrack = playerState.currentTrack?.id == track.id
                            Surface(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clickable { viewModel.playTrackFromList(track, filteredAudioFiles) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrentTrack) appColors.primary.copy(alpha = 0.12f) else appColors.surfaceCard,
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrentTrack) appColors.primary else appColors.surfaceBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrentTrack) appColors.primary else appColors.surfaceBorder,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isCurrentTrack && playerState.isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isCurrentTrack) Color.Black else appColors.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = if (isCurrentTrack) appColors.primary else appColors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.format} • ${String.format(Locale.US, "%.1f MB", track.fileSizeMb)} • ${formatDuration(track.durationSeconds)}",
                                            color = appColors.textSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }

                                    FormatChip(
                                        text = track.format,
                                        color = if (track.format.equals("FLAC", true)) Color(0xFFF5A623) else appColors.primary
                                    )
                                }
                            }
                        }
                    }

                    if (filteredSubfolders.isEmpty() && filteredAudioFiles.isEmpty()) {
                        item(key = "empty_state") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = appColors.textMuted,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No subfolders or audio files in this directory", color = appColors.textSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickJumpChip(
    label: String,
    targetPath: String,
    currentPath: String,
    onClick: (String) -> Unit
) {
    val isSelected = currentPath.startsWith(targetPath)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) ObsidianTheme.colors.primary.copy(alpha = 0.2f) else ObsidianTheme.colors.surfaceCard,
        border = BorderStroke(1.dp, if (isSelected) ObsidianTheme.colors.primary else ObsidianTheme.colors.surfaceBorder),
        modifier = Modifier.clickable {
            if (targetPath.isNotBlank()) {
                if (targetPath.startsWith("content://") || File(targetPath).exists()) {
                    onClick(targetPath)
                }
            }
        }
    ) {
        Text(
            text = label,
            color = if (isSelected) ObsidianTheme.colors.primary else ObsidianTheme.colors.textPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb)
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

