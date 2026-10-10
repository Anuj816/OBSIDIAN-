package com.anuj.obsidian.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anuj.obsidian.data.model.TrackListeningStat
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningStatsDao {
    @Query("SELECT * FROM track_listening_stats WHERE trackId = :trackId")
    suspend fun getStatForTrack(trackId: Long): TrackListeningStat?

    @Query("SELECT * FROM track_listening_stats ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 100): Flow<List<TrackListeningStat>>

    @Query("SELECT * FROM track_listening_stats ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 100): Flow<List<TrackListeningStat>>

    @Query("SELECT * FROM track_listening_stats")
    fun getAllStats(): Flow<List<TrackListeningStat>>

    @Query("""
        SELECT t.* FROM downloaded_tracks t
        INNER JOIN track_listening_stats s ON t.id = s.trackId
        ORDER BY s.lastPlayedTimestamp DESC
        LIMIT :limit
    """)
    fun getRecentlyPlayedTracks(limit: Int = 100): Flow<List<com.anuj.obsidian.data.model.DownloadedTrack>>

    @Query("""
        SELECT t.* FROM downloaded_tracks t
        INNER JOIN track_listening_stats s ON t.id = s.trackId
        WHERE s.lastPlayedTimestamp >= :sinceTimestampMs
        ORDER BY s.playCount DESC
        LIMIT :limit
    """)
    fun getOnRepeatTracks(sinceTimestampMs: Long, limit: Int = 100): Flow<List<com.anuj.obsidian.data.model.DownloadedTrack>>

    @Query("""
        SELECT t.* FROM downloaded_tracks t
        INNER JOIN track_listening_stats s ON t.id = s.trackId
        WHERE s.playCount >= 2 AND s.lastPlayedTimestamp <= :beforeTimestampMs
        ORDER BY s.playCount DESC
        LIMIT :limit
    """)
    fun getForgottenFavoritesTracks(beforeTimestampMs: Long, limit: Int = 100): Flow<List<com.anuj.obsidian.data.model.DownloadedTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stat: TrackListeningStat)

    @Query("UPDATE track_listening_stats SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE trackId = :trackId")
    suspend fun incrementPlayCount(trackId: Long, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM track_listening_stats WHERE trackId = :trackId")
    suspend fun deleteStatForTrack(trackId: Long)
}
