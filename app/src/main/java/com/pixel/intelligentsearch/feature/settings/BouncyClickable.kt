package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    shape: Any? = null,
    interactionSource: MutableInteractionSource? = null,
    suppressClickHaptic: Boolean = false,
    customClickHaptic: PixelHapticType? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val effectiveInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by effectiveInteractionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            stiffness = 420f,
            dampingRatio = 0.74f
        ),
        label = "bouncy_click"
    )

    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    androidx.compose.runtime.LaunchedEffect(isPressed) {
        if (isPressed) {
            sensoryEngine.tick(view, scale = 0.75f)
        }
    }

    val clickAction = remember(view, sensoryEngine, suppressClickHaptic, customClickHaptic) {
        {
            if (!suppressClickHaptic) {
                if (customClickHaptic != null) {
                    sensoryEngine.hapticEngine.performHaptic(view, customClickHaptic)
                } else {
                    sensoryEngine.click(view)
                }
            }
            currentOnClick()
        }
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null) {
        if (onLongClick != null) {
            {
                sensoryEngine.hapticEngine.performHaptic(view, PixelHapticType.HEAVY_IMPACT)
                sensoryEngine.sonicEngine.playSonic(
                    com.pixel.intelligentsearch.core.haptics.SonicMicroFeedbackEngine.SonicType.DELETE_THUD,
                    volumeScale = 0.85f
                )
                currentOnLongClick?.invoke()
                Unit
            }
        } else null
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = effectiveInteractionSource,
            indication = null,
            enabled = enabled,
            onLongClick = longClickAction,
            onClick = clickAction
        )
}

/**
 * Material 3 Expressive Row Clickable.
 * Features bounded ripple, subtle micro-scale (0.985f), and tactile sonic haptics.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.expressiveRowClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val effectiveInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by effectiveInteractionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = 0.85f
        ),
        label = "expressive_row_scale"
    )

    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    androidx.compose.runtime.LaunchedEffect(isPressed) {
        if (isPressed) {
            sensoryEngine.tick(view, scale = 0.40f)
        }
    }

    val clickAction = remember(view, sensoryEngine) {
        {
            sensoryEngine.click(view)
            currentOnClick()
        }
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null) {
        if (onLongClick != null) {
            {
                sensoryEngine.hapticEngine.performHaptic(view, PixelHapticType.HEAVY_IMPACT)
                currentOnLongClick?.invoke()
            }
        } else null
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = effectiveInteractionSource,
            indication = androidx.compose.material3.ripple(),
            enabled = enabled,
            onLongClick = longClickAction,
            onClick = clickAction
        )
}

