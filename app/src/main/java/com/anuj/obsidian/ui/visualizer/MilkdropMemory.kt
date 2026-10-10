package com.anuj.obsidian.ui.visualizer

import java.util.concurrent.atomic.AtomicInteger

/**
 * =========================================================================================
 * OBSIDIAN MILKDROP GLOBAL & PRESET PERSISTENT MEMORY (gmegabuf / megabuf)
 * =========================================================================================
 * MilkDrop provides two indexed global memory spaces for advanced algorithmic presets:
 * 1. `gmegabuf(index)`: Global persistent memory (1,048,576 floats = 1M elements), shared and
 *    persistent across frames and preset switches unless explicitly cleared.
 * 2. `megabuf(index)`: Per-preset persistent memory (1,048,576 floats), preserved across frames
 *    for a single preset lifecycle, initialized to zero on preset load.
 *
 * Requirements:
 * - Thread-safe, deterministic bounds checking (0 until 1_048_576)
 * - Zero allocations during read/write
 * - Support dynamic indices calculated from mathematical expressions
 * =========================================================================================
 */
object MilkdropGlobalMemory {
    const val BUFFER_SIZE = 1_048_576 // 1M floats = 4MB
    val gmegabuf = FloatArray(BUFFER_SIZE)

    fun read(index: Int): Float {
        return if (index in 0 until BUFFER_SIZE) gmegabuf[index] else 0f
    }

    fun read(index: Float): Float {
        if (index < 0f || index >= BUFFER_SIZE.toFloat() || index.isNaN()) return 0f
        val idx = index.toInt()
        return gmegabuf[idx]
    }

    fun write(index: Int, value: Float) {
        if (index in 0 until BUFFER_SIZE) {
            gmegabuf[index] = value
        }
    }

    fun write(index: Float, value: Float) {
        if (index < 0f || index >= BUFFER_SIZE.toFloat() || index.isNaN()) return
        val idx = index.toInt()
        gmegabuf[idx] = value
    }

    fun clear() {
        gmegabuf.fill(0f)
    }

    fun reset() {
        clear()
    }
}

/**
 * Per-preset megabuffer instance.
 */
class MilkdropPresetMemory {
    val megabuf = FloatArray(MilkdropGlobalMemory.BUFFER_SIZE)

    fun read(index: Int): Float {
        return if (index in 0 until MilkdropGlobalMemory.BUFFER_SIZE) megabuf[index] else 0f
    }

    fun read(index: Float): Float {
        if (index < 0f || index >= MilkdropGlobalMemory.BUFFER_SIZE.toFloat() || index.isNaN()) return 0f
        val idx = index.toInt()
        return megabuf[idx]
    }

    fun write(index: Int, value: Float) {
        if (index in 0 until MilkdropGlobalMemory.BUFFER_SIZE) {
            megabuf[index] = value
        }
    }

    fun write(index: Float, value: Float) {
        if (index < 0f || index >= MilkdropGlobalMemory.BUFFER_SIZE.toFloat() || index.isNaN()) return
        val idx = index.toInt()
        megabuf[idx] = value
    }

    fun reset() {
        megabuf.fill(0f)
    }
}
