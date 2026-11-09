package com.pixel.intelligentsearch.feature.search.today

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The moon's phase on a day, for the line under the weather on the Discover Overlay. [name] is how an almanac words the day: one of the
 * four principal phases on the day it falls, else the stretch between them. [age] is how far through the cycle the moon
 * is, 0 at new moon, 0.5 at full and back to 1, which its icon draws ([MoonPhaseIcon]).
 */
internal data class MoonPhase(val name: String, val age: Double)

/** The four principal phases in the order they come, from new moon. */
private val PRINCIPAL = listOf("New moon", "First quarter", "Full moon", "Last quarter")

/** What follows each principal phase until the next: from new moon, a waxing crescent, and so on. */
private val BETWEEN = listOf("Waxing crescent", "Waxing gibbous", "Waning gibbous", "Waning crescent")

/** The moon's phase on [date] where the day runs in [zone]. */
internal fun moonPhase(date: LocalDate, zone: ZoneId): MoonPhase {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val noon = (start + end) / 2
    // Every principal phase from a little before the day to a little after it, in order.
    val first = floor((date.year + (date.dayOfYear - 1) / 365.25 - 2000) * 12.3685).toInt() - 2
    val phases = (0 until 4 * 5).map { step ->
        val k = first + step / 4.0
        Phase(step % 4, principalPhaseMillis(k))
    }
    val today = phases.firstOrNull { it.at in start until end }
    val before = phases.last { it.at <= noon }
    val lastNew = phases.last { it.quarter == 0 && it.at <= noon }
    val nextNew = phases.first { it.quarter == 0 && it.at > noon }
    val age = (noon - lastNew.at).toDouble() / (nextNew.at - lastNew.at)
    return MoonPhase(if (today != null) PRINCIPAL[today.quarter] else BETWEEN[before.quarter], age)
}

private class Phase(val quarter: Int, val at: Long)

/**
 * When the principal phase [k] falls, in epoch milliseconds: [k] counts lunations from the new moon of 6 January 2000,
 * a whole number for a new moon and .25, .5 and .75 for first quarter, full and last quarter. Jean Meeus, Astronomical
 * Algorithms (2nd ed.), chapter 49, without the planetary terms (under half a minute), and with Terrestrial Time taken
 * for Universal Time (about a minute apart this century): good to a few minutes.
 */
internal fun principalPhaseMillis(k: Double): Long {
    val t = k / 1236.85
    val t2 = t * t
    val t3 = t2 * t
    val t4 = t3 * t
    var jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t4
    val e = 1 - 0.002516 * t - 0.0000074 * t2
    val m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t2 - 0.00000011 * t3)
    val mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4)
    val f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4)
    val om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t2 + 0.00000215 * t3)
    val quarter = Math.floorMod(Math.round(k * 4), 4L).toInt()
    jde += when (quarter) {
        0, 2 -> {
            val full = quarter == 2
            (if (full) -0.40614 else -0.40720) * sin(mp) +
                (if (full) 0.17302 else 0.17241) * e * sin(m) +
                (if (full) 0.01614 else 0.01608) * sin(2 * mp) +
                (if (full) 0.01043 else 0.01039) * sin(2 * f) +
                (if (full) 0.00734 else 0.00739) * e * sin(mp - m) -
                (if (full) 0.00515 else 0.00514) * e * sin(mp + m) +
                (if (full) 0.00209 else 0.00208) * e * e * sin(2 * m) -
                0.00111 * sin(mp - 2 * f) -
                0.00057 * sin(mp + 2 * f) +
                0.00056 * e * sin(2 * mp + m) -
                0.00042 * sin(3 * mp) +
                0.00042 * e * sin(m + 2 * f) +
                0.00038 * e * sin(m - 2 * f) -
                0.00024 * e * sin(2 * mp - m) -
                0.00017 * sin(om) -
                0.00007 * sin(mp + 2 * m) +
                0.00004 * sin(2 * mp - 2 * f) +
                0.00004 * sin(3 * m) +
                0.00003 * sin(mp + m - 2 * f) +
                0.00003 * sin(2 * mp + 2 * f) -
                0.00003 * sin(mp + m + 2 * f) +
                0.00003 * sin(mp - m + 2 * f) -
                0.00002 * sin(mp - m - 2 * f) -
                0.00002 * sin(3 * mp + m) +
                0.00002 * sin(4 * mp)
        }
        else -> {
            val w = 0.00306 - 0.00038 * e * cos(m) + 0.00026 * cos(mp) - 0.00002 * cos(mp - m) +
                0.00002 * cos(mp + m) + 0.00002 * cos(2 * f)
            -0.62801 * sin(mp) +
                0.17172 * e * sin(m) -
                0.01183 * e * sin(mp + m) +
                0.00862 * sin(2 * mp) +
                0.00804 * sin(2 * f) +
                0.00454 * e * sin(mp - m) +
                0.00204 * e * e * sin(2 * m) -
                0.00180 * sin(mp - 2 * f) -
                0.00070 * sin(mp + 2 * f) -
                0.00040 * sin(3 * mp) -
                0.00034 * e * sin(2 * mp - m) +
                0.00032 * e * sin(m + 2 * f) +
                0.00032 * e * sin(m - 2 * f) -
                0.00028 * e * e * sin(mp + 2 * m) +
                0.00027 * e * sin(2 * mp + m) -
                0.00017 * sin(om) -
                0.00005 * sin(mp - m - 2 * f) +
                0.00004 * sin(2 * mp + 2 * f) -
                0.00004 * sin(mp + m + 2 * f) +
                0.00004 * sin(mp - 2 * m) +
                0.00003 * sin(mp + m - 2 * f) +
                0.00003 * sin(3 * m) +
                0.00002 * sin(2 * mp - 2 * f) +
                0.00002 * sin(mp - m + 2 * f) -
                0.00002 * sin(3 * mp + m) +
                if (quarter == 1) w else -w
        }
    }
    return ((jde - 2440587.5) * 86_400_000.0).toLong()
}

private fun rad(degrees: Double) = (degrees % 360.0) * PI / 180.0
