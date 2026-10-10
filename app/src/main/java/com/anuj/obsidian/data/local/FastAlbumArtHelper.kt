package com.anuj.obsidian.data.local

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.anuj.obsidian.data.model.DownloadedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object FastAlbumArtHelper {
    private const val TAG = "FastAlbumArtHelper"

    // High performance memory cache for decoded thumbnails (max ~24MB)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024 * 8)
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    private val COMPANION_COVER_NAMES = listOf(
        "cover.jpg", "cover.png", "cover.jpeg", "cover.webp",
        "folder.jpg", "folder.png", "folder.jpeg",
        "album.jpg", "album.png", "album.jpeg",
        "front.jpg", "front.png", "front.jpeg",
        "artwork.jpg", "artwork.png"
    )

    fun getMemoryCachedBitmap(key: String): Bitmap? {
        return memoryCache.get(key)
    }

    fun putMemoryCachedBitmap(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            memoryCache.put(key, bitmap)
        }
    }

    /**
     * Resolves the fastest direct local artwork path or content URI for a given track.
     */
    suspend fun resolveFastArtworkPath(context: Context, track: DownloadedTrack): String = withContext(Dispatchers.IO) {
        resolveArtworkPathSync(context, track)
    }

    /**
     * Synchronous artwork path resolver for instant notification building.
     */
    fun resolveArtworkPathSync(context: Context, track: DownloadedTrack): String {
        // 1. If albumArtUrl is already a direct local file or content URI
        if (track.albumArtUrl.isNotBlank()) {
            if (track.albumArtUrl.startsWith("/")) {
                val file = File(track.albumArtUrl)
                if (file.exists() && file.length() > 64L) return file.absolutePath
            } else if (track.albumArtUrl.startsWith("file://")) {
                val file = File(Uri.parse(track.albumArtUrl).path ?: "")
                if (file.exists() && file.length() > 64L) return file.absolutePath
            } else if (track.albumArtUrl.startsWith("content://")) {
                return track.albumArtUrl
            }
        }

        val artCacheDir = File(context.cacheDir, "DirectArtCache").apply { mkdirs() }
        val artHash = "${track.id}_${track.localFilePath.ifBlank { track.albumArtUrl }}".hashCode()
        val cachedThumb = File(artCacheDir, "art_${artHash}.jpg")
        if (cachedThumb.exists() && cachedThumb.length() > 64L) {
            return cachedThumb.absolutePath
        }

        // 2. Extract embedded picture from audio file if available and cache it (highest quality local tag art)
        if (track.localFilePath.isNotBlank()) {
            val filePath = if (track.localFilePath.startsWith("file://")) {
                Uri.parse(track.localFilePath).path ?: ""
            } else {
                track.localFilePath
            }
            if (filePath.isNotBlank() && File(filePath).exists()) {
                try {
                    val mmr = MediaMetadataRetriever()
                    var rawPicture: ByteArray? = null
                    try {
                        mmr.setDataSource(filePath)
                        rawPicture = mmr.embeddedPicture
                    } catch (e: Exception) {
                        com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed reading embedded picture for ${track.title}: ${e.message}")
                    } finally {
                        try { 
                            mmr.release() 
                        } catch (e: Exception) {
                            com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed to release MMR: ${e.message}")
                        }
                    }
                    if (rawPicture != null && rawPicture.isNotEmpty()) {
                        try {
                            FileOutputStream(cachedThumb).use { fos ->
                                fos.write(rawPicture)
                                fos.flush()
                            }
                            if (cachedThumb.exists() && cachedThumb.length() > 64L) {
                                return cachedThumb.absolutePath
                            }
                        } catch (e: Exception) {
                            com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed writing cached thumbnail: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed to extract album art from file for ${track.title}: ${e.message}")
                }
            }
        }

        // 3. Check companion folder art if track has a local file path
        if (track.localFilePath.isNotBlank() && track.localFilePath.startsWith("/")) {
            val audioFile = File(track.localFilePath)
            val parentDir = audioFile.parentFile
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                for (coverName in COMPANION_COVER_NAMES) {
                    val coverFile = File(parentDir, coverName)
                    if (coverFile.exists() && coverFile.length() > 64L) {
                        return coverFile.absolutePath
                    }
                }
            }
        }

        // 4. If albumArtUrl is an online HTTP/HTTPS URL, use it as fallback
        if (track.albumArtUrl.isNotBlank()) {
            if (track.albumArtUrl.startsWith("http://") || track.albumArtUrl.startsWith("https://")) {
                return track.albumArtUrl
            }
        }

        // 4. Check MediaStore album art URI if possible
        try {
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM_ID)
            val selection = "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?"
            val selectionArgs = arrayOf(track.title, track.artist)
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    if (albumIdCol != -1) {
                        val albumId = cursor.getLong(albumIdCol)
                        val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
                        val albumArtUri = ContentUris.withAppendedId(sArtworkUri, albumId)
                        try {
                            context.contentResolver.openInputStream(albumArtUri)?.use {
                                return albumArtUri.toString()
                            }
                        } catch (e: Throwable) {
                            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "FastAlbumArtHelper.resolveArtworkPathSync - attempt")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "FastAlbumArtHelper.resolveArtworkPathSync - query attempt")
        }

        return if (track.albumArtUrl.startsWith("http")) track.albumArtUrl else ""
    }

    /**
     * Decode a high quality Bitmap for notification or UI.
     */
    fun getArtworkBitmap(context: Context, track: DownloadedTrack, targetSize: Int = 720): Bitmap? {
        val cacheKey = "art_${track.id}_${track.albumArtUrl.hashCode()}_${track.localFilePath.hashCode()}"
        val memBmp = getMemoryCachedBitmap(cacheKey)
        if (memBmp != null && !memBmp.isRecycled) {
            return memBmp
        }

        val artPath = resolveArtworkPathSync(context, track)
        if (artPath.isBlank()) return null

        try {
            val bmp = if (artPath.startsWith("content://")) {
                val uri = Uri.parse(artPath)
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, bounds)
                    }
                    var sample = 1
                    if (bounds.outWidth > targetSize || bounds.outHeight > targetSize) {
                        sample = (bounds.outWidth / targetSize).coerceAtLeast(bounds.outHeight / targetSize).coerceAtLeast(1)
                    }
                    val opt = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, opt)
                    }
                } catch (e: Exception) {
                    if (e is java.io.FileNotFoundException || e is SecurityException) {
                        Log.d(TAG, "MediaStore artwork content URI not accessible: $artPath")
                        null
                    } else {
                        throw e
                    }
                }
            } else if (artPath.startsWith("/") || artPath.startsWith("file://")) {
                val filePath = if (artPath.startsWith("file://")) Uri.parse(artPath).path ?: "" else artPath
                val file = File(filePath)
                if (file.exists()) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    var sample = 1
                    if (bounds.outWidth > targetSize || bounds.outHeight > targetSize) {
                        sample = (bounds.outWidth / targetSize).coerceAtLeast(bounds.outHeight / targetSize).coerceAtLeast(1)
                    }
                    val opt = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeFile(file.absolutePath, opt)
                } else null
            } else if (artPath.startsWith("http://") || artPath.startsWith("https://")) {
                val artCacheDir = File(context.cacheDir, "DirectArtCache").apply { mkdirs() }
                val artHash = "${track.id}_${artPath}".hashCode()
                val cachedThumb = File(artCacheDir, "art_${artHash}.jpg")
                if (cachedThumb.exists() && cachedThumb.length() > 64L) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(cachedThumb.absolutePath, bounds)
                    var sample = 1
                    if (bounds.outWidth > targetSize || bounds.outHeight > targetSize) {
                        sample = (bounds.outWidth / targetSize).coerceAtLeast(bounds.outHeight / targetSize).coerceAtLeast(1)
                    }
                    val opt = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeFile(cachedThumb.absolutePath, opt)
                } else {
                    var downloadedBmp: Bitmap? = null
                    try {
                        val url = java.net.URL(artPath)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.setRequestProperty("User-Agent", com.anuj.obsidian.data.remote.MusicApiService.BROWSER_USER_AGENT)
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.doInput = true
                        conn.connect()
                        if (conn.responseCode == 200) {
                            conn.inputStream.use { input ->
                                val bytes = input.readBytes()
                                if (bytes.isNotEmpty()) {
                                    try {
                                        FileOutputStream(cachedThumb).use { fos ->
                                            fos.write(bytes)
                                            fos.flush()
                                        }
                                    } catch (e: Exception) {
                                        com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed caching downloaded bitmap: ${e.message}")
                                    }
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                                    var sample = 1
                                    if (bounds.outWidth > targetSize || bounds.outHeight > targetSize) {
                                        sample = (bounds.outWidth / targetSize).coerceAtLeast(bounds.outHeight / targetSize).coerceAtLeast(1)
                                    }
                                    val opt = BitmapFactory.Options().apply {
                                        inSampleSize = sample
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                    }
                                    downloadedBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)
                                }
                            }
                        }
                        conn.disconnect()
                    } catch (e: Throwable) {
                        com.anuj.obsidian.ObsidianCrashLogger.logException(e, "FastAlbumArtHelper.getArtworkBitmap.httpFetch")
                    }
                    downloadedBmp
                }
            } else null

            if (bmp != null && !bmp.isRecycled) {
                putMemoryCachedBitmap(cacheKey, bmp)
                return bmp
            }
        } catch (e: Throwable) {
            if (e !is java.io.FileNotFoundException && e !is SecurityException) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "FastAlbumArtHelper.getArtworkBitmap - attempt")
            } else {
                Log.d(TAG, "Artwork file not found or inaccessible: ${e.message}")
            }
        }
        return null
    }

    /**
     * Get compressed JPEG bytes for MediaMetadata.setArtworkData(...)
     */
    fun getArtworkBytes(context: Context, track: DownloadedTrack): ByteArray? {
        val bmp = getArtworkBitmap(context, track, targetSize = 512) ?: return null
        return try {
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            stream.toByteArray()
        } catch (e: Throwable) {
            null
        }
    }
}
