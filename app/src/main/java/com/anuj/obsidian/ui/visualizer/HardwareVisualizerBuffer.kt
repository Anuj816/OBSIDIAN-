package com.anuj.obsidian.ui.visualizer

import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable lock-free snapshot consumed by Compose DrawScope on UI thread.
 * Contains pre-computed zero-allocation float buffers to eliminate UI thread blocking.
 */
class VisualizerSnapshot(
    val fftMagnitudes: FloatArray, // 64 frequency bands (0.0 .. 1.0)
    val waveform: FloatArray,      // 128 normalized time-domain samples (-1.0 .. 1.0)
    var bassEnergy: Float = 0f,
    var midEnergy: Float = 0f,
    var trebleEnergy: Float = 0f,
    var peakAmplitude: Float = 0f,
    var beatPulse: Float = 0f,
    var timestampMs: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisualizerSnapshot) return false
        return timestampMs == other.timestampMs
    }

    override fun hashCode(): Int = timestampMs.hashCode()
}

/**
 * Hardware-Accelerated Audio Visualization Buffer Engine.
 * Serves as the primary bridge between the Audio Analysis Layer and Visualizer Renderers.
 * 
 * Architecture:
 * 1. Delegates all real-time FFT, 5-band frequency normalization, attack/release smoothing, and peak tracking to AudioAnalysisEngine.
 * 2. Uses AtomicReference double/triple-buffering so Compose UI threads read immutable snapshots without locking or GC overhead.
 * 3. Observes playback state changes cleanly without interfering with playback controller or queue decisions.
 */
object HardwareVisualizerBuffer {
    private const val TAG = "HardwareVisBuffer"
    private const val BANDS_COUNT = 64
    private const val WAVEFORM_COUNT = 128

    // Pre-allocated snapshots for zero-GC bridging
    private val snapshots = Array(3) {
        VisualizerSnapshot(FloatArray(BANDS_COUNT), FloatArray(WAVEFORM_COUNT))
    }
    @Volatile private var writeIndex = 0
    private val _snapshot = AtomicReference(snapshots[0])

    val currentSnapshot: VisualizerSnapshot
        get() = _snapshot.get()

    fun getSnapshot(): VisualizerSnapshot = _snapshot.get()

    /**
     * Called directly on Dispatchers.Default by AudioAnalysisEngine when a frame is computed.
     * Guarantees that Compose UI thread never blocks on locks or performs array copies.
     */
    fun publishEngineSnapshot(engineState: VisualizationState) {
        val nextIdx = (writeIndex + 1) % 3
        val snap = snapshots[nextIdx]
        val binsLen = minOf(engineState.normalizedBins.size, BANDS_COUNT)
        if (binsLen > 0) {
            System.arraycopy(engineState.normalizedBins, 0, snap.fftMagnitudes, 0, binsLen)
        }
        val waveLen = minOf(engineState.waveform.size, WAVEFORM_COUNT)
        if (waveLen > 0) {
            System.arraycopy(engineState.waveform, 0, snap.waveform, 0, waveLen)
        }
        snap.bassEnergy = engineState.bass
        snap.midEnergy = engineState.mid
        snap.trebleEnergy = engineState.treble
        snap.peakAmplitude = engineState.peakAmplitude
        snap.beatPulse = engineState.beatPulse
        snap.timestampMs = engineState.timestampMs
        writeIndex = nextIdx
        _snapshot.set(snap)
    }

    fun attachAudioSession(sessionId: Int) {
        AudioAnalysisEngine.attachAudioSession(sessionId)
    }

    fun setPlaybackState(isPlaying: Boolean) {
        AudioAnalysisEngine.onPlaybackStateChanged(
            if (isPlaying) AudioPlaybackState.PLAYING else AudioPlaybackState.PAUSED
        )
    }

    fun onPlaybackStateChanged(state: AudioPlaybackState) {
        AudioAnalysisEngine.onPlaybackStateChanged(state)
    }

    fun detachAudioSession() {
        AudioAnalysisEngine.detachAudioSession()
    }

    fun releaseHardwareVisualizer() {
        AudioAnalysisEngine.releaseHardwareVisualizer()
    }
}
