package com.anuj.obsidian.data.repository

import android.util.Log
import com.anuj.obsidian.data.local.ListeningStatsDao
import com.anuj.obsidian.data.model.AnalyticsSummary
import com.anuj.obsidian.data.model.ArtistListeningStat
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.GenreListeningStat
import com.anuj.obsidian.data.model.TrackListeningStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * ListeningStatsRepository: Tracks audio listening habits, computes analytics summary
 * (top artists, top genres, play counts), and provides smart playlist feeds.
 */
class ListeningStatsRepository(
    private val listeningStatsDao: ListeningStatsDao,
    allTracksFlow: Flow<List<DownloadedTrack>>
) {
    val recentlyPlayedTracksFlow: Flow<List<DownloadedTrack>> = listeningStatsDao.getRecentlyPlayedTracks()

    val onRepeatTracksFlow: Flow<List<DownloadedTrack>> = listeningStatsDao.getOnRepeatTracks(
        sinceTimestampMs = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    )

    val forgottenFavoritesTracksFlow: Flow<List<DownloadedTrack>> = listeningStatsDao.getForgottenFavoritesTracks(
        beforeTimestampMs = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
    )

    val analyticsSummary: Flow<AnalyticsSummary> = combine(
        allTracksFlow,
        listeningStatsDao.getAllStats()
    ) { tracksList, statsList ->
        val tracksMap = tracksList.associateBy { it.id }

        if (statsList.isEmpty()) {
            return@combine AnalyticsSummary(hasEnoughData = false)
        }

        val validStats = statsList.filter { tracksMap.containsKey(it.trackId) }
        if (validStats.isEmpty()) {
            return@combine AnalyticsSummary(hasEnoughData = false)
        }

        val totalPlays = validStats.sumOf { it.playCount }
        if (totalPlays <= 0) {
            return@combine AnalyticsSummary(hasEnoughData = false)
        }

        // Artist Aggregation
        val artistPlays = mutableMapOf<String, Int>()
        val artistUniqueTracks = mutableMapOf<String, MutableSet<Long>>()

        // Genre Aggregation (excluding unknown / placeholder genres)
        val genrePlays = mutableMapOf<String, Int>()

        var totalListeningDurationSec = 0L

        for (stat in validStats) {
            val track = tracksMap[stat.trackId] ?: continue
            val artist = track.artist.trim().ifBlank { "Unknown Artist" }
            artistPlays[artist] = (artistPlays[artist] ?: 0) + stat.playCount
            artistUniqueTracks.getOrPut(artist) { mutableSetOf() }.add(track.id)

            val rawGenre = track.genre.trim()
            if (rawGenre.isNotBlank() && !rawGenre.equals("Unknown", ignoreCase = true) && !rawGenre.equals("Lossless Master", ignoreCase = true)) {
                genrePlays[rawGenre] = (genrePlays[rawGenre] ?: 0) + stat.playCount
            }

            totalListeningDurationSec += (track.durationSeconds.toLong() * stat.playCount)
        }

        val topArtistsList = artistPlays.entries
            .sortedByDescending { it.value }
            .map { entry ->
                ArtistListeningStat(
                    artist = entry.key,
                    totalPlays = entry.value,
                    trackCount = artistUniqueTracks[entry.key]?.size ?: 1
                )
            }

        val topGenresList = genrePlays.entries
            .sortedByDescending { it.value }
            .map { entry ->
                GenreListeningStat(
                    genre = entry.key,
                    totalPlays = entry.value
                )
            }

        val topArtistEntry = topArtistsList.firstOrNull()
        val topGenreEntry = topGenresList.firstOrNull()

        val topTracksList = validStats
            .sortedByDescending { it.playCount }
            .take(20)
            .mapNotNull { stat ->
                tracksMap[stat.trackId]?.let { track -> Pair(track, stat.playCount) }
            }

        val recentTracksList = validStats
            .sortedByDescending { it.lastPlayedTimestamp }
            .take(20)
            .mapNotNull { stat ->
                tracksMap[stat.trackId]?.let { track -> Pair(track, stat.lastPlayedTimestamp) }
            }

        AnalyticsSummary(
            hasEnoughData = true,
            topArtist = topArtistEntry?.artist,
            topArtistPlays = topArtistEntry?.totalPlays ?: 0,
            topGenre = topGenreEntry?.genre,
            topGenrePlays = topGenreEntry?.totalPlays ?: 0,
            totalListeningDurationMinutes = (totalListeningDurationSec / 60).coerceAtLeast(1L),
            totalPlays = totalPlays,
            uniqueTracksPlayed = validStats.size,
            topArtists = topArtistsList,
            topGenres = topGenresList,
            topTracks = topTracksList,
            recentPlays = recentTracksList
        )
    }

    suspend fun recordTrackPlayed(trackId: Long) = withContext(Dispatchers.IO) {
        try {
            val updated = listeningStatsDao.incrementPlayCount(trackId)
            if (updated == 0) {
                listeningStatsDao.insertOrUpdate(
                    TrackListeningStat(
                        trackId = trackId,
                        playCount = 1,
                        lastPlayedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ListeningStatsRepo", "Failed to record track play for $trackId: ${e.message}")
        }
    }
}
