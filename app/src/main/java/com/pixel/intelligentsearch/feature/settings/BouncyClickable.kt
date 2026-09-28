package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.os.SystemClock
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.DisposableEffect

/**
 * Global debouncer for settings clicks and navigation events.
 * Prevents double-taps and touch leakage across screen transitions.
 */
object SettingsDebouncer {
    @Volatile
    private var lastClickTime = 0L

    fun canClick(cooldownMs: Long = 350L): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastClickTime < cooldownMs) {
            return false
        }
        lastClickTime = now
        return true
    }

    fun recordClick(timestamp: Long = SystemClock.uptimeMillis()) {
        lastClickTime = timestamp
    }
}

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

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumedTimestamp by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumedTimestamp = SystemClock.uptimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            resumedTimestamp = SystemClock.uptimeMillis()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val clickAction = remember(view, sensoryEngine, suppressClickHaptic, customClickHaptic, lifecycleOwner) {
        {
            val isResumed = lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
            val hasSettled = (SystemClock.uptimeMillis() - resumedTimestamp) >= 180L
            if (isResumed && hasSettled && SettingsDebouncer.canClick()) {
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
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null, lifecycleOwner) {
        if (onLongClick != null) {
            {
                val isResumed = lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
                val hasSettled = (SystemClock.uptimeMillis() - resumedTimestamp) >= 180L
                if (isResumed && hasSettled && SettingsDebouncer.canClick()) {
                    sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
                    currentOnLongClick?.invoke()
                    Unit
                }
            }
        } else null
    }

    this
        .minimumInteractiveComponentSize()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            role = androidx.compose.ui.semantics.Role.Button,
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

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumedTimestamp by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumedTimestamp = SystemClock.uptimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            resumedTimestamp = SystemClock.uptimeMillis()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val clickAction = remember(view, sensoryEngine, lifecycleOwner) {
        {
            val isResumed = lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
            val hasSettled = (SystemClock.uptimeMillis() - resumedTimestamp) >= 180L
            if (isResumed && hasSettled && SettingsDebouncer.canClick()) {
                sensoryEngine.click(view)
                currentOnClick()
            }
        }
    }

    val longClickAction: (() -> Unit)? = remember(view, sensoryEngine, onLongClick != null, lifecycleOwner) {
        if (onLongClick != null) {
            {
                val isResumed = lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
                val hasSettled = (SystemClock.uptimeMillis() - resumedTimestamp) >= 180L
                if (isResumed && hasSettled && SettingsDebouncer.canClick()) {
                    sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
                    currentOnLongClick?.invoke()
                }
            }
        } else null
    }

    this
        .minimumInteractiveComponentSize()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            role = androidx.compose.ui.semantics.Role.Button,
            interactionSource = effectiveInteractionSource,
            indication = androidx.compose.material3.ripple(),
            enabled = enabled,
            onLongClick = longClickAction,
            onClick = clickAction
        )
}

