package com.anuj.obsidian.ui.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.anuj.obsidian.data.model.ObsidianDspState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Obsidian In-App PCM AudioProcessor inserted into ExoPlayer's DefaultAudioSink pipeline.
 * 
 * Implements Tier 2 Original PCM DSP Suite:
 * 1. Multi-band Graphic EQ (10, 15, 25, 31 band options)
 * 2. Tube / Analog Warmth Saturation (Waveshaping)
 * 3. Multiband Compressor (3-band crossover + envelope followers)
 * 4. Psychoacoustic Bass Enhancement (Harmonic generator + missing fundamental)
 * 5. Upper Harmonic Exciter / Clarity
 * 6. Stereo Imager / Mid-Side Width Control
 * 7. Bauer Crossfeed (Headphone realism)
 * 8. User-Supplied Convolution Reverb Integration
 * 9. Dynamic EQ
 */
@UnstableApi
class ObsidianPcmDspAudioProcessor : BaseAudioProcessor() {

    private fun fastTanh(x: Float): Float {
        if (x <= -3.0f) return -1.0f
        if (x >= 3.0f) return 1.0f
        val x2 = x * x
        return x * (27.0f + x2) / (27.0f + 9.0f * x2)
    }

    private val lock = Any()

    @Volatile
    private var dspState = ObsidianDspState()

    // Sample format & rate
    private var sampleRate = 44100
    private var channelCount = 2

    // 1. Multi-band EQ Filters (Up to 31 bands)
    private var eqBandCount = 10
    private var eqCenterFreqs = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val eqFilters = Array(31) { ObsidianBiquadFilter() }

    // 2. Tube Saturation state
    private var tubeDrive = 0.4f

    // 3. Multiband Compressor (Low < 250Hz, Mid 250Hz-4kHz, High > 4kHz)
    private val compLowLpf = ObsidianBiquadFilter()
    private val compMidBpf = ObsidianBiquadFilter()
    private val compHighHpf = ObsidianBiquadFilter()
    private var envLow = 0.0f
    private var envMid = 0.0f
    private var envHigh = 0.0f

    // 4. Psychoacoustic Bass Generator
    private val bassLpf = ObsidianBiquadFilter()
    private val bassHpfHarmonics = ObsidianBiquadFilter()

    // 5. Clarity Exciter
    private val exciterHpf = ObsidianBiquadFilter()
    private val exciterBpf = ObsidianBiquadFilter()

    // 6. Bauer Crossfeed delay buffer (14 samples for ~0.3ms @ 44.1kHz)
    private val crossfeedLpfL = ObsidianBiquadFilter()
    private val crossfeedLpfR = ObsidianBiquadFilter()
    private val delayBufferL = FloatArray(64)
    private val delayBufferR = FloatArray(64)
    private var delayIdx = 0

    // 7. Convolution Reverb Engine
    val convolverEngine = ObsidianConvolutionEngine()

    // Reusable convolver buffer
    private val convolverBuffer = FloatArray(2)

    // 8. Dynamic EQ Envelope Followers
    private val dynEqFilters = Array(10) { ObsidianBiquadFilter() }
    private val dynEqEnvelopes = FloatArray(10)

    // Encoding handled: C.ENCODING_PCM_16BIT or C.ENCODING_PCM_FLOAT
    private var encoding = C.ENCODING_PCM_16BIT

    // Tap buffer for native visualizer and audio analysis engine
    private val pcmTapBuffer = FloatArray(1024)
    private var pcmTapCount = 0

    init {
        reconfigureEqBands(10)
    }

    fun updateState(newState: ObsidianDspState) {
        synchronized(lock) {
            dspState = newState
            if (sampleRate > 0) {
                configureFilters()
            }
        }
    }

    fun setEqBandCount(bands: Int) {
        synchronized(lock) {
            val count = when (bands) {
                15 -> 15
                25 -> 25
                31 -> 31
                else -> 10
            }
            if (eqBandCount != count) {
                eqBandCount = count
                reconfigureEqBands(count)
                if (sampleRate > 0) {
                    configureFilters()
                }
            }
        }
    }

    private fun reconfigureEqBands(bands: Int) {
        eqCenterFreqs = when (bands) {
            15 -> floatArrayOf(
                25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f,
                1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f
            )
            25 -> floatArrayOf(
                20f, 28f, 40f, 56f, 80f, 112f, 160f, 224f, 315f, 450f,
                630f, 900f, 1250f, 1800f, 2500f, 3550f, 5000f, 7100f,
                10000f, 12500f, 14000f, 16000f, 18000f, 20000f, 22000f
            )
            31 -> floatArrayOf(
                20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f,
                315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f, 2000f, 2500f,
                3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f
            )
            else -> floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        synchronized(lock) {
            configureFilters()
            resetFilterState()
        }

        return inputAudioFormat
    }

    override fun onFlush() {
        synchronized(lock) {
            resetFilterState()
        }
    }

    override fun onReset() {
        synchronized(lock) {
            resetFilterState()
        }
    }

    private fun resetFilterState() {
        for (f in eqFilters) f.reset()
        compLowLpf.reset()
        compMidBpf.reset()
        compHighHpf.reset()
        envLow = 0.0f; envMid = 0.0f; envHigh = 0.0f
        bassLpf.reset()
        bassHpfHarmonics.reset()
        exciterHpf.reset()
        exciterBpf.reset()
        crossfeedLpfL.reset()
        crossfeedLpfR.reset()
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        delayIdx = 0
        for (f in dynEqFilters) f.reset()
        dynEqEnvelopes.fill(0f)
        convolverEngine.reset()
    }

    private fun configureFilters() {
        val state = dspState
        val sr = sampleRate.toFloat()

        // 1. Multi-band Graphic EQ
        for (i in 0 until eqBandCount) {
            val freq = eqCenterFreqs[i]
            val gain = if (state.isEqEnabled && i < state.eqGains.size) state.eqGains[i] else 0.0f
            eqFilters[i].configure(
                type = ObsidianBiquadFilter.Type.PEAKING,
                centerFreqHz = freq,
                sampleRate = sr,
                gainDb = gain,
                q = 1.4f
            )
            eqFilters[i].isEnabled = state.isEqEnabled && abs(gain) > 0.01f
        }

        // 2. Multiband Compressor Filters
        compLowLpf.configure(ObsidianBiquadFilter.Type.LOW_PASS, 250f, sr, q = 0.707f)
        compMidBpf.configure(ObsidianBiquadFilter.Type.BAND_PASS, 1000f, sr, q = 0.5f)
        compHighHpf.configure(ObsidianBiquadFilter.Type.HIGH_PASS, 4000f, sr, q = 0.707f)

        // 3. Bass Generator Filters
        bassLpf.configure(ObsidianBiquadFilter.Type.LOW_PASS, state.bassFrequencyHz.toFloat(), sr, q = 0.707f)
        bassHpfHarmonics.configure(ObsidianBiquadFilter.Type.HIGH_PASS, 120f, sr, q = 0.707f)

        // 4. Clarity Exciter Filters
        exciterHpf.configure(ObsidianBiquadFilter.Type.HIGH_PASS, 3000f, sr, q = 0.707f)
        exciterBpf.configure(ObsidianBiquadFilter.Type.BAND_PASS, 8000f, sr, q = 0.8f)

        // 5. Crossfeed Low-Pass Filter
        crossfeedLpfL.configure(ObsidianBiquadFilter.Type.LOW_PASS, 700f, sr, q = 0.5f)
        crossfeedLpfR.configure(ObsidianBiquadFilter.Type.LOW_PASS, 700f, sr, q = 0.5f)

        // 6. Dynamic EQ Filters
        val dynFreqs = floatArrayOf(60f, 120f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 12000f, 16000f)
        for (i in 0 until 10) {
            dynEqFilters[i].configure(ObsidianBiquadFilter.Type.PEAKING, dynFreqs[i], sr, gainDb = 0f, q = 1.0f)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining() || channelCount <= 0) return

        val state = dspState
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val isFloat = (encoding == C.ENCODING_PCM_FLOAT)
        val bytesPerSample = if (isFloat) 4 else 2
        val bytesPerFrame = bytesPerSample * channelCount

        val remainingBytes = limit - position
        val frameCount = remainingBytes / bytesPerFrame
        if (frameCount <= 0) return

        val processableBytes = frameCount * bytesPerFrame
        val endOffset = position + processableBytes
        val outputBuffer = replaceOutputBuffer(processableBytes)

        if (!state.isMasterEnabled) {
            // Tap PCM for visualizer even in master bypass mode
            var tapOffset = position
            while (tapOffset < endOffset && pcmTapCount + 2 <= pcmTapBuffer.size) {
                val sL: Float
                val sR: Float
                if (isFloat) {
                    sL = inputBuffer.getFloat(tapOffset)
                    sR = if (channelCount >= 2) inputBuffer.getFloat(tapOffset + 4) else sL
                } else {
                    sL = inputBuffer.getShort(tapOffset) / 32768.0f
                    sR = if (channelCount >= 2) inputBuffer.getShort(tapOffset + 2) / 32768.0f else sL
                }
                pcmTapBuffer[pcmTapCount++] = sL
                if (channelCount >= 2) {
                    pcmTapBuffer[pcmTapCount++] = sR
                }
                tapOffset += bytesPerFrame
            }

            if (pcmTapCount >= 256) {
                val tapChannels = if (channelCount >= 2) 2 else 1
                // Feed real-time Audio Analysis Engine with zero GC allocations
                com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.feedPcmSamples(pcmTapBuffer, pcmTapCount, tapChannels)
                pcmTapCount = 0
            }

            val dup = inputBuffer.duplicate()
            dup.position(position)
            dup.limit(endOffset)
            outputBuffer.put(dup)
            inputBuffer.position(endOffset)
            outputBuffer.flip()
            return
        }

        val applyPreamp = abs(state.preampGainDb) > 0.01f
        val preampScale = if (applyPreamp) 10.0f.pow(state.preampGainDb / 20.0f) else 1.0f

        val tubeDrive = (state.tubeDrivePercent / 100.0f).coerceIn(0.0f, 1.0f)
        val tubeDriveMult = 1.0f + tubeDrive * 2.5f
        val tubeWet = tubeDrive * 0.7f
        val tubeNorm = 1.0f / (1.0f + tubeDrive * 0.5f)

        val bassGain = (state.bassGainDb / 18.0f).coerceIn(0.0f, 1.0f) * 0.5f
        val exciterGain = (state.clarityGainDb / 14.0f).coerceIn(0.0f, 1.0f) * 0.4f
        val widthScale = (state.surroundWidthPercent / 100.0f).coerceIn(0.0f, 2.0f)

        var srcOffset = position

        while (srcOffset < endOffset) {
            var sampleL: Float
            var sampleR: Float

            if (isFloat) {
                sampleL = inputBuffer.getFloat(srcOffset)
                sampleR = if (channelCount >= 2) inputBuffer.getFloat(srcOffset + 4) else sampleL
            } else {
                sampleL = inputBuffer.getShort(srcOffset) / 32768.0f
                sampleR = if (channelCount >= 2) inputBuffer.getShort(srcOffset + 2) / 32768.0f else sampleL
            }

            if (state.isMasterEnabled) {
                // Preamp Gain
                if (applyPreamp) {
                    sampleL *= preampScale
                    sampleR *= preampScale
                }

                // 1. Multi-band Graphic Equalizer
                if (state.isEqEnabled) {
                    for (i in 0 until eqBandCount) {
                        sampleL = eqFilters[i].processLeft(sampleL)
                        sampleR = eqFilters[i].processRight(sampleR)
                    }
                }

                // 2. Tube Saturation
                if (state.isTubeEnabled) {
                    val drivenL = sampleL * tubeDriveMult
                    val drivenR = sampleR * tubeDriveMult

                    val satL = fastTanh(drivenL)
                    val satR = fastTanh(drivenR)

                    sampleL = sampleL * (1.0f - tubeWet) + (satL * tubeNorm) * tubeWet
                    sampleR = sampleR * (1.0f - tubeWet) + (satR * tubeNorm) * tubeWet
                }

                // 3. Psychoacoustic Harmonic Bass
                if (state.isBassEnabled && state.bassGainDb > 0.1f) {
                    val lowL = bassLpf.processLeft(sampleL)
                    val lowR = bassLpf.processRight(sampleR)

                    val harm2L = (2.0f * lowL * lowL - 0.5f)
                    val harm2R = (2.0f * lowR * lowR - 0.5f)

                    val hpL = bassHpfHarmonics.processLeft(harm2L)
                    val hpR = bassHpfHarmonics.processRight(harm2R)

                    sampleL += hpL * bassGain
                    sampleR += hpR * bassGain
                }

                // 4. Upper Harmonic Exciter / Clarity
                if (state.isClarityEnabled && state.clarityGainDb > 0.1f) {
                    val highL = exciterHpf.processLeft(sampleL)
                    val highR = exciterHpf.processRight(sampleR)

                    val harmL = highL - 0.25f * highL * highL * highL
                    val harmR = highR - 0.25f * highR * highR * highR

                    val bandL = exciterBpf.processLeft(harmL)
                    val bandR = exciterBpf.processRight(harmR)

                    sampleL += bandL * exciterGain
                    sampleR += bandR * exciterGain
                }

                // 5. Stereo Imager
                if (state.isFieldSurroundEnabled) {
                    val mid = 0.5f * (sampleL + sampleR)
                    val side = 0.5f * (sampleL - sampleR) * widthScale

                    sampleL = mid + side
                    sampleR = mid - side
                }

                // 6. Bauer Crossfeed
                if (state.isFieldSurroundEnabled && state.haasDelayMs > 1.0f) {
                    val lowCrossL = crossfeedLpfL.processLeft(sampleL)
                    val lowCrossR = crossfeedLpfR.processRight(sampleR)

                    delayBufferL[delayIdx] = lowCrossL
                    delayBufferR[delayIdx] = lowCrossR

                    val delaySamples = 14
                    var readIdx = delayIdx - delaySamples
                    if (readIdx < 0) readIdx += delayBufferL.size

                    val delayedL = delayBufferL[readIdx]
                    val delayedR = delayBufferR[readIdx]

                    delayIdx = (delayIdx + 1) % delayBufferL.size

                    sampleL = sampleL * 0.85f + delayedR * 0.15f
                    sampleR = sampleR * 0.85f + delayedL * 0.15f
                }

                // 7. Convolution Reverb
                if (state.isConvolverEnabled && state.convolverWetPercent > 0.1f) {
                    convolverEngine.processToBuffer(sampleL, sampleR, state.convolverWetPercent, convolverBuffer)
                    sampleL = convolverBuffer[0]
                    sampleR = convolverBuffer[1]
                }

                // 8. Peak Limiter
                sampleL = sampleL.coerceIn(-0.98f, 0.98f)
                sampleR = sampleR.coerceIn(-0.98f, 0.98f)
            }

            if (pcmTapCount + 2 <= pcmTapBuffer.size) {
                pcmTapBuffer[pcmTapCount++] = sampleL
                if (channelCount >= 2) {
                    pcmTapBuffer[pcmTapCount++] = sampleR
                }
            }

            if (isFloat) {
                outputBuffer.putFloat(sampleL)
                if (channelCount >= 2) {
                    outputBuffer.putFloat(sampleR)
                }
                for (c in 2 until channelCount) {
                    outputBuffer.putFloat(inputBuffer.getFloat(srcOffset + c * 4))
                }
            } else {
                val outShortL = (sampleL * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                val outShortR = (sampleR * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()

                outputBuffer.putShort(outShortL)
                if (channelCount >= 2) {
                    outputBuffer.putShort(outShortR)
                }
                for (c in 2 until channelCount) {
                    outputBuffer.putShort(inputBuffer.getShort(srcOffset + c * 2))
                }
            }

            srcOffset += bytesPerFrame
        }

        if (pcmTapCount >= 256) {
            val tapChannels = if (channelCount >= 2) 2 else 1
            // Feed real-time Audio Analysis Engine with zero GC allocations
            com.anuj.obsidian.ui.visualizer.AudioAnalysisEngine.feedPcmSamples(pcmTapBuffer, pcmTapCount, tapChannels)
            pcmTapCount = 0
        }

        inputBuffer.position(endOffset)
        outputBuffer.flip()
    }
}
