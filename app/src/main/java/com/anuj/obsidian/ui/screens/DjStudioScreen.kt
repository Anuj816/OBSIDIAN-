package com.anuj.obsidian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.audio.dj.DjDeckState
import com.anuj.obsidian.audio.dj.ObsidianDjDeckEngine
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.animation.AnimatedVinylRecordPlatter
import com.anuj.obsidian.ui.animation.circularBounceClickable
import com.anuj.obsidian.ui.animation.tactilePressBounce
import com.anuj.obsidian.ui.theme.ObsidianTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjStudioScreen(
    viewModel: ObsidianViewModel,
    onExit: () -> Unit
) {
    val mixerState by ObsidianDjDeckEngine.mixerState.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()

    var trackSelectorDeck by remember { mutableStateOf<String?>(null) }

    // Auto load tracks if empty
    LaunchedEffect(allTracks) {
        if (allTracks.isNotEmpty()) {
            if (mixerState.deckA.track == null) {
                ObsidianDjDeckEngine.loadTrackToDeck("DECK_A", allTracks.first())
            }
            if (mixerState.deckB.track == null && allTracks.size > 1) {
                ObsidianDjDeckEngine.loadTrackToDeck("DECK_B", allTracks[1])
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080B))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B1E28))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF141722),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PRO DJ STUDIO",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                Button(
                    onClick = { ObsidianDjDeckEngine.syncBpm() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("SYNC", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Deck A & Deck B Dual View
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Deck A (Cyan)
                Box(modifier = Modifier.weight(1f)) {
                    DjDeckCard(
                        deck = mixerState.deckA,
                        deckColor = Color(0xFF00E5FF),
                        onSelectTrack = { trackSelectorDeck = "DECK_A" },
                        onTogglePlay = { ObsidianDjDeckEngine.togglePlayPause("DECK_A") },
                        onPitchChange = { ObsidianDjDeckEngine.setPitchPercent("DECK_A", it) },
                        onTriggerCue = { ObsidianDjDeckEngine.triggerHotCue("DECK_A", it) },
                        onToggleLoop = { ObsidianDjDeckEngine.toggleLoop("DECK_A", it) },
                        onToggleKill = { ObsidianDjDeckEngine.toggleEqKill("DECK_A", it) }
                    )
                }

                // Deck B (Crimson)
                Box(modifier = Modifier.weight(1f)) {
                    DjDeckCard(
                        deck = mixerState.deckB,
                        deckColor = Color(0xFFFF4081),
                        onSelectTrack = { trackSelectorDeck = "DECK_B" },
                        onTogglePlay = { ObsidianDjDeckEngine.togglePlayPause("DECK_B") },
                        onPitchChange = { ObsidianDjDeckEngine.setPitchPercent("DECK_B", it) },
                        onTriggerCue = { ObsidianDjDeckEngine.triggerHotCue("DECK_B", it) },
                        onToggleLoop = { ObsidianDjDeckEngine.toggleLoop("DECK_B", it) },
                        onToggleKill = { ObsidianDjDeckEngine.toggleEqKill("DECK_B", it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Crossfader Section
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF10131C),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262B3D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "DECK A", color = Color(0xFF00E5FF), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(text = "CROSSFADER", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                        Text(text = "DECK B", color = Color(0xFFFF4081), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = mixerState.crossfaderPosition,
                        onValueChange = { ObsidianDjDeckEngine.setCrossfader(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFFFF4081)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Track Selector Bottom Sheet
        if (trackSelectorDeck != null) {
            val targetDeck = trackSelectorDeck!!
            ModalBottomSheet(
                onDismissRequest = { trackSelectorDeck = null },
                containerColor = Color(0xFF0F121A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "LOAD TRACK TO $targetDeck",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    allTracks.take(20).forEach { track ->
                        Surface(
                            onClick = {
                                ObsidianDjDeckEngine.loadTrackToDeck(targetDeck, track)
                                trackSelectorDeck = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF181C28),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                Text(text = track.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DjDeckCard(
    deck: DjDeckState,
    deckColor: Color,
    onSelectTrack: () -> Unit,
    onTogglePlay: () -> Unit,
    onPitchChange: (Float) -> Unit,
    onTriggerCue: (Int) -> Unit,
    onToggleLoop: (Float) -> Unit,
    onToggleKill: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF10131A),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, deckColor.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Deck ID & BPM Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = deckColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = deck.deckId.replace("_", " "),
                        color = deckColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "${deck.bpm.toInt()} BPM",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Spinning Turntable Vinyl Platter
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .clickable { onTogglePlay() }
            ) {
                AnimatedVinylRecordPlatter(
                    isPlaying = deck.isPlaying,
                    coverUrl = deck.track?.albumArtUrl,
                    rpm = 33.3f * (1.0f + (deck.pitchPercent / 100f)),
                    glowColor = deckColor,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Loaded Track Banner
            Surface(
                onClick = onSelectTrack,
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF181C26),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = deck.track?.title ?: "Select Track...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = deck.track?.artist ?: "Tap to load",
                        color = Color.Gray,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hot Cue Pads (1, 2, 3, 4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                deck.hotCues.forEach { cue ->
                    Surface(
                        onClick = { onTriggerCue(cue.id) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (cue.isActive) deckColor else Color(0xFF202636),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "C${cue.id}",
                                color = if (cue.isActive) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Loop and EQ Kills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { onToggleLoop(4.0f) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (deck.isLoopActive) deckColor else Color(0xFF1E2230),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "4-LOOP",
                            color = if (deck.isLoopActive) Color.Black else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    onClick = { onToggleKill("low") },
                    shape = RoundedCornerShape(6.dp),
                    color = if (deck.lowKill) Color(0xFFFF5252) else Color(0xFF1E2230),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "KILL LOW",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pitch Slider (-16% to +16%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "PITCH", color = Color.Gray, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${if (deck.pitchPercent >= 0) "+" else ""}${String.format("%.1f", deck.pitchPercent)}%",
                    color = deckColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Slider(
                value = deck.pitchPercent,
                onValueChange = { onPitchChange(it) },
                valueRange = -16.0f..16.0f,
                colors = SliderDefaults.colors(
                    thumbColor = deckColor,
                    activeTrackColor = deckColor,
                    inactiveTrackColor = Color(0xFF262C3D)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Main Play/Pause Button with Circular Bounce
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (deck.isPlaying) deckColor else Color(0xFF202638))
                    .circularBounceClickable { onTogglePlay() }
            ) {
                Icon(
                    imageVector = if (deck.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause Deck",
                    tint = if (deck.isPlaying) Color.Black else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
