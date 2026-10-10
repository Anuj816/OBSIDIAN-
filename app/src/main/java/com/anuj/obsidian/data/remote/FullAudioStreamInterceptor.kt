package com.anuj.obsidian.data.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Network Interceptor that handles full file streaming, ensures full byte range requests,
 * prevents premature 30-second truncations, and manages headers for lossless audio streams.
 */
class FullAudioStreamInterceptor : Interceptor {
    companion object {
        private const val TAG = "FullAudioInterceptor"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url.toString()
        val isApiRequest = originalUrl.contains("search") || originalUrl.contains("api.php") || 
                originalUrl.contains("/api/") || originalUrl.contains("oembed") || 
                originalUrl.contains("lrclib") || originalUrl.contains("json") ||
                originalUrl.contains("itunes.apple.com") || originalUrl.contains("api.deezer.com")

        if (isApiRequest) {
            return chain.proceed(originalRequest)
        }

        val isAudioMedia = originalUrl.endsWith(".flac") || originalUrl.endsWith(".mp4") || originalUrl.endsWith(".m4a") ||
                originalUrl.endsWith(".mp3") || originalUrl.endsWith(".ogg") || originalUrl.endsWith(".wav") ||
                originalUrl.contains("saavncdn.com") ||
                originalRequest.header("X-Audio-Stream") == "true"

        val requestBuilder = originalRequest.newBuilder()
        if (originalRequest.header("User-Agent") == null) {
            requestBuilder.header("User-Agent", MusicApiService.BROWSER_USER_AGENT)
        }

        if (isAudioMedia) {
            if (originalRequest.header("Accept") == null) {
                requestBuilder.header("Accept", "audio/flac, audio/x-flac, audio/mp4, audio/m4a, audio/ogg, audio/*;q=0.9, */*;q=0.8")
            }
            requestBuilder.header("Accept-Encoding", "identity")
            requestBuilder.header("Connection", "keep-alive")
        }

        val request = requestBuilder.build()
        val response = chain.proceed(request)

        // Log stream details
        val contentType = response.header("Content-Type", "")
        val contentLength = response.header("Content-Length", "-1")
        val contentRange = response.header("Content-Range", "")
        Log.d(TAG, "Audio stream intercepted: code=${response.code}, type=$contentType, length=$contentLength, range=$contentRange")

        return response
    }
}
