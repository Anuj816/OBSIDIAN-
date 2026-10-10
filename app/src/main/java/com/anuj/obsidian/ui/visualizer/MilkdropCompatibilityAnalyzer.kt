package com.anuj.obsidian.ui.visualizer

/**
 * Level of compatibility for a parsed Milkdrop preset.
 */
enum class PresetCompatibilityLevel {
    SUPPORTED,
    PARTIAL,
    UNSUPPORTED
}

data class FeatureCompatibility(
    val featureName: String,
    val isSupported: Boolean,
    val details: String
)

data class PresetCompatibilityReport(
    val level: PresetCompatibilityLevel,
    val summary: String,
    val features: List<FeatureCompatibility>,
    val unsupportedFeatures: List<String>,
    val suggestedFallbackMode: String = "GPU Hardware Fullscreen Mesh"
)

object MilkdropCompatibilityAnalyzer {

    fun analyze(preset: MilkPresetIR): PresetCompatibilityReport {
        val features = mutableListOf<FeatureCompatibility>()
        val unsupported = mutableListOf<String>()

        // 1. Math expressions
        val hasErrors = preset.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
        val hasWarnings = preset.diagnostics.any { it.severity == DiagnosticSeverity.WARNING }
        features.add(
            FeatureCompatibility(
                "Math Expressions",
                !hasErrors,
                if (hasErrors) "Errors encountered during AST parsing" else if (hasWarnings) "Parsed with non-fatal warnings" else "100% Valid AST"
            )
        )
        if (hasErrors) unsupported.add("Syntax errors in mathematical equations")

        // 2. Per-Frame Equations
        val hasPerFrame = preset.perFrameProgram != null && preset.perFrameProgram.statements.isNotEmpty()
        features.add(
            FeatureCompatibility(
                "Per-Frame Logic",
                true,
                if (hasPerFrame) "${preset.perFrameProgram?.statements?.size} compiled statements" else "Static defaults"
            )
        )

        // 3. Per-Vertex Warp Mesh Logic
        val hasPerVertex = preset.perVertexProgram != null && preset.perVertexProgram.statements.isNotEmpty()
        features.add(
            FeatureCompatibility(
                "Per-Vertex Mesh Warp",
                true,
                if (hasPerVertex) "${preset.perVertexProgram?.statements?.size} per-vertex statements" else "Direct affine camera matrix"
            )
        )

        // 4. Custom Waves
        val activeWaves = preset.customWaves.count { it.enabled || it.perPointProgram != null }
        features.add(
            FeatureCompatibility(
                "Custom Waves (0..3)",
                true,
                "$activeWaves active wave generators"
            )
        )

        // 5. Custom Shapes
        val activeShapes = preset.customShapes.count { it.enabled || it.perFrameProgram != null }
        features.add(
            FeatureCompatibility(
                "Custom Shapes (0..3)",
                true,
                "$activeShapes active geometric shapes"
            )
        )

        // 6. Global & Preset Memory (gmegabuf / megabuf)
        val allPrograms = listOfNotNull(
            preset.presetInitProgram,
            preset.perFrameProgram,
            preset.perVertexProgram
        ) + preset.customWaves.mapNotNull { it.perFrameProgram } +
            preset.customWaves.mapNotNull { it.perPointProgram } +
            preset.customShapes.mapNotNull { it.perFrameProgram }

        val hasMemoryOps = allPrograms.any { prog ->
            prog.statements.any { stmt ->
                stmt is CompiledStatement.GMegabufWrite || stmt is CompiledStatement.MegabufWrite
            }
        }
        features.add(
            FeatureCompatibility(
                "Global/Preset Memory (megabuf)",
                true,
                if (hasMemoryOps) "Active megabuf/gmegabuf reads and writes supported (1M array capacity)" else "None used (Static)"
            )
        )

        // 7. Loop Constructs
        val hasLoops = allPrograms.any { prog ->
            prog.statements.any { stmt -> stmt is CompiledStatement.LoopStatement }
        }
        features.add(
            FeatureCompatibility(
                "Iteration & Loops",
                true,
                if (hasLoops) "Active loop(...) constructs compiled and supported" else "Linear execution flow"
            )
        )

        // 8. Warp & Composite Shaders
        val hasWarpShader = preset.warpShader != null && preset.warpShader.isValid
        val hasCompShader = preset.compositeShader != null && preset.compositeShader.isValid
        features.add(
            FeatureCompatibility(
                "Warp Shader",
                hasWarpShader || preset.warpShader == null,
                if (hasWarpShader) "Valid GLSL ES Warp Shader (PS ${preset.metadata.psVersionWarp})" else if (preset.warpShader != null) "Fallback procedural warp" else "Standard feedback warp"
            )
        )
        features.add(
            FeatureCompatibility(
                "Composite Shader",
                hasCompShader || preset.compositeShader == null,
                if (hasCompShader) "Valid GLSL ES Composite Shader (PS ${preset.metadata.psVersionComp})" else if (preset.compositeShader != null) "Fallback composite" else "Standard color composite"
            )
        )

        // 9. Ping-Pong Framebuffer Feedback
        features.add(
            FeatureCompatibility(
                "Framebuffer Feedback & Decay",
                true,
                "Persistent ping-pong texture loops supported (Decay = ${preset.staticParams.decay})"
            )
        )

        val level = when {
            hasErrors -> PresetCompatibilityLevel.UNSUPPORTED
            hasWarnings || (preset.warpShader != null && !preset.warpShader.isValid) -> PresetCompatibilityLevel.PARTIAL
            else -> PresetCompatibilityLevel.SUPPORTED
        }

        val summary = when (level) {
            PresetCompatibilityLevel.SUPPORTED -> "Fully compatible with Obsidian OpenGL ES 2.0/3.0 MilkDrop runtime."
            PresetCompatibilityLevel.PARTIAL -> "Compatible with fallback handling for unparsed shader blocks or minor warnings."
            PresetCompatibilityLevel.UNSUPPORTED -> "Contains malformed code or incompatible features; running with best-effort fallbacks."
        }

        return PresetCompatibilityReport(
            level = level,
            summary = summary,
            features = features,
            unsupportedFeatures = unsupported,
            suggestedFallbackMode = if (level == PresetCompatibilityLevel.SUPPORTED) "Direct GPU Shader Pipeline" else "GPU Mesh + Procedural Fallback"
        )
    }
}
