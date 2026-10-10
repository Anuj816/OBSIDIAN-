package com.anuj.obsidian.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.components.formatSecToTime
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.util.ArtworkPaletteCache
import com.anuj.obsidian.ui.util.ExtractedTrackColors

@Composable
fun DriveModeScreen(
    viewModel: ObsidianViewModel,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val currentPositionSec by viewModel.currentPositionSeconds.collectAsStateWithLifecycle()
    val track = playerState.currentTrack

    var dynamicColors by remember {
        mutableStateOf(
            ExtractedTrackColors(
                primary = Color(0xFF1DB954),
                secondary = Color(0xFF00E5FF),
                background = Color.Black
            )
        )
    }

    LaunchedEffect(track) {
        if (track != null) {
            val extracted = ArtworkPaletteCache.extractColors(
                context = context,
                track = track,
                fallbackPrimary = Color(0xFF1DB954),
                fallbackSecondary = Color(0xFF00E5FF)
            )
            dynamicColors = extracted
        }
    }

    val animatedPrimary by animateColorAsState(targetValue = dynamicColors.primary, animationSpec = tween(600), label = "primaryColor")
    val animatedBg by animateColorAsState(targetValue = dynamicColors.background.copy(alpha = 0.45f), animationSpec = tween(600), label = "bgColor")

    var totalDragOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.radialGradient(
                    colors = listOf(animatedBg, Color(0xFF060608), Color.Black),
                    radius = 1200f
                )
            )
            .pointerInput(track?.id) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragOffset = 0f },
                    onDragEnd = {
                        if (totalDragOffset > 80f) {
                            viewModel.playPrevious()
                        } else if (totalDragOffset < -80f) {
                            viewModel.playNext(forceLoop = false)
                        }
                        totalDragOffset = 0f
                    },
                    onDragCancel = { totalDragOffset = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDragOffset += dragAmount
                    }
                )
            }
    ) {
        LaunchedEffect(playerState.isDriveModeVisible) {
            if (!playerState.isDriveModeVisible) {
                onExit()
            }
        }

        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E24).copy(alpha = 0.85f))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Exit Drive Mode",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1A1D24).copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, animatedPrimary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = animatedPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DRIVE MODE",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            if (track != null) {
                IconButton(
                    onClick = { viewModel.toggleFavorite(track) },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E24).copy(alpha = 0.85f))
                ) {
                    Icon(
                        if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) animatedPrimary else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }
        }

        if (track != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .padding(top = 80.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Track Info & Format Banner
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = animatedPrimary.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, animatedPrimary.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "${track.format} • ${track.bitDepth} / ${track.sampleRate}",
                            color = animatedPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 42.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = track.artist,
                        color = Color(0xFFB0B6C3),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Progress Indicator
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val duration = track.durationSeconds.coerceAtLeast(1)
                    val progress = (currentPositionSec / duration.toFloat()).coerceIn(0f, 1f)

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = animatedPrimary,
                        trackColor = Color(0xFF262A35)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatSecToTime(currentPositionSec.toInt()),
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatSecToTime(duration),
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Main Oversized Transport Controls
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { viewModel.playPrevious() },
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E212B))
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(animatedPrimary)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playNext(forceLoop = false) },
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E212B))
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Bottom Gesture Hint & Artist Radio Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Swipe,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Swipe anywhere to skip",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }

                    Surface(
                        onClick = { viewModel.startArtistRadio(track.artist, track.title) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E212B)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Radio,
                                contentDescription = null,
                                tint = animatedPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Artist Radio",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No Track Playing",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = animatedPrimary)
                ) {
                    Text("Select Music in Library", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
