package com.anuj.obsidian.data.cue

import android.content.Context
import android.net.Uri
import android.util.Log
import com.anuj.obsidian.data.model.DownloadedTrack
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Data structures representing a parsed CUE sheet.
 */
data class CueTrack(
    val trackNumber: Int,
    val title: String,
    val performer: String,
    val songwriter: String? = null,
    val startOffsetMs: Long,
    val durationMs: Long = 0L,
    val audioFileName: String = ""
)

data class CueSheet(
    val albumTitle: String,
    val albumPerformer: String,
    val audioFileName: String,
    val tracks: List<CueTrack> = emptyList(),
    val cueFilePath: String = ""
)

object CueSheetParser {
    private const val TAG = "CueSheetParser"

    /**
     * Parses standard CUE index timestamp mm:ss:ff into milliseconds.
     * CUE frames (ff) are 1/75th of a second (75 frames/sec).
     */
    fun parseIndexTimestampToMs(timestamp: String): Long {
        val parts = timestamp.trim().split(":")
        if (parts.size != 3) return 0L
        val min = parts[0].toLongOrNull() ?: 0L
        val sec = parts[1].toLongOrNull() ?: 0L
        val frames = parts[2].toLongOrNull() ?: 0L
        return (min * 60 * 1000L) + (sec * 1000L) + ((frames * 1000L) / 75L)
    }

    /**
     * Parses CUE sheet content from an InputStream with UTF-8 / ISO-8859-1 fallback.
     */
    fun parseCueContent(inputStream: InputStream, cueFilePath: String = ""): CueSheet {
        var albumTitle = "Unknown Album"
        var albumPerformer = "Unknown Artist"
        var currentAudioFile = ""
        
        val rawTracks = mutableListOf<CueTrackBuilder>()
        var currentTrackBuilder: CueTrackBuilder? = null

        val lines = inputStream.bufferedReader(Charset.forName("UTF-8")).readLines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("REM")) continue

            val tokens = line.split("\\s+".toRegex(), 2)
            val command = tokens[0].uppercase()
            val args = if (tokens.size > 1) tokens[1] else ""

            when (command) {
                "PERFORMER" -> {
                    val perf = stripQuotes(args)
                    if (currentTrackBuilder == null) {
                        albumPerformer = perf
                    } else {
                        currentTrackBuilder.performer = perf
                    }
                }
                "TITLE" -> {
                    val tit = stripQuotes(args)
                    if (currentTrackBuilder == null) {
                        albumTitle = tit
                    } else {
                        currentTrackBuilder.title = tit
                    }
                }
                "SONGWRITER" -> {
                    val sw = stripQuotes(args)
                    currentTrackBuilder?.songwriter = sw
                }
                "FILE" -> {
                    // Extract filename from quotes e.g. "album.flac" WAVE
                    val fName = extractQuotedOrFirst(args)
                    currentAudioFile = fName
                }
                "TRACK" -> {
                    // Save previous track
                    currentTrackBuilder?.let { rawTracks.add(it) }
                    val trackNum = args.split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: (rawTracks.size + 1)
                    currentTrackBuilder = CueTrackBuilder(
                        trackNumber = trackNum,
                        performer = albumPerformer,
                        audioFileName = currentAudioFile
                    )
                }
                "INDEX" -> {
                    val indexTokens = args.split("\\s+".toRegex())
                    if (indexTokens.size >= 2) {
                        val indexId = indexTokens[0]
                        val timestamp = indexTokens[1]
                        if (indexId == "01") {
                            currentTrackBuilder?.startOffsetMs = parseIndexTimestampToMs(timestamp)
                        } else if (indexId == "00" && currentTrackBuilder?.startOffsetMs == 0L) {
                            // Pregap index 00
                            currentTrackBuilder?.pregapOffsetMs = parseIndexTimestampToMs(timestamp)
                        }
                    }
                }
            }
        }
        currentTrackBuilder?.let { rawTracks.add(it) }

        // Compute track durations based on subsequent start offsets
        val tracks = mutableListOf<CueTrack>()
        for (i in 0 until rawTracks.size) {
            val curr = rawTracks[i]
            val nextOffset = if (i + 1 < rawTracks.size) rawTracks[i + 1].startOffsetMs else null
            val duration = if (nextOffset != null && nextOffset > curr.startOffsetMs) {
                nextOffset - curr.startOffsetMs
            } else {
                0L // Last track; will be resolved using the container's duration
            }

            tracks.add(
                CueTrack(
                    trackNumber = curr.trackNumber,
                    title = curr.title.ifBlank { "Track ${curr.trackNumber}" },
                    performer = curr.performer.ifBlank { albumPerformer },
                    songwriter = curr.songwriter,
                    startOffsetMs = curr.startOffsetMs,
                    durationMs = duration,
                    audioFileName = curr.audioFileName.ifBlank { currentAudioFile }
                )
            )
        }

        return CueSheet(
            albumTitle = albumTitle,
            albumPerformer = albumPerformer,
            audioFileName = currentAudioFile,
            tracks = tracks,
            cueFilePath = cueFilePath
        )
    }

    private fun stripQuotes(s: String): String {
        var str = s.trim()
        if (str.startsWith("\"") && str.endsWith("\"") && str.length >= 2) {
            str = str.substring(1, str.length - 1)
        }
        return str
    }

    private fun extractQuotedOrFirst(s: String): String {
        val firstQuote = s.indexOf('"')
        val lastQuote = s.lastIndexOf('"')
        return if (firstQuote != -1 && lastQuote > firstQuote) {
            s.substring(firstQuote + 1, lastQuote)
        } else {
            s.split("\\s+".toRegex()).firstOrNull() ?: s
        }
    }

    private class CueTrackBuilder(
        var trackNumber: Int = 1,
        var title: String = "",
        var performer: String = "",
        var songwriter: String? = null,
        var startOffsetMs: Long = 0L,
        var pregapOffsetMs: Long = 0L,
        var audioFileName: String = ""
    )
}
