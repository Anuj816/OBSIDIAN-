package com.anuj.obsidian.ui.player

import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.PlaybackContext
import com.anuj.obsidian.data.model.ShuffleTraversal

/**
 * Backwards-compatible controller delegating to [ShuffleManager].
 */
class ShuffleController {
    private val delegate = ShuffleManager()

    fun setPlaybackContext(
        context: PlaybackContext,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        shuffleMode: AdvancedShuffleMode = if (shuffle) AdvancedShuffleMode.SHUFFLE_ALL else AdvancedShuffleMode.SHUFFLE_OFF
    ) = delegate.setPlaybackContext(context, startIndex, shuffle, shuffleMode)

    fun setLibraryContext(tracks: List<DownloadedTrack>, startIndex: Int = 0, shuffle: Boolean = false) =
        delegate.reconcileLibraryContext(tracks, startIndex, shuffle)

    fun getPlaybackContext(): PlaybackContext = delegate.getPlaybackContext()

    fun getLibraryContext(): List<DownloadedTrack> = delegate.getLibraryContext()

    fun getShuffleTraversal(): ShuffleTraversal = delegate.getShuffleTraversal()

    fun getExplicitQueue(): List<DownloadedTrack> = delegate.getExplicitQueue()

    fun isShuffleEnabled(): Boolean = delegate.isShuffleEnabled()

    fun setShuffleEnabled(enabled: Boolean, currentTrack: DownloadedTrack? = null) =
        delegate.setShuffleEnabled(enabled, currentTrack)

    fun setAdvancedShuffleMode(mode: AdvancedShuffleMode, currentTrack: DownloadedTrack? = null) =
        delegate.setAdvancedShuffleMode(mode, currentTrack)

    fun addToExplicitQueue(tracks: List<DownloadedTrack>) = delegate.addToExplicitQueue(tracks)

    fun addToExplicitQueueNext(tracks: List<DownloadedTrack>) = delegate.addToExplicitQueueNext(tracks)

    fun moveQueueItem(from: Int, to: Int) = delegate.moveQueueItem(from, to)

    fun removeFromQueue(index: Int): DownloadedTrack? = delegate.removeFromQueue(index)

    fun playQueueIndex(index: Int): DownloadedTrack? = delegate.playQueueIndex(index)?.track

    fun clearQueue() = delegate.clearQueue()

    fun recordTrackPlayed(track: DownloadedTrack) = delegate.recordTrackPlayed(track)

    fun peekNextTrack(repeatMode: Int, currentTrack: DownloadedTrack?): DownloadedTrack? =
        delegate.peekNextTrack(repeatMode, currentTrack)

    fun advanceToNextTrack(repeatMode: Int, currentTrack: DownloadedTrack?): DownloadedTrack? =
        delegate.advanceToNextTrack(repeatMode, currentTrack)

    fun retreatToPreviousTrack(repeatMode: Int, currentTrack: DownloadedTrack?): DownloadedTrack? =
        delegate.retreatToPreviousTrack(repeatMode, currentTrack)

    @Suppress("DEPRECATION_ERROR")
    fun commitTrackTransition(track: DownloadedTrack) = delegate.commitTrackTransition(track)
}
