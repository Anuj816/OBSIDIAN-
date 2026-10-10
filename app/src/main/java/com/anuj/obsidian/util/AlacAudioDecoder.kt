package com.anuj.obsidian.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AlacAudioDecoder {
    private const val TAG = "AlacAudioDecoder"

    fun isAlacFile(context: Context? = null, filePathOrUri: String): Boolean {
        if (filePathOrUri.isBlank() || filePathOrUri.startsWith("http://") || filePathOrUri.startsWith("https://")) return false
        val lower = filePathOrUri.lowercase()
        if (lower.endsWith(".alac")) return true

        // Fast container inspection for 'alac' atom in MP4/CAF/M4A (head & tail scan)
        val isContentUri = filePathOrUri.startsWith("content://") || filePathOrUri.startsWith("file://")
        try {
            if (isContentUri && context != null) {
                context.contentResolver.openInputStream(android.net.Uri.parse(filePathOrUri))?.use { stream ->
                    if (scanStreamForAlacFourCc(stream)) return true
                }
            } else if (filePathOrUri.startsWith("/")) {
                val f = File(filePathOrUri)
                if (f.exists() && f.length() > 32L) {
                    if (scanFileForAlacFourCc(f)) return true
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Magic byte inspection failed: ${e.message}")
        }

        var extractor: MediaExtractor? = null
        return try {
            val ext = MediaExtractor()
            extractor = ext
            if (isContentUri && context != null) {
                ext.setDataSource(context, android.net.Uri.parse(filePathOrUri), null)
            } else if (filePathOrUri.startsWith("/")) {
                val file = File(filePathOrUri)
                if (!file.exists() || !file.canRead()) return false
                ext.setDataSource(filePathOrUri)
            } else {
                return false
            }

            var isAlac = false
            for (i in 0 until ext.trackCount) {
                val format = ext.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                val codecs = if (format.containsKey("codecs-string")) format.getString("codecs-string") ?: "" else ""
                val csd0 = if (format.containsKey("csd-0")) format.getByteBuffer("csd-0") else null

                if (mime.equals("audio/alac", ignoreCase = true) ||
                    mime.contains("alac", ignoreCase = true) ||
                    codecs.contains("alac", ignoreCase = true) ||
                    (csd0 != null && csd0.remaining() in 24..48)
                ) {
                    isAlac = true
                    break
                }
            }
            isAlac
        } catch (e: Exception) {
            lower.endsWith(".alac")
        } finally {
            try { extractor?.release() } catch (e: Throwable) {
                Log.d(TAG, "Extractor release failed: ${e.message}")
            }
        }
    }

    fun isAlacFile(filePath: String): Boolean = isAlacFile(null, filePath)

    private fun scanStreamForAlacFourCc(stream: InputStream): Boolean {
        val buf = ByteArray(64 * 1024)
        var totalRead = 0
        while (totalRead < 512 * 1024) {
            val read = stream.read(buf)
            if (read < 4) break
            for (j in 0 until (read - 3)) {
                if (buf[j] == 0x61.toByte() && buf[j + 1] == 0x6C.toByte() && buf[j + 2] == 0x61.toByte() && buf[j + 3] == 0x63.toByte()) {
                    return true
                }
            }
            totalRead += read
        }
        return false
    }

    private fun scanFileForAlacFourCc(file: File): Boolean {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val buf = ByteArray(64 * 1024)

                // 1. Scan head
                val headRead = raf.read(buf)
                if (headRead >= 4) {
                    for (j in 0 until (headRead - 3)) {
                        if (buf[j] == 0x61.toByte() && buf[j + 1] == 0x6C.toByte() && buf[j + 2] == 0x61.toByte() && buf[j + 3] == 0x63.toByte()) {
                            return true
                        }
                    }
                }

                // 2. Scan tail if file is large (in case moov atom is at the end)
                if (len > 128 * 1024) {
                    val tailOffset = (len - 64 * 1024).coerceAtLeast(0L)
                    raf.seek(tailOffset)
                    val tailRead = raf.read(buf)
                    if (tailRead >= 4) {
                        for (j in 0 until (tailRead - 3)) {
                            if (buf[j] == 0x61.toByte() && buf[j + 1] == 0x6C.toByte() && buf[j + 2] == 0x61.toByte() && buf[j + 3] == 0x63.toByte()) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "File fourcc scan failed: ${e.message}")
        }
        return false
    }

    /**
     * Checks if the system has a working MediaCodec decoder for audio/alac
     */
    fun isSystemAlacCodecAvailable(): Boolean {
        try {
            val list = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
            for (info in list.codecInfos) {
                if (!info.isEncoder) {
                    for (type in info.supportedTypes) {
                        if (type.equals("audio/alac", ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking MediaCodecList for ALAC: ${e.message}")
        }
        return false
    }

    private fun pruneCacheIfNeeded(cacheDir: File, maxSizeBytes: Long = 500 * 1024 * 1024L) {
        try {
            val files = cacheDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize > maxSizeBytes) {
                val sortedFiles = files.sortedBy { it.lastModified() }
                for (file in sortedFiles) {
                    if (totalSize <= maxSizeBytes) break
                    val len = file.length()
                    if (file.delete()) {
                        totalSize -= len
                    }
                }
            }
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AlacAudioDecoder.pruneCacheIfNeeded")
        }
    }

    /**
     * Attempts to extract the 24-byte ALAC specific config / cookie directly from the audio container.
     */
    fun extractAlacCookie(context: Context, sourcePath: String): ByteArray? {
        val isContentUri = sourcePath.startsWith("content://") || sourcePath.startsWith("file://")
        try {
            if (isContentUri) {
                context.contentResolver.openInputStream(android.net.Uri.parse(sourcePath))?.use { inStream ->
                    val buf = ByteArray(64 * 1024)
                    var totalRead = 0
                    while (totalRead < 2 * 1024 * 1024) {
                        val read = inStream.read(buf)
                        if (read <= 32) break
                        for (i in 0 until read - 32) {
                            if (buf[i] == 0x61.toByte() && buf[i + 1] == 0x6C.toByte() && buf[i + 2] == 0x61.toByte() && buf[i + 3] == 0x63.toByte()) {
                                // In MP4 'alac' box: 4 bytes size, 4 bytes 'alac', 4 bytes flags/version (0), 24 bytes cookie
                                val cookieStart = i + 8
                                if (cookieStart + 24 <= read) {
                                    val cookie = ByteArray(24)
                                    System.arraycopy(buf, cookieStart, cookie, 0, 24)
                                    return cookie
                                }
                            }
                        }
                        totalRead += read
                    }
                }
            } else if (sourcePath.startsWith("/")) {
                val f = File(sourcePath)
                if (f.exists() && f.length() > 32L) {
                    RandomAccessFile(f, "r").use { raf ->
                        val len = raf.length()
                        val buf = ByteArray(64 * 1024)

                        // Scan head
                        val headRead = raf.read(buf)
                        if (headRead > 32) {
                            for (i in 0 until headRead - 32) {
                                if (buf[i] == 0x61.toByte() && buf[i + 1] == 0x6C.toByte() && buf[i + 2] == 0x61.toByte() && buf[i + 3] == 0x63.toByte()) {
                                    val cookieStart = i + 8
                                    if (cookieStart + 24 <= headRead) {
                                        val cookie = ByteArray(24)
                                        System.arraycopy(buf, cookieStart, cookie, 0, 24)
                                        return cookie
                                    }
                                }
                            }
                        }

                        // Scan tail
                        if (len > 128 * 1024) {
                            val tailOffset = (len - 64 * 1024).coerceAtLeast(0L)
                            raf.seek(tailOffset)
                            val tailRead = raf.read(buf)
                            if (tailRead > 32) {
                                for (i in 0 until tailRead - 32) {
                                    if (buf[i] == 0x61.toByte() && buf[i + 1] == 0x6C.toByte() && buf[i + 2] == 0x61.toByte() && buf[i + 3] == 0x63.toByte()) {
                                        val cookieStart = i + 8
                                        if (cookieStart + 24 <= tailRead) {
                                            val cookie = ByteArray(24)
                                            System.arraycopy(buf, cookieStart, cookie, 0, 24)
                                            return cookie
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "extractAlacCookie failed: ${e.message}")
        }
        return null
    }

    /**
     * Decodes an ALAC file to a temporary PCM WAV file for bit-perfect, crash-free playback.
     * Returns the File of the decoded WAV, or null if decoding failed.
     */
    fun decodeAlacToWav(context: Context, sourcePath: String): File? {
        if (sourcePath.isBlank()) return null

        val isHttp = sourcePath.startsWith("http://") || sourcePath.startsWith("https://")
        val isContentUri = sourcePath.startsWith("content://") || sourcePath.startsWith("file://")
        val sourceFile = if (!isContentUri && !isHttp) File(sourcePath) else null

        val fileLength = when {
            isContentUri -> {
                try {
                    context.contentResolver.openFileDescriptor(android.net.Uri.parse(sourcePath), "r")?.use { it.statSize } ?: 0L
                } catch (_: Throwable) { 0L }
            }
            isHttp -> {
                try {
                    val url = java.net.URL(sourcePath)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", com.anuj.obsidian.data.remote.MusicApiService.BROWSER_USER_AGENT)
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    val len = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) conn.contentLengthLong else conn.contentLength.toLong()
                    conn.disconnect()
                    if (len > 0) len else 1024 * 1024L
                } catch (_: Throwable) { 1024 * 1024L }
            }
            else -> {
                if (sourceFile != null && sourceFile.exists()) sourceFile.length() else 0L
            }
        }

        if (fileLength == 0L) return null

        val cacheDir = File(context.cacheDir, "alac_pcm")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        pruneCacheIfNeeded(cacheDir)

        val fileHash = (sourcePath + "_" + fileLength + "_" + (sourceFile?.lastModified() ?: 0L)).hashCode().toString()
        val targetWavFile = File(cacheDir, "alac_$fileHash.wav")

        if (targetWavFile.exists() && targetWavFile.length() > 44L) {
            Log.d(TAG, "Using existing cached ALAC-to-WAV file: ${targetWavFile.absolutePath}")
            return targetWavFile
        }

        val tempWavFile = File(cacheDir, "alac_$fileHash.tmp")
        if (tempWavFile.exists()) tempWavFile.delete()

        var extractor: MediaExtractor? = null
        try {
            extractor = MediaExtractor()
            if (isContentUri) {
                extractor.setDataSource(context, android.net.Uri.parse(sourcePath), null)
            } else if (isHttp) {
                extractor.setDataSource(sourcePath, null)
            } else {
                extractor.setDataSource(sourcePath)
            }

            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: ""
                val codecs = if (f.containsKey("codecs-string")) f.getString("codecs-string") else ""
                val csd0 = if (f.containsKey("csd-0")) f.getByteBuffer("csd-0") else null

                if (m.startsWith("audio/") || m.contains("alac", ignoreCase = true)) {
                    audioTrackIndex = i
                    format = f
                    if (m.equals("audio/alac", ignoreCase = true) ||
                        m.contains("alac", ignoreCase = true) ||
                        codecs?.contains("alac", ignoreCase = true) == true ||
                        (csd0 != null && csd0.remaining() in 24..48)
                    ) {
                        break
                    }
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found in $sourcePath")
                return null
            }

            extractor.selectTrack(audioTrackIndex)

            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            // Stage 1: Try hardware/system MediaCodec decoder for audio/alac if available
            var mediaCodecDecodedBytes = 0L
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (isSystemAlacCodecAvailable() || mimeType.equals("audio/alac", ignoreCase = true)) {
                var codec: android.media.MediaCodec? = null
                try {
                    val dec = android.media.MediaCodec.createDecoderByType(if (mimeType.isNotBlank()) mimeType else "audio/alac")
                    codec = dec
                    dec.configure(format, null, null, 0)
                    dec.start()

                    val bufferInfo = android.media.MediaCodec.BufferInfo()
                    var isEOS = false

                    FileOutputStream(tempWavFile).use { fos ->
                        val headerPlaceholder = ByteArray(44)
                        fos.write(headerPlaceholder)

                        val timeoutUs = 10000L
                        while (!isEOS) {
                            val inIdx = codec.dequeueInputBuffer(timeoutUs)
                            if (inIdx >= 0) {
                                val inputBuf = codec.getInputBuffer(inIdx)
                                if (inputBuf != null) {
                                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                                    if (sampleSize < 0) {
                                        codec.queueInputBuffer(inIdx, 0, 0, 0L, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    } else {
                                        val presentationTimeUs = extractor.sampleTime
                                        codec.queueInputBuffer(inIdx, 0, sampleSize, presentationTimeUs, 0)
                                        extractor.advance()
                                    }
                                }
                            }

                            var outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                            while (outIdx >= 0) {
                                val outBuf = codec.getOutputBuffer(outIdx)
                                if (outBuf != null && bufferInfo.size > 0) {
                                    val chunk = ByteArray(bufferInfo.size)
                                    outBuf.position(bufferInfo.offset)
                                    outBuf.get(chunk)
                                    fos.write(chunk)
                                    mediaCodecDecodedBytes += chunk.size
                                }
                                codec.releaseOutputBuffer(outIdx, false)
                                if ((bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    isEOS = true
                                    break
                                }
                                outIdx = codec.dequeueOutputBuffer(bufferInfo, 0L)
                            }
                        }
                        fos.flush()
                    }
                    try { dec.stop() } catch (e: Throwable) {
                        Log.d(TAG, "Codec stop exception: ${e.message}")
                    }

                    if (mediaCodecDecodedBytes > 44L) {
                        writeWavHeader(tempWavFile, mediaCodecDecodedBytes, sampleRate, channelCount, 16)
                        if (targetWavFile.exists()) targetWavFile.delete()
                        if (tempWavFile.renameTo(targetWavFile)) {
                            Log.d(TAG, "MediaCodec successfully decoded ALAC to WAV: ${targetWavFile.absolutePath}")
                            return targetWavFile
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "System MediaCodec ALAC decode attempt failed, using software fallback: ${e.message}")
                    if (tempWavFile.exists()) tempWavFile.delete()
                } finally {
                    try { codec?.release() } catch (e: Throwable) {
                        Log.d(TAG, "Codec release exception: ${e.message}")
                    }
                }
            }

            // Stage 2: Pure-Kotlin Software ALAC Decoder Fallback
            // Extract csd-0 (ALAC Magic Cookie, 24-36 bytes)
            var csd0Buffer = if (format.containsKey("csd-0")) format.getByteBuffer("csd-0") else null
            var magicCookie = if (csd0Buffer != null && csd0Buffer.remaining() >= 24) {
                val arr = ByteArray(csd0Buffer.remaining())
                csd0Buffer.get(arr)
                arr
            } else {
                extractAlacCookie(context, sourcePath)
            }

            // Read cookie parameters
            var bitDepth = 16
            var frameLength = 4096
            var pb = 40
            var mb = 10
            var kb = 14
            var parsedChannels = channelCount
            var parsedSampleRate = sampleRate

            if (magicCookie != null && magicCookie.size >= 24) {
                try {
                    val bb = ByteBuffer.wrap(magicCookie).order(ByteOrder.BIG_ENDIAN)
                    val fl = bb.getInt(0)
                    if (fl in 64..16384) frameLength = fl
                    val bd = bb.get(5).toInt() and 0xFF
                    if (bd in listOf(16, 20, 24, 32)) bitDepth = bd
                    pb = bb.get(6).toInt() and 0xFF
                    mb = bb.get(7).toInt() and 0xFF
                    kb = bb.get(8).toInt() and 0xFF
                    val ch = bb.get(9).toInt() and 0xFF
                    if (ch in 1..8) parsedChannels = ch
                    if (magicCookie.size >= 24) {
                        val sr = bb.getInt(20)
                        if (sr in 8000..384000) parsedSampleRate = sr
                    }
                } catch (e: Throwable) {
                    Log.d(TAG, "Magic cookie parsing exception: ${e.message}")
                }
            }

            val finalChannels = if (parsedChannels in 1..8) parsedChannels else channelCount.coerceIn(1, 2)
            val finalSampleRate = if (parsedSampleRate in 8000..384000) parsedSampleRate else sampleRate.coerceIn(8000, 192000)

            val decoder = AlacDecoderInstance(magicCookie, finalSampleRate, finalChannels, bitDepth, frameLength, pb, mb, kb)

            val maxInputSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 128 * 1024
            val inputBuffer = ByteBuffer.allocateDirect(maxInputSize.coerceIn(64 * 1024, 4 * 1024 * 1024))
            val pcmBuffer = IntArray(65536 * finalChannels)

            var totalPcmBytesWritten = 0L
            val bytesPerSample = when (bitDepth) {
                16 -> 2
                20, 24 -> 3
                32 -> 4
                else -> 2
            }

            // Preallocate reusable buffers for frame decoding to eliminate GC allocations
            var reusableFrameData = ByteArray(64 * 1024)
            var reusableOutBytes = ByteArray(65536 * finalChannels * bytesPerSample)

            FileOutputStream(tempWavFile).use { fos ->
                // Write placeholder WAV header (44 bytes)
                val headerPlaceholder = ByteArray(44)
                fos.write(headerPlaceholder)

                while (true) {
                    inputBuffer.clear()
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) break

                    if (reusableFrameData.size < sampleSize) {
                        reusableFrameData = ByteArray(sampleSize + 16384)
                    }
                    inputBuffer.get(reusableFrameData, 0, sampleSize)

                    val samplesDecoded = decoder.decodeFrame(reusableFrameData, sampleSize, pcmBuffer)
                    if (samplesDecoded > 0) {
                        val outBytesCount = samplesDecoded * finalChannels * bytesPerSample
                        if (reusableOutBytes.size < outBytesCount) {
                            reusableOutBytes = ByteArray(outBytesCount + 32768)
                        }
                        var outIdx = 0

                        when (bitDepth) {
                            20 -> {
                                for (s in 0 until samplesDecoded * finalChannels) {
                                    val v24 = pcmBuffer[s] shl 4 // Left align 20-bit signed into 24-bit PCM
                                    reusableOutBytes[outIdx++] = (v24 and 0xFF).toByte()
                                    reusableOutBytes[outIdx++] = ((v24 shr 8) and 0xFF).toByte()
                                    reusableOutBytes[outIdx++] = ((v24 shr 16) and 0xFF).toByte()
                                }
                            }
                            24 -> {
                                for (s in 0 until samplesDecoded * finalChannels) {
                                    val v = pcmBuffer[s]
                                    reusableOutBytes[outIdx++] = (v and 0xFF).toByte()
                                    reusableOutBytes[outIdx++] = ((v shr 8) and 0xFF).toByte()
                                    reusableOutBytes[outIdx++] = ((v shr 16) and 0xFF).toByte()
                                }
                            }
                            32 -> {
                                for (s in 0 until samplesDecoded * finalChannels) {
                                    val v = pcmBuffer[s]
                                    reusableOutBytes[outIdx++] = (v and 0xFF).toByte()
                                    reusableOutBytes[outIdx++] = ((v shr 8) and 0xFF).toByte()
                                    reusableOutBytes[outIdx++] = ((v shr 16) and 0xFF).toByte()
                                    reusableOutBytes[outIdx++] = ((v shr 24) and 0xFF).toByte()
                                }
                            }
                            else -> { // 16-bit
                                for (s in 0 until samplesDecoded * finalChannels) {
                                    val v = pcmBuffer[s]
                                    reusableOutBytes[outIdx++] = (v and 0xFF).toByte()
                                    reusableOutBytes[outIdx++] = ((v shr 8) and 0xFF).toByte()
                                }
                            }
                        }

                        fos.write(reusableOutBytes, 0, outBytesCount)
                        totalPcmBytesWritten += outBytesCount
                    }

                    extractor.advance()
                }

                fos.flush()
            }

            if (totalPcmBytesWritten > 0L) {
                val headerBitsPerSample = if (bitDepth == 20) 24 else bitDepth
                writeWavHeader(tempWavFile, totalPcmBytesWritten, finalSampleRate, finalChannels, headerBitsPerSample)

                val finalFile = if (targetWavFile.exists() && !targetWavFile.delete()) {
                    tempWavFile
                } else if (tempWavFile.renameTo(targetWavFile)) {
                    targetWavFile
                } else {
                    try {
                        tempWavFile.copyTo(targetWavFile, overwrite = true)
                        tempWavFile.delete()
                        targetWavFile
                    } catch (_: Exception) {
                        tempWavFile
                    }
                }

                // Validate decoded file before handing to ExoPlayer
                val validation = MediaFileValidator.validate(finalFile)
                if (!validation.isStructurallyValid) {
                    Log.e(TAG, "Decoded ALAC WAV validation failed: ${validation.failureReason}")
                    finalFile.delete()
                    return null
                }

                Log.d(TAG, "Successfully decoded ALAC to WAV: ${finalFile.absolutePath} (${finalFile.length()} bytes, $headerBitsPerSample-bit, ${finalSampleRate}Hz)")
                return finalFile
            }
            return null

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to decode ALAC file $sourcePath", e)
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AlacAudioDecoder.decodeAlacToWav")
            if (tempWavFile.exists()) tempWavFile.delete()
            return null
        } finally {
            try { 
                extractor?.release() 
            } catch (e: Exception) {
                com.anuj.obsidian.ObsidianCrashLogger.logWarning(TAG, "Failed to release extractor: ${e.message}")
            }
        }
    }

    private fun writeWavHeader(file: File, pcmDataLength: Long, sampleRate: Int, channels: Int, bitDepth: Int) {
        val actualBitsPerSample = when (bitDepth) {
            16 -> 16
            20, 24 -> 24
            32 -> 32
            else -> 16
        }
        val bytesPerSample = actualBitsPerSample / 8
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = channels * bytesPerSample
        val totalDataLen = pcmDataLength + 36

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1.toShort()) // AudioFormat PCM = 1
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(actualBitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(pcmDataLength.toInt())

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header.array())
        }
    }
}

class BitReaderEofException : Exception("Unexpected EOF while reading ALAC bitstream")

private class AlacDecoderInstance(
    cookie: ByteArray?,
    val sampleRate: Int,
    val numChannels: Int,
    val bitDepth: Int,
    val frameLength: Int,
    val pb: Int,
    val mb: Int,
    val kb: Int
) {
    private var activeBitDepth = bitDepth
    private val bitReader = BitReader()
    private val residualBuffers = Array(8) { IntArray(65536) }
    private val mode0 = IntArray(8)
    private val mode1 = IntArray(8)
    private val mode2 = IntArray(8)
    private val mode3 = IntArray(8)
    private val coefsBuffers = Array(8) { IntArray(32) }
    private val workingCoefs = IntArray(32)

    fun decodeFrame(frameData: ByteArray, frameLengthBytes: Int, outPcm: IntArray): Int {
        if (frameLengthBytes <= 0) return 0
        try {
            bitReader.reset(frameData, frameLengthBytes)

            val channels = bitReader.readBits(3) + 1
            if (channels !in 1..8) return 0

            bitReader.readBits(4) // unused
            val isPartialFrame = bitReader.readBits(1)
            bitReader.readBits(2) // unused
            val isEscapeFrame = bitReader.readBits(1)

            val currentFrameLength = if (isPartialFrame == 1) bitReader.readBits(32) else frameLength
            if (currentFrameLength <= 0 || currentFrameLength > 65536 || outPcm.size < currentFrameLength * channels) return 0

            if (isEscapeFrame == 1) {
                // Uncompressed PCM frame
                val uncompressedBitDepth = activeBitDepth
                val totalSamples = currentFrameLength * channels
                for (i in 0 until totalSamples) {
                    outPcm[i] = bitReader.readBitsSigned(uncompressedBitDepth)
                }
                return currentFrameLength
            }

            // Compressed ALAC frame
            val mixBits = bitReader.readBits(8)
            val mixRes = bitReader.readBits(8)

            for (c in 0 until channels) {
                mode0[c] = bitReader.readBits(4)
                mode1[c] = bitReader.readBits(4)
                mode2[c] = bitReader.readBits(3)
                val order = bitReader.readBits(5)
                mode3[c] = order

                if (order in 1..31) {
                    val cBuf = coefsBuffers[c]
                    for (i in 0 until order) {
                        cBuf[i] = bitReader.readBitsSigned(16)
                    }
                }
            }

            // Residual decoding per channel
            for (c in 0 until channels) {
                decodeResidual(bitReader, residualBuffers[c], currentFrameLength, pb, mb, kb, activeBitDepth)
            }

            // Apply predictor per channel
            for (c in 0 until channels) {
                val order = mode3[c]
                if (order in 1..31) {
                    unpredict(residualBuffers[c], coefsBuffers[c], order, currentFrameLength)
                }
            }

            // Channel unmixing
            if (channels == 2) {
                val ch0 = residualBuffers[0]
                val ch1 = residualBuffers[1]
                if (mixBits > 0) {
                    for (i in 0 until currentFrameLength) {
                        val u = ch0[i]
                        val v = ch1[i]
                        val left = u + v - ((v * mixBits) shr 8)
                        val right = left - v
                        outPcm[i * 2] = left
                        outPcm[i * 2 + 1] = right
                    }
                } else {
                    for (i in 0 until currentFrameLength) {
                        outPcm[i * 2] = ch0[i]
                        outPcm[i * 2 + 1] = ch1[i]
                    }
                }
            } else {
                val ch0 = residualBuffers[0]
                for (i in 0 until currentFrameLength) {
                    outPcm[i] = ch0[i]
                }
            }

            return currentFrameLength
        } catch (e: Exception) {
            Log.w("AlacDecoderInstance", "Failed to decode frame: ${e.message}")
            return 0
        }
    }

    private fun decodeScalar(br: BitReader, k: Int, bps: Int): Int {
        var x = 0
        while (x < 9) {
            val bit = br.readBits(1)
            if (bit == 1) break
            x++
        }
        if (x > 8) {
            return br.readBits(bps)
        } else if (k != 1) {
            val extraBits = br.peekBits(k)
            x = (x shl k) - x
            if (extraBits > 1) {
                x += extraBits - 1
                br.skipBits(k)
            } else {
                br.skipBits(k - 1)
            }
        }
        return x
    }

    private fun decodeResidual(br: BitReader, out: IntArray, count: Int, pb: Int, mb: Int, kb: Int, bps: Int) {
        var history = mb
        for (i in 0 until count) {
            var k = 31 - Integer.numberOfLeadingZeros((history shr 9) + 3)
            if (k > kb) k = kb

            val x = decodeScalar(br, k, bps)

            // Rice sign un-mapping
            val value = if ((x and 1) != 0) {
                -((x + 1) ushr 1)
            } else {
                x ushr 1
            }
            out[i] = value

            // Update Rice parameter history
            if (x > 0xFFFF) {
                history = 0xFFFF
            } else {
                history += x * pb - ((history * pb) shr 9)
            }
        }
    }

    private fun unpredict(residual: IntArray, coefs: IntArray, order: Int, count: Int) {
        if (order <= 0) return
        System.arraycopy(coefs, 0, workingCoefs, 0, order)

        for (i in 0 until count) {
            var pred = 0L
            for (j in 0 until order) {
                if (i - 1 - j >= 0) {
                    pred += workingCoefs[j].toLong() * residual[i - 1 - j].toLong()
                }
            }
            val resVal = residual[i]
            val sampleVal = (resVal + (pred shr 9)).toInt()
            residual[i] = sampleVal

            // Adapt predictor coefficients using LMS
            if (resVal != 0) {
                val signRes = if (resVal > 0) 1 else -1
                for (j in 0 until order) {
                    if (i - 1 - j >= 0) {
                        val prevSample = residual[i - 1 - j]
                        val signPrev = if (prevSample > 0) 1 else if (prevSample < 0) -1 else 0
                        workingCoefs[j] += signRes * signPrev
                    }
                }
            }
        }
    }
}

private class BitReader {
    private var buffer: ByteArray = ByteArray(0)
    private var length = 0
    private var byteIdx = 0
    private var bitIdx = 0

    fun reset(buf: ByteArray, len: Int) {
        buffer = buf
        length = len
        byteIdx = 0
        bitIdx = 0
    }

    fun readBits(count: Int): Int {
        if (count <= 0) return 0
        var result = 0
        var bitsNeeded = count
        while (bitsNeeded > 0) {
            if (byteIdx >= length) {
                throw BitReaderEofException()
            }
            val currentByte = buffer[byteIdx].toInt() and 0xFF
            val bitsAvailable = 8 - bitIdx
            val bitsToRead = Math.min(bitsNeeded, bitsAvailable)

            val shift = bitsAvailable - bitsToRead
            val mask = ((1 shl bitsToRead) - 1) shl shift
            val chunk = (currentByte and mask) shr shift

            result = (result shl bitsToRead) or chunk
            bitIdx += bitsToRead
            if (bitIdx >= 8) {
                bitIdx = 0
                byteIdx++
            }
            bitsNeeded -= bitsToRead
        }
        return result
    }

    fun peekBits(count: Int): Int {
        if (count <= 0) return 0
        val savedByteIdx = byteIdx
        val savedBitIdx = bitIdx
        try {
            return readBits(count)
        } finally {
            byteIdx = savedByteIdx
            bitIdx = savedBitIdx
        }
    }

    fun skipBits(count: Int) {
        if (count <= 0) return
        bitIdx += count
        byteIdx += bitIdx shr 3
        bitIdx = bitIdx and 7
    }

    fun readBitsSigned(count: Int): Int {
        if (count <= 0) return 0
        val v = readBits(count)
        if (count >= 32) return v
        val shift = 32 - count
        return (v shl shift) shr shift
    }
}
