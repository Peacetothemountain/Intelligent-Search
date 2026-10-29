package com.pixel.intelligentsearch.feature.search

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * One other way of writing a calculator answer: what it is ("Hexadecimal"), the answer written that way and what a tap
 * copies ([copyValue]: binary is shown in spaced nibbles but copied as one literal, "0b110111").
 */
internal data class NumberForm(val label: String, val value: String, val copyValue: String = value)

/**
 * The calculator answer [result] written the other ways people reach for, for the detail pane beside it on large
 * screens: a whole number grouped as [locale] groups digits, then in hexadecimal, binary and octal; a fraction as a
 * fraction (when a small one is exact), a percentage and in scientific notation. Nothing for an answer that isn't a
 * plain number (a conversion, a date), or for forms that would only repeat it. An answer the calculator already rounded
 * to scientific notation ("9.0072e+15") is only written in scientific notation: its digits past the fifth are gone, so
 * grouped or in another base it would look exact when it isn't.
 */
internal fun numberForms(result: String, locale: Locale = Locale.getDefault()): List<NumberForm> {
    val text = result.trim().removePrefix("=").trim()
    val number = text.toBigDecimalOrNull() ?: return emptyList()
    if (text.contains('e', ignoreCase = true) || number.abs() >= ROUNDED_FROM) {
        return if (number.signum() == 0) emptyList() else listOf(NumberForm("Scientific", scientific(number)))
    }
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
            if (magnitude.bitLength() <= 32) {
                val bits = magnitude.toString(2)
                forms += NumberForm("Binary", sign + "0b" + groupsOfFour(bits), copyValue = sign + "0b" + bits)
            }
            forms += NumberForm("Octal", sign + "0o" + magnitude.toString(8))
        }
        if (whole.abs() >= BigInteger.valueOf(1_000_000)) forms += NumberForm("Scientific", scientific(number))
    } else {
        fraction(number, text)?.let { forms += NumberForm("Fraction", it) }
        val percent = number.movePointRight(2).round(MathContext(6, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        forms += NumberForm("Percentage", percent.toPlainString() + "%")
        forms += NumberForm("Scientific", scientific(number))
    }
    return forms
}

/** From here up the calculator writes an answer in scientific notation, rounded to five significant digits. */
private val ROUNDED_FROM = BigDecimal("1e15")

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
 * [number] as the simplest fraction with a denominator up to 100 that is it to the precision [shown] was written with
 * ("0.375" is "3/8", "1.3333" is "4/3", shown "1 1/3"), or null when no such fraction does. The calculator writes four
 * decimals from 1 up and four significant digits below 1, so a fraction counts only within half the last place it
 * kept: "1234.5678" is not "1234 3/5", and a tiny "0.0003333" has no fraction rather than "0/2".
 */
private fun fraction(number: BigDecimal, shown: String): String? {
    if (number.signum() == 0) return null
    val magnitude = number.abs()
    // The decimals the calculator kept: 4 from 1 up, 4 significant digits below (0.0003333 keeps 7).
    val kept = if (magnitude >= BigDecimal.ONE) 4 else 3 - (magnitude.precision() - magnitude.scale() - 1)
    val decimals = maxOf(kept, shown.substringAfter('.', "").length)
    val tolerance = BigDecimal.ONE.movePointLeft(decimals).divide(BigDecimal(2))
    for (denominator in 2L..100L) {
        val d = BigDecimal.valueOf(denominator)
        val scaled = number.multiply(d)
        val numerator = scaled.setScale(0, RoundingMode.HALF_UP)
        if (numerator.signum() == 0) continue
        // |numerator / d - number| < tolerance, kept exact by multiplying through by d.
        if (numerator.subtract(scaled).abs() >= tolerance.multiply(d)) continue
        val n = numerator.toBigInteger().abs()
        val wholePart = n.divide(BigInteger.valueOf(denominator))
        val rest = n.mod(BigInteger.valueOf(denominator))
        val sign = if (numerator.signum() < 0) "-" else ""
        return if (wholePart.signum() == 0) "$sign$rest/$denominator" else "$sign$wholePart $rest/$denominator"
    }
    return null
}
