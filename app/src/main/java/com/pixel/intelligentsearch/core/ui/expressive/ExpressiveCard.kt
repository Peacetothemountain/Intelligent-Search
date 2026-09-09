package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticType

/**
 * Material 3 Expressive Tactile Squircle Card.
 *
 * Provides continuous corner curves, spring press scaling, elevation depth shift,
 * and synchronized tactile haptics.
 */
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
    cornerRadius: Dp = 20.dp,
    idleElevation: Dp = 0.dp,
    pressedElevation: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val hapticEngine = remember(context) { PixelHapticEngine.get(context) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            hapticEngine.performHaptic(view, PixelHapticType.TICK)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) ExpressiveMotionTokens.PRESS_SCALE_CARD else 1.0f,
        animationSpec = ExpressiveMotionTokens.bouncySpring(),
        label = "card_press_scale"
    )

    val currentElevation by animateDpAsState(
        targetValue = if (isPressed) pressedElevation else idleElevation,
        animationSpec = ExpressiveMotionTokens.bouncySpring(),
        label = "card_elevation"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (currentElevation > 0.dp) Modifier.shadow(currentElevation, shape)
                else Modifier
            )
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    hapticEngine.performHaptic(view, PixelHapticType.CLICK)
                    onClick()
                }
            )
            .padding(14.dp),
        content = content
    )
}
