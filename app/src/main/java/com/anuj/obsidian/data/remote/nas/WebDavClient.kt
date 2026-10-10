package com.anuj.obsidian.data.remote.nas

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * WebDAV Client for Nextcloud, ownCloud, Synology, TrueNAS, and standard WebDAV audio shares.
 */
class WebDavClient(
    val serverUrl: String,
    val username: String = "",
    val password: String = ""
) {
    private fun cleanBaseUrl(): String {
        var u = serverUrl.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        if (u.endsWith("/")) {
            u = u.substring(0, u.length - 1)
        }
        return u
    }

    private fun getBasicAuthHeader(): String? {
        if (username.isBlank() && password.isBlank()) return null
        val creds = "$username:$password"
        val encoded = Base64.encodeToString(creds.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = cleanBaseUrl()
            val conn = URL(base).openConnection() as HttpURLConnection
            conn.requestMethod = "PROPFIND"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Depth", "0")
            getBasicAuthHeader()?.let {
                conn.setRequestProperty("Authorization", it)
            }

            val code = conn.responseCode
            if (code in 200..299 || code == 207) {
                Result.success("WebDAV Server connected successfully (HTTP $code)")
            } else if (code == 401) {
                Result.failure(Exception("Authentication required (HTTP 401 Unauthorized)"))
            } else {
                Result.failure(Exception("WebDAV server returned HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
