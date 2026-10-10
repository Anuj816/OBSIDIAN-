package com.anuj.obsidian.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anuj.obsidian.data.model.CachedLyric
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedLyricDao {
    @Query("SELECT * FROM cached_lyrics WHERE lookupKey = :key LIMIT 1")
    suspend fun getLyricByKey(key: String): CachedLyric?

    @Query("SELECT * FROM cached_lyrics WHERE isrc = :isrc LIMIT 1")
    suspend fun getLyricByIsrc(isrc: String): CachedLyric?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyric(lyric: CachedLyric)

    @Query("DELETE FROM cached_lyrics WHERE lookupKey = :key")
    suspend fun deleteLyricByKey(key: String)

    @Query("SELECT COUNT(*) FROM cached_lyrics")
    fun getCachedCount(): Flow<Int>
}
