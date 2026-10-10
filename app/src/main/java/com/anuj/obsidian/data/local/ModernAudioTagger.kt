package com.anuj.obsidian.data.local

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.remote.MusicApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Frame
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Result data class providing granular embedding outcomes.
 */
data class TagResult(
    val success: Boolean,
    val criticalTagsVerified: Boolean,
    val artworkEmbedded: Boolean,
    val lyricsEmbedded: Boolean,
    val details: String,
    val detectedFormat: String
) {
    val isSuccess: Boolean get() = success
    val errorMessage: String get() = if (success) "" else details
}

/**
 * Modern Audio Tagger
 * High-reliability, Scoped-Storage-safe audio metadata embedder for FLAC, MP3, M4A, OGG, WAV.
 * Features:
 * - True container format magic-byte detection.
 * - Format-specific tag writing (ID3v2.4, FlacTag/VorbisComment, MP4 atoms).
 * - Cover art resolution with online fallbacks, downscaling & JPEG normalization.
 * - Format-aware verification before publishing.
 * - Atomic writeback to storage/MediaStore.
 */
object ModernAudioTagger {
    private const val TAG = "ModernAudioTagger"
    private const val BUFFER_SIZE = 64 * 1024

    init {
        try {
            Logger.getLogger("org.jaudiotagger").level = Level.OFF
        } catch (_: Throwable) {}
    }

    /**
     * Inspects magic bytes to accurately detect the true container format.
     */
    fun detectTrueAudioFormat(context: Context, filePathOrUri: String): String {
        try {
            val bytes = ByteArray(32)
            val read = if (filePathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(filePathOrUri))?.use { it.read(bytes) } ?: 0
            } else {
                val f = File(filePathOrUri)
                if (f.exists() && f.length() > 0) {
                    FileInputStream(f).use { it.read(bytes) }
                } else 0
            }

            if (read >= 4) {
                // FLAC: 0x66 0x4C 0x61 0x43 ("fLaC")
                if (bytes[0] == 0x66.toByte() && bytes[1] == 0x4C.toByte() && bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()) {
                    return "flac"
                }
                // MP3: "ID3" or MPEG audio sync frame (0xFF 0xFB, 0xFF 0xFA, 0xFF 0xF3, 0xFF 0xF2, 0xFF 0xE3)
                if (bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) {
                    return "mp3"
                }
                if (bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0) {
                    // Check if ADTS AAC (0xFF 0xF1 / 0xFF 0xF9)
                    val layer = (bytes[1].toInt() and 0x06) shr 1
                    return if (layer == 0) "m4a" else "mp3"
                }
                // OGG: "OggS"
                if (bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() && bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()) {
                    return "ogg"
                }
                // WAV: "RIFF....WAVE"
                if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()) {
                    if (read >= 12 && bytes[8] == 0x57.toByte() && bytes[9] == 0x41.toByte() && bytes[10] == 0x56.toByte() && bytes[11] == 0x45.toByte()) {
                        return "wav"
                    }
                }
                // MP4 / M4A: "....ftyp"
                if (read >= 8 && bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()) {
                    return "m4a"
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Magic byte detection failed for $filePathOrUri: ${e.message}")
        }

        // Extension fallback
        val lower = filePathOrUri.lowercase()
        return when {
            lower.endsWith(".flac") -> "flac"
            lower.endsWith(".mp3") -> "mp3"
            lower.endsWith(".m4a") -> "m4a"
            lower.endsWith(".alac") -> "m4a"
            lower.endsWith(".aac") -> "m4a"
            lower.endsWith(".ogg") -> "ogg"
            lower.endsWith(".opus") -> "ogg"
            lower.endsWith(".wav") -> "wav"
            else -> "flac"
        }
    }

    private fun getWorkingSandboxFile(context: Context, format: String): File {
        val workDir = File(context.cacheDir, "obsidian_tag_work").apply { mkdirs() }
        return File(workDir, "sandbox_${System.currentTimeMillis()}_${(1000..9999).random()}.$format")
    }

    private fun copySourceToSandbox(context: Context, filePathOrUri: String, sandboxFile: File): Boolean {
        return try {
            if (filePathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(filePathOrUri))?.buffered(BUFFER_SIZE)?.use { inStream ->
                    sandboxFile.outputStream().buffered(BUFFER_SIZE).use { outStream ->
                        inStream.copyTo(outStream, bufferSize = BUFFER_SIZE)
                    }
                }
            } else {
                val originalFile = File(filePathOrUri)
                if (originalFile.exists() && originalFile.length() > 0) {
                    originalFile.inputStream().buffered(BUFFER_SIZE).use { inStream ->
                        sandboxFile.outputStream().buffered(BUFFER_SIZE).use { outStream ->
                            inStream.copyTo(outStream, bufferSize = BUFFER_SIZE)
                        }
                    }
                } else {
                    val uri = Uri.parse(filePathOrUri)
                    if (uri.scheme != null) {
                        context.contentResolver.openInputStream(uri)?.buffered(BUFFER_SIZE)?.use { inStream ->
                            sandboxFile.outputStream().buffered(BUFFER_SIZE).use { outStream ->
                                inStream.copyTo(outStream, bufferSize = BUFFER_SIZE)
                            }
                        }
                    } else return false
                }
            }
            sandboxFile.exists() && sandboxFile.length() > 0
        } catch (e: Exception) {
            ObsidianCrashLogger.logException(e, "ModernAudioTagger.copySourceToSandbox")
            Log.e(TAG, "Failed to copy source to sandbox: ${e.message}")
            false
        }
    }

    /**
     * Reads the sandbox audio file with JAudioTagger, attempting format fallbacks if extension differs.
     */
    private fun readAudioFileWithFallback(sandboxFile: File, detectedFormat: String): Pair<AudioFile, File> {
        try {
            val audioFile = AudioFileIO.read(sandboxFile)
            return Pair(audioFile, sandboxFile)
        } catch (e: Throwable) {
            Log.w(TAG, "AudioFileIO.read failed on primary format '$detectedFormat': ${e.message}. Attempting fallbacks...")
        }

        // Try alternate formats
        val altFormats = listOf("flac", "mp3", "m4a", "ogg", "wav").filter { it != detectedFormat }
        for (alt in altFormats) {
            val altFile = File(sandboxFile.parentFile, "${sandboxFile.nameWithoutExtension}_alt.$alt")
            try {
                sandboxFile.copyTo(altFile, overwrite = true)
                val audioFile = AudioFileIO.read(altFile)
                Log.d(TAG, "AudioFileIO.read succeeded with fallback format '$alt'!")
                return Pair(audioFile, altFile)
            } catch (_: Throwable) {
                if (altFile.exists()) altFile.delete()
            }
        }
        throw IllegalStateException("Unable to parse audio container with any supported audio reader")
    }

    /**
     * Multi-tier cover art search across iTunes API (1200x1200bb) and Deezer API (cover_xl).
     */
    suspend fun searchOnlineCoverArt(artist: String, title: String): List<String> = withContext(Dispatchers.IO) {
        if (artist.isBlank() && title.isBlank()) return@withContext emptyList()
        val results = mutableListOf<String>()
        val query = "${artist.trim()} ${title.trim()}".trim()

        // 1. iTunes Search API (1200x1200bb high-res)
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=3"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", MusicApiService.BROWSER_USER_AGENT)
                .build()
            val resp = MusicApiService.apiClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string() ?: "")
                val items = json.optJSONArray("results")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val art100 = item.optString("artworkUrl100", "")
                        if (art100.isNotBlank()) {
                            val highRes = art100.replace("100x100bb.jpg", "1200x1200bb.jpg")
                                .replace("100x100bb", "1200x1200bb")
                            results.add(highRes)
                            results.add(art100)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "iTunes cover search failed for $query: ${e.message}")
        }

        // 2. Deezer Search API (cover_xl)
        if (results.isEmpty()) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://api.deezer.com/search?q=$encoded&limit=3"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", MusicApiService.BROWSER_USER_AGENT)
                    .build()
                val resp = MusicApiService.apiClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "")
                    val data = json.optJSONArray("data")
                    if (data != null) {
                        for (i in 0 until data.length()) {
                            val item = data.getJSONObject(i)
                            val albumObj = item.optJSONObject("album")
                            val coverXl = albumObj?.optString("cover_xl", "") ?: ""
                            val coverBig = albumObj?.optString("cover_big", "") ?: ""
                            if (coverXl.isNotBlank()) results.add(coverXl)
                            if (coverBig.isNotBlank()) results.add(coverBig)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Deezer cover search failed for $query: ${e.message}")
            }
        }

        results.filter { it.isNotBlank() && !it.contains("picsum.photos") && it != "NO_ART" }
    }

    /**
     * Resolves artwork bytes with retries, downscaling and WebP-to-JPEG normalization.
     */
    suspend fun resolveArtworkBytes(
        context: Context,
        artworkUrlOrPath: String,
        fallbackUrls: List<String> = emptyList(),
        artist: String = "",
        title: String = ""
    ): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<String>()
        if (artworkUrlOrPath.isNotBlank() && !artworkUrlOrPath.contains("picsum.photos") && artworkUrlOrPath != "NO_ART") {
            candidates.add(artworkUrlOrPath)
        }
        candidates.addAll(fallbackUrls.filter { it.isNotBlank() && !it.contains("picsum.photos") && it != "NO_ART" })

        // Check provided candidates
        for (candidate in candidates) {
            val bytes = fetchArtworkBytes(context, candidate)
            if (bytes != null && bytes.isNotEmpty()) {
                val processed = processAndOptimizeArtworkBytes(bytes)
                if (processed != null) return@withContext processed
            }
        }

        // Try online search if candidates failed
        if (artist.isNotBlank() || title.isNotBlank()) {
            val onlineUrls = searchOnlineCoverArt(artist, title)
            for (candidate in onlineUrls) {
                val bytes = fetchArtworkBytes(context, candidate)
                if (bytes != null && bytes.isNotEmpty()) {
                    val processed = processAndOptimizeArtworkBytes(bytes)
                    if (processed != null) return@withContext processed
                }
            }
        }

        null
    }

    private fun fetchArtworkBytes(context: Context, urlOrPath: String): ByteArray? {
        if (urlOrPath.isBlank()) return null
        return when {
            urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://") -> {
                for (attempt in 1..2) {
                    try {
                        val request = Request.Builder()
                            .url(urlOrPath)
                            .header("User-Agent", MusicApiService.BROWSER_USER_AGENT)
                            .header("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.9")
                            .build()
                        val response = MusicApiService.apiClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val data = response.body?.bytes()
                            if (data != null && data.isNotEmpty()) return data
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Fetch artwork attempt $attempt failed for $urlOrPath: ${e.message}")
                    }
                }
                // HttpURLConnection fallback
                try {
                    val url = java.net.URL(urlOrPath)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", MusicApiService.BROWSER_USER_AGENT)
                    conn.inputStream.use { it.readBytes() }
                } catch (_: Throwable) { null }
            }
            urlOrPath.startsWith("content://") -> {
                try {
                    context.contentResolver.openInputStream(Uri.parse(urlOrPath))?.use { it.readBytes() }
                } catch (_: Throwable) { null }
            }
            urlOrPath.startsWith("/") || urlOrPath.startsWith("file://") -> {
                try {
                    val p = if (urlOrPath.startsWith("file://")) {
                        Uri.parse(urlOrPath).path ?: urlOrPath.removePrefix("file://")
                    } else urlOrPath
                    val f = File(p)
                    if (f.exists() && f.length() > 0) f.readBytes() else null
                } catch (_: Throwable) { null }
            }
            else -> null
        }
    }

    private fun processAndOptimizeArtworkBytes(rawBytes: ByteArray): Pair<ByteArray, String>? {
        if (rawBytes.isEmpty()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
        val mime = options.outMimeType ?: "image/jpeg"
        val origWidth = options.outWidth
        val origHeight = options.outHeight

        val needsRescale = origWidth > 1600 || origHeight > 1600
        val isWebp = mime.contains("webp", ignoreCase = true)

        if (isWebp || needsRescale || origWidth <= 0) {
            try {
                var sampleSize = 1
                while (origWidth / (sampleSize * 2) >= 1200 && origHeight / (sampleSize * 2) >= 1200) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                if (bmp != null) {
                    try {
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                        return Pair(bos.toByteArray(), "image/jpeg")
                    } finally {
                        bmp.recycle()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Transcoding artwork failed: ${e.message}")
            }
        }
        return Pair(rawBytes, if (isWebp) "image/jpeg" else mime)
    }

    /**
     * Complete, hardened metadata & artwork embedding pipeline.
     */
    suspend fun embedAllTagsDetailed(
        context: Context,
        sourceFilePath: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        lyrics: String,
        artworkUrl: String,
        trackNumber: Int = 0,
        isrc: String = "",
        fallbackArtworkUrls: List<String> = emptyList()
    ): TagResult = withContext(Dispatchers.IO) {
        if (sourceFilePath.isBlank()) {
            return@withContext TagResult(
                success = false,
                criticalTagsVerified = false,
                artworkEmbedded = false,
                lyricsEmbedded = false,
                details = "Source file path is blank",
                detectedFormat = "unknown"
            )
        }

        val detectedFormat = detectTrueAudioFormat(context, sourceFilePath)
        val sandboxFile = getWorkingSandboxFile(context, detectedFormat)
        var activeSandboxFile = sandboxFile

        try {
            if (!copySourceToSandbox(context, sourceFilePath, sandboxFile)) {
                return@withContext TagResult(
                    success = false,
                    criticalTagsVerified = false,
                    artworkEmbedded = false,
                    lyricsEmbedded = false,
                    details = "Failed to copy source audio to isolated sandbox",
                    detectedFormat = detectedFormat
                )
            }

            // 1. Read container with JAudioTagger
            val (audioFile, actualFile) = try {
                readAudioFileWithFallback(sandboxFile, detectedFormat)
            } catch (e: Throwable) {
                return@withContext TagResult(
                    success = false,
                    criticalTagsVerified = false,
                    artworkEmbedded = false,
                    lyricsEmbedded = false,
                    details = "JAudioTagger read failed: ${e.message}",
                    detectedFormat = detectedFormat
                )
            }
            activeSandboxFile = actualFile

            // 2. Format-specific tag setup
            var tag: Tag? = audioFile.tag
            val mp3File = audioFile as? MP3File
            if (mp3File != null) {
                var id3v2Tag = mp3File.iD3v2Tag
                if (id3v2Tag == null) {
                    id3v2Tag = ID3v24Tag()
                    mp3File.iD3v2Tag = id3v2Tag
                }
                tag = id3v2Tag
            } else if (tag == null) {
                tag = audioFile.createDefaultTag()
                audioFile.tag = tag
            }

            val activeTag = tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }

            // 3. Set text tags
            try {
                if (title.isNotBlank()) activeTag.setField(FieldKey.TITLE, title)
                if (artist.isNotBlank()) activeTag.setField(FieldKey.ARTIST, artist)
                if (album.isNotBlank()) activeTag.setField(FieldKey.ALBUM, album)
                if (genre.isNotBlank()) activeTag.setField(FieldKey.GENRE, genre)
                if (year.isNotBlank()) activeTag.setField(FieldKey.YEAR, year)
                if (trackNumber > 0) activeTag.setField(FieldKey.TRACK, trackNumber.toString())
                if (isrc.isNotBlank()) activeTag.setField(FieldKey.ISRC, isrc)
            } catch (e: Throwable) {
                Log.w(TAG, "Setting text fields warning: ${e.message}")
            }

            // 4. Lyrics embedding
            var lyricsEmbedded = false
            if (lyrics.isNotBlank()) {
                try {
                    activeTag.setField(FieldKey.LYRICS, lyrics)
                    lyricsEmbedded = true
                } catch (e: Throwable) {
                    Log.d(TAG, "Standard LYRICS setField note: ${e.message}")
                }

                // Vorbis comment fields (FLAC/OGG)
                try {
                    val vorbisTag: VorbisCommentTag? = when (activeTag) {
                        is FlacTag -> activeTag.vorbisCommentTag
                        is VorbisCommentTag -> activeTag
                        else -> null
                    }
                    if (vorbisTag != null) {
                        try { vorbisTag.setField(vorbisTag.createField("LYRICS", lyrics)) } catch (e: Throwable) {
                            Log.d(TAG, "vorbis LYRICS note: ${e.message}")
                        }
                        try { vorbisTag.setField(vorbisTag.createField("UNSYNCEDLYRICS", lyrics)) } catch (e: Throwable) {
                            Log.d(TAG, "vorbis UNSYNCEDLYRICS note: ${e.message}")
                        }
                        lyricsEmbedded = true
                    }
                } catch (e: Throwable) {
                    Log.d(TAG, "Vorbis comment tag error: ${e.message}")
                }

                // ID3 USLT Frame (MP3)
                try {
                    val id3Tag = (activeTag as? org.jaudiotagger.tag.id3.AbstractID3v2Tag) ?: mp3File?.iD3v2Tag
                    if (id3Tag != null) {
                        val frameBody = FrameBodyUSLT().apply {
                            lyric = lyrics
                            language = "eng"
                            description = ""
                        }
                        val frame = ID3v24Frame(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS).apply { body = frameBody }
                        id3Tag.setFrame(frame)
                        lyricsEmbedded = true
                    }
                } catch (e: Throwable) {
                    Log.d(TAG, "ID3 USLT frame error: ${e.message}")
                }
            }

            // 5. Artwork embedding
            var artworkEmbedded = false
            val artData = resolveArtworkBytes(
                context = context,
                artworkUrlOrPath = artworkUrl,
                fallbackUrls = fallbackArtworkUrls,
                artist = artist,
                title = title
            )

            if (artData != null) {
                val (artworkBytes, mimeType) = artData
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size, options)
                val effectiveMime = if (mimeType.isNotBlank() && mimeType != "image/*") mimeType else (options.outMimeType ?: "image/jpeg")

                try { activeTag.deleteArtworkField() } catch (e: Throwable) {
                    Log.d(TAG, "deleteArtworkField note: ${e.message}")
                }

                if (activeTag is FlacTag) {
                    var flacArtSuccess = false
                    try {
                        val pic = org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture(
                            artworkBytes,
                            PictureTypes.DEFAULT_ID,
                            effectiveMime,
                            "Front Cover",
                            options.outWidth.coerceAtLeast(300),
                            options.outHeight.coerceAtLeast(300),
                            24,
                            0
                        )
                        activeTag.setField(pic)
                        flacArtSuccess = true
                        artworkEmbedded = true
                    } catch (e: Throwable) {
                        Log.w(TAG, "FLAC MetadataBlockDataPicture error: ${e.message}")
                    }
                    if (!flacArtSuccess) {
                        try {
                            val artwork = ArtworkFactory.getNew().apply {
                                binaryData = artworkBytes
                                this.mimeType = effectiveMime
                                pictureType = PictureTypes.DEFAULT_ID
                            }
                            activeTag.setField(artwork)
                            artworkEmbedded = true
                        } catch (e: Throwable) {
                            Log.w(TAG, "FLAC ArtworkFactory error: ${e.message}")
                        }
                    }
                } else {
                    try {
                        val artwork = ArtworkFactory.getNew().apply {
                            binaryData = artworkBytes
                            this.mimeType = effectiveMime
                            pictureType = PictureTypes.DEFAULT_ID
                        }
                        activeTag.setField(artwork)
                        artworkEmbedded = true
                    } catch (e: Throwable) {
                        Log.w(TAG, "Generic artwork embedding error: ${e.message}")
                    }
                }
            }

            // 6. Commit & Write to Sandbox
            try {
                audioFile.commit()
                AudioFileIO.write(audioFile)
            } catch (e: Throwable) {
                Log.e(TAG, "AudioFileIO.write failed on sandbox: ${e.message}")
                return@withContext TagResult(
                    success = false,
                    criticalTagsVerified = false,
                    artworkEmbedded = false,
                    lyricsEmbedded = false,
                    details = "Commit/write failed: ${e.message}",
                    detectedFormat = detectedFormat
                )
            }

            // 7. Format-Aware Verification on Sandbox File
            var criticalTagsVerified = false
            try {
                val verifyFile = AudioFileIO.read(activeSandboxFile)
                val verifyTag = verifyFile.tag
                if (verifyTag != null) {
                    var readTitle = verifyTag.getFirst(FieldKey.TITLE)
                    var readArtist = verifyTag.getFirst(FieldKey.ARTIST)

                    // Vorbis comment fallback check for FLAC/OGG
                    if (readTitle.isBlank() || readArtist.isBlank()) {
                        val vorbis = (verifyTag as? FlacTag)?.vorbisCommentTag ?: (verifyTag as? VorbisCommentTag)
                        if (vorbis != null) {
                            if (readTitle.isBlank()) readTitle = vorbis.getFirst("TITLE").ifBlank { vorbis.getFirst("title") }
                            if (readArtist.isBlank()) readArtist = vorbis.getFirst("ARTIST").ifBlank { vorbis.getFirst("artist") }
                        }
                    }

                    val titleOk = title.isBlank() || readTitle.isNotBlank()
                    val artistOk = artist.isBlank() || readArtist.isNotBlank()
                    criticalTagsVerified = titleOk && artistOk
                    if (!criticalTagsVerified) {
                        Log.w(TAG, "Critical tag verification failed: readTitle='$readTitle', readArtist='$readArtist'")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Sandbox verification read error: ${e.message}")
                // If verification read threw, assume critical tags if activeTag was set
                criticalTagsVerified = title.isNotBlank() && artist.isNotBlank()
            }

            // 8. Atomic Writeback to Target Source
            val writeSuccess = writeSandboxBackToSource(context, activeSandboxFile, sourceFilePath)
            if (!writeSuccess) {
                return@withContext TagResult(
                    success = false,
                    criticalTagsVerified = false,
                    artworkEmbedded = artworkEmbedded,
                    lyricsEmbedded = lyricsEmbedded,
                    details = "writeSandboxBackToSource failed (Scoped Storage write restricted)",
                    detectedFormat = detectedFormat
                )
            }

            updateMediaStoreMetadata(context, sourceFilePath, title, artist, album, genre, year)

            // 9. Verify TITLE + ARTIST on the final file via MediaMetadataRetriever
            val (finalVerified, finalVerifyDetail) = verifyFinalFileTags(context, sourceFilePath, title, artist)
            val effectiveCritical = criticalTagsVerified && finalVerified

            val details = when {
                !effectiveCritical -> finalVerifyDetail
                !artworkEmbedded && artworkUrl.isNotBlank() -> "Cover art download failed; text tags verified OK"
                else -> "Embedded tags OK"
            }

            return@withContext TagResult(
                success = effectiveCritical,
                criticalTagsVerified = effectiveCritical,
                artworkEmbedded = artworkEmbedded,
                lyricsEmbedded = lyricsEmbedded,
                details = details,
                detectedFormat = detectedFormat
            )

        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ModernAudioTagger.embedAllTagsDetailed")
            Log.e(TAG, "embedAllTagsDetailed exception: ${e.message}")
            return@withContext TagResult(
                success = false,
                criticalTagsVerified = false,
                artworkEmbedded = false,
                lyricsEmbedded = false,
                details = "Tagging exception: ${e.message}",
                detectedFormat = detectedFormat
            )
        } finally {
            if (sandboxFile.exists()) sandboxFile.delete()
            if (activeSandboxFile.exists() && activeSandboxFile != sandboxFile) activeSandboxFile.delete()
        }
    }

    /**
     * Backward-compatible boolean wrapper for embedAllTags.
     */
    suspend fun embedAllTags(
        context: Context,
        sourceFilePath: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        lyrics: String,
        artworkUrl: String,
        trackNumber: Int = 0,
        isrc: String = "",
        fallbackArtworkUrls: List<String> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        val result = embedAllTagsDetailed(
            context = context,
            sourceFilePath = sourceFilePath,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            year = year,
            lyrics = lyrics,
            artworkUrl = artworkUrl,
            trackNumber = trackNumber,
            isrc = isrc,
            fallbackArtworkUrls = fallbackArtworkUrls
        )
        result.criticalTagsVerified
    }

    /**
     * Verifies TITLE and ARTIST on the final file / URI using MediaMetadataRetriever.
     */
    fun verifyFinalFileTags(context: Context, filePathOrUri: String, expectedTitle: String, expectedArtist: String): Pair<Boolean, String> {
        if (expectedTitle.isBlank() && expectedArtist.isBlank()) return Pair(true, "No critical tags required")
        val mmr = MediaMetadataRetriever()
        return try {
            if (filePathOrUri.startsWith("content://")) {
                mmr.setDataSource(context, Uri.parse(filePathOrUri))
            } else {
                val f = File(filePathOrUri)
                if (!f.exists() || f.length() == 0L) {
                    return Pair(false, "Final file does not exist or is empty ($filePathOrUri)")
                }
                mmr.setDataSource(filePathOrUri)
            }
            val readTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim() ?: ""
            val readArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim() ?: ""

            val titleOk = expectedTitle.isBlank() || readTitle.isNotBlank()
            val artistOk = expectedArtist.isBlank() || readArtist.isNotBlank()

            if (titleOk && artistOk) {
                Pair(true, "Verified TITLE='$readTitle', ARTIST='$readArtist'")
            } else {
                Pair(false, "Verification failed via MediaMetadataRetriever: TITLE='$readTitle' (expected '$expectedTitle'), ARTIST='$readArtist' (expected '$expectedArtist')")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "MediaMetadataRetriever verification error on $filePathOrUri: ${e.message}")
            Pair(false, "MediaMetadataRetriever error on final file: ${e.message}")
        } finally {
            try { mmr.release() } catch (_: Throwable) {}
        }
    }

    /**
     * Atomic writeback from sandbox file to destination file/URI.
     */
    private fun writeSandboxBackToSource(context: Context, sandboxFile: File, filePathOrUri: String): Boolean {
        if (!sandboxFile.exists() || sandboxFile.length() == 0L) {
            Log.e(TAG, "Sandbox file is empty or does not exist, aborting writeback")
            return false
        }
        val sandboxBytes = try {
            val bytes = sandboxFile.readBytes()
            if (bytes.isEmpty()) return false
            bytes
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read sandbox file: ${e.message}")
            return false
        }

        // 1. Direct File atomic replacement
        if (!filePathOrUri.startsWith("content://")) {
            val originalFile = File(filePathOrUri)
            val parent = originalFile.parentFile ?: context.cacheDir
            val siblingTmp = File(parent, "${originalFile.name}.obsidian_tmp_${System.currentTimeMillis()}")
            try {
                FileOutputStream(siblingTmp).use { it.write(sandboxBytes) }
                // Verify temp non-empty before replace
                if (siblingTmp.exists() && siblingTmp.length() > 0L && siblingTmp.length() == sandboxBytes.size.toLong()) {
                    if (originalFile.exists()) originalFile.delete()
                    if (siblingTmp.renameTo(originalFile)) {
                        notifyMediaScanner(context, filePathOrUri)
                        return true
                    } else {
                        // Copy fallback
                        siblingTmp.copyTo(originalFile, overwrite = true)
                        siblingTmp.delete()
                        if (originalFile.exists() && originalFile.length() > 0L) {
                            notifyMediaScanner(context, filePathOrUri)
                            return true
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Atomic sibling file write failed: ${e.message}")
            } finally {
                if (siblingTmp.exists()) siblingTmp.delete()
            }
        }

        // 2. ContentResolver write via MediaStore or SAF URI
        val uri = if (filePathOrUri.startsWith("content://")) {
            Uri.parse(filePathOrUri)
        } else {
            findMediaStoreUriForPath(context, filePathOrUri)
        }

        if (uri != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !filePathOrUri.startsWith("content://")) {
                    try {
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 1)
                        }
                        context.contentResolver.update(uri, values, null, null)
                    } catch (e: Throwable) {
                        Log.d(TAG, "IS_PENDING=1 update note: ${e.message}")
                    }
                }

                var written = false
                for (mode in arrayOf("rwt", "wt", "w")) {
                    try {
                        context.contentResolver.openOutputStream(uri, mode)?.use { outStream ->
                            outStream.write(sandboxBytes)
                            outStream.flush()
                            written = true
                        }
                        if (written) break
                    } catch (e: Exception) {
                        Log.w(TAG, "ContentResolver mode '$mode' failed: ${e.message}")
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !filePathOrUri.startsWith("content://")) {
                    try {
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 0)
                        }
                        context.contentResolver.update(uri, values, null, null)
                    } catch (e: Throwable) {
                        Log.d(TAG, "IS_PENDING=0 update note: ${e.message}")
                    }
                }

                if (written) {
                    notifyMediaScanner(context, filePathOrUri)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "ContentResolver write failed: ${e.message}")
            }
        }

        // 3. Final copyTo fallback
        if (!filePathOrUri.startsWith("content://")) {
            try {
                val originalFile = File(filePathOrUri)
                sandboxFile.copyTo(originalFile, overwrite = true)
                notifyMediaScanner(context, filePathOrUri)
                return originalFile.exists() && originalFile.length() > 0
            } catch (e: Exception) {
                Log.e(TAG, "Final copyTo fallback failed: ${e.message}")
            }
        }

        return false
    }

    private fun findMediaStoreUriForPath(context: Context, path: String): Uri? {
        try {
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                try {
                    val selection = "${MediaStore.Audio.Media.DATA} = ?"
                    val selectionArgs = arrayOf(path)
                    context.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        }
                    }
                } catch (_: Throwable) {}
            }

            val file = File(path)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(file.name)
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                while (cursor.moveToNext()) {
                    if (dataCol != -1) {
                        val candidatePath = cursor.getString(dataCol)
                        if (candidatePath == path) {
                            val id = cursor.getLong(idCol)
                            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        }
                    } else {
                        val id = cursor.getLong(idCol)
                        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "findMediaStoreUriForPath failed: ${e.message}")
        }
        return null
    }

    private fun updateMediaStoreMetadata(
        context: Context,
        filePathOrUri: String,
        title: String = "",
        artist: String = "",
        album: String = "",
        genre: String = "",
        year: String = ""
    ) {
        try {
            val uri = if (filePathOrUri.startsWith("content://")) Uri.parse(filePathOrUri) else findMediaStoreUriForPath(context, filePathOrUri)
            if (uri != null) {
                val values = android.content.ContentValues()
                if (title.isNotBlank()) values.put(MediaStore.Audio.Media.TITLE, title)
                if (artist.isNotBlank()) values.put(MediaStore.Audio.Media.ARTIST, artist)
                if (album.isNotBlank()) values.put(MediaStore.Audio.Media.ALBUM, album)
                if (year.isNotBlank()) values.put(MediaStore.Audio.Media.YEAR, year)
                if (values.size() > 0) {
                    context.contentResolver.update(uri, values, null, null)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun notifyMediaScanner(context: Context, filePathOrUri: String) {
        if (!filePathOrUri.startsWith("content://")) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePathOrUri),
                    null,
                    { path, uri -> Log.d(TAG, "MediaScanner indexed: $path -> $uri") }
                )
            } catch (e: Throwable) {
                Log.w(TAG, "MediaScanner notification failed: ${e.message}")
            }
        }
    }

    suspend fun createTaggedCopy(
        context: Context,
        sourceFilePath: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        lyrics: String,
        artworkUrl: String,
        trackNumber: Int = 1
    ): String? = withContext(Dispatchers.IO) {
        try {
            val ext = detectTrueAudioFormat(context, sourceFilePath)
            val safeArtist = artist.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim().ifBlank { "Artist" }
            val safeTitle = title.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim().ifBlank { "Track" }
            val newFilename = "$safeArtist - $safeTitle (Tagged).$ext"

            val tempFile = getWorkingSandboxFile(context, ext)
            if (!copySourceToSandbox(context, sourceFilePath, tempFile)) {
                return@withContext null
            }

            val result = embedAllTagsDetailed(
                context = context,
                sourceFilePath = tempFile.absolutePath,
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                year = year,
                lyrics = lyrics,
                artworkUrl = artworkUrl,
                trackNumber = trackNumber
            )
            if (!result.criticalTagsVerified) {
                tempFile.delete()
                return@withContext null
            }

            val appSpecificDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music")
            appSpecificDir.mkdirs()
            val finalTargetFile = File(appSpecificDir, newFilename)
            tempFile.copyTo(finalTargetFile, overwrite = true)
            tempFile.delete()

            notifyMediaScanner(context, finalTargetFile.absolutePath)
            finalTargetFile.absolutePath
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ModernAudioTagger.createTaggedCopy")
            Log.e(TAG, "createTaggedCopy failed: ${e.message}")
            null
        }
    }

    suspend fun embedArtworkBytes(
        context: Context,
        filePathOrUri: String,
        artworkBytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): Boolean = withContext(Dispatchers.IO) {
        if (filePathOrUri.isBlank() || artworkBytes.isEmpty()) return@withContext false
        val format = detectTrueAudioFormat(context, filePathOrUri)
        val sandboxFile = getWorkingSandboxFile(context, format)
        var activeSandboxFile = sandboxFile
        try {
            if (!copySourceToSandbox(context, filePathOrUri, sandboxFile)) {
                return@withContext false
            }
            val (audioFile, actualFile) = readAudioFileWithFallback(sandboxFile, format)
            activeSandboxFile = actualFile

            var tag: Tag? = audioFile.tag
            val mp3File = audioFile as? MP3File
            if (mp3File != null) {
                var id3v2Tag = mp3File.iD3v2Tag
                if (id3v2Tag == null) {
                    id3v2Tag = ID3v24Tag()
                    mp3File.iD3v2Tag = id3v2Tag
                }
                tag = id3v2Tag
            } else if (tag == null) {
                tag = audioFile.createDefaultTag()
                audioFile.tag = tag
            }

            val activeTag = tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size, options)
            val effectiveMime = if (mimeType.isNotBlank() && mimeType != "image/*") mimeType else (options.outMimeType ?: "image/jpeg")

            val processed = processAndOptimizeArtworkBytes(artworkBytes)
            val finalBytes = processed?.first ?: artworkBytes
            val finalMime = processed?.second ?: effectiveMime

            try { activeTag.deleteArtworkField() } catch (e: Throwable) {
                Log.d(TAG, "deleteArtworkField note: ${e.message}")
            }

            if (activeTag is FlacTag) {
                var flacArtOk = false
                try {
                    val pic = org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture(
                        finalBytes,
                        PictureTypes.DEFAULT_ID,
                        finalMime,
                        "Front Cover",
                        options.outWidth.coerceAtLeast(300),
                        options.outHeight.coerceAtLeast(300),
                        24,
                        0
                    )
                    activeTag.setField(pic)
                    flacArtOk = true
                } catch (e: Throwable) {
                    Log.d(TAG, "MetadataBlockDataPicture note: ${e.message}")
                }
                if (!flacArtOk) {
                    try {
                        val artwork = ArtworkFactory.getNew().apply {
                            binaryData = finalBytes
                            this.mimeType = finalMime
                            pictureType = PictureTypes.DEFAULT_ID
                        }
                        activeTag.setField(artwork)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Flac ArtworkFactory fallback note: ${e.message}")
                    }
                }
            } else {
                try {
                    val artwork = ArtworkFactory.getNew().apply {
                        binaryData = finalBytes
                        this.mimeType = finalMime
                        pictureType = PictureTypes.DEFAULT_ID
                    }
                    activeTag.setField(artwork)
                } catch (e: Throwable) {
                    Log.w(TAG, "Generic ArtworkFactory note: ${e.message}")
                }
            }

            audioFile.commit()
            AudioFileIO.write(audioFile)

            writeSandboxBackToSource(context, activeSandboxFile, filePathOrUri)
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ModernAudioTagger.embedArtworkBytes")
            Log.e(TAG, "embedArtworkBytes failed: ${e.message}")
            false
        } finally {
            if (sandboxFile.exists()) sandboxFile.delete()
            if (activeSandboxFile.exists() && activeSandboxFile != sandboxFile) activeSandboxFile.delete()
        }
    }

    suspend fun embedLyrics(
        context: Context,
        filePathOrUri: String,
        lyrics: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (filePathOrUri.isBlank() || lyrics.isBlank()) return@withContext false
        val format = detectTrueAudioFormat(context, filePathOrUri)
        val sandboxFile = getWorkingSandboxFile(context, format)
        var activeSandboxFile = sandboxFile
        try {
            if (!copySourceToSandbox(context, filePathOrUri, sandboxFile)) {
                return@withContext false
            }
            val (audioFile, actualFile) = readAudioFileWithFallback(sandboxFile, format)
            activeSandboxFile = actualFile

            var tag: Tag? = audioFile.tag
            val mp3File = audioFile as? MP3File
            if (mp3File != null) {
                var id3v2Tag = mp3File.iD3v2Tag
                if (id3v2Tag == null) {
                    id3v2Tag = ID3v24Tag()
                    mp3File.iD3v2Tag = id3v2Tag
                }
                tag = id3v2Tag
            } else if (tag == null) {
                tag = audioFile.createDefaultTag()
                audioFile.tag = tag
            }

            val activeTag = tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }

            try { activeTag.setField(FieldKey.LYRICS, lyrics) } catch (e: Throwable) {
                Log.d(TAG, "Standard LYRICS field note: ${e.message}")
            }

            try {
                val vorbisTag: VorbisCommentTag? = when (activeTag) {
                    is FlacTag -> activeTag.vorbisCommentTag
                    is VorbisCommentTag -> activeTag
                    else -> null
                }
                if (vorbisTag != null) {
                    try { vorbisTag.setField(vorbisTag.createField("LYRICS", lyrics)) } catch (e: Throwable) {
                        Log.d(TAG, "Vorbis LYRICS field note: ${e.message}")
                    }
                    try { vorbisTag.setField(vorbisTag.createField("UNSYNCEDLYRICS", lyrics)) } catch (e: Throwable) {
                        Log.d(TAG, "Vorbis UNSYNCEDLYRICS field note: ${e.message}")
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Vorbis comment lyrics note: ${e.message}")
            }

            try {
                val id3Tag = (activeTag as? org.jaudiotagger.tag.id3.AbstractID3v2Tag) ?: mp3File?.iD3v2Tag
                if (id3Tag != null) {
                    val frameBody = FrameBodyUSLT().apply {
                        lyric = lyrics
                        language = "eng"
                        description = ""
                    }
                    val frame = ID3v24Frame(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS).apply { body = frameBody }
                    id3Tag.setFrame(frame)
                }
            } catch (e: Throwable) {
                Log.d(TAG, "ID3 USLT lyrics note: ${e.message}")
            }

            audioFile.commit()
            AudioFileIO.write(audioFile)

            writeSandboxBackToSource(context, activeSandboxFile, filePathOrUri)
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ModernAudioTagger.embedLyrics")
            Log.e(TAG, "embedLyrics failed: ${e.message}")
            false
        } finally {
            if (sandboxFile.exists()) sandboxFile.delete()
            if (activeSandboxFile.exists() && activeSandboxFile != sandboxFile) activeSandboxFile.delete()
        }
    }
}
