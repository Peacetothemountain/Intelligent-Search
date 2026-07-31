package com.pixel.intelligentsearch

import com.pixel.intelligentsearch.core.data.SystemDataProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchViewModelTest {

    @Test
    fun testEvaluateMath_addition() {
        val result = SystemDataProvider.evaluateMath("2+2")
        assertEquals("4", result)
    }

    @Test
    fun testEvaluateMath_subtraction() {
        val result = SystemDataProvider.evaluateMath("10-5")
        assertEquals("5", result)
    }

    @Test
    fun testEvaluateMath_multiplication() {
        val result = SystemDataProvider.evaluateMath("3*3")
        assertEquals("9", result)
    }

    @Test
    fun testEvaluateMath_division() {
        val result = SystemDataProvider.evaluateMath("8/2")
        assertEquals("4", result)
    }

    @Test
    fun testEvaluateMath_divisionByZero() {
        val result = SystemDataProvider.evaluateMath("5/0")
        assertNull(result)
    }

    @Test
    fun testEvaluateMath_invalidExpression() {
        val result = SystemDataProvider.evaluateMath("2+a")
        assertNull(result)
    }
}
