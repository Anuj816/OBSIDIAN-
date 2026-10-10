package com.anuj.obsidian.data.model

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

enum class ProgressiveSessionState {
    DOWNLOADING,
    AVAILABLE_FOR_PLAYBACK,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ProgressiveSession(
    val trackId: Long,
    val title: String,
    val artist: String,
    val sourceStreamUrl: String,
    val tempFile: File,
    val downloadedBytes: Long,
    val expectedTotalBytes: Long,
    val state: ProgressiveSessionState,
    val error: String? = null
)

object ProgressiveDownloadSessionRegistry {
    private val sessions = ConcurrentHashMap<Long, ProgressiveSession>()
    private val listeners = CopyOnWriteArrayList<(ProgressiveSession) -> Unit>()

    fun registerSession(session: ProgressiveSession) {
        sessions[session.trackId] = session
        notifyListeners(session)
    }

    fun updateSession(trackId: Long, update: (ProgressiveSession) -> ProgressiveSession) {
        val current = sessions[trackId] ?: return
        val updated = update(current)
        sessions[trackId] = updated
        notifyListeners(updated)
    }

    fun getSession(trackId: Long): ProgressiveSession? = sessions[trackId]

    fun findSession(trackId: Long, title: String, artist: String): ProgressiveSession? {
        return sessions[trackId] ?: sessions.values.firstOrNull {
            it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
        }
    }

    fun removeSession(trackId: Long) {
        sessions.remove(trackId)
    }

    fun addListener(listener: (ProgressiveSession) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ProgressiveSession) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(session: ProgressiveSession) {
        listeners.forEach { listener ->
            try {
                listener(session)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ProgressiveRegistry", "Listener failed: ${e.message}")
            }
        }
    }
}
