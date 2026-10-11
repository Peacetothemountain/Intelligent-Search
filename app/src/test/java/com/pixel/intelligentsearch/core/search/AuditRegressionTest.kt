package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AuditRegressionTest {

    private fun startOf(intent: NaturalLanguageIntentParser.ParsedIntent?): Calendar {
        val event = intent as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent
        return Calendar.getInstance().apply { timeInMillis = event.startMillis }
    }

    @Test
    fun weekdayMovesEventToTheNextMatchingDay() {
        // Wednesday 2026-10-07 09:00 local time.
        val base = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 7, 9, 0, 0) }
        val parsed = NaturalLanguageIntentParser.parse("meeting lunch with Sam on Friday at 1pm", base.timeInMillis)

        val start = startOf(parsed)
        assertEquals(Calendar.FRIDAY, start.get(Calendar.DAY_OF_WEEK))
        assertEquals(9, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(13, start.get(Calendar.HOUR_OF_DAY))
        assertEquals("lunch with Sam", (parsed as NaturalLanguageIntentParser.ParsedIntent.CalendarEvent).title)
    }

    @Test
    fun namingTodaysWeekdayMeansNextWeek() {
        val base = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 7, 9, 0, 0) } // Wednesday
        val start = startOf(NaturalLanguageIntentParser.parse("meeting standup wednesday at 10am", base.timeInMillis))
        assertEquals(14, start.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun outOfRangeClockTimeIsClampedInsteadOfRollingIntoAnotherDay() {
        val base = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 7, 9, 0, 0) }
        val start = startOf(NaturalLanguageIntentParser.parse("meeting review at 27:90", base.timeInMillis))
        assertEquals(7, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, start.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, start.get(Calendar.MINUTE))
    }

    @Test
    fun accentFoldedQueryScoresAsExactMatch() {
        val meta = SearchScoringEngine.ScoringMetadata(domain = SearchScoringEngine.EntityDomain.CONTACT)
        val folded = SearchScoringEngine.evaluateScore("cafe", "Café", meta)
        val exact = SearchScoringEngine.evaluateScore("café", "Café", meta)
        assertEquals(exact.matchScore, folded.matchScore, 0.001f)
    }

    @Test
    fun currencyConversionIsMarkedApproximate() {
        val result = MathematicalExpressionEngine.evaluate("100 usd to eur")
            as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertTrue(result.formatted, result.formatted.contains("≈"))
        val length = MathematicalExpressionEngine.evaluate("1 km to m")
            as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertTrue(length.formatted, length.formatted.contains(" = "))
    }
}
