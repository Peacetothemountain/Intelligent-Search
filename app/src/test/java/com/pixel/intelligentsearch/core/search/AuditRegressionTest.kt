package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun outOfRangeClockTimeIsRejectedInsteadOfRollingIntoAnotherDay() {
        // Clamping it to 23:59 still set the event at a time nobody asked for; it is no event at all.
        val base = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 7, 9, 0, 0) }
        assertNull(NaturalLanguageIntentParser.parse("meeting review at 27:90", base.timeInMillis))
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

    @Test
    fun subtitlePhoneAndPackageMatchesScoreAboveZero() {
        val contactMeta = SearchScoringEngine.ScoringMetadata(domain = SearchScoringEngine.EntityDomain.CONTACT)
        val phoneScore = SearchScoringEngine.evaluateScore(
            query = "5551234",
            targetTitle = "John Smith",
            metadata = contactMeta,
            targetSubtitle = "+1 (800) 555-1234"
        )
        assertTrue("Phone number match on subtitle must score > 0", phoneScore.totalScore > 0f)

        val appMeta = SearchScoringEngine.ScoringMetadata(domain = SearchScoringEngine.EntityDomain.APPLICATION)
        val pkgScore = SearchScoringEngine.evaluateScore(
            query = "whatsapp",
            targetTitle = "Messenger",
            metadata = appMeta,
            targetSubtitle = "com.whatsapp"
        )
        assertTrue("Package name match on subtitle must score > 0", pkgScore.totalScore > 0f)
    }

    @Test
    fun spacedCaretEvaluatesAsExponentiationWhileXorKeywordEvaluatesBitwise() {
        val expSpaced = MathematicalExpressionEngine.evaluate("2 ^ 3")
            as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(8.0, expSpaced.numericValue, 1e-6)

        val expCompact = MathematicalExpressionEngine.evaluate("2^3")
            as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(8.0, expCompact.numericValue, 1e-6)

        val xorRes = MathematicalExpressionEngine.evaluate("12 XOR 4")
            as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(8L, xorRes.decimalValue)
    }

    @Test
    fun barePiAndSpaceSeparatedNumbersDoNotTriggerFalsePositiveMath() {
        assertNull(MathematicalExpressionEngine.evaluate("pi"))
        assertNull(MathematicalExpressionEngine.evaluate("10 20 - 5"))
    }

    @Test
    fun nlpTimeBoundaryAndPhoneCallValidation() {
        val base = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 7, 9, 0, 0) }
        // "10" is no time; under the whole-phrase rule a number left in the task makes no reminder at all.
        assertNull(NaturalLanguageIntentParser.parse("remind me to meet 10 americans at 3pm", base.timeInMillis))

        // "phone settings" without digits or matched contact should not produce a broken PhoneCall intent
        assertNull(NaturalLanguageIntentParser.parse("phone settings", base.timeInMillis))
    }

    @Test
    fun packageNameSubtitleIsNotFuzzyMatched() {
        val appMeta = SearchScoringEngine.ScoringMetadata(domain = SearchScoringEngine.EntityDomain.APPLICATION)
        // "cam" is one edit from "com", which once ranked every installed app for "cam".
        val cam = SearchScoringEngine.evaluateScore(
            query = "cam",
            targetTitle = "Photos",
            metadata = appMeta,
            targetSubtitle = "com.google.android.apps.photos"
        )
        assertEquals(0f, cam.totalScore, 0f)

        val goo = SearchScoringEngine.evaluateScore(
            query = "goo",
            targetTitle = "Cash App",
            metadata = appMeta,
            targetSubtitle = "com.squareup.cash"
        )
        assertEquals(0f, goo.totalScore, 0f)

        val dialer = SearchScoringEngine.evaluateScore(
            query = "dialer",
            targetTitle = "Phone",
            metadata = appMeta,
            targetSubtitle = "com.google.android.dialer"
        )
        assertTrue("A whole package segment still matches", dialer.totalScore > 0f)
    }
}
