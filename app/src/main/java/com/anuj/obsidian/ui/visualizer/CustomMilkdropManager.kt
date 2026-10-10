package com.anuj.obsidian.ui.visualizer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Data representation of a custom editable Milkdrop preset.
 */
data class MilkdropCustomPresetData(
    val id: Int,
    val name: String,
    val author: String = "User",
    val description: String = "Custom Milkdrop Preset",
    val primaryColorHex: String = "#00E5FF",
    val secondaryColorHex: String = "#FF2A85",
    val accentColorHex: String = "#FFD700",
    val decay: Float = 0.96f,
    val waveMode: Int = 0,
    val waveScale: Float = 1.0f,
    val waveThick: Boolean = true,
    val zoomBase: Float = 1.02f,
    val zoomBassMultiplier: Float = 0.05f,
    val rotSpeed: Float = 0.02f,
    val rotMidMultiplier: Float = 0.02f,
    val warpAmount: Float = 0.4f,
    val warpBassMultiplier: Float = 0.5f,
    val dxSpeed: Float = 0.0f,
    val dySpeed: Float = 0.0f,
    val shapeSides: Int = 6,
    val shapeRadius: Float = 0.18f,
    val shapeAudioReact: Float = 0.12f,
    val isUserCreated: Boolean = true,
    val sourceFileName: String? = null,
    val customEquationScript: String? = null,
    val rawMilkContent: String? = null
) {
    fun toPreset(): MilkdropPreset {
        val prim = try { Color(android.graphics.Color.parseColor(primaryColorHex)) } catch (_: Throwable) { Color(0xFF00E5FF) }
        val sec = try { Color(android.graphics.Color.parseColor(secondaryColorHex)) } catch (_: Throwable) { Color(0xFFFF2A85) }
        val acc = try { Color(android.graphics.Color.parseColor(accentColorHex)) } catch (_: Throwable) { Color(0xFFFFD700) }

        val shapes = if (shapeSides >= 3) {
            listOf(
                MilkdropCustomShape(initialSides = shapeSides) { ctx, out ->
                    out.enabled = true
                    out.sides = shapeSides
                    out.x = 0.5f + sin(ctx.time * 0.7f) * (dxSpeed * 10f)
                    out.y = 0.5f + cos(ctx.time * 0.8f) * (dySpeed * 10f)
                    out.rad = (shapeRadius + ctx.bass * shapeAudioReact).coerceIn(0.04f, 0.45f)
                    out.ang = ctx.time * rotSpeed * 20f + ctx.rot
                    out.r = prim.red
                    out.g = prim.green
                    out.b = prim.blue
                    out.a = 0.82f
                    out.borderR = sec.red
                    out.borderG = sec.green
                    out.borderB = sec.blue
                    out.borderA = 0.95f
                    out.borderWidth = 2.5f
                }
            )
        } else {
            emptyList()
        }

        val compiledProg = if (!customEquationScript.isNullOrBlank()) {
            MilkdropExpressionCompiler.compile(customEquationScript.split("\n"))
        } else {
            null
        }

        return MilkdropPreset(
            id = id,
            name = name,
            author = author,
            description = description,
            primaryColor = prim,
            secondaryColor = sec,
            accentColor = acc,
            defaultDecay = decay,
            defaultWaveMode = waveMode,
            shapes = shapes,
            isCustom = true,
            sourceFileName = sourceFileName,
            perFrame = { ctx ->
                ctx.zoom = zoomBase + (ctx.bass * zoomBassMultiplier)
                ctx.rot = rotSpeed + (ctx.mid * rotMidMultiplier)
                ctx.warp = warpAmount + (ctx.bass * warpBassMultiplier)
                ctx.dx = if (dxSpeed != 0f) sin(ctx.time * 0.8f) * dxSpeed else 0f
                ctx.dy = if (dySpeed != 0f) cos(ctx.time * 0.8f) * dySpeed else 0f
                ctx.waveScale = waveScale
                ctx.waveThick = waveThick
                ctx.waveR = prim.red
                ctx.waveG = prim.green
                ctx.waveB = prim.blue

                compiledProg?.execute(ctx)
            }
        )
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("author", author)
            put("description", description)
            put("primaryColorHex", primaryColorHex)
            put("secondaryColorHex", secondaryColorHex)
            put("accentColorHex", accentColorHex)
            put("decay", decay.toDouble())
            put("waveMode", waveMode)
            put("waveScale", waveScale.toDouble())
            put("waveThick", waveThick)
            put("zoomBase", zoomBase.toDouble())
            put("zoomBassMultiplier", zoomBassMultiplier.toDouble())
            put("rotSpeed", rotSpeed.toDouble())
            put("rotMidMultiplier", rotMidMultiplier.toDouble())
            put("warpAmount", warpAmount.toDouble())
            put("warpBassMultiplier", warpBassMultiplier.toDouble())
            put("dxSpeed", dxSpeed.toDouble())
            put("dySpeed", dySpeed.toDouble())
            put("shapeSides", shapeSides)
            put("shapeRadius", shapeRadius.toDouble())
            put("shapeAudioReact", shapeAudioReact.toDouble())
            put("isUserCreated", isUserCreated)
            put("sourceFileName", sourceFileName ?: "")
            put("customEquationScript", customEquationScript ?: "")
            put("rawMilkContent", rawMilkContent ?: "")
        }
    }

    fun toMilkFileContent(): String {
        if (!rawMilkContent.isNullOrBlank()) {
            return rawMilkContent
        }
        val p = toPreset()
        val builder = StringBuilder()
        builder.append("[preset00]\n")
        builder.append("fRating=3.000000\n")
        builder.append("fGammaAdj=1.000000\n")
        builder.append(String.format(java.util.Locale.US, "fDecay=%.6f\n", decay))
        builder.append(String.format(java.util.Locale.US, "nWaveMode=%d\n", waveMode))
        builder.append(String.format(java.util.Locale.US, "bWaveThick=%d\n", if (waveThick) 1 else 0))
        builder.append(String.format(java.util.Locale.US, "fWaveScale=%.6f\n", waveScale))
        builder.append(String.format(java.util.Locale.US, "zoom=%.6f\n", zoomBase))
        builder.append(String.format(java.util.Locale.US, "rot=%.6f\n", rotSpeed))
        builder.append(String.format(java.util.Locale.US, "warp=%.6f\n", warpAmount))
        builder.append(String.format(java.util.Locale.US, "dx=%.6f\n", dxSpeed))
        builder.append(String.format(java.util.Locale.US, "dy=%.6f\n", dySpeed))
        builder.append(String.format(java.util.Locale.US, "wave_r=%.6f\n", p.primaryColor.red))
        builder.append(String.format(java.util.Locale.US, "wave_g=%.6f\n", p.primaryColor.green))
        builder.append(String.format(java.util.Locale.US, "wave_b=%.6f\n", p.primaryColor.blue))
        builder.append(String.format(java.util.Locale.US, "shape_sides=%d\n", shapeSides))
        builder.append(String.format(java.util.Locale.US, "shape_rad=%.6f\n", shapeRadius))
        builder.append("per_frame_1=zoom = zoom + bass*").append(String.format(java.util.Locale.US, "%.4f;\n", zoomBassMultiplier))
        builder.append("per_frame_2=rot = rot + mid*").append(String.format(java.util.Locale.US, "%.4f;\n", rotMidMultiplier))
        builder.append("per_frame_3=warp = warp + bass*").append(String.format(java.util.Locale.US, "%.4f;\n", warpBassMultiplier))
        return builder.toString()
    }

    companion object {
        private const val TAG = "MilkdropCustomPresetData"
        fun fromJson(json: JSONObject): MilkdropCustomPresetData {
            return MilkdropCustomPresetData(
                id = json.optInt("id", (3000..9999).random()),
                name = json.optString("name", "Custom Preset"),
                author = json.optString("author", "User"),
                description = json.optString("description", "Custom Milkdrop Preset"),
                primaryColorHex = json.optString("primaryColorHex", "#00E5FF"),
                secondaryColorHex = json.optString("secondaryColorHex", "#FF2A85"),
                accentColorHex = json.optString("accentColorHex", "#FFD700"),
                decay = json.optDouble("decay", 0.96).toFloat(),
                waveMode = json.optInt("waveMode", 0),
                waveScale = json.optDouble("waveScale", 1.0).toFloat(),
                waveThick = json.optBoolean("waveThick", true),
                zoomBase = json.optDouble("zoomBase", 1.02).toFloat(),
                zoomBassMultiplier = json.optDouble("zoomBassMultiplier", 0.05).toFloat(),
                rotSpeed = json.optDouble("rotSpeed", 0.02).toFloat(),
                rotMidMultiplier = json.optDouble("rotMidMultiplier", 0.02).toFloat(),
                warpAmount = json.optDouble("warpAmount", 0.4).toFloat(),
                warpBassMultiplier = json.optDouble("warpBassMultiplier", 0.5).toFloat(),
                dxSpeed = json.optDouble("dxSpeed", 0.0).toFloat(),
                dySpeed = json.optDouble("dySpeed", 0.0).toFloat(),
                shapeSides = json.optInt("shapeSides", 6),
                shapeRadius = json.optDouble("shapeRadius", 0.18).toFloat(),
                shapeAudioReact = json.optDouble("shapeAudioReact", 0.12).toFloat(),
                isUserCreated = json.optBoolean("isUserCreated", true),
                sourceFileName = json.optString("sourceFileName").takeIf { it.isNotBlank() },
                customEquationScript = json.optString("customEquationScript").takeIf { it.isNotBlank() },
                rawMilkContent = json.optString("rawMilkContent").takeIf { it.isNotBlank() }
            )
        }

        fun fromMilkFileContent(fileName: String, content: String, assignedId: Int): MilkdropCustomPresetData {
            var name = fileName.removeSuffix(".milk").replace("_", " ")
            var author = "Community"
            var decay = 0.96f
            var waveMode = 0
            var waveThick = true
            var waveScale = 1.0f
            var zoomBase = 1.02f
            var rotSpeed = 0.02f
            var warpAmount = 0.4f
            var dx = 0.0f
            var dy = 0.0f
            var waveR = 0.0f
            var waveG = 0.9f
            var waveB = 1.0f
            var shapeSides = 6
            var shapeRad = 0.18f
            var zoomBass = 0.05f
            var warpBass = 0.5f

            val perFrameLines = mutableListOf<String>()

            content.lines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("[")) return@forEach
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()
                    try {
                        when {
                            key.startsWith("per_frame_") -> if (value.isNotEmpty()) perFrameLines.add(value)
                            key == "fdecay" || key == "decay" -> decay = value.toFloat().coerceIn(0.5f, 1.0f)
                            key == "nwavemode" || key == "wave_mode" -> waveMode = value.toInt().coerceIn(0, 4)
                            key == "bwavethick" || key == "wave_thick" -> waveThick = value == "1" || value.equals("true", true)
                            key == "fwavescale" || key == "wave_scale" -> waveScale = value.toFloat().coerceIn(0.1f, 4.0f)
                            key == "fzoom" || key == "zoom" -> zoomBase = value.toFloat().coerceIn(0.8f, 1.3f)
                            key == "frot" || key == "rot" -> rotSpeed = value.toFloat().coerceIn(-0.2f, 0.2f)
                            key == "fwarp" || key == "warp" -> warpAmount = value.toFloat().coerceIn(0.0f, 2.0f)
                            key == "fdx" || key == "dx" -> dx = value.toFloat().coerceIn(-0.1f, 0.1f)
                            key == "fdy" || key == "dy" -> dy = value.toFloat().coerceIn(-0.1f, 0.1f)
                            key == "fwaver" || key == "wave_r" -> waveR = value.toFloat().coerceIn(0f, 1f)
                            key == "fwaveg" || key == "wave_g" -> waveG = value.toFloat().coerceIn(0f, 1f)
                            key == "fwaveb" || key == "wave_b" -> waveB = value.toFloat().coerceIn(0f, 1f)
                            key == "shape_sides" -> shapeSides = value.toInt().coerceIn(3, 32)
                            key == "shape_rad" -> shapeRad = value.toFloat().coerceIn(0.05f, 0.5f)
                            key == "author" -> author = value
                            key == "name" -> name = value
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Error parsing milk line: $rawLine (${e.message})")
                    }
                }
            }

            val primHex = String.format(java.util.Locale.US, "#%02X%02X%02X", (waveR * 255).toInt(), (waveG * 255).toInt(), (waveB * 255).toInt())
            val secHex = String.format(java.util.Locale.US, "#%02X%02X%02X", ((1f - waveR) * 255).toInt(), (waveB * 255).toInt(), (waveG * 255).toInt())

            return MilkdropCustomPresetData(
                id = assignedId,
                name = name,
                author = author,
                description = "Imported MilkDrop Preset ($fileName)",
                primaryColorHex = primHex,
                secondaryColorHex = secHex,
                accentColorHex = "#FFD700",
                decay = decay,
                waveMode = waveMode,
                waveScale = waveScale,
                waveThick = waveThick,
                zoomBase = zoomBase,
                zoomBassMultiplier = zoomBass,
                rotSpeed = rotSpeed,
                rotMidMultiplier = 0.02f,
                warpAmount = warpAmount,
                warpBassMultiplier = warpBass,
                dxSpeed = dx,
                dySpeed = dy,
                shapeSides = shapeSides,
                shapeRadius = shapeRad,
                shapeAudioReact = 0.12f,
                isUserCreated = false,
                sourceFileName = fileName,
                customEquationScript = if (perFrameLines.isNotEmpty()) perFrameLines.joinToString("\n") else null,
                rawMilkContent = content
            )
        }
    }
}

/**
 * Singleton Manager for Custom Milkdrop Visualizer Presets, Folders, and SAF File Imports.
 */
object CustomMilkdropManager {
    private const val TAG = "CustomMilkdropManager"
    private const val PRESETS_JSON_FILE = "custom_milkdrop_presets.json"
    private const val MILKDROP_DIR_NAME = "Milkdrop"

    val idGenerator = AtomicInteger(3000)
    private val _customPresets = MutableStateFlow<List<MilkdropCustomPresetData>>(emptyList())
    val customPresets: StateFlow<List<MilkdropCustomPresetData>> = _customPresets.asStateFlow()

    private var isInitialized = false

    fun computeStableId(name: String, sourceFileName: String? = null): Int {
        val key = sourceFileName?.takeIf { it.isNotBlank() } ?: name
        val crc = java.util.zip.CRC32()
        crc.update(key.trim().lowercase().toByteArray())
        return -((crc.value % 900_000L).toInt() + 100_000)
    }

    /**
     * Synchronous initialization for fast startup. Loads cached JSON presets immediately into VisualizerLibrary.
     */
    fun initializeSync(context: Context) {
        try {
            val loadedPresets = loadSavedJsonPresets(context)
            if (loadedPresets.isNotEmpty()) {
                _customPresets.value = loadedPresets
                MilkdropPresetLibrary.updateCustomPresets(loadedPresets.map { it.toPreset() })
                syncToVisualizerLibrary(getMilkdropFolder(context), loadedPresets)
                Log.d(TAG, "initializeSync loaded ${loadedPresets.size} cached presets synchronously.")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "initializeSync warning: ${e.message}")
        }
    }

    /**
     * Initializes the custom Milkdrop subsystem:
     * 1. Creates the local Milkdrop presets directory.
     * 2. Seeds standard starter `.milk` presets if the folder is empty.
     * 3. Loads saved custom JSON presets.
     * 4. Scans for new `.milk` files placed in all presets directories.
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        try {
            val milkFolder = getMilkdropFolder(context)
            val internalFolder = MilkdropFileStructure.getInternalPresetsDirectory(context)
            if (!milkFolder.exists()) milkFolder.mkdirs()
            if (!internalFolder.exists()) internalFolder.mkdirs()

            // Do not seed fake/synthetic demo presets into Milkdrop folders
            // Community .milk/.milk2 presets provided by user via SAF or file copy are the true source of truth.

            // Load saved JSON presets
            val loadedPresets = loadSavedJsonPresets(context).toMutableList()

            // Scan all directories for .milk and .json files
            val allDirs = MilkdropFileStructure.getAllPresetDirectories(context)
            val scannedPresets = mutableListOf<MilkdropCustomPresetData>()
            for (dir in allDirs) {
                scannedPresets.addAll(scanDirectoryForPresets(dir, loadedPresets))
            }
            
            // Merge scanned with loaded
            val mergedMap = mutableMapOf<Int, MilkdropCustomPresetData>()
            loadedPresets.forEach { mergedMap[it.id] = it }
            scannedPresets.forEach { mergedMap[it.id] = it }

            val finalList = mergedMap.values.toList()
            _customPresets.value = finalList
            
            // Register into MilkdropPresetLibrary and VisualizerLibrary
            MilkdropPresetLibrary.updateCustomPresets(finalList.map { it.toPreset() })
            syncToVisualizerLibrary(milkFolder, finalList)

            // Save state
            savePresetsToJson(context, finalList)
            isInitialized = true
            Log.d(TAG, "Custom Milkdrop Manager initialized with ${finalList.size} custom presets.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize CustomMilkdropManager: ${e.message}", e)
        }
    }

    /**
     * Gets or creates the primary Milkdrop storage directory.
     */
    fun getMilkdropFolder(context: Context): File {
        return MilkdropFileStructure.getPresetsDirectory(context)
    }

    /**
     * Imports a .milk or .json preset file from a SAF content URI.
     */
    suspend fun importFromUri(context: Context, uri: Uri): Result<MilkdropPreset> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileName = getFileNameFromUri(context, uri) ?: "imported_preset.milk"
            val stringBuilder = StringBuilder()

            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append("\n")
                    }
                }
            } ?: return@withContext Result.failure(Exception("Could not read file stream"))

            val fileContent = stringBuilder.toString()
            val stableId = computeStableId(fileName, fileName)

            val presetData = if (fileName.endsWith(".json", ignoreCase = true)) {
                MilkdropCustomPresetData.fromJson(JSONObject(fileContent)).copy(id = stableId, sourceFileName = fileName)
            } else {
                MilkdropCustomPresetData.fromMilkFileContent(fileName, fileContent, stableId)
            }

            // Save copy into local Milkdrop folders and guaranteed native cache directory
            try {
                val targetFile = File(getMilkdropFolder(context), fileName)
                targetFile.writeText(fileContent)
            } catch (e: Throwable) {
                Log.w(TAG, "Save to milk folder notice: ${e.message}")
            }
            try {
                val internalFile = File(MilkdropFileStructure.getInternalPresetsDirectory(context), fileName)
                internalFile.writeText(fileContent)
            } catch (e: Throwable) {
                Log.w(TAG, "Save to internal presets notice: ${e.message}")
            }
            try {
                MilkdropFileStructure.getNativeCachePresetFile(context, fileName, fileContent)
            } catch (e: Throwable) {
                Log.w(TAG, "Native cache preset notice: ${e.message}")
            }

            // Add to state
            val current = _customPresets.value.toMutableList()
            current.removeAll { it.name.equals(presetData.name, true) }
            current.add(0, presetData)
            _customPresets.value = current

            MilkdropPresetLibrary.updateCustomPresets(current.map { it.toPreset() })
            syncToVisualizerLibrary(getMilkdropFolder(context), current)
            savePresetsToJson(context, current)

            Result.success(presetData.toPreset())
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import preset: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Scans and syncs all presets in the Milkdrop folder.
     */
    suspend fun scanAndSyncFolder(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val folder = getMilkdropFolder(context)
            val current = _customPresets.value.toMutableList()
            val scanned = scanDirectoryForPresets(folder, current)
            
            val mergedMap = mutableMapOf<Int, MilkdropCustomPresetData>()
            current.forEach { mergedMap[it.id] = it }
            scanned.forEach { mergedMap[it.id] = it }

            val finalList = mergedMap.values.toList()
            _customPresets.value = finalList
            MilkdropPresetLibrary.updateCustomPresets(finalList.map { it.toPreset() })
            syncToVisualizerLibrary(folder, finalList)
            savePresetsToJson(context, finalList)
            finalList.size
        } catch (e: Throwable) {
            Log.e(TAG, "Scan and sync failed: ${e.message}", e)
            0
        }
    }

    /**
     * Creates or updates a custom preset created via the Milkdrop Studio Editor.
     */
    suspend fun saveCustomPreset(context: Context, data: MilkdropCustomPresetData): MilkdropPreset = withContext(Dispatchers.IO) {
        val current = _customPresets.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == data.id }
        
        val finalData = if (data.id <= 0) {
            data.copy(id = idGenerator.incrementAndGet())
        } else {
            data
        }

        if (existingIndex >= 0) {
            current[existingIndex] = finalData
        } else {
            current.add(0, finalData)
        }

        val milkFolder = getMilkdropFolder(context)
        // Write to .milk file in folder
        try {
            val safeFileName = "${finalData.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.milk"
            val file = File(milkFolder, safeFileName)
            file.writeText(finalData.toMilkFileContent())
        } catch (e: Throwable) {
            Log.w(TAG, "Failed writing custom preset milk file: ${e.message}")
        }

        _customPresets.value = current
        MilkdropPresetLibrary.updateCustomPresets(current.map { it.toPreset() })
        syncToVisualizerLibrary(milkFolder, current)
        savePresetsToJson(context, current)

        finalData.toPreset()
    }

    /**
     * Deletes a custom preset.
     */
    suspend fun deleteCustomPreset(context: Context, id: Int): Boolean = withContext(Dispatchers.IO) {
        val current = _customPresets.value.toMutableList()
        val item = current.find { it.id == id } ?: return@withContext false
        current.remove(item)

        val folder = getMilkdropFolder(context)
        // Delete file if exists
        try {
            if (item.sourceFileName != null) {
                val file = File(folder, item.sourceFileName)
                if (file.exists()) file.delete()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed deleting custom preset file: ${e.message}")
        }

        _customPresets.value = current
        MilkdropPresetLibrary.updateCustomPresets(current.map { it.toPreset() })
        syncToVisualizerLibrary(folder, current)
        savePresetsToJson(context, current)
        true
    }

    /**
     * Duplicates an existing preset as a new custom preset.
     */
    suspend fun duplicatePreset(context: Context, sourcePresetId: Int): MilkdropPreset? = withContext(Dispatchers.IO) {
        val source = _customPresets.value.find { it.id == sourcePresetId }
        val newId = idGenerator.incrementAndGet()
        
        val duplicated = if (source != null) {
            source.copy(
                id = newId,
                name = "${source.name} (Copy)",
                isUserCreated = true,
                sourceFileName = null
            )
        } else {
            val builtIn = MilkdropPresetLibrary.getPresetById(sourcePresetId)
            MilkdropCustomPresetData(
                id = newId,
                name = "${builtIn.name} (Custom)",
                author = "User Mod",
                description = "Custom variation of ${builtIn.name}",
                primaryColorHex = String.format(java.util.Locale.US, "#%02X%02X%02X", (builtIn.primaryColor.red * 255).toInt(), (builtIn.primaryColor.green * 255).toInt(), (builtIn.primaryColor.blue * 255).toInt()),
                secondaryColorHex = String.format(java.util.Locale.US, "#%02X%02X%02X", (builtIn.secondaryColor.red * 255).toInt(), (builtIn.secondaryColor.green * 255).toInt(), (builtIn.secondaryColor.blue * 255).toInt()),
                accentColorHex = String.format(java.util.Locale.US, "#%02X%02X%02X", (builtIn.accentColor.red * 255).toInt(), (builtIn.accentColor.green * 255).toInt(), (builtIn.accentColor.blue * 255).toInt()),
                decay = builtIn.defaultDecay,
                waveMode = builtIn.defaultWaveMode,
                isUserCreated = true
            )
        }

        saveCustomPreset(context, duplicated)
    }

    private fun syncToVisualizerLibrary(folder: File, presets: List<MilkdropCustomPresetData>) {
        val builtinParsedList = MilkdropPresetLibrary.presets.map { builtin ->
            ParsedMilkdropPreset(
                name = builtin.name,
                author = builtin.author,
                description = builtin.description,
                sourceFile = null,
                preset = builtin,
                presetIR = null,
                rawMilkContent = null
            )
        }

        val customParsedList = presets.map { customData ->
            val safeFileName = customData.sourceFileName ?: "${customData.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.milk"
            val file = File(folder, safeFileName)
            if (!file.exists() || (customData.rawMilkContent != null && (!file.canRead() || file.length() == 0L))) {
                try {
                    file.writeText(customData.toMilkFileContent())
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed writing preset file: ${e.message}")
                }
            }
            val preset = customData.toPreset()
            val milkText = if (!customData.rawMilkContent.isNullOrBlank()) {
                customData.rawMilkContent
            } else if (file.exists() && file.canRead()) {
                runCatching { file.readText() }.getOrNull() ?: customData.toMilkFileContent()
            } else {
                customData.toMilkFileContent()
            }
            val cacheFile = com.anuj.obsidian.ObsidianApplication.instance?.let { ctx ->
                MilkdropFileStructure.getNativeCachePresetFile(ctx, safeFileName, milkText)
            } ?: file
            val presetIR = try {
                MilkdropPresetParser.parse(milkText, customData.name, cacheFile)
            } catch (e: Throwable) {
                Log.w(TAG, "IR parsing fallback: ${e.message}")
                null
            }
            ParsedMilkdropPreset(
                name = customData.name,
                author = customData.author,
                description = customData.description,
                sourceFile = if (cacheFile.exists()) cacheFile else if (file.exists()) file else null,
                preset = preset,
                presetIR = presetIR,
                rawMilkContent = milkText
            )
        }

        val fullList = builtinParsedList + customParsedList
        VisualizerLibrary.registerCustomPresets(fullList)
        val validIrs = fullList.mapNotNull { it.presetIR }
        if (validIrs.isNotEmpty()) {
            MilkdropPresetLibrary.updateCustomPresetIRs(validIrs)
        }
    }

    fun generateBuiltinMilkContent(preset: MilkdropPreset): String {
        val b = StringBuilder()
        b.append("[preset00]\n")
        b.append("fRating=4.000000\n")
        b.append("fGammaAdj=1.000000\n")
        b.append(String.format(java.util.Locale.US, "fDecay=%.6f\n", preset.defaultDecay))
        b.append(String.format(java.util.Locale.US, "nWaveMode=%d\n", preset.defaultWaveMode))
        b.append("bWaveThick=1\n")
        b.append("fWaveScale=1.200000\n")
        b.append("zoom=1.025000\n")
        b.append("rot=0.015000\n")
        b.append("warp=0.400000\n")
        b.append(String.format(java.util.Locale.US, "wave_r=%.6f\n", preset.primaryColor.red))
        b.append(String.format(java.util.Locale.US, "wave_g=%.6f\n", preset.primaryColor.green))
        b.append(String.format(java.util.Locale.US, "wave_b=%.6f\n", preset.primaryColor.blue))
        b.append("per_frame_1=zoom = 1.02 + 0.04*bass;\n")
        b.append("per_frame_2=rot = 0.01 + 0.02*mid;\n")
        b.append("per_frame_3=warp = 0.35 + 0.45*bass;\n")
        b.append("author=").append(preset.author).append("\n")
        b.append("name=").append(preset.name).append("\n")
        return b.toString()
    }

    private fun scanDirectoryForPresets(dir: File, existing: List<MilkdropCustomPresetData>): List<MilkdropCustomPresetData> {
        val result = mutableListOf<MilkdropCustomPresetData>()
        val files = dir.listFiles() ?: return result

        for (file in files) {
            if (file.isDirectory) continue
            val name = file.name
            if (name.endsWith(".milk", ignoreCase = true)) {
                // Check if already in existing
                val alreadyLoaded = existing.find { it.sourceFileName.equals(name, true) }
                if (alreadyLoaded != null) {
                    result.add(alreadyLoaded)
                } else {
                    try {
                        val content = file.readText()
                        val stableId = computeStableId(name, name)
                        val preset = MilkdropCustomPresetData.fromMilkFileContent(name, content, stableId)
                        result.add(preset)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to parse .milk file ${file.name}: ${e.message}")
                    }
                }
            } else if (name.endsWith(".json", ignoreCase = true) && !name.equals(PRESETS_JSON_FILE, true)) {
                try {
                    val content = file.readText()
                    val stableId = computeStableId(name, name)
                    val preset = MilkdropCustomPresetData.fromJson(JSONObject(content)).copy(id = stableId, sourceFileName = name)
                    result.add(preset)
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed reading custom json preset file $name: ${e.message}")
                }
            }
        }
        return result
    }

    private fun loadSavedJsonPresets(context: Context): List<MilkdropCustomPresetData> {
        return try {
            val file = File(context.filesDir, PRESETS_JSON_FILE)
            if (!file.exists()) return emptyList()
            val content = file.readText()
            val array = JSONArray(content)
            val list = mutableListOf<MilkdropCustomPresetData>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(MilkdropCustomPresetData.fromJson(obj))
            }
            list
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading saved json presets: ${e.message}")
            emptyList()
        }
    }

    private fun savePresetsToJson(context: Context, presets: List<MilkdropCustomPresetData>) {
        try {
            val file = File(context.filesDir, PRESETS_JSON_FILE)
            val array = JSONArray()
            presets.forEach { array.put(it.toJson()) }
            file.writeText(array.toString(2))
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving presets to json: ${e.message}")
        }
    }

    private fun seedStarterPresets(folder: File) {
        val starters = mapOf(
            "Cosmic_Nebula_Vortex.milk" to """
                [preset00]
                fRating=4.000000
                fDecay=0.970000
                nWaveMode=1
                bWaveThick=1
                fWaveScale=1.200000
                zoom=1.035000
                rot=0.015000
                warp=0.600000
                wave_r=0.000000
                wave_g=0.900000
                wave_b=1.000000
                shape_sides=8
                shape_rad=0.200000
                author=Obsidian demo (limited)
                name=Cosmic Nebula Vortex
            """.trimIndent(),
            "Acid_Lissajous_Pulse.milk" to """
                [preset00]
                fRating=4.500000
                fDecay=0.950000
                nWaveMode=3
                bWaveThick=1
                fWaveScale=1.400000
                zoom=0.990000
                rot=0.025000
                warp=0.850000
                wave_r=1.000000
                wave_g=0.100000
                wave_b=0.700000
                shape_sides=12
                shape_rad=0.220000
                author=Obsidian demo (limited)
                name=Acid Lissajous Pulse
            """.trimIndent(),
            "Cyber_Matrix_Trench.milk" to """
                [preset00]
                fRating=4.000000
                fDecay=0.960000
                nWaveMode=2
                bWaveThick=1
                fWaveScale=1.100000
                zoom=1.040000
                rot=0.005000
                warp=0.300000
                dy=-0.010000
                wave_r=0.000000
                wave_g=1.000000
                wave_b=0.400000
                shape_sides=4
                shape_rad=0.160000
                author=Obsidian demo (limited)
                name=Cyber Matrix Trench
            """.trimIndent(),
            "Supernova_Burst_Shockwave.milk" to """
                [preset00]
                fRating=5.000000
                fDecay=0.940000
                nWaveMode=1
                bWaveThick=1
                fWaveScale=1.500000
                zoom=1.060000
                rot=0.030000
                warp=0.950000
                wave_r=1.000000
                wave_g=0.600000
                wave_b=0.000000
                shape_sides=32
                shape_rad=0.250000
                author=Obsidian demo (limited)
                name=Supernova Burst Shockwave
            """.trimIndent()
        )

        starters.forEach { (fileName, content) ->
            val file = File(folder, fileName)
            if (!file.exists()) {
                try {
                    file.writeText(content)
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed writing seed starter preset $fileName: ${e.message}")
                }
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }
}
