package com.pixel.intelligentsearch.core.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract.Instances
import androidx.test.core.app.ApplicationProvider
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The upcoming calendar events shown at the top of the overlay. */
@RunWith(RobolectricTestRunner::class)
class UpcomingEventsTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val deviceZone = TimeZone.getDefault()

    @Before fun setUp() {
        // West of UTC, so an all-day event's UTC-midnight start reads as the afternoon before.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR)
        Robolectric.setupContentProvider(Provider::class.java, "com.android.calendar")
    }

    @After fun restoreZone() = TimeZone.setDefault(deviceZone)

    @Test
    fun allDayEventsSayAllDayAndHiddenCalendarsStayOut() = runBlocking {
        val events = SystemDataProvider.getUpcomingEvents(context)

        assertEquals(listOf("Public Holiday", "Standup"), events.map { it.title })
        val holiday = events[0]
        assertEquals("All day", holiday.startTime)
        // The calendar app is still opened at the instance's own start.
        assertEquals(UTC_MIDNIGHT, holiday.beginMillis)
        assertFalse(events[1].startTime == "All day")
    }

    companion object {
        /** Midnight UTC on a fixed day, where calendars store an all-day event's start. */
        val UTC_MIDNIGHT: Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.OCTOBER, 21)
        }.timeInMillis

        private val rows = listOf(
            mapOf(Instances.TITLE to "Public Holiday", Instances.BEGIN to UTC_MIDNIGHT, Instances.EVENT_ID to 1L, Instances.ALL_DAY to 1, Instances.VISIBLE to 1),
            mapOf(Instances.TITLE to "Standup", Instances.BEGIN to UTC_MIDNIGHT + 17 * 60 * 60 * 1000L, Instances.EVENT_ID to 2L, Instances.ALL_DAY to 0, Instances.VISIBLE to 1),
            mapOf(Instances.TITLE to "Colleague's Birthday", Instances.BEGIN to UTC_MIDNIGHT + 18 * 60 * 60 * 1000L, Instances.EVENT_ID to 3L, Instances.ALL_DAY to 0, Instances.VISIBLE to 0)
        )
    }

    /** Instance rows, honouring a "visible = 1" selection the way the calendar provider does. */
    class Provider : ContentProvider() {
        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: emptyArray()
            val visibleOnly = selection?.replace(" ", "")?.contains("${Instances.VISIBLE}=1") == true
            return MatrixCursor(columns).apply {
                rows.filter { !visibleOnly || it[Instances.VISIBLE] == 1 }.forEach { row -> addRow(columns.map { row[it] }) }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
