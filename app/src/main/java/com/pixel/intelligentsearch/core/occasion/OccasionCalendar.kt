package com.pixel.intelligentsearch.core.occasion

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.pow

/**
 * Which [Occasion], if any, a day is. Fixed-date days are the same everywhere; the first day of each season is the
 * local date of the equinox or solstice, named for the hemisphere the phone's time zone is in.
 */
object OccasionCalendar {

    /**
     * Unit tests set this system property (see app/build.gradle.kts) to a day without a doodle, so no screenshot
     * changes on a holiday. Nothing sets it on a phone.
     */
    const val PINNED_DATE_PROPERTY = "intelligentsearch.doodleDate"

    /** The time doodles go by: the phone's clock and time zone. Tests replace it. */
    @Volatile
    internal var now: () -> ZonedDateTime = {
        System.getProperty(PINNED_DATE_PROPERTY)?.let { LocalDate.parse(it).atTime(12, 0).atZone(ZoneId.systemDefault()) }
            ?: ZonedDateTime.now()
    }

    /** Today's occasion where the phone is, or null on an ordinary day. */
    fun today(): Occasion? = now().let { on(it.toLocalDate(), it.zone) }

    fun on(date: LocalDate, zone: ZoneId): Occasion? {
        when (date.monthValue * 100 + date.dayOfMonth) {
            101 -> return Occasion.NEW_YEARS_DAY
            214 -> return Occasion.VALENTINES_DAY
            229 -> return Occasion.LEAP_DAY
            422 -> return Occasion.EARTH_DAY
            1031 -> return Occasion.HALLOWEEN
            1224, 1225 -> return Occasion.HOLIDAYS
            1231 -> return Occasion.NEW_YEARS_EVE
        }
        // Seasons fall from March 19 to 21, June 20 to 22, September 21 to 24 and December 20 to 23.
        val event = when (date.monthValue) {
            3 -> 0
            6 -> 1
            9 -> 2
            12 -> 3
            else -> return null
        }
        if (seasonStart(date.year, event).atZone(zone).toLocalDate() != date) return null
        val southern = isSouthern(zone)
        return when (event) {
            0 -> if (southern) Occasion.FIRST_DAY_OF_AUTUMN else Occasion.FIRST_DAY_OF_SPRING
            1 -> if (southern) Occasion.FIRST_DAY_OF_WINTER else Occasion.FIRST_DAY_OF_SUMMER
            2 -> if (southern) Occasion.FIRST_DAY_OF_SPRING else Occasion.FIRST_DAY_OF_AUTUMN
            else -> if (southern) Occasion.FIRST_DAY_OF_SUMMER else Occasion.FIRST_DAY_OF_WINTER
        }
    }

    /** The next time the doodle changes after [from]: the start of the first later day whose occasion differs. */
    fun nextChange(from: ZonedDateTime): ZonedDateTime {
        val today = on(from.toLocalDate(), from.zone)
        var date = from.toLocalDate().plusDays(1)
        // Every year has several occasions, so this ends within a few months.
        while (on(date, from.zone) == today) date = date.plusDays(1)
        return date.atStartOfDay(from.zone)
    }

    /**
     * When the March equinox (event 0), June solstice (1), September equinox (2) or December solstice (3) of [year]
     * happens: Jean Meeus, Astronomical Algorithms, chapter 27, good to about a minute for years 1000 to 3000.
     */
    internal fun seasonStart(year: Int, event: Int): Instant {
        val y = (year - 2000) / 1000.0
        val c = MEAN_EVENT[event]
        val jde0 = c[0] + c[1] * y + c[2] * y.pow(2) + c[3] * y.pow(3) + c[4] * y.pow(4)
        val t = (jde0 - 2451545.0) / 36525
        val w = Math.toRadians(35999.373 * t - 2.47)
        val deltaLambda = 1 + 0.0334 * cos(w) + 0.0007 * cos(2 * w)
        var s = 0.0
        for (i in PERIODIC_A.indices) s += PERIODIC_A[i] * cos(Math.toRadians(PERIODIC_B[i] + PERIODIC_C[i] * t))
        val jde = jde0 + 0.00001 * s / deltaLambda
        // Julian Ephemeris Day to Unix time; dynamical time runs about 70 seconds ahead of UTC this century.
        val millis = (jde - 2440587.5) * 86_400_000.0 - TT_MINUS_UTC_MS
        return Instant.ofEpochMilli(millis.toLong())
    }

    /** Whether the time zone's place is south of the equator, where the seasons are the other way round. */
    internal fun isSouthern(zone: ZoneId): Boolean {
        val id = zone.id
        return id in SOUTHERN_ZONES || SOUTHERN_PREFIXES.any { id.startsWith(it) }
    }

    private const val TT_MINUS_UTC_MS = 70_000.0

    // Meeus table 27.B: mean equinoxes and solstices, years 1000 to 3000.
    private val MEAN_EVENT = arrayOf(
        doubleArrayOf(2451623.80984, 365242.37404, 0.05169, -0.00411, -0.00057),
        doubleArrayOf(2451716.56767, 365241.62603, 0.00325, 0.00888, -0.00030),
        doubleArrayOf(2451810.21715, 365242.01767, -0.11575, 0.00337, 0.00078),
        doubleArrayOf(2451900.05952, 365242.74049, -0.06223, -0.00823, 0.00032)
    )

    // Meeus table 27.C: the periodic terms.
    private val PERIODIC_A = intArrayOf(485, 203, 199, 182, 156, 136, 77, 74, 70, 58, 52, 50, 45, 44, 29, 18, 17, 16, 14, 12, 12, 12, 9, 8)
    private val PERIODIC_B = doubleArrayOf(
        324.96, 337.23, 342.08, 27.85, 73.14, 171.52, 222.54, 296.72, 243.58, 119.81, 297.17, 21.02,
        247.54, 325.15, 60.93, 155.12, 288.79, 198.04, 199.76, 95.39, 287.11, 320.81, 227.73, 15.45
    )
    private val PERIODIC_C = doubleArrayOf(
        1934.136, 32964.467, 20.186, 445267.112, 45036.886, 22518.443, 65928.934, 3034.906, 9037.513,
        33718.147, 150.678, 2281.226, 29929.562, 31555.956, 4443.417, 67555.328, 4562.452, 62894.029,
        31436.921, 14577.848, 31931.756, 34777.259, 1222.114, 16859.074
    )

    private val SOUTHERN_PREFIXES = listOf("Australia/", "Antarctica/", "America/Argentina/", "Brazil/", "Chile/", "NZ")

    private val SOUTHERN_ZONES = setOf(
        // Americas
        "America/Buenos_Aires", "America/Cordoba", "America/Catamarca", "America/Jujuy", "America/Mendoza",
        "America/Rosario", "America/Santiago", "America/Punta_Arenas", "America/Sao_Paulo", "America/Montevideo",
        "America/Asuncion", "America/La_Paz", "America/Lima", "America/Bahia", "America/Recife", "America/Fortaleza",
        "America/Maceio", "America/Araguaina", "America/Belem", "America/Cuiaba", "America/Campo_Grande",
        "America/Manaus", "America/Porto_Velho", "America/Rio_Branco", "America/Eirunepe", "America/Santarem",
        "America/Noronha", "America/Guayaquil", "Atlantic/Stanley", "Atlantic/South_Georgia", "Atlantic/St_Helena",
        // Africa and the Indian Ocean
        "Africa/Johannesburg", "Africa/Maputo", "Africa/Harare", "Africa/Lusaka", "Africa/Windhoek",
        "Africa/Gaborone", "Africa/Maseru", "Africa/Mbabane", "Africa/Blantyre", "Africa/Lubumbashi",
        "Africa/Luanda", "Africa/Dar_es_Salaam", "Africa/Kinshasa", "Africa/Brazzaville", "Africa/Bujumbura",
        "Africa/Kigali", "Africa/Nairobi", "Indian/Mauritius", "Indian/Reunion", "Indian/Antananarivo",
        "Indian/Mayotte", "Indian/Comoro", "Indian/Mahe", "Indian/Chagos", "Indian/Cocos", "Indian/Christmas",
        "Indian/Kerguelen",
        // Asia and the Pacific
        "Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura", "Asia/Dili", "Pacific/Auckland", "Pacific/Chatham",
        "Pacific/Fiji", "Pacific/Tongatapu", "Pacific/Apia", "Pacific/Noumea", "Pacific/Efate",
        "Pacific/Port_Moresby", "Pacific/Bougainville", "Pacific/Guadalcanal", "Pacific/Norfolk",
        "Pacific/Rarotonga", "Pacific/Tahiti", "Pacific/Pago_Pago", "Pacific/Niue", "Pacific/Fakaofo",
        "Pacific/Funafuti", "Pacific/Wallis", "Pacific/Easter", "Pacific/Galapagos", "Pacific/Gambier",
        "Pacific/Marquesas", "Pacific/Pitcairn", "Pacific/Nauru"
    )
}
