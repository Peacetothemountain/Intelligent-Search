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
}
