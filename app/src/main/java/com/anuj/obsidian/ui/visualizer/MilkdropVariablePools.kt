package com.anuj.obsidian.ui.visualizer

import kotlin.math.max
import kotlin.math.min

/**
 * =========================================================================================
 * OBSIDIAN MILKDROP VARIABLE POOLS & EXECUTION CONTEXT
 * =========================================================================================
 * Strict implementation of MilkDrop 2.0+ variable persistence semantics:
 * - Base Q-values: Established by preset init once.
 * - Start of frame: Q1..Q32 are restored to the base Q-values.
 * - Per-frame execution: Modifies Q1..Q32 and camera/warp/wave parameters.
 * - Later stages in the same frame (Per-Vertex, Custom Waves, Custom Shapes, Shaders):
 *   Observe the updated Q1..Q32 from the per-frame stage.
 * - Frame boundary: Per-frame Q mutations DO NOT bleed into the next frame's initial Q values.
 * - Persistent user variables: Non-Q user accumulators retain their state across frames.
 * - T-Variables (T1..T8): Separate local storage for Custom Wave and Custom Shape pools.
 * - Zero allocations: Preallocated registers per pool.
 * =========================================================================================
 */

/**
 * Manages the PRESET POOL (Preset Init & Per-Frame).
 */
class PresetVariablePool(totalSlots: Int = 256) {
    val registers = FloatArray(max(totalSlots, 256))
    val baseQRegisters = FloatArray(32) // Preserves base Q-values from preset init
    val frameQRegisters = FloatArray(32) // Current frame Q-values

    private val userPersistentMask = BooleanArray(registers.size)

    fun reset() {
        registers.fill(0f)
        baseQRegisters.fill(0f)
        frameQRegisters.fill(0f)
        userPersistentMask.fill(false)
    }

    /**
     * Called once after running the Preset Init program to snapshot the base Q-values.
     */
    fun snapshotBaseQ(qSlotIndices: IntArray) {
        for (i in 0 until 32) {
            val slot = if (i < qSlotIndices.size) qSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                baseQRegisters[i] = registers[slot]
                frameQRegisters[i] = registers[slot]
            }
        }
    }

    /**
     * Called at the start of each frame before Per-Frame execution:
     * Maintains Q1..Q32 persistent state from the previous frame.
     */
    fun beginFrame(qSlotIndices: IntArray) {
        for (i in 0 until 32) {
            val slot = if (i < qSlotIndices.size) qSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                // Do NOT restore registers[slot] to baseQRegisters! Q-values are persistent and evolve.
                frameQRegisters[i] = registers[slot]
            } else {
                frameQRegisters[i] = baseQRegisters[i]
            }
        }
    }

    /**
     * Called immediately after Per-Frame execution:
     * Captures the per-frame modified Q1..Q32 so they can be propagated to vertex, waves, shapes, and shaders.
     */
    fun endPerFrame(qSlotIndices: IntArray) {
        for (i in 0 until 32) {
            val slot = if (i < qSlotIndices.size) qSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                frameQRegisters[i] = registers[slot]
            }
        }
    }
}

/**
 * Manages the VERTEX POOL (Per-Vertex Warp Mesh).
 * Reuses a single preallocated FloatArray to achieve zero heap allocation across all grid vertices.
 */
class VertexVariablePool(totalSlots: Int = 256) {
    val registers = FloatArray(max(totalSlots, 256))

    /**
     * Prepare registers for vertex evaluation using the frame state and current Q-values.
     */
    fun prepareForVertex(
        presetPool: PresetVariablePool,
        qSlotIndices: IntArray,
        time: Float,
        frame: Float,
        fps: Float,
        progress: Float,
        bass: Float,
        mid: Float,
        treb: Float,
        bassAtt: Float,
        midAtt: Float,
        trebAtt: Float
    ) {
        // Copy base registers from preset pool
        System.arraycopy(presetPool.registers, 0, registers, 0, min(presetPool.registers.size, registers.size))

        // Ensure Q-values match the per-frame evaluated values
        for (i in 0 until 32) {
            val slot = if (i < qSlotIndices.size) qSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                registers[slot] = presetPool.frameQRegisters[i]
            }
        }
    }
}

/**
 * Manages the WAVE POOL (Wave Init, Wave Per-Frame, Wave Per-Point).
 */
class WaveVariablePool(totalSlots: Int = 256) {
    val registers = FloatArray(max(totalSlots, 256))
    val tRegisters = FloatArray(8) // T1..T8 for this wave instance

    fun reset() {
        registers.fill(0f)
        tRegisters.fill(0f)
    }

    fun syncFromPresetPool(presetPool: PresetVariablePool, qSlotIndices: IntArray, tSlotIndices: IntArray) {
        System.arraycopy(presetPool.registers, 0, registers, 0, min(presetPool.registers.size, registers.size))
        for (i in 0 until 32) {
            val slot = if (i < qSlotIndices.size) qSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                registers[slot] = presetPool.frameQRegisters[i]
            }
        }
        for (i in 0 until 8) {
            val slot = if (i < tSlotIndices.size) tSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                registers[slot] = tRegisters[i]
            }
        }
    }

    fun syncTRegistersBack(tSlotIndices: IntArray) {
        for (i in 0 until 8) {
            val slot = if (i < tSlotIndices.size) tSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                tRegisters[i] = registers[slot]
            }
        }
    }
}

/**
 * Manages the SHAPE POOL (Shape Init & Shape Per-Frame).
 */
class ShapeVariablePool(totalSlots: Int = 256) {
    val registers = FloatArray(max(totalSlots, 256))
    val tRegisters = FloatArray(8) // T1..T8 for this shape instance

    fun reset() {
        registers.fill(0f)
        tRegisters.fill(0f)
    }

    fun syncFromPresetPool(presetPool: PresetVariablePool, qSlotIndices: IntArray, tSlotIndices: IntArray) {
        System.arraycopy(presetPool.registers, 0, registers, 0, min(presetPool.registers.size, registers.size))
        for (i in 0 until 32) {
            val slot = if (i < qSlotIndices.size) qSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                registers[slot] = presetPool.frameQRegisters[i]
            }
        }
        for (i in 0 until 8) {
            val slot = if (i < tSlotIndices.size) tSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                registers[slot] = tRegisters[i]
            }
        }
    }

    fun syncTRegistersBack(tSlotIndices: IntArray) {
        for (i in 0 until 8) {
            val slot = if (i < tSlotIndices.size) tSlotIndices[i] else -1
            if (slot >= 0 && slot < registers.size) {
                tRegisters[i] = registers[slot]
            }
        }
    }
}

/**
 * Unified context passing state between preset execution, waves, shapes, and GL shaders.
 */
class MilkRuntimeState {
    var time: Float = 0f
    var frame: Long = 0L
    var fps: Float = 60f
    var progress: Float = 0f

    var bass: Float = 1f
    var mid: Float = 1f
    var treb: Float = 1f
    var bassAtt: Float = 1f
    var midAtt: Float = 1f
    var trebAtt: Float = 1f

    // Evaluated per-frame dynamic camera and deformation vars
    var zoom: Float = 1.0f
    var zoomExp: Float = 1.0f
    var rot: Float = 0.0f
    var warp: Float = 0.0f
    var warpAnimSpeed: Float = 1.0f
    var warpScale: Float = 1.0f
    var cx: Float = 0.5f
    var cy: Float = 0.5f
    var dx: Float = 0.0f
    var dy: Float = 0.0f
    var sx: Float = 1.0f
    var sy: Float = 1.0f
    var decay: Float = 0.96f

    // Evaluated waveform vars
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

    // Borders & Echoes
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

    val qVars = FloatArray(32)

    fun resetToDefaults(staticParams: MilkStaticParameters) {
        zoom = staticParams.zoom
        zoomExp = staticParams.zoomExp
        rot = staticParams.rot
        warp = staticParams.warp
        warpAnimSpeed = staticParams.warpAnimSpeed
        warpScale = staticParams.warpScale
        cx = staticParams.cx
        cy = staticParams.cy
        dx = staticParams.dx
        dy = staticParams.dy
        sx = staticParams.sx
        sy = staticParams.sy
        decay = staticParams.decay
        waveMode = staticParams.waveMode
        waveR = staticParams.waveR
        waveG = staticParams.waveG
        waveB = staticParams.waveB
        waveA = staticParams.waveA
        waveX = staticParams.waveX
        waveY = staticParams.waveY
        waveScale = staticParams.waveScale
        waveMystery = staticParams.waveMystery
        waveThick = staticParams.waveThick
        obSize = staticParams.obSize
        obR = staticParams.obR
        obG = staticParams.obG
        obB = staticParams.obB
        obA = staticParams.obA
        ibSize = staticParams.ibSize
        ibR = staticParams.ibR
        ibG = staticParams.ibG
        ibB = staticParams.ibB
        ibA = staticParams.ibA
        mvX = staticParams.mvX
        mvY = staticParams.mvY
        mvDx = staticParams.mvDx
        mvDy = staticParams.mvDy
        mvL = staticParams.mvL
        mvR = staticParams.mvR
        mvG = staticParams.mvG
        mvB = staticParams.mvB
        mvA = staticParams.mvA
        echoZoom = staticParams.echoZoom
        echoAlpha = staticParams.echoAlpha
        echoOrient = staticParams.echoOrient
        System.arraycopy(staticParams.qInit, 0, qVars, 0, 32)
    }
}
