package com.anuj.obsidian.data.remote

import android.util.Log
import com.anuj.obsidian.data.model.ExtensionRegistryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object ExtensionRegistryService {
    private const val TAG = "ExtensionRegistry"
    const val REGISTRY_URL = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/registry.json"

    // In-memory cache with 10-minute TTL to avoid redundant network hits on every recomposition
    @Volatile
    private var cachedEntries: List<ExtensionRegistryEntry> = emptyList()
    @Volatile
    private var lastFetchTimestamp: Long = 0L
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    /**
     * Fetches the official SpotiFLAC Extensions Registry.
     * Uses in-memory caching unless forceRefresh is true or TTL expired.
     */
    suspend fun fetchRegistry(forceRefresh: Boolean = false): List<ExtensionRegistryEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedEntries.isNotEmpty() && (now - lastFetchTimestamp) < CACHE_TTL_MS) {
            return@withContext cachedEntries
        }

        try {
            val request = Request.Builder()
                .url(REGISTRY_URL)
                .header("User-Agent", "SpotiFLAC-Extension-Client/1.0 (Android; Obsidian)")
                .build()

            MusicApiService.apiClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext cachedEntries
                    val root = JSONObject(body)
                    val extensionsArray = root.optJSONArray("extensions") ?: return@withContext cachedEntries

                    val entries = mutableListOf<ExtensionRegistryEntry>()
                    for (i in 0 until extensionsArray.length()) {
                        val obj = extensionsArray.getJSONObject(i)
                        val id = obj.optString("id", "").trim()
                        if (id.isBlank()) continue

                        val name = obj.optString("name", id)
                        val displayName = obj.optString("display_name", name)
                        val version = obj.optString("version", "1.0.0")
                        val description = obj.optString("description", "")
                        val downloadUrl = obj.optString("download_url", "")
                        val sha256 = obj.optString("sha256", "")
                        val iconUrl = if (obj.has("icon_url") && !obj.isNull("icon_url")) {
                            val rawIcon = obj.optString("icon_url").trim()
                            if (rawIcon.isNotBlank()) rawIcon else null
                        } else null
                        val category = obj.optString("category", "integration")
                        val tagsArray = obj.optJSONArray("tags")
                        val tags = if (tagsArray != null) {
                            List(tagsArray.length()) { tagsArray.getString(it) }
                        } else emptyList()
                        val downloads = obj.optInt("downloads", 0)
                        val updatedAt = obj.optString("updated_at", "")
                        val minAppVersion = obj.optString("min_app_version", "")

                        entries.add(
                            ExtensionRegistryEntry(
                                id = id,
                                name = name,
                                displayName = displayName,
                                version = version,
                                description = description,
                                downloadUrl = downloadUrl,
                                sha256 = sha256,
                                iconUrl = iconUrl,
                                category = category,
                                tags = tags,
                                downloads = downloads,
                                updatedAt = updatedAt,
                                minAppVersion = minAppVersion
                            )
                        )
                    }

                    if (entries.isNotEmpty()) {
                        cachedEntries = entries
                        lastFetchTimestamp = now
                    }
                    return@withContext cachedEntries
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch extension registry: ${e.message}", e)
        }

        if (cachedEntries.isEmpty()) {
            cachedEntries = com.anuj.obsidian.data.local.AppDatabase.DEFAULT_EXTENSIONS.map { ext ->
                ExtensionRegistryEntry(
                    id = ext.id,
                    name = ext.name,
                    displayName = ext.name,
                    version = ext.version,
                    description = ext.description,
                    downloadUrl = ext.downloadUrl,
                    category = if (ext.capabilities.contains("lyrics")) "lyrics" else if (ext.capabilities.contains("metadata")) "metadata" else "provider",
                    tags = ext.capabilities.split(",").map { it.trim() },
                    downloads = 1000 + ext.name.hashCode().mod(5000).let { if (it < 0) -it else it },
                    updatedAt = "2024-10-01",
                    minAppVersion = "1.0.0"
                )
            }
        }

        return@withContext cachedEntries
    }

    fun getCachedRegistry(): List<ExtensionRegistryEntry> {
        if (cachedEntries.isEmpty()) {
            return com.anuj.obsidian.data.local.AppDatabase.DEFAULT_EXTENSIONS.map { ext ->
                ExtensionRegistryEntry(
                    id = ext.id,
                    name = ext.name,
                    displayName = ext.name,
                    version = ext.version,
                    description = ext.description,
                    downloadUrl = ext.downloadUrl,
                    category = if (ext.capabilities.contains("lyrics")) "lyrics" else if (ext.capabilities.contains("metadata")) "metadata" else "provider",
                    tags = ext.capabilities.split(",").map { it.trim() },
                    downloads = 1000 + ext.name.hashCode().mod(5000).let { if (it < 0) -it else it },
                    updatedAt = "2024-10-01",
                    minAppVersion = "1.0.0"
                )
            }
        }
        return cachedEntries
    }
}
