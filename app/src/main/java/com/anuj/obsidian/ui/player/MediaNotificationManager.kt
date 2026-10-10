package com.anuj.obsidian.ui.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import coil.imageLoader
import coil.request.ImageRequest
import com.anuj.obsidian.MainActivity
import com.anuj.obsidian.R
import com.anuj.obsidian.ObsidianApplication
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.FastAlbumArtHelper
import com.anuj.obsidian.data.model.DownloadedTrack
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Rebuilt-from-scratch custom Notification Player for Obsidian Lossless Audio Engine.
 * Features:
 * - Pixel-perfect native MediaStyle notification matching Android System Media controls
 * - Dynamic album artwork extraction (embedded tags, cached files, MediaStore, and Coil)
 * - Live dynamic queue counter (e.g. Obsidian • 1/490)
 * - Large high-resolution artwork banner / right card thumbnail
 * - Direct responsive controls: Previous, Play/Pause, Next, and Dismiss (X)
 */
@OptIn(UnstableApi::class)
class MediaNotificationManager(
    private val context: Context
) : MediaNotification.Provider {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var currentTrackId: Long? = null
    private var currentBitmap: Bitmap? = null
    private var imageLoadJob: Job? = null
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    private var activeCallback: MediaNotification.Provider.Callback? = null
    private var lastKnownTitle: String? = null
    private var lastKnownArtist: String? = null
    private var lastKnownAlbum: String? = null

    init {
        createNotificationChannel()
    }

    fun release() {
        imageLoadJob?.cancel()
        imageLoadJob = null
        activeCallback = null
        currentBitmap = null
        currentTrackId = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Obsidian Audio Engine Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Obsidian Lossless Audio Playback and Interactive Controls"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        activeCallback = onNotificationChangedCallback
        
        val currentTrack = ObsidianApplication.playerManager.getCurrentTrack()
        val trackId = currentTrack?.id

        if (trackId != currentTrackId) {
            currentTrackId = trackId
            imageLoadJob?.cancel()

            // Synchronous fast check for memory cached or direct file artwork
            if (currentTrack != null) {
                val memKey = "art_${currentTrack.id}_${currentTrack.albumArtUrl.hashCode()}_${currentTrack.localFilePath.hashCode()}"
                currentBitmap = FastAlbumArtHelper.getMemoryCachedBitmap(memKey)

                imageLoadJob = scope.launch {
                    try {
                        val asyncBmp = withContext(Dispatchers.IO) {
                            FastAlbumArtHelper.getArtworkBitmap(context, currentTrack, targetSize = 720)
                        }

                        if (trackId != currentTrackId) return@launch

                        if (asyncBmp != null && !asyncBmp.isRecycled) {
                            currentBitmap = asyncBmp
                            val updatedNotif = buildNotification(mediaSession)
                            activeCallback?.onNotificationChanged(updatedNotif)
                            try {
                                notificationManager.notify(NOTIFICATION_ID, updatedNotif.notification)
                            } catch (e: SecurityException) {
                                ObsidianCrashLogger.logWarning("MediaNotificationManager", "POST_NOTIFICATIONS permission not granted: ${e.message}")
                            } catch (e: Exception) {
                                ObsidianCrashLogger.logException(e, "MediaNotificationManager.notify failed")
                            }
                            ObsidianWidgetProvider.updateAllWidgets(
                                context = context,
                                title = currentTrack.title,
                                artist = currentTrack.artist,
                                isPlaying = ObsidianApplication.playerManager.playWhenReady(),
                                art = asyncBmp
                            )
                            return@launch
                        }

                        // Fallback to Coil image loader for remote / complex URI loading
                        val resolvedArtPath = FastAlbumArtHelper.resolveArtworkPathSync(context, currentTrack)
                        val dataModel: Any = when {
                            resolvedArtPath.isNotBlank() && java.io.File(resolvedArtPath).exists() -> java.io.File(resolvedArtPath)
                            resolvedArtPath.startsWith("http://") || resolvedArtPath.startsWith("https://") -> resolvedArtPath
                            resolvedArtPath.startsWith("content://") -> Uri.parse(resolvedArtPath)
                            currentTrack.albumArtUrl.startsWith("http://") || currentTrack.albumArtUrl.startsWith("https://") -> currentTrack.albumArtUrl
                            currentTrack.albumArtUrl.startsWith("content://") -> Uri.parse(currentTrack.albumArtUrl)
                            currentTrack.albumArtUrl.startsWith("/") && java.io.File(currentTrack.albumArtUrl).exists() -> java.io.File(currentTrack.albumArtUrl)
                            currentTrack.localFilePath.isNotBlank() && java.io.File(currentTrack.localFilePath).exists() -> java.io.File(currentTrack.localFilePath)
                            else -> currentTrack.albumArtUrl.ifBlank { currentTrack.localFilePath }
                        }

                        val request = ImageRequest.Builder(context)
                            .data(dataModel)
                            .size(720, 720)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .precision(coil.size.Precision.INEXACT)
                            .crossfade(false)
                            .build()

                        val result = withContext(Dispatchers.IO) {
                            context.imageLoader.execute(request)
                        }
                        if (trackId != currentTrackId) return@launch
                        val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bmp != null && !bmp.isRecycled) {
                            currentBitmap = bmp
                            FastAlbumArtHelper.putMemoryCachedBitmap(memKey, bmp)
                            val updatedNotif = buildNotification(mediaSession)
                            activeCallback?.onNotificationChanged(updatedNotif)
                            try {
                                notificationManager.notify(NOTIFICATION_ID, updatedNotif.notification)
                            } catch (e: SecurityException) {
                                ObsidianCrashLogger.logWarning("MediaNotificationManager", "POST_NOTIFICATIONS permission not granted: ${e.message}")
                            } catch (e: Exception) {
                                ObsidianCrashLogger.logException(e, "MediaNotificationManager.notify failed")
                            }
                            ObsidianWidgetProvider.updateAllWidgets(
                                context = context,
                                title = currentTrack.title,
                                artist = currentTrack.artist,
                                isPlaying = ObsidianApplication.playerManager.playWhenReady(),
                                art = bmp
                            )
                        }
                    } catch (e: Throwable) {
                        com.anuj.obsidian.ObsidianCrashLogger.logException(e, "MediaNotificationManager.createNotification - attempt")
                    }
                }
            } else {
                currentBitmap = null
            }
        }
        return buildNotification(mediaSession)
    }

    fun buildNotification(mediaSession: MediaSession): MediaNotification {
        val currentTrack = ObsidianApplication.playerManager.getCurrentTrack()

        if (currentTrack != null) {
            lastKnownTitle = currentTrack.title
            lastKnownArtist = currentTrack.artist
            lastKnownAlbum = currentTrack.album
        }

        val title = lastKnownTitle ?: "Obsidian Player"
        val rawArtist = lastKnownArtist ?: "Preparing playback..."
        val rawAlbum = lastKnownAlbum ?: ""

        val artistSubtitle = if (!rawAlbum.isNullOrBlank() && rawAlbum != "Unknown Album" && rawAlbum != rawArtist) {
            "$rawArtist • $rawAlbum"
        } else {
            rawArtist
        }

        val player = mediaSession.player
        val isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_READY)

        // Calculate queue position info: Obsidian • 1/490
        val (queueIndex, queueTotal) = ObsidianApplication.playerManager.getQueuePositionInfo()
        val headerText = if (queueTotal > 1) {
            "Obsidian • $queueIndex/$queueTotal"
        } else {
            "Obsidian • Lossless Audio"
        }

        // Direct Service PendingIntents for instantaneous control
        fun getServicePendingIntent(intent: Intent, reqCode: Int): PendingIntent {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, reqCode, intent, flags)
            } else {
                PendingIntent.getService(context, reqCode, intent, flags)
            }
        }

        val prevIntent = Intent(context, ObsidianPlaybackService::class.java).apply {
            action = ObsidianPlaybackService.ACTION_PREVIOUS
        }
        val prevPendingIntent = getServicePendingIntent(prevIntent, 101)

        val playPauseIntent = Intent(context, ObsidianPlaybackService::class.java).apply {
            action = ObsidianPlaybackService.ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = getServicePendingIntent(playPauseIntent, 102)

        val nextIntent = Intent(context, ObsidianPlaybackService::class.java).apply {
            action = ObsidianPlaybackService.ACTION_NEXT
        }
        val nextPendingIntent = getServicePendingIntent(nextIntent, 103)

        val closeIntent = Intent(context, ObsidianPlaybackService::class.java).apply {
            action = ObsidianPlaybackService.ACTION_CLOSE_NOTIFICATION
        }
        val closePendingIntent = getServicePendingIntent(closeIntent, 104)

        // Activity Tap Intent
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingActivityIntent = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        var bmp = currentBitmap?.takeIf { !it.isRecycled }
        if (bmp == null && currentTrack != null) {
            bmp = FastAlbumArtHelper.getArtworkBitmap(context, currentTrack, targetSize = 256)
            if (bmp != null) {
                currentBitmap = bmp
            }
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play

        val currentPosMs = player.currentPosition

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_obsidian)
            .setContentTitle(title)
            .setContentText(artistSubtitle)
            .setSubText(headerText)
            .setContentIntent(pendingActivityIntent)
            .setDeleteIntent(closePendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setWhen(if (isPlaying) System.currentTimeMillis() - currentPosMs else 0L)
            .setShowWhen(isPlaying)
            .setUsesChronometer(isPlaying)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_notif_prev, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            .addAction(R.drawable.ic_notif_next, "Next", nextPendingIntent)
            .addAction(R.drawable.ic_notif_close, "Close App", closePendingIntent)

        // Native MediaStyle Integration
        val mediaStyle = androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowActionsInCompactView(0, 1, 2)

        builder.setStyle(mediaStyle)

        if (bmp != null && !bmp.isRecycled) {
            builder.setLargeIcon(bmp)
        }

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
        return when (action) {
            ObsidianPlaybackService.ACTION_CLOSE_NOTIFICATION, ObsidianPlaybackService.ACTION_STOP -> {
                (context as? ObsidianPlaybackService)?.dismissNotificationAndPause() 
                    ?: run {
                        val intent = Intent(context, ObsidianPlaybackService::class.java).apply {
                            this.action = ObsidianPlaybackService.ACTION_CLOSE_NOTIFICATION
                        }
                        context.startService(intent)
                    }
                true
            }
            ObsidianPlaybackService.ACTION_SHUFFLE -> {
                val playerManager = ObsidianApplication.playerManager
                val current = playerManager.isShuffleEnabled()
                playerManager.setShuffleEnabled(!current)
                true
            }
            ObsidianPlaybackService.ACTION_FAST_FORWARD -> {
                ObsidianApplication.playerManager.withPlayer {
                    val newPos = (currentPosition + 10000L).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
                    seekTo(newPos)
                }
                true
            }
            ObsidianPlaybackService.ACTION_REWIND -> {
                ObsidianApplication.playerManager.withPlayer {
                    val newPos = (currentPosition - 10000L).coerceAtLeast(0L)
                    seekTo(newPos)
                }
                true
            }
            else -> false
        }
    }

    companion object {
        const val CHANNEL_ID = "obsidian_playback_channel"
        const val NOTIFICATION_ID = ObsidianPlaybackService.DEFAULT_NOTIFICATION_ID
    }
}
