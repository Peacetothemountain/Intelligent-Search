package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithCache
import com.pixel.intelligentsearch.core.ui.ClickDebouncer
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine
import com.pixel.intelligentsearch.core.ui.expressive.expressivePress

/**
 * Material 3 Expressive Tactile Bouncy Clickable.
 *
 * Pressing morphs the control's shape as Android 17's Material 3 Expressive controls do ([expressivePress]),
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
    shape: Any? = null,
    interactionSource: MutableInteractionSource? = null,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val effectiveInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by effectiveInteractionSource.collectIsPressedAsState()
    // Read only while drawing, so gaining or losing focus redraws the ring without recomposing.
    val isFocused = effectiveInteractionSource.collectIsFocusedAsState()
    val indication = LocalIndication.current
    val outlineShape = shape as? Shape ?: RectangleShape

    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            sensoryEngine.tick(view)
        }
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val clickAction = remember(view, sensoryEngine, effectiveInteractionSource) {
        {
            if (ClickDebouncer.canClick(120L, effectiveInteractionSource)) {
                sensoryEngine.click(view)
                currentOnClick()
            }
        }
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null, effectiveInteractionSource) {
        if (onLongClick != null) {
            {
                if (ClickDebouncer.canClick(120L, effectiveInteractionSource)) {
                    sensoryEngine.click(view)
                    currentOnLongClick?.invoke()
                }
            }
        } else null
    }

    val baseModifier = this
        .minimumInteractiveComponentSize()
        // Android 17's press: the control's shape morphs on the fast spatial spring instead of shrinking.
        .expressivePress(effectiveInteractionSource, shape as? Shape)
        // Ahead of the clickable, so the ring is drawn over the ripple's focus layer rather than under it.
        .focusRing(outlineShape) { isFocused.value }
        .combinedClickable(
            role = role,
            interactionSource = effectiveInteractionSource,
            indication = indication,
            onLongClick = longClickAction,
            // longClickAction plays the long press's one haptic.
            hapticFeedbackEnabled = false,
            onClick = clickAction
        )

    baseModifier
}

/** The width of [bouncyClickable]'s keyboard focus ring. */
internal val FOCUS_RING_WIDTH = 3.dp

/**
 * The keyboard focus ring: a [FOCUS_RING_WIDTH] line in the theme's secondary colour along [shape], drawn over the
 * content while [visible] says so. [bouncyClickable] draws it for real focus; the search overlay draws it on the
 * result the arrow keys highlight, so both look the same. [visible] is read only while drawing, so the ring comes and
 * goes without recomposing; its outline and stroke are made once per size, not on every frame.
 */
fun Modifier.focusRing(shape: Shape = RectangleShape, visible: () -> Boolean): Modifier = composed {
    val focusRingColor = MaterialTheme.colorScheme.secondary
    drawWithCache {
        val ringWidth = FOCUS_RING_WIDTH.toPx()
        // Inset by half the stroke so the whole ring stays inside the (possibly clipped) bounds.
        val inner = androidx.compose.ui.geometry.Size(
            (size.width - ringWidth).coerceAtLeast(0f),
            (size.height - ringWidth).coerceAtLeast(0f)
        )
        val outline = shape.createOutline(inner, layoutDirection, this)
        val stroke = Stroke(width = ringWidth)
        onDrawWithContent {
            drawContent()
            if (visible()) {
                translate(ringWidth / 2f, ringWidth / 2f) {
                    drawOutline(outline = outline, color = focusRingColor, style = stroke)
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Row Clickable.
 * Features bounded ripple, Android 17's press shape morph ([expressivePress]) and the app's haptics.
 * Conforms to Android 17 (Canary ZP11) explicit spring damping and stiffness specifications.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.expressiveRowClickable(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val effectiveInteractionSource = remember { MutableInteractionSource() }
    val isPressed by effectiveInteractionSource.collectIsPressedAsState()

    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            sensoryEngine.tick(view)
        }
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val clickAction = remember(view, sensoryEngine, effectiveInteractionSource) {
        {
            if (ClickDebouncer.canClick(180L, effectiveInteractionSource)) {
                sensoryEngine.click(view)
                currentOnClick()
            }
        }
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null, effectiveInteractionSource) {
        if (onLongClick != null) {
            {
                if (ClickDebouncer.canClick(180L, effectiveInteractionSource)) {
                    sensoryEngine.click(view)
                    currentOnLongClick?.invoke()
                }
            }
        } else null
    }

    this
        .minimumInteractiveComponentSize()
        .expressivePress(effectiveInteractionSource)
        .combinedClickable(
            role = androidx.compose.ui.semantics.Role.Button,
            interactionSource = effectiveInteractionSource,
            indication = androidx.compose.material3.ripple(),
            onLongClick = longClickAction,
            onClick = clickAction
        )
}
