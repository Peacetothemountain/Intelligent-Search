package com.pixel.intelligentsearch.core.search

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
        data class UnitConversion(val fromValue: Double, val fromUnit: String, val toValue: Double, val toUnit: String, val formatted: String) : MathEvaluationResult()
    }

    /**
     * Evaluates any math expression, bitwise operation, base conversion, or unit conversion.
     * Returns null if the query is not a valid mathematical or conversion expression.
     */
    fun evaluate(rawQuery: String): MathEvaluationResult? {
        val query = rawQuery.trim()
        if (query.isEmpty()) return null

        // 1. Try Unit Conversion (e.g. "100 km to miles", "72 f in c", "100 usd to eur")
        evaluateUnitConversion(query)?.let { return it }

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

    private fun evaluateScientific(input: String): MathEvaluationResult.Computation? {
        var expr = input
            .replace("×", "*")
            .replace("÷", "/")
            .replace("x", "*")
            .replace("π", "pi")
            .replace(" ", "")

        if (!isLikelyMathExpression(expr)) return null

        return try {
            val parser = ExpressionParser(expr)
            val result = parser.parse()
            if (result.isInfinite() || result.isNaN()) return null

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
        // Must contain at least one operator or recognized scientific function
        val hasOperatorOrFunc = expr.any { it in "+-*/%^!()" } ||
                expr.contains("sin") || expr.contains("cos") || expr.contains("tan") ||
                expr.contains("log") || expr.contains("ln") || expr.contains("sqrt") ||
                expr.contains("abs") || expr.contains("pi") || expr.contains("deg") || expr.contains("rad")

        if (!hasOperatorOrFunc) return false

        // Check for disallowed characters
        val allowedCharsRegex = Regex("^[0-9a-zA-Z.+\\-*/%^!()_,]+$")
        return allowedCharsRegex.matches(expr)
    }

    private class ExpressionParser(private val str: String) {
        private var pos = -1
        private var ch = 0

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
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
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
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        // Factor = Unary (+, -) Factor | Primary (^ Factor)? (!)*
        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos

            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                // Check for scientific notation (e.g. 1e6)
                if (ch == 'e'.code || ch == 'E'.code) {
                    nextChar()
                    if (ch == '+'.code || ch == '-'.code) nextChar()
                    while (ch in '0'.code..'9'.code) nextChar()
                }
                x = str.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                val func = str.substring(startPos, pos).lowercase()

                x = when (func) {
                    "pi" -> Math.PI
                    "e" -> Math.E
                    "phi" -> 1.618033988749895
                    "tau" -> Math.PI * 2.0
                    else -> {
                        // Function call with argument in parentheses: func(arg)
                        if (!eat('('.code)) throw IllegalArgumentException("Expected ( after function $func")
                        val arg = parseExpression()
                        eat(')'.code)

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
                            "round" -> round(arg)
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

            // Exponentiation
            if (eat('^'.code)) {
                x = x.pow(parseFactor())
            }

            // Factorial operator: e.g. 5!
            while (eat('!'.code)) {
                val n = x.toLong()
                if (x < 0 || x != n.toDouble() || n > 20) throw IllegalArgumentException("Factorial out of range")
                var fact = 1L
                for (i in 2..n) fact *= i
                x = fact.toDouble()
            }

            return x
        }
    }

    // ---------------------------------------------------------------------------------------------
    // BITWISE & BASE CONVERSION EVALUATOR
    // ---------------------------------------------------------------------------------------------

    private fun evaluateBaseConversion(query: String): MathEvaluationResult.Bitwise? {
        val regex = Regex("^([0-9a-fA-FxXbBoO]+)\\s+(?:to|in)\\s+(hex|dec|bin|oct)$", RegexOption.IGNORE_CASE)
        val match = regex.find(query.trim()) ?: return null

        val rawNum = match.groupValues[1]
        val targetBase = match.groupValues[2].lowercase()

        val parsedVal = parseAnyRadix(rawNum) ?: return null

        val formattedHex = "0x" + java.lang.Long.toHexString(parsedVal).uppercase()
        val formattedBin = "0b" + java.lang.Long.toBinaryString(parsedVal)

        val targetResult = when (targetBase) {
            "hex" -> formattedHex
            "bin" -> formattedBin
            "oct" -> "0o" + java.lang.Long.toOctalString(parsedVal)
            "dec" -> parsedVal.toString()
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
        val regex = Regex("^([0-9a-fA-FxXbB]+)\\s+(AND|OR|XOR|SHL|SHR|<<|>>|&|\\||\\^)\\s+([0-9a-fA-FxXbB]+)$", RegexOption.IGNORE_CASE)
        val match = regex.find(query.trim()) ?: return null

        val left = parseAnyRadix(match.groupValues[1]) ?: return null
        val op = match.groupValues[2].uppercase()
        val right = parseAnyRadix(match.groupValues[3]) ?: return null

        val resultVal = when (op) {
            "AND", "&" -> left and right
            "OR", "|" -> left or right
            "XOR", "^" -> left xor right
            "SHL", "<<" -> left shl right.toInt()
            "SHR", ">>" -> left shr right.toInt()
            else -> return null
        }

        return MathEvaluationResult.Bitwise(
            expression = "$query = $resultVal",
            decimalValue = resultVal,
            hexValue = "0x" + java.lang.Long.toHexString(resultVal).uppercase(),
            binaryValue = "0b" + java.lang.Long.toBinaryString(resultVal)
        )
    }

    private fun parseAnyRadix(str: String): Long? {
        val trimmed = str.trim()
        return try {
            when {
                trimmed.startsWith("0x", ignoreCase = true) -> trimmed.substring(2).toLong(16)
                trimmed.startsWith("0b", ignoreCase = true) -> trimmed.substring(2).toLong(2)
                trimmed.startsWith("0o", ignoreCase = true) -> trimmed.substring(2).toLong(8)
                else -> trimmed.toLong(10)
            }
        } catch (e: Exception) {
            null
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
        "byte" to 1.0, "bytes" to 1.0, "b" to 1.0,
        "kb" to 1000.0, "kilobyte" to 1000.0, "kilobytes" to 1000.0,
        "mb" to 1e6, "megabyte" to 1e6, "megabytes" to 1e6,
        "gb" to 1e9, "gigabyte" to 1e9, "gigabytes" to 1e9,
        "tb" to 1e12, "terabyte" to 1e12, "terabytes" to 1e12,
        "kib" to 1024.0, "mib" to 1048576.0, "gib" to 1073741824.0, "tib" to 1099511627776.0
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

    private val currencyRatios = mapOf(
        "usd" to 1.0, "eur" to 0.92, "gbp" to 0.78, "jpy" to 155.2,
        "cad" to 1.36, "aud" to 1.52, "chf" to 0.90, "cny" to 7.23,
        "inr" to 83.5, "brl" to 5.40, "krw" to 1370.0, "mxn" to 18.2,
        "sgd" to 1.35, "nzd" to 1.63, "hkd" to 7.81, "sek" to 10.5,
        "nok" to 10.6, "zar" to 18.1, "aed" to 3.67, "sar" to 3.75
    )

    private fun evaluateUnitConversion(query: String): MathEvaluationResult.UnitConversion? {
        val regex = Regex("^([0-9.]+)\\s*([a-zA-Z/_]+)\\s*(?:to|in)\\s*([a-zA-Z/_]+)$", RegexOption.IGNORE_CASE)
        val match = regex.find(query.trim()) ?: return null

        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromUnit = match.groupValues[2].lowercase()
        val toUnit = match.groupValues[3].lowercase()

        // 1. Temperature Check
        if (isTemperature(fromUnit) && isTemperature(toUnit)) {
            val converted = convertTemperature(value, fromUnit, toUnit)
            return MathEvaluationResult.UnitConversion(
                fromValue = value,
                fromUnit = fromUnit.uppercase(),
                toValue = converted,
                toUnit = toUnit.uppercase(),
                formatted = "${formatNumber(value)} ${fromUnit.uppercase()} = ${formatNumber(converted)} ${toUnit.uppercase()}"
            )
        }

        // 2. Standard Unit Tables
        val unitTables = listOf(lengthUnits, massUnits, speedUnits, digitalUnits, volumeUnits, areaUnits, timeUnits)
        for (table in unitTables) {
            if (table.containsKey(fromUnit) && table.containsKey(toUnit)) {
                val base = value * table[fromUnit]!!
                val result = base / table[toUnit]!!
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
        if (currencyRatios.containsKey(fromUnit) && currencyRatios.containsKey(toUnit)) {
            val inUsd = value / currencyRatios[fromUnit]!!
            val result = inUsd * currencyRatios[toUnit]!!
            return MathEvaluationResult.UnitConversion(
                fromValue = value,
                fromUnit = fromUnit.uppercase(),
                toValue = result,
                toUnit = toUnit.uppercase(),
                formatted = "${formatNumber(value)} ${fromUnit.uppercase()} = ${formatNumber(result)} ${toUnit.uppercase()}"
            )
        }

        return null
    }

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
        return if (value % 1.0 == 0.0 && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }
}
