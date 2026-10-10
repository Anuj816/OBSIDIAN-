package com.anuj.obsidian.data.model

/**
 * Represents the source collection type for playback context.
 */
enum class PlaybackSourceType(val displayName: String) {
    ALL_SONGS("All Songs"),
    ALBUM("Album"),
    ARTIST("Artist"),
    GENRE("Genre"),
    FOLDER("Folder"),
    PLAYLIST("Playlist"),
    SEARCH("Search Results"),
    FAVORITES("Favorites"),
    HI_RES("Hi-Res Collection"),
    EXPLICIT_QUEUE("Queue"),
    CUSTOM("Custom Collection")
}

fun createNewPlaybackContext(
    sourceType: PlaybackSourceType = PlaybackSourceType.ALL_SONGS,
    sourceId: String = "",
    sourceTitle: String = "",
    tracks: List<DownloadedTrack>,
    contextPosition: Int = 0
): PlaybackContext {
    return PlaybackContext(
        sourceType = sourceType,
        sourceId = sourceId,
        sourceTitle = sourceTitle,
        entries = tracks.map { it.toQueueEntry() },
        contextPosition = contextPosition
    )
}

fun PlaybackContext(
    sourceType: PlaybackSourceType = PlaybackSourceType.ALL_SONGS,
    sourceId: String = "",
    sourceTitle: String = "",
    tracks: List<DownloadedTrack>,
    contextPosition: Int = 0
): PlaybackContext = createNewPlaybackContext(sourceType, sourceId, sourceTitle, tracks, contextPosition)

/**
 * Represents the underlying playback context (e.g. an Album, Playlist, Artist, or Folder).
 * Playback naturally progresses through this context without requiring all future tracks
 * to be dumped into the user's manual queue.
 */
data class PlaybackContext(
    val sourceType: PlaybackSourceType = PlaybackSourceType.ALL_SONGS,
    val sourceId: String = "",
    val sourceTitle: String = "",
    val entries: List<QueueEntry> = emptyList(),
    val contextPosition: Int = 0
) {
    val tracks: List<DownloadedTrack>
        get() = entries.map { it.track }

    val currentEntry: QueueEntry?
        get() = entries.getOrNull(contextPosition)

    val currentTrack: DownloadedTrack?
        get() = currentEntry?.track

    val size: Int
        get() = entries.size

    val isEmpty: Boolean
        get() = entries.isEmpty()

    fun getTrackById(id: Long): DownloadedTrack? = entries.find { it.track.id == id }?.track

    fun reconcileExistingPlaybackContext(newTracks: List<DownloadedTrack>): PlaybackContext {
        val oldEntriesPool = entries.toMutableList()
        val reconciledEntries = mutableListOf<QueueEntry>()

        for (i in newTracks.indices) {
            val newTrack = newTracks[i]
            val posMatch = if (i in oldEntriesPool.indices && (oldEntriesPool[i].track.id == newTrack.id || (oldEntriesPool[i].track.localFilePath.isNotBlank() && oldEntriesPool[i].track.localFilePath == newTrack.localFilePath))) {
                oldEntriesPool[i]
            } else null

            if (posMatch != null) {
                reconciledEntries.add(posMatch.copy(track = newTrack))
            } else {
                val matchIdx = oldEntriesPool.indexOfFirst { it.track.id == newTrack.id || (it.track.localFilePath.isNotBlank() && it.track.localFilePath == newTrack.localFilePath) }
                if (matchIdx >= 0) {
                    val matched = oldEntriesPool.removeAt(matchIdx)
                    reconciledEntries.add(matched.copy(track = newTrack))
                } else {
                    reconciledEntries.add(newTrack.toQueueEntry())
                }
            }
        }
        val currentEntryId = currentEntry?.entryId
        val newPos = if (currentEntryId != null) {
            val idx = reconciledEntries.indexOfFirst { it.entryId == currentEntryId }
            if (idx >= 0) idx else contextPosition.coerceIn(0, (reconciledEntries.size - 1).coerceAtLeast(0))
        } else {
            contextPosition.coerceIn(0, (reconciledEntries.size - 1).coerceAtLeast(0))
        }
        return copy(entries = reconciledEntries, contextPosition = newPos)
    }

    fun getEntryByEntryId(entryId: String): QueueEntry? = entries.find { it.entryId == entryId }

    fun indexOfEntryId(entryId: String): Int = entries.indexOfFirst { it.entryId == entryId }

    fun indexOfTrack(id: Long): Int = if (id > 0L) entries.indexOfFirst { it.track.id == id } else -1

    fun indexOfTrack(track: DownloadedTrack): Int = entries.indexOfFirst {
        (track.id > 0L && it.track.id == track.id) ||
        (track.localFilePath.isNotBlank() && it.track.localFilePath == track.localFilePath) ||
        (it.track.title.equals(track.title, ignoreCase = true) && it.track.artist.equals(track.artist, ignoreCase = true))
    }

    fun withPosition(newPosition: Int): PlaybackContext {
        if (entries.isEmpty()) return copy(contextPosition = 0)
        val safePos = newPosition.coerceIn(0, entries.size - 1)
        return copy(contextPosition = safePos)
    }

    fun withPositionForEntry(entryId: String): PlaybackContext {
        val idx = indexOfEntryId(entryId)
        return if (idx >= 0) copy(contextPosition = idx) else this
    }

    fun withPositionForTrack(trackId: Long): PlaybackContext {
        val idx = indexOfTrack(trackId)
        return if (idx >= 0) copy(contextPosition = idx) else this
    }

    fun getNextTrack(loop: Boolean = false): DownloadedTrack? {
        if (entries.isEmpty()) return null
        val nextIdx = contextPosition + 1
        return if (nextIdx in entries.indices) {
            entries[nextIdx].track
        } else if (loop) {
            entries.firstOrNull()?.track
        } else {
            null
        }
    }

    fun getPreviousTrack(loop: Boolean = false): DownloadedTrack? {
        if (entries.isEmpty()) return null
        val prevIdx = contextPosition - 1
        return if (prevIdx in entries.indices) {
            entries[prevIdx].track
        } else if (loop) {
            entries.lastOrNull()?.track
        } else {
            null
        }
    }
}

/**
 * Internal shuffle traversal state (hidden shuffle queue).
 * Operates as a persistent, deterministic traversal path over the current session.
 */
data class ShuffleTraversal(
    val isEnabled: Boolean = false,
    val mode: AdvancedShuffleMode = AdvancedShuffleMode.SHUFFLE_OFF,
    val traversalOrder: List<Long> = emptyList(),
    val shufflePosition: Int = -1
) {
    val currentTrackId: Long?
        get() = if (shufflePosition in traversalOrder.indices) traversalOrder[shufflePosition] else null

    val size: Int
        get() = traversalOrder.size

    val isEmpty: Boolean
        get() = traversalOrder.isEmpty()

    fun indexOfTrackId(trackId: Long): Int = traversalOrder.indexOf(trackId)

    fun withPosition(newPosition: Int): ShuffleTraversal {
        if (traversalOrder.isEmpty()) return copy(shufflePosition = -1)
        val safePos = newPosition.coerceIn(0, traversalOrder.size - 1)
        return copy(shufflePosition = safePos)
    }

    fun getNextTrackId(loop: Boolean = false): Long? {
        if (traversalOrder.isEmpty()) return null
        val nextIdx = shufflePosition + 1
        return if (nextIdx in traversalOrder.indices) {
            traversalOrder[nextIdx]
        } else if (loop) {
            traversalOrder.firstOrNull()
        } else {
            null
        }
    }

    fun getPreviousTrackId(loop: Boolean = false): Long? {
        if (traversalOrder.isEmpty()) return null
        val prevIdx = shufflePosition - 1
        return if (prevIdx in traversalOrder.indices) {
            traversalOrder[prevIdx]
        } else if (loop) {
            traversalOrder.lastOrNull()
        } else {
            null
        }
    }
}

/**
 * Single authoritative PlaybackState containing the complete status of the playback engine.
 */
data class AuthoritativePlaybackState(
    val currentTrack: DownloadedTrack? = null,
    val currentQueueEntry: QueueEntry? = null,
    val currentSongId: Long? = currentTrack?.id,
    val playbackContext: PlaybackContext = PlaybackContext(),
    val contextPosition: Int = playbackContext.contextPosition,
    val shuffleEnabled: Boolean = false,
    val shuffleTraversal: ShuffleTraversal = ShuffleTraversal(),
    val shufflePosition: Int = shuffleTraversal.shufflePosition,
    val explicitQueue: List<QueueEntry> = emptyList(),
    val isPlayingFromExplicitQueue: Boolean = false,
    val repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val loadingType: PlaybackLoadingType = PlaybackLoadingType.NONE,
    val previousTrack: DownloadedTrack? = null,
    val nextTrack: DownloadedTrack? = null
)
