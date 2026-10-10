package com.anuj.obsidian.ui.dsp

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard Time-Domain FIR Ring-Buffer Convolution Reverb Engine for User Impulse Responses (.wav).
 * 
 * Allows loading custom impulse response (.wav) files from device storage or bundled presets,
 * parsing raw PCM audio samples from the WAV header, and executing convolution processing.
 */
class ObsidianConvolutionEngine {
    private companion object {
        private const val TAG = "ObsidianConvolver"
        private const val MAX_IR_SAMPLES = 22050 // ~0.5 second at 44.1kHz for efficient real-time DSP
        private const val MAX_IR_FILE_SIZE = 10 * 1024 * 1024L
    }

    private var irL = FloatArray(0)
    private var irR = FloatArray(0)
    private var irLength = 0

    private var historyL = FloatArray(MAX_IR_SAMPLES)
    private var historyR = FloatArray(MAX_IR_SAMPLES)
    private var writeIndex = 0

    @Volatile
    var isLoaded = false
        private set

    fun reset() {
        historyL.fill(0f)
        historyR.fill(0f)
        writeIndex = 0
    }

    /**
     * Loads a user-supplied impulse response file (.wav) from path or content URI.
     */
    fun loadImpulseFromUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseWavStream(inputStream)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load user IR from URI: ${e.message}", e)
            false
        }
    }

    /**
     * Loads an impulse response file from local file path.
     */
    fun loadImpulseFromFile(filePath: String): Boolean {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) return false
            file.inputStream().use { inputStream ->
                parseWavStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load user IR from path: ${e.message}", e)
            false
        }
    }

    /**
     * Generates a synthetic acoustic room impulse response if no user WAV file is loaded.
     */
    fun generateSyntheticImpulse(decayTimeMs: Int = 800, sampleRate: Int = 44100) {
        val numSamples = ((decayTimeMs / 1000.0f) * sampleRate).toInt().coerceIn(1000, MAX_IR_SAMPLES)
        val l = FloatArray(numSamples)
        val r = FloatArray(numSamples)
        val random = java.util.Random(42)

        val decayFactor = -6.9078f / numSamples // ln(0.001) / N for -60dB decay

        for (i in 0 until numSamples) {
            val envelope = kotlin.math.exp((decayFactor * i).toDouble()).toFloat()
            val noiseL = (random.nextFloat() * 2.0f - 1.0f) * envelope
            val noiseR = (random.nextFloat() * 2.0f - 1.0f) * envelope

            // Early reflection spike + diffuse tail
            if (i == 0) {
                l[i] = 1.0f
                r[i] = 1.0f
            } else if (i < 800 && i % 120 == 0) {
                l[i] = noiseL * 0.8f
                r[i] = noiseR * 0.8f
            } else {
                l[i] = noiseL * 0.35f
                r[i] = noiseR * 0.35f
            }
        }

        irL = l
        irR = r
        irLength = numSamples
        reset()
        isLoaded = true
    }

    private fun parseWavStream(inputStream: InputStream): Boolean {
        try {
            val riffHeader = ByteArray(12)
            var read = 0
            while (read < 12) {
                val c = inputStream.read(riffHeader, read, 12 - read)
                if (c < 0) return false
                read += c
            }

            val chunkId = String(riffHeader, 0, 4)
            val format = String(riffHeader, 8, 4)
            if (chunkId != "RIFF" || format != "WAVE") return false

            val riffSize = (
                ((riffHeader[7].toInt() and 0xFF) shl 24) or
                ((riffHeader[6].toInt() and 0xFF) shl 16) or
                ((riffHeader[5].toInt() and 0xFF) shl 8) or
                (riffHeader[4].toInt() and 0xFF)
            ).toLong() and 0xFFFFFFFFL

            if (riffSize > MAX_IR_FILE_SIZE) return false

            var audioFormat = 1
            var numChannels = 2
            var sampleRate = 44100
            var bitsPerSample = 16
            var foundFmt = false
            var foundData = false
            var dataSize = 0L

            var bytesConsumed = 12L

            while (!foundData && bytesConsumed < MAX_IR_FILE_SIZE) {
                val chunkHeader = ByteArray(8)
                var chRead = 0
                while (chRead < 8) {
                    val c = inputStream.read(chunkHeader, chRead, 8 - chRead)
                    if (c < 0) break
                    chRead += c
                }
                if (chRead < 8) break
                bytesConsumed += 8L

                val subchunkId = String(chunkHeader, 0, 4)
                val subchunkSize = (
                    ((chunkHeader[7].toInt() and 0xFF) shl 24) or
                    ((chunkHeader[6].toInt() and 0xFF) shl 16) or
                    ((chunkHeader[5].toInt() and 0xFF) shl 8) or
                    (chunkHeader[4].toInt() and 0xFF)
                ).toLong() and 0xFFFFFFFFL

                if (subchunkSize > MAX_IR_FILE_SIZE) return false
                val alignedChunkSize = subchunkSize + (subchunkSize and 1L)

                if (subchunkId == "fmt " && subchunkSize >= 16) {
                    val fmtData = ByteArray(subchunkSize.toInt())
                    var fRead = 0
                    while (fRead < fmtData.size) {
                        val c = inputStream.read(fmtData, fRead, fmtData.size - fRead)
                        if (c < 0) return false
                        fRead += c
                    }
                    bytesConsumed += alignedChunkSize

                    val fmtBuf = ByteBuffer.wrap(fmtData).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormat = fmtBuf.getShort(0).toInt() and 0xFFFF
                    numChannels = fmtBuf.getShort(2).toInt() and 0xFFFF
                    sampleRate = fmtBuf.getInt(4)
                    bitsPerSample = fmtBuf.getShort(14).toInt() and 0xFFFF
                    foundFmt = true

                    if (audioFormat != 1 && audioFormat != 3 && audioFormat != 65534) return false
                    if (numChannels !in 1..8) return false
                    if (sampleRate !in 8000..384000) return false
                    if (bitsPerSample !in listOf(8, 16, 20, 24, 32)) return false
                } else if (subchunkId == "data") {
                    foundData = true
                    dataSize = subchunkSize
                } else {
                    // Skip unknown chunk
                    var toSkip = alignedChunkSize
                    while (toSkip > 0) {
                        val skipped = inputStream.skip(toSkip)
                        if (skipped <= 0) {
                            if (inputStream.read() == -1) break
                            toSkip--
                        } else {
                            toSkip -= skipped
                        }
                    }
                    bytesConsumed += alignedChunkSize
                }
            }

            if (!foundFmt || !foundData || dataSize <= 0) return false

            val bytesPerSample = (bitsPerSample / 8)
            val bytesPerFrame = bytesPerSample * numChannels
            if (bytesPerFrame <= 0) return false

            val totalSamples = (dataSize / bytesPerFrame).toInt().coerceAtLeast(0)
            if (totalSamples <= 0) return false

            val count = totalSamples.coerceAtMost(MAX_IR_SAMPLES)
            val parsedL = FloatArray(count)
            val parsedR = FloatArray(count)

            val frameBuf = ByteArray(bytesPerFrame)
            val bb = ByteBuffer.wrap(frameBuf).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until count) {
                var fRead = 0
                while (fRead < bytesPerFrame) {
                    val c = inputStream.read(frameBuf, fRead, bytesPerFrame - fRead)
                    if (c < 0) break
                    fRead += c
                }
                if (fRead < bytesPerFrame) break

                bb.rewind()

                val (sL, sR) = when {
                    audioFormat == 3 && bitsPerSample == 32 -> { // IEEE Float
                        var fL = bb.getFloat(0)
                        var fR = if (numChannels >= 2) bb.getFloat(4) else fL
                        if (!fL.isFinite()) fL = 0f
                        if (!fR.isFinite()) fR = 0f
                        Pair(fL.coerceIn(-1.0f, 1.0f), fR.coerceIn(-1.0f, 1.0f))
                    }
                    audioFormat == 1 && bitsPerSample == 32 -> { // 32-bit Int PCM
                        val iL = bb.getInt(0)
                        val iR = if (numChannels >= 2) bb.getInt(4) else iL
                        Pair((iL / 2147483648.0f).coerceIn(-1.0f, 1.0f), (iR / 2147483648.0f).coerceIn(-1.0f, 1.0f))
                    }
                    bitsPerSample == 24 -> { // 24-bit Int PCM
                        val b0 = frameBuf[0].toInt() and 0xFF
                        val b1 = frameBuf[1].toInt() and 0xFF
                        val b2 = frameBuf[2].toInt()
                        var rawL = (b2 shl 16) or (b1 shl 8) or b0
                        if ((b2 and 0x80) != 0) rawL = rawL or 0xFF000000.toInt()
                        val sampleL = rawL / 8388608.0f

                        val sampleR = if (numChannels >= 2) {
                            val b3 = frameBuf[3].toInt() and 0xFF
                            val b4 = frameBuf[4].toInt() and 0xFF
                            val b5 = frameBuf[5].toInt()
                            var rawR = (b5 shl 16) or (b4 shl 8) or b3
                            if ((b5 and 0x80) != 0) rawR = rawR or 0xFF000000.toInt()
                            rawR / 8388608.0f
                        } else sampleL
                        Pair(sampleL.coerceIn(-1.0f, 1.0f), sampleR.coerceIn(-1.0f, 1.0f))
                    }
                    bitsPerSample == 16 -> { // 16-bit Int PCM
                        val s16L = bb.getShort(0) / 32768.0f
                        val s16R = if (numChannels >= 2) bb.getShort(2) / 32768.0f else s16L
                        Pair(s16L, s16R)
                    }
                    else -> Pair(0f, 0f)
                }

                parsedL[i] = sL
                parsedR[i] = sR
            }

            irL = parsedL
            irR = parsedR
            irLength = count
            reset()
            isLoaded = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WAV stream: ${e.message}", e)
            return false
        }
    }

    private val tempOut = FloatArray(2)

    /**
     * Process stereo sample input (inL, inR) with convolution and wet/dry mix.
     * Writes result to outputArray [outL, outR] without allocating objects.
     */
    fun processToBuffer(inL: Float, inR: Float, wetPercent: Float, outputArray: FloatArray) {
        if (!isLoaded || irLength == 0 || wetPercent <= 0f) {
            outputArray[0] = inL
            outputArray[1] = inR
            return
        }

        val wet = (wetPercent / 100.0f).coerceIn(0.0f, 1.0f)
        val dry = 1.0f - wet * 0.5f

        historyL[writeIndex] = inL
        historyR[writeIndex] = inR

        var convL = 0.0f
        var convR = 0.0f

        var readIdx = writeIndex
        for (k in 0 until irLength) {
            convL += historyL[readIdx] * irL[k]
            convR += historyR[readIdx] * irR[k]
            readIdx--
            if (readIdx < 0) readIdx = MAX_IR_SAMPLES - 1
        }

        writeIndex = (writeIndex + 1) % MAX_IR_SAMPLES

        outputArray[0] = (inL * dry + convL * wet * 0.25f).coerceIn(-1.0f, 1.0f)
        outputArray[1] = (inR * dry + convR * wet * 0.25f).coerceIn(-1.0f, 1.0f)
    }

    /**
     * Process stereo sample input (inL, inR) with convolution and wet/dry mix.
     */
    fun process(inL: Float, inR: Float, wetPercent: Float): Pair<Float, Float> {
        processToBuffer(inL, inR, wetPercent, tempOut)
        return Pair(tempOut[0], tempOut[1])
    }
}
