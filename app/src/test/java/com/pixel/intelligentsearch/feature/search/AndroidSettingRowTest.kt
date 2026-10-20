package com.pixel.intelligentsearch.feature.search

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import com.pixel.intelligentsearch.core.search.AndroidSettingsSearch
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLauncherApps
import org.robolectric.shadows.ShadowToast

/** What TalkBack hears for an Android Settings result, that the row is one tappable target, and how it opens. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AndroidSettingRowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun inject() {
        hilt.inject()
        RecordingLauncherApps.detailsOpened.clear()
    }

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aSettingsPageReadsAsItsNameAndSettings() {
        var clicks = 0
        val battery = AndroidSettingResult("battery", "Battery", "Settings", "battery", Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        compose.setContent { AndroidSettingRow(battery) { clicks++ } }

        compose.onNodeWithContentDescription("Battery settings").assertHasClickAction().performClick()
        assertEquals(1, clicks)
        // A settings page shows its symbol, not an app icon.
        compose.onNodeWithTag(ANDROID_SETTING_APP_ICON_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun anAppInfoPageReadsAsAppInfoForTheApp() {
        val chrome = AppItem("Chrome", "com.android.chrome", ColorDrawable())
        val result = AndroidSettingResult("app_info:chrome", "Chrome App Info", "Settings › Apps", "app_info", Intent(), chrome)
        compose.setContent { AndroidSettingRow(result) {} }

        compose.onNodeWithContentDescription("App info for Chrome").assertHasClickAction()
    }

    @Test
    fun anAppInfoPageShowsTheAppsOwnIcon() {
        val icon = android.graphics.drawable.BitmapDrawable(context.resources, android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888))
        val chrome = AppItem("Chrome", "com.android.chrome", icon)
        val result = AndroidSettingResult("app_info:chrome", "Chrome App Info", "Settings › Apps", "app_info", Intent(), chrome)
        compose.setContent { AndroidSettingRow(result) {} }

        compose.onNodeWithTag(ANDROID_SETTING_APP_ICON_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun openingASettingsPageLaunchesACopyOfItsIntent() {
        val battery = AndroidSettingResult("battery", "Battery", "Settings", "battery", Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        openAndroidSetting(context, battery)

        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_POWER_USAGE_SUMMARY, started.action)
        // The result's own intent is reused while the query stands, so launching must not add flags to it.
        assertEquals(0, battery.intent.flags)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun aPageThatFailsToOpenSaysSo() {
        // Nothing on this "phone" opens the page, and there is no activity to fall back to.
        val nothingOpens = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent?) = throw ActivityNotFoundException()
            override fun startActivity(intent: Intent?, options: Bundle?) = throw ActivityNotFoundException()
        }
        val battery = AndroidSettingResult("battery", "Battery", "Settings", "battery", Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        openAndroidSetting(nothingOpens, battery)

        assertEquals("Couldn’t open Battery", ShadowToast.getTextOfLatestToast())
    }

    /** User 10, a work profile: the profile of uid 10 × 100000 + 10000. */
    private val workProfile: UserHandle = UserHandle.getUserHandleForUid(1_010_000)

    private fun workChrome() = AppItem(
        "Chrome", "com.android.chrome", ColorDrawable(),
        userHandle = workProfile,
        activityName = "com.google.android.apps.chrome.Main"
    )

    private fun appInfo(app: AppItem) = AndroidSettingResult(
        "app_info:${app.packageName}:10", "${app.name} App Info", "Settings › Apps", "app_info",
        AndroidSettingsSearch.appDetailsIntent(app.packageName), app
    )

    @Test
    @Config(shadows = [RecordingLauncherApps::class])
    fun aWorkAppsAppInfoOpensInItsOwnProfile() {
        val chrome = workChrome()
        openAndroidSetting(context, appInfo(chrome))

        assertEquals(listOf(ComponentName("com.android.chrome", "com.google.android.apps.chrome.Main") to workProfile), RecordingLauncherApps.detailsOpened)
        // Not the personal profile's App Info as well.
        assertNull(shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity)
    }

    @Test
    fun aWorkAppsAppInfoNeverFallsBackToThePersonalProfile() {
        // Robolectric's LauncherApps can't open App Info: it throws, as a phone may when the profile is off.
        openAndroidSetting(context, appInfo(workChrome()))

        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertNull("opened ${started?.action} ${started?.dataString}", started)
        assertEquals("Couldn’t open Chrome App Info", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun aPersonalAppsAppInfoOpensWithThePlainIntent() {
        val chrome = AppItem("Chrome", "com.android.chrome", ColorDrawable(), userHandle = android.os.Process.myUserHandle(), activityName = "Main")
        openAndroidSetting(context, appInfo(chrome))

        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:com.android.chrome", started.dataString)
        assertEquals(emptyList<Any>(), RecordingLauncherApps.detailsOpened)
    }
}

/** A LauncherApps that opens App Info by recording what it was asked to open. */
@Implements(LauncherApps::class)
class RecordingLauncherApps : ShadowLauncherApps() {
    @Implementation
    override fun startAppDetailsActivity(component: ComponentName?, user: UserHandle?, sourceBounds: Rect?, opts: Bundle?) {
        detailsOpened += component to user
    }

    companion object {
        val detailsOpened = mutableListOf<Pair<ComponentName?, UserHandle?>>()
    }
}
