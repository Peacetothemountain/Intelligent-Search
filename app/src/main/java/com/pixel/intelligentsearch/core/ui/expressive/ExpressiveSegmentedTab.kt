package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex

/**
 * Material 3 Expressive Elastic Segmented Tab with Dynamic Weight Expansion and Corner Morphing.
 *
 * Tapping a tab triggers an elastic push ("dhakka") effect, expanding the active tab's layout weight
 * while compressing adjacent tabs, morphing corner geometry from Pill (24.dp) to Squircle (12.dp).
 */
@Composable
fun RowScope.ExpressiveSegmentedTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    activeCornerRadius: Dp = 12.dp,
    inactiveCornerRadius: Dp = 24.dp,
    activeContainerColor: Color = MaterialTheme.colorScheme.primary,
    inactiveContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val context = LocalContext.current
    val view = LocalView.current
    val hapticEngine = remember(context) { PixelHapticEngine.get(context) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Elastic layout weight push physics
    val weightAnim by animateFloatAsState(
        targetValue = if (selected) 1.34f else 0.72f,
        animationSpec = ExpressiveMotionTokens.elasticSpring(),
        label = "tab_weight"
    )

    // Corner morphing: Immediate tactile squash on touch down (10.dp) -> Active Squircle (12.dp) -> Inactive Pill (24.dp)
    val targetCorner = when {
        isPressed -> 10.dp
        selected -> activeCornerRadius
        else -> inactiveCornerRadius
    }

    val cornerRadiusAnim by animateDpAsState(
        targetValue = targetCorner,
        animationSpec = ExpressiveMotionTokens.morphSpring(),
        label = "tab_corner"
    )

    // Touch-down tactile scale compression
    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed) ExpressiveMotionTokens.PRESS_SCALE_BUTTON else 1f,
        animationSpec = ExpressiveMotionTokens.morphSpring(),
        label = "tab_press_scale"
    )

    val currentBgColor by animateColorAsState(
        targetValue = if (selected) activeContainerColor else inactiveContainerColor,
        animationSpec = ExpressiveMotionTokens.gentleSpring(),
        label = "tab_bg"
    )

    val currentContentColor by animateColorAsState(
        targetValue = if (selected) activeContentColor else inactiveContentColor,
        animationSpec = ExpressiveMotionTokens.gentleSpring(),
        label = "tab_content"
    )

    val shape = remember(cornerRadiusAnim) { RoundedCornerShape(cornerRadiusAnim) }

    Box(
        modifier = modifier
            .weight(weightAnim)
            .height(44.dp)
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
                this.shape = shape
                clip = true
            }
            .background(currentBgColor)
            .border(
                width = 1.dp,
                color = if (selected) activeContainerColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    hapticEngine.performHaptic(view, PixelHapticType.TICK)
                    onClick()
                }
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = currentContentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = currentContentColor,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = GoogleSansFlex,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
