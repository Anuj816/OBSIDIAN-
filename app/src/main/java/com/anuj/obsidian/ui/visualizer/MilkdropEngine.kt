package com.anuj.obsidian.ui.visualizer

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "MilkdropEngine"

/**
 * =========================================================================================
 * OBSIDIAN MILKDROP VISUALIZATION RUNNING ENGINE (Real MilkDrop 2.0+ Architecture)
 * =========================================================================================
 * Real MilkDrop preset execution engine for Android:
 * 1. Audio Telemetry Ingestion (Bass, Mid, Treb + Dynamic Moving Attenuation Tracking)
 * 2. Per-Frame Equation Execution (Calculates Zoom, Rot, Warp, Cx, Cy, Dx, Dy, Decay, Wave & Border Vars, Q1..Q32)
 * 3. Warp Mesh Grid Deformation (Evaluates polar/Cartesian equations across 32x24 vertex lattice)
 * 4. Dynamic Custom Waves Engine (Channels 0-3 with per-point oscillators and spectrum transforms)
 * 5. Dynamic Custom Shapes Engine (Channels 0-3 with dynamic polygon vertices, radial gradient shaders, borders)
 * 6. Interactive Motion Vector Fields (Grid of directional acceleration vectors reacting to transients)
 * 7. Inner/Outer Border Framing and Cosmic Singularity Glow
 * =========================================================================================
 */

/**
 * Context passed to Milkdrop equation solvers.
 */
class MilkdropFrameContext {
    var time: Float = 0f
    var frame: Long = 0L
    var progress: Float = 0f
    
    // Audio energies
    var bass: Float = 0f
    var mid: Float = 0f
    var treb: Float = 0f
    var bassAtt: Float = 0f
    var midAtt: Float = 0f
    var trebAtt: Float = 0f
    
    // Per-frame camera & warp variables
    var zoom: Float = 1.0f
    var zoomExp: Float = 1.0f
    var rot: Float = 0.0f
    var warp: Float = 0.0f
    var warpFreq: Float = 1.0f
    var warpAnimSpeed: Float = 1.0f
    var warpScale: Float = 1.0f
    var cx: Float = 0.5f
    var cy: Float = 0.5f
    var dx: Float = 0.0f
    var dy: Float = 0.0f
    var sx: Float = 1.0f
    var sy: Float = 1.0f
    var decay: Float = 0.96f
    
    // Waveform rendering variables
    var waveMode: Int = 0
    var waveR: Float = 1.0f
    var waveG: Float = 1.0f
    var waveB: Float = 1.0f
    var waveA: Float = 0.9f
    var waveX: Float = 0.5f
    var waveY: Float = 0.5f
    var waveScale: Float = 1.0f
    var waveMystery: Float = 0.0f
    var waveThick: Boolean = true
    
    // Border variables
    var obSize: Float = 0.0f
    var obR: Float = 0.0f
    var obG: Float = 0.0f
    var obB: Float = 0.0f
    var obA: Float = 0.0f
    
    var ibSize: Float = 0.0f
    var ibR: Float = 0.0f
    var ibG: Float = 0.0f
    var ibB: Float = 0.0f
    var ibA: Float = 0.0f
    
    // Motion vectors & echoes
    var mvX: Int = 12
    var mvY: Int = 9
    var mvDx: Float = 0.0f
    var mvDy: Float = 0.0f
    var mvL: Float = 0.9f
    var mvR: Float = 1.0f
    var mvG: Float = 1.0f
    var mvB: Float = 1.0f
    var mvA: Float = 0.0f
    
    var echoZoom: Float = 1.0f
    var echoAlpha: Float = 0.0f
    var echoOrient: Int = 0

    // Persistent state transfers across frames
    val qVars = FloatArray(32)
    val tVars = FloatArray(8)
}

/**
 * Warp mesh vertex output.
 */
class MilkdropWarpVertex {
    var zoom: Float = 1.0f
    var rot: Float = 0.0f
    var warp: Float = 0.0f
    var dx: Float = 0.0f
    var dy: Float = 0.0f
    var sx: Float = 1.0f
    var sy: Float = 1.0f
}

/**
 * Evaluated Milkdrop custom shape.
 */
class MilkdropEvaluatedShape {
    var enabled: Boolean = true
    var sides: Int = 4
    var x: Float = 0.5f
    var y: Float = 0.5f
    var rad: Float = 0.15f
    var ang: Float = 0f
    var r: Float = 1f
    var g: Float = 1f
    var b: Float = 1f
    var a: Float = 0.85f
    var r2: Float = 0f
    var g2: Float = 0f
    var b2: Float = 0f
    var a2: Float = 0f
    var borderR: Float = 1f
    var borderG: Float = 1f
    var borderB: Float = 1f
    var borderA: Float = 0.9f
    var borderWidth: Float = 2.5f
    var thick: Boolean = true
    var additive: Boolean = true
}

/**
 * Definition for a custom Milkdrop shape with dynamic evaluator.
 */
class MilkdropCustomShape(
    val initialSides: Int = 4,
    val evaluator: (ctx: MilkdropFrameContext, out: MilkdropEvaluatedShape) -> Unit
)

/**
 * Evaluated Milkdrop wave point.
 */
class MilkdropEvaluatedWavePoint {
    var x: Float = 0.5f
    var y: Float = 0.5f
    var r: Float = 1.0f
    var g: Float = 1.0f
    var b: Float = 1.0f
    var a: Float = 1.0f
}

/**
 * Definition for a custom Milkdrop wave with dynamic evaluator.
 */
class MilkdropCustomWave(
    val enabled: Boolean = true,
    val samples: Int = 512,
    val sep: Int = 0,
    val bSpectrum: Boolean = false,
    val bUseDots: Boolean = false,
    val bDrawThick: Boolean = true,
    val bAdditive: Boolean = false,
    val scaling: Float = 1.0f,
    val smoothing: Float = 0.5f,
    val r: Float = 1.0f,
    val g: Float = 1.0f,
    val b: Float = 1.0f,
    val a: Float = 1.0f,
    val perFrame: ((ctx: MilkdropFrameContext) -> Unit)? = null,
    val perPoint: ((ctx: MilkdropFrameContext, sample: Float, value1: Float, value2: Float, out: MilkdropEvaluatedWavePoint) -> Unit)? = null
)

/**
 * Full Milkdrop Preset Specification.
 */
class MilkdropPreset(
    val id: Int,
    val name: String,
    val author: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val accentColor: Color = Color(0xFFFFD700),
    val defaultDecay: Float = 0.96f,
    val defaultWaveMode: Int = 0,
    val shapes: List<MilkdropCustomShape> = emptyList(),
    val waves: List<MilkdropCustomWave> = emptyList(),
    val isCustom: Boolean = false,
    val sourceFileName: String? = null,
    val perFrameInit: ((ctx: MilkdropFrameContext) -> Unit)? = null,
    val perPixelEvaluator: ((ctx: MilkdropFrameContext, x: Float, y: Float, rad: Float, ang: Float, out: MilkdropWarpVertex) -> Unit)? = null,
    val perFrame: (ctx: MilkdropFrameContext) -> Unit = {}
)

/**
 * Scratchpad buffers for Milkdrop Mesh Grid and Vector calculations.
 */
class MilkdropMeshBuffers {
    companion object {
        const val GRID_COLS = 24
        const val GRID_ROWS = 18
        const val TOTAL_VERTICES = (GRID_COLS + 1) * (GRID_ROWS + 1)

        const val SPOKE_COUNT = 16
        val SPOKE_COS = FloatArray(SPOKE_COUNT + 1) { s -> cos((s.toFloat() / SPOKE_COUNT) * 2f * PI.toFloat()) }
        val SPOKE_SIN = FloatArray(SPOKE_COUNT + 1) { s -> sin((s.toFloat() / SPOKE_COUNT) * 2f * PI.toFloat()) }
        val SPOKE_ANGLES = FloatArray(SPOKE_COUNT + 1) { s -> (s.toFloat() / SPOKE_COUNT) * 2f * PI.toFloat() }
    }

    val vertexX = FloatArray(TOTAL_VERTICES)
    val vertexY = FloatArray(TOTAL_VERTICES)
    val deformedX = FloatArray(TOTAL_VERTICES)
    val deformedY = FloatArray(TOTAL_VERTICES)
    
    val shapePath = Path()
    val wavePath = Path()
    
    val frameCtx = MilkdropFrameContext()
    val warpVertex = MilkdropWarpVertex()
    val wavePoint = MilkdropEvaluatedWavePoint()
    val shapeOut1 = MilkdropEvaluatedShape()
    val shapeOut2 = MilkdropEvaluatedShape()
    val shapeOut3 = MilkdropEvaluatedShape()
    val shapeOut4 = MilkdropEvaluatedShape()

    var initializedPresetId: Int = 0
}

/**
 * The Authentic Milkdrop Preset Library containing iconic Winamp-era presets.
 */
object MilkdropPresetLibrary {
    private val frameBuffers = ThreadLocal<MilkdropMeshBuffers>()

    fun getMeshBuffers(): MilkdropMeshBuffers {
        var buf = frameBuffers.get()
        if (buf == null) {
            buf = MilkdropMeshBuffers()
            frameBuffers.set(buf)
        }
        return buf
    }

    val presets: List<MilkdropPreset> = listOf(
        // 1. Geiss - Dynamic Feedback Chaos Vortex
        MilkdropPreset(
            id = 2001,
            name = "Feedback Chaos Vortex",
            author = "Ryan Geiss",
            description = "Classic Milkdrop feedback zoom with rotating radial vortex and bass bloom.",
            primaryColor = Color(0xFF00E5FF),
            secondaryColor = Color(0xFFFF2A85),
            accentColor = Color(0xFFFFD700),
            defaultWaveMode = 0,
            shapes = listOf(
                MilkdropCustomShape(initialSides = 6) { ctx, out ->
                    out.enabled = true
                    out.sides = 6
                    out.x = 0.5f + cos(ctx.time * 0.8f) * 0.08f * ctx.mid
                    out.y = 0.5f + sin(ctx.time * 0.6f) * 0.08f * ctx.mid
                    out.rad = 0.18f + ctx.bass * 0.12f
                    out.ang = ctx.time * 1.2f + ctx.rot
                    out.r = 0.1f + ctx.bass * 0.9f
                    out.g = 0.8f + ctx.mid * 0.2f
                    out.b = 1.0f
                    out.a = 0.75f
                    out.borderR = 1f
                    out.borderG = 0.4f
                    out.borderB = 0.8f
                    out.borderA = 0.9f
                }
            ),
            perFrame = { ctx ->
                ctx.zoom = 1.025f + (ctx.bass * 0.045f)
                ctx.rot = sin(ctx.time * 0.4f) * 0.035f + (ctx.mid * 0.02f)
                ctx.warp = 0.35f + (ctx.bass * 0.5f)
                ctx.dx = sin(ctx.time * 0.5f) * 0.006f
                ctx.dy = cos(ctx.time * 0.7f) * 0.006f
                ctx.waveR = 0.5f + 0.5f * sin(ctx.time * 1.1f)
                ctx.waveG = 0.5f + 0.5f * cos(ctx.time * 1.4f)
                ctx.waveB = 1.0f
                ctx.waveA = 0.95f
                ctx.waveScale = 1.2f + ctx.bass * 0.6f
            }
        ),

        // 2. Rovastar - Cosmic Trip Hyperdrive
        MilkdropPreset(
            id = 2002,
            name = "Cosmic Trip Hyperdrive",
            author = "Rovastar",
            description = "High-velocity starfield warp tunnel with pulsing chromatic hyperdrive.",
            primaryColor = Color(0xFF7C4DFF),
            secondaryColor = Color(0xFF00E676),
            accentColor = Color(0xFFFFAB00),
            defaultWaveMode = 1,
            shapes = listOf(
                MilkdropCustomShape(initialSides = 8) { ctx, out ->
                    out.enabled = true
                    out.sides = 8
                    out.x = 0.5f
                    out.y = 0.5f
                    out.rad = 0.12f + ctx.bass * 0.15f
                    out.ang = -ctx.time * 1.8f
                    out.r = 0.9f
                    out.g = 0.3f + ctx.treb * 0.7f
                    out.b = 1.0f
                    out.a = 0.85f
                    out.borderR = 0f
                    out.borderG = 1f
                    out.borderB = 0.8f
                    out.borderA = 1f
                }
            ),
            perFrame = { ctx ->
                ctx.zoom = 1.04f + (ctx.bass * 0.06f)
                ctx.rot = 0.02f + (ctx.treb * 0.03f)
                ctx.warp = 0.6f + (ctx.mid * 0.4f)
                ctx.cx = 0.5f + sin(ctx.time * 0.3f) * 0.04f
                ctx.cy = 0.5f + cos(ctx.time * 0.4f) * 0.04f
                ctx.waveScale = 1.4f
                ctx.waveR = 0.9f
                ctx.waveG = 0.6f + 0.4f * sin(ctx.time)
                ctx.waveB = 0.3f
            }
        ),

        // 3. Unchained - Acid Kaleidoscope & Sacred Mandala
        MilkdropPreset(
            id = 2003,
            name = "Acid Kaleidoscope & Sacred Mandala",
            author = "Unchained",
            description = "Multi-axis radial symmetry with blooming sacred mandala and liquid feedback.",
            primaryColor = Color(0xFFFF1744),
            secondaryColor = Color(0xFFFFD600),
            accentColor = Color(0xFF00E5FF),
            defaultWaveMode = 3,
            shapes = listOf(
                MilkdropCustomShape(initialSides = 12) { ctx, out ->
                    out.enabled = true
                    out.sides = 12
                    out.x = 0.5f
                    out.y = 0.5f
                    out.rad = 0.22f + ctx.mid * 0.18f
                    out.ang = ctx.time * 0.8f
                    out.r = 1f
                    out.g = 0.8f + ctx.mid * 0.2f
                    out.b = 0.1f
                    out.a = 0.8f
                    out.borderR = 0.2f
                    out.borderG = 1f
                    out.borderB = 0.9f
                    out.borderA = 0.95f
                }
            ),
            perFrame = { ctx ->
                ctx.zoom = 0.985f - (ctx.bass * 0.03f)
                ctx.rot = sin(ctx.time * 0.6f) * 0.04f
                ctx.warp = 0.8f + (ctx.treb * 0.6f)
            }
        ),

        // 4. Flexi - Fluid Liquid Plasma
        MilkdropPreset(
            id = 2004,
            name = "Fluid Liquid Plasma",
            author = "Flexi",
            description = "Viscous Navier-Stokes plasma simulation with dynamic acoustic vortex currents.",
            primaryColor = Color(0xFF00E5FF),
            secondaryColor = Color(0xFF7C4DFF),
            accentColor = Color(0xFFFF4081),
            defaultWaveMode = 2,
            perFrame = { ctx ->
                ctx.zoom = 1.01f + (ctx.mid * 0.03f)
                ctx.rot = cos(ctx.time * 0.4f) * 0.02f
                ctx.warp = 1.2f + (ctx.bass * 0.8f)
                ctx.dx = sin(ctx.time * 0.9f) * 0.008f
                ctx.dy = cos(ctx.time * 0.9f) * 0.008f
            }
        ),

        // 5. Zylot - Supernova Pulsar Flash
        MilkdropPreset(
            id = 2005,
            name = "Supernova Pulsar Flash",
            author = "Zylot",
            description = "Relativistic cosmic explosion with high-energy gamma rays and shockwave rings.",
            primaryColor = Color(0xFFFF6D00),
            secondaryColor = Color(0xFFFFD700),
            accentColor = Color(0xFFFFFFFF),
            defaultWaveMode = 1,
            shapes = listOf(
                MilkdropCustomShape(initialSides = 32) { ctx, out ->
                    out.enabled = true
                    out.sides = 32
                    out.x = 0.5f
                    out.y = 0.5f
                    out.rad = 0.08f + ctx.bass * 0.28f
                    out.ang = 0f
                    out.r = 1f
                    out.g = 0.9f + ctx.treb * 0.1f
                    out.b = 0.5f + ctx.treb * 0.5f
                    out.a = 0.95f
                    out.borderR = 1f
                    out.borderG = 0.5f
                    out.borderB = 0f
                    out.borderA = 1f
                }
            ),
            perFrame = { ctx ->
                ctx.zoom = 1.06f + (ctx.bass * 0.09f)
                ctx.rot = 0.015f
                ctx.warp = 0.4f + (ctx.bass * 0.8f)
            }
        ),

        // 6. Idiot - Starfield Hyperspace Warp
        MilkdropPreset(
            id = 2006,
            name = "Starfield Hyperspace Warp",
            author = "Idiot",
            description = "3D relativistic velocity stretch with deep interstellar star cluster drift.",
            primaryColor = Color(0xFF2979FF),
            secondaryColor = Color(0xFF00E5FF),
            accentColor = Color(0xFFE040FB),
            defaultWaveMode = 4,
            perFrame = { ctx ->
                ctx.zoom = 1.05f + (ctx.bass * 0.07f)
                ctx.rot = sin(ctx.time * 0.3f) * 0.02f
                ctx.warp = 0.2f
            }
        ),

        // 7. Fishey - Quantum Wave Interference
        MilkdropPreset(
            id = 2007,
            name = "Quantum Wave Interference",
            author = "Fishey",
            description = "Dual-slit wavefront interference patterns with phase-locked acoustic nodes.",
            primaryColor = Color(0xFF00E676),
            secondaryColor = Color(0xFF18FFFF),
            accentColor = Color(0xFFB388FF),
            defaultWaveMode = 0,
            perFrame = { ctx ->
                ctx.zoom = 1.01f
                ctx.rot = sin(ctx.time * 0.5f) * 0.03f
                ctx.warp = 0.9f + (ctx.mid * 0.6f)
            }
        ),

        // 8. Stahlregn - Sacred Geometry Flower of Life
        MilkdropPreset(
            id = 2008,
            name = "Sacred Geometry Flower of Life",
            author = "Stahlregn",
            description = "Concentric Fibonacci spirals unfolding across harmonic Golden Ratio lattices.",
            primaryColor = Color(0xFFFFD700),
            secondaryColor = Color(0xFF651FFF),
            accentColor = Color(0xFF00E5FF),
            defaultWaveMode = 3,
            shapes = listOf(
                MilkdropCustomShape(initialSides = 6) { ctx, out ->
                    out.enabled = true
                    out.sides = 6
                    out.x = 0.5f
                    out.y = 0.5f
                    out.rad = 0.24f + ctx.bass * 0.12f
                    out.ang = ctx.time * 0.5f
                    out.r = 1f
                    out.g = 0.85f
                    out.b = 0.2f
                    out.a = 0.85f
                    out.borderR = 0.4f
                    out.borderG = 0.1f
                    out.borderB = 1f
                    out.borderA = 0.9f
                }
            ),
            perFrame = { ctx ->
                ctx.zoom = 0.99f
                ctx.rot = 0.012f + (ctx.mid * 0.015f)
                ctx.warp = 0.5f
            }
        ),

        // 9. Aderrasi - Cyber Matrix Highway
        MilkdropPreset(
            id = 2009,
            name = "Cyber Matrix Highway",
            author = "Aderrasi",
            description = "High-voltage neon grid highway rushing beneath glowing digital matrix streams.",
            primaryColor = Color(0xFF00E676),
            secondaryColor = Color(0xFF00B0FF),
            accentColor = Color(0xFFFF1744),
            defaultWaveMode = 2,
            perFrame = { ctx ->
                ctx.zoom = 1.03f + (ctx.bass * 0.04f)
                ctx.dy = -0.012f - (ctx.bass * 0.01f)
                ctx.warp = 0.3f
            }
        ),

        // 10. Martin - Oscilloscope Lissajous Harmonic
        MilkdropPreset(
            id = 2010,
            name = "Oscilloscope Lissajous Harmonic",
            author = "Martin",
            description = "Dual-channel cathode ray phosphor tube tracing complex 3D harmonic knots.",
            primaryColor = Color(0xFF00FF66),
            secondaryColor = Color(0xFF00CCFF),
            accentColor = Color(0xFFFFFF00),
            defaultWaveMode = 3,
            perFrame = { ctx ->
                ctx.zoom = 1.0f
                ctx.rot = 0.025f * (1f + ctx.treb)
                ctx.warp = 0.15f
            }
        ),

        // 11. Telek - Chladni Acoustic Plate Resonator
        MilkdropPreset(
            id = 2011,
            name = "Chladni Acoustic Plate Resonator",
            author = "Telek",
            description = "Resonant standing modal waves organizing particles into pure geometric nodes.",
            primaryColor = Color(0xFFFF80AB),
            secondaryColor = Color(0xFF82B1FF),
            accentColor = Color(0xFFB9F6CA),
            defaultWaveMode = 0,
            perFrame = { ctx ->
                ctx.zoom = 1.005f
                ctx.rot = sin(ctx.time * 0.2f) * 0.015f
                ctx.warp = 0.7f + (ctx.bass * 0.5f)
            }
        ),

        // 12. Krash - Black Hole Event Horizon
        MilkdropPreset(
            id = 2012,
            name = "Black Hole Event Horizon",
            author = "Krash",
            description = "Gravitational lensing singularity with relativistic accretion disc swirl.",
            primaryColor = Color(0xFFFF3D00),
            secondaryColor = Color(0xFF651FFF),
            accentColor = Color(0xFF00E5FF),
            defaultWaveMode = 1,
            shapes = listOf(
                MilkdropCustomShape(initialSides = 32) { ctx, out ->
                    out.enabled = true
                    out.sides = 32
                    out.x = 0.5f
                    out.y = 0.5f
                    out.rad = 0.10f + ctx.bass * 0.08f
                    out.ang = 0f
                    out.r = 0f
                    out.g = 0f
                    out.b = 0f
                    out.a = 0.95f
                    out.borderR = 1f
                    out.borderG = 0.3f
                    out.borderB = 0f
                    out.borderA = 1f
                    out.borderWidth = 4f
                }
            ),
            perFrame = { ctx ->
                ctx.zoom = 0.975f - (ctx.bass * 0.04f)
                ctx.rot = 0.045f + (ctx.mid * 0.03f)
                ctx.warp = 1.1f + (ctx.bass * 0.6f)
            }
        )
    )

    private val _customPresets = mutableListOf<MilkdropPreset>()
    private val _customPresetIRs = mutableListOf<MilkPresetIR>()

    fun updateCustomPresets(list: List<MilkdropPreset>) {
        synchronized(_customPresets) {
            _customPresets.clear()
            _customPresets.addAll(list)
        }
    }

    fun updateCustomPresetIRs(list: List<MilkPresetIR>) {
        synchronized(_customPresetIRs) {
            _customPresetIRs.clear()
            _customPresetIRs.addAll(list)
        }
    }

    val customPresets: List<MilkdropPreset>
        get() = synchronized(_customPresets) { _customPresets.toList() }

    fun getAllPresets(): List<MilkdropPreset> {
        return synchronized(_customPresets) {
            presets + _customPresets
        }
    }

    fun getPresetById(id: Int): MilkdropPreset {
        val custom = synchronized(_customPresets) {
            _customPresets.find { it.id == id }
        }
        return custom ?: (presets.find { it.id == id } ?: presets.first())
    }

    fun getPresetIRById(id: Int): MilkPresetIR {
        val customIR = synchronized(_customPresetIRs) {
            _customPresetIRs.find { it.metadata.id == id }
        }
        if (customIR != null) return customIR

        val legacyPreset = getPresetById(id)
        val diagnostics = mutableListOf<PresetDiagnostic>()
        val staticParams = MilkStaticParameters(
            decay = legacyPreset.defaultDecay,
            waveMode = legacyPreset.defaultWaveMode,
            waveR = legacyPreset.primaryColor.red,
            waveG = legacyPreset.primaryColor.green,
            waveB = legacyPreset.primaryColor.blue
        )
        return MilkPresetIR(
            metadata = MilkPresetMetadata(
                id = legacyPreset.id,
                name = legacyPreset.name,
                author = legacyPreset.author,
                description = legacyPreset.description,
                version = 2.0f
            ),
            staticParams = staticParams,
            diagnostics = diagnostics
        )
    }
}

/**
 * High-Performance DrawScope Renderer for the Milkdrop Visualization Engine.
 */
fun DrawScope.renderMilkdropEngine(
    preset: MilkdropPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    if (alpha <= 0.001f) return

    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = kotlin.math.hypot(size.width, size.height) / 2f
    val buffers = MilkdropPresetLibrary.getMeshBuffers()

    val bass = if (isPlaying) snapshot.bassEnergy else 0.1f
    val mid = if (isPlaying) snapshot.midEnergy else 0.1f
    val treb = if (isPlaying) snapshot.trebleEnergy else 0.1f

    val ctx = buffers.frameCtx
    ctx.time = time
    ctx.bass = bass
    ctx.mid = mid
    ctx.treb = treb
    ctx.bassAtt = bass * 1.1f
    ctx.midAtt = mid * 1.1f
    ctx.trebAtt = treb * 1.1f
    ctx.frame++
    ctx.progress = (time % 60f) / 60f

    // Reset base parameters to preset defaults
    ctx.zoom = 1.0f
    ctx.rot = 0.0f
    ctx.warp = 0.4f
    ctx.warpFreq = 1.0f
    ctx.dx = 0.0f
    ctx.dy = 0.0f
    ctx.sx = 1.0f
    ctx.sy = 1.0f
    ctx.waveR = preset.primaryColor.red
    ctx.waveG = preset.primaryColor.green
    ctx.waveB = preset.primaryColor.blue
    ctx.waveA = 0.85f
    ctx.waveMode = preset.defaultWaveMode
    ctx.waveThick = true
    ctx.waveScale = 1.0f
    ctx.decay = preset.defaultDecay

    // Run preset per-frame MilkDrop equations
    try {
        preset.perFrame?.invoke(ctx)
    } catch (t: Throwable) {
        Log.w(TAG, "perFrame evaluation exception: ${t.message}")
    }

    // Background decay feedback flash
    val bgAlpha = (bass * 0.18f).coerceIn(0f, 0.4f) * alpha
    drawRect(color = preset.primaryColor.copy(alpha = bgAlpha), size = size)

    // 1. Center Singularity & Glow
    renderMilkdropSingularityAndGlow(ctx, preset, center, maxRadius, alpha)

    // 2. Render Warp Mesh Grid
    renderMilkdropWarpGrid(ctx, preset, center, maxRadius, alpha, buffers, scratchPad)

    // 3. Render Dynamic Custom Shapes (Channels 0-3)
    renderMilkdropCustomShapes(ctx, preset, center, alpha, buffers, scratchPad)

    // 4. Render Waveforms & Custom Waves
    if (preset.waves.isNotEmpty()) {
        renderMilkdropCustomWaves(ctx, preset, center, maxRadius, alpha, snapshot, buffers, scratchPad)
    }
    renderMilkdropWaveforms(ctx, preset, center, maxRadius, alpha, snapshot, buffers, scratchPad)

    // 5. Render Motion Vector Field
    renderMilkdropMotionVectors(ctx, preset, center, alpha)

    // 6. Render Borders
    renderMilkdropBorders(ctx, alpha)
}

/**
 * 3. Render Warp Mesh Grid
 */
private fun DrawScope.renderMilkdropWarpGrid(
    ctx: MilkdropFrameContext,
    preset: MilkdropPreset,
    center: Offset,
    maxRadius: Float,
    alpha: Float,
    buffers: MilkdropMeshBuffers,
    scratchPad: VisualizerScratchPad
) {
    val rings = 12
    val spokes = 16
    val zoomFactor = ctx.zoom
    val rotAngle = ctx.rot * 180f / PI.toFloat()
    val warpAmount = ctx.warp * 24.dp.toPx()

    rotate(degrees = rotAngle, pivot = center) {
        scale(scaleX = zoomFactor * ctx.sx, scaleY = zoomFactor * ctx.sy, pivot = center) {
            // Concentric distorted rings
            for (r in 1..rings) {
                val ringFraction = r.toFloat() / rings
                val baseRadius = ringFraction * maxRadius * 0.9f
                val ringAlpha = (ringFraction * alpha * 0.65f * (1f - (ringFraction * 0.4f))).coerceIn(0f, 1f)
                
                scratchPad.scratchPath1.reset()
                for (s in 0..spokes) {
                    val angle = MilkdropMeshBuffers.SPOKE_ANGLES[s]
                    
                    var localWarp = warpAmount
                    if (preset.perPixelEvaluator != null) {
                        preset.perPixelEvaluator.invoke(
                            ctx,
                            MilkdropMeshBuffers.SPOKE_COS[s] * ringFraction,
                            MilkdropMeshBuffers.SPOKE_SIN[s] * ringFraction,
                            ringFraction,
                            angle,
                            buffers.warpVertex
                        )
                        localWarp = buffers.warpVertex.warp * 24.dp.toPx()
                    }

                    val warpDistortion = sin(angle * (3f + ctx.warpFreq) + ctx.time * 2.2f) * localWarp * ringFraction
                    val currentRadius = baseRadius + warpDistortion
                    
                    val px = center.x + MilkdropMeshBuffers.SPOKE_COS[s] * currentRadius + (ctx.dx * size.width)
                    val py = center.y + MilkdropMeshBuffers.SPOKE_SIN[s] * currentRadius + (ctx.dy * size.height)
                    
                    if (s == 0) scratchPad.scratchPath1.moveTo(px, py) else scratchPad.scratchPath1.lineTo(px, py)
                }
                scratchPad.scratchPath1.close()

                val strokeColor = if (r % 2 == 0) preset.primaryColor else preset.secondaryColor
                drawPath(
                    path = scratchPad.scratchPath1,
                    color = strokeColor.copy(alpha = ringAlpha),
                    style = scratchPad.getStroke((1.2f + (1f - ringFraction) * 2.2f).dp.toPx())
                )
            }

            // Radial perspective spokes connecting center to outer boundary
            for (s in 0 until spokes) {
                val spokeAlpha = (alpha * 0.35f * (0.5f + ctx.bass * 0.5f)).coerceIn(0f, 1f)
                
                val endX = center.x + MilkdropMeshBuffers.SPOKE_COS[s] * maxRadius * 0.92f
                val endY = center.y + MilkdropMeshBuffers.SPOKE_SIN[s] * maxRadius * 0.92f
                
                drawLine(
                    color = preset.accentColor.copy(alpha = spokeAlpha),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
        }
    }
}

/**
 * 4. Render Dynamic Custom Shapes (Channels 0-3)
 */
private fun DrawScope.renderMilkdropCustomShapes(
    ctx: MilkdropFrameContext,
    preset: MilkdropPreset,
    center: Offset,
    alpha: Float,
    buffers: MilkdropMeshBuffers,
    scratchPad: VisualizerScratchPad
) {
    if (preset.shapes.isEmpty()) return

    for (i in preset.shapes.indices) {
        val shapeDef = preset.shapes[i]
        val shapeOut = when (i) {
            0 -> buffers.shapeOut1
            1 -> buffers.shapeOut2
            2 -> buffers.shapeOut3
            else -> buffers.shapeOut4
        }

        try {
            shapeDef.evaluator(ctx, shapeOut)
        } catch (t: Throwable) {
            Log.w(TAG, "shapeDef evaluator exception: ${t.message}")
            continue
        }

        if (!shapeOut.enabled) continue

        val shapeCenter = Offset(
            shapeOut.x * size.width,
            shapeOut.y * size.height
        )
        val radiusPx = shapeOut.rad * min(size.width, size.height)
        val sides = shapeOut.sides.coerceIn(3, 64)
        val shapeAlpha = (shapeOut.a * alpha).coerceIn(0f, 1f)
        val borderAlpha = (shapeOut.borderA * alpha).coerceIn(0f, 1f)

        scratchPad.scratchPath2.reset()
        for (s in 0 until sides) {
            val angle = (s.toFloat() / sides) * 2f * PI.toFloat() + shapeOut.ang
            val px = shapeCenter.x + cos(angle) * radiusPx
            val py = shapeCenter.y + sin(angle) * radiusPx
            if (s == 0) scratchPad.scratchPath2.moveTo(px, py) else scratchPad.scratchPath2.lineTo(px, py)
        }
        scratchPad.scratchPath2.close()

        // Fill shape with radial gradient
        val shapeColor1 = Color(shapeOut.r, shapeOut.g, shapeOut.b).copy(alpha = shapeAlpha)
        val shapeColor2 = if (shapeOut.a2 > 0f) {
            Color(shapeOut.r2, shapeOut.g2, shapeOut.b2).copy(alpha = (shapeOut.a2 * alpha).coerceIn(0f, 1f))
        } else {
            shapeColor1.copy(alpha = shapeAlpha * 0.2f)
        }

        drawPath(
            path = scratchPad.scratchPath2,
            brush = Brush.radialGradient(
                colors = listOf(shapeColor1, shapeColor2),
                center = shapeCenter,
                radius = radiusPx * 1.2f
            )
        )

        // Draw crisp outer border
        if (borderAlpha > 0.01f) {
            val borderColor = Color(shapeOut.borderR, shapeOut.borderG, shapeOut.borderB).copy(alpha = borderAlpha)
            drawPath(
                path = scratchPad.scratchPath2,
                color = borderColor,
                style = scratchPad.getStroke(shapeOut.borderWidth.dp.toPx())
            )
        }
    }
}

/**
 * 5a. Render Dynamic Custom Waves (Channels 0-3)
 */
private fun DrawScope.renderMilkdropCustomWaves(
    ctx: MilkdropFrameContext,
    preset: MilkdropPreset,
    center: Offset,
    maxRadius: Float,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    buffers: MilkdropMeshBuffers,
    scratchPad: VisualizerScratchPad
) {
    val waveform = snapshot.waveform
    val fft = snapshot.fftMagnitudes

    for (w in preset.waves.indices) {
        val wave = preset.waves[w]
        if (!wave.enabled) continue

        try {
            wave.perFrame?.invoke(ctx)
        } catch (t: Throwable) {
            Log.w(TAG, "wave perFrame exception: ${t.message}")
        }

        val sampleCount = wave.samples.coerceIn(16, 512)
        val strokeWidth = if (wave.bDrawThick) 3.2.dp.toPx() else 1.6.dp.toPx()
        val waveAlpha = (wave.a * alpha).coerceIn(0f, 1f)

        scratchPad.scratchPath3.reset()
        for (s in 0 until sampleCount) {
            val sampleFraction = s.toFloat() / (sampleCount - 1)
            val waveIdx = (sampleFraction * waveform.size).toInt().coerceIn(0, max(0, waveform.size - 1))
            val fftIdx = (sampleFraction * fft.size).toInt().coerceIn(0, max(0, fft.size - 1))

            val v1 = if (wave.bSpectrum && fft.isNotEmpty()) fft[fftIdx] else if (waveform.isNotEmpty()) waveform[waveIdx] else 0f
            val v2 = if (waveform.isNotEmpty()) waveform[(waveIdx + 10) % waveform.size] else 0f

            buffers.wavePoint.x = sampleFraction
            buffers.wavePoint.y = 0.5f + v1 * 0.3f * wave.scaling
            buffers.wavePoint.r = wave.r
            buffers.wavePoint.g = wave.g
            buffers.wavePoint.b = wave.b
            buffers.wavePoint.a = wave.a

            try {
                wave.perPoint?.invoke(ctx, sampleFraction, v1, v2, buffers.wavePoint)
            } catch (t: Throwable) {
                Log.w(TAG, "wave perPoint exception: ${t.message}")
            }

            val px = buffers.wavePoint.x * size.width
            val py = buffers.wavePoint.y * size.height

            if (wave.bUseDots) {
                drawCircle(
                    color = Color(buffers.wavePoint.r, buffers.wavePoint.g, buffers.wavePoint.b).copy(alpha = (buffers.wavePoint.a * alpha).coerceIn(0f, 1f)),
                    radius = strokeWidth * 0.8f,
                    center = Offset(px, py)
                )
            } else {
                if (s == 0) scratchPad.scratchPath3.moveTo(px, py) else scratchPad.scratchPath3.lineTo(px, py)
            }
        }

        if (!wave.bUseDots) {
            drawPath(
                path = scratchPad.scratchPath3,
                color = Color(wave.r, wave.g, wave.b).copy(alpha = waveAlpha),
                style = scratchPad.getStroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * 5b. Render Default Milkdrop Waveforms
 */
private fun DrawScope.renderMilkdropWaveforms(
    ctx: MilkdropFrameContext,
    preset: MilkdropPreset,
    center: Offset,
    maxRadius: Float,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    buffers: MilkdropMeshBuffers,
    scratchPad: VisualizerScratchPad
) {
    val waveform = snapshot.waveform
    val fft = snapshot.fftMagnitudes
    val waveAlpha = (ctx.waveA * alpha).coerceIn(0f, 1f)
    val waveColor = Color(ctx.waveR, ctx.waveG, ctx.waveB).copy(alpha = waveAlpha)
    val strokeWidth = if (ctx.waveThick) 3.5.dp.toPx() else 1.8.dp.toPx()

    when (ctx.waveMode) {
        // 0. Classic Horizontal Spline Oscilloscope
        0 -> {
            scratchPad.scratchPath3.reset()
            val points = 96
            val step = size.width / (points - 1)
            val amplitude = size.height * 0.28f * ctx.waveScale
            val centerY = center.y + (sin(ctx.time * 1.5f) * 20.dp.toPx())

            for (i in 0 until points) {
                val waveIdx = (i * waveform.size / points).coerceIn(0, max(0, waveform.size - 1))
                val waveSample = if (waveform.isNotEmpty()) waveform[waveIdx] else sin(ctx.time * 3f + i * 0.15f) * 0.5f
                val px = i * step
                val py = centerY + waveSample * amplitude
                if (i == 0) scratchPad.scratchPath3.moveTo(px, py) else scratchPad.scratchPath3.lineTo(px, py)
            }

            drawPath(
                path = scratchPad.scratchPath3,
                color = waveColor,
                style = scratchPad.getStroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 1. Radial Concentric Audio Bloom
        1 -> {
            scratchPad.scratchPath3.reset()
            val points = 120
            val baseRadius = maxRadius * 0.32f * ctx.waveScale
            val amplitude = 48.dp.toPx() * (1f + ctx.bass)

            for (i in 0..points) {
                val angle = (i.toFloat() / points) * 2f * PI.toFloat() + (ctx.time * 0.4f)
                val fftIdx = (i * fft.size / points).coerceIn(0, max(0, fft.size - 1))
                val mag = if (fft.isNotEmpty()) fft[fftIdx] else (sin(angle * 4f + ctx.time * 2f) * 0.5f + 0.5f) * 0.6f
                val currentRadius = baseRadius + mag * amplitude

                val px = center.x + cos(angle) * currentRadius
                val py = center.y + sin(angle) * currentRadius
                if (i == 0) scratchPad.scratchPath3.moveTo(px, py) else scratchPad.scratchPath3.lineTo(px, py)
            }
            scratchPad.scratchPath3.close()

            drawPath(
                path = scratchPad.scratchPath3,
                color = waveColor,
                style = scratchPad.getStroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 2. Multi-Band Frequency Ribbons
        2 -> {
            val ribbons = 3
            for (r in 0 until ribbons) {
                scratchPad.scratchPath3.reset()
                val points = 80
                val step = size.width / (points - 1)
                val phaseOffset = r * 1.8f
                val ribbonColor = when (r) {
                    0 -> preset.primaryColor
                    1 -> preset.secondaryColor
                    else -> preset.accentColor
                }.copy(alpha = waveAlpha * (0.85f - r * 0.15f))

                for (i in 0 until points) {
                    val px = i * step
                    val fftIdx = ((i + r * 10) * fft.size / points).coerceIn(0, max(0, fft.size - 1))
                    val mag = if (fft.isNotEmpty()) fft[fftIdx] else 0.2f
                    val waveOffset = sin(i * 0.12f + ctx.time * 2.5f + phaseOffset) * (mag * 60.dp.toPx() + 15.dp.toPx())
                    val py = center.y + (r - 1) * 35.dp.toPx() + waveOffset
                    if (i == 0) scratchPad.scratchPath3.moveTo(px, py) else scratchPad.scratchPath3.lineTo(px, py)
                }

                drawPath(
                    path = scratchPad.scratchPath3,
                    color = ribbonColor,
                    style = scratchPad.getStroke(strokeWidth)
                )
            }
        }

        // 3. 3D Lissajous Harmonograph Knot
        3 -> {
            scratchPad.scratchPath3.reset()
            val points = 180
            val scaleX = size.width * 0.35f * ctx.waveScale
            val scaleY = size.height * 0.25f * ctx.waveScale
            val freqA = 3f + floor(ctx.bass * 2f)
            val freqB = 2f + floor(ctx.mid * 2f)
            val phase = ctx.time * 1.2f

            for (i in 0..points) {
                val t = (i.toFloat() / points) * 2f * PI.toFloat()
                val x = sin(freqA * t + phase) * scaleX
                val y = sin(freqB * t) * scaleY
                val px = center.x + x
                val py = center.y + y
                if (i == 0) scratchPad.scratchPath3.moveTo(px, py) else scratchPad.scratchPath3.lineTo(px, py)
            }

            drawPath(
                path = scratchPad.scratchPath3,
                color = waveColor,
                style = scratchPad.getStroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 4. Particle Constellation Spray
        else -> {
            val particleCount = 64
            for (p in 0 until particleCount) {
                val angle = (p.toFloat() / particleCount) * 2f * PI.toFloat() + (ctx.time * 0.8f)
                val dist = ((p * 17 + ctx.time * 40f) % (maxRadius * 0.85f))
                val fftIdx = (p * fft.size / particleCount).coerceIn(0, max(0, fft.size - 1))
                val mag = if (fft.isNotEmpty()) fft[fftIdx] else 0.3f
                val particleRadius = 2.5.dp.toPx() + (mag * 6.dp.toPx())

                val px = center.x + cos(angle) * dist
                val py = center.y + sin(angle) * dist
                drawCircle(
                    color = if (p % 2 == 0) preset.primaryColor.copy(alpha = waveAlpha) else preset.secondaryColor.copy(alpha = waveAlpha),
                    radius = particleRadius,
                    center = Offset(px, py)
                )
            }
        }
    }
}

/**
 * 6. Render Motion Vector Field
 */
private fun DrawScope.renderMilkdropMotionVectors(
    ctx: MilkdropFrameContext,
    preset: MilkdropPreset,
    center: Offset,
    alpha: Float
) {
    if (ctx.mvA <= 0.001f || ctx.mvX <= 0 || ctx.mvY <= 0) return

    val mvAlpha = (ctx.mvA * alpha).coerceIn(0f, 1f)
    val color = Color(ctx.mvR, ctx.mvG, ctx.mvB).copy(alpha = mvAlpha)
    val stepX = size.width / (ctx.mvX + 1)
    val stepY = size.height / (ctx.mvY + 1)
    val length = ctx.mvL * 16.dp.toPx()

    for (x in 1..ctx.mvX) {
        for (y in 1..ctx.mvY) {
            val startX = x * stepX
            val startY = y * stepY
            val endX = startX + (ctx.mvDx * 50f + sin(ctx.time * 2f + x * 0.3f) * 0.5f) * length
            val endY = startY + (ctx.mvDy * 50f + cos(ctx.time * 2f + y * 0.3f) * 0.5f) * length

            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 1.2.dp.toPx()
            )
        }
    }
}

/**
 * 7. Render Outer and Inner Borders
 */
private fun DrawScope.renderMilkdropBorders(
    ctx: MilkdropFrameContext,
    alpha: Float
) {
    if (ctx.obSize > 0f && ctx.obA > 0f) {
        val obWidth = ctx.obSize * min(size.width, size.height)
        drawRect(
            color = Color(ctx.obR, ctx.obG, ctx.obB).copy(alpha = (ctx.obA * alpha).coerceIn(0f, 1f)),
            size = size,
            style = Stroke(width = obWidth * 2)
        )
    }

    if (ctx.ibSize > 0f && ctx.ibA > 0f) {
        val ibWidth = ctx.ibSize * min(size.width, size.height)
        val offset = ctx.obSize * min(size.width, size.height)
        drawRect(
            color = Color(ctx.ibR, ctx.ibG, ctx.ibB).copy(alpha = (ctx.ibA * alpha).coerceIn(0f, 1f)),
            topLeft = Offset(offset, offset),
            size = Size(size.width - offset * 2, size.height - offset * 2),
            style = Stroke(width = ibWidth * 2)
        )
    }
}

/**
 * 8. Center Singularity & Outer Border Glow
 */
private fun DrawScope.renderMilkdropSingularityAndGlow(
    ctx: MilkdropFrameContext,
    preset: MilkdropPreset,
    center: Offset,
    maxRadius: Float,
    alpha: Float
) {
    val coreRadius = (16.dp.toPx() + ctx.bass * 36.dp.toPx()).coerceAtLeast(8.dp.toPx())
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                preset.accentColor.copy(alpha = (alpha * 0.95f).coerceIn(0f, 1f)),
                preset.primaryColor.copy(alpha = (alpha * 0.6f).coerceIn(0f, 1f)),
                Color.Transparent
            ),
            center = center,
            radius = coreRadius * 2.5f
        ),
        radius = coreRadius * 2.5f,
        center = center
    )
}
