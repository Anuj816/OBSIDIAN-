package com.anuj.obsidian.ui.components

import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.ui.animation.bounceClickable
import com.anuj.obsidian.ui.theme.ObsidianTheme

/**
 * Tasteful empty state with gentle breathing/floating motion.
 */
@Composable
fun ObsidianEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null
) {
    val appColors = ObsidianTheme.colors

    // Entrance & breathing animation specs
    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
    }

    val cardAlphaState = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard,
        label = "empty_state_card_alpha"
    )
    val cardScaleState = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.94f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
        label = "empty_state_card_scale"
    )
    val floatOffsetState = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 18f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmooth,
        label = "empty_state_float"
    )
    val iconScaleState = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.6f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.springSmooth(dampingRatio = 0.75f, stiffness = 350f),
        label = "empty_state_scale"
    )
    val glowAlphaState = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0.2f else 0f,
        animationSpec = com.anuj.obsidian.ui.animation.MotionSpec.TweenStandard,
        label = "empty_state_glow"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = appColors.surfaceCard.copy(alpha = 0.70f),
        border = BorderStroke(1.dp, appColors.surfaceBorder.copy(alpha = 0.65f)),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlphaState.value
                scaleX = cardScaleState.value
                scaleY = cardScaleState.value
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Floating & breathing icon container with scale-in entrance
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = floatOffsetState.value
                        scaleX = iconScaleState.value
                        scaleY = iconScaleState.value
                    }
            ) {
                // Ambient pulsating glow halo
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            alpha = glowAlphaState.value * 5f
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    appColors.primary.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Central icon plate
                Surface(
                    shape = CircleShape,
                    color = appColors.surfaceElevated,
                    border = BorderStroke(1.5.dp, appColors.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = appColors.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                color = appColors.textPrimary,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                color = appColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.primary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                    modifier = Modifier.bounceClickable(
                        scaleDown = 0.92f,
                        shape = RoundedCornerShape(12.dp),
                        onClick = onActionClick
                    )
                ) {
                    if (actionIcon != null) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = actionLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
