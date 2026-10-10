package com.anuj.obsidian.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.components.QualityBadge
import com.anuj.obsidian.ui.components.SelectSongsForQueueSheet
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale



/**
 * Resolves the dynamic list index from the stable occurrence key.
 */
private fun findIndexFromKey(key: String, list: List<DownloadedTrack>, prefix: String): Int {
    if (!key.startsWith(prefix)) return -1
    val withoutPrefix = key.removePrefix(prefix)
    val trackIdStr = withoutPrefix.substringBefore("_occ")
    val occurrenceStr = withoutPrefix.substringAfter("_occ", "0")
    val trackId = trackIdStr.toLongOrNull() ?: return -1
    val targetOccurrence = occurrenceStr.toIntOrNull() ?: 0

    var currentOcc = 0
    for (i in list.indices) {
        if (list[i].id == trackId) {
            if (currentOcc == targetOccurrence) return i
            currentOcc++
        }
    }
    return -1
}



@Composable
fun QueueScreen(viewModel: ObsidianViewModel) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val libraryTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var showAddSongsSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    val queueTracks = playerState.queueEntries
    val upcomingTracks = playerState.upcomingEntries
    val currentTrack = playerState.currentTrack
    val isPlaying = playerState.isPlaying


    var localQueue by remember { mutableStateOf(queueTracks) }
    var localUpcoming by remember { mutableStateOf(upcomingTracks) }

    LaunchedEffect(queueTracks) {
        localQueue = queueTracks
    }
    LaunchedEffect(upcomingTracks) {
        localUpcoming = upcomingTracks
    }

    val totalQueueDuration = remember(localQueue, localUpcoming, currentTrack) {
        val qSum = localQueue.sumOf { it.track.durationSeconds }
        val upSum = localUpcoming.sumOf { it.track.durationSeconds }

        val curSum = currentTrack?.durationSeconds ?: 0
        qSum + upSum + curSum
    }

    val explicitLazyListState = rememberLazyListState()

// Reorder state with stable key index resolution
    val reorderState = rememberReorderableLazyListState(explicitLazyListState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState

        if (fromKey.startsWith("queue_") && toKey.startsWith("queue_")) {
            val fromIdx = localQueue.indexOfFirst { "queue_${it.entryId}" == fromKey }
            val toIdx = localQueue.indexOfFirst { "queue_${it.entryId}" == toKey }
            if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                val mut = localQueue.toMutableList()
                val item = mut.removeAt(fromIdx)
                mut.add(toIdx, item)
                localQueue = mut
                viewModel.moveQueueTrack(fromIdx, toIdx)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } else if (fromKey.startsWith("upcoming_") && toKey.startsWith("upcoming_")) {
            val fromIdx = localUpcoming.indexOfFirst { "upcoming_${it.entryId}" == fromKey }
            val toIdx = localUpcoming.indexOfFirst { "upcoming_${it.entryId}" == toKey }
            if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                val mut = localUpcoming.toMutableList()
                val item = mut.removeAt(fromIdx)
                mut.add(toIdx, item)
                localUpcoming = mut
                viewModel.reorderUpcomingTrack(fromIdx, toIdx)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    val showJumpToTopButton by remember {
        derivedStateOf {
            explicitLazyListState.firstVisibleItemIndex > 2
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Action & Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playing Queue",
                        color = appColors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val totalCount = queueTracks.size + upcomingTracks.size + (if (currentTrack != null) 1 else 0)
                    Text(
                        text = if (totalCount > 0) {
                            "$totalCount tracks • ${formatQueueDuration(totalQueueDuration)}"
                        } else {
                            "Queue empty"
                        },
                        color = appColors.textSecondary,
                        fontSize = 13.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Add Songs Button
                    IconButton(
                        onClick = { showAddSongsSheet = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Songs to Queue",
                            tint = appColors.primary
                        )
                    }

                    // Reshuffle / Shuffle Button
                    if (queueTracks.size > 1 || upcomingTracks.size > 1) {
                        IconButton(
                            onClick = {
                                if (queueTracks.size > 1) {
                                    viewModel.shuffleExplicitQueue()
                                    Toast.makeText(context, "Reshuffled queue", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.reshuffle()
                                    Toast.makeText(context, "Reshuffled upcoming tracks", Toast.LENGTH_SHORT).show()
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle Queue",
                                tint = appColors.primary
                            )
                        }
                    }

                    // More Menu Button
                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Queue Options",
                                tint = appColors.textSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier.background(appColors.surfaceCard)
                        ) {
                            if (currentTrack != null) {
                                DropdownMenuItem(
                                    text = { Text("Jump to Current Track", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        showOptionsMenu = false
                                        coroutineScope.launch {
                                            explicitLazyListState.animateScrollToItem(0)
                                        }
                                    }
                                )
                            }
                            if (upcomingTracks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Reshuffle Upcoming", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        showOptionsMenu = false
                                        viewModel.reshuffle()
                                        Toast.makeText(context, "Reshuffled upcoming tracks", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            if (queueTracks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Clear Queue", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = appColors.textSecondary) },
                                    onClick = {
                                        showOptionsMenu = false
                                        viewModel.clearQueue()
                                        Toast.makeText(context, "Cleared queue", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            if (upcomingTracks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Clear Upcoming", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = appColors.textSecondary) },
                                    onClick = {
                                        showOptionsMenu = false
                                        viewModel.clearUpcomingTracks()
                                        Toast.makeText(context, "Cleared upcoming tracks", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            if (queueTracks.isNotEmpty() || upcomingTracks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Clear All Queue & Upcoming", color = Color(0xFFFF5252)) },
                                    leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null, tint = Color(0xFFFF5252)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        viewModel.clearAllQueueAndUpcoming()
                                        Toast.makeText(context, "Cleared all queue and upcoming", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Main Content Area
            if (currentTrack == null && queueTracks.isEmpty() && upcomingTracks.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(appColors.surfaceCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = appColors.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Your Queue is Empty",
                            color = appColors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Add songs to your queue or play a track from your library to start listening.",
                            color = appColors.textSecondary,
                            fontSize = 13.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showAddSongsSheet = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.primary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Songs", fontWeight = FontWeight.SemiBold)
                            }

                            if (libraryTracks.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        val first = libraryTracks.firstOrNull()
                                        if (first != null) {
                                            viewModel.playTrackFromList(first, libraryTracks)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play Library", color = appColors.textPrimary)
                                }
                            }
                        }
                    }
                }
            } else {
                // Classic Queue Layout with Reorderable items
                LazyColumn(
                    state = explicitLazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // 1. Now Playing Section
                    if (currentTrack != null) {
                        item(key = "header_now_playing") {
                            Text(
                                text = "NOW PLAYING",
                                color = appColors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
                            )
                        }

                        item(key = "current_playing_card") {
                            NowPlayingQueueCard(
                                track = currentTrack,
                                isPlaying = isPlaying,
                                onPlayPauseToggle = { viewModel.togglePlayPause() },
                                onCardClick = { viewModel.showFullPlayer(true) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 2. Up Next Section Header
                    if (queueTracks.isNotEmpty()) {
                        item(key = "header_up_next") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "UP NEXT (${queueTracks.size})",
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        viewModel.clearQueue()
                                        Toast.makeText(context, "Cleared queue", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("Clear Queue", fontSize = 11.5.sp, color = appColors.textSecondary)
                                    }
                                }
                            }
                        }

                        // 3. Explicit Queue Reorderable Items
                        itemsIndexed(
                            items = localQueue,
                            key = { _, entry -> "queue_${entry.entryId}" },
                            contentType = { _, _ -> "queue_item" }
                        ) { index, entry ->
                            val track = entry.track
                            val itemKey = "queue_${entry.entryId}"

                            val isCurrentlyActive = currentTrack?.id == track.id

                            ReorderableItem(
                                state = reorderState,
                                key = itemKey
                            ) { isDragging ->
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 14.dp else 0.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "queue_drag_elevation"
                                )
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.04f else 1f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "queue_drag_scale"
                                )

                                Surface(
                                    shadowElevation = elevation,
                                    color = if (isDragging) {
                                        appColors.surfaceCard
                                    } else if (isCurrentlyActive) {
                                        appColors.primary.copy(alpha = 0.10f)
                                    } else {
                                        Color.Transparent
                                    },
                                    border = if (isDragging) {
                                        BorderStroke(2.dp, appColors.primary)
                                    } else if (isCurrentlyActive) {
                                        BorderStroke(1.dp, appColors.primary.copy(alpha = 0.35f))
                                    } else null,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null,
                                            placementSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmoothIntOffset
                                        )
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        )
                                ) {
                                    ClassicQueueTrackRow(
                                        track = track,
                                        position = index + 1,
                                        isPlaying = isCurrentlyActive && isPlaying,
                                        isHighlighted = isCurrentlyActive,
                                        onClick = { viewModel.playQueueIndex(index) },
                                        onPlayNext = {
                                            viewModel.addTracksToNextQueue(listOf(track))
                                            Toast.makeText(context, "Moved to top of queue", Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToEnd = {
                                            viewModel.addTracksToQueue(listOf(track))
                                            Toast.makeText(context, "Added to end of queue", Toast.LENGTH_SHORT).show()
                                        },
                                        onRemove = {
                                            viewModel.removeTrackFromQueueAtIndex(index)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        dragHandleModifier = Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    } else if (currentTrack != null && upcomingTracks.isEmpty()) {
                        // Up Next is empty, show quick add prompt
                        item(key = "empty_up_next_prompt") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .clickable { showAddSongsSheet = true },
                                colors = CardDefaults.cardColors(
                                    containerColor = appColors.surfaceCard.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = appColors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Add more songs to queue",
                                        color = appColors.primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // 4. Upcoming Tracks from Current Playlist / Category / Shuffle
                    if (upcomingTracks.isNotEmpty()) {
                        item(key = "header_upcoming_context") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (playerState.isShuffle) "SHUFFLED UPCOMING (${upcomingTracks.size})" else "UPCOMING (${upcomingTracks.size})",
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        viewModel.reshuffle()
                                        Toast.makeText(context, "Reshuffled upcoming tracks", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("Reshuffle", fontSize = 11.5.sp, color = appColors.primary)
                                    }
                                    TextButton(onClick = {
                                        viewModel.clearUpcomingTracks()
                                        Toast.makeText(context, "Cleared upcoming tracks", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("Clear", fontSize = 11.5.sp, color = appColors.textSecondary)
                                    }
                                }
                            }
                        }
                        itemsIndexed(
                            items = localUpcoming,
                            key = { _, entry -> "upcoming_${entry.entryId}" },
                            contentType = { _, _ -> "upcoming_item" }
                        ) { index, entry ->
                            val track = entry.track
                            val itemKey = "upcoming_${entry.entryId}"

                            val isCurrentlyActive = currentTrack?.id == track.id

                            ReorderableItem(
                                state = reorderState,
                                key = itemKey
                            ) { isDragging ->
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 14.dp else 0.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "upcoming_drag_elevation"
                                )
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.04f else 1f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "upcoming_drag_scale"
                                )

                                Surface(
                                    shadowElevation = elevation,
                                    color = if (isDragging) {
                                        appColors.surfaceCard
                                    } else if (isCurrentlyActive) {
                                        appColors.primary.copy(alpha = 0.10f)
                                    } else {
                                        Color.Transparent
                                    },
                                    border = if (isDragging) {
                                        BorderStroke(2.dp, appColors.primary)
                                    } else if (isCurrentlyActive) {
                                        BorderStroke(1.dp, appColors.primary.copy(alpha = 0.35f))
                                    } else null,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null,
                                            placementSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmoothIntOffset
                                        )
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        )
                                ) {
                                    UpcomingTrackRow(
                                        track = track,
                                        position = (queueTracks.size + index + 1),
                                        isPlaying = isCurrentlyActive && isPlaying,
                                        isHighlighted = isCurrentlyActive,
                                        onClick = { viewModel.playUpcomingTrack(track) },
                                        onPlayNext = {
                                            viewModel.addTracksToNextQueue(listOf(track))
                                            Toast.makeText(context, "Queued to play next", Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToEnd = {
                                            viewModel.addTracksToQueue(listOf(track))
                                            Toast.makeText(context, "Added to end of queue", Toast.LENGTH_SHORT).show()
                                        },
                                        dragHandleModifier = Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating "Jump to Current / Top" Button
        AnimatedVisibility(
            visible = showJumpToTopButton && currentTrack != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        explicitLazyListState.animateScrollToItem(0)
                    }
                },
                containerColor = appColors.primary,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Jump to Current Playing Song",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    // Modal Sheet for adding songs to the queue
    if (showAddSongsSheet) {
        SelectSongsForQueueSheet(
            allTracks = libraryTracks,
            onAddNext = { selected ->
                if (selected.isNotEmpty()) {
                    viewModel.addTracksToNextQueue(selected)
                    Toast.makeText(context, "Added ${selected.size} tracks to play next", Toast.LENGTH_SHORT).show()
                }
                showAddSongsSheet = false
            },
            onAddEnd = { selected ->
                if (selected.isNotEmpty()) {
                    viewModel.addTracksToQueue(selected)
                    Toast.makeText(context, "Added ${selected.size} tracks to queue", Toast.LENGTH_SHORT).show()
                }
                showAddSongsSheet = false
            },
            onDismiss = { showAddSongsSheet = false }
        )
    }
}

@Composable
fun NowPlayingQueueCard(
    track: DownloadedTrack,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        colors = CardDefaults.cardColors(
            containerColor = appColors.surfaceCard
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Artwork with Equalizer Overlay
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = remember(track.id, track.albumArtUrl, track.localFilePath) {
                        ImageRequest.Builder(context)
                            .data(track)
                            .memoryCacheKey(track.id.toString())
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = "Artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = appColors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Track Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = appColors.textPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${track.artist} • ${track.album}",
                    color = appColors.textSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QualityBadge(
                        bitDepth = track.bitDepth,
                        sampleRate = track.sampleRate,
                        compact = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDuration(track.durationSeconds),
                        color = appColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Play / Pause Action Button
            IconButton(
                onClick = onPlayPauseToggle,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(appColors.primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = appColors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ClassicQueueTrackRow(
    track: DownloadedTrack,
    position: Int,
    isPlaying: Boolean = false,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToEnd: () -> Unit = {},
    onRemove: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current
    var showTrackMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag Handle on Left for Classic Native Reordering
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = if (isHighlighted) appColors.primary else appColors.textSecondary.copy(alpha = 0.65f),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Position Number or Playing Indicator
        if (isPlaying) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Currently Playing",
                tint = appColors.primary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                text = "$position",
                color = if (isHighlighted) appColors.primary else appColors.textSecondary.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.width(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Artwork Thumbnail
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(appColors.surfaceCard),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = remember(track.id, track.albumArtUrl, track.localFilePath) {
                    ImageRequest.Builder(context)
                        .data(track)
                        .memoryCacheKey(track.id.toString())
                        .crossfade(true)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Track Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isHighlighted) appColors.primary else appColors.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = appColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        Text(
            text = formatDuration(track.durationSeconds),
            color = appColors.textSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Row Action Menu
        Box {
            IconButton(
                onClick = { showTrackMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Track Actions",
                    tint = appColors.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showTrackMenu,
                onDismissRequest = { showTrackMenu = false },
                modifier = Modifier.background(appColors.surfaceCard)
            ) {
                DropdownMenuItem(
                    text = { Text("Play Now", color = appColors.textPrimary) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = appColors.primary) },
                    onClick = {
                        showTrackMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move to Top (Play Next)", color = appColors.textPrimary) },
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = appColors.primary) },
                    onClick = {
                        showTrackMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to End", color = appColors.textPrimary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = appColors.textSecondary) },
                    onClick = {
                        showTrackMenu = false
                        onAddToEnd()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove from Queue", color = Color(0xFFFF5252)) },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF5252)) },
                    onClick = {
                        showTrackMenu = false
                        onRemove()
                    }
                )
            }
        }

        // Quick Remove Button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove from Queue",
                tint = appColors.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun UpcomingTrackRow(
    track: DownloadedTrack,
    position: Int,
    isPlaying: Boolean = false,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToEnd: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val context = LocalContext.current
    var showTrackMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag Handle on Left for Native Reordering
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = if (isHighlighted) appColors.primary else appColors.textSecondary.copy(alpha = 0.65f),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Position Number or Playing Indicator
        if (isPlaying) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Currently Playing",
                tint = appColors.primary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                text = "$position",
                color = if (isHighlighted) appColors.primary else appColors.textSecondary.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.width(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Artwork Thumbnail
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(appColors.surfaceCard),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = remember(track.id, track.albumArtUrl, track.localFilePath) {
                    ImageRequest.Builder(context)
                        .data(track)
                        .memoryCacheKey(track.id.toString())
                        .crossfade(true)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Track Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isHighlighted) appColors.primary else appColors.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = appColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        Text(
            text = formatDuration(track.durationSeconds),
            color = appColors.textSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Quick "Play Next" Button
        IconButton(
            onClick = onPlayNext,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Queue to Play Next",
                tint = appColors.primary.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
        }

        // More Options Menu
        Box {
            IconButton(
                onClick = { showTrackMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Upcoming Track Actions",
                    tint = appColors.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showTrackMenu,
                onDismissRequest = { showTrackMenu = false },
                modifier = Modifier.background(appColors.surfaceCard)
            ) {
                DropdownMenuItem(
                    text = { Text("Play Now", color = appColors.textPrimary) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = appColors.primary) },
                    onClick = {
                        showTrackMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Play Next (Move to Queue)", color = appColors.textPrimary) },
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = appColors.primary) },
                    onClick = {
                        showTrackMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to End of Queue", color = appColors.textPrimary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = appColors.textSecondary) },
                    onClick = {
                        showTrackMenu = false
                        onAddToEnd()
                    }
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

private fun formatQueueDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0 min"
    val hours = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${mins}m"
    } else {
        "${mins}m"
    }
}
