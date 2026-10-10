package com.anuj.obsidian.ui.visualizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.ui.theme.AppThemePalette
import com.anuj.obsidian.ui.theme.ObsidianTheme

@Composable
fun VisualizerFadeControls(
    currentFadeOpacity: Float,
    onFadeOpacityChanged: (Float) -> Unit,
    currentFadeMode: VisualizerFadeController.FadeMode,
    onFadeModeChanged: (VisualizerFadeController.FadeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(appColors.surfaceCard, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Title Header
        Text(
            text = "Visualizer Fade Controls",
            style = MaterialTheme.typography.titleMedium,
            color = appColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Fade Mode Buttons
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FadeModeButton(
                label = "No Fade (Full)",
                mode = VisualizerFadeController.FadeMode.DISABLED,
                isSelected = currentFadeMode == VisualizerFadeController.FadeMode.DISABLED,
                onClick = { onFadeModeChanged(VisualizerFadeController.FadeMode.DISABLED) },
                appColors = appColors
            )
            
            FadeModeButton(
                label = "Light Audio Fade",
                mode = VisualizerFadeController.FadeMode.LIGHT_FADE,
                isSelected = currentFadeMode == VisualizerFadeController.FadeMode.LIGHT_FADE,
                onClick = { onFadeModeChanged(VisualizerFadeController.FadeMode.LIGHT_FADE) },
                appColors = appColors
            )
            
            FadeModeButton(
                label = "Pulse on Beat",
                mode = VisualizerFadeController.FadeMode.PULSE_ON_BEAT,
                isSelected = currentFadeMode == VisualizerFadeController.FadeMode.PULSE_ON_BEAT,
                onClick = { onFadeModeChanged(VisualizerFadeController.FadeMode.PULSE_ON_BEAT) },
                appColors = appColors
            )
            
            FadeModeButton(
                label = "Breathing Cycle",
                mode = VisualizerFadeController.FadeMode.BREATH,
                isSelected = currentFadeMode == VisualizerFadeController.FadeMode.BREATH,
                onClick = { onFadeModeChanged(VisualizerFadeController.FadeMode.BREATH) },
                appColors = appColors
            )
            
            FadeModeButton(
                label = "Fade on Silence",
                mode = VisualizerFadeController.FadeMode.SILENCE_FADE,
                isSelected = currentFadeMode == VisualizerFadeController.FadeMode.SILENCE_FADE,
                onClick = { onFadeModeChanged(VisualizerFadeController.FadeMode.SILENCE_FADE) },
                appColors = appColors
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Opacity Slider (for manual fade control)
        Text(
            text = "Visualizer Opacity",
            style = MaterialTheme.typography.labelSmall,
            color = appColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Slider(
                value = currentFadeOpacity,
                onValueChange = onFadeOpacityChanged,
                modifier = Modifier.weight(1f),
                valueRange = 0f..1f,
                steps = 20,
                colors = SliderDefaults.colors(
                    thumbColor = appColors.primary,
                    activeTrackColor = appColors.primary.copy(alpha = 0.8f),
                    inactiveTrackColor = appColors.surfaceElevated
                )
            )
            
            Text(
                text = "${(currentFadeOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
                color = appColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        
        // Opacity presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Off" to 0f,
                "20%" to 0.2f,
                "50%" to 0.5f,
                "75%" to 0.75f,
                "Full" to 1f
            ).forEach { (label, opacity) ->
                val isSelected = kotlin.math.abs(currentFadeOpacity - opacity) < 0.05f
                Button(
                    onClick = { onFadeOpacityChanged(opacity) },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) {
                            appColors.primary
                        } else {
                            appColors.surfaceElevated
                        }
                    )
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            Color.Black
                        } else {
                            appColors.textPrimary
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FadeModeButton(
    label: String,
    mode: VisualizerFadeController.FadeMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    appColors: AppThemePalette
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                appColors.primary
            } else {
                appColors.surfaceElevated
            }
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) {
                Color.Black
            } else {
                appColors.textPrimary
            }
        )
    }
}
