package com.anuj.obsidian.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anuj.obsidian.data.model.DownloadedTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM downloaded_tracks ORDER BY dateAddedTimestamp DESC")
    fun getAllTracks(): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloaded_tracks")
    suspend fun getAllTracksList(): List<DownloadedTrack>

    @Query("SELECT * FROM downloaded_tracks WHERE isFavorite = 1 ORDER BY dateAddedTimestamp DESC")
    fun getFavoriteTracks(): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloaded_tracks WHERE bitDepth = '24-bit' ORDER BY dateAddedTimestamp DESC")
    fun getHiResTracks(): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloaded_tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): DownloadedTrack?

    @Query("SELECT * FROM downloaded_tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchTracks(query: String): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloaded_tracks WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchTracksByTitle(query: String): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloaded_tracks WHERE artist LIKE '%' || :query || '%' ORDER BY artist ASC, title ASC")
    fun searchTracksByArtist(query: String): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloaded_tracks WHERE album LIKE '%' || :query || '%' ORDER BY album ASC, trackNumber ASC")
    fun searchTracksByAlbum(query: String): Flow<List<DownloadedTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: DownloadedTrack): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<DownloadedTrack>)

    @Query("UPDATE downloaded_tracks SET lyrics = :lyrics WHERE id = :id")
    suspend fun updateLyrics(id: Long, lyrics: String)

        @Query("SELECT * FROM downloaded_tracks WHERE tagsLoaded = 0 OR bitDepth = 'UNKNOWN' OR sampleRate = 'UNKNOWN' LIMIT 100")
    suspend fun getTracksNeedingTagSync(): List<DownloadedTrack>

    @Update
    suspend fun updateTrack(track: DownloadedTrack)

    @Delete
    suspend fun deleteTrack(track: DownloadedTrack)

    @Query("DELETE FROM downloaded_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("DELETE FROM downloaded_tracks WHERE localFilePath = :filePath")
    suspend fun deleteTrackByPath(filePath: String)

    @Query("UPDATE downloaded_tracks SET bitDepth = '' WHERE LOWER(format) IN ('m4a', 'aac', 'mp3', 'ogg', 'opus', '3gp')")
    suspend fun clearLossyBitDepths()

    @Query("UPDATE downloaded_tracks SET sampleRate = '44.1 kHz', bitDepth = '' WHERE (LOWER(format) = 'm4a' OR LOWER(format) = 'aac' OR LOWER(format) = 'mp3' OR LOWER(format) = 'ogg' OR LOWER(format) = 'opus') AND (sampleRate LIKE '%96%' OR sampleRate LIKE '%192%')")
    suspend fun sanitizeFakeSampleRates()

    @Query("UPDATE downloaded_tracks SET sampleRate = '44.1 kHz' WHERE sampleRate = 'UNKNOWN' OR sampleRate = ''")
    suspend fun sanitizeUnknownSampleRates()

    @Query("UPDATE downloaded_tracks SET bitDepth = '16-bit' WHERE (bitDepth = 'UNKNOWN' OR bitDepth = '') AND (LOWER(format) = 'flac' OR LOWER(format) = 'alac' OR LOWER(format) = 'wav')")
    suspend fun sanitizeUnknownBitDepths()

    @Query("SELECT COUNT(*) FROM downloaded_tracks")
    fun getTrackCount(): Flow<Int>
}
