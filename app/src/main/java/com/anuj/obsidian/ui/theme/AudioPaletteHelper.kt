package com.anuj.obsidian.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.anuj.obsidian.data.model.DownloadedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Advanced Dynamic Audio Palette Helper.
 * Extracts rich, vibrant, muted, and background colors from album art,
 * generates harmonized custom app palettes, and provides curated audiophile color schemes.
 */
data class ExtractedAudioPalette(
    val vibrantAccent: Color,
    val secondaryAccent: Color,
    val glowColor: Color,
    val canvasBackground: Color,
    val surfaceCard: Color,
    val surfaceBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

data class CustomPalettePreset(
    val id: String,
    val name: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val backgroundHex: Long
)

object AudioPaletteHelper {

    val CuratedPalettePresets = listOf(
        CustomPalettePreset("sunset_synth", "Sunset Synthwave", 0xFFFF0055L, 0xFFFFB300L, 0xFF0D0814L),
        CustomPalettePreset("aurora_borealis", "Aurora Borealis", 0xFF00FFCCL, 0xFF76FF03L, 0xFF040E10L),
        CustomPalettePreset("neon_cyber", "Cyberpunk Cyan", 0xFF00E5FFL, 0xFFFF0055L, 0xFF080A12L),
        CustomPalettePreset("golden_vinyl", "Gold Turntable", 0xFFFFB300L, 0xFFFF5722L, 0xFF110D09L),
        CustomPalettePreset("amethyst_dream", "Midnight Amethyst", 0xFFBB86FCL, 0xFF03DAC6L, 0xFF0C0814L),
        CustomPalettePreset("electric_sapphire", "Sapphire Ocean", 0xFF2979FFL, 0xFF00E5FFL, 0xFF060D1AL),
        CustomPalettePreset("vacuum_tube", "Tube Amplifier", 0xFFFF3366L, 0xFFFF9900L, 0xFF12080AL),
        CustomPalettePreset("matrix_green", "Matrix Phosphor", 0xFF00FF66L, 0xFF33FF33L, 0xFF040D07L),
        CustomPalettePreset("sakura_pink", "Tokyo Neon Pink", 0xFFFF4081L, 0xFFE040FBL, 0xFF120710L),
        CustomPalettePreset("acid_lemon", "Acid Techno", 0xFFAEEA00L, 0xFF00E5FFL, 0xFF080F03L),
        CustomPalettePreset("deep_space", "Deep Space Violet", 0xFF9C27B0L, 0xFF3F51B5L, 0xFF07050EL),
        CustomPalettePreset("volcano_red", "Volcanic Magma", 0xFFFF3D00L, 0xFFFFC107L, 0xFF120503L),
        CustomPalettePreset("emerald_glow", "Spotify Emerald", 0xFF1DB954L, 0xFF1ED760L, 0xFF000000L),
        CustomPalettePreset("pure_monochrome", "Monochrome Stealth", 0xFFFFFFFFL, 0xFFAAAAAAL, 0xFF000000L),
        CustomPalettePreset("laser_lime", "Laser Cyber Lime", 0xFF00FF00L, 0xFF00FFFFL, 0xFF000A02L),
        CustomPalettePreset("nordic_frost", "Nordic Frost Blue", 0xFF80D8FFL, 0xFF82B1FFL, 0xFF081018L)
    )

    /**
     * Intelligently extracts vibrant and dark palette colors from album art bitmap or URI.
     */
    suspend fun extractPaletteFromTrack(context: Context, track: DownloadedTrack): ExtractedAudioPalette = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            // First try FastAlbumArtHelper which checks memory cache, direct paths, content uris, and files
            bitmap = com.anuj.obsidian.data.local.FastAlbumArtHelper.getArtworkBitmap(context, track, targetSize = 120)
            
            if (bitmap == null) {
                val artworkUriStr = track.albumArtUrl
                if (artworkUriStr.isNotBlank()) {
                    if (artworkUriStr.startsWith("/")) {
                        val f = java.io.File(artworkUriStr)
                        if (f.exists() && f.length() > 64L) {
                            bitmap = decodeSampledBitmapFromFile(f.absolutePath, 120, 120)
                        }
                    } else if (artworkUriStr.startsWith("content://") || artworkUriStr.startsWith("file://")) {
                        val uri = Uri.parse(artworkUriStr)
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            bitmap = decodeSampledBitmapFromStream(stream, 120, 120)
                        }
                    }
                }
            }

            if (bitmap == null && track.localFilePath.isNotBlank()) {
                val mediaMetadataRetriever = android.media.MediaMetadataRetriever()
                try {
                    val file = java.io.File(track.localFilePath)
                    if (file.exists()) {
                        mediaMetadataRetriever.setDataSource(file.absolutePath)
                        val embeddedArt = mediaMetadataRetriever.embeddedPicture
                        if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                            bitmap = decodeSampledBitmapFromByteArray(embeddedArt, 120, 120)
                        }
                    }
                } catch (e: Exception) {
                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPaletteHelper.extractPaletteFromTrack - attempt")
                } finally {
                    try { 
                        mediaMetadataRetriever.release() 
                    } catch (e: Exception) {
                        com.anuj.obsidian.ObsidianCrashLogger.logWarning("AudioPaletteHelper", "MediaMetadataRetriever release notice: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPaletteHelper.extractPaletteFromTrack - attempt")
        }

        val extracted = bitmap?.let { if (!it.isRecycled && it.width > 0 && it.height > 0) extractColorsFromBitmap(it) else null }
        return@withContext extracted ?: generateDefaultFallbackPalette()
    }

    private fun decodeSampledBitmapFromByteArray(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPaletteHelper.decodeSampledBitmapFromByteArray - attempt")
             null }
    }

    private fun decodeSampledBitmapFromStream(inputStream: InputStream, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val byteBuffer = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            var read: Int
            var totalRead = 0
            while (inputStream.read(buffer).also { read = it } != -1) {
                byteBuffer.write(buffer, 0, read)
                totalRead += read
                if (totalRead > 12 * 1024 * 1024) {
                    throw java.io.IOException("Stream exceeds 12MB limit")
                }
            }
            val bytes = byteBuffer.toByteArray()
            decodeSampledBitmapFromByteArray(bytes, reqWidth, reqHeight)
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPaletteHelper.decodeSampledBitmapFromStream - attempt")
             null }
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPaletteHelper.decodeSampledBitmapFromFile - attempt")
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun extractColorsFromBitmap(bitmap: Bitmap): ExtractedAudioPalette {
        if (bitmap.isRecycled) return generateDefaultFallbackPalette()
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return generateDefaultFallbackPalette()
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var vibrantMaxSat = 0f
        var vibrantColorInt = 0xFF1DB954.toInt()
        
        var secMaxSat = 0f
        var secColorInt = 0xFF00E5FF.toInt()

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var pixelCount = 0

        val hsv = FloatArray(3)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            
            // Skip pure blacks or pure whites for vibrant extraction
            if ((r < 15 && g < 15 && b < 15) || (r > 245 && g > 245 && b > 245)) continue

            totalR += r
            totalG += g
            totalB += b
            pixelCount++

            android.graphics.Color.colorToHSV(p, hsv)
            val sat = hsv[1]
            val valVal = hsv[2]

            if (sat > vibrantMaxSat && valVal > 0.35f) {
                secMaxSat = vibrantMaxSat
                secColorInt = vibrantColorInt
                vibrantMaxSat = sat
                vibrantColorInt = p
            } else if (sat > secMaxSat && valVal > 0.3f && Math.abs(hsv[0] - hsv[0]) > 20f) {
                secMaxSat = sat
                secColorInt = p
            }
        }

        val primaryColor = Color(vibrantColorInt)
        val secondaryColor = Color(secColorInt)

        val avgR = if (pixelCount > 0) (totalR / pixelCount).toInt() else 12
        val avgG = if (pixelCount > 0) (totalG / pixelCount).toInt() else 12
        val avgB = if (pixelCount > 0) (totalB / pixelCount).toInt() else 18

        // Compute deep ambient background based on artwork average tint
        val bgRed = (avgR * 0.12f / 255f).coerceIn(0.02f, 0.12f)
        val bgGreen = (avgG * 0.12f / 255f).coerceIn(0.02f, 0.12f)
        val bgBlue = (avgB * 0.15f / 255f).coerceIn(0.03f, 0.15f)
        val bgColor = Color(red = bgRed, green = bgGreen, blue = bgBlue)

        val cardColor = Color(
            red = (bgRed + 0.05f).coerceAtMost(0.25f),
            green = (bgGreen + 0.05f).coerceAtMost(0.25f),
            blue = (bgBlue + 0.07f).coerceAtMost(0.28f)
        )

        val borderColor = Color(
            red = (bgRed + 0.12f).coerceAtMost(0.35f),
            green = (bgGreen + 0.12f).coerceAtMost(0.35f),
            blue = (bgBlue + 0.15f).coerceAtMost(0.38f)
        )

        return ExtractedAudioPalette(
            vibrantAccent = primaryColor,
            secondaryAccent = secondaryColor,
            glowColor = primaryColor.copy(alpha = 0.6f),
            canvasBackground = bgColor,
            surfaceCard = cardColor,
            surfaceBorder = borderColor,
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8)
        )
        } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AudioPaletteHelper.extractColorsFromBitmap - attempt")
            
            generateDefaultFallbackPalette()
        }
    }

    fun generateDefaultFallbackPalette(): ExtractedAudioPalette {
        return ExtractedAudioPalette(
            vibrantAccent = Color(0xFF1DB954),
            secondaryAccent = Color(0xFF00E5FF),
            glowColor = Color(0x991DB954),
            canvasBackground = Color(0xFF060910),
            surfaceCard = Color(0xFF111723),
            surfaceBorder = Color(0xFF1E2838),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8)
        )
    }

    /**
     * Converts custom primary, secondary, and background colors into a full harmonized AppThemePalette
     */
    fun createHarmonizedThemePalette(primary: Color, secondary: Color, bg: Color): AppThemePalette {
        val bgLuminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
        val isLight = bgLuminance > 0.5f

        val surface = if (isLight) {
            Color(
                red = (bg.red - 0.04f).coerceAtLeast(0f),
                green = (bg.green - 0.04f).coerceAtLeast(0f),
                blue = (bg.blue - 0.04f).coerceAtLeast(0f)
            )
        } else {
            Color(
                red = (bg.red + 0.04f).coerceAtMost(1f),
                green = (bg.green + 0.04f).coerceAtMost(1f),
                blue = (bg.blue + 0.06f).coerceAtMost(1f)
            )
        }

        val surfaceCard = if (isLight) {
            Color(
                red = (bg.red - 0.07f).coerceAtLeast(0f),
                green = (bg.green - 0.07f).coerceAtLeast(0f),
                blue = (bg.blue - 0.07f).coerceAtLeast(0f)
            )
        } else {
            Color(
                red = (bg.red + 0.08f).coerceAtMost(1f),
                green = (bg.green + 0.08f).coerceAtMost(1f),
                blue = (bg.blue + 0.11f).coerceAtMost(1f)
            )
        }

        val surfaceBorder = if (isLight) {
            Color(
                red = (bg.red - 0.18f).coerceAtLeast(0f),
                green = (bg.green - 0.18f).coerceAtLeast(0f),
                blue = (bg.blue - 0.18f).coerceAtLeast(0f)
            )
        } else {
            Color(
                red = (bg.red + 0.14f).coerceAtMost(1f),
                green = (bg.green + 0.14f).coerceAtMost(1f),
                blue = (bg.blue + 0.18f).coerceAtMost(1f)
            )
        }

        val surfaceElevated = if (isLight) {
            Color(
                red = (bg.red - 0.02f).coerceAtLeast(0f),
                green = (bg.green - 0.02f).coerceAtLeast(0f),
                blue = (bg.blue - 0.02f).coerceAtLeast(0f)
            )
        } else {
            Color(
                red = (bg.red + 0.11f).coerceAtMost(1f),
                green = (bg.green + 0.11f).coerceAtMost(1f),
                blue = (bg.blue + 0.15f).coerceAtMost(1f)
            )
        }

        val textPrimary = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)
        val textSecondary = if (isLight) Color(0xFF334155) else Color(0xFF94A3B8)
        val textMuted = if (isLight) Color(0xFF64748B) else Color(0xFF64748B)

        return AppThemePalette(
            id = "custom",
            name = "Custom Color Studio",
            description = "Customized audiophile color palette",
            primary = primary,
            primaryLight = primary.copy(alpha = 0.85f),
            primaryDark = primary.copy(alpha = 0.7f),
            secondary = secondary,
            tertiary = Color(0xFF00E5FF),
            background = bg,
            surface = surface,
            surfaceCard = surfaceCard,
            surfaceBorder = surfaceBorder,
            surfaceElevated = surfaceElevated,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted
        )
    }
}
