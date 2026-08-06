package com.pixel.intelligentsearch.feature.settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClickable(
    shape: Shape? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bouncy_click"
    )

    val view = androidx.compose.ui.platform.LocalView.current

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = ripple(
                color = MaterialTheme.colorScheme.primary,
                bounded = true
            ),
            onLongClick = onLongClick?.let { {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                it()
            } },
            onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        )
}



