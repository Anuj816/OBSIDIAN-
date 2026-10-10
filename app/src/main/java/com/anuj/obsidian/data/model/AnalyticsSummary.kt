package com.anuj.obsidian.data.model

data class ArtistListeningStat(
    val artist: String,
    val totalPlays: Int,
    val trackCount: Int
)

data class GenreListeningStat(
    val genre: String,
    val totalPlays: Int
)

data class AnalyticsSummary(
    val hasEnoughData: Boolean = false,
    val topArtist: String? = null,
    val topArtistPlays: Int = 0,
    val topGenre: String? = null,
    val topGenrePlays: Int = 0,
    val totalListeningDurationMinutes: Long = 0,
    val totalPlays: Int = 0,
    val uniqueTracksPlayed: Int = 0,
    val topArtists: List<ArtistListeningStat> = emptyList(),
    val topGenres: List<GenreListeningStat> = emptyList(),
    val topTracks: List<Pair<DownloadedTrack, Int>> = emptyList(),
    val recentPlays: List<Pair<DownloadedTrack, Long>> = emptyList()
)
