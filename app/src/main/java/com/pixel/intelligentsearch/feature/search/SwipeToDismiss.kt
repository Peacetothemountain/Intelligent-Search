package com.pixel.intelligentsearch.feature.search

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A result row the user can swipe away (recent searches, suggested actions, calendar events). Dragging fades the
 * row; past the threshold it flies off in the drag direction and [onDismissed] runs, otherwise it springs back.
 * TalkBack users get the same through a "Dismiss" action. [threshold] is the drag distance that dismisses;
 * the row fades up to [maxDragFade] over [fadeDistance] while dragged. The haptics are the back gesture's: the
 * commit haptic as the drag crosses the threshold, the cancel haptic if it is pulled back under it, and nothing more
 * on letting go; a TalkBack dismissal plays the commit haptic.
 */
@Stable
internal class SwipeToDismissState(
    private val scope: CoroutineScope,
    private val sensoryEngine: TactileSonicEngine,
    private val view: View,
    private val threshold: Float,
    private val fadeDistance: Float,
    private val maxDragFade: Float
) {
    val offsetX = Animatable(0f)
    val alpha = Animatable(1f)
    var dismissed by mutableStateOf(false)
        private set
    var direction by mutableFloatStateOf(1f)
        private set
    /** Whether the drag is past [threshold], so letting go now dismisses the row. */
    private var pastThreshold = false

    fun dismiss() {
        sensoryEngine.deleteThud(view)
        dismissed = true
    }

    internal fun drag(amount: Float) {
        scope.launch {
            val newOffset = offsetX.value + amount
            offsetX.snapTo(newOffset)
            alpha.snapTo(1f - (kotlin.math.abs(newOffset) / fadeDistance).coerceIn(0f, maxDragFade))
            crossThreshold(kotlin.math.abs(newOffset) > threshold)
        }
    }

    /** Plays the back gesture's commit or cancel haptic when the drag crosses [threshold] one way or the other. */
    private fun crossThreshold(past: Boolean) {
        if (past == pastThreshold) return
        pastThreshold = past
        if (past) sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
        else sensoryEngine.hapticEngine.performPredictiveBackCancelHaptic(view)
    }

    internal fun release() {
        if (kotlin.math.abs(offsetX.value) > threshold) {
            // The commit haptic played as the drag crossed the threshold.
            crossThreshold(true)
            direction = if (offsetX.value >= 0f) 1f else -1f
            dismissed = true
        } else {
            settleBack()
        }
    }

    internal fun settleBack() {
        crossThreshold(false)
        scope.launch {
            launch { offsetX.animateTo(0f, ExpressiveMotionTokens.bouncySpring()) }
            launch { alpha.animateTo(1f, ExpressiveMotionTokens.gentleSpring()) }
        }
    }
}

@Composable
internal fun rememberSwipeToDismiss(
    sensoryEngine: TactileSonicEngine,
    view: View,
    threshold: Float = 130f,
    fadeDistance: Float = 600f,
    maxDragFade: Float = 0.6f,
    onDismissed: () -> Unit
): SwipeToDismissState {
    val scope = rememberCoroutineScope()
    val state = remember { SwipeToDismissState(scope, sensoryEngine, view, threshold, fadeDistance, maxDragFade) }
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    LaunchedEffect(state.dismissed) {
        if (state.dismissed) {
            launch {
                state.offsetX.animateTo(
                    targetValue = state.direction * 1500f,
                    animationSpec = ExpressiveMotionTokens.bouncySpring()
                )
            }
            launch {
                state.alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                )
            }
            delay(160)
            currentOnDismissed()
        }
    }
    return state
}

/** Moves and fades the row with the swipe. */
internal fun Modifier.swipeOffset(state: SwipeToDismissState): Modifier = graphicsLayer {
    translationX = state.offsetX.value
    alpha = state.alpha.value
}

/** The horizontal drag that dismisses the row, and the matching TalkBack action. */
internal fun Modifier.swipeToDismiss(state: SwipeToDismissState, key: Any?): Modifier = this
    .semantics { customActions = listOf(CustomAccessibilityAction("Dismiss") { state.dismiss(); true }) }
    .pointerInput(key) {
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                state.drag(dragAmount)
            },
            onDragEnd = { state.release() },
            onDragCancel = { state.settleBack() }
        )
    }
