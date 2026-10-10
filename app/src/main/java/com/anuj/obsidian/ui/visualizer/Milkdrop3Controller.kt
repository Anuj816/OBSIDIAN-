package com.anuj.obsidian.ui.visualizer

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.random.Random

/**
 * Controller & State Manager for MilkDrop 3 (milkdrop2077/MilkDrop3).
 * Provides full state tracking, keyboard / UI action dispatching, .milk2 export/import,
 * and curated factory double presets.
 */
object Milkdrop3Controller {
    private const val TAG = "Milkdrop3Controller"

    // Active Double Preset
    private val _activeDoublePreset = MutableStateFlow<Milkdrop3DoublePreset?>(null)
    val activeDoublePreset: StateFlow<Milkdrop3DoublePreset?> = _activeDoublePreset.asStateFlow()

    private val _isDoublePresetMode = MutableStateFlow(false)
    val isDoublePresetMode: StateFlow<Boolean> = _isDoublePresetMode.asStateFlow()

    // HUD / Diagnostic overlay visibility (MilkDrop 3 F4 key)
    private val _hudVisible = MutableStateFlow(false)
    val hudVisible: StateFlow<Boolean> = _hudVisible.asStateFlow()

    // Studio / Mixer Sheet visibility
    private val _mixerSheetVisible = MutableStateFlow(false)
    val mixerSheetVisible: StateFlow<Boolean> = _mixerSheetVisible.asStateFlow()

    // Realtime adjustable blend parameters
    private val _liveBlendRatio = MutableStateFlow(0.5f)
    val liveBlendRatio: StateFlow<Float> = _liveBlendRatio.asStateFlow()

    private val _currentPattern = MutableStateFlow(Milkdrop3BlendPattern.PLASMA)
    val currentPattern: StateFlow<Milkdrop3BlendPattern> = _currentPattern.asStateFlow()

    private val _plasmaSeed = MutableStateFlow(1.0f)
    val plasmaSeed: StateFlow<Float> = _plasmaSeed.asStateFlow()

    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    private val _isDirectionLocked = MutableStateFlow(false)
    val isDirectionLocked: StateFlow<Boolean> = _isDirectionLocked.asStateFlow()

    private val _autoBeatSwitch = MutableStateFlow(true)
    val autoBeatSwitch: StateFlow<Boolean> = _autoBeatSwitch.asStateFlow()

    private val _blendDurationSeconds = MutableStateFlow(6.0f)
    val blendDurationSeconds: StateFlow<Float> = _blendDurationSeconds.asStateFlow()

    private val _colorModulation = MutableStateFlow(Milkdrop3ColorModulation.ORIGINAL)
    val colorModulation: StateFlow<Milkdrop3ColorModulation> = _colorModulation.asStateFlow()

    // Status notification message for HUD toast
    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    init {
        // Initialize default double preset from factory presets
        val initialPreset = getCuratedDoublePresets().firstOrNull()
        if (initialPreset != null) {
            _activeDoublePreset.value = initialPreset
        }
    }

    fun showToast(message: String) {
        _statusToast.value = message
    }

    fun clearToast() {
        _statusToast.value = null
    }

    /**
     * Set the active double preset
     */
    fun setActiveDoublePreset(preset: Milkdrop3DoublePreset) {
        _activeDoublePreset.value = preset
        _currentPattern.value = preset.blendPattern
        _plasmaSeed.value = preset.plasmaSeed
        _isFlipped.value = preset.isFlipped
        _isDirectionLocked.value = preset.isDirectionLocked
        _autoBeatSwitch.value = preset.autoBeatSwitch
        _blendDurationSeconds.value = preset.blendDurationSeconds
        _colorModulation.value = preset.colorModulation
        _isDoublePresetMode.value = true
        showToast("Loaded .milk2: ${preset.name}")
    }

    fun toggleDoublePresetMode() {
        _isDoublePresetMode.value = !_isDoublePresetMode.value
        showToast(if (_isDoublePresetMode.value) "MilkDrop 3 Double Mode ON" else "Standard Preset Mode")
    }

    fun toggleHud() {
        _hudVisible.value = !_hudVisible.value
    }

    fun setHudVisible(visible: Boolean) {
        _hudVisible.value = visible
    }

    fun openMixerSheet() {
        _mixerSheetVisible.value = true
    }

    fun closeMixerSheet() {
        _mixerSheetVisible.value = false
    }

    /**
     * 'p' key: Cycle next blend pattern
     */
    fun cycleBlendPattern() {
        val patterns = Milkdrop3BlendPattern.values()
        val nextIdx = (_currentPattern.value.ordinal + 1) % patterns.size
        _currentPattern.value = patterns[nextIdx]
        showToast("Blend Pattern: ${patterns[nextIdx].displayName}")
    }

    fun setBlendPattern(pattern: Milkdrop3BlendPattern) {
        _currentPattern.value = pattern
        showToast("Blend Pattern: ${pattern.displayName}")
    }

    /**
     * 'P' (Shift+p) key: Force blend progress jump / next preset
     */
    fun forceBlendProgress() {
        val newRatio = (_liveBlendRatio.value + 0.25f) % 1.0f
        _liveBlendRatio.value = newRatio
        showToast("Blend Progress: ${(newRatio * 100).toInt()}%")
    }

    fun setLiveBlendRatio(ratio: Float) {
        _liveBlendRatio.value = ratio.coerceIn(0f, 1f)
    }

    /**
     * 'o' key: Randomize plasma pattern seed
     */
    fun randomizePlasmaSeed() {
        val newSeed = Random.nextFloat() * 100f
        _plasmaSeed.value = newSeed
        showToast("Plasma Seed: ${String.format("%.2f", newSeed)}")
    }

    fun setPlasmaSeed(seed: Float) {
        _plasmaSeed.value = seed
    }

    /**
     * 'u' key: Flip preset order (A <-> B)
     */
    fun flipPresetOrder() {
        _isFlipped.value = !_isFlipped.value
        showToast(if (_isFlipped.value) "Flipped: B ↔ A" else "Order: A ↔ B")
    }

    /**
     * 'U' key: Lock / unlock blending direction
     */
    fun toggleDirectionLock() {
        _isDirectionLocked.value = !_isDirectionLocked.value
        showToast(if (_isDirectionLocked.value) "Blend Direction Locked" else "Bidirectional Oscillation ON")
    }

    /**
     * 'b' key: Toggle beat detection auto preset switch
     */
    fun toggleBeatAutoSwitch() {
        _autoBeatSwitch.value = !_autoBeatSwitch.value
        showToast(if (_autoBeatSwitch.value) "Beat Auto-Switch ON" else "Beat Auto-Switch OFF")
    }

    /**
     * 'B' key: Cycle blend duration (seconds)
     */
    fun cycleBlendDuration() {
        val durations = floatArrayOf(2.0f, 4.0f, 6.0f, 10.0f, 15.0f, 20.0f)
        val cur = _blendDurationSeconds.value
        val next = durations.firstOrNull { it > cur } ?: durations.first()
        _blendDurationSeconds.value = next
        showToast("Blend Duration: ${next.toInt()}s")
    }

    fun setBlendDuration(duration: Float) {
        _blendDurationSeconds.value = duration.coerceIn(1f, 60f)
    }

    /**
     * 'c' key: Cycle color palette modulation mode
     */
    fun cycleColorModulation() {
        val mods = Milkdrop3ColorModulation.values()
        val nextIdx = (_colorModulation.value.ordinal + 1) % mods.size
        _colorModulation.value = mods[nextIdx]
        showToast("Color Mode: ${mods[nextIdx].displayName}")
    }

    fun setColorModulation(mode: Milkdrop3ColorModulation) {
        _colorModulation.value = mode
        showToast("Color Mode: ${mode.displayName}")
    }

    /**
     * Export / Save current double preset combination as a .milk2 file
     */
    fun exportCurrentAsMilk2(
        context: Context,
        name: String,
        author: String = "User"
    ): File? {
        return try {
            val cur = _activeDoublePreset.value ?: getCuratedDoublePresets().first()
            val presetsDir = MilkdropFileStructure.getPresetsDirectory(context)
            if (!presetsDir.exists()) presetsDir.mkdirs()

            val sanitizedName = name.trim().replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            val targetFile = File(presetsDir, "$sanitizedName.milk2")

            val content = buildMilk2FileContent(
                name = name,
                author = author,
                description = "MilkDrop 3 Double Preset exported by Obsidian",
                presetA = cur.presetA,
                presetB = cur.presetB,
                pattern = _currentPattern.value,
                blendRatio = _liveBlendRatio.value,
                blendDuration = _blendDurationSeconds.value,
                plasmaSeed = _plasmaSeed.value,
                isDirectionLocked = _isDirectionLocked.value,
                isFlipped = _isFlipped.value,
                autoBeatSwitch = _autoBeatSwitch.value
            )

            targetFile.writeText(content)
            Log.d(TAG, "Successfully exported .milk2 preset: ${targetFile.absolutePath}")
            showToast("Saved $sanitizedName.milk2")
            targetFile
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to export .milk2 preset: ${e.message}", e)
            showToast("Export failed: ${e.message}")
            null
        }
    }

    /**
     * Formats `.milk2` file text format compatible with MilkDrop 3 / BeatDrop specifications
     */
    fun buildMilk2FileContent(
        name: String,
        author: String,
        description: String,
        presetA: MilkdropPreset,
        presetB: MilkdropPreset,
        pattern: Milkdrop3BlendPattern,
        blendRatio: Float,
        blendDuration: Float,
        plasmaSeed: Float,
        isDirectionLocked: Boolean,
        isFlipped: Boolean,
        autoBeatSwitch: Boolean
    ): String {
        val sb = StringBuilder()
        sb.appendLine("[MilkDrop3]")
        sb.appendLine("version=3.0")
        sb.appendLine("Title=$name")
        sb.appendLine("Author=$author")
        sb.appendLine("Description=$description")
        sb.appendLine("blend_pattern=${pattern.name.lowercase()}")
        sb.appendLine("blend_ratio=$blendRatio")
        sb.appendLine("blend_duration=$blendDuration")
        sb.appendLine("plasma_seed=$plasmaSeed")
        sb.appendLine("direction_locked=${if (isDirectionLocked) "1" else "0"}")
        sb.appendLine("is_flipped=${if (isFlipped) "1" else "0"}")
        sb.appendLine("auto_beat_switch=${if (autoBeatSwitch) "1" else "0"}")
        sb.appendLine()

        sb.appendLine("[PresetA]")
        sb.appendLine("Title=${presetA.name}")
        sb.appendLine("Author=${presetA.author}")
        sb.appendLine("fDecay=${presetA.defaultDecay}")
        sb.appendLine("nWaveMode=${presetA.defaultWaveMode}")
        sb.appendLine()

        sb.appendLine("[PresetB]")
        sb.appendLine("Title=${presetB.name}")
        sb.appendLine("Author=${presetB.author}")
        sb.appendLine("fDecay=${presetB.defaultDecay}")
        sb.appendLine("nWaveMode=${presetB.defaultWaveMode}")
        sb.appendLine()

        return sb.toString()
    }

    /**
     * Curated iconic MilkDrop 3 Double Presets
     */
    fun getCuratedDoublePresets(): List<Milkdrop3DoublePreset> {
        val p1 = MilkdropPresetLibrary.getPresetById(2001) // Feedback Chaos Vortex (Geiss)
        val p2 = MilkdropPresetLibrary.getPresetById(2002) // Cosmic Trip Hyperdrive (Rovastar)
        val p3 = MilkdropPresetLibrary.getPresetById(2003) // Acid Morph Supernova (Zylot)
        val p4 = MilkdropPresetLibrary.getPresetById(2004) // Quantum Tunnel Singularity (Telek)
        val p5 = MilkdropPresetLibrary.getPresetById(2005) // Cyber Grid Neon Horizon (Fvese)
        val p6 = MilkdropPresetLibrary.getPresetById(2006) // Sacred Mandala Resonator (Idiot)
        val p7 = MilkdropPresetLibrary.getPresetById(2007) // Liquid Chrome Metamorphosis (Unchained)
        val p8 = MilkdropPresetLibrary.getPresetById(2008) // Aurora Borealis Celestial (Krash)

        return listOf(
            Milkdrop3DoublePreset(
                id = -3001,
                name = "Chaos Vortex & Hyperdrive",
                author = "Geiss x Rovastar",
                description = "MilkDrop 3 Plasma noise morph between Feedback Chaos Vortex and Starfield Hyperdrive.",
                presetA = p1,
                presetB = p2,
                blendPattern = Milkdrop3BlendPattern.PLASMA,
                blendRatio = 0.5f,
                blendDurationSeconds = 6.0f,
                plasmaSeed = 42.18f,
                autoBeatSwitch = true
            ),
            Milkdrop3DoublePreset(
                id = -3002,
                name = "Quantum Tunnel & Aurora Celestial",
                author = "Telek x Krash",
                description = "Expanding radial iris vortex blend with ethereal cosmic lights.",
                presetA = p4,
                presetB = p8,
                blendPattern = Milkdrop3BlendPattern.RADIAL_IRIS,
                blendRatio = 0.45f,
                blendDurationSeconds = 8.0f,
                plasmaSeed = 18.9f,
                autoBeatSwitch = true
            ),
            Milkdrop3DoublePreset(
                id = -3003,
                name = "Acid Supernova & Sacred Mandala",
                author = "Zylot x Idiot",
                description = "Transient beat pulse surge blend triggering harmonic geometry bursts.",
                presetA = p3,
                presetB = p6,
                blendPattern = Milkdrop3BlendPattern.AUDIO_PULSE,
                blendRatio = 0.4f,
                blendDurationSeconds = 5.0f,
                plasmaSeed = 77.3f,
                autoBeatSwitch = true
            ),
            Milkdrop3DoublePreset(
                id = -3004,
                name = "Cyber Grid & Liquid Chrome",
                author = "Fvese x Unchained",
                description = "Horizontal sine wave scan interweaving retro neon grids with fluid chrome.",
                presetA = p5,
                presetB = p7,
                blendPattern = Milkdrop3BlendPattern.SINE_MORPH,
                blendRatio = 0.5f,
                blendDurationSeconds = 7.0f,
                plasmaSeed = 99.4f,
                autoBeatSwitch = false
            ),
            Milkdrop3DoublePreset(
                id = -3005,
                name = "Kaleidoscopic Singularity",
                author = "MilkDrop 3 Mix",
                description = "Symmetrical kaleidoscopic multi-fold cross-mask between Vortex and Mandala.",
                presetA = p1,
                presetB = p6,
                blendPattern = Milkdrop3BlendPattern.KALEIDO_BLEND,
                blendRatio = 0.55f,
                blendDurationSeconds = 9.0f,
                plasmaSeed = 13.37f,
                autoBeatSwitch = true
            )
        )
    }
}
