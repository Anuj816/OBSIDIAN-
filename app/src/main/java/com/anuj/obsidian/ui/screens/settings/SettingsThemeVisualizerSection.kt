package com.anuj.obsidian.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.AppSettings
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.components.ColorTarget
import com.anuj.obsidian.ui.theme.DarkSurface
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.DarkSurfaceCard
import com.anuj.obsidian.ui.theme.PRESET_THEMES
import com.anuj.obsidian.ui.theme.TextMuted
import com.anuj.obsidian.ui.theme.TextPrimary
import com.anuj.obsidian.ui.theme.TextSecondary
import com.anuj.obsidian.ui.theme.getActiveThemePalette

/**
 * =========================================================================================
 * Settings Theme & Visualizer Section
 * =========================================================================================
 * RESPONSIBILITY:
 * Material3 color themes, real-time live theme preview box, preset themes catalog,
 * custom color palette studio with color wheel launcher, visualizer performance modes,
 * auto-cycle intervals, inactivity graceful timeout, and developer layout bounds debugging.
 * =========================================================================================
 */
fun LazyListScope.settingsThemeVisualizerSection(
    settings: AppSettings,
    viewModel: ObsidianViewModel,
    onOpenColorWheel: (ColorTarget) -> Unit
) {
    item {
        val activePalette = getActiveThemePalette(settings)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VISUAL IDENTITY & THEMES",
                        color = activePalette.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Theme & UI Customization",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = activePalette.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Real-Time Theme Preview Box
            Card(
                colors = CardDefaults.cardColors(containerColor = activePalette.surfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, activePalette.surfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE THEME PREVIEW",
                            color = activePalette.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = activePalette.name,
                            color = activePalette.secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = activePalette.description,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = activePalette.primary,
                                contentColor = activePalette.background
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Primary Accent", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = activePalette.surfaceElevated,
                                contentColor = activePalette.secondary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Secondary", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Presets Grid
            Text(
                text = "PRESET THEMES",
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_THEMES.forEach { preset ->
                    val isSelected = settings.themePreset == preset.id
                    Surface(
                        color = if (isSelected) preset.primary.copy(alpha = 0.12f) else DarkSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) preset.primary else DarkSurfaceBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectThemePreset(preset.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Theme Color Swatches Circle
                                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(preset.primary)
                                            .border(1.5.dp, Color.Black, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(preset.secondary)
                                            .border(1.5.dp, Color.Black, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(preset.background)
                                            .border(1.5.dp, Color.Black, CircleShape)
                                    )
                                }

                                Column {
                                    Text(
                                        text = preset.name,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = preset.description,
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(preset.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Custom Color Customization Section
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CUSTOM COLOR STUDIO",
                                color = activePalette.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Tailor Your Personal Palette",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (settings.themePreset == "custom") activePalette.primary else Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, activePalette.primary),
                            modifier = Modifier.clickable {
                                viewModel.setCustomColors(
                                    settings.customPrimaryColor,
                                    settings.customSecondaryColor,
                                    settings.customBackgroundColor
                                )
                            }
                        ) {
                            Text(
                                text = if (settings.themePreset == "custom") "ACTIVE" else "ENABLE CUSTOM",
                                color = if (settings.themePreset == "custom") Color.Black else activePalette.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Interactive Color Wheel Hero Launcher Banner
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = activePalette.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, activePalette.primary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenColorWheel(ColorTarget.PRIMARY)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                Color(0xFFFF0000),
                                                Color(0xFFFFFF00),
                                                Color(0xFF00FF00),
                                                Color(0xFF00FFFF),
                                                Color(0xFF0000FF),
                                                Color(0xFFFF00FF),
                                                Color(0xFFFF0000)
                                            )
                                        )
                                    )
                                    .border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Interactive Color Wheel",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "360° Hue wheel, luminance sliders & live theme preview",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = activePalette.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "OPEN",
                                    color = Color.Black,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // 1. Primary Color Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Primary Accent Color",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "🎨 Pick on Wheel",
                            color = activePalette.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onOpenColorWheel(ColorTarget.PRIMARY)
                            }
                        )
                    }

                    val primarySwatches = listOf(
                        0xFF1DB954L to "Emerald",
                        0xFF00F0FFL to "Neon Cyan",
                        0xFFFF007FL to "Hot Magenta",
                        0xFFFFB300L to "Amber Gold",
                        0xFFBB86FCL to "Amethyst",
                        0xFFFF3366L to "Crimson Red",
                        0xFF2979FFL to "Sapphire Blue",
                        0xFF00FF66L to "Matrix Green",
                        0xFFFFFFFFL to "Monochrome"
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(primarySwatches) { (colorLong, name) ->
                            val isCurrent = settings.customPrimaryColor == colorLong && settings.themePreset == "custom"
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    viewModel.setCustomColors(
                                        colorLong,
                                        settings.customSecondaryColor,
                                        settings.customBackgroundColor
                                    )
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorLong))
                                        .border(
                                            if (isCurrent) 3.dp else 1.dp,
                                            if (isCurrent) Color.White else Color.Black.copy(alpha = 0.5f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (colorLong == 0xFFFFFFFFL) Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = name,
                                    color = if (isCurrent) activePalette.primary else TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 2. Secondary Color Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Secondary Accent Color",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "🎨 Pick on Wheel",
                            color = activePalette.secondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onOpenColorWheel(ColorTarget.SECONDARY)
                            }
                        )
                    }

                    val secondarySwatches = listOf(
                        0xFFF5A623L to "Gold",
                        0xFFFF0055L to "Neon Rose",
                        0xFF00E5FFL to "Cyan Glow",
                        0xFF33FF33L to "Acid Lime",
                        0xFFFF9900L to "Warm Flame",
                        0xFF03DAC6L to "Turquoise",
                        0xFFD4B0FFL to "Lavender"
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(secondarySwatches) { (colorLong, name) ->
                            val isCurrent = settings.customSecondaryColor == colorLong && settings.themePreset == "custom"
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    viewModel.setCustomColors(
                                        settings.customPrimaryColor,
                                        colorLong,
                                        settings.customBackgroundColor
                                    )
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorLong))
                                        .border(
                                            if (isCurrent) 3.dp else 1.dp,
                                            if (isCurrent) Color.White else Color.Black.copy(alpha = 0.5f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = name,
                                    color = if (isCurrent) activePalette.secondary else TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 3. Background Tone Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Background Deep Canvas Tone",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "🎨 Pick on Wheel",
                            color = activePalette.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onOpenColorWheel(ColorTarget.BACKGROUND)
                            }
                        )
                    }

                    val bgSwatches = listOf(
                        0xFF000000L to "Pure Black (OLED)",
                        0xFF090C10L to "Studio Slate",
                        0xFF060D1AL to "Ocean Navy",
                        0xFF040D07L to "Deep Forest",
                        0xFF0C0814L to "Night Violet"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        bgSwatches.forEach { (bgLong, name) ->
                            val isCurrent = settings.customBackgroundColor == bgLong && settings.themePreset == "custom"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(bgLong),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isCurrent) 2.dp else 1.dp,
                                    if (isCurrent) activePalette.primary else DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setCustomColors(
                                            settings.customPrimaryColor,
                                            settings.customSecondaryColor,
                                            bgLong
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = activePalette.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // VISUALIZER PERFORMANCE & ZERO LAG ENGINE
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "VISUALIZER ENGINE & PERFORMANCE",
                                color = activePalette.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Master Controls & GPU Optimizer",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = activePalette.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 1. MASTER VISUALIZER TOGGLE
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Visualizer Engine (Master)",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Toggle hardware visualizer rendering globally across the app",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = settings.visualizerMasterEnabled,
                                onCheckedChange = { viewModel.setVisualizerMasterEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = activePalette.primary,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkSurfaceBorder
                                )
                            )
                        }
                    }

                    // 1b. SHOW VISUALIZATION ACROSS ALL SCREENS TOGGLE
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Show Visualization Across All Screens",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (settings.visualizerShowEverywhere) 
                                        "Visualizations active in background of Home, Library, Queue & Player (Settings remains clean)" 
                                    else 
                                        "Visualizations restricted to Player screen only",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = settings.visualizerShowEverywhere,
                                onCheckedChange = { viewModel.setVisualizerShowEverywhere(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = activePalette.primary,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkSurfaceBorder
                                )
                            )
                        }
                    }

                    // 1c. BACKGROUND VISUALIZER TOGGLE
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Ambient Background Visualizer",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Render live audio-reactive animations behind the UI during playback",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = settings.backgroundVisualizerEnabled,
                                onCheckedChange = { viewModel.setBackgroundVisualizerEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = activePalette.primary,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkSurfaceBorder
                                )
                            )
                        }
                    }

                    // 2. BACKGROUND MODE (Dynamic Adaptive vs Fixed)
                    Text(
                        text = "Background Visualizer Canvas Mode",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val bgModes = listOf(
                            "dynamic" to "Adaptive Artwork",
                            "fixed" to "Fixed Neutral / Dark"
                        )
                        bgModes.forEach { (mode, title) ->
                            val isSelected = settings.visualizerBackgroundMode == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) activePalette.primary.copy(alpha = 0.15f) else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) activePalette.primary else DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setVisualizerBackgroundMode(mode) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) activePalette.primary else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // 3. CHROME LAYERING (Above vs Below Player Controls)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Layer Above Player Controls",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (settings.visualizerAboveChrome) "Visualizer renders over controls with subtle translucency" else "Visualizer renders strictly in the background beneath controls",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = settings.visualizerAboveChrome,
                                onCheckedChange = { viewModel.setVisualizerAboveChrome(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = activePalette.primary,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkSurfaceBorder
                                )
                            )
                        }
                    }

                    // 4. FPS LIMIT / THROTTLE
                    Text(
                        text = "Visualizer Rendering FPS Cap",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val fpsOptions = listOf(
                            30 to "30 FPS",
                            60 to "60 FPS",
                            120 to "120 FPS",
                            0 to "Uncapped"
                        )
                        fpsOptions.forEach { (fpsVal, label) ->
                            val isSelected = settings.visualizerFpsLimit == fpsVal
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) activePalette.primary.copy(alpha = 0.15f) else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) activePalette.primary else DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setVisualizerFpsLimit(fpsVal) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) activePalette.primary else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // 5. VISUALIZER STYLE CATEGORY FILTER
                    Text(
                        text = "Visualizer Style Category",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val catOptions = listOf(
                            "ALL" to "All",
                            "MILKDROP" to "Milkdrop",
                            "BARS" to "Bars",
                            "CIRCULAR" to "Circular",
                            "WAVEFORM" to "Waves",
                            "PARTICLES" to "Particles"
                        )
                        catOptions.take(3).forEach { (catKey, label) ->
                            val isSelected = settings.visualizerStyleCategory == catKey
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) activePalette.primary.copy(alpha = 0.15f) else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) activePalette.primary else DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setVisualizerStyleCategory(catKey) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) activePalette.primary else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val catOptions = listOf(
                            "ALL" to "All",
                            "MILKDROP" to "Milkdrop",
                            "BARS" to "Bars",
                            "CIRCULAR" to "Circular",
                            "WAVEFORM" to "Waves",
                            "PARTICLES" to "Particles"
                        )
                        catOptions.drop(3).forEach { (catKey, label) ->
                            val isSelected = settings.visualizerStyleCategory == catKey
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) activePalette.primary.copy(alpha = 0.15f) else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) activePalette.primary else DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setVisualizerStyleCategory(catKey) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) activePalette.primary else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Hardware Performance Profile",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val perfModes = listOf(
                        Triple(
                            "smooth_60",
                            "Smooth 60 FPS (Zero-Allocations)",
                            "Optimized GPU canvas loop with pre-allocated scratch math buffers. No GC garbage spikes."
                        ),
                        Triple(
                            "ultra_low_power",
                            "Eco Ultra Low Power (0% Lag)",
                            "Minimal mathematical compute; lightweight 20-band frequency analyzer with 0% device load."
                        ),
                        Triple(
                            "minimalist",
                            "Minimalist Wave (Ultra Lightweight)",
                            "Single smooth sine audio line with low memory footprint."
                        ),
                        Triple(
                            "disabled",
                            "Visualizer Disabled (Max Battery)",
                            "Completely halts canvas rendering loop for absolute zero power consumption."
                        )
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        perfModes.forEach { (modeKey, title, desc) ->
                            val isCurrent = settings.visualizerPerformanceMode == modeKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) activePalette.primary.copy(alpha = 0.12f) else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isCurrent) 1.5.dp else 1.dp,
                                    if (isCurrent) activePalette.primary else DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setVisualizerPerformanceMode(modeKey) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            color = if (isCurrent) activePalette.primary else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = desc,
                                            color = TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }

                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(activePalette.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // VISUALIZER AUTO-CYCLE SETTINGS
                    Text(
                        text = "Auto-Cycle Animation Mode",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Automatically transition across 384+ visualizers during playback",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    val cycleModes = listOf(
                        "off" to "Off (Manual Only)",
                        "seq_10" to "Sequential (Every 10s)",
                        "seq_20" to "Sequential (Every 20s)",
                        "seq_30" to "Sequential (Every 30s)",
                        "seq_60" to "Sequential (Every 60s)",
                        "shuf_10" to "Shuffle (Every 10s)",
                        "shuf_20" to "Shuffle (Every 20s)",
                        "shuf_30" to "Shuffle (Every 30s)",
                        "random_wild" to "Wild Dynamic (Random 8-25s)"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cycleModes.forEach { (modeKey, title) ->
                            val isCurrent = settings.visualizerCycleMode == modeKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) activePalette.primary.copy(alpha = 0.12f) else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isCurrent) 1.5.dp else 1.dp,
                                    if (isCurrent) activePalette.primary else DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setVisualizerCycleMode(modeKey) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isCurrent) activePalette.primary else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(activePalette.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // INACTIVITY TIMEOUT SETTINGS
                    Text(
                        text = "Auto-Standby / Inactivity Graceful Timeout",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Gracefully release player memory and audio resources when idle",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    val timeoutOptions = listOf(
                        0 to "Never (Always Ready)",
                        30 to "30 Minutes",
                        60 to "1 Hour",
                        120 to "2 Hours",
                        180 to "3 Hours"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        timeoutOptions.forEach { (mins, label) ->
                            val isCurrent = settings.inactivityTimeoutMinutes == mins
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) activePalette.primary.copy(alpha = 0.12f) else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isCurrent) 1.5.dp else 1.dp,
                                    if (isCurrent) activePalette.primary else DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setInactivityTimeoutMinutes(mins) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isCurrent) activePalette.primary else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(activePalette.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // DEVELOPER LAYOUT BOUNDS
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Developer Layout Boundaries",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toggle Jetpack Compose visual bounds layout debugging overlays",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(10.dp))
                            .clickable { viewModel.updateShowLayoutBounds(!settings.showLayoutBounds) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Layout Bounds",
                                color = if (settings.showLayoutBounds) activePalette.primary else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Draws boundary outlines and corner ticks around all composables",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.showLayoutBounds,
                            onCheckedChange = { viewModel.updateShowLayoutBounds(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = activePalette.primary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurfaceCard
                            )
                        )
                    }
                }
            }
        }
    }
}
