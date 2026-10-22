package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens

/**
 * Material 3 Expressive Tactile Bouncy Clickable.
 *
 * Provides physical spring scale compression (Effects.FastSpatial on press, Spatial.FastSpatial on release),
 * touch-down tactile ticks, click completion feedback, shape clipping, and debouncing.
 * Conforms to Android 17 (Canary ZP11) explicit spring damping and stiffness specifications.
 *
 * A tap also shows the theme's ripple ([LocalIndication], a bounded Material ripple), clipped to [shape] when one is
 * given and otherwise to the control's bounds. Focused from a keyboard, the control draws a focus ring along the same
 * outline. Haptics are this modifier's own: a tick on touch down and one haptic per click or long press, so the
 * system's long-press haptic is turned off. [role] is what TalkBack announces; a radio option passes
 * [Role.RadioButton] and sets its selected state in semantics itself.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    shape: Any? = null,
    interactionSource: MutableInteractionSource? = null,
    suppressClickHaptic: Boolean = false,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val effectiveInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by effectiveInteractionSource.collectIsPressedAsState()
    // Read only while drawing, so gaining or losing focus redraws the ring without recomposing.
    val isFocused = effectiveInteractionSource.collectIsFocusedAsState()
    val indication = LocalIndication.current
    val focusRingColor = MaterialTheme.colorScheme.secondary
    val outlineShape = shape as? Shape ?: RectangleShape

    // Fast micro-compression on touch down, smooth critically damped release without bouncy wobble
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) ExpressiveMotionTokens.PRESS_SCALE_BOUNCY else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "bouncy_click"
    )

    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }

    LaunchedEffect(isPressed) {
        if (isPressed && enabled && !suppressClickHaptic) {
            sensoryEngine.tick(view)
        }
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val clickAction = remember(view, sensoryEngine, suppressClickHaptic, enabled, effectiveInteractionSource) {
        {
            if (enabled && ClickDebouncer.canClick(120L, effectiveInteractionSource)) {
                if (!suppressClickHaptic) sensoryEngine.click(view)
                currentOnClick()
            }
        }
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null, enabled, effectiveInteractionSource) {
        if (onLongClick != null) {
            {
                if (enabled && ClickDebouncer.canClick(120L, effectiveInteractionSource)) {
                    sensoryEngine.click(view)
                    currentOnLongClick?.invoke()
                }
            }
        } else null
    }

    val baseModifier = this
        .minimumInteractiveComponentSize()
        .then(
            if (shape is Shape) Modifier.clip(shape) else Modifier
        )
        // Ahead of the clickable, so the ring is drawn over the ripple's focus layer rather than under it.
        .drawWithContent {
            drawContent()
            if (isFocused.value) {
                val ringWidth = FOCUS_RING_WIDTH.toPx()
                // Inset by half the stroke so the whole ring stays inside the (possibly clipped) bounds.
                inset(ringWidth / 2f) {
                    drawOutline(
                        outline = outlineShape.createOutline(size, layoutDirection, this),
                        color = focusRingColor,
                        style = Stroke(width = ringWidth)
                    )
                }
            }
        }
        .combinedClickable(
            role = role,
            interactionSource = effectiveInteractionSource,
            indication = indication,
            enabled = enabled,
            onLongClick = longClickAction,
            // longClickAction plays the long press's one haptic.
            hapticFeedbackEnabled = false,
            onClick = clickAction
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }

    baseModifier
}

/** The width of [bouncyClickable]'s keyboard focus ring. */
internal val FOCUS_RING_WIDTH = 3.dp

/**
 * Material 3 Expressive Row Clickable.
 * Features bounded ripple, subtle micro-scale, and tactile sonic haptics with critically damped return (zero overshoot).
 * Conforms to Android 17 (Canary ZP11) explicit spring damping and stiffness specifications.
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

    // Fast micro-compression on touch down, smooth critically damped release (damping=1.0f, stiffness=700f)
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) ExpressiveMotionTokens.PRESS_SCALE_ROW else 1f,
        animationSpec = if (isPressed) {
            ExpressiveMotionTokens.Effects.FastSpatial()
        } else {
            ExpressiveMotionTokens.criticallyDampedSpring()
        },
        label = "expressive_row_scale"
    )

    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }

    LaunchedEffect(isPressed) {
        if (isPressed && enabled) {
            sensoryEngine.tick(view)
        }
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val clickAction = remember(view, sensoryEngine, enabled, effectiveInteractionSource) {
        {
            if (enabled && ClickDebouncer.canClick(180L, effectiveInteractionSource)) {
                sensoryEngine.click(view)
                currentOnClick()
            }
        }
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null, enabled, effectiveInteractionSource) {
        if (onLongClick != null) {
            {
                if (enabled && ClickDebouncer.canClick(180L, effectiveInteractionSource)) {
                    sensoryEngine.click(view)
                    currentOnLongClick?.invoke()
                }
            }
        } else null
    }

    this
        .minimumInteractiveComponentSize()
        .combinedClickable(
            role = androidx.compose.ui.semantics.Role.Button,
            interactionSource = effectiveInteractionSource,
            indication = androidx.compose.material3.ripple(),
            enabled = enabled,
            onLongClick = longClickAction,
            onClick = clickAction
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
