package com.anuj.obsidian.data.repository

import android.content.Context
import android.util.Log
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.TrackDao
import com.anuj.obsidian.data.model.AppSettings
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.remote.MusicApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LyricsRepository: Manages lyrics retrieval from local companion .lrc files,
 * embedded audio metadata tags, and multi-provider online lyric engines.
 */
class LyricsRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val cachedLyricDao: com.anuj.obsidian.data.local.CachedLyricDao? = null,
    private val settingsStateFlow: StateFlow<AppSettings>
) {
    private fun isPlaceholderLyrics(lyrics: String): Boolean {
        if (lyrics.isBlank()) return true
        if (lyrics.contains("Streaming Master", ignoreCase = true)) return true
        if (lyrics.contains("Lossless FLAC Engine", ignoreCase = true)) return true
        if (lyrics.contains("Direct Decoding", ignoreCase = true)) return true
        if (lyrics.contains("Decentralized Stream", ignoreCase = true)) return true
        return false
    }

    private fun generateLyricKey(title: String, artist: String, localPath: String): String {
        return if (localPath.isNotBlank()) {
            "path_${localPath.hashCode()}"
        } else {
            val cleanArtist = artist.trim().lowercase().replace("[^a-z0-9]".toRegex(), "")
            val cleanTitle = title.trim().lowercase().replace("[^a-z0-9]".toRegex(), "")
            "track_${cleanArtist}_${cleanTitle}"
        }
    }

    suspend fun fetchAndSaveLyricsForTrack(track: DownloadedTrack, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val lookupKey = generateLyricKey(track.title, track.artist, track.localFilePath)

        // 0. If track already has cached lyrics in database entity AND not forcing refresh, return them unless placeholder
        if (!forceRefresh && track.lyrics.isNotBlank() && !isPlaceholderLyrics(track.lyrics)) {
            return@withContext track.lyrics
        }

        if (!forceRefresh) {
            // 1. Check dedicated CachedLyricDao table
            val dbCached = cachedLyricDao?.getLyricByKey(lookupKey)
                ?: if (track.isrc.isNotBlank()) cachedLyricDao?.getLyricByIsrc(track.isrc) else null
            if (dbCached != null && dbCached.lyrics.isNotBlank() && !isPlaceholderLyrics(dbCached.lyrics)) {
                if (track.id > 0) {
                    trackDao.updateLyrics(track.id, dbCached.lyrics)
                }
                return@withContext dbCached.lyrics
            }

            // 2. Check local companion .lrc file first if localFilePath exists
            if (track.localFilePath.isNotBlank() && !track.localFilePath.startsWith("content://")) {
                val file = File(track.localFilePath)
                if (file.exists()) {
                    val lrcFile = File(file.parentFile, file.nameWithoutExtension + ".lrc")
                    if (lrcFile.exists() && lrcFile.length() > 5) {
                        val localLrc = try {
                            lrcFile.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            ObsidianCrashLogger.logException(e, "LyricsRepository.fetchAndSaveLyricsForTrack - lrc companion")
                            ""
                        }
                        if (localLrc.isNotBlank()) {
                            if (track.id > 0) trackDao.updateLyrics(track.id, localLrc)
                            cachedLyricDao?.insertLyric(
                                com.anuj.obsidian.data.model.CachedLyric(
                                    lookupKey = lookupKey,
                                    isrc = track.isrc,
                                    lyrics = localLrc,
                                    isSynced = true,
                                    source = "companion_lrc"
                                )
                            )
                            return@withContext localLrc
                        }
                    }
                }
            }

            // 3. Check embedded lyrics tags inside the local audio file using Jaudiotagger
            if (track.localFilePath.isNotBlank()) {
                val isContentUri = track.localFilePath.startsWith("content://")
                val localFile = if (!isContentUri) File(track.localFilePath) else null
                if (isContentUri || (localFile != null && localFile.exists() && localFile.length() > 0)) {
                    try {
                        val meta = com.anuj.obsidian.data.local.JaudiotaggerHelper.readDetailedMetadata(context, track.localFilePath)
                        if (meta.lyrics.isNotBlank()) {
                            if (track.id > 0) trackDao.updateLyrics(track.id, meta.lyrics)
                            cachedLyricDao?.insertLyric(
                                com.anuj.obsidian.data.model.CachedLyric(
                                    lookupKey = lookupKey,
                                    isrc = track.isrc,
                                    lyrics = meta.lyrics,
                                    isSynced = meta.lyrics.contains("[0"),
                                    source = "embedded_tag"
                                )
                            )
                            return@withContext meta.lyrics
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (e !is java.io.FileNotFoundException && e !is SecurityException) {
                            ObsidianCrashLogger.logException(e, "LyricsRepository.fetchAndSaveLyricsForTrack - embedded tag")
                        } else {
                            Log.d("LyricsRepo", "Lyrics tag file missing or moved: ${track.localFilePath}")
                        }
                    }
                }
            }
        }

        // 4. Fall back to multi-provider online lyrics engine (LRCLIB, Netease, JioSaavn, Lyrist, Musixmatch, etc.)
        val onlineLyrics = MusicApiService.fetchLyrics(
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSeconds = track.durationSeconds
        )

        if (onlineLyrics.isNotBlank() && !isPlaceholderLyrics(onlineLyrics)) {
            if (track.id > 0) trackDao.updateLyrics(track.id, onlineLyrics)

            // Cache in dedicated lyrics table
            cachedLyricDao?.insertLyric(
                com.anuj.obsidian.data.model.CachedLyric(
                    lookupKey = lookupKey,
                    isrc = track.isrc,
                    lyrics = onlineLyrics,
                    isSynced = onlineLyrics.contains("[0"),
                    source = "online_network"
                )
            )

            // Embed fetched lyrics directly into local file if settings allow
            if (settingsStateFlow.value.embedLyrics && track.localFilePath.isNotBlank()) {
                try {
                    com.anuj.obsidian.data.local.ModernAudioTagger.embedLyrics(context, track.localFilePath, onlineLyrics)
                } catch (e: Exception) {
                    Log.w("LyricsRepo", "Failed to embed fetched lyrics: ${e.message}")
                }
            }

            // Optionally save companion .lrc if local file exists
            if (settingsStateFlow.value.generateLrcFile && track.localFilePath.isNotBlank() && !track.localFilePath.startsWith("content://")) {
                try {
                    val file = File(track.localFilePath)
                    if (file.exists() && file.parentFile != null) {
                        val companionLrc = File(file.parentFile, file.nameWithoutExtension + ".lrc")
                        companionLrc.writeText(onlineLyrics, Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    ObsidianCrashLogger.logException(e, "LyricsRepository.fetchAndSaveLyricsForTrack - save companion")
                }
            }
            return@withContext onlineLyrics
        }
        return@withContext if (track.lyrics.isNotBlank() && !isPlaceholderLyrics(track.lyrics)) track.lyrics else onlineLyrics
    }
}
