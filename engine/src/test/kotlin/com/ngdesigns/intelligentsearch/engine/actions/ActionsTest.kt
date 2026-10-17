package com.ngdesigns.intelligentsearch.engine.actions

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionsTest {
    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 3, 10, hour, minute)
    private fun timer(q: String) = (Actions.parse(q) as? Action.Timer)?.seconds

    @Test fun timers() {
        assertEquals(900, timer("timer 15"))
        assertEquals(90, timer("timer 1:30"))
        assertEquals(3_900, timer("timer 1:05:00"))
        assertEquals(86_400, timer("timer 24 hours"))
        assertEquals(5_415, timer("timer 1h 30m 15s"))
        assertEquals(900, timer("set timer 15m"))
        assertEquals(330, timer("timer 5 mins 30 secs"))
        assertEquals(600, timer("Set a timer for 10 minutes"))
        assertEquals(90, timer("start a timer for 90 seconds"))
        assertEquals(600, timer("10 minute timer"))
    }

    @Test fun impossibleTimersAreRefused() {
        for (q in listOf("timer 71582789", "timer 99999999999 minutes", "timer 25 hours", "timer 2 3", "timer 1:75", "timer")) {
            assertNull(q, Actions.parse(q))
        }
    }

    @Test fun alarms() {
        val a = Actions.parse("set alarm for 7:30 am", at(12)) as Action.Alarm
        assertEquals(7, a.hour); assertEquals(30, a.minute)
        assertEquals(LocalDate.of(2026, 3, 11), a.date)
        assertEquals(20, (Actions.parse("alarm 8pm") as Action.Alarm).hour)
        assertTrue(Actions.parse("Wake me up at 7:30") is Action.Alarm)
        val soon = Actions.parse("alarm in 10 minutes", at(14, 55)) as Action.Alarm
        assertEquals(15, soon.hour); assertEquals(5, soon.minute)
        val wake = Actions.parse("wake me up in 8 hours", at(23, 30)) as Action.Alarm
        assertEquals(7, wake.hour); assertEquals(30, wake.minute)
        assertEquals(LocalDate.of(2026, 3, 11), wake.date)
    }

    @Test fun reminders() {
        val now = LocalDateTime.of(2026, 9, 9, 12, 0)
        val r = Actions.parse("remind me to buy groceries tomorrow at 5pm", now) as Action.Reminder
        assertEquals("buy groceries", r.task)
        assertEquals(LocalDateTime.of(2026, 9, 10, 17, 0), r.at)
        val later = Actions.parse("remind me to call John in 2 hours", now) as Action.Reminder
        assertEquals("call John", later.task)
        assertEquals(LocalDateTime.of(2026, 9, 9, 14, 0), later.at)
        assertTrue(Actions.parse("Remind me to call the pharmacy at 6") is Action.Reminder)
        assertEquals(null, (Actions.parse("remind me to water the plants") as Action.Reminder).at)
    }

    @Test fun events() {
        val now = at(12)
        val m = Actions.parse("meeting with Sarah tomorrow at 3pm", now) as Action.Event
        assertEquals("Meeting with Sarah", m.title)
        assertEquals(LocalDateTime.of(2026, 3, 11, 15, 0), m.start)
        assertTrue(Actions.parse("Meeting tomorrow at 3 PM") is Action.Event)
        assertEquals(LocalDateTime.of(2026, 3, 10, 21, 0), (Actions.parse("meeting tonight at 9", at(10)) as Action.Event).start)
        assertEquals(LocalDateTime.of(2026, 3, 10, 15, 0), (Actions.parse("meeting at 3", at(13)) as Action.Event).start)
        assertEquals(LocalDateTime.of(2026, 3, 11, 9, 0), (Actions.parse("meeting at 9am", at(13)) as Action.Event).start)
        // 2026-03-10 is a Tuesday.
        val lunch = Actions.parse("lunch with Sam on Friday at 12:30", now) as Action.Event
        assertEquals(LocalDateTime.of(2026, 3, 13, 12, 30), lunch.start)
        assertEquals("Lunch with Sam", lunch.title)
        val dinner = Actions.parse("dinner for 2 at 7", at(12)) as Action.Event
        assertEquals(LocalDateTime.of(2026, 3, 10, 19, 0), dinner.start)
        assertNull(Actions.parse("meeting notes"))
    }

    @Test fun messages() {
        fun msg(q: String) = Actions.parse(q) as Action.Message
        assertEquals(Action.Message("José", "see you soon"), msg("text José saying see you soon"))
        assertEquals(Action.Message("Alice", "see you tonight"), msg("message Alice to see you tonight"))
        assertEquals(Action.Message("Sarah", "I'm running 5 minutes late"), msg("Text Sarah that I'm running 5 minutes late"))
        assertEquals(Action.Message("Sarah", "running late"), msg("text Sarah: running late"))
        assertEquals(Action.Message("mom", "on my way"), msg("text mom, on my way"))
        assertEquals(Action.Message("Dave", "hi", MessagingApp.WhatsApp), msg("message Dave on WhatsApp saying hi"))
    }

    @Test fun callsAndNavigation() {
        assertEquals(Action.Call("Mom"), Actions.parse("call Mom"))
        assertEquals(Action.Call("555-0199"), Actions.parse("dial 555-0199"))
        assertEquals(Action.Call("Sam", video = true), Actions.parse("video call Sam"))
        assertEquals(Action.Navigate("Central Park"), Actions.parse("directions to Central Park"))
        for (q in listOf("Directions home", "navigate home", "take me home")) assertEquals(q, Action.Navigate("Home"), Actions.parse(q))
        assertEquals(Action.Navigate("the airport"), Actions.parse("take me to the airport"))
        assertEquals(Action.Navigate("Work"), Actions.parse("navigate to work"))
    }

    @Test fun ordinarySearchesAreNotCommands() {
        for (q in listOf("weather", "pizza near me", "go fish", "calculator", "spotify", "best timers")) assertNull(q, Actions.parse(q))
    }
}
