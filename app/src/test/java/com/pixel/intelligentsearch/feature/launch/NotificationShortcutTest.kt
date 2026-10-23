package com.pixel.intelligentsearch.feature.launch

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.feature.voice.IntelligentVoiceInteractionSession
import com.pixel.intelligentsearch.feature.widget.WidgetActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The Notification Shortcut: its own quiet channel, an ongoing notification that opens search, Voice and (with Lens
 * installed) Lens actions behind immutable PendingIntents, and nothing posted without the notifications permission.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationShortcutTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Before fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun itsChannelIsQuietBadgeFreeAndPrivateOnTheLockScreen() {
        val channel = NotificationShortcut.channel()
        assertEquals("search_shortcut", channel.id)
        assertEquals("Notification Shortcut", channel.name)
        assertTrue(channel.importance <= NotificationManager.IMPORTANCE_LOW)
        assertFalse(channel.canShowBadge())
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertEquals(Notification.VISIBILITY_PRIVATE, channel.lockscreenVisibility)
    }

    @Test
    fun itIsAnOngoingSearchShortcutOnItsChannel() {
        val notification = NotificationShortcut.build(context, lensAvailable = false)
        assertEquals(NotificationShortcut.CHANNEL_ID, notification.channelId)
        assertEquals("Search Your Phone", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_NO_CLEAR != 0)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.smallIcon)
    }

    @Test
    fun aTapOpensSearchAndVoiceOpensItListening() {
        val notification = NotificationShortcut.build(context, lensAvailable = false)
        val tap = shadowOf(notification.contentIntent)
        assertTrue(tap.isActivity)
        assertTrue(notification.contentIntent.isImmutable)
        val search = tap.savedIntent
        assertEquals(ComponentName(context, WidgetActivity::class.java), search.component)
        assertFalse(search.getBooleanExtra(IntelligentVoiceInteractionSession.EXTRA_START_VOICE, false))

        assertEquals(listOf("Voice"), notification.actions.map { it.title.toString() })
        val voice = notification.actions.single().actionIntent
        assertTrue(voice.isImmutable)
        val voiceIntent = shadowOf(voice).savedIntent
        assertEquals(ComponentName(context, WidgetActivity::class.java), voiceIntent.component)
        assertTrue(voiceIntent.getBooleanExtra(IntelligentVoiceInteractionSession.EXTRA_START_VOICE, false))
    }

    @Test
    fun lensIsOfferedOnlyWhenInstalled() {
        assertFalse(com.pixel.intelligentsearch.core.system.GoogleApps.isLensAvailable(context))
        installLens()
        assertTrue(com.pixel.intelligentsearch.core.system.GoogleApps.isLensAvailable(context))

        val notification = NotificationShortcut.build(context)
        assertEquals(listOf("Voice", "Lens"), notification.actions.map { it.title.toString() })
        val lens = notification.actions[1].actionIntent
        assertTrue(lens.isImmutable)
        val lensIntent = shadowOf(lens).savedIntent
        assertEquals(ComponentName(context, WidgetActivity::class.java), lensIntent.component)
        assertEquals("com.pixel.intelligentsearch.LAUNCH_LENS", lensIntent.action)
        // Each action is its own PendingIntent, not three copies of one.
        val all = listOf(notification.contentIntent) + notification.actions.map { it.actionIntent }
        assertEquals(3, all.map { shadowOf(it).requestCode }.distinct().size)
    }

    @Test
    fun switchedOnItIsPostedAndSwitchedOffItGoes() {
        NotificationShortcut.sync(context, on = true)
        val posted = shadowOf(manager).getNotification(NotificationShortcut.NOTIFICATION_ID)
        assertNotNull(posted)
        assertNotNull(manager.getNotificationChannel(NotificationShortcut.CHANNEL_ID))

        NotificationShortcut.sync(context, on = false)
        assertNull(shadowOf(manager).getNotification(NotificationShortcut.NOTIFICATION_ID))
    }

    @Test
    fun withoutTheNotificationsPermissionNothingIsPosted() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(NotificationShortcut.needsPermission(context))
        assertFalse(NotificationShortcut.canPost(context))
        NotificationShortcut.sync(context, on = true)
        assertNull(shadowOf(manager).getNotification(NotificationShortcut.NOTIFICATION_ID))
    }

    @Test
    fun withNotificationsBlockedNothingIsPosted() {
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(NotificationShortcut.canPost(context))
        NotificationShortcut.sync(context, on = true)
        assertNull(shadowOf(manager).getNotification(NotificationShortcut.NOTIFICATION_ID))
    }

    private fun installLens() {
        val pm = shadowOf(context.packageManager)
        val lensPackage = "com.google.ar.lens"
        pm.installPackage(PackageInfo().apply {
            packageName = lensPackage
            applicationInfo = ApplicationInfo().apply { packageName = lensPackage }
        })
        val component = ComponentName(lensPackage, "$lensPackage.MainActivity")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

}
