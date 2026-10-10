package com.anuj.obsidian.ui.visualizer

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

object MilkdropFileStructure {
    private const val TAG = "MilkdropFileStructure"
    
    // Root directory for all MilkDrop content
    private const val MILKDROP_ROOT = "Obsidian/Milkdrop"
    
    // Subdirectories
    private const val PRESETS_DIR = "presets"
    private const val TEXTURES_DIR = "textures"
    private const val EQUATIONS_DIR = "equations"
    private const val SHAPES_DIR = "shapes"
    private const val CACHE_DIR = "cache"
    
    /**
     * Get the root MilkDrop directory
     * This uses app-specific external files directory to avoid MANAGE_EXTERNAL_STORAGE requirement
     */
    fun getRootDirectory(context: Context): File {
        return try {
            context.getExternalFilesDir("MilkdropPresets") ?: File(context.filesDir, "MilkdropPresets").apply {
                if (!exists()) mkdirs()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Fallback to internal dir: ${e.message}")
            File(context.filesDir, "MilkdropPresets").apply {
                if (!exists()) mkdirs()
            }
        }
    }
    
    /**
     * Get internal storage presets directory (always accessible and never unmounted)
     */
    fun getInternalPresetsDirectory(context: Context): File {
        return File(File(context.filesDir, "MilkdropPresets"), PRESETS_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Get the native cache directory where .milk files are guaranteed to be readable by native projectM
     */
    fun getNativeCacheDirectory(context: Context): File {
        val dir = File(context.cacheDir, "milkdrop")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Writes preset text to context.cacheDir/milkdrop/<safeFileName>.milk for native C++ file loading.
     */
    fun getNativeCachePresetFile(context: Context, fileName: String, text: String): File {
        val cacheDir = getNativeCacheDirectory(context)
        val safeName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val finalName = if (safeName.lowercase().endsWith(".milk") || safeName.lowercase().endsWith(".milk2")) safeName else "$safeName.milk"
        val file = File(cacheDir, finalName)
        try {
            file.writeText(text, Charsets.UTF_8)
            Log.d(TAG, "Cached preset file for native projectM: ${file.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed writing cache preset file: ${e.message}", e)
        }
        return file
    }

    /**
     * Get presets directory - users can drop .milk files here
     */
    fun getPresetsDirectory(context: Context): File {
        return File(getRootDirectory(context), PRESETS_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created presets directory: $absolutePath")
            }
        }
    }

    /**
     * Get all possible directories where Milkdrop presets might be stored.
     * Checks internal storage, external files, and legacy paths.
     */
    fun getAllPresetDirectories(context: Context): List<File> {
        val dirs = LinkedHashSet<File>()
        
        // 1. Primary presets directory
        dirs.add(getPresetsDirectory(context))
        
        // 2. Guaranteed internal storage presets directory
        dirs.add(getInternalPresetsDirectory(context))
        dirs.add(File(context.filesDir, "Milkdrop/presets"))
        dirs.add(File(context.filesDir, "presets"))
        
        // 3. App external directories if available
        try {
            context.getExternalFilesDir(null)?.let { extRoot ->
                dirs.add(File(extRoot, "MilkdropPresets/presets"))
                dirs.add(File(extRoot, "Milkdrop/presets"))
                dirs.add(File(extRoot, "presets"))
            }
        } catch (_: Throwable) {}

        return dirs.filter { it.exists() || it.mkdirs() }
    }
    
    /**
     * Get textures directory - place .png, .jpg, .bmp textures here
     */
    fun getTexturesDirectory(context: Context): File {
        return File(getRootDirectory(context), TEXTURES_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created textures directory: $absolutePath")
            }
        }
    }
    
    /**
     * Get equations directory - place .equation files here
     */
    fun getEquationsDirectory(context: Context): File {
        return File(getRootDirectory(context), EQUATIONS_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created equations directory: $absolutePath")
            }
        }
    }
    
    /**
     * Get shapes directory - place custom shape files here
     */
    fun getShapesDirectory(context: Context): File {
        return File(getRootDirectory(context), SHAPES_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created shapes directory: $absolutePath")
            }
        }
    }
    
    /**
     * Get cache directory for processed presets
     */
    fun getCacheDirectory(context: Context): File {
        return File(getRootDirectory(context), CACHE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Initialize all directories and create README files
     */
    fun initializeDirectoryStructure(context: Context) {
        try {
            val root = getRootDirectory(context)
            val presets = getPresetsDirectory(context)
            val textures = getTexturesDirectory(context)
            val equations = getEquationsDirectory(context)
            val shapes = getShapesDirectory(context)
            
            createReadmeFile(presets, "PRESETS_README.txt", getPresetsReadmeContent())
            createReadmeFile(textures, "TEXTURES_README.txt", getTexturesReadmeContent())
            createReadmeFile(equations, "EQUATIONS_README.txt", getEquationsReadmeContent())
            createReadmeFile(shapes, "SHAPES_README.txt", getShapesReadmeContent())
            
            Log.d(TAG, "Initialized MilkDrop directory structure at: ${root.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize directory structure: ${e.message}")
        }
    }
    
    private fun createReadmeFile(directory: File, fileName: String, content: String) {
        try {
            val readmeFile = File(directory, fileName)
            if (!readmeFile.exists()) {
                readmeFile.writeText(content)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to create README: ${e.message}")
        }
    }
    
    private fun getPresetsReadmeContent(): String {
        return """
            OBSIDIAN MILKDROP PRESETS
            =======================
            
            STEP-BY-STEP SETUP:
            
            1. FIND THIS FOLDER:
               - Open your file manager
               - Navigate to: Music/Obsidian/Milkdrop/presets
               - If you can't find it, open Obsidian and try the visualizer once
               
            2. GET PRESET FILES:
               - Download .milk preset files from:
                 http://milkdrop.projectm.sourceforge.net/
                 https://www.milkdrop.nl/ (community presets)
               - Or search: "MilkDrop preset pack download"
               
            3. COPY PRESETS HERE:
               - Drag & drop .milk files into this folder
               - Can be in subfolders (e.g., presets/Psychedelic/)
               - No special naming required
               
            4. ADD TEXTURES (IF NEEDED):
               - Some presets need texture files (.png, .jpg)
               - Copy those to: ../textures/
               - The preset will find them automatically
               
            5. LOAD IN OBSIDIAN:
               - Open Obsidian app
               - Go to Visualizer settings
               - Tap "Custom" tab
               - Tap "Refresh Presets" button
               - Select your preset and enjoy!
               
            TROUBLESHOOTING:
            
            ❌ "No presets found"
               → Check folder path shown in "Show Paths"
               → Verify .milk files are actually in the presets folder
               → Try "Refresh" button
               → Restart the app
               
            ❌ Presets don't show up
               → Check file permissions
               → Make sure files have .milk extension (not .txt!)
               → Files may have load errors - check "Show Paths"
               
            ❌ Presets crash the app
               → Some complex presets may not be fully supported
               → The app will use a fallback rendering
               → Remove problematic preset files
               
            ✓ TIPS:
               - Start with 10-20 presets to test
               - Complex expressions may render with fallback
               - Textures must be in ../textures/ folder
               - File names with special chars might cause issues
               - Organize into subfolders for easier management
        """.trimIndent()
    }
    
    private fun getTexturesReadmeContent(): String {
        return """
            OBSIDIAN MILKDROP TEXTURES
            ==========================
            
            This folder contains texture files used by presets (.png, .jpg, .bmp, .webp).
        """.trimIndent()
    }
    
    private fun getEquationsReadmeContent(): String {
        return """
            OBSIDIAN MILKDROP EQUATIONS
            ==========================
            
            This folder contains custom equation files (.equation).
        """.trimIndent()
    }
    
    private fun getShapesReadmeContent(): String {
        return """
            OBSIDIAN MILKDROP SHAPES
            =======================
            
            This folder contains custom shape definitions.
        """.trimIndent()
    }
}
