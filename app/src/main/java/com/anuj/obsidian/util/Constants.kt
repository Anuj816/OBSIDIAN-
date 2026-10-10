package com.anuj.obsidian.util

/**
 * =========================================================================================
 * Application-wide Constants & Intent Actions
 * =========================================================================================
 * RESPONSIBILITY:
 * Centralizes all action intent strings, notification channel IDs, preference keys,
 * and component identifiers to prevent magic string duplication and runtime mismatches.
 * =========================================================================================
 */
object Constants {
    // Package & Identifier Constants
    const val APPLICATION_ID = "com.anuj.obsidian"

    // Playback Service Actions
    const val ACTION_PLAY_PAUSE = "$APPLICATION_ID.action.PLAY_PAUSE"
    const val ACTION_PREVIOUS = "$APPLICATION_ID.action.PREVIOUS"
    const val ACTION_NEXT = "$APPLICATION_ID.action.NEXT"
    const val ACTION_SHUFFLE = "$APPLICATION_ID.action.SHUFFLE"
    const val ACTION_REPEAT = "$APPLICATION_ID.action.REPEAT"
    const val ACTION_FAST_FORWARD = "$APPLICATION_ID.action.FAST_FORWARD"
    const val ACTION_REWIND = "$APPLICATION_ID.action.REWIND"
    const val ACTION_SEEK_TO = "$APPLICATION_ID.action.SEEK_TO"
    const val ACTION_STOP = "$APPLICATION_ID.action.STOP"
    const val ACTION_CLOSE_NOTIFICATION = "$APPLICATION_ID.action.CLOSE_NOTIFICATION"
    const val ACTION_CLOSE_APP = "$APPLICATION_ID.action.CLOSE_APP"

    // Widget Actions
    const val ACTION_WIDGET_PLAY_PAUSE = "$APPLICATION_ID.ACTION_WIDGET_PLAY_PAUSE"

    // Notification Channel IDs
    const val NOTIFICATION_CHANNEL_ID_PLAYBACK = "obsidian_playback_channel_v4"
    const val NOTIFICATION_CHANNEL_NAME_PLAYBACK = "Obsidian Hi-Res Playback"
    const val NOTIFICATION_CHANNEL_ID_DOWNLOADS = "obsidian_downloads_channel"
    const val NOTIFICATION_CHANNEL_NAME_DOWNLOADS = "Obsidian Downloads"

    // Notification IDs
    const val NOTIFICATION_ID_PLAYBACK = 1001
    const val NOTIFICATION_ID_DOWNLOAD = 1002

    // Intent Extras
    const val EXTRA_SEEK_POSITION_MS = "seek_position_ms"
    const val EXTRA_CRASH_LOOP_DETECTED = "crash_loop_detected"
    const val EXTRA_CRASH_MESSAGE = "crash_message"
    const val EXTRA_RECOVERED_FROM_CRASH = "recovered_from_crash"

    // Providers
    const val PROVIDER_JIOSAAVN = "JioSaavn"
    const val PROVIDER_SPOTIFLAC = "SpotiFlac"
    const val PROVIDER_TIDAL = "Tidal"
    const val PROVIDER_QOBUZ = "Qobuz"
    const val PROVIDER_DEEZER = "Deezer"
}
