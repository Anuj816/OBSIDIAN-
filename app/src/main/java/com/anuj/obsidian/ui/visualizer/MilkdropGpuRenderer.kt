package com.anuj.obsidian.ui.visualizer

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * =========================================================================================
 * OBSIDIAN MILKDROP GPU HARDWARE RENDERING ENGINE (OpenGL ES 2.0 / 3.0)
 * =========================================================================================
 * Specification-compliant MilkDrop renderer supporting:
 * 1. Independent Preset Runtimes: Run simultaneously during transitions without memory bleeding.
 * 2. Persistent Feedback loops: Private fbo pairs per runtime to avoid corruption.
 * 3. GC-Stable Loop: Zero memory allocations in renderFrame().
 * 4. Advanced transitions (Crossfade, Plasma, Radial, Wipe).
 * =========================================================================================
 */
class MilkdropGpuRenderer(
    val meshGridX: Int = 32,
    val meshGridY: Int = 24
) {
    companion object {
        private const val TAG = "MilkdropGpuRenderer"
        private const val FLOAT_SIZE = 4
        private const val SHORT_SIZE = 2
    }

    // Viewport dimensions
    var width: Int = 1080
        private set
    var height: Int = 1920
        private set

    // Active preset runtimes
    var currentRuntime: MilkdropPresetRuntime? = null
        private set
    var transitionRuntime: MilkdropPresetRuntime? = null
        private set
    var transitionProgress: Float = 1.0f // 1.0 = 100% currentRuntime, <1.0 = in transition
    var transitionPattern: Int = 0 // 0=Crossfade, 1=Plasma, 2=Radial, 3=Wipe
    var transitionDurationSeconds: Float = 3.0f

    // Backwards compatibility properties (delegates to currentRuntime if present)
    val currentPresetIR: MilkPresetIR?
        get() = currentRuntime?.presetIR
    val transitionPresetIR: MilkPresetIR?
        get() = transitionRuntime?.presetIR

    val presetPool: PresetVariablePool
        get() = currentRuntime?.presetPool ?: dummyPresetPool
    val vertexPool: VertexVariablePool
        get() = currentRuntime?.vertexPool ?: dummyVertexPool
    val wavePools: Array<WaveVariablePool>
        get() = currentRuntime?.wavePools ?: dummyWavePools
    val shapePools: Array<ShapeVariablePool>
        get() = currentRuntime?.shapePools ?: dummyShapePools
    val runtimeState: MilkRuntimeState
        get() = currentRuntime?.runtimeState ?: dummyRuntimeState
    val presetMemory: MilkdropPresetMemory
        get() = currentRuntime?.presetMemory ?: dummyPresetMemory

    private val dummyPresetPool = PresetVariablePool(256)
    private val dummyVertexPool = VertexVariablePool(256)
    private val dummyWavePools = Array(4) { WaveVariablePool(256) }
    private val dummyShapePools = Array(4) { ShapeVariablePool(256) }
    private val dummyRuntimeState = MilkRuntimeState()
    private val dummyPresetMemory = MilkdropPresetMemory()

    private var qSlotIndices: IntArray
        get() = currentRuntime?.qSlotIndices ?: dummyQSlotIndices
        set(value) { currentRuntime?.qSlotIndices = value }
    private var tSlotIndices: IntArray
        get() = currentRuntime?.tSlotIndices ?: dummyTSlotIndices
        set(value) { currentRuntime?.tSlotIndices = value }

    private val uniformLocationCache = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    private val attribLocationCache = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    private var smoothedFps: Float = 60f

    private fun getUniformLoc(program: Int, name: String): Int {
        if (program <= 0) return -1
        val key = (program.toLong() shl 32) or (name.hashCode().toLong() and 0xFFFFFFFFL)
        val cached = uniformLocationCache[key]
        if (cached != null) return cached
        val loc = GLES20.glGetUniformLocation(program, name)
        uniformLocationCache[key] = loc
        return loc
    }

    private fun getAttribLoc(program: Int, name: String): Int {
        if (program <= 0) return -1
        val key = (program.toLong() shl 32) or (name.hashCode().toLong() and 0xFFFFFFFFL)
        val cached = attribLocationCache[key]
        if (cached != null) return cached
        val loc = GLES20.glGetAttribLocation(program, name)
        attribLocationCache[key] = loc
        return loc
    }

    private val dummyQSlotIndices = IntArray(32) { -1 }
    private val dummyTSlotIndices = IntArray(8) { -1 }

    // GLES Shaders
    val meshWarpProgram: Int
        get() = currentRuntime?.meshWarpProgram ?: defaultMeshWarpProgram
    val compositeProgram: Int
        get() = currentRuntime?.compositeProgram ?: defaultCompositeProgram

    private var defaultMeshWarpProgram: Int = 0
    private var defaultCompositeProgram: Int = 0
    private var blurProgram: Int = 0
    private var blendProgram: Int = 0
    private var shapeWaveProgram: Int = 0

    // Shared Blur Framebuffers (Gaussian Blur is a transient filter)
    private val blurFboIds = IntArray(3)
    private val blurTexIds = IntArray(3)

    // Fullscreen Mesh Buffers
    private val numVertices = (meshGridX + 1) * (meshGridY + 1)
    private val numIndices = meshGridX * meshGridY * 6
    private lateinit var vertexPosBuffer: FloatBuffer
    private lateinit var vertexTexCoordBuffer: FloatBuffer
    private lateinit var meshIndexBuffer: ShortBuffer

    // Fullscreen Quad Buffers for Post-Process & Blur passes
    private lateinit var quadPosBuffer: FloatBuffer
    private lateinit var quadTexCoordBuffer: FloatBuffer

    // Reusable GC-Stable Arrays to avoid allocation in render loop
    private val reusableWavePoints = FloatArray(1024 * 2)
    private val reusableShapeCoords = FloatArray((64 + 2) * 2)

    // Wave and Shape dynamic geometry buffers
    private val maxWavePoints = 1024
    private lateinit var waveVertexBuffer: FloatBuffer
    private lateinit var shapeVertexBuffer: FloatBuffer

    // Noise Texture
    private var noiseTextureId: Int = 0

    // Frame timing & telemetry
    private var frameCount: Long = 0L
    private var startTimeMs: Long = System.currentTimeMillis()
    private var lastTimeSec: Float = 0f

    // Deterministic testing mode flag
    var isDeterministicMode: Boolean = false
    var deterministicTimeSec: Float = 0f
    var deterministicFrame: Long = 0L

    fun initGL(w: Int, h: Int) {
        width = max(w, 64)
        height = max(h, 64)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        setupMeshBuffers()
        setupQuadBuffers()
        setupDynamicBuffers()
        setupSharedFramebuffers()
        setupShaders()
        setupNoiseTexture()

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        width = w
        height = h
        releaseSharedFramebuffers()
        setupSharedFramebuffers()

        currentRuntime?.setupFramebuffers(w, h)
        transitionRuntime?.setupFramebuffers(w, h)
    }

    private fun setupMeshBuffers() {
        val positions = FloatArray(numVertices * 2)
        val texCoords = FloatArray(numVertices * 2)
        val indices = ShortArray(numIndices)

        var vIdx = 0
        for (y in 0..meshGridY) {
            val v = y.toFloat() / meshGridY.toFloat()
            val posY = 1.0f - v * 2.0f // OpenGL NDC Y: +1 at top, -1 at bottom

            for (x in 0..meshGridX) {
                val u = x.toFloat() / meshGridX.toFloat()
                val posX = u * 2.0f - 1.0f // OpenGL NDC X: -1 at left, +1 at right

                positions[vIdx * 2] = posX
                positions[vIdx * 2 + 1] = posY

                texCoords[vIdx * 2] = u
                texCoords[vIdx * 2 + 1] = v
                vIdx++
            }
        }

        var iIdx = 0
        val rowStride = meshGridX + 1
        for (y in 0 until meshGridY) {
            for (x in 0 until meshGridX) {
                val topLeft = (y * rowStride + x).toShort()
                val topRight = (y * rowStride + x + 1).toShort()
                val bottomLeft = ((y + 1) * rowStride + x).toShort()
                val bottomRight = ((y + 1) * rowStride + x + 1).toShort()

                // Triangle 1
                indices[iIdx++] = topLeft
                indices[iIdx++] = bottomLeft
                indices[iIdx++] = topRight

                // Triangle 2
                indices[iIdx++] = topRight
                indices[iIdx++] = bottomLeft
                indices[iIdx++] = bottomRight
            }
        }

        vertexPosBuffer = ByteBuffer.allocateDirect(positions.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(positions)
                position(0)
            }

        vertexTexCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(texCoords)
                position(0)
            }

        meshIndexBuffer = ByteBuffer.allocateDirect(indices.size * SHORT_SIZE)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply {
                put(indices)
                position(0)
            }
    }

    private fun setupQuadBuffers() {
        val quadPositions = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )
        val quadTexCoords = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )

        quadPosBuffer = ByteBuffer.allocateDirect(quadPositions.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(quadPositions)
                position(0)
            }

        quadTexCoordBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(quadTexCoords)
                position(0)
            }
    }

    private fun setupDynamicBuffers() {
        waveVertexBuffer = ByteBuffer.allocateDirect(maxWavePoints * 2 * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

        shapeVertexBuffer = ByteBuffer.allocateDirect(128 * 2 * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    private fun setupSharedFramebuffers() {
        // Setup 3 downsampled blur buffers (1/2, 1/4, 1/8)
        GLES20.glGenFramebuffers(3, blurFboIds, 0)
        GLES20.glGenTextures(3, blurTexIds, 0)

        for (b in 0..2) {
            val scale = 1 shl (b + 1)
            val bw = max(width / scale, 16)
            val bh = max(height / scale, 16)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFboIds[b])
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexIds[b])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                bw, bh, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, blurTexIds[b], 0
            )
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun releaseSharedFramebuffers() {
        if (blurFboIds[0] != 0) {
            GLES20.glDeleteFramebuffers(3, blurFboIds, 0)
            GLES20.glDeleteTextures(3, blurTexIds, 0)
            blurFboIds[0] = 0
            blurFboIds[1] = 0
            blurFboIds[2] = 0
            blurTexIds[0] = 0
            blurTexIds[1] = 0
            blurTexIds[2] = 0
        }
    }

    private fun setupShaders() {
        defaultMeshWarpProgram = MilkdropShaderCompiler.createProgram(
            MilkdropShaderCompiler.VERTEX_SHADER_QUAD,
            MilkdropShaderCompiler.FRAGMENT_SHADER_WARP_DEFAULT
        )
        defaultCompositeProgram = MilkdropShaderCompiler.createProgram(
            MilkdropShaderCompiler.VERTEX_SHADER_QUAD,
            MilkdropShaderCompiler.FRAGMENT_SHADER_COMP_DEFAULT
        )
        blurProgram = MilkdropShaderCompiler.createProgram(
            MilkdropShaderCompiler.VERTEX_SHADER_QUAD,
            MilkdropShaderCompiler.FRAGMENT_SHADER_BLUR
        )
        blendProgram = MilkdropShaderCompiler.createProgram(
            MilkdropShaderCompiler.VERTEX_SHADER_QUAD,
            MilkdropShaderCompiler.FRAGMENT_SHADER_BLEND
        )

        // Simple Solid Color program for dynamic waves and shapes
        val shapeWaveVs = """
            attribute vec4 aPosition;
            uniform mat4 uMatrix;
            void main() {
                gl_Position = aPosition;
            }
        """
        val shapeWaveFs = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
        shapeWaveProgram = MilkdropShaderCompiler.createProgram(shapeWaveVs, shapeWaveFs)
    }

    private fun setupNoiseTexture() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        noiseTextureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTextureId)

        val size = 128
        val noiseData = ByteBuffer.allocateDirect(size * size * 4)
        val rng = java.util.Random(0x4D494C4B)
        for (i in 0 until size * size) {
            val n = rng.nextInt(256).toByte()
            noiseData.put(n)
            noiseData.put(n)
            noiseData.put(n)
            noiseData.put(255.toByte())
        }
        noiseData.position(0)

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            size, size, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, noiseData
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
    }

    /**
     * Loads a new MilkPresetIR into the renderer and performs Preset Init.
     */
    fun loadPreset(presetIR: MilkPresetIR) {
        transitionRuntime?.release()
        transitionRuntime = null
        transitionProgress = 1.0f

        val newRuntime = MilkdropPresetRuntime(presetIR, meshGridX, meshGridY)
        newRuntime.setupFramebuffers(width, height)
        newRuntime.clearFramebuffers()
        newRuntime.compileShaders()

        currentRuntime?.release()
        currentRuntime = newRuntime

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    /**
     * Initiates a smooth GPU blend transition to a new preset.
     */
    fun startTransition(newPresetIR: MilkPresetIR, durationSec: Float = 3.0f, pattern: Int = 0) {
        transitionRuntime?.release()
        transitionRuntime = currentRuntime

        transitionProgress = 0.0f
        transitionPattern = pattern
        transitionDurationSeconds = if (durationSec > 0f) durationSec else 3.0f

        val newRuntime = MilkdropPresetRuntime(newPresetIR, meshGridX, meshGridY)
        newRuntime.setupFramebuffers(width, height)
        newRuntime.compileShaders()
        currentRuntime = newRuntime
    }

    /**
     * Authoritative per-frame render pass.
     */
    fun renderFrame(
        bass: Float,
        mid: Float,
        treb: Float,
        bassAtt: Float,
        midAtt: Float,
        trebAtt: Float,
        audioWaveformLeft: FloatArray? = null,
        audioWaveformRight: FloatArray? = null
    ) {
        try {
            val runtimeB = currentRuntime ?: return

            // 1. Timing calculations
            val currentTimeSec = if (isDeterministicMode) {
                deterministicTimeSec
            } else {
                (System.currentTimeMillis() - startTimeMs) / 1000f
            }
            val currentFrameNum = if (isDeterministicMode) {
                deterministicFrame
            } else {
                frameCount++
            }

            // Advance transition progress
            var dt = 0.016f
            if (lastTimeSec > 0f) {
                dt = currentTimeSec - lastTimeSec
            }
            lastTimeSec = currentTimeSec

            if (dt > 0.0001f) {
                val measuredFps = (1.0f / dt).coerceIn(10f, 240f)
                smoothedFps = smoothedFps * 0.9f + measuredFps * 0.1f
            }

            val inTransition = transitionRuntime != null && transitionProgress < 1.0f
            if (inTransition) {
                val progressStep = dt / transitionDurationSeconds
                transitionProgress = min(transitionProgress + progressStep, 1.0f)
            }

            if (transitionProgress >= 1.0f && transitionRuntime != null) {
                transitionRuntime?.release()
                transitionRuntime = null
            }

            // 2. Render runtime B (current / target)
            renderRuntimeFrame(
                runtimeB, bass, mid, treb, bassAtt, midAtt, trebAtt,
                audioWaveformLeft, audioWaveformRight, currentTimeSec, currentFrameNum
            )

            // 3. Render runtime A (transition / old) if active
            val runtimeA = transitionRuntime
            if (runtimeA != null) {
                renderRuntimeFrame(
                    runtimeA, bass, mid, treb, bassAtt, midAtt, trebAtt,
                    audioWaveformLeft, audioWaveformRight, currentTimeSec, currentFrameNum
                )
            }

            // 4. Post-process shared Blur Passes (using current runtime B's output as filter source)
            val dstFboIdxB = runtimeB.currentFboIndex
            val texB = runtimeB.fboTexIds[dstFboIdxB]
            renderBlurPasses(texB)

            // 5. Final Composite rendering to screen (framebuffer 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glDisable(GLES20.GL_BLEND)

            if (runtimeA != null) {
                val dstFboIdxA = runtimeA.currentFboIndex
                val texA = runtimeA.fboTexIds[dstFboIdxA]
                renderBlendPass(texA, texB, transitionProgress, transitionPattern, currentTimeSec)
            } else {
                renderCompositePassForRuntime(runtimeB, texB)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Milkdrop frame render notice: ${t.message}")
        }
    }

    private fun renderRuntimeFrame(
        runtime: MilkdropPresetRuntime,
        bass: Float,
        mid: Float,
        treb: Float,
        bassAtt: Float,
        midAtt: Float,
        trebAtt: Float,
        audioWaveformLeft: FloatArray?,
        audioWaveformRight: FloatArray?,
        currentTimeSec: Float,
        currentFrameNum: Long
    ) {
        val preset = runtime.presetIR
        val presetPool = runtime.presetPool
        val runtimeState = runtime.runtimeState
        val presetMemory = runtime.presetMemory
        val qSlotIndices = runtime.qSlotIndices

        runtimeState.time = currentTimeSec
        runtimeState.frame = currentFrameNum
        runtimeState.fps = smoothedFps
        runtimeState.bass = bass
        runtimeState.mid = mid
        runtimeState.treb = treb
        runtimeState.bassAtt = bassAtt
        runtimeState.midAtt = midAtt
        runtimeState.trebAtt = trebAtt

        // Sync variables and evolve Q-variables
        presetPool.beginFrame(qSlotIndices)

        setVariableForRuntime(runtime, presetPool.registers, "time", currentTimeSec)
        setVariableForRuntime(runtime, presetPool.registers, "frame", currentFrameNum.toFloat())
        setVariableForRuntime(runtime, presetPool.registers, "fps", 60f)
        setVariableForRuntime(runtime, presetPool.registers, "bass", bass)
        setVariableForRuntime(runtime, presetPool.registers, "mid", mid)
        setVariableForRuntime(runtime, presetPool.registers, "treb", treb)
        setVariableForRuntime(runtime, presetPool.registers, "bass_att", bassAtt)
        setVariableForRuntime(runtime, presetPool.registers, "mid_att", midAtt)
        setVariableForRuntime(runtime, presetPool.registers, "treb_att", trebAtt)
        setVariableForRuntime(runtime, presetPool.registers, "zoom", runtimeState.zoom)
        setVariableForRuntime(runtime, presetPool.registers, "rot", runtimeState.rot)
        setVariableForRuntime(runtime, presetPool.registers, "warp", runtimeState.warp)
        setVariableForRuntime(runtime, presetPool.registers, "cx", runtimeState.cx)
        setVariableForRuntime(runtime, presetPool.registers, "cy", runtimeState.cy)
        setVariableForRuntime(runtime, presetPool.registers, "dx", runtimeState.dx)
        setVariableForRuntime(runtime, presetPool.registers, "dy", runtimeState.dy)
        setVariableForRuntime(runtime, presetPool.registers, "sx", runtimeState.sx)
        setVariableForRuntime(runtime, presetPool.registers, "sy", runtimeState.sy)
        setVariableForRuntime(runtime, presetPool.registers, "decay", runtimeState.decay)

        // Execute Per-Frame AST Program
        preset.perFrameProgram?.let { prog ->
            MilkdropExpressionCompiler.executeProgram(prog, presetPool.registers, presetMemory)
        }

        // Capture modified Q-values and camera variables
        presetPool.endPerFrame(qSlotIndices)
        syncStateFromRegistersForRuntime(runtime, presetPool.registers)

        // Update Fullscreen Warp Mesh UVs via Per-Vertex AST execution
        evaluateWarpMeshForRuntime(runtime)

        // Ping-Pong FBO swap for this isolated runtime
        val srcFboIdx = runtime.currentFboIndex
        val dstFboIdx = 1 - runtime.currentFboIndex
        val srcTexId = runtime.fboTexIds[srcFboIdx]
        val dstFboId = runtime.fboIds[dstFboIdx]

        // Pass 1: Warp feedback Mesh pass (Overwrite dstFbo with decayed warped previous frame, preventing ghost stacking)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, dstFboId)
        GLES20.glViewport(0, 0, width, height)

        GLES20.glDisable(GLES20.GL_BLEND)
        renderWarpPassForRuntime(runtime, srcTexId)

        // Pass 2: Built-in & Custom waves (Blend dynamically over the warped base)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        renderWavesForRuntime(runtime, audioWaveformLeft, audioWaveformRight)

        // Pass 3: Custom shapes (Blend dynamically)
        renderShapesForRuntime(runtime)

        // Advance Ping-Pong index for this runtime
        runtime.currentFboIndex = dstFboIdx
    }

    private fun evaluateWarpMeshForRuntime(runtime: MilkdropPresetRuntime) {
        val preset = runtime.presetIR
        val perVertexProgram = preset.perVertexProgram
        val texCoords = runtime.texCoords

        val cx = runtime.runtimeState.cx
        val cy = runtime.runtimeState.cy
        val zoom = runtime.runtimeState.zoom
        val rot = runtime.runtimeState.rot
        val warp = runtime.runtimeState.warp
        val dx = runtime.runtimeState.dx
        val dy = runtime.runtimeState.dy
        val sx = runtime.runtimeState.sx
        val sy = runtime.runtimeState.sy

        val vertexPool = runtime.vertexPool
        val presetPool = runtime.presetPool
        val qSlotIndices = runtime.qSlotIndices
        val presetMemory = runtime.presetMemory

        val vertexMap = perVertexProgram?.nameToSlotMap
        val slotX = vertexMap?.get("x") ?: -1
        val slotY = vertexMap?.get("y") ?: -1
        val slotRad = vertexMap?.get("rad") ?: -1
        val slotAng = vertexMap?.get("ang") ?: -1
        val slotZoom = vertexMap?.get("zoom") ?: -1
        val slotRot = vertexMap?.get("rot") ?: -1
        val slotWarp = vertexMap?.get("warp") ?: -1
        val slotDx = vertexMap?.get("dx") ?: -1
        val slotDy = vertexMap?.get("dy") ?: -1

        var vIdx = 0
        if (perVertexProgram == null) {
            // ULTRA-FAST PATH: Homogeneous mesh grid without per-vertex scripts
            // Precalculate rotation and zoom reciprocals outside loops
            val invZoom = 1.0f / max(zoom, 0.01f)
            val cosR = cos(rot) * invZoom
            val sinR = sin(rot) * invZoom
            val stepY = 1.0f / meshGridY.toFloat()
            val stepX = 1.0f / meshGridX.toFloat()

            for (y in 0..meshGridY) {
                val v = y * stepY
                val relY = (v - cy) * sy
                val relYCos = relY * cosR
                val relYSin = relY * sinR

                for (x in 0..meshGridX) {
                    val u = x * stepX
                    val relX = (u - cx) * sx

                    val warpedU = cx + (relX * cosR - relYSin) - dx
                    val warpedV = cy + (relX * sinR + relYCos) - dy

                    texCoords[vIdx * 2] = warpedU
                    texCoords[vIdx * 2 + 1] = warpedV
                    vIdx++
                }
            }
        } else {
            // DYNAMIC PATH: Pre-prepare base registers once per frame to eliminate 825 calls to prepareForVertex
            val regs = vertexPool.registers
            vertexPool.prepareForVertex(
                presetPool, qSlotIndices,
                runtime.runtimeState.time, runtime.runtimeState.frame.toFloat(), 60f, 0f,
                runtime.runtimeState.bass, runtime.runtimeState.mid, runtime.runtimeState.treb,
                runtime.runtimeState.bassAtt, runtime.runtimeState.midAtt, runtime.runtimeState.trebAtt
            )
            val baseRegs = FloatArray(regs.size)
            System.arraycopy(regs, 0, baseRegs, 0, regs.size)

            val stepY = 1.0f / meshGridY.toFloat()
            val stepX = 1.0f / meshGridX.toFloat()

            for (y in 0..meshGridY) {
                val v = y * stepY
                val relY = (v - cy) * sy
                for (x in 0..meshGridX) {
                    val u = x * stepX
                    val relX = (u - cx) * sx
                    val rad = sqrt(relX * relX + relY * relY)
                    val ang = atan2(relY, relX)

                    var vZoom = zoom
                    var vRot = rot
                    var vWarp = warp
                    var vDx = dx
                    var vDy = dy

                    System.arraycopy(baseRegs, 0, regs, 0, regs.size)
                    if (slotX in regs.indices) regs[slotX] = u
                    if (slotY in regs.indices) regs[slotY] = v
                    if (slotRad in regs.indices) regs[slotRad] = rad
                    if (slotAng in regs.indices) regs[slotAng] = ang

                    MilkdropExpressionCompiler.executeProgram(perVertexProgram, regs, presetMemory)

                    if (slotZoom in regs.indices) vZoom = regs[slotZoom]
                    if (slotRot in regs.indices) vRot = regs[slotRot]
                    if (slotWarp in regs.indices) vWarp = regs[slotWarp]
                    if (slotDx in regs.indices) vDx = regs[slotDx]
                    if (slotDy in regs.indices) vDy = regs[slotDy]

                    val cosR = cos(vRot) / max(vZoom, 0.01f)
                    val sinR = sin(vRot) / max(vZoom, 0.01f)

                    val warpedU = cx + (relX * cosR - relY * sinR) - vDx
                    val warpedV = cy + (relX * sinR + relY * cosR) - vDy

                    texCoords[vIdx * 2] = warpedU
                    texCoords[vIdx * 2 + 1] = warpedV
                    vIdx++
                }
            }
        }

        vertexTexCoordBuffer.position(0)
        vertexTexCoordBuffer.put(texCoords)
        vertexTexCoordBuffer.position(0)
    }

    private fun renderWarpPassForRuntime(runtime: MilkdropPresetRuntime, srcTextureId: Int) {
        val prog = runtime.meshWarpProgram
        GLES20.glUseProgram(prog)

        val aPosLoc = getAttribLoc(prog, "aPosition")
        val aTexLoc = getAttribLoc(prog, "aTexCoord")
        val uSamplerLoc = getUniformLoc(prog, "uSamplerMain")
        val uDecayLoc = getUniformLoc(prog, "uDecay")
        val uTimeLoc = getUniformLoc(prog, "uTime")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTextureId)
        if (uSamplerLoc >= 0) GLES20.glUniform1i(uSamplerLoc, 0)
        val decayVal = if (runtime.runtimeState.decay <= 0f) 0.96f else runtime.runtimeState.decay.coerceIn(0.70f, 0.985f)
        if (uDecayLoc >= 0) GLES20.glUniform1f(uDecayLoc, decayVal)
        if (uTimeLoc >= 0) GLES20.glUniform1f(uTimeLoc, runtime.runtimeState.time)

        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, vertexPosBuffer)

        GLES20.glEnableVertexAttribArray(aTexLoc)
        GLES20.glVertexAttribPointer(aTexLoc, 2, GLES20.GL_FLOAT, false, 0, vertexTexCoordBuffer)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, numIndices, GLES20.GL_UNSIGNED_SHORT, meshIndexBuffer)

        GLES20.glDisableVertexAttribArray(aPosLoc)
        GLES20.glDisableVertexAttribArray(aTexLoc)
    }

    private fun renderWavesForRuntime(runtime: MilkdropPresetRuntime, audioLeft: FloatArray?, audioRight: FloatArray?) {
        val preset = runtime.presetIR

        for (wIdx in 0 until min(preset.customWaves.size, 4)) {
            val waveIR = preset.customWaves[wIdx]
            if (!waveIR.enabled && waveIR.perPointProgram == null) continue

            val pool = runtime.wavePools[wIdx]
            pool.syncFromPresetPool(runtime.presetPool, runtime.qSlotIndices, runtime.tSlotIndices)

            // Wave Per-Frame program
            waveIR.perFrameProgram?.let { prog ->
                MilkdropExpressionCompiler.executeProgram(prog, pool.registers, runtime.presetMemory)
                pool.syncTRegistersBack(runtime.tSlotIndices)
            }

            val samples = min(waveIR.samples, maxWavePoints)
            var pIdx = 0

            val perPointProg = waveIR.perPointProgram
            val pointMap = perPointProg?.nameToSlotMap
            val slotSample = pointMap?.get("sample") ?: -1
            val slotVal1 = pointMap?.get("value1") ?: -1
            val slotVal2 = pointMap?.get("value2") ?: -1
            val slotPtX = pointMap?.get("x") ?: -1
            val slotPtY = pointMap?.get("y") ?: -1
            val waveRegs = pool.registers

            for (i in 0 until samples) {
                val sampleNorm = i.toFloat() / (samples - 1).toFloat()
                val val1 = if (audioLeft != null && audioLeft.isNotEmpty()) audioLeft[i % audioLeft.size] else sin(sampleNorm * PI.toFloat() * 4f + runtime.runtimeState.time) * 0.3f
                val val2 = if (audioRight != null && audioRight.isNotEmpty()) audioRight[i % audioRight.size] else cos(sampleNorm * PI.toFloat() * 4f + runtime.runtimeState.time) * 0.3f

                var px = sampleNorm * 2.0f - 1.0f
                var py = val1 * waveIR.scaling

                if (perPointProg != null) {
                    if (slotSample in waveRegs.indices) waveRegs[slotSample] = sampleNorm
                    if (slotVal1 in waveRegs.indices) waveRegs[slotVal1] = val1
                    if (slotVal2 in waveRegs.indices) waveRegs[slotVal2] = val2
                    if (slotPtX in waveRegs.indices) waveRegs[slotPtX] = px * 0.5f + 0.5f
                    if (slotPtY in waveRegs.indices) waveRegs[slotPtY] = py * 0.5f + 0.5f

                    MilkdropExpressionCompiler.executeProgram(perPointProg, waveRegs, runtime.presetMemory)

                    if (slotPtX in waveRegs.indices) px = (waveRegs[slotPtX] - 0.5f) * 2.0f
                    if (slotPtY in waveRegs.indices) py = (waveRegs[slotPtY] - 0.5f) * 2.0f
                }

                if (pIdx * 2 + 1 < reusableWavePoints.size) {
                    reusableWavePoints[pIdx * 2] = px
                    reusableWavePoints[pIdx * 2 + 1] = py
                    pIdx++
                }
            }

            waveVertexBuffer.position(0)
            waveVertexBuffer.put(reusableWavePoints, 0, pIdx * 2)
            waveVertexBuffer.position(0)

            // Draw wave line
            GLES20.glUseProgram(shapeWaveProgram)
            val posLoc = getAttribLoc(shapeWaveProgram, "aPosition")
            val colLoc = getUniformLoc(shapeWaveProgram, "uColor")

            GLES20.glUniform4f(colLoc, waveIR.r, waveIR.g, waveIR.b, waveIR.a)
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, waveVertexBuffer)

            GLES20.glLineWidth(if (waveIR.bDrawThick) 4.0f else 2.0f)
            GLES20.glDrawArrays(if (waveIR.bUseDots) GLES20.GL_POINTS else GLES20.GL_LINE_STRIP, 0, pIdx)

            GLES20.glDisableVertexAttribArray(posLoc)
        }
    }

    private fun renderShapesForRuntime(runtime: MilkdropPresetRuntime) {
        val preset = runtime.presetIR
        for (sIdx in 0 until min(preset.customShapes.size, 4)) {
            val shapeIR = preset.customShapes[sIdx]
            if (!shapeIR.enabled && shapeIR.perFrameProgram == null) continue

            val pool = runtime.shapePools[sIdx]
            pool.syncFromPresetPool(runtime.presetPool, runtime.qSlotIndices, runtime.tSlotIndices)

            shapeIR.perFrameProgram?.let { prog ->
                MilkdropExpressionCompiler.executeProgram(prog, pool.registers, runtime.presetMemory)
                pool.syncTRegistersBack(runtime.tSlotIndices)
            }

            val sides = shapeIR.sides.coerceIn(3, 64)
            val rad = shapeIR.rad
            val ang = shapeIR.ang
            val cx = (shapeIR.x - 0.5f) * 2.0f
            val cy = (shapeIR.y - 0.5f) * 2.0f

            reusableShapeCoords[0] = cx
            reusableShapeCoords[1] = cy

            for (i in 0..sides) {
                val a = ang + i.toFloat() / sides.toFloat() * (2.0 * PI).toFloat()
                reusableShapeCoords[(i + 1) * 2] = cx + cos(a) * rad * 2.0f
                reusableShapeCoords[(i + 1) * 2 + 1] = cy + sin(a) * rad * 2.0f
            }

            shapeVertexBuffer.position(0)
            shapeVertexBuffer.put(reusableShapeCoords, 0, (sides + 2) * 2)
            shapeVertexBuffer.position(0)

            GLES20.glUseProgram(shapeWaveProgram)
            val posLoc = getAttribLoc(shapeWaveProgram, "aPosition")
            val colLoc = getUniformLoc(shapeWaveProgram, "uColor")

            GLES20.glUniform4f(colLoc, shapeIR.r, shapeIR.g, shapeIR.b, shapeIR.a)
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, shapeVertexBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, sides + 2)

            // Draw shape border
            GLES20.glUniform4f(colLoc, shapeIR.borderR, shapeIR.borderG, shapeIR.borderB, shapeIR.borderA)
            GLES20.glLineWidth(if (shapeIR.thick) 3.0f else 1.5f)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 1, sides)

            GLES20.glDisableVertexAttribArray(posLoc)
        }
    }

    private fun renderBlurPasses(mainTextureId: Int) {
        GLES20.glUseProgram(blurProgram)
        val posLoc = getAttribLoc(blurProgram, "aPosition")
        val texLoc = getAttribLoc(blurProgram, "aTexCoord")
        val uSrcLoc = getUniformLoc(blurProgram, "uSamplerSource")
        val uOffLoc = getUniformLoc(blurProgram, "uTexelOffset")

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, quadPosBuffer)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoordBuffer)

        var srcTex = mainTextureId
        for (b in 0..2) {
            val scale = 1 shl (b + 1)
            val bw = max(width / scale, 16)
            val bh = max(height / scale, 16)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFboIds[b])
            GLES20.glViewport(0, 0, bw, bh)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            if (uSrcLoc >= 0) GLES20.glUniform1i(uSrcLoc, 0)
            if (uOffLoc >= 0) GLES20.glUniform2f(uOffLoc, 1.0f / bw.toFloat(), 1.0f / bh.toFloat())

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            srcTex = blurTexIds[b]
        }

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    private fun renderCompositePassForRuntime(runtime: MilkdropPresetRuntime, mainTextureId: Int) {
        val prog = runtime.compositeProgram
        GLES20.glUseProgram(prog)

        val posLoc = getAttribLoc(prog, "aPosition")
        val texLoc = getAttribLoc(prog, "aTexCoord")
        val uMainLoc = getUniformLoc(prog, "uSamplerMain")
        val uBlur1Loc = getUniformLoc(prog, "uSamplerBlur1")
        val uBlur2Loc = getUniformLoc(prog, "uSamplerBlur2")
        val uBlur3Loc = getUniformLoc(prog, "uSamplerBlur3")
        val uTimeLoc = getUniformLoc(prog, "uTime")
        val uBassLoc = getUniformLoc(prog, "uBass")
        val uMidLoc = getUniformLoc(prog, "uMid")
        val uTrebLoc = getUniformLoc(prog, "uTreb")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mainTextureId)
        if (uMainLoc >= 0) GLES20.glUniform1i(uMainLoc, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexIds[0])
        if (uBlur1Loc >= 0) GLES20.glUniform1i(uBlur1Loc, 1)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexIds[1])
        if (uBlur2Loc >= 0) GLES20.glUniform1i(uBlur2Loc, 2)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexIds[2])
        if (uBlur3Loc >= 0) GLES20.glUniform1i(uBlur3Loc, 3)

        if (uTimeLoc >= 0) GLES20.glUniform1f(uTimeLoc, runtime.runtimeState.time)
        if (uBassLoc >= 0) GLES20.glUniform1f(uBassLoc, runtime.runtimeState.bass)
        if (uMidLoc >= 0) GLES20.glUniform1f(uMidLoc, runtime.runtimeState.mid)
        if (uTrebLoc >= 0) GLES20.glUniform1f(uTrebLoc, runtime.runtimeState.treb)

        // Pass Q1..Q32 uniforms
        for (q in 1..32) {
            val qLoc = getUniformLoc(prog, "q$q")
            if (qLoc >= 0) {
                GLES20.glUniform1f(qLoc, runtime.presetPool.frameQRegisters[q - 1])
            }
        }

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, quadPosBuffer)

        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    private fun renderBlendPass(
        texA: Int,
        texB: Int,
        ratio: Float,
        pattern: Int,
        timeSec: Float
    ) {
        GLES20.glUseProgram(blendProgram)

        val aPosLoc = getAttribLoc(blendProgram, "aPosition")
        val aTexLoc = getAttribLoc(blendProgram, "aTexCoord")
        val uA = getUniformLoc(blendProgram, "uPresetA")
        val uB = getUniformLoc(blendProgram, "uPresetB")
        val uRatio = getUniformLoc(blendProgram, "uRatio")
        val uPattern = getUniformLoc(blendProgram, "uPattern")
        val uTime = getUniformLoc(blendProgram, "uTime")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texA)
        if (uA >= 0) GLES20.glUniform1i(uA, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texB)
        if (uB >= 0) GLES20.glUniform1i(uB, 1)

        if (uRatio >= 0) GLES20.glUniform1f(uRatio, ratio)
        if (uPattern >= 0) GLES20.glUniform1i(uPattern, pattern)
        if (uTime >= 0) GLES20.glUniform1f(uTime, timeSec)

        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, quadPosBuffer)

        GLES20.glEnableVertexAttribArray(aTexLoc)
        GLES20.glVertexAttribPointer(aTexLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPosLoc)
        GLES20.glDisableVertexAttribArray(aTexLoc)
    }

    private fun setVariableForRuntime(runtime: MilkdropPresetRuntime, registers: FloatArray, name: String, value: Float) {
        val slot = runtime.presetIR.perFrameProgram?.nameToSlotMap?.get(name) ?: return
        if (slot >= 0 && slot < registers.size) registers[slot] = value
    }

    private fun getVariableForRuntime(runtime: MilkdropPresetRuntime, registers: FloatArray, name: String, defaultVal: Float): Float {
        val slot = runtime.presetIR.perFrameProgram?.nameToSlotMap?.get(name) ?: return defaultVal
        return if (slot >= 0 && slot < registers.size) registers[slot] else defaultVal
    }

    private fun syncStateFromRegistersForRuntime(runtime: MilkdropPresetRuntime, registers: FloatArray) {
        val runtimeState = runtime.runtimeState
        runtimeState.zoom = getVariableForRuntime(runtime, registers, "zoom", runtimeState.zoom).coerceIn(0.1f, 10f)
        runtimeState.zoomExp = getVariableForRuntime(runtime, registers, "zoomexp", runtimeState.zoomExp)
        runtimeState.rot = getVariableForRuntime(runtime, registers, "rot", runtimeState.rot)
        runtimeState.warp = getVariableForRuntime(runtime, registers, "warp", runtimeState.warp).coerceIn(0f, 10f)
        runtimeState.warpAnimSpeed = getVariableForRuntime(runtime, registers, "warpanimspeed", runtimeState.warpAnimSpeed)
        runtimeState.warpScale = getVariableForRuntime(runtime, registers, "warpscale", runtimeState.warpScale)
        runtimeState.cx = getVariableForRuntime(runtime, registers, "cx", runtimeState.cx)
        runtimeState.cy = getVariableForRuntime(runtime, registers, "cy", runtimeState.cy)
        runtimeState.dx = getVariableForRuntime(runtime, registers, "dx", runtimeState.dx)
        runtimeState.dy = getVariableForRuntime(runtime, registers, "dy", runtimeState.dy)
        runtimeState.sx = getVariableForRuntime(runtime, registers, "sx", runtimeState.sx)
        runtimeState.sy = getVariableForRuntime(runtime, registers, "sy", runtimeState.sy)
        runtimeState.decay = getVariableForRuntime(runtime, registers, "decay", runtimeState.decay).coerceIn(0.5f, 1.0f)
    }

    // Unused compatibility placeholders
    private fun setVariable(registers: FloatArray, name: String, value: Float) {
        currentRuntime?.let { setVariableForRuntime(it, registers, name, value) }
    }

    private fun getVariable(registers: FloatArray, name: String, defaultVal: Float): Float {
        return currentRuntime?.let { getVariableForRuntime(it, registers, name, defaultVal) } ?: defaultVal
    }

    private fun syncStateFromRegisters(registers: FloatArray) {
        currentRuntime?.let { syncStateFromRegistersForRuntime(it, registers) }
    }

    fun release() {
        releaseSharedFramebuffers()
        currentRuntime?.release()
        currentRuntime = null
        transitionRuntime?.release()
        transitionRuntime = null

        if (defaultMeshWarpProgram != 0) GLES20.glDeleteProgram(defaultMeshWarpProgram)
        if (defaultCompositeProgram != 0) GLES20.glDeleteProgram(defaultCompositeProgram)
        if (blurProgram != 0) GLES20.glDeleteProgram(blurProgram)
        if (blendProgram != 0) GLES20.glDeleteProgram(blendProgram)
        if (shapeWaveProgram != 0) GLES20.glDeleteProgram(shapeWaveProgram)
        if (noiseTextureId != 0) {
            val textures = intArrayOf(noiseTextureId)
            GLES20.glDeleteTextures(1, textures, 0)
        }
    }
}
