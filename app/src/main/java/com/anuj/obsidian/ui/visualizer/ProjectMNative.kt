package com.anuj.obsidian.ui.visualizer

import android.util.Log

object ProjectMNative {
    private const val TAG = "ProjectMNative"
    var isLoaded = false
        private set

    @Volatile
    var isNativeInitialized = false
        private set

    private var activeSessionId = 0
    private val nativeLock = Any()

    init {
        try {
            System.loadLibrary("c++_shared")
            Log.d(TAG, "Loaded c++_shared successfully")
        } catch (e: Throwable) {
            Log.d(TAG, "c++_shared load notice: ${e.message}")
        }

        try {
            System.loadLibrary("obsidian_projectm")
            isLoaded = true
            Log.i(TAG, "Successfully loaded obsidian_projectm JNI library")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Failed to load obsidian_projectm: ${e.message}. Trying debug variant...")
            try {
                System.loadLibrary("obsidian_projectmd")
                isLoaded = true
                Log.i(TAG, "Successfully loaded obsidian_projectmd JNI library fallback")
            } catch (e2: Throwable) {
                Log.e(TAG, "Failed to load obsidian_projectm / obsidian_projectmd: ${e2.message}", e2)
                isLoaded = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading obsidian_projectm: ${e.message}", e)
            isLoaded = false
        }
    }

    fun createSession(): Int {
        synchronized(nativeLock) {
            return ++activeSessionId
        }
    }

    fun destroySession(sessionId: Int) {
        synchronized(nativeLock) {
            if (sessionId == activeSessionId) {
                safeDestroy()
            }
        }
    }

    fun safeInit(width: Int, height: Int, presetPath: String?): Boolean {
        synchronized(nativeLock) {
            if (!isLoaded) return false
            return try {
                val result = nativeInit(width.coerceAtLeast(64), height.coerceAtLeast(64), presetPath)
                isNativeInitialized = result
                result
            } catch (t: Throwable) {
                Log.e(TAG, "nativeInit threw: ${t.message}", t)
                isNativeInitialized = false
                false
            }
        }
    }

    fun safeResize(width: Int, height: Int) {
        synchronized(nativeLock) {
            if (!isLoaded || !isNativeInitialized) return
            try {
                nativeResize(width.coerceAtLeast(64), height.coerceAtLeast(64))
            } catch (t: Throwable) {
                Log.w(TAG, "nativeResize threw: ${t.message}")
            }
        }
    }

    fun safeRender() {
        synchronized(nativeLock) {
            if (!isLoaded || !isNativeInitialized) return
            try {
                nativeRender()
            } catch (t: Throwable) {
                Log.e(TAG, "nativeRender threw: ${t.message}", t)
            }
        }
    }

    fun safeAddPcmF32(samples: FloatArray, channels: Int) {
        synchronized(nativeLock) {
            if (!isLoaded || !isNativeInitialized || samples.isEmpty()) return
            try {
                nativeAddPcmF32(samples, if (channels >= 2) 2 else 1)
            } catch (t: Throwable) {
                Log.w(TAG, "nativeAddPcmF32 threw: ${t.message}")
            }
        }
    }

    fun safeLoadPreset(path: String): Boolean {
        synchronized(nativeLock) {
            if (!isLoaded || !isNativeInitialized || path.isBlank()) return false
            return try {
                nativeLoadPreset(path)
            } catch (t: Throwable) {
                Log.e(TAG, "nativeLoadPreset threw: ${t.message}", t)
                false
            }
        }
    }

    fun safeLoadPresetData(data: String): Boolean {
        synchronized(nativeLock) {
            if (!isLoaded || !isNativeInitialized || data.isBlank()) return false
            return try {
                nativeLoadPresetData(data)
            } catch (t: Throwable) {
                Log.e(TAG, "nativeLoadPresetData threw: ${t.message}", t)
                false
            }
        }
    }

    fun safeDestroy() {
        synchronized(nativeLock) {
            if (!isLoaded || !isNativeInitialized) return
            try {
                nativeDestroy()
            } catch (t: Throwable) {
                Log.w(TAG, "nativeDestroy threw: ${t.message}")
            } finally {
                isNativeInitialized = false
            }
        }
    }

    private external fun nativeInit(width: Int, height: Int, presetPath: String?): Boolean
    private external fun nativeResize(width: Int, height: Int)
    private external fun nativeRender()
    private external fun nativeAddPcmF32(samples: FloatArray, channels: Int)
    private external fun nativeLoadPreset(path: String): Boolean
    private external fun nativeLoadPresetData(data: String): Boolean
    private external fun nativeSetBeatSensitivity(value: Float)
    private external fun nativeDestroy()
}
