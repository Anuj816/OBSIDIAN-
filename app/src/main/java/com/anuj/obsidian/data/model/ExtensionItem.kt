package com.anuj.obsidian.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "extensions")
data class ExtensionItem(
    @PrimaryKey
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val repoUrl: String,
    val isEnabled: Boolean = true,
    val isInstalled: Boolean = true,
    val capabilities: String = "24-bit Hi-Res FLAC, Vorbis Lyrics",
    val pingMs: Int = 42,
    val sourceProvider: String = "Tidal",
    val isOfficial: Boolean = true,
    val downloadUrl: String = ""
)
