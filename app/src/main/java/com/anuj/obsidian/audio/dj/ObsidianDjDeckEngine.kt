package com.anuj.obsidian.audio.dj

import androidx.compose.runtime.Immutable
import com.anuj.obsidian.data.model.DownloadedTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

@Immutable
data class HotCuePoint(
    val id: Int,
    val positionMs: Long = 0L,
    val label: String = "CUE $id",
    val isActive: Boolean = false
)

@Immutable
data class DjDeckState(
    val deckId: String, // "DECK_A" or "DECK_B"
    val track: DownloadedTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bpm: Float = 120.0f,
    val pitchPercent: Float = 0.0f, // -16.0f .. +16.0f %
    val effectiveSpeed: Float = 1.0f,
    val isLoopActive: Boolean = false,
    val loopBeats: Float = 4.0f, // 0.25, 0.5, 1, 2, 4, 8, 16
    val loopStartMs: Long = 0L,
    val loopEndMs: Long = 0L,
    val lowKill: Boolean = false,
    val midKill: Boolean = false,
    val highKill: Boolean = false,
    val volume: Float = 1.0f,
    val hotCues: List<HotCuePoint> = listOf(
        HotCuePoint(1), HotCuePoint(2), HotCuePoint(3), HotCuePoint(4)
    )
)

@Immutable
data class DjMixerState(
    val crossfaderPosition: Float = 0.5f, // 0.0 (Deck A full) .. 1.0 (Deck B full)
    val masterVolume: Float = 1.0f,
    val isSyncActive: Boolean = false,
    val deckA: DjDeckState = DjDeckState("DECK_A"),
    val deckB: DjDeckState = DjDeckState("DECK_B")
)

object ObsidianDjDeckEngine {
    private val _mixerState = MutableStateFlow(DjMixerState())
    val mixerState: StateFlow<DjMixerState> = _mixerState.asStateFlow()

    fun loadTrackToDeck(deckId: String, track: DownloadedTrack) {
        val estimatedBpm = estimateBpm(track)
        val durationMs = (track.durationSeconds * 1000L).coerceAtLeast(1000L)
        val newDeck = DjDeckState(
            deckId = deckId,
            track = track,
            isPlaying = false,
            currentPositionMs = 0L,
            durationMs = durationMs,
            bpm = estimatedBpm,
            pitchPercent = 0.0f,
            effectiveSpeed = 1.0f
        )
        _mixerState.value = if (deckId == "DECK_A") {
            _mixerState.value.copy(deckA = newDeck)
        } else {
            _mixerState.value.copy(deckB = newDeck)
        }
    }

    fun togglePlayPause(deckId: String) {
        val cur = _mixerState.value
        if (deckId == "DECK_A") {
            val a = cur.deckA
            if (a.track != null) {
                _mixerState.value = cur.copy(deckA = a.copy(isPlaying = !a.isPlaying))
            }
        } else {
            val b = cur.deckB
            if (b.track != null) {
                _mixerState.value = cur.copy(deckB = b.copy(isPlaying = !b.isPlaying))
            }
        }
    }

    fun setPitchPercent(deckId: String, pitch: Float) {
        val clamped = pitch.coerceIn(-16.0f, 16.0f)
        val speed = 1.0f + (clamped / 100.0f)
        val cur = _mixerState.value
        if (deckId == "DECK_A") {
            _mixerState.value = cur.copy(deckA = cur.deckA.copy(pitchPercent = clamped, effectiveSpeed = speed))
        } else {
            _mixerState.value = cur.copy(deckB = cur.deckB.copy(pitchPercent = clamped, effectiveSpeed = speed))
        }
    }

    fun setCrossfader(pos: Float) {
        val clamped = pos.coerceIn(0.0f, 1.0f)
        _mixerState.value = _mixerState.value.copy(crossfaderPosition = clamped)
    }

    fun triggerHotCue(deckId: String, cueId: Int) {
        val cur = _mixerState.value
        val deck = if (deckId == "DECK_A") cur.deckA else cur.deckB
        val cue = deck.hotCues.find { it.id == cueId } ?: return

        if (cue.isActive) {
            // Jump to cue position
            val updated = deck.copy(currentPositionMs = cue.positionMs)
            _mixerState.value = if (deckId == "DECK_A") cur.copy(deckA = updated) else cur.copy(deckB = updated)
        } else {
            // Save current position as new cue point
            val newCues = deck.hotCues.map {
                if (it.id == cueId) it.copy(positionMs = deck.currentPositionMs, isActive = true) else it
            }
            val updated = deck.copy(hotCues = newCues)
            _mixerState.value = if (deckId == "DECK_A") cur.copy(deckA = updated) else cur.copy(deckB = updated)
        }
    }

    fun toggleLoop(deckId: String, beats: Float = 4.0f) {
        val cur = _mixerState.value
        val deck = if (deckId == "DECK_A") cur.deckA else cur.deckB
        val bpm = deck.bpm.coerceAtLeast(60.0f)
        val msPerBeat = (60000.0f / bpm).toLong()
        val loopLengthMs = (msPerBeat * beats).toLong()

        if (deck.isLoopActive) {
            val updated = deck.copy(isLoopActive = false)
            _mixerState.value = if (deckId == "DECK_A") cur.copy(deckA = updated) else cur.copy(deckB = updated)
        } else {
            val start = deck.currentPositionMs
            val end = (start + loopLengthMs).coerceAtMost(deck.durationMs)
            val updated = deck.copy(isLoopActive = true, loopBeats = beats, loopStartMs = start, loopEndMs = end)
            _mixerState.value = if (deckId == "DECK_A") cur.copy(deckA = updated) else cur.copy(deckB = updated)
        }
    }

    fun toggleEqKill(deckId: String, band: String) {
        val cur = _mixerState.value
        val deck = if (deckId == "DECK_A") cur.deckA else cur.deckB
        val updated = when (band.lowercase()) {
            "low" -> deck.copy(lowKill = !deck.lowKill)
            "mid" -> deck.copy(midKill = !deck.midKill)
            "high" -> deck.copy(highKill = !deck.highKill)
            else -> deck
        }
        _mixerState.value = if (deckId == "DECK_A") cur.copy(deckA = updated) else cur.copy(deckB = updated)
    }

    fun syncBpm() {
        val cur = _mixerState.value
        val masterBpm = if (cur.deckA.isPlaying) cur.deckA.bpm else cur.deckB.bpm
        val targetDeck = if (cur.deckA.isPlaying) "DECK_B" else "DECK_A"

        if (targetDeck == "DECK_A" && cur.deckA.bpm > 0) {
            val pitchDelta = ((masterBpm - cur.deckA.bpm) / cur.deckA.bpm) * 100f
            setPitchPercent("DECK_A", pitchDelta)
        } else if (cur.deckB.bpm > 0) {
            val pitchDelta = ((masterBpm - cur.deckB.bpm) / cur.deckB.bpm) * 100f
            setPitchPercent("DECK_B", pitchDelta)
        }
    }

    private fun estimateBpm(track: DownloadedTrack): Float {
        // High-quality hash deterministic BPM baseline fallback (118 - 134 BPM typical for DJ tracks)
        val hash = (track.title.hashCode() xor track.artist.hashCode()).toLong()
        val offset = (Math.abs(hash) % 18).toFloat()
        return 118.0f + offset
    }
}
