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
 * [minGapMillis]. Notches that came at least that far apart (timed by when they happened, not when they are handled,
 * as a busy frame hands several over at once) each wait their turn; notches closer together than that keep only one
 * more waiting, so a wheel spun hard moves on steadily instead of racing through.
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
    // Scroll toward the next step, in steps; the steps owed, all one way (its sign); when the last scroll and the last
    // notch that counted on its own happened (event time); when the last step was taken.
    var toward = 0f
    var pending = 0
    var lastScrollAt = 0L
    var lastNotchAt = -minGapMillis
    var lastStepAt = -minGapMillis
    fun take(dir: Int) {
        step(dir)
        lastStepAt = SystemClock.uptimeMillis()
    }
    awaitPointerEventScope {
        while (true) {
            val event = if (pending != 0) {
                val wait = (lastStepAt + minGapMillis - SystemClock.uptimeMillis()).coerceAtLeast(1L)
                withTimeoutOrNull(wait) { awaitPointerEvent() }
            } else awaitPointerEvent()
            if (event == null) {
                // A step's turn has come: it goes if there is still somewhere to go, else the rest are dropped.
                val dir = if (pending > 0) 1 else -1
                if (canStep(dir)) {
                    pending -= dir
                    take(dir)
                } else pending = 0
                continue
            }
            if (event.type == PointerEventType.PanStart || event.type == PointerEventType.PanEnd) toward = 0f
            val amount = event.stepsOf(panStep)
            if (amount == 0f) continue
            val dir = if (amount > 0f) 1 else -1
            if (event.changes.any { it.isConsumed } || !canStep(dir)) {
                toward = 0f
                pending = 0
                continue
            }
            event.changes.forEach { it.consume() }
            val at = event.changes.firstOrNull()?.uptimeMillis ?: SystemClock.uptimeMillis()
            if (at - lastScrollAt > SCROLL_PAUSE_MS || toward * dir < 0f) toward = 0f
            // Turning the wheel back drops the steps still owed the other way.
            if (pending * dir < 0) pending = 0
            lastScrollAt = at
            toward += amount
            if (abs(toward) < 1f) continue
            toward -= dir
            val ownNotch = at - lastNotchAt >= minGapMillis
            if (ownNotch) lastNotchAt = at
            when {
                pending == 0 && SystemClock.uptimeMillis() - lastStepAt >= minGapMillis -> take(dir)
                ownNotch -> pending += dir
                pending == 0 -> pending = dir
            }
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
