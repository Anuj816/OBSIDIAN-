package com.anuj.obsidian.ui.visualizer

/**
 * Standard playback states observed by the audio analysis and visualization pipeline.
 */
enum class AudioPlaybackState {
    PLAYING,
    PAUSED,
    STOPPED,
    BUFFERING,
    TRACK_CHANGED,
    ERROR
}

/**
 * Normalized 5-band frequency energy representation (0.0 .. 1.0).
 * Derived from FFT frequency analysis:
 * - Sub-Bass / Bass: ~20 Hz - 250 Hz
 * - Low-Mid: ~250 Hz - 500 Hz
 * - Mid: ~500 Hz - 2000 Hz
 * - High-Mid: ~2000 Hz - 6000 Hz
 * - Treble: ~6000 Hz - 20000 Hz
 */
data class FrequencyBands(
    val bass: Float = 0f,
    val lowMid: Float = 0f,
    val mid: Float = 0f,
    val highMid: Float = 0f,
    val treble: Float = 0f,
    val subBass: Float = 0f,
    val peakAmplitude: Float = 0f,
    val beatEnergy: Float = 0f,
    val isBeat: Boolean = false
)

/**
 * Immutable snapshot of real-time visualization state consumed by UI renderers.
 * Pre-allocated double/triple buffered primitive arrays to guarantee zero GC stutter on UI thread.
 */
class VisualizationState(
    val normalizedBins: FloatArray, // 64 frequency bins (0.0 .. 1.0)
    val peakBins: FloatArray,       // 64 floating peak markers with gravity decay (0.0 .. 1.0)
    val waveform: FloatArray,       // 128 normalized time-domain samples (-1.0 .. 1.0)
    var frequencyBands: FrequencyBands = FrequencyBands(),
    var beatPulse: Float = 0f,
    var overallEnergy: Float = 0f,
    var playbackState: AudioPlaybackState = AudioPlaybackState.STOPPED,
    var timestampMs: Long = 0L
) {
    val bass: Float get() = frequencyBands.bass
    val lowMid: Float get() = frequencyBands.lowMid
    val mid: Float get() = frequencyBands.mid
    val highMid: Float get() = frequencyBands.highMid
    val treble: Float get() = frequencyBands.treble
    val peakAmplitude: Float get() = frequencyBands.peakAmplitude
    val isBeat: Boolean get() = frequencyBands.isBeat

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisualizationState) return false
        return timestampMs == other.timestampMs && playbackState == other.playbackState
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + playbackState.hashCode()
        return result
    }
}
