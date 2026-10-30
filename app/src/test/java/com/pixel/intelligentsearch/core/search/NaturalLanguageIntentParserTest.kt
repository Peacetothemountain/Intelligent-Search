package com.pixel.intelligentsearch.core.search

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class NaturalLanguageIntentParserTest {

    @org.junit.Test
    fun timerRejectsDurationsBeyondAlarmClockLimitInsteadOfOverflowing() {
        // 71_582_789 * 60 wraps a 32-bit Int to 44; it must be rejected, not turned into a 44s timer.
        org.junit.Assert.assertNull(NaturalLanguageIntentParser.parse("timer 71582789"))
        org.junit.Assert.assertNull(NaturalLanguageIntentParser.parse("timer 99999999999 minutes"))
        org.junit.Assert.assertNull(NaturalLanguageIntentParser.parse("timer 25 hours"))
    }

    @org.junit.Test
    fun timerReadsClockFormsInsteadOfConcatenatingDigits() {
        val ninetySeconds = NaturalLanguageIntentParser.parse("timer 1:30") as NaturalLanguageIntentParser.ParsedIntent.Timer
        assertEquals(90, ninetySeconds.durationSeconds)
        val hourAndFive = NaturalLanguageIntentParser.parse("timer 1:05:00") as NaturalLanguageIntentParser.ParsedIntent.Timer
        assertEquals(3_900, hourAndFive.durationSeconds)
        assertNull(NaturalLanguageIntentParser.parse("timer 2 3"))
        assertNull(NaturalLanguageIntentParser.parse("timer 1:75"))
        val fifteen = NaturalLanguageIntentParser.parse("timer 15") as NaturalLanguageIntentParser.ParsedIntent.Timer
        assertEquals(900, fifteen.durationSeconds)
    }

    @org.junit.Test
    fun timerAcceptsUpToTwentyFourHours() {
        val day = NaturalLanguageIntentParser.parse("timer 24 hours") as NaturalLanguageIntentParser.ParsedIntent.Timer
        org.junit.Assert.assertEquals(86_400, day.durationSeconds)
        val mixed = NaturalLanguageIntentParser.parse("timer 1h 30m 15s") as NaturalLanguageIntentParser.ParsedIntent.Timer
        org.junit.Assert.assertEquals(5_415, mixed.durationSeconds)
        // Named in the app's one duration style, as the short phrasing's timers are.
        org.junit.Assert.assertEquals("Timer 1 hr 30 min 15 sec", mixed.label)
    }

    @org.junit.Test
    fun directMessageSupportsUnicodeRecipientNames() {
        val intent = NaturalLanguageIntentParser.parse("text José saying see you soon")
            as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        org.junit.Assert.assertEquals("José", intent.recipientName)
        org.junit.Assert.assertEquals("see you soon", intent.body)
    }


    @Test
    fun testTimerParsing() {
        val res15m = NaturalLanguageIntentParser.parse("set timer 15m")
        assertTrue(res15m is NaturalLanguageIntentParser.ParsedIntent.Timer)
        assertEquals(900, (res15m as NaturalLanguageIntentParser.ParsedIntent.Timer).durationSeconds)

        val resComplex = NaturalLanguageIntentParser.parse("timer 5 mins 30 secs")
        assertTrue(resComplex is NaturalLanguageIntentParser.ParsedIntent.Timer)
        assertEquals(330, (resComplex as NaturalLanguageIntentParser.ParsedIntent.Timer).durationSeconds)
    }

    @Test
    fun testAlarmParsing() {
        val res = NaturalLanguageIntentParser.parse("set alarm for 7:30 am")
        assertTrue(res is NaturalLanguageIntentParser.ParsedIntent.Alarm)
        val alarm = res as NaturalLanguageIntentParser.ParsedIntent.Alarm
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)

        val resPm = NaturalLanguageIntentParser.parse("alarm 8pm")
        assertTrue(resPm is NaturalLanguageIntentParser.ParsedIntent.Alarm)
        assertEquals(20, (resPm as NaturalLanguageIntentParser.ParsedIntent.Alarm).hour)
    }

    @Test
    fun testReminderAndCalendarParsing() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 9, 12, 0, 0)
        }.timeInMillis

        val reminderRes = NaturalLanguageIntentParser.parse("remind me to buy groceries tomorrow at 5pm", now)
        assertTrue(reminderRes is NaturalLanguageIntentParser.ParsedIntent.Reminder)
        val reminder = reminderRes as NaturalLanguageIntentParser.ParsedIntent.Reminder
        assertTrue(reminder.task.contains("buy groceries", ignoreCase = true))

        val cal = Calendar.getInstance().apply { timeInMillis = reminder.triggerMillis }
        assertEquals(10, cal.get(Calendar.DAY_OF_MONTH)) // Tomorrow
        assertEquals(17, cal.get(Calendar.HOUR_OF_DAY)) // 5pm

        val meetingRes = NaturalLanguageIntentParser.parse("meeting with Sarah tomorrow at 3pm", now)
        assertTrue(meetingRes is NaturalLanguageIntentParser.ParsedIntent.CalendarEvent)
        val meeting = meetingRes as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertTrue(meeting.title.contains("Sarah", ignoreCase = true))
    }

    @Test
    fun testDirectMessagingAndNavigation() {
        val msgRes = NaturalLanguageIntentParser.parse("message Alice to see you tonight")
        assertTrue(msgRes is NaturalLanguageIntentParser.ParsedIntent.DirectMessage)
        val msg = msgRes as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        assertEquals("Alice", msg.recipientName)
        assertEquals("see you tonight", msg.body)

        val navRes = NaturalLanguageIntentParser.parse("directions to Central Park")
        assertTrue(navRes is NaturalLanguageIntentParser.ParsedIntent.Navigation)
        assertEquals("Central Park", (navRes as NaturalLanguageIntentParser.ParsedIntent.Navigation).destination)
    }

    /** The phrasings the Play listing advertises, word for word. */
    @Test
    fun testStoreListingExamples() {
        val timer = NaturalLanguageIntentParser.parse("Set a timer for 10 minutes")
        assertEquals(600, (timer as NaturalLanguageIntentParser.ParsedIntent.Timer).durationSeconds)
        val started = NaturalLanguageIntentParser.parse("start a timer for 90 seconds")
        assertEquals(90, (started as NaturalLanguageIntentParser.ParsedIntent.Timer).durationSeconds)

        for (phrase in listOf("Directions home", "navigate home", "take me home")) {
            val home = NaturalLanguageIntentParser.parse(phrase)
            assertEquals(phrase, "Home", (home as NaturalLanguageIntentParser.ParsedIntent.Navigation).destination)
        }
        val takeMe = NaturalLanguageIntentParser.parse("take me to the airport")
        assertEquals("the airport", (takeMe as NaturalLanguageIntentParser.ParsedIntent.Navigation).destination)

        val text = NaturalLanguageIntentParser.parse("Text Sarah that I'm running 5 minutes late")
        val message = text as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        assertEquals("Sarah", message.recipientName)
        assertEquals("I'm running 5 minutes late", message.body)

        assertTrue(NaturalLanguageIntentParser.parse("Wake me up at 7:30") is NaturalLanguageIntentParser.ParsedIntent.Alarm)
        assertTrue(NaturalLanguageIntentParser.parse("Remind me to call the pharmacy at 6") is NaturalLanguageIntentParser.ParsedIntent.Reminder)
        assertTrue(NaturalLanguageIntentParser.parse("Meeting tomorrow at 3 PM") is NaturalLanguageIntentParser.ParsedIntent.CalendarEvent)
    }

    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, 10, hour, minute, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun hourOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
    private fun dayOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

    @Test
    fun alarmInADurationIsTimeFromNowNotAClockHour() {
        val alarm = NaturalLanguageIntentParser.parse("alarm in 10 minutes", currentTimeMs = at(14, 55)) as NaturalLanguageIntentParser.ParsedIntent.Alarm
        assertEquals(15, alarm.hour)
        assertEquals(5, alarm.minute)
        val wake = NaturalLanguageIntentParser.parse("wake me up in 8 hours", currentTimeMs = at(23, 30)) as NaturalLanguageIntentParser.ParsedIntent.Alarm
        assertEquals(7, wake.hour)
        assertEquals(30, wake.minute)
    }

    @Test
    fun tonightAndBareHoursPickTheEveningWhenTheMorningHasPassed() {
        val tonight = NaturalLanguageIntentParser.parse("meeting tonight at 9", currentTimeMs = at(10)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(21, hourOf(tonight.startMillis))
        assertEquals(10, dayOf(tonight.startMillis))

        val afternoon = NaturalLanguageIntentParser.parse("meeting at 3", currentTimeMs = at(13)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(15, hourOf(afternoon.startMillis))
        assertEquals(10, dayOf(afternoon.startMillis))

        val explicitMorning = NaturalLanguageIntentParser.parse("meeting at 9am", currentTimeMs = at(13)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(9, hourOf(explicitMorning.startMillis))
        assertEquals(11, dayOf(explicitMorning.startMillis))
    }

    @Test
    fun bareHoursWithANamedDayPickTheLikelyHalfOfTheDay() {
        // 2026-03-10 is a Tuesday.
        val tomorrow = NaturalLanguageIntentParser.parse("meeting tomorrow at 3", currentTimeMs = at(10)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(15, hourOf(tomorrow.startMillis))
        assertEquals(11, dayOf(tomorrow.startMillis))

        val today = NaturalLanguageIntentParser.parse("meeting today at 3", currentTimeMs = at(13)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(15, hourOf(today.startMillis))
        assertEquals(10, dayOf(today.startMillis))

        val friday = NaturalLanguageIntentParser.parse("meeting friday at 2", currentTimeMs = at(10)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(14, hourOf(friday.startMillis))
        assertEquals(13, dayOf(friday.startMillis))

        // From 8 on, a bare hour on a named day is the morning.
        val breakfast = NaturalLanguageIntentParser.parse("meeting tomorrow at 9", currentTimeMs = at(10)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(9, hourOf(breakfast.startMillis))
        assertEquals(11, dayOf(breakfast.startMillis))

        // A leading zero is 24-hour notation, so the hour is meant exactly as written.
        val early = NaturalLanguageIntentParser.parse("meeting tomorrow at 07:00", currentTimeMs = at(10)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(7, hourOf(early.startMillis))
        assertEquals(11, dayOf(early.startMillis))

        val earlyFriday = NaturalLanguageIntentParser.parse("meeting friday at 06:30", currentTimeMs = at(10)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(6, hourOf(earlyFriday.startMillis))
        assertEquals(13, dayOf(earlyFriday.startMillis))
    }

    private fun minuteOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MINUTE)

    @Test
    fun timerReadsDecimalAmountsAsTheirFractionOfTheUnit() {
        // The decimal point used to split the amount: "1.5 hours" was a 5 hour timer, "2.5 min" a 5 minute one.
        val hourAndHalf = NaturalLanguageIntentParser.parse("timer 1.5 hours") as NaturalLanguageIntentParser.ParsedIntent.Timer
        assertEquals(5_400, hourAndHalf.durationSeconds)
        assertEquals("Timer 1 hr 30 min", hourAndHalf.label)
        val twoAndHalf = NaturalLanguageIntentParser.parse("timer 2.5 min") as NaturalLanguageIntentParser.ParsedIntent.Timer
        assertEquals(150, twoAndHalf.durationSeconds)
        val comma = NaturalLanguageIntentParser.parse("set a timer for 1,5 h") as NaturalLanguageIntentParser.ParsedIntent.Timer
        assertEquals(5_400, comma.durationSeconds)
    }

    @Test
    fun relativeTimesInDaysAndDecimalsAreThatFarAhead() {
        // Days weren't read at all, so "in 2 days" made a reminder for now.
        val rent = NaturalLanguageIntentParser.parse("remind me to pay rent in 2 days", currentTimeMs = at(12)) as NaturalLanguageIntentParser.ParsedIntent.Reminder
        assertEquals("pay rent", rent.task)
        assertEquals(12, dayOf(rent.triggerMillis))
        assertEquals(12, hourOf(rent.triggerMillis))

        val stretch = NaturalLanguageIntentParser.parse("remind me to stretch in 1.5 hours", currentTimeMs = at(12)) as NaturalLanguageIntentParser.ParsedIntent.Reminder
        assertEquals(13, hourOf(stretch.triggerMillis))
        assertEquals(30, minuteOf(stretch.triggerMillis))

        val hour = NaturalLanguageIntentParser.parse("remind me to call mom in an hour", currentTimeMs = at(12)) as NaturalLanguageIntentParser.ParsedIntent.Reminder
        assertEquals("call mom", hour.task)
        assertEquals(13, hourOf(hour.triggerMillis))

        // "in 5 minutes" matched as "in 5 min" and left "utes" in the task.
        val minutes = NaturalLanguageIntentParser.parse("remind me to call mom in 5 minutes", currentTimeMs = at(12)) as NaturalLanguageIntentParser.ParsedIntent.Reminder
        assertEquals("call mom", minutes.task)
        assertEquals(5, minuteOf(minutes.triggerMillis))
    }

    @Test
    fun aTimeFromNowThatCantBeReadIsNeverNow() {
        assertNull(NaturalLanguageIntentParser.parse("remind me to call mom in two hours", currentTimeMs = at(12)))
        assertNull(NaturalLanguageIntentParser.parse("meeting with Sam in 3 months", currentTimeMs = at(12)))
        // "alarm in 5 seconds" was a 5 o'clock alarm; an alarm days ahead can't be set as an hour and minute.
        assertNull(NaturalLanguageIntentParser.parse("alarm in 5 seconds", currentTimeMs = at(12)))
        assertNull(NaturalLanguageIntentParser.parse("alarm in 2 days", currentTimeMs = at(12)))
        val soon = NaturalLanguageIntentParser.parse("alarm in 1.5 hours", currentTimeMs = at(12)) as NaturalLanguageIntentParser.ParsedIntent.Alarm
        assertEquals(13, soon.hour)
        assertEquals(30, soon.minute)
    }

    @Test
    fun aTimeWordInsideTheTaskIsNotAnUnreadTimeFromNow() {
        // "in hours" sits in the task; it used to read as a time from now that couldn't be read, so no reminder.
        val hours = NaturalLanguageIntentParser.parse("remind me to fill in hours tomorrow at 9", currentTimeMs = at(12))
            as NaturalLanguageIntentParser.ParsedIntent.Reminder
        assertTrue(hours.task, hours.task.contains("fill in hours"))
        assertEquals(9, hourOf(hours.triggerMillis))
        assertNotNull(NaturalLanguageIntentParser.parse("remind me to log in days off on friday at 5pm", currentTimeMs = at(12)))
        // An amount after "in" is still a time from now that can't be read.
        for (query in listOf(
            "remind me to call mom in a couple of hours", "remind me to stretch in thirty seconds",
            "remind me to renew in a year", "remind me to call mom in twenty-five minutes"
        )) {
            assertNull(query, NaturalLanguageIntentParser.parse(query, currentTimeMs = at(12)))
        }
    }

    @Test
    fun aDotSeparatesHoursFromMinutes() {
        val alarm = NaturalLanguageIntentParser.parse("alarm 6.30") as NaturalLanguageIntentParser.ParsedIntent.Alarm
        assertEquals(6, alarm.hour)
        assertEquals(30, alarm.minute)

        val meeting = NaturalLanguageIntentParser.parse("meeting with Sam at 6.30pm", currentTimeMs = at(10)) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(18, hourOf(meeting.startMillis))
        assertEquals(30, minuteOf(meeting.startMillis))
        // The title names the meeting, not just who it's with.
        assertEquals("Meeting with Sam", meeting.title)
    }

    @Test
    fun messagesAcceptColonAndCommaRightAfterTheName() {
        val colon = NaturalLanguageIntentParser.parse("text Sarah: running late") as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        assertEquals("Sarah", colon.recipientName)
        assertEquals("running late", colon.body)
        val comma = NaturalLanguageIntentParser.parse("text mom, on my way") as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        assertEquals("on my way", comma.body)
    }

    private fun alarm(query: String, now: Long = at(12)) =
        NaturalLanguageIntentParser.parse(query, currentTimeMs = now) as NaturalLanguageIntentParser.ParsedIntent.Alarm

    private fun event(query: String, now: Long = at(12)) =
        NaturalLanguageIntentParser.parse(query, currentTimeMs = now) as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent

    private fun reminder(query: String, now: Long = at(12)) =
        NaturalLanguageIntentParser.parse(query, currentTimeMs = now) as NaturalLanguageIntentParser.ParsedIntent.Reminder

    private fun timerSeconds(query: String) =
        (NaturalLanguageIntentParser.parse(query) as NaturalLanguageIntentParser.ParsedIntent.Timer).durationSeconds

    private fun date(millis: Long): Triple<Int, Int, Int> = Calendar.getInstance().apply { timeInMillis = millis }
        .let { Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH)) }

    @Test
    fun anAlarmForADurationIsThatLongFromNowWithoutIn() {
        // "for 10 minutes" was read as 10 o'clock.
        val tenMinutes = alarm("set alarm for 10 minutes", now = at(14, 55))
        assertEquals(15, tenMinutes.hour)
        assertEquals(5, tenMinutes.minute)
        val twenty = alarm("alarm for 20 minutes")
        assertEquals(12, twenty.hour)
        assertEquals(20, twenty.minute)
        val hours = alarm("wake me up 8 hours", now = at(23, 30))
        assertEquals(7, hours.hour)
        assertEquals(30, hours.minute)
        // An alarm can't be set days ahead, and seconds are no hour.
        assertNull(NaturalLanguageIntentParser.parse("alarm 2 days", currentTimeMs = at(12)))
        assertNull(NaturalLanguageIntentParser.parse("alarm 5 seconds", currentTimeMs = at(12)))
        // A bare hour is still a clock time.
        assertEquals(7, alarm("alarm 7").hour)
    }

    @Test
    fun alarmsReadDottedMeridiemsSpacedMinutesNoonAndMidnight() {
        assertEquals(19, alarm("alarm 7 p.m.").hour)
        assertEquals(19, alarm("alarm 7 pm").hour)
        assertEquals(7, alarm("alarm 7 a.m.").hour)
        val spaced = alarm("alarm 6 30")
        assertEquals(6, spaced.hour)
        assertEquals(30, spaced.minute)
        val noon = alarm("alarm at noon")
        assertEquals(12, noon.hour)
        assertEquals(0, noon.minute)
        assertEquals(0, alarm("wake me up at midnight").hour)
        // The same marks work in an event.
        assertEquals(15, hourOf(event("meeting at 3 p.m.").startMillis))
    }

    @Test
    fun timersReadATrailingAmountWordsAndRejectNegatives() {
        assertEquals(9_000, timerSeconds("timer 2 hours 30"))
        assertEquals(90, timerSeconds("timer 1 m 30"))
        assertEquals(5_400, timerSeconds("timer 1h30"))
        assertEquals(1_800, timerSeconds("set a timer for half an hour"))
        assertEquals(1_800, timerSeconds("timer a half hour"))
        assertEquals(3_600, timerSeconds("timer an hour"))
        assertEquals(30, timerSeconds("timer half a minute"))
        // A timer can't run backward, and 90 of the next unit is a typo.
        assertNull(NaturalLanguageIntentParser.parse("timer -5 minutes"))
        assertNull(NaturalLanguageIntentParser.parse("timer 2 hours 90"))
    }

    @Test
    fun anEventOrReminderOnANamedDateLandsOnItWithTheDateOutOfTheTitle() {
        // 2026-03-10, 12:00. These were set for today or tomorrow, with the date left in the title.
        val meeting = event("meeting on dec 5 at 3pm")
        assertEquals(Triple(2026, Calendar.DECEMBER, 5), date(meeting.startMillis))
        assertEquals(15, hourOf(meeting.startMillis))
        assertEquals("Meeting", meeting.title)

        val rent = reminder("remind me to pay rent on november 1 at 9am")
        assertEquals(Triple(2026, Calendar.NOVEMBER, 1), date(rent.triggerMillis))
        assertEquals(9, hourOf(rent.triggerMillis))
        assertEquals("pay rent", rent.task)

        val dayFirst = event("meeting lunch with Sam 5 december at 1pm")
        assertEquals(Triple(2026, Calendar.DECEMBER, 5), date(dayFirst.startMillis))
        assertEquals("lunch with Sam", dayFirst.title)
        assertEquals(Triple(2026, Calendar.DECEMBER, 5), date(event("meeting on the 5th of december at 1pm").startMillis))
        assertEquals(Triple(2026, Calendar.DECEMBER, 5), date(event("meeting December 5th at 1pm").startMillis))

        // A date already past this year is next year's; today's is today.
        assertEquals(Triple(2027, Calendar.MARCH, 5), date(event("meeting on march 5 at 10am").startMillis))
        assertEquals(Triple(2026, Calendar.MARCH, 10), date(event("meeting on march 10 at 3pm").startMillis))
        // A year said is kept.
        assertEquals(Triple(2027, Calendar.DECEMBER, 5), date(event("meeting dec 5, 2027 at 9am").startMillis))
        // A bare hour on a date is read as on a named day.
        assertEquals(15, hourOf(event("meeting dec 5 at 3").startMillis))
        // A date that doesn't exist is no event.
        assertNull(NaturalLanguageIntentParser.parse("meeting on feb 30 at 3pm", currentTimeMs = at(12)))
        // Numbers alone are read the region's way: "12/5" is December 5 in the US and May 12 in the UK. (They were
        // left in the title, with the event set for today.)
        val us = NaturalLanguageIntentParser.parse("meeting 12/5 at 3pm", at(12), locale = java.util.Locale.US)
            as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(Triple(2026, Calendar.DECEMBER, 5), date(us.startMillis))
        assertEquals("Meeting", us.title)
        val uk = NaturalLanguageIntentParser.parse("meeting 12/5 at 3pm", at(12), locale = java.util.Locale.UK)
            as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        assertEquals(Triple(2026, Calendar.MAY, 12), date(uk.startMillis))
    }

    @Test
    fun aDurationWithoutInIsPartOfTheTask() {
        // "cabin 2 hours" read as "in 2 hours", leaving the task "check the cab".
        // Under the whole-phrase rule a number left in the task ("2 hours" with no "in") is a time the reader didn't
        // read, so it is no reminder at all.
        assertNull(NaturalLanguageIntentParser.parse("remind me to check the cabin 2 hours", currentTimeMs = at(12)))
    }

    @Test
    fun dayWordsInsideOtherWordsStay() {
        // "today" was cut out of "todays", leaving "read s report". Under the whole-phrase rule a word of when left in
        // the task makes no reminder, rather than one with a day word it didn't read.
        assertNull(NaturalLanguageIntentParser.parse("remind me to read todays report at 4pm", currentTimeMs = at(12)))
        assertNull(NaturalLanguageIntentParser.parse("remind me to read today's report at 4pm", currentTimeMs = at(12)))
    }

    @Test
    fun hoursFromNowAreRealHoursAcrossAClockChange() {
        val zone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
        try {
            // The clocks go forward overnight on 2026-03-08; "in 24 hours" was read as tomorrow at this time, 23 h on.
            val now = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 7, 22, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val run = reminder("remind me to run in 24 hours", now = now)
            assertEquals(24 * 3_600_000L, run.triggerMillis - now)
            // A day keeps the clock time.
            val day = reminder("remind me to run in 1 day", now = now)
            assertEquals(22, hourOf(day.triggerMillis))
        } finally {
            java.util.TimeZone.setDefault(zone)
        }
    }

    @Test
    fun aClockTimeThatDoesntExistIsNoEventOrReminder() {
        assertNull(NaturalLanguageIntentParser.parse("meeting at 25:00", currentTimeMs = at(12)))
        assertNull(NaturalLanguageIntentParser.parse("remind me to call at 25:00", currentTimeMs = at(12)))
        assertNull(NaturalLanguageIntentParser.parse("meeting at 13pm", currentTimeMs = at(12)))
    }

    @Test
    fun anEventWithOnlyTimeWordsIsCalledWhatItIs() {
        // The leftover time words were the title: "tomorrow at 3pm", "2pm", "at 15:30".
        assertEquals("Meeting", event("meeting tomorrow at 3pm").title)
        assertEquals("Meeting", event("meeting 2pm").title)
        assertEquals("Meeting", event("meeting at 15:30").title)
        assertEquals("Event", event("add event friday at 2").title)
        assertEquals("Reminder", reminder("remind me tomorrow at 9").task)
    }

    private val mom = com.pixel.intelligentsearch.core.data.ContactItem("Mom", "+1 415 555 0100", "")
    private val jean = com.pixel.intelligentsearch.core.data.ContactItem("Jean Valjean", "+33 1 23 45 67 89", "")

    /** The contacts lookup: "mom" and "jean" find their contacts, nothing else does. */
    private val contacts: (String) -> com.pixel.intelligentsearch.core.data.ContactItem? = { name ->
        when (name.lowercase()) {
            "mom" -> mom
            "jean" -> jean
            else -> null
        }
    }

    private fun message(query: String) =
        NaturalLanguageIntentParser.parse(query, contactLookup = contacts) as NaturalLanguageIntentParser.ParsedIntent.DirectMessage

    @Test
    fun aMessageNeedsNoWordBetweenAContactAndTheText() {
        val text = message("text mom hello")
        assertEquals("Mom", text.recipientName)
        assertEquals("hello", text.body)
        assertFalse(text.isWhatsApp)

        val whatsApp = message("whatsapp mom hi")
        assertEquals("Mom", whatsApp.recipientName)
        assertEquals("hi", whatsApp.body)
        assertTrue(whatsApp.isWhatsApp)

        assertEquals("hi there", message("message mom hi there").body)
        // A "to" in the message doesn't end the name early.
        val everyone = message("text mom hello to everyone")
        assertEquals("Mom", everyone.recipientName)
        assertEquals("hello to everyone", everyone.body)

        // Without a contact or a number nothing says where a name would end: searches stay searches.
        assertNull(NaturalLanguageIntentParser.parse("text to speech app", contactLookup = contacts))
        assertNull(NaturalLanguageIntentParser.parse("text mom hello"))
        assertNull(NaturalLanguageIntentParser.parse("whatsapp tips, backup chats", contactLookup = contacts))
    }

    @Test
    fun aMessageShowsTheContactsOwnName() {
        // The row said "Message jean (SMS)", as typed, where "call jean" says "Jean Valjean".
        assertEquals("Jean Valjean", message("text jean: running late").recipientName)
        assertEquals("Jean Valjean", message("text jean running late").recipientName)
    }

    private val tom = com.pixel.intelligentsearch.core.data.ContactItem("Tom Baker", "+1 415 555 0110", "")
    private val arthur = com.pixel.intelligentsearch.core.data.ContactItem("Arthur Dent", "+1 415 555 0111", "")
    private val megan = com.pixel.intelligentsearch.core.data.ContactItem("Megan Lee", "+1 415 555 0112", "")
    private val ingrid = com.pixel.intelligentsearch.core.data.ContactItem("Ingrid Olsen", "+1 415 555 0113", "")
    private val ford = com.pixel.intelligentsearch.core.data.ContactItem("Ford Prefect", "+1 415 555 0114", "")
    private val webster = com.pixel.intelligentsearch.core.data.ContactItem("Webster Hall", "+1 415 555 0115", "")

    /** As the app's contacts search answers: the first contact any of whose names starts with the words. */
    private val prefixContacts: (String) -> com.pixel.intelligentsearch.core.data.ContactItem? = { typed ->
        listOf(tom, arthur, megan, ingrid, ford, webster).firstOrNull { contact ->
            contact.name.lowercase().split(' ').any { it.startsWith(typed.lowercase()) } ||
                contact.name.lowercase().startsWith(typed.lowercase())
        }
    }

    @Test
    fun aSearchStartingWithTextIsNoMessageToAContactWhoseNameItStarts() {
        // Each was "Message Tom Baker", "Message Ingrid Olsen" and so on: the contacts search finds "to" in Tom.
        for (query in listOf(
            "text to speech", "text to speech app", "text to binary", "message to my boyfriend", "message in a bottle",
            "text art generator", "text me when you get home", "message for boss leaving", "whatsapp web login"
        )) {
            assertNull(query, NaturalLanguageIntentParser.parse(query, contactLookup = prefixContacts))
        }
        // A contact named outright is still messaged, by first name or full name.
        val tomMessage = NaturalLanguageIntentParser.parse("text tom running late", contactLookup = prefixContacts)
            as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        assertEquals("Tom Baker", tomMessage.recipientName)
        assertEquals("running late", tomMessage.body)
        assertEquals(
            "Arthur Dent",
            (NaturalLanguageIntentParser.parse("text arthur dent: hi", contactLookup = prefixContacts)
                as NaturalLanguageIntentParser.ParsedIntent.DirectMessage).recipientName
        )
    }

    @Test
    fun aSeparatedNameThatStartsAsASearchIsNoMessage() {
        // These became messages to "saying sorry", "editor" and "board".
        for (query in listOf("message saying sorry to girlfriend", "text editor, free", "message board, reddit")) {
            assertNull(query, NaturalLanguageIntentParser.parse(query))
            assertNull(query, NaturalLanguageIntentParser.parse(query, contactLookup = prefixContacts))
        }
    }

    @Test
    fun aNameIsAContactsOnlyWhenEveryWordIsOneOfTheirNames() {
        assertTrue(NaturalLanguageIntentParser.namesContact("tom", "Tom Baker"))
        assertTrue(NaturalLanguageIntentParser.namesContact("Tom Baker", "Tom Baker"))
        assertTrue(NaturalLanguageIntentParser.namesContact("jose", "José"))
        assertFalse(NaturalLanguageIntentParser.namesContact("to", "Tom Baker"))
        assertFalse(NaturalLanguageIntentParser.namesContact("tom smith", "Tom Baker"))
    }

    @Test
    fun anAlarmOrReminderAddsUpEveryPartOfItsDuration() {
        // These kept only the first part: 11:00, 12:00, 11:00 and 10:30 (only "30m").
        fun alarmAt(query: String) = alarm(query, now = at(10)).let { it.hour to it.minute }
        assertEquals(11 to 30, alarmAt("alarm in 1 hour 30 minutes"))
        assertEquals(12 to 15, alarmAt("alarm in 2 hours 15 minutes"))
        assertEquals(11 to 30, alarmAt("set alarm for 1 hour and 30 minutes"))
        assertEquals(11 to 30, alarmAt("alarm in 1h30m"))
        // The reminder's task kept the rest: "call 30 minutes" at 11:00, "call and 15 minutes" at 12:00.
        val call = reminder("remind me to call in 1 hour 30 minutes", now = at(10))
        assertEquals("call", call.task)
        assertEquals(11 to 30, hourOf(call.triggerMillis) to minuteOf(call.triggerMillis))
        val later = reminder("remind me to call in 2 hours and 15 minutes", now = at(10))
        assertEquals("call", later.task)
        assertEquals(12 to 15, hourOf(later.triggerMillis) to minuteOf(later.triggerMillis))
        // Days and hours together are exact time, not whole days.
        val dayAndHour = reminder("remind me to run in 1 day 2 hours", now = at(10))
        assertEquals(11, dayOf(dayAndHour.triggerMillis))
        assertEquals(12, hourOf(dayAndHour.triggerMillis))
    }

    @Test
    fun commonAlarmPhrasingsSetAnAlarm() {
        // Each gave nothing.
        fun alarmAt(query: String) = alarm(query, now = at(10)).let { it.hour to it.minute }
        assertEquals(7 to 0, alarmAt("set an alarm for 7am"))
        assertEquals(18 to 30, alarmAt("set an alarm for 6:30 pm"))
        assertEquals(10 to 10, alarmAt("set an alarm in 10 minutes"))
        assertEquals(7 to 0, alarmAt("wake me at 7"))
        assertEquals(10 to 30, alarmAt("alarm in half an hour"))
        assertEquals(10 to 30, alarmAt("wake me in half an hour"))
        assertEquals(11 to 30, alarmAt("alarm in an hour and a half"))
        // A time said as "10 to 7" is no 10 o'clock alarm.
        assertNull(NaturalLanguageIntentParser.parse("alarm 10 to 7", currentTimeMs = at(10)))
        assertNull(NaturalLanguageIntentParser.parse("set alarm quarter past 6", currentTimeMs = at(10)))
    }

    @Test
    fun titlesLoseTheirLeadingToAndStrayCommas() {
        // These kept "to call mom", "to buy milk" and a title of ",".
        assertEquals("call mom", reminder("remind me on friday to call mom at 3").task)
        assertEquals("call mom", reminder("remind me in 10 minutes to call mom").task)
        assertEquals("buy milk", reminder("remind me tomorrow to buy milk").task)
        assertEquals("Meeting", event("meeting on friday, dec 4 at 3pm").title)
        assertEquals("Meeting with john", event("meeting with john tomorrow at 3pm").title)
        // "schedule" names no meeting, so its title is as typed.
        assertEquals("lunch with Sam", event("schedule lunch with Sam tomorrow at 1pm").title)
    }

    @Test
    fun aTimerWithWordsBeforeItsLastNumberIsNoTimer() {
        // "45 minutes then 10" was 45 min 10 s.
        assertNull(NaturalLanguageIntentParser.parse("timer 45 minutes then 10"))
        assertEquals(9_000, timerSeconds("timer 2 hours 30"))
        assertEquals(5_400, timerSeconds("timer 1h30"))
    }

    @Test
    fun numbersAndNamesWithMarksCanBeMessaged() {
        val plus = message("text +14155550100 hello")
        assertEquals("+14155550100", plus.recipientName)
        assertEquals("hello", plus.body)
        val dashed = message("text 555-1234 running late")
        assertEquals("555-1234", dashed.recipientName)
        assertEquals("running late", dashed.body)
        assertEquals("+1 (415) 555-0100", message("text +1 (415) 555-0100: hi").recipientName)
        assertEquals("O'Brien", message("text O'Brien saying hi").recipientName)
        assertEquals("Jean-Luc", message("message Jean-Luc: on my way").recipientName)
    }

    /** Thursday 29 October 2026 at [hour]:[minute]. */
    private fun thursday(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 29, hour, minute, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** When [millis] is, as "2026-11-01 09:00". */
    private fun shown(millis: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(millis)

    private fun usReminder(query: String) = NaturalLanguageIntentParser.parse(query, thursday(10), locale = java.util.Locale.US)
        as NaturalLanguageIntentParser.ParsedIntent.Reminder

    @Test
    fun aReminderReadsTheDateAndTimeItIsGiven() {
        // Each was set for now, or the next 9 AM, with the date words left in the task.
        val cases = mapOf(
            "remind me to pay rent on the 1st at 9am" to ("pay rent" to "2026-11-01 09:00"),
            "remind me to call mom on the 5th" to ("call mom" to "2026-11-05 10:00"),
            "remind me to call mom on 11/5 at 3pm" to ("call mom" to "2026-11-05 15:00"),
            "remind me to call mom at noon" to ("call mom" to "2026-10-29 12:00"),
            "remind me to call mom at 12 noon" to ("call mom" to "2026-10-29 12:00"),
            "remind me to call mom at midnight" to ("call mom" to "2026-10-30 00:00"),
            "remind me to call mom this evening" to ("call mom" to "2026-10-29 18:00"),
            "remind me to call mom tomorrow morning" to ("call mom" to "2026-10-30 09:00"),
            "remind me to call mom tomorrow evening at 7" to ("call mom" to "2026-10-30 19:00"),
            "remind me to call mom in the morning" to ("call mom" to "2026-10-30 09:00"),
            "remind me to call mom next week" to ("call mom" to "2026-11-05 10:00"),
            "remind me to renew passport in may" to ("renew passport" to "2027-05-01 10:00"),
            "remind me to buy flowers on valentine's day" to ("buy flowers" to "2027-02-14 10:00"),
            "remind me to call grandma on thanksgiving" to ("call grandma" to "2026-11-26 10:00"),
            "remind me to call mom the day after tomorrow at 5" to ("call mom" to "2026-10-31 17:00"),
            "remind me to call mom at 5 o'clock" to ("call mom" to "2026-10-29 17:00"),
            "remind me to submit the report by friday" to ("submit the report" to "2026-10-30 10:00")
        )
        for ((query, expected) in cases) {
            val reminder = usReminder(query)
            assertEquals(query, expected.first, reminder.task)
            assertEquals(query, expected.second, shown(reminder.triggerMillis))
        }
        // In the UK the day comes first: 11/5 is the 11th of May.
        val uk = NaturalLanguageIntentParser.parse("remind me to call mom on 11/5 at 3pm", thursday(10), locale = java.util.Locale.UK)
            as NaturalLanguageIntentParser.ParsedIntent.Reminder
        assertEquals("2027-05-11 15:00", shown(uk.triggerMillis))
        // An amount written as a fraction is no date; under the whole-phrase rule it is no reminder either.
        assertNull(NaturalLanguageIntentParser.parse("remind me to buy 1/2 gallon milk at 5pm", thursday(10), locale = java.util.Locale.US))
    }

    @Test
    fun aReminderWithWhenWordsItCantReadIsNoReminder() {
        // Each was a reminder for now with the words in its task; a search is better than a wrong time.
        for (query in listOf(
            "remind me to clean the garage this weekend", "remind me to call mom next month", "remind me to pay taxes by june",
            "remind me to call mom at half past 5", "remind me to file the report at the end of the month",
            "remind me to call mom this morning", "remind me to call mom on the 31st of november"
        )) {
            assertNull(query, NaturalLanguageIntentParser.parse(query, thursday(10), locale = java.util.Locale.US))
        }
        // Words that only sound like a time stay in the task.
        assertEquals("plan next year's budget", usReminder("remind me to plan next year's budget tomorrow at 9").task)
    }

    @Test
    fun anEventReadsNextWeekdaysAndLosesTheWordsAroundItsDate() {
        fun usEvent(query: String) = NaturalLanguageIntentParser.parse(query, thursday(10), locale = java.util.Locale.US)
            as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        // "next tuesday" was titled "next"; "next friday" on a Thursday was tomorrow, not the Friday of next week.
        val tuesday = usEvent("meeting next tuesday at 2pm")
        assertEquals("Meeting", tuesday.title)
        assertEquals("2026-11-03 14:00", shown(tuesday.startMillis))
        assertEquals("2026-11-06 15:00", shown(usEvent("meeting next friday at 3pm").startMillis))
        assertEquals("2026-10-30 15:00", shown(usEvent("meeting this friday at 3pm").startMillis))
        // The title was "dentist appointment for".
        val dentist = usEvent("schedule dentist appointment for nov 12 at 10:30am")
        assertEquals("dentist appointment", dentist.title)
        assertEquals("2026-11-12 10:30", shown(dentist.startMillis))
    }

    @Test
    fun aDurationKeepsItsHalfAndItsBareMinutes() {
        // These lost their second part: 3,600 s, 11:00, 11:00, 12:00, and "call and a half" and "call 30" at 11:00.
        assertEquals(5_400, timerSeconds("timer 1 hour and a half"))
        assertEquals(90, timerSeconds("timer 1 minute and a half"))
        fun alarmAt(query: String) = alarm(query, now = thursday(10)).let { it.hour to it.minute }
        assertEquals(11 to 30, alarmAt("alarm in 1 hour and a half"))
        assertEquals(11 to 30, alarmAt("alarm in 1 hour 30"))
        assertEquals(12 to 15, alarmAt("alarm in 2 hours 15"))
        val half = usReminder("remind me to call in 1 hour and a half")
        assertEquals("call" to "2026-10-29 11:30", half.task to shown(half.triggerMillis))
        val thirty = usReminder("remind me to call in 1 hour 30")
        assertEquals("call" to "2026-10-29 11:30", thirty.task to shown(thirty.triggerMillis))
        val before = usReminder("remind me in 1 hour 30 to call")
        assertEquals("call" to "2026-10-29 11:30", before.task to shown(before.triggerMillis))
        // 90 of the smaller unit is a typo, and days have none left unsaid.
        assertNull(NaturalLanguageIntentParser.parse("alarm in 2 hours 90", thursday(10)))
        assertNull(NaturalLanguageIntentParser.parse("remind me to call in 2 days 3", thursday(10)))
    }

    private val will = com.pixel.intelligentsearch.core.data.ContactItem("Will Smith", "+1 415 555 0201", "")
    private val bjorn = com.pixel.intelligentsearch.core.data.ContactItem("Bjørn Larsen", "+47 1234 5678", "")

    /** As the app's contacts search answers: Will Smith for "will", Bjørn Larsen for "bjorn" and "bjørn". */
    private val troubleContacts: (String) -> com.pixel.intelligentsearch.core.data.ContactItem? = { typed ->
        when (typed.lowercase()) {
            "will", "will smith" -> will
            "bjorn", "bjørn" -> bjorn
            else -> null
        }
    }

    @Test
    fun aSearchAboutMessagesNotSendingIsNoMessageToAContact() {
        // With a contact called Will these were messages to Will Smith saying "not send".
        for (query in listOf("text will not send", "message will not send", "text will won't go through", "text will isn't working")) {
            assertNull(query, NaturalLanguageIntentParser.parse(query, contactLookup = troubleContacts))
        }
        // A message to Will still goes, and a colon still marks one that starts that way.
        assertEquals("hi", (NaturalLanguageIntentParser.parse("text will hi", contactLookup = troubleContacts)
            as NaturalLanguageIntentParser.ParsedIntent.DirectMessage).body)
        assertEquals("not coming", (NaturalLanguageIntentParser.parse("text will: not coming", contactLookup = troubleContacts)
            as NaturalLanguageIntentParser.ParsedIntent.DirectMessage).body)
    }

    @Test
    fun aNameTypedWithoutItsLettersKeyStillNamesTheContact() {
        // "bjorn" didn't name Bjørn, nor "strasse" Straße: NFD leaves ø and ß whole.
        assertTrue(NaturalLanguageIntentParser.namesContact("bjorn", "Bjørn Larsen"))
        assertTrue(NaturalLanguageIntentParser.namesContact("strasse", "Straße"))
        val text = NaturalLanguageIntentParser.parse("text bjorn hi", contactLookup = troubleContacts)
            as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        assertEquals("Bjørn Larsen", text.recipientName)
        assertEquals("hi", text.body)
    }

    /** What [query] said at [now] reads as, "task @ 2026-10-30 10:00" (event or reminder), or null when neither. */
    private fun whenOf(query: String, now: Long = thursday(10)): String? =
        when (val parsed = NaturalLanguageIntentParser.parse(query, now, locale = java.util.Locale.US)) {
            is NaturalLanguageIntentParser.ParsedIntent.Reminder -> "${parsed.task} @ ${shown(parsed.triggerMillis)}"
            is NaturalLanguageIntentParser.ParsedIntent.CalendarEvent -> "${parsed.title} @ ${shown(parsed.startMillis)}"
            else -> null
        }

    @Test
    fun aDateThatDescribesTheTaskIsNotItsDay() {
        // Each was set on the holiday, ordinal or month inside the task, with the day named left in the title. Under the
        // whole-phrase rule a holiday, a number or a word of when left in the task is no action at all.
        for (query in listOf(
            "remind me to shop for christmas presents tomorrow", "remind me to buy candy for halloween tomorrow",
            "remind me to order the turkey for thanksgiving on monday", "remind me on friday to buy candy for halloween",
            "schedule planning for new year tomorrow at 3pm", "meeting on the 3rd floor at 2pm",
            "remind me to read the 3rd chapter tomorrow", "remind me to submit the 1st draft by friday",
            "meeting about the 2nd quarter results tomorrow at 3pm", "remind me to watch the 2nd half at 3pm",
            "remind me to buy a midnight snack at 9pm"
        )) {
            assertNull(query, whenOf(query))
        }
        // "May" is as often a word: beside no day it stays in the task.
        assertEquals(
            "book tickets for the concert in may @ 2026-10-30 10:00",
            whenOf("remind me to book tickets for the concert in may tomorrow")
        )
        // Said alone they are still the day.
        assertEquals("call grandma @ 2026-12-25 10:00", whenOf("remind me to call grandma for christmas"))
        assertEquals("pay rent @ 2026-11-03 10:00", whenOf("remind me to pay rent on the 3rd"))
        // Two days that disagree, or "for christmas" with a clock time (today at 5, or Christmas at 5?): no reminder.
        for (query in listOf(
            "remind me to call mom on the 3rd tomorrow", "remind me to call mom on friday on dec 9",
            "remind me to book flights for christmas at 5pm", "meeting on the 3rd floor"
        )) {
            assertNull(query, whenOf(query))
        }
    }

    @Test
    fun aDayOrTimeTheReminderCantReadIsNoReminderForNow() {
        // Each was a reminder for now with the words left in its task, or ("black friday") on the coming Friday.
        for (query in listOf(
            "remind me to call mom on mother's day", "remind me to call dad on father's day", "remind me to call mom on my birthday",
            "remind me to take my pills before bed", "remind me to call mom at lunch", "remind me to call the bank after work",
            "remind me to send the report by eod", "remind me to send the report by end of day", "remind me to call mom later today"
        )) {
            assertNull(query, whenOf(query))
        }
        assertEquals("shop @ 2026-11-27 10:00", whenOf("remind me to shop on black friday"))
        assertEquals("call mom @ 2026-12-26 10:00", whenOf("remind me to call mom on boxing day"))
        assertEquals("pay rent @ 2026-11-01 10:00", whenOf("remind me to pay rent on the first"))
        assertEquals("pay rent @ 2026-11-15 10:00", whenOf("remind me to pay rent on the fifteenth"))
        // With a time, the hour is clear.
        assertEquals("take my pills before bed @ 2026-10-29 22:00", whenOf("remind me to take my pills before bed at 10pm"))
    }

    @Test
    fun tonightRunsPastMidnightAndATimeAlreadyGoneIsNoReminder() {
        // "Tonight at 12" was noon and "tonight at 1" 1 PM; "tonight" said at 20:30 was 20:00, already past.
        assertEquals("call mom @ 2026-10-30 00:00", whenOf("remind me to call mom tonight at 12"))
        assertEquals("call mom @ 2026-10-30 00:30", whenOf("remind me to call mom tonight at 12:30"))
        assertEquals("call mom @ 2026-10-30 01:00", whenOf("remind me to call mom tonight at 1"))
        assertEquals("call mom @ 2026-10-29 23:00", whenOf("remind me to call mom tonight at 11"))
        assertEquals("call mom @ 2026-10-31 00:00", whenOf("remind me to call mom tomorrow night at 12"))
        assertEquals("call mom @ 2026-10-31 00:00", whenOf("remind me to call mom friday night at 12"))
        assertNull(whenOf("remind me to take my pills tonight", thursday(20, 30)))
        assertNull(whenOf("remind me to call mom tonight at 9", thursday(22)))
        assertNull(whenOf("remind me to pay rent on the 29th at 9am"))
        assertNull(whenOf("remind me on 1/2/25"))
        // "This friday" said on a Friday is today, and once its time has gone, none.
        val friday = thursday(10) + 24 * 3_600_000L
        assertEquals("submit the report @ 2026-10-30 17:00", whenOf("remind me to submit the report this friday at 5pm", friday))
        assertNull(whenOf("remind me to submit the report this friday at 5pm", friday + 8 * 3_600_000L))
    }

    @Test
    fun aFractionInTheTaskIsNoDateAndAMealSaysWhichHalfOfTheDay() {
        // These were January 2 and April 5.
        // Under the whole-phrase rule a number left in the task is no reminder at all.
        assertNull(whenOf("remind me to take 1/2 at 9pm"))
        assertNull(whenOf("remind me about episode 4/5 at 9pm"))
        // "Dinner at 7" said at 5 AM was 7 AM.
        assertEquals("make dinner @ 2026-10-29 19:00", whenOf("remind me to make dinner at 7", thursday(5)))
        assertEquals("eat breakfast @ 2026-10-30 07:00", whenOf("remind me to eat breakfast at 7", thursday(10)))
    }

    @Test
    fun nextSundaySaidOnSaturdayIsNotTomorrow() {
        val saturday = thursday(10) + 2 * 24 * 3_600_000L
        // It was tomorrow, the same day as "this sunday".
        assertEquals("call mom @ 2026-11-08 10:00", whenOf("remind me to call mom next sunday", saturday))
        assertEquals("call mom @ 2026-11-01 10:00", whenOf("remind me to call mom this sunday", saturday))
        assertEquals("call mom @ 2026-11-06 10:00", whenOf("remind me to call mom next friday"))
    }

    @Test
    fun aTimerUnitMustEndThere() {
        // "timer 10ms" was a 10 minute timer.
        assertNull(NaturalLanguageIntentParser.parse("timer 10ms"))
        assertEquals(7_200, timerSeconds("timer 2hrs"))
        assertEquals(90, timerSeconds("timer 1m30s"))
    }

    @Test
    fun aMessageThatStartsWithANotWordStillGoes() {
        val family = listOf(
            com.pixel.intelligentsearch.core.data.ContactItem("Mom", "+14155550213", ""),
            com.pixel.intelligentsearch.core.data.ContactItem("Dad", "+14155550214", ""),
            com.pixel.intelligentsearch.core.data.ContactItem("Sarah Connor", "+14155550230", ""),
            com.pixel.intelligentsearch.core.data.ContactItem("Boss", "+14155550233", "")
        )
        val lookup: (String) -> com.pixel.intelligentsearch.core.data.ContactItem? = { typed ->
            family.firstOrNull { NaturalLanguageIntentParser.namesContact(typed, it.name) }
        }
        fun body(query: String) = (NaturalLanguageIntentParser.parse(query, contactLookup = lookup)
            as? NaturalLanguageIntentParser.ParsedIntent.DirectMessage)?.body
        // Each gave no action: the rule for "text will not send" took any not-word for a search.
        assertEquals("can't make it", body("text mom can't make it"))
        assertEquals("stuck in traffic", body("text sarah stuck in traffic"))
        assertEquals("won't be home for dinner", body("text dad won't be home for dinner"))
        assertEquals("working late", body("text boss working late"))
        assertEquals("not feeling well", body("message sarah not feeling well"))
        assertEquals("not working today", body("text boss not working today"))
        // Help with messages that won't send is still a search.
        for (query in listOf("text mom not sending", "text dad won't deliver", "message sarah keeps failing", "text boss not delivered")) {
            assertNull(query, body(query))
        }
    }

    @Test
    fun whenWordsOutsideTheReadersGrammarAreNoActionNeverNow() {
        // Each was a reminder or event for now, for the day named at the clock time now, or ("at 07:00") another hour.
        for (query in listOf(
            "remind me to call mom at 7ish", "remind me to call mom at noonish", "remind me to call mom around 7",
            "remind me to call mom around 5pm", "remind me to call mom at about 7", "remind me to submit the report by 5",
            "remind me to submit the report by 5pm", "remind me to call mom before 9am", "remind me to call mom at seven",
            "remind me to call mom tomorrow at seven pm", "remind me to call mom between 2 and 3 tomorrow",
            "remind me to call mom at 1930", "remind me to call mom at 7 30", "remind me to call mom a week from today",
            "remind me to call mom a week from tomorrow", "remind me to call mom 2 days from now",
            "remind me to call mom the week after next", "remind me to call mom early next week",
            "remind me to finish the report by the end of next week", "remind me to call mom at dawn",
            "remind me to leave at sunset", "remind me to lock the door at night", "remind me to call mom in the early afternoon",
            "remind me to pay rent on the 1st of every month at 9am", "remind me to water the plants every monday at 9am",
            "remind me to stretch each morning", "remind me to take vitamins daily at 8am", "remind me to call mom on wensday at 5pm",
            "remind me to call mom tomorow at 5pm", "remind me to call mom yesterday", "remind me to pay rent the 12th",
            "meeting tomorrow 9-10", "remind me to call mom before friday", "remind me to stretch after 30 minutes",
            "remind me at 10 to 7", "remind me to call mom at 20 past 6"
        )) {
            assertNull(query, whenOf(query))
        }
        // Words that only describe the task stay in it, and "730pm" is 7:30 PM.
        assertEquals("send the weekly report @ 2026-10-30 09:00", whenOf("remind me to send the weekly report tomorrow at 9am"))
        // Under the whole-phrase rule a number or a word of when left in the task is no reminder at all.
        assertNull(whenOf("remind me to read about 3 chapters at 5pm"))
        assertNull(whenOf("remind me to buy a late night snack at 9pm"))
        assertEquals("call mom @ 2026-10-29 19:30", whenOf("remind me to call mom at 730pm"))
        assertEquals("call mom @ 2026-10-31 17:00", whenOf("remind me to call mom the day after tomorrow at 5"))
        assertEquals("call mom @ 2026-10-30 00:00", whenOf("remind me to call mom at midnight"))
    }

    @Test
    fun aWeekdayWithItsDateIsSetOnThatDate() {
        // Each was set on the weekday's coming date, the date left unread.
        val cases = mapOf(
            "remind me to call mom on friday the 13th" to "call mom @ 2026-11-13 10:00",
            "remind me friday the 13th to buy candles" to "buy candles @ 2026-11-13 10:00",
            "schedule dentist on tuesday the 10th at 2pm" to "dentist @ 2026-11-10 14:00",
            "meeting friday the 6th at 2pm" to "Meeting @ 2026-11-06 14:00",
            "remind me next tuesday the 10th to pay rent" to "pay rent @ 2026-11-10 10:00",
            "remind me on tuesday 11/10 to pay rent" to "pay rent @ 2026-11-10 10:00",
            "remind me on tuesday, 11/10 to pay rent" to "pay rent @ 2026-11-10 10:00",
            "remind me on sat 10/31 to buy candy" to "buy candy @ 2026-10-31 10:00"
        )
        for ((query, expected) in cases) assertEquals(query, expected, whenOf(query))
        // A weekday the date doesn't fall on is no reminder.
        assertNull(whenOf("remind me to call mom on friday the 14th"))
        assertNull(whenOf("remind me on monday 11/10 to pay rent"))
        val uk = NaturalLanguageIntentParser.parse("remind me on tuesday 10/11 to pay rent", thursday(10), locale = java.util.Locale.UK)
            as NaturalLanguageIntentParser.ParsedIntent.Reminder
        assertEquals("2026-11-10 10:00", shown(uk.triggerMillis))
    }

    @Test
    fun shortWeekdaysAreDaysOnlyWhereADayGoes() {
        // Each was set for today, or now.
        val cases = mapOf(
            "remind me to call mom on fri" to "call mom @ 2026-10-30 10:00",
            "remind me to call mom fri at 5pm" to "call mom @ 2026-10-30 17:00",
            "meeting tue at 2pm" to "Meeting @ 2026-11-03 14:00",
            "remind me on sat to buy candy" to "buy candy @ 2026-10-31 10:00",
            "remind me to call mom next thurs at 3pm" to "call mom @ 2026-11-05 15:00",
            "remind me to book the dentist the 12th at 10am" to "book the dentist @ 2026-11-12 10:00"
        )
        for ((query, expected) in cases) assertEquals(query, expected, whenOf(query))
        // "Sun" and "sat" are things too.
        assertEquals("put on sun cream @ 2026-10-29 14:00", whenOf("remind me to put on sun cream at 2pm"))
        assertEquals("lie in the sun @ 2026-10-29 14:00", whenOf("remind me to lie in the sun at 2pm"))
        assertEquals("check my sat score @ 2026-10-29 14:00", whenOf("remind me to check my sat score at 2pm"))
    }

    @Test
    fun aWeekdayThatDescribesTheTaskIsNotItsDay() {
        // This was Sunday at 5 PM.
        assertNull(whenOf("remind me to call the sunday school teacher at 5"))
        // Under the whole-phrase rule a weekday left in the task is no reminder, another day named or not.
        assertNull(whenOf("remind me to call the sunday school teacher tomorrow at 5"))
        assertEquals("watch football @ 2026-11-02 17:00", whenOf("remind me to watch monday night football at 5pm"))
    }

    @Test
    fun aTimeRangeStartsAtItsFirstTime() {
        fun usEvent(query: String) = NaturalLanguageIntentParser.parse(query, thursday(10), locale = java.util.Locale.US)
            as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        // Each started at the range's end, or at the clock time now, titled "2" or "from 2 to".
        val cases = mapOf(
            "meeting friday 2-3pm" to Triple("Meeting", "2026-10-30 14:00", "2026-10-30 15:00"),
            "meeting tomorrow from 2 to 3pm" to Triple("Meeting", "2026-10-30 14:00", "2026-10-30 15:00"),
            "schedule dentist 10-11am tomorrow" to Triple("dentist", "2026-10-30 10:00", "2026-10-30 11:00"),
            "schedule lunch with sarah friday 11-1pm" to Triple("lunch with sarah", "2026-10-30 11:00", "2026-10-30 13:00"),
            "meeting tomorrow from 2pm to 3:30pm" to Triple("Meeting", "2026-10-30 14:00", "2026-10-30 15:30"),
            "meeting tomorrow 10:30-11" to Triple("Meeting", "2026-10-30 10:30", "2026-10-30 11:00")
        )
        for ((query, expected) in cases) {
            val event = usEvent(query)
            assertEquals(query, expected, Triple(event.title, shown(event.startMillis), shown(event.endMillis)))
        }
        // A reminder starts at the start.
        assertEquals("call mom @ 2026-10-30 14:00", whenOf("remind me to call mom tomorrow 2-3pm"))
        // Two numbers in the task are no range; under the whole-phrase rule they are no reminder either.
        assertNull(whenOf("remind me to read chapters 2-3 at 5pm"))
    }

    @Test
    fun wordsThatSayWhichHalfOfTheDayGoFirst() {
        // "Dinner tonight at 5:30" was 5:30 the next morning, and the rest the morning hour.
        assertEquals("eat dinner @ 2026-10-29 17:30", whenOf("remind me to eat dinner tonight at 5:30"))
        assertEquals("eat dinner @ 2026-10-30 17:30", whenOf("remind me to eat dinner friday night at 5:30"))
        assertEquals("take my pills before bed @ 2026-10-29 22:00", whenOf("remind me to take my pills before bed at 10", thursday(9)))
        assertEquals("take my pills before bed @ 2026-10-29 22:00", whenOf("remind me to take my pills before bed at 10", thursday(10)))
        assertEquals("call mom at bedtime @ 2026-10-29 21:00", whenOf("remind me to call mom at bedtime at 9", thursday(8)))
        assertEquals("call mom after work @ 2026-10-29 18:00", whenOf("remind me to call mom after work at 6", thursday(5)))
        // "Tonight at 5" may be the small hours or the evening.
        assertNull(whenOf("remind me to pick up the kids tonight at 5"))
    }

    @Test
    fun aLeadingZeroIsTheHourAsWrittenWithNoDayNamed() {
        // These were 7 PM and 9 PM today.
        assertEquals("call mom @ 2026-10-30 07:00", whenOf("remind me to call mom at 07:00"))
        assertEquals("call mom @ 2026-10-30 09:00", whenOf("remind me to call mom at 09:00"))
        assertEquals("call mom @ 2026-10-29 13:30", whenOf("remind me to call mom at 13:30"))
    }

    /** When [query] said at [now] rings, as "2026-10-30 07:00", or null when it is no alarm. */
    private fun alarmRings(query: String, now: Long): String? =
        (NaturalLanguageIntentParser.parse(query, now, locale = java.util.Locale.US) as? NaturalLanguageIntentParser.ParsedIntent.Alarm)
            ?.let { shown(it.ringsAtMillis) }

    @Test
    fun anAlarmIsOnlyOfferedWhenItRingsOnTheDayNamed() {
        val fridayEarly = thursday(6, 40) + 24 * 3_600_000L
        // At 6:40 AM each would have rung within the hour, or on Saturday.
        for (query in listOf(
            "wake me up at 7 tomorrow", "alarm 7am tomorrow", "set alarm for 7:30 tomorrow", "wake me up monday at 6",
            "alarm saturday at 8", "alarm 6am today", "alarm 7am every day", "wake me up around 7"
        )) {
            assertNull(query, alarmRings(query, fridayEarly))
        }
        assertEquals("2026-10-30 07:00", alarmRings("alarm 7am", fridayEarly))
        assertEquals("2026-10-30 07:00", alarmRings("alarm 7am today", fridayEarly))
        assertEquals("2026-10-30 06:50", alarmRings("alarm in 10 minutes", fridayEarly))
        // Said the night before, tomorrow at 7 is the next 7 AM.
        assertEquals("2026-10-30 07:00", alarmRings("wake me up at 7 tomorrow", thursday(21)))
        assertEquals("2026-11-02 06:00", alarmRings("wake me up monday at 6", thursday(10) + 3 * 24 * 3_600_000L))
        assertEquals("2026-10-29 19:00", alarmRings("alarm at 7 in the evening", thursday(10)))
        assertEquals("2026-10-30 02:00", alarmRings("wake me up tonight at 2", thursday(22)))
    }

    @Test
    fun aMessageThatOnlyMentionsSendingStillGoes() {
        val mom = com.pixel.intelligentsearch.core.data.ContactItem("Mom", "+14155550213", "")
        val lookup: (String) -> com.pixel.intelligentsearch.core.data.ContactItem? = { if (it.equals("mom", ignoreCase = true)) mom else null }
        fun body(query: String) = (NaturalLanguageIntentParser.parse(query, contactLookup = lookup)
            as? NaturalLanguageIntentParser.ParsedIntent.DirectMessage)?.body
        // Each gave no action: any not-word and sending word read as a search for help.
        assertEquals("didn't receive the package", body("text mom didn't receive the package"))
        assertEquals("can't send the file until tomorrow", body("text mom can't send the file until tomorrow"))
        assertEquals("not sent yet", body("text mom not sent yet"))
        // Help with what a phone sends is still a search.
        for (query in listOf("text mom not sending pictures", "text mom won't receive texts from iphone", "text mom didn't receive")) {
            assertNull(query, body(query))
        }
    }

    @Test
    fun aTimerOfANumberAndAHalf() {
        // This was no timer.
        assertEquals(150, timerSeconds("set a timer for 2 and a half minutes"))
        assertEquals(5_400, timerSeconds("timer 1 and a half hours"))
    }

    @Test
    fun aDateOrDayLeftInTheTaskIsNoAction() {
        // Each was set for today at the time given, or for now, the date or day words left in its title.
        for (query in listOf(
            "schedule dentist 11/15 at 3pm", "add event dinner 11/14 at 7pm", "meeting with bob 11/12 at 2pm",
            "remind me to pay rent 12/1 at 9am", "remind me to pay rent 12/1", "remind me 11/15 to pay rent",
            "schedule haircut 11/20", "schedule vacation 12/20-12/27", "remind me to pay rent 12-1 at 9am",
            "meeting on the 15 at 3pm", "remind me on the 15 to pay rent", "remind me to pay rent on the 1",
            "remind me on the fifth of december at 3pm", "remind me on december first at 9am",
            "remind me on november fifteenth", "remind me on the last day of november at 5pm",
            "remind me to vote on election day at 7am", "remind me to call grandpa on veterans day at 11am",
            "remind me on presidents day at 9am", "remind me to watch the game on super bowl sunday at 3pm",
            "remind me to make tamales on cinco de mayo", "remind me on juneteenth at noon", "remind me on payday to pay rent",
            "remind me to light candles on hanukkah at 5pm",
            "remind me 2 days before christmas to buy gifts", "remind me the day before thanksgiving to buy a turkey",
            "remind me a week before thanksgiving", "remind me to mail the cards before christmas",
            "remind me to finish shopping by christmas", "remind me to take down the lights after christmas",
            "remind me 3 days before my flight",
            "remind me to call mom in 5", "remind me to check the oven in 45", "remind me to call mom in a bit",
            "remind me to catch the 7.30 train", "remind me to call mom tues", "remind me to call mom fri",
            "remind me to buy tickets for saturday's game at 5pm", "remind me about friday's meeting at 9am"
        )) {
            assertNull(query, whenOf(query))
        }
        // In the UK the day comes first, and "a week on friday" is a week after the coming Friday.
        for (query in listOf("schedule dentist 15/11 at 3pm", "remind me a week on friday")) {
            assertNull(query, NaturalLanguageIntentParser.parse(query, thursday(10), locale = java.util.Locale.UK))
        }
        // Under the whole-phrase rule numbers and holidays left in the task are no action, even where they only describe
        // it; words that are as often things ("the super bowl", "june") stay in it.
        for (query in listOf(
            "remind me to read chapters 2-3 at 5pm", "remind me to stretch for 5-10 minutes at 5pm",
            "remind me to buy candy for halloween tomorrow", "remind me to buy christmas presents tomorrow at 5pm",
            "remind me to watch the 9/11 documentary at 8pm"
        )) {
            assertNull(query, whenOf(query))
        }
        assertEquals("watch the super bowl @ 2026-10-29 18:00", whenOf("remind me to watch the super bowl at 6pm"))
        assertEquals("call mom @ 2026-11-06 10:00", whenOf("remind me to call mom next week on friday"))
        assertEquals("call june @ 2026-10-29 17:00", whenOf("remind me to call june 5pm"))
    }

    @Test
    fun tmrwIsTomorrowInTheFullPhrasing() {
        // Each was today, or now, with "tmrw" left in the title.
        assertEquals("call mom @ 2026-10-30 17:00", whenOf("remind me to call mom tmrw at 5"))
        assertEquals("call mom @ 2026-10-30 17:00", whenOf("remind me to call mom tmr at 5pm"))
        assertEquals("call mom @ 2026-10-30 17:00", whenOf("remind me to call mom tmw at 5pm"))
        assertEquals("Meeting @ 2026-10-30 15:00", whenOf("meeting tmrw at 3pm"))
        assertEquals("call mom @ 2026-10-30 09:00", whenOf("remind me to call mom tmrw morning"))
        assertNull(whenOf("remind me to call mom tomoz at 5pm"))
        // Said at 6:30, the clock's 7:00 alarm rings in half an hour, not tomorrow.
        assertNull(alarmRings("alarm at 7 tmrw", thursday(6, 30)))
        assertNull(alarmRings("wake me up tmrw at 7", thursday(6, 30)))
        assertEquals("2026-10-30 07:00", alarmRings("wake me up tmrw at 7", thursday(21)))
    }

    @Test
    fun theNthWeekdayOfAMonthIsNoDayOfTheMonth() {
        // These were Mon Nov 2 and Sun Nov 1, read from "on the second" and "on the first".
        assertNull(whenOf("remind me on the second friday of november"))
        assertNull(whenOf("remind me on the first sunday of december"))
        assertNull(whenOf("schedule book club on the third thursday in november at 7pm"))
    }

    @Test
    fun aMealOnlySaysTheHourWhenItIsThePlanItself() {
        // Each said in the morning was that evening: the meal was what the task is for, or a word it describes.
        assertEquals("book dinner reservations @ 2026-10-29 10:00", whenOf("remind me to book dinner reservations at 10", thursday(8)))
        assertEquals("take the chicken out for dinner @ 2026-10-29 08:00", whenOf("remind me to take the chicken out for dinner at 8", thursday(7)))
        assertEquals("defrost the turkey for dinner @ 2026-10-29 09:00", whenOf("remind me to defrost the turkey for dinner at 9", thursday(7)))
        // The meal itself still says it.
        assertEquals("start dinner @ 2026-10-29 17:00", whenOf("remind me to start dinner at 5", thursday(8)))
        assertEquals("eat dinner with sarah @ 2026-10-29 19:00", whenOf("remind me to eat dinner with sarah at 7", thursday(5)))
        assertEquals("make dinner @ 2026-10-29 19:00", whenOf("remind me at 7 to make dinner", thursday(5)))
        assertEquals("make breakfast @ 2026-10-30 07:00", whenOf("remind me to make breakfast at 7", thursday(20)))
    }

    @Test
    fun someSunIsSunNotSunday() {
        // This was Sunday at 2 PM, titled "get some".
        assertEquals("get some sun @ 2026-10-29 14:00", whenOf("remind me to get some sun at 2pm"))
        assertEquals("call mom @ 2026-11-01 14:00", whenOf("remind me to call mom sun at 2pm"))
    }

    @Test
    fun aCallGoesOnlyToSomeoneTheWordsName() {
        val will = com.pixel.intelligentsearch.core.data.ContactItem("Will Smith", "+14155550240", "")
        // As the contacts search answers: anyone whose name starts with the first word.
        val lookup: (String) -> com.pixel.intelligentsearch.core.data.ContactItem? = { typed ->
            will.takeIf { typed.lowercase().startsWith("will") }
        }
        fun callee(query: String) = (NaturalLanguageIntentParser.parse(query, contactLookup = lookup)
            as? NaturalLanguageIntentParser.ParsedIntent.PhoneCall)?.contactNameOrNumber
        // This called Will Smith.
        assertNull(callee("call will's mom"))
        assertNull(callee("call will's office"))
        assertEquals("Will Smith", callee("call will"))
        assertEquals("Will Smith", callee("call will smith"))
    }

    @Test
    fun aTimerIsSetOnlyWhenItsWholeLengthIsRead() {
        // "An hour and 15 minutes" was 15 minutes, "a minute and 30 seconds" 30 seconds, "1/2 hour" 2 hours and "1 1/2
        // hours" 2 hours.
        assertEquals(4_500, timerSeconds("set a timer for an hour and 15 minutes"))
        assertEquals(90, timerSeconds("timer for a minute and 30 seconds"))
        assertEquals(2_400, timerSeconds("set a timer for half an hour and 10 minutes"))
        assertEquals(1_800, timerSeconds("set a timer for 1/2 hour"))
        assertEquals(1_800, timerSeconds("timer 1/2 hr"))
        assertEquals(900, timerSeconds("timer 1/4 hour"))
        assertEquals(30, timerSeconds("timer 1/2 minute"))
        assertEquals(5_400, timerSeconds("set a timer for 1 1/2 hours"))
        assertEquals(150, timerSeconds("set a timer for 2 1/2 minutes"))
        assertEquals(5_400, timerSeconds("set a timer for an hour and a half"))
        assertEquals(2_700, timerSeconds("timer for 3/4 of an hour"))
        // What the timer is for may sit around its length.
        assertEquals(180, timerSeconds("timer 3 minutes for eggs"))
        assertEquals(300, timerSeconds("timer for 2 eggs 5 minutes"))
        assertEquals(600, timerSeconds("set a timer for 10 mins for the pasta"))
        // A length read only in part is no timer rather than one of the wrong length.
        for (query in listOf(
            "set a timer for twenty minutes", "set a timer for 3 to 4 minutes", "timer 1½ minutes",
            "timer 5 minutes for eggs 3 minutes", "timer 45 minutes then 10"
        )) {
            assertNull(query, NaturalLanguageIntentParser.parse(query))
        }
    }

    @Test
    fun aTimeInAnotherZoneIsNoAction() {
        // Each was set at that hour here, the zone in its title or ignored.
        for (query in listOf(
            "meeting at 3pm est", "remind me to join the call at 9am et tomorrow", "schedule call with the london office at 4pm gmt",
            "meeting at 10am cet tomorrow", "remind me at 5pm eastern to call mom", "meeting at 2pm central time",
            "schedule standup at 9am ist tomorrow", "set an alarm for 6:45 pm est", "meeting at 3pm my time"
        )) {
            assertNull(query, NaturalLanguageIntentParser.parse(query, thursday(10), locale = java.util.Locale.US))
        }
        // "Central" on its own is a word of the task.
        assertEquals("call the central library @ 2026-10-29 17:00", whenOf("remind me to call the central library at 5pm"))
    }

    @Test
    fun aTimeOrDateWrittenWithADotIsReadOnlyWithAtOrNoAction() {
        // Each was set at the moment it was said, or for today, the "14.00" or "15.11" left in its title.
        for (query in listOf(
            "meeting tomorrow 14.00", "remind me tomorrow 9.30 to call the bank", "remind me to call the bank tomorrow 9.30",
            "meeting tomorrow 14h"
        )) {
            assertNull(query, whenOf(query))
        }
        for ((query, locale) in listOf(
            "remind me to pay rent on 1.12 at 9am" to java.util.Locale.GERMANY, "remind me to pay rent 15.11 at 3pm" to java.util.Locale.GERMANY,
            "meeting on 3.11 at 14:00" to java.util.Locale.UK, "schedule dentist 15.11 at 14:00" to java.util.Locale.UK
        )) {
            assertNull(query, NaturalLanguageIntentParser.parse(query, thursday(10), locale = locale))
        }
        assertEquals("Meeting @ 2026-10-30 14:00", whenOf("meeting tomorrow at 14.00"))
    }

    @Test
    fun anAtSignIsAtAndANumberLeftInTheTaskIsNoAction() {
        // Each was set for now, or for tomorrow at this time, with "@ 5" or "5" in its title.
        assertEquals("call mom @ 2026-10-29 17:00", whenOf("remind me to call mom @ 5"))
        assertEquals("call mom @ 2026-10-29 17:00", whenOf("remind me to call mom @5"))
        assertEquals("call mom @ 2026-10-30 17:00", whenOf("remind me to call mom tomorrow @ 5"))
        assertEquals("Meeting @ 2026-10-30 15:00", whenOf("meeting tomorrow @ 3"))
        for (query in listOf(
            "remind me to call mom 5", "remind me to call mom tomorrow 5", "remind me to call mom b4 5",
            "remind me to watch the 9 o'clock news", "remind me to catch the 5 o'clock train", "remind me to call mom at"
        )) {
            assertNull(query, whenOf(query))
        }
        // An address keeps its "@", and isn't a plain word of a task.
        assertNull(whenOf("remind me to email bob@example.com at 5pm"))
    }

    @Test
    fun aBareHourAfterSomeDaysFromNowIsTheAfternoonOrEvening() {
        // Each was the small hours: 05:00, 03:00, 02:30.
        assertEquals("call mom @ 2026-10-31 17:00", whenOf("remind me in 2 days at 5 to call mom"))
        assertEquals("pay the bill @ 2026-11-05 15:00", whenOf("remind me in a week at 3 to pay the bill"))
        assertEquals("pick up the prescription @ 2026-11-01 14:30", whenOf("remind me in 3 days at 2:30 to pick up the prescription"))
        // 8 to 11 on a day ahead is the morning.
        assertEquals("call mom @ 2026-10-31 09:00", whenOf("remind me in 2 days at 9 to call mom"))
    }

    @Test
    fun aMisspelledOrSlangDayWordIsNoAction() {
        // Each was set for today or now, the misspelling left in the task.
        for (query in listOf(
            "remind me to call mom tomorrrow at 5", "remind me to call mom tomrrow at 5", "remind me to call mom tommorw at 5",
            "remind me to call mom tmrow at 5", "remind me to call mom tomorroe at 5", "remind me to call mom tomororw at 5",
            "remind me to call mom tmro at 5", "remind me to call mom tomm at 5", "remind me to call mom tommrow at 5",
            "remind me to call mom tomorrowww at 5", "remind me to call mom firday at 5", "remind me to call mom on tuesady at 5",
            "remind me to call mom on fridya at 5", "remind me to call mom mondy at 5", "remind me to call mom on novemeber 5 at 3pm",
            "remind me to call mom on decmber 1 at 3pm", "remind me tomorrow mornin at 6 to go for a run",
            "remind me tomorrow morming at 6 to go for a run", "remind me to call mom at 5 tomorrow mornign",
            "remind me to call mom tmrw mornin", "remind me to call mom tmrw nite", "remind me to call mom this arvo",
            "remind me to call mom by eow", "remind me to call mom next wk", "remind me to call mom on the wknd",
            "remind me to call mom on the day of the dead"
        )) {
            assertNull(query, whenOf(query))
        }
        // Words a letter from a day word are still tasks: "light" is no "night", "warning" no "morning".
        assertEquals("turn off the light @ 2026-10-29 22:00", whenOf("remind me to turn off the light at 10pm"))
        assertEquals("check the warning lamp @ 2026-10-29 17:00", whenOf("remind me to check the warning lamp at 5pm"))
        assertEquals("call mom @ 2026-10-30 17:00", whenOf("remind me to call mom tmrw at 5"))
    }

    @Test
    fun firstThingAndInTheAmAreReadOrNoAction() {
        val tenPm = thursday(22)
        // Said at 10 PM these were 10 PM the next day.
        assertNull(whenOf("remind me first thing tomorrow to call the bank", tenPm))
        assertNull(whenOf("remind me to call the bank first thing in the morning", tenPm))
        // "At 6 in the am" was 6 PM.
        assertEquals("go running @ 2026-10-30 06:00", whenOf("remind me at 6 in the am to go running"))
        assertEquals("go running @ 2026-10-30 06:00", whenOf("remind me at 6 in the a.m. to go running"))
        assertEquals("lock the door @ 2026-10-29 21:00", whenOf("remind me at 9 in the pm to lock the door"))
        assertEquals(6 to 0, alarm("set alarm for 6 in the am", now = thursday(10)).let { it.hour to it.minute })
    }

    @Test
    fun aHolidayThatNamesTheEventIsNoAction() {
        // Each was set for today, as "schedule monday standup at 9" already wasn't.
        for (query in listOf(
            "schedule halloween party at 8pm", "schedule thanksgiving dinner at 4", "schedule christmas party at 7pm",
            "schedule new year's eve party at 9pm", "remind me to buy diwali lights at 5pm"
        )) {
            assertNull(query, whenOf(query))
        }
        // The holiday said as the day is still read.
        assertEquals("call grandma @ 2026-11-26 09:00", whenOf("remind me to call grandma on thanksgiving morning at 9"))
    }

    @Test
    fun aDayOfTheMonthAfterAWordOfWhoseIsNoDate() {
        // "December the first" was November 1 with "december" in the task; "my 30th" was October 30.
        assertNull(whenOf("remind me to call mom december the first at 3pm"))
        assertNull(whenOf("remind me to celebrate my 30th at 7pm"))
    }

    @Test
    fun wordsThatSayAnotherHalfOfTheDayThanTheHourAreNoAction() {
        // Under the shared hour rule "pack lunch at 7" would be 7 PM; it is neither a lunch nor clearly the morning.
        assertNull(whenOf("remind me to pack lunch at 7", thursday(6)))
        assertEquals("eat lunch @ 2026-10-29 16:00", whenOf("remind me to eat lunch at 4"))
    }

    @Test
    fun emojiAndWithShorthandStayInTheTask() {
        assertEquals("call mom 📞 @ 2026-10-30 17:00", whenOf("remind me to call mom 📞 tomorrow at 5"))
        assertEquals("Meeting w/ bob @ 2026-10-30 15:00", whenOf("meeting w/ bob tmrw 3pm"))
        // A clock face says an hour.
        assertNull(whenOf("remind me to call mom at 5 🕔"))
    }

    @Test
    fun aContactIsNamedByItsWordsOrAnInitial() {
        // "Sarah c" was Sarah Lee, the contact the search ranked first.
        assertTrue(NaturalLanguageIntentParser.namesContact("sarah c", "Sarah Connor"))
        assertFalse(NaturalLanguageIntentParser.namesContact("sarah c", "Sarah Lee"))
        assertFalse(NaturalLanguageIntentParser.namesContact("c", "Sarah Connor"))
        assertFalse(NaturalLanguageIntentParser.namesContact("sarah s", "Sarah Lee"))
        assertTrue(NaturalLanguageIntentParser.namesContact("dad", "Dad Work"))
    }

    @Test
    fun aMessageToSomeoneByWhoseTheyAreIsNoMessage() {
        val will = com.pixel.intelligentsearch.core.data.ContactItem("Will Smith", "+14155550201", "")
        val lookup: (String) -> com.pixel.intelligentsearch.core.data.ContactItem? = { typed ->
            will.takeIf { typed.lowercase().startsWith("will") }
        }
        // This went to Will Smith, "smith's wife happy birthday" as its words.
        assertNull(NaturalLanguageIntentParser.parse("text will smith's wife happy birthday", contactLookup = lookup))
        // This went to no one, "mom" as its words.
        assertNull(NaturalLanguageIntentParser.parse("text dad's work number to mom", contactLookup = lookup))
        val message = NaturalLanguageIntentParser.parse("text will it's done", contactLookup = lookup)
            as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        assertEquals("it's done", message.body)
    }
}
