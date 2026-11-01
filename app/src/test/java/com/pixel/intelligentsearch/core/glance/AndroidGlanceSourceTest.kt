package com.pixel.intelligentsearch.core.glance

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import com.pixel.intelligentsearch.core.data.CalendarEvent
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.feature.search.openGlanceItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationListenerService

/**
 * At a Glance read from the phone: the next alarm with its own page, the listener connected as the search opens, and
 * the calendar read only for Calendar Events.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidGlanceSourceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val source get() = AndroidGlanceSource(context, NotificationIndex())

    private val alarmOnly = GlanceToggles(alarm = true, timers = false, media = false, birthdays = false)
    private val timersOnly = GlanceToggles(alarm = false, timers = true, media = false, birthdays = false, notifications = true)
    private val calendarOnly = GlanceToggles(alarm = false, timers = false, media = false, birthdays = false, calendarAccess = true)

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

    private fun calendarWith(vararg titles: String) {
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CALENDAR)
        CalendarProvider.titles = titles.toList()
        CalendarProvider.queries = 0
        Robolectric.setupContentProvider(CalendarProvider::class.java, CalendarContract.AUTHORITY)
    }

    @Test
    fun calendarEventsAreReadForCalendarEvents() {
        calendarWith("Dentist")
        val events = runBlocking { source.snapshots(calendarOnly, emptySet()).first() }.events
        assertEquals(listOf("Dentist"), events.map { it.title })
    }

    /** Each look (the search coming back into view) reads the calendar afresh, so a moved event shows as it is now. */
    @Test
    fun eachLookReadsTheCalendarAfresh() {
        calendarWith("Dentist")
        val glance = source
        runBlocking { glance.snapshots(calendarOnly, emptySet()).first() }
        CalendarProvider.titles = listOf("Standup")
        val events = runBlocking { glance.snapshots(calendarOnly, emptySet()).first() }.events
        assertEquals(listOf("Standup"), events.map { it.title })
    }

    @Test
    fun theCalendarIsNotReadWithCalendarEventsOffOrNotAllowed() {
        calendarWith("Dentist")
        for (toggles in listOf(calendarOnly.copy(calendar = false), calendarOnly.copy(calendarAccess = false), calendarOnly.copy(alarm = true, calendar = false))) {
            assertEquals(emptyList<CalendarEvent>(), runBlocking { source.snapshots(toggles, emptySet()).first() }.events)
        }
        assertEquals("the calendar was read", 0, CalendarProvider.queries)
    }

    @Test
    fun tappingAnEventOpensItInTheCalendarAtThatOccurrence() {
        openGlanceItem(context, GlanceItem.Event(CalendarEvent("Dentist", "9:30", eventId = 7, beginMillis = 5_000L)))
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, 7), started?.data)
        assertEquals(5_000L, started?.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L))
    }

    @Test
    fun anEventWithoutAnIdOpensTheCalendarAtItsTime() {
        openGlanceItem(context, GlanceItem.Event(CalendarEvent("Dentist", "9:30", eventId = 0, beginMillis = 5_000L)))
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Uri.parse("content://com.android.calendar/time/5000"), started?.data)
    }

    /** The calendar's instances: one timed event an hour from now per title. */
    class CalendarProvider : ContentProvider() {
        companion object {
            @Volatile var titles: List<String> = emptyList()
            @Volatile var queries = 0
        }

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
            queries++
            val columns = projection ?: emptyArray()
            val begin = System.currentTimeMillis() + 60 * 60 * 1000L
            return MatrixCursor(columns).apply {
                titles.forEachIndexed { i, title ->
                    val row = mapOf(Instances.TITLE to title, Instances.BEGIN to begin, Instances.EVENT_ID to i + 1L, Instances.ALL_DAY to 0)
                    addRow(columns.map { row[it] })
                }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    @Test
    fun withoutAccessNothingAsksToReconnect() {
        grantNotificationAccess(false)
        runBlocking { source.snapshots(timersOnly, emptySet()).first() }
        assertEquals(0, ShadowNotificationListenerService.getRebindRequestCount())
    }
}
