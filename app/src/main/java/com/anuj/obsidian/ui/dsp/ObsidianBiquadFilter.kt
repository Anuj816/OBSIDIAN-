package com.anuj.obsidian.ui.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance Biquad IIR Audio Filter implementation based on 
 * Robert Bristow-Johnson's Audio EQ Cookbook formulas.
 * 
 * Supports:
 * - Peaking EQ
 * - Low-Pass Filter (LPF)
 * - High-Pass Filter (HPF)
 * - Band-Pass Filter (BPF)
 * - Low-Shelf
 * - High-Shelf
 */
class ObsidianBiquadFilter {
    enum class Type {
        PEAKING,
        LOW_PASS,
        HIGH_PASS,
        BAND_PASS,
        LOW_SHELF,
        HIGH_SHELF
    }

    @PublishedApi internal var b0 = 1.0f
    @PublishedApi internal var b1 = 0.0f
    @PublishedApi internal var b2 = 0.0f
    @PublishedApi internal var a1 = 0.0f
    @PublishedApi internal var a2 = 0.0f

    // Direct Form II Transposed state variables per channel (0 = Left, 1 = Right)
    @PublishedApi internal var z1L = 0.0f
    @PublishedApi internal var z2L = 0.0f
    @PublishedApi internal var z1R = 0.0f
    @PublishedApi internal var z2R = 0.0f

    @Volatile
    var isEnabled = true

    fun reset() {
        z1L = 0.0f
        z2L = 0.0f
        z1R = 0.0f
        z2R = 0.0f
    }

    fun configure(
        type: Type,
        centerFreqHz: Float,
        sampleRate: Float,
        gainDb: Float = 0.0f,
        q: Float = 1.0f
    ) {
        if (sampleRate <= 0f || centerFreqHz <= 0f) {
            b0 = 1.0f; b1 = 0.0f; b2 = 0.0f; a1 = 0.0f; a2 = 0.0f
            return
        }

        // Clamp center frequency below Nyquist
        val freq = centerFreqHz.coerceIn(10.0f, (sampleRate * 0.49f))
        val omega = (2.0 * PI * freq / sampleRate).toFloat()
        val cosOmega = cos(omega.toDouble()).toFloat()
        val sinOmega = sin(omega.toDouble()).toFloat()
        val alpha = sinOmega / (2.0f * q.coerceAtLeast(0.1f))

        val A = 10.0f.pow(gainDb / 40.0f)

        var rawB0 = 1.0f
        var rawB1 = 0.0f
        var rawB2 = 0.0f
        var rawA0 = 1.0f
        var rawA1 = 0.0f
        var rawA2 = 0.0f

        when (type) {
            Type.PEAKING -> {
                rawB0 = 1.0f + alpha * A
                rawB1 = -2.0f * cosOmega
                rawB2 = 1.0f - alpha * A
                rawA0 = 1.0f + alpha / A
                rawA1 = -2.0f * cosOmega
                rawA2 = 1.0f - alpha / A
            }
            Type.LOW_PASS -> {
                rawB0 = (1.0f - cosOmega) / 2.0f
                rawB1 = 1.0f - cosOmega
                rawB2 = (1.0f - cosOmega) / 2.0f
                rawA0 = 1.0f + alpha
                rawA1 = -2.0f * cosOmega
                rawA2 = 1.0f - alpha
            }
            Type.HIGH_PASS -> {
                rawB0 = (1.0f + cosOmega) / 2.0f
                rawB1 = -(1.0f + cosOmega)
                rawB2 = (1.0f + cosOmega) / 2.0f
                rawA0 = 1.0f + alpha
                rawA1 = -2.0f * cosOmega
                rawA2 = 1.0f - alpha
            }
            Type.BAND_PASS -> {
                rawB0 = alpha
                rawB1 = 0.0f
                rawB2 = -alpha
                rawA0 = 1.0f + alpha
                rawA1 = -2.0f * cosOmega
                rawA2 = 1.0f - alpha
            }
            Type.LOW_SHELF -> {
                val sqrtA = sqrt(A.toDouble()).toFloat()
                val twoSqrtAAlpha = 2.0f * sqrtA * alpha
                rawB0 = A * ((A + 1.0f) - (A - 1.0f) * cosOmega + twoSqrtAAlpha)
                rawB1 = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cosOmega)
                rawB2 = A * ((A + 1.0f) - (A - 1.0f) * cosOmega - twoSqrtAAlpha)
                rawA0 = (A + 1.0f) + (A - 1.0f) * cosOmega + twoSqrtAAlpha
                rawA1 = -2.0f * ((A - 1.0f) + (A + 1.0f) * cosOmega)
                rawA2 = (A + 1.0f) + (A - 1.0f) * cosOmega - twoSqrtAAlpha
            }
            Type.HIGH_SHELF -> {
                val sqrtA = sqrt(A.toDouble()).toFloat()
                val twoSqrtAAlpha = 2.0f * sqrtA * alpha
                rawB0 = A * ((A + 1.0f) + (A - 1.0f) * cosOmega + twoSqrtAAlpha)
                rawB1 = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cosOmega)
                rawB2 = A * ((A + 1.0f) + (A - 1.0f) * cosOmega - twoSqrtAAlpha)
                rawA0 = (A + 1.0f) - (A - 1.0f) * cosOmega + twoSqrtAAlpha
                rawA1 = 2.0f * ((A - 1.0f) - (A + 1.0f) * cosOmega)
                rawA2 = (A + 1.0f) - (A - 1.0f) * cosOmega - twoSqrtAAlpha
            }
        }

        // Normalize coefficients by a0
        val invA0 = 1.0f / rawA0
        b0 = rawB0 * invA0
        b1 = rawB1 * invA0
        b2 = rawB2 * invA0
        a1 = rawA1 * invA0
        a2 = rawA2 * invA0
    }

    /**
     * Process a single sample for Left channel using Direct Form II Transposed.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun processLeft(x: Float): Float {
        if (!isEnabled) return x
        val y = b0 * x + z1L
        z1L = b1 * x - a1 * y + z2L
        z2L = b2 * x - a2 * y
        return y
    }

    /**
     * Process a single sample for Right channel using Direct Form II Transposed.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun processRight(x: Float): Float {
        if (!isEnabled) return x
        val y = b0 * x + z1R
        z1R = b1 * x - a1 * y + z2R
        z2R = b2 * x - a2 * y
        return y
    }
}
