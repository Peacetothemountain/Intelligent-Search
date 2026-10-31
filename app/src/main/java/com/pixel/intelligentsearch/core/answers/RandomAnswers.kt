package com.pixel.intelligentsearch.core.answers

import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random

/**
 * Chance answers, drawn on the device:
 * - "flip a coin", "coin toss", "heads or tails": Heads or Tails
 * - "roll a die", "roll 2 dice", "roll a d20", "roll 2d6": each roll and their total
 * - "random number" (1 to 100), "random number 1-100", "random 1 to 10", "pick a number between 1 and 6": a whole number
 *
 * Only these whole shapes are answered, so a search such as "random" or "coin" is left alone. [random] draws (fixed in
 * tests) and numbers are written with [locale]'s digits, the phone's when not given.
 */
class RandomAnswers(private val random: Random = Random.Default, private val locale: Locale? = null) {

    /** An answer: [title] for the card's large line (the outcome), [subtitle] under it saying what was drawn. */
    data class RandomAnswer(val title: String, val subtitle: String)

    /** The answer to [rawQuery], or null when it doesn't ask for chance. */
    fun evaluate(rawQuery: String): RandomAnswer? {
        val query = normalize(rawQuery) ?: return null
        if (query in COIN_PHRASES) return RandomAnswer(if (random.nextBoolean()) "Heads" else "Tails", "Coin flip")
        return dice(query) ?: number(query)
    }

    /** "roll a die", "roll 2 dice", "roll a d20", "roll 2d6": up to [MAX_DICE] dice of 2 to [MAX_SIDES] sides. */
    private fun dice(query: String): RandomAnswer? {
        val match = DICE_REGEX.matchEntire(query) ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: 1
        val sides = match.groupValues[2].toIntOrNull() ?: DEFAULT_SIDES
        if (count !in 1..MAX_DICE || sides !in 2..MAX_SIDES) return null
        val rolls = List(count) { random.nextInt(1, sides + 1) }
        val total = rolls.sum().toLong()
        val shown = rolls.take(SHOWN_ROLLS).joinToString(" + ") { format(it.toLong()) } + if (count > SHOWN_ROLLS) " + …" else ""
        // Dice notation is never grouped: "1d1000", not "1d1,000".
        val dice = "${format(count.toLong(), grouped = false)}d${format(sides.toLong(), grouped = false)}"
        return RandomAnswer(format(total), if (count == 1) "Dice roll • $dice" else "Dice roll • $dice: $shown")
    }

    /** "random number", "random number 1-100", "random 1 to 10", "pick a number between 1 and 6". */
    private fun number(query: String): RandomAnswer? {
        val match = NUMBER_REGEX.matchEntire(query) ?: BARE_RANGE_REGEX.matchEntire(query) ?: return null
        // Each pattern's range is both bounds or neither.
        val bounds = match.groupValues.drop(1).filter { it.isNotEmpty() }.map { it.toLongOrNull() ?: return null }
        val low = bounds.getOrElse(0) { DEFAULT_LOW }
        val high = bounds.getOrElse(1) { DEFAULT_HIGH }
        val (from, to) = if (low <= high) low to high else high to low
        val drawn = random.nextLong(from, to + 1)
        return RandomAnswer(format(drawn), "Random number from ${format(from)} to ${format(to)}")
    }

    private fun format(value: Long, grouped: Boolean = true): String =
        NumberFormat.getIntegerInstance(locale ?: Locale.getDefault()).apply { isGroupingUsed = grouped }.format(value)

    private fun normalize(raw: String): String? {
        val text = raw.trim().trimEnd('?', '.', '!').trim()
        if (text.isEmpty() || text.length > MAX_QUERY_LENGTH) return null
        return text.lowercase(Locale.ROOT).replace(WHITESPACE, " ")
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 60
        const val DEFAULT_SIDES = 6
        const val MAX_DICE = 100
        const val MAX_SIDES = 1000
        const val SHOWN_ROLLS = 10
        const val DEFAULT_LOW = 1L
        const val DEFAULT_HIGH = 100L
        val WHITESPACE = Regex("\\s+")

        val COIN_PHRASES = setOf(
            "flip a coin", "flip coin", "flip the coin", "coin flip", "coin toss", "toss a coin", "toss coin",
            "toss the coin", "heads or tails"
        )

        val DICE_REGEX = Regex("^(?:roll|throw)(?: (?:a|an|one|the))?(?: (\\d{1,3}))? ?(?:d(\\d{1,4})|dice|die)$|^dice roll$")

        /** Up to 12 digits a side, so the range and the draw stay well inside a Long. */
        private const val BOUND = "(-?\\d{1,12})"
        private const val RANGE = "(?: (?:between|from))? $BOUND ?(?:-|–|to|and|through|thru) ?$BOUND"
        val NUMBER_REGEX = Regex(
            "^(?:(?:pick|choose|generate|give me|get) )?(?:a )?(?:random number|random integer|random int|random num|rng)(?:$RANGE)?$"
        )
        val BARE_RANGE_REGEX = Regex(
            "^(?:(?:pick|choose|generate|give me) )?(?:a )?random$RANGE$|^(?:pick|choose) (?:a )?number$RANGE$"
        )
    }
}
