package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.currency.CurrencyRates
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
        val query = rawQuery.trim()
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

    // ---------------------------------------------------------------------------------------------
    // SCIENTIFIC EXPRESSION PARSER
    // ---------------------------------------------------------------------------------------------

    private val REGEX_MULTIPLICATION_X = Regex("(?<=[0-9)])(?:(?<!(?<![0-9.])0)\\s*[xX]|\\s+[xX])\\s*(?=[0-9(])")
    private val REGEX_ALLOWED_CHARS = Regex("^[0-9a-zA-Z.+\\-*/%^!()_,]+$")
    private val REGEX_BASE_CONVERSION = Regex("^([0-9a-fA-FxXbBoO]+)\\s+(?:to|in)\\s+(hex|dec|bin|oct)$", RegexOption.IGNORE_CASE)
    private val REGEX_BITWISE = Regex("^([0-9a-fA-FxXbB]+)\\s+(AND|OR|XOR|SHL|SHR|USHR|<<|>>|>>>|&|\\|)\\s+([0-9a-fA-FxXbB]+)$", RegexOption.IGNORE_CASE)
    private val REGEX_BITWISE_NOT = Regex("^(?:NOT|~)\\s*([0-9a-fA-FxXbB]+)$", RegexOption.IGNORE_CASE)
    private val REGEX_UNIT_CONVERSION = Regex("^([-+]?[0-9.]+(?:[eE][-+]?[0-9]+)?)\\s*([a-zA-Z/_°]+)\\s*(?:to|in)\\s*([a-zA-Z/_°]+)$", RegexOption.IGNORE_CASE)
    /** Two currency codes with no amount, "usd to inr", which asks what one unit is worth. */
    private val REGEX_BARE_CURRENCY_CONVERSION = Regex("^([a-zA-Z]{3})\\s+(?:to|in)\\s+([a-zA-Z]{3})$")
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
    // Thousands separators ("1,000") between digits, removed only from purely numeric expressions.
    private val REGEX_THOUSANDS_SEPARATOR = Regex("(?<=\\d),(?=\\d{3}(?!\\d))")
    private val REGEX_CURRENCY_PREFIX = Regex("([$€£¥₹])\\s*([0-9.]+)")
    private val REGEX_WHITESPACE = Regex("\\s+")
    private val REGEX_FUNC_NO_PAREN = Regex("^(sqrt|cbrt|sin|cos|tan|asin|acos|atan|sinh|cosh|tanh|ln|log|log10|log2|abs|round|floor|ceil|rad|deg)\\s+([+-]?[0-9.]+(?:[eE][-+]?[0-9]+)?)$", RegexOption.IGNORE_CASE)

    private fun evaluateScientific(input: String): MathEvaluationResult.Computation? {
        val trimmed = input.trim()
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

        if (REGEX_SPACE_SEPARATED_NUMBERS.containsMatchIn(withNormalizedOperators)) return null

        val compact = withNormalizedOperators.replace(" ", "")
        // A comma is a thousands separator where the decimal point is "." ("1,000") and the decimal point where it
        // is "," ("1,25"), as the user's locale writes numbers.
        val commaIsDecimal = java.text.DecimalFormatSymbols.getInstance().decimalSeparator == ','
        val expr = when {
            compact.any { it.isLetter() } -> compact
            commaIsDecimal -> compact.replace(',', '.')
            else -> compact.replace(REGEX_THOUSANDS_SEPARATOR, "")
        }

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

    private fun isLikelyMathExpression(expr: String): Boolean {
        if (expr.isEmpty()) return false
        val lower = expr.lowercase(Locale.ROOT)
        if (REGEX_SCIENTIFIC_NUMBER.matches(lower)) return true
        if (REGEX_SIGNED_NUMBER.matches(lower)) return false
        if (REGEX_PHONE_OR_DATE.matches(lower)) return false

        // Must contain at least one arithmetic operator or parenthesized function call;
        // bare constants ("pi", "e") or function names without arguments are not evaluated as math.
        val hasOperatorOrParens = lower.any { it in "+-*/%^!()" }
        if (!hasOperatorOrParens) return false

        // Check for disallowed characters
        return REGEX_ALLOWED_CHARS.matches(expr)
    }

    private class ExpressionParser(private val str: String) {
        private var pos = -1
        private var ch = 0
        private var depth = 0

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

        // Expression = Term (+ or - Term)*
        private fun parseExpression(): Double {
            if (++depth > 40) throw IllegalArgumentException("Expression recursion limit exceeded")
            try {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            } finally {
                depth--
            }
        }

        // Term = Factor (*, /, % Factor)*
        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
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
                    else -> return x
                }
            }
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
                                "sin" -> sin(arg)
                                "cos" -> cos(arg)
                                "tan" -> tan(arg)
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

                // Exponentiation
                if (eat('^'.code)) {
                    x = x.pow(parseFactor())
                }

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
        val targetBase = match.groupValues[2].lowercase()

        val parsedVal = parseAnyRadix(rawNum) ?: return null

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

        val left = parseAnyRadix(match.groupValues[1]) ?: return null
        val op = match.groupValues[2].uppercase()
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
        "ton" to 1000.0, "tons" to 1000.0, "t" to 1000.0,
        "st" to 6.35029, "stone" to 6.35029
    )

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
        "m3" to 1000.0
    )

    private val areaUnits = mapOf(
        "m2" to 1.0, "sqm" to 1.0, "sq_m" to 1.0,
        "km2" to 1e6, "sqkm" to 1e6, "sq_km" to 1e6,
        "ft2" to 0.092903, "sqft" to 0.092903, "sq_ft" to 0.092903,
        "mi2" to 2.58999e6, "sqmi" to 2.58999e6, "sq_mi" to 2.58999e6,
        "acre" to 4046.86, "acres" to 4046.86,
        "ha" to 10000.0, "hectare" to 10000.0, "hectares" to 10000.0
    )

    private val timeUnits = mapOf(
        "ms" to 0.001, "millisecond" to 0.001, "milliseconds" to 0.001,
        "s" to 1.0, "sec" to 1.0, "second" to 1.0, "seconds" to 1.0,
        "min" to 60.0, "minute" to 60.0, "minutes" to 60.0,
        "hr" to 3600.0, "h" to 3600.0, "hour" to 3600.0, "hours" to 3600.0,
        "d" to 86400.0, "day" to 86400.0, "days" to 86400.0,
        "wk" to 604800.0, "week" to 604800.0, "weeks" to 604800.0,
        "mo" to 2629746.0, "month" to 2629746.0, "months" to 2629746.0,
        "yr" to 31556952.0, "year" to 31556952.0, "years" to 31556952.0
    )

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
            .trim()

        val match = REGEX_UNIT_CONVERSION.find(normalizedQuery)
            ?: return REGEX_BARE_CURRENCY_CONVERSION.find(normalizedQuery)?.let { bare ->
                val (from, to) = bare.destructured
                convertCurrency(1.0, from.lowercase(Locale.ROOT), to.lowercase(Locale.ROOT), currencyRates)
            }

        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        val fromRaw = match.groupValues[2]
        val toRaw = match.groupValues[3]
        val fromUnit = canonicalUnit(fromRaw)
        val toUnit = canonicalUnit(toRaw)

        // 1. Temperature Check
        if (isTemperature(fromUnit) && isTemperature(toUnit)) {
            val converted = convertTemperature(value, fromUnit, toUnit)
            if (!converted.isFinite()) return null
            return MathEvaluationResult.UnitConversion(
                fromValue = value,
                fromUnit = fromUnit.uppercase(Locale.ROOT),
                toValue = converted,
                toUnit = toUnit.uppercase(Locale.ROOT),
                formatted = "${formatNumber(value)} ${fromUnit.uppercase(Locale.ROOT)} = ${formatNumber(converted)} ${toUnit.uppercase(Locale.ROOT)}"
            )
        }

        // 2. Standard Unit Tables
        val unitTables = listOf(lengthUnits, massUnits, speedUnits, digitalUnits, volumeUnits, areaUnits, timeUnits)
        for (table in unitTables) {
            if (table.containsKey(fromUnit) && table.containsKey(toUnit)) {
                val base = value * table[fromUnit]!!
                val result = base / table[toUnit]!!
                if (!result.isFinite()) return null
                return MathEvaluationResult.UnitConversion(
                    fromValue = value,
                    fromUnit = fromUnit,
                    toValue = result,
                    toUnit = toUnit,
                    formatted = "${formatNumber(value)} $fromUnit = ${formatNumber(result)} $toUnit"
                )
            }
        }

        // 3. Currency Table
        return convertCurrency(value, fromUnit, toUnit, currencyRates)
    }

    /** A currency conversion, with the live rates when they list both currencies, else the built-in ones. */
    private fun convertCurrency(
        value: Double,
        fromUnit: String,
        toUnit: String,
        currencyRates: () -> CurrencyRates
    ): MathEvaluationResult.UnitConversion? {
        if (isCurrencyCode(fromUnit) && isCurrencyCode(toUnit)) {
            val live = currencyRates()
            val rates = if (fromUnit in live.perBase && toUnit in live.perBase) live else CurrencyRates.BUILT_IN
            val result = rates.convert(value, fromUnit, toUnit) ?: return null
            if (!result.isFinite()) return null
            return MathEvaluationResult.UnitConversion(
                fromValue = value,
                fromUnit = fromUnit.uppercase(Locale.ROOT),
                toValue = result,
                toUnit = toUnit.uppercase(Locale.ROOT),
                // Daily reference rates at best, so the result is an estimate rather than a dealing quote.
                formatted = "${formatNumber(value)} ${fromUnit.uppercase(Locale.ROOT)} ≈ ${formatNumber(result)} ${toUnit.uppercase(Locale.ROOT)}",
                currencyRates = rates
            )
        }

        return null
    }

    /** Three letters, the shape of an ISO 4217 code; nothing else is looked up in the rate tables. */
    private fun isCurrencyCode(u: String) = u.length == 3 && u.all { it in 'a'..'z' }

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

    private fun formatNumber(value: Double): String {
        if (!value.isFinite() || value == 0.0 || value == -0.0) return "0"
        val absVal = abs(value)
        return if (value % 1.0 == 0.0 && absVal < 1e15) {
            val longVal = value.toLong()
            if (longVal == 0L) "0" else longVal.toString()
        } else if (absVal < 1e-4 || absVal >= 1e15) {
            String.format(Locale.ROOT, "%.4e", value)
        } else if (absVal < 1.0) {
            // Four significant digits, not four decimals: 1/3000 is 0.0003333, not 0.0003.
            java.math.BigDecimal(value).round(java.math.MathContext(4)).stripTrailingZeros().toPlainString()
        } else {
            val formatted = String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')
            if (formatted.isEmpty() || formatted == "-" || formatted == "-0") "0" else formatted
        }
    }
}
