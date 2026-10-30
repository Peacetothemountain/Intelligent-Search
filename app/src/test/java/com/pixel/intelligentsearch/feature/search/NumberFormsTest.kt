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
        // The calculator writes 4/3 to ten significant digits (it was "1.3333" at four decimals).
        assertEquals("1 1/3", forms("1.333333333")["Fraction"])
        assertEquals("1/3", forms("0.3333333333")["Fraction"])
    }

    @Test
    fun anIrrationalAnswerHasNoFraction() {
        // e was offered as "2 51/71", √3 as "1 41/56", √5 as "2 17/72", φ as "1 55/89" and √2/2 as "70/99".
        for (shown in listOf("2.718281828", "1.732050808", "2.236067977", "1.618033989", "0.7071067812", "2.7183", "1.7321", "0.7071")) {
            assertTrue(shown, "Fraction" !in forms(shown))
        }
        assertTrue("Fraction" !in forms("1.3333"))
    }

    @Test
    fun anAnswerWithADecimalCommaHasItsOtherForms() {
        val forms = forms("2,5", Locale.GERMANY)
        assertEquals("2 1/2", forms["Fraction"])
        assertEquals("250%", forms["Percentage"])
    }

    @Test
    fun noSmallFractionMeansNoFraction() {
        assertTrue("Fraction" !in forms("3.1416"))
    }

    @Test
    fun aFractionMustBeExactToTheShownPrecision() {
        // The tolerance used to grow with the number, so these four-decimal answers were offered rough fractions.
        assertTrue("Fraction" !in forms("12345.6789"))
        assertTrue("Fraction" !in forms("1234.5678"))
        assertEquals("1234 3/5", forms("1234.6")["Fraction"])
        // A tiny answer rounded to no fraction, never "0/2".
        assertTrue("Fraction" !in forms("0.0003333"))
        assertTrue("Fraction" !in forms("0.00001"))
        assertEquals("1/100", forms("0.01")["Fraction"])
        assertEquals("-3/8", forms("-0.375")["Fraction"])
    }

    @Test
    fun anAnswerTheCalculatorAlreadyRoundedIsOnlyWrittenInScientificNotation() {
        // 2^53 was shown as 9.0072e+15; grouped or in hex it looked exact (9,007,200,000,000,000). 2^63 still is.
        assertEquals(mapOf("Scientific" to "9.0072 × 10¹⁵"), forms("9.0072e+15"))
        assertEquals(mapOf("Scientific" to "9.2234 × 10¹⁸"), forms("9.2234e+18"))
        assertEquals(mapOf("Scientific" to "1.5 × 10⁻⁵"), forms("1.5000e-05"))
        // Whole numbers below 2^63 are now written out in full (10^15 was "1.0000e+15"), so they have every form.
        assertEquals("1,000,000,000,000,000", forms("1000000000000000")["Grouped"])
        assertEquals("0x4000000000000", forms("1125899906842624")["Hexadecimal"])
    }

    @Test
    fun binaryIsShownInNibblesButCopiedAsOneLiteral() {
        val binary = numberForms("55", Locale.US).single { it.label == "Binary" }
        assertEquals("0b11 0111", binary.value)
        assertEquals("0b110111", binary.copyValue)
        assertEquals("-0b110111", numberForms("-55", Locale.US).single { it.label == "Binary" }.copyValue)
        // The other forms copy what they show.
        assertTrue(numberForms("1234567", Locale.US).filter { it.label != "Binary" }.all { it.copyValue == it.value })
    }

    @Test
    fun anAnswerThatIsntANumberHasNoOtherForms() {
        assertTrue(numberForms("1500 m").isEmpty())
        assertTrue(numberForms("Tuesday").isEmpty())
    }
}
