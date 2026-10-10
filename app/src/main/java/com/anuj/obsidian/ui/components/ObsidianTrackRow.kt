package com.anuj.obsidian.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.animation.AnimatedFavoriteBurstIcon
import com.anuj.obsidian.ui.animation.AnimatedFavoriteIcon
import com.anuj.obsidian.ui.animation.EqualizerSpectrumLiveBar
import com.anuj.obsidian.ui.animation.drawLayoutBounds
import com.anuj.obsidian.ui.animation.pressSpring
import com.anuj.obsidian.ui.animation.safePerform
import com.anuj.obsidian.ui.theme.ObsidianTheme

/**
 * Obsidian High-Fidelity Track Row
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ObsidianTrackRow(
    track: DownloadedTrack,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean = true,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    sleepTimerMinutes: Int? = null,
    onToggleSelect: () -> Unit = {},
    onPlay: () -> Unit,
    onLongClick: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onOpenTagInspector: () -> Unit = {},
    onIdentifyTrack: () -> Unit = {},
    onStartArtistRadio: () -> Unit = {},
    onOpenDsp: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    showLayoutBounds: Boolean = false,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val rowShape = RoundedCornerShape(if (isCurrentlyPlaying) 16.dp else 14.dp)

    Surface(
        onClick = {
            haptic.safePerform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onPlay()
        },
        shape = rowShape,
        color = if (isSelected) appColors.primary.copy(alpha = 0.18f) 
                else if (isCurrentlyPlaying) appColors.surfaceElevated
                else appColors.surfaceCard,
        border = BorderStroke(
            if (isCurrentlyPlaying) 1.5.dp else 1.dp,
            if (isSelected) appColors.primary 
            else if (isCurrentlyPlaying) appColors.primary.copy(alpha = 0.75f) 
            else appColors.surfaceBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .drawLayoutBounds(showLayoutBounds)
            .testTag("library_track_${track.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection Checkbox with Spring AnimatedVisibility
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = fadeIn(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick) + expandHorizontally(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmoothIntSize),
                    exit = fadeOut(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick) + shrinkHorizontally(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmoothIntSize)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = appColors.primary,
                                checkmarkColor = Color.Black,
                                uncheckedColor = appColors.textMuted
                            ),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                // Album Artwork with Animated Scale on GPU Layer
                val hasArt = hasRealArtwork(track)
                val animatedArtScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isCurrentlyPlaying) 1.08f else 1.0f,
                    animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
                    label = "track_art_scale"
                )
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer {
                            scaleX = animatedArtScale
                            scaleY = animatedArtScale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(appColors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    if (hasArt) {
                        AsyncImage(
                            model = remember(track.id, track.albumArtUrl, track.localFilePath) {
                                ImageRequest.Builder(context)
                                    .data(track)
                                    .memoryCacheKey(track.id.toString())
                                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                                    .crossfade(com.anuj.obsidian.ui.animation.MotionSpec.DurationQuick)
                                    .size(120, 120)
                                    .build()
                            },
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        TrackWaveformArt(
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 12.dp,
                            tint = appColors.primary
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCurrentlyPlaying,
                        enter = fadeIn(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick),
                        exit = fadeOut(animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenQuick)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            EqualizerSpectrumLiveBar(
                                isPlaying = isPlaying,
                                primaryColor = appColors.primary,
                                secondaryColor = Color(0xFF00E5FF),
                                barCount = 4,
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Metadata column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    // Song Title (with skeleton placeholder when resolving)
                    if (track.title.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(14.dp)
                                .shimmerPlaceholder(shape = RoundedCornerShape(4.dp))
                        )
                    } else {
                        Text(
                            text = track.title,
                            color = if (isSelected || isCurrentlyPlaying) appColors.primary else appColors.textPrimary,
                            fontSize = if (isCurrentlyPlaying) 15.sp else 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Artist & Album (with skeleton placeholder when resolving)
                    if (track.artist.isBlank() && track.album.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.40f)
                                .height(11.dp)
                                .shimmerPlaceholder(shape = RoundedCornerShape(4.dp))
                        )
                    } else {
                        Text(
                            text = if (track.album.isNotBlank() && track.album != "Unknown Album") "${track.artist} - ${track.album}" else track.artist,
                            color = appColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Obsidian Audiophile Spec line
                    val formatStr = track.format.lowercase().takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) } ?: ""
                    val isLossy = formatStr in setOf("mp3", "m4a", "aac", "ogg", "opus", "3gp")
                    val durationStr = formatSecToTime(track.durationSeconds)
                    val sampleRateStr = if (track.sampleRate.isNotBlank() && !track.sampleRate.equals("UNKNOWN", ignoreCase = true)) track.sampleRate else ""
                    val bitDepthStr = if (!isLossy && track.bitDepth.isNotBlank() && !track.bitDepth.equals("UNKNOWN", ignoreCase = true) && !track.bitDepth.equals("lossy", ignoreCase = true)) track.bitDepth else ""

                    val specParts = mutableListOf<String>()
                    if (durationStr.isNotBlank()) specParts.add(durationStr)
                    if (formatStr.isNotBlank()) specParts.add(formatStr)
                    if (sampleRateStr.isNotBlank()) specParts.add(sampleRateStr)
                    if (bitDepthStr.isNotBlank()) specParts.add(bitDepthStr)

                    val specLine = if (specParts.isNotEmpty()) specParts.joinToString(" | ") else durationStr

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "𝄞",
                            color = appColors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = specLine,
                            color = appColors.textMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Buttons on Top Row
                if (!isSelectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AnimatedFavoriteBurstIcon(
                            isFavorite = track.isFavorite,
                            onToggle = onToggleFavorite,
                            activeTint = appColors.primary,
                            inactiveTint = appColors.textMuted,
                            size = 20.dp
                        )

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.pressSpring()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = appColors.textSecondary
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(appColors.surfaceCard)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Play Next", color = appColors.primary, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onPlayNext()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Queue", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToQueue()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist...", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToPlaylist()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Start Artist Radio", color = appColors.primary, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Default.Radio, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onStartArtistRadio()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Audio DSP Effects", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenDsp()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (sleepTimerMinutes != null && sleepTimerMinutes > 0) "Sleep Timer (${sleepTimerMinutes}m)" else "Sleep Timer", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenSleepTimer()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select Songs...", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onEnterSelection()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Auto-Identify (Fingerprint)", color = com.anuj.obsidian.ui.theme.FlacLosslessCyan, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = com.anuj.obsidian.ui.theme.FlacLosslessCyan) },
                                    onClick = {
                                        menuExpanded = false
                                        onIdentifyTrack()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Vorbis Tags", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenTagInspector()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Track", color = appColors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = appColors.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onShare()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Track", color = Color(0xFFFF5252)) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF5252)) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val WHITESPACE_REGEX = "\\s+".toRegex()

fun fuzzyMatch(query: String, target: String): Boolean {
    if (query.isBlank()) return true
    val q = query.lowercase().trim()
    val t = target.lowercase()

    if (t.contains(q)) return true

    val words = q.split(WHITESPACE_REGEX)
    return words.all { t.contains(it) }
}
