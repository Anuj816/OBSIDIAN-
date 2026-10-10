package com.anuj.obsidian.data.repository

import android.util.Log
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.AppDatabase
import com.anuj.obsidian.data.local.ExtensionDao
import com.anuj.obsidian.data.model.ExtensionItem
import com.anuj.obsidian.data.remote.MusicApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * ExtensionRepository: Manages modular streaming extensions, remote SpotiFLAC registry updates,
 * installing, enabling, disabling, and custom user extension additions.
 */
class ExtensionRepository(
    private val extensionDao: ExtensionDao
) {
    val extensions: Flow<List<ExtensionItem>> = extensionDao.getAllExtensions()

    suspend fun autoUpdateExtensions(): Int = withContext(Dispatchers.IO) {
        var updatedCount = 0

        // Fetch extensions from official zarzet SpotiFLAC-Extension registry
        try {
            val registryUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/registry.json"
            val request = Request.Builder()
                .url(registryUrl)
                .header("User-Agent", "SpotiFLAC-Mobile/4.9.0 (Android; Obsidian)")
                .build()
            val response = MusicApiService.apiClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: ""
                val json = JSONObject(jsonStr)
                val extensionsArray = json.optJSONArray("extensions") ?: JSONArray()
                val allowedIds = mutableListOf<String>()
                allowedIds.addAll(AppDatabase.DEFAULT_EXTENSIONS.map { it.id })

                for (i in 0 until extensionsArray.length()) {
                    val extObj = extensionsArray.getJSONObject(i)
                    val id = extObj.optString("id").trim()
                    if (id.isBlank()) continue
                    allowedIds.add(id)

                    val displayName = extObj.optString("display_name", extObj.optString("name", id))
                    val version = extObj.optString("version", "1.0.0")
                    val description = extObj.optString("description", "")
                    val downloadUrl = extObj.optString("download_url", "")
                    val tags = extObj.optJSONArray("tags")?.let { arr ->
                        List(arr.length()) { arr.getString(it) }.joinToString(", ")
                    } ?: ""

                    val providerName = when (id) {
                        "spotify-web" -> "Spotify Web"
                        "amazon" -> "Amazon Music"
                        "apple-music" -> "Apple Music"
                        "soundcloud" -> "SoundCloud"
                        "ytmusic-spotiflac" -> "YouTube Music"
                        "deezer" -> "Deezer"
                        "qobuz-web" -> "Qobuz"
                        "tidal-web" -> "Tidal"
                        else -> displayName
                    }

                    val existing = extensionDao.getExtensionById(id)
                    if (existing == null) {
                        extensionDao.insertExtension(
                            ExtensionItem(
                                id = id,
                                name = displayName,
                                version = version,
                                author = "zarzet",
                                description = description,
                                repoUrl = downloadUrl,
                                isEnabled = true,
                                isInstalled = true,
                                capabilities = tags,
                                pingMs = Random.nextInt(15, 45),
                                sourceProvider = providerName,
                                isOfficial = true,
                                downloadUrl = downloadUrl
                            )
                        )
                        updatedCount++
                    } else {
                        // Update to latest version from registry while ensuring all extensions are enabled and active
                        extensionDao.updateExtension(
                            existing.copy(
                                name = displayName,
                                version = version,
                                description = description,
                                repoUrl = downloadUrl,
                                capabilities = tags,
                                isEnabled = true,
                                isInstalled = true,
                                sourceProvider = providerName,
                                downloadUrl = downloadUrl
                            )
                        )
                        updatedCount++
                    }
                }

                // Strictly prune unauthorized extensions from DB so ONLY registry extensions exist
                if (allowedIds.isNotEmpty()) {
                    extensionDao.pruneExtensions(allowedIds)
                }
            }
        } catch (e: Throwable) {
            ObsidianCrashLogger.logException(e, "ExtensionRepository.autoUpdateExtensions")
            Log.e("ExtensionRepo", "Failed to fetch remote registry", e)
        }

        updatedCount
    }

    suspend fun toggleExtension(extension: ExtensionItem) = withContext(Dispatchers.IO) {
        val updated = extension.copy(isEnabled = !extension.isEnabled)
        extensionDao.updateExtension(updated)
    }

    suspend fun installExtension(extension: ExtensionItem) = withContext(Dispatchers.IO) {
        val updated = extension.copy(isInstalled = true, isEnabled = true)
        extensionDao.insertExtension(updated)
    }

    suspend fun uninstallExtension(extension: ExtensionItem) = withContext(Dispatchers.IO) {
        val updated = extension.copy(isInstalled = false, isEnabled = false)
        extensionDao.updateExtension(updated)
    }

    suspend fun addCustomExtension(name: String, repoUrl: String, author: String, description: String) = withContext(Dispatchers.IO) {
        val id = "ext.custom." + System.currentTimeMillis()
        val item = ExtensionItem(
            id = id,
            name = name,
            version = "v1.0.0",
            author = author.ifBlank { "Custom Developer" },
            description = description.ifBlank { "Decentralized community FLAC stream provider." },
            repoUrl = repoUrl,
            isEnabled = true,
            isInstalled = true,
            capabilities = "Custom Stream Engine, FLAC Vorbis",
            pingMs = Random.nextInt(20, 60),
            sourceProvider = "Decentralized Mirror",
            isOfficial = false
        )
        extensionDao.insertExtension(item)
    }
}
