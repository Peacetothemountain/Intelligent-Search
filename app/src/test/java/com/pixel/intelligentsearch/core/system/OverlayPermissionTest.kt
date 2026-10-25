package com.pixel.intelligentsearch.core.system

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The way to "Display over other apps": App info with Settings' highlight on the row where Settings honors it
 * (Android 12 and 13), the list of apps from Android 14, and the other as the fallback.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayPermissionTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val pkg = "com.example.app"

    private fun assertHighlightedAppInfo(page: Intent) {
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, page.action)
        assertEquals("package:$pkg", page.dataString)
        assertEquals("system_alert_window", page.getStringExtra(":settings:fragment_args_key"))
        assertEquals("system_alert_window", page.getBundleExtra(":settings:show_fragment_args")?.getString(":settings:fragment_args_key"))
    }

    private fun assertOverlayList(page: Intent) {
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, page.action)
        assertEquals("package:$pkg", page.dataString)
        assertNull(page.extras)
    }

    private fun assertPlainAppInfo(page: Intent) {
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, page.action)
        assertEquals("package:$pkg", page.dataString)
        assertFalse(page.hasExtra(":settings:fragment_args_key"))
        assertFalse(page.hasExtra(":settings:show_fragment_args"))
    }

    @Test
    fun onAndroid12AppInfoOpensHighlightedThenTheList() {
        val pages = overlayPermissionPages(pkg, sdkInt = 31)
        assertEquals(2, pages.size)
        assertHighlightedAppInfo(pages[0])
        assertOverlayList(pages[1])
    }

    @Test
    fun onAndroid13AppInfoOpensHighlightedThenTheList() {
        val pages = overlayPermissionPages(pkg, sdkInt = 33)
        assertEquals(2, pages.size)
        assertHighlightedAppInfo(pages[0])
        assertOverlayList(pages[1])
    }

    @Test
    fun onAndroid14TheListOpensThenAppInfo() {
        val pages = overlayPermissionPages(pkg, sdkInt = 34)
        assertEquals(2, pages.size)
        assertOverlayList(pages[0])
        assertPlainAppInfo(pages[1])
    }

    @Test
    fun onAndroid15TheListOpensThenAppInfo() {
        val pages = overlayPermissionPages(pkg, sdkInt = 35)
        assertEquals(2, pages.size)
        assertOverlayList(pages[0])
        assertPlainAppInfo(pages[1])
    }

    @Test
    @Config(sdk = [33])
    fun onAndroid13ThisAppsInfoOpensHighlighted() {
        assertTrue(openOverlayPermission(context))
        val opened = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.action)
        assertEquals("package:${context.packageName}", opened.dataString)
        assertEquals("system_alert_window", opened.getStringExtra(":settings:fragment_args_key"))
        assertNull("only the first page opened", shadowOf(context).nextStartedActivity)
    }

    @Test
    @Config(sdk = [34])
    fun onAndroid14TheListOpens() {
        assertTrue(openOverlayPermission(context))
        val opened = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, opened.action)
        assertEquals("package:${context.packageName}", opened.dataString)
        assertNull("only the first page opened", shadowOf(context).nextStartedActivity)
    }
}
