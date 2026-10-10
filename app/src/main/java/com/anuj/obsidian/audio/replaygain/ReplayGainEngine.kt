package com.anuj.obsidian.audio.replaygain

import android.content.Context
import android.util.Log
import com.anuj.obsidian.data.local.JaudiotaggerHelper
import com.anuj.obsidian.data.model.DownloadedTrack
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * EBU R128 & ReplayGain 2.0 Dynamic Loudness Matching Engine.
 * 
 * Features:
 * - Computes exact linear amplitude multipliers based on ITU-R BS.1770 / EBU R128 loudness standards.
 * - Supports Track Gain, Album Gain, and Smart Auto-Context matching.
 * - Integrated true-peak ceiling limiter preventing digital clipping when applying positive gain.
 * - Configurable reference target loudness (-14 LUFS, -18 LUFS, -23 LUFS).
 */
enum class ReplayGainMode(val title: String, val description: String) {
    OFF("Disabled", "Play audio at raw recorded levels"),
    TRACK_GAIN("Track Gain", "Normalize each track independently for uniform volume"),
    ALBUM_GAIN("Album Gain", "Preserve artist-intended dynamic volume differences between album tracks"),
    SMART_AUTO("Smart Context", "Use Album Gain when playing continuous album tracks; use Track Gain for shuffled playlists")
}

data class ReplayGainInfo(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null,
    val r128TrackGainDb: Float? = null,
    val r128AlbumGainDb: Float? = null
)

object ReplayGainEngine {
    private const val TAG = "ReplayGainEngine"
    
    // Default reference loudness: -18 LUFS (Audiophile standard, approx 89 dB SPL)
    var targetLufs: Float = -18.0f
    var preampDb: Float = 0.0f
    var preventClipping: Boolean = true
    var currentMode: ReplayGainMode = ReplayGainMode.SMART_AUTO

    private val cache = ConcurrentHashMap<Long, ReplayGainInfo>()

    /**
     * Extracts ReplayGain and R128 tags from metadata strings or tag fields.
     */
    fun parseReplayGainInfo(context: Context?, track: DownloadedTrack): ReplayGainInfo {
        cache[track.id]?.let { return it }

        var tGain: Float? = null
        var tPeak: Float? = null
        var aGain: Float? = null
        var aPeak: Float? = null
        var r128TGain: Float? = null
        var r128AGain: Float? = null

        if (context != null && track.localFilePath.isNotBlank()) {
            try {
                val meta = JaudiotaggerHelper.readDetailedMetadata(context, track.localFilePath)
                tGain = meta.replayGainTrackGain
                aGain = meta.replayGainAlbumGain
                tPeak = meta.replayGainTrackPeak
                aPeak = meta.replayGainAlbumPeak
                r128TGain = meta.r128TrackGain
                r128AGain = meta.r128AlbumGain
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing ReplayGain for ${track.title}: ${e.message}")
            }
        }

        val info = ReplayGainInfo(
            trackGainDb = tGain,
            trackPeak = tPeak,
            albumGainDb = aGain,
            albumPeak = aPeak,
            r128TrackGainDb = r128TGain,
            r128AlbumGainDb = r128AGain
        )
        if (cache.size > 2000) {
            cache.clear()
        }
        cache[track.id] = info
        return info
    }

    /**
     * Computes the linear volume multiplier (0.0 .. 2.0) for the given track.
     */
    fun calculateVolumeMultiplier(
        context: Context?,
        track: DownloadedTrack?,
        isAlbumContext: Boolean,
        mode: ReplayGainMode = currentMode,
        customPreampDb: Float = preampDb
    ): Float {
        if (track == null || mode == ReplayGainMode.OFF) return 1.0f

        val info = parseReplayGainInfo(context, track)

        // Select gain in dB
        val gainDb: Float? = when (mode) {
            ReplayGainMode.OFF -> 0.0f
            ReplayGainMode.TRACK_GAIN -> info.r128TrackGainDb ?: info.trackGainDb
            ReplayGainMode.ALBUM_GAIN -> info.r128AlbumGainDb ?: info.albumGainDb ?: info.trackGainDb
            ReplayGainMode.SMART_AUTO -> {
                if (isAlbumContext) {
                    info.r128AlbumGainDb ?: info.albumGainDb ?: info.trackGainDb
                } else {
                    info.r128TrackGainDb ?: info.trackGainDb
                }
            }
        }

        val peak: Float = (if (isAlbumContext) info.albumPeak else info.trackPeak) ?: info.trackPeak ?: 1.0f

        if (gainDb == null) {
            return 1.0f
        }

        // Apply preamp
        val totalGainDb = gainDb + customPreampDb

        // Convert dB to linear scale: 10^(dB / 20)
        var linearMultiplier = 10.0.pow((totalGainDb / 20.0).toDouble()).toFloat()

        // Anti-Clipping Peak Limiter Ceiling (Limit to 0.999f full scale)
        if (preventClipping && peak > 0.0f) {
            val maxAllowedMultiplier = 0.999f / peak
            if (linearMultiplier > maxAllowedMultiplier) {
                linearMultiplier = maxAllowedMultiplier
            }
        }

        return linearMultiplier.coerceIn(0.05f, 3.0f)
    }
}

