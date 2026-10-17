package com.ngdesigns.intelligentsearch.engine.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/** What the calculator made of a query. */
sealed interface Answer {
    /** The query as understood, for display ("(1024 + 35%) − 98.65"). */
    val expression: String

    data class Number(override val expression: String, val value: Double, val formatted: String) : Answer

    /** A whole-number result shown in decimal, hexadecimal and binary. [unsigned] is the 64-bit unsigned reading. */
    data class Bits(override val expression: String, val value: Long) : Answer {
        val hex: String get() = "0x" + java.lang.Long.toHexString(value).uppercase()
        val binary: String get() = "0b" + java.lang.Long.toBinaryString(value)
        val unsigned: String get() = java.lang.Long.toUnsignedString(value)
    }

    data class Conversion(
        override val expression: String,
        val fromValue: Double,
        val from: Unit,
        val toValue: Double,
        val to: Unit,
        /** True when a currency conversion used the built-in approximate rates rather than fetched ones. */
        val approximate: Boolean = false,
    ) : Answer {
        val formatted: String get() = "${format(toValue)} ${to.symbol}"
    }
}

/**
 * Turns a query into an [Answer], or null when it isn't a calculation: plain words, a lone number, a phone number
 * or a date. Works entirely offline; currency uses [rates].
 */
object Calculator {

    fun evaluate(input: String, rates: CurrencyRates = CurrencyRates.Fallback): Answer? {
        val text = input.trim()
        if (text.isEmpty() || text.length > 200) return null
        Units.convert(text, rates)?.let { return it }
        baseConversion(text)?.let { return it }
        if (looksLikePhoneOrDate(text)) return null
        val cleaned = text
            .replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-')
            .replace(Regex("(?<=\\d),(?=\\d{3}(\\D|$))"), "")
        val tokens = Lexer(cleaned).tokens() ?: return null
        // Needs a number somewhere ("pi" alone excepted), so a lone "e" or a word is never a calculation.
        if (tokens.none { it is Token.Num } && text.lowercase() !in setOf("pi", "π")) return null
        if (tokens.none { it.isOperatorLike }) {
            // A lone number is only an answer when written in scientific notation ("1e6").
            val only = tokens.singleOrNull() as? Token.Num ?: return null
            if (!only.scientific) return null
        }
        val parser = Parser(tokens)
        val result = try { parser.parse() } catch (_: ArithmeticException) { return null } catch (_: IllegalStateException) { return null }
        return when (result) {
            is Value.Real -> if (result.v.isFinite()) Answer.Number(text, result.v, format(result.v)) else null
            is Value.Whole -> Answer.Bits(text, result.v)
        }
    }

    /** "255 to hex", "0xFF to dec", "0b1010 in octal". */
    private fun baseConversion(text: String): Answer? {
        val m = Regex("^(.+?)\\s+(?:to|in|as)\\s+(hex|hexadecimal|bin|binary|oct|octal|dec|decimal)$", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        val value = parseWhole(m.groupValues[1].trim()) ?: return null
        return if (m.groupValues[2].lowercase().startsWith("dec")) {
            Answer.Bits("$text = ${java.lang.Long.toUnsignedString(value)}", value)
        } else {
            Answer.Bits(text, value)
        }
    }

    internal fun parseWhole(s: String): Long? = try {
        when {
            s.startsWith("0x", true) -> java.lang.Long.parseUnsignedLong(s.substring(2), 16)
            s.startsWith("0b", true) -> java.lang.Long.parseUnsignedLong(s.substring(2), 2)
            s.startsWith("0o", true) -> java.lang.Long.parseUnsignedLong(s.substring(2), 8)
            else -> s.toLong()
        }
    } catch (_: NumberFormatException) { null }

    /** Digit groups joined by dashes that read as a phone number or a date, not a subtraction ("555-1234"). */
    private fun looksLikePhoneOrDate(text: String): Boolean {
        val compact = text.replace(" ", "")
        if (!Regex("^\\+?\\d+(-\\d+)+$").matches(compact)) {
            return Regex("^\\+?\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]\\d{4}$").matches(text) ||
                Regex("^\\d{1,4}[./]\\d{1,2}[./]\\d{2,4}$").matches(text)
        }
        val groups = compact.trimStart('+').split('-')
        if (groups.any { it.length > 1 && it.startsWith('0') }) return true
        val lengths = groups.map { it.length }
        return lengths == listOf(3, 4) || lengths == listOf(3, 3, 4) || lengths == listOf(1, 3, 3, 4) ||
            (groups.size == 3 && groups[0].length == 4 && groups[0].toInt() in 1900..2199)
    }
}

/** Formats a result: up to 10 significant digits, no trailing zeros, scientific for very large or small values. */
internal fun format(v: Double): String {
    if (v == 0.0) return "0"
    val a = abs(v)
    if (a >= 1e15 || a < 1e-9) return String.format(java.util.Locale.ROOT, "%.4e", v)
    val bd = BigDecimal(v).round(MathContext(if (a < 1) 4 else 10, RoundingMode.HALF_EVEN)).stripTrailingZeros()
    return bd.toPlainString()
}

private sealed interface Value {
    data class Real(val v: Double) : Value
    data class Whole(val v: Long) : Value
}

private sealed interface Token {
    val isOperatorLike: Boolean get() = true
    data class Num(val v: Double, val whole: Long?, val scientific: Boolean) : Token { override val isOperatorLike get() = false }
    data class Op(val s: String) : Token
    data class Word(val s: String) : Token
    data object Open : Token
    data object Close : Token
}

private class Lexer(val s: String) {
    fun tokens(): List<Token>? {
        val out = ArrayList<Token>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '0' && i + 1 < s.length && s[i + 1].lowercaseChar() in "xbo" -> {
                    val radix = when (s[i + 1].lowercaseChar()) { 'x' -> 16; 'b' -> 2; else -> 8 }
                    var j = i + 2
                    while (j < s.length && Character.digit(s[j], radix) >= 0) j++
                    if (j == i + 2) return null
                    val v = try { java.lang.Long.parseUnsignedLong(s.substring(i + 2, j), radix) } catch (_: NumberFormatException) { return null }
                    out += Token.Num(v.toDouble(), v, false); i = j
                }
                c.isDigit() || (c == '.' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    var j = i
                    while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                    var scientific = false
                    if (j < s.length && s[j].lowercaseChar() == 'e') {
                        var k = j + 1
                        if (k < s.length && (s[k] == '-' || s[k] == '+')) k++
                        if (k < s.length && s[k].isDigit()) {
                            while (k < s.length && s[k].isDigit()) k++
                            j = k; scientific = true
                        }
                    }
                    val text = s.substring(i, j)
                    val v = text.toDoubleOrNull() ?: return null
                    out += Token.Num(v, if (!scientific && !text.contains('.')) text.toLongOrNull() else null, scientific)
                    i = j
                }
                c == '(' -> { out += Token.Open; i++ }
                c == ')' -> { out += Token.Close; i++ }
                s.startsWith("<<", i) || s.startsWith(">>", i) || s.startsWith("**", i) -> { out += Token.Op(s.substring(i, i + 2)); i += 2 }
                c in "+-*/^%!~&|" -> { out += Token.Op(c.toString()); i++ }
                c == 'π' -> { out += Token.Word("pi"); i++ }
                c.isLetter() -> {
                    var j = i
                    while (j < s.length && s[j].isLetterOrDigit()) j++
                    out += Token.Word(s.substring(i, j).lowercase()); i = j
                }
                else -> return null
            }
        }
        return out
    }
}

/**
 * Recursive descent with the usual precedence: bitwise or, xor, and; shifts; + and -; * / and %; unary; powers;
 * postfix ! and %. Whole numbers stay exact as 64-bit values through bitwise operations.
 */
private class Parser(val t: List<Token>) {
    var p = 0
    var usedBits = false

    fun parse(): Value {
        val v = or()
        check(p == t.size) { "trailing input" }
        return if (usedBits) Value.Whole(whole(v)) else v.real()
    }

    private fun peek() = t.getOrNull(p)
    private fun isOp(s: String) = (peek() as? Token.Op)?.s == s || (peek() as? Token.Word)?.s == s.lowercase()
    private fun word(vararg w: String) = (peek() as? Token.Word)?.s in w

    private fun Value.real() = when (this) { is Value.Real -> this; is Value.Whole -> Value.Real(v.toDouble()) }
    private fun Value.d() = when (this) { is Value.Real -> v; is Value.Whole -> v.toDouble() }
    private fun whole(v: Value): Long = when (v) {
        is Value.Whole -> v.v
        is Value.Real -> { check(v.v == Math.rint(v.v) && abs(v.v) < 9.3e18) { "not whole" }; v.v.toLong() }
    }

    private fun or(): Value {
        var a = xor()
        while (isOp("|") || word("or")) { p++; usedBits = true; a = Value.Whole(whole(a) or whole(xor())) }
        return a
    }
    private fun xor(): Value {
        var a = and()
        while (word("xor")) { p++; usedBits = true; a = Value.Whole(whole(a) xor whole(and())) }
        return a
    }
    private fun and(): Value {
        var a = shift()
        while (isOp("&") || word("and")) { p++; usedBits = true; a = Value.Whole(whole(a) and whole(shift())) }
        return a
    }
    private fun shift(): Value {
        var a = additive()
        while (isOp("<<") || isOp(">>")) {
            val left = isOp("<<"); p++; usedBits = true
            val n = whole(additive())
            check(n in 0..63) { "shift out of range" }
            a = Value.Whole(if (left) whole(a) shl n.toInt() else whole(a) ushr n.toInt())
        }
        return a
    }

    private fun additive(): Value {
        var a = multiplicative()
        while (isOp("+") || isOp("-")) {
            val plus = isOp("+"); p++
            val start = p
            val b = multiplicative()
            // "x + 20%" adds 20% of x, as a calculator does.
            val pct = (t.getOrNull(p - 1) as? Token.Op)?.s == "%" && p - start >= 2
            val bv = if (pct) a.d() * b.d() else b.d()
            a = combine(a, b, if (plus) a.d() + bv else a.d() - bv, pct) { x, y -> if (plus) Math.addExact(x, y) else Math.subtractExact(x, y) }
        }
        return a
    }

    private fun multiplicative(): Value {
        var a = unary()
        while (true) {
            when {
                isOp("*") -> { p++; val b = unary(); a = combine(a, b, a.d() * b.d()) { x, y -> Math.multiplyExact(x, y) } }
                isOp("/") -> { p++; val b = unary(); if (b.d() == 0.0) throw ArithmeticException(); a = Value.Real(a.d() / b.d()) }
                isOp("%") && operandFollows() -> {
                    p++; val b = unary(); if (b.d() == 0.0) throw ArithmeticException()
                    a = combine(a, b, a.d() % b.d()) { x, y -> x % y }
                }
                word("mod") -> { p++; val b = unary(); if (b.d() == 0.0) throw ArithmeticException(); a = Value.Real(a.d() % b.d()) }
                word("of") && percentBefore() -> { p++; a = Value.Real(a.d() * unary().d()) }
                // Implicit multiplication: "2pi", "3(4+1)", "2 sqrt 9".
                peek() is Token.Open || (peek() as? Token.Word)?.s.let { it in FUNCTIONS || it == "pi" || it == "tau" } ->
                    { val b = unary(); a = Value.Real(a.d() * b.d()) }
                else -> return a
            }
        }
    }

    private fun operandFollows(): Boolean = when (val n = t.getOrNull(p + 1)) {
        is Token.Num, Token.Open -> true
        is Token.Word -> n.s in FUNCTIONS || n.s in CONSTANTS
        else -> false
    }
    private fun percentBefore() = (t.getOrNull(p - 1) as? Token.Op)?.s == "%"

    private fun unary(): Value = when {
        isOp("-") -> { p++; val v = unary(); if (v is Value.Whole) Value.Whole(-v.v) else Value.Real(-v.d()) }
        isOp("+") -> { p++; unary() }
        isOp("~") || word("not") -> { p++; usedBits = true; Value.Whole(whole(unary()).inv()) }
        else -> power()
    }

    private fun power(): Value {
        val base = postfix()
        if (isOp("^") || isOp("**")) { p++; return Value.Real(Math.pow(base.d(), unary().d())) }
        return base
    }

    private fun postfix(): Value {
        var v = primary()
        while (true) {
            when {
                isOp("!") -> { p++; v = Value.Real(factorial(v.d())) }
                isOp("%") && !operandFollows() -> { p++; v = Value.Real(v.d() / 100.0) }
                else -> return v
            }
        }
    }

    private fun primary(): Value = when (val tok = peek()) {
        is Token.Num -> { p++; if (tok.whole != null) Value.Whole(tok.whole) else Value.Real(tok.v) }
        Token.Open -> { p++; val v = or(); check(peek() == Token.Close) { "unclosed" }; p++; v }
        is Token.Word -> {
            p++
            CONSTANTS[tok.s]?.let { Value.Real(it) } ?: run {
                val f = FUNCTIONS[tok.s] ?: error("unknown word")
                // "sqrt 144" works without brackets: the function takes the next power-level operand.
                val arg = if (peek() == Token.Open) primary() else power()
                val r = f(arg.d())
                if (r.isNaN()) throw ArithmeticException()
                Value.Real(r)
            }
        }
        else -> error("expected a value")
    }

    private inline fun combine(a: Value, b: Value, real: Double, forceReal: Boolean = false, exact: (Long, Long) -> Long): Value =
        if (!forceReal && a is Value.Whole && b is Value.Whole) {
            try { Value.Whole(exact(a.v, b.v)) } catch (_: ArithmeticException) { Value.Real(real) }
        } else Value.Real(real)

    private fun factorial(n: Double): Double {
        check(n >= 0 && n == Math.rint(n) && n <= 170) { "bad factorial" }
        var r = 1.0
        for (i in 2..n.toInt()) r *= i
        return r
    }

    companion object {
        val CONSTANTS = mapOf("pi" to Math.PI, "e" to Math.E, "tau" to 2 * Math.PI, "phi" to (1 + Math.sqrt(5.0)) / 2)
        val FUNCTIONS: Map<String, (Double) -> Double> = mapOf(
            "sqrt" to Math::sqrt, "cbrt" to Math::cbrt, "abs" to Math::abs, "exp" to Math::exp,
            "sin" to Math::sin, "cos" to Math::cos, "tan" to Math::tan,
            "asin" to Math::asin, "acos" to Math::acos, "atan" to Math::atan,
            "ln" to Math::log, "log" to Math::log10, "log2" to { x -> Math.log(x) / Math.log(2.0) },
            "floor" to Math::floor, "ceil" to Math::ceil,
            // Half away from zero, as people round: 2.5 -> 3, -2.5 -> -3.
            "round" to { x -> BigDecimal(x).setScale(0, RoundingMode.HALF_UP).toDouble() },
        )
    }
}

