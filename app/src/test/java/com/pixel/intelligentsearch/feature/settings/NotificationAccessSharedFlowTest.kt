package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Notification Access is asked for in one place, Search Your Notifications; the same grant lets At a Glance show
 * running timers and what's playing, so its rationale says so. At a Glance's settings have no per-item switches and
 * ask for nothing.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class NotificationAccessSharedFlowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().put(SettingsManager.GLANCE_ENABLED, true).commit()
    }

    @Test
    fun theRationaleNamesWhatTheGrantIsFor() {
        for (use in listOf("search your notifications", "running timers", "what's playing", "At a Glance")) {
            assertTrue(use, NOTIFICATION_ACCESS_RATIONALE.contains(use))
        }
        // The widget no longer has Media Controls, so the rationale doesn't offer it as a use of the grant.
        assertFalse(NOTIFICATION_ACCESS_RATIONALE.contains("widget"))
        assertTrue(NOTIFICATION_ACCESS_RATIONALE.contains("Nothing is stored and nothing leaves your phone"))
    }

    @Test
    fun atAGlanceHasOnlyItsOwnSwitchAndShowClock() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { androidx.compose.foundation.layout.Column { AtAGlanceSettingsCard() } } }
        compose.waitForIdle()
        compose.onAllNodes(isToggleable()).assertCountEquals(2)
        compose.onNode(isToggleable() and hasText("At a Glance")).assertExists()
        compose.onNode(isToggleable() and hasText("Show Clock")).assertExists()
        for (removed in listOf("Timers & Stopwatch", "Next Alarm", "Media Controls", "Birthdays", "Battery")) {
            compose.onNodeWithText(removed).assertDoesNotExist()
        }
    }

    @Test
    fun showClockHidesWithAtAGlanceOff() {
        prefs.edit().put(SettingsManager.GLANCE_ENABLED, false).commit()
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { androidx.compose.foundation.layout.Column { AtAGlanceSettingsCard() } } }
        compose.waitForIdle()
        compose.onNodeWithText("Show Clock").assertDoesNotExist()
    }
}
