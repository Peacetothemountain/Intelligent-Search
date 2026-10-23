package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    private fun grant(granted: Boolean) {
        shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
            .setNotificationListenerAccessGranted(com.pixel.intelligentsearch.core.glance.NotificationAccess.component(context), granted)
    }

    private fun showGlanceSettings() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { androidx.compose.foundation.layout.Column { AtAGlanceSettingsCard() } } }
        compose.waitForIdle()
    }

    private val mediaControls = isToggleable() and hasText("Media Controls")

    @Test
    fun atAGlanceHasASwitchForEachItemAndShowClock() {
        grant(true)
        showGlanceSettings()
        val switches = listOf("At a Glance", "Timers & Stopwatch", "Next Alarm", "Media Controls", "Birthdays", "Show Clock")
        compose.onAllNodes(isToggleable()).assertCountEquals(switches.size)
        for (title in switches) compose.onNode(isToggleable() and hasText(title)).assertExists()
        // The battery was removed at the owner's request and stays gone.
        compose.onNodeWithText("Battery").assertDoesNotExist()
        compose.onNodeWithText("Clock Style").assertExists()
    }

    @Test
    fun mediaControlsWithoutAccessExplainsThenOpensTheListenersPage() {
        grant(false)
        showGlanceSettings()
        // Shown off while it can't show, though it is on underneath.
        compose.onNode(mediaControls).assertIsOff()
        compose.onNode(mediaControls).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertExists()
        compose.onNodeWithText("Open Settings").performClick()
        compose.waitForIdle()
        val opened = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, opened?.action)
        compose.onNode(mediaControls).assertIsOff()
    }

    @Test
    fun withTheGrantMediaControlsSwitchesStraightOffAndOn() {
        grant(true)
        showGlanceSettings()
        compose.onNode(mediaControls).assertIsOn().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertDoesNotExist()
        assertFalse(prefs[SettingsManager.GLANCE_MEDIA])
        // Past the rows' tap debounce.
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        compose.onNode(mediaControls).performClick()
        compose.waitForIdle()
        assertTrue(prefs[SettingsManager.GLANCE_MEDIA])
    }

    @Test
    fun clockStyleHidesWithShowClockOff() {
        prefs.edit().put(SettingsManager.GLANCE_CLOCK, false).commit()
        showGlanceSettings()
        compose.onNodeWithText("Clock Style").assertDoesNotExist()
    }

    @Test
    fun showClockHidesWithAtAGlanceOff() {
        prefs.edit().put(SettingsManager.GLANCE_ENABLED, false).commit()
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { androidx.compose.foundation.layout.Column { AtAGlanceSettingsCard() } } }
        compose.waitForIdle()
        compose.onNodeWithText("Show Clock").assertDoesNotExist()
    }
}
