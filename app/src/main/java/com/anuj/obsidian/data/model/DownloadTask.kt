package com.anuj.obsidian.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    FETCHING_METADATA,
    CONNECTING_PROVIDER,
    DOWNLOADING_STREAM,
    EMBEDDING_TAGS,
    COMPLETED,
    COMPLETED_WITHOUT_TAGS,
    FAILED,
    CANCELLED
}

@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey(autoGenerate = true)
    val taskId: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String = "",
    val quality: String = "24-bit / 96kHz FLAC",
    val progress: Float = 0f,
    val speedKbps: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val provider: String = "Qobuz Master",
    val startedAt: Long = System.currentTimeMillis(),
    val lyricsAvailable: Boolean = true,
    val isrc: String = ""
)
