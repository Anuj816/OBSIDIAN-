package com.anuj.obsidian.data.local

import android.content.Context
import android.net.Uri
import coil.intercept.Interceptor
import coil.request.ImageResult
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.SearchResultItem
import java.io.File
import kotlinx.coroutines.launch

class TrackArtworkInterceptor(private val context: Context) : Interceptor {

    companion object {
        private val requestCounter = java.util.concurrent.atomic.AtomicInteger(0)
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // Enforce 500 MB auto reset guard periodically during online artwork fetching
        if (requestCounter.incrementAndGet() % 30 == 0) {
            com.anuj.obsidian.ObsidianApplication.applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.anuj.obsidian.util.ObsidianStorageCleanupManager.checkAndEnforceOnlineArtworkLimit(context)
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("TrackArtworkInterceptor", "Storage cleanup failed: ${e.message}")
                }
            }
        }

        val request = chain.request
        val data = request.data
        
        if (data is DownloadedTrack) {
            val artUrl = data.albumArtUrl.trim()
            val isPicsum = artUrl.contains("picsum.photos") || artUrl == "NO_ART"
            val effectiveArt = if (isPicsum) "" else artUrl

            // Prefer local embedded / cached art first when local file exists
            val localResolved = if (data.localFilePath.isNotBlank()) {
                FastAlbumArtHelper.resolveArtworkPathSync(context, data)
            } else ""

            val finalData: Any = when {
                localResolved.isNotBlank() && !localResolved.contains("picsum.photos") && !localResolved.startsWith("http") -> {
                    if (localResolved.startsWith("content://")) {
                        Uri.parse(localResolved)
                    } else {
                        val path = if (localResolved.startsWith("file://")) Uri.parse(localResolved).path ?: "" else localResolved
                        val f = File(path)
                        if (f.exists() && f.length() > 64L) f else ""
                    }
                }
                effectiveArt.isNotBlank() -> {
                    if (effectiveArt.startsWith("/") || effectiveArt.startsWith("file://")) {
                        val path = if (effectiveArt.startsWith("file://")) Uri.parse(effectiveArt).path ?: "" else effectiveArt
                        val f = File(path)
                        if (f.exists() && f.length() > 64L) f else ""
                    } else if (effectiveArt.startsWith("content://")) {
                        Uri.parse(effectiveArt)
                    } else {
                        effectiveArt
                    }
                }
                localResolved.isNotBlank() && !localResolved.contains("picsum.photos") -> {
                    localResolved
                }
                else -> {
                    ""
                }
            }
            
            val newRequest = request.newBuilder().data(finalData).build()
            return chain.proceed(newRequest)
        } else if (data is SearchResultItem) {
            val finalCover = if (data.coverUrl.contains("picsum.photos")) "" else data.coverUrl
            val newRequest = request.newBuilder().data(finalCover).build()
            return chain.proceed(newRequest)
        } else if (data is String && (data.isBlank() || data.contains("picsum.photos") || data == "NO_ART")) {
            val newRequest = request.newBuilder().data("").build()
            return chain.proceed(newRequest)
        } else if (data is String && data.startsWith("/")) {
            val file = File(data)
            val finalData: Any = if (file.exists() && file.length() > 64L) file else ""
            val newRequest = request.newBuilder().data(finalData).build()
            return chain.proceed(newRequest)
        }
        
        return chain.proceed(request)
    }
}
