package com.anuj.obsidian.audio.fingerprint

import android.content.Context
import android.util.Log
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.ModernAudioTagger
import com.anuj.obsidian.data.local.TrackDao
import com.anuj.obsidian.data.model.DownloadedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class EnrichmentResultItem(
    val originalTrack: DownloadedTrack,
    val identifiedMetadata: IdentifiedTrackMetadata,
    val isApplied: Boolean = false,
    val errorMessage: String? = null
)

data class EnrichmentProgressState(
    val isRunning: Boolean = false,
    val totalTracks: Int = 0,
    val processedCount: Int = 0,
    val identifiedCount: Int = 0,
    val updatedCount: Int = 0,
    val currentTrackTitle: String = "",
    val currentStatusText: String = "Ready",
    val enrichedResults: List<EnrichmentResultItem> = emptyList(),
    val isDone: Boolean = false,
    val isCancelled: Boolean = false
)

class LibraryMetadataEnricher(
    private val context: Context,
    private val trackDao: TrackDao,
    private val coroutineScope: CoroutineScope
) {
    private val _progress = MutableStateFlow(EnrichmentProgressState())
    val progress: StateFlow<EnrichmentProgressState> = _progress.asStateFlow()

    private var activeJob: Job? = null
    private val isCancelled = AtomicBoolean(false)

    fun isTrackNeedingEnrichment(track: DownloadedTrack): Boolean {
        val unknownNames = setOf("unknown", "unknown artist", "<unknown>", "", "track", "audio", "unknown album")
        val isArtistUnknown = track.artist.trim().lowercase() in unknownNames || track.artist.startsWith("Track ", true)
        val isAlbumUnknown = track.album.trim().lowercase() in unknownNames
        val isTitleGeneric = track.title.trim().lowercase().startsWith("track ") ||
                track.title.matches(Regex("(?i)^audio_?\\d+$")) ||
                track.title.matches(Regex("(?i)^voice_?\\d+$"))
        val isArtMissing = track.albumArtUrl.isBlank()
        val isGenreMissing = track.genre.isBlank() || track.genre.equals("Audio", true) || track.genre.equals("Unknown", true)
        val isYearMissing = track.releaseDate.isBlank()

        return isArtistUnknown || isAlbumUnknown || isTitleGeneric || isArtMissing || (isGenreMissing && isYearMissing)
    }

    suspend fun getTracksNeedingEnrichment(): List<DownloadedTrack> = withContext(Dispatchers.IO) {
        val all = trackDao.getAllTracksList()
        all.filter { isTrackNeedingEnrichment(it) }
    }

    suspend fun identifySingleTrack(track: DownloadedTrack): IdentifiedTrackMetadata? = withContext(Dispatchers.IO) {
        AudioIdentificationService.identifyTrackFromFile(
            context = context,
            filePathOrUri = track.localFilePath,
            hintTitle = track.title,
            hintArtist = track.artist,
            durationSecHint = track.durationSeconds
        )
    }

    suspend fun applyEnrichedMetadata(
        track: DownloadedTrack,
        metadata: IdentifiedTrackMetadata
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            var writeSuccess = false
            if (track.localFilePath.isNotBlank()) {
                val tagResult = ModernAudioTagger.embedAllTagsDetailed(
                    context = context,
                    sourceFilePath = track.localFilePath,
                    title = metadata.title,
                    artist = metadata.artist,
                    album = metadata.album,
                    genre = metadata.genre,
                    year = metadata.releaseYear,
                    lyrics = if (metadata.lyrics.isNotBlank()) metadata.lyrics else track.lyrics,
                    artworkUrl = if (metadata.albumArtUrl.isNotBlank()) metadata.albumArtUrl else track.albumArtUrl,
                    trackNumber = metadata.trackNumber,
                    isrc = metadata.isrc.ifBlank { track.isrc }
                )
                writeSuccess = tagResult.criticalTagsVerified
            }

            val updatedTrack = track.copy(
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                genre = metadata.genre,
                releaseDate = metadata.releaseYear,
                trackNumber = metadata.trackNumber,
                discNumber = metadata.discNumber,
                isrc = metadata.isrc.ifBlank { track.isrc },
                albumArtUrl = if (metadata.albumArtUrl.isNotBlank()) metadata.albumArtUrl else track.albumArtUrl,
                lyrics = if (metadata.lyrics.isNotBlank()) metadata.lyrics else track.lyrics,
                tagsLoaded = true
            )

            trackDao.updateTrack(updatedTrack)
            return@withContext true
        } catch (e: Exception) {
            ObsidianCrashLogger.logException(e, "LibraryMetadataEnricher.applyEnrichedMetadata")
            Log.e("LibraryMetadataEnricher", "Failed to apply metadata to ${track.title}", e)
            return@withContext false
        }
    }

    fun startBatchEnrichment(tracks: List<DownloadedTrack>, autoApply: Boolean = true) {
        if (tracks.isEmpty()) return
        cancelEnrichment()
        isCancelled.set(false)

        activeJob = coroutineScope.launch(Dispatchers.IO) {
            _progress.value = EnrichmentProgressState(
                isRunning = true,
                totalTracks = tracks.size,
                processedCount = 0,
                identifiedCount = 0,
                updatedCount = 0,
                currentStatusText = "Initializing acoustic fingerprint scan..."
            )

            val results = mutableListOf<EnrichmentResultItem>()
            var updatedCount = 0
            var identifiedCount = 0

            for ((index, track) in tracks.withIndex()) {
                if (isCancelled.get()) break

                _progress.value = _progress.value.copy(
                    processedCount = index,
                    currentTrackTitle = track.title,
                    currentStatusText = "Analyzing audio spectrum & AcoustID for '${track.title}'..."
                )

                val matched = try {
                    identifySingleTrack(track)
                } catch (e: Exception) {
                    null
                }

                if (matched != null && matched.title.isNotBlank()) {
                    identifiedCount++
                    var wasApplied = false

                    if (autoApply) {
                        _progress.value = _progress.value.copy(
                            currentStatusText = "Embedding Hi-Res tags & artwork for '${matched.title}'..."
                        )
                        wasApplied = applyEnrichedMetadata(track, matched)
                        if (wasApplied) {
                            updatedCount++
                        }
                    }

                    results.add(
                        EnrichmentResultItem(
                            originalTrack = track,
                            identifiedMetadata = matched,
                            isApplied = wasApplied
                        )
                    )
                } else {
                    results.add(
                        EnrichmentResultItem(
                            originalTrack = track,
                            identifiedMetadata = IdentifiedTrackMetadata(
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                confidenceScore = 0f,
                                serviceSource = "No acoustic match found"
                            ),
                            isApplied = false,
                            errorMessage = "No matching acoustic fingerprint in cloud database"
                        )
                    )
                }

                _progress.value = _progress.value.copy(
                    processedCount = index + 1,
                    identifiedCount = identifiedCount,
                    updatedCount = updatedCount,
                    enrichedResults = results.toList()
                )
            }

            _progress.value = _progress.value.copy(
                isRunning = false,
                isDone = true,
                isCancelled = isCancelled.get(),
                currentStatusText = if (isCancelled.get()) "Enrichment cancelled" else "Library enrichment completed! $updatedCount tracks updated."
            )
        }
    }

    fun cancelEnrichment() {
        isCancelled.set(true)
        activeJob?.cancel()
        activeJob = null
        _progress.value = _progress.value.copy(isRunning = false, isCancelled = true)
    }
}
