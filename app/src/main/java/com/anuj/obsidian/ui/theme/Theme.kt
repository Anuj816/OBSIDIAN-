package com.anuj.obsidian.ui.theme

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anuj.obsidian.data.model.AppSettings

data class AdaptiveUiScaling(
    val scaleFactor: Float = 1.0f,
    val buttonMinHeight: Dp = 48.dp,
    val touchTargetMinSize: Dp = 48.dp,
    val cardPadding: Dp = 12.dp,
    val iconSizeSmall: Dp = 18.dp,
    val iconSizeMedium: Dp = 24.dp,
    val iconSizeLarge: Dp = 32.dp,
    val spacingSmall: Dp = 6.dp,
    val spacingMedium: Dp = 12.dp,
    val spacingLarge: Dp = 20.dp,
    val cornerRadiusSmall: Dp = 8.dp,
    val cornerRadiusMedium: Dp = 12.dp,
    val cornerRadiusLarge: Dp = 20.dp
)

val LocalAdaptiveScaling = staticCompositionLocalOf { AdaptiveUiScaling() }

fun Modifier.adaptiveTouchTarget(scaling: AdaptiveUiScaling = AdaptiveUiScaling()) = this
    .defaultMinSize(minWidth = scaling.touchTargetMinSize, minHeight = scaling.touchTargetMinSize)

fun Modifier.adaptiveButtonHeight(scaling: AdaptiveUiScaling = AdaptiveUiScaling()) = this
    .defaultMinSize(minHeight = scaling.buttonMinHeight)

data class AppThemePalette(
    val id: String,
    val name: String,
    val description: String,
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceCard: Color,
    val surfaceBorder: Color,
    val surfaceElevated: Color,
    val textPrimary: Color = Color(0xFFF8FAFC),
    val textSecondary: Color = Color(0xFF94A3B8),
    val textMuted: Color = Color(0xFF64748B)
)

val PRESET_THEMES = listOf(
        AppThemePalette(
        id = "emerald",
        name = "Spotify Clone",
        description = "Pitch black and classic Spotify green",
        primary = Color(0xFF1DB954),
        primaryLight = Color(0xFF1ED760),
        primaryDark = Color(0xFF15883E),
        secondary = Color(0xFF1DB954),
        tertiary = Color(0xFFFFFFFF),
        background = Color(0xFF000000),
        surface = Color(0xFF121212),
        surfaceCard = Color(0xFF181818),
        surfaceBorder = Color(0xFF282828),
        surfaceElevated = Color(0xFF282828),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB3B3B3),
        textMuted = Color(0xFF535353)
    ),
    AppThemePalette(
        id = "cyberpunk",
        name = "Cyberpunk Neon Cyan",
        description = "Electric neon cyan, hot magenta, and synthwave violet",
        primary = Color(0xFF00F0FF),
        primaryLight = Color(0xFF5CF4FF),
        primaryDark = Color(0xFF009EB3),
        secondary = Color(0xFFFF0055),
        tertiary = Color(0xFFFFE600),
        background = Color(0xFF080912),
        surface = Color(0xFF0F1222),
        surfaceCard = Color(0xFF161A30),
        surfaceBorder = Color(0xFF22284A),
        surfaceElevated = Color(0xFF1C223E)
    ),
    AppThemePalette(
        id = "amber_gold",
        name = "Solar Amber Gold",
        description = "Warm analog turntable vinyl glow with rich amber",
        primary = Color(0xFFFFB300),
        primaryLight = Color(0xFFFFC738),
        primaryDark = Color(0xFFC68400),
        secondary = Color(0xFFFF5722),
        tertiary = Color(0xFFFF80AB),
        background = Color(0xFF110D09),
        surface = Color(0xFF1C150F),
        surfaceCard = Color(0xFF261D15),
        surfaceBorder = Color(0xFF382A1E),
        surfaceElevated = Color(0xFF30241A)
    ),
    AppThemePalette(
        id = "amethyst",
        name = "Midnight Amethyst",
        description = "Deep velvet purple and luminous electric violet",
        primary = Color(0xFFBB86FC),
        primaryLight = Color(0xFFD4B0FF),
        primaryDark = Color(0xFF884ECE),
        secondary = Color(0xFF03DAC6),
        tertiary = Color(0xFFFF4081),
        background = Color(0xFF0C0814),
        surface = Color(0xFF150F22),
        surfaceCard = Color(0xFF1F1633),
        surfaceBorder = Color(0xFF2F214D),
        surfaceElevated = Color(0xFF271C40)
    ),
    AppThemePalette(
        id = "crimson",
        name = "Vacuum Tube Crimson",
        description = "Glowing audio vacuum tube filament red and warm flame",
        primary = Color(0xFFFF3366),
        primaryLight = Color(0xFFFF668C),
        primaryDark = Color(0xFFC70038),
        secondary = Color(0xFFFF9900),
        tertiary = Color(0xFF00FFCC),
        background = Color(0xFF12080A),
        surface = Color(0xFF1C0D10),
        surfaceCard = Color(0xFF291318),
        surfaceBorder = Color(0xFF3D1D24),
        surfaceElevated = Color(0xFF33181E)
    ),
    AppThemePalette(
        id = "matrix",
        name = "Matrix Phosphor",
        description = "CRT oscilloscope phosphor green with deep cyber matrix",
        primary = Color(0xFF00FF66),
        primaryLight = Color(0xFF5CFF9E),
        primaryDark = Color(0xFF00B849),
        secondary = Color(0xFF33FF33),
        tertiary = Color(0xFF00CC44),
        background = Color(0xFF040D07),
        surface = Color(0xFF08170D),
        surfaceCard = Color(0xFF0E2415),
        surfaceBorder = Color(0xFF163821),
        surfaceElevated = Color(0xFF132E1C)
    ),
    AppThemePalette(
        id = "sapphire",
        name = "Sapphire Ocean",
        description = "Crisp deep oceanic blue with vibrant azure peaks",
        primary = Color(0xFF2979FF),
        primaryLight = Color(0xFF6DA3FF),
        primaryDark = Color(0xFF0050D8),
        secondary = Color(0xFF00E5FF),
        tertiary = Color(0xFF651FFF),
        background = Color(0xFF060D1A),
        surface = Color(0xFF0C172E),
        surfaceCard = Color(0xFF132447),
        surfaceBorder = Color(0xFF1D3568),
        surfaceElevated = Color(0xFF192F5C)
    ),
    AppThemePalette(
        id = "oled_stealth",
        name = "Pure OLED Stealth",
        description = "0% battery drain pitch black with clean monochrome accents",
        primary = Color(0xFFFFFFFF),
        primaryLight = Color(0xFFEEEEEE),
        primaryDark = Color(0xFFB0B0B0),
        secondary = Color(0xFFAAAAAA),
        tertiary = Color(0xFF777777),
        background = Color(0xFF000000),
        surface = Color(0xFF080808),
        surfaceCard = Color(0xFF121212),
        surfaceBorder = Color(0xFF222222),
        surfaceElevated = Color(0xFF1A1A1A)
    )
)

fun getActiveThemePalette(settings: AppSettings): AppThemePalette {
    if (settings.themePreset == "custom") {
        val prim = Color(settings.customPrimaryColor)
        val sec = Color(settings.customSecondaryColor)
        val bg = Color(settings.customBackgroundColor)
        return AppThemePalette(
            id = "custom",
            name = "Custom Color Studio",
            description = "Customized audiophile color palette",
            primary = prim,
            primaryLight = prim.copy(alpha = 0.85f),
            primaryDark = prim.copy(alpha = 0.7f),
            secondary = sec,
            tertiary = Color(0xFF00E5FF),
            background = bg,
            surface = Color(
                red = (bg.red + 0.04f).coerceAtMost(1f),
                green = (bg.green + 0.04f).coerceAtMost(1f),
                blue = (bg.blue + 0.06f).coerceAtMost(1f)
            ),
            surfaceCard = Color(
                red = (bg.red + 0.08f).coerceAtMost(1f),
                green = (bg.green + 0.08f).coerceAtMost(1f),
                blue = (bg.blue + 0.11f).coerceAtMost(1f)
            ),
            surfaceBorder = Color(
                red = (bg.red + 0.14f).coerceAtMost(1f),
                green = (bg.green + 0.14f).coerceAtMost(1f),
                blue = (bg.blue + 0.18f).coerceAtMost(1f)
            ),
            surfaceElevated = Color(
                red = (bg.red + 0.11f).coerceAtMost(1f),
                green = (bg.green + 0.11f).coerceAtMost(1f),
                blue = (bg.blue + 0.15f).coerceAtMost(1f)
            )
        )
    }
    val fallback = PRESET_THEMES.firstOrNull() ?: AppThemePalette(
        id = "fallback",
        name = "Fallback",
        description = "Emergency fallback",
        primary = Color(0xFF1DB954),
        primaryLight = Color(0xFF1ED760),
        primaryDark = Color(0xFF15883E),
        secondary = Color(0xFF1DB954),
        tertiary = Color(0xFFFFFFFF),
        background = Color(0xFF121212),
        surface = Color(0xFF121212),
        surfaceCard = Color(0xFF181818),
        surfaceBorder = Color(0xFF282828),
        surfaceElevated = Color(0xFF282828)
    )
    return PRESET_THEMES.find { it.id == settings.themePreset } ?: fallback
}

val LocalAppColors = staticCompositionLocalOf {
    PRESET_THEMES.firstOrNull() ?: AppThemePalette(
        id = "fallback",
        name = "Fallback",
        description = "Emergency fallback",
        primary = Color(0xFF1DB954),
        primaryLight = Color(0xFF1ED760),
        primaryDark = Color(0xFF15883E),
        secondary = Color(0xFF1DB954),
        tertiary = Color(0xFFFFFFFF),
        background = Color(0xFF121212),
        surface = Color(0xFF121212),
        surfaceCard = Color(0xFF181818),
        surfaceBorder = Color(0xFF282828),
        surfaceElevated = Color(0xFF282828)
    )
}

object ObsidianTheme {
    val colors: AppThemePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val scaling: AdaptiveUiScaling
        @Composable
        @ReadOnlyComposable
        get() = LocalAdaptiveScaling.current
}

@Composable
fun MyApplicationTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit
) {
    val palette = getActiveThemePalette(settings)

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val fontScale = density.fontScale
    val densityDpi = configuration.densityDpi

    val scaleFactor = when {
        screenWidthDp >= 840 -> 1.22f
        screenWidthDp >= 600 -> 1.12f
        screenWidthDp < 340 -> 0.88f
        else -> 1.0f
    }.coerceIn(0.85f, 1.30f)

    val adaptiveScaling = remember(screenWidthDp, screenHeightDp, densityDpi, fontScale) {
        AdaptiveUiScaling(
            scaleFactor = scaleFactor,
            buttonMinHeight = (48.dp * scaleFactor).coerceAtLeast(48.dp),
            touchTargetMinSize = (48.dp * scaleFactor).coerceAtLeast(48.dp),
            cardPadding = (12.dp * scaleFactor).coerceAtLeast(8.dp),
            iconSizeSmall = (18.dp * scaleFactor).coerceAtLeast(16.dp),
            iconSizeMedium = (24.dp * scaleFactor).coerceAtLeast(20.dp),
            iconSizeLarge = (32.dp * scaleFactor).coerceAtLeast(28.dp),
            spacingSmall = (6.dp * scaleFactor).coerceAtLeast(4.dp),
            spacingMedium = (12.dp * scaleFactor).coerceAtLeast(8.dp),
            spacingLarge = (20.dp * scaleFactor).coerceAtLeast(16.dp),
            cornerRadiusSmall = (8.dp * scaleFactor).coerceAtLeast(6.dp),
            cornerRadiusMedium = (12.dp * scaleFactor).coerceAtLeast(10.dp),
            cornerRadiusLarge = (20.dp * scaleFactor).coerceAtLeast(16.dp)
        )
    }

    val colorScheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = Color.Black,
        primaryContainer = palette.primaryDark,
        onPrimaryContainer = Color.White,
        secondary = palette.secondary,
        onSecondary = Color.Black,
        secondaryContainer = palette.secondary.copy(alpha = 0.3f),
        onSecondaryContainer = palette.secondary,
        tertiary = palette.tertiary,
        onTertiary = Color.Black,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceCard,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.surfaceBorder
    )

    CompositionLocalProvider(
        LocalAppColors provides palette,
        LocalAdaptiveScaling provides adaptiveScaling
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
