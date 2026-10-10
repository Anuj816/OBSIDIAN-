package com.anuj.obsidian.data.model

data class ObsidianDdcProfile(
    val id: String,
    val name: String,
    val brand: String,
    val description: String,
    val eqCorrection: List<Float> // 10 bands delta EQ in dB
)

val OBSIDIAN_DDC_PROFILES = listOf(
    ObsidianDdcProfile(
        id = "ddc_flat_studio",
        name = "Studio Flat Target (Diffuse Field)",
        brand = "Obsidian Reference",
        description = "Neutral flat response for mastering monitors and clinical analysis.",
        eqCorrection = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_harman_oe_2018",
        name = "Harman Over-Ear Target (2018 Curve)",
        brand = "Harman Research",
        description = "Gold-standard acoustic target with smooth sub-bass shelf and pinna gain compensation.",
        eqCorrection = listOf(3.5f, 2.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.5f, 2.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_harman_ie_2019",
        name = "Harman In-Ear Target (2019 V2)",
        brand = "Harman Research",
        description = "Deep tactile sub-rumble with forward crystal vocal clarity for earphones.",
        eqCorrection = listOf(4.5f, 3.5f, 1.5f, 0.0f, 0.0f, 1.0f, 2.5f, 3.0f, 2.0f, 3.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sennheiser_hd650",
        name = "Sennheiser HD 600 / HD 650",
        brand = "Sennheiser",
        description = "Sub-bass extension correction and upper-treble veil compensation.",
        eqCorrection = listOf(3.5f, 2.5f, 0.5f, 0.0f, 0.0f, -0.5f, 0.5f, 1.5f, 2.0f, 2.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sennheiser_hd800s",
        name = "Sennheiser HD 800 S Reference",
        brand = "Sennheiser",
        description = "Sub-bass linear restoration and 6kHz resonance peak dampening for massive soundstage.",
        eqCorrection = listOf(4.0f, 3.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, -2.5f, 0.5f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sennheiser_ie600",
        name = "Sennheiser IE 600 / IE 200",
        brand = "Sennheiser",
        description = "Extra-wide dynamic micro-driver calibration with airy treble resolution.",
        eqCorrection = listOf(1.5f, 0.5f, -0.5f, 0.0f, 0.5f, 0.5f, 1.0f, -1.0f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sony_wh1000xm5",
        name = "Sony WH-1000XM5 / XM4 ANC",
        brand = "Sony",
        description = "Tames boomy 125Hz-250Hz bleed while boosting vocal presence and airy detail.",
        eqCorrection = listOf(1.0f, -1.0f, -2.5f, -1.5f, 0.5f, 1.5f, 2.0f, 1.0f, 3.0f, 3.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sony_wf1000xm5",
        name = "Sony WF-1000XM5 / XM4 TWS",
        brand = "Sony",
        description = "Balances warm punch with upper midrange opening and pristine sparkle.",
        eqCorrection = listOf(1.5f, 0.0f, -1.5f, -0.5f, 0.5f, 1.5f, 2.0f, 1.5f, 2.5f, 3.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sony_mdr7506",
        name = "Sony MDR-7506 Studio Classic",
        brand = "Sony",
        description = "Smooths aggressive 4kHz-10kHz monitoring bite and warms up lower mids.",
        eqCorrection = listOf(2.5f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.5f, -2.5f, -1.0f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_airpods_max",
        name = "Apple AirPods Max",
        brand = "Apple",
        description = "Harman curve alignment with enhanced soundstage depth and high-end air.",
        eqCorrection = listOf(1.5f, 1.0f, -0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_airpods_pro2",
        name = "Apple AirPods Pro 2 / USB-C",
        brand = "Apple",
        description = "Linear diffuse field calibration for natural timbre and ultra-crisp highs.",
        eqCorrection = listOf(1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 1.0f, 2.0f, 2.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_beyer_dt990",
        name = "Beyerdynamic DT 990 Pro (250Ω)",
        brand = "Beyerdynamic",
        description = "Smooths the 8kHz treble spike and lifts sub-bass punch for effortless listening.",
        eqCorrection = listOf(3.0f, 2.0f, 0.5f, 0.0f, 0.5f, 1.0f, 0.0f, -3.0f, -1.5f, 0.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_beyer_dt770",
        name = "Beyerdynamic DT 770 Pro (80Ω/250Ω)",
        brand = "Beyerdynamic",
        description = "Tames closed-back bass bloom and corrects sharp 6kHz peak for silky studio mastering.",
        eqCorrection = listOf(1.5f, 0.0f, -1.5f, -0.5f, 0.5f, 1.0f, 0.5f, -2.5f, -1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_beyer_dt1990",
        name = "Beyerdynamic DT 1990 Pro (Tesla)",
        brand = "Beyerdynamic",
        description = "Tesla driver 8.5kHz brightness filter with extended linear bass foundation.",
        eqCorrection = listOf(2.5f, 1.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, -2.0f, -3.0f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_ath_m50x",
        name = "Audio-Technica ATH-M50x",
        brand = "Audio-Technica",
        description = "Controls mid-bass punch and opens up wide soundstage separation.",
        eqCorrection = listOf(0.5f, -1.0f, -1.5f, 0.0f, 0.5f, 1.0f, 1.5f, 0.5f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_ath_r70x",
        name = "Audio-Technica ATH-R70x Open",
        brand = "Audio-Technica",
        description = "Reference open-back sub-bass reinforcement and upper-air shimmer extension.",
        eqCorrection = listOf(3.5f, 2.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_moondrop_blessing2",
        name = "Moondrop Blessing 2 / Dusk",
        brand = "Moondrop",
        description = "Hybrid dynamic/BA calibration for breathtaking acoustic string and vocal intimacy.",
        eqCorrection = listOf(2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 0.5f, 1.5f, 2.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_moondrop_aria",
        name = "Moondrop Aria / Chu II / Kato",
        brand = "Moondrop",
        description = "VDSF Target compensation bringing lifelike staging and refined top-end sparkle.",
        eqCorrection = listOf(2.5f, 1.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 1.0f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_shure_se215",
        name = "Shure SE215 / Aonic 215",
        brand = "Shure",
        description = "Lifts upper midrange and treble clarity while tightening warm stage bass.",
        eqCorrection = listOf(-0.5f, -1.0f, -1.0f, 0.0f, 1.0f, 2.0f, 3.0f, 3.5f, 4.0f, 4.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_shure_se846",
        name = "Shure SE846 Quad-BA Studio",
        brand = "Shure",
        description = "Micro-driver high-frequency extension and linear studio flat tuning.",
        eqCorrection = listOf(1.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 1.5f, 2.0f, 3.0f, 3.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_hifiman_sundara",
        name = "Hifiman Sundara / Planar Magnetic",
        brand = "Hifiman",
        description = "Planar magnetic low-end slam boost with ultra-linear harmonic transparency.",
        eqCorrection = listOf(3.5f, 2.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_hifiman_edition_xs",
        name = "Hifiman Edition XS / Ananda",
        brand = "Hifiman",
        description = "Stealth Magnet low-end punch and smooth 1kHz-3kHz pinna transition.",
        eqCorrection = listOf(3.0f, 2.0f, 0.5f, 0.0f, 0.5f, 1.0f, 1.0f, 0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_focal_clear",
        name = "Focal Clear / Utopia / Elear",
        brand = "Focal",
        description = "Magnesium dynamic dome calibration with breathtaking transient punch and air.",
        eqCorrection = listOf(2.5f, 1.5f, 0.5f, 0.0f, 0.0f, 0.5f, 0.0f, 1.0f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_bose_qc_ultra",
        name = "Bose QuietComfort 45 / Ultra",
        brand = "Bose",
        description = "Smooths mid-bass hump and unlocks sparkling vocal clarity across ANC modes.",
        eqCorrection = listOf(1.5f, 0.0f, -2.0f, -1.0f, 0.5f, 1.5f, 2.0f, 1.5f, 2.5f, 3.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_samsung_buds2_pro",
        name = "Samsung Galaxy Buds 2 Pro / 3",
        brand = "Samsung",
        description = "Harman target precision tuning with deep subterranean bass and crisp vocals.",
        eqCorrection = listOf(1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 1.0f, 2.0f, 2.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_akg_k702",
        name = "AKG K702 / K240 Studio",
        brand = "AKG",
        description = "Sub-bass warmth injection for famous wide Austrian soundstage monitoring.",
        eqCorrection = listOf(4.5f, 3.5f, 1.5f, 0.5f, 0.0f, -0.5f, 0.5f, 0.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_etymotic_er4xr",
        name = "Etymotic ER4XR Diffuse Target",
        brand = "Etymotic",
        description = "Deep-insertion BA monitor sub-bass lift and clinical studio fidelity.",
        eqCorrection = listOf(3.5f, 2.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 2.0f, 2.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_tube_analog_warmth",
        name = "Vintage 12AX7 Analog Tube Master",
        brand = "Analog Tube FX",
        description = "Simulates warm triode tube saturation, lush midrange and velvety smooth highs.",
        eqCorrection = listOf(2.0f, 2.5f, 2.0f, 1.0f, 0.5f, 0.0f, 0.5f, -0.5f, -1.0f, -2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_audeze_lcd2",
        name = "Audeze LCD-2 Classic",
        brand = "Audeze",
        description = "Planar magnetic bass extension and smooth, relaxed upper midrange.",
        eqCorrection = listOf(2.0f, 1.0f, 0.5f, 0.0f, 0.0f, 1.5f, 2.5f, 1.0f, 0.0f, -0.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_audeze_lcd_x",
        name = "Audeze LCD-X Reference",
        brand = "Audeze",
        description = "Neutral studio monitor planar target with ultra-low distortion.",
        eqCorrection = listOf(0.5f, 0.0f, 0.0f, 0.0f, -1.0f, 1.5f, 2.0f, 0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_focal_clear_mg",
        name = "Focal Clear Mg",
        brand = "Focal",
        description = "Dynamic punch and metallic dome resonance smoothing.",
        eqCorrection = listOf(1.5f, 1.0f, 0.0f, 0.0f, 0.5f, -0.5f, 1.0f, -1.5f, -0.5f, 0.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_focal_utopia_bex",
        name = "Focal Utopia Beryllium",
        brand = "Focal",
        description = "Beryllium driver 6kHz peak taming and sub-bass shelf lift.",
        eqCorrection = listOf(2.5f, 1.5f, 0.5f, 0.0f, 0.5f, 0.0f, -1.0f, -2.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_hifiman_arya_stealth",
        name = "HiFiMan Arya Stealth",
        brand = "HiFiMan",
        description = "Fills in the 1.5kHz dip and smooths the treble for massive vertical soundstage.",
        eqCorrection = listOf(2.0f, 1.0f, 0.0f, 0.0f, 1.5f, 2.5f, 0.0f, -1.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_meze_empyrean",
        name = "Meze Empyrean",
        brand = "Meze Audio",
        description = "Tightens the warm mid-bass and lifts the upper treble air.",
        eqCorrection = listOf(0.5f, -1.5f, -1.0f, 0.0f, 1.0f, 2.0f, 1.5f, 0.5f, 2.5f, 3.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_moondrop_blessing3_crin",
        name = "Moondrop Blessing 3 / Dusk",
        brand = "Moondrop",
        description = "Crinacle target matching with smooth treble and authoritative sub-bass.",
        eqCorrection = listOf(2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 0.0f, 1.5f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_7hz_timeless",
        name = "7Hz Timeless Planar",
        brand = "7Hz",
        description = "Tames the planar treble peak and warms the lower midrange.",
        eqCorrection = listOf(1.0f, 1.5f, 1.0f, 0.0f, -0.5f, 0.5f, 1.0f, -1.5f, 0.5f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_thieaudio_monarch",
        name = "Thieaudio Monarch MkII",
        brand = "Thieaudio",
        description = "Tribrid endgame linear correction with enhanced 3D spatial air.",
        eqCorrection = listOf(0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 0.5f, 1.0f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_grado_sr80e",
        name = "Grado SR80e / SR225",
        brand = "Grado",
        description = "Reduces the harsh 2kHz-4kHz Grado peak and extends sub-bass roll-off.",
        eqCorrection = listOf(5.0f, 3.5f, 1.0f, 0.0f, -1.0f, -2.5f, -3.0f, -1.0f, 0.5f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_zmf_verite",
        name = "ZMF Verite Closed",
        brand = "ZMF",
        description = "Beryllium dynamic magic with slight upper midrange forwardness.",
        eqCorrection = listOf(1.5f, 1.0f, 0.0f, 0.5f, 1.5f, 2.0f, 0.0f, -0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_dan_clark_stealth",
        name = "Dan Clark Audio Stealth",
        brand = "Dan Clark",
        description = "Metamaterial tuning preservation with slight punch enhancement.",
        eqCorrection = listOf(2.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, -0.5f, 0.5f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sennheiser_hd560s",
        name = "Sennheiser HD 560S Reference",
        brand = "Sennheiser",
        description = "Linear diffuse field target with extended linear sub-bass response.",
        eqCorrection = listOf(3.0f, 2.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, -0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sennheiser_momentum4",
        name = "Sennheiser Momentum 4 Wireless",
        brand = "Sennheiser",
        description = "Smooths heavy mid-bass boom and opens up sparkling vocal clarity.",
        eqCorrection = listOf(0.5f, -1.5f, -2.0f, -0.5f, 0.5f, 1.5f, 2.0f, 1.0f, 2.5f, 3.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_focal_bathys",
        name = "Focal Bathys Hi-Fi ANC",
        brand = "Focal",
        description = "Audiophile ANC calibration with deep natural sub-bass and detailed highs.",
        eqCorrection = listOf(1.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 0.5f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_koss_porta_pro",
        name = "Koss Porta Pro / KSC75",
        brand = "Koss",
        description = "Sub-bass extension and upper-treble detail filter for vintage retro drivers.",
        eqCorrection = listOf(4.0f, 2.5f, -1.0f, -0.5f, 0.0f, 1.0f, 2.0f, 1.5f, 2.5f, 3.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_final_e3000",
        name = "Final Audio E3000 / E5000",
        brand = "Final Audio",
        description = "Micro dynamic driver warmth cleanup and spacious soundstage tuning.",
        eqCorrection = listOf(1.0f, -0.5f, -1.0f, 0.0f, 0.5f, 1.5f, 2.0f, 1.0f, 2.0f, 2.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_beats_studio_pro",
        name = "Beats Studio Pro Spatial",
        brand = "Beats",
        description = "Balanced acoustic target reducing muddy bass and boosting upper midrange.",
        eqCorrection = listOf(0.5f, -1.0f, -2.0f, -0.5f, 0.5f, 1.5f, 2.0f, 1.5f, 2.0f, 2.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_bo_beoplay_h95",
        name = "Bang & Olufsen Beoplay H95",
        brand = "Bang & Olufsen",
        description = "Titanium driver precision calibration for ultra-transparent acoustic fidelity.",
        eqCorrection = listOf(1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.0f, 0.5f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_soundcore_liberty4",
        name = "Anker Soundcore Liberty 4 NC",
        brand = "Anker Soundcore",
        description = "Tames V-shaped harshness and elevates natural vocal timbre.",
        eqCorrection = listOf(0.0f, -1.5f, -2.0f, 0.0f, 0.5f, 1.5f, 1.0f, -1.0f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_hifiman_he400se",
        name = "HiFiMan HE400se Planar",
        brand = "HiFiMan",
        description = "Budget planar magnetic sub-bass slam boost and 2kHz pinna correction.",
        eqCorrection = listOf(3.5f, 2.0f, 0.5f, 0.0f, 0.5f, 1.5f, 1.0f, -1.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_ath_m40x",
        name = "Audio-Technica ATH-M40x",
        brand = "Audio-Technica",
        description = "Flatter studio reference response than M50x with extended highs.",
        eqCorrection = listOf(2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.0f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_truthear_zero_red",
        name = "Truthear x Crinacle Zero:RED",
        brand = "Truthear",
        description = "Sub-woofer dual dynamic driver calibration with silky smooth treble.",
        eqCorrection = listOf(1.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_tangzu_waner",
        name = "Tangzu Wan'er S.G Studio",
        brand = "Tangzu",
        description = "PET diaphragm tuning brings rich vocal emotion and crisp percussive snap.",
        eqCorrection = listOf(2.0f, 1.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 1.0f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_7hz_salnotes_zero",
        name = "7Hz Salnotes Zero Reference",
        brand = "7Hz",
        description = "Ultra-budget reference monitor sub-bass lift and smooth pinna gain.",
        eqCorrection = listOf(2.5f, 1.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 0.5f, 1.0f, 1.5f)
    ),
    // --- Extensive Additional DDC Profiles ---
    ObsidianDdcProfile(
        id = "ddc_apple_airpods_max_usbc",
        name = "Apple AirPods Max (USB-C / Lighting)",
        brand = "Apple",
        description = "Optimizes Computational Audio curve with extended sub-bass punch and natural spatial treble.",
        eqCorrection = listOf(1.5f, 0.5f, 0.0f, 0.0f, -0.5f, 0.5f, 1.0f, -0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sennheiser_ie600",
        name = "Sennheiser IE 600 Audiophile IEM",
        brand = "Sennheiser",
        description = "Resonance-molded AML driver with tamed 7kHz peak and velvety liquid vocals.",
        eqCorrection = listOf(1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 0.5f, -2.0f, 0.5f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sennheiser_ie200",
        name = "Sennheiser IE 200 Tape-Mod Target",
        brand = "Sennheiser",
        description = "Seals acoustic vent leakage to deliver deep sub-bass slam and smooth analytical treble.",
        eqCorrection = listOf(3.5f, 2.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 0.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sennheiser_hd660s2",
        name = "Sennheiser HD 660S2 Warm Open-Back",
        brand = "Sennheiser",
        description = "Improves 30Hz sub-bass extension while preserving legendary Sennheiser vocal intimacy.",
        eqCorrection = listOf(3.0f, 1.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_beyerdynamic_dt700_pro_x",
        name = "Beyerdynamic DT 700 Pro X",
        brand = "Beyerdynamic",
        description = "Stellar.45 driver calibration taming the classic Beyer treble spike with punchy bass.",
        eqCorrection = listOf(1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 0.0f, -2.5f, 0.0f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_beyerdynamic_dt900_pro_x",
        name = "Beyerdynamic DT 900 Pro X",
        brand = "Beyerdynamic",
        description = "Open studio reference calibration with linear bass response and airy treble presentation.",
        eqCorrection = listOf(2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f, -1.5f, 0.5f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_beyerdynamic_dt1990_pro",
        name = "Beyerdynamic DT 1990 Pro (Analytical)",
        brand = "Beyerdynamic",
        description = "Tesla driver 8kHz peak suppression filter for fatigue-free mastering and mixing.",
        eqCorrection = listOf(1.5f, 0.5f, 0.0f, -0.5f, 0.0f, 0.5f, 0.0f, -4.0f, -1.0f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sony_wf1000xm5",
        name = "Sony WF-1000XM5 TWS",
        brand = "Sony",
        description = "Dynamic Driver X correction reducing muddy mid-bass and brightening upper air.",
        eqCorrection = listOf(0.5f, -1.0f, -2.0f, -0.5f, 0.5f, 1.5f, 2.0f, 1.0f, 2.5f, 3.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_sony_mdr7506",
        name = "Sony MDR-7506 Industry Standard",
        brand = "Sony",
        description = "Smooths the harsh 3kHz upper-mid peak and extends deep sub-bass response.",
        eqCorrection = listOf(3.0f, 1.5f, 0.0f, 0.0f, -1.0f, -2.0f, -0.5f, 0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_moondrop_chu2",
        name = "Moondrop Chu II Metal Dynamic",
        brand = "Moondrop",
        description = "Al-Mg alloy dome target correction delivering tactile bass slam and detailed highs.",
        eqCorrection = listOf(1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.0f, 0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_moondrop_kato",
        name = "Moondrop Kato / Starfield II",
        brand = "Moondrop",
        description = "ULT dynamic driver ultra-linear VDSF target alignment for natural acoustic timbre.",
        eqCorrection = listOf(1.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 0.0f, 1.5f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_moondrop_variations",
        name = "Moondrop Variations Tribrid",
        brand = "Moondrop",
        description = "Electrostatic treble smoothing and mid-bass tuck for extreme resolution.",
        eqCorrection = listOf(0.5f, 0.0f, 0.5f, 1.0f, 0.5f, 0.5f, 1.0f, -1.0f, 0.5f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_truthear_hexa",
        name = "Truthear Hexa 1DD+3BA Hybrid",
        brand = "Truthear",
        description = "Neutral analytical target with subtle sub-bass shelf and crisp, airy treble extension.",
        eqCorrection = listOf(2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_truthear_nova",
        name = "Truthear Nova Harman Target",
        brand = "Truthear",
        description = "2019 Harman IE curve perfection with clean bass separation and vivid vocal projection.",
        eqCorrection = listOf(1.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_simgot_ea500lm",
        name = "Simgot EA500 LM Lithium-Magnesium",
        brand = "Simgot",
        description = "Tames the upper-mid energy peak and enhances deep sub-bass rumble.",
        eqCorrection = listOf(2.0f, 1.0f, 0.0f, 0.0f, -1.0f, -1.5f, 0.0f, 0.5f, 1.5f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_simgot_em6l",
        name = "Simgot EM6L Phoenix Hybrid",
        brand = "Simgot",
        description = "Gaming and music hybrid profile with pinpoint spatial positioning and smooth highs.",
        eqCorrection = listOf(1.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 0.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_letshuoer_s12pro",
        name = "Letshuoer S12 Pro Planar",
        brand = "Letshuoer",
        description = "Subdues 8kHz planar sizzle while retaining astonishing speed and micro-detail.",
        eqCorrection = listOf(1.5f, 1.0f, 0.5f, 0.0f, -0.5f, 0.5f, 0.0f, -3.0f, 0.0f, 1.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_dunu_sa6_mk2",
        name = "DUNU Studio SA6 MkII",
        brand = "DUNU",
        description = "6BA custom tuning with atmosphere bass boost switch and lush vocal warmth.",
        eqCorrection = listOf(1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.0f, 0.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_dunu_titan_s",
        name = "DUNU Titan S Cyber-Style",
        brand = "DUNU",
        description = "Poly-condensed crystal diaphragm tuning for crisp snare snap and transparent vocals.",
        eqCorrection = listOf(2.5f, 1.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_ath_r70x",
        name = "Audio-Technica ATH-R70x Open Reference",
        brand = "Audio-Technica",
        description = "470 ohm flagship reference open-back with sub-bass extension and holographic stage.",
        eqCorrection = listOf(3.0f, 1.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_shure_se215",
        name = "Shure SE215 Dynamic Stage Monitor",
        brand = "Shure",
        description = "Reduces heavy dark bass veil and elevates upper treble clarity for live performance.",
        eqCorrection = listOf(-1.5f, -2.0f, -1.0f, 0.0f, 1.0f, 2.0f, 3.0f, 2.0f, 3.5f, 4.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_thieaudio_hype2",
        name = "Thieaudio Hype 2 IMPACT2 Subwoofer",
        brand = "Thieaudio",
        description = "Isobaric dual dynamic subwoofer calibration with pristine Sonion BA treble.",
        eqCorrection = listOf(0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 0.5f, 1.0f, 1.5f)
    ),
    ObsidianDdcProfile(
        id = "ddc_campfire_andromeda",
        name = "Campfire Audio Andromeda 2020",
        brand = "Campfire Audio",
        description = "Tuned Acoustic Expansion Chamber (TAEC) correction balancing zero-impedance bass.",
        eqCorrection = listOf(1.5f, 1.0f, 0.5f, 0.0f, -0.5f, 0.5f, -1.0f, -2.0f, 1.0f, 2.0f)
    ),
    ObsidianDdcProfile(
        id = "ddc_shure_srh1840",
        name = "Shure SRH1840 Professional Mastering",
        brand = "Shure",
        description = "Open-back mastering headphone sub-bass shelf boost and crystalline high-end air.",
        eqCorrection = listOf(3.5f, 2.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 1.0f, 1.5f)
    )
)

data class ObsidianImpulseResponse(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val roomDecayMs: Int,
    val stereoDiffusion: Float
)

val OBSIDIAN_IMPULSE_RESPONSES = listOf(
    ObsidianImpulseResponse(
        id = "ir_abbey_studio",
        name = "Abbey Road Studio Control Room",
        category = "Studio Acoustics",
        description = "World-class acoustic diffusion with tight bass control and pristine imaging.",
        roomDecayMs = 600,
        stereoDiffusion = 0.85f
    ),
    ObsidianImpulseResponse(
        id = "ir_gold_chamber",
        name = "Gold Foil EMT 140 Plate Chamber",
        category = "Analog Vintage",
        description = "Silky analog vintage plate reverberation adding rich golden sheen to vocals.",
        roomDecayMs = 1200,
        stereoDiffusion = 0.95f
    ),
    ObsidianImpulseResponse(
        id = "ir_concert_hall",
        name = "Vienna Philharmonic Concert Hall",
        category = "Classical Hall",
        description = "Spacious three-dimensional acoustic reflections for symphonies and live recordings.",
        roomDecayMs = 2100,
        stereoDiffusion = 1.25f
    ),
    ObsidianImpulseResponse(
        id = "ir_jazz_club",
        name = "Blue Note Acoustic Jazz Club",
        category = "Intimate Venue",
        description = "Warm wooden reflection acoustics that make instruments feel in the room with you.",
        roomDecayMs = 850,
        stereoDiffusion = 0.75f
    ),
    ObsidianImpulseResponse(
        id = "ir_analog_tape",
        name = "Studer A800 2-Inch Analog Tape Console",
        category = "Harmonic Saturator",
        description = "Analog console magnetic tape warmth and subtle harmonic spatial glue.",
        roomDecayMs = 300,
        stereoDiffusion = 0.50f
    )
,

    ObsidianImpulseResponse(
        id = "ir_lexicon_480l",
        name = "Lexicon 480L Large Hall",
        category = "Digital Vintage",
        description = "The industry standard 80s digital reverb. Lush, dense, and instantly recognizable.",
        roomDecayMs = 2800,
        stereoDiffusion = 1.15f
    ),
    ObsidianImpulseResponse(
        id = "ir_bricasti_m7",
        name = "Bricasti M7 Sunset Chamber",
        category = "Modern Studio",
        description = "Incredibly realistic spatial depth with flawless pristine decay tails.",
        roomDecayMs = 1400,
        stereoDiffusion = 0.90f
    ),
    ObsidianImpulseResponse(
        id = "ir_emt_250",
        name = "EMT 250 Electronic Reverberator",
        category = "Analog Vintage",
        description = "The first digital reverb ever made. Thick, metallic, and beautiful.",
        roomDecayMs = 1800,
        stereoDiffusion = 0.80f
    ),
    ObsidianImpulseResponse(
        id = "ir_sydney_opera",
        name = "Sydney Opera House",
        category = "Classical Hall",
        description = "Massive acoustic scale and complex early reflection geometry.",
        roomDecayMs = 3200,
        stereoDiffusion = 1.35f
    ),
    ObsidianImpulseResponse(
        id = "ir_taj_mahal",
        name = "Taj Mahal Marble Mausoleum",
        category = "Extreme Space",
        description = "Near-infinite marble decay. Huge 8-second ethereal wash.",
        roomDecayMs = 8000,
        stereoDiffusion = 1.50f
    ),
    ObsidianImpulseResponse(
        id = "ir_car_interior",
        name = "Luxury SUV Acoustic Cabin",
        category = "Intimate Venue",
        description = "Tight, damped leather interior reflection modeling.",
        roomDecayMs = 150,
        stereoDiffusion = 0.40f
    ),)

data class ObsidianDspPreset(
    val id: String,
    val name: String,
    val description: String,
    val eqGains: List<Float>,
    val bassGainDb: Float = 0f,
    val isBassEnabled: Boolean = true,
    val bassMode: String = "Natural Bass",
    val clarityGainDb: Float = 0f,
    val isClarityEnabled: Boolean = false,
    val isFieldSurroundEnabled: Boolean = false,
    val surroundWidthPercent: Float = 100f,
    val isConvolverEnabled: Boolean = false,
    val selectedImpulseId: String = "ir_abbey_studio",
    val convolverWetPercent: Float = 0f
)

val OBSIDIAN_DSP_PRESETS = listOf(
    ObsidianDspPreset(
        id = "preset_studio_flat",
        name = "Studio Flat",
        description = "Neutral flat response for mastering monitors, clinical accuracy & bit-perfect playback.",
        eqGains = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
        bassGainDb = 0f,
        isBassEnabled = false,
        clarityGainDb = 0f,
        isClarityEnabled = false,
        isFieldSurroundEnabled = false,
        surroundWidthPercent = 100f,
        isConvolverEnabled = false,
        convolverWetPercent = 0f
    ),
    ObsidianDspPreset(
        id = "preset_bass_boost",
        name = "Bass Boost",
        description = "Deep low-end rumble and punchy kick drums with dynamic sub-woofer excitation.",
        eqGains = listOf(9.0f, 7.5f, 4.5f, 2.0f, 0.0f, 0.0f, 0.5f, 1.0f, 2.0f, 2.5f),
        bassGainDb = 10.5f,
        isBassEnabled = true,
        bassMode = "Sub-Woofer",
        clarityGainDb = 2.0f,
        isClarityEnabled = true,
        isFieldSurroundEnabled = true,
        surroundWidthPercent = 115f,
        isConvolverEnabled = false,
        convolverWetPercent = 0f
    ),
    ObsidianDspPreset(
        id = "preset_vocal_clarity",
        name = "Vocal Clarity",
        description = "Emphasizes speech, podcasts, acoustic timbre, and pristine upper vocal frequencies.",
        eqGains = listOf(-1.5f, -0.5f, 0.0f, 1.5f, 3.0f, 5.0f, 5.5f, 4.0f, 3.0f, 2.0f),
        bassGainDb = 1.5f,
        isBassEnabled = false,
        clarityGainDb = 7.0f,
        isClarityEnabled = true,
        isFieldSurroundEnabled = true,
        surroundWidthPercent = 125f,
        isConvolverEnabled = false,
        convolverWetPercent = 0f
    ),
    ObsidianDspPreset(
        id = "preset_concert_hall",
        name = "Concert Hall",
        description = "Impulse response reverb convolution modeling grand symphony hall acoustics & depth.",
        eqGains = listOf(2.5f, 1.5f, 0.5f, 0.0f, 0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 4.5f),
        bassGainDb = 3.5f,
        isBassEnabled = true,
        clarityGainDb = 3.0f,
        isClarityEnabled = true,
        isFieldSurroundEnabled = true,
        surroundWidthPercent = 160f,
        isConvolverEnabled = true,
        selectedImpulseId = "ir_concert_hall",
        convolverWetPercent = 45f
    ),
    ObsidianDspPreset(
        id = "preset_spatial_surround",
        name = "Spatial Surround",
        description = "3D binaural Haas-effect soundstage widener with immersive psychoacoustic separation.",
        eqGains = listOf(3.0f, 2.0f, 1.0f, 0.0f, 0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.0f),
        bassGainDb = 5.0f,
        isBassEnabled = true,
        clarityGainDb = 4.0f,
        isClarityEnabled = true,
        isFieldSurroundEnabled = true,
        surroundWidthPercent = 180f,
        isConvolverEnabled = true,
        selectedImpulseId = "ir_abbey_studio",
        convolverWetPercent = 25f
    ),
    ObsidianDspPreset(
        id = "preset_warm_wide",
        name = "Warm & Wide",
        description = "Analog tube saturation + stereo width enhancement + gentle harmonic bass presence.",
        eqGains = listOf(2.0f, 1.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 1.5f),
        bassGainDb = 4.5f,
        isBassEnabled = true,
        clarityGainDb = 3.0f,
        isClarityEnabled = true,
        isFieldSurroundEnabled = true,
        surroundWidthPercent = 145f,
        isConvolverEnabled = false,
        convolverWetPercent = 0f
    ),
    ObsidianDspPreset(
        id = "preset_bass_tube_impact",
        name = "Bass & Tube Impact",
        description = "Deep sub-woofer harmonic excitation with 12AX7 triode tube warmth.",
        eqGains = listOf(8.0f, 6.0f, 3.5f, 1.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f),
        bassGainDb = 9.0f,
        isBassEnabled = true,
        clarityGainDb = 2.0f,
        isClarityEnabled = true,
        isFieldSurroundEnabled = true,
        surroundWidthPercent = 120f,
        isConvolverEnabled = false,
        convolverWetPercent = 0f
    ),
    ObsidianDspPreset(
        id = "preset_headphone_realism",
        name = "Headphone Realism",
        description = "Bauer crossfeed binaural simulation with Haas soundstage delay for natural fatigue-free listening.",
        eqGains = listOf(1.5f, 1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f),
        bassGainDb = 3.0f,
        isBassEnabled = true,
        clarityGainDb = 4.0f,
        isClarityEnabled = true,
        isFieldSurroundEnabled = true,
        surroundWidthPercent = 135f,
        isConvolverEnabled = true,
        selectedImpulseId = "ir_abbey_studio",
        convolverWetPercent = 20f
    )
)

data class ObsidianDspState(
    val isMasterEnabled: Boolean = true,
    
    // Obsidian-DDC
    val isDdcEnabled: Boolean = true,
    val selectedDdcId: String = "ddc_flat_studio",
    
    // Obsidian Dynamic Bass
    val isBassEnabled: Boolean = true,
    val bassMode: String = "Natural Bass", // "Natural Bass", "Pure Bass", "Sub-Woofer"
    val bassFrequencyHz: Int = 60, // 40, 60, 80, 100
    val bassGainDb: Float = 6.0f, // 0.0 .. 18.0 dB
    
    // Obsidian Clarity
    val isClarityEnabled: Boolean = true,
    val clarityMode: String = "Natural", // "Natural", "Ozone+", "X-HiFi"
    val clarityGainDb: Float = 4.5f, // 0.0 .. 14.0 dB
    
    // Obsidian Tube Simulator
    val isTubeEnabled: Boolean = false,
    val tubeType: String = "6SN7 Triode Master", // "6SN7 Triode Master", "12AX7 Vintage Warmth", "300B Audiophile Pure"
    val tubeDrivePercent: Float = 40f, // 0 .. 100 %
    
    // Convolver
    val isConvolverEnabled: Boolean = false,
    val selectedImpulseId: String = "ir_abbey_studio",
    val convolverWetPercent: Float = 25f, // 0 .. 100 %
    val customImpulseFilePath: String = "",
    val customImpulseFileName: String = "",
    
    // Field Surround / Haas Effect
    val isFieldSurroundEnabled: Boolean = true,
    val haasDelayMs: Float = 14.0f, // 5.0 .. 35.0 ms
    val surroundWidthPercent: Float = 120f, // 50 .. 200 %
    
    // Dynamic System & Limiter
    val isDynamicSystemEnabled: Boolean = true,
    val limiterThresholdDb: Float = -0.5f, // -6.0 .. 0.0 dB
    val dynamicBassProtection: Boolean = true,
    
    // Multi-Band Graphic EQ (10, 15, 25, 31 bands)
    val isEqEnabled: Boolean = true,
    val eqBandMode: Int = 10,
    val eqGains: List<Float> = listOf(2.0f, 1.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 2.0f, 3.0f, 3.5f),
    val preampGainDb: Float = 0.0f // -12.0 .. +12.0 dB
)
