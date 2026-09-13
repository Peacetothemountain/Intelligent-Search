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
        assertEquals(255L, (baseRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise).decimalValue)
        assertEquals("0xFF", (baseRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise).hexValue)
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
}
