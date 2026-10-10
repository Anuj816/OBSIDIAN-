package com.anuj.obsidian.data.model

data class AlbumInfo(
    val title: String,
    val artist: String,
    val coverArtUrl: String,
    val year: String,
    val tracks: List<DownloadedTrack>
) {
    val totalDurationSeconds: Int get() = tracks.sumOf { it.durationSeconds }
    val totalSizeMb: Float get() = tracks.sumOf { it.fileSizeMb.toDouble() }.toFloat()
    val isLossless: Boolean get() = tracks.any { it.bitDepth.contains("24") || it.sampleRate.contains("96") || it.sampleRate.contains("192") }
}

data class ArtistInfo(
    val name: String,
    val albumCount: Int,
    val tracks: List<DownloadedTrack>
) {
    val totalDurationSeconds: Int get() = tracks.sumOf { it.durationSeconds }
    val isLossless: Boolean get() = tracks.any { it.bitDepth.contains("24") }
}

data class GenreInfo(
    val name: String,
    val tracks: List<DownloadedTrack>
) {
    val totalDurationSeconds: Int get() = tracks.sumOf { it.durationSeconds }
}
