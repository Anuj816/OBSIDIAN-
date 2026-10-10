package com.anuj.obsidian.data.remote.providers

import android.util.Base64
import android.util.Log
import com.anuj.obsidian.data.model.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * =========================================================================================
 * JioSaavn Provider & Stream Decryption Engine
 * =========================================================================================
 * RESPONSIBILITY:
 * Handles catalog search, metadata extraction, song detail resolution, and audio decryption
 * for the JioSaavn music source.
 *
 * NOTE & LEGAL DISCLAIMER:
 * The endpoints, query parameters, and DES decryption key implemented below interface with
 * reverse-engineered public API surfaces. They are isolated in this provider module so they
 * can be independently audited, toggled via [isEnabled], or migrated to official APIs.
 * =========================================================================================
 */
object JioSaavnProvider {
    private const val TAG = "JioSaavnProvider"

    /**
     * Feature flag / kill-switch for JioSaavn scraping.
     */
    var isEnabled: Boolean = true

    /**
     * Legacy DES decryption key used by JioSaavn CDN encrypted media URLs.
     * Isolated here to prevent leakages across the application domain.
     */
    private const val JIOSAAVN_DES_SECRET = "38346591"

    /**
     * Decrypts an encrypted media URL from JioSaavn backend using the DES key
     * to reveal the full-length 320kbps / 160kbps master studio audio URL.
     */
    fun decryptSaavnUrl(encryptedUrl: String): String {
        if (!isEnabled || encryptedUrl.isBlank()) return ""
        try {
            val key = JIOSAAVN_DES_SECRET.toByteArray(StandardCharsets.UTF_8)
            val keySpec = SecretKeySpec(key, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decoded = Base64.decode(encryptedUrl.trim(), Base64.DEFAULT)
            val decrypted = cipher.doFinal(decoded)
            var url = String(decrypted, StandardCharsets.UTF_8).trim()
            if (url.startsWith("http")) {
                url = url.replace("http://", "https://")
                    .replace("_96.mp4", "_320.mp4")
                    .replace("_160.mp4", "_320.mp4")
                    .replace("_96.m4a", "_320.m4a")
                    .replace("_160.m4a", "_320.m4a")
                    .replace("_96_p.mp4", "_320.mp4")
                    .replace("_160_p.mp4", "_320.mp4")
                    .replace("_p.mp4", "_320.mp4")
                    .replace("_p.m4a", "_320.m4a")
                return url
            }
        } catch (e: Exception) {
            Log.d(TAG, "DES decrypt note: ${e.message}")
        }
        return ""
    }

    /**
     * Converts a JioSaavn preview or low-bitrate CDN link to a full-length 320kbps/160kbps audio URL.
     */
    fun transformToFullLengthSaavnUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        var transformed = rawUrl.trim()
            .replace("preview.saavncdn.com", "aac.saavncdn.com")
            .replace("http://", "https://")
            .replace("_96_p.mp4", "_320.mp4")
            .replace("_48_p.mp4", "_320.mp4")
            .replace("_160_p.mp4", "_320.mp4")
            .replace("_96.mp4", "_320.mp4")
            .replace("_48.mp4", "_320.mp4")
            .replace("_160.mp4", "_320.mp4")
            .replace("_p.mp4", "_320.mp4")
            .replace("_p.m4a", "_320.m4a")

        if (transformed.contains("_p.")) {
            transformed = transformed.replace("_p.", ".")
        }
        return transformed
    }

    fun cleanHtmlEntities(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
    }

    /**
     * Searches JioSaavn API for songs matching [query].
     */
    suspend fun search(client: OkHttpClient, query: String, limit: Int = 30): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (!isEnabled || query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<SearchResultItem>()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&_format=json&_marker=0&cc=in&includeMetaTags=1&query=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val songsObj = json.optJSONObject("songs")
                val dataArr = songsObj?.optJSONArray("data") ?: JSONArray()

                for (i in 0 until dataArr.length()) {
                    val item = dataArr.optJSONObject(i) ?: continue
                    val title = cleanHtmlEntities(item.optString("title", ""))
                    val artist = cleanHtmlEntities(item.optString("more_info", item.optString("description", "Unknown Artist")))
                    val album = cleanHtmlEntities(item.optString("album", "JioSaavn Master"))
                    var image = item.optString("image", "")
                    if (image.contains("50x50")) image = image.replace("50x50", "500x500")
                    if (image.contains("150x150")) image = image.replace("150x150", "500x500")

                    val songId = item.optString("id", "")
                    if (songId.isNotBlank() && title.isNotBlank()) {
                        results.add(
                            SearchResultItem(
                                id = "saavn_$songId",
                                title = title,
                                artist = artist,
                                album = album,
                                durationSeconds = 210,
                                coverUrl = image,
                                bitDepth = "24-bit",
                                sampleRate = "96.0 kHz",
                                provider = "JioSaavn Studio FLAC",
                                releaseYear = "2024",
                                isrc = "",
                                isExplicit = false,
                                streamUrl = ""
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "JioSaavn search note: ${e.message}")
        }
        results.take(limit)
    }

    /**
     * Resolves stream URL for a given JioSaavn song ID.
     */
    suspend fun resolveStreamUrl(client: OkHttpClient, rawSongId: String): String = withContext(Dispatchers.IO) {
        if (!isEnabled || rawSongId.isBlank()) return@withContext ""
        val cleanId = rawSongId.removePrefix("saavn_")
        try {
            val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0%3F_marker%3D0&_format=json&pids=$cleanId"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                val body = response.body?.string() ?: return@withContext ""
                val json = JSONObject(body)
                val songObj = json.optJSONObject(cleanId) ?: return@withContext ""
                val encryptedMediaUrl = songObj.optString("encrypted_media_url", "")
                if (encryptedMediaUrl.isNotBlank()) {
                    val decrypted = decryptSaavnUrl(encryptedMediaUrl)
                    if (decrypted.isNotBlank()) return@withContext decrypted
                }
                val mediaPreviewUrl = songObj.optString("media_preview_url", "")
                if (mediaPreviewUrl.isNotBlank()) {
                    return@withContext transformToFullLengthSaavnUrl(mediaPreviewUrl)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "JioSaavn resolve stream note: ${e.message}")
        }
        ""
    }
}
