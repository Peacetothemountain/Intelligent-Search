package com.pixel.intelligentsearch.core.occasion

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.cos
import kotlin.math.pow

/**
 * Which [Occasion], if any, a day is. Most days are the same everywhere. A few are kept only where they're
 * celebrated, going by the phone's region (its language setting's country): Groundhog Day, Mother's and Father's Day,
 * Canada Day, the Fourth of July and Thanksgiving. The first day of each season is the local date of the equinox or
 * solstice, named for the hemisphere the phone's time zone is in.
 *
 * Local festivals, such as Diwali in India or Midsummer in Sweden and Finland, are shown only in their regions too (see
 * [local]).
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

    /** The phone's region, an ISO country code such as "US" (empty when unknown). Tests replace it. */
    @Volatile
    internal var region: () -> String = { Locale.getDefault().country.uppercase(Locale.ROOT) }

    /** Today's occasion where the phone is, or null on an ordinary day. */
    fun today(): Occasion? = now().let { on(it.toLocalDate(), it.zone, region()) }

    /** The occasion [date] is in [zone] and [region]; when two fall on one day, the rarer one wins. */
    fun on(date: LocalDate, zone: ZoneId, region: String = ""): Occasion? =
        local(date, region) ?: movable(date, region) ?: regional(date, region) ?: fixed(date) ?: season(date, zone) ?:
            worldDay(date)

    /** The next time the doodle changes after [from]: the start of the first later day whose occasion differs. */
    fun nextChange(from: ZonedDateTime, region: String = ""): ZonedDateTime {
        val today = on(from.toLocalDate(), from.zone, region)
        var date = from.toLocalDate().plusDays(1)
        // Every month has occasions, so this ends within weeks.
        while (on(date, from.zone, region) == today) date = date.plusDays(1)
        return date.atStartOfDay(from.zone)
    }

    /** Days whose date moves from year to year. */
    private fun movable(date: LocalDate, region: String): Occasion? {
        val year = date.year
        if (date == easter(year)) return Occasion.EASTER
        if (date == LUNAR_NEW_YEAR[year]) return Occasion.LUNAR_NEW_YEAR
        val thanksgiving = when (region) {
            "US" -> nth(year, 11, DayOfWeek.THURSDAY, 4)
            "CA" -> nth(year, 10, DayOfWeek.MONDAY, 2)
            else -> null
        }
        if (date == thanksgiving) return Occasion.THANKSGIVING
        val mothersDay = when (region) {
            in MOTHERING_SUNDAY_REGIONS -> easter(year).minusDays(21)
            in MAY_MOTHERS_DAY_REGIONS -> nth(year, 5, DayOfWeek.SUNDAY, 2)
            else -> null
        }
        if (date == mothersDay) return Occasion.MOTHERS_DAY
        val fathersDay = when (region) {
            in SEPTEMBER_FATHERS_DAY_REGIONS -> nth(year, 9, DayOfWeek.SUNDAY, 1)
            in JUNE_FATHERS_DAY_REGIONS -> nth(year, 6, DayOfWeek.SUNDAY, 3)
            else -> null
        }
        if (date == fathersDay) return Occasion.FATHERS_DAY
        return null
    }

    /** Fixed days kept only where they're celebrated. */
    private fun regional(date: LocalDate, region: String): Occasion? = when (date.monthValue * 100 + date.dayOfMonth) {
        202 -> Occasion.GROUNDHOG_DAY.takeIf { region == "US" || region == "CA" }
        701 -> Occasion.CANADA_DAY.takeIf { region == "CA" }
        704 -> Occasion.INDEPENDENCE_DAY.takeIf { region == "US" }
        else -> null
    }

    /**
     * Festivals kept in only one region or a few, on fixed or movable dates. They're the rarest days of all, so they
     * win over anything else that falls on the same day: Songkran over Easter in 2031 and 2036, Tanabata over World
     * Chocolate Day every year, Pancake Day and Carnival over Lunar New Year in 2026 and 2029. US federal holidays come
     * first: Juneteenth over Father's Day in 2033 and 2039, Martin Luther King Jr. Day over National Hugging Day in
     * 2030 and 2036.
     */
    private fun local(date: LocalDate, region: String): Occasion? {
        val year = date.year
        if (region == "US") federalHoliday(date)?.let { return it }
        val shroveTuesday = easter(year).minusDays(47)
        return when {
            date == shroveTuesday && region in PANCAKE_DAY_REGIONS -> Occasion.PANCAKE_DAY
            date == shroveTuesday && region == "BR" -> Occasion.CARNIVAL
            date == LocalDate.of(year, 4, 13) && region == "TH" -> Occasion.SONGKRAN
            date == LocalDate.of(year, 4, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY)) && region == "US" ->
                Occasion.ARBOR_DAY
            date == kingsDay(year) && region == "NL" -> Occasion.KINGS_DAY
            date == LocalDate.of(year, 5, 5) && region == "JP" -> Occasion.CHILDRENS_DAY
            date == midsummer(year, region) -> Occasion.MIDSUMMER
            date == LocalDate.of(year, 7, 7) && region == "JP" -> Occasion.TANABATA
            date == LocalDate.of(year, 7, 14) && region == "FR" -> Occasion.BASTILLE_DAY
            date == nth(year, 7, DayOfWeek.SUNDAY, 3) && region == "US" -> Occasion.ICE_CREAM_DAY
            date == MID_AUTUMN_FESTIVAL[year] && region in MID_AUTUMN_REGIONS -> Occasion.MID_AUTUMN_FESTIVAL
            date == CHUSEOK[year] && region == "KR" -> Occasion.CHUSEOK
            date == LocalDate.of(year, 10, 3) && region == "DE" -> Occasion.GERMAN_UNITY_DAY
            date == LocalDate.of(year, 11, 2) && region == "MX" -> Occasion.DAY_OF_THE_DEAD
            date == DIWALI[year] && region == "IN" -> Occasion.DIWALI
            date == LocalDate.of(year, 11, 5) && region == "GB" -> Occasion.BONFIRE_NIGHT
            else -> null
        }
    }

    /**
     * US federal holidays (5 U.S.C. 6103) that aren't doodles of their own elsewhere, on the day itself rather than the
     * weekday they're observed on when they fall at a weekend.
     */
    private fun federalHoliday(date: LocalDate): Occasion? {
        val year = date.year
        return when (date) {
            nth(year, 1, DayOfWeek.MONDAY, 3) -> Occasion.MLK_DAY
            nth(year, 2, DayOfWeek.MONDAY, 3) -> Occasion.PRESIDENTS_DAY
            LocalDate.of(year, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)) -> Occasion.MEMORIAL_DAY
            LocalDate.of(year, 6, 19) -> Occasion.JUNETEENTH
            nth(year, 9, DayOfWeek.MONDAY, 1) -> Occasion.LABOR_DAY
            LocalDate.of(year, 11, 11) -> Occasion.VETERANS_DAY
            else -> null
        }
    }

    /** King's Day in the Netherlands: April 27, or the 26th when the 27th is a Sunday. */
    private fun kingsDay(year: Int): LocalDate =
        LocalDate.of(year, 4, 27).let { if (it.dayOfWeek == DayOfWeek.SUNDAY) it.minusDays(1) else it }

    /** Midsummer: in Sweden the Friday from June 19 to 25 (Midsummer Eve); in Finland the Saturday from June 20 to 26. */
    private fun midsummer(year: Int, region: String): LocalDate? = when (region) {
        "SE" -> LocalDate.of(year, 6, 19).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        "FI" -> LocalDate.of(year, 6, 20).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        else -> null
    }

    /** Where Shrove Tuesday is Pancake Day; in Brazil the same day is Carnival's. */
    private val PANCAKE_DAY_REGIONS = setOf("GB", "IE", "AU", "NZ", "CA")

    /** Where the Mid-Autumn Festival is kept on the Chinese calendar's date; Korea keeps it as Chuseok. */
    private val MID_AUTUMN_REGIONS = setOf("CN", "HK", "MO", "TW", "SG", "MY")

    /**
     * The Mid-Autumn Festival, the 15th day of the 8th month of the Chinese calendar, as the Hong Kong Observatory's
     * Gregorian-lunar conversion tables give it for each year; there's no doodle for it after 2040.
     */
    private val MID_AUTUMN_FESTIVAL = mapOf(
        2026 to LocalDate.of(2026, 9, 25), 2027 to LocalDate.of(2027, 9, 15), 2028 to LocalDate.of(2028, 10, 3),
        2029 to LocalDate.of(2029, 9, 22), 2030 to LocalDate.of(2030, 9, 12), 2031 to LocalDate.of(2031, 10, 1),
        2032 to LocalDate.of(2032, 9, 19), 2033 to LocalDate.of(2033, 9, 8), 2034 to LocalDate.of(2034, 9, 27),
        2035 to LocalDate.of(2035, 9, 16), 2036 to LocalDate.of(2036, 10, 4), 2037 to LocalDate.of(2037, 9, 24),
        2038 to LocalDate.of(2038, 9, 13), 2039 to LocalDate.of(2039, 10, 2), 2040 to LocalDate.of(2040, 9, 20)
    )

    /**
     * Chuseok, the same lunar date on the Korean calendar, which is reckoned at UTC+9: a day after the Chinese date in
     * 2040, when the new moon falls just before midnight in Beijing and just after it in Seoul. No doodle after 2040.
     */
    private val CHUSEOK = MID_AUTUMN_FESTIVAL + (2040 to LocalDate.of(2040, 9, 21))

    /**
     * Diwali (the day of Lakshmi Puja) for New Delhi, as both Drik Panchang and AstroSage give it (and India's list of
     * public holidays for 2026 and 2027). From 2036 the new moon ends close to sunset and the almanacs may disagree, so
     * there's no doodle for it after 2035.
     */
    private val DIWALI = mapOf(
        2026 to LocalDate.of(2026, 11, 8), 2027 to LocalDate.of(2027, 10, 29), 2028 to LocalDate.of(2028, 10, 17),
        2029 to LocalDate.of(2029, 11, 5), 2030 to LocalDate.of(2030, 10, 26), 2031 to LocalDate.of(2031, 11, 14),
        2032 to LocalDate.of(2032, 11, 2), 2033 to LocalDate.of(2033, 10, 22), 2034 to LocalDate.of(2034, 11, 10),
        2035 to LocalDate.of(2035, 10, 30)
    )

    private fun fixed(date: LocalDate): Occasion? = when (date.monthValue * 100 + date.dayOfMonth) {
        101 -> Occasion.NEW_YEARS_DAY
        214 -> Occasion.VALENTINES_DAY
        229 -> Occasion.LEAP_DAY
        308 -> Occasion.WOMENS_DAY
        314 -> Occasion.PI_DAY
        317 -> Occasion.ST_PATRICKS_DAY
        401 -> Occasion.APRIL_FOOLS_DAY
        422 -> Occasion.EARTH_DAY
        520 -> Occasion.BEE_DAY
        608 -> Occasion.OCEANS_DAY
        707 -> Occasion.CHOCOLATE_DAY
        717 -> Occasion.EMOJI_DAY
        720 -> Occasion.MOON_DAY
        808 -> Occasion.CAT_DAY
        826 -> Occasion.DOG_DAY
        919 -> Occasion.PIRATE_DAY
        1001 -> Occasion.COFFEE_DAY
        1005 -> Occasion.TEACHERS_DAY
        1031 -> Occasion.HALLOWEEN
        1113 -> Occasion.KINDNESS_DAY
        1224, 1225 -> Occasion.HOLIDAYS
        1231 -> Occasion.NEW_YEARS_EVE
        // Global fun days.
        121 -> Occasion.HUGGING_DAY
        209 -> Occasion.PIZZA_DAY
        423 -> Occasion.BOOK_DAY
        425 -> Occasion.PENGUIN_DAY
        430 -> Occasion.JAZZ_DAY
        516 -> Occasion.LIGHT_DAY
        603 -> Occasion.BICYCLE_DAY
        730 -> Occasion.FRIENDSHIP_DAY
        810 -> Occasion.LION_DAY
        819 -> Occasion.PHOTOGRAPHY_DAY
        1121 -> Occasion.HELLO_DAY
        else -> null
    }

    /**
     * Global days that give way to everything else: the Day of the Programmer (the 256th day of the year), World Smile
     * Day (the first Friday of October, which can fall on Coffee Day or Teachers' Day) and two fixed days that often
     * share their date with an equinox or solstice, the International Day of Happiness (March 20) and World Music Day
     * (June 21). Those two show only in years when the season starts on another day where the phone is.
     */
    private fun worldDay(date: LocalDate): Occasion? = when {
        date.dayOfYear == 256 -> Occasion.PROGRAMMERS_DAY
        date == nth(date.year, 10, DayOfWeek.FRIDAY, 1) -> Occasion.SMILE_DAY
        date.monthValue == 3 && date.dayOfMonth == 20 -> Occasion.HAPPINESS_DAY
        date.monthValue == 6 && date.dayOfMonth == 21 -> Occasion.MUSIC_DAY
        else -> null
    }

    private fun season(date: LocalDate, zone: ZoneId): Occasion? {
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

    /** The [n]th [day] of [month] in [year], e.g. the fourth Thursday of November. */
    private fun nth(year: Int, month: Int, day: DayOfWeek, n: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, day))

    /** Western Easter Sunday: the anonymous Gregorian computus (Meeus, Astronomical Algorithms, chapter 8). */
    internal fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** Whether the time zone's place is south of the equator, where the seasons are the other way round. */
    internal fun isSouthern(zone: ZoneId): Boolean {
        val id = zone.id
        return id in SOUTHERN_ZONES || SOUTHERN_PREFIXES.any { id.startsWith(it) }
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

    private const val TT_MINUS_UTC_MS = 70_000.0

    /** Lunar New Year's Day (the first day of the Chinese calendar); there's no doodle for it after 2040. */
    private val LUNAR_NEW_YEAR = mapOf(
        2026 to LocalDate.of(2026, 2, 17), 2027 to LocalDate.of(2027, 2, 6), 2028 to LocalDate.of(2028, 1, 26),
        2029 to LocalDate.of(2029, 2, 13), 2030 to LocalDate.of(2030, 2, 3), 2031 to LocalDate.of(2031, 1, 23),
        2032 to LocalDate.of(2032, 2, 11), 2033 to LocalDate.of(2033, 1, 31), 2034 to LocalDate.of(2034, 2, 19),
        2035 to LocalDate.of(2035, 2, 8), 2036 to LocalDate.of(2036, 1, 28), 2037 to LocalDate.of(2037, 2, 15),
        2038 to LocalDate.of(2038, 2, 4), 2039 to LocalDate.of(2039, 1, 24), 2040 to LocalDate.of(2040, 2, 12)
    )

    // Where Mother's and Father's Day fall on these dates; elsewhere they're on other days, so there's no doodle.
    private val MOTHERING_SUNDAY_REGIONS = setOf("GB", "IE")
    private val MAY_MOTHERS_DAY_REGIONS = setOf(
        "US", "CA", "AU", "NZ", "IN", "JP", "CN", "HK", "TW", "SG", "MY", "PH", "DE", "IT", "CH", "AT", "NL", "BE",
        "DK", "FI", "BR", "ZA", "TR", "GR", "IS"
    )
    private val SEPTEMBER_FATHERS_DAY_REGIONS = setOf("AU", "NZ")
    private val JUNE_FATHERS_DAY_REGIONS = setOf(
        "US", "CA", "GB", "IE", "IN", "JP", "HK", "SG", "MY", "PH", "NL", "FR", "ZA", "TR", "GR", "MX"
    )

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
