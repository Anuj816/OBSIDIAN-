package com.anuj.obsidian.data.model

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.anuj.obsidian.data.local.FastAlbumArtHelper
import com.anuj.obsidian.util.AlacAudioDecoder
import java.io.File

/**
 * Authoritative central source resolver for converting TrackLocation and DownloadedTrack
 * into playable Uris and Media3 MediaItems.
 */
object TrackSourceResolver {

    fun resolveUri(context: Context, location: TrackLocation, track: DownloadedTrack): Uri {
        return when (location) {
            is TrackLocation.MediaStore -> location.contentUri
            is TrackLocation.SAF -> location.safUri
            is TrackLocation.LocalFile -> {
                val file = File(location.absolutePath)
                if (file.exists()) {
                    Uri.fromFile(file)
                } else if (track.localFilePath.isNotBlank() && File(track.localFilePath).exists()) {
                    Uri.fromFile(File(track.localFilePath))
                } else {
                    Uri.EMPTY
                }
            }
            is TrackLocation.AppPrivateFile -> {
                val appFile = File(context.filesDir, location.relativePath)
                if (appFile.exists()) {
                    Uri.fromFile(appFile)
                } else {
                    val cacheFile = File(context.cacheDir, location.relativePath)
                    if (cacheFile.exists()) Uri.fromFile(cacheFile) else Uri.EMPTY
                }
            }
            is TrackLocation.RemoteUri -> {
                Uri.parse(location.url)
            }
        }
    }

    fun resolveMediaItem(
        context: Context,
        location: TrackLocation,
        track: DownloadedTrack,
        expectedMediaId: String = track.id.toString()
    ): MediaItem {
        val uri = resolveUri(context, location, track)
        val directPath = FastAlbumArtHelper.resolveArtworkPathSync(context, track)
        val resolvedArtUri = when {
            directPath.startsWith("content://") || directPath.startsWith("http://") || directPath.startsWith("https://") -> directPath
            directPath.startsWith("/") -> "file://$directPath"
            directPath.startsWith("file://") -> directPath
            track.albumArtUrl.startsWith("http://") || track.albumArtUrl.startsWith("https://") -> track.albumArtUrl
            track.albumArtUrl.startsWith("content://") -> track.albumArtUrl
            track.albumArtUrl.startsWith("file://") -> track.albumArtUrl
            track.albumArtUrl.startsWith("/") -> "file://${track.albumArtUrl}"
            else -> "obsidian://art?trackId=${track.id}&art=${Uri.encode(track.albumArtUrl)}&path=${Uri.encode(track.localFilePath)}"
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)

        try {
            metadataBuilder.setArtworkUri(Uri.parse(resolvedArtUri))
        } catch (_: Exception) {}

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(expectedMediaId)
            .setMediaMetadata(metadataBuilder.build())
            .setTag(track)
            .build()
    }

    fun resolveMediaItem(
        context: Context,
        queueEntry: QueueEntry
    ): MediaItem {
        return resolveMediaItem(
            context = context,
            location = queueEntry.location,
            track = queueEntry.track,
            expectedMediaId = queueEntry.entryId
        )
    }
}
