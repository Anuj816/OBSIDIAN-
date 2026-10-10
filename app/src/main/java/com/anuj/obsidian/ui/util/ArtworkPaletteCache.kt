package com.anuj.obsidian.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.anuj.obsidian.data.model.DownloadedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractedTrackColors(
    val primary: Color,
    val secondary: Color,
    val background: Color
)

object ArtworkPaletteCache {
    private val cache = LruCache<String, ExtractedTrackColors>(64)

    fun clear() {
        try {
            cache.evictAll()
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logWarning("ArtworkPaletteCache", "Failed to clear cache: ${e.message}")
        }
    }

    fun getCachedColors(track: DownloadedTrack?): ExtractedTrackColors? {
        if (track == null) return null
        return try {
            val key = "${track.id}_${track.albumArtUrl.hashCode()}_${track.localFilePath.hashCode()}"
            cache.get(key)
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logWarning("ArtworkPaletteCache", "Failed to get cached colors: ${e.message}")
            null
        }
    }

    suspend fun extractColors(
        context: Context,
        track: DownloadedTrack,
        fallbackPrimary: Color,
        fallbackSecondary: Color,
        fallbackBackground: Color = Color.Black
    ): ExtractedTrackColors {
        val key = "${track.id}_${track.albumArtUrl.hashCode()}_${track.localFilePath.hashCode()}"
        val existing = try { 
            cache.get(key) 
        } catch (e: Exception) { 
            com.anuj.obsidian.ObsidianCrashLogger.logWarning("ArtworkPaletteCache", "Failed to get existing colors: ${e.message}")
            null 
        }
        if (existing != null) {
            return existing
        }

        return withContext(Dispatchers.Default) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(track)
                    .allowHardware(false)
                    .size(64)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                        val palette = Palette.from(bitmap).generate()
                        val vibrant = palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb
                        val dark = palette.darkMutedSwatch?.rgb ?: palette.darkVibrantSwatch?.rgb

                        val prim = if (vibrant != null) Color(vibrant) else fallbackPrimary
                        val darkVibrantRgb = palette.darkVibrantSwatch?.rgb
                        val sec = if (darkVibrantRgb != null) {
                            Color(darkVibrantRgb)
                        } else if (vibrant != null) {
                            Color(vibrant)
                        } else fallbackSecondary
                        val bg = if (dark != null) Color(dark) else fallbackBackground

                        val extracted = ExtractedTrackColors(primary = prim, secondary = sec, background = bg)
                        cache.put(key, extracted)
                        return@withContext extracted
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ArtworkPaletteCache.extractColors - attempt")
            }
            val fallback = ExtractedTrackColors(fallbackPrimary, fallbackSecondary, fallbackBackground)
            try { 
                cache.put(key, fallback) 
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logWarning("ArtworkPaletteCache", "Failed to cache fallback colors: ${e.message}")
            }
            fallback
        }
    }
}
