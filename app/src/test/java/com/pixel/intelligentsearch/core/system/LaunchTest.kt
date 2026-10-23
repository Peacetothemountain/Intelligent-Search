package com.pixel.intelligentsearch.core.system

import android.app.Application
import android.app.PendingIntent
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

/** One way to open other apps and Android's pages: what is tried, in which order, and what is never opened. */
@RunWith(RobolectricTestRunner::class)
class LaunchTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val started get() = shadowOf(context)

    @Test
    fun aTapOpensTheNotificationsOwnActionFirst() {
        val own = Intent(Intent.ACTION_VIEW).setClassName("com.example.chat", "com.example.chat.Conversation")
        val pending = PendingIntent.getActivity(context, 0, own, PendingIntent.FLAG_IMMUTABLE)
        assertTrue(openPendingOrApp(context, pending, "com.example.chat", fallback = Intent("fallback")))
        assertEquals(own.component, started.nextStartedActivity.component)
        assertNull("nothing else opened", started.nextStartedActivity)
    }

    @Test
    fun withoutItsOwnActionTheFallbackOpens() {
        assertTrue(openPendingOrApp(context, null, "com.example.clock", fallback = Intent("android.intent.action.SHOW_TIMERS")))
        assertEquals("android.intent.action.SHOW_TIMERS", started.nextStartedActivity.action)
    }

    @Test
    fun anAppThatIsntInstalledDoesntOpen() {
        assertFalse(launchApp(context, "com.example.missing"))
        assertFalse(openPendingOrApp(context, null, "com.example.missing"))
        assertNull(started.nextStartedActivity)
    }

    @Test
    fun aSettingsPageOpensItsFirstPage() {
        val page = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        assertTrue(openSettingsPage(context, page, Intent(Settings.ACTION_SETTINGS)))
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, started.nextStartedActivity.action)
        assertNull(started.nextStartedActivity)
    }
}
