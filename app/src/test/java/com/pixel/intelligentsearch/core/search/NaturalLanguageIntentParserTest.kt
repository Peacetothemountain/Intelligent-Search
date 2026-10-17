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
    fun messagesAcceptColonAndCommaRightAfterTheName() {
        val colon = NaturalLanguageIntentParser.parse("text Sarah: running late") as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        assertEquals("Sarah", colon.recipientName)
        assertEquals("running late", colon.body)
        val comma = NaturalLanguageIntentParser.parse("text mom, on my way") as NaturalLanguageIntentParser.ParsedIntent.DirectMessage
        assertEquals("on my way", comma.body)
    }
}
