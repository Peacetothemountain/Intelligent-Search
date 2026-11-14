package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorMemoryTest {

    @Test
    fun theLastFewResultsAreKeptNewestFirst() {
        val memory = CalculatorMemory()
        for (n in 1..6) memory.keep("$n", n.toDouble())
        assertEquals(listOf("6", "5", "4", "3"), memory.earlier.map { it.shown })
        assertEquals("6", memory.ans?.shown)
    }

    @Test
    fun theSameResultAgainMovesToTheFrontOnce() {
        val memory = CalculatorMemory()
        memory.keep("55", 55.0)
        memory.keep("48", 48.0)
        memory.keep("55", 55.0)
        assertEquals(listOf("55", "48"), memory.earlier.map { it.shown })
    }

    @Test
    fun ansStandsForTheLastResultAsAWordOfItsOwn() {
        val memory = CalculatorMemory()
        assertNull("nothing kept yet", memory.withAns("ans/5"))
        memory.keep("55", 55.0)
        assertEquals("(55)/5", memory.withAns("ans/5"))
        assertEquals("(55) * 2 + (55)", memory.withAns("ANS * 2 + ans"))
        assertTrue(memory.usesAns("ans/5"))
        // Words that merely contain the letters are left alone.
        for (query in listOf("answer", "cans", "trans", "2 answers", "ans_1", "12*4")) {
            assertNull(query, memory.withAns(query))
            assertFalse(query, memory.usesAns(query))
        }
    }

    @Test
    fun aKeptNumberIsTypedBackWithoutGroupingOrExponent() {
        assertEquals("1234567", CalculatorMemory.Entry("1,234,567", 1234567.0).typed)
        assertEquals("0.1", CalculatorMemory.Entry("0.1", 0.1).typed)
        assertEquals("-2.5", CalculatorMemory.Entry("-2.5", -2.5).typed)
        assertEquals("0.00000012", CalculatorMemory.Entry("1.2E-7", 1.2e-7).typed)
        // A negative result goes in brackets, so "ans*2" stays one number times two.
        val memory = CalculatorMemory().apply { keep("-2.5", -2.5) }
        assertEquals("(-2.5)*2", memory.withAns("ans*2"))
    }

    @Test
    fun anEarlierResultIsRecalledLikeACalculatorsMemory() {
        // The number the sum ends with is replaced.
        assertEquals("2+55", CalculatorMemory.recall("2+2", "55"))
        assertEquals("55", CalculatorMemory.recall("12.5", "55"))
        // After an operator or nothing at all it goes on the end; after a bracket it multiplies.
        assertEquals("2+55", CalculatorMemory.recall("2+", "55"))
        assertEquals("ans*55", CalculatorMemory.recall("ans*", "55"))
        assertEquals("(2+3)*55", CalculatorMemory.recall("(2+3)", "55"))
        assertEquals("55", CalculatorMemory.recall("", "55"))
        assertEquals("2*(-2.5)", CalculatorMemory.recall("2*", "-2.5"))
        // After "ans" or a constant it multiplies too, so the sum still reads.
        assertEquals("2+ans*55", CalculatorMemory.recall("2+ans", "55"))
        assertEquals("2*pi*55", CalculatorMemory.recall("2*pi", "55"))
    }

    @Test
    fun whereTheDecimalPointIsACommaNumbersGoBackInThatWay() {
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            val memory = CalculatorMemory()
            memory.keep("1,125", 1.125)
            assertEquals("1,125", memory.ans?.typed)
            assertEquals("(1,125)*2", memory.withAns("ans*2"))
            // "1,5" is one number: the whole of it is replaced.
            assertEquals("2+55", CalculatorMemory.recall("2+1,5", "55"))
            assertEquals(1.5, CalculatorMemory.plainValue("1,5")!!, 0.0)
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test
    fun aSumBeingTypedShowsItsTotalSoFar() {
        assertEquals("12*4+7", CalculatorMemory.finishedPart("12*4+7+"))
        assertEquals("120", CalculatorMemory.finishedPart("120 + "))
        assertEquals("2", CalculatorMemory.finishedPart("2*("))
        // Not unfinished: nothing at the end to finish, or no number at all.
        assertNull(CalculatorMemory.finishedPart("12*4+7"))
        assertNull(CalculatorMemory.finishedPart("playstation 5 "))
        assertNull(CalculatorMemory.finishedPart("c++"))
        assertTrue(CalculatorMemory.isPlainNumber("120"))
        assertTrue(CalculatorMemory.isPlainNumber("-3.5"))
        assertFalse(CalculatorMemory.isPlainNumber("12*4"))
    }

    @Test
    fun whatIsNotANumberIsNotKeptAndClearForgetsAll() {
        val memory = CalculatorMemory()
        memory.keep("NaN", Double.NaN)
        memory.keep("∞", Double.POSITIVE_INFINITY)
        memory.keep("", 3.0)
        assertTrue(memory.earlier.isEmpty())
        memory.keep("3", 3.0)
        memory.clear()
        assertNull(memory.ans)
        assertNull(memory.withAns("ans+1"))
    }
}
