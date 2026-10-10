package com.anuj.obsidian.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import coil.Coil
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.model.AppSettings
import com.anuj.obsidian.ui.util.ArtworkPaletteCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale

/**
 * Data class representing real-time storage metrics for the cleanup tool.
 */
data class StorageCleanupStats(
    val cachedArtworkBytes: Long = 0L,
    val downloadTempBytes: Long = 0L,
    val downloadedAudioBytes: Long = 0L,
    val totalAppCacheBytes: Long = 0L,
    val maxArtworkLimitBytes: Long = 500L * 1024L * 1024L, // 500 MB
    val isAutoResetActive: Boolean = true,
    val lastAutoResetTimestamp: Long = 0L,
    val autoResetCount: Int = 0
) {
    val totalRecoverableBytes: Long get() = cachedArtworkBytes + downloadTempBytes
    val artworkUsageRatio: Float get() = if (maxArtworkLimitBytes > 0) {
        (cachedArtworkBytes.toFloat() / maxArtworkLimitBytes).coerceIn(0f, 1f)
    } else 0f

    val cachedArtworkFormatted: String get() = ObsidianStorageCleanupManager.formatBytes(cachedArtworkBytes)
    val downloadTempFormatted: String get() = ObsidianStorageCleanupManager.formatBytes(downloadTempBytes)
    val totalRecoverableFormatted: String get() = ObsidianStorageCleanupManager.formatBytes(totalRecoverableBytes)
    val downloadedAudioFormatted: String get() = ObsidianStorageCleanupManager.formatBytes(downloadedAudioBytes)
    val totalAppCacheFormatted: String get() = ObsidianStorageCleanupManager.formatBytes(totalAppCacheBytes)
    val maxArtworkLimitFormatted: String get() = ObsidianStorageCleanupManager.formatBytes(maxArtworkLimitBytes)
}

/**
 * Storage Cleanup Manager:
 * Handles clearing cached album art, download manager temp files (.downloading, buffers, TempMusic),
 * and enforces auto cache reset when online songs artwork reaches the 500 MB limit.
 */
object ObsidianStorageCleanupManager {

    const val MAX_ONLINE_ARTWORK_CACHE_BYTES = 500L * 1024L * 1024L // 500 MB

    private val _lastAutoResetTime = MutableStateFlow<Long>(0L)
    val lastAutoResetTime: StateFlow<Long> = _lastAutoResetTime.asStateFlow()

    private val _autoResetCount = MutableStateFlow<Int>(0)
    val autoResetCount: StateFlow<Int> = _autoResetCount.asStateFlow()

    private val _latestCleanupNotification = MutableStateFlow<String?>(null)
    val latestCleanupNotification: StateFlow<String?> = _latestCleanupNotification.asStateFlow()

    fun dismissNotification() {
        _latestCleanupNotification.value = null
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    /**
     * Calculates storage breakdown metrics for settings screen.
     */
    suspend fun getStorageStats(context: Context, settings: AppSettings): StorageCleanupStats = withContext(Dispatchers.IO) {
        try {
            // 1. Cached Album Art (Coil disk cache + DirectArtCache + image_cache)
            val artDirs = listOf(
                File(context.cacheDir, "obsidian_art_cache"),
                File(context.cacheDir, "DirectArtCache"),
                File(context.cacheDir, "image_cache"),
                context.externalCacheDir?.let { File(it, "obsidian_art_cache") },
                context.externalCacheDir?.let { File(it, "DirectArtCache") }
            ).filterNotNull()

            var artBytes = 0L
            for (dir in artDirs) {
                if (dir.exists()) {
                    dir.walkTopDown().forEach { f ->
                        if (f.isFile) artBytes += f.length()
                    }
                }
            }

            // 2. Download Manager Temp Files (.downloading, TempMusic, StreamMasterBuffer, stream_cache)
            var tempBytes = 0L
            val tempDirs = listOf(
                File(context.cacheDir, "TempMusic"),
                File(context.cacheDir, "StreamMasterBuffer"),
                File(context.cacheDir, "stream_cache"),
                context.externalCacheDir?.let { File(it, "TempMusic") }
            ).filterNotNull()

            for (dir in tempDirs) {
                if (dir.exists()) {
                    dir.walkTopDown().forEach { f ->
                        if (f.isFile) tempBytes += f.length()
                    }
                }
            }

            // Scan download folders for orphaned .downloading or .tmp files
            val downloadDirs = mutableListOf<File>()
            if (settings.storagePath.isNotBlank()) {
                downloadDirs.add(File(settings.storagePath))
            }
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { downloadDirs.add(it) }
            File(context.filesDir, "Music").let { downloadDirs.add(it) }

            val tempExtensions = setOf("downloading", "tmp", "temp", "part", "crdownload")
            for (dir in downloadDirs.distinct()) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val nameLower = file.name.lowercase()
                            val ext = file.extension.lowercase()
                            if (tempExtensions.contains(ext) || nameLower.contains(".downloading")) {
                                tempBytes += file.length()
                            }
                        }
                    }
                }
            }

            // SAF custom folder check
            val customUri = settings.customDownloadFolderUri
            if (!customUri.isNullOrBlank()) {
                try {
                    val parsedUri = Uri.parse(customUri)
                    val doc = DocumentFile.fromTreeUri(context, parsedUri)
                    if (doc != null && doc.exists()) {
                        doc.listFiles().forEach { child ->
                            if (child.isFile) {
                                val nameLower = (child.name ?: "").lowercase()
                                val ext = nameLower.substringAfterLast('.', "")
                                if (tempExtensions.contains(ext) || nameLower.contains(".downloading")) {
                                    tempBytes += child.length()
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w("ObsidianStorageCleanupManager", "Permission to custom download folder revoked: ${e.message}")
                } catch (e: Throwable) {
                    ObsidianCrashLogger.logException(e, "ObsidianStorageCleanupManager.safTempScan")
                }
            }

            // 3. Downloaded Audio Tracks
            var audioBytes = 0L
            val audioExtensions = setOf("flac", "wav", "mp3", "m4a", "aac", "ogg", "opus")
            for (dir in downloadDirs.distinct()) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && audioExtensions.contains(file.extension.lowercase())) {
                            audioBytes += file.length()
                        }
                    }
                }
            }

            // 4. Total App Cache
            var totalCache = 0L
            context.cacheDir?.walkTopDown()?.forEach { if (it.isFile) totalCache += it.length() }

            StorageCleanupStats(
                cachedArtworkBytes = artBytes,
                downloadTempBytes = tempBytes,
                downloadedAudioBytes = audioBytes,
                totalAppCacheBytes = totalCache,
                maxArtworkLimitBytes = MAX_ONLINE_ARTWORK_CACHE_BYTES,
                isAutoResetActive = true,
                lastAutoResetTimestamp = _lastAutoResetTime.value,
                autoResetCount = _autoResetCount.value
            )
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ObsidianStorageCleanupManager.getStorageStats")
            StorageCleanupStats()
        }
    }

    /**
     * Clears all cached album art (Coil disk/memory cache, DirectArtCache, and palette cache).
     * Returns total bytes freed.
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearCachedAlbumArt(context: Context): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        try {
            // 1. Clear Coil Disk & Memory Caches
            try {
                val imageLoader = Coil.imageLoader(context)
                imageLoader.diskCache?.clear()
                imageLoader.memoryCache?.clear()
            } catch (e: Throwable) {
                ObsidianCrashLogger.logException(e, "ObsidianStorageCleanupManager.clearCoil")
            }

            // 2. Clear Palette in-memory cache
            ArtworkPaletteCache.clear()

            // 3. Clear direct art directories
            val artDirs = listOf(
                File(context.cacheDir, "obsidian_art_cache"),
                File(context.cacheDir, "DirectArtCache"),
                File(context.cacheDir, "image_cache"),
                context.externalCacheDir?.let { File(it, "obsidian_art_cache") },
                context.externalCacheDir?.let { File(it, "DirectArtCache") }
            ).filterNotNull()

            for (dir in artDirs) {
                if (dir.exists()) {
                    dir.walkTopDown().forEach { f ->
                        if (f.isFile) {
                            freedBytes += f.length()
                            f.delete()
                        }
                    }
                    dir.delete()
                    dir.mkdirs()
                }
            }

            ObsidianCrashLogger.logMessage("StorageCleanup: Cleared cached album art (${formatBytes(freedBytes)} freed)")
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ObsidianStorageCleanupManager.clearCachedAlbumArt")
        }
        freedBytes
    }

    /**
     * Clears download manager temporary files (.downloading, buffers, TempMusic chunks).
     * Returns total bytes freed.
     */
    suspend fun clearDownloadTempFiles(context: Context, settings: AppSettings): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        try {
            // 1. Clear TempMusic & Buffer dirs
            val tempDirs = listOf(
                File(context.cacheDir, "TempMusic"),
                File(context.cacheDir, "StreamMasterBuffer"),
                File(context.cacheDir, "stream_cache"),
                context.externalCacheDir?.let { File(it, "TempMusic") }
            ).filterNotNull()

            for (dir in tempDirs) {
                if (dir.exists()) {
                    dir.walkTopDown().forEach { f ->
                        if (f.isFile) {
                            freedBytes += f.length()
                            f.delete()
                        }
                    }
                    dir.delete()
                    dir.mkdirs()
                }
            }

            // 2. Clean temporary files in destination folders
            val targetDirs = mutableListOf<File>()
            if (settings.storagePath.isNotBlank()) {
                targetDirs.add(File(settings.storagePath))
            }
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { targetDirs.add(it) }
            File(context.filesDir, "Music").let { targetDirs.add(it) }

            val tempExtensions = setOf("downloading", "tmp", "temp", "part", "crdownload")
            for (dir in targetDirs.distinct()) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val nameLower = file.name.lowercase()
                            val ext = file.extension.lowercase()
                            if (tempExtensions.contains(ext) || nameLower.contains(".downloading")) {
                                freedBytes += file.length()
                                file.delete()
                            }
                        }
                    }
                }
            }

            // 3. Clear SAF document files if available
            val customUri = settings.customDownloadFolderUri
            if (!customUri.isNullOrBlank()) {
                try {
                    val parsedUri = Uri.parse(customUri)
                    val doc = DocumentFile.fromTreeUri(context, parsedUri)
                    if (doc != null && doc.exists()) {
                        doc.listFiles().forEach { child ->
                            if (child.isFile) {
                                val nameLower = (child.name ?: "").lowercase()
                                val ext = nameLower.substringAfterLast('.', "")
                                if (tempExtensions.contains(ext) || nameLower.contains(".downloading")) {
                                    freedBytes += child.length()
                                    child.delete()
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w("ObsidianStorageCleanupManager", "Permission to custom download folder revoked: ${e.message}")
                } catch (e: Throwable) {
                    ObsidianCrashLogger.logException(e, "ObsidianStorageCleanupManager.clearSafTemp")
                }
            }

            ObsidianCrashLogger.logMessage("StorageCleanup: Cleared download temp files (${formatBytes(freedBytes)} freed)")
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ObsidianStorageCleanupManager.clearDownloadTempFiles")
        }
        freedBytes
    }

    /**
     * Quick clean: clears both cached album art and download manager temp files in one operation.
     */
    suspend fun clearAllTemporarySpace(context: Context, settings: AppSettings): Pair<Long, Long> {
        val artFreed = clearCachedAlbumArt(context)
        val tempFreed = clearDownloadTempFiles(context, settings)
        return Pair(artFreed, tempFreed)
    }

    /**
     * Clears general app cache (all files in cacheDir, externalCacheDir, codeCacheDir).
     * Returns total bytes freed.
     */
    suspend fun clearGeneralCache(context: Context): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        try {
            // Also clear Coil & Palette
            freedBytes += clearCachedAlbumArt(context)
            val cacheDirs = listOf(context.cacheDir, context.externalCacheDir, context.codeCacheDir).filterNotNull()
            for (dir in cacheDirs) {
                if (dir.exists()) {
                    dir.walkTopDown().forEach { f ->
                        if (f.isFile) {
                            freedBytes += f.length()
                            f.delete()
                        }
                    }
                }
            }
            ObsidianCrashLogger.logMessage("StorageCleanup: Cleared general cache (${formatBytes(freedBytes)} freed)")
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ObsidianStorageCleanupManager.clearGeneralCache")
        }
        freedBytes
    }

    /**
     * Deletes all downloaded audio tracks from app storage directories and custom download paths.
     * Returns total bytes freed.
     */
    suspend fun clearDownloadedTracks(context: Context, settings: AppSettings): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        try {
            val downloadDirs = mutableListOf<File>()
            if (settings.storagePath.isNotBlank()) {
                val p = if (settings.storagePath.startsWith("file://")) settings.storagePath.substring(7) else settings.storagePath
                downloadDirs.add(File(p))
            }
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ObsidianObsidian").let { downloadDirs.add(it) }
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "obsidian obsidian").let { downloadDirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { downloadDirs.add(it) }
            File(context.filesDir, "Music").let { downloadDirs.add(it) }
            File(context.cacheDir, "TempMusic").let { downloadDirs.add(it) }

            val audioExtensions = setOf("flac", "wav", "mp3", "m4a", "aac", "ogg", "opus", "lrc")
            fun deleteRecursively(dir: File) {
                if (!dir.exists() || !dir.isDirectory) return
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        deleteRecursively(file)
                    } else if (file.isFile && audioExtensions.contains(file.extension.lowercase())) {
                        freedBytes += file.length()
                        file.delete()
                    }
                }
            }

            for (dir in downloadDirs.distinct()) {
                deleteRecursively(dir)
            }
            ObsidianCrashLogger.logMessage("StorageCleanup: Cleared downloaded audio tracks (${formatBytes(freedBytes)} freed)")
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ObsidianStorageCleanupManager.clearDownloadedTracks")
        }
        freedBytes
    }

    /**
     * Auto Cache Reset for Online Songs Artwork & Temporary Media Cache:
     * When the total cache (artwork, stream master buffers, temporary chunks, image cache)
     * reaches or exceeds the 500 MB limit, this automatically triggers a complete reset
     * to protect user device storage and maintain optimum playback performance.
     *
     * @param context Android context
     * @param limitBytes The limit in bytes (defaults to 500 MB)
     * @param force Force a reset regardless of current size
     * @return true if auto-reset was executed, false otherwise
     */
    suspend fun checkAndEnforceOnlineArtworkLimit(
        context: Context,
        limitBytes: Long = MAX_ONLINE_ARTWORK_CACHE_BYTES,
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val artDirs = listOf(
                File(context.cacheDir, "obsidian_art_cache"),
                File(context.cacheDir, "DirectArtCache"),
                File(context.cacheDir, "image_cache"),
                File(context.cacheDir, "StreamMasterBuffer"),
                File(context.cacheDir, "stream_cache"),
                File(context.cacheDir, "TempMusic"),
                File(context.cacheDir, "alac_pcm"),
                File(context.cacheDir, "obsidian_tag_work"),
                context.externalCacheDir?.let { File(it, "obsidian_art_cache") },
                context.externalCacheDir?.let { File(it, "DirectArtCache") },
                context.externalCacheDir?.let { File(it, "TempMusic") }
            ).filterNotNull()

            var totalArtBytes = 0L
            val visitedPaths = mutableSetOf<String>()
            for (dir in artDirs) {
                if (dir.exists()) {
                    dir.walkTopDown().forEach { f ->
                        if (f.isFile && visitedPaths.add(f.absolutePath)) {
                            totalArtBytes += f.length()
                        }
                    }
                }
            }

            // Also check root cache directories for any loose temp files
            listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { rootDir ->
                if (rootDir.exists()) {
                    rootDir.walkTopDown().forEach { f ->
                        if (f.isFile && visitedPaths.add(f.absolutePath)) {
                            totalArtBytes += f.length()
                        }
                    }
                }
            }

            if (force || totalArtBytes >= limitBytes) {
                val sizeBeforeStr = formatBytes(totalArtBytes)
                var freed = clearCachedAlbumArt(context)
                freed += clearDownloadTempFiles(context, AppSettings())
                freed += clearGeneralCache(context)
                
                val now = System.currentTimeMillis()
                _lastAutoResetTime.value = now
                _autoResetCount.value += 1
                val message = "Auto cache reset executed: App media cache reached $sizeBeforeStr (500 MB limit). ${formatBytes(freed)} freed."
                _latestCleanupNotification.value = message
                ObsidianCrashLogger.logMessage(message)
                Log.d("StorageCleanup", message)
                return@withContext true
            }
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ObsidianStorageCleanupManager.checkAndEnforceOnlineArtworkLimit")
        }
        false
    }

    /**
     * Starts a continuous, lightweight background watcher that checks the 500 MB cache limit
     * every 30 seconds throughout the application lifecycle.
     */
    fun startAutoCacheWatcher(scope: kotlinx.coroutines.CoroutineScope, context: Context) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    checkAndEnforceOnlineArtworkLimit(context)
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w("StorageCleanup", "Watcher cycle error: ${e.message}")
                }
                delay(30_000L) // check every 30 seconds
            }
        }
    }
}
