package com.pixel.intelligentsearch.feature.settings

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Global debouncer for settings clicks and navigation events.
 * Prevents rapid accidental double-taps while preserving instant touch response.
 * Supports keyed debouncing per widget to prevent dead zones across independent widgets.
 * Uses atomic CAS operations and concurrent structures for thread safety across all CPU architectures.
 */
object SettingsDebouncer {
    private val globalLastClickTime = AtomicLong(0L)
    private val widgetClickTimes = ConcurrentHashMap<Any, Long>()

    private fun currentTimeMs(): Long {
        return try {
            val uptime = SystemClock.uptimeMillis()
            if (uptime > 0L) uptime else System.currentTimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    fun canClick(cooldownMs: Long = 180L, key: Any? = null): Boolean {
        val now = currentTimeMs()
        if (key != null) {
            val last = widgetClickTimes[key] ?: 0L
            if (last > 0L && (now - last) in 0 until cooldownMs) {
                return false
            }
            widgetClickTimes[key] = now
            if (widgetClickTimes.size > 256) {
                widgetClickTimes.entries.removeIf { now - it.value > 10_000L }
            }
            return true
        }

        while (true) {
            val prev = globalLastClickTime.get()
            if (prev > 0L && (now - prev) in 0 until cooldownMs) {
                return false
            }
            if (globalLastClickTime.compareAndSet(prev, if (now > 0L) now else System.currentTimeMillis())) {
                return true
            }
        }
    }

    fun recordClick(timestamp: Long = currentTimeMs(), key: Any? = null) {
        if (key != null) {
            widgetClickTimes[key] = timestamp
        } else {
            globalLastClickTime.set(timestamp)
        }
    }
}

/**
 * Material 3 Expressive Tactile Bouncy Clickable.
 *
 * Provides physical spring scale compression (Effects.FastSpatial on press, Spatial.FastSpatial on release),
 * touch-down tactile ticks, click completion feedback, shape clipping, and debouncing.
 * Conforms to Android 17 (Canary ZP11) explicit spring damping and stiffness specifications.
 */
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

    val clickAction = remember(view, sensoryEngine, suppressClickHaptic, customClickHaptic, enabled, effectiveInteractionSource) {
        {
            if (enabled && SettingsDebouncer.canClick(120L, effectiveInteractionSource)) {
                if (!suppressClickHaptic) {
                    if (customClickHaptic != null) {
                        if (sensoryEngine.isHapticEnabled) {
                            sensoryEngine.hapticEngine.performHaptic(view, customClickHaptic)
                        }
                    } else {
                        sensoryEngine.click(view)
                    }
                }
                currentOnClick()
            }
        }
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null, enabled, effectiveInteractionSource) {
        if (onLongClick != null) {
            {
                if (enabled && SettingsDebouncer.canClick(120L, effectiveInteractionSource)) {
                    if (sensoryEngine.isHapticEnabled) {
                        sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
                    }
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
        .combinedClickable(
            role = androidx.compose.ui.semantics.Role.Button,
            interactionSource = effectiveInteractionSource,
            indication = null,
            enabled = enabled,
            onLongClick = longClickAction,
            onClick = clickAction
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }

    baseModifier
}

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
            if (enabled && SettingsDebouncer.canClick(180L, effectiveInteractionSource)) {
                sensoryEngine.click(view)
                currentOnClick()
            }
        }
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null, enabled, effectiveInteractionSource) {
        if (onLongClick != null) {
            {
                if (enabled && SettingsDebouncer.canClick(180L, effectiveInteractionSource)) {
                    if (sensoryEngine.isHapticEnabled) {
                        sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
                    }
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
