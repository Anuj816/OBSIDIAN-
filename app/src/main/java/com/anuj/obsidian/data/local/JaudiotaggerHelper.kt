package com.anuj.obsidian.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.anuj.obsidian.data.remote.FlacStreamBufferManager
import com.anuj.obsidian.data.remote.FlacStreamBufferManager.AudioContainerType
import com.anuj.obsidian.util.AlacAudioDecoder
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.flac.FlacAudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Level
import java.util.logging.Logger

data class DetailedCodecInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val composer: String = "",
    val encoder: String = "",
    val comment: String = "",
    val lyrics: String = "",
    val replayGainTrackGain: Float? = null,
    val replayGainAlbumGain: Float? = null,
    val replayGainTrackPeak: Float? = null,
    val replayGainAlbumPeak: Float? = null,
    val r128TrackGain: Float? = null,
    val r128AlbumGain: Float? = null,
    // Detailed Codec & Audio Headers
    val formatName: String = "",          // e.g. "FLAC 24-bit 96kHz"
    val audioEncoding: String = "",       // e.g. "FLAC (Free Lossless Audio Codec)"
    val sampleRateFormatted: String = "", // e.g. "96.0 kHz"
    val bitDepthFormatted: String = "",   // e.g. "24-bit"
    val channelMode: String = "",         // e.g. "Stereo (2 Channels)"
    val bitrateKbps: Int = 0,             // e.g. 2840
    val durationSeconds: Int = 0,
    val isLossless: Boolean = true,
    val bitsPerSample: Int = 0,
    val flacMd5Signature: String = "",
    val vendorString: String = "",
    // Embedded Album Art
    val artworkBitmap: Bitmap? = null,
    val artworkMimeType: String = "",
    val artworkDimensions: String = "",   // e.g. "1200 x 1200"
    val artworkSizeBytes: Long = 0L,
    val rawArtworkBytes: ByteArray? = null
)

object JaudiotaggerHelper {
    private const val TAG = "JaudiotaggerHelper"

    init {
        // Mute noisy jaudiotagger debug logs
        try {
            Logger.getLogger("org.jaudiotagger").level = Level.OFF
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "JaudiotaggerHelper.UnknownFun - attempt")
            }
    }

    private fun hasStoragePermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Inspects file extensions and container headers to detect Opus audio streams.
     * JAudioTagger does not provide a native Opus reader; these must bypass JAudioTagger.
     */
    fun isOpusFile(file: File?, filePathOrUri: String = ""): Boolean {
        val ext = if (filePathOrUri.isNotBlank()) filePathOrUri.substringAfterLast(".", "") else (file?.extension ?: "")
        if (ext.equals("opus", ignoreCase = true)) return true
        if (file != null && file.exists() && file.length() >= 36) {
            try {
                java.io.RandomAccessFile(file, "r").use { raf ->
                    val header = ByteArray(64)
                    val read = raf.read(header)
                    if (read >= 36) {
                        val str = String(header, 0, read, Charsets.ISO_8859_1)
                        if (str.contains("OpusHead")) return true
                    }
                }
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed reading Opus header: ${e.message}")
            }
        }
        return false
    }

    /**
     * Reads comprehensive codec information, Vorbis comments / ID3 tags,
     * and embedded artwork from complex FLAC / audio files using JAudioTagger.
     */
    fun readDetailedMetadata(context: Context, filePathOrUri: String): DetailedCodecInfo {
        var fileToRead: File? = null
        var isTempFile = false
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            if (filePathOrUri.isBlank()) return DetailedCodecInfo()

            // Check permission at point of use for external files
            if (!filePathOrUri.startsWith("content://") && !filePathOrUri.contains(context.packageName)) {
                if (!hasStoragePermission(context)) {
                    Log.w(TAG, "Storage permission is missing or revoked at point of use for reading: $filePathOrUri")
                    return DetailedCodecInfo()
                }
            }

            if (filePathOrUri.startsWith("content://")) {
                val uri = Uri.parse(filePathOrUri)
                try {
                    pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val fdFile = File("/proc/self/fd/${pfd.fd}")
                        if (fdFile.exists() && fdFile.canRead()) {
                            fileToRead = fdFile
                        }
                    }
                } catch (e: Throwable) {
                    // Ignore, fallback to temporary cache copy
                }

                // If /proc/self/fd failed or isn't accessible, make a temporary copy in cache for JAudioTagger
                if (fileToRead == null || !fileToRead.exists()) {
                    try {
                        val tempFile = File(context.cacheDir, "tag_extract_${System.currentTimeMillis()}_${(100..999).random()}.tmp")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            val containerType = FlacStreamBufferManager.detectAudioContainer(tempFile)
                            val actualExt = if (AlacAudioDecoder.isAlacFile(context, tempFile.absolutePath) || containerType == AudioContainerType.M4A) {
                                "m4a"
                            } else if (containerType != AudioContainerType.UNKNOWN) {
                                containerType.extension
                            } else {
                                filePathOrUri.substringAfterLast(".", "m4a").lowercase()
                            }
                            val namedFile = File(context.cacheDir, "tag_extract_${System.currentTimeMillis()}_${(100..999).random()}.$actualExt")
                            if (tempFile.renameTo(namedFile)) {
                                fileToRead = namedFile
                            } else {
                                fileToRead = tempFile
                            }
                            isTempFile = true
                        }
                    } catch (e: Throwable) {
                        com.anuj.obsidian.ObsidianCrashLogger.logException(e, "JaudiotaggerHelper content uri cache copy")
                    }
                }
            } else {
                val file = File(filePathOrUri)
                if (file.exists() && file.length() > 0) {
                    fileToRead = file
                }
            }

            if (fileToRead == null || !fileToRead.exists() || fileToRead.length() == 0L) {
                pfd?.close()
                return DetailedCodecInfo()
            }

            val rawExt = filePathOrUri.substringAfterLast(".", "FLAC").lowercase()

            // 1. Opus / Ogg Check: JAudioTagger does not have native Opus/Ogg support (throws CannotReadException)
            // Bypass JAudioTagger entirely and extract via native MediaMetadataRetriever
            if (rawExt == "opus" || rawExt == "ogg" || isOpusFile(fileToRead, filePathOrUri)) {
                Log.d(TAG, "Opus/Ogg stream detected; bypassing JAudioTagger: $filePathOrUri")
                return readFallbackMetadataViaMmr(context, filePathOrUri, fileToRead, AudioContainerType.OGG, rawExt.uppercase())
            }

            // 2. Corrupted or Misnamed FLAC check: Verify magic bytes
            val detectedContainer = FlacStreamBufferManager.detectAudioContainer(fileToRead)
            if (rawExt == "flac" && detectedContainer != AudioContainerType.FLAC) {
                Log.w(TAG, "File named .flac is actually $detectedContainer container. Bypassing FlacFileReader.")
                if (detectedContainer == AudioContainerType.UNKNOWN) {
                    return readFallbackMetadataViaMmr(context, filePathOrUri, fileToRead, detectedContainer, "Audio")
                }
            }

            val effectiveExt = if (rawExt == "flac" && detectedContainer != AudioContainerType.FLAC && detectedContainer != AudioContainerType.UNKNOWN) {
                detectedContainer.extension
            } else {
                rawExt
            }

            // 3. JAudioTagger read safely wrapped in dedicated try-catch
            var audioFile: AudioFile? = null
            try {
                audioFile = try {
                    org.jaudiotagger.audio.AudioFileIO.readAs(fileToRead, effectiveExt)
                } catch (_: Throwable) {
                    org.jaudiotagger.audio.AudioFileIO.read(fileToRead)
                }
            } catch (e: Throwable) {
                // Catches MP4 NPE (Mp4InfoReader channel count missing) or CannotReadException without polluting crash logger
                Log.w(TAG, "JAudioTagger read warning for $effectiveExt ($filePathOrUri): ${e.message}; falling back to MediaMetadataRetriever")
                return readFallbackMetadataViaMmr(context, filePathOrUri, fileToRead, detectedContainer, effectiveExt.uppercase())
            }

            if (audioFile == null) {
                return readFallbackMetadataViaMmr(context, filePathOrUri, fileToRead, detectedContainer, effectiveExt.uppercase())
            }

            val header = try { audioFile.audioHeader } catch (_: Throwable) { null }
            val tag: Tag? = try { audioFile.tag } catch (_: Throwable) { null }

            // Tag values
            val title = tag?.getFirst(FieldKey.TITLE) ?: ""
            val artist = tag?.getFirst(FieldKey.ARTIST) ?: ""
            val album = tag?.getFirst(FieldKey.ALBUM) ?: ""
            val genre = tag?.getFirst(FieldKey.GENRE) ?: ""
            val year = tag?.getFirst(FieldKey.YEAR) ?: ""
            val trackNumber = tag?.getFirst(FieldKey.TRACK) ?: ""
            val composer = tag?.getFirst(FieldKey.COMPOSER) ?: ""
            val encoder = tag?.getFirst(FieldKey.ENCODER) ?: ""
            val comment = tag?.getFirst(FieldKey.COMMENT) ?: ""
            val lyrics = tag?.getFirst(FieldKey.LYRICS)?.ifBlank { null }
                ?: tag?.getFirst("UNSYNCEDLYRICS")?.ifBlank { null }
                ?: tag?.getFirst("LYRICS")?.ifBlank { null }
                ?: tag?.getFirst("USLT")?.ifBlank { null }
                ?: tag?.getFirst("SYLT")?.ifBlank { null }
                ?: ""

            val trackGainStr = tag?.getFirst("REPLAYGAIN_TRACK_GAIN")?.ifBlank { null }
                ?: tag?.getFirst("replaygain_track_gain")?.ifBlank { null }
                ?: tag?.getFirst("RG_TRACK_GAIN")?.ifBlank { null }
            val albumGainStr = tag?.getFirst("REPLAYGAIN_ALBUM_GAIN")?.ifBlank { null }
                ?: tag?.getFirst("replaygain_album_gain")?.ifBlank { null }
                ?: tag?.getFirst("RG_ALBUM_GAIN")?.ifBlank { null }

            val trackPeakStr = tag?.getFirst("REPLAYGAIN_TRACK_PEAK")?.ifBlank { null }
                ?: tag?.getFirst("replaygain_track_peak")?.ifBlank { null }
                ?: tag?.getFirst("RG_TRACK_PEAK")?.ifBlank { null }
            val albumPeakStr = tag?.getFirst("REPLAYGAIN_ALBUM_PEAK")?.ifBlank { null }
                ?: tag?.getFirst("replaygain_album_peak")?.ifBlank { null }
                ?: tag?.getFirst("RG_ALBUM_PEAK")?.ifBlank { null }

            val r128TrackGainStr = tag?.getFirst("R128_TRACK_GAIN")?.ifBlank { null }
                ?: tag?.getFirst("r128_track_gain")?.ifBlank { null }
            val r128AlbumGainStr = tag?.getFirst("R128_ALBUM_GAIN")?.ifBlank { null }
                ?: tag?.getFirst("r128_album_gain")?.ifBlank { null }

            val trackGainDb = trackGainStr?.replace("dB", "", ignoreCase = true)?.trim()?.toFloatOrNull()
            val albumGainDb = albumGainStr?.replace("dB", "", ignoreCase = true)?.trim()?.toFloatOrNull()
            val trackPeakVal = trackPeakStr?.trim()?.toFloatOrNull()
            val albumPeakVal = albumPeakStr?.trim()?.toFloatOrNull()
            val r128TrackGainDb = r128TrackGainStr?.replace("LU", "", ignoreCase = true)?.replace("dB", "", ignoreCase = true)?.trim()?.toFloatOrNull()
            val r128AlbumGainDb = r128AlbumGainStr?.replace("LU", "", ignoreCase = true)?.replace("dB", "", ignoreCase = true)?.trim()?.toFloatOrNull()

            val rawFormat = try { header?.format ?: "" } catch (_: Throwable) { "" }
            val rawEncoding = try { header?.encodingType ?: "" } catch (_: Throwable) { "" }
            val ext = filePathOrUri.substringAfterLast(".", "FLAC").lowercase()

            val isAlac = ext == "alac" ||
                AlacAudioDecoder.isAlacFile(context, filePathOrUri) ||
                (fileToRead.exists() && AlacAudioDecoder.isAlacFile(context, fileToRead.absolutePath)) ||
                rawFormat.contains("ALAC", ignoreCase = true) ||
                rawFormat.contains("Apple Lossless", ignoreCase = true) ||
                rawEncoding.contains("ALAC", ignoreCase = true) ||
                rawEncoding.contains("Apple Lossless", ignoreCase = true)

            // Audio Header & Codec info
            val formatName = when {
                isAlac -> "ALAC"
                rawFormat.contains("FLAC", ignoreCase = true) -> "FLAC"
                rawFormat.contains("WAV", ignoreCase = true) -> "WAV"
                rawFormat.contains("MP3", ignoreCase = true) -> "MP3"
                rawFormat.contains("AAC", ignoreCase = true) -> "AAC"
                rawFormat.contains("OPUS", ignoreCase = true) -> "OPUS"
                rawFormat.contains("OGG", ignoreCase = true) -> "OGG"
                rawFormat.isNotBlank() -> rawFormat
                else -> ext.uppercase()
            }
            val encodingType = if (isAlac) "Apple Lossless (ALAC)" else rawEncoding.ifBlank { formatName }
            val sampleRateHz = try { header?.sampleRateAsNumber ?: 44100 } catch (_: Throwable) { 44100 }
            val sampleRateFormatted = when {
                sampleRateHz >= 1000 -> String.format("%.1f kHz", sampleRateHz / 1000.0)
                else -> "$sampleRateHz Hz"
            }

            var bitsPerSample = try { header?.bitsPerSample ?: 16 } catch (_: Throwable) { 16 }
            var md5Sig = ""
            var vendorStr = ""

            if (header is FlacAudioHeader) {
                bitsPerSample = try { header.bitsPerSample } catch (_: Throwable) { 16 }
                md5Sig = try { header.md5 ?: "" } catch (_: Throwable) { "" }
            } else if (bitsPerSample <= 0) {
                bitsPerSample = 16
            }

            val isLossless = (formatName == "FLAC" || formatName == "ALAC" || formatName == "WAV" || isAlac)
            val bitDepthFormatted = if (isLossless) (if (bitsPerSample > 0) "$bitsPerSample-bit" else "16-bit") else ""
            val channelMode = try {
                val ch = header?.channels
                if (ch == "1") "1.0 Mono" else if (ch == "2") "2.0 Stereo" else if (!ch.isNullOrBlank()) "$ch Channels" else "2.0 Stereo"
            } catch (_: Throwable) {
                "2.0 Stereo"
            }

            val fileLength = try {
                if (filePathOrUri.startsWith("content://")) {
                    context.contentResolver.openFileDescriptor(android.net.Uri.parse(filePathOrUri), "r")?.use { it.statSize } ?: 0L
                } else {
                    java.io.File(filePathOrUri).length()
                }
            } catch (_: Throwable) { 0L }

            val rawBitrate = try { header?.bitRateAsNumber?.toInt() ?: 0 } catch (_: Throwable) { 0 }
            val durationSec = try { header?.trackLength ?: 0 } catch (_: Throwable) { 0 }

            val bitrateKbps = if (durationSec > 0 && fileLength > 0) {
                ((fileLength * 8L) / durationSec / 1000L).toInt()
            } else if (rawBitrate in 32..9999) {
                rawBitrate
            } else {
                if (isAlac) 850 else 320
            }

            // Embedded Artwork Extraction
            var artworkBitmap: Bitmap? = null
            var artworkMime = ""
            var artworkDims = ""
            var artworkBytes: ByteArray? = null
            var artworkSize = 0L

            try {
                val artworkList: List<Artwork>? = tag?.artworkList
                val artwork: Artwork? = if (!artworkList.isNullOrEmpty()) artworkList[0] else tag?.firstArtwork
                if (artwork != null) {
                    val rawBytes: ByteArray? = try { artwork.binaryData } catch (_: Throwable) { null }
                    if (rawBytes != null && rawBytes.isNotEmpty()) {
                        artworkBytes = rawBytes
                        artworkSize = rawBytes.size.toLong()
                        artworkMime = try { artwork.mimeType ?: "image/jpeg" } catch (_: Throwable) { "image/jpeg" }

                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                        artworkDims = "${options.outWidth} × ${options.outHeight}"

                        val decodeOptions = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            if (options.outWidth > 2048 || options.outHeight > 2048) {
                                inSampleSize = 2
                            }
                        }
                        artworkBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Artwork extraction notice: ${e.message}")
            }

            return DetailedCodecInfo(
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                year = year,
                trackNumber = trackNumber,
                composer = composer,
                encoder = encoder,
                comment = comment,
                lyrics = lyrics,
                replayGainTrackGain = trackGainDb,
                replayGainAlbumGain = albumGainDb,
                replayGainTrackPeak = trackPeakVal,
                replayGainAlbumPeak = albumPeakVal,
                r128TrackGain = r128TrackGainDb,
                r128AlbumGain = r128AlbumGainDb,
                formatName = formatName,
                audioEncoding = encodingType,
                sampleRateFormatted = sampleRateFormatted,
                bitDepthFormatted = bitDepthFormatted,
                channelMode = channelMode,
                bitrateKbps = if (bitrateKbps > 0) bitrateKbps else 1411,
                durationSeconds = durationSec,
                isLossless = isLossless,
                bitsPerSample = bitsPerSample,
                flacMd5Signature = md5Sig,
                vendorString = vendorStr,
                artworkBitmap = artworkBitmap,
                artworkMimeType = artworkMime,
                artworkDimensions = artworkDims,
                artworkSizeBytes = artworkSize,
                rawArtworkBytes = artworkBytes
            )

        } catch (e: Throwable) {
            Log.w(TAG, "Jaudiotagger metadata read notice: ${e.message}")
            if (fileToRead != null && fileToRead.exists()) {
                try {
                    return readFallbackMetadataViaMmr(context, filePathOrUri, fileToRead)
                } catch (fallbackEx: Exception) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(fallbackEx, "readFallbackMetadataViaMmr failed")
                }
            }
            return DetailedCodecInfo()
        } finally {
            if (isTempFile && fileToRead != null && fileToRead.exists()) {
                try { 
                    fileToRead.delete() 
                } catch (e: Exception) {
                    com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed to delete temp file: ${e.message}")
                }
            }
            try { 
                pfd?.close() 
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed to close pfd: ${e.message}")
            }
        }
    }

    /**
     * Fallback metadata reader using Android's native MediaMetadataRetriever.
     * Used for Opus, corrupted/misnamed FLACs, and MP4 containers that fail JAudioTagger parsing.
     */
    fun readFallbackMetadataViaMmr(
        context: Context,
        filePathOrUri: String,
        file: File,
        containerType: AudioContainerType = AudioContainerType.UNKNOWN,
        formatHint: String = ""
    ): DetailedCodecInfo {
        val mmr = MediaMetadataRetriever()
        try {
            if (filePathOrUri.startsWith("content://")) {
                mmr.setDataSource(context, Uri.parse(filePathOrUri))
            } else if (file.exists()) {
                mmr.setDataSource(file.absolutePath)
            } else {
                return DetailedCodecInfo()
            }

            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            val year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: ""
            val trackNumber = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?: ""
            val composer = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER) ?: ""
            val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val durationSec = (durationMs / 1000L).toInt()
            val bitrateRaw = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val bitrateKbps = if (bitrateRaw > 0) bitrateRaw / 1000 else 320
            val mimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""

            val formatName = when {
                formatHint.isNotBlank() -> formatHint
                mimeType.contains("opus", ignoreCase = true) -> "OPUS"
                mimeType.contains("flac", ignoreCase = true) -> "FLAC"
                mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "AAC"
                mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
                mimeType.contains("ogg", ignoreCase = true) -> "OGG"
                else -> containerType.extension.uppercase()
            }

            var artworkBitmap: Bitmap? = null
            var artworkMime = "image/jpeg"
            var artworkDims = ""
            var artworkBytes: ByteArray? = null
            var artworkSize = 0L

            try {
                val pic = mmr.embeddedPicture
                if (pic != null && pic.isNotEmpty()) {
                    artworkBytes = pic
                    artworkSize = pic.size.toLong()
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(pic, 0, pic.size, options)
                    artworkDims = "${options.outWidth} × ${options.outHeight}"
                    if (options.outMimeType != null) artworkMime = options.outMimeType

                    val decodeOptions = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        if (options.outWidth > 2048 || options.outHeight > 2048) {
                            inSampleSize = 2
                        }
                    }
                    artworkBitmap = BitmapFactory.decodeByteArray(pic, 0, pic.size, decodeOptions)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Fallback artwork extraction notice: ${e.message}")
            }

            val sampleRateRaw = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            val sampleRateFormatted = if (sampleRateRaw > 0) {
                if (sampleRateRaw >= 1000) {
                    if (sampleRateRaw % 1000 == 0) "${sampleRateRaw / 1000}.0 kHz"
                    else String.format(java.util.Locale.US, "%.1f kHz", sampleRateRaw / 1000.0)
                } else "$sampleRateRaw Hz"
            } else if (formatName == "OPUS" || formatName == "OGG") {
                "48.0 kHz"
            } else {
                "44.1 kHz"
            }

            val isLossless = (formatName == "FLAC" || formatName == "ALAC" || formatName == "WAV")
            val bitDepthFormatted = if (isLossless) "16-bit" else ""

            return DetailedCodecInfo(
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                year = year,
                trackNumber = trackNumber,
                composer = composer,
                formatName = formatName,
                audioEncoding = if (formatName == "OPUS") "Opus Audio Codec" else formatName,
                sampleRateFormatted = sampleRateFormatted,
                bitDepthFormatted = bitDepthFormatted,
                channelMode = "2.0 Stereo",
                bitrateKbps = bitrateKbps,
                durationSeconds = durationSec,
                isLossless = isLossless,
                artworkBitmap = artworkBitmap,
                artworkMimeType = artworkMime,
                artworkDimensions = artworkDims,
                artworkSizeBytes = artworkSize,
                rawArtworkBytes = artworkBytes
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Fallback MMR metadata extraction notice: ${e.message}")
            return DetailedCodecInfo()
        } finally {
            try {
                mmr.release()
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed to release MMR: ${e.message}")
            }
        }
    }
}
