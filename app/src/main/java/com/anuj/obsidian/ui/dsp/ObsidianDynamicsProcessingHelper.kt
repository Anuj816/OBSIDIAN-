package com.anuj.obsidian.ui.dsp

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Isolated DynamicsProcessing Helper for Android 10.0+ (Q / API 29+).
 * 
 * By isolating all direct references to `android.media.audiofx.DynamicsProcessing`
 * in this separate class, we completely prevent Dalvik/ART class verification crashes
 * (NoClassDefFoundError / VerifyError) on Android 8.0 & 8.1 (API 26-27) devices.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class ObsidianDynamicsProcessingHelper {
    companion object {
        private const val TAG = "ObsidianDPHelper"
    }

    private var dynamicsProcessing: DynamicsProcessing? = null
    var isInitialized: Boolean = false
        private set

    fun init(sessionId: Int, isMasterEnabled: Boolean): Boolean {
        release()
        return try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,    // 2 channels (Stereo)
                true, // PreEQ
                10,   // 10 bands
                true, // Multi-band compressor
                10,   // 10 bands
                true, // PostEQ
                10,   // 10 bands
                true  // Limiter
            ).build()

            dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                enabled = isMasterEnabled
            }
            isInitialized = true
            Log.d(TAG, "DynamicsProcessing initialized successfully for session $sessionId")
            true
        } catch (e: Throwable) {
            isInitialized = false
            dynamicsProcessing = null
            Log.d(TAG, "DynamicsProcessing initialization note: ${e.message}")
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            dynamicsProcessing?.enabled = enabled
        } catch (e: Throwable) {
            Log.d(TAG, "Failed to set DynamicsProcessing enabled state: ${e.message}")
        }
    }

    fun apply10BandEq(freqs: FloatArray, gains: FloatArray) {
        val dp = dynamicsProcessing ?: return
        try {
            val preEq = DynamicsProcessing.Eq(true, true, 10)
            for (i in 0 until 10) {
                val freq = if (i < freqs.size) freqs[i] else 1000f
                val gain = if (i < gains.size) gains[i] else 0f
                val bandEq = DynamicsProcessing.EqBand(true, freq, gain)
                preEq.setBand(i, bandEq)
            }
            dp.setPreEqAllChannelsTo(preEq)
        } catch (e: Throwable) {
            Log.d(TAG, "Failed to apply 10-band EQ in DynamicsProcessing: ${e.message}")
        }
    }

    fun applyLimiter(thresholdDb: Float) {
        val dp = dynamicsProcessing ?: return
        try {
            val limiter = DynamicsProcessing.Limiter(
                true, // inUse
                true, // enabled
                0, // linkGroup
                1.5f, // attackTime ms
                50.0f, // releaseTime ms
                10.0f, // ratio
                thresholdDb, // threshold dB
                0.0f // postGain dB
            )
            dp.setLimiterByChannelIndex(0, limiter)
            dp.setLimiterByChannelIndex(1, limiter)
        } catch (e: Throwable) {
            Log.d(TAG, "Failed to apply limiter in DynamicsProcessing: ${e.message}")
        }
    }

    fun release() {
        try {
            dynamicsProcessing?.release()
        } catch (e: Throwable) {
            Log.d(TAG, "Failed to release DynamicsProcessing: ${e.message}")
        }
        dynamicsProcessing = null
        isInitialized = false
    }
}
