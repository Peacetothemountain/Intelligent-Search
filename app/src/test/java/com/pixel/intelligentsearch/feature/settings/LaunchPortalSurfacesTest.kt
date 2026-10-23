package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.launch.FloatingSearchOverlays
import com.pixel.intelligentsearch.feature.launch.ScreenEdge
import com.pixel.intelligentsearch.feature.launch.NotificationShortcut
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Launch Portal's Notification Shortcut, Floating Search Button and Edge Swipe: none can be switched on without its
 * permission; each explains itself first and sends the user to Android's page, and with the permission each
 * switches straight on.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class LaunchPortalSurfacesTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(false)
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        FloatingSearchOverlays.sync(context)
    }

    private fun showPortal() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { LaunchPortalScreen(prefs) {} } }
        compose.waitForIdle()
    }

    private fun switch(title: String) = compose.onNode(isToggleable() and hasText(title))

    @Test
    fun theSubtitlesSayWhatEachDoesAndWarnAboutBack() {
        showPortal()
        compose.onNodeWithText(notificationShortcutSubtitle(lens = false)).assertExists()
        compose.onNodeWithText("Hold to Hide Until You Unlock", substring = true).assertExists()
        compose.onNodeWithText(edgeSwipeSubtitle(ScreenEdge.RIGHT)).assertExists()
        assertTrue(edgeSwipeSubtitle(ScreenEdge.LEFT).contains("Left Edge"))
        assertTrue(edgeSwipeSubtitle(ScreenEdge.RIGHT).contains("Back Gesture"))
        assertTrue(notificationShortcutSubtitle(lens = true).contains("Voice and Lens"))
    }

    @Test
    fun theNotificationExplainsFirstAndNotNowLeavesItOff() {
        showPortal()
        switch("Notification Shortcut").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notifications?").assertExists()
        compose.onNodeWithText("Not Now").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notifications?").assertDoesNotExist()
        assertFalse(prefs[SettingsManager.NOTIFICATION_SHORTCUT])
        assertEquals(null, shadowOf(manager).getNotification(NotificationShortcut.NOTIFICATION_ID))
    }

    @Test
    fun withNotificationsAllowedItSwitchesOnAndPosts() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showPortal()
        switch("Notification Shortcut").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Notifications?").assertDoesNotExist()
        assertTrue(prefs[SettingsManager.NOTIFICATION_SHORTCUT])
        assertNotNull(shadowOf(manager).getNotification(NotificationShortcut.NOTIFICATION_ID))
    }

    @Test
    fun switchedOffTheNotificationGoes() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        prefs.edit().put(SettingsManager.NOTIFICATION_SHORTCUT, true).commit()
        showPortal()
        assertNotNull(shadowOf(manager).getNotification(NotificationShortcut.NOTIFICATION_ID))
        switch("Notification Shortcut").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(prefs[SettingsManager.NOTIFICATION_SHORTCUT])
        assertEquals(null, shadowOf(manager).getNotification(NotificationShortcut.NOTIFICATION_ID))
    }

    @Test
    fun theFloatingButtonExplainsThenOpensDisplayOverOtherApps() {
        showPortal()
        switch("Floating Search Button").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Display over Other Apps?").assertExists()
        compose.onNodeWithText("nothing on screen is read", substring = true).assertExists()
        compose.onNodeWithText("Open Settings").performClick()
        compose.waitForIdle()
        val opened = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, opened?.action)
        assertEquals("package:${context.packageName}", opened?.dataString)
        // Not on until Android has granted it.
        assertFalse(prefs[SettingsManager.FLOATING_SEARCH_BUTTON])
        assertFalse(FloatingSearchOverlays.isButtonShowing)
    }

    @Test
    fun withDisplayOverOtherAppsTheEdgeSwipeSwitchesOnAndShowsItsControls() {
        ShadowSettings.setCanDrawOverlays(true)
        showPortal()
        compose.onNodeWithText("Edge").assertDoesNotExist()
        switch("Edge Swipe").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow Display over Other Apps?").assertDoesNotExist()
        assertTrue(prefs[SettingsManager.EDGE_SWIPE])
        assertTrue(FloatingSearchOverlays.isStripShowing)
        compose.onNodeWithText("Edge").assertExists()
        compose.onNodeWithText("Position").assertExists()
        compose.onNodeWithText("Height").assertExists()

        compose.onNodeWithText("Left").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("left", prefs[SettingsManager.EDGE_SWIPE_EDGE])
        compose.onNodeWithText(edgeSwipeSubtitle(ScreenEdge.LEFT)).assertExists()
    }

    @Test
    fun anOptionWhosePermissionWasRevokedShowsOffAndIsSwitchedOff() {
        prefs.edit()
            .put(SettingsManager.FLOATING_SEARCH_BUTTON, true)
            .put(SettingsManager.NOTIFICATION_SHORTCUT, true)
            .commit()
        showPortal()
        switch("Floating Search Button").performScrollTo().assertIsOff()
        switch("Notification Shortcut").assertIsOff()
        assertFalse(prefs[SettingsManager.FLOATING_SEARCH_BUTTON])
        assertFalse(prefs[SettingsManager.NOTIFICATION_SHORTCUT])
        assertFalse(FloatingSearchOverlays.isButtonShowing)
    }

    @Test
    fun withThePermissionAnOptionThatIsOnShowsOn() {
        ShadowSettings.setCanDrawOverlays(true)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).commit()
        showPortal()
        switch("Floating Search Button").performScrollTo().assertIsOn()
        assertTrue(FloatingSearchOverlays.isButtonShowing)
    }

    @Test
    fun theEdgeSwipeSlidersSayWhereAndHowTall() {
        org.junit.Assert.assertEquals("Top", edgeSwipePositionLabel(0))
        org.junit.Assert.assertEquals("Middle", edgeSwipePositionLabel(50))
        org.junit.Assert.assertEquals("Bottom", edgeSwipePositionLabel(100))
        org.junit.Assert.assertEquals("30% Down", edgeSwipePositionLabel(30))
    }
}
