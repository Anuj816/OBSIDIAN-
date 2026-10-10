package com.anuj.obsidian.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anuj.obsidian.data.model.AlbumInfo
import com.anuj.obsidian.data.model.ArtistInfo
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.GenreInfo
import com.anuj.obsidian.data.model.Playlist
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.animation.bounceClickable
import com.anuj.obsidian.ui.animation.pressSpring
import com.anuj.obsidian.ui.components.formatSecToTime
import com.anuj.obsidian.ui.components.hasRealArtwork
import com.anuj.obsidian.ui.theme.ObsidianTheme

@Composable
fun AlbumGrid(
    albums: List<AlbumInfo>,
    onSelectAlbum: (AlbumInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors

    if (albums.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No albums found in library.", color = appColors.textSecondary, fontSize = 14.sp)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums, key = { "${it.title}_${it.artist}" }) { album ->
                AlbumCard(
                    album = album,
                    onClick = { onSelectAlbum(album) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }
}

@Composable
fun AlbumCard(
    album: AlbumInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .bounceClickable(scaleDown = 0.96f) { onClick() },
        color = appColors.surfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, appColors.surfaceBorder)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                val representativeTrack = album.tracks.firstOrNull { hasRealArtwork(it) }
                if (hasRealArtwork(album.coverArtUrl) || representativeTrack != null) {
                    val artData: Any = if (hasRealArtwork(album.coverArtUrl)) album.coverArtUrl else representativeTrack ?: ""
                    val imageRequest = remember(artData, context) {
                        ImageRequest.Builder(context)
                            .data(artData)
                            .crossfade(true)
                            .size(260, 260)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        tint = appColors.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(44.dp)
                    )
                }

                if (album.isLossless) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, appColors.primary.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "HI-RES",
                            color = appColors.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = album.title,
                color = appColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = album.artist,
                color = appColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${album.tracks.size} tracks • ${formatSecToTime(album.totalDurationSeconds)}",
                color = appColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AlbumDetailView(
    album: AlbumInfo,
    onBack: () -> Unit,
    viewModel: ObsidianViewModel,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    val sortedAlbumTracks = remember(album.tracks) {
        album.tracks.sortedWith(
            compareBy<DownloadedTrack> { if (it.discNumber > 0) it.discNumber else 1 }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else 999 }
                .thenBy { it.title.lowercase() }
        )
    }
    val hasMultipleDiscs = remember(sortedAlbumTracks) {
        sortedAlbumTracks.map { if (it.discNumber > 0) it.discNumber else 1 }.distinct().size > 1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.pressSpring()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Albums",
                    tint = appColors.primary
                )
            }
            Text(
                text = "Album Details",
                color = appColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = appColors.surfaceCard,
                    border = BorderStroke(1.dp, appColors.surfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(appColors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            val representativeTrack = album.tracks.firstOrNull { hasRealArtwork(it) }
                            if (hasRealArtwork(album.coverArtUrl) || representativeTrack != null) {
                                val artData: Any = if (hasRealArtwork(album.coverArtUrl)) album.coverArtUrl else representativeTrack ?: ""
                                val imageRequest = remember(artData, context) {
                                    ImageRequest.Builder(context)
                                        .data(artData)
                                        .crossfade(true)
                                        .size(240, 240)
                                        .build()
                                }
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = album.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = appColors.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.title,
                                color = appColors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = album.artist,
                                color = appColors.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            if (album.year.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Released ${album.year}",
                                    color = appColors.textMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${album.tracks.size} tracks • ${formatSecToTime(album.totalDurationSeconds)} • ${String.format(java.util.Locale.US, "%.1f MB", album.totalSizeMb)}",
                                color = appColors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            sortedAlbumTracks.firstOrNull()?.let { firstT ->
                                viewModel.playTrackFromList(
                                    firstT,
                                    sortedAlbumTracks,
                                    sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.ALBUM,
                                    sourceId = album.title,
                                    sourceTitle = album.title
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.primary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play Album", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (sortedAlbumTracks.isNotEmpty()) {
                                viewModel.playTrackFromList(
                                    null,
                                    sortedAlbumTracks,
                                    forceShuffle = true,
                                    sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.ALBUM,
                                    sourceId = album.title,
                                    sourceTitle = album.title
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, appColors.surfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = appColors.surfaceCard,
                            contentColor = appColors.textPrimary
                        )
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }

                    IconButton(
                        onClick = {
                            if (sortedAlbumTracks.isNotEmpty()) {
                                viewModel.addTracksToQueue(sortedAlbumTracks)
                                Toast.makeText(context, "Added ${sortedAlbumTracks.size} tracks to Queue", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(appColors.surfaceCard)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue All", tint = appColors.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(
                sortedAlbumTracks,
                key = { _, track -> track.id },
                contentType = { _, _ -> "album_track_item" }
            ) { index, track ->
                val isCurrentlyPlaying = playerState.currentTrack?.id == track.id
                val prevTrack = if (index > 0) sortedAlbumTracks[index - 1] else null
                val showDiscHeader = hasMultipleDiscs && (index == 0 || (prevTrack != null && prevTrack.discNumber != track.discNumber))

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (showDiscHeader) {
                        val discNum = if (track.discNumber > 0) track.discNumber else 1
                        Surface(
                            color = appColors.surfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = "DISC $discNum",
                                color = appColors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            viewModel.playTrackFromList(
                                track,
                                sortedAlbumTracks,
                                sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.ALBUM,
                                sourceId = album.title,
                                sourceTitle = album.title
                            )
                        },
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth(),
                        color = if (isCurrentlyPlaying) appColors.primary.copy(alpha = 0.12f) else appColors.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrentlyPlaying) appColors.primary.copy(alpha = 0.6f) else appColors.surfaceBorder
                        )
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (track.trackNumber > 0) "${track.trackNumber}" else "${index + 1}",
                            color = if (isCurrentlyPlaying) appColors.primary else appColors.textMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(28.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCurrentlyPlaying) appColors.primary else appColors.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatSecToTime(track.durationSeconds)} • ${track.format} • ${track.bitDepth}",
                                color = appColors.textMuted,
                                fontSize = 11.5.sp
                            )
                        }

                        IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (track.isFavorite) appColors.primary else appColors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = {
                            viewModel.addToQueue(track)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to Queue",
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun ArtistGrid(
    artists: List<ArtistInfo>,
    onSelectArtist: (ArtistInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors

    if (artists.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No artists found in library.", color = appColors.textSecondary, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(artists, key = { it.name }) { artist ->
                ArtistRowCard(
                    artist = artist,
                    onClick = { onSelectArtist(artist) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }
}

@Composable
fun ArtistRowCard(
    artist: ArtistInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .bounceClickable(scaleDown = 0.98f) { onClick() },
        color = appColors.surfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, appColors.surfaceBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(appColors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                val representativeTrack = artist.tracks.firstOrNull { hasRealArtwork(it) }
                if (representativeTrack != null) {
                    val imageRequest = remember(representativeTrack.id, context) {
                        ImageRequest.Builder(context)
                            .data(representativeTrack)
                            .crossfade(true)
                            .size(120, 120)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = appColors.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    color = appColors.textPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${artist.tracks.size} songs • ${artist.albumCount} album${if (artist.albumCount > 1) "s" else ""}",
                    color = appColors.textSecondary,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "View Artist",
                tint = appColors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ArtistDetailView(
    artist: ArtistInfo,
    onBack: () -> Unit,
    viewModel: ObsidianViewModel,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.pressSpring()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Artists",
                    tint = appColors.primary
                )
            }
            Text(
                text = "Artist Details",
                color = appColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = appColors.surfaceCard,
                    border = BorderStroke(1.dp, appColors.surfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(appColors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            val representativeTrack = artist.tracks.firstOrNull { hasRealArtwork(it) }
                            if (representativeTrack != null) {
                                val imageRequest = remember(representativeTrack.id, context) {
                                    ImageRequest.Builder(context)
                                        .data(representativeTrack)
                                        .crossfade(true)
                                        .size(200, 200)
                                        .build()
                                }
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = artist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = appColors.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = artist.name,
                            color = appColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${artist.tracks.size} tracks • ${artist.albumCount} albums • ${formatSecToTime(artist.totalDurationSeconds)}",
                            color = appColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            artist.tracks.firstOrNull()?.let { firstT ->
                                viewModel.playTrackFromList(
                                    firstT,
                                    artist.tracks,
                                    sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.ARTIST,
                                    sourceId = artist.name,
                                    sourceTitle = artist.name
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.primary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play Artist", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (artist.tracks.isNotEmpty()) {
                                viewModel.playTrackFromList(
                                    null,
                                    artist.tracks,
                                    forceShuffle = true,
                                    sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.ARTIST,
                                    sourceId = artist.name,
                                    sourceTitle = artist.name
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, appColors.surfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = appColors.surfaceCard,
                            contentColor = appColors.textPrimary
                        )
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }

                    IconButton(
                        onClick = {
                            if (artist.tracks.isNotEmpty()) {
                                viewModel.addTracksToQueue(artist.tracks)
                                Toast.makeText(context, "Added ${artist.tracks.size} tracks to Queue", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(appColors.surfaceCard)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue All", tint = appColors.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            items(artist.tracks, key = { it.id }) { track ->
                val isCurrentlyPlaying = playerState.currentTrack?.id == track.id
                Surface(
                    onClick = {
                        viewModel.playTrackFromList(
                            track,
                            artist.tracks,
                            sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.ARTIST,
                            sourceId = artist.name,
                            sourceTitle = artist.name
                        )
                    },
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth(),
                    color = if (isCurrentlyPlaying) appColors.primary.copy(alpha = 0.12f) else appColors.surfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isCurrentlyPlaying) appColors.primary.copy(alpha = 0.6f) else appColors.surfaceBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCurrentlyPlaying) appColors.primary else appColors.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${track.album.ifBlank { "Single" }} • ${formatSecToTime(track.durationSeconds)} • ${track.format}",
                                color = appColors.textMuted,
                                fontSize = 11.5.sp
                            )
                        }

                        IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (track.isFavorite) appColors.primary else appColors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = {
                            viewModel.addToQueue(track)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to Queue",
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenreGrid(
    genres: List<GenreInfo>,
    onSelectGenre: (GenreInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors

    if (genres.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No genres found in library.", color = appColors.textSecondary, fontSize = 14.sp)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(genres, key = { it.name }) { genre ->
                GenreCard(
                    genre = genre,
                    onClick = { onSelectGenre(genre) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }
}

@Composable
fun GenreCard(
    genre: GenreInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .bounceClickable(scaleDown = 0.96f) { onClick() },
        color = appColors.surfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, appColors.surfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(appColors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = appColors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = genre.name,
                color = appColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${genre.tracks.size} tracks",
                color = appColors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun GenreDetailView(
    genre: GenreInfo,
    onBack: () -> Unit,
    viewModel: ObsidianViewModel,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.pressSpring()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Genres",
                    tint = appColors.primary
                )
            }
            Text(
                text = "Genre: ${genre.name}",
                color = appColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            genre.tracks.firstOrNull()?.let { firstT ->
                                viewModel.playTrackFromList(
                                    firstT,
                                    genre.tracks,
                                    sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.GENRE,
                                    sourceId = genre.name,
                                    sourceTitle = genre.name
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.primary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (genre.tracks.isNotEmpty()) {
                                viewModel.playTrackFromList(
                                    null,
                                    genre.tracks,
                                    forceShuffle = true,
                                    sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.GENRE,
                                    sourceId = genre.name,
                                    sourceTitle = genre.name
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, appColors.surfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = appColors.surfaceCard,
                            contentColor = appColors.textPrimary
                        )
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            items(genre.tracks, key = { it.id }) { track ->
                val isCurrentlyPlaying = playerState.currentTrack?.id == track.id
                Surface(
                    onClick = {
                        viewModel.playTrackFromList(
                            track,
                            genre.tracks,
                            sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.GENRE,
                            sourceId = genre.name,
                            sourceTitle = genre.name
                        )
                    },
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth(),
                    color = if (isCurrentlyPlaying) appColors.primary.copy(alpha = 0.12f) else appColors.surfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isCurrentlyPlaying) appColors.primary.copy(alpha = 0.6f) else appColors.surfaceBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCurrentlyPlaying) appColors.primary else appColors.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${track.artist} • ${formatSecToTime(track.durationSeconds)} • ${track.format}",
                                color = appColors.textMuted,
                                fontSize = 11.5.sp
                            )
                        }

                        IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (track.isFavorite) appColors.primary else appColors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = {
                            viewModel.addToQueue(track)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to Queue",
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    onBack: () -> Unit,
    viewModel: ObsidianViewModel,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playlistTracks by viewModel.getPlaylistTracksFlow(playlist.id).collectAsStateWithLifecycle(initialValue = emptyList<DownloadedTrack>())
    val totalSec = remember(playlistTracks) { playlistTracks.sumOf { it.durationSeconds } }
    var showAddSongsSheet by remember { mutableStateOf(false) }

    if (showAddSongsSheet) {
        com.anuj.obsidian.ui.components.SelectSongsForQueueSheet(
            allTracks = allTracks,
            onAddNext = { selected ->
                viewModel.addTracksToPlaylist(playlist.id, selected.map { it.id })
                Toast.makeText(context, "Added ${selected.size} track(s) to playlist", Toast.LENGTH_SHORT).show()
                showAddSongsSheet = false
            },
            onAddEnd = { selected ->
                viewModel.addTracksToPlaylist(playlist.id, selected.map { it.id })
                Toast.makeText(context, "Added ${selected.size} track(s) to playlist", Toast.LENGTH_SHORT).show()
                showAddSongsSheet = false
            },
            onDismiss = { showAddSongsSheet = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.pressSpring()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Playlists",
                    tint = appColors.primary
                )
            }
            Text(
                text = playlist.name,
                color = appColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            TextButton(
                onClick = { showAddSongsSheet = true },
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Add Songs", color = appColors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = appColors.surfaceCard,
                    border = BorderStroke(1.dp, appColors.surfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(appColors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = appColors.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = playlist.name,
                            color = appColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val formattedTotalTime = if (totalSec > 0) " • ${formatSecToTime(totalSec)}" else ""
                        Text(
                            text = "${playlistTracks.size} tracks$formattedTotalTime • Created ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US).format(java.util.Date(playlist.createdAt))}",
                            color = appColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (playlistTracks.isNotEmpty()) {
                                viewModel.playPlaylist(playlist.id)
                            }
                        },
                        enabled = playlistTracks.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.primary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play Playlist", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (playlistTracks.isNotEmpty()) {
                                viewModel.playPlaylist(playlist.id, forceShuffle = true)
                            }
                        },
                        enabled = playlistTracks.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, appColors.surfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = appColors.surfaceCard,
                            contentColor = appColors.textPrimary
                        )
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = appColors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }

                    IconButton(
                        onClick = {
                            viewModel.deletePlaylist(playlist)
                            Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(appColors.surfaceCard)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Playlist", tint = Color(0xFFFF5252))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (playlistTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No songs in this playlist yet.",
                                color = appColors.textMuted,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showAddSongsSheet = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.primary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Songs to Add", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    playlistTracks,
                    key = { _, track -> track.id },
                    contentType = { _, _ -> "playlist_track_item" }
                ) { index, track ->
                    val isCurrentlyPlaying = playerState.currentTrack?.id == track.id
                    Surface(
                        onClick = { viewModel.playPlaylist(playlist.id, startTrack = track) },
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth(),
                        color = if (isCurrentlyPlaying) appColors.primary.copy(alpha = 0.12f) else appColors.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrentlyPlaying) appColors.primary.copy(alpha = 0.6f) else appColors.surfaceBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (isCurrentlyPlaying) appColors.primary else appColors.textMuted,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(26.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = if (isCurrentlyPlaying) appColors.primary else appColors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${track.artist} • ${formatSecToTime(track.durationSeconds)} • ${track.format}",
                                    color = appColors.textMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (index > 0) {
                                    IconButton(
                                        onClick = { viewModel.reorderPlaylistTrack(playlist.id, index, index - 1) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = appColors.textMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                if (index < playlistTracks.size - 1) {
                                    IconButton(
                                        onClick = { viewModel.reorderPlaylistTrack(playlist.id, index, index + 1) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = appColors.textMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.removeTrackFromPlaylist(playlist.id, track.id)
                                        Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Track",
                                        tint = appColors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
