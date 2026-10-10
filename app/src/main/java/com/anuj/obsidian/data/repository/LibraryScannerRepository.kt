package com.anuj.obsidian.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.AudioMetadataExtractor
import com.anuj.obsidian.data.local.ExtractedAudioInfo
import com.anuj.obsidian.data.local.TrackDao
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.util.safeLaunch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * LibraryScannerRepository: Scans device MediaStore audio files, SAF user-selected folder trees,
 * imports local audio files directly, updates embedded ID3/FLAC audio tags, and performs database deduplication.
 */
class LibraryScannerRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) {
    private var tagSyncJob: Job? = null

    suspend fun deduplicateDatabaseTracks() = withContext(Dispatchers.IO) {
        try {
            val allTracks = trackDao.getAllTracksList()
            val seenPaths = HashSet<String>()
            val toDelete = ArrayList<DownloadedTrack>()

            for (track in allTracks) {
                val pathKey = track.localFilePath.trim().lowercase()

                if (pathKey.isNotBlank()) {
                    if (seenPaths.contains(pathKey)) {
                        toDelete.add(track)
                    } else {
                        seenPaths.add(pathKey)
                    }
                }
            }

            for (dup in toDelete) {
                trackDao.deleteTrack(dup)
            }
            trackDao.sanitizeUnknownSampleRates()
            trackDao.sanitizeUnknownBitDepths()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ObsidianCrashLogger.logException(e, "LibraryScannerRepository.deduplicateDatabaseTracks")
            Log.e("LibraryScannerRepo", "Failed to deduplicate tracks", e)
        }
    }

    fun startBackgroundTagSync() {
        if (tagSyncJob?.isActive == true) return
        tagSyncJob = scope.safeLaunch("LibraryScannerTagSync", Dispatchers.IO) {
            while (isActive) {
                val pendingBatch = trackDao.getTracksNeedingTagSync()
                if (pendingBatch.isEmpty()) break
                for (track in pendingBatch) {
                    if (!isActive) break
                    try {
                        if (track.localFilePath.isNotBlank() && File(track.localFilePath).exists()) {
                            val meta = com.anuj.obsidian.data.local.JaudiotaggerHelper.readDetailedMetadata(context, track.localFilePath)
                            val updated = track.copy(
                                tagsLoaded = true,
                                title = if (meta.title.isNotBlank()) meta.title else track.title,
                                artist = if (meta.artist.isNotBlank()) meta.artist else track.artist,
                                album = if (meta.album.isNotBlank()) meta.album else track.album,
                                genre = if (meta.genre.isNotBlank()) meta.genre else track.genre,
                                releaseDate = if (meta.year.isNotBlank()) meta.year else track.releaseDate,
                                lyrics = if (meta.lyrics.isNotBlank()) meta.lyrics else track.lyrics,
                                bitDepth = if (meta.bitDepthFormatted.isNotBlank() && !meta.bitDepthFormatted.equals("UNKNOWN", ignoreCase = true)) meta.bitDepthFormatted else if (track.bitDepth.isNotBlank() && !track.bitDepth.equals("UNKNOWN", ignoreCase = true)) track.bitDepth else "16-bit",
                                sampleRate = if (meta.sampleRateFormatted.isNotBlank() && !meta.sampleRateFormatted.equals("UNKNOWN", ignoreCase = true)) meta.sampleRateFormatted else if (track.sampleRate.isNotBlank() && !track.sampleRate.equals("UNKNOWN", ignoreCase = true)) track.sampleRate else "44.1 kHz",
                                bitrateKbps = if (meta.bitrateKbps > 0) meta.bitrateKbps else track.bitrateKbps,
                                durationSeconds = if (meta.durationSeconds > 0) meta.durationSeconds else track.durationSeconds,
                                format = if (meta.formatName.isNotBlank() && !meta.formatName.equals("UNKNOWN", ignoreCase = true)) meta.formatName.uppercase() else track.format
                            )
                            trackDao.updateTrack(updated)
                        } else {
                            val safeBitDepth = if (track.bitDepth.isBlank() || track.bitDepth.equals("UNKNOWN", ignoreCase = true)) "16-bit" else track.bitDepth
                            val safeSampleRate = if (track.sampleRate.isBlank() || track.sampleRate.equals("UNKNOWN", ignoreCase = true)) "44.1 kHz" else track.sampleRate
                            trackDao.updateTrack(track.copy(tagsLoaded = true, bitDepth = safeBitDepth, sampleRate = safeSampleRate))
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        ObsidianCrashLogger.logException(e, "LibraryScannerRepository.startBackgroundTagSync")
                    }
                    kotlinx.coroutines.yield()
                }
            }
        }
    }

    suspend fun updateTrackTags(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        trackNumber: Int,
        discNumber: Int,
        lyrics: String
    ) = withContext(Dispatchers.IO) {
        val existing = trackDao.getTrackById(trackId) ?: return@withContext
        val updated = existing.copy(
            title = title.ifBlank { existing.title },
            artist = artist.ifBlank { existing.artist },
            album = album.ifBlank { existing.album },
            genre = genre.ifBlank { existing.genre },
            releaseDate = year.ifBlank { existing.releaseDate },
            lyrics = lyrics.ifBlank { existing.lyrics }
        )
        trackDao.updateTrack(updated)
    }

    suspend fun scanDirectoryTree(treeUri: Uri, context: Context): Int = withContext(Dispatchers.IO) {
        if (treeUri.scheme != "content") {
            val path = if (treeUri.scheme == "file") treeUri.path ?: "" else treeUri.toString()
            return@withContext scanLocalDirectory(File(path), context)
        }
        var indexedCount = 0
        try {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "LibraryScannerRepository.scanDirectoryTree - takePermission")
            }

            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
            val folderDisplayName = rootDoc.name ?: "Selected Storage Folder"

            settingsRepository.addLinkedMusicFolder(treeUri.toString(), folderDisplayName)

            val existingTracksList = trackDao.getAllTracksList()
            val existingPaths = existingTracksList.map { it.localFilePath }.toHashSet()

            val audioDocFiles = ArrayList<Pair<Uri, String>>()

            try {
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val queue = ArrayDeque<String>()
                queue.add(rootDocId)

                val resolver = context.contentResolver

                while (queue.isNotEmpty()) {
                    val currentDocId = queue.removeFirst()
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)

                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )

                    resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(idCol)
                            val mimeType = cursor.getString(mimeCol)
                            val name = cursor.getString(nameCol) ?: ""

                            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                                queue.add(docId)
                            } else {
                                if (AudioMetadataExtractor.isSupportedAudio(name)) {
                                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                    val uriStr = uri.toString()
                                    if (existingPaths.contains(uriStr)) {
                                        indexedCount++
                                    } else {
                                        audioDocFiles.add(Pair(uri, name))
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                ObsidianCrashLogger.logException(e, "LibraryScannerRepository.scanDirectoryTree - traverse")
                Log.e("LibraryScannerRepo", "Traversal error", e)
            }

            if (audioDocFiles.isNotEmpty()) {
                val newTracksBatch = ArrayList<DownloadedTrack>()
                val chunks = audioDocFiles.chunked(12)

                for (chunk in chunks) {
                    val extractedChunk = coroutineScope {
                        chunk.map { (uri, name) ->
                            async(Dispatchers.IO) {
                                val uriStr = uri.toString()
                                val ext = name.substringAfterLast(".", "FLAC").lowercase()
                                val extracted = try {
                                    AudioMetadataExtractor.extract(
                                        context = context,
                                        uri = uri,
                                        fallbackName = name
                                    )
                                } catch (e: Throwable) {
                                    ObsidianCrashLogger.logException(e, "AudioMetadataExtractor.extract in scanDirectoryTree")
                                    val rawTitle = name.substringBeforeLast(".")
                                    ExtractedAudioInfo(
                                        title = rawTitle,
                                        artist = "Unknown Artist",
                                        album = folderDisplayName,
                                        durationSeconds = 0,
                                        bitDepth = "16-bit",
                                        sampleRate = "44.1 kHz",
                                        bitrateKbps = 320,
                                        albumArtPath = "",
                                        lyrics = "",
                                        year = "2024",
                                        genre = "Audio",
                                        trackNumber = 1
                                    )
                                }
                                var sizeMb = 5f
                                try {
                                    context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            sizeMb = cursor.getLong(0) / (1024f * 1024f)
                                        }
                                    }
                                } catch (e: Throwable) {
                                    ObsidianCrashLogger.logException(e, "LibraryScannerRepository.scanDirectoryTree - size query")
                                }

                                if (existingPaths.contains(uriStr)) {
                                    null
                                } else {
                                    existingPaths.add(uriStr)
                                    val finalDuration = if (extracted.durationSeconds > 0) extracted.durationSeconds else 0
                                    DownloadedTrack(
                                        id = 0L,
                                        tagsLoaded = true,
                                        title = extracted.title,
                                        artist = extracted.artist,
                                        album = if (extracted.album.isNotBlank() && extracted.album != "Unknown Album") extracted.album else folderDisplayName,
                                        durationSeconds = finalDuration,
                                        format = if (extracted.format.isNotBlank()) extracted.format else ext.uppercase(),
                                        bitDepth = extracted.bitDepth,
                                        sampleRate = extracted.sampleRate,
                                        bitrateKbps = extracted.bitrateKbps,
                                        albumArtUrl = extracted.albumArtPath,
                                        localFilePath = uriStr,
                                        fileSizeMb = sizeMb,
                                        lyrics = extracted.lyrics,
                                        isrc = "",
                                        releaseDate = extracted.year,
                                        genre = if (extracted.genre.isNotBlank()) extracted.genre else "Audio",
                                        providerSource = "Folder: $folderDisplayName",
                                        isFavorite = false,
                                        dateAddedTimestamp = System.currentTimeMillis()
                                    )
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
                    newTracksBatch.addAll(extractedChunk)
                    if (newTracksBatch.size >= 100) {
                        trackDao.insertTracks(newTracksBatch.toList())
                        indexedCount += newTracksBatch.size
                        newTracksBatch.clear()
                    }
                }

                if (newTracksBatch.isNotEmpty()) {
                    trackDao.insertTracks(newTracksBatch)
                    indexedCount += newTracksBatch.size
                }
            }

            deduplicateDatabaseTracks()
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "LibraryScannerRepository.scanDirectoryTree")
            Log.e("LibraryScannerRepo", "Directory tree scan error", e)
        }
        indexedCount
    }

    suspend fun getAvailableAudioFolders(context: Context): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, Int>()
        try {
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val data = cursor.getString(dataIndex)
                    val parent = File(data).parent ?: continue
                    folders[parent] = (folders[parent] ?: 0) + 1
                }
            }
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "LibraryScannerRepository.getAvailableAudioFolders")
            Log.e("LibraryScannerRepo", "Failed to get audio folders", e)
        }
        folders.toList().sortedByDescending { it.second }
    }

    suspend fun autoScanSavedDirectory(context: Context) = withContext(Dispatchers.IO) {
        scanMediaStore(context)
    }

    suspend fun scanMediaStore(context: Context): Int = withContext(Dispatchers.IO) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("LibraryScannerRepo", "scanMediaStore skipped: Storage permission not granted.")
            return@withContext 0
        }

        var addedCount = 0
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.YEAR
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val existingTracksList = trackDao.getAllTracksList()
            val existingPaths = existingTracksList.map { it.localFilePath }.toHashSet()

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)

                val allowedFolders = settingsRepository.settings.value.linkedMusicFolders.map { it.first }
                val newTracks = mutableListOf<DownloadedTrack>()

                while (cursor.moveToNext()) {
                    val dataPathCheck = if (dataColumn != -1) cursor.getString(dataColumn) else ""
                    if (allowedFolders.isNotEmpty() && !dataPathCheck.isNullOrBlank()) {
                        if (!allowedFolders.any { dataPathCheck.startsWith(it) }) continue
                    }

                    val id = cursor.getLong(idColumn)
                    val contentUri = android.content.ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val dataPath = if (dataColumn != -1 && !cursor.getString(dataColumn).isNullOrBlank()) {
                        cursor.getString(dataColumn)
                    } else {
                        contentUri
                    }

                    if (existingPaths.contains(dataPath) || existingPaths.contains(contentUri)) continue
                    existingPaths.add(dataPath)

                    val rawTitle = cursor.getString(titleColumn) ?: "Track $id"
                    val rawArtist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val rawAlbum = cursor.getString(albumColumn) ?: "Unknown Album"
                    val durationMs = cursor.getLong(durationColumn)
                    val durationSec = (durationMs / 1000).toInt().coerceAtLeast(0)
                    val sizeBytes = cursor.getLong(sizeColumn)
                    val sizeMb = sizeBytes / (1024f * 1024f)
                    val ext = dataPath.substringAfterLast(".", "FLAC").lowercase()

                    val albumId = if (albumIdColumn != -1) cursor.getLong(albumIdColumn) else -1L
                    val albumArtUrl = if (albumId > 0) {
                        "content://media/external/audio/albumart/$albumId"
                    } else {
                        ""
                    }

                    val year = if (yearColumn != -1) cursor.getString(yearColumn) ?: "2024" else "2024"

                    val track = DownloadedTrack(
                        id = 0L,
                        tagsLoaded = false,
                        title = rawTitle,
                        artist = rawArtist,
                        album = rawAlbum,
                        durationSeconds = durationSec,
                        format = ext.uppercase(),
                        bitDepth = if (ext.equals("flac", true) || ext.equals("alac", true) || ext.equals("wav", true)) "16-bit" else "16-bit",
                        sampleRate = "44.1 kHz",
                        bitrateKbps = 0,
                        albumArtUrl = albumArtUrl,
                        localFilePath = dataPath,
                        fileSizeMb = sizeMb,
                        lyrics = "",
                        isrc = "",
                        releaseDate = year,
                        genre = "Audio",
                        providerSource = "MediaStore",
                        isFavorite = false,
                        dateAddedTimestamp = System.currentTimeMillis()
                    )

                    newTracks.add(track)
                    if (newTracks.size >= 100) {
                        trackDao.insertTracks(newTracks.toList())
                        addedCount += newTracks.size
                        newTracks.clear()
                    }
                }

                if (newTracks.isNotEmpty()) {
                    trackDao.insertTracks(newTracks)
                    addedCount += newTracks.size
                    newTracks.clear()
                }
            }
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "LibraryScannerRepository.scanMediaStore")
            Log.e("LibraryScannerRepo", "MediaStore scan error", e)
        }
        addedCount
    }

    suspend fun fullLibraryRescan(context: Context): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            count += scanMediaStore(context)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ObsidianCrashLogger.logException(e, "LibraryScannerRepository.fullLibraryRescan - mediaStore")
        }

        val folders = settingsRepository.settings.value.linkedMusicFolders
        for (folder in folders) {
            try {
                if (folder.first.startsWith("content://")) {
                    count += scanDirectoryTree(Uri.parse(folder.first), context)
                } else {
                    val path = if (folder.first.startsWith("file://")) folder.first.substring(7) else folder.first
                    count += scanLocalDirectory(File(path), context)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ObsidianCrashLogger.logException(e, "LibraryScannerRepository.fullLibraryRescan - folders")
            }
        }
        deduplicateDatabaseTracks()
        count
    }

    private suspend fun scanLocalDirectory(dir: File, context: Context): Int = withContext(Dispatchers.IO) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (!dir.absolutePath.contains(context.packageName) && ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("LibraryScannerRepo", "scanLocalDirectory skipped: Storage permission not granted for ${dir.absolutePath}.")
            return@withContext 0
        }

        if (!dir.exists() || !dir.isDirectory) return@withContext 0
        var indexedCount = 0
        val existingTracksList = trackDao.getAllTracksList()
        val existingPaths = existingTracksList.map { it.localFilePath }.toHashSet()
        val pendingFiles = mutableListOf<File>()

        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile && AudioMetadataExtractor.isSupportedAudio(file.name)) {
                    if (!existingPaths.contains(file.absolutePath)) {
                        pendingFiles.add(file)
                    }
                }
            }

            val newTracksBatch = mutableListOf<DownloadedTrack>()
            for (file in pendingFiles) {
                try {
                    val uri = Uri.fromFile(file)
                    val info = AudioMetadataExtractor.extract(context, uri, file.name, null)

                    newTracksBatch.add(DownloadedTrack(
                        id = 0L,
                        title = info.title,
                        artist = info.artist,
                        album = info.album,
                        durationSeconds = info.durationSeconds,
                        format = if (info.format.isNotBlank()) info.format else file.extension.uppercase(),
                        bitDepth = info.bitDepth,
                        sampleRate = info.sampleRate,
                        bitrateKbps = info.bitrateKbps,
                        albumArtUrl = info.albumArtPath,
                        localFilePath = file.absolutePath,
                        fileSizeMb = file.length() / (1024f * 1024f),
                        lyrics = info.lyrics,
                        isrc = "",
                        releaseDate = info.year,
                        genre = info.genre,
                        providerSource = "local",
                        isFavorite = false
                    ))
                    existingPaths.add(file.absolutePath)

                    if (newTracksBatch.size >= 50) {
                        trackDao.insertTracks(newTracksBatch.toList())
                        indexedCount += newTracksBatch.size
                        newTracksBatch.clear()
                    }
                } catch (e: Throwable) {
                    ObsidianCrashLogger.logException(e, "LibraryScannerRepository.scanLocalDirectory - file extract")
                    Log.e("LibraryScannerRepo", "File extract error", e)
                }
            }

            if (newTracksBatch.isNotEmpty()) {
                trackDao.insertTracks(newTracksBatch)
                indexedCount += newTracksBatch.size
            }
            deduplicateDatabaseTracks()
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "LibraryScannerRepository.scanLocalDirectory")
            Log.e("LibraryScannerRepo", "Local directory scan error", e)
        }
        indexedCount
    }

    suspend fun importAudioFile(uri: Uri, context: Context) = withContext(Dispatchers.IO) {
        try {
            var fileName = "imported_audio.flac"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        fileName = cursor.getString(index) ?: fileName
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "LibraryScannerRepository.importAudioFile - takePermission")
            }

            val savedPath = uri.toString()
            val extracted = try {
                AudioMetadataExtractor.extract(
                    context = context,
                    uri = uri,
                    fallbackName = fileName
                )
            } catch (e: Throwable) {
                ObsidianCrashLogger.logException(e, "LibraryScannerRepository.importAudioFile - extract")
                ExtractedAudioInfo(
                    title = fileName.substringBeforeLast("."),
                    artist = "Unknown Artist",
                    album = "Imported",
                    durationSeconds = 0,
                    bitDepth = "16-bit",
                    sampleRate = "44.1 kHz",
                    bitrateKbps = 1411,
                    albumArtPath = "",
                    lyrics = "",
                    year = "2024",
                    genre = "Imported",
                    trackNumber = 1
                )
            }

            val sizeMb = fileSize / (1024f * 1024f)
            val finalDuration = if (extracted.durationSeconds > 0) extracted.durationSeconds else 0
            val downloadedTrack = DownloadedTrack(
                id = 0L,
                title = extracted.title,
                artist = extracted.artist,
                album = extracted.album,
                durationSeconds = finalDuration,
                format = if (extracted.format.isNotBlank()) extracted.format else fileName.substringAfterLast(".", "FLAC").uppercase(),
                bitDepth = extracted.bitDepth,
                sampleRate = extracted.sampleRate,
                bitrateKbps = extracted.bitrateKbps,
                albumArtUrl = extracted.albumArtPath,
                localFilePath = savedPath,
                fileSizeMb = sizeMb,
                lyrics = extracted.lyrics,
                isrc = "",
                releaseDate = extracted.year,
                genre = if (extracted.genre.isNotBlank()) extracted.genre else "Imported",
                providerSource = "Direct Local Playback",
                isFavorite = false,
                dateAddedTimestamp = System.currentTimeMillis()
            )
            trackDao.insertTrack(downloadedTrack)
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "LibraryScannerRepository.importAudioFile")
            Log.e("LibraryScannerRepo", "Import audio file error", e)
        }
    }

    suspend fun importLocalAudioFiles(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        for (uri in uris) {
            try {
                importAudioFile(uri, context)
                importedCount++
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "LibraryScannerRepository.importLocalAudioFiles - uri: $uri")
            }
        }
        importedCount
    }
}
