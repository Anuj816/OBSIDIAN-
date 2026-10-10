package com.anuj.obsidian.data.model

data class FullPlaybackState(
    val track: DownloadedTrack?,
    val positionSec: Float,
    val isPlaying: Boolean,
    val isShuffle: Boolean,
    val repeatMode: Int,
    val speed: Float,
    val autoplayMode: String,
    val queueTrackIds: List<Long>,
    val shuffledTrackIds: List<Long>,
    val shuffledIndex: Int
)
