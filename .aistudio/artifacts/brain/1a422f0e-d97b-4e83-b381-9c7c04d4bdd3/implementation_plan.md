# Obsidian Audiophile Ultimate Suite: Karaoke Lyrics, Bit-Perfect USB DAC, Pro DJ Mixer & Audio Fingerprinting

A comprehensive expansion delivering animated syllable/line-synchronized karaoke lyrics with romanization, bit-perfect direct USB DAC exclusive audio telemetry, an interactive Pro DJ dual deck mixer with hot cues & beatmatching, and a real-time smart audio fingerprinting song recognizer.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All requested additions ("all this") have been integrated into a unified, modular audiophile architecture that maintains Obsidian's true black aesthetic, 60 FPS hardware OpenGL visualizer pipeline, and lossless ExoPlayer foundation.

- **Confirmed Decision 1 (Synchronized Karaoke Lyrics & Romanization)**: Implement high-precision time-synced lyrics with word-by-word highlight animations, romanization (Romaji, Pinyin, Hangul), bilingual translation toggle, and tap-to-seek line scrolling.
- **Confirmed Decision 2 (Bit-Perfect USB DAC Direct Output Telemetry)**: Provide USB Audio device detection, sample-rate locking telemetry (bypassing Android OS mixer/resampler), bit-perfect badge verification, and detailed hardware DAC inspector.
- **Confirmed Decision 3 (Pro DJ Dual Deck Mixer)**: Build a dedicated Dual Deck A/B DJ studio screen featuring independent tempo/pitch sliders, crossfader, 4 hot cue pads per deck, beat loopers (1/4 to 8 beats), and 3-band EQ kill switches.
- **Confirmed Decision 4 (Smart Audio Fingerprinting & Recognition)**: Implement a microphone audio listener with real-time FFT spectrogram analysis, acoustic peak matching against online music APIs, and instant one-tap download / play queueing.

---

## 1. Overview & Core Concept

- **What It Does**: Evolves Obsidian into the premier professional music hub—combining live karaoke lyrics with multi-language romanization, studio-grade USB DAC direct output verification, a full-featured DJ mixing studio, and instant acoustic song identification.
- **Target Audience**: Audiophiles, DJ enthusiasts, music learners, and lossless collectors who want cutting-edge playback and mixing tools in a single pitch-black app.
- **Key Value**: Delivers professional studio capabilities, engaging visual performance, and instant track recognition without bloated third-party dependencies.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Karaoke Lyrics View**: Open Full Player $\rightarrow$ tap Lyrics $\rightarrow$ enjoy fluid glowing word-by-word highlights synced to music timing, with one-tap toggle for Romanized pronunciation and English translation.
2. **Bit-Perfect DAC Inspector**: Plug in external USB-C DAC dongle $\rightarrow$ top bar displays gold "DIRECT BIT-PERFECT" pill with exact hardware sample rate (e.g. 192.0 kHz / 24-bit) $\rightarrow$ tap to inspect active audio sink properties.
3. **Pro DJ Studio**: Navigate to DJ tab / tools strip $\rightarrow$ load Track A and Track B $\rightarrow$ adjust tempo pitch faders, trigger hot cue points, set 4-beat auto-loops, and blend smoothly with the center crossfader.
4. **Instant Song Recognition**: Tap the audio recognizer icon in Home or Search bar $\rightarrow$ listening animation displays live microphone frequency peaks $\rightarrow$ instant match card appears with artist, title, album, lossless streaming, and one-tap download.

### Visual Identity & Theme Tokens
- **Background**: `#000000` (Pitch Black) with dynamic radial aura glowing from the active album art.
- **DJ Decks**: Deck A styled in `#00E5FF` (Neon Cyan) and Deck B in `#FF5252` (Crimson Fire) for instant spatial clarity.
- **Lyrics Typography**: Heavy weight `FontWeight.ExtraBold` with animated linear gradient shimmer across active lines and subtle frosted glass backing (`#141416` at `alpha = 0.70f`).

---

## 3. Technical Architecture & Component Mapping

```
┌────────────────────────────────────────────────────────────────────────┐
│                        OBSIDIAN ULTIMATE CORE                          │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
       ┌──────────────┬──────────────┴──────────────┬──────────────┐
       ▼              ▼                             ▼              ▼
┌──────────────┐┌──────────────┐             ┌──────────────┐┌──────────────┐
│  Sync Lyrics ││ USB DAC & DoP│             │ Pro DJ Mixer ││ Audio Recon  │
│  & Romanizer ││ Direct Output│             │ Dual Decks   ││ Fingerprint  │
│  Controller  ││ Inspector    │             │ Engine       ││ Microphone   │
└──────┬───────┘└──────┬───────┘             └──────┬───────┘└──────┬───────┘
       │              │                             │              │
       ▼              ▼                             ▼              ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        AUDIO & UI INTEGRATION LAYER                    │
│     (FullPlayerSheet, LibraryScreen, HomeScreen, ObsidianViewModel)    │
└────────────────────────────────────────────────────────────────────────┘
```

### New Components & Engines
- `SyncedLyricsParser`: Parses LRC and enhanced word-timed formats, providing real-time line synchronization and phonetic romanization mapping.
- `UsbDacOutputManager`: Monitors Android `AudioDeviceInfo` for `TYPE_USB_DEVICE` / `TYPE_USB_HEADSET`, calculating bit-perfect hardware output matching.
- `ObsidianDjDeckEngine`: Manages Deck A and Deck B playback states, scratch emulation, tempo shifting ($\pm 8\% / \pm 16\%$), loop quantizing, and crossfade curve blending.
- `AudioFingerprintRecognizer`: Captures microphone audio PCM chunks via `AudioRecord`, extracts peak frequency constellation maps, and queries online music identification endpoints.
- `DjStudioScreen`: Interactive dual deck visual mixing interface with waveform strips and cue buttons.
- `SongRecognitionModal`: Minimalist pulsing radar listening sheet with recognized track result card.

---

## Next Steps

Review the plan and click **Proceed** to begin implementation of the Synchronized Karaoke Lyrics Engine, Bit-Perfect USB DAC Inspector, Pro DJ Dual Deck Mixer, and Audio Fingerprinting Recognizer.
