package com.pixel.intelligentsearch.core.currency

import com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine
import com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Currency answers convert with live rates when there are some, and say which rates they used. */
class LiveCurrencyConversionTest {

    private val live = CurrencyRates(
        base = "eur",
        perBase = mapOf("eur" to 1.0, "usd" to 1.25, "gbp" to 0.8, "pln" to 4.0, "jpy" to 160.0),
        date = LocalDate.of(2026, 10, 19)
    )

    private fun convert(query: String, rates: CurrencyRates = live) =
        MathematicalExpressionEngine.evaluate(query) { rates } as? UnitConversion

    @Test
    fun liveRatesAreUsedWhenTheyListBothCurrencies() {
        val result = convert("100 usd to eur")!!
        assertEquals(80.0, result.toValue, 1e-9)
        assertSame(live, result.currencyRates)
        assertTrue(result.formatted, result.formatted.contains("≈"))
        assertEquals(128.0, convert("$200 to gbp")!!.toValue, 1e-9)
        // Neither side need be the base, and the live table may list currencies the built-in one doesn't.
        assertEquals(31.25, convert("100 pln to usd")!!.toValue, 1e-9)
    }

    @Test
    fun aCurrencyTheLiveRatesLackFallsBackToTheBuiltInRates() {
        val result = convert("100 usd to aed")!!
        assertEquals(367.0, result.toValue, 1e-9)
        assertSame(CurrencyRates.BUILT_IN, result.currencyRates)
    }

    @Test
    fun withoutLiveRatesTheBuiltInRatesAnswerAsBefore() {
        val result = MathematicalExpressionEngine.evaluate("100 usd to eur") as UnitConversion
        assertEquals(92.0, result.toValue, 1e-9)
        assertSame(CurrencyRates.BUILT_IN, result.currencyRates)
        // The built-in table has no zloty, so this was never an answer and still isn't.
        assertNull(MathematicalExpressionEngine.evaluate("100 pln to usd"))
        assertNull(MathematicalExpressionEngine.evaluate("100 abc to xyz"))
    }

    @Test
    fun twoCurrenciesWithNoAmountGiveTheRateForOne() {
        val result = convert("USD to INR", CurrencyRates.BUILT_IN)!!
        assertEquals(1.0, result.fromValue, 0.0)
        assertEquals("USD", result.fromUnit)
        assertEquals("INR", result.toUnit)
        assertEquals(83.5, result.toValue, 1e-9)
        assertEquals(0.64, convert("usd in gbp")!!.toValue, 1e-9)
        assertSame(live, convert("usd in gbp")!!.currencyRates)
        assertEquals(83.5, convert("$ to ₹", CurrencyRates.BUILT_IN)!!.toValue, 1e-9)
        // Codes no table lists, and other units without an amount, are still not answers.
        assertNull(convert("abc to xyz"))
        assertNull(MathematicalExpressionEngine.evaluate("km to mi"))
        assertNull(MathematicalExpressionEngine.evaluate("usd to"))
    }

    @Test
    fun otherUnitsNeverAskForRates() {
        val noRates: () -> CurrencyRates = { throw AssertionError("rates were read for a unit conversion") }
        val length = MathematicalExpressionEngine.evaluate("1 km to m", noRates) as UnitConversion
        assertEquals(1000.0, length.toValue, 0.0)
        assertNull(length.currencyRates)
        assertTrue(MathematicalExpressionEngine.evaluate("12*4", noRates) is MathematicalExpressionEngine.MathEvaluationResult.Computation)
    }

    @Test
    fun theCardSaysWhereTheRatesComeFrom() {
        assertEquals("Rates from Oct 19, 2026", live.sourceLabel(Locale.US))
        assertEquals("Built-in rates", CurrencyRates.BUILT_IN.sourceLabel(Locale.US))
    }

    @Test
    fun theCardNeverMixesDigitsInALocaleWithItsOwn() {
        assertTrue("this test needs a locale with its own digits", java.text.DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ar-EG")).zeroDigit != '0')
        val conversion = convert("1234.5 usd to eur")!!.formatted
        for (tag in listOf("ar-EG", "fa-IR", "hi-IN-u-nu-deva", "ar-SA-u-nu-arab", "bn-BD", "dz-BT")) {
            val locale = Locale.forLanguageTag(tag)
            val label = live.sourceLabel(locale)
            assertOneKindOfDigits("$conversion / $label in $tag", conversion + label)
            assertTrue("$label in $tag gives the day and year", label.contains("19") && label.contains("2026"))
        }
    }

    @Test
    fun everyLocaleWritesTheCardInOneKindOfDigits() {
        val conversion = convert("1234.5 usd to eur")!!.formatted
        for (locale in Locale.getAvailableLocales()) {
            val label = live.sourceLabel(locale)
            assertTrue("$label in $locale", label.startsWith("Rates from "))
            assertOneKindOfDigits("$conversion / $label in $locale", conversion + label)
        }
    }

    /** Every digit in [text] is from one set of ten: all 0 to 9, or all of one script's own. */
    private fun assertOneKindOfDigits(message: String, text: String) {
        val zeros = text.filter(Char::isDigit).map { it - Character.getNumericValue(it) }.toSet()
        assertEquals(message, 1, zeros.size)
    }
}
