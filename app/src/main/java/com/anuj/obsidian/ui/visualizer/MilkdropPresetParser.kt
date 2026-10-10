package com.anuj.obsidian.ui.visualizer

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import java.io.File

data class ParsedMilkdropPreset(
    val name: String,
    val author: String,
    val description: String,
    val sourceFile: File?,
    val referencedTextures: List<String> = emptyList(),
    val customScript: String? = null,
    val preset: MilkdropPreset,
    val presetIR: MilkPresetIR? = null,
    val isDoublePreset: Boolean = false,
    val doublePreset: Milkdrop3DoublePreset? = null,
    val rawMilkContent: String? = null
)

/**
 * =========================================================================================
 * OBSIDIAN SPEC-COMPLIANT MILKDROP PRESET PARSER
 * =========================================================================================
 * Robust, case-insensitive, fault-tolerant parser producing MilkPresetIR with structured diagnostics.
 * =========================================================================================
 */
object MilkdropPresetParser {

    private const val TAG = "MilkdropPresetParser"

    fun discoverAllPresets(
        context: Context,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<ParsedMilkdropPreset> {
        val irs = discoverAllPresetIRs(context, onProgress)
        return irs.map { ir ->
            val legacy = irToLegacyPreset(ir)
            val doublePreset = if (ir.metadata.isDoublePreset && ir.secondaryPresetIR != null) {
                val legacyB = irToLegacyPreset(ir.secondaryPresetIR)
                val meta = ir.metadata.doublePresetMeta
                val pattern = when (meta?.patternName?.uppercase()) {
                    "PLASMA" -> Milkdrop3BlendPattern.PLASMA
                    "RADIAL", "RADIAL_IRIS" -> Milkdrop3BlendPattern.RADIAL_IRIS
                    "WIPE", "HORIZONTAL_WIPE" -> Milkdrop3BlendPattern.HORIZONTAL_WIPE
                    "VERTICAL_WIPE" -> Milkdrop3BlendPattern.VERTICAL_WIPE
                    else -> Milkdrop3BlendPattern.CROSSFADE
                }
                Milkdrop3DoublePreset(
                    id = ir.metadata.id,
                    name = ir.metadata.name,
                    author = ir.metadata.author,
                    description = ir.metadata.description,
                    presetA = legacy,
                    presetB = legacyB,
                    blendPattern = pattern,
                    blendRatio = meta?.blendRatio ?: 0.5f,
                    blendDurationSeconds = meta?.blendDuration ?: 6.0f,
                    plasmaSeed = meta?.plasmaSeed ?: 1.0f,
                    isDirectionLocked = meta?.isDirectionLocked ?: false,
                    isFlipped = meta?.isFlipped ?: false,
                    autoBeatSwitch = meta?.autoBeatSwitch ?: false,
                    sourceFile = ir.metadata.sourceFile
                )
            } else null

            val content = ir.metadata.sourceFile?.let {
                if (it.exists() && it.canRead()) {
                    runCatching { it.readText(Charsets.UTF_8) }
                        .getOrElse { _ -> runCatching { it.readBytes().toString(Charsets.ISO_8859_1) }.getOrNull() }
                } else null
            }
            val cacheFile = if (!content.isNullOrBlank()) {
                val fileName = ir.metadata.sourceFile?.name ?: "${ir.metadata.name}.milk"
                MilkdropFileStructure.getNativeCachePresetFile(context, fileName, content)
            } else {
                ir.metadata.sourceFile
            }

            ParsedMilkdropPreset(
                name = ir.metadata.name,
                author = ir.metadata.author,
                description = ir.metadata.description,
                sourceFile = cacheFile,
                referencedTextures = emptyList(),
                customScript = null,
                preset = legacy,
                presetIR = ir,
                isDoublePreset = ir.metadata.isDoublePreset,
                doublePreset = doublePreset,
                rawMilkContent = content
            )
        }
    }

    fun parsePresetFile(file: File, context: Context): MilkPresetIR? {
        return try {
            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "Cannot parse empty or missing preset file: ${file.absolutePath}")
                return null
            }
            val content = try {
                file.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                file.readBytes().toString(Charsets.ISO_8859_1)
            }
            val cacheFile = MilkdropFileStructure.getNativeCachePresetFile(context, file.name, content)
            parseContent(content, file.nameWithoutExtension, cacheFile)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed reading preset file ${file.name}: ${e.message}")
            null
        }
    }

    fun parse(
        content: String,
        fallbackTitle: String = "Preset",
        sourceFile: File? = null
    ): MilkPresetIR = parseContent(content, fallbackTitle, sourceFile)

    fun parseContent(
        content: String,
        fallbackTitle: String,
        sourceFile: File? = null
    ): MilkPresetIR {
        val diagnostics = mutableListOf<PresetDiagnostic>()
        val lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n")

        var title = fallbackTitle
        var author = "Unknown"
        var description = ""
        var version = 2.0f
        var isDoublePreset = false
        var doubleMeta: Milkdrop3DoublePresetMeta? = null

        // Check if this is a MilkDrop 3 double-preset file
        if (content.contains("[MilkDrop3]", ignoreCase = true) || content.contains("[PresetA]", ignoreCase = true)) {
            isDoublePreset = true
            version = 3.0f
        }

        if (isDoublePreset) {
            return parseDoublePresetContent(lines, title, author, description, sourceFile, diagnostics)
        }

        return parseSinglePreset(lines, title, author, description, version, sourceFile, diagnostics)
    }

    private fun parseSinglePreset(
        lines: List<String>,
        initialTitle: String,
        initialAuthor: String,
        initialDescription: String,
        version: Float,
        sourceFile: File?,
        diagnostics: MutableList<PresetDiagnostic>
    ): MilkPresetIR {
        var presetTitle = initialTitle
        var presetAuthor = initialAuthor
        var presetDescription = initialDescription
        var parsedPresetVersion = 200
        var psVersion = 2
        var psVersionWarp = 2
        var psVersionComp = 2

        val staticParams = MilkStaticParameters()

        val presetInitLines = mutableListOf<String>()
        val perFrameLines = mutableListOf<String>()
        val perPixelLines = mutableListOf<String>()
        val warpShaderLines = mutableListOf<String>()
        val compShaderLines = mutableListOf<String>()

        class RawWaveCollector {
            var enabled = false
            var samples = 512
            var sep = 0
            var bSpectrum = false
            var bUseDots = false
            var bDrawThick = true
            var bAdditive = false
            var scaling = 1.0f
            var smoothing = 0.5f
            var r = 1.0f
            var g = 1.0f
            var b = 1.0f
            var a = 1.0f
            val initLines = mutableListOf<String>()
            val perFrameLines = mutableListOf<String>()
            val perPointLines = mutableListOf<String>()
        }
        val waveCollectors = Array(4) { RawWaveCollector() }

        class RawShapeCollector {
            var enabled = false
            var numInst = 1
            var sides = 4
            var thick = true
            var additive = false
            var textured = false
            var texZoom = 1.0f
            var texAng = 0.0f
            var x = 0.5f
            var y = 0.5f
            var rad = 0.15f
            var ang = 0.0f
            var r = 1.0f
            var g = 1.0f
            var b = 1.0f
            var a = 0.85f
            var r2 = 0.0f
            var g2 = 0.0f
            var b2 = 0.0f
            var a2 = 0.0f
            var borderR = 1.0f
            var borderG = 1.0f
            var borderB = 1.0f
            var borderA = 0.9f
            val initLines = mutableListOf<String>()
            val perFrameLines = mutableListOf<String>()
        }
        val shapeCollectors = Array(4) { RawShapeCollector() }

        var inWarpShader = false
        var inCompShader = false

        for ((lineIdx, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) continue

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val sectionHeader = trimmed.substring(1, trimmed.length - 1).trim()
                if (sectionHeader.equals("warp", ignoreCase = true)) {
                    inWarpShader = true
                    inCompShader = false
                } else if (sectionHeader.equals("comp", ignoreCase = true) || sectionHeader.equals("composite", ignoreCase = true)) {
                    inCompShader = true
                    inWarpShader = false
                } else {
                    inWarpShader = false
                    inCompShader = false
                }
                continue
            }

            if (inWarpShader) {
                warpShaderLines.add(line)
                continue
            }
            if (inCompShader) {
                compShaderLines.add(line)
                continue
            }

            val eqIndex = trimmed.indexOf('=')
            if (eqIndex <= 0) {
                // Header tags like 'Title: ...' or comments
                if (trimmed.startsWith("Title:", ignoreCase = true)) presetTitle = trimmed.substringAfter(":").trim()
                else if (trimmed.startsWith("Author:", ignoreCase = true)) presetAuthor = trimmed.substringAfter(":").trim()
                else if (trimmed.startsWith("Description:", ignoreCase = true)) presetDescription = trimmed.substringAfter(":").trim()
                continue
            }

            val key = trimmed.substring(0, eqIndex).trim().lowercase()
            val value = trimmed.substring(eqIndex + 1).trim()

            try {
                when {
                    key == "milkdrop_preset_version" || key == "version" -> parsedPresetVersion = value.toIntOrNull() ?: parsedPresetVersion
                    key == "psversion" -> {
                        psVersion = value.toIntOrNull() ?: psVersion
                        psVersionWarp = psVersion
                        psVersionComp = psVersion
                    }
                    key == "psversion_warp" -> psVersionWarp = value.toIntOrNull() ?: psVersionWarp
                    key == "psversion_comp" -> psVersionComp = value.toIntOrNull() ?: psVersionComp
                    key == "fdecay" || key == "decay" -> staticParams.decay = value.toFloatOrNull() ?: staticParams.decay
                    key == "nwavemode" || key == "wave_mode" -> staticParams.waveMode = value.toIntOrNull() ?: staticParams.waveMode
                    key == "bwavethick" || key == "wave_thick" -> staticParams.waveThick = value == "1" || value.equals("true", ignoreCase = true)
                    key == "fwavescale" || key == "wave_scale" -> staticParams.waveScale = value.toFloatOrNull() ?: staticParams.waveScale
                    key == "fwavealpha" || key == "wave_a" -> staticParams.waveA = value.toFloatOrNull() ?: staticParams.waveA
                    key == "fwaver" || key == "wave_r" -> staticParams.waveR = value.toFloatOrNull() ?: staticParams.waveR
                    key == "fwaveg" || key == "wave_g" -> staticParams.waveG = value.toFloatOrNull() ?: staticParams.waveG
                    key == "fwaveb" || key == "wave_b" -> staticParams.waveB = value.toFloatOrNull() ?: staticParams.waveB
                    key == "fwavemystery" || key == "wave_mystery" -> staticParams.waveMystery = value.toFloatOrNull() ?: staticParams.waveMystery
                    key == "fwavex" || key == "wave_x" -> staticParams.waveX = value.toFloatOrNull() ?: staticParams.waveX
                    key == "fwavey" || key == "wave_y" -> staticParams.waveY = value.toFloatOrNull() ?: staticParams.waveY

                    key == "fzoom" || key == "zoom" -> staticParams.zoom = value.toFloatOrNull() ?: staticParams.zoom
                    key == "fzoomexponent" || key == "zoom_exp" || key == "zoomexp" -> staticParams.zoomExp = value.toFloatOrNull() ?: staticParams.zoomExp
                    key == "frot" || key == "rot" -> staticParams.rot = value.toFloatOrNull() ?: staticParams.rot
                    key == "fwarp" || key == "warp" -> staticParams.warp = value.toFloatOrNull() ?: staticParams.warp
                    key == "fwarpanimspeed" || key == "warp_anim_speed" || key == "warpanimspeed" -> staticParams.warpAnimSpeed = value.toFloatOrNull() ?: staticParams.warpAnimSpeed
                    key == "fwarpscale" || key == "warp_scale" || key == "warpscale" -> staticParams.warpScale = value.toFloatOrNull() ?: staticParams.warpScale
                    key == "fcx" || key == "cx" -> staticParams.cx = value.toFloatOrNull() ?: staticParams.cx
                    key == "fcy" || key == "cy" -> staticParams.cy = value.toFloatOrNull() ?: staticParams.cy
                    key == "fdx" || key == "dx" -> staticParams.dx = value.toFloatOrNull() ?: staticParams.dx
                    key == "fdy" || key == "dy" -> staticParams.dy = value.toFloatOrNull() ?: staticParams.dy
                    key == "fsx" || key == "sx" -> staticParams.sx = value.toFloatOrNull() ?: staticParams.sx
                    key == "fsy" || key == "sy" -> staticParams.sy = value.toFloatOrNull() ?: staticParams.sy

                    key == "fvideoechozoom" || key == "echo_zoom" -> staticParams.echoZoom = value.toFloatOrNull() ?: staticParams.echoZoom
                    key == "fvideoechoalpha" || key == "echo_alpha" -> staticParams.echoAlpha = value.toFloatOrNull() ?: staticParams.echoAlpha
                    key == "nvideoechoorientation" || key == "echo_orient" -> staticParams.echoOrient = value.toIntOrNull() ?: staticParams.echoOrient

                    key == "ob_size" -> staticParams.obSize = value.toFloatOrNull() ?: staticParams.obSize
                    key == "ob_r" -> staticParams.obR = value.toFloatOrNull() ?: staticParams.obR
                    key == "ob_g" -> staticParams.obG = value.toFloatOrNull() ?: staticParams.obG
                    key == "ob_b" -> staticParams.obB = value.toFloatOrNull() ?: staticParams.obB
                    key == "ob_a" -> staticParams.obA = value.toFloatOrNull() ?: staticParams.obA

                    key == "ib_size" -> staticParams.ibSize = value.toFloatOrNull() ?: staticParams.ibSize
                    key == "ib_r" -> staticParams.ibR = value.toFloatOrNull() ?: staticParams.ibR
                    key == "ib_g" -> staticParams.ibG = value.toFloatOrNull() ?: staticParams.ibG
                    key == "ib_b" -> staticParams.ibB = value.toFloatOrNull() ?: staticParams.ibB
                    key == "ib_a" -> staticParams.ibA = value.toFloatOrNull() ?: staticParams.ibA

                    key == "nmotionvectorsx" || key == "mv_x" -> staticParams.mvX = value.toIntOrNull() ?: staticParams.mvX
                    key == "nmotionvectorsy" || key == "mv_y" -> staticParams.mvY = value.toIntOrNull() ?: staticParams.mvY
                    key == "mv_dx" -> staticParams.mvDx = value.toFloatOrNull() ?: staticParams.mvDx
                    key == "mv_dy" -> staticParams.mvDy = value.toFloatOrNull() ?: staticParams.mvDy
                    key == "mv_l" -> staticParams.mvL = value.toFloatOrNull() ?: staticParams.mvL
                    key == "mv_r" -> staticParams.mvR = value.toFloatOrNull() ?: staticParams.mvR
                    key == "mv_g" -> staticParams.mvG = value.toFloatOrNull() ?: staticParams.mvG
                    key == "mv_b" -> staticParams.mvB = value.toFloatOrNull() ?: staticParams.mvB
                    key == "mv_a" -> staticParams.mvA = value.toFloatOrNull() ?: staticParams.mvA

                    key.startsWith("q") && key.substring(1).toIntOrNull() in 1..32 -> {
                        val qIndex = key.substring(1).toInt() - 1
                        staticParams.qInit[qIndex] = value.toFloatOrNull() ?: 0f
                    }

                    // Equation lines
                    key.startsWith("per_frame_init_") -> if (value.isNotEmpty()) presetInitLines.add(value)
                    key.startsWith("per_frame_") -> if (value.isNotEmpty()) perFrameLines.add(value)
                    key.startsWith("per_pixel_") -> if (value.isNotEmpty()) perPixelLines.add(value)

                    // Warp & Composite shader blocks in key=value format (warp_1, comp_1, etc.)
                    key.startsWith("warp_") -> if (value.isNotEmpty()) warpShaderLines.add(value)
                    key.startsWith("comp_") -> if (value.isNotEmpty()) compShaderLines.add(value)

                    // Custom Shapes (0..3)
                    key.startsWith("shapecode_") -> {
                        val parts = key.split("_")
                        if (parts.size >= 3) {
                            val idx = parts[1].toIntOrNull()?.coerceIn(0, 3) ?: 0
                            val shape = shapeCollectors[idx]
                            when (parts[2]) {
                                "enabled" -> shape.enabled = value == "1"
                                "sides" -> shape.sides = value.toIntOrNull() ?: shape.sides
                                "additive" -> shape.additive = value == "1"
                                "thickoutline" -> shape.thick = value == "1"
                                "textured" -> shape.textured = value == "1"
                                "tex_zoom" -> shape.texZoom = value.toFloatOrNull() ?: shape.texZoom
                                "tex_ang" -> shape.texAng = value.toFloatOrNull() ?: shape.texAng
                                "x" -> shape.x = value.toFloatOrNull() ?: shape.x
                                "y" -> shape.y = value.toFloatOrNull() ?: shape.y
                                "rad" -> shape.rad = value.toFloatOrNull() ?: shape.rad
                                "ang" -> shape.ang = value.toFloatOrNull() ?: shape.ang
                                "r" -> shape.r = value.toFloatOrNull() ?: shape.r
                                "g" -> shape.g = value.toFloatOrNull() ?: shape.g
                                "b" -> shape.b = value.toFloatOrNull() ?: shape.b
                                "a" -> shape.a = value.toFloatOrNull() ?: shape.a
                                "r2" -> shape.r2 = value.toFloatOrNull() ?: shape.r2
                                "g2" -> shape.g2 = value.toFloatOrNull() ?: shape.g2
                                "b2" -> shape.b2 = value.toFloatOrNull() ?: shape.b2
                                "a2" -> shape.a2 = value.toFloatOrNull() ?: shape.a2
                                "border_r" -> shape.borderR = value.toFloatOrNull() ?: shape.borderR
                                "border_g" -> shape.borderG = value.toFloatOrNull() ?: shape.borderG
                                "border_b" -> shape.borderB = value.toFloatOrNull() ?: shape.borderB
                                "border_a" -> shape.borderA = value.toFloatOrNull() ?: shape.borderA
                            }
                        }
                    }
                    key.startsWith("shape_") && key.contains("_init") -> {
                        val parts = key.split("_")
                        if (parts.size >= 2) {
                            val idx = parts[1].toIntOrNull()?.coerceIn(0, 3) ?: 0
                            if (value.isNotEmpty()) shapeCollectors[idx].initLines.add(value)
                        }
                    }
                    key.startsWith("shape_") && key.contains("_per_frame") -> {
                        val parts = key.split("_")
                        if (parts.size >= 2) {
                            val idx = parts[1].toIntOrNull()?.coerceIn(0, 3) ?: 0
                            if (value.isNotEmpty()) shapeCollectors[idx].perFrameLines.add(value)
                        }
                    }

                    // Custom Waves (0..3)
                    key.startsWith("wavecode_") -> {
                        val parts = key.split("_")
                        if (parts.size >= 3) {
                            val idx = parts[1].toIntOrNull()?.coerceIn(0, 3) ?: 0
                            val wave = waveCollectors[idx]
                            when (parts[2]) {
                                "enabled" -> wave.enabled = value == "1"
                                "samples" -> wave.samples = value.toIntOrNull() ?: wave.samples
                                "sep" -> wave.sep = value.toIntOrNull() ?: wave.sep
                                "bspectrum" -> wave.bSpectrum = value == "1"
                                "busedots" -> wave.bUseDots = value == "1"
                                "bdrawthick" -> wave.bDrawThick = value == "1"
                                "badditive" -> wave.bAdditive = value == "1"
                                "scaling" -> wave.scaling = value.toFloatOrNull() ?: wave.scaling
                                "smoothing" -> wave.smoothing = value.toFloatOrNull() ?: wave.smoothing
                                "r" -> wave.r = value.toFloatOrNull() ?: wave.r
                                "g" -> wave.g = value.toFloatOrNull() ?: wave.g
                                "b" -> wave.b = value.toFloatOrNull() ?: wave.b
                                "a" -> wave.a = value.toFloatOrNull() ?: wave.a
                            }
                        }
                    }
                    key.startsWith("wave_") && key.contains("_init") -> {
                        val parts = key.split("_")
                        if (parts.size >= 2) {
                            val idx = parts[1].toIntOrNull()?.coerceIn(0, 3) ?: 0
                            if (value.isNotEmpty()) waveCollectors[idx].initLines.add(value)
                        }
                    }
                    key.startsWith("wave_") && key.contains("_per_frame") -> {
                        val parts = key.split("_")
                        if (parts.size >= 2) {
                            val idx = parts[1].toIntOrNull()?.coerceIn(0, 3) ?: 0
                            if (value.isNotEmpty()) waveCollectors[idx].perFrameLines.add(value)
                        }
                    }
                    key.startsWith("wave_") && key.contains("_per_point") -> {
                        val parts = key.split("_")
                        if (parts.size >= 2) {
                            val idx = parts[1].toIntOrNull()?.coerceIn(0, 3) ?: 0
                            if (value.isNotEmpty()) waveCollectors[idx].perPointLines.add(value)
                        }
                    }
                }
            } catch (e: Throwable) {
                diagnostics.add(
                    PresetDiagnostic(
                        DiagnosticSeverity.WARNING,
                        lineIdx + 1,
                        key,
                        "Could not parse key value: ${e.message}"
                    )
                )
            }
        }

        // Compile AST Programs
        val compiledInit = if (presetInitLines.isNotEmpty()) {
            MilkdropExpressionCompiler.compileProgram(presetInitLines, diagnostics, MilkdropVariableScope.GLOBAL)
        } else null

        val compiledPerFrame = if (perFrameLines.isNotEmpty()) {
            MilkdropExpressionCompiler.compileProgram(perFrameLines, diagnostics, MilkdropVariableScope.PER_FRAME)
        } else null

        val compiledPerVertex = if (perPixelLines.isNotEmpty()) {
            MilkdropExpressionCompiler.compileProgram(perPixelLines, diagnostics, MilkdropVariableScope.PER_VERTEX)
        } else null

        // Compile Custom Waves
        val compiledWaves = waveCollectors.mapIndexed { idx, w ->
            MilkCustomWaveIR(
                index = idx,
                enabled = w.enabled,
                samples = w.samples,
                sep = w.sep,
                bSpectrum = w.bSpectrum,
                bUseDots = w.bUseDots,
                bDrawThick = w.bDrawThick,
                bAdditive = w.bAdditive,
                scaling = w.scaling,
                smoothing = w.smoothing,
                r = w.r,
                g = w.g,
                b = w.b,
                a = w.a,
                initProgram = if (w.initLines.isNotEmpty()) MilkdropExpressionCompiler.compileProgram(w.initLines, diagnostics, MilkdropVariableScope.WAVE_FRAME) else null,
                perFrameProgram = if (w.perFrameLines.isNotEmpty()) MilkdropExpressionCompiler.compileProgram(w.perFrameLines, diagnostics, MilkdropVariableScope.WAVE_FRAME) else null,
                perPointProgram = if (w.perPointLines.isNotEmpty()) MilkdropExpressionCompiler.compileProgram(w.perPointLines, diagnostics, MilkdropVariableScope.WAVE_POINT) else null
            )
        }

        // Compile Custom Shapes
        val compiledShapes = shapeCollectors.mapIndexed { idx, s ->
            MilkCustomShapeIR(
                index = idx,
                enabled = s.enabled,
                numInst = s.numInst,
                sides = s.sides,
                thick = s.thick,
                additive = s.additive,
                textured = s.textured,
                texZoom = s.texZoom,
                texAng = s.texAng,
                x = s.x,
                y = s.y,
                rad = s.rad,
                ang = s.ang,
                r = s.r,
                g = s.g,
                b = s.b,
                a = s.a,
                r2 = s.r2,
                g2 = s.g2,
                b2 = s.b2,
                a2 = s.a2,
                borderR = s.borderR,
                borderG = s.borderG,
                borderB = s.borderB,
                borderA = s.borderA,
                initProgram = if (s.initLines.isNotEmpty()) MilkdropExpressionCompiler.compileProgram(s.initLines, diagnostics, MilkdropVariableScope.SHAPE_FRAME) else null,
                perFrameProgram = if (s.perFrameLines.isNotEmpty()) MilkdropExpressionCompiler.compileProgram(s.perFrameLines, diagnostics, MilkdropVariableScope.SHAPE_FRAME) else null
            )
        }

        // Compile Warp and Composite Shaders
        val compiledWarp = MilkdropShaderCompiler.compileShaderBlock(warpShaderLines, isWarp = true, diagnostics)
        val compiledComp = MilkdropShaderCompiler.compileShaderBlock(compShaderLines, isWarp = false, diagnostics)

        val crc = java.util.zip.CRC32()
        crc.update((presetTitle + presetAuthor + version).toByteArray())
        val stableId = -((crc.value % 900_000L).toInt() + 100_000)

        val metadata = MilkPresetMetadata(
            id = stableId,
            name = presetTitle,
            author = presetAuthor,
            description = presetDescription,
            version = version,
            milkdropPresetVersion = parsedPresetVersion,
            psVersion = psVersion,
            psVersionWarp = psVersionWarp,
            psVersionComp = psVersionComp,
            sourceFile = sourceFile
        )

        val rawPresetIR = MilkPresetIR(
            metadata = metadata,
            staticParams = staticParams,
            presetInitProgram = compiledInit,
            perFrameProgram = compiledPerFrame,
            perVertexProgram = compiledPerVertex,
            customWaves = compiledWaves,
            customShapes = compiledShapes,
            warpShader = compiledWarp,
            compositeShader = compiledComp,
            diagnostics = diagnostics
        )

        val compatibilityReport = MilkdropCompatibilityAnalyzer.analyze(rawPresetIR)
        return rawPresetIR.copy(compatibility = compatibilityReport)
    }

    private fun parseDoublePresetContent(
        lines: List<String>,
        initialTitle: String,
        initialAuthor: String,
        initialDescription: String,
        sourceFile: File?,
        diagnostics: MutableList<PresetDiagnostic>
    ): MilkPresetIR {
        var title = initialTitle
        var author = initialAuthor
        var description = initialDescription
        var pattern = "PLASMA"
        var blendRatio = 0.5f
        var blendDuration = 6.0f
        var plasmaSeed = 1.0f
        var isDirectionLocked = false
        var isFlipped = false
        var autoBeatSwitch = true

        val sectionLinesA = mutableListOf<String>()
        val sectionLinesB = mutableListOf<String>()
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) continue

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.substring(1, trimmed.length - 1).trim()
                continue
            }

            when (currentSection.lowercase()) {
                "milkdrop3" -> {
                    val eqIndex = trimmed.indexOf('=')
                    if (eqIndex > 0) {
                        val key = trimmed.substring(0, eqIndex).trim().lowercase()
                        val value = trimmed.substring(eqIndex + 1).trim()
                        when (key) {
                            "title" -> title = value
                            "author" -> author = value
                            "description" -> description = value
                            "blend_pattern" -> pattern = value.uppercase()
                            "blend_ratio" -> blendRatio = value.toFloatOrNull() ?: blendRatio
                            "blend_duration" -> blendDuration = value.toFloatOrNull() ?: blendDuration
                            "plasma_seed" -> plasmaSeed = value.toFloatOrNull() ?: plasmaSeed
                            "direction_locked" -> isDirectionLocked = value == "1" || value.equals("true", ignoreCase = true)
                            "flipped" -> isFlipped = value == "1" || value.equals("true", ignoreCase = true)
                            "auto_beat_switch" -> autoBeatSwitch = value == "1" || value.equals("true", ignoreCase = true)
                        }
                    }
                }
                "preseta" -> sectionLinesA.add(line)
                "presetb" -> sectionLinesB.add(line)
                else -> {
                    if (sectionLinesA.isEmpty()) sectionLinesA.add(line)
                }
            }
        }

        val presetA = parseSinglePreset(sectionLinesA, "$title (A)", author, description, 2.0f, null, diagnostics)
        val presetB = if (sectionLinesB.isNotEmpty()) {
            parseSinglePreset(sectionLinesB, "$title (B)", author, description, 2.0f, null, diagnostics)
        } else {
            presetA
        }

        val crc = java.util.zip.CRC32()
        crc.update((title + author + "milk2").toByteArray())
        val stableId = -((crc.value % 900_000L).toInt() + 100_000)

        val doubleMeta = Milkdrop3DoublePresetMeta(
            patternName = pattern,
            blendRatio = blendRatio,
            blendDuration = blendDuration,
            plasmaSeed = plasmaSeed,
            isDirectionLocked = isDirectionLocked,
            isFlipped = isFlipped,
            autoBeatSwitch = autoBeatSwitch
        )

        val metadata = MilkPresetMetadata(
            id = stableId,
            name = title,
            author = author,
            description = description,
            version = 3.0f,
            sourceFile = sourceFile,
            isDoublePreset = true,
            doublePresetMeta = doubleMeta
        )

        return presetA.copy(
            metadata = metadata,
            secondaryPresetIR = presetB
        )
    }

    /**
     * Converts a modern MilkPresetIR into the legacy MilkdropPreset for backward compatibility
     * with existing browser sheets and UI state.
     */
    fun irToLegacyPreset(ir: MilkPresetIR): MilkdropPreset {
        val primaryCol = Color(ir.staticParams.waveR, ir.staticParams.waveG, ir.staticParams.waveB, 1.0f)
        val secondaryCol = Color(1.0f - ir.staticParams.waveR, ir.staticParams.waveG, 1.0f - ir.staticParams.waveB, 1.0f)

        return MilkdropPreset(
            id = ir.metadata.id,
            name = ir.metadata.name,
            author = ir.metadata.author,
            description = ir.metadata.description,
            primaryColor = primaryCol,
            secondaryColor = secondaryCol,
            accentColor = Color(0xFFFFD700),
            defaultDecay = ir.staticParams.decay,
            defaultWaveMode = ir.staticParams.waveMode,
            shapes = emptyList(),
            waves = emptyList(),
            isCustom = true,
            sourceFileName = ir.metadata.sourceFile?.name,
            perFrameInit = { ctx ->
                // Handled in modern GPU engine
            },
            perPixelEvaluator = null,
            perFrame = { ctx ->
                // Copy base static parameters to frame context
                ctx.zoom = ir.staticParams.zoom
                ctx.rot = ir.staticParams.rot
                ctx.warp = ir.staticParams.warp
                ctx.dx = ir.staticParams.dx
                ctx.dy = ir.staticParams.dy
                ctx.sx = ir.staticParams.sx
                ctx.sy = ir.staticParams.sy
                ctx.decay = ir.staticParams.decay
                ctx.waveR = ir.staticParams.waveR
                ctx.waveG = ir.staticParams.waveG
                ctx.waveB = ir.staticParams.waveB
                ctx.waveA = ir.staticParams.waveA
                ctx.waveMode = ir.staticParams.waveMode
            }
        )
    }

    /**
     * Auto-discover all .milk and .milk2 presets from local storage.
     */
    fun discoverAllPresetIRs(
        context: Context,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<MilkPresetIR> {
        return try {
            val presetsDir = MilkdropFileStructure.getPresetsDirectory(context)
            if (!presetsDir.exists()) return emptyList()

            val allFiles = presetsDir.walkTopDown()
                .filter { it.isFile && (it.extension.equals("milk", ignoreCase = true) || it.extension.equals("milk2", ignoreCase = true)) }
                .distinctBy { it.canonicalPath }
                .toList()

            val total = allFiles.size
            val parsedList = mutableListOf<MilkPresetIR>()

            allFiles.forEachIndexed { index, file ->
                val parsed = parsePresetFile(file, context)
                if (parsed != null) parsedList.add(parsed)
                onProgress?.invoke(index + 1, total)
            }

            parsedList.distinctBy { it.metadata.sourceFile?.canonicalPath ?: it.metadata.name }
        } catch (e: Throwable) {
            Log.e(TAG, "Error discovering presets: ${e.message}")
            emptyList()
        }
    }
}
