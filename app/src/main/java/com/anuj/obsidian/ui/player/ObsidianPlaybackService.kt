package com.anuj.obsidian.ui.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil.imageLoader
import coil.request.ImageRequest
import com.anuj.obsidian.MainActivity
import com.anuj.obsidian.R
import com.anuj.obsidian.ObsidianApplication
import com.anuj.obsidian.data.local.FastAlbumArtHelper
import com.anuj.obsidian.data.model.DownloadedTrack
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * =========================================================================================
 * Obsidian Playback Service (Media3 MediaSessionService)
 * =========================================================================================
 * EXPORTED STATUS: android:exported="true"
 * REASON:
 * Media3 MediaSessionService requires exported="true" so external system clients (Android Auto,
 * System Media Controls in notification shade/lock screen, Bluetooth AVRCP hardware headsets,
 * Google Assistant voice search intents, and WearOS) can bind to the service via the standard
 * `androidx.media3.session.MediaSessionService` and `MediaBrowserService` intent actions.
 *
 * SECURITY MODEL:
 * - Direct intent communication is constrained to standard Media3 Session commands and internal actions.
 * - MediaController access is gated through `onGetSession(controllerInfo)`, which enforces MediaSession
 *   token validation before returning the active player session instance.
 * =========================================================================================
 */
@OptIn(UnstableApi::class)
class ObsidianPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var customSessionCallback: CustomSessionCallback? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    companion object {
        const val ACTION_CLOSE_NOTIFICATION = "ACTION_CLOSE_NOTIFICATION"
        const val ACTION_CLOSE_APP = "com.anuj.obsidian.action.CLOSE_APP"
        const val ACTION_STOP = "com.anuj.obsidian.action.STOP"
        const val ACTION_PLAY_PAUSE = "com.anuj.obsidian.action.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.anuj.obsidian.action.PREVIOUS"
        const val ACTION_NEXT = "com.anuj.obsidian.action.NEXT"
        const val ACTION_SHUFFLE = "com.anuj.obsidian.action.SHUFFLE"
        const val ACTION_REPEAT = "com.anuj.obsidian.action.REPEAT"
        const val ACTION_FAST_FORWARD = "com.anuj.obsidian.action.FAST_FORWARD"
        const val ACTION_REWIND = "com.anuj.obsidian.action.REWIND"
        const val ACTION_SEEK_TO = "com.anuj.obsidian.action.SEEK_TO"
        const val DEFAULT_NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "obsidian_playback_channel"
        
        var actionCallback: ((String) -> Unit)? = null
        @Volatile
        var instance: ObsidianPlaybackService? = null

        fun updateNotification() {
            instance?.forceUpdateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        createNotificationChannel()
        
        // Initialize WakeLock
        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Obsidian:PlaybackWakeLock").apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackService.onCreate - attempt")
            
            android.util.Log.e("ObsidianPlaybackService", "Failed to create WakeLock", e)
        }
        
        val player = ObsidianApplication.playerManager.exoPlayer
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationProvider = MediaNotificationManager(this)
        setMediaNotificationProvider(notificationProvider)

        val customLayout = getCustomLayoutButtons()

        val callback = CustomSessionCallback()
        customSessionCallback = callback

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(ObsidianBitmapLoader())
            .setCallback(callback)
            .setCustomLayout(customLayout)
            .build()

        mediaSession?.setCustomLayout(customLayout)
    }

    fun getCustomLayoutButtons(): List<CommandButton> {
        val closeCommand = CommandButton.Builder()
            .setSessionCommand(SessionCommand(ACTION_CLOSE_NOTIFICATION, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_notif_close)
            .setDisplayName("Close")
            .setEnabled(true)
            .build()

        val shuffleCommand = CommandButton.Builder()
            .setSessionCommand(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_notif_shuffle_on)
            .setDisplayName("Shuffle")
            .setEnabled(true)
            .build()

        return listOf(closeCommand, shuffleCommand)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val playerManager = ObsidianApplication.playerManager
        val isPlaying = playerManager.isPlaying()
        if (!isPlaying) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                stopSelf()
            }
        }
        // If actively playing, do nothing: let the foreground media service continue running uninterrupted
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Acquire WakeLock when starting playback commands
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours timeout limit
            }
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackService.onStartCommand - attempt")
            
            android.util.Log.e("ObsidianPlaybackService", "Failed to acquire WakeLock", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notification = buildInitialMediaNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        DEFAULT_NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(DEFAULT_NOTIFICATION_ID, notification)
                }
            } catch (e: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackService.onStartCommand - attempt")
                android.util.Log.e("ObsidianPlaybackService", "Failed to start foreground service", e)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        intent?.action?.let { action ->
            when (action) {
                ACTION_STOP, ACTION_CLOSE_NOTIFICATION -> {
                    dismissNotificationAndPause()
                }
                ACTION_PLAY_PAUSE -> {
                    ObsidianApplication.playerManager.togglePlayPause()
                    forceUpdateNotification()
                }
                ACTION_PREVIOUS -> {
                    ObsidianApplication.playerManager.skipToPrevious()
                    forceUpdateNotification()
                }
                ACTION_NEXT -> {
                    ObsidianApplication.playerManager.skipToNext()
                    forceUpdateNotification()
                }
                ACTION_FAST_FORWARD -> {
                    ObsidianApplication.playerManager.withPlayer {
                        val newPos = (currentPosition + 10000L).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
                        seekTo(newPos)
                    }
                }
                ACTION_REWIND -> {
                    ObsidianApplication.playerManager.withPlayer {
                        val newPos = (currentPosition - 10000L).coerceAtLeast(0L)
                        seekTo(newPos)
                    }
                }
            }
            actionCallback?.invoke(action)
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun forceUpdateNotification() {
        val session = mediaSession ?: return
        try {
            val provider = MediaNotificationManager(this)
            val notif = provider.buildNotification(session)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(DEFAULT_NOTIFICATION_ID, notif.notification)
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackService.forceUpdateNotification - attempt")
        }
    }

    fun dismissNotificationAndPause() {
        ObsidianApplication.playerManager.pause()
        
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackService.dismissNotificationAndPause - attempt")
            
            android.util.Log.e("ObsidianPlaybackService", "Failed to release WakeLock inside dismissNotificationAndPause", e)
        }

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(DEFAULT_NOTIFICATION_ID)

            // Broadcast close app event to terminate activity and close app completely
            try {
                val closeAppIntent = Intent(ACTION_CLOSE_APP).apply {
                    setPackage(packageName)
                }
                sendBroadcast(closeAppIntent)
            } catch (e: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackService.broadcastCloseApp - attempt")
            }

            stopSelf()
        }
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        actionCallback = null
        ObsidianApplication.playerManager.pause()
        
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackService.onDestroy - attempt")
            
            android.util.Log.e("ObsidianPlaybackService", "Failed to release WakeLock inside onDestroy", e)
        }

        customSessionCallback?.cleanup()
        customSessionCallback = null
        mediaSession?.run {
            release()
        }
        mediaSession = null
        com.anuj.obsidian.ui.visualizer.HardwareVisualizerBuffer.detachAudioSession()
        com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.detachAudioSession()
        com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.onPlaybackStateChanged(com.anuj.obsidian.ui.visualizer.AudioPlaybackState.STOPPED)
        com.anuj.obsidian.ui.dsp.ObsidianDspEngine.releaseAudioEffects()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class CustomSessionCallback : MediaSession.Callback {
        private var mediaButtonTapCount = 0
        private var lastMediaButtonTapTime = 0L
        private val mediaButtonHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private val mediaButtonRunnable = Runnable {
            val count = mediaButtonTapCount
            mediaButtonTapCount = 0
            val playerManager = ObsidianApplication.playerManager
            when (count) {
                1 -> playerManager.togglePlayPause()
                2 -> playerManager.skipToNext()
                3 -> playerManager.skipToPrevious()
            }
        }

        fun cleanup() {
            mediaButtonHandler.removeCallbacksAndMessages(null)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent != null && keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                val keyCode = keyEvent.keyCode
                if (keyCode == android.view.KeyEvent.KEYCODE_HEADSETHOOK || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    val now = System.currentTimeMillis()
                    if (now - lastMediaButtonTapTime > 450) {
                        mediaButtonTapCount = 0
                    }
                    mediaButtonTapCount++
                    lastMediaButtonTapTime = now
                    mediaButtonHandler.removeCallbacks(mediaButtonRunnable)
                    mediaButtonHandler.postDelayed(mediaButtonRunnable, 350)
                    return true
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(ACTION_CLOSE_NOTIFICATION, Bundle.EMPTY))
                .add(SessionCommand(ACTION_CLOSE_APP, Bundle.EMPTY))
                .add(SessionCommand(ACTION_STOP, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_FAST_FORWARD, Bundle.EMPTY))
                .add(SessionCommand(ACTION_REWIND, Bundle.EMPTY))
                .build()

            val customLayout = getCustomLayoutButtons()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(connectionResult.availablePlayerCommands)
                .setCustomLayout(customLayout)
                .build()
        }

        @Deprecated("Deprecated in MediaSession.Callback")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onPostConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            super.onPostConnect(session, controller)
            session.setCustomLayout(controller, getCustomLayoutButtons())
        }

        @Deprecated("Deprecated in MediaSession.Callback", ReplaceWith("onPlayerCommandRequest"))
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_STOP -> {
                    serviceScope.launch(Dispatchers.Main) {
                        dismissNotificationAndPause()
                    }
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SET_SHUFFLE_MODE -> {
                    serviceScope.launch(Dispatchers.Main) {
                        val current = ObsidianApplication.playerManager.isShuffleEnabled()
                        ObsidianApplication.playerManager.setShuffleEnabled(!current)
                    }
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    serviceScope.launch(Dispatchers.Main) {
                        ObsidianApplication.playerManager.skipToNext()
                    }
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    serviceScope.launch(Dispatchers.Main) {
                        ObsidianApplication.playerManager.skipToPrevious()
                    }
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        @Suppress("DEPRECATION")
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SHUFFLE -> {
                    val playerManager = ObsidianApplication.playerManager
                    val current = playerManager.isShuffleEnabled()
                    playerManager.setShuffleEnabled(!current)
                    return SettableFuture.create<SessionResult>().apply {
                        set(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                ACTION_CLOSE_NOTIFICATION, ACTION_STOP, ACTION_CLOSE_APP -> {
                    dismissNotificationAndPause()
                    return SettableFuture.create<SessionResult>().apply {
                        set(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                ACTION_FAST_FORWARD -> {
                    val playerManager = ObsidianApplication.playerManager
                    playerManager.withPlayer {
                        val currentPos = currentPosition
                        val dur = duration
                        val newPos = (currentPos + 10000L).coerceAtMost(if (dur > 0) dur else Long.MAX_VALUE)
                        seekTo(newPos)
                    }
                    return SettableFuture.create<SessionResult>().apply {
                        set(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                ACTION_REWIND -> {
                    val playerManager = ObsidianApplication.playerManager
                    playerManager.withPlayer {
                        val currentPos = currentPosition
                        val newPos = (currentPos - 10000L).coerceAtLeast(0L)
                        seekTo(newPos)
                    }
                    return SettableFuture.create<SessionResult>().apply {
                        set(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    @OptIn(UnstableApi::class)
    private inner class ObsidianBitmapLoader : BitmapLoader {
        override fun supportsMimeType(mimeType: String): Boolean = true

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
                
                val reqWidth = 1024
                val reqHeight = 1024
                var inSampleSize = 1
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                        inSampleSize *= 2
                    }
                }
                
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
                if (bitmap != null && !bitmap.isRecycled) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalArgumentException("Decode failed"))
                }
            } catch (e: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackService.decodeBitmap - attempt")
                future.setException(e)
            }
            return future
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()

            serviceScope.launch(Dispatchers.IO) {
                try {
                    val trackId = uri.getQueryParameter("trackId")?.toLongOrNull() ?: 0L
                    val artUrl = uri.getQueryParameter("art") ?: uri.toString()
                    val filePath = uri.getQueryParameter("path") ?: ""

                    val tempTrack = DownloadedTrack(
                        id = trackId,
                        title = "",
                        artist = "",
                        album = "",
                        durationSeconds = 0,
                        albumArtUrl = artUrl,
                        localFilePath = filePath
                    )

                    val fastBmp = FastAlbumArtHelper.getArtworkBitmap(this@ObsidianPlaybackService, tempTrack, targetSize = 720)
                    if (fastBmp != null && !fastBmp.isRecycled) {
                        future.set(fastBmp)
                        return@launch
                    }

                    val resolvedArtPath = FastAlbumArtHelper.resolveArtworkPathSync(this@ObsidianPlaybackService, tempTrack)
                    val dataModel: Any = when {
                        resolvedArtPath.isNotBlank() && java.io.File(resolvedArtPath).exists() -> java.io.File(resolvedArtPath)
                        resolvedArtPath.startsWith("http://") || resolvedArtPath.startsWith("https://") -> resolvedArtPath
                        resolvedArtPath.startsWith("content://") -> Uri.parse(resolvedArtPath)
                        artUrl.startsWith("http://") || artUrl.startsWith("https://") -> artUrl
                        artUrl.startsWith("content://") -> Uri.parse(artUrl)
                        artUrl.startsWith("/") && java.io.File(artUrl).exists() -> java.io.File(artUrl)
                        filePath.isNotBlank() && java.io.File(filePath).exists() -> java.io.File(filePath)
                        else -> artUrl.ifBlank { filePath }
                    }

                    val imageLoader = this@ObsidianPlaybackService.imageLoader
                    val request = ImageRequest.Builder(this@ObsidianPlaybackService)
                        .data(dataModel)
                        .size(720, 720)
                        .allowHardware(false)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .precision(coil.size.Precision.INEXACT)
                        .crossfade(false)
                        .build()

                    val result = imageLoader.execute(request)
                    val drawable = result.drawable
                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    
                    if (bitmap != null && !bitmap.isRecycled) {
                        future.set(bitmap)
                    } else {
                        future.setException(Exception("Artwork decode returned null"))
                    }
                } catch (e: Throwable) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackService.loadBitmap - attempt")
                    future.setException(e)
                }
            }
            return future
        }
    }

    private fun buildInitialMediaNotification(): android.app.Notification {
        val currentTrack = ObsidianApplication.playerManager.getCurrentTrack()
        val title = currentTrack?.title ?: "Obsidian Player"
        val artist = currentTrack?.artist ?: "Preparing playback..."
        val album = currentTrack?.album ?: ""

        val artistSubtitle = if (album.isNotBlank() && album != "Unknown Album" && album != artist) {
            "$artist • $album"
        } else {
            artist
        }

        fun getServicePendingIntent(intent: Intent, reqCode: Int): PendingIntent {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(this, reqCode, intent, flags)
            } else {
                PendingIntent.getService(this, reqCode, intent, flags)
            }
        }

        val prevIntent = Intent(this, ObsidianPlaybackService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPendingIntent = getServicePendingIntent(prevIntent, 101)

        val playPauseIntent = Intent(this, ObsidianPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = getServicePendingIntent(playPauseIntent, 102)

        val nextIntent = Intent(this, ObsidianPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = getServicePendingIntent(nextIntent, 103)

        val closeIntent = Intent(this, ObsidianPlaybackService::class.java).apply { action = ACTION_CLOSE_NOTIFICATION }
        val closePendingIntent = getServicePendingIntent(closeIntent, 104)

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingActivityIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPlaying = ObsidianApplication.playerManager.playWhenReady()
        val playPauseIcon = if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_obsidian)
            .setContentTitle(title)
            .setContentText(artistSubtitle)
            .setContentIntent(pendingActivityIntent)
            .setDeleteIntent(closePendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_notif_prev, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, "Play/Pause", playPausePendingIntent)
            .addAction(R.drawable.ic_notif_next, "Next", nextPendingIntent)
            .addAction(R.drawable.ic_notif_close, "Close App", closePendingIntent)

        val mediaSessionInstance = mediaSession
        val mediaStyle = if (mediaSessionInstance != null) {
            androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSessionInstance)
                .setShowActionsInCompactView(0, 1, 2)
        } else {
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
        }

        builder.setStyle(mediaStyle)

        if (currentTrack != null) {
            val bmp = FastAlbumArtHelper.getArtworkBitmap(this, currentTrack, targetSize = 720)
            if (bmp != null && !bmp.isRecycled) {
                builder.setLargeIcon(bmp)
            }
        }

        return builder.build()
    }
}
