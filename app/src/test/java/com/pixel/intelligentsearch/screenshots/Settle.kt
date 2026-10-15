package com.pixel.intelligentsearch.screenshots

import android.os.Looper
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.pixel.intelligentsearch.core.ui.LOADING_INDICATOR_TAG
import org.robolectric.Shadows.shadowOf

/**
 * Screenshot tests run with system animations off (as with Accessibility > Remove animations): the app's
 * hand-rolled frame loops (Gemini corner wave, matrix and mesh backgrounds) then stay still, so their frame
 * can't depend on when the screen happened to resume. Compose animations still follow the frozen test clock.
 */
fun disableHandRolledAnimations() {
    // ValueAnimator.setDurationScale is hidden; inside the Robolectric sandbox reflection reaches it.
    android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 0f)
}

/**
 * Lets a screen finish loading before it is captured, so every capture is repeatable.
 *
 * 1. While a loading indicator (tagged [LOADING_INDICATOR_TAG]) is on screen, wait in real time for the
 *    background work, draining main-thread callbacks; the frozen clock moves one frame per check only so the
 *    result can be composed.
 * 2. Then a fixed schedule: the clock always advances the same total, so Compose animations land on the same
 *    frame, and each step also drains anything that arrived late.
 */
fun ComposeTestRule.settle(steps: Int = 30, stepMs: Long = 100, realWaitMs: Long = 120, maxLoadingWaits: Int = 250) {
    fun drain(advanceMs: Long) {
        Thread.sleep(realWaitMs)
        // Move the main looper's clock with the Compose clock, so work posted with a delay (debounces) runs at
        // the same point in every run instead of whenever the looper happens to be idled.
        if (advanceMs > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(advanceMs))
        } else {
            shadowOf(Looper.getMainLooper()).idle()
        }
        mainClock.advanceTimeBy(advanceMs)
    }
    drain(stepMs)
    var waits = 0
    while (waits < maxLoadingWaits && onAllNodes(hasTestTag(LOADING_INDICATOR_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
        drain(0)
        waits++
    }
    repeat(steps) { drain(stepMs) }
}
