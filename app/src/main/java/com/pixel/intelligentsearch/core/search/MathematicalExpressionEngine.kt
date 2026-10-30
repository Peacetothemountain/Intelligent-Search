package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.currency.CurrencyRates
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.IllformedLocaleException
import java.util.Locale
import kotlin.math.*

/**
 * High-performance, zero-network Scientific Mathematical Expression Parser,
 * Bitwise Evaluator, and Unit & Currency Converter.
 *
 * Implements a recursive descent parser with an extensible tokenizer,
 * supporting trigonometric, logarithmic, root, combinatoric, and bitwise operations.
 */
object MathematicalExpressionEngine {

    sealed class MathEvaluationResult {
        data class Computation(val expression: String, val formattedResult: String, val numericValue: Double) : MathEvaluationResult()
        data class Bitwise(val expression: String, val decimalValue: Long, val hexValue: String, val binaryValue: String) : MathEvaluationResult()
        /** [currencyRates] is the table a currency conversion used; null for other units. */
        data class UnitConversion(
            val fromValue: Double,
            val fromUnit: String,
            val toValue: Double,
            val toUnit: String,
            val formatted: String,
            val currencyRates: CurrencyRates? = null
        ) : MathEvaluationResult()
    }

    /**
     * Evaluates any math expression, bitwise operation, base conversion, or unit conversion.
     * Returns null if the query is not a valid mathematical or conversion expression.
     *
     * [currencyRates] supplies live rates; it is asked only when the query is a currency conversion. A currency
     * it doesn't list is converted with the built-in rates instead.
     */
    fun evaluate(rawQuery: String, currencyRates: () -> CurrencyRates = { CurrencyRates.BUILT_IN }): MathEvaluationResult? {
        val query = withoutQuestionWords(rawQuery)
        if (query.isEmpty()) return null

        // 1. Try Unit Conversion (e.g. "100 km to miles", "72 f in c", "100 usd to eur")
        evaluateUnitConversion(query, currencyRates)?.let { return it }

        // 2. Try Base Conversion (e.g. "255 to hex", "0xff to dec", "15 to bin")
        evaluateBaseConversion(query)?.let { return it }

        // 3. Try Bitwise Expression (e.g. "0xFF AND 0x0F", "1 << 10", "12 XOR 4")
        evaluateBitwise(query)?.let { return it }

        // 4. Try Scientific Expression (e.g. "sin(pi/2) + sqrt(16) * ln(e^3)")
        evaluateScientific(query)?.let { return it }

        return null
    }

    /**
     * [raw] without the words and signs people put around a sum to ask for its answer: "what is 2+2", "what's 15% of
     * 80?", "2+2=", "=2+2". What is left must still be a sum or conversion on its own, so "what is love" stays a search.
     */
    private fun withoutQuestionWords(raw: String): String =
        raw.trim().trimEnd('?').trim().replace(REGEX_QUESTION_PREFIX, "").trim().trim('=').trim()

    /**
     * Whether [word] is a unit or currency the converter knows ("km", "ft2", "euros", "usd"), which a spell checker
     * should leave as typed.
     */
    fun isUnitName(word: String): Boolean {
        val unit = canonicalUnit(word)
        return unit in CURRENCY_NAMES || unit in CurrencyRates.BUILT_IN.perBase || isTemperature(unit) || unitTables(Locale.US).any { unit in it } ||
            unit in volumeUnitsImperial || unit in massUnitsMetricTon
    }

    // ---------------------------------------------------------------------------------------------
    // SCIENTIFIC EXPRESSION PARSER
    // ---------------------------------------------------------------------------------------------

    private val REGEX_QUESTION_PREFIX = Regex("^(?:what(?:'s|’s|s| is)|how much is|calculate|compute)\\s+", RegexOption.IGNORE_CASE)

    private val REGEX_MULTIPLICATION_X = Regex("(?<=[0-9)])(?:(?<!(?<![0-9.])0)\\s*[xX]|\\s+[xX])\\s*(?=[0-9(])")
    private val REGEX_ALLOWED_CHARS = Regex("^[0-9a-zA-Z.+\\-*/%^!()_,]+$")
    private val REGEX_BASE_CONVERSION = Regex(
        "^([0-9a-fA-FxXbBoO]+)\\s+(?:to|in|into)\\s+(hex(?:adecimal)?|dec(?:imal)?|bin(?:ary)?|oct(?:al)?)$",
        RegexOption.IGNORE_CASE
    )
    /** Hexadecimal digits without "0x", which only a letter among them shows aren't decimal: "ff in decimal". */
    private val REGEX_BARE_HEX = Regex("^(?=.*[a-fA-F])[0-9a-fA-F]{1,16}$")
    private val REGEX_BITWISE = Regex("^([0-9a-fA-FxXbB]+)\\s+(AND|OR|XOR|SHL|SHR|USHR|<<|>>|>>>|&|\\|)\\s+([0-9a-fA-FxXbB]+)$", RegexOption.IGNORE_CASE)
    private val REGEX_BITWISE_NOT = Regex("^(?:NOT|~)\\s*([0-9a-fA-FxXbB]+)$", RegexOption.IGNORE_CASE)
    // A unit may end in digits, as "m2", "km2" and "m3" do.
    private val REGEX_UNIT_CONVERSION = Regex(
        "^([-+]?[0-9.,]+(?:[eE][-+]?[0-9]+)?)\\s*([a-zA-Z/_°][a-zA-Z0-9/_°]*)\\s*(?:to|in)\\s*([a-zA-Z/_°][a-zA-Z0-9/_°]*)$",
        RegexOption.IGNORE_CASE
    )
    // Units written in two words, joined into their table names: "fl oz", "sq ft", "square meters", "metric ton".
    private val REGEX_FLUID_OUNCE = Regex("\\b(?:fl\\.?\\s*oz|fluid\\s+ounces?)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_SQUARE_OR_CUBIC_UNIT = Regex(
        "\\b(sq|square|cu|cubic)\\.?\\s+(kilomet(?:er|re)s?|km|miles?|mi|feet|foot|ft|yards?|yd|inch(?:es)?|in|" +
            "centimet(?:er|re)s?|cm|met(?:er|re)s?|m)\\b",
        RegexOption.IGNORE_CASE
    )
    /** "us gallon", "imperial pint", "uk fl oz": a measure named with the system it is from. */
    private val REGEX_VOLUME_SYSTEM = Regex(
        "(?<![a-zA-Z])(us|u\\.s\\.|american|imperial|imp|uk|british)\\s+(gallons?|gal|quarts?|qt|pints?|pt|fl_oz)\\b",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_METRIC_TON = Regex("\\bmetric\\s+tons?\\b", RegexOption.IGNORE_CASE)
    private val REGEX_SHORT_OR_LONG_TON = Regex("\\b(short|long)\\s+tons?\\b", RegexOption.IGNORE_CASE)
    /** Two currencies with no amount, "usd to inr" or "euro to dollar", which asks what one unit is worth. */
    private val REGEX_BARE_CURRENCY_CONVERSION = Regex("^([a-zA-Z]+)\\s+(?:to|in)\\s+([a-zA-Z]+)$")
    private val REGEX_SCIENTIFIC_NUMBER = Regex("^[+-]?[0-9]+(?:\\.[0-9]+)?[eE][+-]?[0-9]+$")
    // A number with only a sign in front ("+14155552671", "-5") has nothing to work out; answering it would
    // echo every international phone number back as a calculation, with a haptic on each keystroke.
    private val REGEX_SIGNED_NUMBER = Regex("^[+-]+[0-9.]+$")
    private val REGEX_SPACE_SEPARATED_NUMBERS = Regex("\\d\\s+\\d")
    // Phone numbers and dates look like subtraction ("555-1234" would show -679). Matched after
    // whitespace is stripped, so these shapes never become a calculator card.
    private val REGEX_PHONE_OR_DATE = Regex(
        "^\\d{3}-\\d{4}$" +                          // 555-1234
            "|^\\+?1?-?\\d{3}-\\d{3}-\\d{4}$" +      // 1-800-555-1234, 800-555-1234
            "|^\\+?1?\\(\\d{3}\\)\\d{3}-?\\d{4}$" +  // (800)555-1234
            "|^\\d{4}-\\d{1,2}-\\d{1,2}$" +          // 2024-01-31
            "|^\\d{1,2}-\\d{1,2}-\\d{2,4}$" +        // 01-31-2024
            "|^\\d{1,2}/\\d{1,2}/\\d{2,4}$" +        // 12/25/2024
            "|^\\d{4}/\\d{1,2}/\\d{1,2}$" +          // 2024/12/25
            "|^\\+\\d{1,3}(?:-\\d{1,4}){2,4}$"      // +44-20-7946-0958
    )
    // Digits joined by dashes and nothing else: a subtraction, or a phone number or ZIP+4 code ("94103-1234").
    private val REGEX_DASHED_DIGITS = Regex("^\\d+(?:-\\d+)+$")
    // Thousands separators ("1,000") between digits, removed only from purely numeric expressions.
    private val REGEX_THOUSANDS_SEPARATOR = Regex("(?<=\\d),(?=\\d{3}(?!\\d))")
    // Where the decimal point is ",", "." groups thousands: "1.000" and "12.345.678", never "0.125" or "1.5".
    private val REGEX_DOT_GROUPED_NUMBER = Regex("(?<![\\d.,])[1-9]\\d{0,2}(?:\\.\\d{3})+(?![\\d.])")
    // "15% of 80" is 15% times 80; "20% off 59.99" is 59.99 less 20%.
    private val REGEX_PERCENT_OF = Regex("%\\s*of\\s+", RegexOption.IGNORE_CASE)
    private val REGEX_PERCENT_OFF = Regex("^(.+?)%\\s*off\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val REGEX_CURRENCY_PREFIX = Regex("([$€£¥₹])\\s*([0-9.,]+)")
    private val REGEX_WHITESPACE = Regex("\\s+")
    // "√16" is the square root of 16; the sign takes only the number right after it, as "sqrt" does.
    private val REGEX_ROOT_OF_NUMBER = Regex("√\\s*([0-9.]+)")
    private val REGEX_FUNC_NO_PAREN = Regex("^(sqrt|cbrt|sin|cos|tan|asin|acos|atan|sinh|cosh|tanh|ln|log|log10|log2|abs|round|floor|ceil|rad|deg)\\s+([+-]?[0-9.]+(?:[eE][-+]?[0-9]+)?)$", RegexOption.IGNORE_CASE)

    private fun evaluateScientific(input: String): MathEvaluationResult.Computation? {
        val trimmed = input.trim().replace(REGEX_ROOT_OF_NUMBER, "sqrt($1)").replace("√", "sqrt")
        val funcMatch = REGEX_FUNC_NO_PAREN.matchEntire(trimmed)
        val normalizedInput = if (funcMatch != null) {
            "${funcMatch.groupValues[1]}(${funcMatch.groupValues[2]})"
        } else {
            trimmed
        }

        val withNormalizedOperators = normalizedInput
            .replace("×", "*")
            .replace("÷", "/")
            .replace(REGEX_MULTIPLICATION_X, "*")
            .replace("π", "pi")
            .replace(REGEX_PERCENT_OFF, "($2)-($1)%")
            .replace(REGEX_PERCENT_OF, "%*")

        if (REGEX_SPACE_SEPARATED_NUMBERS.containsMatchIn(withNormalizedOperators)) return null

        // Numbers inside functions are read the phone's way too: "sqrt(6,25)" where the decimal point is a comma.
        val expr = localNumbers(withNormalizedOperators.replace(" ", ""))

        if (!isLikelyMathExpression(expr)) return null

        return try {
            val parser = ExpressionParser(expr)
            val result = parser.parse()
            if (!result.isFinite()) return null

            val formatted = formatNumber(result)
            MathEvaluationResult.Computation(
                expression = input,
                formattedResult = formatted,
                numericValue = result
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * [text]'s numbers with "." as the decimal point and no separators. A comma is a thousands separator where the
     * decimal point is "." ("1,000") and the decimal point where it is "," ("1,25"), where "1.000" is a thousand.
     */
    private fun localNumbers(text: String): String =
        if (decimalComma()) {
            text.replace(REGEX_DOT_GROUPED_NUMBER) { it.value.replace(".", "") }.replace(',', '.')
        } else {
            text.replace(REGEX_THOUSANDS_SEPARATOR, "")
        }

    /** Whether the phone writes the decimal point as a comma ("1,5"), so answers are written that way too. */
    private fun decimalComma(): Boolean = DecimalFormatSymbols.getInstance().decimalSeparator == ','

    /** A result this much smaller than what it was worked out from is binary rounding left over from a zero. */
    private const val NEAR_ZERO = 1e-12

    /** The significant digits of its larger operand a sum keeps: a double holds about 16, the last of them noise. */
    private const val SUM_DIGITS = 14

    /** [value], or 0 when it is only rounding next to [arg]: sin(pi) is 1.2e-16, cos(pi/2) 6.1e-17. */
    private fun nearZeroIsZero(value: Double, arg: Double): Double = if (abs(value) < NEAR_ZERO * abs(arg)) 0.0 else value

    private fun isLikelyMathExpression(expr: String): Boolean {
        if (expr.isEmpty()) return false
        val lower = expr.lowercase(Locale.ROOT)
        if (REGEX_SCIENTIFIC_NUMBER.matches(lower)) return true
        if (REGEX_SIGNED_NUMBER.matches(lower)) return false
        if (REGEX_PHONE_OR_DATE.matches(lower)) return false
        if (isDashedNumber(lower)) return false

        // Must contain at least one arithmetic operator or parenthesized function call;
        // bare constants ("pi", "e") or function names without arguments are not evaluated as math.
        val hasOperatorOrParens = lower.any { it in "+-*/%^!()" }
        if (!hasOperatorOrParens) return false

        // Check for disallowed characters
        return REGEX_ALLOWED_CHARS.matches(expr)
    }

    /**
     * Whether [expr], digits joined by dashes, reads as a phone number or ZIP+4 code rather than a subtraction:
     * "030-12345678" and "0044-20-7946-0958" (a group starting with 0, as dialing and area codes do), "98765-43210" and
     * "94103-1234" (a long group with 4 or more digits after the dash), "12-3456-7890" (three groups, 7 digits or more).
     * Round amounts are sums ("12000-3500"), as are short ones ("10-3", "2024-1999", "100-20-5").
     */
    private fun isDashedNumber(expr: String): Boolean {
        if (!REGEX_DASHED_DIGITS.matches(expr)) return false
        val groups = expr.split('-')
        if (groups.any { it.length > 1 && it.startsWith('0') }) return true
        if (groups.any { it.endsWith("00") }) return false
        if (groups.size > 2) return groups.sumOf { it.length } >= 7
        val (left, right) = groups[0].length to groups[1].length
        return left >= 3 && right >= 4 && maxOf(left, right) >= 5
    }

    private class ExpressionParser(private val str: String) {
        private var pos = -1
        private var ch = 0
        private var depth = 0
        /** Whether the factor or term just parsed was a bare percentage ("15%"), which "+" and "-" take of the left. */
        private var factorIsPercent = false
        private var termIsPercent = false

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected char: " + ch.toChar())
            return x
        }

        // Expression = Term (+ or - Term)*. Adding or taking a percentage is of what is on its left: 80 + 15% = 92.
        private fun parseExpression(): Double {
            if (++depth > 40) throw IllegalArgumentException("Expression recursion limit exceeded")
            try {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> { val t = parseTerm(); x = sum(x, if (termIsPercent) x * t else t) }
                        eat('-'.code) -> { val t = parseTerm(); x = sum(x, -(if (termIsPercent) x * t else t)) }
                        else -> return x
                    }
                }
            } finally {
                depth--
            }
        }

        /**
         * [a] + [b] to [SUM_DIGITS] significant digits of the larger: decimals aren't exact in binary, and what that
         * leaves (about 1e-16 of the larger) is noise, not answer. So 0.1 + 0.2 - 0.3 is 0, not 5.55e-17, while
         * 1e6 - 999999.9999999 is still 1e-7. Whole numbers add exactly and are left alone.
         */
        private fun sum(a: Double, b: Double): Double {
            val s = a + b
            if ((a % 1.0 == 0.0 && b % 1.0 == 0.0) || s == 0.0 || !s.isFinite()) return s
            val larger = max(abs(a), abs(b))
            val decimals = SUM_DIGITS - 1 - floor(log10(larger)).toInt()
            return BigDecimal(s).setScale(decimals, RoundingMode.HALF_EVEN).toDouble()
        }

        // Term = Factor (*, /, % Factor)*. "%" between two numbers is modulo ("10 % 3" = 1). Side by side is times:
        // "3(4+5)", "(2)(3)", "2pi".
        private fun parseTerm(): Double {
            var x = parseFactor()
            var percent = factorIsPercent
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    ch == '('.code || ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0 || divisor == -0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> {
                        val mod = parseFactor()
                        if (mod == 0.0 || mod == -0.0) throw ArithmeticException("Modulo by zero")
                        x %= mod
                    }
                    else -> {
                        termIsPercent = percent
                        return x
                    }
                }
                percent = false
            }
        }

        /** Whether the "%" at [pos] ends its number ("50%", "15%*80", "(80+15%)") rather than starting a modulo. */
        private fun isPercentSign(): Boolean {
            if (ch != '%'.code) return false
            var next = pos + 1
            while (next < str.length && str[next] == ' ') next++
            return next >= str.length || str[next] in ")+-*/^"
        }

        // Factor = Unary (+, -)* Primary (!)* (^ Factor)?
        private fun parseFactor(): Double {
            if (++depth > 40) throw IllegalArgumentException("Expression recursion limit exceeded")
            try {
                var sign = 1.0
                while (true) {
                    if (eat('+'.code)) continue
                    if (eat('-'.code)) {
                        sign = -sign
                        continue
                    }
                    break
                }

                var x: Double
                val startPos = pos

                if (eat('('.code)) {
                    x = parseExpression()
                    if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis")
                } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                    while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                    // Check for scientific notation (e.g. 1e6, 2.5E-3)
                    if (ch == 'e'.code || ch == 'E'.code) {
                        val mark = pos
                        nextChar()
                        if (ch == '+'.code || ch == '-'.code) nextChar()
                        val expDigitsStart = pos
                        while (ch in '0'.code..'9'.code) nextChar()
                        if (pos == expDigitsStart) {
                            // Incomplete exponent part (e.g. "1e"), backtrack to before 'e'
                            pos = mark - 1
                            nextChar()
                        }
                    }
                    val numStr = str.substring(startPos, pos)
                    x = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
                } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                    while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code || ch in '0'.code..'9'.code) nextChar()
                    val func = str.substring(startPos, pos).lowercase(Locale.ROOT)

                    x = when (func) {
                        "pi" -> Math.PI
                        "e" -> Math.E
                        "phi" -> 1.618033988749895
                        "tau" -> Math.PI * 2.0
                        else -> {
                            // Function call with argument in parentheses: func(arg)
                            if (!eat('('.code)) throw IllegalArgumentException("Expected ( after function $func")
                            val arg = parseExpression()
                            if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis after $func argument")

                            when (func) {
                                "sqrt" -> sqrt(arg)
                                "cbrt" -> cbrt(arg)
                                // pi isn't exact in binary, so sin(pi) comes out as 1.2e-16: a zero.
                                "sin" -> nearZeroIsZero(sin(arg), arg)
                                "cos" -> nearZeroIsZero(cos(arg), arg)
                                // tan(pi/2) is undefined, not the 1.6e16 that cos(pi/2)'s rounding gives.
                                "tan" -> if (nearZeroIsZero(cos(arg), arg) == 0.0) throw ArithmeticException("tan is undefined here")
                                    else nearZeroIsZero(tan(arg), arg)
                                "asin" -> asin(arg)
                                "acos" -> acos(arg)
                                "atan" -> atan(arg)
                                "sinh" -> sinh(arg)
                                "cosh" -> cosh(arg)
                                "tanh" -> tanh(arg)
                                "ln" -> ln(arg)
                                "log", "log10" -> log10(arg)
                                "log2" -> ln(arg) / ln(2.0)
                                "abs" -> abs(arg)
                                // Half away from zero like spreadsheets; kotlin.math.round is half-to-even (2.5 -> 2).
                                "round" -> sign(arg) * floor(abs(arg) + 0.5)
                                "floor" -> floor(arg)
                                "ceil" -> ceil(arg)
                                "rad" -> Math.toRadians(arg)
                                "deg" -> Math.toDegrees(arg)
                                else -> throw IllegalArgumentException("Unknown function: $func")
                            }
                        }
                    }
                } else {
                    throw IllegalArgumentException("Unexpected char: " + ch.toChar())
                }

                // Factorial operator has higher binding precedence than exponentiation: e.g. 3!^2 = 6^2 = 36
                while (eat('!'.code)) {
                    val n = x.toLong()
                    if (x < 0 || x != n.toDouble() || n > 20) throw IllegalArgumentException("Factorial out of range")
                    var fact = 1L
                    for (i in 2..n) fact *= i
                    x = fact.toDouble()
                }

                // A percentage: "50%" is 0.5.
                var percent = false
                if (isPercentSign()) {
                    nextChar()
                    x /= 100.0
                    percent = true
                }

                // Exponentiation
                if (eat('^'.code)) {
                    x = x.pow(parseFactor())
                    percent = false
                }

                factorIsPercent = percent
                return sign * x
            } finally {
                depth--
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // BITWISE & BASE CONVERSION EVALUATOR
    // ---------------------------------------------------------------------------------------------

    private fun evaluateBaseConversion(query: String): MathEvaluationResult.Bitwise? {
        val match = REGEX_BASE_CONVERSION.find(query.trim()) ?: return null

        val rawNum = match.groupValues[1]
        // "binary", "hexadecimal": the full words name the same bases as "bin" and "hex".
        val targetBase = match.groupValues[2].lowercase(Locale.ROOT).take(3)

        val parsedVal = parseAnyRadix(rawNum)
            ?: rawNum.takeIf { REGEX_BARE_HEX.matches(it) }?.let { java.lang.Long.parseUnsignedLong(it, 16) }
            ?: return null

        val formattedHex = "0x" + java.lang.Long.toHexString(parsedVal).uppercase()
        val formattedBin = "0b" + java.lang.Long.toBinaryString(parsedVal)

        val targetResult = when (targetBase) {
            "hex" -> formattedHex
            "bin" -> formattedBin
            "oct" -> "0o" + java.lang.Long.toOctalString(parsedVal)
            "dec" -> java.lang.Long.toUnsignedString(parsedVal)
            else -> return null
        }

        return MathEvaluationResult.Bitwise(
            expression = "$rawNum to $targetBase = $targetResult",
            decimalValue = parsedVal,
            hexValue = formattedHex,
            binaryValue = formattedBin
        )
    }

    private fun evaluateBitwise(query: String): MathEvaluationResult.Bitwise? {
        val notMatch = REGEX_BITWISE_NOT.find(query.trim())
        if (notMatch != null) {
            val operand = parseAnyRadix(notMatch.groupValues[1]) ?: return null
            val resultVal = operand.inv()
            val formattedHex = "0x" + java.lang.Long.toHexString(resultVal).uppercase()
            val formattedBin = "0b" + java.lang.Long.toBinaryString(resultVal)
            return MathEvaluationResult.Bitwise(
                expression = "$query = $resultVal",
                decimalValue = resultVal,
                hexValue = formattedHex,
                binaryValue = formattedBin
            )
        }

        val match = REGEX_BITWISE.find(query.trim()) ?: return null

        val op = match.groupValues[2].uppercase(Locale.ROOT)
        // "10 or 12" and "1 and 2" are words, not bit operations; the words count only beside a 0x, 0b or 0o number.
        if ((op == "AND" || op == "OR") && !hasRadixPrefix(match.groupValues[1]) && !hasRadixPrefix(match.groupValues[3])) return null
        val left = parseAnyRadix(match.groupValues[1]) ?: return null
        val right = parseAnyRadix(match.groupValues[3]) ?: return null

        val resultVal = when (op) {
            "AND", "&" -> left and right
            "OR", "|" -> left or right
            "XOR", "^" -> left xor right
            // Kotlin masks shift distances to 6 bits ("1 << 64" would give 1); out-of-range shifts aren't answered.
            "SHL", "<<" -> if (right in 0..63) left shl right.toInt() else return null
            "SHR", ">>" -> if (right in 0..63) left shr right.toInt() else return null
            "USHR", ">>>" -> if (right in 0..63) left ushr right.toInt() else return null
            else -> return null
        }

        val displayVal = if (resultVal >= 0) "$resultVal" else "${java.lang.Long.toUnsignedString(resultVal)} (signed: $resultVal)"
        return MathEvaluationResult.Bitwise(
            expression = "$query = $displayVal",
            decimalValue = resultVal,
            hexValue = "0x" + java.lang.Long.toHexString(resultVal).uppercase(),
            binaryValue = "0b" + java.lang.Long.toBinaryString(resultVal)
        )
    }

    private fun hasRadixPrefix(str: String): Boolean =
        str.length > 2 && str[0] == '0' && str[1].lowercaseChar() in "xbo"

    private fun parseAnyRadix(str: String): Long? {
        val trimmed = str.trim()
        return try {
            when {
                trimmed.startsWith("0x", ignoreCase = true) -> java.lang.Long.parseUnsignedLong(trimmed.substring(2), 16)
                trimmed.startsWith("0b", ignoreCase = true) -> java.lang.Long.parseUnsignedLong(trimmed.substring(2), 2)
                trimmed.startsWith("0o", ignoreCase = true) -> java.lang.Long.parseUnsignedLong(trimmed.substring(2), 8)
                else -> java.lang.Long.parseUnsignedLong(trimmed, 10)
            }
        } catch (e: Exception) {
            try {
                trimmed.toLong(10)
            } catch (_: Exception) {
                null
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // EXTENDED UNIT & CURRENCY CONVERTER
    // ---------------------------------------------------------------------------------------------

    private val lengthUnits = mapOf(
        "m" to 1.0, "meter" to 1.0, "meters" to 1.0,
        "km" to 1000.0, "kilometer" to 1000.0, "kilometers" to 1000.0,
        "cm" to 0.01, "centimeter" to 0.01, "centimeters" to 0.01,
        "mm" to 0.001, "millimeter" to 0.001, "millimeters" to 0.001,
        "um" to 1e-6, "nm" to 1e-9,
        "mi" to 1609.344, "mile" to 1609.344, "miles" to 1609.344,
        "yd" to 0.9144, "yard" to 0.9144, "yards" to 0.9144,
        "ft" to 0.3048, "foot" to 0.3048, "feet" to 0.3048,
        "in" to 0.0254, "inch" to 0.0254, "inches" to 0.0254,
        "nmi" to 1852.0, "nauticalmile" to 1852.0
    )

    private val massUnits = mapOf(
        "kg" to 1.0, "kilogram" to 1.0, "kilograms" to 1.0,
        "g" to 0.001, "gram" to 0.001, "grams" to 0.001,
        "mg" to 1e-6, "milligram" to 1e-6,
        "lb" to 0.45359237, "lbs" to 0.45359237, "pound" to 0.45359237, "pounds" to 0.45359237,
        "oz" to 0.028349523, "ounce" to 0.028349523, "ounces" to 0.028349523,
        "tonne" to 1000.0, "tonnes" to 1000.0, "t" to 1000.0,
        "short_ton" to 907.18474, "long_ton" to 1016.0469088,
        "st" to 6.35029, "stone" to 6.35029
    )

    // A "ton" is the 2,000 lb short ton in the US and the metric tonne elsewhere.
    private val massUnitsMetricTon = massUnits + mapOf("ton" to 1000.0, "tons" to 1000.0)
    private val massUnitsShortTon = massUnits + mapOf("ton" to 907.18474, "tons" to 907.18474)

    private val speedUnits = mapOf(
        "m/s" to 1.0, "mps" to 1.0,
        "km/h" to 0.27777778, "kmh" to 0.27777778, "kph" to 0.27777778,
        "mph" to 0.44704, "mi/h" to 0.44704,
        "knot" to 0.514444, "knots" to 0.514444,
        "ft/s" to 0.3048, "fps" to 0.3048
    )

    private val digitalUnits = mapOf(
        "b" to 1.0 / 8.0, "bit" to 1.0 / 8.0, "bits" to 1.0 / 8.0,
        "byte" to 1.0, "bytes" to 1.0,
        "kb" to 1000.0, "kilobyte" to 1000.0, "kilobytes" to 1000.0,
        "mb" to 1e6, "megabyte" to 1e6, "megabytes" to 1e6,
        "gb" to 1e9, "gigabyte" to 1e9, "gigabytes" to 1e9,
        "tb" to 1e12, "terabyte" to 1e12, "terabytes" to 1e12,
        "kib" to 1024.0, "mib" to 1048576.0, "gib" to 1073741824.0, "tib" to 1099511627776.0,
        "kbit" to 125.0, "kilobit" to 125.0, "kilobits" to 125.0,
        "mbit" to 1.25e5, "megabit" to 1.25e5, "megabits" to 1.25e5,
        "gbit" to 1.25e8, "gigabit" to 1.25e8, "gigabits" to 1.25e8,
        "tbit" to 1.25e11, "terabit" to 1.25e11, "terabits" to 1.25e11
    )

    private val volumeUnits = mapOf(
        "l" to 1.0, "liter" to 1.0, "liters" to 1.0, "litre" to 1.0, "litres" to 1.0,
        "ml" to 0.001, "milliliter" to 0.001, "milliliters" to 0.001,
        "gal" to 3.78541, "gallon" to 3.78541, "gallons" to 3.78541,
        "qt" to 0.946353, "quart" to 0.946353, "quarts" to 0.946353,
        "pt" to 0.473176, "pint" to 0.473176, "pints" to 0.473176,
        "cup" to 0.236588, "cups" to 0.236588,
        "fl_oz" to 0.0295735, "floz" to 0.0295735,
        "tbsp" to 0.0147868, "tablespoon" to 0.0147868, "tablespoons" to 0.0147868,
        "tsp" to 0.00492892, "teaspoon" to 0.00492892, "teaspoons" to 0.00492892,
        "m3" to 1000.0, "cu_m" to 1000.0, "cm3" to 0.001, "cu_cm" to 0.001, "cc" to 0.001,
        "ft3" to 28.316846592, "cu_ft" to 28.316846592, "in3" to 0.016387064, "cu_in" to 0.016387064,
        // Named with their system ("us gallon", "imperial pint"), these are the same anywhere.
        "us_gal" to 3.78541, "us_qt" to 0.946353, "us_pt" to 0.473176, "us_fl_oz" to 0.0295735,
        "imp_gal" to 4.54609, "imp_qt" to 1.1365225, "imp_pt" to 0.56826125, "imp_fl_oz" to 0.0284130625
    )

    /** Where a plain gallon, quart or pint is the imperial one: the UK and Ireland. */
    private val imperialVolumeCountries = setOf("GB", "IE")
    private val volumeUnitsImperial = volumeUnits + listOf("gal", "gallon", "gallons").associateWith { 4.54609 } +
        listOf("qt", "quart", "quarts").associateWith { 1.1365225 } + listOf("pt", "pint", "pints").associateWith { 0.56826125 }

    /** How the names [REGEX_VOLUME_SYSTEM] reads are written in the tables: "gallons" is "gal". */
    private val volumeSystemUnits = mapOf(
        "gallon" to "gal", "gallons" to "gal", "gal" to "gal", "quart" to "qt", "quarts" to "qt", "qt" to "qt",
        "pint" to "pt", "pints" to "pt", "pt" to "pt", "fl_oz" to "fl_oz"
    )

    /** How the tables' system-named measures are shown: "us_gal" is "US gal". */
    private fun unitLabel(unit: String): String = when {
        unit.startsWith("us_") -> "US " + unit.removePrefix("us_").replace('_', ' ')
        unit.startsWith("imp_") -> "imp " + unit.removePrefix("imp_").replace('_', ' ')
        // Joined names read as written: "fl oz", "sq ft".
        else -> unit.replace('_', ' ')
    }

    /** The short names of the lengths [REGEX_SQUARE_OR_CUBIC_UNIT] reads, for "sq_ft" and "cu_m". */
    private fun shortLength(name: String): String = when {
        name.startsWith("kilomet") -> "km"
        name.startsWith("mile") -> "mi"
        name == "feet" || name == "foot" -> "ft"
        name.startsWith("yard") -> "yd"
        name.startsWith("inch") -> "in"
        name.startsWith("centimet") -> "cm"
        name.startsWith("met") -> "m"
        else -> name
    }

    /** Ounces, which mean fluid ounces when converted to or from a volume ("8 oz to ml"). */
    private val ounceUnits = setOf("oz", "ounce", "ounces")

    private val pressureUnits = mapOf(
        "pa" to 1.0, "pascal" to 1.0, "pascals" to 1.0,
        "hpa" to 100.0, "kpa" to 1000.0, "mpa" to 1e6,
        "bar" to 1e5, "bars" to 1e5, "mbar" to 100.0,
        "psi" to 6894.757293168,
        "atm" to 101325.0,
        "mmhg" to 133.322387415, "inhg" to 3386.389
    )

    private val energyUnits = mapOf(
        "j" to 1.0, "joule" to 1.0, "joules" to 1.0,
        "kj" to 1000.0, "kilojoule" to 1000.0, "kilojoules" to 1000.0,
        "cal" to 4.184, "calorie" to 4.184, "calories" to 4.184,
        "kcal" to 4184.0, "kilocalorie" to 4184.0, "kilocalories" to 4184.0,
        "wh" to 3600.0, "kwh" to 3.6e6,
        "btu" to 1055.05585
    )

    private val areaUnits = mapOf(
        "m2" to 1.0, "sqm" to 1.0, "sq_m" to 1.0,
        "km2" to 1e6, "sqkm" to 1e6, "sq_km" to 1e6,
        "ft2" to 0.092903, "sqft" to 0.092903, "sq_ft" to 0.092903,
        "mi2" to 2.58999e6, "sqmi" to 2.58999e6, "sq_mi" to 2.58999e6,
        "yd2" to 0.83612736, "sq_yd" to 0.83612736, "in2" to 0.00064516, "sq_in" to 0.00064516,
        "cm2" to 1e-4, "sq_cm" to 1e-4,
        "acre" to 4046.86, "acres" to 4046.86,
        "ha" to 10000.0, "hectare" to 10000.0, "hectares" to 10000.0
    )

    // A year is the calendar's 365 days and a month a twelfth of it, 30.4167 days, as calculators and Google answer;
    // the mean Gregorian year (365.2425 days) made "1 year to days" look wrong.
    private val timeUnits = mapOf(
        "ms" to 0.001, "millisecond" to 0.001, "milliseconds" to 0.001,
        "s" to 1.0, "sec" to 1.0, "second" to 1.0, "seconds" to 1.0,
        "min" to 60.0, "minute" to 60.0, "minutes" to 60.0,
        "hr" to 3600.0, "h" to 3600.0, "hour" to 3600.0, "hours" to 3600.0,
        "d" to 86400.0, "day" to 86400.0, "days" to 86400.0,
        "wk" to 604800.0, "week" to 604800.0, "weeks" to 604800.0,
        "mo" to 2628000.0, "month" to 2628000.0, "months" to 2628000.0,
        "yr" to 31536000.0, "year" to 31536000.0, "years" to 31536000.0
    )

    /** The unit tables for [locale]: what a plain "ton" and a plain "gallon" are depends on the country. */
    private fun unitTables(locale: Locale): List<Map<String, Double>> {
        val massTable = if (locale.country == "US") massUnitsShortTon else massUnitsMetricTon
        val volumeTable = if (locale.country in imperialVolumeCountries) volumeUnitsImperial else volumeUnits
        return listOf(lengthUnits, massTable, speedUnits, digitalUnits, volumeTable, areaUnits, timeUnits, pressureUnits, energyUnits)
    }

    /**
     * Lower-cases a unit, keeping the case distinctions that matter for data sizes: "B" is a byte, and an
     * upper-case prefix with a lower-case b ("Mb", "Gb") is bits, while "MB" and the casual "mb" are bytes.
     */
    private fun canonicalUnit(raw: String): String = when {
        raw == "B" -> "byte"
        raw.length == 2 && raw[1] == 'b' && raw[0].isUpperCase() && raw[0].lowercaseChar() in "kmgt" ->
            "${raw[0].lowercaseChar()}bit"
        else -> raw.lowercase(Locale.ROOT).replace("°", "")
    }

    private fun evaluateUnitConversion(query: String, currencyRates: () -> CurrencyRates): MathEvaluationResult.UnitConversion? {
        // Shift prefix currency symbols (e.g. "$100 to eur" -> "100 $ to eur")
        val prefixShifted = query.trim()
            .replace(REGEX_CURRENCY_PREFIX, "$2 $1")

        val normalizedQuery = prefixShifted
            .replace("$", " usd ")
            .replace("€", " eur ")
            .replace("£", " gbp ")
            .replace("¥", " jpy ")
            .replace("₹", " inr ")
            .replace(REGEX_WHITESPACE, " ")
            .replace('²', '2')
            .replace('³', '3')
            .replace(REGEX_CURRENCY_PHRASE) { phrase ->
                val name = phrase.value.lowercase(Locale.ROOT).replace(REGEX_WHITESPACE, " ")
                CURRENCY_PHRASES[name] ?: CURRENCY_PHRASES[name.removeSuffix("s")] ?: phrase.value
            }
            .replace(REGEX_FLUID_OUNCE, "fl_oz")
            .replace(REGEX_SQUARE_OR_CUBIC_UNIT) { unit ->
                val prefix = if (unit.groupValues[1].lowercase(Locale.ROOT).startsWith("s")) "sq_" else "cu_"
                prefix + shortLength(unit.groupValues[2].lowercase(Locale.ROOT))
            }
            .replace(REGEX_VOLUME_SYSTEM) { unit ->
                val system = unit.groupValues[1].lowercase(Locale.ROOT)
                val prefix = if (system == "us" || system == "u.s." || system == "american") "us_" else "imp_"
                prefix + volumeSystemUnits.getValue(unit.groupValues[2].lowercase(Locale.ROOT))
            }
            .replace(REGEX_METRIC_TON, "tonne")
            .replace(REGEX_SHORT_OR_LONG_TON, "$1_ton")
            .trim()

        val match = REGEX_UNIT_CONVERSION.find(normalizedQuery)
            ?: return REGEX_BARE_CURRENCY_CONVERSION.find(normalizedQuery)?.let { bare ->
                val (from, to) = bare.destructured
                convertCurrency(1.0, from.lowercase(Locale.ROOT), to.lowercase(Locale.ROOT), currencyRates)
            }

        val value = localNumbers(match.groupValues[1]).toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        val fromRaw = match.groupValues[2]
        val toRaw = match.groupValues[3]
        var fromUnit = canonicalUnit(fromRaw)
        var toUnit = canonicalUnit(toRaw)
        if (fromUnit in ounceUnits && toUnit in volumeUnits) fromUnit = "fl_oz"
        if (toUnit in ounceUnits && fromUnit in volumeUnits) toUnit = "fl_oz"

        // 1. Temperature Check
        if (isTemperature(fromUnit) && isTemperature(toUnit)) {
            val converted = convertTemperature(value, fromUnit, toUnit)
            if (!converted.isFinite()) return null
            return MathEvaluationResult.UnitConversion(
                fromValue = value,
                fromUnit = fromUnit.uppercase(Locale.ROOT),
                toValue = converted,
                toUnit = toUnit.uppercase(Locale.ROOT),
                formatted = "${formatNumber(value)} ${fromUnit.uppercase(Locale.ROOT)} = " +
                    "${formatConverted(converted)} ${toUnit.uppercase(Locale.ROOT)}"
            )
        }

        // 2. Standard Unit Tables
        for (table in unitTables(Locale.getDefault())) {
            if (table.containsKey(fromUnit) && table.containsKey(toUnit)) {
                val base = value * table[fromUnit]!!
                val result = base / table[toUnit]!!
                if (!result.isFinite()) return null
                val fromLabel = unitLabel(fromUnit)
                val toLabel = unitLabel(toUnit)
                return MathEvaluationResult.UnitConversion(
                    fromValue = value,
                    fromUnit = fromLabel,
                    toValue = result,
                    toUnit = toLabel,
                    formatted = "${formatNumber(value)} $fromLabel = ${formatConverted(result)} $toLabel"
                )
            }
        }

        // 3. Currency Table
        return convertCurrency(value, fromUnit, toUnit, currencyRates)
    }

    /**
     * A currency conversion, with the live rates when they list both currencies, else the built-in ones. Either may be
     * a code ("eur") or a name ("euros", "dollar", "yen").
     */
    private fun convertCurrency(
        value: Double,
        fromName: String,
        toName: String,
        currencyRates: () -> CurrencyRates
    ): MathEvaluationResult.UnitConversion? {
        val fromUnit = CURRENCY_NAMES[fromName] ?: fromName.takeIf(::isCurrencyCode) ?: return null
        val toUnit = CURRENCY_NAMES[toName] ?: toName.takeIf(::isCurrencyCode) ?: return null
        val live = currencyRates()
        val rates = if (fromUnit in live.perBase && toUnit in live.perBase) live else CurrencyRates.BUILT_IN
        val result = rates.convert(value, fromUnit, toUnit) ?: return null
        if (!result.isFinite()) return null
        val from = fromUnit.uppercase(Locale.ROOT)
        val to = toUnit.uppercase(Locale.ROOT)
        return MathEvaluationResult.UnitConversion(
            fromValue = value,
            fromUnit = from,
            toValue = result,
            toUnit = to,
            // Daily reference rates at best, so the result is an estimate rather than a dealing quote.
            formatted = "${money(value, from, wholeAsTyped = true)} $from ≈ ${money(result, to, wholeAsTyped = false)} $to",
            currencyRates = rates
        )
    }

    /**
     * [value] written as money in [code]: grouped the phone's way, in the digits 0 to 9 like every answer, with the
     * currency's own decimals (2 for most, 0 for the yen and the won). An amount the user typed without decimals keeps
     * none when [wholeAsTyped]: "100 USD", not "100.00 USD".
     */
    private fun money(value: Double, code: String, wholeAsTyped: Boolean): String {
        val decimals = if (wholeAsTyped && value % 1.0 == 0.0) 0 else currencyDecimals(code)
        val pattern = "#,##0" + if (decimals > 0) "." + "0".repeat(decimals) else ""
        return DecimalFormat(pattern, latinSymbols()).apply { roundingMode = RoundingMode.HALF_UP }.format(value)
    }

    /** The decimals [code] is written with: 2 for most currencies, 0 for JPY and KRW; 2 when the phone doesn't know it. */
    private fun currencyDecimals(code: String): Int =
        try {
            Currency.getInstance(code).defaultFractionDigits.takeIf { it >= 0 } ?: 2
        } catch (_: IllegalArgumentException) {
            2
        }

    /** The phone's number symbols as written with the digits 0 to 9 (Arabic's with Latin digits use "." and ","). */
    private fun latinSymbols(): DecimalFormatSymbols {
        val locale = Locale.getDefault()
        val latin = try {
            Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
        } catch (_: IllformedLocaleException) {
            locale
        }
        return DecimalFormatSymbols.getInstance(latin).apply { zeroDigit = '0' }
    }

    /** Three letters, the shape of an ISO 4217 code; nothing else is looked up in the rate tables. */
    private fun isCurrencyCode(u: String) = u.length == 3 && u.all { it in 'a'..'z' }

    /** Currencies by the names people type for them ("50 euros in dollars"), each its ISO 4217 code. */
    private val CURRENCY_NAMES: Map<String, String> = buildMap {
        fun names(code: String, vararg words: String) = words.forEach { put(it, code) }
        names("usd", "dollar", "dollars", "buck", "bucks")
        names("eur", "euro", "euros")
        names("gbp", "pound", "pounds", "quid", "sterling")
        names("jpy", "yen")
        names("cny", "yuan", "renminbi", "rmb")
        names("inr", "rupee", "rupees")
        names("krw", "won")
        names("mxn", "peso", "pesos")
        names("brl", "real", "reais")
        names("chf", "franc", "francs")
        names("rub", "ruble", "rubles", "rouble", "roubles")
        names("try", "lira", "liras")
        names("thb", "baht")
        names("pln", "zloty", "zlotys")
        names("ils", "shekel", "shekels")
        names("zar", "rand")
        names("aed", "dirham", "dirhams")
        names("sar", "riyal", "riyals")
        names("sek", "krona", "kronor")
        names("nok", "krone", "kroner")
        names("huf", "forint", "forints")
        names("czk", "koruna")
        names("myr", "ringgit")
        names("idr", "rupiah")
    }

    /** Currencies named in more than one word, read before the units are: "canadian dollars" is "cad". */
    private val CURRENCY_PHRASES: Map<String, String> = mapOf(
        "us dollar" to "usd", "american dollar" to "usd", "canadian dollar" to "cad", "australian dollar" to "aud",
        "new zealand dollar" to "nzd", "singapore dollar" to "sgd", "hong kong dollar" to "hkd",
        "mexican peso" to "mxn", "swiss franc" to "chf", "british pound" to "gbp", "pound sterling" to "gbp",
        "pounds sterling" to "gbp", "indian rupee" to "inr", "japanese yen" to "jpy", "chinese yuan" to "cny",
        "south african rand" to "zar", "brazilian real" to "brl", "brazilian reais" to "brl",
        "swedish krona" to "sek", "swedish kronor" to "sek", "norwegian krone" to "nok", "norwegian kroner" to "nok",
        "danish krone" to "dkk", "danish kroner" to "dkk", "turkish lira" to "try", "russian ruble" to "rub",
        "polish zloty" to "pln", "south korean won" to "krw", "korean won" to "krw"
    )
    private val REGEX_CURRENCY_PHRASE = Regex(
        "(?<![a-zA-Z])(?:" + CURRENCY_PHRASES.keys.sortedByDescending { it.length }.joinToString("|") { it.replace(" ", "\\s+") } + ")s?\\b",
        RegexOption.IGNORE_CASE
    )

    private fun isTemperature(u: String) = u in listOf("c", "celsius", "f", "fahrenheit", "k", "kelvin")

    private fun convertTemperature(v: Double, from: String, to: String): Double {
        val c = when (from) {
            "f", "fahrenheit" -> (v - 32.0) * 5.0 / 9.0
            "k", "kelvin" -> v - 273.15
            else -> v
        }
        return when (to) {
            "f", "fahrenheit" -> c * 9.0 / 5.0 + 32.0
            "k", "kelvin" -> c + 273.15
            else -> c
        }
    }

    /**
     * Significant digits a calculation is written with: enough that 1.00001 isn't shown as 1 and 1/3 reads as
     * 0.3333333333, few enough that binary rounding (0.30000000000000004) never shows.
     */
    private const val CALCULATION_DIGITS = 10

    /**
     * Significant digits a unit conversion is written with: its factors are mostly given to about 6, so more would show
     * their rounding ("1 gal to qt" as 3.999998), and 1 month is 30.4167 days as Google writes it.
     */
    private const val CONVERSION_DIGITS = 6

    /** A converted amount, to [CONVERSION_DIGITS] and no decimals past them however large: "1000 gal" is 3785.41 l. */
    private fun formatConverted(value: Double): String = formatNumber(value, CONVERSION_DIGITS, decimalsForLarge = 0)

    /** Whole numbers below 2^63 are written out in full: 2^50 is 1125899906842624, not 1.1259e+15. */
    private const val LARGEST_WRITTEN_OUT = 9.223372036854775807E18

    /**
     * [value] to [significantDigits], with trailing zeros dropped, the phone's decimal comma where it uses one ("2,5"),
     * and scientific notation only below 0.0001 and from 10^15 for what isn't a whole number. A number with more whole
     * digits than that keeps them all, and up to [decimalsForLarge] decimals as well (15 digits at most, about all a
     * double holds): 1234567.891234 is 1234567.8912, not 1234567.891.
     */
    private fun formatNumber(value: Double, significantDigits: Int = CALCULATION_DIGITS, decimalsForLarge: Int = 4): String {
        if (!value.isFinite() || value == 0.0) return "0"
        val absVal = abs(value)
        val written = if (value % 1.0 == 0.0 && absVal < LARGEST_WRITTEN_OUT) {
            value.toLong().toString()
        } else if (absVal < 1e-4 || absVal >= 1e15) {
            String.format(Locale.ROOT, "%.4e", value)
        } else {
            val wholeDigits = floor(log10(absVal)).toInt() + 1
            val digits = max(significantDigits, min(wholeDigits + decimalsForLarge, MAX_DIGITS))
            BigDecimal(value).round(MathContext(digits, RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString()
        }
        return if (decimalComma()) written.replace('.', ',') else written
    }

    /** The most significant digits any answer is written with: a double holds about 15 exactly. */
    private const val MAX_DIGITS = 15
}
