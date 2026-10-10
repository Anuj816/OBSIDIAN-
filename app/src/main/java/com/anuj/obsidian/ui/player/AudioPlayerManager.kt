package com.anuj.obsidian.ui.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.QueueEntry
import com.anuj.obsidian.data.model.toQueueEntry
import com.anuj.obsidian.data.local.FastAlbumArtHelper
import com.anuj.obsidian.data.model.PlaybackLoadingType
import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.data.model.QueueReturnMode
import com.anuj.obsidian.data.model.PlaybackContext
import com.anuj.obsidian.data.model.createNewPlaybackContext
import com.anuj.obsidian.data.model.PlaybackSourceType
import com.anuj.obsidian.data.model.ShuffleTraversal
import com.anuj.obsidian.data.model.AuthoritativePlaybackState
import com.anuj.obsidian.data.remote.MusicApiService
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.ui.AutoplayMode
import com.anuj.obsidian.ui.dsp.ObsidianDspEngine
import com.anuj.obsidian.ui.visualizer.HardwareVisualizerBuffer
import com.anuj.obsidian.ui.visualizer.AudioPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.anuj.obsidian.util.safeLaunch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
class AudioPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var exoPlayerInstance: ExoPlayer? = null
    private val playerLock = Any()

    private fun getOrCreateExoPlayer(): ExoPlayer? = synchronized(playerLock) {
        if (!isReleased && exoPlayerInstance == null) {
            exoPlayerInstance = buildGlitchFreePlayer()
        }
        exoPlayerInstance.takeIf { !isReleased }
    }

    val exoPlayer: ExoPlayer
        get() = getOrCreateExoPlayer() ?: throw IllegalStateException("ExoPlayer is released or uninitialized")

    private var onProgressCallback: ((Float, Int) -> Unit)? = null
    private var onCompletionCallback: (() -> Unit)? = null
    private var onLoadingStateCallback: ((PlaybackLoadingType) -> Unit)? = null
    private var consecutiveFailures = 0
    private var onQueueChangedCallback: ((List<DownloadedTrack>) -> Unit)? = null
    private var onErrorCallback: (() -> Unit)? = null
    private var onTrackTransitionedCallback: ((DownloadedTrack?) -> Unit)? = null
    private var onTrackFormatUpdatedCallback: ((DownloadedTrack) -> Unit)? = null

    private var primaryLoudnessEnhancer: LoudnessEnhancer? = null
    private var activeEnhancerSessionId: Int = 0
    private var isVolumeNormalizationEnabled: Boolean = false
    private var isLoudnessEnhancerSupported = true

    private var progressJob: Job? = null

    @Volatile private var isPlayingState: Boolean = false
    @Volatile private var playWhenReadyState: Boolean = false
    @Volatile private var currentPlaybackState: Int = Player.STATE_IDLE
    @Volatile private var currentRepeatMode: Int = Player.REPEAT_MODE_OFF
    @Volatile private var lastSkipNextTime: Long = 0L
    @Volatile private var lastSkipPrevTime: Long = 0L

    private val fadeController = com.anuj.obsidian.ui.visualizer.VisualizerFadeController()

    fun setVisualizerFadeOpacity(opacity: Float) {
        fadeController.setFadeOpacity(opacity)
        Log.d("AudioPlayerManager", "Visualizer fade opacity set to: $opacity")
    }

    fun getVisualizerFadeOpacity(): Float = fadeController.getFadeOpacity()

    fun setVisualizerFadeMode(mode: com.anuj.obsidian.ui.visualizer.VisualizerFadeController.FadeMode) {
        fadeController.setFadeMode(mode)
        Log.d("AudioPlayerManager", "Visualizer fade mode set to: $mode")
    }

    fun getVisualizerFadeController(): com.anuj.obsidian.ui.visualizer.VisualizerFadeController = fadeController

    fun updateVisualizerFade(bass: Float, mid: Float, treble: Float) {
        fadeController.updateAudioLevels(bass, mid, treble)
    }

    fun onAudioFrameAnalyzed(bass: Float, mid: Float, treble: Float) {
        updateVisualizerFade(bass, mid, treble)
    }

    fun isPlaying(): Boolean = isPlayingState
    fun playWhenReady(): Boolean = playWhenReadyState
    fun getPlaybackState(): Int = currentPlaybackState
    fun getRepeatMode(): Int = currentRepeatMode

    fun withPlayer(action: ExoPlayer.() -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            synchronized(playerLock) {
                if (!isReleased) {
                    try {
                        val player = exoPlayerInstance ?: getOrCreateExoPlayer()
                        player?.action()
                    } catch (e: Throwable) {
                        ObsidianCrashLogger.logException(e, "AudioPlayerManager.withPlayer")
                        Log.e("AudioPlayerManager", "Error executing player action: ${e.message}", e)
                    }
                }
            }
        } else {
            mainHandler.post {
                synchronized(playerLock) {
                    if (!isReleased) {
                        try {
                            val player = exoPlayerInstance ?: getOrCreateExoPlayer()
                            player?.action()
                        } catch (e: Throwable) {
                            ObsidianCrashLogger.logException(e, "AudioPlayerManager.withPlayer")
                            Log.e("AudioPlayerManager", "Error executing player action: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    suspend fun withPlayerSuspend(action: ExoPlayer.() -> Unit) {
        val player = synchronized(playerLock) {
            if (isReleased) null else (exoPlayerInstance ?: getOrCreateExoPlayer())
        } ?: return
        withContext(Dispatchers.Main) {
            synchronized(playerLock) {
                if (!isReleased) {
                    try {
                        player.action()
                    } catch (e: Throwable) {
                        ObsidianCrashLogger.logException(e, "AudioPlayerManager.withPlayerSuspend")
                        Log.e("AudioPlayerManager", "Error in withPlayerSuspend: ${e.message}", e)
                    }
                }
            }
        }
    }
    private val activePlaybackRequestId = java.util.concurrent.atomic.AtomicLong(0L)
    private val activePrefetchRequestId = java.util.concurrent.atomic.AtomicLong(0L)
    private var networkRetryCount = 0
    private val maxNetworkRetries = 3
    @Volatile private var attemptedAlacFallbackTrackId: Long? = null
    
    @Volatile
    private var isReleased = false

    private val resolvedStreamUrls = ConcurrentHashMap<String, String>()
    private val activeResolutions = ConcurrentHashMap<Long, Deferred<String>>()

    private fun getStreamCacheKey(track: DownloadedTrack): String {
        return if (track.id > 0L) "id_${track.id}" else "meta_${track.title.trim().lowercase()}_${track.artist.trim().lowercase()}_${track.localFilePath.hashCode()}"
    }

    private fun isSameTrack(a: DownloadedTrack?, b: DownloadedTrack?): Boolean {
        if (a == null || b == null) return false
        if (a.id > 0L && b.id > 0L) return a.id == b.id
        if (a.localFilePath.isNotBlank() && b.localFilePath.isNotBlank()) return a.localFilePath == b.localFilePath
        return a.title.equals(b.title, ignoreCase = true) && a.artist.equals(b.artist, ignoreCase = true)
    }

    // Playback State
    private var currentQueueEntry: QueueEntry? = null
    private var currentPlayingTrack: DownloadedTrack? = null
    private val shuffleManager = ShuffleManager()

    @Volatile private var currentLoadingType: PlaybackLoadingType = PlaybackLoadingType.NONE

    fun getCurrentLoadingType(): PlaybackLoadingType = synchronized(playerLock) { currentLoadingType }
    fun getExoPlayerPlaybackState(): Int = exoPlayerInstance?.playbackState ?: Player.STATE_IDLE

    fun isLikelyLocalTrack(track: DownloadedTrack?): Boolean {
        if (track == null) return false
        val path = track.localFilePath
        if (path.isNotBlank() && (path.startsWith("content://") || path.startsWith("file://") || path.startsWith("/"))) return true
        if (track.id > 0L && !path.startsWith("http")) return true
        return false
    }

    private val _playbackStateFlow = MutableStateFlow(AuthoritativePlaybackState())
    val playbackStateFlow: StateFlow<AuthoritativePlaybackState> = _playbackStateFlow.asStateFlow()

    fun emitAuthoritativeState() {
        _playbackStateFlow.value = getAuthoritativeState()
    }
    private var allLibraryTracks: List<DownloadedTrack> = emptyList()
    private var autoplayMode: com.anuj.obsidian.ui.AutoplayMode = com.anuj.obsidian.ui.AutoplayMode.AUTOPLAY_LIBRARY
    @Volatile private var isOfflineMode: Boolean = false
    private var isGaplessEnabled: Boolean = true
    private var crossfadeDurationSec: Float = 0f
    private var headsetAutoresume: Boolean = false
    private var headsetFadeOutOnDisconnect: Boolean = false
    private var headsetFadeOutDurationSec: Float = 1.5f

    private var currentPlayJob: Job? = null
    private var prefetchNextJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    @Volatile private var lastAutoTransitionTimestamp: Long = 0L
    private val replayGainCache = ConcurrentHashMap<Long, Float>()

    init {
        withPlayer {
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    HardwareVisualizerBuffer.onPlaybackStateChanged(AudioPlaybackState.TRACK_CHANGED)
                    ObsidianPlaybackService.updateNotification()
                    // Immediately restore volume to basePlaybackVolume to prevent crossfade dips on new track
                    withPlayer {
                        if (this.volume != basePlaybackVolume) {
                            this.volume = basePlaybackVolume
                        }
                    }
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        networkRetryCount = 0 // Reset on successful track transition
                        handleAutoTransition()
                    }
                    emitAuthoritativeState()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    isPlayingState = isPlaying
                    playWhenReadyState = isPlaying
                    HardwareVisualizerBuffer.onPlaybackStateChanged(
                        if (isPlaying) AudioPlaybackState.PLAYING else AudioPlaybackState.PAUSED
                    )
                    ObsidianPlaybackService.updateNotification()
                    if (isPlaying) {
                        networkRetryCount = 0 // Reset on successful playback resume
                        currentPlayingTrack?.let { track ->
                            startProgressTracker(track.id, activePlaybackRequestId.get(), track.durationSeconds)
                        }
                    } else {
                        progressJob?.cancel()
                    }
                    emitAuthoritativeState()
                }
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    if (exoPlayer.playbackState != Player.STATE_READY) return
                    for (group in tracks.groups) {
                        if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.isSelected) {
                            for (i in 0 until group.length) {
                                if (group.isTrackSelected(i)) {
                                    val format = group.getTrackFormat(i)
                                    updateCurrentTrackRealFormat(format)
                                }
                            }
                        }
                    }
                    emitAuthoritativeState()
                }
                override fun onRepeatModeChanged(repeatMode: Int) {
                    currentRepeatMode = repeatMode
                    emitAuthoritativeState()
                }
                override fun onPlaybackStateChanged(state: Int) {
                    currentPlaybackState = state
                    if (state == Player.STATE_ENDED) {
                        networkRetryCount = 0
                        bufferingWatchdogJob?.cancel()
                        currentLoadingType = PlaybackLoadingType.NONE
                        onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
                        val elapsedSinceAuto = System.currentTimeMillis() - lastAutoTransitionTimestamp
                        if (elapsedSinceAuto > 1200L) {
                            if (exoPlayer.mediaItemCount <= 1 || exoPlayer.currentMediaItemIndex >= exoPlayer.mediaItemCount - 1) {
                                onCompletionCallback?.invoke()
                            }
                        }
                        HardwareVisualizerBuffer.onPlaybackStateChanged(AudioPlaybackState.STOPPED)
                        com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.onPlaybackStateChanged(AudioPlaybackState.STOPPED)
                    } else if (state == Player.STATE_BUFFERING) {
                        val loading = if (isLikelyLocalTrack(currentPlayingTrack)) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING
                        currentLoadingType = loading
                        onLoadingStateCallback?.invoke(loading)
                        HardwareVisualizerBuffer.onPlaybackStateChanged(AudioPlaybackState.BUFFERING)
                        com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.onPlaybackStateChanged(AudioPlaybackState.BUFFERING)
                        // Active Watchdog to break out of hanging local storage / network buffering loops
                        bufferingWatchdogJob?.cancel()
                        bufferingWatchdogJob = scope.safeLaunch("BufferingWatchdog", Dispatchers.Main) {
                            kotlinx.coroutines.delay(4500L)
                            if (currentPlaybackState == Player.STATE_BUFFERING && (isPlayingState || playWhenReadyState)) {
                                Log.w(TAG, "STATE_BUFFERING watchdog timed out (4.5s). Nudging player position to break stall...")
                                withPlayer {
                                    val curPos = currentPosition
                                    seekTo(curPos)
                                    play()
                                }
                                kotlinx.coroutines.delay(2500L)
                                if (currentPlaybackState == Player.STATE_BUFFERING) {
                                    Log.w(TAG, "Still buffering after seek nudge. Clearing stuck loading state.")
                                    synchronized(playerLock) {
                                        currentLoadingType = PlaybackLoadingType.NONE
                                        emitAuthoritativeState()
                                    }
                                    onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
                                }
                            } else if (currentPlaybackState == Player.STATE_BUFFERING) {
                                synchronized(playerLock) {
                                    currentLoadingType = PlaybackLoadingType.NONE
                                    emitAuthoritativeState()
                                }
                                onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
                            }
                        }
                    } else if (state == Player.STATE_READY) {
                        bufferingWatchdogJob?.cancel()
                        currentLoadingType = PlaybackLoadingType.NONE
                        onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
                        val analysisState = if (isPlayingState) AudioPlaybackState.PLAYING else AudioPlaybackState.PAUSED
                        HardwareVisualizerBuffer.onPlaybackStateChanged(analysisState)
                        com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.onPlaybackStateChanged(analysisState)
                        for (group in exoPlayer.currentTracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.isSelected) {
                                for (i in 0 until group.length) {
                                    if (group.isTrackSelected(i)) {
                                        val format = group.getTrackFormat(i)
                                        updateCurrentTrackRealFormat(format)
                                    }
                                }
                            }
                        }
                    } else if (state == Player.STATE_IDLE) {
                        bufferingWatchdogJob?.cancel()
                        if (currentPlayJob == null || currentPlayJob?.isActive != true) {
                            currentLoadingType = PlaybackLoadingType.NONE
                            onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
                        }
                        HardwareVisualizerBuffer.onPlaybackStateChanged(AudioPlaybackState.STOPPED)
                        com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.onPlaybackStateChanged(AudioPlaybackState.STOPPED)
                    }
                    emitAuthoritativeState()
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    playWhenReadyState = playWhenReady
                    Log.d("AudioPlayerManager", "onPlayWhenReadyChanged: playWhenReady=$playWhenReady, reason=$reason")
                    if (!playWhenReady) {
                        when (reason) {
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                                Log.d("AudioPlayerManager", "Audio focus lost. Pausing playback dynamically.")
                                isPlayingState = false
                                HardwareVisualizerBuffer.onPlaybackStateChanged(AudioPlaybackState.PAUSED)
                                com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.onPlaybackStateChanged(AudioPlaybackState.PAUSED)
                            }
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                                Log.d("AudioPlayerManager", "Audio became noisy (headphones unplugged). Pausing playback.")
                                isPlayingState = false
                                HardwareVisualizerBuffer.onPlaybackStateChanged(AudioPlaybackState.PAUSED)
                                com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.onPlaybackStateChanged(AudioPlaybackState.PAUSED)
                            }
                        }
                    }
                    emitAuthoritativeState()
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("AudioPlayerManager", "Player Error: ${error.errorCodeName} (Code: ${error.errorCode})", error)
                    com.anuj.obsidian.ObsidianCrashLogger.logPlaybackError(error)
                    HardwareVisualizerBuffer.onPlaybackStateChanged(AudioPlaybackState.ERROR)
                    com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.onPlaybackStateChanged(AudioPlaybackState.ERROR)

                    val errTrack = currentPlayingTrack
                    val isAlacPotential = errTrack != null && (
                        errTrack.format.equals("ALAC", true) ||
                        errTrack.localFilePath.contains(".alac", true) ||
                        errTrack.localFilePath.contains(".m4a", true) ||
                        errTrack.localFilePath.contains(".caf", true) ||
                        com.anuj.obsidian.util.AlacAudioDecoder.isAlacFile(context, errTrack.localFilePath)
                    )
                    if (errTrack != null && attemptedAlacFallbackTrackId != errTrack.id && isAlacPotential) {
                        attemptedAlacFallbackTrackId = errTrack.id
                        val errorRequestId = activePlaybackRequestId.get()
                        Log.w("AudioPlayerManager", "Playback error on potential ALAC/M4A track ${errTrack.id}. Attempting ALAC to WAV software fallback decode...")
                        scope.safeLaunch("AudioPlayerManagerAlacFallback", Dispatchers.IO) {
                            val wav = com.anuj.obsidian.util.AlacAudioDecoder.decodeAlacToWav(context, errTrack.localFilePath)
                            if (activePlaybackRequestId.get() != errorRequestId) {
                                // Request stale, user moved on
                                if (wav != null && wav.exists()) {
                                    wav.delete()
                                }
                                return@safeLaunch
                            }
                            if (wav != null && wav.exists() && wav.length() > 44) {
                                withContext(Dispatchers.Main) {
                                    if (activePlaybackRequestId.get() != errorRequestId) return@withContext
                                    val item = buildMediaItem(errTrack, Uri.fromFile(wav))
                                    withPlayer {
                                        setMediaItem(item)
                                        prepare()
                                        play()
                                    }
                                }
                            } else {
                                if (wav != null && wav.exists()) {
                                    wav.delete()
                                }
                                withContext(Dispatchers.Main) {
                                    if (activePlaybackRequestId.get() != errorRequestId) return@withContext
                                    Log.e("AudioPlayerManager", "ALAC fallback WAV conversion failed or invalid for track ${errTrack.id}. Proceeding to standard error handling.")
                                    onErrorCallback?.invoke()
                                }
                            }
                        }
                        return
                    }

                    val isAudioTrackWriteFailed = error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                            error.message?.contains("-6") == true ||
                            error.cause?.message?.contains("-6") == true ||
                            error.message?.contains("DEAD_OBJECT", ignoreCase = true) == true

                    if (isAudioTrackWriteFailed) {
                        Log.w("AudioPlayerManager", "AudioTrack write failure (-6 DEAD_OBJECT) detected. Resetting AudioTrack and resuming...")
                        var currentPos = 0L
                        withPlayer { currentPos = currentPosition }
                        val curTrack = errTrack ?: currentPlayingTrack
                        val errorRequestId = activePlaybackRequestId.get()

                        scope.safeLaunch("AudioTrackReset", Dispatchers.Main) {
                            synchronized(playerLock) {
                                if (isReleased || activePlaybackRequestId.get() != errorRequestId) return@safeLaunch
                            }
                            if (curTrack != null) {
                                withPlayer {
                                    stop()
                                    clearMediaItems()
                                    val uriToUse = if (curTrack.localFilePath.startsWith("http://") || curTrack.localFilePath.startsWith("https://") || curTrack.localFilePath.startsWith("content://")) {
                                        Uri.parse(curTrack.localFilePath)
                                    } else {
                                        Uri.fromFile(File(curTrack.localFilePath))
                                    }
                                    val item = buildMediaItem(curTrack, uriToUse)
                                    setMediaItem(item)
                                    prepare()
                                    seekTo(currentPos)
                                    play()
                                }
                            }
                        }
                        return
                    }

                    val is404Error = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                            error.message?.contains("404") == true ||
                            error.cause?.message?.contains("404") == true

                    if (is404Error && errTrack != null) {
                        Log.w("AudioPlayerManager", "HTTP 404 stream error for track ${errTrack.id}. Recovering via FlacStreamBufferManager...")
                        val errorRequestId = activePlaybackRequestId.get()
                        scope.safeLaunch("AudioPlayerManager404Recovery", Dispatchers.IO) {
                            val recoveredFile = File(context.cacheDir, "recovered_stream_${errTrack.id}.flac")
                            val resultFile = try {
                                com.anuj.obsidian.data.remote.FlacStreamBufferManager.streamAndRecoverFullAudio(
                                    client = MusicApiService.client,
                                    streamUrl = errTrack.localFilePath,
                                    destinationFile = recoveredFile,
                                    trackTitle = errTrack.title,
                                    trackArtist = errTrack.artist,
                                    trackAlbum = errTrack.album,
                                    durationSeconds = errTrack.durationSeconds
                                )
                            } catch (e: Exception) {
                                null
                            }
                            if (activePlaybackRequestId.get() == errorRequestId && resultFile != null && resultFile.exists() && resultFile.length() > 0) {
                                withContext(Dispatchers.Main) {
                                    if (activePlaybackRequestId.get() != errorRequestId) return@withContext
                                    val item = buildMediaItem(errTrack, Uri.fromFile(resultFile))
                                    withPlayer {
                                        setMediaItem(item)
                                        prepare()
                                        play()
                                    }
                                }
                            } else if (activePlaybackRequestId.get() == errorRequestId) {
                                withContext(Dispatchers.Main) {
                                    onErrorCallback?.invoke()
                                }
                            }
                        }
                        return
                    }

                    val isNetworkError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    
                    if (isNetworkError && networkRetryCount < maxNetworkRetries) {
                        networkRetryCount++
                        var currentPos = 0L
                        withPlayer { currentPos = currentPosition }
                        Log.w("AudioPlayerManager", "Transient network exception detected. Retrying playback ($networkRetryCount/$maxNetworkRetries) from position $currentPos ms...")
                        
                        val errorRequestId = activePlaybackRequestId.get()
                        val errorTrackId = currentPlayingTrack?.id
                        
                        scope.safeLaunch("AudioPlayerManagerRetry", Dispatchers.Main) {
                            kotlinx.coroutines.delay(2000L * networkRetryCount) // exponential backoff
                            synchronized(playerLock) {
                                if (isReleased) return@safeLaunch
                                if (activePlaybackRequestId.get() != errorRequestId) return@safeLaunch
                                if (currentPlayingTrack?.id != errorTrackId) return@safeLaunch
                            }
                            withPlayer {
                                prepare()
                                seekTo(currentPos)
                                play()
                            }
                        }
                    } else {
                        networkRetryCount = 0
                        bufferingWatchdogJob?.cancel()
                        synchronized(playerLock) {
                            currentLoadingType = PlaybackLoadingType.NONE
                            emitAuthoritativeState()
                        }
                        onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
                        onErrorCallback?.invoke()
                    }
                }
            })
        }
    }

    fun setCallbacks(
        onProgress: (Float, Int) -> Unit,
        onCompletion: () -> Unit,
        onLoadingState: (PlaybackLoadingType) -> Unit,
        onQueueChanged: (List<DownloadedTrack>) -> Unit,
        onError: (() -> Unit)? = null,
        onTrackTransitioned: ((DownloadedTrack?) -> Unit)? = null,
        onTrackFormatUpdated: ((DownloadedTrack) -> Unit)? = null
    ) {
        onProgressCallback = onProgress
        onCompletionCallback = onCompletion
        onLoadingStateCallback = onLoadingState
        onQueueChangedCallback = onQueueChanged
        onErrorCallback = onError
        onTrackTransitionedCallback = onTrackTransitioned
        onTrackFormatUpdatedCallback = onTrackFormatUpdated
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        basePlaybackVolume = clamped
        withPlayer { this.volume = clamped }
    }

    private fun buildGlitchFreePlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, 65536))
            .setBufferDurationsMs(
                15000, // minBufferMs
                50000, // maxBufferMs
                1000,  // bufferForPlaybackMs
                2000   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10000, true)
            .build()
        
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(ObsidianDspEngine.pcmDspProcessor))
                    .build()
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Audio-only player: omit video hardware renderers to prevent Codec2 HAL component interface resource query errors
            }
        }
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setUserAgent("ObsidianMusic/1.0 (Android; Media3)")

        val upstreamDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val simpleCache = getSimpleCache(context)
        val dataSourceFactory = if (simpleCache != null) {
            try {
                CacheDataSource.Factory()
                    .setCache(simpleCache)
                    .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPlayerManager.buildGlitchFreePlayer - attempt")
            
                Log.e("AudioPlayerManager", "Failed to create CacheDataSource.Factory, falling back to upstream only", e)
                upstreamDataSourceFactory
            }
        } else {
            upstreamDataSourceFactory
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLooper(android.os.Looper.getMainLooper())
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        playWhenReadyState = player.playWhenReady
        currentPlaybackState = player.playbackState
            
        return player
    }

    fun buildMediaItem(entry: QueueEntry, uri: Uri): MediaItem {
        val track = entry.track
        val mediaId = entry.entryId
        val directPath = FastAlbumArtHelper.resolveArtworkPathSync(context, track)
        val resolvedArtUri = when {
            directPath.startsWith("content://") || directPath.startsWith("http://") || directPath.startsWith("https://") -> directPath
            directPath.startsWith("/") -> "file://$directPath"
            directPath.startsWith("file://") -> directPath
            track.albumArtUrl.startsWith("http://") || track.albumArtUrl.startsWith("https://") -> track.albumArtUrl
            track.albumArtUrl.startsWith("content://") -> track.albumArtUrl
            track.albumArtUrl.startsWith("file://") -> track.albumArtUrl
            track.albumArtUrl.startsWith("/") -> "file://${track.albumArtUrl}"
            else -> "obsidian://art?trackId=${track.id}&art=${android.net.Uri.encode(track.albumArtUrl)}&path=${android.net.Uri.encode(track.localFilePath)}"
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)

        try {
            metadataBuilder.setArtworkUri(android.net.Uri.parse(resolvedArtUri))
        } catch (e: Exception) {
            ObsidianCrashLogger.logWarning("AudioPlayerManager", "Failed to set artworkUri for ${track.title}: ${e.message}")
        }

        val metadata = metadataBuilder.build()
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .setTag(entry)

        if (track.isVirtualCueTrack && track.cueDurationMs > 0L) {
            val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(track.cueStartOffsetMs)
                .setEndPositionMs(track.cueStartOffsetMs + track.cueDurationMs)
                .setStartsAtKeyFrame(false)
            mediaItemBuilder.setClippingConfiguration(clipBuilder.build())
        }

        return mediaItemBuilder.build()
    }

    fun buildMediaItem(track: DownloadedTrack, uri: Uri): MediaItem {
        val existingEntry = synchronized(playerLock) {
            currentQueueEntry?.takeIf { it.track.id == track.id } ?: track.toQueueEntry()
        }
        return buildMediaItem(existingEntry, uri)
    }

    fun setGaplessConfig(enabled: Boolean, crossfadeSec: Float) {
        val changed = isGaplessEnabled != enabled
        isGaplessEnabled = enabled
        crossfadeDurationSec = crossfadeSec
        if (changed) {
            updateNextMediaItemInExoPlayer()
        }
    }

    fun setOfflineMode(enabled: Boolean) {
        isOfflineMode = enabled
    }

    fun setHeadsetConfig(autoresume: Boolean, fadeOut: Boolean, fadeOutDur: Float) {
        headsetAutoresume = autoresume
        headsetFadeOutOnDisconnect = fadeOut
        headsetFadeOutDurationSec = fadeOutDur
    }

    fun setVolumeNormalization(enabled: Boolean) {
        isVolumeNormalizationEnabled = enabled
        if (!enabled) {
            try {
                primaryLoudnessEnhancer?.enabled = false
                primaryLoudnessEnhancer?.release()
            } catch (t: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(t, "AudioPlayerManager.setVolumeNormalization.release")
            }
            primaryLoudnessEnhancer = null
        } else if (activeEnhancerSessionId > 0 && primaryLoudnessEnhancer == null && isLoudnessEnhancerSupported) {
            try {
                primaryLoudnessEnhancer = LoudnessEnhancer(activeEnhancerSessionId)
            } catch (e: Throwable) {
                isLoudnessEnhancerSupported = false
                primaryLoudnessEnhancer = null
            }
        }
        applyVolumeNormalization()
    }

    private fun getTrackReplayGainDb(track: DownloadedTrack): Float? {
        if (track.localFilePath.isBlank()) return null
        return try {
            val meta = com.anuj.obsidian.data.local.JaudiotaggerHelper.readDetailedMetadata(context, track.localFilePath)
            meta.replayGainTrackGain ?: meta.replayGainAlbumGain
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPlayerManager.getTrackReplayGainDb - attempt")
            null
        }
    }

    fun isCurrentTrackLocal(): Boolean {
        val track = currentPlayingTrack ?: return false
        if (track.localFilePath.isBlank()) return false
        val file = File(track.localFilePath)
        return file.exists() && file.length() > 0
    }

    @Volatile private var basePlaybackVolume: Float = 1.0f

    private fun applyVolumeNormalization() {
        if (!isVolumeNormalizationEnabled) {
            primaryLoudnessEnhancer?.enabled = false
            basePlaybackVolume = 1.0f
            withPlayer { volume = basePlaybackVolume }
            return
        }

        val track = currentPlayingTrack
        val q = getExplicitQueue()
        val isAlbumContext = !isShuffleEnabled() && q.isNotEmpty() && track != null && q.all { it.album == track.album }
        val multiplier = com.anuj.obsidian.audio.replaygain.ReplayGainEngine.calculateVolumeMultiplier(
            context = context,
            track = track,
            isAlbumContext = isAlbumContext
        )

        basePlaybackVolume = multiplier.coerceIn(0.05f, 2.0f)
        withPlayer { volume = basePlaybackVolume }

        val cachedGain = if (track != null) replayGainCache[track.id] else null
        if (primaryLoudnessEnhancer != null && cachedGain != null && cachedGain > 0f) {
            primaryLoudnessEnhancer?.enabled = true
            primaryLoudnessEnhancer?.setTargetGain((cachedGain * 100).toInt().coerceIn(0, 1500))
        } else {
            primaryLoudnessEnhancer?.enabled = false
        }
    }

    fun updateTrackData(track: DownloadedTrack) {
        if (currentPlayingTrack?.id == track.id) {
            currentPlayingTrack = track
            withPlayer {
                val currentItem = currentMediaItem
                if (currentItem != null && currentItem.mediaId == track.id.toString()) {
                    val updatedItem = buildMediaItem(track, currentItem.localConfiguration?.uri ?: Uri.EMPTY)
                    replaceMediaItem(currentMediaItemIndex, updatedItem)
                }
            }
        }
    }

    fun setAllLibraryTracks(tracks: List<DownloadedTrack>) {
        synchronized(playerLock) {
            allLibraryTracks = tracks.toList()
            shuffleManager.setLibraryTracks(tracks)
        }
    }

    fun setAutoplayMode(mode: com.anuj.obsidian.ui.AutoplayMode) {
        synchronized(playerLock) {
            autoplayMode = mode
        }
        withPlayer {
            repeatMode = when (mode) {
                com.anuj.obsidian.ui.AutoplayMode.REPEAT_ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
                com.anuj.obsidian.ui.AutoplayMode.REPEAT_ALL, com.anuj.obsidian.ui.AutoplayMode.AUTOPLAY_LIBRARY -> androidx.media3.common.Player.REPEAT_MODE_ALL
                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
            }
        }
    }

    fun updateLibraryContext(tracks: List<DownloadedTrack>) {
        synchronized(playerLock) {
            val currentPos = shuffleManager.getPlaybackContext().contextPosition
            shuffleManager.reconcileLibraryContext(tracks, startIndex = currentPos, shuffle = shuffleManager.isShuffleEnabled())
        }
        updateNextMediaItemInExoPlayer()
    }

    fun registerTrackForResolution(track: DownloadedTrack) {}
    fun getCachedStreamUrl(id: Long): String? = resolvedStreamUrls["id_$id"]
    
    fun getCurrentTrack(): DownloadedTrack? = synchronized(playerLock) { currentPlayingTrack }
    fun getExplicitQueueEntries(): List<QueueEntry> {
        return synchronized(playerLock) {
            shuffleManager.getExplicitQueueEntries()
        }
    }

    fun getExplicitQueue(): List<DownloadedTrack> = shuffleManager.getExplicitQueue()
    
    fun isShuffleEnabled(): Boolean = synchronized(playerLock) { shuffleManager.isShuffleEnabled() }

    fun setShuffleEnabled(enabled: Boolean) {
        synchronized(playerLock) {
            val mode = if (enabled) AdvancedShuffleMode.SHUFFLE_ALL else AdvancedShuffleMode.SHUFFLE_OFF
            shuffleManager.setAdvancedShuffleMode(mode, currentPlayingTrack)
            exoPlayer.shuffleModeEnabled = enabled
        }
        updateNextMediaItemInExoPlayer()
        currentPlayingTrack?.let { onTrackTransitionedCallback?.invoke(it) }
        emitAuthoritativeState()
    }

    fun setAdvancedShuffleMode(mode: AdvancedShuffleMode) { 
        synchronized(playerLock) {
            shuffleManager.setAdvancedShuffleMode(mode, currentPlayingTrack)
            exoPlayer.shuffleModeEnabled = (mode != AdvancedShuffleMode.SHUFFLE_OFF)
        }
        updateNextMediaItemInExoPlayer()
        currentPlayingTrack?.let { onTrackTransitionedCallback?.invoke(it) }
        emitAuthoritativeState()
    }
    fun setQueueReturnMode(mode: QueueReturnMode) {
        synchronized(playerLock) {
            shuffleManager.setQueueReturnMode(mode)
        }
        emitAuthoritativeState()
    }
    fun restoreExplicitQueue(tracks: List<DownloadedTrack>) { shuffleManager.addToExplicitQueue(tracks); onQueueChangedCallback?.invoke(getExplicitQueue()); emitAuthoritativeState() }
    fun addTracksToNextQueue(tracks: List<DownloadedTrack>) { shuffleManager.addToExplicitQueueNext(tracks); updateNextMediaItemInExoPlayer(); onQueueChangedCallback?.invoke(getExplicitQueue()); emitAuthoritativeState() }
    fun addToQueue(tracks: List<DownloadedTrack>) { shuffleManager.addToExplicitQueue(tracks); updateNextMediaItemInExoPlayer(); onQueueChangedCallback?.invoke(getExplicitQueue()); emitAuthoritativeState() }
    fun moveQueueItem(from: Int, to: Int) { shuffleManager.moveQueueItem(from, to); updateNextMediaItemInExoPlayer(); onQueueChangedCallback?.invoke(getExplicitQueue()); emitAuthoritativeState() }
    fun reorderUpcomingTrack(fromIndex: Int, toIndex: Int) { shuffleManager.reorderUpcomingTrack(fromIndex, toIndex); updateNextMediaItemInExoPlayer(); onQueueChangedCallback?.invoke(getExplicitQueue()); emitAuthoritativeState() }
    fun clearUpcomingTracks() { synchronized(playerLock) { shuffleManager.clearUpcomingTracks(); updateNextMediaItemInExoPlayer(); onQueueChangedCallback?.invoke(getExplicitQueue()) }; emitAuthoritativeState() }
    fun clearAllQueueAndUpcoming() { synchronized(playerLock) { shuffleManager.clearAllQueueAndUpcoming(); updateNextMediaItemInExoPlayer(); onQueueChangedCallback?.invoke(getExplicitQueue()) }; emitAuthoritativeState() }
    fun reshuffle() { synchronized(playerLock) { shuffleManager.reshuffle(currentPlayingTrack); updateNextMediaItemInExoPlayer(); onQueueChangedCallback?.invoke(getExplicitQueue()) }; emitAuthoritativeState() }
    fun setLibraryTracks(tracks: List<DownloadedTrack>) { synchronized(playerLock) { shuffleManager.setLibraryTracks(tracks) }; emitAuthoritativeState() }
    fun clearQueue() { shuffleManager.clearQueue(); updateNextMediaItemInExoPlayer(); onQueueChangedCallback?.invoke(getExplicitQueue()); emitAuthoritativeState() }
    fun removeFromQueue(index: Int): DownloadedTrack? { val t = shuffleManager.removeFromQueue(index); updateNextMediaItemInExoPlayer(); onQueueChangedCallback?.invoke(getExplicitQueue()); emitAuthoritativeState(); return t }
    fun playQueueIndex(index: Int): DownloadedTrack? { 
        val entry = shuffleManager.peekQueueIndex(index)
        if (entry != null) {
            internalPlayQueueEntry(entry, 0f, autoPlay = true)
        }
        emitAuthoritativeState()
        return entry?.track
    }
    fun getAdvancedShuffleMode(): AdvancedShuffleMode = shuffleManager.getShuffleTraversal().mode
    fun getQueueReturnMode(): QueueReturnMode = synchronized(playerLock) { shuffleManager.getQueueReturnMode() }
    fun shuffleExplicitQueue() {
        val q = shuffleManager.getExplicitQueue().toMutableList()
        q.shuffle()
        shuffleManager.clearQueue()
        shuffleManager.addToExplicitQueue(q)
        updateNextMediaItemInExoPlayer()
        onQueueChangedCallback?.invoke(getExplicitQueue())
        emitAuthoritativeState()
    }
    fun reshuffleUpcoming() {
        val mode = getAdvancedShuffleMode()
        if (mode != AdvancedShuffleMode.SHUFFLE_OFF) {
            shuffleManager.setAdvancedShuffleMode(mode, currentPlayingTrack)
            updateNextMediaItemInExoPlayer()
            emitAuthoritativeState()
        }
    }
    
    fun getPlaybackContext(): PlaybackContext = shuffleManager.getPlaybackContext()
    fun getShuffleTraversal(): ShuffleTraversal = shuffleManager.getShuffleTraversal()
    fun getShuffledList(): List<DownloadedTrack> = shuffleManager.getShuffledList()

    fun restoreShuffledState(tracks: List<DownloadedTrack>, index: Int) {
        synchronized(playerLock) {
            shuffleManager.restoreShuffledTracksState(tracks, index)
            updateNextMediaItemInExoPlayer()
        }
    }

    fun getAuthoritativeState(): AuthoritativePlaybackState = synchronized(playerLock) {
        val current = currentPlayingTrack
        val next = getNextTrackPreview()
        val prev = getPreviousTrackPreview()
        AuthoritativePlaybackState(
            currentTrack = current,
            currentQueueEntry = currentQueueEntry,
            currentSongId = current?.id,
            playbackContext = shuffleManager.getPlaybackContext(),
            contextPosition = shuffleManager.getPlaybackContext().contextPosition,
            shuffleEnabled = shuffleManager.isShuffleEnabled(),
            shuffleTraversal = shuffleManager.getShuffleTraversal(),
            shufflePosition = shuffleManager.getShuffleTraversal().shufflePosition,
            explicitQueue = shuffleManager.getExplicitQueueEntries(),
            isPlayingFromExplicitQueue = shuffleManager.isPlayingFromExplicitQueue(),
            repeatMode = currentRepeatMode,
            isPlaying = isPlayingState,
            currentPositionMs = exoPlayerInstance?.currentPosition ?: 0L,
            loadingType = currentLoadingType,
            previousTrack = prev,
            nextTrack = next
        )
    }

    fun getCurrentPositionMs(): Long {
        return synchronized(playerLock) {
            exoPlayerInstance?.currentPosition ?: 0L
        }
    }

    fun toggleShuffle() {
        val currentMode = getAdvancedShuffleMode()
        val newMode = when (currentMode) {
            AdvancedShuffleMode.SHUFFLE_OFF -> AdvancedShuffleMode.SHUFFLE_ALL
            AdvancedShuffleMode.SHUFFLE_ALL -> AdvancedShuffleMode.SHUFFLE_CATEGORIES
            AdvancedShuffleMode.SHUFFLE_CATEGORIES -> AdvancedShuffleMode.SHUFFLE_TRACKS
            AdvancedShuffleMode.SHUFFLE_TRACKS -> AdvancedShuffleMode.SHUFFLE_OFF
            else -> AdvancedShuffleMode.SHUFFLE_OFF
        }
        setAdvancedShuffleMode(newMode)
    }

    fun getQueuePositionInfo(): Pair<Int, Int> {
        synchronized(playerLock) {
            val cur = currentPlayingTrack
            val list = shuffleManager.getLibraryContext()
            if (cur == null || list.isEmpty()) return Pair(1, 1)
            val index = list.indexOfFirst { it.id == cur.id }
            val displayIdx = if (index >= 0) index + 1 else 1
            val total = list.size.coerceAtLeast(1)
            return Pair(displayIdx, total)
        }
    }
    fun getUpcomingQueueEntries(maxCount: Int = 100): List<QueueEntry> {
        return synchronized(playerLock) {
            shuffleManager.getUpcomingQueueEntries(maxCount)
        }
    }


    fun getUpcomingTracks(maxCount: Int = 100): List<DownloadedTrack> {
        synchronized(playerLock) {
            return shuffleManager.getUpcomingTracks(maxCount)
        }
    }

    fun playUpcomingTrack(track: DownloadedTrack) {
        synchronized(playerLock) {
            val idx = shuffleManager.getPlaybackContext().indexOfTrack(track.id)
            if (idx >= 0) {
                shuffleManager.setPlaybackContext(shuffleManager.getPlaybackContext(), idx, shuffleManager.isShuffleEnabled(), shuffleManager.getShuffleTraversal().mode)
            }
        }
        internalPlayTrack(track, 0f, autoPlay = true, skipHistoryPush = true)
    }





    fun registerDirectStreamUrl(id: Long, url: String) {
        if (url.isNotBlank()) resolvedStreamUrls["id_$id"] = url
    }

    fun registerDirectStreamUrl(track: DownloadedTrack, url: String) {
        if (url.isNotBlank()) resolvedStreamUrls[getStreamCacheKey(track)] = url
    }

    fun playContext(
        context: PlaybackContext,
        startIndex: Int = 0,
        startPositionSec: Float = 0f,
        isShuffle: Boolean = false,
        repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
        autoPlay: Boolean = true,
        forceReshuffle: Boolean = false
    ) {
        if (context.isEmpty) return
        val safeIndex = if (startIndex in context.tracks.indices) startIndex else 0
        val track = context.tracks.getOrNull(safeIndex) ?: return
        synchronized(playerLock) {
            shuffleManager.setPlaybackContext(
                context = context,
                startIndex = safeIndex,
                shuffle = isShuffle,
                shuffleMode = if (isShuffle) AdvancedShuffleMode.SHUFFLE_ALL else AdvancedShuffleMode.SHUFFLE_OFF,
                forceReshuffle = forceReshuffle
            )
        }
        withPlayer {
            this.repeatMode = repeatMode
            this.shuffleModeEnabled = false
        }
        internalPlayTrack(track, startPositionSec, autoPlay = autoPlay, skipHistoryPush = true)
        emitAuthoritativeState()
    }

    fun playPlaylist(
        tracks: List<DownloadedTrack>,
        startIndex: Int = 0,
        startPositionSec: Float = 0f,
        isShuffle: Boolean = false,
        repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
        autoPlay: Boolean = true,
        sourceType: PlaybackSourceType = PlaybackSourceType.PLAYLIST,
        sourceId: String = "",
        sourceTitle: String = "",
        forceReshuffle: Boolean = false
    ) {
        val context = PlaybackContext(
            sourceType = sourceType,
            sourceId = sourceId,
            sourceTitle = sourceTitle,
            tracks = tracks,
            contextPosition = startIndex
        )
        playContext(context, startIndex, startPositionSec, isShuffle, repeatMode, autoPlay, forceReshuffle)
    }

    fun playTrack(track: DownloadedTrack, directStreamUrl: String = "", contextTracks: List<DownloadedTrack>? = null, sourceType: PlaybackSourceType = PlaybackSourceType.ALL_SONGS, sourceId: String = "") {
        if (directStreamUrl.isNotBlank()) registerDirectStreamUrl(track, directStreamUrl)
        val selectedEntry: QueueEntry = synchronized(playerLock) {
            if (contextTracks != null) {
                val idx = contextTracks.indexOfFirst { isSameTrack(it, track) }.coerceAtLeast(0)
                val context = createNewPlaybackContext(
                    sourceType = sourceType,
                    sourceId = sourceId,
                    tracks = contextTracks,
                    contextPosition = idx
                )
                shuffleManager.setPlaybackContext(context, idx, shuffleManager.isShuffleEnabled(), shuffleManager.getShuffleTraversal().mode, forceReshuffle = true)
                context.entries.getOrNull(idx) ?: track.toQueueEntry()
            } else {
                shuffleManager.getPlaybackContext().entries.find { isSameTrack(it.track, track) } ?: track.toQueueEntry()
            }
        }
        internalPlayQueueEntry(selectedEntry, 0f, autoPlay = true)
    }

    private fun commitPlaybackTransition(requestId: Long, entry: QueueEntry, autoPlay: Boolean = true, startPos: Float = 0f) {
        synchronized(playerLock) {
            if (activePlaybackRequestId.get() != requestId) {
                Log.d(TAG, "commitPlaybackTransition: Stale request $requestId (current is ${activePlaybackRequestId.get()}), ignoring")
                return
            }

            val track = entry.track
            currentQueueEntry = entry
            currentPlayingTrack = track

            // Commit transition in shuffle manager (records history exactly once)
            shuffleManager.commitEntryTransition(entry)

            isPlayingState = autoPlay
            playWhenReadyState = autoPlay

            updateNextMediaItemInExoPlayer(requestId)
            val isExoReady = exoPlayerInstance?.playbackState == Player.STATE_READY
            if (isExoReady) {
                currentLoadingType = PlaybackLoadingType.NONE
                onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
            }
            onTrackTransitionedCallback?.invoke(track)
            onQueueChangedCallback?.invoke(getExplicitQueue())
            if (startPos > 0f) {
                onProgressCallback?.invoke(startPos, track.durationSeconds)
            }
            if (autoPlay) {
                startProgressTracker(track.id, requestId, track.durationSeconds)
            } else {
                progressJob?.cancel()
            }

            consecutiveFailures = 0
            emitAuthoritativeState()
            Log.d(TAG, "commitPlaybackTransition SUCCESS: '${track.title}' (entryId=${entry.entryId}), reqId=$requestId, autoPlay=$autoPlay, startPos=$startPos")
        }
    }

    private fun internalPlayQueueEntry(entry: QueueEntry, startPos: Float, autoPlay: Boolean = true) {
        val requestId = activePlaybackRequestId.incrementAndGet()
        val track = entry.track
        val isLikelyLocal = isLikelyLocalTrack(track)
        val initialLoading = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING
        
        synchronized(playerLock) {
            currentPlayJob?.cancel()
            isPlayingState = autoPlay
            playWhenReadyState = autoPlay
            currentLoadingType = initialLoading
            
            // ONE-TAP: Update track state immediately for UI responsiveness
            currentQueueEntry = entry
            currentPlayingTrack = entry.track
            emitAuthoritativeState()
        }

        onLoadingStateCallback?.invoke(initialLoading)

        // CRITICAL: Stop previous audio immediately so previous song stops playing while resolving the new song
        withPlayer {
            stop()
            clearMediaItems()
        }
        
        Log.d(TAG, "internalPlayQueueEntry ENTRY: track='${track.title}' (entryId=${entry.entryId}), reqId=$requestId, activeReqId=${activePlaybackRequestId.get()}")

        val job = scope.safeLaunch("AudioPlayerManagerPlay") {
            try {
                val loading = if (isLikelyLocal) PlaybackLoadingType.LOCAL_STORAGE_IO else PlaybackLoadingType.NETWORK_BUFFERING
                withContext(Dispatchers.Main) {
                    if (activePlaybackRequestId.get() != requestId) return@withContext
                    synchronized(playerLock) {
                        currentLoadingType = loading
                    }
                    onLoadingStateCallback?.invoke(loading)
                }

                val uri = withContext(Dispatchers.IO) { resolveTrackMediaUri(track) }
                ensureActive()
                if (activePlaybackRequestId.get() != requestId) {
                    Log.d(TAG, "internalPlayQueueEntry STALE (after URI resolve): track='${track.title}', reqId=$requestId, activeReqId=${activePlaybackRequestId.get()}")
                    return@safeLaunch
                }
                if (uri == null) {
                    Log.e(TAG, "Failed to resolve Media URI for track: ${track.title}")
                    withContext(Dispatchers.Main) {
                        if (activePlaybackRequestId.get() == requestId) {
                            synchronized(playerLock) {
                                currentLoadingType = PlaybackLoadingType.NONE
                                emitAuthoritativeState()
                            }
                            onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
                            onErrorCallback?.invoke()
                            withPlayer {
                                stop()
                                clearMediaItems()
                            }
                        }
                    }
                    return@safeLaunch
                }

                val item = buildMediaItem(entry, uri)
                
                withContext(Dispatchers.Main) {
                    ensureActive()
                    if (isReleased) return@withContext
                    if (activePlaybackRequestId.get() != requestId) {
                        Log.d(TAG, "internalPlayQueueEntry STALE (before main sync): track='${track.title}', reqId=$requestId, activeReqId=${activePlaybackRequestId.get()}")
                        return@withContext
                    }

                    var sessionId = 0
                    withPlayerSuspend {
                        if (activePlaybackRequestId.get() != requestId) {
                            Log.d(TAG, "internalPlayQueueEntry STALE (inside withPlayerSuspend): track='${track.title}', reqId=$requestId, activeReqId=${activePlaybackRequestId.get()}")
                            return@withPlayerSuspend
                        }
                        Log.d(TAG, "internalPlayQueueEntry setMediaItem: track='${track.title}', reqId=$requestId, activeReqId=${activePlaybackRequestId.get()}")
                        setMediaItem(item)
                        prepare()
                        if (startPos > 0) seekTo((startPos * 1000).toLong())
                        if (autoPlay) {
                            playWhenReady = true
                            play()
                            ensureServiceStarted()
                        } else {
                            playWhenReady = false
                            pause()
                        }
                        sessionId = audioSessionId
                    }
                    
                    if (activePlaybackRequestId.get() != requestId) {
                        Log.d(TAG, "internalPlayQueueEntry STALE (after setMediaItem): track='${track.title}', reqId=$requestId, activeReqId=${activePlaybackRequestId.get()}")
                        return@withContext
                    }

                    // Strict Transactional Commit: Only after Media3 setMediaItem and prepare succeeded
                    commitPlaybackTransition(requestId, entry, autoPlay, startPos)
                    setupPrimaryEnhancer(sessionId)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "internalPlayQueueEntry CANCELLED: track='${track.title}', reqId=$requestId")
                throw e
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "AudioPlayerManager.internalPlayQueueEntry - attempt")
                Log.e(TAG, "Playback Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (activePlaybackRequestId.get() == requestId) {
                        synchronized(playerLock) {
                            currentLoadingType = PlaybackLoadingType.NONE
                            emitAuthoritativeState()
                        }
                        onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
                        withPlayer {
                            stop()
                            clearMediaItems()
                        }
                        consecutiveFailures++
                        if (autoPlay) {
                            if (consecutiveFailures <= 2) {
                                kotlinx.coroutines.delay(500)
                                if (activePlaybackRequestId.get() == requestId) {
                                    skipToNext()
                                }
                            } else {
                                Log.e(TAG, "Consecutive playback failures reached limit, stopping auto-skip.")
                                consecutiveFailures = 0
                                pause()
                            }
                        }
                    }
                }
            }
        }
        synchronized(playerLock) {
            currentPlayJob = job
        }
    }

    private fun internalPlayTrack(track: DownloadedTrack, startPos: Float, autoPlay: Boolean = true, skipHistoryPush: Boolean = false) {
        val existingEntry = synchronized(playerLock) {
            val curEntry = currentQueueEntry
            if (curEntry != null && isSameTrack(curEntry.track, track)) {
                curEntry
            } else {
                shuffleManager.getPlaybackContext().entries.firstOrNull { isSameTrack(it.track, track) }
                    ?: track.toQueueEntry()
            }
        }
        internalPlayQueueEntry(existingEntry, startPos, autoPlay)
    }

    private suspend fun resolveTrackMediaUri(track: DownloadedTrack): Uri? {
        val path = track.localFilePath.trim()

        // 1. Fully-downloaded local file on disk or direct URI
        if (path.isNotBlank() && !path.endsWith(".downloading")) {
            if (path.startsWith("content://") || path.startsWith("file://") || path.startsWith("android.resource://") || path.startsWith("asset://")) {
                if (com.anuj.obsidian.util.AlacAudioDecoder.isAlacFile(context, path) || track.format.equals("ALAC", true)) {
                    val decoded = com.anuj.obsidian.util.AlacAudioDecoder.decodeAlacToWav(context, path)
                    if (decoded != null && decoded.exists()) return Uri.fromFile(decoded)
                }
                return Uri.parse(path)
            } else if (path.startsWith("/")) {
                val f = File(path)
                if (f.exists() && f.length() > 0L) {
                    if (com.anuj.obsidian.util.AlacAudioDecoder.isAlacFile(context, path) || track.format.equals("ALAC", true)) {
                        val decoded = com.anuj.obsidian.util.AlacAudioDecoder.decodeAlacToWav(context, path)
                        if (decoded != null && decoded.exists()) return Uri.fromFile(decoded)
                    }
                    return Uri.fromFile(f)
                }
                // Try querying MediaStore by DATA path in case of scoped storage
                try {
                    val cursor = context.contentResolver.query(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(android.provider.MediaStore.Audio.Media._ID),
                        "${android.provider.MediaStore.Audio.Media.DATA} = ?",
                        arrayOf(path),
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val mediaId = it.getLong(0)
                            return android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed resolving MediaStore query for path $path: ${e.message}")
                }
            }
        }

        // Check MediaStore content URI by ID if positive
        if (track.id > 0L) {
            val mediaStoreUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
            try {
                context.contentResolver.openFileDescriptor(mediaStoreUri, "r")?.use {
                    val uriStr = mediaStoreUri.toString()
                    if (com.anuj.obsidian.util.AlacAudioDecoder.isAlacFile(context, uriStr) || track.format.equals("ALAC", true)) {
                        val decoded = com.anuj.obsidian.util.AlacAudioDecoder.decodeAlacToWav(context, uriStr)
                        if (decoded != null && decoded.exists()) return Uri.fromFile(decoded)
                    }
                    return mediaStoreUri
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed opening MediaStore URI $mediaStoreUri: ${e.message}")
            }
        }

        // 2. Direct HTTP Stream URL from track.localFilePath
        if (path.startsWith("http://") || path.startsWith("https://")) {
            if (MusicApiService.isSafeFullLengthUrl(path)) {
                return Uri.parse(path)
            }
        }

        // 3. Registered or Cached Direct Stream URL
        val cacheKey = getStreamCacheKey(track)
        val cached = resolvedStreamUrls[cacheKey] ?: if (track.id > 0L) resolvedStreamUrls["id_${track.id}"] else null
        if (!cached.isNullOrBlank()) {
            if (cached.startsWith("http://") || cached.startsWith("https://")) {
                if (MusicApiService.isSafeFullLengthUrl(cached)) {
                    return Uri.parse(cached)
                }
            } else {
                val cachedFile = File(cached)
                if (cachedFile.exists() && !cachedFile.name.endsWith(".downloading") && com.anuj.obsidian.util.MediaFileValidator.validate(cachedFile).isStructurallyValid) {
                    return Uri.fromFile(cachedFile)
                }
            }
        }

        // 4. Active Progressive Download Session: Check temp file with data or stream URL
        val activeSession = com.anuj.obsidian.data.model.ProgressiveDownloadSessionRegistry.findSession(track.id, track.title, track.artist)
        if (activeSession != null) {
            if (activeSession.tempFile.exists() && com.anuj.obsidian.util.MediaFileValidator.validate(activeSession.tempFile).isStructurallyValid) {
                return Uri.fromFile(activeSession.tempFile)
            }
            if (activeSession.sourceStreamUrl.isNotBlank() && MusicApiService.isSafeFullLengthUrl(activeSession.sourceStreamUrl)) {
                resolvedStreamUrls[cacheKey] = activeSession.sourceStreamUrl
                return Uri.parse(activeSession.sourceStreamUrl)
            }
        }

        // 5. Fetch Stream URL Online if not offline
        if (!isOfflineMode) {
            try {
                val fetched = kotlinx.coroutines.withTimeoutOrNull(3500L) {
                    MusicApiService.resolveFullLengthStreamUrl(
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationSeconds = track.durationSeconds.coerceAtLeast(180),
                        providerSource = track.providerSource
                    )
                }
                if (!fetched.isNullOrBlank() && MusicApiService.isSafeFullLengthUrl(fetched)) {
                    resolvedStreamUrls[cacheKey] = fetched
                    return Uri.parse(fetched)
                }
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPlayerManager.resolveTrackMediaUri - fetch attempt")
                Log.w("AudioPlayerManager", "Stream resolution fallback note: ${e.message}")
            }
        }

        // 6. Resilient Master Buffer Fallback
        val bufferDir = File(context.cacheDir, "StreamMasterBuffer").apply { mkdirs() }
        val targetCache = File(bufferDir, "stream_${track.id}.wav")
        if (targetCache.exists()) {
            val validation = com.anuj.obsidian.util.MediaFileValidator.validate(targetCache)
            if (validation.isStructurallyValid) {
                resolvedStreamUrls[cacheKey] = targetCache.absolutePath
                return Uri.fromFile(targetCache)
            }
        }

        Log.e("AudioPlayerManager", "Failed to resolve valid audio stream for: ${track.title} (${track.id})")
        return null
    }

    fun getNextEntryPreview(): QueueEntry? {
        synchronized(playerLock) {
            val effectiveRepeat = if (currentRepeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else currentRepeatMode
            val entry = shuffleManager.peekNextEntry(effectiveRepeat, currentQueueEntry)
            if (entry != null) return entry
            if (effectiveRepeat == Player.REPEAT_MODE_ALL) {
                return shuffleManager.getPlaybackContext().entries.firstOrNull()
            }
            if (autoplayMode == AutoplayMode.AUTOPLAY_LIBRARY) {
                val lib = shuffleManager.getLibraryContext()
                val nextTrack = lib.firstOrNull { it.id != currentPlayingTrack?.id } ?: allLibraryTracks.firstOrNull { it.id != currentPlayingTrack?.id }
                return nextTrack?.toQueueEntry()
            }
            return null
        }
    }

    private fun updateNextMediaItemInExoPlayer(expectedRequestId: Long? = null) {
        val currentPlayId = expectedRequestId ?: activePlaybackRequestId.get()
        val nextEntry = getNextEntryPreview()
        val nextTrack = nextEntry?.track
        val requestId = activePrefetchRequestId.incrementAndGet()
        prefetchNextJob?.cancel()

        // Synchronously purge any existing next media item if it doesn't match the new target nextTrack
        withPlayer {
            val count = mediaItemCount
            val currentIndex = currentMediaItemIndex
            if (count > currentIndex + 1) {
                val existingNextEntry = getMediaItemAt(currentIndex + 1).localConfiguration?.tag as? QueueEntry
                val existingNextTrack = existingNextEntry?.track ?: (getMediaItemAt(currentIndex + 1).localConfiguration?.tag as? DownloadedTrack)
                if (existingNextTrack == null || existingNextTrack.id != nextTrack?.id) {
                    for (i in count - 1 downTo currentIndex + 1) {
                        removeMediaItem(i)
                    }
                }
            }
        }

        if (nextEntry == null || nextTrack == null || !isGaplessEnabled) {
            return
        }

        prefetchNextJob = scope.safeLaunch("AudioPlayerManagerPrefetch") {
            try {
                val uri = withContext(Dispatchers.IO) { resolveTrackMediaUri(nextTrack) } ?: return@safeLaunch
                ensureActive()
                if (activePrefetchRequestId.get() != requestId || activePlaybackRequestId.get() != currentPlayId) return@safeLaunch
                
                val nextItem = buildMediaItem(nextEntry, uri)
                
                withContext(Dispatchers.Main) {
                    ensureActive()
                    if (activePrefetchRequestId.get() != requestId || activePlaybackRequestId.get() != currentPlayId) return@withContext
                    withPlayerSuspend {
                        val count = mediaItemCount
                        val currentIndex = currentMediaItemIndex
                        if (count > 0 && currentIndex in 0 until count) {
                            if (count > currentIndex + 1) {
                                replaceMediaItem(currentIndex + 1, nextItem)
                                for (i in count - 1 downTo currentIndex + 2) {
                                    removeMediaItem(i)
                                }
                            } else {
                                addMediaItem(nextItem)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPlayerManager.updateNextMediaItemInExoPlayer - attempt")
                // Ignore prefetch errors
            }
        }
    }

    fun updateCurrentTrack(track: DownloadedTrack) {
        synchronized(playerLock) {
            if (currentPlayingTrack?.title.equals(track.title, ignoreCase = true)) {
                currentPlayingTrack = track
            }
        }
    }

    private fun updateCurrentTrackRealFormat(format: androidx.media3.common.Format) {
        // Only publish the "real" format once confirmed stable in Player.STATE_READY
        if (exoPlayer.playbackState != Player.STATE_READY) return
        val sampleMime = format.sampleMimeType ?: ""
        if (sampleMime.isBlank() || sampleMime.equals("audio/none", ignoreCase = true)) return

        val track = currentPlayingTrack ?: return
        val currentTag = exoPlayer.currentMediaItem?.localConfiguration?.tag as? DownloadedTrack
        if (currentTag != null && currentTag.id != track.id) return

        val sampleRateHz = format.sampleRate
        if (sampleRateHz <= 0 && format.bitrate <= 0 && format.pcmEncoding == androidx.media3.common.Format.NO_VALUE) {
            return
        }

        val realSampleRate = if (sampleRateHz > 0) {
            if (sampleRateHz >= 1000) String.format(java.util.Locale.US, "%.1f kHz", sampleRateHz / 1000.0)
            else "$sampleRateHz Hz"
        } else track.sampleRate

        val rawBitrate = format.bitrate
        val localFile = if (track.localFilePath.isNotBlank() && !track.localFilePath.startsWith("http")) java.io.File(track.localFilePath) else null
        val fileSizeBytes = if (localFile != null && localFile.exists() && localFile.length() > 0) {
            localFile.length()
        } else if (track.fileSizeMb > 0f) {
            (track.fileSizeMb * 1024f * 1024f).toLong()
        } else 0L

        val durationSec = if (track.durationSeconds > 0) track.durationSeconds else (exoPlayer.duration / 1000).toInt()

        val realBitrateKbps = if (fileSizeBytes > 0 && durationSec > 0) {
            ((fileSizeBytes * 8L) / durationSec / 1000L).toInt()
        } else if (rawBitrate > 0) {
            rawBitrate / 1000
        } else if (sampleRateHz > 0 && format.pcmEncoding != androidx.media3.common.Format.NO_VALUE) {
            val bits = when (format.pcmEncoding) {
                androidx.media3.common.C.ENCODING_PCM_16BIT -> 16
                androidx.media3.common.C.ENCODING_PCM_24BIT -> 24
                androidx.media3.common.C.ENCODING_PCM_32BIT, androidx.media3.common.C.ENCODING_PCM_FLOAT -> 32
                else -> 16
            }
            val channels = if (format.channelCount > 0) format.channelCount else 2
            (sampleRateHz.toLong() * bits * channels / 1000L).toInt()
        } else track.bitrateKbps

        val isAlacTrack = track.format.equals("ALAC", true) || com.anuj.obsidian.util.AlacAudioDecoder.isAlacFile(context, track.localFilePath)

        val realFormat = when {
            isAlacTrack -> "ALAC"
            sampleMime.contains("flac", ignoreCase = true) -> "FLAC"
            sampleMime.contains("alac", ignoreCase = true) -> "ALAC"
            sampleMime.contains("raw", ignoreCase = true) || sampleMime.contains("wav", ignoreCase = true) -> if (isAlacTrack) "ALAC" else "WAV"
            sampleMime.contains("opus", ignoreCase = true) -> "OPUS"
            sampleMime.contains("vorbis", ignoreCase = true) || sampleMime.contains("ogg", ignoreCase = true) -> "OGG"
            sampleMime.contains("mpeg", ignoreCase = true) || sampleMime.contains("mp3", ignoreCase = true) -> "MP3"
            sampleMime.contains("mp4a", ignoreCase = true) || sampleMime.contains("aac", ignoreCase = true) -> "M4A"
            track.format.isNotBlank() && !track.format.equals("UNKNOWN", true) -> track.format
            else -> "MP3"
        }

        val isLossless = realFormat.equals("FLAC", true) || realFormat.equals("ALAC", true) || realFormat.equals("WAV", true)
        val realBitDepth = if (isLossless) {
            when (format.pcmEncoding) {
                androidx.media3.common.C.ENCODING_PCM_16BIT -> "16-bit"
                androidx.media3.common.C.ENCODING_PCM_24BIT -> "24-bit"
                androidx.media3.common.C.ENCODING_PCM_32BIT -> "32-bit"
                androidx.media3.common.C.ENCODING_PCM_FLOAT -> "32-bit Float"
                else -> track.bitDepth.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) } ?: "16-bit"
            }
        } else {
            "" // Lossy compressed formats have no bit depth
        }

        if (track.sampleRate != realSampleRate || track.bitrateKbps != realBitrateKbps || track.bitDepth != realBitDepth || track.format != realFormat) {
            val updatedTrack = track.copy(
                sampleRate = realSampleRate,
                bitrateKbps = realBitrateKbps,
                bitDepth = realBitDepth,
                format = realFormat
            )
            synchronized(playerLock) {
                currentPlayingTrack = updatedTrack
            }
            onTrackFormatUpdatedCallback?.invoke(updatedTrack)
        }
    }

    private fun handleAutoTransition() {
        lastAutoTransitionTimestamp = System.currentTimeMillis()
        val mediaItem = exoPlayer.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId
        val entryTag = mediaItem.localConfiguration?.tag as? QueueEntry
        
        val targetEntry = synchronized(playerLock) {
            entryTag 
                ?: shuffleManager.findQueueEntryByEntryId(mediaId)
                ?: (mediaItem.localConfiguration?.tag as? DownloadedTrack)?.let { shuffleManager.findQueueEntryByEntryId(currentQueueEntry?.entryId ?: "") }
                ?: shuffleManager.peekNextEntry(currentRepeatMode, currentQueueEntry)
        }

        if (targetEntry != null) {
            val requestId = activePlaybackRequestId.incrementAndGet()
            commitPlaybackTransition(requestId, targetEntry)
        }
    }

    fun getNextTrackPreview(): DownloadedTrack? {
        synchronized(playerLock) {
            val effectiveRepeat = if (currentRepeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else currentRepeatMode
            val entry = shuffleManager.peekNextEntry(effectiveRepeat, currentQueueEntry)
            if (entry != null) return entry.track
            if (effectiveRepeat == Player.REPEAT_MODE_ALL) {
                return shuffleManager.getPlaybackContext().tracks.firstOrNull()
            }
            if (autoplayMode == AutoplayMode.AUTOPLAY_LIBRARY) {
                val lib = shuffleManager.getLibraryContext()
                return lib.firstOrNull { it.id != currentPlayingTrack?.id } ?: allLibraryTracks.firstOrNull { it.id != currentPlayingTrack?.id }
            }
            return null
        }
    }

    fun getPreviousTrackPreview(): DownloadedTrack? {
        synchronized(playerLock) {
            val effectiveRepeat = if (currentRepeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else currentRepeatMode
            val entry = shuffleManager.peekPreviousEntry(effectiveRepeat, currentQueueEntry)
            if (entry == null && (autoplayMode == AutoplayMode.REPEAT_ALL || autoplayMode == AutoplayMode.AUTOPLAY_LIBRARY)) {
                return shuffleManager.getLibraryContext().lastOrNull()
            }
            return entry?.track
        }
    }

    fun skipToNext() {
        if (isReleased) return
        val effectiveRepeat = if (currentRepeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else currentRepeatMode
        val nextEntry = synchronized(playerLock) {
            shuffleManager.peekNextEntry(effectiveRepeat, currentQueueEntry)
        }
        if (nextEntry != null) {
            internalPlayQueueEntry(nextEntry, 0f, autoPlay = true)
        } else if (effectiveRepeat == Player.REPEAT_MODE_ALL) {
            val wrapEntry = synchronized(playerLock) {
                shuffleManager.getPlaybackContext().entries.firstOrNull()
            }
            if (wrapEntry != null) {
                internalPlayQueueEntry(wrapEntry, 0f, autoPlay = true)
            } else {
                withPlayer { pause() }
            }
        } else if (autoplayMode == AutoplayMode.AUTOPLAY_LIBRARY) {
            val lib = synchronized(playerLock) { shuffleManager.getLibraryContext() }
            val nextLibTrack = lib.firstOrNull { it.id != currentPlayingTrack?.id }
                ?: allLibraryTracks.firstOrNull { it.id != currentPlayingTrack?.id }
            if (nextLibTrack != null) {
                playTrack(nextLibTrack)
            } else {
                withPlayer { pause() }
            }
        } else {
            withPlayer { pause() }
        }
    }

    fun skipToPrevious(forceSkip: Boolean = false) {
        if (isReleased) return

        val currentPosMs = synchronized(playerLock) { exoPlayerInstance?.currentPosition ?: 0L }

        if (!forceSkip && currentPosMs > 3000L) {
            withPlayer {
                seekTo(0L)
                play()
            }
            return
        }

        val effectiveRepeat = if (currentRepeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else currentRepeatMode
        val prevEntry = synchronized(playerLock) {
            shuffleManager.peekPreviousEntry(effectiveRepeat, currentQueueEntry)
        }
        
        if (prevEntry != null) {
            internalPlayQueueEntry(prevEntry, 0f, autoPlay = true)
        } else if (effectiveRepeat == Player.REPEAT_MODE_ALL) {
            val wrapEntry = synchronized(playerLock) {
                shuffleManager.getPlaybackContext().entries.lastOrNull()
            }
            if (wrapEntry != null) {
                internalPlayQueueEntry(wrapEntry, 0f, autoPlay = true)
            } else {
                withPlayer {
                    seekTo(0L)
                    play()
                }
            }
        } else if (autoplayMode == AutoplayMode.REPEAT_ALL || autoplayMode == AutoplayMode.AUTOPLAY_LIBRARY) {
            val lib = synchronized(playerLock) { shuffleManager.getLibraryContext() }
            val prevLibTrack = lib.lastOrNull { it.id != currentPlayingTrack?.id }
                ?: allLibraryTracks.lastOrNull { it.id != currentPlayingTrack?.id }
            if (prevLibTrack != null) {
                playTrack(prevLibTrack)
            } else {
                withPlayer {
                    seekTo(0L)
                    play()
                }
            }
        } else {
            withPlayer {
                seekTo(0L)
                play()
            }
        }
    }

    fun skipToNextCategory(): DownloadedTrack? {
        if (isReleased) return null
        val targetEntry = synchronized(playerLock) {
            shuffleManager.planNextCategoryEntry(currentQueueEntry)
        }
        if (targetEntry != null) {
            internalPlayQueueEntry(targetEntry, 0f, autoPlay = true)
        }
        return targetEntry?.track
    }

    fun skipToPreviousCategory(): DownloadedTrack? {
        if (isReleased) return null
        val targetEntry = synchronized(playerLock) {
            shuffleManager.planPreviousCategoryEntry(currentQueueEntry)
        }
        if (targetEntry != null) {
            internalPlayQueueEntry(targetEntry, 0f, autoPlay = true)
        }
        return targetEntry?.track
    }

    fun pause() { 
        bufferingWatchdogJob?.cancel()
        withPlayer { 
            playWhenReady = false
            pause() 
            synchronized(playerLock) {
                isPlayingState = false
                playWhenReadyState = false
                currentLoadingType = PlaybackLoadingType.NONE
                progressJob?.cancel()
            }
            onLoadingStateCallback?.invoke(PlaybackLoadingType.NONE)
            emitAuthoritativeState()
        } 
    }

    fun togglePlayPause() {
        withPlayer {
            if (isPlaying) {
                pause()
            } else {
                play()
            }
        }
    }
    
    fun stopPlayback() {
        withPlayer {
            stop()
            clearMediaItems()
        }
    }

    fun play() {
        ensureServiceStarted()
        withPlayer {
            val track = currentPlayingTrack
            if (playerError != null) {
                if (track != null) {
                    resolvedStreamUrls.remove(getStreamCacheKey(track))
                    if (track.id > 0L) resolvedStreamUrls.remove("id_${track.id}")
                    internalPlayTrack(track, currentPosition / 1000f, autoPlay = true, skipHistoryPush = true)
                    return@withPlayer
                }
            }
    
            if (playbackState == Player.STATE_IDLE || mediaItemCount == 0) {
                if (track != null) {
                    val startPos = if (currentPosition > 0) currentPosition / 1000f else 0f
                    internalPlayTrack(track, startPos, autoPlay = true, skipHistoryPush = true)
                    return@withPlayer
                } else {
                    prepare()
                }
            } else if (playbackState == Player.STATE_ENDED) {
                seekTo(0)
            }
            playWhenReady = true
            play()
            synchronized(playerLock) {
                isPlayingState = true
                playWhenReadyState = true
                if (track != null) {
                    startProgressTracker(track.id, activePlaybackRequestId.get(), track.durationSeconds)
                }
            }
            emitAuthoritativeState()
        }
    }

    fun ensureServiceStarted() {
        try {
            val intent = android.content.Intent(context, ObsidianPlaybackService::class.java).apply {
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES or android.content.Intent.FLAG_RECEIVER_FOREGROUND
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPlayerManager.ensureServiceStarted - attempt")
            
            Log.e("AudioPlayerManager", "Failed to start ObsidianPlaybackService: ${e.message}")
        }
    }
    
    @Volatile var isUserSeeking: Boolean = false

    fun seekTo(positionSeconds: Float, isScrubbing: Boolean = false) {
        if (isScrubbing) {
            isUserSeeking = true
        }
        withPlayer {
            setSeekParameters(if (isScrubbing) androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC else androidx.media3.exoplayer.SeekParameters.EXACT)
            seekTo((positionSeconds * 1000).toLong())
            val durMs = if (duration > 0) duration else (currentPlayingTrack?.durationSeconds ?: 0) * 1000L
            if (!isScrubbing) {
                isUserSeeking = false
                onProgressCallback?.invoke(positionSeconds, (durMs / 1000L).toInt())
            }
        }
    }
    
    fun setPlaybackSpeed(speed: Float) {
        withPlayer { playbackParameters = PlaybackParameters(speed, 1.0f) }
    }

    private fun startProgressTracker(trackId: Long, requestId: Long, fallbackDurationSec: Int) {
        progressJob?.cancel()
        progressJob = scope.safeLaunch("AudioPlayerManagerProgress", Dispatchers.Main) {
            while (isActive) {
                if (activePlaybackRequestId.get() != requestId || currentPlayingTrack?.id != trackId) {
                    return@safeLaunch
                }
                withPlayer {
                    if (isPlaying) {
                        val currentMs = currentPosition
                        val durMs = if (duration > 0) duration else fallbackDurationSec * 1000L
                        if (!isUserSeeking) {
                            onProgressCallback?.invoke(currentMs / 1000f, (durMs / 1000L).toInt())
                        }

                        // Crossfade volume ramp down / up logic respecting basePlaybackVolume
                        if (crossfadeDurationSec > 0f && duration > 15000L) {
                            val crossfadeMs = (crossfadeDurationSec * 1000).toLong()
                            val remMs = duration - currentMs
                            if (remMs in 0..crossfadeMs) {
                                val ratio = remMs.toFloat() / crossfadeMs.toFloat()
                                val targetVol = basePlaybackVolume * ratio.coerceIn(0.05f, 1f)
                                if (this.volume != targetVol) {
                                    this.volume = targetVol
                                }
                            } else if (currentMs in 0..crossfadeMs) {
                                val ratio = currentMs.toFloat() / crossfadeMs.toFloat()
                                val targetVol = basePlaybackVolume * ratio.coerceIn(0.05f, 1f)
                                if (this.volume != targetVol) {
                                    this.volume = targetVol
                                }
                            } else {
                                if (this.volume != basePlaybackVolume) {
                                    this.volume = basePlaybackVolume
                                }
                            }
                        } else {
                            if (this.volume != basePlaybackVolume) {
                                this.volume = basePlaybackVolume
                            }
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun setupPrimaryEnhancer(sessionId: Int) {
        if (sessionId <= 0) return
        synchronized(playerLock) {
            if (activeEnhancerSessionId == sessionId && primaryLoudnessEnhancer != null) {
                Log.d(TAG, "LoudnessEnhancer already active for session $sessionId")
                return
            }
            activeEnhancerSessionId = sessionId

            if (isVolumeNormalizationEnabled && isLoudnessEnhancerSupported) {
                try {
                    primaryLoudnessEnhancer?.release()
                    primaryLoudnessEnhancer = LoudnessEnhancer(sessionId)
                    applyVolumeNormalization()
                } catch (e: Throwable) {
                    Log.d(TAG, "LoudnessEnhancer unavailable on session $sessionId: ${e.message}")
                    isLoudnessEnhancerSupported = false
                    primaryLoudnessEnhancer = null
                }
            } else {
                try {
                    primaryLoudnessEnhancer?.enabled = false
                    primaryLoudnessEnhancer?.release()
                } catch (t: Throwable) {
                    ObsidianCrashLogger.logException(t, "AudioPlayerManager.setupPrimaryEnhancer.releaseEnhancer")
                }
                primaryLoudnessEnhancer = null
            }
        }
        
        ObsidianDspEngine.attachAudioSession(sessionId)
        HardwareVisualizerBuffer.attachAudioSession(sessionId)
        com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.attachAudioSession(sessionId)
    }

    fun insertTrackAtIndex(track: DownloadedTrack, index: Int): Boolean {
        val result = shuffleManager.insertTrackAtIndex(track, index)
        if (result) {
            updateNextMediaItemInExoPlayer()
            val cb = synchronized(playerLock) { onQueueChangedCallback }
            cb?.invoke(getExplicitQueue())
        }
        return result
    }

    fun removeTrackAtIndex(index: Int): DownloadedTrack? {
        val removed = shuffleManager.removeTrackAtIndex(index)
        if (removed != null) {
            updateNextMediaItemInExoPlayer()
            val cb = synchronized(playerLock) { onQueueChangedCallback }
            cb?.invoke(getExplicitQueue())
        }
        return removed
    }

    fun moveTrackInQueue(fromIndex: Int, toIndex: Int): Boolean {
        val result = shuffleManager.moveTrackInQueue(fromIndex, toIndex)
        if (result) {
            updateNextMediaItemInExoPlayer()
            val cb = synchronized(playerLock) { onQueueChangedCallback }
            cb?.invoke(getExplicitQueue())
        }
        return result
    }

    fun swapQueueTracks(index1: Int, index2: Int): Boolean {
        val result = shuffleManager.swapQueueTracks(index1, index2)
        if (result) {
            updateNextMediaItemInExoPlayer()
            val cb = synchronized(playerLock) { onQueueChangedCallback }
            cb?.invoke(getExplicitQueue())
        }
        return result
    }

    fun clearQueueAfterIndex(index: Int): List<DownloadedTrack> {
        val removed = shuffleManager.clearQueueAfterIndex(index)
        if (removed.isNotEmpty()) {
            updateNextMediaItemInExoPlayer()
            val cb = synchronized(playerLock) { onQueueChangedCallback }
            cb?.invoke(getExplicitQueue())
        }
        return removed
    }

    fun clearQueueAfterCurrentTrack(): List<DownloadedTrack> {
        val entry = synchronized(playerLock) { currentQueueEntry } ?: return emptyList()
        val explicitEntries = shuffleManager.getExplicitQueueEntries()
        val idx = explicitEntries.indexOfFirst { it.entryId == entry.entryId }
        return if (idx >= 0) {
            clearQueueAfterIndex(idx)
        } else {
            val removed = shuffleManager.clearQueueAfterEntry(entry)
            updateNextMediaItemInExoPlayer()
            val cb = synchronized(playerLock) { onQueueChangedCallback }
            cb?.invoke(getExplicitQueue())
            removed
        }
    }

    fun setShuffleMode(mode: ShuffleMode) {
        val current = getCurrentTrack()
        shuffleManager.setShuffleMode(mode, current)
        updateNextMediaItemInExoPlayer()
    }

    fun getPlaybackHistory(): List<PlaybackHistory> = shuffleManager.getFullPlaybackHistory()

    fun getRecentlyPlayedTracks(): List<Long> = shuffleManager.getRecentlyPlayedTracks()

    fun clearCallbacks() {
        synchronized(playerLock) {
            onProgressCallback = null
            onCompletionCallback = null
            onLoadingStateCallback = null
            onQueueChangedCallback = null
            onErrorCallback = null
            onTrackTransitionedCallback = null
        }
    }

    fun release() {
        synchronized(playerLock) {
            if (isReleased) return
            isReleased = true
            
            try {
                primaryLoudnessEnhancer?.enabled = false
                primaryLoudnessEnhancer?.release()
                primaryLoudnessEnhancer = null
            } catch (e: Throwable) {
                Log.w(TAG, "LoudnessEnhancer release error: ${e.message}")
            }

            try {
                exoPlayerInstance?.release()
                exoPlayerInstance = null
            } catch (e: Throwable) {
                ObsidianCrashLogger.logException(e, "AudioPlayerManager.release")
                Log.e("AudioPlayerManager", "Error releasing player: ${e.message}")
            }

            currentPlayJob?.cancel()
            currentPlayJob = null
            prefetchNextJob?.cancel()
            prefetchNextJob = null
            progressJob?.cancel()
            progressJob = null

            resolvedStreamUrls.clear()
            activeResolutions.clear()
            replayGainCache.clear()
        }

        clearCallbacks()
        
        ObsidianDspEngine.releaseAudioEffects()
        HardwareVisualizerBuffer.releaseHardwareVisualizer()
        com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.detachAudioSession()
        com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.onPlaybackStateChanged(AudioPlaybackState.STOPPED)
    }

    companion object {
        private const val TAG = "AudioPlayerManager"
        @Volatile
        private var simpleCacheInstance: SimpleCache? = null

        @Synchronized
        fun getSimpleCache(context: Context): SimpleCache? {
            if (simpleCacheInstance == null) {
                val cacheDir = File(context.cacheDir, "exo_media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(150 * 1024 * 1024L) // 150MB disk cache
                try {
                    val databaseProvider = StandaloneDatabaseProvider(context)
                    simpleCacheInstance = SimpleCache(cacheDir, evictor, databaseProvider)
                } catch (e: Throwable) {
                    Log.e("AudioPlayerManager", "First SimpleCache initialization attempt failed. Wiping cache and retrying...", e)
                    try {
                        if (cacheDir.exists()) {
                            cacheDir.deleteRecursively()
                        }
                        val databaseProvider = StandaloneDatabaseProvider(context)
                        simpleCacheInstance = SimpleCache(cacheDir, evictor, databaseProvider)
                    } catch (e2: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e2, "AudioPlayerManager.getSimpleCache - attempt")
            
                        Log.e("AudioPlayerManager", "Second SimpleCache initialization attempt failed. Caching is disabled.", e2)
                        return null
                    }
                }
            }
            return simpleCacheInstance
        }
    }
}

