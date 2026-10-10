package com.anuj.obsidian.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

import androidx.collection.LruCache

object AudioWaveformExtractor {
    private val memoryCache = LruCache<String, FloatArray>(128)

    suspend fun extractWaveformAmplitudes(
        context: Context,
        trackId: Long,
        localFilePath: String,
        barCount: Int = 54
    ): FloatArray? {
        val cacheKey = "${trackId}_$barCount"
        val inMemory = memoryCache.get(cacheKey)
        if (inMemory != null) {
            return inMemory
        }

        return withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, "waveform_v2_${trackId}_${barCount}.bin")
            if (cacheFile.exists()) {
                try {
                    val expectedBytes = barCount * 4
                    val bytes = cacheFile.inputStream().use { input ->
                        val buf = ByteArray(expectedBytes)
                        val bytesRead = input.read(buf)
                        if (bytesRead == expectedBytes) buf else null
                    }
                    if (bytes != null) {
                        val floatBuffer = ByteBuffer.wrap(bytes).asFloatBuffer()
                        val array = FloatArray(barCount)
                        floatBuffer.get(array)
                        memoryCache.put(cacheKey, array)
                        return@withContext array
                    }
                } catch (e: Exception) {
                    // fallthrough to regenerate
                }
            }
        
        if (localFilePath.startsWith("http") || localFilePath.isBlank()) return@withContext null
        
        val extractor = MediaExtractor()
        try {
            if (localFilePath.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(localFilePath), null)
            } else {
                extractor.setDataSource(localFilePath)
            }
            
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            var mime: String? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME)
                if (m?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = f
                    mime = m
                    extractor.selectTrack(i)
                    break
                }
            }
            
            if (audioTrackIndex < 0 || format == null || mime == null) {
                extractor.release()
                return@withContext null
            }
            
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            if (durationUs <= 0) {
                extractor.release()
                return@withContext null
            }
            
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            
            val chunkDurationUs = durationUs / barCount
            val amplitudes = FloatArray(barCount)
            var currentBar = 0
            var chunkMax = 0f
            var globalMax = 0.001f
            
            val info = MediaCodec.BufferInfo()
            var isEOS = false
            
            while (!isEOS) {
                val inIndex = codec.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)
                    if (buffer != null) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                
                var outIndex = codec.dequeueOutputBuffer(info, 10000)
                while (outIndex >= 0) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true
                    }
                    if (info.size > 0) {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null) {
                            outBuffer.position(info.offset)
                            outBuffer.limit(info.offset + info.size)
                            
                            val timeUs = info.presentationTimeUs
                            val barIndex = (timeUs / chunkDurationUs).toInt().coerceIn(0, barCount - 1)
                            
                            if (barIndex > currentBar) {
                                amplitudes[currentBar] = chunkMax
                                if (chunkMax > globalMax) globalMax = chunkMax
                                currentBar = barIndex
                                chunkMax = 0f
                            }
                            
                            while (outBuffer.remaining() >= 2) { // 16-bit PCM
                                val sample = Math.abs(outBuffer.short.toInt().toFloat())
                                if (sample > chunkMax) chunkMax = sample
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    outIndex = codec.dequeueOutputBuffer(info, 0)
                }
            }
            amplitudes[currentBar] = chunkMax
            if (chunkMax > globalMax) globalMax = chunkMax
            
            codec.stop()
            codec.release()
            extractor.release()
            
            for (i in amplitudes.indices) {
                amplitudes[i] = (amplitudes[i] / globalMax).coerceIn(0.1f, 1f)
            }
            
            try {
                val byteBuffer = ByteBuffer.allocate(barCount * 4)
                byteBuffer.asFloatBuffer().put(amplitudes)
                cacheFile.writeBytes(byteBuffer.array())
            } catch (e: Exception) { }
            
            memoryCache.put(cacheKey, amplitudes)
            return@withContext amplitudes
        } catch (e: Exception) {
            extractor.release()
            return@withContext null
        }
    }
}
}
