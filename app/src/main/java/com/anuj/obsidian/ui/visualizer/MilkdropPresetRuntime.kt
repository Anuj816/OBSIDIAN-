package com.anuj.obsidian.ui.visualizer

import android.opengl.GLES20
import android.util.Log

/**
 * Encapsulates the entire, isolated execution state of a single MilkDrop preset.
 * This ensures independent Q-variable progression, T-variables, private megabuffers,
 * and compiled GLES shader programs for each active preset during rendering transitions.
 */
class MilkdropPresetRuntime(
    val presetIR: MilkPresetIR,
    val meshGridX: Int = 32,
    val meshGridY: Int = 24
) {
    companion object {
        private const val TAG = "MilkdropPresetRuntime"
    }

    // Preallocated vertex texture coordinate array for GC-stability
    val texCoords = FloatArray((meshGridX + 1) * (meshGridY + 1) * 2)

    // Isolated Variable Pools & Registers
    val presetPool = PresetVariablePool(presetIR.presetInitProgram?.totalSlots ?: 256)
    val vertexPool = VertexVariablePool(presetIR.perVertexProgram?.totalSlots ?: 256)
    val wavePools = Array(4) { i ->
        val slots = presetIR.customWaves.getOrNull(i)?.perFrameProgram?.totalSlots ?: 256
        WaveVariablePool(slots)
    }
    val shapePools = Array(4) { i ->
        val slots = presetIR.customShapes.getOrNull(i)?.perFrameProgram?.totalSlots ?: 256
        ShapeVariablePool(slots)
    }

    // Isolated Memory buffer (megabuf)
    val presetMemory = MilkdropPresetMemory()

    // Isolated General runtime variables (decay, zoom, rot, etc.)
    val runtimeState = MilkRuntimeState()

    // Q-variable slot mapping caches (specific to this preset's IR variable names)
    var qSlotIndices = IntArray(32) { -1 }
    var tSlotIndices = IntArray(8) { -1 }

    // Isolated Compiled Shader Programs
    var meshWarpProgram: Int = 0
    var compositeProgram: Int = 0

    // Isolated Frame count & Timing track
    var frameCount: Long = 0L
    var startTimeMs: Long = System.currentTimeMillis()

    // Private Ping-Pong Framebuffers so old and new presets do not corrupt each other's feedback loops
    val fboIds = IntArray(2)
    val fboTexIds = IntArray(2)
    var currentFboIndex = 0

    init {
        qSlotIndices = IntArray(32) { q ->
            presetIR.perFrameProgram?.nameToSlotMap?.get("q${q + 1}") ?: -1
        }
        tSlotIndices = IntArray(8) { t ->
            presetIR.perFrameProgram?.nameToSlotMap?.get("t${t + 1}") ?: -1
        }

        // Initialize variables and state to defaults
        presetPool.reset()
        presetMemory.reset()
        runtimeState.resetToDefaults(presetIR.staticParams)

        // Run Preset Init equations ONCE to set initial variables, Q variables, etc.
        presetIR.presetInitProgram?.let { initProg ->
            MilkdropExpressionCompiler.executeProgram(initProg, presetPool.registers, presetMemory)
        }

        // Snapshot base Q variables from preset init
        presetPool.snapshotBaseQ(qSlotIndices)

        // Reset per-instance wave and shape pools
        for (w in wavePools) w.reset()
        for (s in shapePools) s.reset()
    }

    /**
     * Initializes the private GLES ping-pong framebuffers for this runtime.
     */
    fun setupFramebuffers(width: Int, height: Int) {
        releaseFramebuffers()

        GLES20.glGenFramebuffers(2, fboIds, 0)
        GLES20.glGenTextures(2, fboTexIds, 0)

        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexIds[i])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[i])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, fboTexIds[i], 0
            )

            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "Private FBO $i creation failed with status $status")
            } else {
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun clearFramebuffers() {
        for (i in 0..1) {
            if (fboIds[i] != 0) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[i])
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /**
     * Compiles the custom shaders for this runtime, or loads fallbacks.
     */
    fun compileShaders() {
        releaseShaders()

        presetIR.warpShader?.let { ws ->
            if (ws.isValid && ws.glslSource.isNotEmpty()) {
                meshWarpProgram = MilkdropShaderCompiler.createProgram(
                    MilkdropShaderCompiler.VERTEX_SHADER_QUAD,
                    ws.glslSource
                )
            }
        }
        if (meshWarpProgram == 0) {
            meshWarpProgram = MilkdropShaderCompiler.createProgram(
                MilkdropShaderCompiler.VERTEX_SHADER_QUAD,
                MilkdropShaderCompiler.FRAGMENT_SHADER_WARP_DEFAULT
            )
        }

        presetIR.compositeShader?.let { cs ->
            if (cs.isValid && cs.glslSource.isNotEmpty()) {
                compositeProgram = MilkdropShaderCompiler.createProgram(
                    MilkdropShaderCompiler.VERTEX_SHADER_QUAD,
                    cs.glslSource
                )
            }
        }
        if (compositeProgram == 0) {
            compositeProgram = MilkdropShaderCompiler.createProgram(
                MilkdropShaderCompiler.VERTEX_SHADER_QUAD,
                MilkdropShaderCompiler.FRAGMENT_SHADER_COMP_DEFAULT
            )
        }
    }

    fun releaseFramebuffers() {
        if (fboIds[0] != 0) {
            GLES20.glDeleteFramebuffers(2, fboIds, 0)
            GLES20.glDeleteTextures(2, fboTexIds, 0)
            fboIds[0] = 0
            fboIds[1] = 0
            fboTexIds[0] = 0
            fboTexIds[1] = 0
        }
    }

    fun releaseShaders() {
        if (meshWarpProgram != 0) {
            GLES20.glDeleteProgram(meshWarpProgram)
            meshWarpProgram = 0
        }
        if (compositeProgram != 0) {
            GLES20.glDeleteProgram(compositeProgram)
            compositeProgram = 0
        }
    }

    fun release() {
        releaseFramebuffers()
        releaseShaders()
    }
}
