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
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.core.glance.NotificationIndex
import com.pixel.intelligentsearch.core.glance.NotificationSearchListener
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
import javax.inject.Inject

/**
 * Search Sources' Notifications switch: without access it explains first and sends the user to Android's
 * Notification Access page; with access it switches straight on; off forgets every notification read.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class NotificationSearchSettingTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Inject lateinit var index: NotificationIndex

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    private fun grant(granted: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(NotificationSearchListener.component(context), granted)
    }

    private fun showSources() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { SearchSourcesScreen(prefs, {}, {}) } }
        compose.waitForIdle()
    }

    private val notificationsSwitch = isToggleable() and hasText("Notifications")

    @Test
    fun theSubtitleSaysNothingLeavesThePhoneAndNothingIsStored() {
        showSources()
        compose.onNodeWithText("Nothing Leaves the Phone and Nothing Is Stored", substring = true).assertExists()
        compose.onNodeWithText("Messages and Gmail Show Only What Their Notifications Show", substring = true).assertExists()
    }

    @Test
    fun withoutAccessItExplainsThenOpensNotificationAccess() {
        grant(false)
        showSources()
        compose.onNode(notificationsSwitch).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertExists()
        compose.onNodeWithText("never your messages or emails themselves", substring = true).assertExists()
        compose.onNodeWithText("Open Settings").performClick()
        compose.waitForIdle()
        val opened = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, opened?.action)
        // Not on until Android has granted access.
        assertFalse(prefs[SettingsManager.SEARCH_NOTIFICATIONS])
    }

    @Test
    fun notNowLeavesItOff() {
        grant(false)
        showSources()
        compose.onNode(notificationsSwitch).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Not Now").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertDoesNotExist()
        assertFalse(prefs[SettingsManager.SEARCH_NOTIFICATIONS])
    }

    @Test
    fun withAccessItSwitchesStraightOn() {
        grant(true)
        showSources()
        compose.onNode(notificationsSwitch).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertDoesNotExist()
        assertTrue(prefs[SettingsManager.SEARCH_NOTIFICATIONS])
    }

    @Test
    fun switchingOffForgetsEveryNotification() {
        grant(true)
        prefs.edit().put(SettingsManager.SEARCH_NOTIFICATIONS, true).commit()
        index.post(IndexedNotification("k", "com.example.chat", "Chat", "Ana", "Dinner?", 1L))
        showSources()
        compose.onNode(notificationsSwitch).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(prefs[SettingsManager.SEARCH_NOTIFICATIONS])
        assertTrue(index.entries.value.isEmpty())
    }
}
