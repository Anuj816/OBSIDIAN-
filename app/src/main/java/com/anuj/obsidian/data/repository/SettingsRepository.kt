package com.anuj.obsidian.data.repository

import android.content.Context
import android.util.Log
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.TrackDao
import com.anuj.obsidian.data.model.AppSettings
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.FullPlaybackState
import com.anuj.obsidian.util.safeLaunch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * SettingsRepository: Manages application-wide settings, user preferences persistence (SharedPreferences),
 * and playback state persistence across app restarts.
 */
class SettingsRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("obsidian_audio_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        loadSettingsFromPreferences()
    }

    private fun loadSettingsFromPreferences() {
        try {
            val savedPreset = prefs.getString("themePreset", "emerald") ?: "emerald"
            val savedPrim = prefs.getLong("customPrimaryColor", 0xFF1DB954L)
            val savedSec = prefs.getLong("customSecondaryColor", 0xFFF5A623L)
            val savedBg = prefs.getLong("customBackgroundColor", 0xFF090C10L)
            val savedPerf = prefs.getString("visualizerPerformanceMode", "smooth_60") ?: "smooth_60"
            val savedGapless = prefs.getBoolean("gaplessPlayback", true)
            val savedCrossfade = prefs.getFloat("crossfadeDurationSec", 0f)
            val savedVolNorm = prefs.getBoolean("volumeNormalizationEnabled", false)
            val savedOfflineMode = prefs.getBoolean("offlineMode", false)
            val savedAutoplayOnLaunch = prefs.getBoolean("autoplayOnLaunch", false)
            val savedTrackId = prefs.getLong("lastPlayedTrackId", 0L)
            val savedPos = prefs.getFloat("lastPlayedPositionSec", 0f)
            val savedTrackJson = prefs.getString("lastPlayedTrackJson", "") ?: ""
            val savedVisualizerPresetId = prefs.getInt("lastVisualizerPresetId", 3)
            val savedVisualizerPresetName = prefs.getString("lastVisualizerPresetName", "") ?: ""
            val savedVisualizerPresetPath = prefs.getString("lastVisualizerPresetPath", "") ?: ""
            val savedVisualizerMaster = prefs.getBoolean("visualizerMasterEnabled", true)
            val savedVisualizerAboveChrome = prefs.getBoolean("visualizerAboveChrome", false)
            val savedVisualizerBgMode = prefs.getString("visualizerBackgroundMode", "dynamic") ?: "dynamic"
            val savedVisualizerFpsLimit = prefs.getInt("visualizerFpsLimit", 60)
            val savedVisualizerStyleCategory = prefs.getString("visualizerStyleCategory", "ALL") ?: "ALL"
            val savedVisualizerCycleMode = prefs.getString("visualizerCycleMode", "off") ?: "off"
            val savedBgVisualizer = prefs.getBoolean("backgroundVisualizerEnabled", true)
            val savedVisualizerShowEverywhere = prefs.getBoolean("visualizerShowEverywhere", true)
            val savedVisualizerAlpha = prefs.getFloat("visualizerAlpha", 0.85f)
            val savedVisualizerColorMode = prefs.getString("visualizerColorMode", "ORIGINAL") ?: "ORIGINAL"
            val savedInactivityTimeout = prefs.getInt("inactivityTimeoutMinutes", 60)
            val savedAutoplayMode = prefs.getString("autoplayMode", "AUTOPLAY_LIBRARY") ?: "AUTOPLAY_LIBRARY"
            val savedAdvancedShuffleMode = prefs.getString("advancedShuffleMode", "SHUFFLE_OFF") ?: "SHUFFLE_OFF"
            val savedQueueReturnMode = prefs.getString("queueReturnMode", "RETURN_TO_PREVIOUS") ?: "RETURN_TO_PREVIOUS"
            val savedHeadsetAutoresume = prefs.getBoolean("headsetAutoresume", false)
            val savedHeadsetFadeOut = prefs.getBoolean("headsetFadeOutOnDisconnect", false)
            val savedHeadsetFadeOutDur = prefs.getFloat("headsetFadeOutDurationSec", 1.5f)
            val savedDynamicCanvasEnabled = prefs.getBoolean("dynamicCanvasEnabled", false)
            val savedDynamicCanvasPreset = prefs.getString("dynamicCanvasPreset", "cosmic_nebula") ?: "cosmic_nebula"
            val savedHasCompletedOnboarding = prefs.getBoolean("hasCompletedOnboarding", false)
            val savedDefaultLibraryTab = prefs.getString("defaultLibraryTab", "SONGS") ?: "SONGS"
            val savedHasSeenCoachMarkMiniPlayerSwipe = prefs.getBoolean("hasSeenCoachMarkMiniPlayerSwipe", false)
            val savedHasSeenCoachMarkShuffleLongPress = prefs.getBoolean("hasSeenCoachMarkShuffleLongPress", false)
            val savedHasSeenCoachMarkSettingsSwipe = prefs.getBoolean("hasSeenCoachMarkSettingsSwipe", false)
            if (!prefs.getBoolean("showLayoutBounds_v2_initialized", false)) {
                prefs.edit().putBoolean("showLayoutBounds", false).putBoolean("showLayoutBounds_v2_initialized", true).apply()
            }
            val savedShowLayoutBounds = prefs.getBoolean("showLayoutBounds", false)
            val savedFoldersSet = prefs.getStringSet("linkedMusicFolders", emptySet()) ?: emptySet()
            val savedPresetFoldersSet = prefs.getStringSet("linkedCustomPresetFolders", emptySet()) ?: emptySet()

            val parsedFolders = savedFoldersSet.mapNotNull {
                val parts = it.split("|", limit = 2)
                if (parts.size == 2) Pair(parts[0], parts[1]) else null
            }
            val parsedPresetFolders = savedPresetFoldersSet.mapNotNull {
                val parts = it.split("|", limit = 2)
                if (parts.size == 2) Pair(parts[0], parts[1]) else null
            }

            _settings.value = _settings.value.copy(
                linkedMusicFolders = parsedFolders,
                linkedCustomPresetFolders = parsedPresetFolders,
                themePreset = savedPreset,
                customPrimaryColor = savedPrim,
                customSecondaryColor = savedSec,
                customBackgroundColor = savedBg,
                visualizerPerformanceMode = savedPerf,
                gaplessPlayback = savedGapless,
                crossfadeDurationSec = savedCrossfade,
                volumeNormalizationEnabled = savedVolNorm,
                offlineMode = savedOfflineMode,
                autoplayOnLaunch = savedAutoplayOnLaunch,
                lastPlayedTrackId = savedTrackId,
                lastPlayedPositionSec = savedPos,
                lastPlayedTrackJson = savedTrackJson,
                lastVisualizerPresetId = savedVisualizerPresetId,
                lastVisualizerPresetName = savedVisualizerPresetName,
                lastVisualizerPresetPath = savedVisualizerPresetPath,
                visualizerMasterEnabled = savedVisualizerMaster,
                visualizerAboveChrome = savedVisualizerAboveChrome,
                visualizerBackgroundMode = savedVisualizerBgMode,
                visualizerFpsLimit = savedVisualizerFpsLimit,
                visualizerStyleCategory = savedVisualizerStyleCategory,
                visualizerCycleMode = savedVisualizerCycleMode,
                backgroundVisualizerEnabled = savedBgVisualizer,
                visualizerShowEverywhere = savedVisualizerShowEverywhere,
                visualizerAlpha = savedVisualizerAlpha,
                visualizerColorMode = savedVisualizerColorMode,
                inactivityTimeoutMinutes = savedInactivityTimeout,
                autoplayMode = savedAutoplayMode,
                advancedShuffleMode = savedAdvancedShuffleMode,
                queueReturnMode = savedQueueReturnMode,
                headsetAutoresume = savedHeadsetAutoresume,
                headsetFadeOutOnDisconnect = savedHeadsetFadeOut,
                headsetFadeOutDurationSec = savedHeadsetFadeOutDur,
                dynamicCanvasEnabled = savedDynamicCanvasEnabled,
                dynamicCanvasPreset = savedDynamicCanvasPreset,
                hasCompletedOnboarding = savedHasCompletedOnboarding,
                defaultLibraryTab = savedDefaultLibraryTab,
                hasSeenCoachMarkMiniPlayerSwipe = savedHasSeenCoachMarkMiniPlayerSwipe,
                hasSeenCoachMarkShuffleLongPress = savedHasSeenCoachMarkShuffleLongPress,
                hasSeenCoachMarkSettingsSwipe = savedHasSeenCoachMarkSettingsSwipe,
                showLayoutBounds = savedShowLayoutBounds
            )
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "SettingsRepository.loadSettings")
            Log.e("SettingsRepository", "Failed to load preferences cleanly. Falling back to default settings.", e)
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated

        prefs.edit()
            .putBoolean("hasCompletedOnboarding", updated.hasCompletedOnboarding)
            .putString("defaultLibraryTab", updated.defaultLibraryTab)
            .putBoolean("hasSeenCoachMarkMiniPlayerSwipe", updated.hasSeenCoachMarkMiniPlayerSwipe)
            .putBoolean("hasSeenCoachMarkShuffleLongPress", updated.hasSeenCoachMarkShuffleLongPress)
            .putBoolean("hasSeenCoachMarkSettingsSwipe", updated.hasSeenCoachMarkSettingsSwipe)
            .putBoolean("generateLrcFile", updated.generateLrcFile)
            .putString("storagePath", updated.storagePath)
            .apply()
    }

    fun saveBackgroundColor(color: Long) {
        try {
            synchronized(this) {
                prefs.edit().putLong("customBackgroundColor", color).apply()
                android.util.Log.d("SettingsRepository", "Saved background color: ${color.toString(16)}")
                
                _settings.value = _settings.value.copy(
                    customBackgroundColor = color
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("SettingsRepository", "Failed to save background color: ${e.message}", e)
        }
    }

    fun persistThemeSettings(themePreset: String, customPrim: Long, customSec: Long, customBg: Long, perfMode: String) {
        prefs.edit()
            .putString("themePreset", themePreset)
            .putLong("customPrimaryColor", customPrim)
            .putLong("customSecondaryColor", customSec)
            .putLong("customBackgroundColor", customBg)
            .putString("visualizerPerformanceMode", perfMode)
            .apply()

        _settings.value = _settings.value.copy(
            themePreset = themePreset,
            customPrimaryColor = customPrim,
            customSecondaryColor = customSec,
            customBackgroundColor = customBg,
            visualizerPerformanceMode = perfMode
        )
    }

    fun persistGaplessSettings(gapless: Boolean, crossfadeSec: Float) {
        prefs.edit()
            .putBoolean("gaplessPlayback", gapless)
            .putFloat("crossfadeDurationSec", crossfadeSec)
            .apply()

        _settings.value = _settings.value.copy(
            gaplessPlayback = gapless,
            crossfadeDurationSec = crossfadeSec
        )
    }

    fun persistVolumeNormalization(enabled: Boolean) {
        prefs.edit()
            .putBoolean("volumeNormalizationEnabled", enabled)
            .apply()

        _settings.value = _settings.value.copy(
            volumeNormalizationEnabled = enabled
        )
    }

    fun persistVisualizerSettings(presetId: Int, bgEnabled: Boolean, alpha: Float, presetName: String = "", presetPath: String = "") {
        prefs.edit()
            .putInt("lastVisualizerPresetId", presetId)
            .putString("lastVisualizerPresetName", presetName)
            .putString("lastVisualizerPresetPath", presetPath)
            .putBoolean("backgroundVisualizerEnabled", bgEnabled)
            .putFloat("visualizerAlpha", alpha)
            .apply()

        _settings.value = _settings.value.copy(
            lastVisualizerPresetId = presetId,
            lastVisualizerPresetName = presetName,
            lastVisualizerPresetPath = presetPath,
            backgroundVisualizerEnabled = bgEnabled,
            visualizerAlpha = alpha
        )
    }

    fun persistVisualizerMasterEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("visualizerMasterEnabled", enabled)
            .putBoolean("backgroundVisualizerEnabled", enabled)
            .apply()
        _settings.value = _settings.value.copy(
            visualizerMasterEnabled = enabled,
            backgroundVisualizerEnabled = enabled
        )
    }

    fun persistVisualizerAboveChrome(aboveChrome: Boolean) {
        prefs.edit()
            .putBoolean("visualizerAboveChrome", aboveChrome)
            .apply()
        _settings.value = _settings.value.copy(visualizerAboveChrome = aboveChrome)
    }

    fun persistVisualizerShowEverywhere(showEverywhere: Boolean) {
        prefs.edit()
            .putBoolean("visualizerShowEverywhere", showEverywhere)
            .apply()
        _settings.value = _settings.value.copy(visualizerShowEverywhere = showEverywhere)
    }

    fun persistBackgroundVisualizerEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("backgroundVisualizerEnabled", enabled)
            .apply()
        _settings.value = _settings.value.copy(backgroundVisualizerEnabled = enabled)
    }

    fun persistVisualizerBackgroundMode(mode: String) {
        prefs.edit()
            .putString("visualizerBackgroundMode", mode)
            .apply()
        _settings.value = _settings.value.copy(visualizerBackgroundMode = mode)
    }

    fun persistVisualizerFpsLimit(fps: Int) {
        prefs.edit()
            .putInt("visualizerFpsLimit", fps)
            .apply()
        _settings.value = _settings.value.copy(visualizerFpsLimit = fps)
    }

    fun persistVisualizerStyleCategory(category: String) {
        prefs.edit()
            .putString("visualizerStyleCategory", category)
            .apply()
        _settings.value = _settings.value.copy(visualizerStyleCategory = category)
    }

    fun persistVisualizerCycleMode(mode: String) {
        prefs.edit()
            .putString("visualizerCycleMode", mode)
            .apply()

        _settings.value = _settings.value.copy(
            visualizerCycleMode = mode
        )
    }

    fun persistVisualizerColorMode(mode: String) {
        prefs.edit()
            .putString("visualizerColorMode", mode)
            .apply()

        _settings.value = _settings.value.copy(
            visualizerColorMode = mode
        )
    }

    fun persistAutoplayOnLaunch(enabled: Boolean) {
        prefs.edit()
            .putBoolean("autoplayOnLaunch", enabled)
            .apply()

        _settings.value = _settings.value.copy(
            autoplayOnLaunch = enabled
        )
    }

    fun persistInactivityTimeout(minutes: Int) {
        prefs.edit()
            .putInt("inactivityTimeoutMinutes", minutes)
            .apply()

        _settings.value = _settings.value.copy(
            inactivityTimeoutMinutes = minutes
        )
    }

    fun persistAutoplayMode(mode: String) {
        prefs.edit()
            .putString("autoplayMode", mode)
            .apply()

        _settings.value = _settings.value.copy(
            autoplayMode = mode
        )
    }

    fun persistAdvancedShuffleMode(mode: String) {
        prefs.edit()
            .putString("advancedShuffleMode", mode)
            .apply()

        _settings.value = _settings.value.copy(
            advancedShuffleMode = mode
        )
    }

    fun persistQueueReturnMode(mode: String) {
        prefs.edit()
            .putString("queueReturnMode", mode)
            .apply()

        _settings.value = _settings.value.copy(
            queueReturnMode = mode
        )
    }

    fun persistHeadsetActions(autoresume: Boolean, fadeOut: Boolean, fadeOutDurSec: Float = 1.5f) {
        prefs.edit()
            .putBoolean("headsetAutoresume", autoresume)
            .putBoolean("headsetFadeOutOnDisconnect", fadeOut)
            .putFloat("headsetFadeOutDurationSec", fadeOutDurSec)
            .apply()

        _settings.value = _settings.value.copy(
            headsetAutoresume = autoresume,
            headsetFadeOutOnDisconnect = fadeOut,
            headsetFadeOutDurationSec = fadeOutDurSec
        )
    }

    fun persistDynamicCanvasSettings(enabled: Boolean, preset: String) {
        prefs.edit()
            .putBoolean("dynamicCanvasEnabled", enabled)
            .putString("dynamicCanvasPreset", preset)
            .apply()

        _settings.value = _settings.value.copy(
            dynamicCanvasEnabled = enabled,
            dynamicCanvasPreset = preset
        )
    }

    fun persistShowLayoutBounds(enabled: Boolean) {
        prefs.edit().putBoolean("showLayoutBounds", enabled).apply()
        _settings.value = _settings.value.copy(showLayoutBounds = enabled)
    }

    fun setOfflineMode(enabled: Boolean) {
        prefs.edit().putBoolean("offlineMode", enabled).apply()
        _settings.value = _settings.value.copy(offlineMode = enabled)
    }

    fun addLinkedMusicFolder(uri: String, name: String) {
        val current = _settings.value.linkedMusicFolders.toMutableList()
        if (current.none { it.first == uri }) {
            current.add(Pair(uri, name))
            saveLinkedFolders(current)
            _settings.value = _settings.value.copy(linkedMusicFolders = current)
        }
    }

    fun setLinkedMusicFolders(folders: List<Pair<String, String>>) {
        saveLinkedFolders(folders)
        _settings.value = _settings.value.copy(linkedMusicFolders = folders)
    }

    fun removeLinkedMusicFolder(uri: String, externalScope: CoroutineScope = scope) {
        val current = _settings.value.linkedMusicFolders.toMutableList()
        current.removeAll { it.first == uri }
        saveLinkedFolders(current)
        _settings.value = _settings.value.copy(linkedMusicFolders = current)

        externalScope.safeLaunch("RemoveLinkedMusicFolderTracks", Dispatchers.IO) {
            try {
                val allTracks = trackDao.getAllTracksList()
                val tracksToDelete = allTracks.filter { it.localFilePath.startsWith(uri) }
                for (track in tracksToDelete) {
                    trackDao.deleteTrack(track)
                }
            } catch (e: Throwable) {
                ObsidianCrashLogger.logException(e, "SettingsRepository.removeLinkedMusicFolder")
            }
        }
    }

    private fun saveLinkedFolders(folders: List<Pair<String, String>>) {
        val set = folders.map { "${it.first}|${it.second}" }.toSet()
        prefs.edit().putStringSet("linkedMusicFolders", set).apply()
    }

    fun saveLastPlaybackState(track: DownloadedTrack?, positionSec: Float) {
        scope.safeLaunch("SettingsRepositorySaveLastPlayback", Dispatchers.IO) {
            try {
                if (track != null) {
                    val json = JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        put("durationSeconds", track.durationSeconds)
                        put("format", track.format)
                        put("bitDepth", track.bitDepth)
                        put("sampleRate", track.sampleRate)
                        put("bitrateKbps", track.bitrateKbps)
                        put("localFilePath", track.localFilePath)
                        put("albumArtUrl", track.albumArtUrl)
                        put("fileSizeMb", track.fileSizeMb.toDouble())
                        put("lyrics", track.lyrics)
                        put("isrc", track.isrc)
                        put("releaseDate", track.releaseDate)
                        put("genre", track.genre)
                        put("providerSource", track.providerSource)
                        put("isFavorite", track.isFavorite)
                        put("dateAddedTimestamp", track.dateAddedTimestamp)
                    }.toString()

                    prefs.edit()
                        .putLong("lastPlayedTrackId", track.id)
                        .putFloat("lastPlayedPositionSec", positionSec)
                        .putString("lastPlayedTrackJson", json)
                        .apply()

                    _settings.value = _settings.value.copy(
                        lastPlayedTrackId = track.id,
                        lastPlayedPositionSec = positionSec,
                        lastPlayedTrackJson = json
                    )
                } else {
                    prefs.edit()
                        .putFloat("lastPlayedPositionSec", positionSec)
                        .apply()
                    _settings.value = _settings.value.copy(
                        lastPlayedPositionSec = positionSec
                    )
                }
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "SettingsRepository.saveLastPlaybackState")
            }
        }
    }

    fun saveActiveQueue(trackIds: List<Long>) {
        scope.safeLaunch("SettingsRepositorySaveQueue", Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                trackIds.forEach { jsonArray.put(it) }
                prefs.edit().putString("activeQueueIds", jsonArray.toString()).apply()
            } catch (e: Throwable) {
                ObsidianCrashLogger.logException(e, "SettingsRepository.saveActiveQueue")
            }
        }
    }

    fun saveShuffledQueue(trackIds: List<Long>, shuffledIndex: Int) {
        scope.safeLaunch("SettingsRepositorySaveShuffledQueue", Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                trackIds.forEach { jsonArray.put(it) }
                prefs.edit()
                    .putString("shuffledTrackIds", jsonArray.toString())
                    .putInt("shuffledIndex", shuffledIndex)
                    .apply()
            } catch (e: Throwable) {
                ObsidianCrashLogger.logException(e, "SettingsRepository.saveShuffledQueue")
            }
        }
    }

    suspend fun restoreShuffledQueue(): Pair<List<Long>, Int> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString("shuffledTrackIds", "[]") ?: "[]"
        val shuffledIndex = prefs.getInt("shuffledIndex", 0)
        val list = mutableListOf<Long>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getLong(i))
            }
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "SettingsRepository.restoreShuffledQueue")
        }
        Pair(list, shuffledIndex)
    }

    fun saveFullPlaybackState(
        track: DownloadedTrack?,
        positionSec: Float,
        isPlaying: Boolean,
        isShuffle: Boolean,
        repeatMode: Int,
        speed: Float,
        autoplayMode: String,
        queueTrackIds: List<Long>,
        shuffledTrackIds: List<Long> = emptyList(),
        shuffledIndex: Int = 0
    ) {
        scope.safeLaunch("SettingsRepositorySaveFullState", Dispatchers.IO) {
            try {
                val json = if (track != null) {
                    JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        put("durationSeconds", track.durationSeconds)
                        put("format", track.format)
                        put("bitDepth", track.bitDepth)
                        put("sampleRate", track.sampleRate)
                        put("bitrateKbps", track.bitrateKbps)
                        put("localFilePath", track.localFilePath)
                        put("albumArtUrl", track.albumArtUrl)
                        put("fileSizeMb", track.fileSizeMb.toDouble())
                        put("lyrics", track.lyrics)
                        put("isrc", track.isrc)
                        put("releaseDate", track.releaseDate)
                        put("genre", track.genre)
                        put("providerSource", track.providerSource)
                        put("isFavorite", track.isFavorite)
                        put("dateAddedTimestamp", track.dateAddedTimestamp)
                    }.toString()
                } else ""

                val queueJsonArray = JSONArray()
                queueTrackIds.forEach { queueJsonArray.put(it) }

                val shuffledJsonArray = JSONArray()
                shuffledTrackIds.forEach { shuffledJsonArray.put(it) }

                val editor = prefs.edit()
                    .putLong("lastPlayedTrackId", track?.id ?: 0L)
                    .putFloat("lastPlayedPositionSec", positionSec)
                    .putString("lastPlayedTrackJson", json)
                    .putBoolean("isPlaying", isPlaying)
                    .putBoolean("isShuffle", isShuffle)
                    .putInt("repeatMode", repeatMode)
                    .putFloat("playbackSpeed", speed)
                    .putString("autoplayMode", autoplayMode)
                    .putString("activeQueueIds", queueJsonArray.toString())
                    .putString("shuffledTrackIds", shuffledJsonArray.toString())
                    .putInt("shuffledIndex", shuffledIndex)

                editor.apply()

                _settings.value = _settings.value.copy(
                    lastPlayedTrackId = track?.id ?: 0L,
                    lastPlayedPositionSec = positionSec,
                    lastPlayedTrackJson = json
                )
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "SettingsRepository.saveFullPlaybackState")
            }
        }
    }

    suspend fun restoreFullPlaybackState(): FullPlaybackState = withContext(Dispatchers.IO) {
        val savedId = prefs.getLong("lastPlayedTrackId", 0L)
        val savedPos = prefs.getFloat("lastPlayedPositionSec", 0f)
        val isPlaying = prefs.getBoolean("isPlaying", false)
        val isShuffle = prefs.getBoolean("isShuffle", false)
        val repeatMode = prefs.getInt("repeatMode", androidx.media3.common.Player.REPEAT_MODE_OFF)
        val speed = prefs.getFloat("playbackSpeed", 1.0f)
        val autoplayMode = prefs.getString("autoplayMode", "AUTOPLAY_LIBRARY") ?: "AUTOPLAY_LIBRARY"

        val jsonString = prefs.getString("activeQueueIds", "[]") ?: "[]"
        val queueTrackIds = mutableListOf<Long>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                queueTrackIds.add(jsonArray.getLong(i))
            }
        } catch (e: Exception) {
            ObsidianCrashLogger.logException(e, "SettingsRepository.restoreFullPlaybackState - queue")
        }

        val shuffledJsonString = prefs.getString("shuffledTrackIds", "[]") ?: "[]"
        val shuffledIndex = prefs.getInt("shuffledIndex", 0)
        val shuffledTrackIds = mutableListOf<Long>()
        try {
            val jsonArray = JSONArray(shuffledJsonString)
            for (i in 0 until jsonArray.length()) {
                shuffledTrackIds.add(jsonArray.getLong(i))
            }
        } catch (e: Exception) {
            ObsidianCrashLogger.logException(e, "SettingsRepository.restoreFullPlaybackState - shuffled")
        }

        var track: DownloadedTrack? = null
        if (savedId > 0L) {
            track = trackDao.getTrackById(savedId)
        }
        if (track == null) {
            val savedJson = prefs.getString("lastPlayedTrackJson", "") ?: ""
            if (savedJson.isNotBlank()) {
                try {
                    val obj = JSONObject(savedJson)
                    track = DownloadedTrack(
                        id = obj.optLong("id", 1L),
                        title = obj.optString("title", "Studio Master"),
                        artist = obj.optString("artist", "Lossless Artist"),
                        album = obj.optString("album", "Hi-Res Album"),
                        durationSeconds = obj.optInt("durationSeconds", 210),
                        format = obj.optString("format", "FLAC"),
                        bitDepth = obj.optString("bitDepth", "16-bit"),
                        sampleRate = obj.optString("sampleRate", "44.1 kHz"),
                        bitrateKbps = obj.optInt("bitrateKbps", 320),
                        albumArtUrl = obj.optString("albumArtUrl", ""),
                        localFilePath = obj.optString("localFilePath", ""),
                        fileSizeMb = obj.optDouble("fileSizeMb", 25.0).toFloat(),
                        lyrics = obj.optString("lyrics", ""),
                        isrc = obj.optString("isrc", ""),
                        releaseDate = obj.optString("releaseDate", "2024"),
                        genre = obj.optString("genre", "Studio Master"),
                        providerSource = obj.optString("providerSource", "Obsidian Lossless Engine"),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        dateAddedTimestamp = obj.optLong("dateAddedTimestamp", System.currentTimeMillis())
                    )
                } catch (e: Exception) {
                    ObsidianCrashLogger.logException(e, "SettingsRepository.restoreFullPlaybackState - parseTrack")
                }
            }
        }

        FullPlaybackState(
            track = track,
            positionSec = savedPos,
            isPlaying = isPlaying,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            speed = speed,
            autoplayMode = autoplayMode,
            queueTrackIds = queueTrackIds,
            shuffledTrackIds = shuffledTrackIds,
            shuffledIndex = shuffledIndex
        )
    }

    suspend fun restoreActiveQueue(): List<Long> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString("activeQueueIds", "[]") ?: "[]"
        val list = mutableListOf<Long>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getLong(i))
            }
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "SettingsRepository.restoreActiveQueue")
        }
        list
    }

    suspend fun restoreLastPlayedTrack(): Pair<DownloadedTrack?, Float> = withContext(Dispatchers.IO) {
        val savedId = prefs.getLong("lastPlayedTrackId", 0L)
        val savedPos = prefs.getFloat("lastPlayedPositionSec", 0f)
        if (savedId > 0L) {
            val fromDb = trackDao.getTrackById(savedId)
            if (fromDb != null) {
                return@withContext Pair(fromDb, savedPos)
            }
        }
        val savedJson = prefs.getString("lastPlayedTrackJson", "") ?: ""
        if (savedJson.isNotBlank()) {
            try {
                val obj = JSONObject(savedJson)
                val track = DownloadedTrack(
                    id = obj.optLong("id", 1L),
                    title = obj.optString("title", "Studio Master"),
                    artist = obj.optString("artist", "Lossless Artist"),
                    album = obj.optString("album", "Hi-Res Album"),
                    durationSeconds = obj.optInt("durationSeconds", 210),
                    format = obj.optString("format", "FLAC"),
                    bitDepth = obj.optString("bitDepth", "16-bit"),
                    sampleRate = obj.optString("sampleRate", "44.1 kHz"),
                    bitrateKbps = obj.optInt("bitrateKbps", 320),
                    albumArtUrl = obj.optString("albumArtUrl", ""),
                    localFilePath = obj.optString("localFilePath", ""),
                    fileSizeMb = obj.optDouble("fileSizeMb", 25.0).toFloat(),
                    lyrics = obj.optString("lyrics", ""),
                    isrc = obj.optString("isrc", ""),
                    releaseDate = obj.optString("releaseDate", "2024"),
                    genre = obj.optString("genre", "Studio Master"),
                    providerSource = obj.optString("providerSource", "Obsidian Lossless Engine"),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    dateAddedTimestamp = obj.optLong("dateAddedTimestamp", System.currentTimeMillis())
                )
                return@withContext Pair(track, savedPos)
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "SettingsRepository.restoreLastPlayedTrack")
            }
        }
        return@withContext Pair(null, 0f)
    }

    fun saveVisualizerFadeOpacity(opacity: Float) {
        try {
            prefs.edit()
                .putFloat("visualizer_fade_opacity", opacity.coerceIn(0f, 1f))
                .apply()
            Log.d("SettingsRepository", "Saved visualizer fade opacity: $opacity")
        } catch (e: Throwable) {
            Log.e("SettingsRepository", "Failed to save visualizer fade opacity: ${e.message}")
        }
    }

    fun saveVisualizerFadeMode(mode: com.anuj.obsidian.ui.visualizer.VisualizerFadeController.FadeMode) {
        try {
            prefs.edit()
                .putString("visualizer_fade_mode", mode.name)
                .apply()
            Log.d("SettingsRepository", "Saved visualizer fade mode: $mode")
        } catch (e: Throwable) {
            Log.e("SettingsRepository", "Failed to save visualizer fade mode: ${e.message}")
        }
    }

    fun getVisualizerFadeOpacity(): Float {
        return prefs.getFloat("visualizer_fade_opacity", 1f)
    }

    fun getVisualizerFadeMode(): com.anuj.obsidian.ui.visualizer.VisualizerFadeController.FadeMode {
        return try {
            val modeName = prefs.getString("visualizer_fade_mode", "DISABLED") ?: "DISABLED"
            com.anuj.obsidian.ui.visualizer.VisualizerFadeController.FadeMode.valueOf(modeName)
        } catch (e: Exception) {
            com.anuj.obsidian.ui.visualizer.VisualizerFadeController.FadeMode.DISABLED
        }
    }

    fun addLinkedCustomPresetFolder(folderUri: String, folderName: String) {
        val currentSet = prefs.getStringSet("linkedCustomPresetFolders", emptySet())?.toMutableSet() ?: mutableSetOf()
        val entry = "$folderUri|$folderName"
        currentSet.removeAll { it.startsWith("$folderUri|") }
        currentSet.add(entry)
        prefs.edit().putStringSet("linkedCustomPresetFolders", currentSet).apply()

        val parsed = currentSet.mapNotNull {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
        _settings.value = _settings.value.copy(linkedCustomPresetFolders = parsed)
    }

    fun removeLinkedCustomPresetFolder(folderUri: String) {
        val currentSet = prefs.getStringSet("linkedCustomPresetFolders", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.removeAll { it.startsWith("$folderUri|") }
        prefs.edit().putStringSet("linkedCustomPresetFolders", currentSet).apply()

        val parsed = currentSet.mapNotNull {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
        _settings.value = _settings.value.copy(linkedCustomPresetFolders = parsed)
    }
}
