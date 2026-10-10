package com.anuj.obsidian.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@androidx.compose.runtime.Immutable
@Entity(
    tableName = "cached_lyrics",
    indices = [
        Index(value = ["lookupKey"], unique = true),
        Index(value = ["isrc"]),
        Index(value = ["updatedTimestamp"])
    ]
)
data class CachedLyric(
    @PrimaryKey
    val lookupKey: String, // format: "artist_title_clean" or localFilePath
    val isrc: String = "",
    val lyrics: String,
    val isSynced: Boolean = false,
    val source: String = "online",
    val updatedTimestamp: Long = System.currentTimeMillis()
)
