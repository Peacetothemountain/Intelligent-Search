package com.pixel.intelligentsearch.core.ui

import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A mouse wheel and a trackpad for a control that moves through things one at a time (a fan of looks, a wheel of
 * numbers, a row of pages): each notch of the wheel, or each [PAN_STEP] of a two-finger swipe on a trackpad, is one
 * [step], 1 for down (or right, as the content moves on) and -1 for up (or left). Steps come at most every
 * [minGapMillis], one more waiting meanwhile, so a wheel spun hard moves on steadily instead of racing through.
 *
 * Where [canStep] says there is nothing more that way, the scroll is left alone, so the page around scrolls instead.
 * The lambdas are read when the scroll comes, so they should reach the control's state through [keys].
 */
internal fun Modifier.wheelSteps(
    vararg keys: Any?,
    minGapMillis: Long = 150L,
    canStep: (Int) -> Boolean,
    step: (Int) -> Unit
): Modifier = pointerInput(*keys) {
    val panStep = PAN_STEP.toPx()
    // Scroll toward the next step, in steps; a step of the other way waiting for its turn; when the last one came.
    var toward = 0f
    var waiting = 0
    var lastScrollAt = 0L
    var lastStepAt = -minGapMillis
    fun take(dir: Int) {
        step(dir)
        lastStepAt = SystemClock.uptimeMillis()
    }
    awaitPointerEventScope {
        while (true) {
            val event = if (waiting != 0) {
                val wait = (lastStepAt + minGapMillis - SystemClock.uptimeMillis()).coerceAtLeast(1L)
                withTimeoutOrNull(wait) { awaitPointerEvent() }
            } else awaitPointerEvent()
            if (event == null) {
                // The wait is over: the waiting step goes, if there is still somewhere to go.
                val dir = waiting
                waiting = 0
                if (canStep(dir)) take(dir)
                continue
            }
            if (event.type == PointerEventType.PanStart || event.type == PointerEventType.PanEnd) toward = 0f
            val amount = event.stepsOf(panStep)
            if (amount == 0f) continue
            val dir = if (amount > 0f) 1 else -1
            if (event.changes.any { it.isConsumed } || !canStep(dir)) {
                toward = 0f
                waiting = 0
                continue
            }
            event.changes.forEach { it.consume() }
            val now = SystemClock.uptimeMillis()
            if (now - lastScrollAt > SCROLL_PAUSE_MS || toward * dir < 0f) toward = 0f
            if (waiting == -dir) waiting = 0
            lastScrollAt = now
            toward += amount
            if (abs(toward) < 1f) continue
            toward -= dir
            if (now - lastStepAt >= minGapMillis && waiting == 0) take(dir) else waiting = dir
        }
    }
}

/**
 * How far this event scrolls, in steps: a wheel's notches (down or right positive), or a trackpad's two-finger move in
 * [panStep]s (the content moving as the fingers do). The larger of the two directions counts.
 */
private fun PointerEvent.stepsOf(panStep: Float): Float {
    val d = when (type) {
        PointerEventType.Scroll -> changes.fold(Offset.Zero) { acc, c -> acc + c.scrollDelta }
        PointerEventType.PanStart, PointerEventType.PanMove ->
            changes.fold(Offset.Zero) { acc, c -> acc - c.panOffset - c.historical.fold(Offset.Zero) { h, p -> h + p.panOffset } } / panStep
        else -> return 0f
    }
    return if (abs(d.x) > abs(d.y)) d.x else d.y
}

/** A trackpad's two-finger move that makes one step. */
private val PAN_STEP = 56.dp

/** A pause in scrolling this long starts the count toward a step over. */
private const val SCROLL_PAUSE_MS = 300L
