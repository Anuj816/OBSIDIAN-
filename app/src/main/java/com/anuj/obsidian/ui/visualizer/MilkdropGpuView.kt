package com.anuj.obsidian.ui.visualizer

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * =========================================================================================
 * OBSIDIAN HARDWARE MILKDROP GPU VIEW (GLSurfaceView Compose Wrapper)
 * =========================================================================================
 * Bridges the OpenGL ES 2.0/3.0 MilkdropGpuRenderer into Jetpack Compose with zero overhead.
 * =========================================================================================
 */
class MilkdropGLSurfaceView(
    context: Context,
    val renderer: MilkdropGpuRenderer = MilkdropGpuRenderer()
) : GLSurfaceView(context), GLSurfaceView.Renderer {

    private var audioTelemetryProvider: (() -> AudioTelemetrySnapshot)? = null

    init {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val reqVersion = am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
            val glesVersion = if (reqVersion >= 0x00030000) 3 else 2
            setEGLContextClientVersion(glesVersion)
        } catch (e: Exception) {
            try {
                setEGLContextClientVersion(2)
            } catch (e2: Exception) {
                // Ignore context setup fallback
            }
        }
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setTelemetryProvider(provider: () -> AudioTelemetrySnapshot) {
        audioTelemetryProvider = provider
    }

    fun loadPreset(presetIR: MilkPresetIR) {
        queueEvent {
            try {
                if (renderer.currentPresetIR != null) {
                    val randPattern = (0..3).random()
                    renderer.startTransition(presetIR, durationSec = 3.0f, pattern = randPattern)
                } else {
                    renderer.loadPreset(presetIR)
                }
            } catch (t: Throwable) {
                android.util.Log.w("MilkdropGLSurfaceView", "loadPreset error: ${t.message}")
            }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            renderer.initGL(width.coerceAtLeast(64), height.coerceAtLeast(64))
        } catch (t: Throwable) {
            android.util.Log.e("MilkdropGLSurfaceView", "onSurfaceCreated error: ${t.message}")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        try {
            renderer.resize(w.coerceAtLeast(64), h.coerceAtLeast(64))
        } catch (t: Throwable) {
            android.util.Log.e("MilkdropGLSurfaceView", "onSurfaceChanged error: ${t.message}")
        }
    }

    private val defaultTelemetry = AudioTelemetrySnapshot(1f, 1f, 1f, 1f, 1f, 1f)

    override fun onDrawFrame(gl: GL10?) {
        try {
            val telemetry = audioTelemetryProvider?.invoke() ?: defaultTelemetry
            renderer.renderFrame(
                bass = telemetry.bass,
                mid = telemetry.mid,
                treb = telemetry.treb,
                bassAtt = telemetry.bassAtt,
                midAtt = telemetry.midAtt,
                trebAtt = telemetry.trebAtt,
                audioWaveformLeft = telemetry.waveformLeft,
                audioWaveformRight = telemetry.waveformRight
            )
        } catch (t: Throwable) {
            android.util.Log.w("MilkdropGLSurfaceView", "renderFrame notice: ${t.message}")
        }
    }

    fun release() {
        queueEvent {
            try {
                renderer.release()
            } catch (t: Throwable) {
                android.util.Log.w("MilkdropGLSurfaceView", "release notice: ${t.message}")
            }
        }
    }
}

class AudioTelemetrySnapshot(
    var bass: Float = 0.1f,
    var mid: Float = 0.1f,
    var treb: Float = 0.1f,
    var bassAtt: Float = 0.1f,
    var midAtt: Float = 0.1f,
    var trebAtt: Float = 0.1f,
    var waveformLeft: FloatArray? = null,
    var waveformRight: FloatArray? = null
)

/**
 * Jetpack Compose Composable hosting the hardware MilkDrop GPU View.
 */
@Composable
fun MilkdropGpuCanvas(
    presetIR: MilkPresetIR?,
    modifier: Modifier = Modifier,
    telemetryProvider: () -> AudioTelemetrySnapshot
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val glView = remember {
        MilkdropGLSurfaceView(context).apply {
            setTelemetryProvider(telemetryProvider)
        }
    }

    LaunchedEffect(presetIR) {
        if (presetIR != null) {
            glView.loadPreset(presetIR)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { glView.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { glView.onPause() }
                Lifecycle.Event.ON_DESTROY -> runCatching { glView.release() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                glView.onPause()
                glView.release()
            }
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier.fillMaxSize()
    )
}
