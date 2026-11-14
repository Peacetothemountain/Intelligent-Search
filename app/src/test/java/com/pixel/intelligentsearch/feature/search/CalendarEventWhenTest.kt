package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.data.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** What At a Glance says under an event's title, at fixed times so the answer can't depend on when the tests run. */
class CalendarEventWhenTest {
    private val zone = ZoneId.of("America/New_York")

    private fun millis(day: Int, hour: Int, minute: Int = 0, at: ZoneId = zone) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(at).toInstant().toEpochMilli()

    private fun whenIs(begin: Long, now: Long, allDay: Boolean = false) =
        calendarEventWhen(CalendarEvent("Dentist", "t", beginMillis = begin, allDay = allDay), "9:30", now, zone)

    @Test
    fun laterTodaySaysToday() {
        assertEquals("Today · 9:30", whenIs(begin = millis(6, 13), now = millis(6, 12)))
    }

    @Test
    fun anHourAheadLateInTheEveningIsTomorrow() {
        assertEquals("Tomorrow · 9:30", whenIs(begin = millis(7, 0, 30), now = millis(6, 23, 30)))
    }

    @Test
    fun aMinuteBeforeMidnightIsStillToday() {
        assertEquals("Today · 9:30", whenIs(begin = millis(6, 23, 59), now = millis(6, 23, 30)))
    }

    @Test
    fun onceItHasStartedItSaysNow() {
        assertEquals("Now", whenIs(begin = millis(6, 12), now = millis(6, 12)))
        assertEquals("Now", whenIs(begin = millis(6, 12), now = millis(6, 12, 30)))
    }

    @Test
    fun furtherAheadNamesTheWeekday() {
        val sunday = DayOfWeek.SUNDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
        assertEquals("$sunday · 9:30", whenIs(begin = millis(8, 9), now = millis(6, 12)))
    }

    /** Calendars store an all-day event at midnight UTC, which is the evening before in New York. */
    @Test
    fun anAllDayEventKeepsItsOwnDateWestOfUtc() {
        val todayAllDay = millis(6, 0, at = ZoneOffset.UTC)
        val tomorrowAllDay = millis(7, 0, at = ZoneOffset.UTC)
        assertEquals("Today · All day", whenIs(todayAllDay, now = millis(6, 20), allDay = true))
        assertEquals("Tomorrow · All day", whenIs(tomorrowAllDay, now = millis(6, 20), allDay = true))
    }
}
