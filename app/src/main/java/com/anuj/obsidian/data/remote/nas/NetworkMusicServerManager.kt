package com.anuj.obsidian.data.remote.nas

import android.content.Context
import android.util.Log
import com.anuj.obsidian.data.model.DownloadedTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ServerType(val displayName: String) {
    SUBSONIC("Subsonic / Navidrome / Jellyfin"),
    WEBDAV("WebDAV / Nextcloud / Synology"),
    DIRECT_HTTP("HTTP Lossless Audio Stream")
}

data class NasServerConfig(
    val id: String,
    val name: String,
    val serverType: ServerType,
    val serverUrl: String,
    val username: String = "",
    val tokenOrPassword: String = "",
    val isEnabled: Boolean = true,
    val lastConnectedTimeMs: Long = 0L
)

object NetworkMusicServerManager {
    private const val TAG = "NetworkMusicServer"
    private const val PREFS_NAME = "obsidian_nas_servers"
    private const val KEY_SERVERS_JSON = "servers_json"

    private val _servers = MutableStateFlow<List<NasServerConfig>>(emptyList())
    val servers: StateFlow<List<NasServerConfig>> = _servers.asStateFlow()

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_SERVERS_JSON, null)
            if (!jsonStr.isNullOrBlank()) {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<NasServerConfig>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        NasServerConfig(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            serverType = ServerType.valueOf(obj.optString("serverType", ServerType.SUBSONIC.name)),
                            serverUrl = obj.getString("serverUrl"),
                            username = obj.optString("username", ""),
                            tokenOrPassword = obj.optString("tokenOrPassword", ""),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            lastConnectedTimeMs = obj.optLong("lastConnectedTimeMs", 0L)
                        )
                    )
                }
                _servers.value = list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load NAS servers: ${e.message}")
        }
    }

    fun saveServer(context: Context, server: NasServerConfig) {
        val current = _servers.value.filter { it.id != server.id } + server
        _servers.value = current
        persist(context, current)
    }

    fun removeServer(context: Context, serverId: String) {
        val current = _servers.value.filter { it.id != serverId }
        _servers.value = current
        persist(context, current)
    }

    private fun persist(context: Context, list: List<NasServerConfig>) {
        try {
            val array = JSONArray()
            list.forEach { s ->
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("serverType", s.serverType.name)
                    put("serverUrl", s.serverUrl)
                    put("username", s.username)
                    put("tokenOrPassword", s.tokenOrPassword)
                    put("isEnabled", s.isEnabled)
                    put("lastConnectedTimeMs", s.lastConnectedTimeMs)
                }
                array.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SERVERS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save NAS servers: ${e.message}")
        }
    }
}
