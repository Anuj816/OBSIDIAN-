package com.anuj.obsidian.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@androidx.compose.runtime.Immutable
@Entity(
    tableName = "downloaded_tracks",
    indices = [
        androidx.room.Index(value = ["localFilePath"], unique = false),
        androidx.room.Index(value = ["title", "artist"]),
        androidx.room.Index(value = ["album"]),
        androidx.room.Index(value = ["isFavorite"]),
        androidx.room.Index(value = ["dateAddedTimestamp"])
    ]
)
data class DownloadedTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val format: String = "FLAC",
    val bitDepth: String = "16-bit",
    val sampleRate: String = "44.1 kHz",
    val bitrateKbps: Int = 0,
    val albumArtUrl: String = "",
    val localFilePath: String = "",
    val fileSizeMb: Float = 0f,
    val lyrics: String = "",
    val isrc: String = "",
    val releaseDate: String = "",
    val genre: String = "Audio",
    val trackNumber: Int = 1,
    val discNumber: Int = 1,
    val providerSource: String = "Local Library",
    val isFavorite: Boolean = false,
    val dateAddedTimestamp: Long = System.currentTimeMillis(),
    val tagsLoaded: Boolean = true,
    @androidx.room.Ignore
    val isVirtualCueTrack: Boolean = false,
    @androidx.room.Ignore
    val cueStartOffsetMs: Long = 0L,
    @androidx.room.Ignore
    val cueDurationMs: Long = 0L,
    @androidx.room.Ignore
    val cueSheetPath: String = ""
) {
    // Secondary constructor for Room to instantiate without Ignored fields
    constructor(
        id: Long,
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
        format: String,
        bitDepth: String,
        sampleRate: String,
        bitrateKbps: Int,
        albumArtUrl: String,
        localFilePath: String,
        fileSizeMb: Float,
        lyrics: String,
        isrc: String,
        releaseDate: String,
        genre: String,
        trackNumber: Int,
        discNumber: Int,
        providerSource: String,
        isFavorite: Boolean,
        dateAddedTimestamp: Long,
        tagsLoaded: Boolean
    ) : this(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        format = format,
        bitDepth = bitDepth,
        sampleRate = sampleRate,
        bitrateKbps = bitrateKbps,
        albumArtUrl = albumArtUrl,
        localFilePath = localFilePath,
        fileSizeMb = fileSizeMb,
        lyrics = lyrics,
        isrc = isrc,
        releaseDate = releaseDate,
        genre = genre,
        trackNumber = trackNumber,
        discNumber = discNumber,
        providerSource = providerSource,
        isFavorite = isFavorite,
        dateAddedTimestamp = dateAddedTimestamp,
        tagsLoaded = tagsLoaded,
        isVirtualCueTrack = false,
        cueStartOffsetMs = 0L,
        cueDurationMs = 0L,
        cueSheetPath = ""
    )

    companion object {
        fun generatePseudoId(title: String, artist: String, path: String = ""): Long {
            val raw = "${title.trim().lowercase()}|${artist.trim().lowercase()}|${path.trim()}"
            val hash = kotlin.math.abs(raw.hashCode().toLong())
            return if (hash <= 0L) 1L else hash
        }
    }
}
