package com.anuj.obsidian.ui.visualizer

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ProjectMGLSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val sessionId = ProjectMNative.createSession()
    private val renderer: ProjectMRenderer
    @Volatile private var currentPresetPath: String? = null
    @Volatile private var currentPresetData: String? = null
    private var lastLoadedKey: String? = null
    var onNativeFailureCallback: (() -> Unit)? = null

    init {
        try {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        } catch (e: Exception) {
            Log.w("ProjectMGLSurfaceView", "EGL setup fallback: ${e.message}")
            try {
                setEGLContextClientVersion(2)
            } catch (e2: Exception) {
                Log.d("ProjectMGLSurfaceView", "EGL v2 context fallback notice: ${e2.message}")
            }
        }
        setPreserveEGLContextOnPause(true)
        setZOrderMediaOverlay(true)
        renderer = ProjectMRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    
    fun setPreset(path: String?, data: String? = null) {
        currentPresetPath = path
        currentPresetData = data
        queueEvent {
            try {
                if (ProjectMNative.isLoaded && renderer.initialized) {
                    loadPresetInternal(path, data)
                }
            } catch (t: Throwable) {
                Log.e("ProjectMGLSurfaceView", "setPreset error: ${t.message}")
            }
        }
        requestRender()
    }

    private fun loadPresetInternal(path: String?, data: String?) {
        val key = "${path ?: ""}_${data?.hashCode() ?: 0}"
        if (key == lastLoadedKey && !lastLoadedKey.isNullOrBlank()) {
            return
        }
        var loaded = false
        val fileName = path?.let { java.io.File(it).name } ?: "memory_preset.milk"

        // 1. Prefer nativeLoadPreset(cacheAbsolutePath) if file path exists
        if (!path.isNullOrBlank()) {
            val file = java.io.File(path)
            if (file.exists() && file.canRead()) {
                try {
                    loaded = ProjectMNative.safeLoadPreset(file.absolutePath)
                    Log.d("ProjectMGLSurfaceView", "safeLoadPreset result=$loaded for $fileName at ${file.absolutePath}")
                } catch (e: Throwable) {
                    Log.w("ProjectMGLSurfaceView", "safeLoadPreset threw: ${e.message} for $fileName")
                }
            }
        }

        // 2. Fallback nativeLoadPresetData(text) if path load fails or path was not present
        if (!loaded) {
            val content = if (!data.isNullOrBlank()) {
                data
            } else if (!path.isNullOrBlank()) {
                val file = java.io.File(path)
                if (file.exists() && file.canRead()) runCatching { file.readText() }.getOrNull() else null
            } else null

            if (!content.isNullOrBlank()) {
                try {
                    loaded = ProjectMNative.safeLoadPresetData(content)
                    Log.d("ProjectMGLSurfaceView", "safeLoadPresetData fallback result=$loaded for $fileName")
                } catch (e: Throwable) {
                    Log.w("ProjectMGLSurfaceView", "safeLoadPresetData threw: ${e.message} for $fileName")
                }
            }
        }

        if (loaded) {
            lastLoadedKey = key
            Log.i("ProjectMGLSurfaceView", "✓ Successfully loaded preset in projectM: $fileName")
        } else {
            Log.w("ProjectMGLSurfaceView", "✗ Failed to load preset in projectM: $fileName (path=$path)")
        }
    }
    
    fun addPcm(samples: FloatArray, channels: Int) {
        queueEvent {
            try {
                if (ProjectMNative.isLoaded && renderer.initialized) {
                    ProjectMNative.safeAddPcmF32(samples, channels)
                }
            } catch (t: Throwable) {
                Log.w("ProjectMGLSurfaceView", "addPcm error: ${t.message}")
            }
        }
    }

    fun destroy() {
        queueEvent {
            try {
                if (ProjectMNative.isLoaded) {
                    lastLoadedKey = null
                    ProjectMNative.destroySession(sessionId)
                }
            } catch (t: Throwable) {
                Log.w("ProjectMGLSurfaceView", "destroy error: ${t.message}")
            }
        }
    }

    private inner class ProjectMRenderer : GLSurfaceView.Renderer {
        var initialized = false
        private var failureCount = 0
        
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            Log.d("ProjectMGLSurfaceView", "onSurfaceCreated")
            try {
                if (ProjectMNative.isLoaded && !initialized) {
                    val w = width.coerceAtLeast(64)
                    val h = height.coerceAtLeast(64)
                    initialized = ProjectMNative.safeInit(w, h, null)
                    if (initialized) {
                        loadPresetInternal(currentPresetPath, currentPresetData)
                    }
                }
            } catch (t: Throwable) {
                Log.e("ProjectMGLSurfaceView", "onSurfaceCreated error: ${t.message}")
                onNativeFailureCallback?.invoke()
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            Log.d("ProjectMGLSurfaceView", "onSurfaceChanged: $width x $height")
            try {
                if (ProjectMNative.isLoaded) {
                    val w = width.coerceAtLeast(64)
                    val h = height.coerceAtLeast(64)
                    if (!initialized) {
                        initialized = ProjectMNative.safeInit(w, h, null)
                        if (initialized) {
                            loadPresetInternal(currentPresetPath, currentPresetData)
                        }
                    } else {
                        ProjectMNative.safeResize(w, h)
                        if (lastLoadedKey == null && (currentPresetPath != null || currentPresetData != null)) {
                            loadPresetInternal(currentPresetPath, currentPresetData)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("ProjectMGLSurfaceView", "onSurfaceChanged error: ${t.message}")
                onNativeFailureCallback?.invoke()
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            try {
                if (ProjectMNative.isLoaded && initialized) {
                    val snap = HardwareVisualizerBuffer.currentSnapshot
                    if (snap.waveform.isNotEmpty()) {
                        try {
                            ProjectMNative.safeAddPcmF32(snap.waveform, 1)
                        } catch (e: Throwable) {
                            Log.w("ProjectMGLSurfaceView", "PCM dispatch notice: ${e.message}")
                        }
                    }
                    ProjectMNative.safeRender()
                    failureCount = 0
                }
            } catch (t: Throwable) {
                failureCount++
                Log.e("ProjectMGLSurfaceView", "onDrawFrame error ($failureCount): ${t.message}")
                if (failureCount > 3) {
                    initialized = false
                    onNativeFailureCallback?.invoke()
                }
            }
        }
    }
}

@Composable
fun ProjectMVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    presetPath: String?,
    presetContent: String? = null,
    snapshot: VisualizerSnapshot,
    onFallbackRequired: () -> Unit
) {
    val context = LocalContext.current
    var viewRef by remember { mutableStateOf<ProjectMGLSurfaceView?>(null) }
    var nativeFailed by remember { mutableStateOf(!ProjectMNative.isLoaded) }
    var validationResult by remember { mutableStateOf<PresetValidationResult?>(null) }
    
    // Background validation pipeline: inspect and sanitize .milk file before sending to C++
    LaunchedEffect(presetPath, presetContent) {
        if (!ProjectMNative.isLoaded) {
            nativeFailed = true
            onFallbackRequired()
            return@LaunchedEffect
        }

        val result = MilkdropPresetValidator.validateAndSanitize(context, presetPath, presetContent)
        validationResult = result

        when (result) {
            is PresetValidationResult.Valid -> {
                if (result.isNativeSafe) {
                    nativeFailed = false
                    val safePath = result.sanitizedFile?.absolutePath ?: presetPath
                    viewRef?.setPreset(safePath, result.sanitizedContent)
                } else {
                    Log.w("ProjectMVisualizer", "Preset contains non-native constructs. Switching to GPU shader canvas.")
                    nativeFailed = true
                    onFallbackRequired()
                }
            }
            is PresetValidationResult.Invalid -> {
                Log.w("ProjectMVisualizer", "Preset validation failed: ${result.reason}. Engaging safe GPU canvas.")
                nativeFailed = true
                onFallbackRequired()
            }
        }
    }

    if (ProjectMNative.isLoaded && !nativeFailed) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                ProjectMGLSurfaceView(ctx).also {
                    viewRef = it
                    it.onNativeFailureCallback = {
                        nativeFailed = true
                        onFallbackRequired()
                    }
                    val currentVal = validationResult
                    if (currentVal is PresetValidationResult.Valid) {
                        it.setPreset(currentVal.sanitizedFile?.absolutePath ?: presetPath, currentVal.sanitizedContent)
                    } else {
                        it.setPreset(presetPath, presetContent)
                    }
                    it.onResume()
                }
            },
            update = { view ->
                view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        )
        
        DisposableEffect(Unit) {
            onDispose {
                try {
                    viewRef?.onPause()
                    viewRef?.destroy()
                } catch (t: Throwable) {
                    Log.w("ProjectMVisualizer", "Dispose error: ${t.message}")
                }
                viewRef = null
            }
        }
    } else {
        // Safe GPU Canvas Fallback for MilkDrop presets
        val fallbackIR = remember(validationResult, presetPath) {
            (validationResult as? PresetValidationResult.Valid)?.presetIR
                ?: com.anuj.obsidian.ui.visualizer.MilkdropPresetLibrary.customPresets.firstOrNull()?.let {
                    com.anuj.obsidian.ui.visualizer.MilkdropPresetLibrary.getPresetIRById(it.id)
                }
        }
        com.anuj.obsidian.ui.visualizer.MilkdropGpuCanvas(
            presetIR = fallbackIR,
            modifier = modifier.fillMaxSize(),
            telemetryProvider = {
                val snap = HardwareVisualizerBuffer.currentSnapshot
                AudioTelemetrySnapshot(
                    bass = snap.bassEnergy,
                    mid = snap.midEnergy,
                    treb = snap.trebleEnergy,
                    bassAtt = snap.bassEnergy,
                    midAtt = snap.midEnergy,
                    trebAtt = snap.trebleEnergy,
                    waveformLeft = snap.waveform,
                    waveformRight = snap.waveform
                )
            }
        )
    }
}
