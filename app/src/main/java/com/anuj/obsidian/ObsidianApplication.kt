package com.anuj.obsidian

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import com.anuj.obsidian.data.local.AppDatabase
import com.anuj.obsidian.data.repository.ObsidianRepository
import com.anuj.obsidian.ui.player.AudioPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ObsidianApplication : Application(), ImageLoaderFactory {
    companion object {
        @Volatile
        private var _instance: ObsidianApplication? = null
        
        val instance: ObsidianApplication
            get() = _instance ?: throw IllegalStateException("ObsidianApplication not initialized yet")

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.e("ObsidianApp", "Unhandled global exception", e) })
        
        val repository: ObsidianRepository by lazy {
            val database = AppDatabase.getDatabase(instance, applicationScope)
            ObsidianRepository(
                context = instance,
                trackDao = database.trackDao(),
                extensionDao = database.extensionDao(),
                downloadDao = database.downloadDao(),
                searchHistoryDao = database.searchHistoryDao(),
                playlistDao = database.playlistDao(),
                listeningStatsDao = database.listeningStatsDao(),
                cachedLyricDao = database.cachedLyricDao(),
                scope = applicationScope
            )
        }
        
        val playerManager: AudioPlayerManager by lazy {
            AudioPlayerManager(instance, applicationScope)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        _instance = this
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build()
            )
        }
        ObsidianCrashLogger.init(this)
        com.anuj.obsidian.AppInitializationGuard.performStartupCheck(this)
        com.anuj.obsidian.util.BluetoothAudioCodecManager.initialize(this)
        com.anuj.obsidian.audio.dac.UsbDacManager.init(this)
        com.anuj.obsidian.data.remote.nas.NetworkMusicServerManager.init(this)

        // Force early initialization of visualizer presets to eliminate race conditions
        try {
            android.util.Log.d("ObsidianApplication", "Initializing visualizer presets...")
            com.anuj.obsidian.ui.visualizer.CustomMilkdropManager.initializeSync(this)
            val presets = com.anuj.obsidian.ui.visualizer.VisualizerLibrary.presets
            android.util.Log.d("ObsidianApplication", "Loaded ${presets.size} visualizer presets")
            if (presets.isEmpty()) {
                android.util.Log.e("ObsidianApplication", "WARNING: Visualizer presets list is empty!")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("ObsidianApplication", "Failed to initialize visualizer presets: ${e.message}", e)
            ObsidianCrashLogger.logException(e, "ObsidianApplication.onCreate - visualizer init")
        }

        // Initialize MilkDrop preset loader and manager
        try {
            android.util.Log.d("ObsidianApplication", "Initializing custom MilkDrop presets...")
            val loaderService = com.anuj.obsidian.ui.visualizer.CustomPresetLoaderService.getInstance(this)
            applicationScope.launch(Dispatchers.IO) {
                try {
                    com.anuj.obsidian.ui.visualizer.CustomMilkdropManager.initialize(this@ObsidianApplication)
                    loaderService.initialize()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("ObsidianApplication", "MilkDrop loader init failed in worker: ${e.message}")
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("ObsidianApplication", "Failed to init MilkDrop loader: ${e.message}")
        }

        // Asynchronous storage guard: Enforce 500 MB limit on online songs artwork & media cache continuously
        com.anuj.obsidian.util.ObsidianStorageCleanupManager.startAutoCacheWatcher(applicationScope, this@ObsidianApplication)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "obsidian_art_cache"))
                    .maxSizeBytes(500L * 1024 * 1024) // 500 MB upper limit for online song artwork
                    .build()
            }
            .components {
                add(com.anuj.obsidian.data.local.TrackArtworkInterceptor(this@ObsidianApplication))
            }
            .precision(coil.size.Precision.INEXACT)
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
