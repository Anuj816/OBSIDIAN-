package com.anuj.obsidian.ui.controller

import android.content.Context
import android.util.Log
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.ModernAudioTagger
import com.anuj.obsidian.data.local.TrackDao
import com.anuj.obsidian.data.model.DownloadedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class BatchEditFields(
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val releaseYear: String? = null,
    val coverArtUrl: String? = null
)

data class BatchProgressState(
    val isRunning: Boolean = false,
    val currentTrackTitle: String = "",
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val succeededIds: Set<Long> = emptySet(),
    val failedIds: Set<Long> = emptySet(),
    val isDone: Boolean = false
)

class BatchTagController(
    private val context: Context,
    private val trackDao: TrackDao
) {
    private val _progress = MutableStateFlow(BatchProgressState())
    val progress: StateFlow<BatchProgressState> = _progress.asStateFlow()

    suspend fun executeBatchTagUpdate(
        tracks: List<DownloadedTrack>,
        edits: BatchEditFields
    ): BatchProgressState = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext _progress.value

        _progress.value = BatchProgressState(
            isRunning = true,
            totalCount = tracks.size,
            completedCount = 0
        )

        val succeeded = mutableSetOf<Long>()
        val failed = mutableSetOf<Long>()

        for (track in tracks) {
            _progress.value = _progress.value.copy(
                currentTrackTitle = track.title
            )

            val updatedArtist = edits.artist?.takeIf { it.isNotBlank() } ?: track.artist
            val updatedAlbum = edits.album?.takeIf { it.isNotBlank() } ?: track.album
            val updatedGenre = edits.genre?.takeIf { it.isNotBlank() } ?: track.genre
            val updatedYear = edits.releaseYear?.takeIf { it.isNotBlank() } ?: track.releaseDate
            val updatedArt = edits.coverArtUrl?.takeIf { it.isNotBlank() } ?: track.albumArtUrl

            var writeSuccess = false
            try {
                if (track.localFilePath.isNotBlank()) {
                    val result = ModernAudioTagger.embedAllTagsDetailed(
                        context = context,
                        sourceFilePath = track.localFilePath,
                        title = track.title,
                        artist = updatedArtist,
                        album = updatedAlbum,
                        genre = updatedGenre,
                        year = updatedYear,
                        lyrics = track.lyrics,
                        artworkUrl = updatedArt,
                        trackNumber = track.trackNumber,
                        isrc = track.isrc
                    )
                    writeSuccess = result.criticalTagsVerified
                }
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "BatchTagController.embed")
                Log.e("BatchTagController", "Failed to embed metadata for ${track.title}", e)
            }

            try {
                val updatedTrack = track.copy(
                    artist = updatedArtist,
                    album = updatedAlbum,
                    genre = updatedGenre,
                    releaseDate = updatedYear,
                    albumArtUrl = updatedArt
                )
                trackDao.updateTrack(updatedTrack)
                if (writeSuccess || track.localFilePath.isBlank()) {
                    succeeded.add(track.id)
                } else {
                    failed.add(track.id)
                }
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "BatchTagController.dbUpdate")
                failed.add(track.id)
            }

            _progress.value = _progress.value.copy(
                completedCount = succeeded.size + failed.size,
                succeededIds = succeeded.toSet(),
                failedIds = failed.toSet()
            )
        }

        val finalState = _progress.value.copy(
            isRunning = false,
            isDone = true,
            currentTrackTitle = ""
        )
        _progress.value = finalState
        finalState
    }

    fun resetProgress() {
        _progress.value = BatchProgressState()
    }
}
