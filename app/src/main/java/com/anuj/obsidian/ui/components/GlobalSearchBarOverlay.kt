package com.anuj.obsidian.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.anuj.obsidian.data.model.GlobalSearchFilter
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.animation.bounceClickable
import com.anuj.obsidian.ui.animation.pressSpring
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.FlacLosslessCyan
import com.anuj.obsidian.ui.theme.ObsidianTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchBarOverlay(
    viewModel: ObsidianViewModel,
    onDismiss: () -> Unit
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val searchState by viewModel.globalSearchResults.collectAsStateWithLifecycle()
    val activeFilter = searchState.selectedFilter
    val query = searchState.query

    BackHandler {
        onDismiss()
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("global_search_overlay"),
        color = Color(0xFF090B10)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            // Search Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("global_search_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = appColors.textPrimary
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setGlobalSearchQuery(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("global_search_input"),
                    placeholder = {
                        Text(
                            text = "Search tracks, artists, albums, genres...",
                            color = appColors.textMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = FlacLosslessCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.setGlobalSearchQuery("") },
                                    modifier = Modifier.size(32.dp).testTag("global_search_clear_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = appColors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    onDismiss()
                                    viewModel.showSongRecognition(true)
                                },
                                modifier = Modifier.size(34.dp).testTag("global_search_mic_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Song Recognizer",
                                    tint = FlacHiResGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = appColors.surfaceElevated,
                        unfocusedContainerColor = appColors.surfaceCard,
                        focusedBorderColor = FlacLosslessCyan,
                        unfocusedBorderColor = appColors.surfaceBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(GlobalSearchFilter.values()) { filter ->
                    val isSelected = activeFilter == filter
                    val badgeCount = when (filter) {
                        GlobalSearchFilter.ALL -> searchState.totalCount
                        GlobalSearchFilter.TRACKS -> searchState.matchingTracks.size
                        GlobalSearchFilter.ALBUMS -> searchState.matchingAlbums.size
                        GlobalSearchFilter.ARTISTS -> searchState.matchingArtists.size
                        GlobalSearchFilter.GENRES -> searchState.matchingGenres.size
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setGlobalSearchFilter(filter) },
                        label = {
                            Text(
                                text = if (query.isNotBlank()) "${filter.label} ($badgeCount)" else filter.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FlacLosslessCyan.copy(alpha = 0.2f),
                            selectedLabelColor = FlacLosslessCyan,
                            containerColor = appColors.surfaceCard,
                            labelColor = appColors.textSecondary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) FlacLosslessCyan.copy(alpha = 0.6f) else appColors.surfaceBorder
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Results Content
            if (query.isBlank()) {
                GlobalSearchEmptyState(
                    onSuggestionClick = { suggestion ->
                        viewModel.setGlobalSearchQuery(suggestion)
                    },
                    onEnrichClick = {
                        onDismiss()
                        viewModel.showEnrichmentSheet(true)
                    }
                )
            } else if (searchState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FlacLosslessCyan, modifier = Modifier.size(36.dp))
                }
            } else if (searchState.totalCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = appColors.textMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No results found for '$query'",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching by another title, artist, album, or genre.",
                            color = appColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Artists Section
                    if ((activeFilter == GlobalSearchFilter.ALL || activeFilter == GlobalSearchFilter.ARTISTS) && searchState.matchingArtists.isNotEmpty()) {
                        item {
                            SectionHeader(title = "ARTISTS (${searchState.matchingArtists.size})")
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                items(searchState.matchingArtists, key = { it.name }) { artist ->
                                    ArtistSearchCard(
                                        artist = artist,
                                        onClick = {
                                            viewModel.startArtistRadio(artist.name, "")
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Albums Section
                    if ((activeFilter == GlobalSearchFilter.ALL || activeFilter == GlobalSearchFilter.ALBUMS) && searchState.matchingAlbums.isNotEmpty()) {
                        item {
                            SectionHeader(title = "ALBUMS (${searchState.matchingAlbums.size})")
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                items(searchState.matchingAlbums, key = { "${it.title}_${it.artist}" }) { album ->
                                    AlbumSearchCard(
                                        album = album,
                                        onClick = {
                                            if (album.tracks.isNotEmpty()) {
                                                viewModel.playTrack(album.tracks.first())
                                                onDismiss()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Tracks Section
                    if ((activeFilter == GlobalSearchFilter.ALL || activeFilter == GlobalSearchFilter.TRACKS) && searchState.matchingTracks.isNotEmpty()) {
                        item {
                            SectionHeader(title = "TRACKS (${searchState.matchingTracks.size})")
                        }
                        items(searchState.matchingTracks, key = { it.id }) { track ->
                            TrackSearchRow(
                                track = track,
                                onPlay = {
                                    viewModel.playTrack(track)
                                    onDismiss()
                                },
                                onPlayNext = {
                                    viewModel.playNextInQueue(track)
                                },
                                onInspectTags = {
                                    onDismiss()
                                    viewModel.openTagInspector(track)
                                }
                            )
                        }
                    }

                    // Bottom padding
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = FlacLosslessCyan,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
    )
}

@Composable
private fun ArtistSearchCard(
    artist: ArtistInfo,
    onClick: () -> Unit
) {
    val appColors = ObsidianTheme.colors

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = appColors.surfaceCard,
        border = BorderStroke(1.dp, appColors.surfaceBorder),
        modifier = Modifier.width(130.dp).testTag("artist_card_${artist.name}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(FlacLosslessCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = FlacLosslessCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = artist.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.tracks.size} tracks",
                color = appColors.textSecondary,
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun AlbumSearchCard(
    album: AlbumInfo,
    onClick: () -> Unit
) {
    val appColors = ObsidianTheme.colors

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = appColors.surfaceCard,
        border = BorderStroke(1.dp, appColors.surfaceBorder),
        modifier = Modifier.width(130.dp).testTag("album_card_${album.title}")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .size(114.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF191B24)),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverArtUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(album.coverArtUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        tint = FlacHiResGold,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = album.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artist,
                color = appColors.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrackSearchRow(
    track: DownloadedTrack,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onInspectTags: () -> Unit
) {
    val appColors = ObsidianTheme.colors

    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(12.dp),
        color = appColors.surfaceCard,
        border = BorderStroke(1.dp, appColors.surfaceBorder),
        modifier = Modifier.fillMaxWidth().testTag("search_track_row_${track.id}")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E212B)),
                contentAlignment = Alignment.Center
            ) {
                if (track.albumArtUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(track.albumArtUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = FlacLosslessCyan)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${track.artist} • ${track.album}",
                        color = appColors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = track.format,
                        color = FlacHiResGold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = FlacLosslessCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GlobalSearchEmptyState(
    onSuggestionClick: (String) -> Unit,
    onEnrichClick: () -> Unit
) {
    val appColors = ObsidianTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "QUICK SUGGESTIONS",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        val suggestions = listOf("FLAC", "Hi-Res", "Lossless", "Rock", "Pop", "Jazz", "Electronic")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suggestions) { tag ->
                Surface(
                    onClick = { onSuggestionClick(tag) },
                    shape = RoundedCornerShape(10.dp),
                    color = appColors.surfaceCard,
                    border = BorderStroke(1.dp, appColors.surfaceBorder)
                ) {
                    Text(
                        text = tag,
                        color = FlacLosslessCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Auto-Enrich Callout
        Surface(
            onClick = onEnrichClick,
            shape = RoundedCornerShape(14.dp),
            color = FlacLosslessCyan.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, FlacLosslessCyan.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = FlacLosslessCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Identify Missing Library Tags",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scan audio acoustic peaks to fix unknown artists and fetch Hi-Res covers.",
                        color = appColors.textSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }
}
