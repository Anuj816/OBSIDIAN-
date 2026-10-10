package com.anuj.obsidian.ui.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.anuj.obsidian.MainActivity
import com.anuj.obsidian.R

class ObsidianWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val currentTrack = runCatching { com.anuj.obsidian.ObsidianApplication.playerManager.getCurrentTrack() }.getOrNull()
        val isPlaying = runCatching { com.anuj.obsidian.ObsidianApplication.playerManager.playWhenReady() }.getOrDefault(false)
        if (currentTrack != null) {
            loadTrackArtworkBitmap(context, currentTrack) { art ->
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId, art, currentTrack.artist, currentTrack.title, isPlaying)
                }
            }
        } else {
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, null, "Not Playing", "Obsidian Player", false)
            }
        }
    }

    companion object {
        fun loadTrackArtworkBitmap(
            context: Context,
            track: com.anuj.obsidian.data.model.DownloadedTrack,
            onLoaded: (Bitmap?) -> Unit
        ) {
            val resolvedArtPath = com.anuj.obsidian.data.local.FastAlbumArtHelper.resolveArtworkPathSync(context, track)
            val dataModel: Any = when {
                resolvedArtPath.isNotBlank() && java.io.File(resolvedArtPath).exists() -> java.io.File(resolvedArtPath)
                resolvedArtPath.startsWith("http://") || resolvedArtPath.startsWith("https://") -> resolvedArtPath
                resolvedArtPath.startsWith("content://") -> android.net.Uri.parse(resolvedArtPath)
                track.albumArtUrl.startsWith("http://") || track.albumArtUrl.startsWith("https://") -> track.albumArtUrl
                track.albumArtUrl.startsWith("content://") -> android.net.Uri.parse(track.albumArtUrl)
                track.albumArtUrl.startsWith("/") && java.io.File(track.albumArtUrl).exists() -> java.io.File(track.albumArtUrl)
                track.localFilePath.isNotBlank() && java.io.File(track.localFilePath).exists() -> java.io.File(track.localFilePath)
                else -> track.albumArtUrl.ifBlank { track.localFilePath }
            }

            val request = coil.request.ImageRequest.Builder(context)
                .data(dataModel)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .precision(coil.size.Precision.EXACT)
                .allowHardware(false)
                .size(256, 256)
                .target { drawable ->
                    val bmp = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    onLoaded(bmp)
                }
                .build()
            coil.Coil.imageLoader(context).enqueue(request)
        }

        fun updateAllWidgets(context: Context, title: String, artist: String, isPlaying: Boolean, art: Bitmap?) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ObsidianWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id, art, artist, title, isPlaying)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            art: Bitmap?,
            artist: String,
            title: String,
            isPlaying: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_obsidian)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            
            if (art != null) {
                views.setImageViewBitmap(R.id.widget_art, art)
            } else {
                views.setImageViewResource(R.id.widget_art, android.R.drawable.ic_media_play)
            }

            views.setImageViewResource(
                R.id.widget_btn_play,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            // Intents
            val mainIntent = Intent(context, MainActivity::class.java)
            val pendingMain = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_container, pendingMain)

            views.setOnClickPendingIntent(
                R.id.widget_btn_play,
                getServicePendingIntent(context, ObsidianPlaybackService.ACTION_PLAY_PAUSE, 1)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                getServicePendingIntent(context, ObsidianPlaybackService.ACTION_PREVIOUS, 2)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                getServicePendingIntent(context, ObsidianPlaybackService.ACTION_NEXT, 3)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getServicePendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, ObsidianPlaybackService::class.java).apply {
                this.action = action
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, reqCode, intent, flags)
            } else {
                PendingIntent.getService(context, reqCode, intent, flags)
            }
        }
    }
}
