package com.anuj.obsidian.ui.player

import android.util.Log
import androidx.media3.common.Player
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.PlaybackContext
import com.anuj.obsidian.data.model.PlaybackSourceType
import com.anuj.obsidian.data.model.QueueEntry
import com.anuj.obsidian.data.model.QueueReturnMode
import com.anuj.obsidian.data.model.ShuffleTraversal
import com.anuj.obsidian.data.model.toQueueEntry
import java.util.Collections

data class PlaybackHistory(
    val trackId: Long,
    val timestamp: Long,
    val position: Float,
    val context: PlaybackSourceType
)

data class QueueState(
    val explicitQueue: List<QueueEntry>,
    val playbackContext: PlaybackContext,
    val shuffleTraversal: ShuffleTraversal,
    val playbackHistory: List<PlaybackHistory>,
    val recentlyPlayed: List<Long>
)

enum class ShuffleMode {
    OFF,                          // No shuffle
    ALL,                          // Shuffle all tracks
    RECENT_BIAS,                  // Prefer recently played
    ENERGY_FLOW,                  // Smooth transitions
    GENRE_TRANSITIONS,            // Stay within genre clusters
    DISCOVERY,                    // Minimize repeats, surface unknown tracks
    SMART_MIX                     // Combine all above
}

/**
 * Pure Poweramp-Style Playback, Queue, and Shuffle Architecture:
 * 
 * 1. Playback Context:
 *    - Direct 1:1 reflection of the current Library / Folder / Album / Category list.
 *    - Uses QueueEntry occurrence identity (QueueEntry.entryId).
 * 
 * 2. Explicit Queue Priority:
 *    - Tracks queued via "Play Next" / "Add to Queue" are stored as QueueEntries and played FIFO.
 * 
 * 3. Indefinite Repeat & Shuffle Traversal:
 *    - Persistent, deterministic shuffle traversal mapped by QueueEntry.entryId.
 */
class ShuffleManager {

    private val explicitQueue = Collections.synchronizedList(mutableListOf<QueueEntry>())

    @Volatile
    private var playbackContext = PlaybackContext()

    @Volatile
    private var shuffleTraversal = ShuffleTraversal()

    private val shuffledEntries = Collections.synchronizedList(mutableListOf<QueueEntry>())

    private var isPlayingFromExplicitQueue = false
    private var savedContextPositionBeforeQueue: Int? = null
    private var savedShufflePositionBeforeQueue: Int? = null
    private var lastPlayedQueueEntry: QueueEntry? = null

    private var queueReturnMode = QueueReturnMode.RETURN_TO_PREVIOUS

    private val playHistory = java.util.ArrayDeque<Long>()
    private val maxHistorySize = 50

    private val fullPlaybackHistory = Collections.synchronizedList(mutableListOf<PlaybackHistory>())
    private val recentlyPlayedWindow = Collections.synchronizedSet(LinkedHashSet<Long>())
    private val maxHistoryWindow = 50

    private val libraryTracks = Collections.synchronizedList(mutableListOf<DownloadedTrack>())

    companion object {
        private const val TAG = "ShuffleManager"
    }

    @Synchronized
    fun setLibraryTracks(tracks: List<DownloadedTrack>) {
        synchronized(libraryTracks) {
            libraryTracks.clear()
            libraryTracks.addAll(tracks)
        }
    }

    fun getLibraryTracks(): List<DownloadedTrack> = synchronized(libraryTracks) { ArrayList(libraryTracks) }

    @Synchronized
    fun getFullPlaybackHistory(): List<PlaybackHistory> = synchronized(fullPlaybackHistory) { ArrayList(fullPlaybackHistory) }

    @Synchronized
    fun getRecentlyPlayedTracks(): List<Long> = synchronized(recentlyPlayedWindow) { ArrayList(recentlyPlayedWindow) }

    @Synchronized
    fun recordTrackPlayed(track: DownloadedTrack, positionFraction: Float = 1.0f) {
        synchronized(playHistory) {
            if (playHistory.lastOrNull() != track.id) {
                playHistory.remove(track.id)
                playHistory.addLast(track.id)
                if (playHistory.size > maxHistorySize) {
                    playHistory.removeFirst()
                }
            }
        }

        val entry = PlaybackHistory(
            trackId = track.id,
            timestamp = System.currentTimeMillis(),
            position = positionFraction,
            context = playbackContext.sourceType
        )
        synchronized(fullPlaybackHistory) {
            fullPlaybackHistory.add(entry)
        }
        
        synchronized(recentlyPlayedWindow) {
            recentlyPlayedWindow.add(track.id)
            if (recentlyPlayedWindow.size > maxHistoryWindow) {
                val iterator = recentlyPlayedWindow.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }

    @Synchronized
    fun saveQueueState(): QueueState {
        return QueueState(
            explicitQueue = ArrayList(explicitQueue),
            playbackContext = playbackContext.copy(),
            shuffleTraversal = shuffleTraversal.copy(),
            playbackHistory = synchronized(fullPlaybackHistory) { ArrayList(fullPlaybackHistory) },
            recentlyPlayed = synchronized(recentlyPlayedWindow) { ArrayList(recentlyPlayedWindow) }
        )
    }

    @Synchronized
    fun restoreQueueState(state: QueueState) {
        explicitQueue.clear()
        explicitQueue.addAll(state.explicitQueue)
        playbackContext = state.playbackContext
        shuffleTraversal = state.shuffleTraversal
        synchronized(fullPlaybackHistory) {
            fullPlaybackHistory.clear()
            fullPlaybackHistory.addAll(state.playbackHistory)
        }
        synchronized(recentlyPlayedWindow) {
            recentlyPlayedWindow.clear()
            recentlyPlayedWindow.addAll(state.recentlyPlayed)
        }
    }

    @Synchronized
    fun setPlaybackContext(
        context: PlaybackContext,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        shuffleMode: AdvancedShuffleMode = if (shuffle) AdvancedShuffleMode.SHUFFLE_ALL else AdvancedShuffleMode.SHUFFLE_OFF,
        forceReshuffle: Boolean = false
    ) {
        val safeIndex = if (context.entries.isNotEmpty()) startIndex.coerceIn(0, context.entries.size - 1) else 0
        val targetEntry = context.entries.getOrNull(safeIndex)
        val targetEntryId = targetEntry?.entryId

        val prevContextEntryIds = playbackContext.entries.map { it.entryId }
        val newContextEntryIds = context.entries.map { it.entryId }
        val isSameContext = prevContextEntryIds.isNotEmpty() && prevContextEntryIds == newContextEntryIds

        playbackContext = context.withPosition(safeIndex)

        if (!isSameContext) {
            isPlayingFromExplicitQueue = false
            savedContextPositionBeforeQueue = null
            savedShufflePositionBeforeQueue = null
        }

        if (shuffle && context.entries.isNotEmpty()) {
            val effectiveMode = if (shuffleMode == AdvancedShuffleMode.SHUFFLE_OFF) AdvancedShuffleMode.SHUFFLE_ALL else shuffleMode
            if (targetEntryId != null) {
                val existingTraversalIdx = if (!forceReshuffle && isSameContext && shuffleTraversal.isEnabled && shuffledEntries.isNotEmpty()) {
                    shuffledEntries.indexOfFirst { it.entryId == targetEntryId }
                } else -1

                if (existingTraversalIdx >= 0) {
                    val order = synchronized(shuffledEntries) { shuffledEntries.map { it.track.id } }
                    shuffleTraversal = ShuffleTraversal(
                        isEnabled = true,
                        mode = effectiveMode,
                        traversalOrder = order,
                        shufflePosition = existingTraversalIdx
                    )
                } else {
                    generateShuffleTraversal(targetEntry, effectiveMode)
                }
            } else {
                generateShuffleTraversal(context.entries.first(), effectiveMode)
            }
        } else if (!shuffle) {
            synchronized(shuffledEntries) {
                shuffledEntries.clear()
            }
            shuffleTraversal = ShuffleTraversal(isEnabled = false, mode = AdvancedShuffleMode.SHUFFLE_OFF)
        }
    }

    @Synchronized
    fun setLibraryContext(tracks: List<DownloadedTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        reconcileLibraryContext(tracks, startIndex, shuffle)
    }

    @Synchronized
    fun reconcileLibraryContext(tracks: List<DownloadedTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        val reconciled = playbackContext.reconcileExistingPlaybackContext(tracks)
        val safeIndex = startIndex.coerceIn(0, (reconciled.entries.size - 1).coerceAtLeast(0))
        setPlaybackContext(
            context = reconciled.copy(sourceType = PlaybackSourceType.ALL_SONGS, contextPosition = safeIndex),
            startIndex = safeIndex,
            shuffle = shuffle
        )
    }

    fun getPlaybackContext(): PlaybackContext = playbackContext

    fun getLibraryContext(): List<DownloadedTrack> = playbackContext.tracks

    fun getShuffleTraversal(): ShuffleTraversal = shuffleTraversal

    @Synchronized
    fun getShuffledEntries(): List<QueueEntry> {
        synchronized(shuffledEntries) {
            return ArrayList(shuffledEntries)
        }
    }

    @Synchronized
    fun getShuffledList(): List<DownloadedTrack> {
        synchronized(shuffledEntries) {
            return shuffledEntries.map { it.track }
        }
    }

    @Synchronized
    fun restoreShuffledState(restoredEntries: List<QueueEntry>, index: Int) {
        if (restoredEntries.isNotEmpty()) {
            val safeIdx = index.coerceIn(0, restoredEntries.size - 1)
            synchronized(shuffledEntries) {
                shuffledEntries.clear()
                shuffledEntries.addAll(restoredEntries)
            }
            val order = restoredEntries.map { it.track.id }
            shuffleTraversal = ShuffleTraversal(
                isEnabled = true,
                mode = AdvancedShuffleMode.SHUFFLE_ALL,
                traversalOrder = order,
                shufflePosition = safeIdx
            )
        }
    }

    @Synchronized
    fun restoreShuffledTracksState(restoredTracks: List<DownloadedTrack>, index: Int) {
        restoreShuffledState(restoredTracks.map { it.toQueueEntry() }, index)
    }

    @Synchronized
    fun getExplicitQueueEntries(): List<QueueEntry> = ArrayList(explicitQueue)

    @Synchronized
    fun getExplicitQueue(): List<DownloadedTrack> = synchronized(explicitQueue) { explicitQueue.map { it.track } }

    fun isShuffleEnabled(): Boolean = shuffleTraversal.isEnabled

    fun isPlayingFromExplicitQueue(): Boolean = isPlayingFromExplicitQueue

    fun getQueueReturnMode(): QueueReturnMode = queueReturnMode

    fun setQueueReturnMode(mode: QueueReturnMode) {
        queueReturnMode = mode
    }
    @Synchronized
    fun getUpcomingQueueEntries(maxCount: Int = 100): List<QueueEntry> {
        val result = mutableListOf<QueueEntry>()
        if (shuffleTraversal.isEnabled && shuffledEntries.isNotEmpty()) {
            val startIdx = if (shuffleTraversal.shufflePosition >= 0) shuffleTraversal.shufflePosition + 1 else 0
            synchronized(shuffledEntries) {
                for (i in startIdx until shuffledEntries.size) {
                    result.add(shuffledEntries[i])
                    if (result.size >= maxCount) break
                }
            }
        } else {
            val startIdx = if (playbackContext.contextPosition >= 0) playbackContext.contextPosition + 1 else 0
            for (i in startIdx until playbackContext.entries.size) {
                result.add(playbackContext.entries[i])
                if (result.size >= maxCount) break
            }
        }
        return result
    }



    @Synchronized
    fun getUpcomingTracks(maxCount: Int = 100): List<DownloadedTrack> {
        val result = mutableListOf<DownloadedTrack>()

        if (shuffleTraversal.isEnabled && shuffledEntries.isNotEmpty()) {
            val startIdx = if (shuffleTraversal.shufflePosition >= 0) shuffleTraversal.shufflePosition + 1 else 0
            synchronized(shuffledEntries) {
                for (i in startIdx until shuffledEntries.size) {
                    result.add(shuffledEntries[i].track)
                    if (result.size >= maxCount) break
                }
            }
        } else {
            val startIdx = if (playbackContext.contextPosition >= 0) playbackContext.contextPosition + 1 else 0
            for (i in startIdx until playbackContext.entries.size) {
                result.add(playbackContext.entries[i].track)
                if (result.size >= maxCount) break
            }
        }
        return result
    }

    @Synchronized
    fun setShuffleEnabled(enabled: Boolean, currentTrack: DownloadedTrack? = null) {
        val currentEntry = playbackContext.currentEntry?.takeIf { currentTrack == null || it.track.id == currentTrack.id }
            ?: (if (currentTrack != null) playbackContext.entries.find { it.track.id == currentTrack.id } ?: currentTrack.toQueueEntry() else null)
            ?: playbackContext.currentEntry
        if (enabled) {
            if (currentEntry != null && playbackContext.indexOfEntryId(currentEntry.entryId) < 0) {
                val updated = listOf(currentEntry) + playbackContext.entries.filter { it.entryId != currentEntry.entryId }
                playbackContext = playbackContext.copy(entries = updated, contextPosition = 0)
            }
            val startEntry = currentEntry ?: playbackContext.entries.firstOrNull()
            if (startEntry != null) {
                generateShuffleTraversal(startEntry, AdvancedShuffleMode.SHUFFLE_ALL)
            } else {
                synchronized(shuffledEntries) { shuffledEntries.clear() }
                shuffleTraversal = ShuffleTraversal(isEnabled = true, mode = AdvancedShuffleMode.SHUFFLE_ALL)
            }
        } else {
            if (currentEntry != null) {
                val idx = playbackContext.indexOfEntryId(currentEntry.entryId)
                if (idx >= 0) {
                    playbackContext = playbackContext.withPosition(idx)
                } else {
                    val trackIdx = playbackContext.indexOfTrack(currentEntry.track.id)
                    if (trackIdx >= 0) {
                        playbackContext = playbackContext.withPosition(trackIdx)
                    } else {
                        com.anuj.obsidian.ObsidianCrashLogger.log("ShuffleManager: handled idx == -1 for ${currentEntry.track.title}")
                        val updated = listOf(currentEntry) + playbackContext.entries
                        playbackContext = playbackContext.copy(entries = updated, contextPosition = 0)
                    }
                }
            }
            synchronized(shuffledEntries) { shuffledEntries.clear() }
            shuffleTraversal = ShuffleTraversal(isEnabled = false, mode = AdvancedShuffleMode.SHUFFLE_OFF)
        }
    }

    @Synchronized
    fun setAdvancedShuffleMode(mode: AdvancedShuffleMode, currentTrack: DownloadedTrack? = null) {
        if (mode == AdvancedShuffleMode.SHUFFLE_OFF) {
            setShuffleEnabled(false, currentTrack)
        } else {
            val currentEntry = playbackContext.currentEntry?.takeIf { currentTrack == null || it.track.id == currentTrack.id }
                ?: (if (currentTrack != null) playbackContext.entries.find { it.track.id == currentTrack.id } ?: currentTrack.toQueueEntry() else null)
                ?: playbackContext.currentEntry
            if (currentEntry != null && playbackContext.indexOfEntryId(currentEntry.entryId) < 0) {
                val updated = listOf(currentEntry) + playbackContext.entries.filter { it.entryId != currentEntry.entryId }
                playbackContext = playbackContext.copy(entries = updated, contextPosition = 0)
            }
            val startEntry = currentEntry ?: playbackContext.entries.firstOrNull()
            if (startEntry != null) {
                generateShuffleTraversal(startEntry, mode)
            } else {
                synchronized(shuffledEntries) { shuffledEntries.clear() }
                shuffleTraversal = ShuffleTraversal(isEnabled = true, mode = mode)
            }
        }
    }

    @Synchronized
    fun addToExplicitQueue(tracks: List<DownloadedTrack>): List<QueueEntry> {
        val newEntries = tracks.map { it.toQueueEntry() }
        explicitQueue.addAll(newEntries)
        return newEntries
    }

    @Synchronized
    fun addToExplicitQueueEntries(entries: List<QueueEntry>) {
        explicitQueue.addAll(entries)
    }

    @Synchronized
    fun addToExplicitQueueNext(tracks: List<DownloadedTrack>): List<QueueEntry> {
        val newEntries = tracks.map { it.toQueueEntry() }
        explicitQueue.addAll(0, newEntries)
        return newEntries
    }

    @Synchronized
    fun moveQueueItem(from: Int, to: Int) {
        synchronized(explicitQueue) {
            if (from in explicitQueue.indices && to in explicitQueue.indices) {
                val item = explicitQueue.removeAt(from)
                explicitQueue.add(to, item)
            }
        }
    }

    @Synchronized
    fun reorderUpcomingTrack(fromIndex: Int, toIndex: Int) {
        if (shuffleTraversal.isEnabled && shuffledEntries.isNotEmpty()) {
            val startIdx = (shuffleTraversal.shufflePosition + 1).coerceAtLeast(0)
            val actualFrom = startIdx + fromIndex
            val actualTo = startIdx + toIndex
            if (actualFrom in shuffledEntries.indices && actualTo in shuffledEntries.indices) {
                val item = shuffledEntries.removeAt(actualFrom)
                shuffledEntries.add(actualTo, item)
                shuffleTraversal = shuffleTraversal.copy(traversalOrder = shuffledEntries.map { it.track.id })
            }
        } else if (playbackContext.entries.isNotEmpty()) {
            val startIdx = (playbackContext.contextPosition + 1).coerceAtLeast(0)
            val actualFrom = startIdx + fromIndex
            val actualTo = startIdx + toIndex
            if (actualFrom in playbackContext.entries.indices && actualTo in playbackContext.entries.indices) {
                val mutableEntries = playbackContext.entries.toMutableList()
                val item = mutableEntries.removeAt(actualFrom)
                mutableEntries.add(actualTo, item)
                playbackContext = playbackContext.copy(entries = mutableEntries)
            }
        }
    }

    @Synchronized
    fun clearUpcomingTracks() {
        if (shuffleTraversal.isEnabled && shuffledEntries.isNotEmpty()) {
            val curPos = shuffleTraversal.shufflePosition.coerceAtLeast(0)
            synchronized(shuffledEntries) {
                while (shuffledEntries.size > curPos + 1) {
                    shuffledEntries.removeAt(shuffledEntries.size - 1)
                }
            }
            shuffleTraversal = shuffleTraversal.copy(traversalOrder = shuffledEntries.map { it.track.id })
        } else if (playbackContext.entries.isNotEmpty()) {
            val curPos = playbackContext.contextPosition.coerceAtLeast(0)
            val kept = playbackContext.entries.take(curPos + 1)
            playbackContext = playbackContext.copy(entries = kept)
        }
    }

    @Synchronized
    fun clearAllQueueAndUpcoming() {
        explicitQueue.clear()
        clearUpcomingTracks()
    }

    @Synchronized
    fun reshuffle(currentTrack: DownloadedTrack? = null) {
        val mode = if (shuffleTraversal.isEnabled && shuffleTraversal.mode != AdvancedShuffleMode.SHUFFLE_OFF) {
            shuffleTraversal.mode
        } else {
            AdvancedShuffleMode.SHUFFLE_ALL
        }
        val startEntry = playbackContext.currentEntry ?: currentTrack?.toQueueEntry() ?: playbackContext.entries.firstOrNull()
        if (startEntry != null) {
            generateShuffleTraversal(startEntry, mode)
        }
    }

    @Synchronized
    fun clearQueueAfterEntry(entry: QueueEntry): List<DownloadedTrack> {
        val idx = explicitQueue.indexOfFirst { it.entryId == entry.entryId }
        return if (idx >= 0) {
            clearQueueAfterIndex(idx)
        } else {
            clearUpcomingTracks()
            emptyList()
        }
    }

    @Synchronized
    fun removeFromQueue(index: Int): DownloadedTrack? {
        return if (index in explicitQueue.indices) {
            explicitQueue.removeAt(index).track
        } else null
    }

    @Synchronized
    fun peekQueueIndex(index: Int): QueueEntry? {
        return if (index in explicitQueue.indices) {
            explicitQueue[index]
        } else null
    }

    @Synchronized
    fun consumeExplicitQueueEntry(entryId: String): Boolean {
        synchronized(explicitQueue) {
            val idx = explicitQueue.indexOfFirst { it.entryId == entryId }
            if (idx >= 0) {
                if (!isPlayingFromExplicitQueue) {
                    savedContextPositionBeforeQueue = playbackContext.contextPosition
                    savedShufflePositionBeforeQueue = shuffleTraversal.shufflePosition
                }
                isPlayingFromExplicitQueue = true
                val entry = explicitQueue.removeAt(idx)
                lastPlayedQueueEntry = entry
                return true
            }
        }
        return false
    }

    @Synchronized
    fun findQueueEntryByEntryId(entryId: String): QueueEntry? {
        synchronized(explicitQueue) {
            explicitQueue.firstOrNull { it.entryId == entryId }?.let { return it }
        }
        synchronized(shuffledEntries) {
            shuffledEntries.firstOrNull { it.entryId == entryId }?.let { return it }
        }
        playbackContext.entries.firstOrNull { it.entryId == entryId }?.let { return it }
        return null
    }

    @Synchronized
    fun playQueueIndex(index: Int): QueueEntry? {
        if (index in explicitQueue.indices) {
            if (!isPlayingFromExplicitQueue) {
                savedContextPositionBeforeQueue = playbackContext.contextPosition
                savedShufflePositionBeforeQueue = shuffleTraversal.shufflePosition
            }
            isPlayingFromExplicitQueue = true
            val entry = explicitQueue.removeAt(index)
            lastPlayedQueueEntry = entry
            return entry
        }
        return null
    }

    @Synchronized
    fun clearQueue() {
        explicitQueue.clear()
        isPlayingFromExplicitQueue = false
        savedContextPositionBeforeQueue = null
        savedShufflePositionBeforeQueue = null
        lastPlayedQueueEntry = null
    }

    private fun isMatchingTrack(a: DownloadedTrack?, b: DownloadedTrack?): Boolean {
        if (a == null || b == null) return false
        if (a.id > 0L && b.id > 0L) return a.id == b.id
        if (a.localFilePath.isNotBlank() && b.localFilePath.isNotBlank()) return a.localFilePath == b.localFilePath
        return a.title.equals(b.title, ignoreCase = true) && a.artist.equals(b.artist, ignoreCase = true)
    }

    private fun isMatchingEntry(entry: QueueEntry, currentEntry: QueueEntry?): Boolean {
        if (currentEntry == null) return false
        if (entry.entryId == currentEntry.entryId) return true
        return isMatchingTrack(entry.track, currentEntry.track)
    }

    @Synchronized
    fun peekNextEntry(repeatMode: Int, currentEntry: QueueEntry?): QueueEntry? {
        if (explicitQueue.isNotEmpty()) {
            return explicitQueue.firstOrNull()
        }

        if (repeatMode == Player.REPEAT_MODE_ONE && currentEntry != null) {
            return currentEntry
        }

        if (shuffleTraversal.isEnabled && shuffledEntries.isNotEmpty()) {
            val curIdx = if (currentEntry != null) {
                val found = shuffledEntries.indexOfFirst { isMatchingEntry(it, currentEntry) }
                if (found >= 0) found else shuffleTraversal.shufflePosition.coerceIn(0, shuffledEntries.size - 1)
            } else {
                shuffleTraversal.shufflePosition.coerceIn(0, shuffledEntries.size - 1)
            }
            val nextIdx = curIdx + 1
            if (nextIdx in shuffledEntries.indices) {
                return shuffledEntries[nextIdx]
            }
            if (repeatMode == Player.REPEAT_MODE_ALL) {
                return shuffledEntries.firstOrNull()
            }
            return null
        }

        if (playbackContext.isEmpty) return null
        val curEntryId = currentEntry?.entryId ?: playbackContext.currentEntry?.entryId
        val curIdx = (if (curEntryId != null) playbackContext.indexOfEntryId(curEntryId) else -1).takeIf { it >= 0 }
            ?: (if (currentEntry != null) playbackContext.indexOfTrack(currentEntry.track) else -1).takeIf { it >= 0 }
            ?: playbackContext.contextPosition
        val nextIdx = curIdx + 1
        if (nextIdx in playbackContext.entries.indices) {
            return playbackContext.entries[nextIdx]
        }
        if (repeatMode == Player.REPEAT_MODE_ALL && playbackContext.entries.isNotEmpty()) {
            return playbackContext.entries.firstOrNull()
        }
        return null
    }

    @Synchronized
    fun peekNextTrack(repeatMode: Int, currentTrack: DownloadedTrack?): DownloadedTrack? {
        val curEntry = playbackContext.currentEntry?.takeIf { currentTrack == null || isMatchingTrack(it.track, currentTrack) }
            ?: (if (currentTrack != null) playbackContext.entries.find { isMatchingTrack(it.track, currentTrack) } ?: currentTrack.toQueueEntry() else null)
            ?: playbackContext.currentEntry
        return peekNextEntry(repeatMode, curEntry)?.track
    }

    @Synchronized
    fun advanceToNextEntry(repeatMode: Int, currentEntry: QueueEntry?): QueueEntry? {
        if (explicitQueue.isNotEmpty()) {
            if (!isPlayingFromExplicitQueue) {
                savedContextPositionBeforeQueue = playbackContext.contextPosition
                savedShufflePositionBeforeQueue = shuffleTraversal.shufflePosition
            }
            isPlayingFromExplicitQueue = true
            val entry = explicitQueue.removeAt(0)
            lastPlayedQueueEntry = entry
            return entry
        }

        if (isPlayingFromExplicitQueue) {
            isPlayingFromExplicitQueue = false
            handleQueueFinishReturn()
            val resumedEntry = if (shuffleTraversal.isEnabled && shuffledEntries.isNotEmpty()) {
                val sPos = shuffleTraversal.shufflePosition.coerceIn(0, shuffledEntries.size - 1)
                shuffledEntries.getOrNull(sPos)
            } else {
                playbackContext.currentEntry
            }
            return advanceToNextEntry(repeatMode, resumedEntry)
        }

        if (repeatMode == Player.REPEAT_MODE_ONE && currentEntry != null) {
            return currentEntry
        }

        if (shuffleTraversal.isEnabled && shuffledEntries.isNotEmpty()) {
            val curIdx = if (currentEntry != null) {
                val found = shuffledEntries.indexOfFirst { isMatchingEntry(it, currentEntry) }
                if (found >= 0) found else shuffleTraversal.shufflePosition.coerceIn(0, shuffledEntries.size - 1)
            } else {
                shuffleTraversal.shufflePosition.coerceIn(0, shuffledEntries.size - 1)
            }
            val nextIdx = curIdx + 1
            if (nextIdx in shuffledEntries.indices) {
                shuffleTraversal = shuffleTraversal.withPosition(nextIdx)
                return shuffledEntries[nextIdx]
            }
            if (repeatMode == Player.REPEAT_MODE_ALL) {
                shuffleTraversal = shuffleTraversal.withPosition(0)
                return shuffledEntries.firstOrNull()
            }
            return null
        }

        if (playbackContext.isEmpty) return null
        val curEntryId = currentEntry?.entryId ?: playbackContext.currentEntry?.entryId
        val curIdx = (if (curEntryId != null) playbackContext.indexOfEntryId(curEntryId) else -1).takeIf { it >= 0 }
            ?: (if (currentEntry != null) playbackContext.indexOfTrack(currentEntry.track) else -1).takeIf { it >= 0 }
            ?: playbackContext.contextPosition
        val nextIdx = curIdx + 1
        if (nextIdx in playbackContext.entries.indices) {
            playbackContext = playbackContext.withPosition(nextIdx)
            return playbackContext.entries[nextIdx]
        }
        if (repeatMode == Player.REPEAT_MODE_ALL && playbackContext.entries.isNotEmpty()) {
            playbackContext = playbackContext.withPosition(0)
            return playbackContext.entries.firstOrNull()
        }
        return null
    }

    @Synchronized
    fun advanceToNextTrack(repeatMode: Int, currentTrack: DownloadedTrack?): DownloadedTrack? {
        val curEntry = playbackContext.currentEntry?.takeIf { currentTrack == null || isMatchingTrack(it.track, currentTrack) }
            ?: (if (currentTrack != null) playbackContext.entries.find { isMatchingTrack(it.track, currentTrack) } ?: currentTrack.toQueueEntry() else null)
            ?: playbackContext.currentEntry
        return advanceToNextEntry(repeatMode, curEntry)?.track
    }

    private fun handleQueueFinishReturn() {
        when (queueReturnMode) {
            QueueReturnMode.RETURN_TO_PREVIOUS -> {
                savedContextPositionBeforeQueue?.let {
                    playbackContext = playbackContext.withPosition(it)
                }
                savedShufflePositionBeforeQueue?.let {
                    shuffleTraversal = shuffleTraversal.withPosition(it)
                }
            }
            QueueReturnMode.CONTINUE_CATEGORY -> {
                val lastEntry = lastPlayedQueueEntry
                if (lastEntry != null && lastEntry.track.album.isNotBlank()) {
                    val matchingIndex = playbackContext.entries.indexOfFirst { it.track.album.equals(lastEntry.track.album, ignoreCase = true) }
                    if (matchingIndex >= 0) {
                        playbackContext = playbackContext.withPosition(matchingIndex)
                    }
                }
            }
            QueueReturnMode.STOP_PLAYBACK -> {
                // Handled by completion
            }
        }
        savedContextPositionBeforeQueue = null
        savedShufflePositionBeforeQueue = null
        lastPlayedQueueEntry = null
    }

    @Synchronized
    fun peekPreviousEntry(repeatMode: Int, currentEntry: QueueEntry?): QueueEntry? {
        if (shuffleTraversal.isEnabled && shuffledEntries.isNotEmpty()) {
            val curIdx = if (currentEntry != null) {
                val found = shuffledEntries.indexOfFirst { isMatchingEntry(it, currentEntry) }
                if (found >= 0) found else shuffleTraversal.shufflePosition.coerceIn(0, shuffledEntries.size - 1)
            } else {
                shuffleTraversal.shufflePosition.coerceIn(0, shuffledEntries.size - 1)
            }
            val prevIdx = curIdx - 1
            if (prevIdx in shuffledEntries.indices) {
                return shuffledEntries[prevIdx]
            }
            if (repeatMode == Player.REPEAT_MODE_ALL) {
                return shuffledEntries.lastOrNull()
            }
            return null
        }

        val curEntryId = currentEntry?.entryId ?: playbackContext.currentEntry?.entryId
        val curIdx = (if (curEntryId != null) playbackContext.indexOfEntryId(curEntryId) else -1).takeIf { it >= 0 }
            ?: (if (currentEntry != null) playbackContext.indexOfTrack(currentEntry.track) else -1).takeIf { it >= 0 }
            ?: playbackContext.contextPosition
        val prevIdx = curIdx - 1
        if (prevIdx in playbackContext.entries.indices) {
            return playbackContext.entries[prevIdx]
        }
        if (repeatMode == Player.REPEAT_MODE_ALL && playbackContext.entries.isNotEmpty()) {
            return playbackContext.entries.lastOrNull()
        }
        return null
    }

    @Synchronized
    fun peekPreviousTrack(repeatMode: Int, currentTrack: DownloadedTrack?): DownloadedTrack? {
        val curEntry = playbackContext.currentEntry?.takeIf { currentTrack == null || isMatchingTrack(it.track, currentTrack) }
            ?: (if (currentTrack != null) playbackContext.entries.find { isMatchingTrack(it.track, currentTrack) } else null)
            ?: playbackContext.currentEntry
        return peekPreviousEntry(repeatMode, curEntry)?.track
    }

    @Synchronized
    fun retreatToPreviousEntry(repeatMode: Int, currentEntry: QueueEntry?): QueueEntry? {
        return peekPreviousEntry(repeatMode, currentEntry)
    }

    @Synchronized
    fun retreatToPreviousTrack(repeatMode: Int, currentTrack: DownloadedTrack?): DownloadedTrack? {
        val curEntry = playbackContext.currentEntry?.takeIf { currentTrack == null || isMatchingTrack(it.track, currentTrack) }
            ?: (if (currentTrack != null) playbackContext.entries.find { isMatchingTrack(it.track, currentTrack) } else null)
            ?: playbackContext.currentEntry
        return peekPreviousEntry(repeatMode, curEntry)?.track
    }

    @Synchronized
    fun commitEntryTransition(entry: QueueEntry) {
        consumeExplicitQueueEntry(entry.entryId)

        val ctxIdx = playbackContext.indexOfEntryId(entry.entryId)
        if (ctxIdx >= 0) {
            playbackContext = playbackContext.withPosition(ctxIdx)
        }

        if (shuffleTraversal.isEnabled) {
            val shfIdx = shuffledEntries.indexOfFirst { it.entryId == entry.entryId }
            if (shfIdx >= 0) {
                shuffleTraversal = shuffleTraversal.withPosition(shfIdx)
            }
        }
        recordTrackPlayed(entry.track)
    }

    @Deprecated("Track-only commit is disabled to preserve QueueEntry identity", level = DeprecationLevel.ERROR)
    @Synchronized
    fun commitTrackTransition(track: DownloadedTrack) {
        val matchingEntry = explicitQueue.firstOrNull { it.track.id == track.id }
            ?: playbackContext.entries.find { it.track.id == track.id }
        if (matchingEntry != null) {
            commitEntryTransition(matchingEntry)
        }
    }

    @Synchronized
    fun planNextCategoryEntry(currentEntry: QueueEntry?): QueueEntry? {
        val entries = playbackContext.entries
        if (entries.isEmpty()) return null
        val cur = currentEntry?.track ?: playbackContext.currentTrack ?: return null
        val curCat = getCategoryKey(cur)
        val curIdx = (if (currentEntry != null) playbackContext.indexOfEntryId(currentEntry.entryId) else playbackContext.contextPosition).coerceAtLeast(0)
        
        var targetEntry = entries.drop(curIdx + 1).firstOrNull { getCategoryKey(it.track) != curCat }
        if (targetEntry == null) {
            targetEntry = entries.firstOrNull { getCategoryKey(it.track) != curCat }
        }
        return targetEntry
    }

    @Synchronized
    fun planPreviousCategoryEntry(currentEntry: QueueEntry?): QueueEntry? {
        val entries = playbackContext.entries
        if (entries.isEmpty()) return null
        val cur = currentEntry?.track ?: playbackContext.currentTrack ?: return null
        val curCat = getCategoryKey(cur)
        val curIdx = (if (currentEntry != null) playbackContext.indexOfEntryId(currentEntry.entryId) else playbackContext.contextPosition).coerceAtLeast(0)
        
        for (i in (curIdx - 1) downTo 0) {
            val e = entries[i]
            if (getCategoryKey(e.track) != curCat) {
                val prevCat = getCategoryKey(e.track)
                return entries.firstOrNull { getCategoryKey(it.track) == prevCat } ?: e
            }
        }
        return null
    }

    @Synchronized
    fun skipToNextCategory(currentTrack: DownloadedTrack?): DownloadedTrack? {
        val curEntry = playbackContext.currentEntry?.takeIf { currentTrack == null || it.track.id == currentTrack.id }
            ?: (if (currentTrack != null) playbackContext.entries.find { it.track.id == currentTrack.id } else null)
            ?: playbackContext.currentEntry
        return planNextCategoryEntry(curEntry)?.track
    }

    @Synchronized
    fun skipToPreviousCategory(currentTrack: DownloadedTrack?): DownloadedTrack? {
        val curEntry = playbackContext.currentEntry?.takeIf { currentTrack == null || it.track.id == currentTrack.id }
            ?: (if (currentTrack != null) playbackContext.entries.find { it.track.id == currentTrack.id } else null)
            ?: playbackContext.currentEntry
        return planPreviousCategoryEntry(curEntry)?.track
    }

    private fun getCategoryKey(track: DownloadedTrack): String {
        return when (playbackContext.sourceType) {
            PlaybackSourceType.ALBUM -> track.album.ifBlank { "Unknown Album" }
            PlaybackSourceType.ARTIST -> track.artist.ifBlank { "Unknown Artist" }
            PlaybackSourceType.FOLDER -> {
                val path = track.localFilePath
                if (path.isNotBlank()) {
                    val file = java.io.File(path)
                    file.parentFile?.name ?: "Unknown Folder"
                } else "Unknown Folder"
            }
            PlaybackSourceType.GENRE -> track.genre.ifBlank { "Unknown Genre" }
            else -> track.album.ifBlank { track.artist.ifBlank { "Unknown Category" } }
        }
    }

    @Synchronized
    fun setShuffleMode(mode: ShuffleMode, currentTrack: DownloadedTrack? = null) {
        val entries = playbackContext.entries
        when (mode) {
            ShuffleMode.OFF -> {
                setShuffleEnabled(false, currentTrack)
            }
            ShuffleMode.ALL -> {
                setAdvancedShuffleMode(AdvancedShuffleMode.SHUFFLE_ALL, currentTrack)
            }
            ShuffleMode.RECENT_BIAS -> {
                if (entries.isNotEmpty()) {
                    val recentSet = synchronized(recentlyPlayedWindow) { recentlyPlayedWindow.toSet() }
                    val recentEntries = entries.filter { it.track.id in recentSet }.shuffled()
                    val otherEntries = entries.filter { it.track.id !in recentSet }.shuffled()
                    val start = playbackContext.currentEntry ?: currentTrack?.toQueueEntry() ?: entries.first()
                    val ordered = (listOf(start) + (recentEntries + otherEntries).filter { !isMatchingEntry(it, start) })
                    synchronized(shuffledEntries) {
                        shuffledEntries.clear()
                        shuffledEntries.addAll(ordered)
                    }
                    shuffleTraversal = ShuffleTraversal(
                        isEnabled = true,
                        mode = AdvancedShuffleMode.SHUFFLE_ALL,
                        traversalOrder = ordered.map { it.track.id },
                        shufflePosition = 0
                    )
                }
            }
            ShuffleMode.ENERGY_FLOW, ShuffleMode.GENRE_TRANSITIONS -> {
                if (entries.isNotEmpty()) {
                    val byCategory = entries.groupBy { getCategoryKey(it.track) }
                    val categoryOrder = byCategory.keys.shuffled()
                    val orderedEntries = mutableListOf<QueueEntry>()
                    val start = playbackContext.currentEntry ?: currentTrack?.toQueueEntry() ?: entries.first()
                    orderedEntries.add(start)
                    for (cat in categoryOrder) {
                        val catEntries = byCategory[cat]?.filter { !isMatchingEntry(it, start) }?.shuffled() ?: emptyList()
                        orderedEntries.addAll(catEntries)
                    }
                    synchronized(shuffledEntries) {
                        shuffledEntries.clear()
                        shuffledEntries.addAll(orderedEntries)
                    }
                    shuffleTraversal = ShuffleTraversal(
                        isEnabled = true,
                        mode = AdvancedShuffleMode.SHUFFLE_CATEGORIES,
                        traversalOrder = orderedEntries.map { it.track.id },
                        shufflePosition = 0
                    )
                }
            }
            ShuffleMode.DISCOVERY -> {
                if (entries.isNotEmpty()) {
                    val recentSet = synchronized(recentlyPlayedWindow) { recentlyPlayedWindow.toSet() }
                    val unplayed = entries.filter { it.track.id !in recentSet }.shuffled()
                    val played = entries.filter { it.track.id in recentSet }.shuffled()
                    val start = playbackContext.currentEntry ?: unplayed.firstOrNull() ?: entries.first()
                    val ordered = (listOf(start) + (unplayed + played).filter { !isMatchingEntry(it, start) })
                    synchronized(shuffledEntries) {
                        shuffledEntries.clear()
                        shuffledEntries.addAll(ordered)
                    }
                    shuffleTraversal = ShuffleTraversal(
                        isEnabled = true,
                        mode = AdvancedShuffleMode.SHUFFLE_ALL,
                        traversalOrder = ordered.map { it.track.id },
                        shufflePosition = 0
                    )
                }
            }
            ShuffleMode.SMART_MIX -> {
                setAdvancedShuffleMode(AdvancedShuffleMode.SHUFFLE_SONGS_AND_CATEGORIES, currentTrack)
            }
        }
        Log.d(TAG, "Shuffle mode set to: $mode")
    }

    @Synchronized
    fun insertTrackAtIndex(track: DownloadedTrack, index: Int): Boolean {
        synchronized(explicitQueue) {
            if (index < 0 || index > explicitQueue.size) return false
            explicitQueue.add(index, track.toQueueEntry())
            Log.d(TAG, "Inserted ${track.title} at index $index in explicit queue")
            return true
        }
    }

    @Synchronized
    fun removeTrackAtIndex(index: Int): DownloadedTrack? {
        synchronized(explicitQueue) {
            if (index !in explicitQueue.indices) return null
            val removed = explicitQueue.removeAt(index)
            Log.d(TAG, "Removed ${removed.track.title} from explicit queue at index $index")
            return removed.track
        }
    }

    @Synchronized
    fun moveTrackInQueue(fromIndex: Int, toIndex: Int): Boolean {
        synchronized(explicitQueue) {
            if (fromIndex !in explicitQueue.indices || toIndex !in explicitQueue.indices) return false
            val entry = explicitQueue.removeAt(fromIndex)
            explicitQueue.add(toIndex, entry)
            Log.d(TAG, "Moved ${entry.track.title} from $fromIndex to $toIndex")
            return true
        }
    }

    @Synchronized
    fun swapQueueTracks(index1: Int, index2: Int): Boolean {
        synchronized(explicitQueue) {
            if (index1 !in explicitQueue.indices || index2 !in explicitQueue.indices) return false
            val temp = explicitQueue[index1]
            explicitQueue[index1] = explicitQueue[index2]
            explicitQueue[index2] = temp
            Log.d(TAG, "Swapped queue tracks at $index1 and $index2")
            return true
        }
    }

    @Synchronized
    fun clearQueueAfterIndex(index: Int): List<DownloadedTrack> {
        synchronized(explicitQueue) {
            if (index < 0 || index >= explicitQueue.size) return emptyList()
            val removed = explicitQueue.drop(index + 1)
            while (explicitQueue.size > index + 1) {
                explicitQueue.removeAt(explicitQueue.size - 1)
            }
            Log.d(TAG, "Cleared ${removed.size} tracks after index $index")
            return removed.map { it.track }
        }
    }

    private fun MutableList<QueueEntry>.shuffleEntriesWithHistory() {
        if (size <= 1) return
        val recentSet = synchronized(recentlyPlayedWindow) { recentlyPlayedWindow.toSet() }
        val unplayed = filter { it.track.id !in recentSet }.toMutableList()
        val played = filter { it.track.id in recentSet }.toMutableList()
        unplayed.shuffle()
        played.shuffle()
        clear()
        addAll(unplayed)
        addAll(played)
    }

    private fun generateShuffleTraversal(startEntry: QueueEntry?, mode: AdvancedShuffleMode) {
        val entries = playbackContext.entries
        if (entries.isEmpty()) {
            synchronized(shuffledEntries) { shuffledEntries.clear() }
            shuffleTraversal = ShuffleTraversal(isEnabled = true, mode = mode, traversalOrder = emptyList(), shufflePosition = -1)
            return
        }

        val start = startEntry ?: entries.first()
        val startCategory = getCategoryKey(start.track)

        val newList = ArrayList<QueueEntry>(entries.size)

        when (mode) {
            AdvancedShuffleMode.SHUFFLE_TRACKS -> {
                val currentCategoryEntries = entries.filter { getCategoryKey(it.track) == startCategory }
                val remainingCurrentCat = currentCategoryEntries.filter { !isMatchingEntry(it, start) }.toMutableList()
                remainingCurrentCat.shuffleEntriesWithHistory()

                val otherCategoryEntries = entries.filter { getCategoryKey(it.track) != startCategory }.toMutableList()
                otherCategoryEntries.shuffleEntriesWithHistory()

                newList.add(start)
                newList.addAll(remainingCurrentCat)
                newList.addAll(otherCategoryEntries)
            }
            AdvancedShuffleMode.SHUFFLE_CATEGORIES -> {
                val currentCategoryEntries = entries.filter { getCategoryKey(it.track) == startCategory }
                val startCatRemaining = currentCategoryEntries.filter { !isMatchingEntry(it, start) }

                val otherCategories = entries.filter { getCategoryKey(it.track) != startCategory }
                    .groupBy { getCategoryKey(it.track) }
                    .values
                    .toMutableList()
                otherCategories.shuffle()

                newList.add(start)
                newList.addAll(startCatRemaining)
                for (catEntries in otherCategories) {
                    newList.addAll(catEntries)
                }
            }
            AdvancedShuffleMode.SHUFFLE_SONGS_AND_CATEGORIES -> {
                val currentCategoryEntries = entries.filter { getCategoryKey(it.track) == startCategory }
                val startCatRemaining = currentCategoryEntries.filter { !isMatchingEntry(it, start) }.toMutableList()
                startCatRemaining.shuffleEntriesWithHistory()

                val otherCategories = entries.filter { getCategoryKey(it.track) != startCategory }
                    .groupBy { getCategoryKey(it.track) }
                    .values
                    .map { it.toMutableList().apply { shuffleEntriesWithHistory() } }
                    .toMutableList()
                otherCategories.shuffle()

                newList.add(start)
                newList.addAll(startCatRemaining)
                for (catEntries in otherCategories) {
                    newList.addAll(catEntries)
                }
            }
            AdvancedShuffleMode.SHUFFLE_ALL -> {
                val allLibrary = synchronized(libraryTracks) { libraryTracks.toList() }
                val sourceEntries = if (allLibrary.isNotEmpty() && allLibrary.size >= entries.size) {
                    allLibrary.map { it.toQueueEntry() }
                } else entries
                val targetStart = sourceEntries.firstOrNull { isMatchingEntry(it, start) } ?: start
                val remaining = sourceEntries.filter { !isMatchingEntry(it, targetStart) }.toMutableList()
                remaining.shuffleEntriesWithHistory()

                newList.add(targetStart)
                newList.addAll(remaining)
            }
            else -> {
                val remaining = entries.filter { !isMatchingEntry(it, start) }.toMutableList()
                remaining.shuffleEntriesWithHistory()

                newList.add(start)
                newList.addAll(remaining)
            }
        }

        synchronized(shuffledEntries) {
            shuffledEntries.clear()
            shuffledEntries.addAll(newList)
        }

        val order = newList.map { it.track.id }
        shuffleTraversal = ShuffleTraversal(
            isEnabled = true,
            mode = mode,
            traversalOrder = order,
            shufflePosition = 0
        )
    }
}
