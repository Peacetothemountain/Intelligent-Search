package com.pixel.intelligentsearch.feature.settings

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.glance.NotificationAccess
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
 * At a Glance's Media Controls ask for Notification Access the way Search Your Notifications does: the one rationale,
 * which names both, then the one listener's page in Android's settings.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class NotificationAccessSharedFlowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val nowPlaying = isToggleable() and hasText("Media Controls")

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().put(SettingsManager.GLANCE_ENABLED, true).put(SettingsManager.GLANCE_MEDIA, false).commit()
    }

    private fun grant(granted: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(NotificationAccess.component(context), granted)
    }

    private fun showGlanceSettings() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { androidx.compose.foundation.layout.Column { AtAGlanceSettingsCard() } } }
        compose.waitForIdle()
    }

    @Test
    fun theRationaleNamesBothFeatures() {
        for (feature in listOf("Search Your Notifications", "Media Controls", "At a Glance")) {
            assertTrue(feature, NOTIFICATION_ACCESS_RATIONALE.contains(feature))
        }
        // The widget no longer has Media Controls, so the rationale doesn't offer it as a use of the grant.
        assertTrue(!NOTIFICATION_ACCESS_RATIONALE.contains("widget"))
        assertTrue(NOTIFICATION_ACCESS_RATIONALE.contains("Notifications are read only while Search Your Notifications is on"))
    }

    @Test
    fun nowPlayingWithoutAccessExplainsThenOpensTheListenersPage() {
        grant(false)
        showGlanceSettings()
        compose.onNode(nowPlaying).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertExists()
        compose.onNodeWithText("One Notification Access grant covers", substring = true).assertExists()
        compose.onNodeWithText("Open Settings").performClick()
        compose.waitForIdle()
        val opened = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, opened?.action)
        assertEquals(
            NotificationAccess.component(context).flattenToString(),
            opened?.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME)
        )
        // Not on until Android has granted access.
        assertFalse(prefs[SettingsManager.GLANCE_MEDIA])
    }

    @Test
    fun nowPlayingNotNowLeavesItOff() {
        grant(false)
        showGlanceSettings()
        compose.onNode(nowPlaying).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Not Now").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertDoesNotExist()
        assertFalse(prefs[SettingsManager.GLANCE_MEDIA])
    }

    @Test
    fun nowPlayingWithTheGrantSwitchesStraightOn() {
        grant(true)
        showGlanceSettings()
        compose.onNode(nowPlaying).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertDoesNotExist()
        assertTrue(prefs[SettingsManager.GLANCE_MEDIA])
    }
}
