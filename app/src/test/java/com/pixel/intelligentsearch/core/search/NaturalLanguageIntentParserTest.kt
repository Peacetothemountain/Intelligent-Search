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
        assertEquals("with Sam", meeting.title)
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
        // Numbers alone are left be: "12/5" is December 5 in one country and May 12 in another.
        assertTrue(event("meeting 12/5 at 3pm").title.contains("12/5"))
    }

    @Test
    fun aDurationWithoutInIsPartOfTheTask() {
        // "cabin 2 hours" read as "in 2 hours", leaving the task "check the cab".
        val cabin = reminder("remind me to check the cabin 2 hours")
        assertEquals("check the cabin 2 hours", cabin.task)
        assertEquals(at(12), cabin.triggerMillis)
    }

    @Test
    fun dayWordsInsideOtherWordsStay() {
        // "today" was cut out of "todays", leaving "read s report".
        assertEquals("read todays report", reminder("remind me to read todays report at 4pm").task)
        assertEquals("read today's report", reminder("remind me to read today's report at 4pm").task)
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
}
