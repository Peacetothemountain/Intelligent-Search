package com.pixel.intelligentsearch.core.ui

import android.animation.ValueAnimator
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.createComposeRule
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The clock behind the mesh backgrounds ticks about 30 times a second and stands still with animations removed. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MeshClockTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    @Test
    fun ticksAboutThirtyTimesASecondOnASixtyHertzDisplay() {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 1f)
        compose.mainClock.autoAdvance = false
        lateinit var clock: State<Float>
        compose.setContent { clock = rememberMeshClock(isPaused = false) }
        compose.mainClock.advanceTimeBy(16)

        var last = clock.value
        var changes = 0
        repeat(1000 / 16) {
            compose.mainClock.advanceTimeBy(16)
            if (clock.value != last) {
                changes++
                last = clock.value
            }
        }

        assertTrue("$changes changes in a second", changes in 20..31)
    }

    @Test
    @Config(sdk = [32])
    fun standsStillWithAnimationsRemoved() {
        disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        lateinit var clock: State<Float>
        compose.setContent { clock = rememberMeshClock(isPaused = false) }

        repeat(1000 / 16) { compose.mainClock.advanceTimeBy(16) }

        assertEquals(0f, clock.value)
    }
}
