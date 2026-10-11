package com.pixel.intelligentsearch

import com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchViewModelTest {

    @Test
    fun testEvaluateMath_addition() {
        val result = (MathematicalExpressionEngine.evaluate("2+2") as? MathematicalExpressionEngine.MathEvaluationResult.Computation)?.formattedResult
        assertEquals("4", result)
    }

    @Test
    fun testEvaluateMath_subtraction() {
        val result = (MathematicalExpressionEngine.evaluate("10-5") as? MathematicalExpressionEngine.MathEvaluationResult.Computation)?.formattedResult
        assertEquals("5", result)
    }

    @Test
    fun testEvaluateMath_multiplication() {
        val result = (MathematicalExpressionEngine.evaluate("3*3") as? MathematicalExpressionEngine.MathEvaluationResult.Computation)?.formattedResult
        assertEquals("9", result)
    }

    @Test
    fun testEvaluateMath_division() {
        val result = (MathematicalExpressionEngine.evaluate("8/2") as? MathematicalExpressionEngine.MathEvaluationResult.Computation)?.formattedResult
        assertEquals("4", result)
    }

    @Test
    fun testEvaluateMath_divisionByZero() {
        val result = (MathematicalExpressionEngine.evaluate("5/0") as? MathematicalExpressionEngine.MathEvaluationResult.Computation)?.formattedResult
        assertNull(result)
    }

    @Test
    fun testEvaluateMath_invalidExpression() {
        val result = (MathematicalExpressionEngine.evaluate("2+a") as? MathematicalExpressionEngine.MathEvaluationResult.Computation)?.formattedResult
        assertNull(result)
    }
}
