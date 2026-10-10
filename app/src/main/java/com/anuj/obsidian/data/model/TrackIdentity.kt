package com.anuj.obsidian.data.model

import android.net.Uri
import java.util.UUID

/**
 * =========================================================================================
 * OBSIDIAN SPEC-COMPLIANT TRACK IDENTITY & LOCATION ABSTRACTION MODEL
 * =========================================================================================
 * Separates persistent logical identity from physical storage location and queue instances.
 * Follows Parts 2, 3, and 15 of Obsidian Master Architecture Specification.
 * =========================================================================================
 */

/**
 * Logical identity of a track.
 * Distinct tracks with same title/artist (e.g. different album/release/master/file) co-exist safely.
 */
data class TrackIdentity(
    val dbId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val localFilePath: String = "",
    val isrc: String? = null,
    val providerSource: String? = null,
    val format: String? = null
) {
    val stableKey: String
        get() {
            val pathKey = localFilePath.trim().lowercase()
            val providerKey = providerSource?.trim()?.lowercase() ?: "local"
            val isrcKey = isrc?.trim()?.lowercase() ?: ""
            val formatKey = format?.trim()?.lowercase() ?: ""
            return when {
                pathKey.startsWith("content://") -> "uri:$pathKey"
                pathKey.isNotBlank() -> "path:$pathKey|fmt:$formatKey"
                providerKey != "local" && isrcKey.isNotBlank() -> "prov:$providerKey|isrc:$isrcKey"
                providerKey != "local" -> "prov:$providerKey|title:${title.trim().lowercase()}|artist:${artist.trim().lowercase()}|album:${album.trim().lowercase()}"
                else -> "meta:${title.trim().lowercase()}|${artist.trim().lowercase()}|${album.trim().lowercase()}"
            }
        }
}

/**
 * Explicit physical/network storage location abstraction.
 */
sealed class TrackLocation {
    data class MediaStore(val mediaStoreId: Long, val contentUri: Uri) : TrackLocation()
    data class SAF(val safUri: Uri) : TrackLocation()
    data class LocalFile(val absolutePath: String) : TrackLocation()
    data class AppPrivateFile(val relativePath: String) : TrackLocation()
    data class RemoteUri(val url: String) : TrackLocation()
}

/**
 * Uniquely addressable queue item instance.
 * Allows duplicate tracks in a queue to be independently managed via entryId.
 */
data class QueueEntry(
    val entryId: String = UUID.randomUUID().toString(),
    val trackIdentity: TrackIdentity,
    val location: TrackLocation,
    val track: DownloadedTrack,
    val addedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Extension to resolve a DownloadedTrack into its TrackIdentity and TrackLocation safely.
 */
fun DownloadedTrack.toTrackIdentity(): TrackIdentity {
    return TrackIdentity(
        dbId = id,
        title = title,
        artist = artist,
        album = album,
        localFilePath = localFilePath,
        isrc = if (isrc.isNotBlank()) isrc else null,
        providerSource = if (providerSource.isNotBlank()) providerSource else null,
        format = if (format.isNotBlank()) format else null
    )
}

fun DownloadedTrack.toTrackLocation(): TrackLocation {
    val path = localFilePath.trim()
    return when {
        path.startsWith("content://") -> {
            val uri = Uri.parse(path)
            if (path.contains("media", ignoreCase = true)) {
                val mediaId = uri.lastPathSegment?.toLongOrNull() ?: id
                TrackLocation.MediaStore(mediaId, uri)
            } else {
                TrackLocation.SAF(uri)
            }
        }
        path.startsWith("http://") || path.startsWith("https://") -> {
            TrackLocation.RemoteUri(path)
        }
        path.isNotBlank() -> {
            TrackLocation.LocalFile(path)
        }
        else -> {
            TrackLocation.AppPrivateFile("track_${id}.audio")
        }
    }
}

fun DownloadedTrack.toQueueEntry(): QueueEntry {
    return QueueEntry(
        entryId = "q_${id}_${UUID.randomUUID().toString().take(8)}",
        trackIdentity = toTrackIdentity(),
        location = toTrackLocation(),
        track = this
    )
}
