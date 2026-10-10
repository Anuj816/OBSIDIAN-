package com.anuj.obsidian.data.repository

import android.content.Context
import android.net.Uri
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.DownloadDao
import com.anuj.obsidian.data.local.ExtensionDao
import com.anuj.obsidian.data.local.ListeningStatsDao
import com.anuj.obsidian.data.local.PlaylistDao
import com.anuj.obsidian.data.local.SearchHistoryDao
import com.anuj.obsidian.data.local.TrackDao
import com.anuj.obsidian.data.model.AnalyticsSummary
import com.anuj.obsidian.data.model.AppSettings
import com.anuj.obsidian.data.model.DownloadTask
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.ExtensionItem
import com.anuj.obsidian.data.model.FullPlaybackState
import com.anuj.obsidian.data.model.Playlist
import com.anuj.obsidian.data.model.SearchHistoryItem
import com.anuj.obsidian.data.model.SearchResultItem
import com.anuj.obsidian.data.remote.MusicApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ObsidianRepository: Central aggregator repository for the Obsidian audio system.
 * Modularized and composed of specialized repositories for Settings, Search History, Playlists,
 * Listening Stats, Lyrics, Extensions, Scanner/Folders, and Downloads.
 */
class ObsidianRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val extensionDao: ExtensionDao,
    private val downloadDao: DownloadDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val playlistDao: PlaylistDao,
    private val listeningStatsDao: ListeningStatsDao,
    private val cachedLyricDao: com.anuj.obsidian.data.local.CachedLyricDao? = null,
    private val scope: CoroutineScope
) {
    // ---------------- Sub-Repositories ----------------
    val settingsRepository = SettingsRepository(context, trackDao, scope)
    val searchHistoryRepository = SearchHistoryRepository(searchHistoryDao)
    val playlistRepository = PlaylistRepository(playlistDao)
    val listeningStatsRepository = ListeningStatsRepository(listeningStatsDao, trackDao.getAllTracks())
    val lyricsRepository = LyricsRepository(context, trackDao, cachedLyricDao, settingsRepository.settings)
    val extensionRepository = ExtensionRepository(extensionDao)
    val libraryScannerRepository = LibraryScannerRepository(context, trackDao, settingsRepository, scope)
    val downloadRepository = DownloadRepository(
        context = context,
        downloadDao = downloadDao,
        trackDao = trackDao,
        settingsRepository = settingsRepository,
        lyricsRepository = lyricsRepository,
        libraryScannerRepository = libraryScannerRepository,
        scope = scope
    )
    val libraryMetadataEnricher = com.anuj.obsidian.audio.fingerprint.LibraryMetadataEnricher(
        context = context,
        trackDao = trackDao,
        coroutineScope = scope
    )

    // ---------------- Flows & Live Data ----------------
    val trackDaoRef: TrackDao = trackDao
    val allTracks: Flow<List<DownloadedTrack>> = trackDao.getAllTracks()
    val favoriteTracks: Flow<List<DownloadedTrack>> = trackDao.getFavoriteTracks()
    val hiResTracks: Flow<List<DownloadedTrack>> = trackDao.getHiResTracks()
    val allPlaylists: Flow<List<Playlist>> = playlistRepository.allPlaylists
    val extensions: Flow<List<ExtensionItem>> = extensionRepository.extensions
    val downloadTasks: Flow<List<DownloadTask>> = downloadRepository.downloadTasks
    val activeTasks: Flow<List<DownloadTask>> = downloadRepository.activeTasks
    val searchHistory: Flow<List<SearchHistoryItem>> = searchHistoryRepository.searchHistory
    val settings: StateFlow<AppSettings> = settingsRepository.settings

    val recentlyPlayedTracksFlow: Flow<List<DownloadedTrack>> = listeningStatsRepository.recentlyPlayedTracksFlow
    val onRepeatTracksFlow: Flow<List<DownloadedTrack>> = listeningStatsRepository.onRepeatTracksFlow
    val forgottenFavoritesTracksFlow: Flow<List<DownloadedTrack>> = listeningStatsRepository.forgottenFavoritesTracksFlow
    val analyticsSummary: Flow<AnalyticsSummary> = listeningStatsRepository.analyticsSummary

    // ---------------- Settings & Preferences Delegations ----------------
    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsRepository.updateSettings(transform)
    fun persistThemeSettings(themePreset: String, customPrim: Long, customSec: Long, customBg: Long, perfMode: String) =
        settingsRepository.persistThemeSettings(themePreset, customPrim, customSec, customBg, perfMode)
    fun persistGaplessSettings(gapless: Boolean, crossfadeSec: Float) = settingsRepository.persistGaplessSettings(gapless, crossfadeSec)
    fun persistVolumeNormalization(enabled: Boolean) = settingsRepository.persistVolumeNormalization(enabled)
    fun persistVisualizerSettings(presetId: Int, bgEnabled: Boolean, alpha: Float, presetName: String = "", presetPath: String = "") =
        settingsRepository.persistVisualizerSettings(presetId, bgEnabled, alpha, presetName, presetPath)
    fun persistVisualizerCycleMode(mode: String) = settingsRepository.persistVisualizerCycleMode(mode)
    fun persistVisualizerColorMode(mode: String) = settingsRepository.persistVisualizerColorMode(mode)
    fun persistVisualizerMasterEnabled(enabled: Boolean) = settingsRepository.persistVisualizerMasterEnabled(enabled)
    fun persistVisualizerShowEverywhere(showEverywhere: Boolean) = settingsRepository.persistVisualizerShowEverywhere(showEverywhere)
    fun persistBackgroundVisualizerEnabled(enabled: Boolean) = settingsRepository.persistBackgroundVisualizerEnabled(enabled)
    fun persistVisualizerAboveChrome(aboveChrome: Boolean) = settingsRepository.persistVisualizerAboveChrome(aboveChrome)
    fun persistVisualizerBackgroundMode(mode: String) = settingsRepository.persistVisualizerBackgroundMode(mode)
    fun persistVisualizerFpsLimit(fps: Int) = settingsRepository.persistVisualizerFpsLimit(fps)
    fun persistVisualizerStyleCategory(category: String) = settingsRepository.persistVisualizerStyleCategory(category)
    fun persistAutoplayOnLaunch(enabled: Boolean) = settingsRepository.persistAutoplayOnLaunch(enabled)
    fun persistInactivityTimeout(minutes: Int) = settingsRepository.persistInactivityTimeout(minutes)
    fun persistAutoplayMode(mode: String) = settingsRepository.persistAutoplayMode(mode)
    fun persistAdvancedShuffleMode(mode: String) = settingsRepository.persistAdvancedShuffleMode(mode)
    fun persistQueueReturnMode(mode: String) = settingsRepository.persistQueueReturnMode(mode)
    fun persistHeadsetActions(autoresume: Boolean, fadeOut: Boolean, fadeOutDurSec: Float = 1.5f) =
        settingsRepository.persistHeadsetActions(autoresume, fadeOut, fadeOutDurSec)
    fun persistDynamicCanvasSettings(enabled: Boolean, preset: String) =
        settingsRepository.persistDynamicCanvasSettings(enabled, preset)
    fun persistShowLayoutBounds(enabled: Boolean) = settingsRepository.persistShowLayoutBounds(enabled)
    fun setOfflineMode(enabled: Boolean) = settingsRepository.setOfflineMode(enabled)
    fun saveVisualizerFadeOpacity(opacity: Float) = settingsRepository.saveVisualizerFadeOpacity(opacity)
    fun saveVisualizerFadeMode(mode: com.anuj.obsidian.ui.visualizer.VisualizerFadeController.FadeMode) = settingsRepository.saveVisualizerFadeMode(mode)
    fun getVisualizerFadeOpacity(): Float = settingsRepository.getVisualizerFadeOpacity()
    fun getVisualizerFadeMode(): com.anuj.obsidian.ui.visualizer.VisualizerFadeController.FadeMode = settingsRepository.getVisualizerFadeMode()
    fun addLinkedMusicFolder(folderUri: String, folderName: String) = settingsRepository.addLinkedMusicFolder(folderUri, folderName)
    fun setLinkedMusicFolders(folders: List<Pair<String, String>>) = settingsRepository.setLinkedMusicFolders(folders)
    fun removeLinkedMusicFolder(folderUri: String, externalScope: CoroutineScope = scope) =
        settingsRepository.removeLinkedMusicFolder(folderUri, externalScope)

    fun saveLastPlaybackState(track: DownloadedTrack, positionSec: Float) = settingsRepository.saveLastPlaybackState(track, positionSec)
    fun saveFullPlaybackState(
        track: DownloadedTrack?,
        positionSec: Float,
        isPlaying: Boolean,
        isShuffle: Boolean,
        repeatMode: Int,
        speed: Float,
        autoplayMode: String,
        queueTrackIds: List<Long>,
        shuffledTrackIds: List<Long>,
        shuffledIndex: Int
    ) = settingsRepository.saveFullPlaybackState(track, positionSec, isPlaying, isShuffle, repeatMode, speed, autoplayMode, queueTrackIds, shuffledTrackIds, shuffledIndex)

    suspend fun restoreFullPlaybackState(): FullPlaybackState = settingsRepository.restoreFullPlaybackState()
    suspend fun restoreActiveQueue(): List<Long> = settingsRepository.restoreActiveQueue()
    suspend fun restoreShuffledQueue(): Pair<List<Long>, Int> = settingsRepository.restoreShuffledQueue()
    fun saveActiveQueue(trackIds: List<Long>) = settingsRepository.saveActiveQueue(trackIds)
    fun saveShuffledQueue(trackIds: List<Long>, shuffledIndex: Int) = settingsRepository.saveShuffledQueue(trackIds, shuffledIndex)

    // ---------------- Search History ----------------
    suspend fun addSearchHistory(query: String) = searchHistoryRepository.addSearchHistory(query)
    suspend fun removeSearchHistory(query: String) = searchHistoryRepository.removeSearchHistory(query)
    suspend fun clearSearchHistory() = searchHistoryRepository.clearSearchHistory()

    // ---------------- Listening Stats ----------------
    suspend fun recordTrackPlayed(trackId: Long) = listeningStatsRepository.recordTrackPlayed(trackId)

    // ---------------- Playlists ----------------
    fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<Long>> = playlistRepository.getTrackIdsForPlaylist(playlistId)
    suspend fun createPlaylist(name: String, description: String = ""): Long = playlistRepository.createPlaylist(name, description)
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) = playlistRepository.addTrackToPlaylist(playlistId, trackId)
    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) = playlistRepository.addTracksToPlaylist(playlistId, trackIds)
    suspend fun reorderPlaylistTracks(playlistId: Long, fromIndex: Int, toIndex: Int) = playlistRepository.reorderPlaylistTracks(playlistId, fromIndex, toIndex)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
    suspend fun deletePlaylist(playlist: Playlist) = playlistRepository.deletePlaylist(playlist)

    // ---------------- Extensions ----------------
    suspend fun autoUpdateExtensions(): Int = extensionRepository.autoUpdateExtensions()
    suspend fun toggleExtension(extension: ExtensionItem) = extensionRepository.toggleExtension(extension)
    suspend fun installExtension(extension: ExtensionItem) = extensionRepository.installExtension(extension)
    suspend fun uninstallExtension(extension: ExtensionItem) = extensionRepository.uninstallExtension(extension)
    suspend fun addCustomExtension(name: String, repoUrl: String, author: String, description: String) =
        extensionRepository.addCustomExtension(name, repoUrl, author, description)

    // ---------------- Lyrics ----------------
    suspend fun fetchAndSaveLyricsForTrack(track: DownloadedTrack, forceRefresh: Boolean = false): String =
        lyricsRepository.fetchAndSaveLyricsForTrack(track, forceRefresh)

    suspend fun updateTrackLyrics(trackId: Long, lyrics: String) {
        if (trackId > 0) {
            trackDao.updateLyrics(trackId, lyrics)
        }
    }

    // ---------------- Library Scanning & File Operations ----------------
    fun startBackgroundTagSync() = libraryScannerRepository.startBackgroundTagSync()
    suspend fun deduplicateDatabaseTracks() = libraryScannerRepository.deduplicateDatabaseTracks()
    suspend fun scanDirectoryTree(treeUri: Uri, context: Context): Int = libraryScannerRepository.scanDirectoryTree(treeUri, context)
    suspend fun getAvailableAudioFolders(context: Context): List<Pair<String, Int>> = libraryScannerRepository.getAvailableAudioFolders(context)
    suspend fun autoScanSavedDirectory(context: Context) = libraryScannerRepository.autoScanSavedDirectory(context)
    suspend fun scanMediaStore(context: Context): Int = libraryScannerRepository.scanMediaStore(context)
    suspend fun fullLibraryRescan(context: Context): Int = libraryScannerRepository.fullLibraryRescan(context)
    suspend fun importAudioFile(uri: Uri, context: Context) = libraryScannerRepository.importAudioFile(uri, context)
    suspend fun importLocalAudioFiles(uris: List<Uri>): Int = libraryScannerRepository.importLocalAudioFiles(uris)
    suspend fun updateTrackTags(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        trackNumber: Int = 1,
        discNumber: Int = 1,
        lyrics: String = ""
    ) = libraryScannerRepository.updateTrackTags(trackId, title, artist, album, genre, year, trackNumber, discNumber, lyrics)

    // ---------------- Download Engine ----------------
    fun startDownload(
        title: String,
        artist: String,
        album: String,
        albumArtUrl: String,
        quality: String = "24-bit / 96kHz FLAC",
        lyrics: String = "",
        isrc: String = "",
        streamUrl: String = "",
        durationSeconds: Int = 210,
        isTemp: Boolean = false,
        releaseDate: String = "2024",
        genre: String = "Lossless",
        trackNumber: Int = 1,
        providerSource: String = "",
        onComplete: ((String) -> Unit)? = null
    ) = downloadRepository.startDownload(
        title = title,
        artist = artist,
        album = album,
        albumArtUrl = albumArtUrl,
        quality = quality,
        lyrics = lyrics,
        isrc = isrc,
        streamUrl = streamUrl,
        durationSeconds = durationSeconds,
        isTemp = isTemp,
        releaseDate = releaseDate,
        genre = genre,
        trackNumber = trackNumber,
        providerSource = providerSource,
        onComplete = onComplete
    )

    suspend fun clearCompletedDownloads() = downloadRepository.clearCompletedDownloads()
    suspend fun cancelDownload(taskId: Long) = downloadRepository.cancelDownload(taskId)
    suspend fun retryTagging(taskId: Long) = downloadRepository.retryTagging(taskId)

    // ---------------- Direct Track CRUD ----------------
    suspend fun getAllTracksList(): List<DownloadedTrack> = withContext(Dispatchers.IO) {
        trackDao.getAllTracksList()
    }

    suspend fun getTrackById(id: Long): DownloadedTrack? = withContext(Dispatchers.IO) {
        trackDao.getTrackById(id)
    }

    suspend fun toggleFavorite(track: DownloadedTrack) = withContext(Dispatchers.IO) {
        val updated = track.copy(isFavorite = !track.isFavorite)
        trackDao.updateTrack(updated)
    }

    suspend fun updateTrack(track: DownloadedTrack) = withContext(Dispatchers.IO) {
        trackDao.updateTrack(track)
    }

    suspend fun insertTrack(track: DownloadedTrack) = withContext(Dispatchers.IO) {
        trackDao.insertTrack(track)
    }

    suspend fun insertAllTracks(tracks: List<DownloadedTrack>) = withContext(Dispatchers.IO) {
        trackDao.insertTracks(tracks)
    }

    suspend fun deleteTrack(track: DownloadedTrack) = withContext(Dispatchers.IO) {
        trackDao.deleteTrack(track)
    }

    suspend fun deleteTrackFromStorage(track: DownloadedTrack, context: Context? = null) = withContext(Dispatchers.IO) {
        try {
            if (track.localFilePath.isNotBlank() && !track.localFilePath.startsWith("content://")) {
                val file = File(track.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                val lrcFile = File(file.parentFile, file.nameWithoutExtension + ".lrc")
                if (lrcFile.exists()) {
                    lrcFile.delete()
                }
            }
        } catch (e: Exception) {
            ObsidianCrashLogger.logException(e, "ObsidianRepository.deleteTrackFromStorage")
        }
        trackDao.deleteTrack(track)
    }

    suspend fun removeTrackFromLibraryOnly(track: DownloadedTrack) = withContext(Dispatchers.IO) {
        trackDao.deleteTrack(track)
    }

    suspend fun sanitizeLibraryMetadata() = withContext(Dispatchers.IO) {
        try {
            trackDao.clearLossyBitDepths()
            trackDao.sanitizeFakeSampleRates()
            trackDao.sanitizeUnknownSampleRates()
            trackDao.sanitizeUnknownBitDepths()
            val tracks = trackDao.getAllTracksList()
            for (t in tracks) {
                if (t.localFilePath.isNotBlank()) {
                    val file = File(t.localFilePath)
                    if (file.exists() && file.isFile) {
                        val real = com.anuj.obsidian.data.local.AudioMetadataExtractor.extract(
                            context = context,
                            uri = android.net.Uri.fromFile(file),
                            fallbackName = file.name,
                            docFile = null,
                            localFilePath = t.localFilePath
                        )
                        val realFormat = file.extension.uppercase().ifBlank { t.format }
                        val fmtLower = realFormat.lowercase()
                        val isLossless = fmtLower in setOf("flac", "alac", "wav", "dsf", "dff")
                        val realBitDepth = if (isLossless) {
                            real.bitDepth.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) } ?: t.bitDepth
                        } else {
                            ""
                        }
                        val realSampleRate = real.sampleRate.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) } ?: t.sampleRate
                        val realDuration = if (real.durationSeconds > 0) real.durationSeconds else t.durationSeconds

                        if (realBitDepth != t.bitDepth || realSampleRate != t.sampleRate || realFormat != t.format || (realDuration > 0 && realDuration != t.durationSeconds)) {
                            trackDao.updateTrack(t.copy(
                                bitDepth = realBitDepth,
                                sampleRate = realSampleRate,
                                format = realFormat,
                                durationSeconds = realDuration
                            ))
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ObsidianRepository.sanitizeLibraryMetadata")
        }
    }

    // ---------------- Search & Discovery ----------------
    suspend fun parseUrlOrSearch(query: String): List<SearchResultItem> {
        if (settingsRepository.settings.value.offlineMode) {
            val q = query.trim().lowercase()
            val queryTokens = q.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val local = trackDao.getAllTracksList().filter { track ->
                val combinedText = "${track.title} ${track.artist} ${track.album} ${track.genre} ${track.providerSource} ${track.localFilePath}".lowercase()
                queryTokens.all { token -> combinedText.contains(token) }
            }
            return local.map {
                SearchResultItem(
                    id = it.id.toString(),
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    durationSeconds = it.durationSeconds,
                    coverUrl = it.albumArtUrl,
                    streamUrl = it.localFilePath,
                    provider = "Local (Offline)",
                    bitDepth = it.bitDepth,
                    sampleRate = it.sampleRate,
                    isrc = it.isrc,
                    releaseYear = it.releaseDate
                )
            }
        }
        return MusicApiService.searchTracks(query)
    }
}
