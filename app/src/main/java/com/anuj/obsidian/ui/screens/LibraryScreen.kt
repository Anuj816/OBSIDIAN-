package com.anuj.obsidian.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.anuj.obsidian.ui.components.TrackListSkeleton
import com.anuj.obsidian.ui.components.ObsidianEmptyState
import com.anuj.obsidian.ui.components.ObsidianPullToRefreshIndicator
import com.anuj.obsidian.ui.components.ObsidianTrackRow
import com.anuj.obsidian.ui.components.fuzzyMatch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anuj.obsidian.data.model.AlbumInfo
import com.anuj.obsidian.data.model.ArtistInfo
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.GenreInfo
import com.anuj.obsidian.data.model.Playlist
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.animation.*
import com.anuj.obsidian.ui.components.ChooseFolderSheet
import com.anuj.obsidian.ui.components.EqualizerWaveAnimation
import com.anuj.obsidian.ui.components.SleepTimerBottomSheet
import com.anuj.obsidian.ui.components.TrackArtworkPlaceholder
import com.anuj.obsidian.ui.components.TrackWaveformArt
import com.anuj.obsidian.ui.components.ObsidianDspBottomSheet
import com.anuj.obsidian.ui.components.formatSecToTime
import com.anuj.obsidian.ui.theme.FlacLosslessCyan
import com.anuj.obsidian.ui.theme.ObsidianTheme
import android.content.Intent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class LibraryFilter {
    ALL,
    DIRECT_FOLDER,
    HI_RES,
    CD_QUALITY,
    FAVORITES
}

enum class LibrarySearchScope(val label: String) {
    ALL("All Fields"),
    DOWNLOADED("Downloaded"),
    ARTIST("Artist"),
    ALBUM("Album")
}

enum class SortField(val displayName: String) {
    DATE_ADDED("Date Added"),
    ALPHABETICAL("Title (A-Z)"),
    ARTIST("Artist (A-Z)"),
    MODIFIED_DATE("Modified Date"),
    DURATION("Duration"),
    FILE_SIZE("File Size")
}


enum class FolderSortField(val displayName: String) {
    ALPHABETICAL("Alphabetical (A-Z)"),
    DATE_ADDED("Date Added"),
    TRACK_COUNT("Track Count")
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: ObsidianViewModel,
    modifier: Modifier = Modifier,
    onManageFolders: () -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val appColors = ObsidianTheme.colors
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLibraryScanning by viewModel.isLibraryScanning.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playerStateState = viewModel.playerState.collectAsStateWithLifecycle()
    
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        viewModel.setLibraryScrolling(listState.isScrollInProgress)
    }
    
    val selectedFolderFilter by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    var searchQuery by remember { mutableStateOf("") }
    var searchScope by remember { mutableStateOf(LibrarySearchScope.ALL) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var sortField by remember { mutableStateOf(SortField.DATE_ADDED) }
    var sortDirection by remember { mutableStateOf(SortDirection.DESCENDING) }
    var folderSortField by remember { mutableStateOf(FolderSortField.TRACK_COUNT) }
    var folderSortDirection by remember { mutableStateOf(SortDirection.DESCENDING) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isScanningFolder by remember { mutableStateOf(false) }
    var showChooseFolderSheet by remember { mutableStateOf(false) }
    var showDspSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showBatchTagSheet by remember { mutableStateOf(false) }
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()

    // Navigation Subview States
    var selectedAlbum by remember { mutableStateOf<AlbumInfo?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistInfo?>(null) }
    var selectedGenre by remember { mutableStateOf<GenreInfo?>(null) }
    var selectedPlaylistForView by remember { mutableStateOf<Playlist?>(null) }

    // Handle System Back for Subviews
    BackHandler(enabled = selectedAlbum != null || selectedArtist != null || selectedGenre != null || selectedPlaylistForView != null) {
        selectedAlbum = null
        selectedArtist = null
        selectedGenre = null
        selectedPlaylistForView = null
    }

    // 0 = Tracks, 1 = Albums, 2 = Artists, 3 = Genres, 4 = Playlists, 5 = Folders
    var currentLibraryTab by remember { mutableIntStateOf(0) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    // Multi-selection state for queue selection & playlist actions
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTrackIdsState = remember { mutableStateOf(setOf<Long>()) }
    var selectedTrackIds by selectedTrackIdsState

    var showAddToPlaylistDialog by remember { mutableStateOf<DownloadedTrack?>(null) }
    showAddToPlaylistDialog?.let { track ->
        var inlineNewPlaylistName by remember { mutableStateOf("") }
        var isCreatingInline by remember { mutableStateOf(false) }
        val targetTracks = remember(track, selectedTrackIds) {
            if (selectedTrackIds.contains(track.id) && selectedTrackIds.size > 1) {
                tracks.filter { it.id in selectedTrackIds }
            } else {
                listOf(track)
            }
        }

        androidx.compose.ui.window.Dialog(onDismissRequest = { showAddToPlaylistDialog = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = appColors.surfaceCard,
                border = BorderStroke(1.dp, appColors.surfaceBorder),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (targetTracks.size > 1) "Add ${targetTracks.size} Tracks to Playlist" else "Add to Playlist",
                        color = appColors.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (targetTracks.size > 1) "Select playlist or create a new one" else "'${track.title}'",
                        color = appColors.textSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Create New Playlist row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inlineNewPlaylistName,
                            onValueChange = { inlineNewPlaylistName = it },
                            placeholder = { Text("New playlist name...", color = appColors.textMuted, fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = appColors.primary,
                                unfocusedBorderColor = appColors.surfaceBorder,
                                focusedTextColor = appColors.textPrimary,
                                unfocusedTextColor = appColors.textPrimary,
                                focusedContainerColor = appColors.surfaceElevated,
                                unfocusedContainerColor = appColors.surfaceElevated
                            ),
                            modifier = Modifier.weight(1f).height(48.dp)
                        )

                        Button(
                            onClick = {
                                if (inlineNewPlaylistName.isNotBlank() && !isCreatingInline) {
                                    isCreatingInline = true
                                    val nameToCreate = inlineNewPlaylistName.trim()
                                    val idsToAdd = targetTracks.map { it.id }
                                    viewModel.createPlaylistAndAddTracks(
                                        name = nameToCreate,
                                        trackIds = idsToAdd,
                                        onComplete = {
                                            Toast.makeText(context, "Created '$nameToCreate' & added ${targetTracks.size} track(s)", Toast.LENGTH_SHORT).show()
                                            showAddToPlaylistDialog = null
                                        }
                                    )
                                }
                            },
                            enabled = inlineNewPlaylistName.isNotBlank() && !isCreatingInline,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = appColors.primary)
                        ) {
                            Text("Create & Add", color = Color.Black, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (playlists.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Type a name above to create your first playlist!", color = appColors.textMuted, fontSize = 13.sp)
                        }
                    } else {
                        Text(
                            text = "EXISTING PLAYLISTS",
                            color = appColors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(playlists, key = { it.id }) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (targetTracks.size == 1) {
                                                viewModel.addTrackToPlaylist(playlist.id, track.id)
                                                Toast.makeText(context, "Added '${track.title}' to ${playlist.name}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.addTracksToPlaylist(playlist.id, targetTracks.map { it.id })
                                                Toast.makeText(context, "Added ${targetTracks.size} tracks to ${playlist.name}", Toast.LENGTH_SHORT).show()
                                            }
                                            showAddToPlaylistDialog = null
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(appColors.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(playlist.name, color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Icon(Icons.Default.Add, contentDescription = "Add", tint = appColors.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddToPlaylistDialog = null }) {
                            Text("Cancel", color = appColors.textSecondary)
                        }
                    }
                }
            }
        }
    }

    var trackToDelete by remember { mutableStateOf<DownloadedTrack?>(null) }
    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = appColors.surfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Delete Track",
                        color = appColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "'${track.title}'",
                        color = appColors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Choose whether to permanently delete this audio file from device storage or remove it from your library list only:",
                        color = appColors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val target = track
                            trackToDelete = null
                            viewModel.deleteTrack(target, deleteFromStorage = true, context = context)
                            Toast.makeText(context, "Permanently deleted '${target.title}' from storage", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete from Device Storage", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val target = track
                            trackToDelete = null
                            viewModel.deleteTrack(target, deleteFromStorage = false, context = context)
                            Toast.makeText(context, "Removed '${target.title}' from library list", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primary),
                        border = BorderStroke(1.dp, appColors.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = null,
                            tint = appColors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove from Library Only", color = appColors.primary, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { trackToDelete = null },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Cancel", color = appColors.textSecondary)
                    }
                }
            },
            dismissButton = {}
        )
    }

    // Direct SAF Folder Access Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            isScanningFolder = true
            Toast.makeText(context, "Scanning folder directly for lossless music...", Toast.LENGTH_SHORT).show()
            viewModel.scanSelectedDirectory(treeUri, context) { count ->
                isScanningFolder = false
                Toast.makeText(
                    context,
                    if (count > 0) "Indexed $count tracks from folder!" else "Folder linked. No compatible audio files found.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Computed Groupings - Optimized to only compute when the corresponding tab or detail is active
    val albumsList = remember(tracks, searchQuery, currentLibraryTab, selectedAlbum) {
        if (currentLibraryTab != 1 && selectedAlbum == null) emptyList()
        else {
            tracks.groupBy { it.album.ifBlank { "Unknown Album" } }
                .map { (albumTitle, albumTracks) ->
                    AlbumInfo(
                        title = albumTitle,
                        artist = albumTracks.firstOrNull()?.artist ?: "Unknown Artist",
                        coverArtUrl = albumTracks.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl ?: "",
                        year = albumTracks.firstOrNull()?.releaseDate ?: "",
                        tracks = albumTracks.sortedWith(compareBy<DownloadedTrack> { it.trackNumber }.thenBy { it.title })
                    )
                }
                .filter { album ->
                    if (searchQuery.isBlank()) true
                    else fuzzyMatch(searchQuery, album.title) || fuzzyMatch(searchQuery, album.artist)
                }
                .sortedBy { it.title.lowercase() }
        }
    }

    val artistsList = remember(tracks, searchQuery, currentLibraryTab, selectedArtist) {
        if (currentLibraryTab != 2 && selectedArtist == null) emptyList()
        else {
            tracks.groupBy { it.artist.ifBlank { "Unknown Artist" } }
                .map { (artistName, artistTracks) ->
                    val distinctAlbumCount = artistTracks.map { it.album.ifBlank { "Single" } }.distinct().size
                    ArtistInfo(
                        name = artistName,
                        albumCount = distinctAlbumCount,
                        tracks = artistTracks.sortedBy { it.title.lowercase() }
                    )
                }
                .filter { artist ->
                    if (searchQuery.isBlank()) true
                    else fuzzyMatch(searchQuery, artist.name)
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    val genresList = remember(tracks, searchQuery, currentLibraryTab, selectedGenre) {
        if (currentLibraryTab != 3 && selectedGenre == null) emptyList()
        else {
            tracks.groupBy { it.genre.ifBlank { "General / Unassigned" } }
                .map { (genreName, genreTracks) ->
                    GenreInfo(
                        name = genreName,
                        tracks = genreTracks.sortedBy { it.title.lowercase() }
                    )
                }
                .filter { genre ->
                    if (searchQuery.isBlank()) true
                    else fuzzyMatch(searchQuery, genre.name)
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    val distinctFolders = remember(tracks, folderSortField, folderSortDirection) {
        val map = mutableMapOf<String, Pair<Int, Long>>() // name -> (count, latestDateAdded)
        tracks.forEach { track ->
            val folderName = if (track.providerSource.startsWith("Folder:", ignoreCase = true)) {
                track.providerSource.substringAfter("Folder:").trim()
            } else if (track.localFilePath.startsWith("content://")) {
                "Linked Storage Folder"
            } else {
                val parent = track.localFilePath.substringBeforeLast('/', "").substringAfterLast('/')
                if (parent.isNotBlank()) parent else "Storage"
            }
            val current = map[folderName] ?: Pair(0, 0L)
            map[folderName] = Pair(current.first + 1, maxOf(current.second, track.dateAddedTimestamp))
        }
        val list = map.toList()
        val comparator: Comparator<Pair<String, Pair<Int, Long>>> = when (folderSortField) {
            FolderSortField.ALPHABETICAL -> Comparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.first, b.first) }
            FolderSortField.DATE_ADDED -> compareBy { it.second.second }
            FolderSortField.TRACK_COUNT -> compareBy { it.second.first }
        }
        if (folderSortDirection == SortDirection.ASCENDING) {
            list.sortedWith(comparator)
        } else {
            list.sortedWith(comparator).reversed()
        }
    }

    val favoriteTracks = remember(tracks) { tracks.filter { it.isFavorite } }
    val hiResTracks = remember(tracks) { tracks.filter { it.bitDepth.contains("24") || it.sampleRate.contains("96") } }
    val recentlyPlayedTracks by viewModel.recentlyPlayedSmartPlaylist.collectAsStateWithLifecycle()
    val onRepeatTracks by viewModel.onRepeatSmartPlaylist.collectAsStateWithLifecycle()
    val forgottenFavoritesTracks by viewModel.forgottenFavoritesSmartPlaylist.collectAsStateWithLifecycle()

    val filteredTracks = remember(tracks, searchQuery, searchScope, selectedFilter, selectedFolderFilter, sortField, sortDirection) {
        val filtered = tracks.filter { track ->
            val matchesFilter = when (selectedFilter) {
                LibraryFilter.ALL -> true
                LibraryFilter.DIRECT_FOLDER -> track.providerSource.startsWith("Folder:", ignoreCase = true) || track.localFilePath.startsWith("content://")
                LibraryFilter.HI_RES -> track.bitDepth.contains("24") || track.sampleRate.contains("96") || track.sampleRate.contains("192")
                LibraryFilter.CD_QUALITY -> !track.bitDepth.contains("24")
                LibraryFilter.FAVORITES -> track.isFavorite
            }

            val currentFolderFilter = selectedFolderFilter
            val matchesFolder = if (currentFolderFilter == null) true else {
                val folderName = if (track.providerSource.startsWith("Folder:", ignoreCase = true)) {
                    track.providerSource.substringAfter("Folder:").trim()
                } else if (track.localFilePath.startsWith("content://")) {
                    "Linked Storage Folder"
                } else {
                    val parent = track.localFilePath.substringBeforeLast('/', "").substringAfterLast('/')
                    if (parent.isNotBlank()) parent else "Storage"
                }
                folderName.equals(currentFolderFilter, ignoreCase = true) ||
                        track.localFilePath.contains(currentFolderFilter, ignoreCase = true)
            }

            val matchesSearch = if (searchQuery.isBlank()) {
                when (searchScope) {
                    LibrarySearchScope.DOWNLOADED -> track.localFilePath.isNotBlank() && java.io.File(track.localFilePath).exists()
                    else -> true
                }
            } else {
                when (searchScope) {
                    LibrarySearchScope.ALL -> fuzzyMatch(searchQuery, track.title) || fuzzyMatch(searchQuery, track.artist) || fuzzyMatch(searchQuery, track.album)
                    LibrarySearchScope.DOWNLOADED -> (track.localFilePath.isNotBlank() && java.io.File(track.localFilePath).exists()) &&
                            (fuzzyMatch(searchQuery, track.title) || fuzzyMatch(searchQuery, track.artist) || fuzzyMatch(searchQuery, track.album))
                    LibrarySearchScope.ARTIST -> fuzzyMatch(searchQuery, track.artist)
                    LibrarySearchScope.ALBUM -> fuzzyMatch(searchQuery, track.album)
                }
            }
            matchesFilter && matchesFolder && matchesSearch
        }

        // Apply Sorting by selected field and direction
        val comparator: Comparator<DownloadedTrack> = when (sortField) {
            SortField.DATE_ADDED -> compareBy<DownloadedTrack> { it.dateAddedTimestamp }.thenBy { it.id }
            SortField.ALPHABETICAL -> compareBy<DownloadedTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id }
            SortField.ARTIST -> compareBy<DownloadedTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy { it.id }
            SortField.MODIFIED_DATE -> compareBy<DownloadedTrack> { it.dateAddedTimestamp }.thenBy { it.id }
            SortField.DURATION -> compareBy<DownloadedTrack> { it.durationSeconds }.thenBy { it.id }
            SortField.FILE_SIZE -> compareBy<DownloadedTrack> { it.fileSizeMb }.thenBy { it.id }
        }

        if (sortDirection == SortDirection.ASCENDING) {
            filtered.sortedWith(comparator)
        } else {
            filtered.sortedWith { a, b -> comparator.compare(b, a) }
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            try {
                listState.scrollToItem(0)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("LibraryScreen", "Failed to scroll to top: ${e.message}")
            }
        }
    }

    val totalSizeMb = remember(tracks) {
        tracks.sumOf { it.fileSizeMb.toDouble() }
    }

    // Dynamically resolve drill-down selections against latest tracks state to prevent stale artwork/state
    val currentSelectedAlbum = remember(albumsList, selectedAlbum) {
        val targetTitle = selectedAlbum?.title ?: return@remember null
        albumsList.find { it.title.equals(targetTitle, ignoreCase = true) } ?: selectedAlbum
    }
    val currentSelectedArtist = remember(artistsList, selectedArtist) {
        val targetName = selectedArtist?.name ?: return@remember null
        artistsList.find { it.name.equals(targetName, ignoreCase = true) } ?: selectedArtist
    }
    val currentSelectedGenre = remember(genresList, selectedGenre) {
        val targetName = selectedGenre?.name ?: return@remember null
        genresList.find { it.name.equals(targetName, ignoreCase = true) } ?: selectedGenre
    }
    val currentSelectedPlaylist = remember(playlists, selectedPlaylistForView) {
        val targetId = selectedPlaylistForView?.id ?: return@remember null
        playlists.find { it.id == targetId } ?: selectedPlaylistForView
    }

    // Render Drill-Down Subviews when selected
    currentSelectedAlbum?.let { album ->
        AlbumDetailView(
            album = album,
            onBack = { selectedAlbum = null },
            viewModel = viewModel,
            modifier = modifier
        )
        return
    }

    currentSelectedArtist?.let { artist ->
        ArtistDetailView(
            artist = artist,
            onBack = { selectedArtist = null },
            viewModel = viewModel,
            modifier = modifier
        )
        return
    }

    currentSelectedGenre?.let { genre ->
        GenreDetailView(
            genre = genre,
            onBack = { selectedGenre = null },
            viewModel = viewModel,
            modifier = modifier
        )
        return
    }

    currentSelectedPlaylist?.let { playlist ->
        PlaylistDetailView(
            playlist = playlist,
            onBack = { selectedPlaylistForView = null },
            viewModel = viewModel,
            modifier = modifier
        )
        return
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isLibraryScanning,
        onRefresh = { viewModel.rescanLibrary(context) },
        indicator = {
            ObsidianPullToRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = isLibraryScanning
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val listTransitionKey = remember(selectedFilter, sortField, sortDirection, filteredTracks.size) { Any() }
            LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                // Top Section: Header & Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "OBSIDIAN AUDIO ENGINE",
                            color = appColors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.6.sp
                        )
                        Text(
                            text = "Library",
                            color = appColors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Surface(
                        color = appColors.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, appColors.surfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = appColors.primary
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f MB", totalSizeMb),
                                color = appColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Obsidian Category Navigation Tab Row
                val categories = listOf(
                    "Tracks (${tracks.size})",
                    "Albums (${albumsList.size})",
                    "Artists (${artistsList.size})",
                    "Genres (${genresList.size})",
                    "Playlists (${playlists.size})",
                    "Folders (${distinctFolders.size})"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(categories) { index, categoryTitle ->
                        val isTabSelected = currentLibraryTab == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isTabSelected) appColors.primary else appColors.surfaceCard,
                            border = BorderStroke(
                                1.dp,
                                if (isTabSelected) appColors.primary else appColors.surfaceBorder
                            ),
                            modifier = Modifier
                                .bounceClickable(scaleDown = 0.96f) {
                                    selectedAlbum = null
                                    selectedArtist = null
                                    selectedGenre = null
                                    selectedPlaylistForView = null
                                    currentLibraryTab = index
                                }
                        ) {
                            Text(
                                text = categoryTitle,
                                color = if (isTabSelected) Color.Black else appColors.textSecondary,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            when (currentLibraryTab) {
                1 -> {
                    // Albums Tab
                    item {
                        AlbumGrid(
                            albums = albumsList,
                            onSelectAlbum = { selectedAlbum = it }
                        )
                    }
                }
                2 -> {
                    // Artists Tab
                    item {
                        ArtistGrid(
                            artists = artistsList,
                            onSelectArtist = { selectedArtist = it }
                        )
                    }
                }
                3 -> {
                    // Genres Tab
                    item {
                        GenreGrid(
                            genres = genresList,
                            onSelectGenre = { selectedGenre = it }
                        )
                    }
                }
                4 -> {
                    // Playlists Tab
                    item {
                        var newPlaylistName by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newPlaylistName,
                                onValueChange = { newPlaylistName = it },
                                placeholder = { Text("New playlist name...", color = appColors.textMuted, fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = appColors.primary,
                                    unfocusedBorderColor = appColors.surfaceBorder,
                                    focusedTextColor = appColors.textPrimary,
                                    unfocusedTextColor = appColors.textPrimary,
                                    focusedContainerColor = appColors.surfaceCard,
                                    unfocusedContainerColor = appColors.surfaceCard
                                ),
                                modifier = Modifier.weight(1f).height(50.dp)
                            )

                            Button(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        val nameToCreate = newPlaylistName.trim()
                                        if (selectedTrackIds.isNotEmpty()) {
                                            val idsToAdd = tracks.filter { it.id in selectedTrackIds }.map { it.id }
                                            viewModel.createPlaylistAndAddTracks(
                                                name = nameToCreate,
                                                trackIds = idsToAdd,
                                                onComplete = {
                                                    Toast.makeText(context, "Created '$nameToCreate' with ${idsToAdd.size} track(s)", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            viewModel.createPlaylist(nameToCreate)
                                            Toast.makeText(context, "Created '$nameToCreate'", Toast.LENGTH_SHORT).show()
                                        }
                                        newPlaylistName = ""
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.primary)
                            ) {
                                Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Smart Auto-Playlists Header
                        Text(
                            text = "SMART PLAYLISTS",
                            color = appColors.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Smart Playlist: Favorites
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable {
                                    if (favoriteTracks.isNotEmpty()) {
                                        favoriteTracks.firstOrNull()?.let { viewModel.playTrackFromList(it, favoriteTracks) }
                                    } else {
                                        Toast.makeText(context, "No favorites yet. Tap the heart on songs to add them!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            color = appColors.surfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, appColors.surfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFF5252).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Favorite Tracks", color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${favoriteTracks.size} songs • Tap to play directly", color = appColors.textSecondary, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    if (favoriteTracks.isNotEmpty()) viewModel.playTrackFromList(null, favoriteTracks, forceShuffle = true)
                                }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Favorites", tint = appColors.primary)
                                }
                            }
                        }
                    }

                    // Smart Playlist: Hi-Res Studio Masters
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable {
                                    if (hiResTracks.isNotEmpty()) {
                                        hiResTracks.firstOrNull()?.let { viewModel.playTrackFromList(it, hiResTracks) }
                                    } else {
                                        Toast.makeText(context, "No 24-bit lossless files loaded yet.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            color = appColors.surfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, appColors.surfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(appColors.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Hi-Res Studio Masters", color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${hiResTracks.size} songs (24-bit 96/192kHz) • Lossless Master", color = appColors.textSecondary, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    if (hiResTracks.isNotEmpty()) viewModel.playTrackFromList(null, hiResTracks, forceShuffle = true)
                                }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Hi-Res", tint = appColors.primary)
                                }
                            }
                        }
                    }

                    // Smart Playlist: Recently Played
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable {
                                    if (recentlyPlayedTracks.isNotEmpty()) {
                                        recentlyPlayedTracks.firstOrNull()?.let { viewModel.playTrackFromList(it, recentlyPlayedTracks) }
                                    } else {
                                        Toast.makeText(context, "No recently played tracks yet.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            color = appColors.surfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, appColors.surfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF29B6F6).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF29B6F6), modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Recently Played", color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${recentlyPlayedTracks.size} songs • Listening History", color = appColors.textSecondary, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    if (recentlyPlayedTracks.isNotEmpty()) viewModel.playTrackFromList(null, recentlyPlayedTracks, forceShuffle = true)
                                }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Recently Played", tint = appColors.primary)
                                }
                            }
                        }
                    }

                    // Smart Playlist: On Repeat
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable {
                                    if (onRepeatTracks.isNotEmpty()) {
                                        onRepeatTracks.firstOrNull()?.let { viewModel.playTrackFromList(it, onRepeatTracks) }
                                    } else {
                                        Toast.makeText(context, "Listen to songs more often this week to build your On Repeat mix!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            color = appColors.surfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, appColors.surfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFAB47BC).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Repeat, contentDescription = null, tint = Color(0xFFAB47BC), modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("On Repeat", color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${onRepeatTracks.size} songs • Top played this week", color = appColors.textSecondary, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    if (onRepeatTracks.isNotEmpty()) viewModel.playTrackFromList(null, onRepeatTracks, forceShuffle = true)
                                }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle On Repeat", tint = appColors.primary)
                                }
                            }
                        }
                    }

                    // Smart Playlist: Forgotten Favorites
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable {
                                    if (forgottenFavoritesTracks.isNotEmpty()) {
                                        forgottenFavoritesTracks.firstOrNull()?.let { viewModel.playTrackFromList(it, forgottenFavoritesTracks) }
                                    } else {
                                        Toast.makeText(context, "No forgotten favorites detected yet.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            color = appColors.surfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, appColors.surfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFFA726).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Forgotten Favorites", color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${forgottenFavoritesTracks.size} songs • Loved songs you haven't played recently", color = appColors.textSecondary, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    if (forgottenFavoritesTracks.isNotEmpty()) viewModel.playTrackFromList(null, forgottenFavoritesTracks, forceShuffle = true)
                                }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Forgotten Favorites", tint = appColors.primary)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "CUSTOM PLAYLISTS",
                            color = appColors.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (playlists.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No custom playlists created yet.", color = appColors.textSecondary)
                            }
                        }
                    } else {
                        items(playlists, key = { it.id }) { playlist ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClickable { selectedPlaylistForView = playlist },
                                color = appColors.surfaceCard,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, appColors.surfaceBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(appColors.surfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = appColors.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(playlist.name, color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(
                                            "Created ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US).format(java.util.Date(playlist.createdAt))}",
                                            color = appColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(onClick = { viewModel.playPlaylist(playlist.id) }) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Playlist", tint = appColors.primary)
                                    }
                                    IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete Playlist", tint = Color(0xFFFF5252))
                                    }
                                }
                            }
                        }
                    }
                }
                5 -> {
                    // Folders Tab
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = appColors.surfaceCard,
                            border = BorderStroke(1.dp, appColors.surfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showChooseFolderSheet = true },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = appColors.primary,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Link Folders", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = { onManageFolders() },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, appColors.surfaceBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = appColors.surfaceCard,
                                        contentColor = appColors.textPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("File Explorer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "INDEXED MUSIC DIRECTORIES",
                            color = appColors.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (distinctFolders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No folders scanned yet.", color = appColors.textSecondary)
                            }
                        }
                    } else {
                        items(distinctFolders, key = { it.first }) { folderPair ->
                            val folderName = folderPair.first
                            val songCount = folderPair.second
                            val folderSongs = remember(tracks, folderName) {
                                tracks.filter { track ->
                                    val fName = if (track.providerSource.startsWith("Folder:", ignoreCase = true)) {
                                        track.providerSource.substringAfter("Folder:").trim()
                                    } else if (track.localFilePath.startsWith("content://")) {
                                        "Linked Storage Folder"
                                    } else {
                                        try {
                                            java.io.File(track.localFilePath).parentFile?.name ?: "Storage"
                                        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "LibraryScreen.LibraryScreen - attempt")
            
                                            "Storage"
                                        }
                                    }
                                    fName.equals(folderName, ignoreCase = true)
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClickable {
                                        viewModel.setSelectedFolderFilter(folderName)
                                        currentLibraryTab = 0
                                    },
                                color = appColors.surfaceCard,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, appColors.surfaceBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = appColors.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
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
                                            text = "$songCount songs indexed • Tap to view songs",
                                            color = appColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(onClick = {
                                        folderSongs.firstOrNull()?.let { firstT ->
                                            viewModel.playTrackFromList(firstT, folderSongs)
                                        }
                                    }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Folder", tint = appColors.primary)
                                    }
                                    IconButton(onClick = {
                                        if (folderSongs.isNotEmpty()) {
                                            viewModel.playTrackFromList(null, folderSongs, forceShuffle = true)
                                        }
                                    }) {
                                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Folder", tint = appColors.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                0 -> {
                    // Default Tracks View with Full Filter, Search, Fast Scroller, and Selection
                    item {
                        // Quick Folder / File Scanner Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = appColors.surfaceCard,
                            border = BorderStroke(1.dp, appColors.surfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    onClick = { showChooseFolderSheet = true },
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(40.dp)
                                        .pressSpring(scaleDown = 0.95f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = appColors.primary.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, appColors.primary.copy(alpha = 0.55f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderSpecial,
                                            contentDescription = null,
                                            tint = appColors.primary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Choose Folders",
                                            color = appColors.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { onManageFolders() },
                                    modifier = Modifier
                                        .weight(0.95f)
                                        .height(40.dp)
                                        .pressSpring(scaleDown = 0.95f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = appColors.surfaceElevated,
                                    border = BorderStroke(1.dp, appColors.surfaceBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = "Hierarchy",
                                            tint = appColors.primary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Explorer",
                                            color = appColors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        isScanningFolder = true
                                        Toast.makeText(context, "Refreshing media library...", Toast.LENGTH_SHORT).show()
                                        viewModel.rescanLibrary(context) { count ->
                                            isScanningFolder = false
                                            Toast.makeText(
                                                context,
                                                if (count > 0) "Refreshed! Added $count new track(s)." else "Library up to date.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .pressSpring(scaleDown = 0.95f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = appColors.surfaceElevated,
                                    border = BorderStroke(1.dp, appColors.surfaceBorder)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh Library",
                                            tint = appColors.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Search Bar (Expandable or always available if searching)
                    if (tracks.isNotEmpty() && (isSearchExpanded || searchQuery.isNotEmpty())) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search songs, artists, albums...", color = appColors.textMuted, fontSize = 13.sp) },
                                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = appColors.primary) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = appColors.textSecondary)
                                        }
                                    }
                                },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                }),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = appColors.primary,
                                    unfocusedBorderColor = appColors.surfaceBorder,
                                    focusedTextColor = appColors.textPrimary,
                                    unfocusedTextColor = appColors.textPrimary,
                                    focusedContainerColor = appColors.surfaceCard,
                                    unfocusedContainerColor = appColors.surfaceCard
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("library_search_input")
                            )

                            // Search Scoping Filter Chips (All, Downloaded, Artist, Album)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(LibrarySearchScope.entries, key = { it.name }) { scope ->
                                    val isSelected = searchScope == scope
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { searchScope = scope },
                                        leadingIcon = {
                                            val icon = when (scope) {
                                                LibrarySearchScope.ALL -> Icons.Default.Search
                                                LibrarySearchScope.DOWNLOADED -> Icons.Default.Download
                                                LibrarySearchScope.ARTIST -> Icons.Default.Person
                                                LibrarySearchScope.ALBUM -> Icons.Default.Album
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.Black else appColors.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = scope.label,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = appColors.primary,
                                            selectedLabelColor = Color.Black,
                                            containerColor = appColors.surfaceCard,
                                            labelColor = appColors.textSecondary
                                        ),
                                        border = BorderStroke(1.dp, if (isSelected) appColors.primary else appColors.surfaceBorder)
                                    )
                                }
                            }
                        }
                    }

                    // Filter Chips Row (Categories & Folders)
                    if (tracks.isNotEmpty()) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedFilter == LibraryFilter.ALL && selectedFolderFilter == null,
                                        onClick = {
                                            selectedFilter = LibraryFilter.ALL
                                            viewModel.setSelectedFolderFilter(null)
                                        },
                                        label = { Text("All (${tracks.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = appColors.primary,
                                            selectedLabelColor = Color.Black,
                                            containerColor = appColors.surfaceCard,
                                            labelColor = appColors.textSecondary
                                        )
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = { showChooseFolderSheet = true },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.CreateNewFolder,
                                                contentDescription = null,
                                                tint = appColors.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Folders (${settings.linkedMusicFolders.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = appColors.surfaceCard,
                                            labelColor = appColors.primary
                                        ),
                                        border = BorderStroke(1.dp, appColors.primary.copy(alpha = 0.5f))
                                    )
                                }

                                // Distinct folder filter chips
                                items(distinctFolders, key = { it.first }) { folderItem ->
                                    val isSelected = selectedFolderFilter.equals(folderItem.first, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                viewModel.setSelectedFolderFilter(null)
                                            } else {
                                                viewModel.setSelectedFolderFilter(folderItem.first)
                                            }
                                        },
                                        label = { Text("📁 ${folderItem.first} (${folderItem.second})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = appColors.primary,
                                            selectedLabelColor = Color.Black,
                                            containerColor = appColors.surfaceCard,
                                            labelColor = appColors.textSecondary
                                        )
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = selectedFilter == LibraryFilter.HI_RES,
                                        onClick = { selectedFilter = LibraryFilter.HI_RES },
                                        label = { Text("24-bit Hi-Res", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = appColors.primary,
                                            selectedLabelColor = Color.Black,
                                            containerColor = appColors.surfaceCard,
                                            labelColor = appColors.textSecondary
                                        )
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = selectedFilter == LibraryFilter.FAVORITES,
                                        onClick = { selectedFilter = LibraryFilter.FAVORITES },
                                        label = { Text("Favorites", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
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

                        // Active Folder Indicator Banner
                        if (selectedFolderFilter != null) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = appColors.surfaceCard,
                                    border = BorderStroke(1.dp, appColors.primary.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = appColors.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Folder: $selectedFolderFilter",
                                                    color = appColors.textPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${filteredTracks.size} songs loaded",
                                                    color = appColors.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (filteredTracks.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { filteredTracks.firstOrNull()?.let { viewModel.playTrackFromList(it, filteredTracks) } },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Folder", tint = appColors.primary)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (filteredTracks.isNotEmpty()) viewModel.playTrackFromList(null, filteredTracks, forceShuffle = true)
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Folder", tint = appColors.primary)
                                                }
                                            }
                                            IconButton(
                                                onClick = { viewModel.setSelectedFolderFilter(null) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear Folder Filter", tint = appColors.textSecondary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Current Count & Sort Indicator
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${filteredTracks.size} tracks • Sorted by ${sortField.displayName}",
                                    color = appColors.textSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    text = if (sortDirection == SortDirection.ASCENDING) "▲ ASC" else "▼ DESC",
                                    color = appColors.primary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        sortDirection = if (sortDirection == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
                                    }
                                )
                            }
                        }
                    }

                    // Empty State or Skeleton or List
                    if (isLibraryScanning && tracks.isEmpty()) {
                        item {
                            TrackListSkeleton(count = 7)
                        }
                    } else if (tracks.isEmpty()) {
                        item {
                            ObsidianEmptyState(
                                icon = Icons.Default.LibraryMusic,
                                title = "No Tracks In Library",
                                subtitle = "Select a music folder or import local songs to enjoy high-resolution lossless playback.",
                                actionLabel = "Add Music Folder",
                                actionIcon = Icons.Default.FolderOpen,
                                onActionClick = { onManageFolders() },
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    } else if (filteredTracks.isEmpty()) {
                        item {
                            ObsidianEmptyState(
                                icon = Icons.Default.SearchOff,
                                title = "No matching tracks",
                                subtitle = if (searchQuery.isNotBlank()) "No tracks matched \"$searchQuery\" in your library." else "No tracks match the selected filters.",
                                actionLabel = "Clear Filter",
                                actionIcon = Icons.Default.Clear,
                                onActionClick = {
                                    searchQuery = ""
                                    selectedFilter = LibraryFilter.ALL
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = filteredTracks,
                            key = { _, track -> track.id },
                            contentType = { _, _ -> "track_row" }
                        ) { index, track ->
                            val isCurrentlyPlaying by remember(track.id) { derivedStateOf { playerStateState.value.currentTrack?.id == track.id } }
                            val isPlaying by remember(track.id) { derivedStateOf { isCurrentlyPlaying && playerStateState.value.isPlaying } }
                            val isSelected by remember(track.id) { derivedStateOf { track.id in selectedTrackIdsState.value } }

                            val onToggleSelect = remember(track.id) {
                                {
                                    val currentSelected = selectedTrackIdsState.value
                                    selectedTrackIds = if (track.id in currentSelected) currentSelected - track.id else currentSelected + track.id
                                }
                            }
                            val onPlay = remember(track.id, index, filteredTracks) {
                                {
                                    if (isSelectionMode) {
                                        val currentSelected = selectedTrackIdsState.value
                                        selectedTrackIds = if (track.id in currentSelected) currentSelected - track.id else currentSelected + track.id
                                    } else {
                                        if (isSearchExpanded || searchQuery.isNotEmpty()) {
                                            keyboardController?.hide()
                                            focusManager.clearFocus(force = false)
                                        }
                                        viewModel.playTrackFromList(track, filteredTracks, targetIndex = index)
                                    }
                                }
                            }
                            val onLongClick = remember(track.id) {
                                {
                                    isSelectionMode = true
                                    selectedTrackIds = selectedTrackIdsState.value + track.id
                                }
                            }
                            val onPlayNext = remember(track.id) {
                                {
                                    viewModel.playNextInQueue(track)
                                    Toast.makeText(context, "Will play next: '${track.title}'", Toast.LENGTH_SHORT).show()
                                }
                            }
                            val onAddToQueue = remember(track.id) {
                                {
                                    viewModel.addToQueue(track)
                                    Toast.makeText(context, "Added '${track.title}' to queue", Toast.LENGTH_SHORT).show()
                                }
                            }
                            val onAddToPlaylist = remember(track.id) {
                                {
                                    showAddToPlaylistDialog = track
                                }
                            }
                            val onEnterSelection = remember(track.id) {
                                {
                                    isSelectionMode = true
                                    selectedTrackIds = setOf(track.id)
                                }
                            }
                            val onToggleFavorite = remember(track.id, track.isFavorite) {
                                { viewModel.toggleFavorite(track) }
                            }
                            val onOpenTagInspector = remember(track.id) {
                                { viewModel.openTagInspector(track) }
                            }
                            val onOpenDsp = remember {
                                { showDspSheet = true }
                            }
                            val onOpenSleepTimer = remember {
                                { showSleepTimerSheet = true }
                            }
                            val onShare = remember(track.id, track.title, track.artist) {
                                {
                                    try {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Listening to ${track.title} by ${track.artist}")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                                    } catch (e: Exception) {
                                        com.anuj.obsidian.ObsidianCrashLogger.logException(e, "LibraryScreen.LibraryScreen - attempt")
                                    }
                                }
                            }
                            val onDelete = remember(track.id, track.localFilePath) {
                                { trackToDelete = track }
                            }

                            ObsidianTrackRow(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard,
                                        fadeOutSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick,
                                        placementSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmoothIntOffset
                                    ),
                                track = track,
                                isCurrentlyPlaying = isCurrentlyPlaying,
                                isPlaying = isPlaying,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                sleepTimerMinutes = sleepTimerMinutes,
                                onToggleSelect = onToggleSelect,
                                onPlay = onPlay,
                                onLongClick = onLongClick,
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                onAddToPlaylist = onAddToPlaylist,
                                onEnterSelection = onEnterSelection,
                                onToggleFavorite = onToggleFavorite,
                                onOpenTagInspector = onOpenTagInspector,
                                onIdentifyTrack = {
                                    viewModel.identifyAndEnrichSingleTrack(track)
                                    Toast.makeText(context, "Analyzing audio fingerprint for '${track.title}'...", Toast.LENGTH_SHORT).show()
                                },
                                onStartArtistRadio = {
                                    viewModel.startArtistRadio(track.artist, track.title)
                                    Toast.makeText(context, "Starting Artist Radio for ${track.artist}...", Toast.LENGTH_SHORT).show()
                                },
                                onOpenDsp = onOpenDsp,
                                onOpenSleepTimer = onOpenSleepTimer,
                                onShare = onShare,
                                onDelete = onDelete,
                                showLayoutBounds = settings.showLayoutBounds
                            )
                        }
                    }
                }
            }

            item {
                // Bottom spacing to ensure floating pill doesn't obstruct content
                Spacer(modifier = Modifier.height(110.dp))
            }
        }

        // Obsidian Proportional Fast-Scroller (Smooth Drag, Auto-Hiding Handle & Conditional Alphabet Badge)
        if (currentLibraryTab == 0 && filteredTracks.isNotEmpty()) {
            var isDraggingFastScroll by remember { mutableStateOf(false) }
            var isFastScrollVisible by remember { mutableStateOf(false) }
            var fastScrollTouchY by remember { mutableFloatStateOf(0f) }
            var dragNormalizedY by remember { mutableFloatStateOf(0f) }
            var containerHeightPx by remember { mutableFloatStateOf(1f) }
            var lastTargetTrackIdx by remember { mutableIntStateOf(-1) }
            var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

            val coroutineScope = rememberCoroutineScope()
            val totalTracks = filteredTracks.size

            val headerCount = remember(tracks.size, isSearchExpanded, searchQuery, selectedFolderFilter) {
                var count = 2
                if (tracks.isNotEmpty() && (isSearchExpanded || searchQuery.isNotEmpty())) count += 1
                if (tracks.isNotEmpty()) count += 1
                if (selectedFolderFilter != null) count += 1
                if (tracks.isNotEmpty()) count += 1
                count
            }

            LaunchedEffect(listState.firstVisibleItemIndex, isDraggingFastScroll) {
                if (listState.isScrollInProgress || isDraggingFastScroll) {
                    isFastScrollVisible = true
                } else {
                    delay(1800)
                    isFastScrollVisible = false
                }
            }

            val currentTrackIndex = remember(headerCount, totalTracks, isDraggingFastScroll) {
                derivedStateOf {
                    if (isDraggingFastScroll) {
                        (dragNormalizedY * (totalTracks - 1)).toInt().coerceIn(0, totalTracks - 1)
                    } else {
                        (listState.firstVisibleItemIndex - headerCount).coerceIn(0, (totalTracks - 1).coerceAtLeast(0))
                    }
                }
            }

            // Smooth fractional scroll position including item pixel offset to eliminate stepped jumps
            val scrollFraction = remember(totalTracks) {
                derivedStateOf {
                    if (totalTracks <= 1) 0f
                    else {
                        val firstIndex = (listState.firstVisibleItemIndex - headerCount).coerceAtLeast(0)
                        val offsetFraction = if (listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                            val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                            (listState.firstVisibleItemScrollOffset.toFloat() / itemSize.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        } else 0f
                        ((firstIndex + offsetFraction) / totalTracks.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }

            val targetNormalizedY = remember(isDraggingFastScroll) {
                derivedStateOf {
                    if (isDraggingFastScroll) dragNormalizedY else scrollFraction.value
                }
            }

            val isAlphabeticalSelected = sortField == SortField.ALPHABETICAL
            val badgeText = remember(isAlphabeticalSelected, filteredTracks) {
                derivedStateOf {
                    val track = filteredTracks.getOrNull(currentTrackIndex.value)
                    if (!isAlphabeticalSelected || track == null) ""
                    else track.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                }
            }

            val isFastScrollActive = isFastScrollVisible || isDraggingFastScroll
            val handleWidth by animateDpAsState(
                targetValue = if (isDraggingFastScroll) 10.dp else 6.dp,
                animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmoothDp,
                label = "fast_scroll_handle_w"
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = isFastScrollActive,
                enter = fadeIn(com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard),
                exit = fadeOut(com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard),
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 100.dp, bottom = 120.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // Fast Scroll Touch Track & Handle
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .fillMaxHeight(0.75f)
                            .onGloballyPositioned { containerHeightPx = it.size.height.toFloat().coerceAtLeast(1f) }
                            .pointerInput(filteredTracks, headerCount) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isDraggingFastScroll = true
                                        fastScrollTouchY = offset.y
                                        dragNormalizedY = (offset.y / containerHeightPx).coerceIn(0f, 1f)
                                        val targetTrackIdx = (dragNormalizedY * (totalTracks - 1)).toInt().coerceIn(0, totalTracks - 1)
                                        val totalItems = listState.layoutInfo.totalItemsCount
                                        val targetItemIdx = (headerCount + targetTrackIdx).coerceIn(0, (totalItems - 1).coerceAtLeast(0))
                                        if (targetItemIdx != lastTargetTrackIdx) {
                                            lastTargetTrackIdx = targetItemIdx
                                            scrollJob?.cancel()
                                            scrollJob = coroutineScope.launch {
                                                try { 
                                                    listState.scrollToItem(targetItemIdx) 
                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "LibraryScreen fast scroll scrollToItem")
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = { 
                                        isDraggingFastScroll = false 
                                    },
                                    onDragCancel = { 
                                        isDraggingFastScroll = false 
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        fastScrollTouchY += dragAmount.y
                                        dragNormalizedY = (fastScrollTouchY / containerHeightPx).coerceIn(0f, 1f)
                                        val targetTrackIdx = (dragNormalizedY * (totalTracks - 1)).toInt().coerceIn(0, totalTracks - 1)
                                        val totalItems = listState.layoutInfo.totalItemsCount
                                        val targetItemIdx = (headerCount + targetTrackIdx).coerceIn(0, (totalItems - 1).coerceAtLeast(0))
                                        if (targetItemIdx != lastTargetTrackIdx) {
                                            lastTargetTrackIdx = targetItemIdx
                                            scrollJob?.cancel()
                                            scrollJob = coroutineScope.launch {
                                                try { 
                                                    listState.scrollToItem(targetItemIdx) 
                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "LibraryScreen fast scroll scrollToItem onDrag")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .align(Alignment.Center)
                                .background(appColors.surfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                        )

                        // Draggable Obsidian Handle Pill with dynamic width and smooth positioning
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset { 
                                    androidx.compose.ui.unit.IntOffset(
                                        0, 
                                        (targetNormalizedY.value * (containerHeightPx - 44.dp.toPx())).toInt().coerceAtLeast(0)
                                    ) 
                                }
                                .width(handleWidth)
                                .height(44.dp)
                                .background(
                                    color = appColors.primary,
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .shadow(6.dp, RoundedCornerShape(5.dp), ambientColor = appColors.primary, spotColor = appColors.primary)
                        )
                    }

                    // Floating Obsidian Section Badge Pill - ONLY shown when alphabetical sort is selected in library
                    AnimatedVisibility(
                        visible = isDraggingFastScroll && isAlphabeticalSelected && badgeText.value.isNotBlank(),
                        enter = fadeIn(com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick) + scaleIn(com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick),
                        exit = fadeOut(com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick) + scaleOut(com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { 
                                androidx.compose.ui.unit.IntOffset(
                                    0, 
                                    (targetNormalizedY.value * (containerHeightPx - 52.dp.toPx())).toInt().coerceAtLeast(0)
                                ) 
                            }
                            .padding(end = 36.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = appColors.surfaceElevated.copy(alpha = 0.96f),
                            border = BorderStroke(1.5.dp, appColors.primary),
                            shadowElevation = 8.dp
                        ) {
                            Box(
                                modifier = Modifier.size(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = badgeText.value,
                                    color = appColors.primary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selection Mode Bottom Floating Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val selectedTracks = remember(selectedTrackIds, tracks) {
                tracks.filter { it.id in selectedTrackIds }
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp, start = 14.dp, end = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = appColors.surfaceElevated.copy(alpha = 0.98f),
                    border = BorderStroke(1.5.dp, appColors.primary),
                    shadowElevation = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Select All / Deselect All Toggle Button
                            Surface(
                                onClick = {
                                    val allFilteredIds = filteredTracks.map { it.id }.toSet()
                                    selectedTrackIds = if (selectedTrackIds.size == allFilteredIds.size) emptySet() else allFilteredIds
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = appColors.primary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, appColors.primary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .height(36.dp)
                                    .pressSpring(scaleDown = 0.95f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (selectedTrackIds.size == filteredTracks.size && filteredTracks.isNotEmpty()) Icons.Default.Deselect else Icons.Default.SelectAll,
                                        contentDescription = "Toggle Select All",
                                        tint = appColors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedTrackIds.size == filteredTracks.size && filteredTracks.isNotEmpty()) "None" else "All",
                                        color = appColors.primary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "${selectedTrackIds.size} SELECTED",
                                    color = appColors.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (selectedTrackIds.isEmpty()) "Tap songs to select" else "Queue options",
                                    color = appColors.textPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    if (selectedTracks.isNotEmpty()) {
                                        viewModel.addTracksToNextQueue(selectedTracks)
                                        Toast.makeText(context, "Added ${selectedTracks.size} tracks to Play Next", Toast.LENGTH_SHORT).show()
                                        isSelectionMode = false
                                        selectedTrackIds = emptySet()
                                    }
                                },
                                enabled = selectedTracks.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedTracks.isNotEmpty()) appColors.primary else appColors.surfaceCard,
                                modifier = Modifier
                                    .height(36.dp)
                                    .pressSpring(scaleDown = 0.95f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (selectedTracks.isNotEmpty()) Color.Black else appColors.textMuted, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play Next", color = if (selectedTracks.isNotEmpty()) Color.Black else appColors.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                onClick = {
                                    if (selectedTracks.isNotEmpty()) {
                                        viewModel.addTracksToQueue(selectedTracks)
                                        Toast.makeText(context, "Added ${selectedTracks.size} tracks to Queue", Toast.LENGTH_SHORT).show()
                                        isSelectionMode = false
                                        selectedTrackIds = emptySet()
                                    }
                                },
                                enabled = selectedTracks.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                color = appColors.surfaceCard,
                                border = BorderStroke(1.dp, appColors.surfaceBorder),
                                modifier = Modifier
                                    .height(36.dp)
                                    .pressSpring(scaleDown = 0.95f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = if (selectedTracks.isNotEmpty()) appColors.primary else appColors.textMuted, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", color = if (selectedTracks.isNotEmpty()) appColors.textPrimary else appColors.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                onClick = {
                                    if (selectedTracks.isNotEmpty()) {
                                        showBatchTagSheet = true
                                    }
                                },
                                enabled = selectedTracks.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                color = appColors.surfaceCard,
                                border = BorderStroke(1.dp, appColors.surfaceBorder),
                                modifier = Modifier
                                    .height(36.dp)
                                    .pressSpring(scaleDown = 0.95f)
                                    .testTag("batch_edit_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = if (selectedTracks.isNotEmpty()) appColors.secondary else appColors.textMuted, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tags", color = if (selectedTracks.isNotEmpty()) appColors.textPrimary else appColors.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                onClick = {
                                    isSelectionMode = false
                                    selectedTrackIds = emptySet()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = appColors.surfaceCard,
                                border = BorderStroke(1.dp, appColors.surfaceBorder),
                                modifier = Modifier
                                    .size(36.dp)
                                    .pressSpring(scaleDown = 0.95f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = appColors.textMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isSelectionMode && tracks.isNotEmpty() && currentLibraryTab == 0) {
            // Floating Quick Action Dock
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = appColors.surfaceCard.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, appColors.surfaceBorder.copy(alpha = 0.8f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier.testTag("obsidian_floating_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Shuffle All
                        Surface(
                            onClick = {
                                if (filteredTracks.isNotEmpty()) {
                                    viewModel.playTrackFromList(null, filteredTracks, forceShuffle = true)
                                    Toast.makeText(context, "Intelligent Shuffle started...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = CircleShape,
                            color = appColors.primary.copy(alpha = 0.16f),
                            modifier = Modifier
                                .size(38.dp)
                                .pressSpring(scaleDown = 0.92f)
                                .testTag("floating_shuffle_btn")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle All",
                                    tint = appColors.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // 2. Play All
                        Surface(
                            onClick = {
                                filteredTracks.firstOrNull()?.let { viewModel.playTrackFromList(it, filteredTracks) }
                            },
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier
                                .size(38.dp)
                                .pressSpring(scaleDown = 0.92f)
                                .testTag("floating_play_all_btn")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play All",
                                    tint = appColors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Subtle Divider between playback and management tools
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(1.dp)
                                .height(20.dp)
                                .background(appColors.surfaceBorder.copy(alpha = 0.7f))
                        )

                        // 3. Queue All
                        Surface(
                            onClick = {
                                if (filteredTracks.isNotEmpty()) {
                                    viewModel.addTracksToQueue(filteredTracks)
                                    Toast.makeText(context, "Added ${filteredTracks.size} tracks to Queue", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier
                                .size(38.dp)
                                .pressSpring(scaleDown = 0.92f)
                                .testTag("floating_queue_all_btn")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Queue All",
                                    tint = appColors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 4. Search Toggle
                        Surface(
                            onClick = { isSearchExpanded = !isSearchExpanded },
                            shape = CircleShape,
                            color = if (isSearchExpanded) appColors.primary.copy(alpha = 0.16f) else Color.Transparent,
                            modifier = Modifier
                                .size(38.dp)
                                .pressSpring(scaleDown = 0.92f)
                                .testTag("floating_search_btn")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchExpanded) appColors.primary else appColors.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // 5. Multi-Select Toggle
                        Surface(
                            onClick = { isSelectionMode = true },
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier
                                .size(38.dp)
                                .pressSpring(scaleDown = 0.92f)
                                .testTag("floating_select_mode_btn")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Select Songs",
                                    tint = appColors.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // 6. Sort & Options Menu
                        Box {
                            Surface(
                                onClick = { showMoreMenu = true },
                                shape = CircleShape,
                                color = if (showMoreMenu) appColors.primary.copy(alpha = 0.16f) else Color.Transparent,
                                modifier = Modifier
                                    .size(38.dp)
                                    .pressSpring(scaleDown = 0.92f)
                                    .testTag("floating_more_btn")
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Options",
                                        tint = if (showMoreMenu) appColors.primary else appColors.textSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.background(appColors.surfaceCard)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sort Ascending (▲)", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        sortDirection = SortDirection.ASCENDING
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort Descending (▼)", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        sortDirection = SortDirection.DESCENDING
                                        showMoreMenu = false
                                    }
                                )
                                HorizontalDivider(color = appColors.surfaceBorder, modifier = Modifier.padding(vertical = 4.dp))
                                SortField.values().forEach { field ->
                                    val isSelected = sortField == field
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = field.displayName,
                                                color = if (isSelected) appColors.primary else appColors.textPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = appColors.primary)
                                            } else {
                                                Spacer(modifier = Modifier.size(24.dp))
                                            }
                                        },
                                        onClick = {
                                            sortField = field
                                            showMoreMenu = false
                                        }
                                    )
                                }
                                HorizontalDivider(color = appColors.surfaceBorder, modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Auto-Enrich Missing Tags & Art", color = FlacLosslessCyan, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FlacLosslessCyan) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.showEnrichmentSheet(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Re-scan Music Folders", color = appColors.primary) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        showMoreMenu = false
                                        showChooseFolderSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showChooseFolderSheet) {
            ChooseFolderSheet(
                viewModel = viewModel,
                onDismiss = { showChooseFolderSheet = false },
                onOpenFolderExplorer = { onManageFolders() }
            )
        }

        if (showDspSheet) {
            ObsidianDspBottomSheet(
                onDismiss = { showDspSheet = false }
            )
        }

        if (showSleepTimerSheet) {
            SleepTimerBottomSheet(
                onDismiss = { showSleepTimerSheet = false },
                onSetTimer = { minutes -> viewModel.startSleepTimer(minutes) },
                onCancelTimer = { viewModel.cancelSleepTimer() },
                currentTimerMinutes = sleepTimerMinutes
            )
        }

        if (showBatchTagSheet) {
            val selectedTracks = remember(selectedTrackIds, tracks) {
                tracks.filter { it.id in selectedTrackIds }
            }
            com.anuj.obsidian.ui.components.BatchTagEditorBottomSheet(
                tracks = selectedTracks,
                batchTagController = viewModel.batchTagController,
                onDismiss = {
                    showBatchTagSheet = false
                    isSelectionMode = false
                    selectedTrackIds = emptySet()
                }
            )
        }
    }
    }
}

