package com.pixel.intelligentsearch.core.answers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/** Coins, dice and random numbers, drawn from a fixed sequence so every answer is known. */
class RandomAnswersTest {

    /** A Random that hands out [values] in turn: booleans from the low bit, ints and longs as they are. */
    private class Scripted(vararg values: Long) : Random() {
        private val queue = ArrayDeque(values.toList())
        val drawn = mutableListOf<Pair<Long, Long>>()
        override fun nextBits(bitCount: Int): Int = queue.removeFirst().toInt()
        override fun nextBoolean(): Boolean = queue.removeFirst() == 1L
        override fun nextInt(from: Int, until: Int): Int {
            drawn += from.toLong() to until.toLong()
            return queue.removeFirst().toInt()
        }
        override fun nextLong(from: Long, until: Long): Long {
            drawn += from to until
            return queue.removeFirst()
        }
    }

    private fun answers(vararg values: Long) = Scripted(*values).let { it to RandomAnswers(it, Locale.US) }

    @Test
    fun aCoinFlipIsHeadsOrTails() {
        assertEquals(RandomAnswers.RandomAnswer("Heads", "Coin flip"), answers(1).second.evaluate("flip a coin"))
        assertEquals(RandomAnswers.RandomAnswer("Tails", "Coin flip"), answers(0).second.evaluate("Coin toss"))
        for (query in listOf("coin flip", "toss a coin", "heads or tails?", "Flip a coin!")) {
            assertEquals(query, "Heads", answers(1).second.evaluate(query)?.title)
        }
    }

    @Test
    fun oneDieIsASixSidedRoll() {
        for (query in listOf("roll a die", "roll a dice", "Roll the dice", "roll dice", "dice roll", "throw a die")) {
            val (random, answers) = answers(4)
            assertEquals(query, RandomAnswers.RandomAnswer("4", "Dice roll • 1d6"), answers.evaluate(query))
            assertEquals(query, listOf(1L to 7L), random.drawn)
        }
    }

    @Test
    fun severalDiceShowEachRollAndTheTotal() {
        val (random, answers) = answers(3, 5)
        assertEquals(RandomAnswers.RandomAnswer("8", "Dice roll • 2d6: 3 + 5"), answers.evaluate("roll 2d6"))
        assertEquals(listOf(1L to 7L, 1L to 7L), random.drawn)

        assertEquals(RandomAnswers.RandomAnswer("17", "Dice roll • 1d20"), answers(17).second.evaluate("roll a d20"))
        assertEquals("Dice roll • 1d20", answers(17).second.evaluate("roll d20")?.subtitle)
        assertEquals(RandomAnswers.RandomAnswer("1,000", "Dice roll • 1d1000"), answers(1000).second.evaluate("roll a d1000"))
        assertEquals(RandomAnswers.RandomAnswer("7", "Dice roll • 2d6: 1 + 6"), answers(1, 6).second.evaluate("roll 2 dice"))
        // A long roll lists its first ten dice.
        val many = answers(*LongArray(12) { 2 }).second.evaluate("roll 12d4")!!
        assertEquals("24", many.title)
        assertTrue(many.subtitle, many.subtitle.endsWith("2 + 2 + …"))
    }

    @Test
    fun aRandomNumberIsDrawnFromTheRangeAsked() {
        val (random, answers) = answers(42)
        assertEquals(RandomAnswers.RandomAnswer("42", "Random number from 1 to 100"), answers.evaluate("random number"))
        assertEquals(listOf(1L to 101L), random.drawn)

        for ((query, range) in listOf(
            "random number 1-100" to (1L to 101L),
            "random 1 to 10" to (1L to 11L),
            "random number between 5 and 50" to (5L to 51L),
            "pick a number between 1 and 6" to (1L to 7L),
            "random number from 10 to 1" to (1L to 11L),
            "random -10 to 10" to (-10L to 11L),
            "RNG 1 - 1000000" to (1L to 1_000_001L)
        )) {
            val (drawnFrom, numbers) = answers(7)
            assertEquals(query, "7", numbers.evaluate(query)?.title)
            assertEquals(query, listOf(range), drawnFrom.drawn)
        }
        assertEquals("Random number from 1 to 1,000,000", answers(7).second.evaluate("random number 1-1000000")?.subtitle)
        assertEquals("Random number from -10 to 10", answers(7).second.evaluate("random -10 to 10")?.subtitle)
    }

    @Test
    fun ordinarySearchesAreLeftAlone() {
        for (query in listOf(
            "random", "coin", "dice", "roll", "random facts", "random number generator app", "number 1 to 10",
            "roll 0d6", "roll 101d6", "roll a d1", "roll d10000", "coin flip game", "rock and roll", "dice games", ""
        )) {
            assertNull("\"$query\" should not be a chance answer", answers(1, 1, 1).second.evaluate(query))
        }
    }
}
