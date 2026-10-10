package com.anuj.obsidian.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.util.AlacAudioDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class ExtractedAudioInfo(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationSeconds: Int,
    val bitDepth: String,
    val sampleRate: String,
    val bitrateKbps: Int,
    val albumArtPath: String,
    val lyrics: String,
    val trackNumber: Int,
    val year: String,
    val format: String = ""
)

object AudioMetadataExtractor {
    private const val TAG = "AudioMetadataExtractor"

    private fun sanitizeTag(input: String): String {
        // Strip typical junk like [1080p] or _id123
        return input.replace(Regex("\\[.*?\\]|\\(.*?\\)|_[a-zA-Z0-9]+$"), "").trim()
    }

    val ALL_SUPPORTED_EXTENSIONS = setOf(
        "flac", "wav", "mp3", "m4a", "aac", "ogg", "opus",
        "wma", "aiff", "aif", "alac", "dsf", "dff", "ape",
        "wv", "mpc", "mka", "mid", "midi", "ac3", "eac3",
        "dts", "m4b", "m4p", "oga", "spx", "caf", "3gp",
        "amr", "webm"
    )

    fun isSupportedAudio(nameOrExt: String): Boolean {
        val ext = nameOrExt.substringAfterLast(".", nameOrExt).lowercase()
        return ext in ALL_SUPPORTED_EXTENSIONS
    }

    /**
     * Saves raw embedded picture bytes with variable native resolution (up to 2048x2048 with ARGB_8888 32-bit color fidelity).
     * Retains 100% bit-exact original image bytes for standard/high-res artwork without lossy downscaling.
     */
    fun saveOptimizedAlbumArt(rawPic: ByteArray, targetFile: File) {
        try {
            if (rawPic.isEmpty()) return

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(rawPic, 0, rawPic.size, boundsOptions)
            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight

            // For artwork up to 2048x2048, write the exact original bytes directly to preserve 100% native quality
            if (origW in 1..2048 && origH in 1..2048 && rawPic.size <= 5 * 1024 * 1024) {
                FileOutputStream(targetFile).use { out ->
                    out.write(rawPic)
                    out.flush()
                }
                return
            }

            // For extreme resolution images (> 2048x2048), scale down smoothly with ARGB_8888 32-bit color
            var sampleSize = 1
            val maxDim = 2048
            if (origW > maxDim || origH > maxDim) {
                val halfW = origW / 2
                val halfH = origH / 2
                while ((halfW / sampleSize) >= maxDim && (halfH / sampleSize) >= maxDim) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(rawPic, 0, rawPic.size, decodeOptions)
            if (decoded != null) {
                FileOutputStream(targetFile).use { out ->
                    decoded.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    out.flush()
                }
                decoded.recycle()
            } else {
                FileOutputStream(targetFile).use { it.write(rawPic) }
            }
        } catch (e: Exception) {
            ObsidianCrashLogger.logWarning(TAG, "Bitmap compression failed, falling back to raw write: ${e.message}")
            try {
                FileOutputStream(targetFile).use { it.write(rawPic) }
            } catch (e2: Exception) {
                ObsidianCrashLogger.logException(e2, "AudioMetadataExtractor.saveOptimizedAlbumArt - raw write attempt failed")
            }
        }
    }

    /**
     * Extracts full audio metadata and embedded lyrics from FLAC, MP3, M4A, WAV, etc.
     */
    fun extract(
        context: Context,
        uri: Uri,
        fallbackName: String = "",
        docFile: DocumentFile? = null,
        localFilePath: String? = null
    ): ExtractedAudioInfo {
        var title = fallbackName.substringBeforeLast(".").ifBlank { "Unknown Track" }
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var genre = "Audio"
        var durationSeconds = 0
        var bitDepth = ""
        var sampleRate = ""
        var bitrateKbps = 0
        var albumArtPath = ""
        var embeddedLyrics = ""
        var trackNumber = 1
        var year = "2024"

        val ext = fallbackName.substringAfterLast(".", "").lowercase()

        // Smart name fallback: Check for "Artist - Title" or "01 - Artist - Title" pattern
        val rawBaseName = fallbackName.substringBeforeLast(".")
        if (rawBaseName.contains(" - ")) {
            val parts = rawBaseName.split(" - ")
            if (parts.size >= 3 && parts[0].trim().toIntOrNull() != null) {
                // "01 - Artist - Title"
                trackNumber = parts[0].trim().toIntOrNull() ?: 1
                artist = parts[1].trim()
                title = parts.drop(2).joinToString(" - ").trim()
            } else if (parts.size == 2) {
                // "Artist - Title"
                artist = parts[0].trim()
                title = parts[1].trim()
            }
        }

        // 1. JAudioTagger extraction for FLAC Vorbis / ID3 tags & Artwork
        try {
            val targetPathOrUri = if (localFilePath != null && File(localFilePath).exists()) localFilePath else uri.toString()
            val jAudioInfo = JaudiotaggerHelper.readDetailedMetadata(context, targetPathOrUri)
            if (jAudioInfo.title.isNotBlank()) title = sanitizeTag(jAudioInfo.title)
            if (jAudioInfo.artist.isNotBlank()) artist = sanitizeTag(jAudioInfo.artist)
            if (jAudioInfo.album.isNotBlank()) album = sanitizeTag(jAudioInfo.album)
            if (jAudioInfo.genre.isNotBlank()) genre = jAudioInfo.genre
            if (jAudioInfo.year.isNotBlank()) year = jAudioInfo.year
            if (jAudioInfo.trackNumber.isNotBlank()) trackNumber = jAudioInfo.trackNumber.toIntOrNull() ?: trackNumber
            if (jAudioInfo.sampleRateFormatted.isNotBlank()) sampleRate = jAudioInfo.sampleRateFormatted
            if (jAudioInfo.bitDepthFormatted.isNotBlank()) bitDepth = jAudioInfo.bitDepthFormatted
            if (jAudioInfo.bitrateKbps > 0) bitrateKbps = jAudioInfo.bitrateKbps
            if (jAudioInfo.durationSeconds > 0) durationSeconds = jAudioInfo.durationSeconds
            if (jAudioInfo.lyrics.isNotBlank()) embeddedLyrics = jAudioInfo.lyrics

            if (jAudioInfo.rawArtworkBytes != null && jAudioInfo.rawArtworkBytes.isNotEmpty()) {
                val artDir = File(context.cacheDir, "DirectArtCache").apply { mkdirs() }
                val artHash = (localFilePath ?: uri.toString()).hashCode()
                val thumb = File(artDir, "art_${artHash}.jpg")
                if (!thumb.exists()) {
                    saveOptimizedAlbumArt(jAudioInfo.rawArtworkBytes, thumb)
                }
                albumArtPath = thumb.absolutePath
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Jaudiotagger extraction notice: ${e.message}")
        }

        // 2. Android MediaMetadataRetriever (Native C++ framework extractor)
        try {
            val mmr = MediaMetadataRetriever()
            try {
                if (localFilePath != null && File(localFilePath).exists()) {
                    mmr.setDataSource(localFilePath)
                } else if (uri.scheme == "content" || uri.scheme == "file") {
                    mmr.setDataSource(context, uri)
                }
                val mmrTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!mmrTitle.isNullOrBlank() && (title == "Unknown Track" || title == fallbackName.substringBeforeLast("."))) {
                    title = sanitizeTag(mmrTitle)
                }
                val mmrArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                if (!mmrArtist.isNullOrBlank() && (artist == "Unknown Artist" || artist.isBlank())) {
                    artist = sanitizeTag(mmrArtist)
                }
                val mmrAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                if (!mmrAlbum.isNullOrBlank() && (album == "Unknown Album" || album.isBlank())) {
                    album = sanitizeTag(mmrAlbum)
                }
                val mmrGenre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                if (!mmrGenre.isNullOrBlank() && (genre == "Audio" || genre.isBlank())) {
                    genre = mmrGenre
                }
                val mmrYear = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                if (!mmrYear.isNullOrBlank() && (year == "2024" || year.isBlank())) {
                    year = mmrYear
                }
                val mmrTrackNum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                if (!mmrTrackNum.isNullOrBlank()) {
                    mmrTrackNum.toIntOrNull()?.let { trackNumber = it }
                }
                val mmrDuration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                if (mmrDuration != null && mmrDuration > 0) {
                    val extractedSec = (mmrDuration / 1000).toInt()
                    if (extractedSec > 0 && durationSeconds <= 0) {
                        durationSeconds = extractedSec
                    }
                }
                val mmrBitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                if (mmrBitrate != null && mmrBitrate > 0 && (bitrateKbps <= 0 || bitrateKbps == 1411)) {
                    bitrateKbps = mmrBitrate / 1000
                }
                if (albumArtPath.isBlank()) {
                    val pic = mmr.embeddedPicture
                    if (pic != null && pic.isNotEmpty()) {
                        val artDir = File(context.cacheDir, "DirectArtCache").apply { mkdirs() }
                        val artHash = (localFilePath ?: uri.toString()).hashCode()
                        val thumb = File(artDir, "art_${artHash}.jpg")
                        if (!thumb.exists()) {
                            saveOptimizedAlbumArt(pic, thumb)
                        }
                        albumArtPath = thumb.absolutePath
                    }
                }
            } finally {
                try { 
                    mmr.release() 
                } catch (e: Exception) {
                    ObsidianCrashLogger.logWarning(TAG, "Failed to release MMR: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "MediaMetadataRetriever fallback note: ${e.message}")
        }

        // 3. MediaStore system correlation query for missing metadata fields or system album artwork
        if (artist == "Unknown Artist" || album == "Unknown Album" || durationSeconds <= 0 || albumArtPath.isBlank()) {
            try {
                val projection = arrayOf(
                    android.provider.MediaStore.Audio.Media.TITLE,
                    android.provider.MediaStore.Audio.Media.ARTIST,
                    android.provider.MediaStore.Audio.Media.ALBUM,
                    android.provider.MediaStore.Audio.Media.DURATION,
                    android.provider.MediaStore.Audio.Media.YEAR,
                    android.provider.MediaStore.Audio.Media.ALBUM_ID
                )
                val searchPath = localFilePath ?: if (uri.scheme == "file") uri.path else null
                val cursor = if (!searchPath.isNullOrBlank()) {
                    context.contentResolver.query(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        "${android.provider.MediaStore.Audio.Media.DATA} = ?",
                        arrayOf(searchPath),
                        null
                    )
                } else if (fallbackName.isNotBlank()) {
                    context.contentResolver.query(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        "${android.provider.MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                        arrayOf(fallbackName),
                        null
                    )
                } else null

                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val titleIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE)
                        val artistIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST)
                        val albumIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ALBUM)
                        val durIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DURATION)
                        val yearIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.YEAR)
                        val albumIdIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ALBUM_ID)

                        if (titleIdx != -1 && (title == "Unknown Track" || title == fallbackName.substringBeforeLast("."))) {
                            c.getString(titleIdx)?.takeIf { it.isNotBlank() }?.let { title = sanitizeTag(it) }
                        }
                        if (artistIdx != -1 && (artist == "Unknown Artist" || artist.isBlank())) {
                            c.getString(artistIdx)?.takeIf { it.isNotBlank() }?.let { artist = sanitizeTag(it) }
                        }
                        if (albumIdx != -1 && (album == "Unknown Album" || album.isBlank())) {
                            c.getString(albumIdx)?.takeIf { it.isNotBlank() }?.let { album = sanitizeTag(it) }
                        }
                        if (durIdx != -1 && durationSeconds <= 0) {
                            val durMs = c.getLong(durIdx)
                            if (durMs > 0) durationSeconds = (durMs / 1000).toInt()
                        }
                        if (yearIdx != -1 && (year == "2024" || year.isBlank())) {
                            c.getString(yearIdx)?.takeIf { it.isNotBlank() }?.let { year = it }
                        }
                        if (albumIdIdx != -1 && albumArtPath.isBlank()) {
                            val albId = c.getLong(albumIdIdx)
                            if (albId > 0) {
                                val sArtworkUri = Uri.parse("content://media/external/audio/albumart/$albId")
                                albumArtPath = sArtworkUri.toString()
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioMetadataExtractor.extract - mediastore")
            }
        }

        // 4. MediaExtractor verification for authentic duration, sample rate, and bit depth
        try {
            val extractor = android.media.MediaExtractor()
            try {
                if (localFilePath != null && File(localFilePath).exists()) {
                    extractor.setDataSource(localFilePath)
                } else {
                    extractor.setDataSource(context, uri, null)
                }
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                            val sr = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                            if (sr > 0) {
                                sampleRate = if (sr >= 1000) {
                                    if (sr % 1000 == 0) "${sr / 1000}.0 kHz"
                                    else String.format(java.util.Locale.US, "%.1f kHz", sr / 1000.0)
                                } else "$sr Hz"
                            }
                        }
                        if (durationSeconds <= 0 && format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                            val durUs = format.getLong(android.media.MediaFormat.KEY_DURATION)
                            val durSec = (durUs / 1_000_000L).toInt()
                            if (durSec > 0) {
                                durationSeconds = durSec
                            }
                        }
                        if (format.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING)) {
                            val enc = format.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING)
                            val bits = when (enc) {
                                android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                                android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                                android.media.AudioFormat.ENCODING_PCM_32BIT -> 32
                                else -> 0
                            }
                            if (bits > 0) {
                                bitDepth = "$bits-bit"
                            }
                        }
                        if (format.containsKey("bits-per-sample")) {
                            val bps = format.getInteger("bits-per-sample")
                            if (bps > 0) {
                                bitDepth = "$bps-bit"
                            }
                        }
                        break
                    }
                }
            } finally {
                try { extractor.release() } catch (e: Throwable) {
                    Log.d(TAG, "extractor release failed: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "MediaExtractor verification note: ${e.message}")
        }
        val fileSizeLength = try {
            if (localFilePath != null && localFilePath.isNotBlank() && !localFilePath.startsWith("content://")) {
                java.io.File(localFilePath).length()
            } else if (docFile != null) {
                docFile.length()
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }
        } catch (e: Exception) {
            ObsidianCrashLogger.logWarning(TAG, "Failed to determine file size: ${e.message}")
            0L 
        }

        if (durationSeconds > 0 && fileSizeLength > 0) {
            bitrateKbps = ((fileSizeLength * 8L) / durationSeconds / 1000L).toInt()
        }

        val isAlacDetected = ext == "alac" ||
                AlacAudioDecoder.isAlacFile(context, if (localFilePath != null && File(localFilePath).exists()) localFilePath else uri.toString())

        val detectedFormat = when {
            isAlacDetected -> "ALAC"
            ext.isNotBlank() -> ext.uppercase()
            else -> "FLAC"
        }

        val isCompressed = !isAlacDetected && (ext == "m4a" || ext == "aac" || ext == "mp3" || ext == "ogg" || ext == "opus" || ext == "3gp")
        val isLossless = isAlacDetected || ext == "flac" || ext == "alac" || ext == "wav" || ext == "aiff" || ext == "aif" || ext == "dsf" || ext == "dff"

        if (isCompressed) {
            bitDepth = "" // Lossy compressed audio formats do NOT have a bit depth
            if (sampleRate.isBlank() || sampleRate.contains("96") || sampleRate.contains("192")) {
                sampleRate = when (ext) {
                    "ogg", "opus" -> "48.0 kHz"
                    else -> "44.1 kHz"
                }
            }
        } else if (isLossless) {
            if (bitDepth.isBlank() || bitDepth.equals("UNKNOWN", ignoreCase = true)) {
                bitDepth = when {
                    ext in listOf("dsf", "dff") -> "1-bit DSD"
                    isAlacDetected -> "16-bit" // Default baseline for lossless ALAC
                    else -> "16-bit" // Authentic baseline for lossless audio is standard 16-bit
                }
            }
            if (sampleRate.isBlank() || sampleRate.equals("UNKNOWN", ignoreCase = true)) {
                sampleRate = when (ext) {
                    "dsf", "dff" -> "2.8224 MHz (DSD64)"
                    "ogg", "opus" -> "48.0 kHz"
                    else -> "44.1 kHz"
                }
            }
        } else {
            bitDepth = ""
            if (sampleRate.isBlank()) sampleRate = "44.1 kHz"
        }

        if (bitrateKbps <= 0) {
            bitrateKbps = when {
                isAlacDetected -> 900
                ext == "flac" -> 956
                ext in listOf("wav", "aiff", "aif") -> 1411
                ext in listOf("dsf", "dff") -> 5645
                ext in listOf("m4a", "aac", "mp3") -> 320
                ext in listOf("ogg", "opus") -> 256
                else -> 320
            }
        }

        // 3. Read embedded Vorbis Comments from FLAC / OGG stream if still needed
        if (embeddedLyrics.isBlank()) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val flacTags = parseFlacVorbisComments(stream)
                    if (flacTags.isNotEmpty()) {
                        flacTags["TITLE"]?.takeIf { it.isNotBlank() }?.let { title = sanitizeTag(it) }
                        flacTags["ARTIST"]?.takeIf { it.isNotBlank() }?.let { artist = sanitizeTag(it) }
                        flacTags["ALBUM"]?.takeIf { it.isNotBlank() }?.let { album = sanitizeTag(it) }
                        flacTags["GENRE"]?.takeIf { it.isNotBlank() }?.let { genre = sanitizeTag(it) }
                        flacTags["DATE"]?.takeIf { it.isNotBlank() }?.let { year = sanitizeTag(it) }
                        flacTags["YEAR"]?.takeIf { it.isNotBlank() }?.let { year = sanitizeTag(it) }
                        flacTags["TRACKNUMBER"]?.toIntOrNull()?.let { trackNumber = it }

                        val vorbisLyrics = flacTags["LYRICS"]
                            ?: flacTags["UNSYNCEDLYRICS"]
                            ?: flacTags["SYNCEDLYRICS"]
                            ?: flacTags["UNSYNCED LYRICS"]
                            ?: flacTags["LYRICS_SYNCHRONIZED"]
                            ?: flacTags["DESCRIPTION"]

                        if (!vorbisLyrics.isNullOrBlank()) {
                            embeddedLyrics = vorbisLyrics.trim()
                        }
                    }
                }
            } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioMetadataExtractor.extract - attempt")
            
                Log.d(TAG, "Vorbis comment parse notice: ${e.message}")
            }
        }

        // 4. Look for companion .lrc file if still no lyrics found
        if (embeddedLyrics.isBlank()) {
            embeddedLyrics = findCompanionLrcLyrics(context, uri, fallbackName, docFile)
        }

        return ExtractedAudioInfo(
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            durationSeconds = durationSeconds,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            bitrateKbps = bitrateKbps,
            albumArtPath = albumArtPath,
            lyrics = embeddedLyrics,
            trackNumber = trackNumber,
            year = year,
            format = detectedFormat
        )
    }

    /**
     * Efficient FLAC Vorbis Comment block parser without loading full audio into memory
     */
    private fun parseFlacVorbisComments(inputStream: InputStream): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val header = ByteArray(4)
            if (inputStream.read(header) != 4) return result
            if (String(header, StandardCharsets.US_ASCII) != "fLaC") return result

            var isLast = false
            while (!isLast) {
                val blockHeader = ByteArray(4)
                if (inputStream.read(blockHeader) != 4) break

                val firstByte = blockHeader[0].toInt() and 0xFF
                isLast = (firstByte and 0x80) != 0
                val blockType = firstByte and 0x7F
                val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                        ((blockHeader[2].toInt() and 0xFF) shl 8) or
                        (blockHeader[3].toInt() and 0xFF)

                if (length < 0 || length > 12 * 1024 * 1024) { // Cap metadata block size at 12MB to prevent NegativeArraySizeException or OutOfMemoryError
                    break
                }

                if (blockType == 4) { // VORBIS_COMMENT block
                    val buffer = ByteArray(length)
                    var readTotal = 0
                    while (readTotal < length) {
                        val r = inputStream.read(buffer, readTotal, length - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                    if (readTotal == length && length >= 4) {
                        val byteBuf = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                        val vendorLen = byteBuf.int
                        if (vendorLen in 0..byteBuf.remaining()) {
                            byteBuf.position(byteBuf.position() + vendorLen)
                            if (byteBuf.remaining() >= 4) {
                                val commentCount = byteBuf.int
                                for (i in 0 until commentCount) {
                                    if (byteBuf.remaining() < 4) break
                                    val len = byteBuf.int
                                    if (len < 0 || len > byteBuf.remaining()) break
                                    val commentBytes = ByteArray(len)
                                    byteBuf.get(commentBytes)
                                    val comment = String(commentBytes, StandardCharsets.UTF_8)
                                    val eqIdx = comment.indexOf('=')
                                    if (eqIdx > 0) {
                                        val key = comment.substring(0, eqIdx).uppercase().trim()
                                        val value = comment.substring(eqIdx + 1).trim()
                                        result[key] = value
                                    }
                                }
                            }
                        }
                    }
                    break
                } else {
                    var skipped = 0L
                    while (skipped < length) {
                        val s = inputStream.skip(length - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                }
            }
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioMetadataExtractor.parseFlacVorbisComments - attempt")
            }
        return result
    }

    /**
     * Finds companion .lrc or .txt in the same directory for direct DocumentFiles or filesystem Files
     */
    private fun findCompanionLrcLyrics(
        context: Context,
        uri: Uri,
        fallbackName: String,
        docFile: DocumentFile?
    ): String {
        try {
            val parent = docFile?.parentFile
            if (parent != null) {
                val baseName = fallbackName.substringBeforeLast(".")
                val lrcName1 = "$baseName.lrc"
                val lrcName2 = "$fallbackName.lrc"
                val txtName = "$baseName.txt"

                val match = parent.findFile(lrcName1) ?: parent.findFile(lrcName2) ?: parent.findFile(txtName)
                if (match != null && match.isFile) {
                    context.contentResolver.openInputStream(match.uri)?.use { input ->
                        val byteBuffer = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        var read: Int
                        var totalRead = 0
                        while (input.read(buffer).also { read = it } != -1) {
                            byteBuffer.write(buffer, 0, read)
                            totalRead += read
                            if (totalRead > 1024 * 1024) {
                                break
                            }
                        }
                        val bytes = byteBuffer.toByteArray()
                        val text = String(bytes, StandardCharsets.UTF_8)
                        if (text.isNotBlank()) return text.trim()
                    }
                }
            }

            val path = uri.path
            if (path != null && !uri.toString().startsWith("content://")) {
                val file = File(path)
                val baseName = file.nameWithoutExtension
                val dir = file.parentFile
                if (dir != null && dir.exists()) {
                    val lrcFile1 = File(dir, "$baseName.lrc")
                    val lrcFile2 = File(dir, "${file.name}.lrc")
                    if (lrcFile1.exists()) return lrcFile1.readText(StandardCharsets.UTF_8).trim()
                    if (lrcFile2.exists()) return lrcFile2.readText(StandardCharsets.UTF_8).trim()
                }
            }
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioMetadataExtractor.findCompanionLrcLyrics - attempt")
            }
        return ""
    }
}
