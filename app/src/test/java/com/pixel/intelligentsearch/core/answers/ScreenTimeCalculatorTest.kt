package com.pixel.intelligentsearch.core.answers

import com.pixel.intelligentsearch.core.answers.ScreenTimeCalculator.Event
import com.pixel.intelligentsearch.core.answers.ScreenTimeCalculator.PAUSED
import com.pixel.intelligentsearch.core.answers.ScreenTimeCalculator.RESUMED
import com.pixel.intelligentsearch.core.answers.ScreenTimeCalculator.SCREEN_NON_INTERACTIVE
import com.pixel.intelligentsearch.core.answers.ScreenTimeCalculator.STOPPED
import org.junit.Assert.assertEquals
import org.junit.Test

/** Screen time is counted the way Digital Wellbeing counts it, from the moments apps come and go. */
class ScreenTimeCalculatorTest {

    private val minute = 60_000L
    private val start = 0L
    private val end = 600 * minute

    @Test
    fun eachAppCountsTheTimeItWasInFront() {
        val events = listOf(
            Event("maps", RESUMED, 10 * minute), Event("maps", PAUSED, 25 * minute),
            Event("chat", RESUMED, 25 * minute), Event("chat", PAUSED, 30 * minute),
            Event("maps", RESUMED, 40 * minute), Event("maps", STOPPED, 45 * minute)
        )
        assertEquals(mapOf("maps" to 20 * minute, "chat" to 5 * minute), ScreenTimeCalculator.perApp(events, start, end))
    }

    @Test
    fun anAppOpenAtMidnightCountsFromMidnightAndOneStillOpenUpToNow() {
        val events = listOf(
            Event("reader", PAUSED, 15 * minute),
            Event("video", RESUMED, 590 * minute)
        )
        assertEquals(mapOf("reader" to 15 * minute, "video" to 10 * minute), ScreenTimeCalculator.perApp(events, start, end))
    }

    @Test
    fun theScreenGoingOffEndsWhatWasShowing() {
        val events = listOf(
            Event("video", RESUMED, 100 * minute),
            Event("android", SCREEN_NON_INTERACTIVE, 130 * minute),
            Event("video", PAUSED, 300 * minute)
        )
        assertEquals(mapOf("video" to 30 * minute), ScreenTimeCalculator.perApp(events, start, end))
    }

    @Test
    fun twoActivitiesOfOneAppAreCountedOnce() {
        val events = listOf(
            Event("mail", RESUMED, 10 * minute, "Inbox"),
            Event("mail", RESUMED, 12 * minute, "Compose"),
            Event("mail", PAUSED, 14 * minute, "Inbox"),
            Event("mail", PAUSED, 20 * minute, "Compose")
        )
        assertEquals(mapOf("mail" to 10 * minute), ScreenTimeCalculator.perApp(events, start, end))
    }

    @Test
    fun theHomeScreenIsLeftOut() {
        val events = listOf(Event("launcher", RESUMED, 0), Event("launcher", PAUSED, 50 * minute), Event("maps", RESUMED, 50 * minute), Event("maps", PAUSED, 60 * minute))
        assertEquals(mapOf("maps" to 10 * minute), ScreenTimeCalculator.perApp(events, start, end, excluded = setOf("launcher")))
    }

    @Test
    fun theReportTotalsEverythingAndNamesTheTopFive() {
        val perApp = (1..7).associate { "app$it" to it * minute } + ("gone" to 100 * minute)
        val report = ScreenTimeCalculator.report(perApp) { pkg -> if (pkg == "gone") null else pkg.uppercase() }
        assertEquals(128 * minute, report.totalMs)
        assertEquals(listOf("APP7", "APP6", "APP5", "APP4", "APP3"), report.topApps.map { it.label })
    }

    @Test
    fun durationsReadLikeDigitalWellbeing() {
        assertEquals("3 hr 12 min", ScreenTimeCalculator.formatDuration(192 * minute))
        assertEquals("2 hr", ScreenTimeCalculator.formatDuration(120 * minute + 20_000))
        assertEquals("45 min", ScreenTimeCalculator.formatDuration(45 * minute))
        assertEquals("Under 1 min", ScreenTimeCalculator.formatDuration(30_000))
    }
}
