package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.glance.GlanceAlarm
import com.pixel.intelligentsearch.core.glance.GlanceItem
import com.pixel.intelligentsearch.core.glance.GlanceTimer
import com.pixel.intelligentsearch.core.glance.timerReading
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * The times At a Glance shows follow the phone while the search is kept between looks: a new time zone or 12 or 24
 * hour style shows on the next tick, and nothing ticks while the search is in the background.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class GlanceTimeTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val originalZone: TimeZone = TimeZone.getDefault()

    /** 20:05:00 UTC, on the whole minute. */
    private var now = 1_800_000_000_000L - 1_800_000_000_000L % 86_400_000L + (20 * 60 + 5) * 60_000L

    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Before fun setUp() {
        hilt.inject()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        use24Hour(false)
    }

    @After fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun use24Hour(on: Boolean) {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, if (on) "24" else "12")
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalGlanceClock provides GlanceClock({ now }, ticks = true),
                    LocalLifecycleOwner provides owner
                ) { content() }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun advance(ms: Long) {
        now += ms
        compose.mainClock.advanceTimeBy(ms)
        // One more frame composes what the last tick read.
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun theDigitalClockTakesUpANewZoneAnd24HourStyle() {
        show { GlanceClockFace(style = GlanceClockStyle.DIGITAL) }
        compose.onNodeWithText("8").assertExists()
        compose.onNodeWithText("05").assertExists()
        // India is 5:30 ahead of UTC, so 20:06 there is 01:36.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        use24Hour(true)
        advance(60_000L)
        compose.onNodeWithText("01").assertExists()
        compose.onNodeWithText("36").assertExists()
    }

    @Test
    fun theAlarmsTimeTakesUpANewZoneAnd24HourStyle() {
        val ringsAt = now + 60 * 60 * 1000L
        fun formatted() = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(ringsAt))
        val before = formatted()
        show { GlanceCard(listOf(GlanceItem.Alarm(GlanceAlarm(ringsAt))), onOpened = {}) }
        compose.onNodeWithText("Alarm · $before").assertExists()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        use24Hour(true)
        val after = formatted()
        assertNotEquals(before, after)
        advance(30_000L)
        compose.onNodeWithText("Alarm · $after").assertExists()
    }

    @Test
    fun aTimerStopsTickingInTheBackgroundAndCatchesUpOnReturn() {
        val timer = GlanceTimer("t", GlanceTimer.Kind.Timer, "", now + 10 * 60_000L, "Clock", "com.example.clock", null)
        show { GlanceCard(listOf(GlanceItem.Timer(timer)), onOpened = {}) }
        compose.onNodeWithText("Timer · ${timerReading(timer, now)}").assertExists()
        advance(1_000L)
        val left = "Timer · ${timerReading(timer, now)}"
        compose.onNodeWithText(left).assertExists()
        owner.registry.currentState = Lifecycle.State.CREATED
        advance(5_000L)
        compose.onNodeWithText(left).assertExists()
        owner.registry.currentState = Lifecycle.State.RESUMED
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Timer · ${timerReading(timer, now)}").assertExists()
    }
}
