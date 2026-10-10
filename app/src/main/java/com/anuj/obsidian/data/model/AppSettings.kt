package com.anuj.obsidian.data.model

@androidx.compose.runtime.Immutable
data class AudioQualityOption(
    val id: String,
    val title: String,
    val description: String,
    val bitDepth: String,
    val sampleRate: String,
    val isLossless: Boolean = true,
    val badgeColor: Long = 0xFFF5A623
)

val AvailableAudioQualities = listOf(
    AudioQualityOption("hi_res_192", "24-bit / 192.0 kHz Master FLAC", "Ultra Hi-Res Studio Master tier with bit-perfect depth", "24-bit", "192.0 kHz", true, 0xFFF5A623),
    AudioQualityOption("hi_res_96", "24-bit / 96.0 kHz Studio FLAC", "Audiophile standard high-resolution lossless audio", "24-bit", "96.0 kHz", true, 0xFF1DB954),
    AudioQualityOption("cd_44", "16-bit / 44.1 kHz CD Quality FLAC", "Lossless 1411 kbps Red Book standard", "16-bit", "44.1 kHz", true, 0xFF00E5FF),
    AudioQualityOption("high_320", "320 kbps High Quality MP3", "Compressed audio for saving internal device storage", "16-bit", "44.1 kHz", false, 0xFF94A3B8)
)

@androidx.compose.runtime.Immutable
data class AppSettings(
    val audioQualityId: String = "hi_res_96",
    val filenameFormat: String = "{artist} - {title}",
    val downloadFormat: String = "FLAC", // FLAC, WAV, MP3, M4A
    val downloadDestinationMode: String = "APP_MUSIC", // APP_MUSIC, CUSTOM_FOLDER, SYSTEM_MUSIC
    val storagePath: String = "",
    val customDownloadFolderUri: String = "",
    val customDownloadFolderName: String = "",
    val selectedMusicFolderUri: String = "",
    val selectedMusicFolderName: String = "",
    val linkedMusicFolders: List<Pair<String, String>> = emptyList(), // Pair of URI and Display Name
    val autoSyncFolderOnLaunch: Boolean = true,
    val embedLyrics: Boolean = true,
    val generateLrcFile: Boolean = false, // Off by default
    val embedMaxCoverArt: Boolean = true,
    val downloadConcurrentThreads: Int = 3,
    val downloadOverWifiOnly: Boolean = false,
    val autoRetryFailed: Boolean = true,
    val themeMode: String = "pitch_black", // pitch_black, dark_obsidian, emerald_neon
    val themePreset: String = "emerald", // emerald, cyberpunk, amber_gold, amethyst, crimson, matrix, sapphire, oled_stealth, custom
    val customPrimaryColor: Long = 0xFF1DB954L,
    val customSecondaryColor: Long = 0xFFF5A623L,
    val customBackgroundColor: Long = 0xFF090C10L,
    val visualizerPerformanceMode: String = "smooth_60", // smooth_60, ultra_low_power, minimalist, disabled
    val gaplessPlayback: Boolean = true, // Gapless playback with seamless Media3 queue pre-buffering
    val crossfadeDurationSec: Float = 0f, // 0f = 0ms true gapless cut, >0f = smooth crossfade
    val volumeNormalizationEnabled: Boolean = false, // ReplayGain style volume normalization
    val offlineMode: Boolean = false, // When enabled, forces player and media system to only pull tracks from local Room database and local storage
    val autoplayOnLaunch: Boolean = false, // When true, resumes playback automatically on app open. When false (default), keeps player paused on launch.
    val lastPlayedTrackId: Long = 0L,
    val lastPlayedPositionSec: Float = 0f,
    val lastPlayedTrackJson: String = "",
    val lastVisualizerPresetId: Int = 3,
    val lastVisualizerPresetName: String = "",
    val lastVisualizerPresetPath: String = "",
    val linkedCustomPresetFolders: List<Pair<String, String>> = emptyList(),
    val visualizerMasterEnabled: Boolean = true,
    val visualizerAboveChrome: Boolean = false, // When true, visualizer renders over player controls with subtle alpha; when false, in background
    val visualizerBackgroundMode: String = "dynamic", // dynamic (adapts to album art) vs fixed (pure dark/neutral, no art color bleed)
    val visualizerFpsLimit: Int = 60, // 30, 60, 120, 0 (uncapped)
    val visualizerStyleCategory: String = "ALL", // ALL, MILKDROP, BARS, CIRCULAR, WAVEFORM, PARTICLES
    val visualizerCycleMode: String = "off", // off, seq_10, seq_20, seq_30, seq_60, shuf_10, shuf_20, shuf_30, random_wild
    val backgroundVisualizerEnabled: Boolean = true,
    val visualizerShowEverywhere: Boolean = true, // When true, renders in background of Home, Library, Queue, and Player (Settings remains clean)
    val visualizerAlpha: Float = 0.85f,
    val visualizerColorMode: String = "ORIGINAL", // ORIGINAL, APP_THEME, CUSTOM
    val inactivityTimeoutMinutes: Int = 60, // 30, 60, 120, 0 (0 = disabled)
    val autoplayMode: String = "AUTOPLAY_LIBRARY",
    val advancedShuffleMode: String = "SHUFFLE_OFF", // SHUFFLE_OFF, SHUFFLE_ALL, SHUFFLE_TRACKS, SHUFFLE_CATEGORIES, SHUFFLE_SONGS_AND_CATEGORIES
    val queueReturnMode: String = "RETURN_TO_PREVIOUS", // RETURN_TO_PREVIOUS, CONTINUE_CATEGORY, STOP_PLAYBACK
    val headsetAutoresume: Boolean = false,
    val headsetFadeOutOnDisconnect: Boolean = false,
    val headsetFadeOutDurationSec: Float = 1.5f,
    val dynamicCanvasEnabled: Boolean = false,
    val dynamicCanvasPreset: String = "cosmic_nebula",
    val showLayoutBounds: Boolean = false,
    val customRepoUrl: String = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/registry.json",
    val backendServerUrl: String = "https://saavn.dev",
    val backendProvider: String = "Obsidian Universal Full-Length Cluster",
    val hasCompletedOnboarding: Boolean = false,
    val defaultLibraryTab: String = "SONGS",
    val hasSeenCoachMarkMiniPlayerSwipe: Boolean = false,
    val hasSeenCoachMarkShuffleLongPress: Boolean = false,
    val hasSeenCoachMarkSettingsSwipe: Boolean = false,
    val fullPlayerSpecsExpanded: Boolean = false,
    val dailyMixLastGeneratedDate: String = "",
    val dailyMixTrackIdsJson: String = ""
)

@androidx.compose.runtime.Immutable
data class SearchResultItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val coverUrl: String,
    val bitDepth: String = "16-bit",
    val sampleRate: String = "44.1 kHz",
    val provider: String = "Spotify / Qobuz",
    val isrc: String = "",
    val releaseYear: String = "2024",
    val isExplicit: Boolean = false,
    val streamUrl: String = "",
    val trackNumber: Int = 1,
    val artistId: String = "",
    val artistIds: List<String> = emptyList(),
    val singerMatchScore: Float = 1.0f
)
