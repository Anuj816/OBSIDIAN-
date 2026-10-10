package com.anuj.obsidian.data.model

enum class PlaybackLoadingType(
    val title: String,
    val description: String
) {
    NONE(
        title = "Ready",
        description = "Playback operational"
    ),
    LOCAL_STORAGE_IO(
        title = "Local Storage I/O",
        description = "Reading audio data from local storage disk"
    ),
    NETWORK_BUFFERING(
        title = "Network Buffering",
        description = "Buffering audio stream from remote network"
    );

    val isLoading: Boolean
        get() = this != NONE

    val isLocalStorage: Boolean
        get() = this == LOCAL_STORAGE_IO

    val isNetwork: Boolean
        get() = this == NETWORK_BUFFERING
}
