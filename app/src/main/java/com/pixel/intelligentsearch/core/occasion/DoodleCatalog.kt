package com.pixel.intelligentsearch.core.occasion

import java.time.LocalDate
import java.time.ZoneId

/**
 * What Developer Settings' doodle gallery says about each doodle: what kind of day it is, where it's shown and when it
 * next shows. Everything comes from [OccasionCalendar], so the gallery can't disagree with the widgets.
 */
object DoodleCatalog {

    /** The gallery's groups, in the order its filters list them. */
    enum class Kind(val label: String) {
        US_HOLIDAY("US Holidays"),
        HOLIDAY("Holidays"),
        WORLD("Around the World"),
        SEASON("Seasons"),
        FUN_DAY("Fun Days")
    }

    data class Entry(
        val occasion: Occasion,
        val kind: Kind,
        /** The next day it shows on this phone, today included; null when it isn't kept here within [HORIZON_YEARS]. */
        val nextHere: LocalDate?,
        /**
         * Where it's shown: empty when it shows everywhere, else the ISO country codes of the regions keeping it. Also
         * empty when it shows nowhere in this time zone (when it always shares its day with a season's start here).
         */
        val regions: Set<String>,
        /** When [nextHere] is null, the next day it shows anywhere, and the region showing it then. */
        val nextElsewhere: Pair<LocalDate, String>?,
        /** Whether a phone with no region set gets it in this time zone (what the gallery calls shown everywhere). */
        val everywhere: Boolean
    )

    /** How far ahead the gallery looks: long enough for Leap Day and for days that only show in some years. */
    const val HORIZON_YEARS = 8L

    fun kind(occasion: Occasion): Kind = when (occasion) {
        Occasion.MLK_DAY, Occasion.PRESIDENTS_DAY, Occasion.MEMORIAL_DAY, Occasion.JUNETEENTH,
        Occasion.INDEPENDENCE_DAY, Occasion.LABOR_DAY, Occasion.VETERANS_DAY, Occasion.THANKSGIVING -> Kind.US_HOLIDAY

        Occasion.NEW_YEARS_DAY, Occasion.LUNAR_NEW_YEAR, Occasion.VALENTINES_DAY, Occasion.ST_PATRICKS_DAY,
        Occasion.APRIL_FOOLS_DAY, Occasion.EASTER, Occasion.MOTHERS_DAY, Occasion.FATHERS_DAY, Occasion.HALLOWEEN,
        Occasion.HOLIDAYS, Occasion.NEW_YEARS_EVE -> Kind.HOLIDAY

        Occasion.PANCAKE_DAY, Occasion.CARNIVAL, Occasion.SONGKRAN, Occasion.KINGS_DAY, Occasion.CHILDRENS_DAY,
        Occasion.MIDSUMMER, Occasion.CANADA_DAY, Occasion.TANABATA, Occasion.BASTILLE_DAY,
        Occasion.MID_AUTUMN_FESTIVAL, Occasion.CHUSEOK, Occasion.GERMAN_UNITY_DAY, Occasion.DAY_OF_THE_DEAD,
        Occasion.DIWALI, Occasion.BONFIRE_NIGHT -> Kind.WORLD

        Occasion.FIRST_DAY_OF_SPRING, Occasion.FIRST_DAY_OF_SUMMER, Occasion.FIRST_DAY_OF_AUTUMN,
        Occasion.FIRST_DAY_OF_WINTER -> Kind.SEASON

        else -> Kind.FUN_DAY
    }

    /**
     * Every doodle, in calendar order, as seen from [today] in [zone] by a phone in [region]. A doodle is shown
     * everywhere when a phone with no region gets it; otherwise it's shown in the regions that get it within the
     * horizon. Walks the calendar day by day, so call it off the main thread.
     */
    fun entries(today: LocalDate, zone: ZoneId, region: String): List<Entry> {
        val end = today.plusYears(HORIZON_YEARS)
        val nextHere = firstDays(today, end, zone, region)
        val everywhere = firstDays(today, end, zone, "").keys
        // Regional days come round every year, so four years (one leap year) finds them all.
        val regionalEnd = today.plusYears(4)
        val elsewhere = OccasionCalendar.REGIONS.sorted().associateWith { firstDays(today, regionalEnd, zone, it) }
        return Occasion.entries.map { occasion ->
            val keptIn = if (occasion in everywhere) emptySet() else elsewhere.filterValues { occasion in it }.keys
            Entry(
                occasion = occasion,
                kind = kind(occasion),
                nextHere = nextHere[occasion],
                regions = keptIn,
                nextElsewhere = if (occasion in nextHere) null else elsewhere
                    .mapNotNull { (where, days) -> days[occasion]?.let { it to where } }
                    .minWithOrNull(compareBy<Pair<LocalDate, String>> { it.first }.thenBy { it.second }),
                everywhere = occasion in everywhere
            )
        }
    }

    /** The first day from [from] (inclusive) to [until] (exclusive) that each doodle shows in [region]. */
    private fun firstDays(from: LocalDate, until: LocalDate, zone: ZoneId, region: String): Map<Occasion, LocalDate> {
        val first = HashMap<Occasion, LocalDate>()
        var date = from
        while (date < until && first.size < Occasion.entries.size) {
            OccasionCalendar.on(date, zone, region)?.let { first.putIfAbsent(it, date) }
            date = date.plusDays(1)
        }
        return first
    }
}
