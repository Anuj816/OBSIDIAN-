package com.anuj.obsidian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.theme.ObsidianTheme
import java.io.File

/**
 * Checks if a track has genuine embedded or remote artwork available.
 * Returns false if blank, "NO_ART", contains picsum fallback, or points to a non-existent local file.
 */
fun hasRealArtwork(track: DownloadedTrack?): Boolean {
    if (track == null) return false
    return hasRealArtwork(track.albumArtUrl)
}

/**
 * Checks if an artwork URL or file path is genuine.
 */
fun hasRealArtwork(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val trimmed = url.trim()
    if (trimmed.isEmpty() || trimmed == "NO_ART" || trimmed.contains("picsum.photos")) {
        return false
    }
    if (trimmed.startsWith("/") || trimmed.startsWith("file://")) {
        val path = if (trimmed.startsWith("file://")) android.net.Uri.parse(trimmed).path ?: "" else trimmed
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.length() > 64L
    }
    return true
}

/**
 * Obsidian Honest Artwork Neutral Placeholder
 * Displays an elegant music-note icon on a subtle gradient background matching Obsidian's dark theme.
 * Used whenever no genuine album art exists instead of loading fake stock photos.
 */
@Composable
fun TrackArtworkPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp? = null
) {
    val appColors = ObsidianTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF25242C),
                        Color(0xFF1B1A22),
                        Color(0xFF121118)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = "No Artwork Available",
            tint = appColors.primary.copy(alpha = 0.70f),
            modifier = if (iconSize != null) Modifier.size(iconSize) else Modifier.fillMaxSize(0.40f)
        )
    }
}

/**
 * TrackWaveformArt delegates to TrackArtworkPlaceholder for a consistent, honest placeholder.
 */
@Composable
fun TrackWaveformArt(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    tint: Color = ObsidianTheme.colors.primary
) {
    TrackArtworkPlaceholder(
        modifier = modifier,
        cornerRadius = cornerRadius
    )
}

