package com.anuj.obsidian.ui.visualizer

import android.util.Log
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.anuj.obsidian.ObsidianCrashLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class VisualizerCategory(val displayName: String, val isGpuShader: Boolean = false) {
    SPECTRUM_BARS("Spectrum Bars"),
    CIRCULAR_RADIAL("Circular & Radial"),
    WAVEFORMS_CURVES("Waves & Oscilloscopes"),
    PARTICLES_CONSTELLATIONS("Particles & Stars"),
    RETRO_NEON("Cyberpunk & Retro"),
    AUDIOPHILE_ANALOG("VU Meters & Analog"),
    SACRED_GEOMETRY("Sacred Geometry"),
    OBSIDIAN_MILKDROP("Obsidian GPU MilkDrop"),
    SUPER_REALISTIC_3D("Super Realistic 3D"),
    OBSIDIAN_FLUID("Obsidian Fluid Dynamics", true),
    GEOMETRIC_KALEIDO("Kaleido & Fractals", true),
    COSMIC_NEBULA("Cosmic & Nebulae", true),
    DIGITAL_MATRIX("Matrix & Cyber Rain", true),
    ORGANIC_PLASMA("Organic Plasma Blobs", true),
    HARMONIC_RINGS("Harmonic Cymatics"),
    MINIMAL_AMBIENT("Minimalist Ambient"),

    // 20 Previous Distinct Rendering Algorithms
    TUNNEL_ZOOM_WARP("Tunnel & Perspective Zoom"),
    ROTATING_STARFIELD("Rotating Starfield & Warp", true),
    VORONOI_CELLULAR("Voronoi & Cellular Mesh"),
    FLAME_FIRE_FRACTAL("Flame & Fire Fractal"),
    WATER_SURFACE_RIPPLE("Rippling Water & Waves"),
    STROBING_GRID_TESSERACT("Strobing Grid & Tesseract"),
    SPIRAL_GALAXY_ARMS("Spiral Galaxy & Cosmic Arms"),
    LISSAJOUS_PERSISTENCE("Lissajous Laser & Trails"),
    RORSCHACH_BUTTERFLY("Rorschach & Butterfly Symmetry"),
    BEAT_FLASH_BLOOM("Supernova & Flash Bloom"),
    AURORA_PLASMA_CURTAINS("Aurora Borealis Plasma", true),
    SACRED_MANDALA_MORPH("Sacred Mandala Morpher"),
    QUANTUM_INTERFERENCE("Quantum Wave Interference"),
    ELECTRIC_TESLA_LIGHTNING("Tesla Lightning & Arcs"),
    CRYSTAL_PRISM_DISPERSION("Crystal Prism & Dispersion"),
    BLACK_HOLE_ACCRETION("Black Hole & Accretion Vortex"),
    MOBIUS_SOUNDWAVE_RIBBON("Möbius Soundwave Ribbon"),
    SONAR_RADAR_SWEEP("Retro Radar & Sonar Sweep"),
    CYBER_SPIROGRAPH_LASER("Cybernetic Laser Spirograph"),
    CHLADNI_ACOUSTIC_PLATE("Chladni Resonance Plate"),

    // 16 New Genuinely Distinct Rendering Algorithms
    MANDELBROT_JULIA_ZOOM("Mandelbrot & Julia Zoom", true),
    DNA_HELIX_ROTATION("DNA Helix Rotation"),
    WIREFRAME_TERRAIN_FLYOVER("Wireframe Terrain Flyover"),
    MOIRE_INTERFERENCE_RINGS("Moiré Interference Rings", true),
    LIQUID_CHROME_MORPH("Liquid Chrome Morph", true),
    BEAT_FIREWORKS_BURST("Beat Fireworks Burst", true),
    CELLULAR_AUTOMATON_LIFE("Cellular Automaton Life", true),
    CIRCUIT_BOARD_PULSE("Circuit Board Pulse", true),
    SOLAR_FLARE_PLASMA("Solar Flare Plasma", true),
    STAINED_GLASS_SHATTER("Stained Glass Shatter", true),
    WORMHOLE_PORTAL_RING("Wormhole Portal Ring", true),
    NEURAL_NETWORK_PULSE("Neural Network Pulse"),
    INK_DROP_DIFFUSION("Ink Drop Diffusion", true),
    VIBRATING_STRING_HARMONICS("Vibrating String Harmonics"),
    ORIGAMI_GEOMETRIC_UNFOLD("Origami Geometric Unfold"),
    SUPERNOVA_RADIAL_BURST("Supernova Radial Burst", true)
}

enum class GeometryMode {
    DEFAULT, LINEAR, RADIAL, POLAR, SPIRAL, LISSAJOUS, GRID, FRACTAL
}

enum class SymmetryMode {
    NONE, BILATERAL, QUAD, HEXAGONAL, OCTAGONAL, KALEIDOSCOPE
}

enum class VisualizerTransitionMode {
    INSTANT,      // Switch presets immediately
    FADE,         // Crossfade between presets
    SCROLL,       // Slide transition
    DISSOLVE      // Pixel-dissolve effect
}

class VisualizerTransitioner {
    private var currentPresetId = 0
    private var targetPresetId = 0
    private var transitionProgress = 0f
    private var transitionMode = VisualizerTransitionMode.FADE
    private var transitionDuration = 1000L  // 1 second
    private var startTime = 0L
    
    fun setTargetPreset(presetId: Int, mode: VisualizerTransitionMode = VisualizerTransitionMode.FADE) {
        targetPresetId = presetId
        transitionMode = mode
        startTime = System.currentTimeMillis()
    }
    
    fun updateTransition(): Float {
        if (currentPresetId == targetPresetId) return 1f
        
        val elapsed = System.currentTimeMillis() - startTime
        transitionProgress = (elapsed / transitionDuration.toFloat()).coerceIn(0f, 1f)
        
        if (transitionProgress >= 1f) {
            currentPresetId = targetPresetId
        }
        
        return when (transitionMode) {
            VisualizerTransitionMode.INSTANT -> if (transitionProgress >= 1f) 1f else 0f
            VisualizerTransitionMode.FADE -> transitionProgress
            VisualizerTransitionMode.SCROLL -> {
                // Ease-in-out
                if (transitionProgress < 0.5f) {
                    (transitionProgress * 2f) * (transitionProgress * 2f)
                } else {
                    1f - (2f * (1f - transitionProgress)) * (2f * (1f - transitionProgress)) / 2f
                }
            }
            VisualizerTransitionMode.DISSOLVE -> {
                // Ease-out-cubic
                val t = 1f - transitionProgress
                1f - (t * t * t)
            }
        }
    }
    
    fun isTransitioning(): Boolean = currentPresetId != targetPresetId
}

data class VisualizerPreset(
    val id: Int,
    val name: String,
    val category: VisualizerCategory,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val geometryMode: GeometryMode = GeometryMode.DEFAULT,
    val symmetryMode: SymmetryMode = SymmetryMode.NONE,
    val motionSpeed: Float = 1.0f,
    val sensitivity: Float = 1.0f,
    val particleCount: Int = 36,
    val glowIntensity: Float = 0.5f,
    val beatResponse: Float = 1.0f,
    val accentColor: Color = Color(0xFFFFD700)
) {
    fun withColorMode(
        colorMode: String,
        themePrimary: Color,
        themeSecondary: Color,
        customPrimary: Color,
        customSecondary: Color
    ): VisualizerPreset {
        return when (colorMode.uppercase()) {
            "APP_THEME" -> copy(primaryColor = themePrimary, secondaryColor = themeSecondary)
            "CUSTOM" -> copy(primaryColor = customPrimary, secondaryColor = customSecondary)
            else -> this // ORIGINAL by default: retains unique designed colors
        }
    }
}

object VisualizerLibrary {
    private val palette = listOf(
        Color(0xFF1DB954), Color(0xFFFFD700), Color(0xFF00E5FF), Color(0xFFFF2A85),
        Color(0xFFFF7A00), Color(0xFFB388FF), Color(0xFFFF3D00), Color(0xFF2979FF),
        Color(0xFFAEEA00), Color(0xFFFF4081), Color(0xFF00B0FF), Color(0xFF76FF03),
        Color(0xFFFF6D00), Color(0xFFE040FB), Color(0xFF18FFFF), Color(0xFFFF5252),
        Color(0xFF00E676), Color(0xFFFFAB00), Color(0xFF651FFF), Color(0xFF00B8D4),
        Color(0xFFFF1744), Color(0xFF3D5AFE), Color(0xFFC6FF00), Color(0xFFFF9100),
        Color(0xFF00E5FF), Color(0xFFD500F9), Color(0xFF00F5FF), Color(0xFFFF3366),
        Color(0xFF7C4DFF), Color(0xFF00E6FF), Color(0xFFFFD600), Color(0xFF64DD17)
    )

    // Initialize presets thread-safely with error handling and fallbacks
    private val presetsLock = Any()
    
    @Volatile
    private var presetsInitialized = false
    
    private val _presets = mutableListOf<VisualizerPreset>()
    
    // Custom presets registry
    private val _customPresets = mutableMapOf<Int, VisualizerPreset>()
    private val _parsedPresets = mutableMapOf<Int, ParsedMilkdropPreset>()
    
    // StateFlow for reactive preset updates across UI
    private val _presetsStateFlow = MutableStateFlow<List<VisualizerPreset>>(emptyList())
    val presetsStateFlow: StateFlow<List<VisualizerPreset>> = _presetsStateFlow.asStateFlow()

    init {
        synchronized(presetsLock) {
            try {
                initializePresets()
                presetsInitialized = true
            } catch (e: Throwable) {
                Log.e("VisualizerLibrary", "Failed to initialize presets in init: ${e.message}", e)
                createFallbackPreset()
                presetsInitialized = true
            }
            _presetsStateFlow.value = _presets.toList()
        }
    }

    private fun updatePresetsState() {
        synchronized(presetsLock) {
            val all = _presets.toList() + _customPresets.values.toList()
            _presetsStateFlow.value = all
        }
    }

    val presets: List<VisualizerPreset>
        get() {
            if (!presetsInitialized) {
                synchronized(presetsLock) {
                    if (!presetsInitialized) {
                        try {
                            initializePresets()
                            presetsInitialized = true
                        } catch (e: Throwable) {
                            Log.e("VisualizerLibrary", "Failed to initialize presets: ${e.message}", e)
                            createFallbackPreset()
                            presetsInitialized = true
                        }
                    }
                }
            }
            return synchronized(presetsLock) {
                if (_presets.isEmpty()) {
                    createFallbackPreset()
                }
                val list = _presets.toList() + _customPresets.values.toList()
                if (_presetsStateFlow.value.isEmpty()) {
                    _presetsStateFlow.value = list
                }
                list
            }
        }
    
    /**
     * Register custom presets from parsed milkdrop files
     */
    fun registerCustomPresets(parsedPresets: List<ParsedMilkdropPreset>) {
        synchronized(presetsLock) {
            Log.d("VisualizerLibrary", "Registering ${parsedPresets.size} custom presets")
            
            for (parsed in parsedPresets) {
                try {
                    val preset = VisualizerPreset(
                        id = parsed.preset.id,
                        name = parsed.name,
                        category = VisualizerCategory.OBSIDIAN_MILKDROP,
                        description = if (parsed.description.isNotEmpty()) "${parsed.description} (${parsed.author})" else "Custom preset by ${parsed.author}",
                        primaryColor = parsed.preset.primaryColor,
                        secondaryColor = parsed.preset.secondaryColor,
                        accentColor = parsed.preset.accentColor,
                        particleCount = 64,
                        motionSpeed = 1.0f,
                        sensitivity = 1.0f,
                        beatResponse = 1.0f,
                        glowIntensity = 1.0f
                    )
                    
                    _customPresets[parsed.preset.id] = preset
                    _parsedPresets[parsed.preset.id] = parsed
                } catch (e: Throwable) {
                    Log.e("VisualizerLibrary", "Failed to register preset ${parsed.name}: ${e.message}")
                }
            }
            
            updatePresetsState()
            Log.d("VisualizerLibrary", "Successfully registered ${_customPresets.size} custom presets. Total available: ${_presetsStateFlow.value.size}")
        }
    }
    
    /**
     * Get parsed milkdrop preset data for rendering
     */
    fun getParsedPreset(id: Int): ParsedMilkdropPreset? {
        return synchronized(presetsLock) {
            val direct = _parsedPresets[id]
            if (direct != null) return direct

            val targetPreset = _customPresets[id] ?: _presets.find { it.id == id && it.category == VisualizerCategory.OBSIDIAN_MILKDROP }
            if (targetPreset != null) {
                val byName = _parsedPresets.values.find { it.name.equals(targetPreset.name, ignoreCase = true) }
                if (byName != null) return byName
            }
            
            // Fallback synthesis for any MilkdropPreset
            val customPreset = MilkdropPresetLibrary.customPresets.find { it.id == id }
            val builtinPreset = MilkdropPresetLibrary.presets.find { it.id == id }
            val libPreset = customPreset ?: builtinPreset
            if (libPreset != null) {
                val isCustom = customPreset != null || libPreset.isCustom
                val content = if (isCustom) CustomMilkdropManager.generateBuiltinMilkContent(libPreset) else null
                val ir = if (content != null) {
                    try {
                        MilkdropPresetParser.parse(content, libPreset.name, null)
                    } catch (_: Throwable) { null }
                } else null
                val synth = ParsedMilkdropPreset(
                    name = libPreset.name,
                    author = libPreset.author,
                    description = libPreset.description,
                    sourceFile = null,
                    preset = libPreset,
                    presetIR = ir,
                    rawMilkContent = content
                )
                _parsedPresets[id] = synth
                synth
            } else {
                null
            }
        }
    }
    
    /**
     * Get all custom presets
     */
    fun getCustomPresets(): List<VisualizerPreset> {
        return synchronized(presetsLock) {
            _customPresets.values.toList()
        }
    }
    
    /**
     * Clear all custom presets
     */
    fun clearCustomPresets() {
        synchronized(presetsLock) {
            Log.d("VisualizerLibrary", "Clearing ${_customPresets.size} custom presets")
            _customPresets.clear()
            _parsedPresets.clear()
            updatePresetsState()
        }
    }
    
    private fun initializePresets() {
        _presets.clear()
        var idCounter = 1
        
        try {
            for (category in VisualizerCategory.entries) {
                val pCounts = when (category) {
                    VisualizerCategory.MANDELBROT_JULIA_ZOOM -> listOf(32, 48, 64, 80, 96, 120, 140, 160, 200, 240)
                    VisualizerCategory.DNA_HELIX_ROTATION -> listOf(16, 20, 24, 28, 32, 36, 40, 48, 56, 64)
                    VisualizerCategory.WIREFRAME_TERRAIN_FLYOVER -> listOf(12, 16, 20, 24, 28, 32, 36, 40, 44, 48)
                    VisualizerCategory.MOIRE_INTERFERENCE_RINGS -> listOf(20, 28, 36, 44, 52, 60, 72, 84, 96, 110)
                    VisualizerCategory.LIQUID_CHROME_MORPH -> listOf(4, 5, 6, 7, 8, 9, 10, 12, 14, 16)
                    VisualizerCategory.BEAT_FIREWORKS_BURST -> listOf(24, 36, 48, 60, 72, 84, 96, 120, 150, 180)
                    VisualizerCategory.CELLULAR_AUTOMATON_LIFE -> listOf(16, 20, 24, 28, 32, 36, 40, 44, 48, 52)
                    VisualizerCategory.CIRCUIT_BOARD_PULSE -> listOf(12, 16, 20, 24, 28, 32, 38, 44, 50, 60)
                    VisualizerCategory.SOLAR_FLARE_PLASMA -> listOf(8, 12, 16, 20, 24, 28, 32, 36, 42, 48)
                    VisualizerCategory.STAINED_GLASS_SHATTER -> listOf(12, 16, 20, 24, 28, 32, 38, 44, 50, 60)
                    VisualizerCategory.WORMHOLE_PORTAL_RING -> listOf(10, 14, 18, 22, 26, 30, 36, 42, 48, 54)
                    VisualizerCategory.NEURAL_NETWORK_PULSE -> listOf(16, 20, 24, 28, 32, 38, 44, 50, 56, 64)
                    VisualizerCategory.INK_DROP_DIFFUSION -> listOf(4, 6, 8, 10, 12, 14, 16, 18, 20, 24)
                    VisualizerCategory.VIBRATING_STRING_HARMONICS -> listOf(3, 4, 5, 6, 7, 8, 9, 10, 12, 14)
                    VisualizerCategory.ORIGAMI_GEOMETRIC_UNFOLD -> listOf(6, 8, 10, 12, 14, 16, 18, 20, 24, 28)
                    VisualizerCategory.SUPERNOVA_RADIAL_BURST -> listOf(16, 24, 32, 40, 48, 60, 72, 84, 96, 120)
                    else -> listOf(6, 10, 16, 24, 32, 40, 48, 56, 64, 20)
                }
                val glows = listOf(0.3f, 0.5f, 0.7f, 0.9f, 1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)
                
                for (i in 0 until 10) {
                    try {
                        val customName = when (category) {
                            VisualizerCategory.OBSIDIAN_MILKDROP -> {
                                val mdPresets = MilkdropPresetLibrary.presets
                                mdPresets[i % mdPresets.size].name
                            }
                            VisualizerCategory.MANDELBROT_JULIA_ZOOM -> listOf("Seahorse Valley Morph", "Julia Dendrite Fractal", "Electric Mandelbrot Core", "Cosmic Spiral Julia", "Deep Orbit Zoom", "Hyperbolic Escape Basin", "Burning Ship Variant", "Feigenbaum Cascade", "Cubic Julia Swirl", "Quantum Multibrot Matrix")[i]
                            VisualizerCategory.DNA_HELIX_ROTATION -> listOf("Double Helix Classic", "Triple Strand Chromosome", "Major Groove Glow", "Base-Pair Ladder Pulse", "Telomere Fluorescence", "Nucleotide Ribbon", "Supercoiled DNA", "RNA Transcription Loop", "Optogenetic Strand", "B-DNA Quantum Lattice")[i]
                            VisualizerCategory.WIREFRAME_TERRAIN_FLYOVER -> listOf("Synthwave Neon Grid", "Cyber Canyon Flight", "Alpine Audio Peaks", "Matrix Trench Run", "Quantum Valley Wireframe", "Sunset Mesh Ridge", "Digital Highlands", "Tron Grid Low-Altitude", "Hyper-Tessellated Range", "Vaporwave Horizon")[i]
                            VisualizerCategory.MOIRE_INTERFERENCE_RINGS -> listOf("Dual Origin Fringes", "Tri-Focal Wavefronts", "Dynamic Phase Moiré", "Hyperbolic Interference", "Optic Chiasm Rings", "Diffraction Lattice", "Stroboscopic Vortices", "Acoustic Beat Fringes", "Concentric Drift Waves", "Quantum Superposition Rings")[i]
                            VisualizerCategory.LIQUID_CHROME_MORPH -> listOf("Mercury Droplets", "Molten Chrome Sphere", "Ferrofluid Spikes", "Iridescent Bismuth Blobs", "Liquid Platinum Core", "Viscous Silver Mesh", "Plasma Liquid Metal", "Superfluid Helium Drops", "Gallic Alloy Morph", "Quicksilver Surge")[i]
                            VisualizerCategory.BEAT_FIREWORKS_BURST -> listOf("Midnight Peony Shells", "Strobe Chrysanthemum", "Willow Golden Cascades", "Crossette Sparkle Star", "Kamuro Heavy Gold", "Palm Tree Radial Burst", "Glittering Dahlia Ring", "Salute Transients", "Brocade Crown Trails", "Multi-Break Grand Finale")[i]
                            VisualizerCategory.CELLULAR_AUTOMATON_LIFE -> listOf("Gosper Glider Gun", "Pulsar Oscillator", "Beacon Rhythm Grid", "Blinker Audio Matrix", "Spaceship Fleet", "Toad Harmonic Colony", "Pentadecathlon Resonator", "Acorn Entropy Growth", "Diehard Life Cycle", "Conway Audio Synthesis")[i]
                            VisualizerCategory.CIRCUIT_BOARD_PULSE -> listOf("Motherboard Bus Transits", "GPU Trace Highway", "Quantum Processor Lattice", "Fiber-Optic Interconnect", "Logic Gate Neon Paths", "Superconductor Grid", "Neural Silicon Bus", "Nanobot Circuit Matrix", "Bioluminescent PCB", "High-Voltage Backplane")[i]
                            VisualizerCategory.SOLAR_FLARE_PLASMA -> listOf("Coronal Mass Ejection", "Chromosphere Turbulence", "Prominence Loop Arc", "Photosphere Sunspots", "Solar Wind Flare", "Magnetic Reconnection", "Thermonuclear Core", "Heliospheric Aurora", "Solar Dynamo Filament", "Superflare Gamma Pulse")[i]
                            VisualizerCategory.STAINED_GLASS_SHATTER -> listOf("Cathedral Rose Window", "Gothic Prism Facets", "Shattered Crystal Mosaic", "Art Nouveau Floral Glass", "Kaleido Stained Panel", "Cobalt Cathedral Glass", "Obsidian Leaded Shards", "Iridescent Tiffany Panes", "Stained Quartz Geometry", "Prismatic Sacred Glass")[i]
                            VisualizerCategory.WORMHOLE_PORTAL_RING -> listOf("Einstein-Rosen Bridge", "Event Horizon Swirl", "Singularity Accretion Ring", "Interstellar Warp Gate", "Tachyon Ring Portal", "Subspace Tunnel Mouth", "Gravitational Lens Vortex", "Chronos Temporal Ring", "Stargate Chevron Horizon", "Dark Energy Funnel")[i]
                            VisualizerCategory.NEURAL_NETWORK_PULSE -> listOf("Deep Synaptic Layers", "Dendrite Impulse Network", "Cortical Column Spike", "Neuroplasticity Flow", "Axon Myelin Glow", "Hebbian Learning Paths", "Transformer Attention Map", "Cognitive Pulse Mesh", "Quantum Neural Mesh", "Bio-Electric Brainwave")[i]
                            VisualizerCategory.INK_DROP_DIFFUSION -> listOf("Sumi-e Charcoal Cloud", "Marbled Indigo Bloom", "Crimson Water Blossom", "Vibrant Alcohol Ink", "Turbulent Watercolor Swirl", "Diffusing Pigment Rings", "Ebony Smoke Plume", "Cyan Aquamarine Drift", "Fluid Marbling Swirls", "Chromatography Dispersion")[i]
                            VisualizerCategory.VIBRATING_STRING_HARMONICS -> listOf("Harp Acoustic Wave", "Bass Guitar Resonator", "Violin Fundamental Mode", "Koto Standing Harmonics", "Electric Cello Wave", "Multi-String Polyphony", "Sitar Sympathetic Resonance", "Double Bass Pluck", "Strobotune Waveform", "Quantum String Theory")[i]
                            VisualizerCategory.ORIGAMI_GEOMETRIC_UNFOLD -> listOf("Miura-Ori Kinetic Fold", "Paper Crane Wingspan", "Yoshizawa Polyhedron", "Waterbomb Tessellation", "Pleated Hexagon Unfold", "Modular Origami Cube", "Crease Pattern Pulse", "Kinetic Paper Bloom", "Fractal Origami Lotus", "Tessellated Flap Grid")[i]
                            VisualizerCategory.SUPERNOVA_RADIAL_BURST -> listOf("Type Ia Relativistic Burst", "Core-Collapse Nebula", "Magnetar Gamma Ray Flash", "Crab Nebula Filament Shock", "Hypernova Plasma Ring", "Pulsar Wind Shockwave", "Stellar Remnant Cloud", "Shock Front Expander", "Cosmic Ray Explosion", "Omega Burst Annihilation")[i]
                            else -> "${category.displayName} ${i + 1}"
                        }
                        val pColor = palette[(i * 3 + category.ordinal) % palette.size]
                        val sColor = palette[(i * 3 + category.ordinal + 5) % palette.size]
                        
                        val assignedId = if (category == VisualizerCategory.OBSIDIAN_MILKDROP) {
                            val md = MilkdropPresetLibrary.presets
                            md.getOrNull(i % md.size)?.id ?: idCounter++
                        } else {
                            idCounter++
                        }

                        _presets.add(VisualizerPreset(
                            id = assignedId,
                            name = customName,
                            category = category,
                            description = "${category.displayName} - $customName visualizer preset.",
                            primaryColor = pColor,
                            secondaryColor = sColor,
                            particleCount = pCounts[i],
                            motionSpeed = 0.5f + (i * 0.14f),
                            sensitivity = 0.8f + (i * 0.12f),
                            beatResponse = 0.85f + (i * 0.15f),
                            glowIntensity = glows[i],
                            geometryMode = GeometryMode.entries[i % GeometryMode.entries.size],
                            symmetryMode = SymmetryMode.entries[i % SymmetryMode.entries.size]
                        ))
                    } catch (e: Throwable) {
                        Log.e("VisualizerLibrary", "Failed to create preset for $category index $i: ${e.message}")
                    }
                }
            }
            
            if (_presets.isEmpty()) {
                Log.e("VisualizerLibrary", "Presets list is empty after initialization!")
                createFallbackPreset()
            }
            updatePresetsState()
        } catch (e: Throwable) {
            Log.e("VisualizerLibrary", "Critical error during preset initialization: ${e.message}")
            createFallbackPreset()
            updatePresetsState()
        }
    }

    private fun createFallbackPreset() {
        try {
            _presets.clear()
            _presets.add(VisualizerPreset(
                id = 1,
                name = "Default Spectrum",
                category = VisualizerCategory.SPECTRUM_BARS,
                description = "Fallback spectrum bars visualizer",
                primaryColor = Color(0xFF1DB954),
                secondaryColor = Color(0xFFFFD700),
                particleCount = 64,
                motionSpeed = 1.0f,
                sensitivity = 1.0f,
                beatResponse = 1.0f,
                glowIntensity = 1.0f,
                geometryMode = GeometryMode.entries.firstOrNull() ?: GeometryMode.DEFAULT,
                symmetryMode = SymmetryMode.entries.firstOrNull() ?: SymmetryMode.NONE
            ))
            Log.d("VisualizerLibrary", "Created fallback preset")
        } catch (e: Throwable) {
            Log.e("VisualizerLibrary", "Failed to create fallback preset: ${e.message}")
        }
    }

    private fun createDefaultFallback(): VisualizerPreset {
        return VisualizerPreset(
            id = 1,
            name = "Default Fallback",
            category = VisualizerCategory.SPECTRUM_BARS,
            description = "System fallback visualizer",
            primaryColor = Color(0xFF1DB954),
            secondaryColor = Color(0xFFFFD700),
            particleCount = 64,
            motionSpeed = 1.0f,
            sensitivity = 1.0f,
            beatResponse = 1.0f,
            glowIntensity = 1.0f,
            geometryMode = GeometryMode.entries.firstOrNull() ?: GeometryMode.DEFAULT,
            symmetryMode = SymmetryMode.entries.firstOrNull() ?: SymmetryMode.NONE
        )
    }

    fun getPresetById(id: Int): VisualizerPreset {
        return try {
            // Check standalone preset IDs first
            when (id) {
                1001 -> return VisualizerPreset(1001, "Spectrum Bars", VisualizerCategory.SPECTRUM_BARS, "Clean audio spectrum bars", Color(0xFFE040FB), Color(0xFF00E5FF))
                1002 -> return VisualizerPreset(1002, "Waveform Flow", VisualizerCategory.WAVEFORMS_CURVES, "Oscilloscope flowing soundwave", Color(0xFF00E676), Color(0xFF1DE9B6))
                1003 -> return VisualizerPreset(1003, "Circular Pulse", VisualizerCategory.CIRCULAR_RADIAL, "Radial concentric audio pulse", Color(0xFF00E5FF), Color(0xFFFF4081))
                1004 -> return VisualizerPreset(1004, "Particle Burst", VisualizerCategory.PARTICLES_CONSTELLATIONS, "Dynamic orbital particle explosion", Color(0xFFFFD700), Color(0xFFFF5722))
                1005 -> return VisualizerPreset(1005, "Neon Grid", VisualizerCategory.RETRO_NEON, "Cyberpunk neon perspective grid", Color(0xFFFF0055), Color(0xFF00FFEE))
                1006 -> return VisualizerPreset(1006, "Audio Spectrum", VisualizerCategory.SPECTRUM_BARS, "High-density multi-color EQ spectrum", Color(0xFF7C4DFF), Color(0xFFFF5252))
            }

            // Check custom presets first
            synchronized(presetsLock) {
                val customPreset = _customPresets[id]
                if (customPreset != null) {
                    return customPreset
                }
                val parsed = _parsedPresets[id]
                if (parsed != null) {
                    val p = VisualizerPreset(
                        id = parsed.preset.id,
                        name = parsed.name,
                        category = VisualizerCategory.OBSIDIAN_MILKDROP,
                        description = if (parsed.description.isNotEmpty()) "${parsed.description} (${parsed.author})" else "Custom preset by ${parsed.author}",
                        primaryColor = parsed.preset.primaryColor,
                        secondaryColor = parsed.preset.secondaryColor,
                        accentColor = parsed.preset.accentColor
                    )
                    _customPresets[id] = p
                    return p
                }
            }

            val libCustom = MilkdropPresetLibrary.customPresets.find { it.id == id } ?: MilkdropPresetLibrary.presets.find { it.id == id }
            if (libCustom != null) {
                val p = VisualizerPreset(
                    id = libCustom.id,
                    name = libCustom.name,
                    category = VisualizerCategory.OBSIDIAN_MILKDROP,
                    description = if (libCustom.description.isNotEmpty()) "${libCustom.description} (${libCustom.author})" else "Preset by ${libCustom.author}",
                    primaryColor = libCustom.primaryColor,
                    secondaryColor = libCustom.secondaryColor,
                    accentColor = libCustom.accentColor
                )
                synchronized(presetsLock) {
                    _customPresets[id] = p
                }
                return p
            }

            val presetList = presets
            if (presetList.isEmpty()) {
                Log.e("VisualizerLibrary", "Presets list is empty in getPresetById!")
                createFallbackPreset()
                presets.firstOrNull() ?: createDefaultFallback()
            } else {
                presetList.find { it.id == id } ?: run {
                    Log.w("VisualizerLibrary", "Preset with id $id not found, returning first preset")
                    presetList.first()
                }
            }
        } catch (e: Throwable) {
            Log.e("VisualizerLibrary", "Error in getPresetById: ${e.message}")
            ObsidianCrashLogger.logException(e, "VisualizerLibrary.getPresetById")
            createDefaultFallback()
        }
    }

    fun generateRandomPreset(): VisualizerPreset {
        return try {
            val presetList = presets
            
            if (presetList.isEmpty()) {
                Log.e("VisualizerLibrary", "Presets list is empty! Attempting recovery...")
                presetsInitialized = false
                val retryList = presets
                
                if (retryList.isEmpty()) {
                    Log.e("VisualizerLibrary", "Recovery failed, all presets empty. Returning fallback.")
                    createDefaultFallback()
                } else {
                    retryList.random()
                }
            } else {
                presetList.random()
            }
        } catch (e: NoSuchElementException) {
            Log.e("VisualizerLibrary", "NoSuchElementException in generateRandomPreset: ${e.message}")
            ObsidianCrashLogger.logException(e, "VisualizerLibrary.generateRandomPreset - empty list")
            createDefaultFallback()
        } catch (e: Throwable) {
            Log.e("VisualizerLibrary", "Unexpected error in generateRandomPreset: ${e.message}")
            ObsidianCrashLogger.logException(e, "VisualizerLibrary.generateRandomPreset")
            createDefaultFallback()
        }
    }
    
    fun generatePresetForEnergy(isHighEnergy: Boolean): VisualizerPreset {
        return try {
            val presetList = presets
            
            if (presetList.isEmpty()) {
                Log.w("VisualizerLibrary", "Presets empty, returning random instead")
                return generateRandomPreset()
            }
            
            val highEnergyCategories = setOf(
                VisualizerCategory.RETRO_NEON,
                VisualizerCategory.BEAT_FIREWORKS_BURST,
                VisualizerCategory.BEAT_FLASH_BLOOM,
                VisualizerCategory.ELECTRIC_TESLA_LIGHTNING,
                VisualizerCategory.SUPERNOVA_RADIAL_BURST,
                VisualizerCategory.DIGITAL_MATRIX,
                VisualizerCategory.LIQUID_CHROME_MORPH
            )
            val lowEnergyCategories = setOf(
                VisualizerCategory.MINIMAL_AMBIENT,
                VisualizerCategory.WATER_SURFACE_RIPPLE,
                VisualizerCategory.AURORA_PLASMA_CURTAINS,
                VisualizerCategory.COSMIC_NEBULA,
                VisualizerCategory.INK_DROP_DIFFUSION,
                VisualizerCategory.MANDELBROT_JULIA_ZOOM,
                VisualizerCategory.OBSIDIAN_FLUID
            )
            
            val targetCategories = if (isHighEnergy) highEnergyCategories else lowEnergyCategories
            
            val filtered = presetList.filter { it.category in targetCategories }
            if (filtered.isNotEmpty()) {
                filtered.random()
            } else {
                Log.w("VisualizerLibrary", "No presets found for ${if (isHighEnergy) "high" else "low"} energy, using random")
                generateRandomPreset()
            }
        } catch (e: Throwable) {
            Log.e("VisualizerLibrary", "Error in generatePresetForEnergy: ${e.message}")
            generateRandomPreset()
        }
    }
}

class VisualizerScratchPad {
    val telemetry = AudioTelemetrySnapshot()
    val particleX = FloatArray(1024)
    val particleY = FloatArray(1024)
    val scratchPath1 = androidx.compose.ui.graphics.Path()
    val scratchPath2 = androidx.compose.ui.graphics.Path()
    val scratchPath3 = androidx.compose.ui.graphics.Path()
    
    val starX = FloatArray(1024)
    val starY = FloatArray(1024)
    val starZ = FloatArray(1024)
    
    val voronoiSitesX = FloatArray(256)
    val voronoiSitesY = FloatArray(256)
    
    val galaxyStarsRadius = FloatArray(512)
    val galaxyStarsAngle = FloatArray(512)
    val galaxyStarsArm = IntArray(512)
    
    val fluidNodesX = FloatArray(512)
    val fluidNodesY = FloatArray(512)
    
    val chladniParticlesX = FloatArray(512)
    val chladniParticlesY = FloatArray(512)
    
    val matrixDropsY = FloatArray(512)
    val matrixDropsSpeed = FloatArray(512)
    
    val tesseractOuterOffsets = Array(8) { androidx.compose.ui.geometry.Offset.Zero }
    val tesseractInnerOffsets = Array(8) { androidx.compose.ui.geometry.Offset.Zero }
    val tesseractEdges = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 0,
        4 to 5, 5 to 6, 6 to 7, 7 to 4,
        0 to 4, 1 to 5, 2 to 6, 3 to 7
    )

    // Additional buffers for the 16 new high-performance canvas visualizers
    val fireworkX = FloatArray(512)
    val fireworkY = FloatArray(512)
    val fireworkVx = FloatArray(512)
    val fireworkVy = FloatArray(512)
    val fireworkAge = FloatArray(512)
    
    val neuralNodeX = FloatArray(256)
    val neuralNodeY = FloatArray(256)
    val neuralNodeLayer = IntArray(256)
    
    val circuitTraceX1 = FloatArray(256)
    val circuitTraceY1 = FloatArray(256)
    val circuitTraceX2 = FloatArray(256)
    val circuitTraceY2 = FloatArray(256)
    
    val lifeGrid = BooleanArray(1024)
    val lifeGridNext = BooleanArray(1024)
    
    val terrainHeights = FloatArray(1024)
    val terrainGridX = FloatArray(1024)
    val terrainGridY = FloatArray(1024)
    val terrainGridAlpha = FloatArray(128)
    
    // High-performance direct cache for Stroke objects across 60fps rendering passes
    private val strokeCache = java.util.HashMap<Long, androidx.compose.ui.graphics.drawscope.Stroke>(64)
    private val gradientCache = java.util.HashMap<Long, androidx.compose.ui.graphics.Brush>(64)

    fun getStroke(
        width: Float,
        cap: androidx.compose.ui.graphics.StrokeCap = androidx.compose.ui.graphics.StrokeCap.Butt,
        join: androidx.compose.ui.graphics.StrokeJoin = androidx.compose.ui.graphics.StrokeJoin.Miter
    ): androidx.compose.ui.graphics.drawscope.Stroke {
        val safeWidth = width.coerceAtLeast(0.1f)
        val quantizedWidth = (safeWidth * 10f).toInt()
        val key = (quantizedWidth.toLong() shl 16) or ((cap.hashCode() and 0xFF).toLong() shl 8) or (join.hashCode() and 0xFF).toLong()
        var stroke = strokeCache[key]
        if (stroke == null) {
            if (strokeCache.size > 256) {
                strokeCache.clear()
            }
            stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = safeWidth, cap = cap, join = join)
            strokeCache[key] = stroke
        }
        return stroke
    }

    fun getRadialGradient(
        color1: androidx.compose.ui.graphics.Color,
        color2: androidx.compose.ui.graphics.Color,
        center: androidx.compose.ui.geometry.Offset,
        radius: Float
    ): androidx.compose.ui.graphics.Brush {
        val qRad = (radius / 2f).toInt()
        val key = (color1.value.toLong() xor (color2.value.toLong() shl 16)) xor (qRad.toLong() shl 32)
        var brush = gradientCache[key]
        if (brush == null) {
            if (gradientCache.size > 128) {
                gradientCache.clear()
            }
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(color1, color2),
                center = center,
                radius = radius.coerceAtLeast(1f)
            )
            gradientCache[key] = brush
        }
        return brush
    }
}

fun DrawScope.renderObsidianVisualizer(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    progressFraction: Float = 0f,
    performanceMode: String = "smooth_60",
    scratchPad: VisualizerScratchPad
) {
    if (alpha <= 0.001f) return
    try {
        val snapshot = HardwareVisualizerBuffer.currentSnapshot
        val center = Offset(size.width / 2f, size.height / 2f)
        when (preset.category) {
            VisualizerCategory.SPECTRUM_BARS -> renderSpectrumBars(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.CIRCULAR_RADIAL -> renderCircularRadial(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.WAVEFORMS_CURVES -> renderWaveforms(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.PARTICLES_CONSTELLATIONS -> renderParticles(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.RETRO_NEON -> renderRetroNeon(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.AUDIOPHILE_ANALOG -> renderAnalogMeter(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.SACRED_GEOMETRY -> renderSacredGeometry(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.OBSIDIAN_MILKDROP -> renderMilkdrop(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.SUPER_REALISTIC_3D -> {
                when (kotlin.math.abs(preset.id) % 5) {
                    0 -> draw3DTesseractCube(preset, size, center, snapshot, scratchPad)
                    1 -> draw3DAudioSphere(preset, size, center, snapshot, scratchPad)
                    2 -> draw3DEqualizerPillarField(preset, size, center, snapshot, scratchPad)
                    3 -> draw3DHyperdriveStarWarp(preset, size, center, snapshot, scratchPad)
                    else -> drawSuperRealistic3D(preset, size, center, snapshot, scratchPad)
                }
            }
            VisualizerCategory.OBSIDIAN_FLUID -> drawObsidianFluid(preset, size, center, snapshot, scratchPad)
            VisualizerCategory.GEOMETRIC_KALEIDO -> renderGeometricKaleido(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.COSMIC_NEBULA -> renderCosmicNebula(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.DIGITAL_MATRIX -> renderDigitalMatrix(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.ORGANIC_PLASMA -> renderOrganicPlasma(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.HARMONIC_RINGS -> renderHarmonicRings(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.MINIMAL_AMBIENT -> renderMinimalAmbient(preset, time, isPlaying, alpha, snapshot, scratchPad)
            
            VisualizerCategory.TUNNEL_ZOOM_WARP -> renderTunnelZoomWarp(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.ROTATING_STARFIELD -> renderRotatingStarfield(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.VORONOI_CELLULAR -> renderVoronoiCellular(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.FLAME_FIRE_FRACTAL -> renderFlameFireFractal(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.WATER_SURFACE_RIPPLE -> renderWaterSurfaceRipple(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.STROBING_GRID_TESSERACT -> renderStrobingGridTesseract(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.SPIRAL_GALAXY_ARMS -> renderSpiralGalaxyArms(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.LISSAJOUS_PERSISTENCE -> renderLissajousPersistence(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.RORSCHACH_BUTTERFLY -> renderRorschachButterfly(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.BEAT_FLASH_BLOOM -> renderBeatFlashBloom(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.AURORA_PLASMA_CURTAINS -> renderAuroraPlasmaCurtains(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.SACRED_MANDALA_MORPH -> renderSacredMandalaMorph(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.QUANTUM_INTERFERENCE -> renderQuantumInterference(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.ELECTRIC_TESLA_LIGHTNING -> renderElectricTeslaLightning(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.CRYSTAL_PRISM_DISPERSION -> renderCrystalPrismDispersion(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.BLACK_HOLE_ACCRETION -> renderBlackHoleAccretion(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.MOBIUS_SOUNDWAVE_RIBBON -> renderMobiusSoundwaveRibbon(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.SONAR_RADAR_SWEEP -> renderSonarRadarSweep(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.CYBER_SPIROGRAPH_LASER -> renderCyberSpirographLaser(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.CHLADNI_ACOUSTIC_PLATE -> renderChladniAcousticPlate(preset, time, isPlaying, alpha, snapshot, scratchPad)

            // 16 New Rendering Algorithms Dispatch
            VisualizerCategory.MANDELBROT_JULIA_ZOOM -> renderMandelbrotJuliaZoom(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.DNA_HELIX_ROTATION -> renderDnaHelixRotation(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.WIREFRAME_TERRAIN_FLYOVER -> renderWireframeTerrainFlyover(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.MOIRE_INTERFERENCE_RINGS -> renderMoireInterferenceRings(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.LIQUID_CHROME_MORPH -> renderLiquidChromeMorph(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.BEAT_FIREWORKS_BURST -> renderBeatFireworksBurst(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.CELLULAR_AUTOMATON_LIFE -> renderCellularAutomatonLife(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.CIRCUIT_BOARD_PULSE -> renderCircuitBoardPulse(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.SOLAR_FLARE_PLASMA -> renderSolarFlarePlasma(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.STAINED_GLASS_SHATTER -> renderStainedGlassShatter(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.WORMHOLE_PORTAL_RING -> renderWormholePortalRing(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.NEURAL_NETWORK_PULSE -> renderNeuralNetworkPulse(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.INK_DROP_DIFFUSION -> renderInkDropDiffusion(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.VIBRATING_STRING_HARMONICS -> renderVibratingStringHarmonics(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.ORIGAMI_GEOMETRIC_UNFOLD -> renderOrigamiGeometricUnfold(preset, time, isPlaying, alpha, snapshot, scratchPad)
            VisualizerCategory.SUPERNOVA_RADIAL_BURST -> renderSupernovaRadialBurst(preset, time, isPlaying, alpha, snapshot, scratchPad)
        }
    } catch (e: Throwable) {
        // Fail-safe protection against any arithmetic or draw exceptions
        renderSpectrumBars(preset, time, isPlaying, alpha, HardwareVisualizerBuffer.currentSnapshot, scratchPad)
    }
}

// 1. Spectrum Bars
private fun DrawScope.renderSpectrumBars(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    if (snapshot.fftMagnitudes.isEmpty()) return
    val barWidth = size.width / snapshot.fftMagnitudes.size
    for (i in snapshot.fftMagnitudes.indices) {
        val h = snapshot.fftMagnitudes[i] * size.height * preset.sensitivity
        val color = if (i % 2 == 0) preset.primaryColor else preset.secondaryColor
        drawRect(color.copy(alpha=alpha), topLeft = Offset(i * barWidth, size.height - h), size = Size(barWidth - 2f.dp.toPx(), h))
    }
}

// 2. Circular Radial
private fun DrawScope.renderCircularRadial(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseR = minOf(size.width, size.height) * 0.25f
    val mags = snapshot.fftMagnitudes
    if (mags.isEmpty()) return
    scratchPad.scratchPath1.reset()
    for (i in 0..mags.size) {
        val idx = i % mags.size
        val angle = (i * 2 * Math.PI / mags.size).toFloat() + time * preset.motionSpeed
        val r = baseR + mags[idx] * 100f.dp.toPx() * preset.sensitivity
        val x = center.x + kotlin.math.cos(angle) * r
        val y = center.y + kotlin.math.sin(angle) * r
        if (i == 0) scratchPad.scratchPath1.moveTo(x, y) else scratchPad.scratchPath1.lineTo(x, y)
    }
    scratchPad.scratchPath1.close()
    drawPath(scratchPad.scratchPath1, color = preset.primaryColor.copy(alpha=alpha), style = Stroke(2f.dp.toPx()))
}

// 3. Waveforms & Oscilloscopes
private fun DrawScope.renderWaveforms(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val midY = size.height * 0.55f
    val step = 5
    scratchPad.scratchPath1.reset()
    scratchPad.scratchPath2.reset()

    var first = true
    val waveLen = snapshot.waveform.size
    if (waveLen <= 0) return
    for (x in 0..size.width.toInt() step step) {
        val xNorm = x / size.width
        val sampleIdx1 = (xNorm * (waveLen - 1)).toInt().coerceIn(0, waveLen - 1)
        val sampleIdx2 = ((1f - xNorm) * (waveLen - 1)).toInt().coerceIn(0, waveLen - 1)

        val s1 = if (isPlaying) snapshot.waveform[sampleIdx1] * preset.sensitivity else sin(time * 3f + xNorm * 10f).toFloat() * 0.05f
        val s2 = if (isPlaying) snapshot.waveform[sampleIdx2] * preset.sensitivity else cos(time * 2f - xNorm * 7f).toFloat() * 0.04f

        val y1 = midY + s1 * (size.height * 0.28f)
        val y2 = midY + s2 * (size.height * 0.20f)

        if (first) {
            scratchPad.scratchPath1.moveTo(x.toFloat(), y1)
            scratchPad.scratchPath2.moveTo(x.toFloat(), y2)
            first = false
        } else {
            scratchPad.scratchPath1.lineTo(x.toFloat(), y1)
            scratchPad.scratchPath2.lineTo(x.toFloat(), y2)
        }
    }

    drawPath(scratchPad.scratchPath1, preset.primaryColor.copy(alpha = alpha * 0.9f), style = scratchPad.getStroke(2.5.dp.toPx()))
    drawPath(scratchPad.scratchPath2, preset.secondaryColor.copy(alpha = alpha * 0.65f), style = scratchPad.getStroke(1.6.dp.toPx()))
}

// 4. Particles & Constellations
private fun DrawScope.renderParticles(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val particleCount = preset.particleCount.coerceIn(16, 64)
    val beatScale = if (isPlaying) 1f + snapshot.beatPulse * 0.8f * preset.beatResponse else 1f

    for (i in 0 until particleCount) {
        val seed = i * 137.5
        val speedX = (sin(seed) * 0.35f * preset.motionSpeed).toFloat()
        val speedY = (cos(seed) * 0.35f * preset.motionSpeed).toFloat()

        val px = (abs(sin(seed + time * speedX)) * size.width).toFloat()
        val py = (abs(cos(seed * 1.5 + time * speedY)) * size.height).toFloat()
        scratchPad.particleX[i] = px
        scratchPad.particleY[i] = py

        val pRadius = if (isPlaying) (2.2f + (snapshot.bassEnergy * 3.5f) * beatScale) else 2.2f
        drawCircle(
            color = if (i % 2 == 0) preset.primaryColor.copy(alpha = alpha * 0.85f) else preset.secondaryColor.copy(alpha = alpha * 0.85f),
            radius = pRadius.dp.toPx(),
            center = Offset(px, py)
        )
    }

    val maxDistSq = (65.dp.toPx()) * (65.dp.toPx())
    for (i in 0 until particleCount) {
        for (j in i + 1 until particleCount) {
            val dx = scratchPad.particleX[i] - scratchPad.particleX[j]
            val dy = scratchPad.particleY[i] - scratchPad.particleY[j]
            val distSq = dx * dx + dy * dy
            if (distSq < maxDistSq) {
                val lineAlpha = (1f - distSq / maxDistSq) * alpha * 0.30f
                drawLine(
                    color = preset.primaryColor.copy(alpha = lineAlpha),
                    start = Offset(scratchPad.particleX[i], scratchPad.particleY[i]),
                    end = Offset(scratchPad.particleX[j], scratchPad.particleY[j]),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

// 5. Retro Neon & Synthwave
private fun DrawScope.renderRetroNeon(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val horizonY = size.height * 0.65f
    val sunPulse = if (isPlaying) snapshot.bassEnergy * 8.dp.toPx() * preset.beatResponse else 0f

    val vLines = 10
    for (i in 0..vLines) {
        val xTop = (size.width / vLines) * i
        val xBottom = size.width / 2f + (xTop - size.width / 2f) * 2.2f
        drawLine(
            color = preset.secondaryColor.copy(alpha = alpha * 0.30f),
            start = Offset(xTop, horizonY),
            end = Offset(xBottom, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }

    val hCount = 6
    for (i in 0 until hCount) {
        val progress = ((i + (time * 1.2f * preset.motionSpeed) % 1f) / hCount)
        val y = horizonY + (progress * progress) * (size.height - horizonY)
        drawLine(
            color = preset.primaryColor.copy(alpha = (progress * alpha * 0.45f)),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.2.dp.toPx()
        )
    }

    drawCircle(
        brush = Brush.verticalGradient(
            listOf(preset.primaryColor.copy(alpha = alpha * 0.65f * preset.glowIntensity), preset.secondaryColor.copy(alpha = alpha * 0.1f)),
            startY = horizonY - (40.dp.toPx() + sunPulse),
            endY = horizonY
        ),
        radius = 35.dp.toPx() + sunPulse,
        center = Offset(size.width / 2f, horizonY)
    )
}

// 6. Analog VU Meter
private fun DrawScope.renderAnalogMeter(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val centerPivot = Offset(size.width / 2f, size.height * 0.95f)
    val gaugeRadius = size.height * 0.70f

    drawArc(
        color = Color(0xFF1B2E22),
        startAngle = 210f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = Offset(centerPivot.x - gaugeRadius, centerPivot.y - gaugeRadius),
        size = Size(gaugeRadius * 2, gaugeRadius * 2),
        style = scratchPad.getStroke(8.dp.toPx())
    )

    drawArc(
        color = Color(0xFFFF334B).copy(alpha = alpha * 0.8f),
        startAngle = 300f,
        sweepAngle = 30f,
        useCenter = false,
        topLeft = Offset(centerPivot.x - gaugeRadius, centerPivot.y - gaugeRadius),
        size = Size(gaugeRadius * 2, gaugeRadius * 2),
        style = scratchPad.getStroke(8.dp.toPx())
    )

    val needleAngleDeg = if (isPlaying) {
        210f + 120f * (snapshot.peakAmplitude * preset.sensitivity).coerceIn(0.05f, 0.98f)
    } else {
        215f + sin(time * 2f) * 2f
    }
    val needleRad = (needleAngleDeg * PI / 180.0)
    val needleTip = Offset(
        (centerPivot.x + gaugeRadius * 0.92f * cos(needleRad)).toFloat(),
        (centerPivot.y + gaugeRadius * 0.92f * sin(needleRad)).toFloat()
    )

    drawLine(
        color = preset.primaryColor.copy(alpha = alpha),
        start = centerPivot,
        end = needleTip,
        strokeWidth = 2.5.dp.toPx()
    )

    drawCircle(
        color = Color(0xFF111111),
        radius = 12.dp.toPx(),
        center = centerPivot
    )
}

// 7. Sacred Geometry
private fun DrawScope.renderSacredGeometry(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = minOf(size.width, size.height) * (0.28f + (if (isPlaying) snapshot.midEnergy * 0.08f * preset.sensitivity else 0f))
    val rings = preset.particleCount.coerceIn(3, 16)
    val overlap = preset.glowIntensity * 1.5f

    for (i in 0 until rings) {
        val angle = (i * 2 * Math.PI / rings) + (time * 0.18 * preset.motionSpeed)
        val offset = Offset(
            center.x + (radius * overlap * cos(angle)).toFloat(),
            center.y + (radius * overlap * sin(angle)).toFloat()
        )
        drawCircle(
            color = preset.primaryColor.copy(alpha = alpha * 0.35f),
            radius = radius * overlap,
            center = offset,
            style = scratchPad.getStroke(1.5.dp.toPx())
        )
    }

    drawCircle(
        color = preset.secondaryColor.copy(alpha = alpha * 0.6f),
        radius = radius * 0.75f,
        center = center,
        style = scratchPad.getStroke(1.8.dp.toPx())
    )
}

// 8. Obsidian Milkdrop
private fun DrawScope.renderMilkdrop(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    try {
        val parsed = VisualizerLibrary.getParsedPreset(preset.id)
        if (parsed?.doublePreset != null) {
            with(Milkdrop3BlendEngine) {
                renderDoublePreset(
                    doublePreset = parsed.doublePreset,
                    time = time,
                    isPlaying = isPlaying,
                    alpha = alpha,
                    liveRatioOverride = Milkdrop3Controller.liveBlendRatio.value,
                    livePatternOverride = Milkdrop3Controller.currentPattern.value,
                    livePlasmaSeedOverride = Milkdrop3Controller.plasmaSeed.value,
                    liveFlippedOverride = Milkdrop3Controller.isFlipped.value,
                    liveColorModulationOverride = Milkdrop3Controller.colorModulation.value,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }
        } else if (Milkdrop3Controller.isDoublePresetMode.value && Milkdrop3Controller.activeDoublePreset.value != null) {
            with(Milkdrop3BlendEngine) {
                renderDoublePreset(
                    doublePreset = Milkdrop3Controller.activeDoublePreset.value!!,
                    time = time,
                    isPlaying = isPlaying,
                    alpha = alpha,
                    liveRatioOverride = Milkdrop3Controller.liveBlendRatio.value,
                    livePatternOverride = Milkdrop3Controller.currentPattern.value,
                    livePlasmaSeedOverride = Milkdrop3Controller.plasmaSeed.value,
                    liveFlippedOverride = Milkdrop3Controller.isFlipped.value,
                    liveColorModulationOverride = Milkdrop3Controller.colorModulation.value,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }
        } else if (parsed != null) {
            renderMilkdropEngine(
                preset = parsed.preset,
                time = time,
                isPlaying = isPlaying,
                alpha = alpha,
                snapshot = snapshot,
                scratchPad = scratchPad
            )
        } else {
            val milkdropPreset = MilkdropPresetLibrary.getPresetById(preset.id)
            renderMilkdropEngine(
                preset = milkdropPreset,
                time = time,
                isPlaying = isPlaying,
                alpha = alpha,
                snapshot = snapshot,
                scratchPad = scratchPad
            )
        }
    } catch (e: Throwable) {
        Log.e("VisualizerEngine", "Error rendering Milkdrop preset ${preset.id} (${preset.name}): ${e.message}")
        // Safe fallback rendering using preset 1 to ensure animation never freezes or crashes
        try {
            val fallback = MilkdropPresetLibrary.getPresetById(1)
            renderMilkdropEngine(
                preset = fallback,
                time = time,
                isPlaying = isPlaying,
                alpha = alpha,
                snapshot = snapshot,
                scratchPad = scratchPad
            )
        } catch (f: Throwable) {
            Log.e("VisualizerEngine", "Fallback milkdrop render failed: ${f.message}")
        }
    }
}

// 9. 3D Super Realistic
private fun DrawScope.drawSuperRealistic3D(
    preset: VisualizerPreset,
    size: Size,
    center: Offset,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val fft = snapshot.fftMagnitudes
    val maxRadius = (minOf(size.width, size.height) / 2f) * 0.9f
    val bass = snapshot.bassEnergy * preset.beatResponse
    val numLayers = 12

    for (layer in numLayers downTo 1) {
        val depth = layer / numLayers.toFloat()
        val zScale = 1.0f + (bass * 0.5f * (1f - depth))
        val radius = maxRadius * depth * zScale
        val alpha = (1f - depth).coerceIn(0.1f, 1f)
        val rotationOffset = snapshot.timestampMs / 2000f * preset.motionSpeed + (depth * PI.toFloat())

        val strokeWidth = 2f + (bass * 8f * depth)
        val sides = 6
        scratchPad.scratchPath1.reset()
        for (i in 0..sides) {
            val angle = (i * 2 * PI / sides) + rotationOffset
            val fftMod = if (fft.size > 20) fft[((i * 4) + layer) % (fft.size / 2)] else 0f
            val r = radius + (fftMod * maxRadius * 0.2f * preset.sensitivity)
            val x = center.x + cos(angle.toDouble()).toFloat() * r
            val y = center.y + sin(angle.toDouble()).toFloat() * r
            if (i == 0) scratchPad.scratchPath1.moveTo(x, y) else scratchPad.scratchPath1.lineTo(x, y)
        }

        drawPath(
            path = scratchPad.scratchPath1,
            color = preset.primaryColor.copy(alpha = alpha),
            style = Stroke(width = strokeWidth, pathEffect = PathEffect.cornerPathEffect(radius = 20f))
        )

        drawCircle(
            color = preset.secondaryColor.copy(alpha = alpha * 0.1f * bass),
            radius = radius * 1.2f,
            center = center
        )
    }
}

private fun DrawScope.draw3DTesseractCube(
    preset: VisualizerPreset,
    size: Size,
    center: Offset,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val time = snapshot.timestampMs / 1000f * preset.motionSpeed
    val bass = snapshot.bassEnergy * preset.beatResponse
    val mid = snapshot.midEnergy
    val rotX = time * 0.6f + bass * 0.2f
    val rotY = time * 0.8f
    val rotZ = time * 0.4f

    val cosX = cos(rotX.toDouble()).toFloat()
    val sinX = sin(rotX.toDouble()).toFloat()
    val cosY = cos(rotY.toDouble()).toFloat()
    val sinY = sin(rotY.toDouble()).toFloat()
    val cosZ = cos(rotZ.toDouble()).toFloat()
    val sinZ = sin(rotZ.toDouble()).toFloat()

    val outerSize = minOf(size.width, size.height) * 0.28f
    val innerSize = outerSize * (0.42f + bass * 0.28f)

    fun project(x: Float, y: Float, z: Float): Offset {
        val x1 = x * cosY + z * sinY
        val z1 = -x * sinY + z * cosY
        val y2 = y * cosX - z1 * sinX
        val z2 = y * sinX + z1 * cosX
        val x3 = x1 * cosZ - y2 * sinZ
        val y3 = x1 * sinZ + y2 * cosZ

        val f = 450f
        val p = f / (f + z2 + 220f)
        return Offset(center.x + x3 * p, center.y + y3 * p)
    }

    scratchPad.tesseractOuterOffsets[0] = project(-outerSize, -outerSize, -outerSize)
    scratchPad.tesseractOuterOffsets[1] = project(outerSize, -outerSize, -outerSize)
    scratchPad.tesseractOuterOffsets[2] = project(outerSize, outerSize, -outerSize)
    scratchPad.tesseractOuterOffsets[3] = project(-outerSize, outerSize, -outerSize)
    scratchPad.tesseractOuterOffsets[4] = project(-outerSize, -outerSize, outerSize)
    scratchPad.tesseractOuterOffsets[5] = project(outerSize, -outerSize, outerSize)
    scratchPad.tesseractOuterOffsets[6] = project(outerSize, outerSize, outerSize)
    scratchPad.tesseractOuterOffsets[7] = project(-outerSize, outerSize, outerSize)

    scratchPad.tesseractInnerOffsets[0] = project(-innerSize, -innerSize, -innerSize)
    scratchPad.tesseractInnerOffsets[1] = project(innerSize, -innerSize, -innerSize)
    scratchPad.tesseractInnerOffsets[2] = project(innerSize, innerSize, -innerSize)
    scratchPad.tesseractInnerOffsets[3] = project(-innerSize, innerSize, -innerSize)
    scratchPad.tesseractInnerOffsets[4] = project(-innerSize, -innerSize, innerSize)
    scratchPad.tesseractInnerOffsets[5] = project(innerSize, -innerSize, innerSize)
    scratchPad.tesseractInnerOffsets[6] = project(innerSize, innerSize, innerSize)
    scratchPad.tesseractInnerOffsets[7] = project(-innerSize, innerSize, innerSize)

    val outerVerts = scratchPad.tesseractOuterOffsets
    val innerVerts = scratchPad.tesseractInnerOffsets
    val edges = scratchPad.tesseractEdges

    val strokeWidthOuter = 2.5.dp.toPx() + bass * 4.dp.toPx()
    val strokeWidthInner = 1.8.dp.toPx() + mid * 3.dp.toPx()

    for ((a, b) in edges) {
        drawLine(
            color = preset.primaryColor.copy(alpha = 0.88f),
            start = outerVerts[a],
            end = outerVerts[b],
            strokeWidth = strokeWidthOuter
        )
        drawLine(
            color = preset.secondaryColor.copy(alpha = 0.78f),
            start = innerVerts[a],
            end = innerVerts[b],
            strokeWidth = strokeWidthInner
        )
        drawLine(
            color = Color.White.copy(alpha = 0.35f + bass * 0.45f),
            start = outerVerts[a],
            end = innerVerts[a],
            strokeWidth = 1.2.dp.toPx()
        )
    }

    for (v in outerVerts) {
        drawCircle(
            color = preset.primaryColor,
            radius = 3.5.dp.toPx() + bass * 3.dp.toPx(),
            center = v
        )
    }
}

private fun DrawScope.draw3DAudioSphere(
    preset: VisualizerPreset,
    size: Size,
    center: Offset,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val time = snapshot.timestampMs / 1000f * preset.motionSpeed
    val bass = snapshot.bassEnergy * preset.beatResponse
    val fft = snapshot.fftMagnitudes
    val rotY = time * 0.5f
    val rotX = time * 0.3f

    val cosY = cos(rotY.toDouble()).toFloat()
    val sinY = sin(rotY.toDouble()).toFloat()
    val cosX = cos(rotX.toDouble()).toFloat()
    val sinX = sin(rotX.toDouble()).toFloat()

    val baseRadius = minOf(size.width, size.height) * 0.22f
    val latLines = 10
    val lonLines = 16

    for (lat in 0..latLines) {
        val theta = (lat.toFloat() / latLines) * PI.toFloat()
        val sinTheta = sin(theta.toDouble()).toFloat()
        val cosTheta = cos(theta.toDouble()).toFloat()

        scratchPad.scratchPath1.reset()
        var moved = false

        for (lon in 0..lonLines) {
            val phi = (lon.toFloat() / lonLines) * 2f * PI.toFloat()
            val fftIndex = (lat * lonLines + lon) % maxOf(1, fft.size)
            val audioMod = if (fft.size > fftIndex) fft[fftIndex] else 0f
            val r = baseRadius * (1f + audioMod * 0.45f * preset.sensitivity + bass * 0.15f)

            val x0 = r * sinTheta * cos(phi.toDouble()).toFloat()
            val y0 = r * cosTheta
            val z0 = r * sinTheta * sin(phi.toDouble()).toFloat()

            val x1 = x0 * cosY + z0 * sinY
            val z1 = -x0 * sinY + z0 * cosY
            val y2 = y0 * cosX - z1 * sinX
            val z2 = y0 * sinX + z1 * cosX

            val f = 400f
            val p = f / (f + z2 + 200f)
            val sx = center.x + x1 * p
            val sy = center.y + y2 * p

            if (!moved) {
                scratchPad.scratchPath1.moveTo(sx, sy)
                moved = true
            } else {
                scratchPad.scratchPath1.lineTo(sx, sy)
            }

            val nodeAlpha = (0.35f + z2 / (baseRadius * 2.2f)).coerceIn(0.1f, 1f)
            drawCircle(
                color = if (lon % 2 == 0) preset.primaryColor.copy(alpha = nodeAlpha) else preset.secondaryColor.copy(alpha = nodeAlpha),
                radius = 2.dp.toPx() + audioMod * 4.dp.toPx(),
                center = Offset(sx, sy)
            )
        }

        drawPath(
            path = scratchPad.scratchPath1,
            color = preset.primaryColor.copy(alpha = 0.55f),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

private fun DrawScope.draw3DEqualizerPillarField(
    preset: VisualizerPreset,
    size: Size,
    center: Offset,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val fft = snapshot.fftMagnitudes
    val bass = snapshot.bassEnergy * preset.beatResponse
    val gridRows = 6
    val gridCols = 8
    val spacing = minOf(size.width, size.height) * 0.065f

    val rotX = 0.65f
    val cosX = cos(rotX.toDouble()).toFloat()
    val sinX = sin(rotX.toDouble()).toFloat()

    for (r in gridRows - 1 downTo 0) {
        for (c in 0 until gridCols) {
            val fftIdx = (r * gridCols + c) % maxOf(1, fft.size)
            val audioVal = if (fft.size > fftIdx) fft[fftIdx] else 0.1f
            val h = (audioVal * 160.dp.toPx() * preset.sensitivity + bass * 35.dp.toPx()).coerceAtLeast(8.dp.toPx())

            val x0 = (c - gridCols / 2f + 0.5f) * spacing
            val z0 = (r - gridRows / 2f + 0.5f) * spacing

            val yBase = 70.dp.toPx()
            val yTop = yBase - h

            val yBaseProj = yBase * cosX - z0 * sinX
            val zBaseProj = yBase * sinX + z0 * cosX

            val yTopProj = yTop * cosX - z0 * sinX
            val zTopProj = yTop * sinX + z0 * cosX

            val f = 450f
            val pBase = f / (f + zBaseProj + 200f)
            val pTop = f / (f + zTopProj + 200f)

            val pxBase = center.x + x0 * pBase
            val pyBase = center.y + yBaseProj * pBase

            val pxTop = center.x + x0 * pTop
            val pyTop = center.y + yTopProj * pTop

            val w = 11.dp.toPx() * pBase

            val colColor = if ((r + c) % 2 == 0) preset.primaryColor else preset.secondaryColor
            val alpha = (1f - r / gridRows.toFloat()).coerceIn(0.2f, 1f)

            drawLine(
                color = colColor.copy(alpha = alpha * 0.85f),
                start = Offset(pxBase, pyBase),
                end = Offset(pxTop, pyTop),
                strokeWidth = w
            )

            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = w * 0.65f,
                center = Offset(pxTop, pyTop)
            )
        }
    }
}

private fun DrawScope.draw3DHyperdriveStarWarp(
    preset: VisualizerPreset,
    size: Size,
    center: Offset,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val bass = snapshot.bassEnergy * preset.beatResponse
    val speed = (8f + bass * 35f) * preset.motionSpeed

    for (i in scratchPad.starX.indices) {
        scratchPad.starZ[i] -= speed
        if (scratchPad.starZ[i] <= 5f) {
            scratchPad.starZ[i] = 1000f
            scratchPad.starX[i] = ((Math.random() - 0.5) * 1000).toFloat()
            scratchPad.starY[i] = ((Math.random() - 0.5) * 1000).toFloat()
        }

        val z = scratchPad.starZ[i]
        val p = 400f / z
        val sx = center.x + scratchPad.starX[i] * p
        val sy = center.y + scratchPad.starY[i] * p

        val pPrev = 400f / (z + speed * 2.2f)
        val sxPrev = center.x + scratchPad.starX[i] * pPrev
        val syPrev = center.y + scratchPad.starY[i] * pPrev

        val alpha = (1f - z / 1000f).coerceIn(0.1f, 1f)
        val strokeW = (3f * p).coerceIn(1f, 6.dp.toPx())

        drawLine(
            color = if (i % 2 == 0) preset.primaryColor.copy(alpha = alpha) else preset.secondaryColor.copy(alpha = alpha),
            start = Offset(sxPrev, syPrev),
            end = Offset(sx, sy),
            strokeWidth = strokeW
        )
    }
}

// 10. Obsidian Fluid
private fun DrawScope.drawObsidianFluid(
    preset: VisualizerPreset,
    size: Size,
    center: Offset,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val fft = snapshot.fftMagnitudes
    val bass = snapshot.bassEnergy * preset.beatResponse
    val time = snapshot.timestampMs / 1000f * preset.motionSpeed
    
    val nodeCount = 8
    val maxRadius = minOf(size.width, size.height) * 0.35f
    
    // Metaball / Fluid blobs physics simulation
    for (i in 0 until nodeCount) {
        val angle = (i.toFloat() / nodeCount) * 2f * PI.toFloat() + time * 0.5f
        val fftIdx = (i * 3) % maxOf(1, fft.size)
        val audioEnergy = if (fft.isNotEmpty()) fft[fftIdx] * preset.sensitivity else 0f
        
        // Fluid spring physics oscillating around center
        val dist = maxRadius * 0.4f + audioEnergy * maxRadius * 0.8f + sin(time * 2f + i) * maxRadius * 0.2f
        val targetX = center.x + cos(angle) * dist
        val targetY = center.y + sin(angle) * dist
        
        // Simple easing for fluid drag
        scratchPad.fluidNodesX[i] += (targetX - scratchPad.fluidNodesX[i]) * 0.12f
        scratchPad.fluidNodesY[i] += (targetY - scratchPad.fluidNodesY[i]) * 0.12f
        
        // Draw fluid blob
        val blobRadius = maxRadius * 0.35f + bass * maxRadius * 0.2f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(preset.primaryColor.copy(alpha = 0.85f * preset.glowIntensity), Color.Transparent),
                center = Offset(scratchPad.fluidNodesX[i], scratchPad.fluidNodesY[i]),
                radius = blobRadius * 1.5f
            ),
            radius = blobRadius * 1.5f,
            center = Offset(scratchPad.fluidNodesX[i], scratchPad.fluidNodesY[i])
        )
    }
    
    // Viscous surface tension connecting the blobs
    scratchPad.scratchPath1.reset()
    for (i in 0..nodeCount) {
        val idx = i % nodeCount
        val px = scratchPad.fluidNodesX[idx]
        val py = scratchPad.fluidNodesY[idx]
        
        val nextIdx = (i + 1) % nodeCount
        val nx = scratchPad.fluidNodesX[nextIdx]
        val ny = scratchPad.fluidNodesY[nextIdx]
        
        val midX = (px + nx) / 2f
        val midY = (py + ny) / 2f
        
        if (i == 0) {
            scratchPad.scratchPath1.moveTo(midX, midY)
        } else {
            // Quadratic bezier for fluid surface tension
            scratchPad.scratchPath1.quadraticTo(px, py, midX, midY)
        }
    }
    drawPath(
        path = scratchPad.scratchPath1,
        color = preset.secondaryColor.copy(alpha = 0.6f),
        style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round)
    )
}

// 11. Geometric & Kaleido
private fun DrawScope.renderGeometricKaleido(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = minOf(size.width, size.height) * 0.45f
    val bass = snapshot.bassEnergy * preset.beatResponse
    val symmetrySegments = preset.particleCount.coerceIn(4, 32)
    val baseAngle = time * 0.2f * preset.motionSpeed

    for (s in 0 until symmetrySegments) {
        val segAngle = (s * 2f * PI.toFloat() / symmetrySegments) + baseAngle
        rotate(degrees = (segAngle * 180f / PI.toFloat()), pivot = center) {
            scratchPad.scratchPath1.reset()
            val points = (preset.glowIntensity * 16).toInt().coerceIn(3, 16)
            for (p in 0..points) {
                val r = (maxRadius / points) * p
                val fftIdx = (p * 4) % maxOf(1, snapshot.fftMagnitudes.size)
                val audioMod = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 40.dp.toPx() * preset.sensitivity else 0f
                
                // Kaleidoscopic fractal folding
                val fold = if (p % 2 == 0) 1f else -1f
                val px = center.x + r + audioMod
                val py = center.y + fold * (r * 0.35f + bass * 30.dp.toPx())
                
                if (p == 0) scratchPad.scratchPath1.moveTo(px, py) else scratchPad.scratchPath1.lineTo(px, py)
            }
            
            // Mirror symmetry (bilateral reflection for each segment)
            val pathColor = if (s % 2 == 0) preset.primaryColor else preset.secondaryColor
            drawPath(
                path = scratchPad.scratchPath1,
                color = pathColor.copy(alpha = alpha * 0.9f),
                style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round)
            )
            
            // Draw reflection
            scratchPad.scratchPath2.reset()
            for (p in 0..points) {
                val r = (maxRadius / points) * p
                val fftIdx = (p * 4) % maxOf(1, snapshot.fftMagnitudes.size)
                val audioMod = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 40.dp.toPx() * preset.sensitivity else 0f
                
                val fold = if (p % 2 == 0) -1f else 1f
                val px = center.x + r + audioMod
                val py = center.y + fold * (r * 0.35f + bass * 30.dp.toPx())
                
                if (p == 0) scratchPad.scratchPath2.moveTo(px, py) else scratchPad.scratchPath2.lineTo(px, py)
            }
            drawPath(
                path = scratchPad.scratchPath2,
                color = pathColor.copy(alpha = alpha * 0.5f),
                style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
            )
        }
    }
}

// 12. Cosmic & Nebula
private fun DrawScope.renderCosmicNebula(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val maxR = minOf(size.width, size.height) * 0.7f
    val speed = time * 0.4f * preset.motionSpeed

    // Volumetric multi-layer nebula gas clouds
    val layers = 5
    for (l in 0 until layers) {
        val lRadius = maxR * (0.4f + l * 0.15f) + bass * 40.dp.toPx()
        val driftX = sin(speed * 0.7f + l) * 80f
        val driftY = cos(speed * 0.5f - l) * 80f
        
        val lColor = if (l % 2 == 0) preset.primaryColor else preset.secondaryColor
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    lColor.copy(alpha = (0.4f - l * 0.05f) * alpha * preset.glowIntensity),
                    lColor.copy(alpha = 0.1f * alpha),
                    Color.Transparent
                ),
                center = Offset(center.x + driftX, center.y + driftY),
                radius = lRadius
            ),
            radius = lRadius,
            center = Offset(center.x + driftX, center.y + driftY)
        )
    }

    // Stellar nursery / stars embedded in nebula
    val starCount = scratchPad.starX.size.coerceAtMost(80)
    for (i in 0 until starCount) {
        // Recycle stars for continuous flow
        scratchPad.starZ[i] -= speed * 20f
        if (scratchPad.starZ[i] <= 10f) {
            scratchPad.starZ[i] = 1000f
            scratchPad.starX[i] = ((Math.random() - 0.5) * 1200).toFloat()
            scratchPad.starY[i] = ((Math.random() - 0.5) * 1200).toFloat()
        }
        
        val z = scratchPad.starZ[i]
        val p = 400f / z
        val sx = center.x + scratchPad.starX[i] * p
        val sy = center.y + scratchPad.starY[i] * p
        
        val starAlpha = ((1f - z / 1000f) * alpha).coerceIn(0f, 1f)
        val audioTwinkle = if (isPlaying && i % 3 == 0) bass * 2.5f else 0f
        
        drawCircle(
            color = Color.White.copy(alpha = starAlpha),
            radius = (1.5.dp.toPx() * p + audioTwinkle).coerceIn(1f, 6.dp.toPx()),
            center = Offset(sx, sy)
        )
    }
}

// 13. Digital Matrix
private fun DrawScope.renderDigitalMatrix(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val cols = scratchPad.matrixDropsY.size
    val colWidth = size.width / cols
    val trebleBoost = if (isPlaying) 1f + snapshot.trebleEnergy * 2f else 1f

    for (c in 0 until cols) {
        scratchPad.matrixDropsY[c] += scratchPad.matrixDropsSpeed[c] * trebleBoost * preset.motionSpeed
        if (scratchPad.matrixDropsY[c] > size.height) {
            scratchPad.matrixDropsY[c] = -40f
        }

        val x = c * colWidth + colWidth / 2f
        val y = scratchPad.matrixDropsY[c]
        val dropLen = 45.dp.toPx()

        drawLine(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, preset.secondaryColor.copy(alpha = 0.4f * alpha), preset.primaryColor.copy(alpha = alpha)),
                startY = y - dropLen,
                endY = y
            ),
            start = Offset(x, y - dropLen),
            end = Offset(x, y),
            strokeWidth = 2.dp.toPx()
        )

        // Head glowing glyph
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = 2.dp.toPx(),
            center = Offset(x, y)
        )
    }
}

// 14. Organic Plasma Blobs
private fun DrawScope.renderOrganicPlasma(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bass = snapshot.bassEnergy * preset.beatResponse
    val baseR = minOf(size.width, size.height) * 0.24f
    scratchPad.scratchPath1.reset()

    val numSegments = 16
    for (i in 0..numSegments) {
        val angle = (i * 2 * PI / numSegments)
        val waveDeform = sin(angle * 3.0 + time * 2.2f * preset.motionSpeed).toFloat() * (20.dp.toPx() + bass * 35.dp.toPx())
        val r = baseR + waveDeform
        val x = center.x + cos(angle).toFloat() * r
        val y = center.y + sin(angle).toFloat() * r
        if (i == 0) scratchPad.scratchPath1.moveTo(x, y) else scratchPad.scratchPath1.lineTo(x, y)
    }
    scratchPad.scratchPath1.close()

    drawPath(
        path = scratchPad.scratchPath1,
        brush = Brush.radialGradient(
            listOf(preset.primaryColor.copy(alpha = 0.75f * alpha), preset.secondaryColor.copy(alpha = 0.25f * alpha)),
            center = center,
            radius = baseR * 1.5f
        )
    )
    drawPath(
        path = scratchPad.scratchPath1,
        color = preset.primaryColor.copy(alpha = alpha),
        style = Stroke(width = 3.dp.toPx())
    )
}

// 15. Harmonic Cymatics
private fun DrawScope.renderHarmonicRings(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val numRings = 7
    val maxRadius = minOf(size.width, size.height) * 0.40f

    for (r in 1..numRings) {
        val ringFraction = r.toFloat() / numRings
        val fftIdx = (r * 7) % maxOf(1, snapshot.fftMagnitudes.size)
        val mod = if (isPlaying && snapshot.fftMagnitudes.isNotEmpty()) snapshot.fftMagnitudes[fftIdx] * 20.dp.toPx() * preset.sensitivity else 0f
        val rad = maxRadius * ringFraction + mod

        val strokeCol = if (r % 2 == 0) preset.primaryColor else preset.secondaryColor
        drawCircle(
            color = strokeCol.copy(alpha = (1f - ringFraction * 0.4f) * alpha * 0.75f),
            radius = rad,
            center = center,
            style = Stroke(width = (1.5f + (1f - ringFraction)).dp.toPx())
        )
    }
}

// 16. Minimalist Ambient
private fun DrawScope.renderMinimalAmbient(
    preset: VisualizerPreset,
    time: Float,
    isPlaying: Boolean,
    alpha: Float,
    snapshot: VisualizerSnapshot,
    scratchPad: VisualizerScratchPad
) {
    val center = Offset(size.width / 2f, size.height * 0.65f)
    val radius = minOf(size.width, size.height) * 0.26f
    val subtlePulse = if (isPlaying) snapshot.bassEnergy * 6.dp.toPx() else sin(time * 1.5f).toFloat() * 3.dp.toPx()

    drawCircle(
        color = preset.primaryColor.copy(alpha = 0.25f * alpha * preset.glowIntensity),
        radius = radius + subtlePulse * 1.5f,
        center = center
    )

    drawCircle(
        color = preset.primaryColor.copy(alpha = 0.65f * alpha),
        radius = radius + subtlePulse,
        center = center,
        style = Stroke(width = 1.2.dp.toPx())
    )

    // Subtle horizontal horizon line
    drawLine(
        color = preset.secondaryColor.copy(alpha = 0.35f * alpha),
        start = Offset(center.x - radius * 1.8f, center.y),
        end = Offset(center.x + radius * 1.8f, center.y),
        strokeWidth = 1.dp.toPx()
    )
}
