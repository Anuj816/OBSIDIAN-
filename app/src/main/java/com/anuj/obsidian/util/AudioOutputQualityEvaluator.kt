package com.anuj.obsidian.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build

data class AudioOutputState(
    val isBitPerfect: Boolean,
    val isUsbDac: Boolean,
    val isBluetooth: Boolean,
    val hardwareNativeSampleRate: Int,
    val sourceSampleRate: Int,
    val outputStatusLabel: String,
    val statusDetail: String
)

object AudioOutputQualityEvaluator {
    fun evaluate(context: Context, sourceSampleRateText: String): AudioOutputState {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nativeSampleRateStr = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val nativeSampleRate = nativeSampleRateStr?.toIntOrNull()
            ?: AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 }
            ?: 48000

        var hasUsbDac = false
        var hasBluetooth = false

        if (audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (dev in devices) {
                when (dev.type) {
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY -> hasUsbDac = true
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> hasBluetooth = true
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET || dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                        ) {
                            hasBluetooth = true
                        }
                    }
                }
            }
        }

        // Parse source rate
        val sourceRate = parseSampleRate(sourceSampleRateText)

        val isBitPerfect = if (hasUsbDac) {
            true
        } else if (sourceRate > 0 && sourceRate == nativeSampleRate) {
            true
        } else {
            false
        }

        val label = when {
            hasUsbDac -> "🎯 USB BIT-PERFECT"
            isBitPerfect -> "🎯 BIT-PERFECT"
            sourceRate > 0 -> "SRC RESAMPLED"
            else -> "BIT-PERFECT"
        }

        val detail = when {
            hasUsbDac -> "Direct USB DAC hardware pipeline active (${sourceRate / 1000.0} kHz)"
            isBitPerfect -> "Native ${nativeSampleRate / 1000.0} kHz direct hardware match"
            sourceRate > 0 -> "${sourceRate / 1000.0} kHz → ${nativeSampleRate / 1000.0} kHz"
            else -> "Direct 32-bit float audio engine"
        }

        return AudioOutputState(
            isBitPerfect = isBitPerfect,
            isUsbDac = hasUsbDac,
            isBluetooth = hasBluetooth,
            hardwareNativeSampleRate = nativeSampleRate,
            sourceSampleRate = sourceRate,
            outputStatusLabel = label,
            statusDetail = detail
        )
    }

    private fun parseSampleRate(text: String): Int {
        if (text.isBlank()) return 44100
        val clean = text.lowercase().replace("khz", "").replace("hz", "").trim()
        val num = clean.toDoubleOrNull() ?: return 44100
        return if (num < 1000) {
            (num * 1000).toInt()
        } else {
            num.toInt()
        }
    }
}
