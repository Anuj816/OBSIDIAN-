package com.anuj.obsidian.data.remote.providers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * =========================================================================================
 * Lyrics Provider (LRCLIB & Lyrist Mirror Integration)
 * =========================================================================================
 * RESPONSIBILITY:
 * Fetches time-synced LRC lyrics and plain lyrics for current playback and embeds them
 * or delivers them to the UI LyricsSheet.
 * =========================================================================================
 */
object LyricsProvider {
    private const val TAG = "LyricsProvider"
    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * Fetches synced (LRC) lyrics or falls back to plain timed lyrics from LRCLIB and mirrors.
     */
    suspend fun fetchLyrics(
        client: OkHttpClient,
        title: String,
        artist: String,
        durationSeconds: Int = 0
    ): String = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext ""
        
        // 1. Primary: Use MusicApiService multi-provider engine (LRCLIB, NetEase, KuGou, JioSaavn, Lyrist, etc.)
        val musicApiLyrics = try {
            com.anuj.obsidian.data.remote.MusicApiService.fetchLyrics(
                title = title,
                artist = artist,
                durationSeconds = durationSeconds
            )
        } catch (_: Throwable) {
            ""
        }
        if (musicApiLyrics.isNotBlank()) return@withContext musicApiLyrics

        val cleanTitle = title.replace("\\(.*?\\)|\\[.*?\\]|-.*$".toRegex(), "").trim()
        val cleanArtist = artist.replace("\\(.*?\\)|\\[.*?\\]".toRegex(), "").trim()

        // 2. Direct Fallback: Try LRCLIB official API
        val lrclibLyrics = fetchLrclib(client, cleanTitle, cleanArtist, durationSeconds)
        if (lrclibLyrics.isNotBlank()) return@withContext lrclibLyrics

        // 3. Direct Fallback: Try Lyrist mirror
        val lyristLyrics = fetchLyrist(client, cleanTitle, cleanArtist, durationSeconds)
        if (lyristLyrics.isNotBlank()) return@withContext lyristLyrics

        ""
    }

    private suspend fun fetchLrclib(
        client: OkHttpClient,
        title: String,
        artist: String,
        durationSeconds: Int
    ): String {
        return try {
            withTimeoutOrNull(4000L) {
                val encTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                val encArtist = URLEncoder.encode(artist, StandardCharsets.UTF_8.toString())
                val durParam = if (durationSeconds > 0) "&duration=$durationSeconds" else ""
                val url = "https://lrclib.net/api/get?track_name=$encTitle&artist_name=$encArtist$durParam"

                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "ObsidianObsidian/1.0 (https://github.com/anuj/obsidian)")
                    .build()

                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null && body.trim().startsWith("{")) {
                            val json = JSONObject(body)
                            val synced = json.optString("syncedLyrics", "")
                            if (synced.isNotBlank()) return@use synced
                            val plain = json.optString("plainLyrics", "")
                            if (plain.isNotBlank()) return@use convertPlainToTimed(plain, durationSeconds)
                        }
                    }
                    ""
                }
            } ?: ""
        } catch (e: Exception) {
            Log.d(TAG, "LRCLIB note: ${e.message}")
            ""
        }
    }

    private suspend fun fetchLyrist(
        client: OkHttpClient,
        title: String,
        artist: String,
        durationSeconds: Int
    ): String {
        return try {
            withTimeoutOrNull(3500L) {
                val encTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                val encArtist = URLEncoder.encode(artist, StandardCharsets.UTF_8.toString())
                val url = "https://lyrist.vercel.app/api/$encTitle/$encArtist"

                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()

                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null && body.trim().startsWith("{")) {
                            val json = JSONObject(body)
                            val lyrics = json.optString("lyrics", "")
                            if (lyrics.isNotBlank()) {
                                return@use convertPlainToTimed(lyrics, durationSeconds)
                            }
                        }
                    }
                    ""
                }
            } ?: ""
        } catch (e: Exception) {
            Log.d(TAG, "Lyrist note: ${e.message}")
            ""
        }
    }

    fun convertPlainToTimed(plain: String, totalDurationSec: Int): String {
        val lines = plain.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return plain
        val dur = if (totalDurationSec > 0) totalDurationSec.toFloat() else 180f
        val interval = (dur * 0.85f) / lines.size.coerceAtLeast(1)
        val sb = StringBuilder()
        lines.forEachIndexed { index, line ->
            val time = (5f + index * interval).coerceAtMost(dur - 2f)
            val min = (time / 60).toInt()
            val sec = (time % 60).toInt()
            val frac = ((time - time.toInt()) * 100).toInt()
            sb.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d] %s\n", min, sec, frac, line.trim()))
        }
        return sb.toString()
    }
}
