package com.anuj.obsidian.data.remote.nas

import android.util.Log
import com.anuj.obsidian.data.model.DownloadedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Random

/**
 * Subsonic / OpenSubsonic / Navidrome / Jellyfin / Gonic Client.
 * Implements OpenSubsonic REST API with token+salt MD5 authentication.
 */
class SubsonicClient(
    val serverUrl: String,
    val username: String,
    val tokenOrPassword: String,
    val isPlainPassword: Boolean = false
) {
    private val clientVersion = "1.16.1"
    private val appName = "ObsidianAudio"

    private fun cleanBaseUrl(): String {
        var u = serverUrl.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://$u"
        }
        if (u.endsWith("/")) {
            u = u.substring(0, u.length - 1)
        }
        return u
    }

    private fun buildAuthParams(): String {
        val u = URLEncoder.encode(username, "UTF-8")
        val v = URLEncoder.encode(clientVersion, "UTF-8")
        val c = URLEncoder.encode(appName, "UTF-8")

        return if (isPlainPassword) {
            val p = URLEncoder.encode(tokenOrPassword, "UTF-8")
            "u=$u&p=$p&v=$v&c=$c&f=json"
        } else {
            val salt = generateSalt()
            val token = md5(tokenOrPassword + salt)
            "u=$u&t=$token&s=$salt&v=$v&c=$c&f=json"
        }
    }

    private fun generateSalt(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = Random()
        val sb = StringBuilder(12)
        for (i in 0 until 12) {
            sb.append(chars[rnd.nextInt(chars.length)])
        }
        return sb.toString()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun ping(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = cleanBaseUrl()
            val endpoint = "$base/rest/ping.view?${buildAuthParams()}"
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val subResponse = json.optJSONObject("subsonic-response")
            val status = subResponse?.optString("status") ?: "failed"
            val version = subResponse?.optString("version") ?: "1.16"

            if (status == "ok") {
                Result.success("Connected (Subsonic API v$version)")
            } else {
                val error = subResponse?.optJSONObject("error")?.optString("message") ?: "Server returned status: $status"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtists(): Result<List<NasArtist>> = withContext(Dispatchers.IO) {
        try {
            val base = cleanBaseUrl()
            val endpoint = "$base/rest/getArtists.view?${buildAuthParams()}"
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val artistsArray = json.optJSONObject("subsonic-response")
                ?.optJSONObject("artists")
                ?.optJSONArray("index")

            val list = mutableListOf<NasArtist>()
            if (artistsArray != null) {
                for (i in 0 until artistsArray.length()) {
                    val indexObj = artistsArray.getJSONObject(i)
                    val arts = indexObj.optJSONArray("artist") ?: continue
                    for (j in 0 until arts.length()) {
                        val art = arts.getJSONObject(j)
                        list.add(
                            NasArtist(
                                id = art.getString("id"),
                                name = art.getString("name"),
                                albumCount = art.optInt("albumCount", 0)
                            )
                        )
                    }
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getStreamUrl(songId: String, rawLossless: Boolean = true): String {
        val base = cleanBaseUrl()
        val formatParam = if (rawLossless) "&format=raw" else ""
        return "$base/rest/stream.view?id=$songId&${buildAuthParams()}$formatParam"
    }

    fun getCoverArtUrl(coverArtId: String, size: Int = 500): String {
        val base = cleanBaseUrl()
        return "$base/rest/getCoverArt.view?id=$coverArtId&size=$size&${buildAuthParams()}"
    }
}

data class NasArtist(
    val id: String,
    val name: String,
    val albumCount: Int = 0
)

data class NasSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val trackNumber: Int = 1,
    val suffix: String = "flac",
    val bitRate: Int = 0,
    val coverArtId: String? = null
)
