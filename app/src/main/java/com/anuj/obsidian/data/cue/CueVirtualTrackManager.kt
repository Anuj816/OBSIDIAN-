package com.anuj.obsidian.data.cue

import android.content.Context
import android.net.Uri
import android.util.Log
import com.anuj.obsidian.data.model.DownloadedTrack
import java.io.File
import java.io.FileInputStream

object CueVirtualTrackManager {
    private const val TAG = "CueVirtualTrackManager"
    private val cueCache = mutableMapOf<String, CueSheet>()

    /**
     * Checks if a .cue file exists alongside the given audio file or directory.
     */
    fun findCueSheetForAudioFile(audioFile: File): File? {
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension
        val sameNameCue = File(parent, "$baseName.cue")
        if (sameNameCue.exists() && sameNameCue.canRead()) {
            return sameNameCue
        }
        val cueFiles = parent.listFiles { f -> f.extension.equals("cue", ignoreCase = true) }
        return cueFiles?.firstOrNull()
    }

    /**
     * Parses a .cue file and generates virtual DownloadedTrack items for each track in the CUE sheet.
     */
    fun generateVirtualTracks(
        cueFile: File,
        baseAudioTrack: DownloadedTrack
    ): List<DownloadedTrack> {
        return try {
            val cueSheet = cueCache.getOrPut(cueFile.absolutePath) {
                FileInputStream(cueFile).use { ins ->
                    CueSheetParser.parseCueContent(ins, cueFile.absolutePath)
                }
            }

            val totalDurationMs = baseAudioTrack.durationSeconds * 1000L

            cueSheet.tracks.mapIndexed { idx, ct ->
                val trackDurationMs = if (ct.durationMs > 0) {
                    ct.durationMs
                } else if (totalDurationMs > ct.startOffsetMs) {
                    totalDurationMs - ct.startOffsetMs
                } else {
                    180000L
                }

                val virtualId = baseAudioTrack.id * 1000L + ct.trackNumber

                baseAudioTrack.copy(
                    id = virtualId,
                    title = ct.title,
                    artist = ct.performer.ifBlank { baseAudioTrack.artist },
                    album = cueSheet.albumTitle.ifBlank { baseAudioTrack.album },
                    durationSeconds = (trackDurationMs / 1000L).toInt(),
                    isVirtualCueTrack = true,
                    cueStartOffsetMs = ct.startOffsetMs,
                    cueDurationMs = trackDurationMs,
                    cueSheetPath = cueFile.absolutePath
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating virtual tracks from ${cueFile.name}: ${e.message}")
            emptyList()
        }
    }
}
