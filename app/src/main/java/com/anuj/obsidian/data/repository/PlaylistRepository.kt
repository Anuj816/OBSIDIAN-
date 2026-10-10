package com.anuj.obsidian.data.repository

import com.anuj.obsidian.data.local.PlaylistDao
import com.anuj.obsidian.data.model.Playlist
import com.anuj.obsidian.data.model.PlaylistTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * PlaylistRepository: Manages playlist CRUD, playlist tracks ordering, adding, and removing.
 */
class PlaylistRepository(
    private val playlistDao: PlaylistDao
) {
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<Long>> {
        return playlistDao.getTrackIdsForPlaylist(playlistId)
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(Playlist(name = name, description = description))
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylistTrack(PlaylistTrack(playlistId = playlistId, trackId = trackId))
    }

    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistTracks(playlistId)
        val startOrder = existing.maxOfOrNull { it.orderIndex }?.plus(1) ?: 0
        trackIds.forEachIndexed { index, trackId ->
            playlistDao.insertPlaylistTrack(
                PlaylistTrack(
                    playlistId = playlistId,
                    trackId = trackId,
                    orderIndex = startOrder + index
                )
            )
        }
    }

    suspend fun reorderPlaylistTracks(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val list = playlistDao.getPlaylistTracks(playlistId).toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            val updated = list.mapIndexed { idx, pt -> pt.copy(orderIndex = idx) }
            playlistDao.clearPlaylistTracks(playlistId)
            playlistDao.insertPlaylistTracks(updated)
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }
}
