package com.pixel.intelligentsearch.core.currency

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A table of exchange rates: how many units of each currency one unit of [base] buys, keyed by lower-case ISO 4217
 * code. [date] is the day the rates were published, or null for the approximate table built into the app.
 */
data class CurrencyRates(val base: String, val perBase: Map<String, Double>, val date: LocalDate?) {

    val isBuiltIn: Boolean get() = date == null

    /** [value] in [from] converted to [to], or null when either currency isn't in this table. */
    fun convert(value: Double, from: String, to: String): Double? {
        val fromRate = perBase[from] ?: return null
        val toRate = perBase[to] ?: return null
        return value / fromRate * toRate
    }

    /** Where the rates come from, for the answer card: "Rates from Oct 19, 2026" or "Built-in rates". */
    fun sourceLabel(locale: Locale = Locale.getDefault()): String = date?.let {
        "Rates from " + DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withDecimalStyle(DecimalStyle.of(locale)).format(it)
    } ?: "Built-in rates"

    companion object {
        /** Approximate rates per US dollar, used until live rates have been fetched, or when they can't be read. */
        val BUILT_IN = CurrencyRates(
            base = "usd",
            perBase = mapOf(
                "usd" to 1.0, "eur" to 0.92, "gbp" to 0.78, "jpy" to 155.2,
                "cad" to 1.36, "aud" to 1.52, "chf" to 0.90, "cny" to 7.23,
                "inr" to 83.5, "brl" to 5.40, "krw" to 1370.0, "mxn" to 18.2,
                "sgd" to 1.35, "nzd" to 1.63, "hkd" to 7.81, "sek" to 10.5,
                "nok" to 10.6, "zar" to 18.1, "aed" to 3.67, "sar" to 3.75
            ),
            date = null
        )
    }
}
