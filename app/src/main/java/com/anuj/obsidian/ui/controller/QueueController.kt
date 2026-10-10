package com.anuj.obsidian.ui.controller

import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.player.AudioPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QueueController(
    private val playerManager: AudioPlayerManager?
) {
    private val _explicitQueue = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val explicitQueue: StateFlow<List<DownloadedTrack>> = _explicitQueue.asStateFlow()

    private val _upcomingQueue = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val upcomingQueue: StateFlow<List<DownloadedTrack>> = _upcomingQueue.asStateFlow()

    fun syncFromPlayer() {
        playerManager?.let { pm ->
            _explicitQueue.value = pm.getExplicitQueue()
            _upcomingQueue.value = pm.getUpcomingTracks(50)
        }
    }

    fun playNext(track: DownloadedTrack) {
        playerManager?.addTracksToNextQueue(listOf(track))
        syncFromPlayer()
    }

    fun addToQueueEnd(track: DownloadedTrack) {
        playerManager?.addToQueue(listOf(track))
        syncFromPlayer()
    }

    fun removeExplicitQueueItem(index: Int): DownloadedTrack? {
        val removed = playerManager?.removeFromQueue(index)
        syncFromPlayer()
        return removed
    }

    fun reorderExplicitQueue(fromIndex: Int, toIndex: Int): Boolean {
        val res = playerManager?.moveTrackInQueue(fromIndex, toIndex) ?: false
        syncFromPlayer()
        return res
    }

    fun clearExplicitQueue() {
        playerManager?.clearQueue()
        syncFromPlayer()
    }
}
