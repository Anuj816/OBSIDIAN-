package com.anuj.obsidian.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuj.obsidian.ui.dsp.ObsidianDspEngine
import com.anuj.obsidian.data.model.AppSettings
import com.anuj.obsidian.data.model.AvailableAudioQualities
import com.anuj.obsidian.data.model.DownloadTask
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.PlaybackLoadingType
import com.anuj.obsidian.data.model.DspPreset
import com.anuj.obsidian.data.model.DspState
import com.anuj.obsidian.data.model.PREMADE_DSP_PRESETS
import com.anuj.obsidian.data.model.ExtensionRegistryEntry
import com.anuj.obsidian.data.remote.ExtensionRegistryService
import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.data.model.QueueReturnMode
import com.anuj.obsidian.data.model.PlaybackContext
import com.anuj.obsidian.data.model.PlaybackSourceType
import com.anuj.obsidian.data.model.ShuffleTraversal
import com.anuj.obsidian.data.model.AuthoritativePlaybackState
import com.anuj.obsidian.data.model.QueueEntry
import com.anuj.obsidian.data.model.SearchResultItem
import com.anuj.obsidian.data.model.SearchHistoryItem
import com.anuj.obsidian.data.model.TrendingChart
import com.anuj.obsidian.data.remote.MusicApiService
import com.anuj.obsidian.data.remote.TrendingChartsProvider
import com.anuj.obsidian.data.repository.ObsidianRepository
import com.anuj.obsidian.ui.player.AudioPlayerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import android.net.Uri
import android.util.Log
import java.io.File

enum class AppTab {
    PLAYER,
    HOME,
    LIBRARY,
    QUEUE,
    SETTINGS,
    FOLDER_SELECTION,
    FOLDER_EXPLORER
}
enum class AutoplayMode(val title: String, val subtitle: String, val badge: String) {
    AUTOPLAY_LIBRARY("Continuous Library Autoplay", "Automatically plays the next song from your library in sequence until changed", "📚"),
    QUEUE_ONLY("Active Queue Only", "Plays solely from your downloaded library and active queue without random web tracks", "📥"),
    AUTOPLAY_SIMILAR("Auto-Play Similar Master Tracks", "Discovers and streams similar lossless studio master tracks when your queue completes", "✨"),
    REPEAT_ONE("Repeat Current Track", "Continuously replays current track", "🔂"),
    REPEAT_ALL("Repeat Queue / Library", "Loops your entire queue and downloaded library indefinitely", "🔁")

}
@androidx.compose.runtime.Immutable
data class PlayerState(
    val currentTrack: DownloadedTrack? = null,
    val previousTrack: DownloadedTrack? = null,
    val nextTrack: DownloadedTrack? = null,
    val queue: List<DownloadedTrack> = emptyList(),
    val queueEntries: List<QueueEntry> = emptyList(),
    val upcomingTracks: List<DownloadedTrack> = emptyList(),
    val upcomingEntries: List<QueueEntry> = emptyList(),
    val isPlaying: Boolean = false,
    val loadingType: PlaybackLoadingType = PlaybackLoadingType.NONE,
    val durationSeconds: Int = 0,
    val isShuffle: Boolean = false,
    val shuffleMode: AdvancedShuffleMode = AdvancedShuffleMode.SHUFFLE_OFF,
    val queueReturnMode: QueueReturnMode = QueueReturnMode.RETURN_TO_PREVIOUS,
    val autoplayMode: AutoplayMode = AutoplayMode.AUTOPLAY_LIBRARY,
    val speed: Float = 1.0f,
    val isFullPlayerVisible: Boolean = false,
    val isLyricsVisible: Boolean = false,
    val isDriveModeVisible: Boolean = false,
    val isTagInspectorVisible: Boolean = false,
    val tagInspectorTrack: DownloadedTrack? = null,
    val queuePosition: Pair<Int, Int> = Pair(1, 1)
)

class ObsidianViewModel(
    private val repository: ObsidianRepository = com.anuj.obsidian.ObsidianApplication.repository,
    private val playerManager: AudioPlayerManager? = com.anuj.obsidian.ObsidianApplication.playerManager
) : ViewModel() {
    val searchCoordinator = com.anuj.obsidian.ui.controller.SearchPlaybackCoordinator()
    val queueController = com.anuj.obsidian.ui.controller.QueueController(playerManager)
    val batchTagController = com.anuj.obsidian.ui.controller.BatchTagController(
        com.anuj.obsidian.ObsidianApplication.instance,
        repository.trackDaoRef
    )

    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, e -> 
        android.util.Log.e("ObsidianViewModel", "Unhandled coroutine exception", e) 
        com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel")
    }

    private val similarResolutionMutex = Mutex()

    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            // Handled authoritatively by AudioPlayerManager callbacks
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            viewModelScope.launch(Dispatchers.Main) {
                when (playbackState) {
                    androidx.media3.common.Player.STATE_BUFFERING -> {
                        val track = playerManager?.getCurrentTrack() ?: _playerState.value.currentTrack
                        val isLikelyLocal = track != null && track.localFilePath.isNotBlank() &&
                            (track.localFilePath.startsWith("/") || track.localFilePath.startsWith("content://") || !track.localFilePath.startsWith("http"))
                        _playerState.value = _playerState.value.copy(
                            loadingType = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING
                        )
                    }
                    androidx.media3.common.Player.STATE_READY -> {
                        _playerState.value = _playerState.value.copy(
                            loadingType = PlaybackLoadingType.NONE,
                            isPlaying = playerManager?.isPlaying() ?: _playerState.value.isPlaying
                        )
                    }
                    androidx.media3.common.Player.STATE_ENDED -> {
                        _playerState.value = _playerState.value.copy(
                            loadingType = PlaybackLoadingType.NONE
                        )
                    }
                    androidx.media3.common.Player.STATE_IDLE -> {
                        if (playerManager?.getCurrentLoadingType() == PlaybackLoadingType.NONE) {
                            _playerState.value = _playerState.value.copy(
                                loadingType = PlaybackLoadingType.NONE
                            )
                        }
                    }
                }
            }
        }
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            viewModelScope.launch(Dispatchers.Main) {
                val currentTrack = playerManager?.getCurrentTrack() ?: _playerState.value.currentTrack
                if (currentTrack != null) {
                    val isLikelyLocal = currentTrack.localFilePath.isNotBlank() &&
                        (currentTrack.localFilePath.startsWith("/") || currentTrack.localFilePath.startsWith("content://") || !currentTrack.localFilePath.startsWith("http"))
                    val isReady = playerManager?.getExoPlayerPlaybackState() == androidx.media3.common.Player.STATE_READY
                    _playerState.value = _playerState.value.copy(
                        currentTrack = currentTrack,
                        loadingType = if (isReady) PlaybackLoadingType.NONE else (if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING)
                    )
                }
                syncPlayerStateFromMedia3()
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            viewModelScope.launch(Dispatchers.Main) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            // We ignore ExoPlayer's native shuffleModeEnabled value because we use a custom advanced shuffle mode
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            viewModelScope.launch(Dispatchers.Main) {
                val currentMode = _playerState.value.autoplayMode
                val mode = when (repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_ONE -> AutoplayMode.REPEAT_ONE
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> {
                        if (currentMode == AutoplayMode.AUTOPLAY_LIBRARY) AutoplayMode.AUTOPLAY_LIBRARY else AutoplayMode.REPEAT_ALL
                    }
                    else -> {
                        if (currentMode == AutoplayMode.REPEAT_ONE || currentMode == AutoplayMode.REPEAT_ALL) {
                            AutoplayMode.AUTOPLAY_LIBRARY
                        } else {
                            currentMode
                        }
                    }
                }
                if (_playerState.value.autoplayMode != AutoplayMode.AUTOPLAY_SIMILAR) {
                    _playerState.value = _playerState.value.copy(autoplayMode = mode)
                    repository.persistAutoplayMode(mode.name)
                }
            }
        }
    }

    private val _currentTab = MutableStateFlow(AppTab.PLAYER)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _isLibraryScanning = MutableStateFlow(false)
    val isLibraryScanning: StateFlow<Boolean> = _isLibraryScanning.asStateFlow()
    
    private val _isLibraryScrolling = MutableStateFlow(false)
    val isLibraryScrolling: StateFlow<Boolean> = _isLibraryScrolling.asStateFlow()
    
    fun setLibraryScrolling(isScrolling: Boolean) {
        if (_isLibraryScrolling.value != isScrolling) {
            _isLibraryScrolling.value = isScrolling
        }
    }

    private val _isInitialLibraryLoading = MutableStateFlow(true)
    val isInitialLibraryLoading: StateFlow<Boolean> = _isInitialLibraryLoading.asStateFlow()

    private val _isChartsLoading = MutableStateFlow(true)
    val isChartsLoading: StateFlow<Boolean> = _isChartsLoading.asStateFlow()

    private val _isRefreshingHome = MutableStateFlow(false)
    val isRefreshingHome: StateFlow<Boolean> = _isRefreshingHome.asStateFlow()

    private val _isQueueLoading = MutableStateFlow(true)
    val isQueueLoading: StateFlow<Boolean> = _isQueueLoading.asStateFlow()

    val tracks: StateFlow<List<DownloadedTrack>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedSmartPlaylist: StateFlow<List<DownloadedTrack>> = repository.recentlyPlayedTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onRepeatSmartPlaylist: StateFlow<List<DownloadedTrack>> = repository.onRepeatTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val forgottenFavoritesSmartPlaylist: StateFlow<List<DownloadedTrack>> = repository.forgottenFavoritesTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyMixSmartPlaylist: StateFlow<List<DownloadedTrack>> = combine(
        tracks,
        recentlyPlayedSmartPlaylist,
        repository.settings
    ) { allTracks, recentlyPlayed, currentSettings ->
        if (allTracks.isEmpty()) return@combine emptyList<DownloadedTrack>()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        
        if (currentSettings.dailyMixLastGeneratedDate == todayStr && currentSettings.dailyMixTrackIdsJson.isNotBlank()) {
            try {
                val savedIds = currentSettings.dailyMixTrackIdsJson.split(",").mapNotNull { it.trim().toLongOrNull() }
                val idToTrackMap = allTracks.associateBy { it.id }
                val restored = savedIds.mapNotNull { idToTrackMap[it] }
                if (restored.isNotEmpty()) return@combine restored
            } catch (e: Exception) {
                Log.e("ObsidianViewModel", "Failed to parse saved Daily Mix IDs", e)
            }
        }
        
        val topTracks = recentlyPlayed.ifEmpty { allTracks }
        val pool = (topTracks + allTracks).distinctBy { it.id }.shuffled(Random(todayStr.hashCode()))
        val selected = pool.take(20)
        val selectedIdsJson = selected.joinToString(",") { it.id.toString() }
        
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSettings { 
                it.copy(
                    dailyMixLastGeneratedDate = todayStr,
                    dailyMixTrackIdsJson = selectedIdsJson
                )
            }
        }
        
        selected
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun completeOnboarding(defaultTab: String) {
        repository.updateSettings { 
            it.copy(
                hasCompletedOnboarding = true,
                defaultLibraryTab = defaultTab
            )
        }
    }

    fun dismissCoachMark(markKey: String) {
        repository.updateSettings { 
            when (markKey) {
                "mini_player_swipe" -> it.copy(hasSeenCoachMarkMiniPlayerSwipe = true)
                "shuffle_long_press" -> it.copy(hasSeenCoachMarkShuffleLongPress = true)
                "settings_swipe" -> it.copy(hasSeenCoachMarkSettingsSwipe = true)
                else -> it
            }
        }
    }

    fun setFullPlayerSpecsExpanded(expanded: Boolean) {
        repository.updateSettings { it.copy(fullPlayerSpecsExpanded = expanded) }
    }

    val playlists: StateFlow<List<com.anuj.obsidian.data.model.Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _extensions = MutableStateFlow<List<ExtensionRegistryEntry>>(emptyList())
    val extensions: StateFlow<List<ExtensionRegistryEntry>> = _extensions.asStateFlow()

    val downloadTasks: StateFlow<List<DownloadTask>> = repository.downloadTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks: StateFlow<List<DownloadTask>> = repository.activeTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val searchHistory: StateFlow<List<SearchHistoryItem>> = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isUpdatingExtensions = MutableStateFlow(false)
    val isUpdatingExtensions: StateFlow<Boolean> = _isUpdatingExtensions.asStateFlow()

    val settings: StateFlow<AppSettings> = repository.settings

    val analyticsSummary: StateFlow<com.anuj.obsidian.data.model.AnalyticsSummary> = repository.analyticsSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.anuj.obsidian.data.model.AnalyticsSummary())

    override fun onCleared() {
        super.onCleared()
        playerManager?.withPlayer { removeListener(playerListener) }
        playerManager?.clearCallbacks()
        playerManager?.release()
    }

    fun checkAndAutoLinkDefaultFolder(context: android.content.Context) {
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            val currentLinked = repository.settings.value.linkedMusicFolders
            val musicDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "ObsidianObsidian")

            musicDir.mkdirs()
            val defaultPath = musicDir.absolutePath
            val uriStr = "file://" + defaultPath
            
            if (currentLinked.isEmpty()) {
                repository.addLinkedMusicFolder(uriStr, "Obsidian Music")
                repository.updateSettings { 
                    it.copy(
                        selectedMusicFolderUri = uriStr,
                        selectedMusicFolderName = "Obsidian Music",
                        storagePath = "$defaultPath/"
                    )
                }
                rescanCurrentDirectory(context)
            }
        }
    }
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerMinutes = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: kotlinx.coroutines.flow.StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        
        if (minutes <= 0) {
            playerManager?.setVolume(1.0f)
            return

        }
        sleepTimerJob = viewModelScope.launch(exceptionHandler) {
            val totalSeconds = minutes * 60
            for (i in totalSeconds downTo 30) {
                _sleepTimerMinutes.value = (i / 60) + if (i % 60 > 0) 1 else 0
                kotlinx.coroutines.delay(1000)
            }
            // Last 30 seconds fade out
            _sleepTimerMinutes.value = 0
            for (i in 30 downTo 1) {
                val volume = (i / 30f)
                playerManager?.setVolume(volume)
                kotlinx.coroutines.delay(1000)
            }
            playerManager?.pause()
            playerManager?.setVolume(1.0f) // Reset volume for next play
            _sleepTimerMinutes.value = null
    
        }
    }
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = null
        playerManager?.setVolume(1.0f)

    }
    // DSP Audio Customization Engine State

    private val _dspState = MutableStateFlow(DspState())
    val dspState: StateFlow<DspState> = _dspState.asStateFlow()

    // Trending Charts & Online Discovery Catalog
    private val _trendingCharts = MutableStateFlow<List<TrendingChart>>(emptyList())
    val trendingCharts: StateFlow<List<TrendingChart>> = _trendingCharts.asStateFlow()
    
    private val _selectedChartId = MutableStateFlow("chart_global")
    val selectedChartId: StateFlow<String> = _selectedChartId.asStateFlow()

    private val _featuredThisWeek = MutableStateFlow<List<SearchResultItem>>(TrendingChartsProvider.featuredThisWeek)
    val featuredThisWeek: StateFlow<List<SearchResultItem>> = _featuredThisWeek.asStateFlow()

    private val _stayIndie = MutableStateFlow<List<SearchResultItem>>(TrendingChartsProvider.stayIndie)
    val stayIndie: StateFlow<List<SearchResultItem>> = _stayIndie.asStateFlow()

    private val _onlineSearchSuggestions = MutableStateFlow<List<String>>(
        listOf("Espresso", "Blinding Lights", "Birds of a Feather", "Good Luck, Babe!", "Cruel Summer", "Lose Yourself", "Starboy", "Chaleya", "Husn", "Tu Hai Kahan", "Stay With Me", "Midnight City")
    )
    val onlineSearchSuggestions: StateFlow<List<String>> = _onlineSearchSuggestions.asStateFlow()
    
    init {
        loadRealTrendingCharts()

        val savedFadeOpacity = repository.getVisualizerFadeOpacity()
        val savedFadeMode = repository.getVisualizerFadeMode()
        playerManager?.let { pm ->
            pm.setVisualizerFadeOpacity(savedFadeOpacity)
            pm.setVisualizerFadeMode(savedFadeMode)
            Log.d("ObsidianViewModel", "Restored fade settings: opacity=$savedFadeOpacity, mode=$savedFadeMode")
        }

        viewModelScope.launch(exceptionHandler) {
            repository.allTracks.collect {
                _isInitialLibraryLoading.value = false
                _isQueueLoading.value = false
            }
        }
    }

    fun setVisualizerFadeOpacity(opacity: Float) {
        playerManager?.setVisualizerFadeOpacity(opacity)
        repository.saveVisualizerFadeOpacity(opacity)
    }

    fun setVisualizerFadeMode(mode: com.anuj.obsidian.ui.visualizer.VisualizerFadeController.FadeMode) {
        playerManager?.setVisualizerFadeMode(mode)
        repository.saveVisualizerFadeMode(mode)
    }
    private fun loadRealTrendingCharts() {
        viewModelScope.launch(exceptionHandler) {
            _isChartsLoading.value = true
            try {
                val globalHits = withContext(Dispatchers.IO) {
                    com.anuj.obsidian.data.remote.MusicApiService.fetchRealTrending("https://itunes.apple.com/us/rss/topsongs/limit=50/json")
                }
                val indieHits = withContext(Dispatchers.IO) {
                    com.anuj.obsidian.data.remote.MusicApiService.fetchRealTrending("https://itunes.apple.com/us/rss/topsongs/limit=25/genre=20/json")
                }
                val hipHop = withContext(Dispatchers.IO) {
                    com.anuj.obsidian.data.remote.MusicApiService.fetchRealTrending("https://itunes.apple.com/us/rss/topsongs/limit=50/genre=18/json")
                }
                val pop = withContext(Dispatchers.IO) {
                    com.anuj.obsidian.data.remote.MusicApiService.fetchRealTrending("https://itunes.apple.com/us/rss/topsongs/limit=50/genre=14/json")
                }
                val desi = withContext(Dispatchers.IO) {
                    com.anuj.obsidian.data.remote.MusicApiService.fetchRealTrending("https://itunes.apple.com/in/rss/topsongs/limit=50/json")
                }

                if (globalHits.isNotEmpty()) {
                    _featuredThisWeek.value = globalHits.take(10)
                }
                if (indieHits.isNotEmpty()) {
                    _stayIndie.value = indieHits.take(10)
                }

                // Dynamically derive popular search suggestions from the online lists
                val dynamicSuggestions = mutableListOf<String>()
                (globalHits + indieHits + desi).forEach { item ->
                    if (item.title.isNotBlank() && !dynamicSuggestions.contains(item.title)) {
                        dynamicSuggestions.add(item.title)
                    }
                    if (item.artist.isNotBlank() && !dynamicSuggestions.contains(item.artist)) {
                        dynamicSuggestions.add(item.artist)
                    }
                }
                if (dynamicSuggestions.isNotEmpty()) {
                    _onlineSearchSuggestions.value = dynamicSuggestions.take(18)
                }
                
                val charts = mutableListOf<TrendingChart>()
                if (globalHits.isNotEmpty()) {
                    charts.add(TrendingChart("chart_global", "Global Top Hits", "Top Trending Worldwide", "🔥", 0xFF1DB954, globalHits))
                }
                if (indieHits.isNotEmpty()) {
                    charts.add(TrendingChart("chart_indie", "Stay Indie & Alt", "Curated Indie & Alternative", "🎸", 0xFF9C27B0, indieHits))
                }
                if (hipHop.isNotEmpty()) {
                    charts.add(TrendingChart("chart_hiphop", "Hot Hip-Hop & Rap", "Top Charting Beats & Heavy Bass", "🎧", 0xFFE91E63, hipHop))
                }
                if (pop.isNotEmpty()) {
                    charts.add(TrendingChart("chart_pop", "Pop Anthems", "Current Trending Pop Hits", "🌟", 0xFF00E5FF, pop))
                }
                if (desi.isNotEmpty()) {
                    charts.add(TrendingChart("chart_desi", "Bollywood Hits", "Top Trending Indian Music", "🇮🇳", 0xFFFF9800, desi))
                }
                
                if (charts.isEmpty()) {
                    _trendingCharts.value = TrendingChartsProvider.allCharts
                    TrendingChartsProvider.allCharts.firstOrNull()?.id?.let { _selectedChartId.value = it }
                } else {
                    _trendingCharts.value = charts
                    charts.firstOrNull()?.id?.let { _selectedChartId.value = it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.loadRealTrendingCharts - attempt")
                _trendingCharts.value = TrendingChartsProvider.allCharts
                TrendingChartsProvider.allCharts.firstOrNull()?.id?.let { _selectedChartId.value = it }
            } finally {
                _isChartsLoading.value = false
                _isRefreshingHome.value = false
            }
        }
    }
    fun refreshHomeData() {
        _isRefreshingHome.value = true
        loadRealTrendingCharts()
    }
    fun selectChart(chartId: String) {
        _selectedChartId.value = chartId

    }
    private val _urlOrSearchQuery = MutableStateFlow("")
    val urlOrSearchQuery: StateFlow<String> = _urlOrSearchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    // Global Search State & Real-Time Room Database Indexing
    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _globalSearchFilter = MutableStateFlow(com.anuj.obsidian.data.model.GlobalSearchFilter.ALL)
    val globalSearchFilter: StateFlow<com.anuj.obsidian.data.model.GlobalSearchFilter> = _globalSearchFilter.asStateFlow()

    private val _isGlobalSearchVisible = MutableStateFlow(false)
    val isGlobalSearchVisible: StateFlow<Boolean> = _isGlobalSearchVisible.asStateFlow()

    val globalSearchResults: StateFlow<com.anuj.obsidian.data.model.GlobalSearchResultsState> = combine(
        _globalSearchQuery,
        _globalSearchFilter,
        repository.allTracks
    ) { query, filter, tracks ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            com.anuj.obsidian.data.model.GlobalSearchResultsState(query = query, selectedFilter = filter)
        } else {
            val matchingTracks = tracks.filter { track ->
                track.title.contains(trimmed, ignoreCase = true) ||
                track.artist.contains(trimmed, ignoreCase = true) ||
                track.album.contains(trimmed, ignoreCase = true) ||
                track.genre.contains(trimmed, ignoreCase = true)
            }

            // Index albums in real time from Room database tracks
            val matchingAlbums = tracks.filter { it.album.contains(trimmed, ignoreCase = true) || it.artist.contains(trimmed, ignoreCase = true) }
                .groupBy { "${it.album.trim().lowercase()}__${it.artist.trim().lowercase()}" }
                .map { (_, albumTracks) ->
                    val first = albumTracks.first()
                    com.anuj.obsidian.data.model.AlbumInfo(
                        title = first.album,
                        artist = first.artist,
                        coverArtUrl = albumTracks.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl ?: "",
                        year = first.releaseDate,
                        tracks = albumTracks
                    )
                }

            // Index artists in real time from Room database tracks
            val matchingArtists = tracks.filter { it.artist.contains(trimmed, ignoreCase = true) }
                .groupBy { it.artist.trim().lowercase() }
                .map { (_, artistTracks) ->
                    val first = artistTracks.first()
                    val distinctAlbumsCount = artistTracks.map { it.album.trim().lowercase() }.distinct().size
                    com.anuj.obsidian.data.model.ArtistInfo(
                        name = first.artist,
                        albumCount = distinctAlbumsCount,
                        tracks = artistTracks
                    )
                }

            // Index genres in real time from Room database tracks
            val matchingGenres = tracks.filter { it.genre.contains(trimmed, ignoreCase = true) }
                .groupBy { it.genre.trim().lowercase() }
                .map { (_, genreTracks) ->
                    val first = genreTracks.first()
                    com.anuj.obsidian.data.model.GenreInfo(
                        name = first.genre,
                        tracks = genreTracks
                    )
                }

            val total = when (filter) {
                com.anuj.obsidian.data.model.GlobalSearchFilter.ALL -> matchingTracks.size + matchingAlbums.size + matchingArtists.size
                com.anuj.obsidian.data.model.GlobalSearchFilter.TRACKS -> matchingTracks.size
                com.anuj.obsidian.data.model.GlobalSearchFilter.ALBUMS -> matchingAlbums.size
                com.anuj.obsidian.data.model.GlobalSearchFilter.ARTISTS -> matchingArtists.size
                com.anuj.obsidian.data.model.GlobalSearchFilter.GENRES -> matchingGenres.size
            }

            com.anuj.obsidian.data.model.GlobalSearchResultsState(
                query = query,
                selectedFilter = filter,
                matchingTracks = matchingTracks,
                matchingAlbums = matchingAlbums,
                matchingArtists = matchingArtists,
                matchingGenres = matchingGenres,
                totalCount = total,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.anuj.obsidian.data.model.GlobalSearchResultsState()
    )

    fun setGlobalSearchQuery(query: String) {
        _globalSearchQuery.value = query
    }

    fun setGlobalSearchFilter(filter: com.anuj.obsidian.data.model.GlobalSearchFilter) {
        _globalSearchFilter.value = filter
    }

    fun showGlobalSearch(show: Boolean) {
        _isGlobalSearchVisible.value = show
        if (!show) {
            _globalSearchQuery.value = ""
        }
    }

    // Player State
    private val _currentPositionSeconds = MutableStateFlow(0f)
    val currentPositionSeconds: StateFlow<Float> = _currentPositionSeconds.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var activeContextList: List<DownloadedTrack> = emptyList()

    private val _playbackSequence = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val playbackSequence: StateFlow<List<DownloadedTrack>> = _playbackSequence.asStateFlow()

    val previousTrack: StateFlow<DownloadedTrack?> = _playerState.map { it.previousTrack }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    val nextTrack: StateFlow<DownloadedTrack?> = _playerState.map { it.nextTrack }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    private fun buildMediaItemForTrack(track: DownloadedTrack): androidx.media3.common.MediaItem {
        playerManager?.registerTrackForResolution(track)
        val cachedUrl = playerManager?.getCachedStreamUrl(track.id)
        val uri = if (track.localFilePath.isNotBlank()) {
            if (track.localFilePath.startsWith("content://")) android.net.Uri.parse(track.localFilePath)
            else android.net.Uri.fromFile(java.io.File(track.localFilePath))
        } else if (!cachedUrl.isNullOrBlank()) {
            android.net.Uri.parse(cachedUrl)
        } else {
            android.net.Uri.parse("obsidian://resolve/${track.id}")
        }
        return playerManager?.buildMediaItem(track, uri) ?: run {
            val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .build()
            androidx.media3.common.MediaItem.Builder()
                .setUri(uri)
                .setMediaId(track.id.toString())
                .setMediaMetadata(mediaMetadata)
                .setTag(track)
                .build()
        }
    }




    private val _isFetchingLyrics = MutableStateFlow(false)
    private var fetchLyricsJob: Job? = null
    private val playedSimilarTrackIds = mutableSetOf<String>()
    val isFetchingLyrics: StateFlow<Boolean> = _isFetchingLyrics.asStateFlow()

    private val _crashLogs = MutableStateFlow<List<com.anuj.obsidian.CrashLogEntry>>(emptyList())
    val crashLogs: StateFlow<List<com.anuj.obsidian.CrashLogEntry>> = _crashLogs.asStateFlow()

    private val _recurringCrashGroups = MutableStateFlow<List<com.anuj.obsidian.RecurringCrashGroup>>(emptyList())
    val recurringCrashGroups: StateFlow<List<com.anuj.obsidian.RecurringCrashGroup>> = _recurringCrashGroups.asStateFlow()

    fun refreshCrashLogs() {
        _crashLogs.value = com.anuj.obsidian.ObsidianCrashLogger.getLogs()
        _recurringCrashGroups.value = com.anuj.obsidian.ObsidianCrashLogger.getRecurringCrashGroups()
    }

    fun clearCrashLogs() {
        com.anuj.obsidian.ObsidianCrashLogger.clearLogs()
        _crashLogs.value = emptyList()
        _recurringCrashGroups.value = emptyList()
    }

    private var lastPersistTimestamp = 0L
    private var transitionGeneration = 0L

    init {
        refreshCrashLogs()

        viewModelScope.launch(exceptionHandler) {
            com.anuj.obsidian.ObsidianCrashLogger.logFlow.collect { logs ->
                _crashLogs.value = logs
                _recurringCrashGroups.value = com.anuj.obsidian.ObsidianCrashLogger.getRecurringCrashGroups()
            }
        }

        playerManager?.withPlayer { addListener(playerListener) }

        viewModelScope.launch(Dispatchers.Main + exceptionHandler) {
            playerManager?.playbackStateFlow?.collect { authState ->
                val pShuffleMode = authState.shuffleTraversal.mode
                val isShuffleActive = pShuffleMode != com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_OFF
                val qReturnMode = playerManager.getQueueReturnMode()
                
                _playbackSequence.value = authState.explicitQueue.map { it.track }

                _playerState.value = _playerState.value.copy(
                    currentTrack = authState.currentTrack,
                    previousTrack = authState.previousTrack,
                    nextTrack = authState.nextTrack,
                queue = authState.explicitQueue.map { it.track },
                queueEntries = authState.explicitQueue,
                upcomingTracks = playerManager.getUpcomingTracks(),
                upcomingEntries = playerManager.getUpcomingQueueEntries(),
                    durationSeconds = authState.currentTrack?.durationSeconds ?: _playerState.value.durationSeconds,
                    isPlaying = authState.isPlaying,
                    loadingType = authState.loadingType,
                    isShuffle = isShuffleActive,
                    shuffleMode = pShuffleMode,
                    queueReturnMode = qReturnMode,
                    queuePosition = playerManager.getQueuePositionInfo()
                )
            }
        }

        viewModelScope.launch(exceptionHandler) {
            tracks.collect { tracksList ->
                tracksList.forEach { track ->
                    playerManager?.updateTrackData(track)
                }
                if (tracksList.isNotEmpty()) {
                    playerManager?.setAllLibraryTracks(tracksList)
                    if (activeContextList.isEmpty()) {
                        activeContextList = tracksList
                    }
                    playerManager?.updateLibraryContext(tracksList)
                }
            }
        }

        playerManager?.setCallbacks(
            onProgress = { positionSec, durSec ->
                _currentPositionSeconds.value = positionSec
                val current = _playerState.value
                if (durSec > 0 && durSec != current.durationSeconds) {
                    _playerState.value = current.copy(durationSeconds = durSec)
                }
                val now = System.currentTimeMillis()
                if (now - lastPersistTimestamp > 4000L && current.currentTrack != null) {
                    lastPersistTimestamp = now
                    val trackToSave = current.currentTrack
                    viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
                        try {
                            repository.saveLastPlaybackState(trackToSave, positionSec)
                        } catch (e: Throwable) {
                            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ViewModel.onProgress.saveLastPlaybackState")
                        }
                    }
                }
            },
            onCompletion = {
                viewModelScope.launch(Dispatchers.Main + exceptionHandler) {
                    handleTrackCompletion()
                }
            },
            onError = {
                viewModelScope.launch(Dispatchers.Main + exceptionHandler) {
                    _playerState.value = _playerState.value.copy(isPlaying = false, loadingType = PlaybackLoadingType.NONE)
                }
            },
            onTrackTransitioned = { newTrack ->
                viewModelScope.launch(Dispatchers.Main + exceptionHandler) {
                    if (newTrack != null) {
                        val currentPos = _currentPositionSeconds.value
                        val isSameTrack = newTrack.id == _playerState.value.currentTrack?.id
                        val posToPersist = if (isSameTrack && currentPos > 0f) currentPos else 0f
                        _playerState.value = _playerState.value.copy(
                            currentTrack = newTrack,
                            isPlaying = playerManager?.isPlaying() == true,
                            loadingType = if (playerManager?.getExoPlayerPlaybackState() == androidx.media3.common.Player.STATE_READY) PlaybackLoadingType.NONE else _playerState.value.loadingType,
                            durationSeconds = newTrack.durationSeconds
                        )
                        try {
                            withContext(Dispatchers.IO) {
                                repository.saveLastPlaybackState(newTrack, posToPersist)
                            }
                        } catch (e: Throwable) {
                            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ViewModel.onTrackTransitioned.saveLastPlaybackState")
                        }
                        syncPlayerStateFromMedia3()
                        if (newTrack.lyrics.isBlank() || newTrack.lyrics.contains("Lossless FLAC Engine") || newTrack.lyrics.contains("Direct Decoding") || newTrack.lyrics.contains("Streaming Master") || !newTrack.lyrics.contains("[00:")) {
                            fetchLyricsForCurrentTrack()
                        }
                    }
                }
            },
            onLoadingState = { loadingType ->
                viewModelScope.launch(Dispatchers.Main + exceptionHandler) {
                    _playerState.value = _playerState.value.copy(loadingType = loadingType)
                }
            },
            onQueueChanged = { updatedExplicitQueue ->
                viewModelScope.launch(Dispatchers.Main + exceptionHandler) {
                    _playerState.value = _playerState.value.copy(queue = updatedExplicitQueue)
                    syncPlayerStateFromMedia3()
                    try {
                        withContext(Dispatchers.IO) {
                            persistPlaybackContext()
                        }
                    } catch (e: Throwable) {
                        com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ViewModel.onQueueChanged.persistPlaybackContext")
                    }
                }
            },
            onTrackFormatUpdated = { updatedTrack ->
                viewModelScope.launch(Dispatchers.Main + exceptionHandler) {
                    val current = _playerState.value
                    if (current.currentTrack?.id == updatedTrack.id) {
                        _playerState.value = current.copy(
                            currentTrack = current.currentTrack?.copy(
                                sampleRate = updatedTrack.sampleRate,
                                bitrateKbps = updatedTrack.bitrateKbps,
                                bitDepth = updatedTrack.bitDepth,
                                format = updatedTrack.format
                            )
                        )
                    }
                }
            }
        )

        // Observe Settings for gapless, crossfade, offline mode, headset, and advanced shuffle/queue modes
        viewModelScope.launch(exceptionHandler) {
            settings.collect { s ->
                playerManager?.setGaplessConfig(s.gaplessPlayback, s.crossfadeDurationSec)
                playerManager?.setVolumeNormalization(s.volumeNormalizationEnabled)
                playerManager?.setOfflineMode(s.offlineMode)
                playerManager?.setHeadsetConfig(s.headsetAutoresume, s.headsetFadeOutOnDisconnect, s.headsetFadeOutDurationSec)
                
                try {
                    val pShuffle = AdvancedShuffleMode.valueOf(s.advancedShuffleMode)
                    playerManager?.setAdvancedShuffleMode(pShuffle)
                } catch (e: Exception) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.settingsShuffle - attempt")
                }
                
                try {
                    val qReturn = QueueReturnMode.valueOf(s.queueReturnMode)
                    playerManager?.setQueueReturnMode(qReturn)
                } catch (e: Exception) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.settingsQueueReturn - attempt")
                }
            }
        }

        // Continuously update playerManager with latest library tracks for category/album transitions
        viewModelScope.launch(exceptionHandler) {
            repository.allTracks.collect { tracks ->
                playerManager?.setAllLibraryTracks(tracks)
            }
        }

        // Verify and sanitize library metadata to ensure genuine audio specs
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            repository.sanitizeLibraryMetadata()
        }
        // Robust Player State Recovery: Load previously playing track, active queue, and exact state
        viewModelScope.launch(exceptionHandler) {
            try {
                val fullState = repository.restoreFullPlaybackState()
                var savedTrack = fullState.track
                val savedQueueIds = fullState.queueTrackIds
                val allLocalTracks = repository.getAllTracksList()
                val trackMap = allLocalTracks.associateBy { it.id }

                // Match saved track in current library if available
                if (savedTrack != null && allLocalTracks.isNotEmpty()) {
                    val matched = trackMap[savedTrack.id]
                        ?: allLocalTracks.firstOrNull { it.localFilePath.isNotBlank() && it.localFilePath == savedTrack.localFilePath }
                        ?: allLocalTracks.firstOrNull { it.title.equals(savedTrack.title, ignoreCase = true) && it.artist.equals(savedTrack.artist, ignoreCase = true) }
                    if (matched != null) {
                        savedTrack = matched
                    }
                }

                // If no saved track (first app run), use the first track from library
                if (savedTrack == null && allLocalTracks.isNotEmpty()) {
                    savedTrack = allLocalTracks.firstOrNull()
                }

                val maxDuration = (savedTrack?.durationSeconds?.toFloat() ?: 0f).coerceAtLeast(1f)
                val savedPos = fullState.positionSec.coerceIn(0f, maxDuration)

                if (savedTrack != null) {
                    val restoredAutoplayMode = try {
                        AutoplayMode.valueOf(fullState.autoplayMode)
                    } catch (e: Exception) {
                        AutoplayMode.AUTOPLAY_LIBRARY
                    }
                    val shouldAutoPlay = if (repository.settings.value.autoplayOnLaunch) fullState.isPlaying else false

                    _playerState.value = _playerState.value.copy(
                        currentTrack = savedTrack,
                        durationSeconds = savedTrack.durationSeconds,
                        isShuffle = fullState.isShuffle,
                        autoplayMode = restoredAutoplayMode,
                        speed = fullState.speed,
                        isPlaying = shouldAutoPlay
                    )
                    _currentPositionSeconds.value = savedPos

                    playerManager?.setAllLibraryTracks(allLocalTracks)
                    playerManager?.setAutoplayMode(restoredAutoplayMode)

                    // Validate and restore explicit user queue tracks preserving user order
                    if (savedQueueIds.isNotEmpty()) {
                        val validQueueTracks = savedQueueIds.mapNotNull { trackMap[it] }
                        if (validQueueTracks.isEmpty()) {
                            Log.w("ObsidianViewModel", "Saved queue IDs contain no valid tracks, clearing queue")
                            playerManager?.clearQueue()
                        } else {
                            playerManager?.restoreExplicitQueue(validQueueTracks)
                        }
                    }

                    val contextTracks = if (allLocalTracks.isNotEmpty()) allLocalTracks else listOf(savedTrack)
                    activeContextList = contextTracks
                    val index = contextTracks.indexOfFirst { it.id == savedTrack.id }.coerceAtLeast(0)
                    val repeatMode = when (restoredAutoplayMode) {
                        AutoplayMode.REPEAT_ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
                        AutoplayMode.REPEAT_ALL, AutoplayMode.AUTOPLAY_LIBRARY -> androidx.media3.common.Player.REPEAT_MODE_ALL
                        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                    }
                    
                    playerManager?.playPlaylist(
                        tracks = contextTracks,
                        startIndex = index,
                        startPositionSec = savedPos,
                        isShuffle = fullState.isShuffle,
                        repeatMode = repeatMode,
                        autoPlay = shouldAutoPlay
                    )
                    if (fullState.isShuffle && fullState.shuffledTrackIds.isNotEmpty()) {
                        val restoredShuffledTracks = fullState.shuffledTrackIds.mapNotNull { trackMap[it] }
                        if (restoredShuffledTracks.isNotEmpty()) {
                            val safeShuffledIndex = fullState.shuffledIndex.coerceIn(0, restoredShuffledTracks.size - 1)
                            playerManager?.restoreShuffledState(restoredShuffledTracks, safeShuffledIndex)
                        }
                    }
                    playerManager?.setPlaybackSpeed(fullState.speed)
                    
                    // Immediately synchronize timeline state and populate previous/next tracks
                    syncPlayerStateFromMedia3()
                } else {
                    if (allLocalTracks.isNotEmpty()) {
                        val firstTrack = allLocalTracks.firstOrNull() ?: return@launch
                        activeContextList = allLocalTracks
                        _playerState.value = _playerState.value.copy(
                            currentTrack = firstTrack,
                            durationSeconds = firstTrack.durationSeconds,
                            autoplayMode = AutoplayMode.AUTOPLAY_LIBRARY
                        )
                        playerManager?.playPlaylist(
                            tracks = allLocalTracks,
                            startIndex = 0,
                            startPositionSec = 0f,
                            isShuffle = false,
                            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL,
                            autoPlay = false
                        )
                        syncPlayerStateFromMedia3()
                    } else {
                        repository.allTracks.collect { trackList ->
                            if (_playerState.value.currentTrack == null && trackList.isNotEmpty()) {
                                val firstTrack = trackList.firstOrNull() ?: return@collect
                                activeContextList = trackList
                                _playerState.value = _playerState.value.copy(
                                    currentTrack = firstTrack,
                                    durationSeconds = firstTrack.durationSeconds,
                                    autoplayMode = AutoplayMode.AUTOPLAY_LIBRARY
                                )
                                playerManager?.playPlaylist(
                                    tracks = trackList,
                                    startIndex = 0,
                                    startPositionSec = 0f,
                                    isShuffle = false,
                                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL,
                                    autoPlay = false
                                )
                                syncPlayerStateFromMedia3()
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.restorePlaybackState")
                Log.e("ObsidianViewModel", "Playback state restoration crashed, starting fresh: ${e.message}", e)
            }
        }
        // Auto-update all extensions including Spotiflac Lossless Master Engine on startup and periodically in background
        startBackgroundExtensionRegistrySync()

        // Start Auto-Cycling Visualizer Loop
        startVisualizerAutoCycleLoop()

        // Start Inactivity Timeout Graceful Shutdown Watcher
        startInactivityTimeoutWatcher()

    }
    private var visualizerCycleJob: kotlinx.coroutines.Job? = null
    private fun startVisualizerAutoCycleLoop() {
        visualizerCycleJob?.cancel()
        visualizerCycleJob = viewModelScope.launch(exceptionHandler) {
            while (true) {
                val s = settings.value
                val mode = s.visualizerCycleMode
                if (mode != "off" && _playerState.value.isPlaying) {
                    val delayMs = when (mode) {
                        "seq_10", "shuf_10" -> 10_000L
                        "seq_20", "shuf_20" -> 20_000L
                        "seq_30", "shuf_30", "energy_match" -> 30_000L
                        "seq_60" -> 60_000L
                        "random_wild" -> kotlin.random.Random.nextLong(7000L, 22000L)
                        else -> 20_000L
                    }
                    kotlinx.coroutines.delay(delayMs)
                    
                    if (_playerState.value.isPlaying && settings.value.visualizerCycleMode == mode) {
                        val allPresets = com.anuj.obsidian.ui.visualizer.VisualizerLibrary.presets
                        if (allPresets.isNotEmpty()) {
                            val nextPreset = if (mode.startsWith("seq")) {
                                val curId = settings.value.lastVisualizerPresetId
                                val nextIdx = (allPresets.indexOfFirst { it.id == curId } + 1) % allPresets.size
                                allPresets[nextIdx.coerceAtLeast(0)]
                            } else if (mode == "energy_match") {
                                val track = _playerState.value.currentTrack
                                val isHighEnergy = track?.genre?.let {
                                    it.contains("Electronic", true) || it.contains("Rock", true) || 
                                    it.contains("Dance", true) || it.contains("Metal", true) || 
                                    it.contains("Pop", true)
                                } ?: (kotlin.random.Random.nextFloat() > 0.5f)
                                com.anuj.obsidian.ui.visualizer.VisualizerLibrary.generatePresetForEnergy(isHighEnergy)
                            } else {
                                allPresets.random()
                            }
                            repository.persistVisualizerSettings(
                                nextPreset.id,
                                settings.value.backgroundVisualizerEnabled,
                                settings.value.visualizerAlpha
                            )
                        }
                    }
                } else {
                    kotlinx.coroutines.delay(3000L)
                }
            }
        }
    }
    private val lastActiveTimestamp = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
    private var inactivityTimeoutJob: kotlinx.coroutines.Job? = null

    fun recordUserActivity() {
        lastActiveTimestamp.set(System.currentTimeMillis())

    }
    private fun startInactivityTimeoutWatcher() {
        inactivityTimeoutJob?.cancel()
        inactivityTimeoutJob = viewModelScope.launch(exceptionHandler) {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                val isCurrentlyPlaying = _playerState.value.isPlaying
                if (isCurrentlyPlaying) {
                    lastActiveTimestamp.set(System.currentTimeMillis())
                } else {
                    val timeoutMinutes = settings.value.inactivityTimeoutMinutes
                    if (timeoutMinutes > 0) {
                        val elapsedMillis = System.currentTimeMillis() - lastActiveTimestamp.get()
                        val timeoutMillis = timeoutMinutes * 60 * 1000L
                        if (elapsedMillis >= timeoutMillis) {
                            Log.d("ObsidianObsidianVM", "Inactivity timeout reached (${timeoutMinutes}m). Gracefully suspending playback resources.")
                            playerManager?.pause()
                            _playerState.value = _playerState.value.copy(
                                isPlaying = false,
                                loadingType = PlaybackLoadingType.NONE
                            )
                            lastActiveTimestamp.set(System.currentTimeMillis()) // Reset to avoid continuous triggers
                        }
                    }
                }
            }
        }
    }
    fun setInactivityTimeoutMinutes(minutes: Int) {
        repository.persistInactivityTimeout(minutes)
    }
    fun selectThemePreset(presetId: String) {
        val s = settings.value
        repository.persistThemeSettings(
            themePreset = presetId,
            customPrim = s.customPrimaryColor,
            customSec = s.customSecondaryColor,
            customBg = s.customBackgroundColor,
            perfMode = s.visualizerPerformanceMode
        )

    }
    fun setCustomColors(primary: Long, secondary: Long, background: Long) {
        val s = settings.value
        repository.persistThemeSettings(
            themePreset = "custom",
            customPrim = primary,
            customSec = secondary,
            customBg = background,
            perfMode = s.visualizerPerformanceMode
        )

    }
    fun setVisualizerPerformanceMode(mode: String) {
        val s = settings.value
        repository.persistThemeSettings(
            themePreset = s.themePreset,
            customPrim = s.customPrimaryColor,
            customSec = s.customSecondaryColor,
            customBg = s.customBackgroundColor,
            perfMode = mode
        )

    }
    fun fetchLyricsForCurrentTrack(forceRefresh: Boolean = false) {
        val current = _playerState.value.currentTrack ?: return
        fetchLyricsJob?.cancel()
        fetchLyricsJob = viewModelScope.launch(exceptionHandler) {
            _isFetchingLyrics.value = true
            try {
                val lyrics = repository.fetchAndSaveLyricsForTrack(current, forceRefresh)
                if (lyrics.isNotBlank() && _playerState.value.currentTrack?.id == current.id) {
                    val updated = current.copy(lyrics = lyrics)
                    _playerState.value = _playerState.value.copy(currentTrack = updated)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.fetchLyricsForCurrentTrack - attempt")
                Log.d("ObsidianViewModel", "Lyrics fetch notice: ${e.message}")
            } finally {
                _isFetchingLyrics.value = false
            }
        }
    }

    fun searchCustomLyricsForCurrentTrack(customTitle: String, customArtist: String) {
        val current = _playerState.value.currentTrack ?: return
        fetchLyricsJob?.cancel()
        fetchLyricsJob = viewModelScope.launch(exceptionHandler) {
            _isFetchingLyrics.value = true
            try {
                val tempTrack = current.copy(
                    title = customTitle.trim().ifBlank { current.title },
                    artist = customArtist.trim().ifBlank { current.artist }
                )
                val lyrics = repository.fetchAndSaveLyricsForTrack(tempTrack, forceRefresh = true)
                if (lyrics.isNotBlank() && _playerState.value.currentTrack?.id == current.id) {
                    val updated = current.copy(lyrics = lyrics)
                    _playerState.value = _playerState.value.copy(currentTrack = updated)
                    if (current.id > 0) {
                        repository.updateTrackLyrics(current.id, lyrics)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.searchCustomLyricsForCurrentTrack")
            } finally {
                _isFetchingLyrics.value = false
            }
        }
    }
    fun setTab(tab: AppTab) {
        _currentTab.value = tab

    }
    fun onQueryChange(query: String) {
        _urlOrSearchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()

        }
    }
    fun executeSearch() {
        val q = _urlOrSearchQuery.value.trim()
        if (q.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        val isUrl = q.startsWith("http://") || q.startsWith("https://")
        viewModelScope.launch(exceptionHandler) {
            repository.addSearchHistory(q)
            _isSearching.value = true
            try {
                val results = repository.parseUrlOrSearch(q)
                _searchResults.value = results
                if (isUrl && results.isNotEmpty()) {
                    // Do not open player directly because that prevents triggering download.
                    // Instead, trigger downloads for the resolved tracks from the link!
                    results.forEach { item ->
                        downloadSingle(item, playIfExisting = false)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.executeSearch - attempt")
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }
    fun removeSearchHistory(query: String) {
        viewModelScope.launch(exceptionHandler) {
            repository.removeSearchHistory(query)

        }
    }
    fun clearSearchHistory() {
        viewModelScope.launch(exceptionHandler) {
            repository.clearSearchHistory()

        }
    }
    fun clearSearch() {
        _urlOrSearchQuery.value = ""
        _searchResults.value = emptyList()

    }
    fun importAudioFile(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch(exceptionHandler) {
            repository.importAudioFile(uri, context)

        }
    }
    fun findDownloadedTrack(item: SearchResultItem): DownloadedTrack? {
        val currentTracks = tracks.value

        // 1. Exact provider + provider track match
        if (item.provider.isNotBlank() && item.id.isNotBlank()) {
            val byProvider = currentTracks.filter { 
                it.providerSource.equals(item.provider, ignoreCase = true) &&
                it.id.toString() == item.id
            }
            if (byProvider.size == 1) return byProvider.first()
        }

        // 2. Exact canonical source identity / location match
        if (item.streamUrl.isNotBlank()) {
            val byUrl = currentTracks.filter { it.localFilePath == item.streamUrl }
            if (byUrl.size == 1) return byUrl.first()
        }

        // 3. ISRC as correlation signal (only if unique)
        val safeIsrc = item.isrc
        if (safeIsrc.isNotBlank()) {
            val byIsrc = currentTracks.filter { it.isrc.equals(safeIsrc, ignoreCase = true) }
            if (byIsrc.size == 1) return byIsrc.first()
        }

        // 4. Match by Title + Artist if local file exists on disk
        val byTitleArtist = currentTracks.firstOrNull {
            it.title.trim().equals(item.title.trim(), ignoreCase = true) &&
            it.artist.trim().equals(item.artist.trim(), ignoreCase = true) &&
            it.localFilePath.isNotBlank() &&
            java.io.File(it.localFilePath).exists()
        }
        if (byTitleArtist != null) {
            return byTitleArtist
        }

        return null
    }

    fun playSearchResult(item: SearchResultItem, openFullPlayer: Boolean = false) {
        val existingTrack = findDownloadedTrack(item)
        if (existingTrack != null) {
            playTrack(existingTrack)
            if (openFullPlayer) {
                showFullPlayer(true)
            }
            return
        }

        val isLossless = item.streamUrl.endsWith(".flac", ignoreCase = true)
        val tempTrack = DownloadedTrack(
            id = DownloadedTrack.generatePseudoId(item.title, item.artist, item.streamUrl.ifBlank { item.id }),
            title = item.title,
            artist = item.artist,
            album = item.album,
            durationSeconds = item.durationSeconds,
            format = if (isLossless) "FLAC" else "MP3",
            bitDepth = if (isLossless) (item.bitDepth.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) } ?: "16-bit") else "",
            sampleRate = item.sampleRate.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) } ?: "44.1 kHz",
            bitrateKbps = if (isLossless) 956 else 320,
            albumArtUrl = item.coverUrl,
            localFilePath = item.streamUrl,
            fileSizeMb = 0f,
            lyrics = "",
            isrc = item.isrc,
            releaseDate = item.releaseYear,
            genre = "Audio",
            providerSource = item.provider.ifBlank { "Obsidian Stream" },
            isFavorite = false,
            dateAddedTimestamp = System.currentTimeMillis()
        )
        playTrack(tempTrack)
        if (openFullPlayer) {
            showFullPlayer(true)
        }

        // Automatically trigger background download into library if not present
        downloadSingle(item, playIfExisting = false)
    }

    fun downloadSingle(item: SearchResultItem, quality: String? = null, playIfExisting: Boolean = false) {
        // Check if track is already downloaded in the local library
        val existingTrack = findDownloadedTrack(item)
        if (existingTrack != null) {
            if (playIfExisting) {
                // Already downloaded, play the local downloaded version
                playTrack(existingTrack)
            }
            return
        }

        val selectedQ = quality ?: AvailableAudioQualities.find { it.id == settings.value.audioQualityId }?.title ?: "24-bit / 96kHz FLAC"
        val safeIsrc = item.isrc
        repository.startDownload(
            title = item.title,
            artist = item.artist,
            album = item.album,
            albumArtUrl = item.coverUrl,
            quality = selectedQ,
            isrc = safeIsrc,
            streamUrl = item.streamUrl,
            durationSeconds = item.durationSeconds,
            releaseDate = item.releaseYear,
            trackNumber = item.trackNumber,
            providerSource = item.provider,
            onComplete = { savedPath ->
                viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
                    val savedTrack = repository.getAllTracksList().firstOrNull { it.localFilePath == savedPath }
                    if (savedTrack != null) {
                        val current = _playerState.value.currentTrack
                        if (current != null && (current.id == 0L || current.localFilePath.isBlank()) && current.title.equals(item.title, true)) {
                            withContext(Dispatchers.Main) {
                                _playerState.value = _playerState.value.copy(currentTrack = savedTrack)
                                playerManager?.updateCurrentTrack(savedTrack)
                            }
                        }
                    }
                }
            }
        )
    }
    fun downloadTrendingChart(chart: TrendingChart) {
        val quality = AvailableAudioQualities.find { it.id == settings.value.audioQualityId }?.title ?: "24-bit / 96kHz FLAC"
        chart.tracks.forEach { item ->
            val safeIsrc = item.isrc
            repository.startDownload(
                title = item.title,
                artist = item.artist,
                album = item.album,
                albumArtUrl = item.coverUrl,
                quality = quality,
                isrc = safeIsrc,
                streamUrl = item.streamUrl,
                durationSeconds = item.durationSeconds,
                releaseDate = item.releaseYear,
                trackNumber = item.trackNumber,
                providerSource = item.provider
            )

        }
    }
    fun downloadAllSearchResults() {
        val currentResults = _searchResults.value
        val quality = AvailableAudioQualities.find { it.id == settings.value.audioQualityId }?.title ?: "24-bit / 96kHz FLAC"
        currentResults.forEach { item ->
            val safeIsrc = item.isrc
            repository.startDownload(
                title = item.title,
                artist = item.artist,
                album = item.album,
                albumArtUrl = item.coverUrl,
                quality = quality,
                isrc = safeIsrc,
                streamUrl = item.streamUrl,
                durationSeconds = item.durationSeconds,
                releaseDate = item.releaseYear,
                trackNumber = item.trackNumber,
                providerSource = item.provider
            )

        }
    }
    fun downloadAlbum(items: List<SearchResultItem>) {
        val quality = AvailableAudioQualities.find { it.id == settings.value.audioQualityId }?.title ?: "24-bit / 96kHz FLAC"
        items.forEach { item ->
            val safeIsrc = item.isrc
            repository.startDownload(
                title = item.title,
                artist = item.artist,
                album = item.album,
                albumArtUrl = item.coverUrl,
                quality = quality,
                isrc = safeIsrc,
                streamUrl = item.streamUrl,
                durationSeconds = item.durationSeconds,
                releaseDate = item.releaseYear,
                trackNumber = item.trackNumber,
                providerSource = item.provider
            )

        }
    }
    fun queueAlbum(items: List<SearchResultItem>) {
        items.forEach { item ->
            addSearchResultToQueue(item)

        }
    }
    // Playback & Queue Controls

    fun computeUpcomingTrack(): DownloadedTrack? {
        return playerManager?.getNextTrackPreview()
    }

    fun playNextInQueue(track: DownloadedTrack) {
        addTracksToNextQueue(listOf(track))
    }

    fun playNextInQueue(item: SearchResultItem) {
        val existing = findDownloadedTrack(item)
        if (existing != null) {
            playNextInQueue(existing)
            return
        }

        val convertedTrack = DownloadedTrack(
            id = DownloadedTrack.generatePseudoId(item.title, item.artist, item.streamUrl.ifBlank { item.id }),
            title = item.title,
            artist = item.artist,
            album = item.album,
            durationSeconds = item.durationSeconds,
            format = if (item.streamUrl.endsWith(".flac", ignoreCase = true)) "FLAC" else "MP3",
            bitDepth = item.bitDepth.ifBlank { "UNKNOWN" },
            sampleRate = item.sampleRate.ifBlank { "UNKNOWN" },
            bitrateKbps = 0,
            albumArtUrl = item.coverUrl,
            localFilePath = item.streamUrl,
            fileSizeMb = 0f,
            lyrics = "",
            isrc = item.isrc,
            releaseDate = item.releaseYear,
            genre = "UNKNOWN",
            providerSource = item.provider.ifBlank { "UNKNOWN" },
            isFavorite = false,
            dateAddedTimestamp = System.currentTimeMillis()
        )
        playNextInQueue(convertedTrack)
    }

    fun addTracksToNextQueue(tracks: List<DownloadedTrack>) {
        if (tracks.isEmpty()) return
        playerManager?.addTracksToNextQueue(tracks)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun addTracksToQueue(tracks: List<DownloadedTrack>) {
        if (tracks.isEmpty()) return
        playerManager?.addToQueue(tracks)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playerManager?.moveQueueItem(fromIndex, toIndex)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun reorderUpcomingTrack(fromIndex: Int, toIndex: Int) {
        playerManager?.reorderUpcomingTrack(fromIndex, toIndex)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun clearUpcomingTracks() {
        playerManager?.clearUpcomingTracks()
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun clearAllQueueAndUpcoming() {
        playerManager?.clearAllQueueAndUpcoming()
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun reshuffle() {
        playerManager?.reshuffle()
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun startArtistRadio(artist: String, trackTitle: String = "") {
        if (artist.isBlank()) return
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                val query = if (trackTitle.isNotBlank()) "$artist $trackTitle" else "$artist top tracks"
                val radioResults = MusicApiService.searchTracks(query)
                if (radioResults.isNotEmpty()) {
                    val tracks = radioResults.map { item ->
                        DownloadedTrack(
                            id = DownloadedTrack.generatePseudoId(item.title, item.artist, item.streamUrl.ifBlank { item.id }),
                            title = item.title,
                            artist = item.artist,
                            album = item.album,
                            durationSeconds = item.durationSeconds,
                            format = if (item.streamUrl.endsWith(".flac", ignoreCase = true)) "FLAC" else "MP3",
                            bitDepth = item.bitDepth.ifBlank { "16-bit" },
                            sampleRate = item.sampleRate.ifBlank { "44.1 kHz" },
                            bitrateKbps = 0,
                            albumArtUrl = item.coverUrl,
                            localFilePath = item.streamUrl,
                            fileSizeMb = 0f,
                            lyrics = "",
                            isrc = item.isrc,
                            releaseDate = item.releaseYear,
                            genre = "Artist Radio",
                            providerSource = item.provider.ifBlank { "Radio Stream" },
                            isFavorite = false,
                            dateAddedTimestamp = System.currentTimeMillis()
                        )
                    }
                    withContext(Dispatchers.Main) {
                        playTrackFromList(
                            track = tracks.first(),
                            contextList = tracks,
                            forceShuffle = false,
                            sourceType = PlaybackSourceType.SEARCH,
                            sourceId = "radio_${artist.hashCode()}",
                            sourceTitle = "Artist Radio: $artist"
                        )
                    }
                }
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.startArtistRadio - attempt")
            }
        }
    }

    fun startBatchAutoResolveMetadata(
        tracksToResolve: List<DownloadedTrack>,
        context: android.content.Context,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onComplete: (Int, Int) -> Unit = { _, _ -> }
    ) {
        if (tracksToResolve.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            var successCount = 0
            var failCount = 0
            val total = tracksToResolve.size
            for ((index, track) in tracksToResolve.withIndex()) {
                withContext(Dispatchers.Main) { onProgress(index + 1, total) }
                try {
                    val result = com.anuj.obsidian.data.local.ModernAudioTagger.embedAllTagsDetailed(
                        context = context,
                        sourceFilePath = track.localFilePath,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        year = track.releaseDate,
                        genre = track.genre,
                        trackNumber = 0,
                        lyrics = track.lyrics,
                        artworkUrl = track.albumArtUrl,
                        isrc = track.isrc
                    )
                    if (result.success) {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.startBatchAutoResolveMetadata")
                }
            }
            withContext(Dispatchers.Main) {
                onComplete(successCount, failCount)
            }
        }
    }

    fun addToQueue(track: DownloadedTrack) {
        addTracksToQueue(listOf(track))
    }

    fun addSearchResultToQueue(item: SearchResultItem) {
        val existing = findDownloadedTrack(item)
        if (existing != null) {
            addToQueue(existing)
            return
        }

        val convertedTrack = DownloadedTrack(
            id = DownloadedTrack.generatePseudoId(item.title, item.artist, item.streamUrl.ifBlank { item.id }),
            title = item.title,
            artist = item.artist,
            album = item.album,
            durationSeconds = item.durationSeconds,
            format = if (item.streamUrl.endsWith(".flac", ignoreCase = true)) "FLAC" else "MP3",
            bitDepth = item.bitDepth.ifBlank { "UNKNOWN" },
            sampleRate = item.sampleRate.ifBlank { "UNKNOWN" },
            bitrateKbps = 0,
            albumArtUrl = item.coverUrl,
            localFilePath = item.streamUrl,
            fileSizeMb = 0f,
            lyrics = "",
            isrc = item.isrc,
            releaseDate = item.releaseYear,
            genre = "UNKNOWN",
            providerSource = item.provider.ifBlank { "UNKNOWN" },
            isFavorite = false,
            dateAddedTimestamp = System.currentTimeMillis()
        )
        addToQueue(convertedTrack)
    }

    fun stopPlayback() {
        _playerState.value = _playerState.value.copy(isPlaying = false, currentTrack = null, loadingType = PlaybackLoadingType.NONE, queue = emptyList())
        _currentPositionSeconds.value = 0f
        playerManager?.clearQueue()
        playerManager?.stopPlayback()
    }

    fun removeFromQueue(index: Int) {
        playerManager?.removeTrackAtIndex(index)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun insertTrackInQueueAtIndex(track: DownloadedTrack, index: Int) {
        playerManager?.insertTrackAtIndex(track, index)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun removeTrackFromQueueAtIndex(index: Int): DownloadedTrack? {
        val removed = playerManager?.removeTrackAtIndex(index)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
        return removed
    }

    fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        playerManager?.moveTrackInQueue(fromIndex, toIndex)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun swapQueueTracks(index1: Int, index2: Int) {
        playerManager?.swapQueueTracks(index1, index2)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun clearQueueAfterCurrentTrack() {
        playerManager?.clearQueueAfterCurrentTrack()
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun setShuffleMode(mode: com.anuj.obsidian.ui.player.ShuffleMode) {
        playerManager?.setShuffleMode(mode)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun getPlaybackHistory(): List<com.anuj.obsidian.ui.player.PlaybackHistory> {
        return playerManager?.getPlaybackHistory() ?: emptyList()
    }

    fun getRecentlyPlayedTracks(): List<Long> {
        return playerManager?.getRecentlyPlayedTracks() ?: emptyList()
    }

    fun clearQueue() {
        playerManager?.clearQueue()
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }
    private fun updatePlaybackNotification() {
        val cur = _playerState.value
        if (cur.currentTrack != null) {
            try {
                
            } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.updatePlaybackNotification - attempt")
            }
        }
    }

    fun setAutoplayMode(mode: AutoplayMode) {
        _playerState.value = _playerState.value.copy(autoplayMode = mode)
        repository.persistAutoplayMode(mode.name)
        playerManager?.setAutoplayMode(mode)
        
        // Sync repeat to ExoPlayer
        playerManager?.withPlayer {
            repeatMode = when (mode) {
                AutoplayMode.REPEAT_ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
                AutoplayMode.REPEAT_ALL, AutoplayMode.AUTOPLAY_LIBRARY -> androidx.media3.common.Player.REPEAT_MODE_ALL
                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
            }
        }
        
        updatePlaybackNotification()
        syncPlayerStateFromMedia3()
    }
    fun cycleAutoplayMode() {
        val nextMode = when (_playerState.value.autoplayMode) {
            AutoplayMode.REPEAT_ALL -> AutoplayMode.REPEAT_ONE
            AutoplayMode.REPEAT_ONE -> AutoplayMode.AUTOPLAY_LIBRARY
            else -> AutoplayMode.REPEAT_ALL
        }
        setAutoplayMode(nextMode)
    }
    fun saveVisualizerSettings(presetId: Int, bgEnabled: Boolean, alpha: Float) {
        val preset = com.anuj.obsidian.ui.visualizer.VisualizerLibrary.getPresetById(presetId)
        val parsed = com.anuj.obsidian.ui.visualizer.VisualizerLibrary.getParsedPreset(presetId)
        val path = parsed?.sourceFile?.absolutePath ?: ""
        val name = preset.name
        repository.persistVisualizerSettings(presetId, bgEnabled, alpha, presetName = name, presetPath = path)
    }
    private fun handleTrackCompletion() {
        if (_playerState.value.autoplayMode == AutoplayMode.AUTOPLAY_SIMILAR) {
            playerManager?.withPlayer {
                if (!hasNextMediaItem()) {
                    playNextSimilar()
                } else {
                    playerManager?.skipToNext()
                }
            }
        } else if (_playerState.value.autoplayMode == AutoplayMode.REPEAT_ONE) {
            playerManager?.withPlayer {
                seekTo(0L)
                play()
            }
        } else {
            // Continues playback from library / hidden shuffle cycle seamlessly
            playerManager?.skipToNext()
        }
    }
    private fun playNextSimilar() {
        val currentTrack = _playerState.value.currentTrack ?: return
        transitionGeneration++
        val gen = transitionGeneration
        viewModelScope.launch(exceptionHandler) {
            similarResolutionMutex.withLock {
                if (gen != transitionGeneration) return@withLock
                try {
                    if (settings.value.offlineMode) {
                        val localCandidates = tracks.value.filter { it.id != currentTrack.id }
                        val artistMatch = localCandidates.firstOrNull { it.artist == currentTrack.artist }
                        val genreMatch = localCandidates.firstOrNull { it.genre == currentTrack.genre }
                        val nextLocal = artistMatch ?: genreMatch ?: localCandidates.firstOrNull()
                        if (nextLocal != null) {
                            if (gen != transitionGeneration) return@withLock
                            playNextInQueue(nextLocal)
                        } else {
                            _playerState.value = _playerState.value.copy(isPlaying = false)
                        }
                    } else {
                        val similarResults = withContext(Dispatchers.IO) {
                            val query = if (currentTrack.genre.isNotBlank() && currentTrack.artist.isNotBlank()) {
                                "${currentTrack.artist} ${currentTrack.genre}"
                            } else if (currentTrack.genre.isNotBlank()) {
                                currentTrack.genre
                            } else {
                                currentTrack.artist
                            }
                            MusicApiService.searchTracks(query)
                        }
                        // Filter out current track to prevent repeating
                        val recentIds = playedSimilarTrackIds + currentTrack.id.toString()
                        val candidates = similarResults.filter { !recentIds.contains(it.id) }
                        
                        val nextCandidate = if (candidates.isNotEmpty()) {
                            // Pick a random track from the top 10 candidates
                            candidates.firstOrNull()?.also {
                                playedSimilarTrackIds.add(it.id)
                            }
                        } else {
                            similarResults.firstOrNull { it.id != currentTrack.id.toString() }?.also {
                                playedSimilarTrackIds.clear() // Reset if we run out of unique similar tracks
                                playedSimilarTrackIds.add(it.id)
                            }
                        }

                        if (nextCandidate != null) {
                            if (gen != transitionGeneration) return@withLock
                            playNextInQueue(nextCandidate)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.playNextSimilar - attempt")
                    Log.e("ObsidianVM", "Error in playNextSimilar resolution: ${e.message}", e)
                }
            }
        }
    }
    fun playTrackFromList(
        track: DownloadedTrack?,
        contextList: List<DownloadedTrack>,
        forceShuffle: Boolean = false,
        sourceType: PlaybackSourceType = PlaybackSourceType.CUSTOM,
        sourceId: String = "",
        sourceTitle: String = "",
        targetIndex: Int? = null
    ) {
        if (contextList.isEmpty()) return
        transitionGeneration++
        activeContextList = contextList
        
        val isShuffle = if (forceShuffle) true else _playerState.value.isShuffle
        val targetTrack = if (track != null) {
            track
        } else if (forceShuffle && contextList.isNotEmpty()) {
            contextList.random()
        } else {
            contextList.firstOrNull() ?: return
        }
        
        val index = if (targetIndex != null && targetIndex in contextList.indices) {
            targetIndex
        } else {
            val matchIndex = contextList.indexOfFirst { it === targetTrack }
            if (matchIndex >= 0) {
                matchIndex
            } else {
                val pathIndex = contextList.indexOfFirst { it.localFilePath.isNotBlank() && it.localFilePath == targetTrack.localFilePath }
                if (pathIndex >= 0) pathIndex else contextList.indexOfFirst { it.id == targetTrack.id }.coerceAtLeast(0)
            }
        }

        val repeatMode = when (_playerState.value.autoplayMode) {
            AutoplayMode.REPEAT_ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
            AutoplayMode.REPEAT_ALL, AutoplayMode.AUTOPLAY_LIBRARY -> androidx.media3.common.Player.REPEAT_MODE_ALL
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }

        val isLikelyLocal = targetTrack.localFilePath.isNotBlank() &&
            (targetTrack.localFilePath.startsWith("/") || targetTrack.localFilePath.startsWith("content://") || !targetTrack.localFilePath.startsWith("http"))

        _playerState.value = _playerState.value.copy(
            currentTrack = targetTrack,
            isPlaying = true,
            isShuffle = isShuffle,
            loadingType = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING,
            durationSeconds = targetTrack.durationSeconds
        )
        _currentPositionSeconds.value = 0f
        
        playerManager?.playPlaylist(
            tracks = contextList,
            startIndex = index,
            startPositionSec = 0f,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            sourceType = sourceType,
            sourceId = sourceId,
            sourceTitle = sourceTitle,
            forceReshuffle = forceShuffle
        )
        syncPlayerStateFromMedia3()
        persistPlaybackContext(0f)
    }

    fun playQueueIndex(queueIndex: Int) {
        fetchLyricsJob?.cancel()
        val targetTrack = _playerState.value.queueEntries.getOrNull(queueIndex)?.track
            ?: _playerState.value.queue.getOrNull(queueIndex)
        if (targetTrack != null) {
            val isLikelyLocal = targetTrack.localFilePath.isNotBlank() &&
                (targetTrack.localFilePath.startsWith("/") || targetTrack.localFilePath.startsWith("content://") || !targetTrack.localFilePath.startsWith("http"))
            _playerState.value = _playerState.value.copy(
                currentTrack = targetTrack,
                loadingType = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING
            )
        } else {
            _playerState.value = _playerState.value.copy(
                loadingType = PlaybackLoadingType.LOCAL_STORAGE_IO
            )
        }
        transitionGeneration++
        playerManager?.playQueueIndex(queueIndex)
        persistPlaybackContext(0f)
    }

    fun playTrack(track: DownloadedTrack, directStreamUrl: String = "") {
        fetchLyricsJob?.cancel()
        val isLikelyLocal = track.localFilePath.isNotBlank() &&
            (track.localFilePath.startsWith("/") || track.localFilePath.startsWith("content://") || !track.localFilePath.startsWith("http"))
        
        val fullLibrary = tracks.value
        val contextList = if (fullLibrary.any { it.id == track.id }) fullLibrary else listOf(track)
        activeContextList = contextList
        val index = contextList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        
        val repeatMode = when (_playerState.value.autoplayMode) {
            AutoplayMode.REPEAT_ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
            AutoplayMode.REPEAT_ALL, AutoplayMode.AUTOPLAY_LIBRARY -> androidx.media3.common.Player.REPEAT_MODE_ALL
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }

        _playerState.value = _playerState.value.copy(
            currentTrack = track,
            isPlaying = true,
            loadingType = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING,
            durationSeconds = track.durationSeconds
        )
        _currentPositionSeconds.value = 0f

        if (directStreamUrl.isNotBlank()) {
            playerManager?.registerDirectStreamUrl(track.id, directStreamUrl)
        }

        if (contextList.size > 1) {
            playerManager?.playPlaylist(
                tracks = contextList, 
                startIndex = index, 
                startPositionSec = 0f, 
                isShuffle = _playerState.value.isShuffle, 
                repeatMode = repeatMode
            )
        } else {
            playerManager?.playTrack(track, directStreamUrl = directStreamUrl)
        }
        
        syncPlayerStateFromMedia3()
        persistPlaybackContext(0f)

        viewModelScope.launch(Dispatchers.IO) {
            repository.recordTrackPlayed(track.id)
        }

        if (track.lyrics.isBlank() || track.lyrics.contains("Lossless FLAC Engine") || track.lyrics.contains("Direct Decoding") || track.lyrics.contains("Streaming Master") || !track.lyrics.contains("[00:")) {
            fetchLyricsForCurrentTrack()
        }
    }

    private fun syncPlayerStateFromMedia3() {
        viewModelScope.launch(Dispatchers.Main) {
            val activeTrack = playerManager?.getCurrentTrack() ?: _playerState.value.currentTrack
            val pShuffleMode = playerManager?.getAdvancedShuffleMode() ?: AdvancedShuffleMode.SHUFFLE_OFF
            val isShuffleActive = pShuffleMode != AdvancedShuffleMode.SHUFFLE_OFF
            val qReturnMode = playerManager?.getQueueReturnMode() ?: QueueReturnMode.RETURN_TO_PREVIOUS
            val contextPool = if (activeContextList.isNotEmpty()) activeContextList else (playerManager?.getPlaybackContext()?.tracks?.takeIf { it.isNotEmpty() } ?: tracks.value)
            val prevTrack = playerManager?.getPreviousTrackPreview() ?: if (activeTrack != null && contextPool.size > 1) {
                val idx = contextPool.indexOfFirst { it.id == activeTrack.id }
                if (idx > 0) contextPool.getOrNull(idx - 1) else contextPool.lastOrNull()
            } else null

            val nextTrackLocal = playerManager?.getNextTrackPreview() ?: if (activeTrack != null && contextPool.size > 1) {
                val idx = contextPool.indexOfFirst { it.id == activeTrack.id }
                if (idx >= 0 && idx < contextPool.size - 1) contextPool.getOrNull(idx + 1) else contextPool.firstOrNull()
            } else null
            val explicitQueue = playerManager?.getExplicitQueue() ?: emptyList()
            val upcomingTracksList = playerManager?.getUpcomingTracks() ?: emptyList()
            val queuePos = playerManager?.getQueuePositionInfo() ?: Pair(1, 1)

            _playbackSequence.value = explicitQueue

            _playerState.value = _playerState.value.copy(
                currentTrack = activeTrack,
                previousTrack = prevTrack,
                nextTrack = nextTrackLocal,
                queue = explicitQueue,
                queueEntries = playerManager?.getExplicitQueueEntries() ?: emptyList(),
                upcomingTracks = upcomingTracksList,
                upcomingEntries = playerManager?.getUpcomingQueueEntries() ?: emptyList(),
                durationSeconds = activeTrack?.durationSeconds ?: _playerState.value.durationSeconds,
                isPlaying = if (playerManager?.playWhenReady() == true) true else (playerManager?.isPlaying() ?: _playerState.value.isPlaying),
                loadingType = playerManager?.getCurrentLoadingType() ?: _playerState.value.loadingType,
                isShuffle = isShuffleActive,
                shuffleMode = pShuffleMode,
                queueReturnMode = qReturnMode,
                queuePosition = queuePos
            )
        }
    }

    fun persistPlaybackContext(positionOverride: Float? = null) {
        val current = _playerState.value
        val track = current.currentTrack ?: return
        val pos = positionOverride ?: _currentPositionSeconds.value
        val isPlaying = current.isPlaying
        val isShuffle = current.isShuffle
        val repeatMode = playerManager?.getRepeatMode() ?: androidx.media3.common.Player.REPEAT_MODE_OFF
        val speed = current.speed
        val autoplayModeStr = current.autoplayMode.name
        val explicitQueueIds = playerManager?.getExplicitQueue()?.map { it.id } ?: emptyList()
        val shuffledTracks = playerManager?.getShuffledList() ?: emptyList()
        val shuffledIds = shuffledTracks.map { it.id }
        val shuffledIdx = playerManager?.getShuffleTraversal()?.shufflePosition ?: 0

        repository.saveFullPlaybackState(
            track = track,
            positionSec = pos,
            isPlaying = isPlaying,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            speed = speed,
            autoplayMode = autoplayModeStr,
            queueTrackIds = explicitQueueIds,
            shuffledTrackIds = shuffledIds,
            shuffledIndex = shuffledIdx
        )
    }

    fun togglePlayPause() {
        val current = _playerState.value
        val track = current.currentTrack
        if (track == null) {
            val first = tracks.value.firstOrNull() ?: return
            playTrackFromList(first, tracks.value)
            return
        }
        if (playerManager?.isPlaying() == true) {
            playerManager?.pause()
            persistPlaybackContext(_currentPositionSeconds.value)
        } else {
            val activeTrack = playerManager?.getCurrentTrack()
            if (activeTrack != null && activeTrack.id == track.id) {
                playerManager?.play()
            } else {
                val contextList = activeContextList.ifEmpty { tracks.value.ifEmpty { listOf(track) } }
                val startPos = _currentPositionSeconds.value
                val index = contextList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                playerManager?.playPlaylist(
                    tracks = contextList,
                    startIndex = index,
                    startPositionSec = startPos,
                    isShuffle = current.isShuffle,
                    repeatMode = when (current.autoplayMode) {
                        AutoplayMode.REPEAT_ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
                        AutoplayMode.REPEAT_ALL, AutoplayMode.AUTOPLAY_LIBRARY -> androidx.media3.common.Player.REPEAT_MODE_ALL
                        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                    },
                    autoPlay = true
                )
            }
        }
    }

    fun seekTo(positionSeconds: Float, isScrubbing: Boolean = false) {
        val bounded = positionSeconds.coerceIn(0f, _playerState.value.durationSeconds.toFloat())
        _currentPositionSeconds.value = bounded
        playerManager?.seekTo(bounded, isScrubbing)
        if (!isScrubbing) {
            persistPlaybackContext(bounded)
        }
    }

    fun playNext(forceLoop: Boolean = false) {
        fetchLyricsJob?.cancel()
        val nextTrack = playerManager?.getNextTrackPreview()
        if (nextTrack != null) {
            val isLikelyLocal = nextTrack.localFilePath.isNotBlank() &&
                (nextTrack.localFilePath.startsWith("/") || nextTrack.localFilePath.startsWith("content://") || !nextTrack.localFilePath.startsWith("http"))
            _playerState.value = _playerState.value.copy(
                currentTrack = nextTrack,
                loadingType = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING
            )
        } else {
            _playerState.value = _playerState.value.copy(
                loadingType = PlaybackLoadingType.LOCAL_STORAGE_IO
            )
        }
        transitionGeneration++
        playerManager?.skipToNext()
    }

    fun playPrevious(forceSkip: Boolean = false) {
        fetchLyricsJob?.cancel()
        val prevTrack = playerManager?.getPreviousTrackPreview()
        if (prevTrack != null) {
            val isLikelyLocal = prevTrack.localFilePath.isNotBlank() &&
                (prevTrack.localFilePath.startsWith("/") || prevTrack.localFilePath.startsWith("content://") || !prevTrack.localFilePath.startsWith("http"))
            if (forceSkip) {
                _playerState.value = _playerState.value.copy(
                    currentTrack = prevTrack,
                    loadingType = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING
                )
            } else {
                _playerState.value = _playerState.value.copy(
                    loadingType = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING
                )
            }
        } else {
            _playerState.value = _playerState.value.copy(
                loadingType = PlaybackLoadingType.LOCAL_STORAGE_IO
            )
        }
        transitionGeneration++
        playerManager?.skipToPrevious(forceSkip)
    }

    fun skipToNextCategory() {
        _playerState.value = _playerState.value.copy(loadingType = PlaybackLoadingType.LOCAL_STORAGE_IO)
        transitionGeneration++
        playerManager?.skipToNextCategory()
    }

    fun skipToPreviousCategory() {
        _playerState.value = _playerState.value.copy(loadingType = PlaybackLoadingType.LOCAL_STORAGE_IO)
        transitionGeneration++
        playerManager?.skipToPreviousCategory()
    }

    fun toggleShuffle() {
        val currentMode = playerManager?.getAdvancedShuffleMode() ?: _playerState.value.shuffleMode
        setAdvancedShuffleMode(currentMode.next())
    }

    fun setAdvancedShuffleMode(mode: AdvancedShuffleMode) {
        playerManager?.setAdvancedShuffleMode(mode)
        repository.persistAdvancedShuffleMode(mode.name)
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun cycleAdvancedShuffleMode(): AdvancedShuffleMode {
        val currentMode = playerManager?.getAdvancedShuffleMode() ?: _playerState.value.shuffleMode
        val nextMode = currentMode.next()
        setAdvancedShuffleMode(nextMode)
        return nextMode
    }

    fun setQueueReturnMode(mode: QueueReturnMode) {
        playerManager?.setQueueReturnMode(mode)
        repository.persistQueueReturnMode(mode.name)
        syncPlayerStateFromMedia3()
    }

    fun shuffleExplicitQueue() {
        playerManager?.shuffleExplicitQueue()
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun playUpcomingTrack(track: DownloadedTrack) {
        val isLikelyLocal = track.localFilePath.isNotBlank() &&
            (track.localFilePath.startsWith("/") || track.localFilePath.startsWith("content://") || !track.localFilePath.startsWith("http"))
        _playerState.value = _playerState.value.copy(
            currentTrack = track,
            loadingType = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING
        )
        transitionGeneration++
        playerManager?.playUpcomingTrack(track)
        persistPlaybackContext()
    }

    fun reshuffleUpcoming() {
        playerManager?.reshuffleUpcoming()
        syncPlayerStateFromMedia3()
        persistPlaybackContext()
    }

    fun setVisualizerColorMode(mode: String) {
        repository.persistVisualizerColorMode(mode)
    }

    fun setVisualizerMasterEnabled(enabled: Boolean) {
        repository.persistVisualizerMasterEnabled(enabled)
    }

    fun setVisualizerShowEverywhere(showEverywhere: Boolean) {
        repository.persistVisualizerShowEverywhere(showEverywhere)
    }

    fun setBackgroundVisualizerEnabled(enabled: Boolean) {
        repository.persistBackgroundVisualizerEnabled(enabled)
    }

    fun setVisualizerAboveChrome(aboveChrome: Boolean) {
        repository.persistVisualizerAboveChrome(aboveChrome)
    }

    fun setVisualizerBackgroundMode(mode: String) {
        repository.persistVisualizerBackgroundMode(mode)
    }

    fun setVisualizerFpsLimit(fps: Int) {
        repository.persistVisualizerFpsLimit(fps)
    }

    fun setVisualizerStyleCategory(category: String) {
        repository.persistVisualizerStyleCategory(category)
    }

    fun setVisualizerCycleMode(mode: String) {
        repository.persistVisualizerCycleMode(mode)
    }

    fun setVisualizerAlpha(alpha: Float) {
        repository.persistVisualizerSettings(
            presetId = settings.value.lastVisualizerPresetId,
            bgEnabled = settings.value.backgroundVisualizerEnabled,
            alpha = alpha,
            presetName = settings.value.lastVisualizerPresetName,
            presetPath = settings.value.lastVisualizerPresetPath
        )
    }

    fun setAutoplayOnLaunch(enabled: Boolean) {
        repository.persistAutoplayOnLaunch(enabled)
    }

    fun setGaplessPlayback(enabled: Boolean) {
        repository.persistGaplessSettings(enabled, settings.value.crossfadeDurationSec)
    }
    fun setCrossfadeDuration(seconds: Float) {
        repository.persistGaplessSettings(settings.value.gaplessPlayback, seconds)
    
    }
    fun setVolumeNormalizationEnabled(enabled: Boolean) {
        repository.persistVolumeNormalization(enabled)
        playerManager?.setVolumeNormalization(enabled)

    }
    fun setOfflineMode(enabled: Boolean) {
        repository.setOfflineMode(enabled)
        playerManager?.setOfflineMode(enabled)

    }
    fun toggleOfflineMode() {
        setOfflineMode(!settings.value.offlineMode)

    }
    fun setSpeed(speed: Float) {
        _playerState.value = _playerState.value.copy(speed = speed)
        playerManager?.setPlaybackSpeed(speed)

    }
    fun showFullPlayer(show: Boolean) {
        _playerState.value = _playerState.value.copy(isFullPlayerVisible = show)

    }
    fun showLyrics(show: Boolean) {
        _playerState.value = _playerState.value.copy(isLyricsVisible = show)

    }
    fun openTagInspector(track: DownloadedTrack) {
        _playerState.value = _playerState.value.copy(
            isTagInspectorVisible = true,
            tagInspectorTrack = track
        )

    }
    fun closeTagInspector() {
        _playerState.value = _playerState.value.copy(
            isTagInspectorVisible = false,
            tagInspectorTrack = null
        )

    }

    val libraryMetadataEnricher get() = repository.libraryMetadataEnricher

    private val _isEnrichmentSheetVisible = MutableStateFlow(false)
    val isEnrichmentSheetVisible: StateFlow<Boolean> = _isEnrichmentSheetVisible.asStateFlow()

    fun showEnrichmentSheet(show: Boolean) {
        _isEnrichmentSheetVisible.value = show
    }

    private val _isSongRecognitionVisible = MutableStateFlow(false)
    val isSongRecognitionVisible: StateFlow<Boolean> = _isSongRecognitionVisible.asStateFlow()

    fun showSongRecognition(show: Boolean) {
        _isSongRecognitionVisible.value = show
    }

    fun identifyAndEnrichSingleTrack(track: DownloadedTrack) {
        viewModelScope.launch(exceptionHandler) {
            val enricher = repository.libraryMetadataEnricher
            val identified = enricher.identifySingleTrack(track)
            if (identified != null) {
                enricher.applyEnrichedMetadata(track, identified)
            }
        }
    }

    fun saveTags(trackId: Long, title: String, artist: String, album: String, genre: String, releaseDate: String, trackNumber: Int, lyrics: String = "") {
        viewModelScope.launch(exceptionHandler) {
            repository.updateTrackTags(trackId, title, artist, album, genre, releaseDate, trackNumber, lyrics = lyrics)
            if (_playerState.value.currentTrack?.id == trackId) {
                _playerState.value = _playerState.value.copy(
                    currentTrack = _playerState.value.currentTrack?.copy(
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre,
                        releaseDate = releaseDate,
                        trackNumber = trackNumber,
                        lyrics = if (lyrics.isNotBlank()) lyrics else _playerState.value.currentTrack!!.lyrics
                    )
                )
            }
            closeTagInspector()
        }
    }
    fun toggleFavorite(track: DownloadedTrack) {
        viewModelScope.launch(exceptionHandler) {
            val current = repository.getTrackById(track.id) ?: track
            val newFav = !current.isFavorite
            repository.toggleFavorite(track)
            if (_playerState.value.currentTrack?.id == track.id) {
                _playerState.value = _playerState.value.copy(
                    currentTrack = _playerState.value.currentTrack?.copy(isFavorite = newFav)
                )
            }
        }
    }
    fun deleteTrack(track: DownloadedTrack, deleteFromStorage: Boolean = true, context: android.content.Context? = null) {
        viewModelScope.launch(exceptionHandler) {
            if (_playerState.value.currentTrack?.id == track.id) {
                playNext()
            }
            playerManager?.let { pm ->
                val currentQueue = _playerState.value.queue
                val idx = currentQueue.indexOfFirst { it.id == track.id }
                if (idx >= 0) {
                    pm.removeFromQueue(idx)
                }
            }
            if (deleteFromStorage) {
                repository.deleteTrackFromStorage(track, context)
            } else {
                repository.removeTrackFromLibraryOnly(track)
            }
        }
    }
    // Local Audio Import
    fun importLocalAudio(uris: List<Uri>, onComplete: (Int) -> Unit) {
        viewModelScope.launch(exceptionHandler) {
            val count = repository.importLocalAudioFiles(uris)
            onComplete(count)

        }
    }
    // DSP Customization Functions
    fun setDspEnabled(enabled: Boolean) {
        _dspState.value = _dspState.value.copy(isDspEnabled = enabled)
        ObsidianDspEngine.updateState { it.copy(isMasterEnabled = enabled) }
    }
    fun applyDspPreset(preset: DspPreset) {
        _dspState.value = _dspState.value.copy(
            activePresetId = preset.id,
            bassBoostDb = preset.bassBoostDb,
            trebleBoostDb = preset.trebleBoostDb,
            soundstageWidth = preset.soundstageWidth,
            eqGains = preset.eqGains,
            dynamicRangeCompression = preset.dynamicRangeCompression,
            tubeWarmth = preset.tubeWarmth
        )
        ObsidianDspEngine.updateState { current ->
            current.copy(
                isMasterEnabled = true,
                isEqEnabled = true,
                eqGains = preset.eqGains,
                isBassEnabled = preset.bassBoostDb > 0.1f,
                bassGainDb = preset.bassBoostDb,
                isFieldSurroundEnabled = preset.soundstageWidth > 0.1f,
                surroundWidthPercent = (preset.soundstageWidth * 100f).coerceIn(50f, 200f),
                isTubeEnabled = preset.tubeWarmth,
                isDynamicSystemEnabled = preset.dynamicRangeCompression
            )
        }
    }
    fun setBassBoost(db: Float) {
        _dspState.value = _dspState.value.copy(bassBoostDb = db)
        ObsidianDspEngine.updateState { it.copy(isBassEnabled = db > 0.1f, bassGainDb = db) }
    }
    fun setTrebleBoost(db: Float) {
        _dspState.value = _dspState.value.copy(trebleBoostDb = db)
        ObsidianDspEngine.updateState { it.copy(isClarityEnabled = db > 0.1f, clarityGainDb = db) }
    }
    fun setSoundstageWidth(width: Float) {
        _dspState.value = _dspState.value.copy(soundstageWidth = width)
        ObsidianDspEngine.updateState { it.copy(isFieldSurroundEnabled = width > 0.1f, surroundWidthPercent = (width * 100f).coerceIn(50f, 200f)) }
    }
    fun setEqGain(bandIndex: Int, gainDb: Float) {
        val currentGains = _dspState.value.eqGains.toMutableList()
        if (bandIndex in 0 until currentGains.size) {
            currentGains[bandIndex] = gainDb
            _dspState.value = _dspState.value.copy(eqGains = currentGains)
            ObsidianDspEngine.updateState { it.copy(isEqEnabled = true, eqGains = currentGains) }
        }
    }
    fun setBitPerfectDac(enabled: Boolean) {
        _dspState.value = _dspState.value.copy(isBitPerfectDac = enabled)
    }
    fun setResamplingRate(rate: String) {
        _dspState.value = _dspState.value.copy(resamplingRate = rate)
    }
    fun setReplayGainMode(mode: String) {
        _dspState.value = _dspState.value.copy(replayGainMode = mode)
    }
    fun setDitheringMode(mode: String) {
        _dspState.value = _dspState.value.copy(ditheringMode = mode)
    }
    fun toggleTubeWarmth() {
        val next = !_dspState.value.tubeWarmth
        _dspState.value = _dspState.value.copy(tubeWarmth = next)
        ObsidianDspEngine.updateState { it.copy(isTubeEnabled = next) }
    }
    fun toggleDynamicRangeCompression() {
        val next = !_dspState.value.dynamicRangeCompression
        _dspState.value = _dspState.value.copy(dynamicRangeCompression = next)
        ObsidianDspEngine.updateState { it.copy(isDynamicSystemEnabled = next) }
    }

    private var extensionRegistrySyncJob: kotlinx.coroutines.Job? = null

    fun startBackgroundExtensionRegistrySync() {
        extensionRegistrySyncJob?.cancel()
        extensionRegistrySyncJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _isUpdatingExtensions.value = true
                try {
                    val list = ExtensionRegistryService.fetchRegistry(forceRefresh = false)
                    _extensions.value = list
                    repository.autoUpdateExtensions()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.backgroundRegistrySync - attempt")
                } finally {
                    _isUpdatingExtensions.value = false
                }
                // Periodic fetch every 2 hours
                delay(2 * 3600 * 1000L)
            }
        }
    }

    fun autoUpdateExtensions(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch(exceptionHandler) {
            _isUpdatingExtensions.value = true
            try {
                val list = ExtensionRegistryService.fetchRegistry(forceRefresh = true)
                _extensions.value = list
                repository.autoUpdateExtensions()
                onComplete(list.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.autoUpdateExtensions - attempt")
                onComplete(0)
            } finally {
                _isUpdatingExtensions.value = false
            }
        }
    }
    // Settings
    fun scanSelectedDirectory(uri: Uri, context: android.content.Context, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch(exceptionHandler) {
            val count = repository.scanDirectoryTree(uri, context)
            repository.startBackgroundTagSync()
            onComplete(count)

        }
    }
    fun rescanLibrary(context: android.content.Context, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch(exceptionHandler) {
            _isLibraryScanning.value = true
            try {
                val count = repository.fullLibraryRescan(context)
                repository.startBackgroundTagSync()
                onComplete(count)
            } finally {
                _isLibraryScanning.value = false
            }
        }
    }
    fun fullLibraryRescan(context: android.content.Context?) {
        viewModelScope.launch(exceptionHandler) {
            repository.fullLibraryRescan(context ?: com.anuj.obsidian.ObsidianApplication.instance)

        }
    }
    private val _selectedFolderFilter = MutableStateFlow<String?>(null)
    val selectedFolderFilter: StateFlow<String?> = _selectedFolderFilter.asStateFlow()

    fun setSelectedFolderFilter(folder: String?) {
        _selectedFolderFilter.value = folder

    }
    suspend fun getAvailableAudioFolders(): List<Pair<String, Int>> {
        return repository.getAvailableAudioFolders(com.anuj.obsidian.ObsidianApplication.instance)

    }
    fun addLinkedMusicFolder(path: String, name: String) {
        viewModelScope.launch(exceptionHandler) {
            repository.addLinkedMusicFolder(path, name)

        }
    }
    fun setLinkedMusicFolders(folders: List<Pair<String, String>>, context: android.content.Context? = null, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch(exceptionHandler) {
            repository.setLinkedMusicFolders(folders)
            val ctx = context ?: com.anuj.obsidian.ObsidianApplication.instance
            val count = repository.fullLibraryRescan(ctx)
            onComplete(count)

        }
    }
    fun rescanCurrentDirectory(context: android.content.Context, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch(exceptionHandler) {
            val currentUri = settings.value.selectedMusicFolderUri
            if (currentUri.isNotBlank()) {
                val count = repository.scanDirectoryTree(Uri.parse(currentUri), context)
                onComplete(count)
            } else {
                onComplete(0)
            }
        }
    }
    fun updateShowLayoutBounds(enabled: Boolean) {
        repository.persistShowLayoutBounds(enabled)
    }
    fun updateCustomDownloadFolder(uri: Uri, folderName: String) {
        repository.updateSettings {
            it.copy(
                customDownloadFolderUri = uri.toString(),
                customDownloadFolderName = folderName
            )

        }
    }
    fun clearCustomDownloadFolder() {
        repository.updateSettings {
            it.copy(
                customDownloadFolderUri = "",
                customDownloadFolderName = "",
                storagePath = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "ObsidianObsidian").absolutePath
            )

        }
    }
    fun updateDownloadFormat(format: String) {

    }
    fun updateDownloadDestinationMode(mode: String) {

    }
    fun updateQualitySetting(qualityId: String) {
        repository.updateSettings { it.copy(audioQualityId = qualityId) }
    }
    fun updateFilenamePattern(pattern: String) {
        repository.updateSettings { it.copy(filenameFormat = pattern) }
    }
    fun updateStorageDirectory(path: String) {
        repository.updateSettings { it.copy(storagePath = path) }
    }
    fun toggleEmbedLyrics(enabled: Boolean) {
        repository.updateSettings { it.copy(embedLyrics = enabled) }
    }
    fun toggleGenerateLrc(enabled: Boolean) {
        repository.updateSettings { it.copy(generateLrcFile = enabled) }
    }
    fun toggleEmbedMaxCoverArt(enabled: Boolean) {
        repository.updateSettings { it.copy(embedMaxCoverArt = enabled) }
    }
    fun updateConcurrentDownloads(count: Int) {
        repository.updateSettings { it.copy(downloadConcurrentThreads = count) }
    }
    fun toggleWifiOnly(enabled: Boolean) {
        repository.updateSettings { it.copy(downloadOverWifiOnly = enabled) }
    }
    fun updateBackendServer(url: String) {
        repository.updateSettings { it.copy(backendServerUrl = url) }
    }
    fun clearCompletedDownloads() {
        viewModelScope.launch(exceptionHandler) {
            repository.clearCompletedDownloads()
        }
    }
    fun retryTagging(taskId: Long) {
        viewModelScope.launch(exceptionHandler) {
            repository.retryTagging(taskId)
        }
    }
    fun removeLinkedMusicFolder(uri: String, context: android.content.Context?) {
        repository.removeLinkedMusicFolder(uri, viewModelScope)
        rescanLibrary(context ?: com.anuj.obsidian.ObsidianApplication.instance)

    }
    fun cancelDownload(taskId: Long) {
        viewModelScope.launch(exceptionHandler) {
            repository.cancelDownload(taskId)
        }
    }
    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch(exceptionHandler) {
            repository.createPlaylist(name, description)
        }
    }
    fun createPlaylistAndAddTracks(name: String, description: String = "", trackIds: List<Long>, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(exceptionHandler) {
            val newId = repository.createPlaylist(name, description)
            if (newId > 0L && trackIds.isNotEmpty()) {
                repository.addTracksToPlaylist(newId, trackIds)
            }
            onComplete?.invoke()
        }
    }
    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(exceptionHandler) {
            repository.addTrackToPlaylist(playlistId, trackId)
        }
    }
    fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        viewModelScope.launch(exceptionHandler) {
            repository.addTracksToPlaylist(playlistId, trackIds)
        }
    }
    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(exceptionHandler) {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }
    fun reorderPlaylistTrack(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(exceptionHandler) {
            repository.reorderPlaylistTracks(playlistId, fromIndex, toIndex)
        }
    }
    fun deletePlaylist(playlist: com.anuj.obsidian.data.model.Playlist) {
        viewModelScope.launch(exceptionHandler) {
            repository.deletePlaylist(playlist)

        }
    }
    fun playPlaylist(playlistId: Long, startTrack: DownloadedTrack? = null, forceShuffle: Boolean = false) {
        viewModelScope.launch(exceptionHandler) {
            val playlist = playlists.value.find { it.id == playlistId }
            val trackIds = repository.getTrackIdsForPlaylist(playlistId).firstOrNull() ?: emptyList()
            val allT = tracks.value
            val playlistTracks = trackIds.mapNotNull { id -> allT.find { it.id == id } }
            if (playlistTracks.isNotEmpty()) {
                val target = startTrack ?: if (forceShuffle) playlistTracks.random() else playlistTracks.firstOrNull() ?: return@launch
                playTrackFromList(
                    track = target,
                    contextList = playlistTracks,
                    forceShuffle = forceShuffle,
                    sourceType = PlaybackSourceType.PLAYLIST,
                    sourceId = playlistId.toString(),
                    sourceTitle = playlist?.name ?: "Playlist"
                )
            }
        }
    }
    fun getPlaylistTracksFlow(playlistId: Long): Flow<List<DownloadedTrack>> {
        return repository.getTrackIdsForPlaylist(playlistId).map { ids ->
            val allT = tracks.value
            ids.mapNotNull { id -> allT.find { it.id == id } }
        }
    }
    fun intelligentShuffleAndPlay(tracksToShuffle: List<DownloadedTrack>) {
        if (tracksToShuffle.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.Default + exceptionHandler) {
            try {
                val pool = tracksToShuffle.toMutableList()
                val smartQueue = mutableListOf<DownloadedTrack>()
                
                while(pool.isNotEmpty()) {
                    val seed = pool.randomOrNull() ?: break
                    pool.remove(seed)
                    
                    val chunk = mutableListOf(seed)
                    val chunkSize = (3..5).random()
                    
                    while(chunk.size < chunkSize && pool.isNotEmpty()) {
                        val seedArtist = seed.artist.lowercase().trim()
                        val seedGenre = seed.genre.lowercase().trim()
                        
                        // Parse releaseDate (which might be "2024" or full date) to get decade
                        val seedYear = seed.releaseDate.take(4).toIntOrNull() ?: -1
                        val seedDecade = if (seedYear > 1900) (seedYear / 10) * 10 else -1
                        
                        val bestMatch = pool.firstOrNull { it.artist.lowercase().trim() == seedArtist }
                            ?: pool.firstOrNull { it.genre.lowercase().trim() == seedGenre }
                            ?: pool.firstOrNull { 
                                val year = it.releaseDate.take(4).toIntOrNull() ?: -1
                                year > 1900 && (year / 10) * 10 == seedDecade 
                            }
                            ?: pool.randomOrNull()
                        if (bestMatch == null) break
                            
                        chunk.add(bestMatch)
                        pool.remove(bestMatch)
                    }
                    smartQueue.addAll(chunk)
                }
                withContext(Dispatchers.Main) {
                    if (smartQueue.isNotEmpty()) {
                        val firstT = smartQueue.firstOrNull() ?: return@withContext
                        playTrackFromList(firstT, smartQueue)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.intelligentShuffleAndPlay - attempt")
                Log.e("ObsidianViewModel", "Error in intelligentShuffleAndPlay: ${e.message}")
            }
        }
    }
    fun exportFullBackup(context: android.content.Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                val allT = tracks.value
                val s = settings.value
                val dsp = dspState.value
                
                val backupJson = org.json.JSONObject().apply {
                    put("version", 1)
                    put("timestamp", System.currentTimeMillis())
                    put("tracks_count", allT.size)
                    
                    // Tracks export
                    val tracksArray = org.json.JSONArray()
                    allT.forEach { t ->
                        val tObj = org.json.JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("artist", t.artist)
                            put("album", t.album)
                            put("durationSeconds", t.durationSeconds)
                            put("format", t.format)
                            put("bitDepth", t.bitDepth)
                            put("sampleRate", t.sampleRate)
                            put("albumArtUrl", t.albumArtUrl)
                            put("localFilePath", t.localFilePath)
                            put("genre", t.genre)
                            put("releaseDate", t.releaseDate)
                            put("isFavorite", t.isFavorite)
                        }
                        tracksArray.put(tObj)
                    }
                    put("tracks", tracksArray)
                    
                    // Settings
                    val settingsObj = org.json.JSONObject().apply {
                        put("themePreset", s.themePreset)
                        put("gaplessPlayback", s.gaplessPlayback)
                        put("crossfadeDurationSec", s.crossfadeDurationSec.toDouble())
                        put("volumeNormalizationEnabled", s.volumeNormalizationEnabled)
                        put("audioQualityId", s.audioQualityId)
                    }
                    put("settings", settingsObj)

                    // DSP
                    val dspObj = org.json.JSONObject().apply {
                        put("bassGainDb", dsp.bassBoostDb.toDouble())
                        put("trebleBoostDb", dsp.trebleBoostDb.toDouble())
                        put("soundstageWidth", dsp.soundstageWidth.toDouble())
                        put("tubeWarmth", dsp.tubeWarmth)
                        put("dynamicRangeCompression", dsp.dynamicRangeCompression)
                        val eqArray = org.json.JSONArray()
                        put("eqGains", eqArray)
                    }
                    put("dsp", dspObj)

                }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(backupJson.toString(2).toByteArray(Charsets.UTF_8))

                }
                withContext(Dispatchers.Main) {
                    onResult(true, "Backup successfully exported (${allT.size} tracks & settings)")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.exportFullBackup - attempt")
                withContext(Dispatchers.Main) {
                    onResult(false, "Export failed: ${e.localizedMessage}")
                }
            }
        }
    }
    fun importFullBackup(context: android.content.Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonStr = inputStream?.bufferedReader().use { it?.readText() }
                if (jsonStr.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Backup file is empty or unreadable")
                    }
                    return@launch
                }
                val backupJson = org.json.JSONObject(jsonStr)
                var restoredCount = 0

                if (backupJson.has("tracks")) {
                    val tracksArray = backupJson.getJSONArray("tracks")
                    val restoredTracks = mutableListOf<DownloadedTrack>()
                    for (i in 0 until tracksArray.length()) {
                        val tObj = tracksArray.getJSONObject(i)
                        val track = DownloadedTrack(
                            id = tObj.optLong("id", System.currentTimeMillis() + i),
                            title = tObj.optString("title", "Unknown"),
                            artist = tObj.optString("artist", "Unknown Artist"),
                            album = tObj.optString("album", "Unknown Album"),
                            durationSeconds = tObj.optInt("durationSeconds", 0),
                            format = tObj.optString("format", "FLAC"),
                            bitDepth = tObj.optString("bitDepth", "16-bit"),
                            sampleRate = tObj.optString("sampleRate", "44.1 kHz"),
                            bitrateKbps = tObj.optInt("bitrateKbps", 1411),
                            albumArtUrl = tObj.optString("albumArtUrl", ""),
                            localFilePath = tObj.optString("localFilePath", ""),
                            fileSizeMb = tObj.optDouble("fileSizeMb", 25.0).toFloat(),
                            lyrics = tObj.optString("lyrics", ""),
                            isrc = tObj.optString("isrc", ""),
                            releaseDate = tObj.optString("releaseDate", "2024"),
                            genre = tObj.optString("genre", "Audiophile"),
                            providerSource = tObj.optString("providerSource", "Backup"),
                            isFavorite = tObj.optBoolean("isFavorite", false),
                            dateAddedTimestamp = tObj.optLong("dateAddedTimestamp", System.currentTimeMillis())
                        )
                        restoredTracks.add(track)
                    }
                    if (restoredTracks.isNotEmpty()) {
                        repository.insertAllTracks(restoredTracks)
                        restoredCount = restoredTracks.size

                    }
                }
                // Restore DSP settings if present
                if (backupJson.has("dsp")) {
                    val dspObj = backupJson.getJSONObject("dsp")
                    val bass = dspObj.optDouble("bassGainDb", 0.0).toFloat()
                    val treble = dspObj.optDouble("trebleBoostDb", 0.0).toFloat()
                    val width = dspObj.optDouble("soundstageWidth", 1.0).toFloat()
                    setBassBoost(bass)
                    setTrebleBoost(treble)
                    setSoundstageWidth(width)

                }
                withContext(Dispatchers.Main) {
                    onResult(true, "Backup restored: $restoredCount tracks and settings imported successfully!")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.importFullBackup - attempt")
                withContext(Dispatchers.Main) {
                    onResult(false, "Restore failed: ${e.localizedMessage}")
                }
            }
        }
    }
    fun setHeadsetActions(autoresume: Boolean, fadeOut: Boolean, fadeOutDurSec: Float = 1.5f) {
        repository.persistHeadsetActions(autoresume, fadeOut, fadeOutDurSec)

    }
    fun setDynamicCanvasSettings(enabled: Boolean, preset: String) {
        repository.persistDynamicCanvasSettings(enabled, preset)

    }
    fun playFolder(folderPath: String, shuffle: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                val allLocalTracks = tracks.value

                if (folderPath.startsWith("content://")) {
                    val matchingTracks = allLocalTracks.filter { track ->
                        track.localFilePath.startsWith(folderPath)
                    }
                    if (matchingTracks.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (shuffle) {
                                playTrackFromList(null, matchingTracks, forceShuffle = true)
                            } else {
                                matchingTracks.firstOrNull()?.let { playTrackFromList(it, matchingTracks, forceShuffle = false) }
                            }
                        }
                    } else {
                        // Directly scan SAF tree and queue tracks
                        val context = com.anuj.obsidian.ObsidianApplication.instance
                        repository.scanDirectoryTree(android.net.Uri.parse(folderPath), context)
                        val updatedTracks = repository.getAllTracksList().filter { it.localFilePath.startsWith(folderPath) }
                        if (updatedTracks.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                if (shuffle) {
                                    playTrackFromList(null, updatedTracks, forceShuffle = true)
                                } else {
                                    updatedTracks.firstOrNull()?.let { playTrackFromList(it, updatedTracks, forceShuffle = false) }
                                }
                            }
                        }
                    }
                    return@launch
                }

                val folderFile = File(folderPath)
                if (!folderFile.exists()) return@launch

                val normalizedFolder = folderFile.absolutePath.trimEnd('/')

                // Find matching tracks in library first
                var matchingTracks = allLocalTracks.filter { track ->
                    track.localFilePath.startsWith(normalizedFolder)
                }
                // If none registered in library, directly scan the directory files
                if (matchingTracks.isEmpty()) {
                    val scannedAudioFiles = mutableListOf<File>()
                    fun scanRecursive(dir: File) {
                        dir.listFiles()?.forEach { file ->
                            if (file.isDirectory) {
                                scanRecursive(file)
                            } else if (file.isFile) {
                                val ext = file.extension.lowercase()
                                if (ext in com.anuj.obsidian.data.local.AudioMetadataExtractor.ALL_SUPPORTED_EXTENSIONS) {
                                    scannedAudioFiles.add(file)
                                }
                            }
                        }
                    }
                    scanRecursive(folderFile)

                    val context = com.anuj.obsidian.ObsidianApplication.instance
                    matchingTracks = scannedAudioFiles.map { file ->
                        val uri = android.net.Uri.fromFile(file)
                        val info = try {
                            com.anuj.obsidian.data.local.AudioMetadataExtractor.extract(context, uri, file.name, null)
                        } catch (e: Throwable) {
                            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.scanRecursive - attempt")
                            com.anuj.obsidian.data.local.ExtractedAudioInfo(
                                title = file.nameWithoutExtension,
                                artist = folderFile.name,
                                album = folderFile.name,
                                genre = "",
                                durationSeconds = 0,
                                bitDepth = if (file.extension.equals("flac", true)) "16-bit" else "16-bit",
                                sampleRate = "44.1 kHz",
                                bitrateKbps = if (file.extension.equals("flac", true)) 1411 else 320,
                                albumArtPath = "",
                                lyrics = "",
                                trackNumber = 0,
                                year = ""
                            )
                        }
                        val finalDuration = if (info.durationSeconds > 0) info.durationSeconds else 0
                        DownloadedTrack(
                            id = DownloadedTrack.generatePseudoId(info.title, info.artist, file.absolutePath),
                            title = info.title,
                            artist = info.artist,
                            album = info.album,
                            albumArtUrl = info.albumArtPath,
                            durationSeconds = finalDuration,
                            fileSizeMb = (file.length() / (1024f * 1024f)).coerceAtLeast(0.1f),
                            format = file.extension.uppercase(),
                            localFilePath = file.absolutePath,
                            bitDepth = info.bitDepth,
                            sampleRate = info.sampleRate,
                            bitrateKbps = info.bitrateKbps,
                            lyrics = info.lyrics,
                            genre = info.genre.ifBlank { "Folder Play" },
                            releaseDate = info.year,
                            dateAddedTimestamp = file.lastModified(),
                            providerSource = "local"
                        )
                    }
                    if (matchingTracks.isNotEmpty()) {
                        repository.insertAllTracks(matchingTracks)
                    }
                }
                if (matchingTracks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (shuffle) {
                            playTrackFromList(null, matchingTracks, forceShuffle = true)
                        } else {
                            matchingTracks.firstOrNull()?.let { playTrackFromList(it, matchingTracks, forceShuffle = false) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.scanRecursive - attempt")
                Log.e("ObsidianViewModel", "Error in playFolder: ${e.message}")
            }
        }
    }

    suspend fun getDirectoryContents(folderPath: String): Pair<List<FileNodeItem>, List<DownloadedTrack>> = withContext(Dispatchers.IO) {
        val allKnownTracks = tracks.value
        val trackMapByPath = allKnownTracks.associateBy { it.localFilePath }
        val subfolders = mutableListOf<FileNodeItem>()
        val directTracks = mutableListOf<DownloadedTrack>()

        // 1. Check for SAF content:// URI paths
        if (folderPath.startsWith("content://")) {
            val context = com.anuj.obsidian.ObsidianApplication.instance
            val uri = android.net.Uri.parse(folderPath)
            val docFile = try {
                if (folderPath.contains("/tree/") && !folderPath.contains("/document/")) {
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                } else {
                    androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                        ?: androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                }
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianViewModel.getDirectoryContents - SAF DocumentFile")
                null
            }

            if (docFile == null || !docFile.exists() || !docFile.isDirectory) {
                return@withContext Pair(emptyList(), emptyList())
            }

            val docFiles = try {
                docFile.listFiles().sortedWith(compareBy({ !it.isDirectory }, { (it.name ?: "").lowercase() }))
            } catch (e: Exception) {
                emptyList()
            }

            for (f in docFiles) {
                val name = f.name ?: ""
                if (f.isDirectory) {
                    var audioCount = 0
                    var subfolderCount = 0
                    try {
                        f.listFiles().forEach { child ->
                            if (child.isDirectory) {
                                subfolderCount++
                            } else {
                                val childName = child.name ?: ""
                                if (com.anuj.obsidian.data.local.AudioMetadataExtractor.isSupportedAudio(childName)) {
                                    audioCount++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore counting failure
                    }
                    subfolders.add(
                        FileNodeItem(
                            path = f.uri.toString(),
                            name = name.ifBlank { "Subfolder" },
                            audioCount = audioCount,
                            subfolderCount = subfolderCount,
                            lastModified = f.lastModified()
                        )
                    )
                } else if (f.isFile) {
                    if (com.anuj.obsidian.data.local.AudioMetadataExtractor.isSupportedAudio(name)) {
                        val uriStr = f.uri.toString()
                        val existing = trackMapByPath[uriStr]
                        val ext = name.substringAfterLast('.', "FLAC").lowercase()
                        val track = if (existing != null) existing else {
                            val context = com.anuj.obsidian.ObsidianApplication.instance
                            val info = try {
                                com.anuj.obsidian.data.local.AudioMetadataExtractor.extract(context, f.uri, name, f)
                            } catch (e: Throwable) {
                                android.util.Log.w("ObsidianViewModel", "Metadata extraction failed for ${f.uri}: ${e.message}")
                                null
                            }
                            DownloadedTrack(
                                id = DownloadedTrack.generatePseudoId(info?.title ?: name.substringBeforeLast('.'), info?.artist ?: (docFile.name ?: "SAF Storage"), uriStr),
                                title = info?.title ?: name.substringBeforeLast('.'),
                                artist = info?.artist ?: (docFile.name ?: "SAF Storage"),
                                album = info?.album ?: (docFile.name ?: "SAF Folder"),
                                albumArtUrl = info?.albumArtPath ?: "",
                                durationSeconds = info?.durationSeconds ?: 0,
                                fileSizeMb = (f.length() / (1024f * 1024f)).coerceAtLeast(0.1f),
                                format = ext.uppercase(),
                                localFilePath = uriStr,
                                bitDepth = info?.bitDepth ?: "16-bit",
                                sampleRate = info?.sampleRate ?: "44.1 kHz",
                                bitrateKbps = info?.bitrateKbps ?: 320,
                                dateAddedTimestamp = f.lastModified()
                            )
                        }
                        directTracks.add(track)
                    }
                }
            }
            return@withContext Pair(subfolders, directTracks)
        }

        // 2. Standard filesystem path logic
        val root = File(folderPath)
        if (!root.exists() || !root.isDirectory) {
            return@withContext Pair(emptyList(), emptyList())
        }

        val files = root.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

        for (f in files) {
            if (f.isDirectory) {
                // Count audio files in this subfolder
                var audioCount = 0
                var subfolderCount = 0
                f.listFiles()?.forEach { child ->
                    if (child.isDirectory) subfolderCount++
                    else if (child.extension.lowercase() in com.anuj.obsidian.data.local.AudioMetadataExtractor.ALL_SUPPORTED_EXTENSIONS) {
                        audioCount++
                    }
                }
                subfolders.add(
                    FileNodeItem(
                        path = f.absolutePath,
                        name = f.name,
                        audioCount = audioCount,
                        subfolderCount = subfolderCount,
                        lastModified = f.lastModified()
                    )
                )
            } else if (f.isFile) {
                val ext = f.extension.lowercase()
                if (ext in com.anuj.obsidian.data.local.AudioMetadataExtractor.ALL_SUPPORTED_EXTENSIONS) {
                    val existing = trackMapByPath[f.absolutePath]
                    val track = if (existing != null) existing else {
                        val context = com.anuj.obsidian.ObsidianApplication.instance
                        val uri = android.net.Uri.fromFile(f)
                        val info = try {
                            com.anuj.obsidian.data.local.AudioMetadataExtractor.extract(context, uri, f.name, null)
                        } catch (e: Throwable) {
                            android.util.Log.w("ObsidianViewModel", "Filesystem metadata extraction failed for ${f.name}: ${e.message}")
                            null
                        }
                        DownloadedTrack(
                            id = DownloadedTrack.generatePseudoId(info?.title ?: f.nameWithoutExtension, info?.artist ?: root.name, f.absolutePath),
                            title = info?.title ?: f.nameWithoutExtension,
                            artist = info?.artist ?: root.name,
                            album = info?.album ?: root.name,
                            albumArtUrl = info?.albumArtPath ?: "",
                            durationSeconds = info?.durationSeconds ?: 0,
                            fileSizeMb = (f.length() / (1024f * 1024f)).coerceAtLeast(0.1f),
                            format = f.extension.uppercase(),
                            localFilePath = f.absolutePath,
                            bitDepth = info?.bitDepth?.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) } ?: if (f.extension.equals("flac", true)) "24-bit" else "16-bit",
                            sampleRate = info?.sampleRate?.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) } ?: "44.1 kHz",
                            bitrateKbps = info?.bitrateKbps ?: 0,
                            dateAddedTimestamp = f.lastModified()
                        )
                    }
                    directTracks.add(track)
                }
            }
        }
        Pair(subfolders, directTracks)
    }
    fun showDriveMode(visible: Boolean) {
        _playerState.value = _playerState.value.copy(isDriveModeVisible = visible)
    }

    private val _storageStats = MutableStateFlow(com.anuj.obsidian.util.StorageCleanupStats())
    val storageStats: StateFlow<com.anuj.obsidian.util.StorageCleanupStats> = _storageStats.asStateFlow()

    fun refreshStorageStats(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = com.anuj.obsidian.util.ObsidianStorageCleanupManager.getStorageStats(context, settings.value)
            _storageStats.value = stats
        }
    }

    fun clearCachedAlbumArt(context: android.content.Context, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = com.anuj.obsidian.util.ObsidianStorageCleanupManager.clearCachedAlbumArt(context)
            val updated = com.anuj.obsidian.util.ObsidianStorageCleanupManager.getStorageStats(context, settings.value)
            _storageStats.value = updated
            withContext(Dispatchers.Main) {
                val freedStr = com.anuj.obsidian.util.ObsidianStorageCleanupManager.formatBytes(freed)
                onComplete("Cleared $freedStr of cached album art")
            }
        }
    }

    fun clearDownloadTempFiles(context: android.content.Context, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = com.anuj.obsidian.util.ObsidianStorageCleanupManager.clearDownloadTempFiles(context, settings.value)
            val updated = com.anuj.obsidian.util.ObsidianStorageCleanupManager.getStorageStats(context, settings.value)
            _storageStats.value = updated
            withContext(Dispatchers.Main) {
                val freedStr = com.anuj.obsidian.util.ObsidianStorageCleanupManager.formatBytes(freed)
                onComplete("Cleared $freedStr of download manager temp files")
            }
        }
    }

    fun quickCleanAllStorage(context: android.content.Context, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val (artFreed, tempFreed) = com.anuj.obsidian.util.ObsidianStorageCleanupManager.clearAllTemporarySpace(context, settings.value)
            val updated = com.anuj.obsidian.util.ObsidianStorageCleanupManager.getStorageStats(context, settings.value)
            _storageStats.value = updated
            withContext(Dispatchers.Main) {
                val totalFreedStr = com.anuj.obsidian.util.ObsidianStorageCleanupManager.formatBytes(artFreed + tempFreed)
                onComplete("Reclaimed $totalFreedStr of storage space (${com.anuj.obsidian.util.ObsidianStorageCleanupManager.formatBytes(artFreed)} art, ${com.anuj.obsidian.util.ObsidianStorageCleanupManager.formatBytes(tempFreed)} temp files)")
            }
        }
    }

    fun forceCheckOnlineArtworkLimit(context: android.content.Context, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            val resetTriggered = com.anuj.obsidian.util.ObsidianStorageCleanupManager.checkAndEnforceOnlineArtworkLimit(context)
            val updated = com.anuj.obsidian.util.ObsidianStorageCleanupManager.getStorageStats(context, settings.value)
            _storageStats.value = updated
            withContext(Dispatchers.Main) {
                if (resetTriggered) {
                    onComplete(true, "Artwork limit (500 MB) reached: Cache was automatically reset.")
                } else {
                    onComplete(false, "Artwork cache is within 500 MB limit (${updated.cachedArtworkFormatted} used).")
                }
            }
        }
    }

    fun clearAppCache(context: android.content.Context, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = com.anuj.obsidian.util.ObsidianStorageCleanupManager.clearGeneralCache(context)
            val updated = com.anuj.obsidian.util.ObsidianStorageCleanupManager.getStorageStats(context, settings.value)
            _storageStats.value = updated
            withContext(Dispatchers.Main) {
                onComplete("Cleared ${com.anuj.obsidian.util.ObsidianStorageCleanupManager.formatBytes(freed)} of app cache.")
            }
        }
    }

    fun clearAllDownloadedMusic(context: android.content.Context, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = com.anuj.obsidian.util.ObsidianStorageCleanupManager.clearDownloadedTracks(context, settings.value)
            val currentTracks = tracks.value
            currentTracks.forEach { track ->
                repository.deleteTrackFromStorage(track, context)
            }
            val updated = com.anuj.obsidian.util.ObsidianStorageCleanupManager.getStorageStats(context, settings.value)
            _storageStats.value = updated
            withContext(Dispatchers.Main) {
                onComplete("Cleared ${com.anuj.obsidian.util.ObsidianStorageCleanupManager.formatBytes(freed)} and removed ${currentTracks.size} songs from library.")
            }
        }
    }


    fun deleteAllDownloadedTracks(context: android.content.Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            tracks.value.forEach { track ->
                repository.deleteTrackFromStorage(track, context)
            }
        }
    }
}
data class FileNodeItem(
    val path: String,
    val name: String,
    val audioCount: Int,
    val subfolderCount: Int,
    val lastModified: Long
)
