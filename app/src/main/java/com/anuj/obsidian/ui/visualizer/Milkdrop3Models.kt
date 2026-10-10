package com.anuj.obsidian.ui.visualizer

import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * Blend patterns supported by MilkDrop 3 (milkdrop2077/MilkDrop3) for dual preset mixing.
 */
enum class Milkdrop3BlendPattern(val displayName: String, val description: String) {
    PLASMA("Plasma Noise", "Procedural 2D multi-harmonic plasma wave distortion"),
    CROSSFADE("Cross-Dissolve", "Smooth linear alpha blend between presets"),
    RADIAL_IRIS("Radial Iris", "Expanding/contracting circular iris with spiral vortex"),
    HORIZONTAL_WIPE("Horizontal Wipe", "Soft-edged left-to-right curtain transition"),
    VERTICAL_WIPE("Vertical Wipe", "Soft-edged top-to-bottom curtain transition"),
    DIAMOND_WIPE("Diamond Wipe", "Expanding Manhattan-metric diamond iris"),
    CHECKERBOARD("Checkerboard", "Interlaced alternating grid mask"),
    AUDIO_PULSE("Audio Pulse Surge", "Transient beat drops surge Preset B over Preset A"),
    SINE_MORPH("Sine Wave Morph", "Horizontal undulating sinusoidal wave bands"),
    SPIRAL_VORTEX("Spiral Vortex", "Logarithmic twisting spatial spiral morph"),
    KALEIDO_BLEND("Kaleido Blend", "Symmetrical multi-fold kaleidoscopic cross-mask")
}

/**
 * Real-time color modulation and palette shifting modes in MilkDrop 3.
 */
enum class Milkdrop3ColorModulation(val displayName: String) {
    ORIGINAL("Original Colors"),
    CYBER_NEON("Cyber Neon Boost"),
    ACID_INVERT("Acid Invert & Solarize"),
    STROBE_PULSE("Strobe Beat Flash"),
    PLASMA_SPECTRUM("Rainbow Plasma Hue"),
    WARM_AMBER("Warm Amber Glow"),
    COOL_AURORA("Cool Aurora Cyan")
}

/**
 * Represents a MilkDrop 3 Double Preset (.milk2) combining two distinct presets.
 */
data class Milkdrop3DoublePreset(
    val id: Int,
    val name: String,
    val author: String = "MilkDrop 3",
    val description: String = "",
    val presetA: MilkdropPreset,
    val presetB: MilkdropPreset,
    val blendPattern: Milkdrop3BlendPattern = Milkdrop3BlendPattern.PLASMA,
    val blendRatio: Float = 0.5f,
    val blendDurationSeconds: Float = 6.0f,
    val plasmaSeed: Float = 1.0f,
    val isDirectionLocked: Boolean = false,
    val isFlipped: Boolean = false,
    val autoBeatSwitch: Boolean = false,
    val colorModulation: Milkdrop3ColorModulation = Milkdrop3ColorModulation.ORIGINAL,
    val sourceFileName: String? = null,
    val sourceFile: File? = null
)
