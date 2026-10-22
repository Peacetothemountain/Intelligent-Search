package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.search.ShortActionPhrasing.Kind
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Shorter action phrasing: "15 min" offers a timer, "7:30am" an alarm, "call mom at 5pm" a reminder, all at a fixed
 * moment (Thursday 22 October 2026, 2:00 PM UTC). Plain numbers, phone numbers, sums, conversions, dates and searches
 * that happen to name a time are never read as actions.
 */
@RunWith(RobolectricTestRunner::class)
class ShortActionPhrasingTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, 22, 14, 0, 0) }.timeInMillis

    private fun parse(query: String, is24Hour: Boolean = false) =
        ShortActionPhrasing.parse(query, now, is24Hour, utc, Locale.US)

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, day, hour, minute, 0) }.timeInMillis

    // ---------------------------------------------------------------------------------------------
    // Timers
    // ---------------------------------------------------------------------------------------------

    @Test
    fun bareDurationsOfferATimer() {
        val cases = mapOf(
            "15 min" to (900 to "Start 15 min Timer"),
            "15m" to (900 to "Start 15 min Timer"),
            "15 minutes" to (900 to "Start 15 min Timer"),
            "90 sec" to (90 to "Start 1 min 30 sec Timer"),
            "45s" to (45 to "Start 45 sec Timer"),
            "1h 30m" to (5_400 to "Start 1 hr 30 min Timer"),
            "1h30m" to (5_400 to "Start 1 hr 30 min Timer"),
            "1 hour and 30 minutes" to (5_400 to "Start 1 hr 30 min Timer"),
            "1.5 hours" to (5_400 to "Start 1 hr 30 min Timer"),
            "2 hrs" to (7_200 to "Start 2 hr Timer"),
            "10 minutes timer" to (600 to "Start 10 min Timer"),
            "24h" to (86_400 to "Start 24 hr Timer")
        )
        for ((query, expected) in cases) {
            val timer = parse(query)
            assertNotNull(query, timer)
            assertEquals(query, Kind.TIMER, timer!!.kind)
            assertEquals(query, expected.first, timer.durationSeconds)
            assertEquals(query, expected.second, timer.title)
            assertTrue(query, timer.runsOnEnter)
        }
    }

    @Test
    fun theTimerOpensTheClockToStartIt() {
        val intent = parse("15 min")!!.intent
        assertEquals(AlarmClock.ACTION_SET_TIMER, intent.action)
        assertEquals(900, intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0))
        // The clock shows the timer for the user to start; nothing is started behind their back.
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    @Test
    fun durationsThatArentATimerAreLeftAlone() {
        for (query in listOf(
            "0 min", "25 hours", "5m 5m", "15 min + 5", "15 min in seconds", "15 minutes ago", "15", "min",
            "15 min walk", "100 mph", "5 km"
        )) {
            assertEquals(query, null, parse(query)?.takeIf { it.kind == Kind.TIMER })
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Alarms
    // ---------------------------------------------------------------------------------------------

    @Test
    fun timesWithAmPmOrAtOfferAnAlarm() {
        val cases = mapOf(
            "7:30am" to Triple(7, 30, "Set Alarm for 7:30 AM"),
            "7:30 AM" to Triple(7, 30, "Set Alarm for 7:30 AM"),
            "730am" to Triple(7, 30, "Set Alarm for 7:30 AM"),
            "1230pm" to Triple(12, 30, "Set Alarm for 12:30 PM"),
            "7 pm" to Triple(19, 0, "Set Alarm for 7:00 PM"),
            "12am" to Triple(0, 0, "Set Alarm for 12:00 AM"),
            "6am alarm" to Triple(6, 0, "Set Alarm for 6:00 AM"),
            // At 2 PM on a 12-hour clock, "at 6" is the 6 o'clock still ahead: this evening's.
            "at 6" to Triple(18, 0, "Set Alarm for 6:00 PM"),
            "at 19:30" to Triple(19, 30, "Set Alarm for 7:30 PM")
        )
        for ((query, expected) in cases) {
            val alarm = parse(query)
            assertNotNull(query, alarm)
            assertEquals(query, Kind.ALARM, alarm!!.kind)
            assertEquals(query, expected.first, alarm.hour)
            assertEquals(query, expected.second, alarm.minute)
            assertEquals(query, expected.third, alarm.title)
            assertTrue(query, alarm.runsOnEnter)
        }
    }

    @Test
    fun theAlarmSaysWhichDayItRingsAndOpensTheClock() {
        assertEquals("Clock Alarm · Tomorrow", parse("7:30am")!!.subtitle)
        assertEquals("Clock Alarm · Today", parse("7:30pm")!!.subtitle)
        val intent = parse("7:30am")!!.intent
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(7, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    @Test
    fun aBareClockTimeIsOfferedButEnterKeepsSearching() {
        val alarm = parse("7:30")!!
        assertEquals(Kind.ALARM, alarm.kind)
        assertFalse("7:30 may be a search", alarm.runsOnEnter)
        // 7:30 this morning has passed at 2 PM, so a 12-hour clock offers this evening's.
        assertEquals(19, alarm.hour)
        assertFalse(parse("19:30")!!.runsOnEnter)
    }

    @Test
    fun aTwentyFourHourClockReadsTheHourAsWrittenAndShowsIt() {
        assertEquals(7, parse("7:30", is24Hour = true)!!.hour)
        assertEquals("Set Alarm for 07:30", parse("7:30", is24Hour = true)!!.title)
        assertEquals("Set Alarm for 06:00", parse("at 6", is24Hour = true)!!.title)
        assertEquals("Set Alarm for 19:30", parse("7:30pm", is24Hour = true)!!.title)
    }

    @Test
    fun numbersThatArentTimesAreLeftAlone() {
        for (query in listOf(
            "7", "730", "1234", "13pm", "0am", "10:61", "25:00", "7.30", "3:45:10", "555-1234", "555 1234",
            "+1 415 555 2671", "4155552671", "3/4", "2026", "1:2"
        )) {
            assertNull(query, parse(query))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Reminders
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aTaskWithATimeOffersAReminder() {
        val cases = mapOf(
            "laundry in 2 hrs" to (at(22, 16) to "Remind: Laundry at 4:00 PM"),
            "laundry in 90 minutes" to (at(22, 15, 30) to "Remind: Laundry at 3:30 PM"),
            "take out the bins in an hour" to (at(22, 15) to "Remind: Take out the bins at 3:00 PM"),
            "call mom at 5pm" to (at(22, 17) to "Remind: Call mom at 5:00 PM"),
            "dinner at 7" to (at(22, 19) to "Remind: Dinner at 7:00 PM"),
            "pay rent tomorrow 9am" to (at(23, 9) to "Remind: Pay rent tomorrow at 9:00 AM"),
            "tomorrow 9am pay rent" to (at(23, 9) to "Remind: Pay rent tomorrow at 9:00 AM"),
            "standup monday at 3" to (at(26, 15) to "Remind: Standup on Monday at 3:00 PM"),
            "gym tonight at 8" to (at(22, 20) to "Remind: Gym at 8:00 PM"),
            // 1 PM has passed today, so it is tomorrow's.
            "call mom at 1pm" to (at(23, 13) to "Remind: Call mom tomorrow at 1:00 PM")
        )
        for ((query, expected) in cases) {
            val reminder = parse(query)
            assertNotNull(query, reminder)
            assertEquals(query, Kind.REMINDER, reminder!!.kind)
            assertEquals(query, expected.first, reminder.triggerMillis)
            assertEquals(query, expected.second, reminder.title)
            assertTrue(query, reminder.runsOnEnter)
        }
    }

    @Test
    fun theReminderOpensTheCalendarWithTheTaskAndTime() {
        val reminder = parse("pay rent tomorrow 9am")!!
        assertEquals("Calendar Reminder · Fri, Oct 23", reminder.subtitle)
        val intent = reminder.intent
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("Pay rent", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(at(23, 9), intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
    }

    @Test
    fun aReminderDaysAwayIsOfferedButEnterKeepsSearching() {
        val reminder = parse("laundry in 2 days")!!
        assertEquals(Kind.REMINDER, reminder.kind)
        assertFalse(reminder.runsOnEnter)
    }

    @Test
    fun searchesThatNameATimeAreLeftAlone() {
        for (query in listOf(
            "weather tomorrow 9am", "what time is it at 6pm", "time in london at 5pm", "flights at 5pm",
            "is it open at 9am?", "dec 25 at 5pm", "tomorrow at 9", "iphone 15", "bus 42", "weather tomorrow",
            "pay rent tomorrow", "5 + 3 at 5pm", "call 555 1234", "pizza", "days until christmas", "movies at 7pm"
        )) {
            assertEquals(query, null, parse(query)?.takeIf { it.kind == Kind.REMINDER })
        }
    }

    // ---------------------------------------------------------------------------------------------
    // In the search: the full phrasings, the calculator, converters and date maths come first
    // ---------------------------------------------------------------------------------------------

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun coordinator() = UnifiedSearchCoordinator(
        context, SystemActionRouter(context, SystemToggleManager(context)), MultiProfileManager(context), SettingsManager(context)
    )

    @Test
    fun theSearchOffersTheShortPhrasingOnlyWhenNothingElseReadsTheQuery() {
        val search = coordinator()
        assertEquals(Kind.TIMER, search.executeSearchSync("15 min").shortAction?.kind)
        assertEquals(Kind.ALARM, search.executeSearchSync("7:30am").shortAction?.kind)
        assertEquals(Kind.REMINDER, search.executeSearchSync("call mom at 5pm").shortAction?.kind)

        // A conversion and a sum keep their answers.
        val conversion = search.executeSearchSync("15 min in seconds")
        assertNotNull(conversion.mathResult)
        assertNull(conversion.shortAction)
        assertNull(search.executeSearchSync("15 min + 5").shortAction)
        assertNull(search.executeSearchSync("12*4+7").shortAction)
        // Date maths keeps its answer.
        val days = search.executeSearchSync("days between 2026-03-01 and 2026-06-30")
        assertNotNull(days.dateAnswer)
        assertNull(days.shortAction)
        // The full phrasing keeps its own parser.
        val full = search.executeSearchSync("set a timer for 10 minutes")
        assertTrue(full.parsedIntent is NaturalLanguageIntentParser.ParsedIntent.Timer)
        assertNull(full.shortAction)
    }
}
