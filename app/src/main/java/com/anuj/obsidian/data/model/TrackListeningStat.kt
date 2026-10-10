package com.anuj.obsidian.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_listening_stats",
    indices = [
        Index(value = ["lastPlayedTimestamp"]),
        Index(value = ["playCount"])
    ]
)
data class TrackListeningStat(
    @PrimaryKey
    val trackId: Long,
    val playCount: Int = 1,
    val lastPlayedTimestamp: Long = System.currentTimeMillis()
)
