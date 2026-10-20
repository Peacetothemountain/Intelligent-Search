package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** Date questions are answered in plain words for a fixed "today"; anything else is left to ordinary search. */
class DateMathEngineTest {

    /** The engine as it would answer on [today] (a Tuesday, October 20, 2026 unless given). */
    private fun on(today: String = "2026-10-20", locale: Locale = Locale.US) = DateMathEngine(
        Clock.fixed(LocalDate.parse(today).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
        locale
    )

    private fun text(query: String, today: String = "2026-10-20") = on(today).evaluate(query)?.text

    @Test
    fun daysUntilADate() {
        val answer = on().evaluate("days until Dec 25")!!
        assertEquals("66 days until Friday, December 25, 2026", answer.text)
        assertEquals("66 days", answer.title)
        assertEquals("Until Friday, December 25, 2026", answer.subtitle)

        assertEquals(answer.text, text("how many days until december 25th?"))
        assertEquals(answer.text, text("days till 25 dec"))
        assertEquals(answer.text, text("days left until the 25th of December"))
        assertEquals(answer.text, text("Days to Dec. 25"))
        assertEquals("73 days until Friday, January 1, 2027", text("how many days until 2027-01-01"))
        assertEquals("1 day until Wednesday, October 21, 2026", text("days until tomorrow"))
    }

    @Test
    fun daysUntilANamedDay() {
        assertEquals("66 days until Friday, December 25, 2026", text("days until christmas"))
        assertEquals("66 days until Friday, December 25, 2026", text("How many days until Christmas?"))
        assertEquals("65 days until Thursday, December 24, 2026", text("days until christmas eve"))
        assertEquals("11 days until Saturday, October 31, 2026", text("days until halloween"))
        assertEquals("72 days until Thursday, December 31, 2026", text("days until new year's eve"))
        assertEquals("73 days until Friday, January 1, 2027", text("days until new years"))
        assertEquals("117 days until Sunday, February 14, 2027", text("days until valentine’s day"))
    }

    @Test
    fun daysSinceADate() {
        val answer = on().evaluate("days since Jan 1")!!
        assertEquals("292 days since Thursday, January 1, 2026", answer.text)
        assertEquals("292 days", answer.title)
        assertEquals("Since Thursday, January 1, 2026", answer.subtitle)
        // A day still to come this year was last year's.
        assertEquals("299 days since Thursday, December 25, 2025", text("days since christmas"))
        assertEquals("1 day since Monday, October 19, 2026", text("days since yesterday"))
    }

    @Test
    fun aDateWithAYearIsCountedEitherWay() {
        // Asked "until" a day already past, the answer says how long ago it was, and the other way round.
        assertEquals("2,484 days since Wednesday, January 1, 2020", text("days until 2020-01-01"))
        assertEquals("431 days until Saturday, December 25, 2027", text("days since Dec 25, 2027"))
    }

    @Test
    fun todayIsSaidInWords() {
        val answer = on("2026-12-25").evaluate("days until christmas")!!
        assertEquals("Today", answer.title)
        assertEquals("Friday, December 25, 2026", answer.subtitle)
        assertEquals("Friday, December 25, 2026 is today", answer.text)
    }

    @Test
    fun aDateWithoutAYearRollsOverToNextYear() {
        assertEquals("17 days until Friday, January 1, 2027", text("days until jan 1", today = "2026-12-15"))
        assertEquals("1 day until Friday, January 1, 2027", text("days until new year's day", today = "2026-12-31"))
        assertEquals("364 days until Saturday, December 25, 2027", text("days until christmas", today = "2026-12-26"))
        assertEquals("349 days since Thursday, January 1, 2026", text("days since jan 1", today = "2026-12-16"))
    }

    @Test
    fun leapDaysAndLeapYears() {
        // Feb 29 without a year is the next one there is.
        assertEquals("497 days until Tuesday, February 29, 2028", text("days until feb 29"))
        assertEquals("29 days between Tuesday, February 1, 2028 and Wednesday, March 1, 2028", text("days between 2028-02-01 and 2028-03-01"))
        assertEquals("28 days between Monday, February 1, 2027 and Monday, March 1, 2027", text("days between Feb 1 2027 and Mar 1 2027"))
        assertEquals("366 days between Saturday, January 1, 2028 and Monday, January 1, 2029", text("days between 2028-01-01 and 2029-01-01"))
        // A year on from a leap day lands on the last day of February.
        assertEquals("1 year from today is Wednesday, February 28, 2029", text("today + 1 year", today = "2028-02-29"))
        assertEquals("1 month from today is Sunday, February 28, 2027", text("1 month from today", today = "2027-01-31"))
        assertEquals("1 month from today is Tuesday, February 29, 2028", text("a month from now", today = "2028-01-31"))
        // Not a real day in any or in that year.
        assertNull(text("days until feb 30"))
        assertNull(text("days until 2027-02-29"))
        assertNull(text("days between feb 29 2027 and mar 1 2027"))
    }

    @Test
    fun aDateFromToday() {
        val answer = on().evaluate("today + 90 days")!!
        assertEquals("Monday, January 18, 2027", answer.title)
        assertEquals("90 days from today", answer.subtitle)
        assertEquals("90 days from today is Monday, January 18, 2027", answer.text)

        assertEquals(answer.text, text("90 days from now"))
        assertEquals(answer.text, text("today+90 days"))
        assertEquals(answer.text, text("today plus 90 days"))
        assertEquals("2 weeks from today is Tuesday, November 3, 2026", text("2 weeks from today"))
        assertEquals("1 week from today is Tuesday, October 27, 2026", text("a week from today"))
        assertEquals("1 day from today is Wednesday, October 21, 2026", text("1 day from now"))
        assertEquals("1 year from today is Wednesday, October 20, 2027", text("one year from now"))
    }

    @Test
    fun aDateAgo() {
        val answer = on().evaluate("3 months ago")!!
        assertEquals("Monday, July 20, 2026", answer.title)
        assertEquals("3 months ago", answer.subtitle)
        assertEquals("3 months ago was Monday, July 20, 2026", answer.text)
        assertEquals("10 days ago was Saturday, October 10, 2026", text("now - 10 days"))
        assertEquals("10 days ago was Saturday, October 10, 2026", text("today minus 10 days"))
        assertEquals("1 year ago was Monday, October 20, 2025", text("a year ago"))
        // Across the start of the year.
        assertEquals("2 weeks ago was Friday, December 25, 2026", text("2 weeks ago", today = "2027-01-08"))
    }

    @Test
    fun aDateFromAnotherDate() {
        val answer = on().evaluate("2 weeks before christmas")!!
        assertEquals("Friday, December 11, 2026", answer.title)
        assertEquals("2 weeks before Friday, December 25, 2026", answer.subtitle)
        assertEquals("2 weeks before Friday, December 25, 2026 is Friday, December 11, 2026", answer.text)
        assertEquals("100 days after Thursday, January 1, 2026 is Saturday, April 11, 2026", text("100 days after 2026-01-01"))
    }

    @Test
    fun daysBetweenTwoDates() {
        val answer = on().evaluate("how many days between Mar 1 and Jun 30")!!
        assertEquals("121 days", answer.title)
        assertEquals("Between Sunday, March 1, 2026 and Tuesday, June 30, 2026", answer.subtitle)
        assertEquals("121 days between Sunday, March 1, 2026 and Tuesday, June 30, 2026", answer.text)

        assertEquals(answer.text, text("days from march 1 to june 30"))
        // In either order, and a second date before the first without a year is the next year's.
        assertEquals("121 days between Tuesday, June 30, 2026 and Sunday, March 1, 2026", text("days between 2026-06-30 and 2026-03-01"))
        assertEquals("45 days between Tuesday, December 1, 2026 and Friday, January 15, 2027", text("days between dec 1 and jan 15"))
        assertEquals("0 days between Friday, December 25, 2026 and Friday, December 25, 2026", text("days between christmas and dec 25"))
        assertEquals("66 days between Tuesday, October 20, 2026 and Friday, December 25, 2026", text("days between today and christmas"))
    }

    @Test
    fun datesAreWrittenTheLocaleWay() {
        val german = on(locale = Locale.GERMANY)
        assertEquals("2.484 days since Mittwoch, 1. Januar 2020", german.evaluate("days until 2020-01-01")?.text)
    }

    @Test
    fun ordinarySearchesAreLeftAlone() {
        for (query in listOf(
            "28 days later", "500 days of summer", "days of our lives", "days until dawn", "days since the war",
            "christmas", "christmas movies", "days", "today", "now", "how many days", "days until", "days since",
            "3 months", "90 days", "today + 90", "today 90 days", "the days until christmas", "days until christmas party",
            "90 days in weeks", "100 usd to eur", "12*4", "2027-01-01", "dec 25", "weather today", "time in tokyo",
            "days between", "days between mar 1", "days between mar 1 and", "days between monday and friday",
            "next 7 days", "7 days to die", "days until 2027-13-01", "days until dec 32", "days until 0000-01-01",
            "today + 9999999 days", "today + 999999 years", "3 fortnights ago", "a b c", ""
        )) {
            assertNull("\"$query\" should not be a date answer", on().evaluate(query))
        }
    }
}
