package com.ngdesigns.intelligentsearch.engine.calc

/** What a unit measures; only units of the same dimension convert. */
enum class Dimension { Length, Mass, Temperature, Volume, Speed, Time, Data, Area, Energy, Pressure, Currency }

/**
 * A unit: [factor] converts to the dimension's base unit (metre, kilogram, litre, m/s, second, byte, m², joule,
 * pascal, euro). Temperature uses [offset] too: base = value * factor + offset, in kelvin.
 */
data class Unit(val symbol: String, val name: String, val dimension: Dimension, val factor: Double, val offset: Double = 0.0)

/**
 * Exchange rates against the euro (units of the currency per euro). The app can supply fetched daily rates; the
 * built-in [Fallback] is approximate and marks its answers so.
 */
interface CurrencyRates {
    fun perEuro(code: String): Double?
    val approximate: Boolean

    object Fallback : CurrencyRates {
        override val approximate = true
        private val table = mapOf(
            "EUR" to 1.0, "USD" to 1.087, "GBP" to 0.86, "JPY" to 162.0, "CAD" to 1.48, "AUD" to 1.65, "NZD" to 1.8,
            "CHF" to 0.95, "CNY" to 7.85, "INR" to 90.5, "MXN" to 19.8, "BRL" to 5.9, "KRW" to 1480.0, "SEK" to 11.4,
            "NOK" to 11.7, "DKK" to 7.46, "PLN" to 4.3, "CZK" to 25.2, "HUF" to 395.0, "SGD" to 1.46, "HKD" to 8.5,
            "ZAR" to 20.0, "TRY" to 37.0, "ILS" to 4.0, "THB" to 38.5, "PHP" to 61.0, "IDR" to 17500.0,
        )
        override fun perEuro(code: String) = table[code]
    }
}

internal object Units {

    private val all: List<Unit> = buildList {
        fun u(dim: Dimension, factor: Double, symbol: String, name: String, vararg aliases: String, offset: Double = 0.0) {
            add(Unit(symbol, name, dim, factor, offset))
            aliases.forEach { add(Unit(it, name, dim, factor, offset)) }
        }
        val L = Dimension.Length
        u(L, 0.001, "mm", "millimetres", "millimeter", "millimeters", "millimetre", "millimetres")
        u(L, 0.01, "cm", "centimetres", "centimeter", "centimeters", "centimetre", "centimetres")
        u(L, 1.0, "m", "metres", "meter", "meters", "metre", "metres")
        u(L, 1000.0, "km", "kilometres", "kilometer", "kilometers", "kilometre", "kilometres")
        u(L, 0.0254, "in", "inches", "inch", "inches", "\"")
        u(L, 0.3048, "ft", "feet", "foot", "feet", "'")
        u(L, 0.9144, "yd", "yards", "yard", "yards")
        u(L, 1609.344, "mi", "miles", "mile", "miles")
        u(L, 1852.0, "nmi", "nautical miles", "nautical mile", "nautical miles")
        val M = Dimension.Mass
        u(M, 1e-6, "mg", "milligrams", "milligram", "milligrams")
        u(M, 0.001, "g", "grams", "gram", "grams")
        u(M, 1.0, "kg", "kilograms", "kilogram", "kilograms", "kilo", "kilos")
        u(M, 1000.0, "t", "tonnes", "tonne", "tonnes", "metric ton")
        u(M, 0.028349523125, "oz", "ounces", "ounce", "ounces")
        u(M, 0.45359237, "lb", "pounds", "lbs", "pound", "pounds")
        u(M, 6.35029318, "st", "stone", "stone", "stones")
        val T = Dimension.Temperature
        u(T, 1.0, "°C", "celsius", "c", "celsius", "°c", "degc", offset = 273.15)
        u(T, 5.0 / 9.0, "°F", "fahrenheit", "f", "fahrenheit", "°f", "degf", offset = 273.15 - 32 * 5.0 / 9.0)
        u(T, 1.0, "K", "kelvin", "k", "kelvin")
        val V = Dimension.Volume
        u(V, 0.001, "ml", "millilitres", "milliliter", "milliliters", "millilitre", "millilitres")
        u(V, 0.01, "cl", "centilitres", "centiliter", "centilitre")
        u(V, 1.0, "l", "litres", "liter", "liters", "litre", "litres")
        u(V, 1000.0, "m³", "cubic metres", "m3", "cubic meter", "cubic meters")
        u(V, 3.785411784, "gal", "US gallons", "gallon", "gallons")
        u(V, 0.946352946, "qt", "US quarts", "quart", "quarts")
        u(V, 0.473176473, "pt", "US pints", "pint", "pints")
        u(V, 0.2365882365, "cup", "US cups", "cups")
        u(V, 0.0295735295625, "fl oz", "US fluid ounces", "floz", "fluid ounce", "fluid ounces")
        u(V, 0.01478676478125, "tbsp", "tablespoons", "tablespoon", "tablespoons")
        u(V, 0.00492892159375, "tsp", "teaspoons", "teaspoon", "teaspoons")
        val S = Dimension.Speed
        u(S, 1.0, "m/s", "metres per second", "mps")
        u(S, 1 / 3.6, "km/h", "kilometres per hour", "kmh", "kph", "kmph")
        u(S, 0.44704, "mph", "miles per hour")
        u(S, 0.514444, "kn", "knots", "knot", "knots", "kt")
        val Ti = Dimension.Time
        u(Ti, 0.001, "ms", "milliseconds", "millisecond", "milliseconds")
        u(Ti, 1.0, "s", "seconds", "sec", "secs", "second", "seconds")
        u(Ti, 60.0, "min", "minutes", "mins", "minute", "minutes")
        u(Ti, 3600.0, "h", "hours", "hr", "hrs", "hour", "hours")
        u(Ti, 86400.0, "d", "days", "day", "days")
        u(Ti, 604800.0, "wk", "weeks", "week", "weeks")
        u(Ti, 31557600.0, "yr", "years", "year", "years")
        val A = Dimension.Area
        u(A, 1.0, "m²", "square metres", "m2", "sq m", "square meter", "square meters")
        u(A, 1e6, "km²", "square kilometres", "km2", "sq km")
        u(A, 0.09290304, "ft²", "square feet", "ft2", "sq ft", "square foot")
        u(A, 4046.8564224, "ac", "acres", "acre", "acres")
        u(A, 10000.0, "ha", "hectares", "hectare", "hectares")
        val E = Dimension.Energy
        u(E, 1.0, "J", "joules", "j", "joule", "joules")
        u(E, 1000.0, "kJ", "kilojoules", "kj")
        u(E, 4.184, "cal", "calories", "calorie")
        u(E, 4184.0, "kcal", "kilocalories", "kilocalorie", "calories (food)")
        u(E, 3.6e6, "kWh", "kilowatt hours", "kwh")
        val P = Dimension.Pressure
        u(P, 1.0, "Pa", "pascals", "pa")
        u(P, 1000.0, "kPa", "kilopascals", "kpa")
        u(P, 100000.0, "bar", "bar", "bars")
        u(P, 6894.757293168, "psi", "pounds per square inch")
        u(P, 101325.0, "atm", "atmospheres", "atmosphere")
    }

    private val byName: Map<String, Unit> = all.associateBy { it.symbol.lowercase() }

    /** Data units are case-sensitive in the usual way: "Mb" is megabits, "MB" megabytes; all-lower "kb" means kilobytes. */
    private fun dataUnit(s: String): Unit? {
        val m = Regex("^([kKmMgGtTpP]?)(i?)(b|B|bit|bits|byte|bytes)$").find(s) ?: return null
        val (prefix, binary, base) = m.destructured
        val isBit = base == "bit" || base == "bits" || (base == "b" && prefix.isNotEmpty() && prefix[0].isUpperCase())
        if (base == "b" && prefix.isEmpty()) return Unit("b", "bits", Dimension.Data, 0.125)
        val power = "kmgtp".indexOf(prefix.lowercase()) + 1
        val step = if (binary.isNotEmpty()) 1024.0 else 1000.0
        val factor = Math.pow(step, power.toDouble()) * (if (isBit) 0.125 else 1.0)
        val symbol = prefix.uppercase().replace("K", if (binary.isEmpty()) "k" else "K") + binary + (if (isBit) "b" else "B")
        return Unit(symbol, if (isBit) "bits" else "bytes", Dimension.Data, factor)
    }

    private fun currency(s: String, rates: CurrencyRates): Unit? {
        val code = when (s) { "$" -> "USD"; "€" -> "EUR"; "£" -> "GBP"; "¥" -> "JPY"; "₹" -> "INR"; "₩" -> "KRW"; else -> s.uppercase() }
        if (code.length != 3) return null
        val perEuro = rates.perEuro(code) ?: return null
        return Unit(code, code, Dimension.Currency, 1.0 / perEuro)
    }

    private fun unit(s: String, rates: CurrencyRates): Unit? {
        val t = s.trim()
        return dataUnit(t) ?: byName[t.lowercase()] ?: currency(t, rates)
    }

    private val pattern = Regex(
        "^([$€£¥₹₩])?\\s*(-?\\d+(?:[.,]\\d+)?)\\s*([^\\d\\s][^=]*?)?\\s+(?:to|in|into|as|=)\\s+([^\\d][^=]*?)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun convert(text: String, rates: CurrencyRates): Answer.Conversion? {
        val m = pattern.find(text) ?: return null
        val (symbol, number, fromText, toText) = m.destructured
        val fromName = symbol.ifEmpty { fromText }.ifEmpty { return null }
        val value = number.replace(',', '.').toDoubleOrNull() ?: return null
        val from = unit(fromName, rates) ?: return null
        val to = unit(toText, rates) ?: return null
        if (from.dimension != to.dimension || from == to && from.symbol == to.symbol) return null
        val base = value * from.factor + from.offset
        val result = (base - to.offset) / to.factor
        return Answer.Conversion(text, value, from, result, to, approximate = from.dimension == Dimension.Currency && rates.approximate)
    }
}
