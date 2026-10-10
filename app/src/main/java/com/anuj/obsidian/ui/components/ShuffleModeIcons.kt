package com.anuj.obsidian.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import com.anuj.obsidian.data.model.AdvancedShuffleMode

fun AdvancedShuffleMode.icon(): ImageVector = when (this) {
    AdvancedShuffleMode.SHUFFLE_OFF -> Icons.Filled.Shuffle
    AdvancedShuffleMode.SHUFFLE_ALL -> Icons.Filled.Shuffle
    AdvancedShuffleMode.SHUFFLE_TRACKS -> Icons.Filled.Album
    AdvancedShuffleMode.SHUFFLE_CATEGORIES -> Icons.Filled.Folder
    AdvancedShuffleMode.SHUFFLE_SONGS_AND_CATEGORIES -> Icons.Filled.AutoAwesome
}
