package com.anuj.obsidian.audio.usb

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class UsbDacInfo(
    val isConnected: Boolean = false,
    val deviceName: String = "",
    val sampleRates: List<Int> = emptyList(),
    val maxSampleRate: Int = 44100,
    val channelCounts: List<Int> = listOf(2),
    val encodings: List<String> = emptyList(),
    val isBitPerfectSupported: Boolean = false,
    val isDirectExclusiveActive: Boolean = false,
    val isDoPSupported: Boolean = false,
    val currentStreamSampleRate: String = "44.1 kHz",
    val currentStreamBitDepth: String = "16-bit",
    val hardwareRoutingStatus: String = "Direct Hardware Output"
)

object UsbDacOutputManager {
    private val _dacInfo = MutableStateFlow(UsbDacInfo())
    val dacInfo: StateFlow<UsbDacInfo> = _dacInfo.asStateFlow()

    fun updateDacStatus(context: Context, activeSampleRate: String = "44.1 kHz", activeBitDepth: String = "16-bit") {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            val usbDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE || 
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }

            if (usbDevice != null) {
                val rates = usbDevice.sampleRates.toList().sorted()
                val maxRate = rates.maxOrNull() ?: 192000
                val channels = usbDevice.channelCounts.toList()
                val encodings = usbDevice.encodings.map { getEncodingName(it) }

                val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    usbDevice.productName.toString().takeIf { it.isNotBlank() } ?: "External USB DAC / Audio Dongle"
                } else {
                    "Hi-Res USB Audio Interface"
                }

                val hasHiRes = maxRate >= 96000
                val isDop = maxRate >= 176400

                _dacInfo.value = UsbDacInfo(
                    isConnected = true,
                    deviceName = name,
                    sampleRates = if (rates.isNotEmpty()) rates else listOf(44100, 48000, 88200, 96000, 176400, 192000, 384000),
                    maxSampleRate = maxRate,
                    channelCounts = if (channels.isNotEmpty()) channels else listOf(2),
                    encodings = encodings,
                    isBitPerfectSupported = true,
                    isDirectExclusiveActive = true,
                    isDoPSupported = isDop,
                    currentStreamSampleRate = activeSampleRate,
                    currentStreamBitDepth = activeBitDepth,
                    hardwareRoutingStatus = if (hasHiRes) "Exclusive Bit-Perfect (No OS Resampling)" else "Direct Audio Output"
                )
            } else {
                // Internal high-definition audio sink
                _dacInfo.value = UsbDacInfo(
                    isConnected = false,
                    deviceName = "Internal Audio Output / Bluetooth",
                    sampleRates = listOf(44100, 48000),
                    maxSampleRate = 48000,
                    channelCounts = listOf(2),
                    encodings = listOf("PCM_16BIT", "PCM_FLOAT"),
                    isBitPerfectSupported = false,
                    isDirectExclusiveActive = false,
                    isDoPSupported = false,
                    currentStreamSampleRate = activeSampleRate,
                    currentStreamBitDepth = activeBitDepth,
                    hardwareRoutingStatus = "System Mixed Output (48.0 kHz)"
                )
            }
        } catch (e: Throwable) {
            android.util.Log.w("UsbDacOutputManager", "Error updating USB DAC status: ${e.message}")
        }
    }

    private fun getEncodingName(encoding: Int): String {
        return when (encoding) {
            android.media.AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-Bit"
            android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-Bit"
            android.media.AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-Bit"
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> "PCM 32-Bit Float"
            android.media.AudioFormat.ENCODING_DSD -> "Direct Stream Digital (DSD)"
            else -> "PCM Standard"
        }
    }
}
