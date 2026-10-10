package com.anuj.obsidian.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.data.model.DspState
import com.anuj.obsidian.data.model.QueueReturnMode
import com.anuj.obsidian.data.model.AppSettings
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.theme.DarkSurface
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.DarkSurfaceCard
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.TextMuted
import com.anuj.obsidian.ui.theme.TextPrimary
import com.anuj.obsidian.ui.theme.TextSecondary
import com.anuj.obsidian.ui.theme.ObsidianTheme

/**
 * =========================================================================================
 * Settings DAC & Engine Section
 * =========================================================================================
 * RESPONSIBILITY:
 * Hardware DAC output settings, resampling rates, tube harmonic simulation, gapless playback,
 * ReplayGain volume normalization, sleep timer controls, headset auto-actions, and queue modes.
 * =========================================================================================
 */
fun LazyListScope.settingsDacEngineSection(
    dspState: DspState,
    settings: AppSettings,
    sleepTimerMinutes: Int?,
    viewModel: ObsidianViewModel,
    onManageFolders: () -> Unit
) {
    // HARDWARE DAC & RESAMPLING
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "DAC & Resampling Engine",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                // Live Hardware DAC Status Card
                val dacStatus by com.anuj.obsidian.audio.dac.UsbDacManager.dacStatus.collectAsState()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (dacStatus.isUsbDacConnected) ObsidianTheme.colors.primary.copy(alpha = 0.12f) else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (dacStatus.isUsbDacConnected) ObsidianTheme.colors.primary.copy(alpha = 0.5f) else DarkSurfaceBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Hardware DAC",
                                tint = if (dacStatus.isUsbDacConnected) ObsidianTheme.colors.primary else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = if (dacStatus.isUsbDacConnected) dacStatus.deviceName else "Standard System Output",
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (dacStatus.isUsbDacConnected) {
                                        val maxSr = (dacStatus.supportedSampleRates.maxOrNull() ?: 192000) / 1000
                                        "${dacStatus.productType} • $maxSr kHz Max"
                                    } else {
                                        "Connect USB-C DAC for Bit-Perfect Output"
                                    },
                                    color = if (dacStatus.isUsbDacConnected) ObsidianTheme.colors.primary else TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        if (dacStatus.isUsbDacConnected && dspState.isBitPerfectDac) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = FlacHiResGold,
                                contentColor = Color.Black
                            ) {
                                Text(
                                    text = "BIT-PERFECT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Bit-Perfect Direct USB DAC
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bit-Perfect Direct USB DAC Mode",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Bypasses standard Android AudioFlinger mixer for unadulterated high-resolution stream delivery",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = dspState.isBitPerfectDac,
                        onCheckedChange = { viewModel.setBitPerfectDac(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ObsidianTheme.colors.primary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Vacuum Tube Harmonic Warmth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vacuum Tube Even-Harmonic Warmth",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Simulates triode tube amplifier 2nd-order harmonic richness",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = dspState.tubeWarmth,
                        onCheckedChange = { viewModel.toggleTubeWarmth() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ObsidianTheme.colors.primary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Dynamic Range Compressor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Studio Dynamic Range Compression",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Balances quiet whispering nuances with loud orchestra peaks",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = dspState.dynamicRangeCompression,
                        onCheckedChange = { viewModel.toggleDynamicRangeCompression() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ObsidianTheme.colors.primary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Resampling Mode
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Target Audio Resampling Rate",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val rates = listOf("Native Bit-Perfect", "96.0 kHz / 24-bit", "192.0 kHz / 24-bit")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rates.forEach { rate ->
                            val isSel = dspState.resamplingRate == rate
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setResamplingRate(rate) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) ObsidianTheme.colors.primary.copy(alpha = 0.2f) else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSel) ObsidianTheme.colors.primary else DarkSurfaceBorder
                                )
                            ) {
                                Text(
                                    text = rate,
                                    color = if (isSel) ObsidianTheme.colors.primary else TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Autoplay On App Launch Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Autoplay On App Launch",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Resume audio playback automatically when opening the app (Off by default)",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = settings.autoplayOnLaunch,
                        onCheckedChange = { viewModel.setAutoplayOnLaunch(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ObsidianTheme.colors.primary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Gapless Playback Engine
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Seamless Gapless Playback Engine",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Uses pre-buffering pipeline to achieve 0ms song transition latency, with linear volume crossfade when duration is set above 0s",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = settings.gaplessPlayback,
                        onCheckedChange = { viewModel.setGaplessPlayback(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ObsidianTheme.colors.primary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceBorder
                        )
                    )
                }

                // Volume Normalization (ReplayGain)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Volume Normalization (ReplayGain)",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ensure a consistent volume level across different FLAC tracks within your library",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = settings.volumeNormalizationEnabled,
                        onCheckedChange = { viewModel.setVolumeNormalizationEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ObsidianTheme.colors.primary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceBorder
                        )
                    )
                }
                
                if (settings.gaplessPlayback) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Transition Crossfade (0s = Sample-Accurate Gapless)",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (settings.crossfadeDurationSec <= 0f) "0.0s (Sample-Accurate Gapless)" else "${"%.1f".format(settings.crossfadeDurationSec)}s",
                                color = if (settings.crossfadeDurationSec <= 0f) ObsidianTheme.colors.primary else FlacHiResGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.crossfadeDurationSec.coerceIn(0f, 12f),
                            onValueChange = { viewModel.setCrossfadeDuration(it) },
                            valueRange = 0f..12f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = ObsidianTheme.colors.primary,
                                activeTrackColor = ObsidianTheme.colors.primary,
                                inactiveTrackColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier.testTag("crossfade_duration_slider")
                        )
                    }
                }

                // Sleep Timer with Gentle Audio Fade-out
                val isSleepActive = (sleepTimerMinutes ?: 0) > 0
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSleepActive) ObsidianTheme.colors.primary.copy(alpha = 0.12f) else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSleepActive) ObsidianTheme.colors.primary else DarkSurfaceBorder
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSleepActive) ObsidianTheme.colors.primary.copy(alpha = 0.25f) else DarkSurfaceCard,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Bedtime,
                                            contentDescription = "Sleep Timer",
                                            tint = if (isSleepActive) ObsidianTheme.colors.primary else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Audiophile Sleep Timer",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isSleepActive) "Active: Stops playback in ${sleepTimerMinutes ?: 0} min with smooth fade-out" else "Smooth exponential fade-out before stopping playback",
                                        color = if (isSleepActive) ObsidianTheme.colors.primary else TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (isSleepActive) {
                                Button(
                                    onClick = { viewModel.cancelSleepTimer() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DarkSurfaceCard,
                                        contentColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Timer Preset Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 30, 45, 60, 90).forEach { mins ->
                                val isSel = sleepTimerMinutes == mins
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.startSleepTimer(mins) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) ObsidianTheme.colors.primary else DarkSurfaceCard,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSel) ObsidianTheme.colors.primary else DarkSurfaceBorder
                                    )
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        color = if (isSel) Color.Black else TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Offline Mode (Forced Local Room & Storage Playback)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (settings.offlineMode) ObsidianTheme.colors.primary.copy(alpha = 0.12f) else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (settings.offlineMode) ObsidianTheme.colors.primary else DarkSurfaceBorder
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (settings.offlineMode) ObsidianTheme.colors.primary.copy(alpha = 0.25f) else DarkSurfaceCard,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = "Offline Mode",
                                        tint = if (settings.offlineMode) ObsidianTheme.colors.primary else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Offline Mode",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (settings.offlineMode) ObsidianTheme.colors.primary else DarkSurfaceBorder
                                    ) {
                                        Text(
                                            text = if (settings.offlineMode) "OFFLINE ROOM ONLY" else "ONLINE STREAMING",
                                            color = if (settings.offlineMode) Color.Black else TextMuted,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Forces the MediaController to only pull tracks from the local Room database and internal file storage, preventing network request attempts when internet connectivity is unstable.",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                        Switch(
                            checked = settings.offlineMode,
                            onCheckedChange = { viewModel.setOfflineMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ObsidianTheme.colors.primary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier.testTag("switch_offline_mode")
                        )
                    }
                }

                // Headset & Bluetooth Auto-Actions Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianTheme.colors.primary.copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = "Headset Actions",
                                        tint = ObsidianTheme.colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Headset & Bluetooth Auto-Actions",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Hardware connection triggers & disconnect fade-outs",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        // Auto-Resume on Connect Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Auto-Resume on Connect",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Automatically resumes playback when wired headphones or Bluetooth audio connects",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = settings.headsetAutoresume,
                                onCheckedChange = {
                                    viewModel.setHeadsetActions(
                                        autoresume = it,
                                        fadeOut = settings.headsetFadeOutOnDisconnect,
                                        fadeOutDurSec = settings.headsetFadeOutDurationSec
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = ObsidianTheme.colors.primary,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurfaceBorder
                                )
                            )
                        }

                        // Configurable Fade-Out on Disconnect (Default OFF)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Fade-Out on Disconnect",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (settings.headsetFadeOutOnDisconnect) ObsidianTheme.colors.primary else DarkSurfaceBorder
                                    ) {
                                        Text(
                                            text = if (settings.headsetFadeOutOnDisconnect) "ON" else "OFF (DEFAULT)",
                                            color = if (settings.headsetFadeOutOnDisconnect) Color.Black else TextMuted,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Smoothly fades out volume over duration when headphones unplug or Bluetooth drops instead of an abrupt cut",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = settings.headsetFadeOutOnDisconnect,
                                onCheckedChange = {
                                    viewModel.setHeadsetActions(
                                        autoresume = settings.headsetAutoresume,
                                        fadeOut = it,
                                        fadeOutDurSec = settings.headsetFadeOutDurationSec
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = ObsidianTheme.colors.primary,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurfaceBorder
                                )
                            )
                        }

                        if (settings.headsetFadeOutOnDisconnect) {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Fade-Out Duration",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1fs", settings.headsetFadeOutDurationSec),
                                        color = ObsidianTheme.colors.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = settings.headsetFadeOutDurationSec,
                                    onValueChange = {
                                        viewModel.setHeadsetActions(
                                            autoresume = settings.headsetAutoresume,
                                            fadeOut = true,
                                            fadeOutDurSec = it
                                        )
                                    },
                                    valueRange = 0.5f..5.0f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ObsidianTheme.colors.primary,
                                        activeTrackColor = ObsidianTheme.colors.primary,
                                        inactiveTrackColor = DarkSurfaceBorder
                                    )
                                )
                            }
                        }
                    }
                }

                // Obsidian Advanced Shuffle & Queue Navigation Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianTheme.colors.primary.copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Shuffle and Queue Modes",
                                        tint = ObsidianTheme.colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Obsidian Smart Shuffle & Queue Modes",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Advanced list randomization & explicit queue return behaviors",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        // Default Shuffle Mode Selection
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "SHUFFLE MODE",
                                color = ObsidianTheme.colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            val curShuffle = try {
                                AdvancedShuffleMode.valueOf(settings.advancedShuffleMode)
                            } catch (e: Exception) {
                                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "SettingsDacEngineSection.curShuffle")
                                AdvancedShuffleMode.SHUFFLE_OFF
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AdvancedShuffleMode.entries.forEach { mode ->
                                    val isSelected = curShuffle == mode
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) ObsidianTheme.colors.primary.copy(alpha = 0.18f) else DarkSurfaceCard,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ObsidianTheme.colors.primary else DarkSurfaceBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setAdvancedShuffleMode(mode)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = mode.title,
                                                    color = if (isSelected) ObsidianTheme.colors.primary else TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                Text(
                                                    text = mode.description,
                                                    color = TextSecondary,
                                                    fontSize = 10.5.sp
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = ObsidianTheme.colors.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // On Queue Completion Behavior
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "ON QUEUE COMPLETION",
                                color = ObsidianTheme.colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            val curQueueReturn = try {
                                QueueReturnMode.valueOf(settings.queueReturnMode)
                            } catch (e: Exception) {
                                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "SettingsDacEngineSection.curQueueReturn")
                                QueueReturnMode.RETURN_TO_PREVIOUS
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                QueueReturnMode.entries.forEach { qMode ->
                                    val isSelected = curQueueReturn == qMode
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) ObsidianTheme.colors.primary.copy(alpha = 0.18f) else DarkSurfaceCard,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ObsidianTheme.colors.primary else DarkSurfaceBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setQueueReturnMode(qMode)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = qMode.title,
                                                    color = if (isSelected) ObsidianTheme.colors.primary else TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                Text(
                                                    text = qMode.description,
                                                    color = TextSecondary,
                                                    fontSize = 10.5.sp
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = ObsidianTheme.colors.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dynamic Canvas & Video Backgrounds Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Dynamic Canvas",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dynamic Canvas & Animated Covers",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Animated Spotify Canvas / Apple Music dynamic art",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            Switch(
                                checked = settings.dynamicCanvasEnabled,
                                onCheckedChange = {
                                    viewModel.setDynamicCanvasSettings(it, settings.dynamicCanvasPreset)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = ObsidianTheme.colors.primary,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurfaceBorder
                                )
                            )
                        }

                        if (settings.dynamicCanvasEnabled) {
                            Text(
                                text = "ACTIVE CANVAS ENGINE",
                                color = ObsidianTheme.colors.primary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            val canvasPresets = listOf(
                                Pair("cosmic_nebula", "🌌 Cosmic Nebula"),
                                Pair("liquid_aurora", "🌊 Liquid Aurora"),
                                Pair("vinyl_turntable", "💿 Vinyl Turntable"),
                                Pair("cyberpunk_grid", "⚡ Cyberpunk Grid"),
                                Pair("vacuum_tube", "💡 Tube Glow"),
                                Pair("audio_reactive_waves", "🎚️ Harmonic Waves")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                canvasPresets.forEach { (id, label) ->
                                    val isSelected = settings.dynamicCanvasPreset == id
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) ObsidianTheme.colors.primary else DarkSurfaceCard,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) ObsidianTheme.colors.primary else DarkSurfaceBorder
                                        ),
                                        modifier = Modifier.clickable {
                                            viewModel.setDynamicCanvasSettings(true, id)
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.Black else TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Folder Hierarchy Explorer Direct Launch
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManageFolders() }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Folder Tree Explorer",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Folder Hierarchy Tree Explorer",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Browse file system directories with instant recursive folder quick-play buttons",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        Button(
                            onClick = onManageFolders,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ObsidianTheme.colors.primary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Explore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
