package com.pixel.intelligentsearch.core.glance

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.feature.search.openGlanceItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationListenerService

/** At a Glance read from the phone: the next alarm with its own page, and the listener connected as the search opens. */
@RunWith(RobolectricTestRunner::class)
class AndroidGlanceSourceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val source get() = AndroidGlanceSource(context, NotificationIndex())

    private val alarmOnly = GlanceToggles(alarm = true, timers = false, media = false, birthdays = false)
    private val timersOnly = GlanceToggles(alarm = false, timers = true, media = false, birthdays = false, notifications = true)

    /** The page the clock app set its alarm with, as AlarmClockInfo.getShowIntent hands it back. */
    private val alarmPage = Intent(Intent.ACTION_VIEW).setClassName("com.example.clock", "com.example.clock.AlarmPage")

    @Before fun resetRebinds() = ShadowNotificationListenerService.reset()

    private fun grantNotificationAccess(granted: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(NotificationAccess.component(context), granted)
    }

    @Test
    fun theNextAlarmKeepsThePageItsAppGaveIt() {
        val show = PendingIntent.getActivity(context, 0, alarmPage, PendingIntent.FLAG_IMMUTABLE)
        val ring = PendingIntent.getBroadcast(context, 1, Intent("ring"), PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis() + 60 * 60 * 1000L
        context.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), ring)
        val alarm = runBlocking { source.snapshots(alarmOnly, emptySet()).first() }.alarm
        assertEquals(at, alarm?.triggerAt)
        assertEquals(show, alarm?.showIntent)
    }

    @Test
    fun tappingTheAlarmOpensItsOwnPage() {
        val show = PendingIntent.getActivity(context, 0, alarmPage, PendingIntent.FLAG_IMMUTABLE)
        openGlanceItem(context, GlanceItem.Alarm(GlanceAlarm(1_000L, show)))
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(alarmPage.component, started?.component)
    }

    @Test
    fun anAlarmWithoutAPageOpensTheClocksAlarms() {
        openGlanceItem(context, GlanceItem.Alarm(GlanceAlarm(1_000L)))
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, started?.action)
    }

    /** Android can drop the listener (after an update, say); opening the search asks it to connect again. */
    @Test
    fun openingAtAGlanceReconnectsTheListenerWhenGranted() {
        grantNotificationAccess(true)
        runBlocking { source.snapshots(timersOnly, emptySet()).first() }
        assertEquals(1, ShadowNotificationListenerService.getRebindRequestCount())
    }

    @Test
    fun withoutAccessNothingAsksToReconnect() {
        grantNotificationAccess(false)
        runBlocking { source.snapshots(timersOnly, emptySet()).first() }
        assertEquals(0, ShadowNotificationListenerService.getRebindRequestCount())
    }
}
