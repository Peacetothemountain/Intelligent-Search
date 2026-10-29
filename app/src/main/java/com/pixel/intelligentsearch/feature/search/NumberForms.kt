package com.pixel.intelligentsearch.feature.search

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** One other way of writing a calculator answer: what it is ("Hexadecimal") and the answer written that way. */
internal data class NumberForm(val label: String, val value: String)

/**
 * The calculator answer [result] written the other ways people reach for, for the detail pane beside it on large
 * screens: a whole number grouped as [locale] groups digits, then in hexadecimal, binary and octal; a fraction as a
 * fraction (when a small one is exact), a percentage and in scientific notation. Nothing for an answer that isn't a
 * plain number (a conversion, a date), or for forms that would only repeat it.
 */
internal fun numberForms(result: String, locale: Locale = Locale.getDefault()): List<NumberForm> {
    val number = result.trim().removePrefix("=").trim().toBigDecimalOrNull() ?: return emptyList()
    val forms = mutableListOf<NumberForm>()
    val whole = number.stripTrailingZeros().let { if (it.scale() <= 0) it.toBigIntegerExact() else null }
    if (whole != null) {
        val grouped = NumberFormat.getIntegerInstance(locale).format(whole)
        if (grouped != whole.toString()) forms += NumberForm("Grouped", grouped)
        // Bases only where they're readable: up to 64 bits, binary up to 32.
        if (whole.abs().bitLength() <= 63 && whole.signum() != 0) {
            val sign = if (whole.signum() < 0) "-" else ""
            val magnitude = whole.abs()
            forms += NumberForm("Hexadecimal", sign + "0x" + magnitude.toString(16).uppercase())
            if (magnitude.bitLength() <= 32) forms += NumberForm("Binary", sign + "0b" + groupsOfFour(magnitude.toString(2)))
            forms += NumberForm("Octal", sign + "0o" + magnitude.toString(8))
        }
        if (whole.abs() >= BigInteger.valueOf(1_000_000)) forms += NumberForm("Scientific", scientific(number))
    } else {
        fraction(number)?.let { forms += NumberForm("Fraction", it) }
        val percent = number.movePointRight(2).round(MathContext(6, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        forms += NumberForm("Percentage", percent.toPlainString() + "%")
        forms += NumberForm("Scientific", scientific(number))
    }
    return forms
}

/** [number] as "1.2346 × 10⁵". */
private fun scientific(number: BigDecimal): String {
    if (number.signum() == 0) return "0"
    val rounded = number.round(MathContext(5, RoundingMode.HALF_EVEN))
    val exponent = rounded.precision() - rounded.scale() - 1
    val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
    return "$mantissa × 10${superscript(exponent)}"
}

private fun superscript(n: Int): String = n.toString().map {
    when (it) {
        '-' -> '⁻'
        '0' -> '⁰'; '1' -> '¹'; '2' -> '²'; '3' -> '³'; '4' -> '⁴'
        '5' -> '⁵'; '6' -> '⁶'; '7' -> '⁷'; '8' -> '⁸'; else -> '⁹'
    }
}.joinToString("")

/** "101101" as "10 1101", so long binary reads in nibbles. */
private fun groupsOfFour(bits: String): String = bits.reversed().chunked(4).joinToString(" ").reversed()

/**
 * [number] as the simplest fraction with a denominator up to 100 that equals it to the calculator's four decimals
 * ("0.375" is "3/8", "1.3333" is "4/3", shown "1 1/3"), or null when no such fraction does.
 */
private fun fraction(number: BigDecimal): String? {
    val value = number.toDouble()
    if (!value.isFinite()) return null
    var bestNumerator = 0L
    var bestDenominator = 0L
    for (denominator in 2L..100L) {
        val numerator = Math.round(value * denominator)
        if (abs(numerator.toDouble() / denominator - value) < 0.5e-4 * maxOf(1.0, abs(value))) {
            bestNumerator = numerator
            bestDenominator = denominator
            break
        }
    }
    if (bestDenominator == 0L) return null
    val sign = if (bestNumerator < 0) "-" else ""
    val n = abs(bestNumerator)
    val wholePart = n / bestDenominator
    val rest = n % bestDenominator
    return if (wholePart == 0L) "$sign$rest/$bestDenominator" else "$sign$wholePart $rest/$bestDenominator"
}
