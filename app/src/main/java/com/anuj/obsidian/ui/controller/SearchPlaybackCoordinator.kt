package com.anuj.obsidian.ui.controller

import android.util.Log
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.SearchResultItem
import com.anuj.obsidian.data.remote.MusicApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class SearchPlaybackCoordinator {
    private val streamCache = ConcurrentHashMap<String, String>()

    suspend fun resolveStreamUrl(
        item: SearchResultItem
    ): String? = withContext(Dispatchers.IO) {
        val cacheKey = "${item.id}_${item.title}_${item.artist}"
        streamCache[cacheKey]?.let { return@withContext it }

        if (item.streamUrl.isNotBlank() && !item.streamUrl.startsWith("http://")) {
            streamCache[cacheKey] = item.streamUrl
            return@withContext item.streamUrl
        }

        val resolved = withTimeoutOrNull(12000L) {
            try {
                MusicApiService.resolveFullLengthStreamUrl(
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    durationSeconds = item.durationSeconds,
                    trackId = item.id,
                    isrc = item.isrc,
                    providerSource = item.provider
                )
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "SearchPlaybackCoordinator.resolveStreamUrl")
                null
            }
        }

        if (!resolved.isNullOrBlank()) {
            streamCache[cacheKey] = resolved
        }
        resolved
    }

    fun convertSearchResultToTrack(item: SearchResultItem, streamUrl: String): DownloadedTrack {
        val generatedId = DownloadedTrack.generatePseudoId(item.title, item.artist, streamUrl.ifBlank { item.id })
        return DownloadedTrack(
            id = generatedId,
            title = item.title,
            artist = item.artist,
            album = item.album.ifBlank { "Single / Stream" },
            durationSeconds = if (item.durationSeconds > 0) item.durationSeconds else 0,
            format = if (streamUrl.endsWith(".flac", ignoreCase = true)) "FLAC" else if (streamUrl.endsWith(".mp3", ignoreCase = true)) "MP3" else "UNKNOWN",
            bitDepth = item.bitDepth.ifBlank { "UNKNOWN" },
            sampleRate = item.sampleRate.ifBlank { "UNKNOWN" },
            bitrateKbps = 0,
            albumArtUrl = item.coverUrl,
            localFilePath = streamUrl,
            fileSizeMb = 0f,
            lyrics = "",
            isrc = item.isrc,
            releaseDate = item.releaseYear,
            genre = "UNKNOWN",
            trackNumber = item.trackNumber,
            discNumber = 1,
            providerSource = item.provider.ifBlank { "UNKNOWN" },
            isFavorite = false,
            dateAddedTimestamp = System.currentTimeMillis(),
            tagsLoaded = true
        )
    }

    fun clearCache() {
        streamCache.clear()
    }
}
