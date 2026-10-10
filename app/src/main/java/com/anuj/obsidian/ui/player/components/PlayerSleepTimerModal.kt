package com.anuj.obsidian.ui.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSleepTimerModal(
    sleepTimerMinutes: Int?,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = ObsidianTheme.colors
    var customTimerMinutes by remember(sleepTimerMinutes) { mutableIntStateOf(sleepTimerMinutes ?: 30) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = appColors.surfaceElevated,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = appColors.surfaceBorder
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "SLEEP TIMER",
                    color = appColors.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                if (sleepTimerMinutes != null && sleepTimerMinutes > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = appColors.primary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, appColors.primary)
                    ) {
                        Text(
                            text = "ACTIVE: ${sleepTimerMinutes}m",
                            color = appColors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Text(
                text = "Smoothly fades out audio and stops playback upon expiry",
                color = appColors.textMuted,
                fontSize = 11.5.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Scroller / Slider
            Surface(
                color = appColors.surfaceCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, appColors.surfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${customTimerMinutes} Minutes",
                        color = appColors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = customTimerMinutes.toFloat(),
                        onValueChange = { customTimerMinutes = it.roundToInt().coerceIn(1, 180) },
                        valueRange = 1f..180f,
                        steps = 179,
                        colors = SliderDefaults.colors(
                            thumbColor = appColors.primary,
                            activeTrackColor = appColors.primary,
                            inactiveTrackColor = appColors.surfaceBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1m", color = appColors.textMuted, fontSize = 11.sp)
                        Text("60m", color = appColors.textMuted, fontSize = 11.sp)
                        Text("120m", color = appColors.textMuted, fontSize = 11.sp)
                        Text("180m", color = appColors.textMuted, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Quick Presets",
                color = appColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            val timerOptions = listOf(5, 10, 15, 30, 45, 60, 90, 120)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(timerOptions) { min ->
                    FilterChip(
                        selected = customTimerMinutes == min,
                        onClick = {
                            customTimerMinutes = min
                            onSetSleepTimer(min)
                            onDismiss()
                        },
                        label = { Text("${min}m", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = appColors.primary,
                            selectedLabelColor = Color.Black,
                            containerColor = appColors.surfaceCard,
                            labelColor = appColors.textSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start & Cancel Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (sleepTimerMinutes != null && sleepTimerMinutes > 0) {
                    OutlinedButton(
                        onClick = {
                            onCancelSleepTimer()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5252)
                        )
                    ) {
                        Text("Stop Timer", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        onSetSleepTimer(customTimerMinutes)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.primary,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Start (${customTimerMinutes}m)", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
