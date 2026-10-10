package com.anuj.obsidian.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.AudioMetadataExtractor
import com.anuj.obsidian.data.local.DownloadDao
import com.anuj.obsidian.data.local.ModernAudioTagger
import com.anuj.obsidian.data.local.TrackDao
import com.anuj.obsidian.data.model.DownloadStatus
import com.anuj.obsidian.data.model.DownloadTask
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.remote.FlacStreamBufferManager
import com.anuj.obsidian.data.remote.MusicApiService
import com.anuj.obsidian.data.model.ProgressiveDownloadSessionRegistry
import com.anuj.obsidian.data.model.ProgressiveSession
import com.anuj.obsidian.data.model.ProgressiveSessionState
import com.anuj.obsidian.util.safeLaunch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * DownloadRepository: Manages studio master & lossless audio download engine,
 * progressive download session registry, tag embedding, and storage synchronization.
 */
class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val settingsRepository: SettingsRepository,
    private val lyricsRepository: LyricsRepository,
    private val libraryScannerRepository: LibraryScannerRepository,
    private val scope: CoroutineScope
) {
    val downloadTasks: Flow<List<DownloadTask>> = downloadDao.getAllTasks()
    val activeTasks: Flow<List<DownloadTask>> = downloadDao.getActiveTasks()

    private val activeDownloadJobs = ConcurrentHashMap<Long, Job>()

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
    ) {
        val job = scope.safeLaunch("DownloadRepositoryTask", Dispatchers.IO) {
            var tempDownloadingFile: File? = null
            val initialArtUrl = albumArtUrl.ifBlank { "" }
            val task = DownloadTask(
                title = title,
                artist = artist,
                album = album,
                albumArtUrl = initialArtUrl,
                quality = quality,
                progress = 0.05f,
                speedKbps = 1200f,
                status = DownloadStatus.FETCHING_METADATA,
                provider = "Obsidian Master Engine",
                lyricsAvailable = true,
                isrc = isrc.trim()
            )
            val taskId = downloadDao.insertTask(task)
            val currentJob = coroutineContext[Job]
            if (currentJob != null) {
                activeDownloadJobs[taskId] = currentJob
            }

            try {
                // Step 1: Real lyrics fetching
                val fetchedLyrics = if (lyrics.isNotBlank()) {
                    lyrics
                } else {
                    MusicApiService.fetchLyrics(
                        title = title,
                        artist = artist,
                        album = album,
                        durationSeconds = durationSeconds
                    )
                }

                // Step 2: Provider handshake & prepare local storage target with custom naming
                downloadDao.updateTask(
                    task.copy(
                        taskId = taskId,
                        status = DownloadStatus.CONNECTING_PROVIDER,
                        progress = 0.15f,
                        speedKbps = 2400f
                    )
                )

                val safeArtist = artist.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
                val safeTitle = title.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
                val safeAlbum = album.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()

                val settings = settingsRepository.settings.value
                val userNamingPattern = settings.filenameFormat.ifBlank { "{artist} - {title}" }
                val formattedBaseName = userNamingPattern
                    .replace("{artist}", safeArtist)
                    .replace("{title}", safeTitle)
                    .replace("{album}", safeAlbum)
                    .replace("{trackNumber}", "01")
                    .ifBlank { "$safeArtist - $safeTitle" }

                val targetExt = settings.downloadFormat.lowercase().ifBlank { "flac" }
                val fullFilename = if (formattedBaseName.endsWith(".$targetExt", ignoreCase = true)) formattedBaseName else "$formattedBaseName.$targetExt"

                val customFolderUri = settings.customDownloadFolderUri
                val customPath = settings.storagePath

                val appSpecificMusicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music")
                appSpecificMusicDir.mkdirs()

                val publicMusicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ObsidianObsidian")
                val isPublicWritable = try {
                    if (publicMusicDir.mkdirs() || publicMusicDir.exists()) {
                        val testFile = File(publicMusicDir, ".obsidian_test_${System.currentTimeMillis()}")
                        if (testFile.createNewFile()) {
                            testFile.delete()
                            true
                        } else false
                    } else false
                } catch (_: Throwable) { false }

                val defaultMusicDir = if (isPublicWritable) publicMusicDir else appSpecificMusicDir

                val musicDir = if (isTemp) {
                    File(context.cacheDir, "TempMusic").apply { mkdirs() }
                } else if (customPath.isNotBlank()) {
                    val pathStr = if (customPath.startsWith("file://")) customPath.substring(7) else customPath
                    val customDir = File(pathStr)
                    val isCustomWritable = try {
                        customDir.mkdirs()
                        val testFile = File(customDir, ".obsidian_test_${System.currentTimeMillis()}")
                        if (testFile.createNewFile()) {
                            testFile.delete()
                            true
                        } else false
                    } catch (e: Throwable) {
                        ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload - customDir")
                        false
                    }
                    if (isCustomWritable) customDir else defaultMusicDir
                } else {
                    defaultMusicDir
                }

                var uniqueFilename = fullFilename
                var counter = 1
                var localAudioFile = File(musicDir, uniqueFilename)
                while (localAudioFile.exists()) {
                    val base = fullFilename.substringBeforeLast(".")
                    val ext = fullFilename.substringAfterLast(".", targetExt)
                    uniqueFilename = "$base ($counter).$ext"
                    localAudioFile = File(musicDir, uniqueFilename)
                    counter++
                }

                // Step 3: Stream download to an isolated temp file in app cache first
                val is192k = quality.contains("192")
                val is96k = quality.contains("96")
                val estimatedBytes = if (is192k) 65L * 1024 * 1024 else if (is96k) 42L * 1024 * 1024 else 28L * 1024 * 1024

                val isolatedTempFile = File(context.cacheDir, "obsidian_dl_${taskId}_${System.currentTimeMillis()}.$targetExt")
                tempDownloadingFile = isolatedTempFile
                val session = ProgressiveSession(
                    trackId = taskId,
                    title = title,
                    artist = artist,
                    sourceStreamUrl = streamUrl,
                    tempFile = isolatedTempFile,
                    downloadedBytes = 0,
                    expectedTotalBytes = estimatedBytes,
                    state = ProgressiveSessionState.DOWNLOADING
                )
                ProgressiveDownloadSessionRegistry.registerSession(session)

                var lastDbUpdate = 0L

                val finalDownloadedFile = MusicApiService.downloadAudioStream(
                    title = title,
                    artist = artist,
                    album = album,
                    durationSeconds = durationSeconds,
                    streamUrl = streamUrl,
                    destinationFile = isolatedTempFile,
                    estimatedTotalBytes = estimatedBytes
                ) { downloadedBytes, totalBytes, speedKbps ->
                    val now = System.currentTimeMillis()

                    val nextState = if (downloadedBytes > 128 * 1024L) {
                        ProgressiveSessionState.AVAILABLE_FOR_PLAYBACK
                    } else {
                        ProgressiveSessionState.DOWNLOADING
                    }
                    ProgressiveDownloadSessionRegistry.updateSession(taskId) { current ->
                        current.copy(
                            downloadedBytes = downloadedBytes,
                            expectedTotalBytes = totalBytes,
                            state = nextState
                        )
                    }

                    if (now - lastDbUpdate >= 120) {
                        lastDbUpdate = now
                        val currentProg = 0.15f + ((downloadedBytes.toFloat() / totalBytes.coerceAtLeast(1L)) * 0.70f).coerceIn(0f, 0.70f)
                        scope.safeLaunch("DownloadProgressUpdate", Dispatchers.IO) {
                            downloadDao.updateTask(
                                task.copy(
                                    taskId = taskId,
                                    status = DownloadStatus.DOWNLOADING_STREAM,
                                    progress = currentProg,
                                    speedKbps = speedKbps,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    }
                }

                // Step 4: Validate Downloaded Temp File
                val downloadedTempFile = finalDownloadedFile ?: isolatedTempFile
                var realFileSizeMb = 0f
                var formatString = targetExt.uppercase()
                var finalDestinationPath = ""
                var isValid = false
                var finalDuration = durationSeconds

                if (downloadedTempFile.exists() && FlacStreamBufferManager.isValidAudioFile(downloadedTempFile)) {
                    val detectedType = FlacStreamBufferManager.detectAudioContainer(downloadedTempFile)
                    val ext = if (detectedType != FlacStreamBufferManager.AudioContainerType.UNKNOWN) {
                        detectedType.extension
                    } else if (targetExt.isNotBlank()) {
                        targetExt
                    } else {
                        "flac"
                    }
                    formatString = ext.uppercase()

                    val retriever = MediaMetadataRetriever()
                    var decodeSuccess = false
                    try {
                        retriever.setDataSource(downloadedTempFile.absolutePath)
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durationMs = durationStr?.toLongOrNull() ?: 0L
                        if (durationMs > 0) {
                            finalDuration = (durationMs / 1000).toInt()
                            decodeSuccess = true
                        } else {
                            decodeSuccess = FlacStreamBufferManager.isValidAudioFile(downloadedTempFile)
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload - decodeValidation")
                        Log.e("DownloadRepo", "Decoder validation notice: ${e.message}")
                        decodeSuccess = FlacStreamBufferManager.isValidAudioFile(downloadedTempFile)
                    } finally {
                        try { retriever.release() } catch (e: Exception) {
                            ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload - retrieverRelease")
                        }
                    }

                    if (decodeSuccess) {
                        isValid = true
                    }
                }

                if (isValid) {
                    // Step 5: Embed ALL tags directly on the isolated temp file BEFORE publishing
                    downloadDao.updateTask(
                        task.copy(
                            taskId = taskId,
                            status = DownloadStatus.EMBEDDING_TAGS,
                            progress = 0.90f,
                            speedKbps = 850f
                        )
                    )

                    var tagResult = com.anuj.obsidian.data.local.TagResult(
                        success = false,
                        criticalTagsVerified = false,
                        artworkEmbedded = false,
                        lyricsEmbedded = false,
                        details = "Not attempted",
                        detectedFormat = formatString.lowercase()
                    )

                    val effectiveArtUrl = if (albumArtUrl.isNotBlank() && !albumArtUrl.contains("picsum.photos") && albumArtUrl != "NO_ART") {
                        albumArtUrl
                    } else {
                        ""
                    }

                    try {
                        val shouldEmbedLyrics = settings.embedLyrics
                        val effectiveLyrics = if (shouldEmbedLyrics) fetchedLyrics else ""

                        tagResult = ModernAudioTagger.embedAllTagsDetailed(
                            context = context,
                            sourceFilePath = downloadedTempFile.absolutePath,
                            title = title,
                            artist = artist,
                            album = album,
                            genre = genre.ifBlank { "Lossless Master" },
                            year = releaseDate.ifBlank { "2024" },
                            lyrics = effectiveLyrics,
                            artworkUrl = effectiveArtUrl,
                            trackNumber = trackNumber,
                            isrc = isrc
                        )

                        Log.d("DownloadRepo", "ModernAudioTagger result for $title: success=${tagResult.success}, critical=${tagResult.criticalTagsVerified}, art=${tagResult.artworkEmbedded}, details=${tagResult.details}")
                    } catch (e: Throwable) {
                        ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload - embedTags")
                        Log.e("DownloadRepo", "Metadata embedding exception: ${e.message}")
                        tagResult = tagResult.copy(
                            success = false,
                            criticalTagsVerified = false,
                            details = "Metadata error: ${e.message}"
                        )
                    }

                    // Step 6: Publish the verified tagged file to the final destination
                    val targetFilename = if (localAudioFile.name.endsWith(".${formatString.lowercase()}", ignoreCase = true)) {
                        localAudioFile.name
                    } else {
                        localAudioFile.nameWithoutExtension + ".${formatString.lowercase()}"
                    }

                    val mimeType = when (formatString.lowercase()) {
                        "flac" -> "audio/flac"
                        "wav" -> "audio/wav"
                        "mp3" -> "audio/mpeg"
                        "m4a" -> "audio/mp4"
                        "ogg" -> "audio/ogg"
                        else -> "audio/*"
                    }

                    if (isTemp) {
                        val tempFinal = File(musicDir, targetFilename)
                        downloadedTempFile.copyTo(tempFinal, overwrite = true)
                        finalDestinationPath = tempFinal.absolutePath
                        realFileSizeMb = tempFinal.length() / (1024f * 1024f)
                    } else {
                        var publishedViaMediaStore = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                val contentValues = ContentValues().apply {
                                    put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, targetFilename)
                                    put(android.provider.MediaStore.Audio.Media.TITLE, title)
                                    put(android.provider.MediaStore.Audio.Media.ARTIST, artist)
                                    put(android.provider.MediaStore.Audio.Media.ALBUM, album)
                                    put(android.provider.MediaStore.Audio.Media.MIME_TYPE, mimeType)
                                    put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/ObsidianObsidian")
                                    put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
                                }
                                val mediaUri = context.contentResolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                                if (mediaUri != null) {
                                    context.contentResolver.openOutputStream(mediaUri)?.use { outStream ->
                                        downloadedTempFile.inputStream().use { inStream ->
                                            inStream.copyTo(outStream)
                                        }
                                    }
                                    contentValues.clear()
                                    contentValues.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                                    context.contentResolver.update(mediaUri, contentValues, null, null)

                                    val directPublicFile = File(publicMusicDir, targetFilename)
                                    if (directPublicFile.exists() && directPublicFile.length() > 0) {
                                        finalDestinationPath = directPublicFile.absolutePath
                                    } else {
                                        try {
                                            context.contentResolver.query(mediaUri, arrayOf(android.provider.MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
                                                if (cursor.moveToFirst()) {
                                                    val dataIdx = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                                                    if (dataIdx != -1) {
                                                        val resolvedPath = cursor.getString(dataIdx)
                                                        if (!resolvedPath.isNullOrBlank() && File(resolvedPath).exists()) {
                                                            finalDestinationPath = resolvedPath
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Throwable) {
                                            Log.w("DownloadRepo", "Failed to update IS_PENDING: ${e.message}")
                                        }
                                    }
                                    publishedViaMediaStore = true
                                }
                            } catch (e: Throwable) {
                                ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload - mediaStorePublish")
                                Log.w("DownloadRepo", "MediaStore scoped storage publish failed, falling back to direct file: ${e.message}")
                            }
                        }

                        // Verify temp non-empty before atomic replace/publish
                        if (!downloadedTempFile.exists() || downloadedTempFile.length() == 0L) {
                            throw IllegalStateException("Downloaded temp file was empty or missing prior to final publish")
                        }

                        // Direct file write fallback or pre-Q
                        if (finalDestinationPath.isBlank() || !File(finalDestinationPath).exists()) {
                            val targetDestFile = File(musicDir, targetFilename)
                            try {
                                downloadedTempFile.copyTo(targetDestFile, overwrite = true)
                                if (targetDestFile.exists() && targetDestFile.length() > 0) {
                                finalDestinationPath = targetDestFile.absolutePath
                                }
                            } catch (e: Throwable) {
                                ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload - directCopy")
                                Log.w("DownloadRepo", "Direct copy to $targetDestFile failed: ${e.message}")
                            }
                        }

                        // App-specific storage ultimate fallback to never lose audio
                        if (finalDestinationPath.isBlank() || !File(finalDestinationPath).exists()) {
                            val appFallbackFile = File(appSpecificMusicDir, targetFilename)
                            try {
                                downloadedTempFile.copyTo(appFallbackFile, overwrite = true)
                                finalDestinationPath = appFallbackFile.absolutePath
                            } catch (e: Throwable) {
                                Log.w("DownloadRepo", "App-specific storage fallback copy failed: ${e.message}")
                            }
                        }

                        val publishedFile = File(finalDestinationPath)
                        realFileSizeMb = if (publishedFile.exists()) publishedFile.length() / (1024f * 1024f) else downloadedTempFile.length() / (1024f * 1024f)

                        // Write sidecar .lrc if requested
                        if (settings.generateLrcFile && fetchedLyrics.isNotBlank()) {
                            try {
                                val lrcFile = File(publishedFile.parentFile ?: musicDir, "${publishedFile.nameWithoutExtension}.lrc")
                                lrcFile.writeText(fetchedLyrics)
                            } catch (e: Throwable) {
                                Log.w("DownloadRepo", "Sidecar .lrc write failed: ${e.message}")
                            }
                        }

                        // Custom SAF Export
                        if (customFolderUri.isNotBlank()) {
                            try {
                                val treeDoc = DocumentFile.fromTreeUri(context, Uri.parse(customFolderUri))
                                if (treeDoc != null && treeDoc.canWrite()) {
                                    val exportedDoc = treeDoc.createFile(mimeType, targetFilename)
                                    if (exportedDoc != null) {
                                        context.contentResolver.openOutputStream(exportedDoc.uri)?.use { out ->
                                            downloadedTempFile.inputStream().use { input ->
                                                input.copyTo(out)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Throwable) {
                                ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload - customSafExport")
                                Log.e("DownloadRepo", "Custom SAF export failed: ${e.message}")
                            }
                        }

                        // Notify MediaScanner
                        try {
                            MediaScannerConnection.scanFile(context, arrayOf(finalDestinationPath), arrayOf(mimeType), null)
                        } catch (e: Throwable) {
                            Log.w("DownloadRepo", "MediaScanner scanFile failed: ${e.message}")
                        }
                    }

                    // Clean up isolated temp file
                    try {
                        if (downloadedTempFile.exists() && downloadedTempFile.absolutePath != finalDestinationPath) {
                            downloadedTempFile.delete()
                        }
                    } catch (e: Throwable) {
                        Log.w("DownloadRepo", "Temp file deletion failed: ${e.message}")
                    }

                    // Verify TITLE + ARTIST on final published file via MediaMetadataRetriever
                    val (finalVerified, finalVerifyDetails) = ModernAudioTagger.verifyFinalFileTags(context, finalDestinationPath, title, artist)
                    val effectiveCriticalTags = tagResult.criticalTagsVerified && finalVerified

                    // Update task status reflecting embedding outcome
                    val finalStatus = if (effectiveCriticalTags) DownloadStatus.COMPLETED else DownloadStatus.COMPLETED_WITHOUT_TAGS
                    val finalError = if (effectiveCriticalTags) {
                        if (!tagResult.artworkEmbedded && effectiveArtUrl.isNotBlank()) "Cover art unavailable; text tags OK" else null
                    } else {
                        if (!finalVerified) {
                            finalVerifyDetails
                        } else {
                            tagResult.details.ifBlank { "Critical metadata (title/artist) could not be verified on final file" }
                        }
                    }

                    downloadDao.updateTask(
                        task.copy(
                            taskId = taskId,
                            status = finalStatus,
                            progress = 1.0f,
                            speedKbps = 0f,
                            errorMessage = finalError
                        )
                    )

                    var resolvedArtUrl = albumArtUrl
                    if (resolvedArtUrl.isBlank() && finalDestinationPath.isNotBlank()) {
                        try {
                            val mmr = MediaMetadataRetriever()
                            try {
                                mmr.setDataSource(finalDestinationPath)
                                val rawPic = mmr.embeddedPicture
                                if (rawPic != null && rawPic.isNotEmpty()) {
                                    val artCacheDir = File(context.cacheDir, "DirectArtCache").apply { mkdirs() }
                                    val thumbFile = File(artCacheDir, "art_dl_${System.currentTimeMillis()}.jpg")
                                    AudioMetadataExtractor.saveOptimizedAlbumArt(rawPic, thumbFile)
                                    if (thumbFile.exists() && thumbFile.length() > 64L) {
                                        resolvedArtUrl = thumbFile.absolutePath
                                    }
                                }
                            } finally {
                                try { mmr.release() } catch (e: Exception) {
                                    ObsidianCrashLogger.logWarning("DownloadRepo", "Failed to release MMR in startDownload: ${e.message}")
                                }
                            }
                        } catch (e: Throwable) {
                            ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload.artExtract")
                        }
                    }
                    if (resolvedArtUrl.isBlank()) {
                        resolvedArtUrl = ""
                    }

                    val realExtracted = try {
                        val finalFile = File(finalDestinationPath)
                        if (finalFile.exists()) {
                            AudioMetadataExtractor.extract(
                                context = context,
                                uri = Uri.fromFile(finalFile),
                                fallbackName = finalFile.name,
                                docFile = null,
                                localFilePath = finalDestinationPath
                            )
                        } else null
                    } catch (e: Throwable) {
                        ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload.extractRealMetadata")
                        null
                    }

                    val fmtLower = formatString.lowercase()
                    val isLosslessAudio = fmtLower == "flac" || fmtLower == "wav" || fmtLower == "alac" || fmtLower == "dsf" || fmtLower == "dff"

                    val finalSampleRate = realExtracted?.sampleRate?.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) }
                        ?: if (fmtLower == "ogg" || fmtLower == "opus") "48.0 kHz" else "44.1 kHz"

                    val finalBitDepth = if (isLosslessAudio) {
                        realExtracted?.bitDepth?.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) } ?: "16-bit"
                    } else {
                        "" // Lossy compressed audio has NO bit depth
                    }

                    val finalBitrate = if (realExtracted != null && realExtracted.bitrateKbps > 0) {
                        realExtracted.bitrateKbps
                    } else if (isLosslessAudio) {
                        956
                    } else {
                        320
                    }

                    if (realExtracted != null && realExtracted.durationSeconds > 0) {
                        finalDuration = realExtracted.durationSeconds
                    }

                    val finalProviderSource = if (isTemp) "Temp Stream" else if (providerSource.isNotBlank()) providerSource else "Obsidian Library"

                    val downloadedTrack = DownloadedTrack(
                        id = 0L,
                        title = title,
                        artist = artist,
                        album = album,
                        durationSeconds = finalDuration,
                        format = formatString,
                        bitDepth = finalBitDepth,
                        sampleRate = finalSampleRate,
                        bitrateKbps = finalBitrate,
                        albumArtUrl = resolvedArtUrl,
                        localFilePath = finalDestinationPath,
                        fileSizeMb = realFileSizeMb,
                        lyrics = fetchedLyrics,
                        isrc = isrc.trim(),
                        releaseDate = releaseDate.ifBlank { "2024" },
                        genre = genre.ifBlank { "Audio" },
                        providerSource = finalProviderSource,
                        isFavorite = false,
                        dateAddedTimestamp = System.currentTimeMillis()
                    )

                    if (!isTemp) {
                        trackDao.insertTrack(downloadedTrack)
                        try {
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(finalDestinationPath),
                                arrayOf("audio/*"),
                                null
                            )
                        } catch (e: Exception) {
                            ObsidianCrashLogger.logWarning("DownloadRepo", "MediaScanner scanFile notice: ${e.message}")
                        }
                        onComplete?.invoke(finalDestinationPath)
                    }
                } else {
                    ProgressiveDownloadSessionRegistry.updateSession(taskId) { current ->
                        current.copy(
                            state = ProgressiveSessionState.FAILED,
                            error = "Validation failed"
                        )
                    }
                    try {
                        val tFile = tempDownloadingFile
                        if (tFile.exists()) {
                            tFile.delete()
                        }
                    } catch (e: Exception) {
                        ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload - deleteTemp")
                    }

                    downloadDao.updateTask(
                        task.copy(
                            taskId = taskId,
                            status = DownloadStatus.FAILED,
                            progress = 0f,
                            speedKbps = 0f,
                            errorMessage = "Unable to fetch or validate complete audio stream. Please retry."
                        )
                    )
                }
            } catch (e: Throwable) {
                ProgressiveDownloadSessionRegistry.updateSession(taskId) { current ->
                    current.copy(
                        state = if (e is CancellationException) ProgressiveSessionState.CANCELLED else ProgressiveSessionState.FAILED,
                        error = e.message ?: "Download failed"
                    )
                }
                try {
                    val tFile = tempDownloadingFile
                    if (tFile != null && tFile.exists()) {
                        tFile.delete()
                    }
                } catch (e: Exception) {
                    ObsidianCrashLogger.logException(e, "DownloadRepository.startDownload - cleanupTemp")
                }

                if (e is CancellationException) {
                    downloadDao.deleteTaskById(taskId)
                } else {
                    downloadDao.updateTask(
                        task.copy(
                            taskId = taskId,
                            status = DownloadStatus.FAILED,
                            progress = 0f,
                            speedKbps = 0f,
                            errorMessage = e.message ?: "Download failed"
                        )
                    )
                }
            } finally {
                activeDownloadJobs.remove(taskId)
            }
        }
    }

    suspend fun clearCompletedDownloads() {
        downloadDao.clearCompletedTasks()
    }

    suspend fun cancelDownload(taskId: Long) {
        activeDownloadJobs.remove(taskId)?.cancel()
        downloadDao.deleteTaskById(taskId)
    }

    suspend fun retryTagging(taskId: Long): Boolean = withContext(Dispatchers.IO) {
        val task = downloadDao.getTaskById(taskId) ?: return@withContext false
        downloadDao.updateTask(
            task.copy(
                status = DownloadStatus.EMBEDDING_TAGS,
                errorMessage = null
            )
        )

        val safeArtist = task.artist.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
        val safeTitle = task.title.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
        val settings = settingsRepository.settings.value
        val targetExt = settings.downloadFormat.lowercase().ifBlank { "flac" }

        val publicMusicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ObsidianObsidian")
        val appSpecificMusicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music")

        val candidateFiles = mutableListOf<File>()
        for (dir in listOf(publicMusicDir, appSpecificMusicDir)) {
            if (dir.exists()) {
                candidateFiles.add(File(dir, "$safeArtist - $safeTitle.$targetExt"))
                candidateFiles.add(File(dir, "${task.title}.$targetExt"))
                for (alt in listOf("flac", "mp3", "m4a", "ogg", "wav")) {
                    candidateFiles.add(File(dir, "$safeArtist - $safeTitle.$alt"))
                    candidateFiles.add(File(dir, "${task.title}.$alt"))
                }
                // Also search directory for files containing track title
                try {
                    dir.listFiles()?.filter { it.isFile && it.name.contains(safeTitle, ignoreCase = true) }?.let {
                        candidateFiles.addAll(it)
                    }
                } catch (e: Throwable) {
                    Log.w("DownloadRepo", "Directory file list scan failed: ${e.message}")
                }
            }
        }

        val targetFile = candidateFiles.firstOrNull { it.exists() && it.length() > 0 }
        if (targetFile == null) {
            downloadDao.updateTask(
                task.copy(
                    status = DownloadStatus.COMPLETED_WITHOUT_TAGS,
                    errorMessage = "Audio file not found in storage to retry tags"
                )
            )
            return@withContext false
        }

        val lyrics = MusicApiService.fetchLyrics(task.title, task.artist, task.album)
        val tagResult = ModernAudioTagger.embedAllTagsDetailed(
            context = context,
            sourceFilePath = targetFile.absolutePath,
            title = task.title,
            artist = task.artist,
            album = task.album,
            genre = "Lossless Master",
            year = "2024",
            lyrics = if (settings.embedLyrics) lyrics else "",
            artworkUrl = task.albumArtUrl,
            trackNumber = 1,
            isrc = task.isrc
        )

        if (tagResult.criticalTagsVerified) {
            val finalErr = if (!tagResult.artworkEmbedded && task.albumArtUrl.isNotBlank()) "Cover art unavailable; text tags OK" else null
            downloadDao.updateTask(
                task.copy(
                    status = DownloadStatus.COMPLETED,
                    errorMessage = finalErr
                )
            )
        } else {
            downloadDao.updateTask(
                task.copy(
                    status = DownloadStatus.COMPLETED_WITHOUT_TAGS,
                    errorMessage = tagResult.details.ifBlank { "Metadata verification failed after retry" }
                )
            )
        }
        tagResult.criticalTagsVerified
    }
}
