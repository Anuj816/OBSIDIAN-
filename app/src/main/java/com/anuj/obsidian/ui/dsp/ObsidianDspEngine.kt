@file:Suppress("UnsafeOptInUsageError", "DEPRECATION")


package com.anuj.obsidian.ui.dsp

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import com.anuj.obsidian.data.model.OBSIDIAN_DDC_PROFILES
import com.anuj.obsidian.data.model.OBSIDIAN_IMPULSE_RESPONSES
import com.anuj.obsidian.data.model.ObsidianDspState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Obsidian4Android In-App DSP Audio Processing Engine (Non-Root).
 * 
 * Directly binds to Android AudioSessionId to process all audio in real-time with
 * hardware-accelerated DSP filters:
 * - Obsidian-DDC Headphone Acoustic Correction
 * - Obsidian Dynamic Bass & Sub-Woofer Synthesizer
 * - Obsidian Clarity (X-HiFi / Ozone+ transient enhancer)
 * - Obsidian Tube Simulator (Vacuum tube harmonic saturation)
 * - Convolver (Impulse Response Room & Vintage Console emulation)
 * - Field Surround & Haas Differential Stereo Widener
 * - Dynamic System Peak Limiter & Multi-band protection
 * - 10-Band Audiophile Equalizer
 */
object ObsidianDspEngine {
    private const val TAG = "ObsidianDspEngine"

    val pcmDspProcessor = ObsidianPcmDspAudioProcessor()

    private val _dspState = MutableStateFlow(ObsidianDspState())
    val dspState: StateFlow<ObsidianDspState> = _dspState.asStateFlow()

    private val _fftData = MutableStateFlow(ByteArray(0))
    val fftData: StateFlow<ByteArray> = _fftData.asStateFlow()
    
    private var spectrumVisualizer: Visualizer? = null

    private var currentSessionId: Int = 0
    private var isEqualizerSupported: Boolean = true
    private var isBassBoostSupported: Boolean = true
    private var isVirtualizerSupported: Boolean = true
    private var isReverbSupported: Boolean = true
    private var isDynamicsProcessingSupported: Boolean = true

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var environmentalReverb: EnvironmentalReverb? = null
    private var dynamicsProcessingHelper: ObsidianDynamicsProcessingHelper? = null

    val dspLock = Any()
    private val dspScope = CoroutineScope(Dispatchers.Default)

    fun attachAudioSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        currentSessionId = sessionId
        dspScope.launch {
            synchronized(dspLock) {
                initAudioEffects(sessionId)
                applyCurrentDspState()
            }
        }
    }

    private fun initAudioEffects(sessionId: Int) {
        releaseAudioEffects()
        
        try {
            val state = _dspState.value

            // 1. DynamicsProcessing (Android 10+ API 29+) for true 10-Band EQ, Limiter, Clarity, Tube & DDC
            var dpSuccess = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val helper = ObsidianDynamicsProcessingHelper()
                    if (helper.init(sessionId, state.isMasterEnabled)) {
                        dynamicsProcessingHelper = helper
                        isDynamicsProcessingSupported = true
                        dpSuccess = true
                    }
                } catch (e: Throwable) {
                    isDynamicsProcessingSupported = false
                    Log.d(TAG, "DynamicsProcessing initialization notice: ${e.message}")
                    dynamicsProcessingHelper = null
                }
            }

            // 2. Hardware Equalizer (Only needed as fallback when DynamicsProcessing is absent)
            if (!dpSuccess) {
                try {
                    equalizer = Equalizer(0, sessionId).apply {
                        enabled = state.isMasterEnabled
                    }
                    isEqualizerSupported = true
                } catch (e: Throwable) {
                    isEqualizerSupported = false
                    Log.d(TAG, "Hardware Equalizer not supported on this session: ${e.message}")
                    equalizer = null
                }
            } else {
                equalizer = null
            }

            // 3. Hardware Bass Boost - on-demand allocation
            if (state.isMasterEnabled && state.isBassEnabled) {
                try {
                    bassBoost = BassBoost(0, sessionId).apply { enabled = true }
                    isBassBoostSupported = true
                } catch (e: Throwable) {
                    isBassBoostSupported = false
                    Log.d(TAG, "Hardware BassBoost not supported: ${e.message}")
                    bassBoost = null
                }
            }

            // 4. Hardware Spatial Virtualizer - on-demand allocation
            if (state.isMasterEnabled && state.isFieldSurroundEnabled) {
                try {
                    virtualizer = Virtualizer(0, sessionId).apply { enabled = true }
                    isVirtualizerSupported = true
                } catch (e: Throwable) {
                    isVirtualizerSupported = false
                    Log.d(TAG, "Hardware Virtualizer not supported: ${e.message}")
                    virtualizer = null
                }
            }

            // 5. Environmental Reverb - only if convolver simulation is explicitly enabled
            if (state.isMasterEnabled && state.isConvolverEnabled) {
                try {
                    environmentalReverb = EnvironmentalReverb(0, sessionId).apply { enabled = true }
                    isReverbSupported = true
                } catch (e: Throwable) {
                    isReverbSupported = false
                    Log.d(TAG, "Reverb not supported: ${e.message}")
                    environmentalReverb = null
                }
            }

            Log.d(TAG, "Obsidian4Android DSP Engine successfully bound to session $sessionId")
        } catch (e: Throwable) {
            Log.d(TAG, "Note initializing Obsidian audio effects: ${e.message}")
        }
    }

    fun updateDspState(newState: ObsidianDspState) {
        _dspState.value = newState
        dspScope.launch {
            synchronized(dspLock) {
                applyCurrentDspState()
            }
        }
    }

    fun updateState(transform: (ObsidianDspState) -> ObsidianDspState) {
        val updated = transform(_dspState.value)
        _dspState.value = updated
        dspScope.launch {
            synchronized(dspLock) {
                applyCurrentDspState()
            }
        }
    }

    private fun applyCurrentDspState() {
        val state = _dspState.value

        try {
            pcmDspProcessor.setEqBandCount(state.eqBandMode)
            pcmDspProcessor.updateState(state)

            if (!state.isMasterEnabled) {
                equalizer?.enabled = false
                bassBoost?.apply { enabled = false; release() }
                bassBoost = null
                virtualizer?.apply { enabled = false; release() }
                virtualizer = null
                environmentalReverb?.apply { enabled = false; release() }
                environmentalReverb = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dynamicsProcessingHelper?.setEnabled(false)
                }
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dynamicsProcessingHelper?.setEnabled(true)
            }

            // 1. Combine 10-band Equalizer + DDC Correction + Clarity Curve
            val ddcProfile = OBSIDIAN_DDC_PROFILES.find { it.id == state.selectedDdcId }
            val ddcGains = if (state.isDdcEnabled && ddcProfile != null) ddcProfile.eqCorrection else List(10) { 0f }

            val totalGains = FloatArray(10)
            for (i in 0 until 10) {
                val userGain = if (state.isEqEnabled && i < state.eqGains.size) state.eqGains[i] else 0f
                val ddcGain = if (i < ddcGains.size) ddcGains[i] else 0f
                
                // Add Clarity boost to upper bands (bands 7, 8, 9 -> 4kHz, 8kHz, 16kHz)
                val clarityBoost = if (state.isClarityEnabled && i >= 7) {
                    when (state.clarityMode) {
                        "X-HiFi" -> (state.clarityGainDb * 0.8f)
                        "Ozone+" -> (state.clarityGainDb * 0.6f)
                        else -> (state.clarityGainDb * 0.4f)
                    }
                } else 0f

                // Add Tube warmth to 125Hz-500Hz and gentle treble roll-off
                val tubeHarmonics = if (state.isTubeEnabled) {
                    val drive = state.tubeDrivePercent / 100f
                    if (i in 1..3) drive * 2.5f else if (i >= 8) -drive * 1.5f else 0f
                } else 0f

                totalGains[i] = (userGain + ddcGain + clarityBoost + tubeHarmonics + state.preampGainDb).coerceIn(-15f, 15f)
            }

            // Apply true 10-Band EQ using DynamicsProcessing if available, fallback to hardware Equalizer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && dynamicsProcessingHelper?.isInitialized == true) {
                val freqs = floatArrayOf(31.5f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
                dynamicsProcessingHelper?.apply10BandEq(freqs, totalGains)
            } else {
                if (equalizer == null && currentSessionId > 0) {
                    try {
                        equalizer = Equalizer(0, currentSessionId).apply { enabled = true }
                    } catch (t: Throwable) {
                        com.anuj.obsidian.ObsidianCrashLogger.logException(t, "ObsidianDspEngine.equalizer.init")
                    }
                }
                equalizer?.let { eq ->
                    eq.enabled = true
                    val numBands = eq.numberOfBands.toInt()
                    val minRange = eq.bandLevelRange[0]
                    val maxRange = eq.bandLevelRange[1]

                    for (band in 0 until numBands) {
                        val gainIndex = ((band.toFloat() / numBands.coerceAtLeast(1)) * 10).toInt().coerceIn(0, 9)
                        val db = totalGains[gainIndex]
                        val millibels = (db * 100).toInt().toShort().coerceIn(minRange, maxRange)
                        eq.setBandLevel(band.toShort(), millibels)
                    }
                }
            }

            // 2. Obsidian Dynamic Bass (on-demand allocation/release)
            if (state.isBassEnabled) {
                if (bassBoost == null && currentSessionId > 0 && isBassBoostSupported) {
                    try {
                        bassBoost = BassBoost(0, currentSessionId).apply { enabled = true }
                    } catch (e: Throwable) {
                        Log.w(TAG, "BassBoost init failed for session $currentSessionId: ${e.message}")
                        isBassBoostSupported = false
                    }
                }
                bassBoost?.let { bb ->
                    bb.enabled = true
                    if (bb.strengthSupported) {
                        val strength = ((state.bassGainDb / 18.0f) * 1000f).toInt().toShort().coerceIn(0, 1000)
                        bb.setStrength(strength)
                    }
                }
            } else {
                try {
                    bassBoost?.enabled = false
                    bassBoost?.release()
                } catch (t: Throwable) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(t, "ObsidianDspEngine.bassBoost.release")
                }
                bassBoost = null
            }

            // 3. Field Surround & Haas Width (on-demand allocation/release)
            if (state.isFieldSurroundEnabled) {
                if (virtualizer == null && currentSessionId > 0 && isVirtualizerSupported) {
                    try {
                        virtualizer = Virtualizer(0, currentSessionId).apply { enabled = true }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Virtualizer init failed for session $currentSessionId: ${e.message}")
                        isVirtualizerSupported = false
                    }
                }
                virtualizer?.let { v ->
                    v.enabled = true
                    if (v.strengthSupported) {
                        val strength = ((state.surroundWidthPercent / 200f) * 1000f).toInt().toShort().coerceIn(0, 1000)
                        v.setStrength(strength)
                    }
                }
            } else {
                try {
                    virtualizer?.enabled = false
                    virtualizer?.release()
                } catch (t: Throwable) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(t, "ObsidianDspEngine.virtualizer.release")
                }
                virtualizer = null
            }

            // 4. Convolver Reverb Simulation (on-demand allocation/release)
            if (state.isConvolverEnabled) {
                if (environmentalReverb == null && currentSessionId > 0 && isReverbSupported) {
                    try {
                        environmentalReverb = EnvironmentalReverb(0, currentSessionId).apply { enabled = true }
                    } catch (e: Throwable) {
                        Log.w(TAG, "EnvironmentalReverb init failed for session $currentSessionId: ${e.message}")
                        isReverbSupported = false
                    }
                }
                environmentalReverb?.let { rev ->
                    rev.enabled = true
                    val ir = OBSIDIAN_IMPULSE_RESPONSES.find { it.id == state.selectedImpulseId }
                    val wet = (state.convolverWetPercent / 100f).coerceIn(0f, 1f)
                    val decayTimeMs = ir?.roomDecayMs ?: 800
                    rev.decayTime = decayTimeMs
                    rev.roomLevel = ((-2000 + (wet * 2000)).toInt()).toShort().coerceIn(-9000, 0)
                    rev.reverbLevel = ((-1500 + (wet * 1800)).toInt()).toShort().coerceIn(-9000, 1000)
                    rev.reflectionsDelay = (decayTimeMs / 20).coerceIn(5, 100)
                }
            } else {
                try {
                    environmentalReverb?.enabled = false
                    environmentalReverb?.release()
                } catch (t: Throwable) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(t, "ObsidianDspEngine.environmentalReverb.release")
                }
                environmentalReverb = null
            }

            // 5. Dynamics Processing (Limiter & Compressor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (state.isDynamicSystemEnabled) {
                    dynamicsProcessingHelper?.applyLimiter(state.limiterThresholdDb)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Exception applying Obsidian DSP settings: ${e.message}")
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianDspEngine.applyCurrentDspState")
        }
    }

    fun detachAudioSession() {
        releaseAudioEffects()
    }

    fun releaseAudioEffects() {
        synchronized(dspLock) {
            try { equalizer?.release() } catch (e: Throwable) { Log.d(TAG, "Equalizer release note: ${e.message}") }
            try { bassBoost?.release() } catch (e: Throwable) { Log.d(TAG, "BassBoost release note: ${e.message}") }
            try { virtualizer?.release() } catch (e: Throwable) { Log.d(TAG, "Virtualizer release note: ${e.message}") }
            try { environmentalReverb?.release() } catch (e: Throwable) { Log.d(TAG, "Reverb release note: ${e.message}") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try { dynamicsProcessingHelper?.release() } catch (e: Throwable) { Log.d(TAG, "DynamicsProcessing release note: ${e.message}") }
            }
            try { spectrumVisualizer?.release() } catch (e: Throwable) { Log.d(TAG, "Visualizer release note: ${e.message}") }
    
            spectrumVisualizer = null
            equalizer = null
            bassBoost = null
            virtualizer = null
            environmentalReverb = null
            dynamicsProcessingHelper = null
            currentSessionId = 0
        }
    }
}
