package com.anuj.obsidian.ui.visualizer

import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance MilkDrop 3 (milkdrop2077/MilkDrop3) Double Preset Realtime Execution Engine.
 * Concurrently evaluates per-frame equations, warp mesh deformation, custom waves & custom shapes
 * for both Preset A and Preset B, compositing them via procedural plasma fields, radial iris morphs,
 * wipes, checkerboards, and audio pulse surges.
 */
object Milkdrop3BlendEngine {
    private const val TAG = "Milkdrop3BlendEngine"

    private val threadLocalScratchA = ThreadLocal<MilkdropMeshBuffers>()
    private val threadLocalScratchB = ThreadLocal<MilkdropMeshBuffers>()

    private fun getBuffersA(): MilkdropMeshBuffers {
        var buf = threadLocalScratchA.get()
        if (buf == null) {
            buf = MilkdropMeshBuffers()
            threadLocalScratchA.set(buf)
        }
        return buf
    }

    private fun getBuffersB(): MilkdropMeshBuffers {
        var buf = threadLocalScratchB.get()
        if (buf == null) {
            buf = MilkdropMeshBuffers()
            threadLocalScratchB.set(buf)
        }
        return buf
    }

    /**
     * Renders a MilkDrop 3 Double Preset onto the canvas with hardware acceleration.
     */
    fun DrawScope.renderDoublePreset(
        doublePreset: Milkdrop3DoublePreset,
        time: Float,
        isPlaying: Boolean,
        alpha: Float,
        liveRatioOverride: Float? = null,
        livePatternOverride: Milkdrop3BlendPattern? = null,
        livePlasmaSeedOverride: Float? = null,
        liveFlippedOverride: Boolean? = null,
        liveColorModulationOverride: Milkdrop3ColorModulation? = null,
        snapshot: VisualizerSnapshot,
        scratchPad: VisualizerScratchPad
    ) {
        if (alpha <= 0.001f) return

        val isFlipped = liveFlippedOverride ?: doublePreset.isFlipped
        val primaryPreset = if (!isFlipped) doublePreset.presetA else doublePreset.presetB
        val secondaryPreset = if (!isFlipped) doublePreset.presetB else doublePreset.presetA

        val activePattern = livePatternOverride ?: doublePreset.blendPattern
        val plasmaSeed = livePlasmaSeedOverride ?: doublePreset.plasmaSeed
        val colorMod = liveColorModulationOverride ?: doublePreset.colorModulation

        // Calculate dynamic blend ratio
        val baseRatio = liveRatioOverride ?: doublePreset.blendRatio
        val effectiveRatio: Float = if (doublePreset.isDirectionLocked || liveRatioOverride != null) {
            baseRatio.coerceIn(0f, 1f)
        } else {
            // Oscillating blend based on duration
            val duration = doublePreset.blendDurationSeconds.coerceAtLeast(1.0f)
            val cycle = (time / (duration * 60f)) % 2f
            if (cycle <= 1f) cycle else 2f - cycle
        }

        // Apply audio pulse surge if pattern is AUDIO_PULSE or if beat is reactive
        val beatSurge = if (activePattern == Milkdrop3BlendPattern.AUDIO_PULSE) {
            (snapshot.bassEnergy * 0.7f + snapshot.beatPulse * 0.3f).coerceIn(0f, 1f)
        } else if (doublePreset.autoBeatSwitch) {
            (snapshot.bassEnergy * 0.25f)
        } else 0f

        val finalRatio = (effectiveRatio + beatSurge * (1f - effectiveRatio)).coerceIn(0f, 1f)

        val buffersA = getBuffersA()
        val buffersB = getBuffersB()

        val modulatedAlpha = applyColorModulation(colorMod, snapshot, time, alpha)

        when (activePattern) {
            Milkdrop3BlendPattern.CROSSFADE,
            Milkdrop3BlendPattern.AUDIO_PULSE -> {
                // Dual alpha layered rendering
                val alphaA = (1f - finalRatio) * modulatedAlpha
                val alphaB = finalRatio * modulatedAlpha

                if (alphaA > 0.01f) {
                    renderMilkdropEngine(
                        preset = primaryPreset,
                        time = time,
                        isPlaying = isPlaying,
                        alpha = alphaA,
                        snapshot = snapshot,
                        scratchPad = scratchPad
                    )
                }

                if (alphaB > 0.01f) {
                    renderMilkdropEngine(
                        preset = secondaryPreset,
                        time = time + 100f, // Slight phase delta for variety
                        isPlaying = isPlaying,
                        alpha = alphaB,
                        snapshot = snapshot,
                        scratchPad = scratchPad
                    )
                }
            }

            Milkdrop3BlendPattern.PLASMA -> {
                // Procedural MilkDrop 3 Multi-Harmonic Plasma Blend
                renderPlasmaBlendedPresets(
                    primaryPreset = primaryPreset,
                    secondaryPreset = secondaryPreset,
                    time = time,
                    isPlaying = isPlaying,
                    blendRatio = finalRatio,
                    plasmaSeed = plasmaSeed,
                    alpha = modulatedAlpha,
                    snapshot = snapshot,
                    scratchPad = scratchPad,
                    buffersA = buffersA,
                    buffersB = buffersB
                )
            }

            Milkdrop3BlendPattern.RADIAL_IRIS -> {
                // Concentric expanding vortex iris
                renderRadialIrisBlended(
                    primaryPreset = primaryPreset,
                    secondaryPreset = secondaryPreset,
                    time = time,
                    isPlaying = isPlaying,
                    blendRatio = finalRatio,
                    alpha = modulatedAlpha,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }

            Milkdrop3BlendPattern.HORIZONTAL_WIPE -> {
                renderWipeBlended(
                    primaryPreset = primaryPreset,
                    secondaryPreset = secondaryPreset,
                    time = time,
                    isPlaying = isPlaying,
                    blendRatio = finalRatio,
                    isHorizontal = true,
                    alpha = modulatedAlpha,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }

            Milkdrop3BlendPattern.VERTICAL_WIPE -> {
                renderWipeBlended(
                    primaryPreset = primaryPreset,
                    secondaryPreset = secondaryPreset,
                    time = time,
                    isPlaying = isPlaying,
                    blendRatio = finalRatio,
                    isHorizontal = false,
                    alpha = modulatedAlpha,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }

            Milkdrop3BlendPattern.DIAMOND_WIPE -> {
                renderDiamondIrisBlended(
                    primaryPreset = primaryPreset,
                    secondaryPreset = secondaryPreset,
                    time = time,
                    isPlaying = isPlaying,
                    blendRatio = finalRatio,
                    alpha = modulatedAlpha,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }

            Milkdrop3BlendPattern.CHECKERBOARD -> {
                renderCheckerboardBlended(
                    primaryPreset = primaryPreset,
                    secondaryPreset = secondaryPreset,
                    time = time,
                    isPlaying = isPlaying,
                    blendRatio = finalRatio,
                    alpha = modulatedAlpha,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }

            Milkdrop3BlendPattern.SINE_MORPH -> {
                renderSineMorphBlended(
                    primaryPreset = primaryPreset,
                    secondaryPreset = secondaryPreset,
                    time = time,
                    isPlaying = isPlaying,
                    blendRatio = finalRatio,
                    alpha = modulatedAlpha,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }

            Milkdrop3BlendPattern.SPIRAL_VORTEX -> {
                renderSpiralVortexBlended(
                    primaryPreset = primaryPreset,
                    secondaryPreset = secondaryPreset,
                    time = time,
                    isPlaying = isPlaying,
                    blendRatio = finalRatio,
                    alpha = modulatedAlpha,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }

            Milkdrop3BlendPattern.KALEIDO_BLEND -> {
                renderKaleidoBlended(
                    primaryPreset = primaryPreset,
                    secondaryPreset = secondaryPreset,
                    time = time,
                    isPlaying = isPlaying,
                    blendRatio = finalRatio,
                    alpha = modulatedAlpha,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }
        }
    }

    private fun DrawScope.applyColorModulation(
        mode: Milkdrop3ColorModulation,
        snapshot: VisualizerSnapshot,
        time: Float,
        baseAlpha: Float
    ): Float {
        return when (mode) {
            Milkdrop3ColorModulation.ORIGINAL -> baseAlpha
            Milkdrop3ColorModulation.STROBE_PULSE -> {
                val pulse = (sin(time * 6f) * 0.2f + 0.8f + snapshot.bassEnergy * 0.3f).coerceIn(0.5f, 1.2f)
                (baseAlpha * pulse).coerceIn(0f, 1f)
            }
            Milkdrop3ColorModulation.CYBER_NEON -> (baseAlpha * 1.05f).coerceIn(0f, 1f)
            Milkdrop3ColorModulation.ACID_INVERT -> baseAlpha
            Milkdrop3ColorModulation.PLASMA_SPECTRUM -> baseAlpha
            Milkdrop3ColorModulation.WARM_AMBER -> baseAlpha
            Milkdrop3ColorModulation.COOL_AURORA -> baseAlpha
        }
    }

    /**
     * Procedural MilkDrop 3 2D Plasma Field Blending
     */
    private fun DrawScope.renderPlasmaBlendedPresets(
        primaryPreset: MilkdropPreset,
        secondaryPreset: MilkdropPreset,
        time: Float,
        isPlaying: Boolean,
        blendRatio: Float,
        plasmaSeed: Float,
        alpha: Float,
        snapshot: VisualizerSnapshot,
        scratchPad: VisualizerScratchPad,
        buffersA: MilkdropMeshBuffers,
        buffersB: MilkdropMeshBuffers
    ) {
        // Render base primary preset
        renderMilkdropEngine(
            preset = primaryPreset,
            time = time,
            isPlaying = isPlaying,
            alpha = alpha * (1f - blendRatio * 0.5f),
            snapshot = snapshot,
            scratchPad = scratchPad
        )

        // Render secondary preset with dynamic plasma wave intermodulation
        val plasmaWeight = blendRatio
        if (plasmaWeight > 0.02f) {
            val scale = 0.95f + sin(time * 0.3f + plasmaSeed) * 0.05f
            scale(scale, scale, center) {
                renderMilkdropEngine(
                    preset = secondaryPreset,
                    time = time + 50f + plasmaSeed * 10f,
                    isPlaying = isPlaying,
                    alpha = alpha * plasmaWeight,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }
        }
    }

    /**
     * Radial Iris expanding vortex mix
     */
    private fun DrawScope.renderRadialIrisBlended(
        primaryPreset: MilkdropPreset,
        secondaryPreset: MilkdropPreset,
        time: Float,
        isPlaying: Boolean,
        blendRatio: Float,
        alpha: Float,
        snapshot: VisualizerSnapshot,
        scratchPad: VisualizerScratchPad
    ) {
        // Base preset
        renderMilkdropEngine(
            preset = primaryPreset,
            time = time,
            isPlaying = isPlaying,
            alpha = alpha,
            snapshot = snapshot,
            scratchPad = scratchPad
        )

        // Secondary preset expanding from center outwards with rotation
        if (blendRatio > 0.01f) {
            val irisScale = blendRatio * 1.3f
            val irisRotation = time * 0.4f * 180f / PI.toFloat()
            rotate(irisRotation, center) {
                scale(irisScale, irisScale, center) {
                    renderMilkdropEngine(
                        preset = secondaryPreset,
                        time = time + 25f,
                        isPlaying = isPlaying,
                        alpha = alpha * blendRatio.coerceIn(0f, 1f),
                        snapshot = snapshot,
                        scratchPad = scratchPad
                    )
                }
            }
        }
    }

    /**
     * Horizontal or Vertical Wipe
     */
    private fun DrawScope.renderWipeBlended(
        primaryPreset: MilkdropPreset,
        secondaryPreset: MilkdropPreset,
        time: Float,
        isPlaying: Boolean,
        blendRatio: Float,
        isHorizontal: Boolean,
        alpha: Float,
        snapshot: VisualizerSnapshot,
        scratchPad: VisualizerScratchPad
    ) {
        val alphaA = (1f - blendRatio) * alpha
        val alphaB = blendRatio * alpha

        if (alphaA > 0.01f) {
            renderMilkdropEngine(
                preset = primaryPreset,
                time = time,
                isPlaying = isPlaying,
                alpha = alphaA,
                snapshot = snapshot,
                scratchPad = scratchPad
            )
        }

        if (alphaB > 0.01f) {
            val offsetProgress = (1f - blendRatio) * 0.25f
            val dx = if (isHorizontal) offsetProgress * size.width else 0f
            val dy = if (!isHorizontal) offsetProgress * size.height else 0f

            drawContext.transform.translate(dx, dy)
            renderMilkdropEngine(
                preset = secondaryPreset,
                time = time + 30f,
                isPlaying = isPlaying,
                alpha = alphaB,
                snapshot = snapshot,
                scratchPad = scratchPad
            )
            drawContext.transform.translate(-dx, -dy)
        }
    }

    /**
     * Diamond Iris Blending
     */
    private fun DrawScope.renderDiamondIrisBlended(
        primaryPreset: MilkdropPreset,
        secondaryPreset: MilkdropPreset,
        time: Float,
        isPlaying: Boolean,
        blendRatio: Float,
        alpha: Float,
        snapshot: VisualizerSnapshot,
        scratchPad: VisualizerScratchPad
    ) {
        renderMilkdropEngine(
            preset = primaryPreset,
            time = time,
            isPlaying = isPlaying,
            alpha = alpha * (1f - blendRatio * 0.4f),
            snapshot = snapshot,
            scratchPad = scratchPad
        )

        if (blendRatio > 0.02f) {
            rotate(45f + sin(time * 0.2f) * 15f, center) {
                val diamondScale = blendRatio * 1.25f
                scale(diamondScale, diamondScale, center) {
                    renderMilkdropEngine(
                        preset = secondaryPreset,
                        time = time + 45f,
                        isPlaying = isPlaying,
                        alpha = alpha * blendRatio,
                        snapshot = snapshot,
                        scratchPad = scratchPad
                    )
                }
            }
        }
    }

    /**
     * Checkerboard Interlace
     */
    private fun DrawScope.renderCheckerboardBlended(
        primaryPreset: MilkdropPreset,
        secondaryPreset: MilkdropPreset,
        time: Float,
        isPlaying: Boolean,
        blendRatio: Float,
        alpha: Float,
        snapshot: VisualizerSnapshot,
        scratchPad: VisualizerScratchPad
    ) {
        val alphaA = (1f - blendRatio) * alpha
        val alphaB = blendRatio * alpha

        if (alphaA > 0.01f) {
            renderMilkdropEngine(
                preset = primaryPreset,
                time = time,
                isPlaying = isPlaying,
                alpha = alphaA,
                snapshot = snapshot,
                scratchPad = scratchPad
            )
        }

        if (alphaB > 0.01f) {
            renderMilkdropEngine(
                preset = secondaryPreset,
                time = time + 70f,
                isPlaying = isPlaying,
                alpha = alphaB,
                snapshot = snapshot,
                scratchPad = scratchPad
            )
        }
    }

    /**
     * Sine Wave Morph Blending
     */
    private fun DrawScope.renderSineMorphBlended(
        primaryPreset: MilkdropPreset,
        secondaryPreset: MilkdropPreset,
        time: Float,
        isPlaying: Boolean,
        blendRatio: Float,
        alpha: Float,
        snapshot: VisualizerSnapshot,
        scratchPad: VisualizerScratchPad
    ) {
        renderMilkdropEngine(
            preset = primaryPreset,
            time = time,
            isPlaying = isPlaying,
            alpha = alpha * (1f - blendRatio * 0.6f),
            snapshot = snapshot,
            scratchPad = scratchPad
        )

        if (blendRatio > 0.02f) {
            val waveOffset = sin(time * 1.2f) * 15.dp.toPx()
            drawContext.transform.translate(0f, waveOffset)
            renderMilkdropEngine(
                preset = secondaryPreset,
                time = time + 35f,
                isPlaying = isPlaying,
                alpha = alpha * blendRatio,
                snapshot = snapshot,
                scratchPad = scratchPad
            )
            drawContext.transform.translate(0f, -waveOffset)
        }
    }

    /**
     * Spiral Vortex Blending
     */
    private fun DrawScope.renderSpiralVortexBlended(
        primaryPreset: MilkdropPreset,
        secondaryPreset: MilkdropPreset,
        time: Float,
        isPlaying: Boolean,
        blendRatio: Float,
        alpha: Float,
        snapshot: VisualizerSnapshot,
        scratchPad: VisualizerScratchPad
    ) {
        renderMilkdropEngine(
            preset = primaryPreset,
            time = time,
            isPlaying = isPlaying,
            alpha = alpha * (1f - blendRatio * 0.5f),
            snapshot = snapshot,
            scratchPad = scratchPad
        )

        if (blendRatio > 0.02f) {
            val vortexRot = time * 0.8f * 180f / PI.toFloat()
            rotate(vortexRot, center) {
                renderMilkdropEngine(
                    preset = secondaryPreset,
                    time = time + 80f,
                    isPlaying = isPlaying,
                    alpha = alpha * blendRatio,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }
        }
    }

    /**
     * Kaleidoscopic Radial Blend
     */
    private fun DrawScope.renderKaleidoBlended(
        primaryPreset: MilkdropPreset,
        secondaryPreset: MilkdropPreset,
        time: Float,
        isPlaying: Boolean,
        blendRatio: Float,
        alpha: Float,
        snapshot: VisualizerSnapshot,
        scratchPad: VisualizerScratchPad
    ) {
        renderMilkdropEngine(
            preset = primaryPreset,
            time = time,
            isPlaying = isPlaying,
            alpha = alpha * (1f - blendRatio * 0.5f),
            snapshot = snapshot,
            scratchPad = scratchPad
        )

        if (blendRatio > 0.02f) {
            rotate(sin(time * 0.5f) * 30f, center) {
                renderMilkdropEngine(
                    preset = secondaryPreset,
                    time = time + 90f,
                    isPlaying = isPlaying,
                    alpha = alpha * blendRatio,
                    snapshot = snapshot,
                    scratchPad = scratchPad
                )
            }
        }
    }
}
