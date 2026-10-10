package com.anuj.obsidian.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.theme.AudioPaletteHelper
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class ColorTarget {
    PRIMARY,
    SECONDARY,
    BACKGROUND
}

/**
 * Converts HSV to Compose Color
 */
fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
    val hsv = floatArrayOf(
        hue.coerceIn(0f, 360f),
        saturation.coerceIn(0f, 1f),
        value.coerceIn(0f, 1f)
    )
    val argb = android.graphics.Color.HSVToColor((alpha * 255).toInt().coerceIn(0, 255), hsv)
    return Color(argb)
}

/**
 * Converts Compose Color to HSV (Hue: 0..360, Saturation: 0..1, Value: 0..1)
 */
fun colorToHsv(color: Color): Triple<Float, Float, Float> {
    val hsv = FloatArray(3)
    val argb = android.graphics.Color.argb(
        (color.alpha * 255).toInt().coerceIn(0, 255),
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255)
    )
    android.graphics.Color.colorToHSV(argb, hsv)
    return Triple(hsv[0], hsv[1], hsv[2])
}

/**
 * Converts Color to 32-bit ARGB Long (e.g. 0xFF1DB954L)
 */
fun colorToLong(color: Color): Long {
    val argb = color.toArgb()
    return argb.toLong() and 0xFFFFFFFFL
}

/**
 * Formats a Compose Color as #RRGGBB Hex string
 */
fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt().coerceIn(0, 255)
    val g = (color.green * 255).toInt().coerceIn(0, 255)
    val b = (color.blue * 255).toInt().coerceIn(0, 255)
    return String.format(java.util.Locale.US, "#%02X%02X%02X", r, g, b)
}

/**
 * Parses Hex String (#RRGGBB or RRGGBB) to Compose Color, or null if invalid
 */
fun parseHexColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    return try {
        if (clean.length == 6) {
            val colorInt = android.graphics.Color.parseColor("#$clean")
            Color(colorInt)
        } else if (clean.length == 8) {
            val colorInt = android.graphics.Color.parseColor("#$clean")
            Color(colorInt)
        } else {
            null
        }
    } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "ColorWheelPicker.parseHexColor - attempt")
            
        null
    }
}

/**
 * Interactive Circular Color Wheel Composable
 */
@Composable
fun InteractiveColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChanged: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val wheelColors = remember {
        listOf(
            Color(0xFFFF0000), // Red 0°
            Color(0xFFFFFF00), // Yellow 60°
            Color(0xFF00FF00), // Green 120°
            Color(0xFF00FFFF), // Cyan 180°
            Color(0xFF0000FF), // Blue 240°
            Color(0xFFFF00FF), // Magenta 300°
            Color(0xFFFF0000)  // Red 360°
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val wheelRadius = (min(size.width, size.height) / 2f) - 4.dp.toPx()
                        val maxHandleRadius = (wheelRadius - 13.dp.toPx()).coerceAtLeast(1f)

                        fun updateFromOffset(x: Float, y: Float) {
                            val dx = x - centerX
                            val dy = y - centerY
                            val dist = sqrt(dx * dx + dy * dy)
                            val newSat = (dist / maxHandleRadius).coerceIn(0f, 1f)
                            var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angleDeg < 0) angleDeg += 360f
                            onColorChanged(angleDeg, newSat)
                        }

                        updateFromOffset(down.position.x, down.position.y)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                            updateFromOffset(change.position.x, change.position.y)
                        }
                    }
                }
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val wheelRadius = (min(size.width, size.height) / 2f) - 4.dp.toPx()
            val maxHandleRadius = (wheelRadius - 13.dp.toPx()).coerceAtLeast(1f)
            val centerOffset = Offset(centerX, centerY)

            // 1. Draw Sweep Rainbow Hue Gradient
            drawCircle(
                brush = Brush.sweepGradient(wheelColors, centerOffset),
                radius = wheelRadius,
                center = centerOffset
            )

            // 2. Draw Saturation (Radial White Gradient in center fading to transparent)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 1f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = centerOffset,
                    radius = wheelRadius
                ),
                radius = wheelRadius,
                center = centerOffset
            )

            // 3. Draw Darken Overlay if brightness / value < 1f
            if (value < 0.99f) {
                drawCircle(
                    color = Color.Black.copy(alpha = (1f - value).coerceIn(0f, 0.92f)),
                    radius = wheelRadius,
                    center = centerOffset
                )
            }

            // 4. Outer Stylized Ring Border
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = wheelRadius,
                center = centerOffset,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.7f),
                radius = wheelRadius + 2.dp.toPx(),
                center = centerOffset,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 5. Calculate selector handle position inside safe boundary
            val angleRad = Math.toRadians(hue.toDouble())
            val handleDistance = (saturation.coerceIn(0f, 1f) * maxHandleRadius)
            val handleX = centerX + (handleDistance * cos(angleRad)).toFloat()
            val handleY = centerY + (handleDistance * sin(angleRad)).toFloat()
            val handlePos = Offset(handleX, handleY)

            val currentColor = hsvToColor(hue, saturation, value)

            // Draw Selector Reticle Target (stays perfectly framed within wheel)
            drawCircle(
                color = Color.Black.copy(alpha = 0.85f),
                radius = 13.dp.toPx(),
                center = handlePos,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = 11.dp.toPx(),
                center = handlePos,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = currentColor,
                radius = 8.5.dp.toPx(),
                center = handlePos
            )
        }
    }
}

/**
 * Full Interactive Color Wheel Modal Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorWheelPickerSheet(
    initialPrimary: Long,
    initialSecondary: Long,
    initialBackground: Long,
    initialTarget: ColorTarget = ColorTarget.PRIMARY,
    currentTrack: DownloadedTrack? = null,
    onDismiss: () -> Unit,
    onApplyColors: (primary: Long, secondary: Long, background: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val appColors = ObsidianTheme.colors

    var activeTarget by remember { mutableStateOf(initialTarget) }

    // Color states for all 3 channels
    var primaryColor by remember { mutableStateOf(Color(initialPrimary)) }
    var secondaryColor by remember { mutableStateOf(Color(initialSecondary)) }
    var backgroundColor by remember { mutableStateOf(Color(initialBackground)) }

    val initialActiveCol = when (initialTarget) {
        ColorTarget.PRIMARY -> Color(initialPrimary)
        ColorTarget.SECONDARY -> Color(initialSecondary)
        ColorTarget.BACKGROUND -> Color(initialBackground)
    }
    val (initialH, initialS, initialV) = remember { colorToHsv(initialActiveCol) }

    var currentHue by remember { mutableFloatStateOf(initialH) }
    var currentSat by remember { mutableFloatStateOf(initialS) }
    var currentVal by remember { mutableFloatStateOf(initialV) }
    var hexInputText by remember { mutableStateOf(colorToHex(initialActiveCol)) }

    val activeColor = remember(currentHue, currentSat, currentVal) {
        hsvToColor(currentHue, currentSat, currentVal)
    }

    fun updateActiveHsv(newH: Float, newS: Float, newV: Float) {
        currentHue = newH
        currentSat = newS
        currentVal = newV
        val updatedColor = hsvToColor(newH, newS, newV)
        when (activeTarget) {
            ColorTarget.PRIMARY -> primaryColor = updatedColor
            ColorTarget.SECONDARY -> secondaryColor = updatedColor
            ColorTarget.BACKGROUND -> backgroundColor = updatedColor
        }
        hexInputText = colorToHex(updatedColor)
    }

    fun switchActiveTarget(newTarget: ColorTarget) {
        activeTarget = newTarget
        val col = when (newTarget) {
            ColorTarget.PRIMARY -> primaryColor
            ColorTarget.SECONDARY -> secondaryColor
            ColorTarget.BACKGROUND -> backgroundColor
        }
        val (h, s, v) = colorToHsv(col)
        currentHue = h
        currentSat = s
        currentVal = v
        hexInputText = colorToHex(col)
    }

    fun setAllColors(p: Color, s: Color, bg: Color) {
        primaryColor = p
        secondaryColor = s
        backgroundColor = bg
        val activeCol = when (activeTarget) {
            ColorTarget.PRIMARY -> p
            ColorTarget.SECONDARY -> s
            ColorTarget.BACKGROUND -> bg
        }
        val (h, sat, v) = colorToHsv(activeCol)
        currentHue = h
        currentSat = sat
        currentVal = v
        hexInputText = colorToHex(activeCol)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.surfaceCard,
        contentColor = appColors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(appColors.textMuted.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(activeColor)
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = if (currentVal > 0.6f && currentSat < 0.3f) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "COLOR WHEEL STUDIO",
                            color = primaryColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Interactive Theme Designer",
                            color = appColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(appColors.surfaceElevated)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = appColors.textSecondary)
                }
            }

            // Target Selector Tabs (Primary, Secondary, Background Tone)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appColors.surface)
                    .border(1.dp, appColors.surfaceBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val targets = listOf(
                    Triple(ColorTarget.PRIMARY, "Primary Accent", primaryColor),
                    Triple(ColorTarget.SECONDARY, "Secondary", secondaryColor),
                    Triple(ColorTarget.BACKGROUND, "Canvas Tone", backgroundColor)
                )

                targets.forEach { (target, label, col) ->
                    val isSelected = activeTarget == target
                    Surface(
                        onClick = { switchActiveTarget(target) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) appColors.surfaceElevated else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.5.dp, primaryColor) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = if (isSelected) primaryColor else appColors.textSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 1. Interactive Color Wheel Canvas
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                InteractiveColorWheel(
                    hue = currentHue,
                    saturation = currentSat,
                    value = currentVal,
                    onColorChanged = { newHue, newSat ->
                        updateActiveHsv(newHue, newSat, currentVal)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. Brightness / Value Slider
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Luminance / Brightness",
                            color = appColors.textSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${(currentVal * 100).toInt()}%",
                        color = primaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Gradient background track for brightness slider
                val hueFullColor = remember(currentHue, currentSat) { hsvToColor(currentHue, currentSat, 1f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black, hueFullColor)
                            )
                        )
                        .border(1.dp, appColors.surfaceBorder, RoundedCornerShape(7.dp))
                )

                Slider(
                    value = currentVal,
                    onValueChange = { updateActiveHsv(currentHue, currentSat, it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = appColors.surfaceElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Saturation Fine-Tune Slider
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.ColorLens, contentDescription = null, tint = secondaryColor, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Color Saturation",
                            color = appColors.textSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${(currentSat * 100).toInt()}%",
                        color = secondaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                val satZeroColor = remember(currentVal) { hsvToColor(0f, 0f, currentVal) }
                val satFullColor = remember(currentHue, currentVal) { hsvToColor(currentHue, 1f, currentVal) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(satZeroColor, satFullColor)
                            )
                        )
                        .border(1.dp, appColors.surfaceBorder, RoundedCornerShape(7.dp))
                )

                Slider(
                    value = currentSat,
                    onValueChange = { updateActiveHsv(currentHue, it, currentVal) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = secondaryColor,
                        inactiveTrackColor = appColors.surfaceElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Hex Input, RGB Badges, and Clipboard Controls
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = appColors.surface,
                border = BorderStroke(1.dp, appColors.surfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(activeColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        )

                        OutlinedTextField(
                            value = hexInputText,
                            onValueChange = { input ->
                                hexInputText = input.uppercase()
                                val parsed = parseHexColor(input)
                                if (parsed != null) {
                                    val (h, s, v) = colorToHsv(parsed)
                                    updateActiveHsv(h, s, v)
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = appColors.surfaceBorder,
                                focusedContainerColor = appColors.surfaceElevated,
                                unfocusedContainerColor = appColors.surfaceElevated
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(130.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(colorToHex(activeColor)))
                                Toast.makeText(context, "Copied ${colorToHex(activeColor)} to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Hex", tint = appColors.textSecondary, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    val parsed = parseHexColor(clip)
                                    if (parsed != null) {
                                        val (h, s, v) = colorToHsv(parsed)
                                        updateActiveHsv(h, s, v)
                                        Toast.makeText(context, "Pasted $clip", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Invalid hex code in clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste Hex", tint = primaryColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // 5. Harmonize & Palette Generator Tools
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Palette Studio Helper",
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Auto-Extractor & Harmony",
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (currentTrack != null) {
                    Button(
                        onClick = {
                            scope.launch {
                                val palette = AudioPaletteHelper.extractPaletteFromTrack(context, currentTrack)
                                setAllColors(palette.vibrantAccent, palette.secondaryAccent, palette.canvasBackground)
                                Toast.makeText(context, "Extracted palette from ${currentTrack.title}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor.copy(alpha = 0.15f),
                            contentColor = primaryColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, primaryColor),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Extract Color Scheme From Current Album Art",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Complementary Harmonizer
                    Button(
                        onClick = {
                            val compHue = (currentHue + 180f) % 360f
                            val compCol = hsvToColor(compHue, currentSat.coerceAtLeast(0.7f), 0.95f)
                            secondaryColor = compCol
                            if (activeTarget == ColorTarget.SECONDARY) {
                                val (h, s, v) = colorToHsv(compCol)
                                updateActiveHsv(h, s, v)
                            }
                            Toast.makeText(context, "Generated complementary secondary accent", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.surfaceElevated,
                            contentColor = appColors.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, appColors.surfaceBorder),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Complementary", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Analogous Harmonizer
                    Button(
                        onClick = {
                            val anaHue = (currentHue + 35f) % 360f
                            val anaCol = hsvToColor(anaHue, currentSat.coerceAtLeast(0.8f), 0.95f)
                            secondaryColor = anaCol
                            if (activeTarget == ColorTarget.SECONDARY) {
                                val (h, s, v) = colorToHsv(anaCol)
                                updateActiveHsv(h, s, v)
                            }
                            Toast.makeText(context, "Generated analogous secondary accent", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.surfaceElevated,
                            contentColor = appColors.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, appColors.surfaceBorder),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Colorize, contentDescription = null, tint = secondaryColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Analogous", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Deep Canvas Darkifier
                    Button(
                        onClick = {
                            val bgCol = hsvToColor(currentHue, 0.45f, 0.06f)
                            backgroundColor = bgCol
                            if (activeTarget == ColorTarget.BACKGROUND) {
                                val (h, s, v) = colorToHsv(bgCol)
                                updateActiveHsv(h, s, v)
                            }
                            Toast.makeText(context, "Generated tinted deep canvas", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.surfaceElevated,
                            contentColor = appColors.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, appColors.surfaceBorder),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("OLED Tint", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 6. Quick Swatches Row with Curated Studio Palettes
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Curated Studio Palette Presets",
                    color = appColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AudioPaletteHelper.CuratedPalettePresets) { preset ->
                        val pCol = Color(preset.primaryHex)
                        val sCol = Color(preset.secondaryHex)
                        val bgCol = Color(preset.backgroundHex)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = appColors.surfaceElevated,
                            border = BorderStroke(
                                1.5.dp,
                                if (colorToHex(primaryColor) == colorToHex(pCol)) primaryColor else appColors.surfaceBorder
                            ),
                            modifier = Modifier.clickable {
                                setAllColors(pCol, sCol, bgCol)
                                Toast.makeText(context, "Selected ${preset.name}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(pCol)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(sCol)
                                )
                                Text(
                                    text = preset.name,
                                    color = appColors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 7. Live Theme Mini Preview Card
            Card(
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE THEME PREVIEW",
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = secondaryColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, secondaryColor)
                        ) {
                            Text(
                                text = "24-bit / 192 kHz FLAC",
                                color = secondaryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(listOf(primaryColor, secondaryColor))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stairway to Heaven",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Led Zeppelin • Studio Master",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = primaryColor,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    // Mini Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                    }
                }
            }

            // 8. Action Buttons: Apply & Save, Reset
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val defaultPrim = 0xFF1DB954L
                        val defaultSec = 0xFFF5A623L
                        val defaultBg = 0xFF090C10L
                        primaryColor = Color(defaultPrim)
                        secondaryColor = Color(defaultSec)
                        backgroundColor = Color(defaultBg)
                        val (h, s, v) = colorToHsv(primaryColor)
                        currentHue = h
                        currentSat = s
                        currentVal = v
                        Toast.makeText(context, "Reset colors to defaults", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.surfaceElevated,
                        contentColor = appColors.textSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, appColors.surfaceBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val pLong = colorToLong(primaryColor)
                        val sLong = colorToLong(secondaryColor)
                        val bgLong = colorToLong(backgroundColor)
                        onApplyColors(pLong, sLong, bgLong)
                        Toast.makeText(context, "Custom theme applied & saved!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = if (currentVal > 0.6f && currentSat < 0.3f) Color.Black else Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(2f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply & Save Palette", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
