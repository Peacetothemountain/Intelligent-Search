package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The other ways the detail pane writes a calculator answer. */
class NumberFormsTest {

    private fun forms(result: String, locale: Locale = Locale.US) =
        numberForms(result, locale).associate { it.label to it.value }

    @Test
    fun aWholeNumberShowsInTheOtherBases() {
        val forms = forms("255")
        assertEquals("0xFF", forms["Hexadecimal"])
        assertEquals("0b1111 1111", forms["Binary"])
        assertEquals("0o377", forms["Octal"])
        // 255 groups no differently, so it isn't repeated.
        assertTrue("Grouped" !in forms)
    }

    @Test
    fun aLargeNumberIsGroupedTheLocalesWayAndInScientificNotation() {
        assertEquals("1,234,567", forms("1234567")["Grouped"])
        assertEquals("1.234.567", forms("1234567", Locale.GERMANY)["Grouped"])
        assertEquals("1.2346 × 10⁶", forms("1234567")["Scientific"])
    }

    @Test
    fun aNegativeNumberKeepsItsSignInEveryBase() {
        assertEquals("-0xA", forms("-10")["Hexadecimal"])
        assertEquals("-0b1010", forms("-10")["Binary"])
    }

    @Test
    fun aDecimalShowsAsAFractionAPercentageAndInScientificNotation() {
        val forms = forms("0.375")
        assertEquals("3/8", forms["Fraction"])
        assertEquals("37.5%", forms["Percentage"])
        assertEquals("3.75 × 10⁻¹", forms["Scientific"])
        assertEquals("1 1/3", forms("1.3333")["Fraction"])
    }

    @Test
    fun noSmallFractionMeansNoFraction() {
        assertTrue("Fraction" !in forms("3.1416"))
    }

    @Test
    fun anAnswerThatIsntANumberHasNoOtherForms() {
        assertTrue(numberForms("1500 m").isEmpty())
        assertTrue(numberForms("Tuesday").isEmpty())
    }
}
