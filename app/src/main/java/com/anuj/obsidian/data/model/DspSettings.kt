package com.anuj.obsidian.data.model

data class DspPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val iconEmoji: String,
    val description: String,
    val bassBoostDb: Float, // 0..12
    val trebleBoostDb: Float, // -6..12
    val soundstageWidth: Float, // 0..1.5 (1.0 = standard, 1.5 = ultra-wide 3D)
    val eqGains: List<Float>, // 10 bands: 31Hz, 63Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
    val dynamicRangeCompression: Boolean = false,
    val tubeWarmth: Boolean = false
)

val PREMADE_DSP_PRESETS = listOf(
    DspPreset(
        id = "preset_viper_fx_simulation",
        name = "ViPER-FX Simulation Mode",
        subtitle = "Rootless Engine Emulation",
        iconEmoji = "🐍",
        description = "Emulates ViPER4Android's signature V4A punchy bass extension, clarity processing, and multi-band dynamic limiting natively without requiring root.",
        bassBoostDb = 8.0f,
        trebleBoostDb = 4.5f,
        soundstageWidth = 1.30f,
        eqGains = listOf(8.0f, 6.0f, 3.5f, -1.0f, 1.0f, 2.5f, 4.0f, 5.0f, 4.5f, 3.0f),
        dynamicRangeCompression = true,
        tubeWarmth = true
    ),
    DspPreset(
        id = "preset_studio_master",
        name = "Audiophile Studio Master",
        subtitle = "Ultra-Transparent & Airy",
        iconEmoji = "💎",
        description = "High-precision master curve preserving natural dynamic range with airy high-frequency extension and tight bass response.",
        bassBoostDb = 2.0f,
        trebleBoostDb = 3.0f,
        soundstageWidth = 1.15f,
        eqGains = listOf(1.5f, 1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.5f, 3.0f),
        dynamicRangeCompression = false,
        tubeWarmth = false
    ),
    DspPreset(
        id = "preset_flat_reference",
        name = "Flat Reference Monitor",
        subtitle = "Zero Coloration Bit-Perfect",
        iconEmoji = "🏆",
        description = "100% flat acoustic response curve tailored for critical studio listening, testing IEMs, and pure bit-perfect DAC monitoring.",
        bassBoostDb = 0.0f,
        trebleBoostDb = 0.0f,
        soundstageWidth = 1.0f,
        eqGains = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
        dynamicRangeCompression = false,
        tubeWarmth = false
    ),
    DspPreset(
        id = "preset_tube_warmth",
        name = "Tube Warmth & Analog Tape",
        subtitle = "Rich Vintage Harmonics",
        iconEmoji = "📻",
        description = "Simulates vintage vacuum tube preamps and analog tape saturation with sweet 2nd-order harmonics and gentle high roll-off.",
        bassBoostDb = 3.5f,
        trebleBoostDb = 1.0f,
        soundstageWidth = 1.1f,
        eqGains = listOf(3.0f, 2.5f, 2.0f, 1.0f, 0.0f, -0.5f, -1.0f, 0.0f, 1.0f, 1.5f),
        dynamicRangeCompression = false,
        tubeWarmth = true
    ),
    DspPreset(
        id = "preset_sub_bass",
        name = "Sub-Bass & Deep Punch",
        subtitle = "Deep 30Hz-80Hz Energy",
        iconEmoji = "🔊",
        description = "Massive sub-bass resonance for EDM, Hip-Hop, and drill, delivering physical slam without muddying midrange vocals.",
        bassBoostDb = 7.5f,
        trebleBoostDb = 2.5f,
        soundstageWidth = 1.25f,
        eqGains = listOf(7.0f, 6.0f, 4.5f, 2.0f, 0.0f, 0.0f, 1.0f, 2.5f, 3.5f, 4.0f),
        dynamicRangeCompression = false,
        tubeWarmth = false
    ),
    DspPreset(
        id = "preset_vocal_clarity",
        name = "Vocal & Acoustic Intimacy",
        subtitle = "Forward Midrange & Air",
        iconEmoji = "🎙️",
        description = "Brings vocals, acoustic guitars, and pianos straight to the front row with silky presence and reduced boominess.",
        bassBoostDb = 0.5f,
        trebleBoostDb = 3.0f,
        soundstageWidth = 1.1f,
        eqGains = listOf(-1.0f, -0.5f, 0.0f, 1.5f, 3.0f, 3.5f, 2.5f, 2.0f, 3.0f, 2.5f),
        dynamicRangeCompression = false,
        tubeWarmth = false
    ),
    DspPreset(
        id = "preset_harman_target",
        name = "Harman Target Curve 2024",
        subtitle = "Headphone Gold Standard",
        iconEmoji = "🎧",
        description = "Calibrated to the famous Harman International target curve preferred by 85%+ of audio engineers and audiophiles globally.",
        bassBoostDb = 4.0f,
        trebleBoostDb = 2.0f,
        soundstageWidth = 1.1f,
        eqGains = listOf(4.5f, 4.0f, 2.5f, 0.5f, 0.0f, 0.5f, 1.5f, 2.5f, 1.0f, 1.5f),
        dynamicRangeCompression = false,
        tubeWarmth = false
    ),
    DspPreset(
        id = "preset_3d_spatial",
        name = "3D Spatial Holographic Stage",
        subtitle = "Binaural Crossfeed & Width",
        iconEmoji = "🌐",
        description = "Expands stereo imaging far beyond headphone bounds into a holographic 360° concert hall with custom interaural time delay.",
        bassBoostDb = 2.0f,
        trebleBoostDb = 4.0f,
        soundstageWidth = 1.45f,
        eqGains = listOf(1.0f, 1.0f, 0.0f, 0.0f, 0.5f, 1.0f, 2.0f, 3.0f, 4.0f, 4.5f),
        dynamicRangeCompression = false,
        tubeWarmth = false
    ),
    DspPreset(
        id = "preset_live_stadium",
        name = "Live Stadium Concert",
        subtitle = "Atmospheric Reverb & Depth",
        iconEmoji = "🏟️",
        description = "Simulates stadium acoustic reflections, crowd ambient width, and dynamic kick presence for live albums.",
        bassBoostDb = 3.5f,
        trebleBoostDb = 3.0f,
        soundstageWidth = 1.35f,
        eqGains = listOf(3.0f, 2.0f, 1.0f, 0.5f, 1.5f, 2.0f, 2.5f, 3.5f, 4.0f, 3.5f),
        dynamicRangeCompression = false,
        tubeWarmth = false
    ),
    DspPreset(
        id = "preset_treble_sparkle",
        name = "Treble Sparkle & Hi-Res Air",
        subtitle = "Crisp Cymbals & Micro-Detail",
        iconEmoji = "✨",
        description = "Ultra-fast transient response boosting 10kHz–20kHz air frequencies for acoustic strings, cymbals, and studio breath.",
        bassBoostDb = 0.0f,
        trebleBoostDb = 6.0f,
        soundstageWidth = 1.2f,
        eqGains = listOf(0.0f, 0.0f, -0.5f, 0.0f, 0.0f, 1.0f, 2.0f, 4.0f, 5.5f, 6.0f),
        dynamicRangeCompression = false,
        tubeWarmth = false
    ),
    DspPreset(
        id = "preset_night_mode",
        name = "Night Mode / Peak Limiter",
        subtitle = "Tamed Dynamics & Whispers",
        iconEmoji = "🌙",
        description = "Compresses sudden loud explosions and bass drops while elevating quiet dialogue and delicate instruments for late-night listening.",
        bassBoostDb = 1.0f,
        trebleBoostDb = -1.0f,
        soundstageWidth = 1.0f,
        eqGains = listOf(1.0f, 1.0f, 0.5f, 2.0f, 3.0f, 2.5f, 1.5f, 0.5f, -1.0f, -2.0f),
        dynamicRangeCompression = true,
        tubeWarmth = false
    ),
    DspPreset(
        id = "preset_rock_metal",
        name = "Rock & Metal Distortion Drive",
        subtitle = "Punchy Snare & Crunchy Guitars",
        iconEmoji = "🎸",
        description = "Tuned for distortion guitars, rapid double-bass pedals, and aggressive rock mastering with zero clipping distortion.",
        bassBoostDb = 4.0f,
        trebleBoostDb = 3.0f,
        soundstageWidth = 1.2f,
        eqGains = listOf(4.0f, 3.5f, 2.0f, -1.0f, 0.5f, 2.0f, 3.5f, 4.0f, 3.0f, 2.0f),
        dynamicRangeCompression = false,
        tubeWarmth = true
    ),
    DspPreset(
        id = "preset_classical_symphony",
        name = "Classical Symphony Orchestra",
        subtitle = "Dynamic Headroom & Separation",
        iconEmoji = "🎻",
        description = "Provides immense dynamic headroom for symphonic crescendos and precise spatial positioning of woodwinds, brass, and strings.",
        bassBoostDb = 1.5f,
        trebleBoostDb = 3.0f,
        soundstageWidth = 1.35f,
        eqGains = listOf(1.5f, 1.0f, 0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 3.5f),
        dynamicRangeCompression = false,
        tubeWarmth = false
    )
)

val EQ_BAND_FREQUENCIES = listOf("31 Hz", "63 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")

data class DspState(
    val isDspEnabled: Boolean = true,
    val activePresetId: String = "preset_studio_master",
    val bassBoostDb: Float = 2.0f,
    val trebleBoostDb: Float = 3.0f,
    val soundstageWidth: Float = 1.15f,
    val eqGains: List<Float> = listOf(1.5f, 1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.5f, 3.0f),
    val isBitPerfectDac: Boolean = true,
    val resamplingRate: String = "192.0 kHz (Studio Master)", // Native, 44.1 kHz, 48.0 kHz, 96.0 kHz, 192.0 kHz, 384.0 kHz DSD
    val replayGainMode: String = "Track Peak Normalization", // Off, Track Peak, Album Peak
    val ditheringMode: String = "Triangular TPDF (32-bit float)",
    val tubeWarmth: Boolean = false,
    val dynamicRangeCompression: Boolean = false
)
