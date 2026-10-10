package com.anuj.obsidian.ui.visualizer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class CustomPresetLoaderService(private val context: Context) {
    companion object {
        private const val TAG = "CustomPresetLoaderService"
        @Volatile
        private var instance: CustomPresetLoaderService? = null
        
        fun getInstance(context: Context): CustomPresetLoaderService {
            return instance ?: synchronized(this) {
                instance ?: CustomPresetLoaderService(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    private val _customPresets = MutableStateFlow<List<ParsedMilkdropPreset>>(emptyList())
    val customPresets: StateFlow<List<ParsedMilkdropPreset>> = _customPresets.asStateFlow()
    
    private val _discoveryInProgress = MutableStateFlow(false)
    val discoveryInProgress: StateFlow<Boolean> = _discoveryInProgress.asStateFlow()
    
    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

    // Progress feedback for large preset libraries (Item 16)
    private val _discoveryProgress = MutableStateFlow(0f)
    val discoveryProgress: StateFlow<Float> = _discoveryProgress.asStateFlow()

    /**
     * Check storage permissions before attempting file access (Item 13)
     */
    fun hasStoragePermission(): Boolean {
        // App-specific external directories don't require any permissions on API 19+
        return true
    }
    
    /**
     * Initialize directory structure and discover presets
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing MilkDrop file structure...")
            MilkdropFileStructure.initializeDirectoryStructure(context)
            
            loadCustomPresets()
        } catch (e: Throwable) {
            Log.e(TAG, "Initialization failed: ${e.message}")
            _discoveryError.value = "Failed to initialize: ${e.message}"
        }
    }
    
    /**
     * Discover and load all custom presets with 30s timeout and progress tracking (Items 9, 13, 16)
     */
    suspend fun loadCustomPresets() = withContext(Dispatchers.IO) {
        try {
            _discoveryInProgress.value = true
            _discoveryProgress.value = 0f
            _discoveryError.value = null

            // Check permissions before accessing storage (Item 13)
            val hasPerm = hasStoragePermission()
            if (!hasPerm) {
                Log.w(TAG, "Storage permission not granted. Scanning app-specific storage only.")
            }
            
            val presetsDir = MilkdropFileStructure.getPresetsDirectory(context)
            Log.d(TAG, "========== STARTING PRESET DISCOVERY ==========")
            Log.d(TAG, "Presets directory: ${presetsDir.absolutePath}")
            Log.d(TAG, "Directory exists: ${presetsDir.exists()}")
            
            if (presetsDir.exists()) {
                val allFiles = presetsDir.listFiles()
                Log.d(TAG, "Total files in presets dir: ${allFiles?.size ?: 0}")
                allFiles?.forEach { file ->
                    Log.d(TAG, "  - ${file.name} (${file.length()} bytes)")
                }
            }
            
            Log.d(TAG, "Starting parser discovery with 30s timeout...")
            // Add 30s timeout to prevent app hanging on corrupt storage (Item 9)
            val discovered = withTimeoutOrNull(30_000L) {
                MilkdropPresetParser.discoverAllPresets(context) { current, total ->
                    _discoveryProgress.value = if (total > 0) current.toFloat() / total else 1f
                }
            }

            if (discovered == null) {
                val timeoutMsg = "Preset discovery timed out (30s limit). Please check storage."
                Log.e(TAG, timeoutMsg)
                _discoveryError.value = timeoutMsg
                _discoveryInProgress.value = false
                return@withContext
            }
            
            _customPresets.value = discovered
            _discoveryProgress.value = 1f
            
            // ✅ Register with rendering engine and MilkDrop library
            try {
                Log.d(TAG, "Registering ${discovered.size} presets with VisualizerLibrary...")
                VisualizerLibrary.registerCustomPresets(discovered)
                MilkdropPresetLibrary.updateCustomPresets(discovered.map { it.preset })
                MilkdropPresetLibrary.updateCustomPresetIRs(discovered.mapNotNull { it.presetIR })
                Log.d(TAG, "✓ Successfully registered all presets with VisualizerLibrary")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to register presets with library: ${e.message}")
                _discoveryError.value = "Failed to register presets: ${e.message}"
            }
            
            Log.d(TAG, "========== DISCOVERY COMPLETE ==========")
            Log.d(TAG, "Loaded ${discovered.size} custom presets")
            discovered.forEach { preset ->
                Log.d(TAG, "  ✓ ${preset.name} by ${preset.author} (ID: ${preset.preset.id}, File: ${preset.sourceFile?.name})")
            }
            
            if (discovered.isEmpty()) {
                val errorMsg = if (!hasPerm) {
                    "Storage permission needed or drop .milk files into:\n${presetsDir.absolutePath}"
                } else {
                    "No presets found. Drop .milk files into:\n${presetsDir.absolutePath}"
                }
                _discoveryError.value = errorMsg
                Log.w(TAG, errorMsg)
            }
        } catch (e: Throwable) {
            val errorMsg = "Preset discovery failed: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _discoveryError.value = errorMsg
            _customPresets.value = emptyList()
            
            // Clear previously registered presets on failure
            VisualizerLibrary.clearCustomPresets()
        } finally {
            _discoveryInProgress.value = false
        }
    }
    
    /**
     * Reload presets from disk (call after adding new files)
     */
    suspend fun refreshPresets() = withContext(Dispatchers.IO) {
        VisualizerLibrary.clearCustomPresets()
        loadCustomPresets()
    }

    /**
     * Force refresh presets from disk, clearing cache first
     */
    suspend fun forceRefresh() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Force refresh requested")
        VisualizerLibrary.clearCustomPresets()  // Clear before reload
        loadCustomPresets()
    }

    /**
     * Clean up service resources to avoid memory leaks (Item 15)
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up CustomPresetLoaderService")
        _customPresets.value = emptyList()
        _discoveryError.value = null
        _discoveryProgress.value = 0f
        _discoveryInProgress.value = false
    }
    
    /**
     * Get detailed info about a preset for debugging
     */
    fun getPresetDebugInfo(preset: ParsedMilkdropPreset): String {
        val ir = preset.presetIR
        val compat = ir?.compatibility ?: ir?.let { MilkdropCompatibilityAnalyzer.analyze(it) }

        val compatSection = if (compat != null) {
            val featureList = compat.features.joinToString("\n") { "  • ${it.featureName}: ${if (it.isSupported) "✓" else "✗"} (${it.details})" }
            val unsuppList = if (compat.unsupportedFeatures.isNotEmpty()) {
                "\nUnsupported Items:\n" + compat.unsupportedFeatures.joinToString("\n") { "  ⚠ $it" }
            } else ""

            """
            
            [Compatibility Report]
            Status: ${compat.level}
            Summary: ${compat.summary}
            Features:
            $featureList$unsuppList
            """.trimIndent()
        } else ""

        val diagSection = if (ir != null && ir.diagnostics.isNotEmpty()) {
            "\nDiagnostics:\n" + ir.diagnostics.take(10).joinToString("\n") { "  [${it.severity}] Line ${it.line} in '${it.key}': ${it.message}" }
        } else ""

        return """
            Name: ${preset.name}
            Author: ${preset.author}
            Description: ${preset.description}
            Type: ${if (preset.isDoublePreset) "MilkDrop 3 Double Preset (.milk2)" else "MilkDrop Preset (.milk)"}
            File: ${preset.sourceFile?.name ?: "In-Memory"}
            Size: ${preset.sourceFile?.length() ?: 0} bytes
            ID: ${preset.preset.id}
            $compatSection$diagSection
        """.trimIndent()
    }
    
    /**
     * Get preset by ID
     */
    fun getPresetById(id: Int): ParsedMilkdropPreset? {
        return _customPresets.value.find { it.preset.id == id }
    }
    
    /**
     * Get presets by category/author/name
     */
    fun searchPresets(query: String): List<ParsedMilkdropPreset> {
        val lowerQuery = query.lowercase()
        return _customPresets.value.filter {
            it.name.lowercase().contains(lowerQuery) ||
            it.author.lowercase().contains(lowerQuery) ||
            it.description.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * Verify all referenced files exist for a preset
     */
    fun verifyPresetIntegrity(preset: ParsedMilkdropPreset): Boolean {
        return try {
            val sourceSt = preset.sourceFile?.exists() ?: return false
            
            val texturesDir = MilkdropFileStructure.getTexturesDirectory(context)
            val allTexturesExist = preset.referencedTextures.all { texture ->
                File(texturesDir, texture).exists()
            }
            
            sourceSt && allTexturesExist
        } catch (e: Throwable) {
            Log.e(TAG, "Error verifying preset integrity: ${e.message}")
            false
        }
    }
    
    /**
     * Get directory info for UI display
     */
    fun getDirectoryPaths(): Map<String, String> {
        return mapOf(
            "root" to MilkdropFileStructure.getRootDirectory(context).absolutePath,
            "presets" to MilkdropFileStructure.getPresetsDirectory(context).absolutePath,
            "textures" to MilkdropFileStructure.getTexturesDirectory(context).absolutePath,
            "equations" to MilkdropFileStructure.getEquationsDirectory(context).absolutePath,
            "shapes" to MilkdropFileStructure.getShapesDirectory(context).absolutePath
        )
    }
}
