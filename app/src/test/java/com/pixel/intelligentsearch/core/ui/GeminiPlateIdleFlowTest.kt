package com.pixel.intelligentsearch.core.ui

import android.animation.ValueAnimator
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.test.junit4.createComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The search bar's bottom light flows only while the bar sits idle, fades out once typing or listening starts, and
 * never moves or shows with animations removed. (Robolectric can't run AGSL, so this checks the flow's strength and
 * clock, which are all its shader is drawn from.)
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class GeminiPlateIdleFlowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    @Before fun setUp() = hilt.inject()

    @After fun restoreAnimations() = setAnimatorScale(1f)

    @Test
    fun itFlowsOnlyWithNothingTypedAndNotListening() {
        assertTrue(plateFlowsWhileIdle(query = "", listening = false))
        assertFalse(plateFlowsWhileIdle(query = "w", listening = false))
        assertFalse(plateFlowsWhileIdle(query = "", listening = true))
        assertFalse(plateFlowsWhileIdle(query = "weather", listening = true))
    }

    @Test
    fun theFlowUsesThePlatesOwnBottomColour() {
        // What the plate drew along its bottom before the colour moved into geminiPlateBottomLiftColor: in dark theme
        // (surface luminance under 0.5) a lift of primaryContainer, in light theme its outer colour,
        // surfaceContainerHigh. The plate's look must not change.
        val dark = darkColorScheme()
        assertEquals(lerp(Color(0xFF160E14), dark.primaryContainer, 0.46f), geminiPlateBottomLiftColor(dark))
        val light = lightColorScheme()
        assertEquals(light.surfaceContainerHigh, geminiPlateBottomLiftColor(light))
    }

    private var idle by mutableStateOf(true)
    private lateinit var flow: PlateIdleFlow

    private fun showFlow() {
        compose.mainClock.autoAdvance = false
        compose.setContent { flow = rememberPlateIdleFlow(idle) }
        compose.waitForIdle()
    }

    private fun advance(ms: Long) {
        // A change to idle made here reaches composition from the next frame. Then frame-sized steps, as the display
        // would drive it.
        Snapshot.sendApplyNotifications()
        repeat((ms / 16).toInt()) { compose.mainClock.advanceTimeBy(16) }
        compose.waitForIdle()
    }

    @Test
    fun anIdleBarsLightFadesInAndMoves() {
        setAnimatorScale(1f)
        showFlow()
        advance(2_000)
        assertEquals(1f, flow.strength, 0.001f)
        val time = flow.time
        advance(1_000)
        assertNotEquals(time, flow.time, 0.0001f)
    }

    @Test
    fun itFadesOutAndStandsStillOnceTypingStarts() {
        setAnimatorScale(1f)
        showFlow()
        advance(2_000)
        idle = false
        advance(1_000)
        assertEquals(0f, flow.strength, 0f)
        val time = flow.time
        advance(1_000)
        assertEquals(time, flow.time, 0f)

        // And comes back when the field is cleared.
        idle = true
        advance(2_000)
        assertEquals(1f, flow.strength, 0.001f)
    }

    @Test
    fun withAnimationsRemovedItNeverShowsOrMoves() {
        setAnimatorScale(0f)
        showFlow()
        advance(2_000)
        assertEquals(0f, flow.strength, 0f)
        assertEquals(0f, flow.time, 0f)
    }
}
