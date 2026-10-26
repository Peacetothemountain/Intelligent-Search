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
import com.pixel.intelligentsearch.core.glance.NotificationAccessListener
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
 * Notification access has no switch of the app's own: Search Sources has no Notifications, Calendar or Android
 * Settings row, and Finish Setting Up asks for the access (rationale first, then Android's Notification Access page)
 * and shows it done once Android has granted it.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class NotificationAccessSetupTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    private fun grant(granted: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(NotificationAccessListener.component(context), granted)
    }

    private fun showSetup() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { SetupChecklistScreen(prefs, {}, {}) } }
        compose.waitForIdle()
    }

    @Test
    fun searchSourcesHasNoSwitchForWhatAndroidAlreadyAsksAbout() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { SearchSourcesScreen({}, {}) } }
        compose.waitForIdle()
        for (title in listOf("Notifications", "Calendar", "Android Settings")) {
            compose.onNode(isToggleable() and hasText(title)).assertDoesNotExist()
        }
        compose.onNode(isToggleable() and hasText("Calculator")).assertExists()
    }

    @Test
    fun notificationsIsASetupStepDoneOnlyWithAccess() {
        grant(false)
        assertTrue(SetupStep.NOTIFICATIONS in SetupChecklist.steps(context))
        assertFalse(SetupChecklist.isDone(context, prefs, SetupStep.NOTIFICATIONS))
        grant(true)
        assertTrue(SetupChecklist.isDone(context, prefs, SetupStep.NOTIFICATIONS))
    }

    @Test
    fun theCalendarStepIsDoneWithThePermissionAlone() {
        val app = shadowOf(ApplicationProvider.getApplicationContext<Application>())
        app.denyPermissions(android.Manifest.permission.READ_CALENDAR)
        assertFalse(SetupChecklist.isDone(context, prefs, SetupStep.CALENDAR))
        app.grantPermissions(android.Manifest.permission.READ_CALENDAR)
        assertTrue(SetupChecklist.isDone(context, prefs, SetupStep.CALENDAR))
    }

    @Test
    fun withoutAccessTheStepExplainsThenOpensNotificationAccess() {
        grant(false)
        showSetup()
        compose.onNodeWithText("Search Your Notifications", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertExists()
        compose.onNodeWithText("not the messages or emails behind it", substring = true).assertExists()
        compose.onNodeWithText("Open Settings").performClick()
        compose.waitForIdle()
        val opened = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, opened?.action)
    }

    @Test
    fun notNowClosesTheExplanation() {
        grant(false)
        showSetup()
        compose.onNodeWithText("Search Your Notifications", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Not Now").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notification Access?").assertDoesNotExist()
    }
}
