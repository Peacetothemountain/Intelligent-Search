package com.pixel.intelligentsearch.core.search

import org.junit.Assert.*
import org.junit.Test

class MathematicalExpressionEngineTest {

    @Test
    fun testBasicArithmetic() {
        val res = MathematicalExpressionEngine.evaluate("2 + 2 * 3")
        assertTrue(res is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val comp = res as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(8.0, comp.numericValue, 0.001)
        assertEquals("8", comp.formattedResult)
    }

    @Test
    fun testScientificFunctions() {
        val resSqrt = MathematicalExpressionEngine.evaluate("sqrt(144)")
        assertTrue(resSqrt is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        assertEquals(12.0, (resSqrt as MathematicalExpressionEngine.MathEvaluationResult.Computation).numericValue, 0.001)

        val resFact = MathematicalExpressionEngine.evaluate("5!")
        assertTrue(resFact is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        assertEquals(120.0, (resFact as MathematicalExpressionEngine.MathEvaluationResult.Computation).numericValue, 0.001)

        val resTrig = MathematicalExpressionEngine.evaluate("sin(pi/2)")
        assertTrue(resTrig is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        assertEquals(1.0, (resTrig as MathematicalExpressionEngine.MathEvaluationResult.Computation).numericValue, 0.001)
    }

    @Test
    fun testBitwiseAndBaseConversions() {
        val bitwiseRes = MathematicalExpressionEngine.evaluate("0xFF AND 0x0F")
        assertTrue(bitwiseRes is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bit = bitwiseRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(15L, bit.decimalValue)
        assertEquals("0xF", bit.hexValue)

        val shiftRes = MathematicalExpressionEngine.evaluate("1 << 8")
        assertTrue(shiftRes is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        assertEquals(256L, (shiftRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise).decimalValue)

        val baseRes = MathematicalExpressionEngine.evaluate("255 to hex")
        assertTrue(baseRes is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bitwise = baseRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(255L, bitwise.decimalValue)
        assertEquals("0xFF", bitwise.hexValue)
    }

    @Test
    fun testUnitAndCurrencyConversion() {
        val lengthRes = MathematicalExpressionEngine.evaluate("10 km to m")
        assertTrue(lengthRes is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        val conv = lengthRes as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(10000.0, conv.toValue, 0.1)

        val tempRes = MathematicalExpressionEngine.evaluate("32 f to c")
        assertTrue(tempRes is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        assertEquals(0.0, (tempRes as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion).toValue, 0.1)

        val currRes = MathematicalExpressionEngine.evaluate("100 usd to eur")
        assertTrue(currRes is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        assertEquals(92.0, (currRes as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion).toValue, 1.0)
    }

    @Test
    fun testUnsigned64BitMask() {
        val maskRes = MathematicalExpressionEngine.evaluate("0xFFFFFFFFFFFFFFFF AND 0x0F")
        assertTrue(maskRes is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bit = maskRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(15L, bit.decimalValue)
    }

    @Test
    fun testTinyNumberFormat() {
        val res = MathematicalExpressionEngine.evaluate("1e-18")
        assertTrue(res is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val comp = res as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("1.0000e-18", comp.formattedResult)
    }

    @Test
    fun testUnsigned64BitDecimalConversion() {
        val res = MathematicalExpressionEngine.evaluate("0xFFFFFFFFFFFFFFFF to dec")
        assertTrue(res is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bit = res as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals("0xFFFFFFFFFFFFFFFF to dec = 18446744073709551615", bit.expression)
    }

    @Test
    fun testNegativeTemperatureConversion() {
        val res = MathematicalExpressionEngine.evaluate("-40 f to c")
        assertTrue(res is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        val conv = res as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(-40.0, conv.toValue, 0.1)
    }

    @Test
    fun testPureScientificNotation() {
        val res = MathematicalExpressionEngine.evaluate("1e6")
        assertTrue("Pure 1e6 must evaluate to Computation", res is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val comp = res as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(1000000.0, comp.numericValue, 0.001)
        assertEquals("1000000", comp.formattedResult)
    }

    @Test
    fun testDivisionAndModuloByZeroSafety() {
        val divZero = MathematicalExpressionEngine.evaluate("10 / 0")
        assertNull("Division by zero must safely evaluate to null", divZero)

        val modZero = MathematicalExpressionEngine.evaluate("10 % 0")
        assertNull("Modulo by zero must safely evaluate to null", modZero)
    }

    @Test
    fun testFunctionWithoutParens() {
        val res = MathematicalExpressionEngine.evaluate("sqrt 144")
        assertTrue("sqrt without parens must evaluate to Computation", res is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val comp = res as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(12.0, comp.numericValue, 0.001)
    }

    @Test
    fun testBitwiseNot() {
        val res = MathematicalExpressionEngine.evaluate("~0x0F")
        assertTrue("Bitwise NOT must return Bitwise result", res is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bit = res as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(15L.inv(), bit.decimalValue)
    }

    @Test
    fun phoneNumbersAndDatesAreNotSubtraction() {
        assertNull(MathematicalExpressionEngine.evaluate("555-1234"))
        assertNull(MathematicalExpressionEngine.evaluate("1-800-555-1234"))
        assertNull(MathematicalExpressionEngine.evaluate("2024-01-01"))
        assertNull(MathematicalExpressionEngine.evaluate("01-31-2024"))
        // Plain subtraction still works.
        val res = MathematicalExpressionEngine.evaluate("100-20-5")
        assertEquals(75.0, (res as MathematicalExpressionEngine.MathEvaluationResult.Computation).numericValue, 0.001)
    }

    @Test
    fun roundIsHalfAwayFromZero() {
        val up = MathematicalExpressionEngine.evaluate("round(2.5)") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(3.0, up.numericValue, 0.0)
        val down = MathematicalExpressionEngine.evaluate("round(-2.5)") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(-3.0, down.numericValue, 0.0)
    }

    @Test
    fun smallResultsKeepSignificantDigits() {
        val tiny = MathematicalExpressionEngine.evaluate("1/3000") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("0.0003333", tiny.formattedResult)
        val third = MathematicalExpressionEngine.evaluate("1/3") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("0.3333", third.formattedResult)
        val sum = MathematicalExpressionEngine.evaluate("0.1+0.2") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("0.3", sum.formattedResult)
    }

    @Test
    fun thousandsSeparatorsInNumericExpressions() {
        val res = MathematicalExpressionEngine.evaluate("1,000*2") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(2000.0, res.numericValue, 0.001)
    }

    @Test
    fun shiftsOfSixtyFourOrMoreAreNotAnswered() {
        val ok = MathematicalExpressionEngine.evaluate("1 << 10") as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(1024L, ok.decimalValue)
        assertNull(MathematicalExpressionEngine.evaluate("1 << 64"))
    }

    @Test
    fun megabitsAndMegabytesAreDistinguished() {
        val bits = MathematicalExpressionEngine.evaluate("100 Mb to MB") as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(12.5, bits.toValue, 0.0001)
        val bytes = MathematicalExpressionEngine.evaluate("1 MB to kb") as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(1000.0, bytes.toValue, 0.0001)
    }
}
