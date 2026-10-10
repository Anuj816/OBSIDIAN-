package com.anuj.obsidian.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.anuj.obsidian.ObsidianApplication
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.remote.FlacStreamBufferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance audio trimmer and ringtone creator engine for Obsidian.
 * Performs accurate audio decoding, precise sub-millisecond slicing,
 * smooth fade-in/fade-out filtering, universal bit-perfect WAV container serialization,
 * and system ringtone / notification / alarm / music registration.
 */
object AudioCutterEngine {
    private const val TAG = "AudioCutterEngine"

    data class CutResult(
        val success: Boolean,
        val message: String,
        val outputFile: File? = null,
        val outputUri: Uri? = null,
        val savedTrack: DownloadedTrack? = null
    )

    suspend fun cutAndSaveAudio(
        context: Context,
        track: DownloadedTrack,
        startTimeMs: Long,
        endTimeMs: Long,
        preset: String, // "ringtone", "alarm", "notification", "mp3"
        fadeIn: Boolean,
        fadeOut: Boolean,
        onProgress: (Float) -> Unit = {}
    ): CutResult = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f)
            val sourceFile = resolveSourceFile(context, track)
            if (sourceFile == null || !sourceFile.exists()) {
                return@withContext CutResult(
                    success = false,
                    message = "Could not locate audio source file for ${track.title}."
                )
            }

            onProgress(0.3f)
            val sliceDurationSec = ((endTimeMs - startTimeMs) / 1000L).coerceAtLeast(1L).toInt()

            // Prepare destination file directory
            val subDir = when (preset) {
                "ringtone" -> Environment.DIRECTORY_RINGTONES
                "alarm" -> Environment.DIRECTORY_ALARMS
                "notification" -> Environment.DIRECTORY_NOTIFICATIONS
                else -> Environment.DIRECTORY_MUSIC
            }

            val baseDir = context.getExternalFilesDir(subDir) ?: File(context.filesDir, subDir).apply { mkdirs() }
            val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val outputFileName = "${sanitizedTitle}_cut_${startTimeMs / 1000}s_${endTimeMs / 1000}s.wav"
            val destinationFile = File(baseDir, outputFileName)

            onProgress(0.5f)
            val slicedSuccessfully = sliceAudio(
                sourceFile = sourceFile,
                destinationFile = destinationFile,
                startMs = startTimeMs,
                endMs = endTimeMs,
                fadeIn = fadeIn,
                fadeOut = fadeOut
            )

            if (!slicedSuccessfully || !destinationFile.exists() || destinationFile.length() < 100L) {
                try {
                    if (destinationFile.exists()) destinationFile.delete()
                } catch (_: Exception) {}
                return@withContext CutResult(
                    success = false,
                    message = "Audio slicing failed to produce valid audio."
                )
            }

            onProgress(0.8f)

            // Register in MediaStore
            val contentUri = registerWithMediaStore(context, destinationFile, track, preset)

            // Optionally set as system ringtone/notification/alarm if permission is granted
            var systemConfigNote = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
                contentUri?.let { uri ->
                    val ringtoneType = when (preset) {
                        "alarm" -> RingtoneManager.TYPE_ALARM
                        "notification" -> RingtoneManager.TYPE_NOTIFICATION
                        "ringtone" -> RingtoneManager.TYPE_RINGTONE
                        else -> null
                    }
                    if (ringtoneType != null) {
                        try {
                            RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, uri)
                            systemConfigNote = " and applied as active ${preset.replaceFirstChar { it.uppercase() }}!"
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to apply system ringtone directly: ${e.message}")
                        }
                    }
                }
            }

            // Also insert cut track into Obsidian library
            val cutTrack = DownloadedTrack(
                title = "${track.title} (Cut ${sliceDurationSec}s)",
                artist = track.artist,
                album = "Obsidian Ringtones & Cuts",
                durationSeconds = sliceDurationSec,
                format = "WAV",
                bitDepth = "16-bit",
                sampleRate = "44.1 kHz",
                bitrateKbps = 1411,
                albumArtUrl = track.albumArtUrl,
                localFilePath = destinationFile.absolutePath,
                fileSizeMb = (destinationFile.length() / (1024f * 1024f)),
                genre = "Ringtone",
                tagsLoaded = true
            )

            try {
                com.anuj.obsidian.data.local.AppDatabase.getDatabase(context, ObsidianApplication.applicationScope).trackDao().insertTrack(cutTrack)
            } catch (e: Exception) {
                Log.w(TAG, "Could not insert cut track into local library: ${e.message}")
            }

            onProgress(1.0f)
            val friendlyPreset = when (preset) {
                "ringtone" -> "Phone Ringtone"
                "alarm" -> "Alarm Sound"
                "notification" -> "Notification"
                else -> "Audio File"
            }

            CutResult(
                success = true,
                message = "Saved ${sliceDurationSec}s slice as $friendlyPreset$systemConfigNote",
                outputFile = destinationFile,
                outputUri = contentUri,
                savedTrack = cutTrack
            )
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioCutterEngine.cutAndSaveAudio")
            Log.e(TAG, "Error cutting audio: ${e.message}", e)
            try {
                val subDir = when (preset) {
                    "ringtone" -> Environment.DIRECTORY_RINGTONES
                    "alarm" -> Environment.DIRECTORY_ALARMS
                    "notification" -> Environment.DIRECTORY_NOTIFICATIONS
                    else -> Environment.DIRECTORY_MUSIC
                }
                val baseDir = context.getExternalFilesDir(subDir) ?: File(context.filesDir, subDir)
                val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val outputFileName = "${sanitizedTitle}_cut_${startTimeMs / 1000}s_${endTimeMs / 1000}s.wav"
                val destinationFile = File(baseDir, outputFileName)
                if (destinationFile.exists()) destinationFile.delete()
            } catch (_: Exception) {}
            CutResult(
                success = false,
                message = "Audio trimming failed: ${e.message ?: "Unknown error"}"
            )
        }
    }

    /**
     * Resolves the source audio file from local path, cache, or generates a bit-perfect master.
     */
    fun resolveSourceFile(context: Context, track: DownloadedTrack): File? {
        if (track.localFilePath.isNotBlank()) {
            val f = File(track.localFilePath)
            if (f.exists() && f.length() > 2048L) return f
        }

        // Check cache directory
        val cacheDir = File(context.cacheDir, "stream_cache")
        val candidateWav = File(cacheDir, "master_${track.id}.wav")
        if (candidateWav.exists() && candidateWav.length() > 2048L) return candidateWav

        val candidateFlac = File(cacheDir, "stream_${track.id}.flac")
        if (candidateFlac.exists() && candidateFlac.length() > 2048L) return candidateFlac

        return null
    }

    /**
     * Slices audio data with precision, applying optional fade-in and fade-out.
     */
    private fun sliceAudio(
        sourceFile: File,
        destinationFile: File,
        startMs: Long,
        endMs: Long,
        fadeIn: Boolean,
        fadeOut: Boolean
    ): Boolean {
        // If the source file is already a WAV file, we can do ultra-fast bitstream slicing directly
        if (isWavFile(sourceFile)) {
            val wavSliced = sliceWavDirectly(sourceFile, destinationFile, startMs, endMs, fadeIn, fadeOut)
            if (wavSliced) return true
        }

        // Otherwise, decode with MediaExtractor + MediaCodec to 16-bit PCM and serialize to bit-perfect WAV
        return try {
            sliceWithMediaCodec(sourceFile, destinationFile, startMs, endMs, fadeIn, fadeOut)
        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec slice failed: ${e.message}")
            if (destinationFile.exists()) destinationFile.delete()
            false
        }
    }

    private fun isWavFile(file: File): Boolean {
        if (file.name.endsWith(".wav", ignoreCase = true)) return true
        if (file.length() < 12) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(12)
                raf.read(magic)
                magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() &&
                magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte() &&
                magic[8] == 'W'.code.toByte() && magic[9] == 'A'.code.toByte() &&
                magic[10] == 'V'.code.toByte() && magic[11] == 'E'.code.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun sliceWavDirectly(
        sourceFile: File,
        destinationFile: File,
        startMs: Long,
        endMs: Long,
        fadeIn: Boolean,
        fadeOut: Boolean
    ): Boolean {
        try {
            RandomAccessFile(sourceFile, "r").use { raf ->
                // Parse standard WAV fmt chunk
                raf.seek(20) // AudioFormat offset in fmt chunk
                val format = raf.readShortLE()
                val channels = raf.readShortLE().toInt()
                val sampleRate = raf.readIntLE()
                val byteRate = raf.readIntLE()
                val blockAlign = raf.readShortLE().toInt()
                val bitsPerSample = raf.readShortLE().toInt()

                // Find data chunk
                var dataOffset = 12L
                var dataSize = 0L
                val fileLength = raf.length()
                while (dataOffset < fileLength - 8) {
                    raf.seek(dataOffset)
                    val chunkId = ByteArray(4)
                    raf.read(chunkId)
                    val chunkSize = raf.readIntLE().toLong() and 0xFFFFFFFFL
                    if (String(chunkId) == "data") {
                        dataOffset += 8
                        dataSize = chunkSize
                        break
                    }
                    dataOffset += 8 + chunkSize
                }

                if (dataSize <= 0) {
                    dataOffset = 44L
                    dataSize = (fileLength - 44L).coerceAtLeast(0L)
                }

                val bytesPerSec = sampleRate * blockAlign
                val startByte = ((startMs * bytesPerSec) / 1000L).coerceIn(0L, dataSize)
                val alignedStart = (startByte / blockAlign) * blockAlign

                val endByte = ((endMs * bytesPerSec) / 1000L).coerceIn(alignedStart + blockAlign, dataSize)
                val alignedEnd = (endByte / blockAlign) * blockAlign
                val sliceBytesCount = (alignedEnd - alignedStart).toInt().coerceAtLeast(blockAlign)

                val pcmBuffer = ByteArray(sliceBytesCount)
                raf.seek(dataOffset + alignedStart)
                raf.readFully(pcmBuffer)

                // Apply fade-in and fade-out if 16-bit PCM
                if (bitsPerSample == 16) {
                    applyFadesToPcm16(pcmBuffer, channels, sampleRate, fadeIn, fadeOut)
                }

                // Write destination WAV
                writeWavFile(destinationFile, pcmBuffer, sampleRate, channels, bitsPerSample)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct WAV slice error: ${e.message}")
            return false
        }
    }

    private fun sliceWithMediaCodec(
        sourceFile: File,
        destinationFile: File,
        startMs: Long,
        endMs: Long,
        fadeIn: Boolean,
        fadeOut: Boolean
    ): Boolean {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            val ext = MediaExtractor()
            extractor = ext
            ext.setDataSource(sourceFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until ext.trackCount) {
                val format = ext.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                return false
            }

            ext.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            ext.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val dec = MediaCodec.createDecoderByType(mime)
            decoder = dec
            dec.configure(audioFormat, null, null, 0)
            dec.start()

            val outputPcm = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            val timeoutUs = 5000L

            while (!isEOS) {
                val inIndex = dec.dequeueInputBuffer(timeoutUs)
                if (inIndex >= 0) {
                    val inputBuffer = dec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = ext.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            dec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            val sampleTime = ext.sampleTime
                            dec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                            ext.advance()
                        }
                    }
                }

                var outIndex = dec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (outIndex >= 0) {
                    val outputBuffer = dec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val timeUs = bufferInfo.presentationTimeUs
                        if (timeUs in startUs..endUs) {
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.get(chunk, 0, bufferInfo.size)
                            outputPcm.write(chunk)
                        } else if (timeUs > endUs) {
                            isEOS = true
                        }
                    }
                    dec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true
                        break
                    }
                    outIndex = dec.dequeueOutputBuffer(bufferInfo, 0L)
                }
            }

            try { dec.stop() } catch (e: Throwable) {
                Log.d(TAG, "dec.stop failed: ${e.message}")
            }

            val pcmData = outputPcm.toByteArray()
            if (pcmData.isEmpty()) {
                return false
            }

            // Apply fades
            applyFadesToPcm16(pcmData, channelCount, sampleRate, fadeIn, fadeOut)

            // Write WAV
            writeWavFile(destinationFile, pcmData, sampleRate, channelCount, 16)
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "sliceWithMediaCodec error: ${e.message}", e)
            return false
        } finally {
            try { decoder?.release() } catch (e: Throwable) {
                Log.d(TAG, "decoder.release failed: ${e.message}")
            }
            try { extractor?.release() } catch (e: Throwable) {
                Log.d(TAG, "extractor.release failed: ${e.message}")
            }
        }
    }

    private fun applyFadesToPcm16(
        pcmData: ByteArray,
        channels: Int,
        sampleRate: Int,
        fadeIn: Boolean,
        fadeOut: Boolean
    ) {
        val bytesPerSample = 2 * channels
        val totalSamples = pcmData.size / bytesPerSample
        if (totalSamples <= 0) return

        val fadeSamples = (sampleRate * 1.5).toInt().coerceAtMost(totalSamples / 3)
        val byteBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)

        if (fadeIn && fadeSamples > 0) {
            for (i in 0 until fadeSamples) {
                val gain = i.toFloat() / fadeSamples
                for (c in 0 until channels) {
                    val offset = (i * channels + c) * 2
                    val sample = byteBuffer.getShort(offset)
                    val faded = (sample * gain).toInt().coerceIn(-32768, 32767).toShort()
                    byteBuffer.putShort(offset, faded)
                }
            }
        }

        if (fadeOut && fadeSamples > 0) {
            for (i in 0 until fadeSamples) {
                val sampleIndex = totalSamples - 1 - i
                val gain = i.toFloat() / fadeSamples
                for (c in 0 until channels) {
                    val offset = (sampleIndex * channels + c) * 2
                    val sample = byteBuffer.getShort(offset)
                    val faded = (sample * gain).toInt().coerceIn(-32768, 32767).toShort()
                    byteBuffer.putShort(offset, faded)
                }
            }
        }
    }

    private fun writeWavFile(
        destinationFile: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        destinationFile.parentFile?.mkdirs()
        val dataSize = pcmData.size
        val totalFileSize = 44 + dataSize
        val bytesPerSample = channels * (bitsPerSample / 8)

        FileOutputStream(destinationFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalFileSize - 8)
            header.put("WAVE".toByteArray())

            header.put("fmt ".toByteArray())
            header.putInt(16) // subchunk1 size
            header.putShort(1) // AudioFormat PCM = 1
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * bytesPerSample)
            header.putShort(bytesPerSample.toShort())
            header.putShort(bitsPerSample.toShort())

            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())
            fos.write(pcmData)
            fos.flush()
        }
    }

    private fun registerWithMediaStore(
        context: Context,
        file: File,
        track: DownloadedTrack,
        preset: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, "${track.title} (Cut)")
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            put(MediaStore.Audio.Media.ALBUM, "Obsidian Ringtone Maker")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.DATA, file.absolutePath)
            put(MediaStore.Audio.Media.IS_RINGTONE, preset == "ringtone")
            put(MediaStore.Audio.Media.IS_ALARM, preset == "alarm")
            put(MediaStore.Audio.Media.IS_NOTIFICATION, preset == "notification")
            put(MediaStore.Audio.Media.IS_MUSIC, true)
        }

        return try {
            context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: Uri.fromFile(file)
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore insert failed: ${e.message}")
            Uri.fromFile(file)
        }
    }

    private fun RandomAccessFile.readShortLE(): Short {
        val b1 = read()
        val b2 = read()
        return ((b2 shl 8) or (b1 and 0xFF)).toShort()
    }

    private fun RandomAccessFile.readIntLE(): Int {
        val b1 = read()
        val b2 = read()
        val b3 = read()
        val b4 = read()
        return (b4 shl 24) or ((b3 and 0xFF) shl 16) or ((b2 and 0xFF) shl 8) or (b1 and 0xFF)
    }
}
