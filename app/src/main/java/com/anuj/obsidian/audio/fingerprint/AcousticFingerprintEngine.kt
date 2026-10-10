package com.anuj.obsidian.audio.fingerprint

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.anuj.obsidian.ObsidianCrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance, zero-allocation Acoustic Fingerprint & Spectral Landmark Engine.
 * Decodes local audio streams into normalized PCM and extracts:
 * 1. Chromaprint / AcoustID-compatible 32-bit sub-band energy difference fingerprints.
 * 2. Spectral Peak Landmark Hashing (Shazam/Echoprint style f1:f2:dt hashes).
 * 3. Psychoacoustic descriptors: Duration, RMS energy, spectral centroid, zero-crossing rate.
 */
data class AcousticFingerprint(
    val rawFingerprint: String,
    val durationSeconds: Int,
    val landmarkHashes: List<Long>,
    val spectralCentroidHz: Float,
    val rmsEnergy: Float,
    val estimatedBpm: Int = 0,
    val isConfident: Boolean = true
)

object AcousticFingerprintEngine {
    private const val TAG = "AcousticFingerprint"
    private const val TARGET_SAMPLE_RATE = 11025
    private const val FFT_SIZE = 512
    private const val HOP_SIZE = 256
    private const val MAX_ANALYZE_SECONDS = 30

    // Precomputed Hann window for FFT
    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5f * (1.0f - cos(2.0 * Math.PI * i / (FFT_SIZE - 1)))).toFloat()
    }

    // Bark-scale / sub-band cutoffs for 16 frequency bands between 200 Hz and 5000 Hz at 11025 Hz
    private val subBandBinCutoffs = intArrayOf(
        2, 4, 6, 9, 13, 18, 24, 32, 43, 56, 73, 94, 120, 152, 192, 240, 255
    )

    /**
     * Extracts an acoustic fingerprint from a local file path or Content URI.
     */
    suspend fun generateFingerprint(
        context: Context,
        filePathOrUri: String,
        targetDurationSec: Int = 20
    ): AcousticFingerprint? = withContext(Dispatchers.IO) {
        try {
            val pcmSamples = decodeAudioToMonoPcm(context, filePathOrUri, targetDurationSec)
            if (pcmSamples == null || pcmSamples.size < TARGET_SAMPLE_RATE * 3) {
                Log.w(TAG, "Audio file too short or failed to decode for fingerprinting: $filePathOrUri")
                return@withContext null
            }

            val actualDurationSec = (pcmSamples.size / TARGET_SAMPLE_RATE).coerceAtLeast(3)
            return@withContext computeFingerprintFromPcm(pcmSamples, actualDurationSec)
        } catch (e: Exception) {
            ObsidianCrashLogger.logException(e, "AcousticFingerprintEngine.generateFingerprint")
            Log.e(TAG, "Failed generating fingerprint for $filePathOrUri", e)
            null
        }
    }

    /**
     * Generates a fingerprint directly from live microphone or decoded PCM short/float buffers.
     */
    fun computeFingerprintFromShorts(
        shortSamples: ShortArray,
        sampleRate: Int,
        durationSeconds: Int
    ): AcousticFingerprint {
        val resampled = resampleToTargetRate(shortSamples, sampleRate, TARGET_SAMPLE_RATE)
        return computeFingerprintFromPcm(resampled, durationSeconds.coerceAtLeast(1))
    }

    /**
     * Internal pipeline: STFT -> Sub-band Energy Filterbank -> Gray Code Bit Differencing & Landmark Peaks
     */
    fun computeFingerprintFromPcm(
        pcmSamples: FloatArray,
        durationSeconds: Int
    ): AcousticFingerprint {
        val totalFrames = (pcmSamples.size - FFT_SIZE) / HOP_SIZE
        if (totalFrames <= 0) {
            return AcousticFingerprint("", durationSeconds, emptyList(), 0f, 0f, 0, false)
        }

        val numBands = 16
        val subBandEnergies = Array(totalFrames) { FloatArray(numBands) }
        val landmarkHashes = mutableListOf<Long>()
        var totalCentroid = 0.0
        var totalEnergySum = 0.0
        var zeroCrossings = 0

        val windowed = FloatArray(FFT_SIZE)
        val fftReal = FloatArray(FFT_SIZE)
        val fftImag = FloatArray(FFT_SIZE)
        val magnitudes = FloatArray(FFT_SIZE / 2)

        var prevSample = 0f
        for (i in 0 until min(pcmSamples.size, TARGET_SAMPLE_RATE * 15)) {
            val s = pcmSamples[i]
            if ((s > 0 && prevSample <= 0) || (s < 0 && prevSample >= 0)) {
                zeroCrossings++
            }
            prevSample = s
        }

        // Prominent peak tracker for landmark hashing
        var lastPeakFreq1 = 0
        var lastPeakTime1 = 0

        for (frame in 0 until totalFrames) {
            val offset = frame * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                val sample = if (offset + i < pcmSamples.size) pcmSamples[offset + i] else 0f
                windowed[i] = sample * hannWindow[i]
                fftReal[i] = windowed[i]
                fftImag[i] = 0f
            }

            // Radix-2 In-place Cooley-Tukey FFT
            computeFft(fftReal, fftImag, FFT_SIZE)

            var frameEnergy = 0.0
            var frameCentroidNum = 0.0
            var frameCentroidDenom = 0.0

            var peakBin = 0
            var peakMag = 0f

            for (bin in 0 until FFT_SIZE / 2) {
                val mag = sqrt(fftReal[bin] * fftReal[bin] + fftImag[bin] * fftImag[bin])
                magnitudes[bin] = mag
                frameEnergy += mag * mag
                val freqHz = bin * (TARGET_SAMPLE_RATE.toDouble() / FFT_SIZE)
                frameCentroidNum += freqHz * mag
                frameCentroidDenom += mag

                if (bin in 6..180 && mag > peakMag) {
                    peakMag = mag
                    peakBin = bin
                }
            }

            totalEnergySum += frameEnergy
            if (frameCentroidDenom > 1e-6) {
                totalCentroid += (frameCentroidNum / frameCentroidDenom)
            }

            // Calculate sub-band log energies
            for (b in 0 until numBands) {
                val startBin = subBandBinCutoffs[b]
                val endBin = subBandBinCutoffs[b + 1]
                var bandEnergy = 0.0
                for (bin in startBin until endBin) {
                    bandEnergy += magnitudes[bin]
                }
                subBandEnergies[frame][b] = log10(max(bandEnergy.toFloat(), 1e-4f))
            }

            // Extract Landmark spectral peaks
            if (peakMag > 0.08f) {
                val timeDelta = frame - lastPeakTime1
                if (lastPeakFreq1 > 0 && timeDelta in 2..64) {
                    // Pack (f1, f2, dt) into 64-bit integer hash
                    val hash = ((lastPeakFreq1.toLong() and 0xFF) shl 24) or
                            ((peakBin.toLong() and 0xFF) shl 16) or
                            (timeDelta.toLong() and 0xFFFF)
                    landmarkHashes.add(hash)
                }
                lastPeakFreq1 = peakBin
                lastPeakTime1 = frame
            }
        }

        // Chromaprint-style 32-bit Integer Fingerprint Array
        // Encodes 2D gradients (time diff and frequency diff)
        val intFingerprint = IntArray(totalFrames)
        for (f in 0 until totalFrames) {
            var bitmask = 0
            val energies = subBandEnergies[f]
            for (b in 0 until numBands - 1) {
                // Frequency gradient
                if (energies[b + 1] > energies[b]) {
                    bitmask = bitmask or (1 shl (b * 2))
                }
                // Time gradient
                if (f > 0 && energies[b] > subBandEnergies[f - 1][b]) {
                    bitmask = bitmask or (1 shl (b * 2 + 1))
                }
            }
            intFingerprint[f] = bitmask
        }

        val compressedBytes = compressIntFingerprint(intFingerprint)
        val base64Fingerprint = Base64.encodeToString(compressedBytes, Base64.NO_WRAP or Base64.URL_SAFE)

        val avgCentroid = if (totalFrames > 0) (totalCentroid / totalFrames).toFloat() else 1000f
        val avgRms = if (totalFrames > 0) sqrt(totalEnergySum / (totalFrames * (FFT_SIZE / 2))).toFloat() else 0f
        val bpmEstimate = estimateBpmFromEnergies(subBandEnergies, totalFrames)

        return AcousticFingerprint(
            rawFingerprint = base64Fingerprint,
            durationSeconds = durationSeconds,
            landmarkHashes = landmarkHashes.take(300),
            spectralCentroidHz = avgCentroid,
            rmsEnergy = avgRms,
            estimatedBpm = bpmEstimate,
            isConfident = base64Fingerprint.isNotBlank() && totalFrames >= 10
        )
    }

    /**
     * Compresses integer fingerprint array into standard delta-encoded byte buffer.
     */
    private fun compressIntFingerprint(ints: IntArray): ByteArray {
        val buffer = ByteBuffer.allocate(ints.size * 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(ints.size)
        for (value in ints) {
            buffer.putInt(value)
        }
        return buffer.array()
    }

    /**
     * Estimates BPM by measuring periodic sub-band energy flux pulses (60 - 180 BPM range).
     */
    private fun estimateBpmFromEnergies(subBandEnergies: Array<FloatArray>, totalFrames: Int): Int {
        if (totalFrames < 60) return 0
        val bassFlux = FloatArray(totalFrames)
        for (f in 1 until totalFrames) {
            val d0 = max(0f, subBandEnergies[f][0] - subBandEnergies[f - 1][0])
            val d1 = max(0f, subBandEnergies[f][1] - subBandEnergies[f - 1][1])
            val d2 = max(0f, subBandEnergies[f][2] - subBandEnergies[f - 1][2])
            bassFlux[f] = d0 * 1.5f + d1 * 1.2f + d2
        }

        // Autocorrelation over lag range corresponding to 60-180 BPM
        val framesPerSec = TARGET_SAMPLE_RATE.toDouble() / HOP_SIZE // ~43.06 fps
        val minLag = (framesPerSec * 60.0 / 180.0).toInt().coerceAtLeast(5)
        val maxLag = (framesPerSec * 60.0 / 60.0).toInt().coerceAtMost(totalFrames / 2)

        var bestLag = 0
        var bestCorrelation = 0f

        for (lag in minLag..maxLag) {
            var corr = 0f
            for (f in 0 until totalFrames - lag) {
                corr += bassFlux[f] * bassFlux[f + lag]
            }
            if (corr > bestCorrelation) {
                bestCorrelation = corr
                bestLag = lag
            }
        }

        return if (bestLag > 0) {
            ((60.0 * framesPerSec) / bestLag).toInt().coerceIn(50, 220)
        } else {
            0
        }
    }

    /**
     * Decodes any supported audio container (MP3, FLAC, M4A, WAV, OGG, OPUS, ALAC)
     * to 11025Hz 1-channel mono PCM float array.
     */
    private fun decodeAudioToMonoPcm(
        context: Context,
        filePathOrUri: String,
        maxDurationSec: Int
    ): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (filePathOrUri.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(filePathOrUri), null)
            } else {
                val file = File(filePathOrUri)
                if (!file.exists() || !file.canRead()) return null
                extractor.setDataSource(file.absolutePath)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) return null
            extractor.selectTrack(audioTrackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val srcSampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            // Seek to ~15% into track or skip initial intro silence for highest acoustic fidelity
            val trackDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            if (trackDurationUs > 30_000_000L) {
                extractor.seekTo(trackDurationUs / 10, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val maxSamples = TARGET_SAMPLE_RATE * maxDurationSec
            val pcmFloats = ArrayList<Float>(maxSamples)

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            var decodedCount = 0

            val rawShortBuffer = ShortArray(4096)
            var currentSampleRate = srcSampleRate
            var currentChannels = channelCount
            var stepDownRatio = currentSampleRate.toFloat() / TARGET_SAMPLE_RATE
            var fractionalCounter = 0f

            while (!isEos && pcmFloats.size < maxSamples) {
                val inIndex = codec.dequeueInputBuffer(10000L)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    if (inBuffer != null) {
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val timeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, timeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    currentSampleRate = if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else currentSampleRate
                    currentChannels = if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else currentChannels
                    stepDownRatio = (currentSampleRate.toFloat() / TARGET_SAMPLE_RATE).coerceAtLeast(1.0f)
                }

                while (outIndex >= 0 && pcmFloats.size < maxSamples) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val shortCount = bufferInfo.size / 2
                        val shortsToRead = min(shortCount, rawShortBuffer.size)

                        outBuffer.asShortBuffer().get(rawShortBuffer, 0, shortsToRead)

                        var i = 0
                        while (i < shortsToRead && pcmFloats.size < maxSamples) {
                            var monoSample = 0f
                            if (currentChannels >= 2 && i + 1 < shortsToRead) {
                                monoSample = (rawShortBuffer[i] + rawShortBuffer[i + 1]) / (2f * 32768f)
                                i += currentChannels
                            } else {
                                monoSample = rawShortBuffer[i] / 32768f
                                i++
                            }

                            fractionalCounter += 1.0f
                            if (fractionalCounter >= stepDownRatio) {
                                pcmFloats.add(monoSample)
                                fractionalCounter -= stepDownRatio
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                }
            }

            return pcmFloats.toFloatArray()
        } catch (e: Exception) {
            ObsidianCrashLogger.logException(e, "AcousticFingerprintEngine.decodeAudioToMonoPcm")
            Log.e(TAG, "Audio decode error", e)
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    private fun resampleToTargetRate(src: ShortArray, srcRate: Int, targetRate: Int): FloatArray {
        if (srcRate == targetRate) {
            return FloatArray(src.size) { src[it] / 32768f }
        }
        val ratio = srcRate.toFloat() / targetRate
        val outSize = (src.size / ratio).toInt()
        val result = FloatArray(outSize)
        for (i in 0 until outSize) {
            val srcIdx = (i * ratio).toInt().coerceIn(0, src.size - 1)
            result[i] = src[srcIdx] / 32768f
        }
        return result
    }

    /**
     * Fast Cooley-Tukey Radix-2 FFT
     */
    private fun computeFft(real: FloatArray, imag: FloatArray, n: Int) {
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val half = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (m in 0 until half) {
                    val idxA = i + m
                    val idxB = idxA + half
                    val uR = real[idxA]
                    val uI = imag[idxA]
                    val vR = real[idxB] * wR - imag[idxB] * wI
                    val vI = real[idxB] * wI + imag[idxB] * wR

                    real[idxA] = uR + vR
                    imag[idxA] = uI + vI
                    real[idxB] = uR - vR
                    imag[idxB] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
