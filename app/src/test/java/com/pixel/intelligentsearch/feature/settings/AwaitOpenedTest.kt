package com.pixel.intelligentsearch.feature.settings

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Settings page is taken off the back stack only once it has finished opening: popped while the NavHost was still
 * animating it in, the NavHost composed it after it was destroyed and the app crashed (a tap on a row and a back swipe
 * at once, on the owner's phone). The NavHost resumes a page as its opening animation ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AwaitOpenedTest {

    private fun page(state: Lifecycle.State): LifecycleRegistry =
        LifecycleRegistry.createUnsafe(object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = throw UnsupportedOperationException()
        }).apply { currentState = state }

    @Test
    fun aPageStillOpeningIsWaitedForUntilItIsResumed() = runTest {
        val opening = page(Lifecycle.State.STARTED)
        var done = false
        launch { awaitOpened(opening); done = true }
        advanceTimeBy(500)
        runCurrent()
        assertFalse("popped while still opening", done)
        opening.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertTrue(done)
    }

    @Test
    fun anOpenPageIsNotWaitedFor() = runTest {
        var done = false
        launch { awaitOpened(page(Lifecycle.State.RESUMED)); done = true }
        runCurrent()
        assertTrue(done)
    }

    /** A page that never resumes (Settings itself paused) still goes back, a second later. */
    @Test
    fun aPageThatNeverResumesGoesBackAfterASecond() = runTest {
        var done = false
        launch { awaitOpened(page(Lifecycle.State.STARTED)); done = true }
        advanceTimeBy(999)
        runCurrent()
        assertFalse(done)
        advanceTimeBy(2)
        runCurrent()
        assertTrue(done)
    }
}
