package com.anuj.obsidian.data.model

/**
 * Advanced-Style Advanced Shuffle Modes
 */
enum class AdvancedShuffleMode(
    val title: String,
    val shortName: String,
    val description: String
) {
    SHUFFLE_OFF(
        title = "Shuffle Off",
        shortName = "Off",
        description = "Plays tracks sequentially in normal order"
    ),
    SHUFFLE_ALL(
        title = "Shuffle All Songs",
        shortName = "All Songs",
        description = "Plays all songs across current list/library in smart randomized order"
    ),
    SHUFFLE_TRACKS(
        title = "Shuffle Tracks in Category",
        shortName = "Tracks",
        description = "Shuffles songs within current album/folder, then moves to next category"
    ),
    SHUFFLE_CATEGORIES(
        title = "Shuffle Categories",
        shortName = "Categories",
        description = "Plays albums/folders in random order, with songs in 1..N order"
    ),
    SHUFFLE_SONGS_AND_CATEGORIES(
        title = "Shuffle Songs & Categories",
        shortName = "All & Cats",
        description = "Random category order and random songs inside each category"
    );

    fun next(): AdvancedShuffleMode {
        val values = entries
        val nextIdx = (ordinal + 1) % values.size
        return values[nextIdx]
    }
}

/**
 * Obsidian Advanced Queue Return Behavior when user queue finishes
 */
enum class QueueReturnMode(
    val title: String,
    val description: String,
    val iconName: String
) {
    RETURN_TO_PREVIOUS(
        title = "Return to Previous List",
        description = "Resumes previous album, folder, or playlist playback position",
        iconName = "undo"
    ),
    CONTINUE_CATEGORY(
        title = "Continue Current Category",
        description = "Continues playing from the album/folder of the last queue song",
        iconName = "play_arrow"
    ),
    STOP_PLAYBACK(
        title = "Stop After Queue Ends",
        description = "Stops playback automatically when all queued songs finish",
        iconName = "stop"
    )
}
