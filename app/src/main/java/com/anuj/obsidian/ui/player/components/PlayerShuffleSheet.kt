package com.anuj.obsidian.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.ui.components.icon
import com.anuj.obsidian.ui.theme.ObsidianTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerShuffleSheet(
    shuffleMode: AdvancedShuffleMode,
    dynamicPrimaryColor: Color,
    onSelectShuffleMode: (AdvancedShuffleMode) -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = ObsidianTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = appColors.surfaceCard,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Shuffle Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            AdvancedShuffleMode.entries.forEach { mode ->
                val isSelected = shuffleMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectShuffleMode(mode)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = mode.icon(),
                        contentDescription = mode.title,
                        tint = if (isSelected) dynamicPrimaryColor else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = mode.title,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) dynamicPrimaryColor else Color.White
                        )
                        Text(
                            text = mode.description,
                            fontSize = 12.sp,
                            color = if (isSelected) dynamicPrimaryColor.copy(alpha = 0.8f) else appColors.textSecondary
                        )
                    }
                }
            }
        }
    }
}
