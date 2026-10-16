package com.pixel.intelligentsearch.screenshots

import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.pixel.intelligentsearch.core.ui.LOADING_INDICATOR_TAG
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import org.robolectric.Shadows.shadowOf

/**
 * The effect context for every compose rule in these tests. By default the rule runs composition and effects
 * on an unconfined dispatcher, so an effect that comes back from `withContext(Dispatchers.IO)` continues on
 * the IO worker and writes its state there, racing the test thread's composition; a write that lands while a
 * frame is in progress can be lost, leaving the screen stuck on its loading indicator. A standard test
 * dispatcher queues the continuation for the test thread, as the main dispatcher does in the app.
 */
fun confinedEffects(): CoroutineContext = StandardTestDispatcher()

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
 *    background work, draining main-thread callbacks. Each check also publishes snapshot writes made off the
 *    main thread and moves the clock one frame so the loaded result composes; with auto-advance off nothing
 *    else would, and the loop would run until its cap.
 * 2. Then a fixed schedule long enough for any entrance animation to finish, so the capture does not depend on
 *    how long loading took; each step also drains anything that arrived late.
 */
fun ComposeTestRule.settle(steps: Int = 30, stepMs: Long = 100, realWaitMs: Long = 120, maxLoadingWaits: Int = 250) {
    val frameMs = 16L
    fun drain(advanceMs: Long) {
        Thread.sleep(realWaitMs)
        // Move the main looper's clock with the Compose clock, so work posted with a delay (debounces) runs at
        // the same point in every run instead of whenever the looper happens to be idled.
        if (advanceMs > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(advanceMs))
        } else {
            shadowOf(Looper.getMainLooper()).idle()
        }
        Snapshot.sendApplyNotifications()
        mainClock.advanceTimeBy(advanceMs)
    }
    drain(stepMs)
    var waits = 0
    while (waits < maxLoadingWaits && onAllNodes(hasTestTag(LOADING_INDICATOR_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
        drain(frameMs)
        waits++
    }
    repeat(steps) { drain(stepMs) }
}
