package com.anuj.obsidian.ui.components

import kotlinx.coroutines.isActive
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.anuj.obsidian.ui.animation.safePerform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LyricLine(
    val timeSeconds: Float,
    val text: String,
    val translation: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    track: DownloadedTrack?,
    currentPositionFlow: StateFlow<Float>,
    isFetchingOnline: Boolean = false,
    onFetchOnlineLyrics: () -> Unit = {},
    onSearchCustomLyrics: (String, String) -> Unit = { _, _ -> },
    onSeek: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    if (track == null) return
    val appColors = ObsidianTheme.colors
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isDualLanguageMode by remember { mutableStateOf(true) }
    var showCustomSearchDialog by remember { mutableStateOf(false) }

    var searchTitleInput by remember(track) { mutableStateOf(track.title) }
    var searchArtistInput by remember(track) { mutableStateOf(track.artist) }

    val parsedLines = remember(track.lyrics) {
        parseLrc(track.lyrics)
    }

    val isSyncedLyrics = remember(parsedLines) {
        parsedLines.isNotEmpty() && parsedLines.any { it.timeSeconds >= 0f }
    }

    val currentPositionState = currentPositionFlow.collectAsStateWithLifecycle(initialValue = 0f)

    val activeIndex by remember(parsedLines, isSyncedLyrics) {
        derivedStateOf {
            if (parsedLines.isEmpty() || !isSyncedLyrics) return@derivedStateOf -1
            val pos = currentPositionState.value
            val idx = parsedLines.indexOfLast { it.timeSeconds in 0f..pos }
            if (idx >= 0) idx else 0
        }
    }

    val listState = rememberLazyListState()

    // Smooth intelligent auto-scroll state management
    var isUserScrollingManual by remember { mutableStateOf(false) }
    var userManualScrollTimestamp by remember { mutableLongStateOf(0L) }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }

    // Auto-resume sync timer when user stops physically dragging
    LaunchedEffect(isUserScrollingManual, userManualScrollTimestamp) {
        if (isUserScrollingManual) {
            delay(3500)
            if (System.currentTimeMillis() - userManualScrollTimestamp >= 3400) {
                isUserScrollingManual = false
            }
        }
    }

    // Auto-scroll to center the active line with buttery smooth animation (only for synced lyrics)
    LaunchedEffect(activeIndex, isUserScrollingManual, isAutoScrollEnabled, isSyncedLyrics) {
        if (isSyncedLyrics && isAutoScrollEnabled && !isUserScrollingManual && parsedLines.isNotEmpty() && activeIndex in parsedLines.indices) {
            val viewportHeightPx = listState.layoutInfo.viewportSize.height
            // Calculate offset so the active lyric is centered in the sweet spot (upper-middle)
            val centerOffsetPx = if (viewportHeightPx > 0) {
                -(viewportHeightPx * 0.36f).toInt()
            } else {
                -with(density) { 160.dp.roundToPx() }
            }

            try {
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = centerOffsetPx
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "LyricsSheet.animateScrollToItem")
            }
        }
    }

    // Custom Search Dialog
    if (showCustomSearchDialog) {
        AlertDialog(
            onDismissRequest = { showCustomSearchDialog = false },
            title = {
                Text(
                    text = "Search Online Lyrics",
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Edit track info to search alternative lyrics across providers:",
                        color = appColors.textSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = searchTitleInput,
                        onValueChange = { searchTitleInput = it },
                        label = { Text("Track Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = appColors.primary,
                            focusedLabelColor = appColors.primary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedTextColor = appColors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("lyrics_custom_title_input")
                    )
                    OutlinedTextField(
                        value = searchArtistInput,
                        onValueChange = { searchArtistInput = it },
                        label = { Text("Artist Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = appColors.primary,
                            focusedLabelColor = appColors.primary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedTextColor = appColors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("lyrics_custom_artist_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCustomSearchDialog = false
                        onSearchCustomLyrics(searchTitleInput, searchArtistInput)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.primary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("lyrics_custom_search_confirm")
                ) {
                    Text("Search", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomSearchDialog = false }) {
                    Text("Cancel", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surfaceCard
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.background,
        dragHandle = null,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            appColors.primary.copy(alpha = 0.18f),
                            appColors.background,
                            appColors.background
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = appColors.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isSyncedLyrics) "SYNCED KARAOKE & LYRICS" else "PLAIN TEXT LYRICS",
                                color = appColors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${track.title} • ${track.artist}",
                                color = appColors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Custom search button
                        IconButton(
                            onClick = {
                                searchTitleInput = track.title
                                searchArtistInput = track.artist
                                showCustomSearchDialog = true
                            },
                            modifier = Modifier.testTag("lyrics_custom_search_dialog_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Custom Lyrics Search",
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Dual Language Translation Toggle
                        IconButton(
                            onClick = {
                                isDualLanguageMode = !isDualLanguageMode
                                haptic.safePerform(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier.testTag("lyrics_translation_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Toggle Dual-Language Translation",
                                tint = if (isDualLanguageMode) appColors.primary else appColors.textMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Online Lyrics Fetcher Trigger with isolated animation
                        SyncSpinButton(
                            isFetchingOnline = isFetchingOnline,
                            onClick = {
                                haptic.safePerform(HapticFeedbackType.LongPress)
                                onFetchOnlineLyrics()
                            }
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("lyrics_dismiss")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Lyrics",
                                tint = appColors.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (parsedLines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = appColors.textMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No synchronized lyrics found locally.",
                                color = appColors.textSecondary,
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Search LRCLIB, Kugou, NetEase, QQ Music, JioSaavn, ChartLyrics, Lyrist & Lyrics.ovh multi-provider network for time-synchronized LRC lyrics and karaoke timing.",
                                color = appColors.textMuted,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ElevatedButton(
                                    onClick = onFetchOnlineLyrics,
                                    enabled = !isFetchingOnline,
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = appColors.primary,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("fetch_online_lyrics_btn")
                                ) {
                                    if (isFetchingOnline) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.Black,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Searching...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Quick Search", fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        searchTitleInput = track.title
                                        searchArtistInput = track.artist
                                        showCustomSearchDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, appColors.primary.copy(alpha = 0.6f)),
                                    modifier = Modifier.testTag("custom_search_empty_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Custom Search", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = parsedLines,
                            key = { index, line -> "${index}_${line.timeSeconds}_${line.text.hashCode()}" }
                        ) { index, line ->
                            val isActive = isSyncedLyrics && index == activeIndex
                            val isPassed = isSyncedLyrics && index < activeIndex
                            val nextLineTime = if (index < parsedLines.size - 1) parsedLines[index + 1].timeSeconds else (line.timeSeconds + 6f)

                            LyricLineItem(
                                index = index,
                                line = line,
                                isActive = isActive,
                                isPassed = isPassed,
                                isSyncedLyrics = isSyncedLyrics,
                                isDualLanguageMode = isDualLanguageMode,
                                nextLineTime = nextLineTime,
                                currentPositionFlow = currentPositionFlow,
                                primaryColor = appColors.primary,
                                secondaryColor = appColors.secondary,
                                textPrimaryColor = appColors.textPrimary,
                                textMutedColor = appColors.textMuted,
                                onLineClick = {
                                    if (line.timeSeconds >= 0f) {
                                        haptic.safePerform(HapticFeedbackType.LongPress)
                                        isUserScrollingManual = false
                                        onSeek(line.timeSeconds)
                                    }
                                }
                            )
                        }
                    }
                }

                // Footer Info Bar
                Surface(
                    color = appColors.surfaceCard,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAutoScrollEnabled = !isAutoScrollEnabled }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAutoScrollEnabled) "Auto-Scroll: ON (Tap line to seek)" else "Auto-Scroll: OFF (Manual Mode)",
                            color = if (isAutoScrollEnabled) appColors.textMuted else appColors.secondary,
                            fontSize = 12.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = if (isAutoScrollEnabled) Icons.Default.Sync else Icons.Default.Close,
                                contentDescription = "Toggle Auto-Scroll",
                                tint = if (isAutoScrollEnabled) appColors.primary else appColors.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isUserScrollingManual && isAutoScrollEnabled) "Paused" else if (isAutoScrollEnabled) "Synced" else "Manual",
                                color = if (isUserScrollingManual || !isAutoScrollEnabled) appColors.secondary else appColors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Floating "Sync / Resume Auto-Scroll" Button when user has scrolled away
            androidx.compose.animation.AnimatedVisibility(
                visible = isUserScrollingManual && parsedLines.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp)
            ) {
                Surface(
                    onClick = {
                        isUserScrollingManual = false
                        haptic.safePerform(HapticFeedbackType.LongPress)
                        scope.launch {
                            val viewportHeightPx = listState.layoutInfo.viewportSize.height
                            val centerOffsetPx = if (viewportHeightPx > 0) -(viewportHeightPx * 0.36f).toInt() else -with(density) { 160.dp.roundToPx() }
                            listState.animateScrollToItem(activeIndex, centerOffsetPx)
                        }
                    },
                    shape = CircleShape,
                    color = appColors.primary,
                    shadowElevation = 8.dp,
                    modifier = Modifier.testTag("resume_lyrics_sync_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Sync to Playhead",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

fun parseLrc(raw: String): List<LyricLine> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.lines()
    val rawList = mutableListOf<LyricLine>()

    var offsetMs = 0L
    val timePattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{2,3}))?\]""")
    val offsetPattern = Regex("""\[offset:\s*([+-]?\d+)\]""", RegexOption.IGNORE_CASE)
    var anyTimeTagFound = false

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) continue

        // Check for offset tag
        val offsetMatch = offsetPattern.matchEntire(trimmed)
        if (offsetMatch != null) {
            offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
            continue
        }

        // Skip metadata header tags like [ti:], [ar:], [al:], [by:], etc.
        if (Regex("""^\[[a-zA-Z]+:.*\]$""").matches(trimmed)) continue

        val matches = timePattern.findAll(trimmed).toList()
        if (matches.isNotEmpty()) {
            anyTimeTagFound = true
            val lastMatch = matches.lastOrNull()
            var text = if (lastMatch != null && lastMatch.range.last + 1 <= trimmed.length) {
                trimmed.substring(lastMatch.range.last + 1).trim()
            } else ""
            // Clean inline karaoke word timing tags e.g. <00:12.34> or <00:12:34>
            text = text.replace(Regex("""<\d{1,2}:\d{2}(?:[.:]\d{1,3})?>"""), "").trim()
            for (match in matches) {
                val minStr = match.groupValues.getOrNull(1) ?: "0"
                val secStr = match.groupValues.getOrNull(2) ?: "0"
                val fracStr = match.groupValues.getOrNull(3) ?: ""
                val min = minStr.toIntOrNull() ?: 0
                val sec = secStr.toIntOrNull() ?: 0
                val frac = if (fracStr.isNotBlank()) {
                    fracStr.toFloatOrNull()?.let {
                        if (fracStr.length == 2) it / 100f else it / 1000f
                    } ?: 0f
                } else 0f
                val totalSeconds = (min * 60f + sec + frac) + (offsetMs / 1000f)
                rawList.add(LyricLine(totalSeconds.coerceAtLeast(0f), text))
            }
        } else {
            val cleanPlain = trimmed.replace(Regex("""<\d{1,2}:\d{2}(?:[.:]\d{1,3})?>"""), "").trim()
            rawList.add(LyricLine(-1f, cleanPlain))
        }
    }

    if (!anyTimeTagFound) {
        // Plain text lyrics: preserve original order
        return rawList
    }

    // Filter out plain lines if synced lines exist or convert negative to nearest
    val sorted = rawList.filter { it.timeSeconds >= 0f }.sortedBy { it.timeSeconds }

    // Merge duplicate/subsequent lines with identical timestamps as translation lines
    val merged = mutableListOf<LyricLine>()
    var i = 0
    while (i < sorted.size) {
        val current = sorted[i]
        if (i + 1 < sorted.size) {
            val next = sorted[i + 1]
            // If timestamps match closely (within 0.35 seconds), treat next line as dual translation
            if (kotlin.math.abs(current.timeSeconds - next.timeSeconds) < 0.35f && current.text != next.text) {
                merged.add(LyricLine(current.timeSeconds, current.text, next.text))
                i += 2
                continue
            }
        }
        merged.add(current)
        i++
    }

    return merged
}

@Composable
fun SyncSpinButton(
    isFetchingOnline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    if (isFetchingOnline) {
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "sync_spin")
        val rotationAngle = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = com.anuj.obsidian.ui.animation.MotionSpec.EqualizerLoopSpec.animation,
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "sync_angle"
        )

        IconButton(
            onClick = {},
            enabled = false,
            modifier = modifier.testTag("lyrics_online_fetch_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Fetching online lyrics...",
                tint = appColors.secondary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotationAngle.value }
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier.testTag("lyrics_online_fetch_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Fetch Online Lyrics (Multi-Fetcher)",
                tint = appColors.secondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ActiveLyricProgressBar(
    lineTimeSeconds: Float,
    nextLineTimeSeconds: Float,
    currentPositionFlow: StateFlow<Float>,
    primaryColor: Color,
    secondaryColor: Color
) {
    val currentPositionState = currentPositionFlow.collectAsStateWithLifecycle(initialValue = 0f)

    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .graphicsLayer {
                val currentPos = currentPositionState.value
                val lineDuration = (nextLineTimeSeconds - lineTimeSeconds).coerceAtLeast(1f)
                val lineProgress = ((currentPos - lineTimeSeconds) / lineDuration).coerceIn(0f, 1f)
                scaleX = lineProgress
                alpha = if (lineProgress > 0f) 1f else 0f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(primaryColor, secondaryColor)
                )
            )
    )
}

@Composable
private fun LyricLineItem(
    index: Int,
    line: LyricLine,
    isActive: Boolean,
    isPassed: Boolean,
    isSyncedLyrics: Boolean,
    isDualLanguageMode: Boolean,
    nextLineTime: Float,
    currentPositionFlow: StateFlow<Float>,
    primaryColor: Color,
    secondaryColor: Color,
    textPrimaryColor: Color,
    textMutedColor: Color,
    onLineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Smooth animated scale and alpha for karaoke effect
    val itemScale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else if (isSyncedLyrics) 0.96f else 1.0f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
        label = "lyric_scale"
    )

    val itemAlpha by animateFloatAsState(
        targetValue = when {
            !isSyncedLyrics -> 0.92f
            isActive -> 1f
            isPassed -> 0.68f
            else -> 0.35f
        },
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard,
        label = "lyric_alpha"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            !isSyncedLyrics -> textPrimaryColor
            isActive -> primaryColor
            isPassed -> textPrimaryColor
            else -> textMutedColor
        },
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.tweenStandard(),
        label = "lyric_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
                alpha = itemAlpha
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) primaryColor.copy(alpha = 0.16f)
                else Color.Transparent
            )
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = if (isActive) primaryColor.copy(alpha = 0.50f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = line.timeSeconds >= 0f, onClick = onLineClick)
            .padding(horizontal = 16.dp, vertical = if (isActive) 14.dp else 8.dp)
            .testTag("lyric_line_$index")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = line.text,
                color = textColor,
                fontSize = if (isActive) 23.sp else if (!isSyncedLyrics) 17.sp else 16.sp,
                fontWeight = if (isActive) FontWeight.Black else if (!isSyncedLyrics) FontWeight.SemiBold else FontWeight.Medium,
                lineHeight = if (isActive) 30.sp else 24.sp
            )

            if (isDualLanguageMode && line.translation.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = line.translation,
                    color = if (isActive) secondaryColor else textMutedColor.copy(alpha = 0.7f),
                    fontSize = if (isActive) 15.sp else 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 19.sp
                )
            }

            // Active karaoke fluid progress indicator bar
            if (isActive) {
                ActiveLyricProgressBar(
                    lineTimeSeconds = line.timeSeconds,
                    nextLineTimeSeconds = nextLineTime,
                    currentPositionFlow = currentPositionFlow,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor
                )
            }
        }
    }
}

