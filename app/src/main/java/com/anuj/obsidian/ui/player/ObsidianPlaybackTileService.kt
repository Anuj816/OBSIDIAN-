package com.anuj.obsidian.ui.player

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.anuj.obsidian.ObsidianApplication

@RequiresApi(Build.VERSION_CODES.N)
class ObsidianPlaybackTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        try {
            ObsidianApplication.playerManager.togglePlayPause()
            updateTileState()
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackTileService.onClick")
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        try {
            val isPlaying = ObsidianApplication.playerManager.isPlaying()
            val currentTrack = ObsidianApplication.playerManager.getCurrentTrack()
            
            tile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (currentTrack != null) {
                currentTrack.title.take(18)
            } else {
                "Obsidian Player"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isPlaying) "Playing" else "Paused"
            }
            tile.updateTile()
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ObsidianPlaybackTileService.updateTileState")
        }
    }
}
