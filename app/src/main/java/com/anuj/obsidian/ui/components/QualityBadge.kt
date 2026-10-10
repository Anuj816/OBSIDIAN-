package com.anuj.obsidian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.anuj.obsidian.ui.theme.ObsidianTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.FlacLosslessCyan

import com.anuj.obsidian.ui.animation.shimmerGlowWave

@Composable
fun QualityBadge(
    bitDepth: String,
    sampleRate: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    format: String = ""
) {
    val isLossy = format.equals("mp3", true) || format.equals("aac", true) || format.equals("m4a", true) || format.equals("ogg", true) || format.equals("opus", true)
    val hasBitDepth = bitDepth.isNotBlank() && !bitDepth.equals("UNKNOWN", true) && !bitDepth.equals("lossy", true)
    val safeSampleRate = if (sampleRate.isBlank() || sampleRate.equals("UNKNOWN", ignoreCase = true)) "44.1 kHz" else sampleRate
    val isHiRes = hasBitDepth && (bitDepth.contains("24") || bitDepth.contains("32")) && (safeSampleRate.contains("96") || safeSampleRate.contains("192") || safeSampleRate.contains("88") || safeSampleRate.contains("176"))
    val badgeBg = if (isHiRes) Color(0xFF261D0C) else if (isLossy) Color(0xFF1B1C22) else Color(0xFF0C212B)
    val badgeBorder = if (isHiRes) FlacHiResGold.copy(alpha = 0.5f) else if (isLossy) Color(0xFF5A5C6A).copy(alpha = 0.5f) else FlacLosslessCyan.copy(alpha = 0.5f)
    val badgeText = if (isHiRes) FlacHiResGold else if (isLossy) Color(0xFFA0A4B8) else FlacLosslessCyan

    val cleanRate = safeSampleRate.replace(".0", "").replace(" ", "")
    val formatDisplay = if (format.isNotBlank()) format.uppercase() else "FLAC"
    val label = when {
        isHiRes -> if (compact) "24-BIT / $cleanRate" else "HI-RES • $bitDepth / $cleanRate"
        isLossy -> if (format.isNotBlank()) format.uppercase() else "AUDIO"
        hasBitDepth -> if (compact) "$bitDepth / $cleanRate" else "$formatDisplay • $bitDepth"
        else -> if (compact) cleanRate else (if (format.isNotBlank()) format.uppercase() else "44.1kHz")
    }

    Box(
        modifier = modifier
            .background(badgeBg, RoundedCornerShape(4.dp))
            .border(1.dp, badgeBorder, RoundedCornerShape(4.dp))
            .shimmerGlowWave(enabled = isHiRes, highlightColor = FlacHiResGold)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isHiRes) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = FlacHiResGold,
                    modifier = Modifier.size(9.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                color = badgeText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FormatChip(
    text: String,
    color: Color = ObsidianTheme.colors.primary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

fun formatSecToTime(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val mins = s / 60
    val secs = s % 60
    return "%02d:%02d".format(mins, secs)
}


