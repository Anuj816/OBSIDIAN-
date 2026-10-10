package com.anuj.obsidian

import android.content.Context
import android.util.Log
import java.io.File

object AppInitializationGuard {
    private const val TAG = "AppInitGuard"
    private const val PREFS_NAME = "obsidian_audio_prefs"

    /**
     * Verifies critical subsystems (SharedPreferences, file system paths) on startup.
     * If corruption or access issues are detected, it aggressively clears the corrupted
     * state to ensure the app can still launch cleanly.
     */
    fun performStartupCheck(context: Context) {
        Log.d(TAG, "Running AppInitializationGuard checks...")
        
        // 1. SharedPreferences Integrity Check
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // Attempt a simple read to trigger parsing
            prefs.all
        } catch (e: Throwable) {
            Log.e(TAG, "SharedPreferences corruption detected! Wiping preferences.", e)
            try {
                // In case of XML corruption, delete the shared prefs file directly
                val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml")
                if (prefsFile.exists()) {
                    prefsFile.delete()
                    Log.d(TAG, "Deleted corrupted SharedPreferences file.")
                }
            } catch (deleteEx: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(deleteEx, "AppInitializationGuard.performStartupCheck - attempt")
            
                Log.e(TAG, "Failed to delete corrupted SharedPreferences file.", deleteEx)
            }
        }

        // 2. File System Permission/Cache Check
        try {
            val cacheDir = context.cacheDir
            if (cacheDir != null && !cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            // Check for read/write on primary directories
            val testFile = File(cacheDir, "init_check.tmp")
            testFile.writeText("ok")
            testFile.delete()
        } catch (e: Throwable) {
            Log.e(TAG, "File system sanity check failed! Clearing cache...", e)
            try {
                context.cacheDir?.deleteRecursively()
            } catch (clearEx: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(clearEx, "AppInitializationGuard.performStartupCheck - attempt")
            
                Log.e(TAG, "Failed to clear cache.", clearEx)
            }
        }
        
        Log.d(TAG, "AppInitializationGuard checks complete.")
    }
}
