package com.anuj.obsidian.ui.visualizer

/**
 * Variable scope in the MilkDrop execution pipeline.
 */
enum class MilkdropVariableScope {
    GLOBAL,
    PER_FRAME,
    PER_VERTEX,
    WAVE_FRAME,
    WAVE_POINT,
    SHAPE_FRAME,
    SHADER_UNIFORM
}

enum class MilkdropVariableType {
    FLOAT,
    INT,
    BOOLEAN
}

/**
 * Metadata descriptor for a standard MilkDrop variable.
 */
data class MilkdropVariableDescriptor(
    val name: String,
    val readableScopes: Set<MilkdropVariableScope>,
    val writableScopes: Set<MilkdropVariableScope>,
    val type: MilkdropVariableType,
    val defaultValue: Float,
    val minVal: Float = -Float.MAX_VALUE,
    val maxVal: Float = Float.MAX_VALUE,
    val isShaderVisible: Boolean = false
)

/**
 * Central Variable Registry for MilkDrop specification parity.
 */
object MilkdropVariableTable {

    private val registry = HashMap<String, MilkdropVariableDescriptor>()

    init {
        // Global / Frame timing & audio telemetry
        register("time", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 0f, isShaderVisible = true)
        register("frame", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 0f, isShaderVisible = true)
        register("fps", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 60f, isShaderVisible = true)
        register("progress", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 0f, isShaderVisible = true)

        register("bass", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 1f, isShaderVisible = true)
        register("mid", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 1f, isShaderVisible = true)
        register("treb", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 1f, isShaderVisible = true)
        register("bass_att", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 1f, isShaderVisible = true)
        register("mid_att", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 1f, isShaderVisible = true)
        register("treb_att", setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), emptySet(), defaultValue = 1f, isShaderVisible = true)

        // Camera / Warp variables (writable in PER_FRAME and PER_VERTEX)
        register("zoom", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 1.0f, minVal = 0.01f, maxVal = 100.0f)
        register("zoomexp", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 1.0f)
        register("rot", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 0.0f)
        register("warp", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 0.0f, minVal = 0.0f, maxVal = 100.0f)
        register("warpanimspeed", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f)
        register("warpscale", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f)
        register("cx", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 0.5f)
        register("cy", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 0.5f)
        register("dx", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 0.0f)
        register("dy", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 0.0f)
        register("sx", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 1.0f)
        register("sy", allScopes(), setOf(MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.PER_VERTEX), defaultValue = 1.0f)
        register("decay", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.96f, minVal = 0.0f, maxVal = 1.0f)

        // Waveform parameters
        register("wave_r", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f, minVal = 0.0f, maxVal = 1.0f)
        register("wave_g", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f, minVal = 0.0f, maxVal = 1.0f)
        register("wave_b", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f, minVal = 0.0f, maxVal = 1.0f)
        register("wave_a", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.9f, minVal = 0.0f, maxVal = 1.0f)
        register("wave_x", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.5f)
        register("wave_y", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.5f)
        register("wave_mode", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0f, type = MilkdropVariableType.INT)
        register("wave_scale", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f, minVal = 0.01f, maxVal = 100.0f)
        register("wave_mystery", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f)

        // Echoes & Borders
        register("echo_zoom", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f)
        register("echo_alpha", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 1.0f)
        register("echo_orient", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, type = MilkdropVariableType.INT)

        register("ob_size", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 0.5f)
        register("ob_r", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 1.0f)
        register("ob_g", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 1.0f)
        register("ob_b", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 1.0f)
        register("ob_a", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 1.0f)

        register("ib_size", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 0.5f)
        register("ib_r", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 1.0f)
        register("ib_g", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 1.0f)
        register("ib_b", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 1.0f)
        register("ib_a", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f, minVal = 0.0f, maxVal = 1.0f)

        // Motion vectors
        register("mv_x", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 12f, type = MilkdropVariableType.INT)
        register("mv_y", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 9f, type = MilkdropVariableType.INT)
        register("mv_dx", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0f)
        register("mv_dy", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0f)
        register("mv_l", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.9f)
        register("mv_r", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f)
        register("mv_g", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f)
        register("mv_b", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 1.0f)
        register("mv_a", allScopes(), setOf(MilkdropVariableScope.PER_FRAME), defaultValue = 0.0f)

        // Per-vertex coordinates (read-only in vertex stage)
        register("x", setOf(MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0.5f)
        register("y", setOf(MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0.5f)
        register("rad", setOf(MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0.0f)
        register("ang", setOf(MilkdropVariableScope.PER_VERTEX, MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0.0f)

        // Aspect and mesh geometry variables
        register("aspectx", allScopes(), emptySet(), defaultValue = 1.0f)
        register("aspecty", allScopes(), emptySet(), defaultValue = 1.0f)
        register("meshx", allScopes(), emptySet(), defaultValue = 32.0f)
        register("meshy", allScopes(), emptySet(), defaultValue = 24.0f)

        // Wave per-point audio samples
        register("sample", setOf(MilkdropVariableScope.WAVE_POINT), setOf(MilkdropVariableScope.WAVE_POINT), defaultValue = 0f)
        register("value1", setOf(MilkdropVariableScope.WAVE_POINT), emptySet(), defaultValue = 0f)
        register("value2", setOf(MilkdropVariableScope.WAVE_POINT), emptySet(), defaultValue = 0f)

        // Shape attributes
        register("sides", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 4f, type = MilkdropVariableType.INT)
        register("additive", setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_FRAME), defaultValue = 0f)
        register("thick", setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_FRAME), defaultValue = 1f)
        register("textured", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0f)
        register("tex_zoom", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 1f)
        register("tex_ang", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0f)
        register("r", setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.WAVE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.WAVE_FRAME), defaultValue = 1f)
        register("g", setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.WAVE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.WAVE_FRAME), defaultValue = 1f)
        register("b", setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.WAVE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.WAVE_FRAME), defaultValue = 1f)
        register("a", setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.WAVE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_POINT, MilkdropVariableScope.WAVE_FRAME), defaultValue = 1f)
        register("r2", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0f)
        register("g2", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0f)
        register("b2", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0f)
        register("a2", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 0f)
        register("border_r", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 1f)
        register("border_g", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 1f)
        register("border_b", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 1f)
        register("border_a", setOf(MilkdropVariableScope.SHAPE_FRAME), setOf(MilkdropVariableScope.SHAPE_FRAME), defaultValue = 1f)

        // Persistent Q-variables (q1..q32)
        for (q in 1..32) {
            register("q$q", allScopes(), setOf(MilkdropVariableScope.GLOBAL, MilkdropVariableScope.PER_FRAME, MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_FRAME), defaultValue = 0.0f, isShaderVisible = true)
        }

        // T-variables (t1..t8) for shapes and waves
        for (t in 1..8) {
            register("t$t", setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT), setOf(MilkdropVariableScope.SHAPE_FRAME, MilkdropVariableScope.WAVE_FRAME, MilkdropVariableScope.WAVE_POINT), defaultValue = 0.0f)
        }
    }

    private fun allScopes(): Set<MilkdropVariableScope> = MilkdropVariableScope.values().toSet()

    private fun register(
        name: String,
        readable: Set<MilkdropVariableScope>,
        writable: Set<MilkdropVariableScope>,
        defaultValue: Float = 0f,
        type: MilkdropVariableType = MilkdropVariableType.FLOAT,
        minVal: Float = -Float.MAX_VALUE,
        maxVal: Float = Float.MAX_VALUE,
        isShaderVisible: Boolean = false
    ) {
        val lower = name.lowercase().trim()
        registry[lower] = MilkdropVariableDescriptor(
            name = lower,
            readableScopes = readable,
            writableScopes = writable,
            type = type,
            defaultValue = defaultValue,
            minVal = minVal,
            maxVal = maxVal,
            isShaderVisible = isShaderVisible
        )
    }

    fun getDescriptor(name: String): MilkdropVariableDescriptor? = registry[name.lowercase().trim()]

    fun isRegistered(name: String): Boolean = registry.containsKey(name.lowercase().trim())
}
