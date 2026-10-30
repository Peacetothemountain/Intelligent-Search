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
            // "At 6" is AM or PM by a guess, so Enter leaves it for a tap (the certainty rule); the rest say which.
            assertEquals(query, query != "at 6", alarm.runsOnEnter)
        }
    }

    @Test
    fun theAlarmSaysWhichDayItRingsAndOpensTheClock() {
        // The day and time come first, in the region's words; it was "Clock Alarm · Tomorrow", in English everywhere.
        assertEquals("Tomorrow, 7:30 AM · Clock Alarm", parse("7:30am")!!.subtitle)
        assertEquals("Today, 7:30 PM · Clock Alarm", parse("7:30pm")!!.subtitle)
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
        // A bare hour follows the one alarm rule on both clocks, the next of 6 AM and 6 PM (it was 06:00 as written),
        // and is a guess Enter leaves for a tap; minutes are 24-hour notation, which Enter sets.
        assertEquals("Set Alarm for 18:00", parse("at 6", is24Hour = true)!!.title)
        assertFalse(parse("at 6", is24Hour = true)!!.runsOnEnter)
        assertTrue(parse("at 7:30", is24Hour = true)!!.runsOnEnter)
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
        // The row says when as the full phrasings' rows do, first in the subtitle; it was "Remind: Laundry at 4:00 PM"
        // over "Calendar Reminder · Thu, Oct 22", English words in US order on every phone.
        val cases = mapOf(
            "laundry in 2 hrs" to Triple(at(22, 16), "Remind: Laundry", "Today, 4:00 PM"),
            "laundry in 90 minutes" to Triple(at(22, 15, 30), "Remind: Laundry", "Today, 3:30 PM"),
            "take out the bins in an hour" to Triple(at(22, 15), "Remind: Take out the bins", "Today, 3:00 PM"),
            "call mom at 5pm" to Triple(at(22, 17), "Remind: Call mom", "Today, 5:00 PM"),
            "dinner at 7" to Triple(at(22, 19), "Remind: Dinner", "Today, 7:00 PM"),
            "pay rent tomorrow 9am" to Triple(at(23, 9), "Remind: Pay rent", "Tomorrow, 9:00 AM"),
            "tomorrow 9am pay rent" to Triple(at(23, 9), "Remind: Pay rent", "Tomorrow, 9:00 AM"),
            "standup monday at 3" to Triple(at(26, 15), "Remind: Standup", "Mon, Oct 26, 3:00 PM"),
            "gym tonight at 8" to Triple(at(22, 20), "Remind: Gym", "Today, 8:00 PM"),
            // 1 PM has passed today, so it is tomorrow's.
            "call mom at 1pm" to Triple(at(23, 13), "Remind: Call mom", "Tomorrow, 1:00 PM")
        )
        for ((query, expected) in cases) {
            val reminder = parse(query)
            assertNotNull(query, reminder)
            assertEquals(query, Kind.REMINDER, reminder!!.kind)
            assertEquals(query, expected.first, reminder.triggerMillis)
            assertEquals(query, expected.second, reminder.title)
            assertEquals(query, "${expected.third} · Calendar Reminder", reminder.subtitle)
            // The certainty rule: Enter sets only a time from now, or a time that says itself on a day that is named.
            // "Call mom at 5pm" (which day?), "dinner at 7" and "standup monday at 3" (AM or PM?) wait for a tap.
            val certain = query in setOf(
                "laundry in 2 hrs", "laundry in 90 minutes", "take out the bins in an hour", "pay rent tomorrow 9am",
                "tomorrow 9am pay rent", "gym tonight at 8"
            )
            assertEquals(query, certain, reminder.runsOnEnter)
        }
    }

    @Test
    fun theReminderOpensTheCalendarWithTheTaskAndTime() {
        val reminder = parse("pay rent tomorrow 9am")!!
        assertEquals("Tomorrow, 9:00 AM · Calendar Reminder", reminder.subtitle)
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

    @Test
    fun openingHoursSearchesAreNoReminder() {
        // Each was a reminder Enter ran, opening the calendar instead of searching.
        for (query in listOf(
            "restaurants open at 7pm", "stores open at 9", "costco hours at 10", "bars open till 2am", "happy hour at 5",
            "pharmacy closes at 9pm"
        )) {
            assertNull(query, parse(query))
        }
        // A task still is one.
        assertEquals(Kind.REMINDER, parse("dinner at 8pm")?.kind)
        assertEquals(Kind.REMINDER, parse("close the garage at 9pm")?.kind)
    }

    @Test
    fun aTimeAfterWordsThatArentATaskIsNoReminder() {
        // Each was a reminder Enter ran, opening the calendar instead of searching for the train, game or service.
        for (query in listOf(
            "trains to london at 8am", "nba game tonight at 8", "uber at 5am", "church at 10am", "mass at 9",
            "superbowl at 6:30pm", "pharmacy at 9pm", "concert tonight at 8"
        )) {
            assertNull(query, parse(query))
        }
        // Something to do, or a plan of one's own, still is one.
        for (query in listOf("pick up kids at 3:15", "lunch with sarah at 12:30", "dentist tomorrow at 9am", "take pills at 8:30pm")) {
            assertEquals(query, Kind.REMINDER, parse(query)?.kind)
        }
    }

    private fun on(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply { clear(); set(2026, month, day, hour, minute, 0) }.timeInMillis

    @Test
    fun aDateInsideTheTaskIsReadAsTheFullPhrasingReadsIt() {
        // Each was set for today or tomorrow with the date words left in the task ("gym tomorrow morning at 7" was
        // today at 7 PM).
        val cases = mapOf(
            "gym tomorrow morning at 7" to (at(23, 7) to "Remind: Gym"),
            "call mom tomorrow evening at 7" to (at(23, 19) to "Remind: Call mom"),
            "dentist on the 3rd at 2pm" to (on(Calendar.NOVEMBER, 3, 14) to "Remind: Dentist"),
            "dinner next week at 7" to (at(29, 19) to "Remind: Dinner"),
            "dentist next friday at 2pm" to (at(30, 14) to "Remind: Dentist"),
            "call mom on thanksgiving at 5" to (on(Calendar.NOVEMBER, 26, 17) to "Remind: Call mom"),
            "haircut on nov 12 at 10am" to (on(Calendar.NOVEMBER, 12, 10) to "Remind: Haircut")
        )
        for ((query, expected) in cases) {
            val reminder = parse(query)
            assertEquals(query, expected.first, reminder?.triggerMillis)
            assertEquals(query, expected.second, reminder?.title)
        }
        // Days it can't read, or a holiday the task may only be for, are no reminder rather than one for today.
        for (query in listOf("call mom on mother's day at 5", "buy candy for halloween at 6pm", "call mom on the 3rd tomorrow at 5")) {
            assertNull(query, parse(query))
        }
    }

    @Test
    fun aMealOrTonightSaysWhichHalfOfTheDayABareHourIs() {
        fun at5am(query: String) = ShortActionPhrasing.parse(query, at(22, 5), false, utc, Locale.US)
        // At 5 AM "dinner at 7" was 7 AM.
        assertEquals(at(22, 19), at5am("dinner at 7")?.triggerMillis)
        assertEquals(at(22, 12, 30), at5am("lunch with sarah at 12:30")?.triggerMillis)
        assertEquals(at(22, 8), at5am("breakfast with sam at 8")?.triggerMillis)
        // "Tonight at 12" was noon; "tonight" said once its time has gone was set in the past.
        assertEquals(at(23, 0), parse("gym tonight at 12")?.triggerMillis)
        assertEquals(at(23, 1), parse("gym tonight at 1")?.triggerMillis)
        assertNull(ShortActionPhrasing.parse("gym tonight at 8", at(22, 21), false, utc, Locale.US))
    }

    @Test
    fun commonAppointmentsAreTasks() {
        // Each was no suggestion.
        for (query in listOf("doctor's appointment at 3pm", "dr appointment at 3pm", "team meeting at 2pm", "swim class at 5pm")) {
            assertEquals(query, Kind.REMINDER, parse(query)?.kind)
        }
        // A word like "team" starts a task only with one after it.
        assertNull(parse("team liquid at 2pm"))
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

    @Test
    fun clockAndChanceAnswersKeepTheirQueries() {
        val search = coordinator()
        // "random 1 to 10" also reads as a reminder called "random 1 to" at 10.
        val random = search.executeSearchSync("random 1 to 10")
        assertNotNull(random.randomAnswer)
        assertNull(random.shortAction)
        val conversion = search.executeSearchSync("3pm PST in IST")
        assertNotNull(conversion.timeAnswer)
        assertNull(conversion.shortAction)
        assertNull(conversion.randomAnswer)
        // Date maths answers first.
        val days = search.executeSearchSync("days until friday")
        assertNotNull(days.dateAnswer)
        assertNull(days.timeAnswer)
        assertNull(days.randomAnswer)
    }

    @Test
    fun shortWeekdaysAndADayOfTheMonthBeforeATimeAreItsDay() {
        // Each was set for today or tomorrow, the day words left in the task. Thursday 22 October, 2 PM.
        val cases = mapOf(
            "dentist fri 2pm" to (at(23, 14) to "Remind: Dentist"),
            "gym sat 9am" to (at(24, 9) to "Remind: Gym"),
            "call mom mon at 5pm" to (at(26, 17) to "Remind: Call mom"),
            "gym tues 6am" to (at(27, 6) to "Remind: Gym"),
            "fri 2pm dentist" to (at(23, 14) to "Remind: Dentist"),
            "dentist the 12th at 10am" to (on(Calendar.NOVEMBER, 12, 10) to "Remind: Dentist"),
            "dentist friday the 13th at 2pm" to (on(Calendar.NOVEMBER, 13, 14) to "Remind: Dentist")
        )
        for ((query, expected) in cases) {
            val reminder = parse(query)
            assertEquals(query, expected.first, reminder?.triggerMillis)
            assertEquals(query, expected.second, reminder?.title)
        }
        // "Sun cream" is no Sunday; a weekday its date doesn't fall on, or a date that isn't before the time, is none.
        assertEquals(at(22, 16), parse("buy sun cream at 4pm")?.triggerMillis)
        assertNull(parse("dentist friday the 12th at 2pm"))
        assertNull(parse("dentist the 12th tomorrow at 2pm"))
    }

    @Test
    fun aTimeSaidRoughlyOrRepeatedIsNoReminder() {
        // Each was a reminder at the time given.
        for (query in listOf(
            "call mom around 5pm", "pay rent by 5pm", "lunch 12 - 1pm", "call mom before 6pm",
            "pay rent on the 1st of every month at 9am", "gym every monday at 6am", "pick up kids tonight at 5"
        )) {
            assertNull(query, parse(query))
        }
    }

    @Test
    fun wordsThatSayWhichHalfOfTheDayGoBeforeTonightAndALeadingZeroIsAsWritten() {
        // "Dinner tonight at 5:30" was 5:30 the next morning, "pills before bed at 10" said at 9 AM 10 AM, and "standup
        // 09:30" 9:30 PM.
        assertEquals(at(22, 17, 30), parse("dinner tonight at 5:30")?.triggerMillis)
        assertEquals(at(22, 22), ShortActionPhrasing.parse("pills before bed at 10", at(22, 9), false, utc, Locale.US)?.triggerMillis)
        assertEquals(at(22, 18), ShortActionPhrasing.parse("call mom after work at 6", at(22, 5), false, utc, Locale.US)?.triggerMillis)
        assertEquals(at(23, 9, 30), parse("standup 09:30")?.triggerMillis)
        assertEquals(at(23, 7), parse("call mom at 07:00")?.triggerMillis)
    }

    @Test
    fun wordsThatSayWhichHalfOfTheDayCountOnA24HourClockToo() {
        // On a 24-hour phone each was the morning hour, tomorrow: 07:00, 01:00, 07:30, and tomorrow at 07:00.
        assertEquals(at(22, 19), parse("dinner at 7", is24Hour = true)?.triggerMillis)
        assertEquals("Today, 19:00 · Calendar Reminder", parse("dinner at 7", is24Hour = true)?.subtitle)
        assertEquals(at(23, 13), ShortActionPhrasing.parse("lunch at 1", at(22, 14), true, utc, Locale.US)?.triggerMillis)
        // Minutes there are 24-hour notation, so "dinner at 7:30" is 07:30 as written, which no dinner is: none (it was
        // 19:30).
        assertNull(parse("dinner at 7:30", is24Hour = true))
        assertEquals(at(23, 19), parse("dinner tomorrow at 7", is24Hour = true)?.triggerMillis)
        assertEquals(at(22, 21), parse("gym tonight at 9", is24Hour = true)?.triggerMillis)
        // A leading zero, or an hour past 12, is as written.
        assertEquals(at(23, 7), parse("dinner at 07:00", is24Hour = true)?.triggerMillis)
        assertEquals(at(22, 18), parse("dinner at 18:00", is24Hour = true)?.triggerMillis)
    }

    @Test
    fun aMealThatSaysWhatTheTaskIsForLeavesTheHourAlone() {
        // Said at 8 AM this was 10 PM.
        assertEquals(at(22, 10), ShortActionPhrasing.parse("book dinner reservations at 10", at(22, 8), false, utc, Locale.US)?.triggerMillis)
        assertEquals(at(22, 19), ShortActionPhrasing.parse("dinner at 7", at(22, 5), false, utc, Locale.US)?.triggerMillis)
    }

    @Test
    fun aDayTheReaderCantPlaceIsNoReminder() {
        // "Some sun" was Sunday; the others were tomorrow or today, the day words left in the task.
        assertEquals(at(22, 16), parse("get some sun 4pm")?.triggerMillis)
        assertNull(parse("pay rent on december first at 9am"))
        assertNull(parse("call grandpa on veterans day at 11am"))
        assertNull(parse("buy tickets for saturday's game at 5pm"))
        assertEquals(at(23, 17), parse("call mom tmw 5pm")?.triggerMillis)
    }

    @Test
    fun aBareHourIsReadAsTheFullPhrasingReadsItOnA24HourClock() {
        fun at10(query: String) = ShortActionPhrasing.parse(query, at(22, 10), true, utc, Locale.US)?.triggerMillis
        // On a 24-hour phone each was the small hours, tomorrow, while "remind me to pick up kids at 3" was 3 PM.
        assertEquals(at(22, 15), at10("pick up kids at 3"))
        // Minutes are 24-hour notation there, read as written: the next 02:30 (it was 14:30).
        assertEquals(at(23, 2, 30), at10("call mom at 2:30"))
        assertEquals(at(23, 15), at10("pick up kids fri at 3"))
        assertEquals(at(23, 16), at10("call mom tmrw at 4"))
        assertEquals(at(23, 16), at10("dentist tomorrow at 4"))
        // 8 to 11 is the next one still ahead, a leading zero or an hour past 12 as written.
        assertEquals(at(22, 11), at10("meds at 11"))
        assertEquals(at(23, 9), at10("meds at 09:00"))
        assertEquals(at(22, 15), at10("pick up kids at 15:00"))
    }

    @Test
    fun theRowSaysWhenTheWayTheRegionWritesIt() {
        // In Germany these read "Remind: Dinner at 19:00 · Fr., Okt. 30" and "Remind: Dentist on Dienstag at 03:30".
        fun german(query: String) = ShortActionPhrasing.parse(query, now, true, utc, Locale.GERMANY)
        assertEquals("Heute, 19:00 · Calendar Reminder", german("dinner at 7")?.subtitle)
        assertEquals("Do., 12. Nov., 16:00 · Calendar Reminder", german("doctor on nov 12 at 4pm")?.subtitle)
        assertEquals("Remind: Doctor", german("doctor on nov 12 at 4pm")?.title)
        assertEquals("Morgen, 07:30 · Clock Alarm", german("7:30am")?.subtitle)
    }

    @Test
    fun aTaskWithWordsTheReaderLeftIsNoReminder() {
        // Each was a reminder Enter ran: today at the time, the date or typo in the task, or "Call mom @ at 5:00 PM".
        for (query in listOf(
            "call mom tomorrrow 5pm", "pay rent 1.12 at 9am", "dentist 15.11 at 3pm", "call mom tmrw in 2 hours",
            "tomorrow 9am eastern call mom", "call mom 5pm est"
        )) {
            assertNull(query, parse(query))
        }
        assertEquals("Remind: Call mom", parse("call mom @ 5pm")?.title)
        assertEquals(at(22, 17), parse("call mom @ 5pm")?.triggerMillis)
    }

    @Test
    fun raceDistancesAndDecadesAreNoTimer() {
        // Each started a timer on Enter: 13 hr 20 min for "800m", 1 min 30 sec for "90s".
        for (query in listOf("800m", "400m", "100m", "200 m", "90s", "80s", "60s")) {
            assertNull(query, parse(query))
        }
        // Lengths still are timers, and "timer" makes any amount one.
        assertEquals(900, parse("15m")?.durationSeconds)
        assertEquals(45, parse("45s")?.durationSeconds)
        assertEquals(300, parse("300s")?.durationSeconds)
        assertEquals(6_000, parse("100 min")?.durationSeconds)
        assertEquals(48_000, parse("800m timer")?.durationSeconds)
    }

    @Test
    fun enterRunsOnlyAReminderWhoseDayAndTimeAreSaid() {
        // The certainty rule: a time that says itself on a day that is named. Each ran on Enter.
        assertEquals(true, parse("call mom tomorrow 5pm")?.runsOnEnter)
        assertEquals(true, parse("dentist tues 10am")?.runsOnEnter)
        assertEquals(false, parse("call mom at 5pm")?.runsOnEnter)
        assertEquals(false, parse("dentist friday at 10")?.runsOnEnter)
        assertEquals(false, parse("at 3")?.runsOnEnter)
        assertEquals(at(22, 15), parse("at 3")?.let { at(22, it.hour) })
        // On a 24-hour phone minutes are as written, and said.
        assertEquals(at(23, 6, 30), parse("gym tomorrow 6:30", is24Hour = true)?.triggerMillis)
        assertEquals(true, parse("gym tomorrow 6:30", is24Hour = true)?.runsOnEnter)
        assertEquals(at(23, 6, 30), parse("gym 6:30", is24Hour = true)?.triggerMillis)
        assertEquals(false, parse("gym 6:30", is24Hour = true)?.runsOnEnter)
    }

    @Test
    fun wordsTheShortReaderDoesntKnowAreNoReminder() {
        // Each was a reminder Enter ran today: "nxt week", "mañana" and "everyday" in the task, and "half 5pm" at 5:00.
        for (query in listOf("call mom nxt week 5pm", "call mom mañana 5pm", "gym everyday 6am", "call mum half 5pm")) {
            assertNull(query, parse(query))
        }
        // A task that says its half of the day: "go to bed at 11" was 11 AM, "leave for work at 7" 7 PM.
        assertEquals(at(22, 23), parse("go to bed at 11")?.triggerMillis)
        assertEquals(at(23, 7), parse("leave for work at 7")?.triggerMillis)
        assertEquals(false, parse("go to bed at 11")?.runsOnEnter)
    }
}
