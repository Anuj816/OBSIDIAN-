package com.anuj.obsidian.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@androidx.compose.runtime.Immutable
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@androidx.compose.runtime.Immutable
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val orderIndex: Int = 0
)
