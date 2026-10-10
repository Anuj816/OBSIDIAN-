package com.anuj.obsidian.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.anuj.obsidian.ObsidianCrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BluetoothAudioInfo(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val codecName: String? = null,
    val sampleRate: String? = null,
    val bitsPerSample: String? = null
) {
    /**
     * Formatted string for UI chips (e.g., "LDAC", "aptX HD", "AAC", "SBC")
     */
    val displayCodec: String?
        get() = if (isConnected && !codecName.isNullOrBlank()) codecName else null
}

/**
 * Manages Bluetooth A2DP audio device connectivity and queries active codec information
 * (aptX, aptX HD, LDAC, AAC, SBC, LC3) using BluetoothA2dp APIs with fallback heuristics.
 */
object BluetoothAudioCodecManager {
    private const val TAG = "BluetoothCodecMgr"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _bluetoothAudioInfo = MutableStateFlow(BluetoothAudioInfo())
    val bluetoothAudioInfo: StateFlow<BluetoothAudioInfo> = _bluetoothAudioInfo.asStateFlow()

    private var appContext: Context? = null
    private var bluetoothA2dp: BluetoothProfile? = null
    private var isInitialized = false

    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = proxy
                Log.d(TAG, "Bluetooth A2DP Profile Connected")
                refreshStatus()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = null
                Log.d(TAG, "Bluetooth A2DP Profile Disconnected")
                _bluetoothAudioInfo.value = BluetoothAudioInfo(isConnected = false)
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            Log.d(TAG, "Received broadcast action: $action")
            when (action) {
                "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
                "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED",
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_STATE_CHANGED,
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    refreshStatus()
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshStatus()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshStatus()
        }
    }

    fun initialize(context: Context) {
        if (isInitialized) {
            refreshStatus()
            return
        }
        val appCtx = context.applicationContext
        appContext = appCtx
        isInitialized = true

        try {
            val bluetoothManager = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            adapter?.getProfileProxy(appCtx, profileServiceListener, BluetoothProfile.A2DP)

            // Register BroadcastReceiver
            val filter = IntentFilter().apply {
                addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
                addAction("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED")
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appCtx.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appCtx.registerReceiver(bluetoothReceiver, filter)
            }

            // Register AudioManager device callback
            val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))

            refreshStatus()
        } catch (t: Throwable) {
            ObsidianCrashLogger.logException(t, "BluetoothAudioCodecManager.initialize")
        }
    }

    fun refreshStatus() {
        scope.launch(Dispatchers.IO) {
            val ctx = appContext ?: return@launch
            try {
                val hasPermission = checkBluetoothPermission(ctx)
                val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

                // 1. Check if Bluetooth Audio is actively connected via AudioManager
                var isBtAudioDeviceActive = false
                var connectedBtDeviceName: String? = null

                if (audioManager != null) {
                    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    for (dev in devices) {
                        if (dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET || dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
                        ) {
                            isBtAudioDeviceActive = true
                            if (dev.productName.isNotBlank()) {
                                connectedBtDeviceName = dev.productName.toString()
                            }
                            break
                        }
                    }
                }

                if (!isBtAudioDeviceActive) {
                    _bluetoothAudioInfo.value = BluetoothAudioInfo(isConnected = false)
                    return@launch
                }

                // 2. Query A2DP Codec Status if permission is granted and proxy is available
                var detectedCodecName: String? = null
                var detectedSampleRate: String? = null
                var detectedBitsPerSample: String? = null

                val a2dp = bluetoothA2dp
                if (hasPermission && a2dp != null) {
                    try {
                        val getConnectedDevicesMethod = a2dp.javaClass.getMethod("getConnectedDevices")
                        @Suppress("UNCHECKED_CAST")
                        val connectedDevices = getConnectedDevicesMethod.invoke(a2dp) as? List<BluetoothDevice>

                        val activeDevice = connectedDevices?.firstOrNull()
                        if (activeDevice != null) {
                            if (connectedBtDeviceName == null && hasPermission) {
                                try {
                                    connectedBtDeviceName = activeDevice.name
                                } catch (e: SecurityException) {
                                    ObsidianCrashLogger.logWarning(TAG, "BLUETOOTH_CONNECT permission denied for device name: ${e.message}")
                                }
                            }

                            // Try getCodecStatus(BluetoothDevice)
                            try {
                                val getCodecStatusMethod = a2dp.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
                                val codecStatus = getCodecStatusMethod.invoke(a2dp, activeDevice)
                                if (codecStatus != null) {
                                    val getCodecConfigMethod = codecStatus.javaClass.getMethod("getCodecConfig")
                                    val codecConfig = getCodecConfigMethod.invoke(codecStatus)
                                    if (codecConfig != null) {
                                        val parseResult = parseCodecConfig(codecConfig)
                                        detectedCodecName = parseResult.first
                                        detectedSampleRate = parseResult.second
                                        detectedBitsPerSample = parseResult.third
                                    }
                                }
                            } catch (e: Throwable) {
                                Log.d(TAG, "getCodecStatus method invocation failed: ${e.message}")
                            }
                        }
                    } catch (e: Throwable) {
                        Log.d(TAG, "Error querying A2DP devices: ${e.message}")
                    }
                }

                // Fallback: If Bluetooth device is connected but codec cannot be read directly via hidden API,
                // detect from device name patterns or default to standard high quality A2DP
                if (detectedCodecName == null && isBtAudioDeviceActive) {
                    detectedCodecName = inferCodecFromDeviceName(connectedBtDeviceName)
                }

                _bluetoothAudioInfo.value = BluetoothAudioInfo(
                    isConnected = true,
                    deviceName = connectedBtDeviceName,
                    codecName = detectedCodecName ?: "AAC",
                    sampleRate = detectedSampleRate,
                    bitsPerSample = detectedBitsPerSample
                )
            } catch (t: Throwable) {
                ObsidianCrashLogger.logException(t, "BluetoothAudioCodecManager.refreshStatus")
            }
        }
    }

    private fun parseCodecConfig(codecConfig: Any): Triple<String?, String?, String?> {
        try {
            var codecType = -1
            var codecName: String? = null
            var sampleRateInt = 0
            var bitsPerSampleInt = 0

            try {
                val getCodecTypeMethod = codecConfig.javaClass.getMethod("getCodecType")
                codecType = (getCodecTypeMethod.invoke(codecConfig) as? Int) ?: -1
            } catch (e: Exception) {
                ObsidianCrashLogger.logWarning(TAG, "Failed reading getCodecType: ${e.message}")
            }

            try {
                val getCodecNameMethod = codecConfig.javaClass.getMethod("getCodecName")
                codecName = getCodecNameMethod.invoke(codecConfig) as? String
            } catch (e: Exception) {
                ObsidianCrashLogger.logWarning(TAG, "Failed reading getCodecName: ${e.message}")
            }

            try {
                val getSampleRateMethod = codecConfig.javaClass.getMethod("getSampleRate")
                sampleRateInt = (getSampleRateMethod.invoke(codecConfig) as? Int) ?: 0
            } catch (e: Exception) {
                ObsidianCrashLogger.logWarning(TAG, "Failed reading getSampleRate: ${e.message}")
            }

            try {
                val getBitsPerSampleMethod = codecConfig.javaClass.getMethod("getBitsPerSample")
                bitsPerSampleInt = (getBitsPerSampleMethod.invoke(codecConfig) as? Int) ?: 0
            } catch (e: Exception) {
                ObsidianCrashLogger.logWarning(TAG, "Failed reading getBitsPerSample: ${e.message}")
            }

            val resolvedCodec = when {
                !codecName.isNullOrBlank() && !codecName.equals("NONE", ignoreCase = true) -> mapRawCodecName(codecName)
                codecType == 0 -> "SBC"
                codecType == 1 -> "AAC"
                codecType == 2 -> "aptX"
                codecType == 3 -> "aptX HD"
                codecType == 4 -> "LDAC"
                codecType == 5 -> "aptX Adaptive"
                codecType == 6 -> "aptX TWS+"
                codecType == 7 -> "LC3"
                codecType == 8 -> "Opus"
                else -> {
                    // Try parsing toString()
                    val str = codecConfig.toString().uppercase()
                    when {
                        str.contains("LDAC") -> "LDAC"
                        str.contains("APTX_HD") || str.contains("APTX HD") -> "aptX HD"
                        str.contains("APTX_ADAPTIVE") -> "aptX Adaptive"
                        str.contains("APTX") -> "aptX"
                        str.contains("AAC") -> "AAC"
                        str.contains("LC3") -> "LC3"
                        str.contains("OPUS") -> "Opus"
                        str.contains("SBC") -> "SBC"
                        else -> "AAC"
                    }
                }
            }

            val resolvedSampleRate = when (sampleRateInt) {
                1 shl 0 -> "44.1 kHz"
                1 shl 1 -> "48.0 kHz"
                1 shl 2 -> "88.2 kHz"
                1 shl 3 -> "96.0 kHz"
                1 shl 4 -> "176.4 kHz"
                1 shl 5 -> "192.0 kHz"
                else -> null
            }

            val resolvedBits = when (bitsPerSampleInt) {
                1 shl 0 -> "16-bit"
                1 shl 1 -> "24-bit"
                1 shl 2 -> "32-bit"
                else -> null
            }

            return Triple(resolvedCodec, resolvedSampleRate, resolvedBits)
        } catch (e: Throwable) {
            Log.d(TAG, "Error parsing codec config: ${e.message}")
            return Triple(null, null, null)
        }
    }

    fun cleanup() {
        if (!isInitialized) return
        val appCtx = appContext ?: return
        try {
            appCtx.unregisterReceiver(bluetoothReceiver)
            Log.d(TAG, "Unregistered Bluetooth receiver")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to unregister Bluetooth receiver: ${e.message}")
        }
        try {
            val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to unregister audio device callback: ${e.message}")
        }
        try {
            val bluetoothManager = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            val proxy = bluetoothA2dp
            if (adapter != null && proxy != null) {
                adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to close Bluetooth profile proxy: ${e.message}")
        }
        bluetoothA2dp = null
        isInitialized = false
    }

    private fun mapRawCodecName(raw: String): String {
        val upper = raw.trim().uppercase()
        return when {
            upper.contains("LDAC") -> "LDAC"
            upper.contains("APTX HD") || upper.contains("APTX_HD") -> "aptX HD"
            upper.contains("APTX ADAPTIVE") || upper.contains("APTX_ADAPTIVE") -> "aptX Adaptive"
            upper.contains("APTX") -> "aptX"
            upper.contains("AAC") -> "AAC"
            upper.contains("LC3") -> "LC3"
            upper.contains("OPUS") -> "Opus"
            upper.contains("SBC") -> "SBC"
            else -> raw
        }
    }

    private fun inferCodecFromDeviceName(deviceName: String?): String {
        if (deviceName == null) return "AAC"
        val name = deviceName.uppercase()
        return when {
            name.contains("WH-1000") || name.contains("WF-1000") || name.contains("SONY") || name.contains("FI ये") || name.contains("FIIO") -> "LDAC"
            name.contains("SENNHEISER") || name.contains("MOMENTUM") || name.contains("BOWERS") || name.contains("B&W") -> "aptX HD"
            name.contains("BOSE") || name.contains("AIRPODS") || name.contains("BEATS") || name.contains("GALAXY BUDS") || name.contains("PIXEL BUDS") -> "AAC"
            else -> "AAC"
        }
    }

    private fun checkBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }
}
