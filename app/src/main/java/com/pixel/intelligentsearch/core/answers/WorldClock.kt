package com.pixel.intelligentsearch.core.answers

import com.pixel.intelligentsearch.core.util.Durations
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Clock answers, worked out on the device:
 * - "time", "what time is it": the time here
 * - "time in tokyo", "what time is it in tokyo", "tokyo time", "time in PST": the time in a place or zone
 * - "3pm PST in IST", "15:00 utc to est": a time of day moved to another zone, with the day it lands on
 *
 * A place is a city or country the phone's time zone database or [ALIASES] knows, or a zone abbreviation
 * ([ABBREVIATIONS]: PST, CET, IST for India, and so on, each its fixed offset; PT, ET, CT and MT follow daylight
 * saving). Anything else is left to ordinary search rather than answered with the wrong clock. [clock] says what time it
 * is and [locale] how times are written; both default to the phone's, read at each call, and are fixed in tests.
 */
class WorldClock(private val clock: Clock? = null, private val locale: Locale? = null) {

    /** An answer: [title] for the card's large line (a time), [subtitle] under it saying where and which day. */
    data class TimeAnswer(val title: String, val subtitle: String)

    /** A zone a query named, with how its name is shown ("Tokyo", "PST"). */
    private data class Place(val zone: ZoneId, val label: String)

    /**
     * The answer to [rawQuery], or null when it isn't a time question or names no known place. [is24Hour] is the phone's
     * clock setting; [localZone] is where the phone is, from which "tomorrow" and "yesterday" are told.
     */
    fun evaluate(rawQuery: String, is24Hour: Boolean, localZone: ZoneId = (clock?.zone ?: ZoneId.systemDefault())): TimeAnswer? {
        val query = normalize(rawQuery) ?: return null
        return try {
            if (query in LOCAL_PHRASES) {
                val now = now().atZone(localZone)
                return TimeAnswer(time(now.toLocalTime(), is24Hour), "Current time in your location")
            }
            conversion(query, is24Hour) ?: placeTime(query, is24Hour, localZone)
        } catch (_: DateTimeException) {
            null
        }
    }

    /** "time in tokyo", "what time is it in tokyo", "tokyo time", "pst time". */
    private fun placeTime(query: String, is24Hour: Boolean, localZone: ZoneId): TimeAnswer? {
        val placeText = (PLACE_FIRST_REGEX.matchEntire(query) ?: PLACE_LAST_REGEX.matchEntire(query))?.groupValues?.get(1)
            ?: return null
        val place = resolve(placeText) ?: return null
        val now = now()
        val there = now.atZone(place.zone)
        val here = now.atZone(localZone)
        val day = when (ChronoUnit.DAYS.between(here.toLocalDate(), there.toLocalDate())) {
            1L -> " • Tomorrow"
            -1L -> " • Yesterday"
            else -> ""
        }
        return TimeAnswer(time(there.toLocalTime(), is24Hour), "Current time in ${place.label}$day")
    }

    /** "3pm PST in IST", "15:00 utc to est", "9:30 am tokyo time to london": the time of day today in the first zone. */
    private fun conversion(query: String, is24Hour: Boolean): TimeAnswer? {
        val match = CONVERSION_REGEX.matchEntire(query) ?: return null
        val timeOfDay = clockTime(match.groupValues[1]) ?: return null
        val from = resolve(match.groupValues[2]) ?: return null
        val to = resolve(match.groupValues[3]) ?: return null
        val fromDate = now().atZone(from.zone).toLocalDate()
        val start = ZonedDateTime.of(fromDate, timeOfDay, from.zone)
        val end = start.withZoneSameInstant(to.zone)
        val day = when (ChronoUnit.DAYS.between(fromDate, end.toLocalDate())) {
            0L -> ""
            1L -> ", next day"
            -1L -> ", previous day"
            else -> return null
        }
        return TimeAnswer(time(end.toLocalTime(), is24Hour), "In ${to.label}$day • ${time(timeOfDay, is24Hour)} ${from.label}")
    }

    /** "3pm", "3:30 pm", "15:00", "noon", "midnight"; a bare hour needs am or pm, so "5 days" isn't a time. */
    private fun clockTime(text: String): LocalTime? {
        when (text) {
            "noon" -> return LocalTime.NOON
            "midnight" -> return LocalTime.MIDNIGHT
        }
        val match = TIME_REGEX.matchEntire(text) ?: return null
        var hour = match.groupValues[1].toInt()
        val minutes = match.groupValues[2]
        val minute = minutes.toIntOrNull() ?: 0
        val meridiem = match.groupValues[3].replace(".", "")
        if (minute > 59) return null
        when (meridiem) {
            "" -> if (minutes.isEmpty() || hour > 23) return null
            else -> {
                if (hour !in 1..12) return null
                hour = hour % 12 + if (meridiem == "pm") 12 else 0
            }
        }
        return LocalTime.of(hour, minute)
    }

    /** The zone [text] names, or null. A trailing "time" ("tokyo time", "pacific time") is part of how it's said. */
    private fun resolve(text: String): Place? {
        val name = text.removeSuffix(" time").removeSuffix(" local").replace(NOT_LETTERS, "").trim().replace(WHITESPACE, " ")
        if (name.isEmpty() || name in NOT_PLACES) return null
        ABBREVIATIONS[name]?.let { return Place(it, name.uppercase(Locale.ROOT)) }
        val zoneId = ALIASES[name]
            ?: ZoneId.getAvailableZoneIds().firstOrNull { id ->
                isCityZone(id) && id.substringAfterLast('/').replace('_', ' ').lowercase(Locale.ROOT) == name
            }
            ?: return null
        val label = name.split(' ').joinToString(" ") { word ->
            if (word in ACRONYMS) word.uppercase(Locale.ROOT) else word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
        return Place(ZoneId.of(zoneId), label)
    }

    /**
     * Whether [id] is a place's own zone, "Area/City": the old aliases outside those areas ("US/Pacific",
     * "Mexico/General", "Brazil/East") and Australia's state-letter ones ("Australia/North") would let ordinary words
     * such as "general" or "east" name a zone.
     */
    private fun isCityZone(id: String): Boolean =
        id.substringBefore('/', "") in ZONE_AREAS && id !in LEGACY_AREA_ZONES

    private fun time(time: LocalTime, is24Hour: Boolean): String =
        Durations.timeOfDay(time.hour, time.minute, is24Hour, locale ?: Locale.getDefault())

    private fun now() = (clock ?: Clock.systemUTC()).instant()

    private fun normalize(raw: String): String? {
        val text = raw.trim().trimEnd('?', '.', '!').trim()
        if (text.isEmpty() || text.length > MAX_QUERY_LENGTH) return null
        // Accents are dropped, so "são paulo" and "bogotá" are the zone database's "sao paulo" and "bogota".
        val plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(MARKS, "")
        return plain.lowercase(Locale.ROOT).replace('’', '\'').replace(WHITESPACE, " ")
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 80
        val WHITESPACE = Regex("\\s+")
        val NOT_LETTERS = Regex("[^a-z ]")
        val MARKS = Regex("\\p{M}+")

        /** The zone database's areas, whose "Area/City" ids name places. */
        val ZONE_AREAS = setOf(
            "Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific"
        )

        /** Old aliases inside those areas whose last part isn't a place's name ("north", "act") or is the wrong one. */
        val LEGACY_AREA_ZONES = setOf(
            "Australia/ACT", "Australia/LHI", "Australia/NSW", "Australia/North", "Australia/South", "Australia/West",
            "Australia/Yancowinna", "America/Knox_IN", "Pacific/Samoa"
        )

        /** Words of a place's name written in capitals. */
        val ACRONYMS = setOf("uk", "uae", "usa", "nyc", "la", "dc")

        val LOCAL_PHRASES = setOf(
            "time", "the time", "time now", "current time", "local time", "what time is it", "what time is it now",
            "what's the time", "whats the time", "what is the time", "what's the time now", "whats the time now"
        )

        private const val PLACE = "([a-z][a-z .'-]*?)"
        val PLACE_FIRST_REGEX = Regex(
            "^(?:what time is it|(?:what's|whats|what is) the (?:current |local )?time|(?:current |local |the )?time(?: now| right now)?)" +
                " (?:in|at) $PLACE(?: (?:now|right now))?$"
        )
        val PLACE_LAST_REGEX = Regex("^(?:current |what's |whats )?$PLACE (?:local )?time(?: now| right now)?$")

        private const val MERIDIEM = "am|pm|a\\.m\\.|p\\.m\\."
        val TIME_REGEX = Regex("^(\\d{1,2})(?::(\\d{2}))? ?($MERIDIEM)?$")
        val CONVERSION_REGEX = Regex(
            "^(?:convert )?(noon|midnight|\\d{1,2}(?::\\d{2})? ?(?:$MERIDIEM)?) $PLACE (?:in|to|into) $PLACE$"
        )

        /** Zone names typed for a place, not timekeeping: Christmas Island's zone is only "christmas" in the database. */
        val NOT_PLACES = setOf("christmas", "easter", "troll")

        /** Zone abbreviations, each its fixed offset; the bare PT, ET, CT and MT follow daylight saving. IST is India's. */
        val ABBREVIATIONS: Map<String, ZoneId> = buildMap {
            fun fixed(name: String, hours: Int, minutes: Int = 0) = put(name, ZoneOffset.ofHoursMinutes(hours, minutes))
            fixed("utc", 0); fixed("gmt", 0); fixed("wet", 0); fixed("west", 1)
            fixed("bst", 1); fixed("cet", 1); fixed("cest", 2); fixed("eet", 2); fixed("eest", 3); fixed("msk", 3)
            fixed("ist", 5, 30); fixed("sgt", 8); fixed("hkt", 8); fixed("awst", 8); fixed("jst", 9); fixed("kst", 9)
            fixed("acst", 9, 30); fixed("aest", 10); fixed("aedt", 11); fixed("nzst", 12); fixed("nzdt", 13)
            fixed("hst", -10); fixed("akst", -9); fixed("akdt", -8); fixed("pst", -8); fixed("pdt", -7)
            fixed("mst", -7); fixed("mdt", -6); fixed("cst", -6); fixed("cdt", -5); fixed("est", -5); fixed("edt", -4)
            put("pt", ZoneId.of("America/Los_Angeles")); put("mt", ZoneId.of("America/Denver"))
            put("ct", ZoneId.of("America/Chicago")); put("et", ZoneId.of("America/New_York"))
        }

        /**
         * Places the zone database doesn't name by their own city ("india" is Asia/Kolkata's): countries, by their
         * capital's or largest city's zone, and big cities that share another city's zone.
         */
        val ALIASES = mapOf(
            // Countries
            "israel" to "Asia/Jerusalem", "turkey" to "Europe/Istanbul", "turkiye" to "Europe/Istanbul",
            "egypt" to "Africa/Cairo", "iceland" to "Atlantic/Reykjavik", "brazil" to "America/Sao_Paulo",
            "australia" to "Australia/Sydney", "south africa" to "Africa/Johannesburg", "nigeria" to "Africa/Lagos",
            "kenya" to "Africa/Nairobi", "mexico" to "America/Mexico_City", "canada" to "America/Toronto",
            "argentina" to "America/Argentina/Buenos_Aires", "ireland" to "Europe/Dublin", "scotland" to "Europe/London",
            "wales" to "Europe/London", "britain" to "Europe/London", "great britain" to "Europe/London",
            "united kingdom" to "Europe/London", "netherlands" to "Europe/Amsterdam", "holland" to "Europe/Amsterdam",
            "belgium" to "Europe/Brussels", "switzerland" to "Europe/Zurich", "austria" to "Europe/Vienna",
            "portugal" to "Europe/Lisbon", "greece" to "Europe/Athens", "sweden" to "Europe/Stockholm",
            "norway" to "Europe/Oslo", "denmark" to "Europe/Copenhagen", "finland" to "Europe/Helsinki",
            "poland" to "Europe/Warsaw", "czechia" to "Europe/Prague", "czech republic" to "Europe/Prague",
            "hungary" to "Europe/Budapest", "romania" to "Europe/Bucharest", "ukraine" to "Europe/Kiev",
            "russia" to "Europe/Moscow", "saudi arabia" to "Asia/Riyadh", "qatar" to "Asia/Qatar", "iran" to "Asia/Tehran",
            "iraq" to "Asia/Baghdad", "pakistan" to "Asia/Karachi", "bangladesh" to "Asia/Dhaka",
            "nepal" to "Asia/Kathmandu", "sri lanka" to "Asia/Colombo", "thailand" to "Asia/Bangkok",
            "indonesia" to "Asia/Jakarta", "philippines" to "Asia/Manila", "malaysia" to "Asia/Kuala_Lumpur",
            "taiwan" to "Asia/Taipei", "colombia" to "America/Bogota", "peru" to "America/Lima",
            "chile" to "America/Santiago", "venezuela" to "America/Caracas", "cuba" to "America/Havana",
            "morocco" to "Africa/Casablanca", "ethiopia" to "Africa/Addis_Ababa", "ghana" to "Africa/Accra",
            "samoa" to "Pacific/Apia",
            // Cities sharing another city's zone
            "barcelona" to "Europe/Madrid", "washington dc" to "America/New_York", "new york city" to "America/New_York",
            "orlando" to "America/New_York", "ottawa" to "America/Toronto", "quebec" to "America/Toronto",
            "calgary" to "America/Edmonton", "san diego" to "America/Los_Angeles", "new orleans" to "America/Chicago",
            "minneapolis" to "America/Chicago", "munich" to "Europe/Berlin", "frankfurt" to "Europe/Berlin",
            "hamburg" to "Europe/Berlin", "milan" to "Europe/Rome", "florence" to "Europe/Rome", "venice" to "Europe/Rome",
            "naples" to "Europe/Rome", "geneva" to "Europe/Zurich", "manchester" to "Europe/London",
            "edinburgh" to "Europe/London", "osaka" to "Asia/Tokyo", "kyoto" to "Asia/Tokyo",
            "shenzhen" to "Asia/Shanghai", "guangzhou" to "Asia/Shanghai", "chennai" to "Asia/Kolkata",
            "hyderabad" to "Asia/Kolkata", "doha" to "Asia/Qatar", "tel aviv" to "Asia/Jerusalem",
            "cape town" to "Africa/Johannesburg", "rio" to "America/Sao_Paulo", "rio de janeiro" to "America/Sao_Paulo",
            "brasilia" to "America/Sao_Paulo",
            // US zones and states, and more countries and cities

            "pacific" to "America/Los_Angeles", "mountain" to "America/Denver", "central" to "America/Chicago",
            "eastern" to "America/New_York",
            "san francisco" to "America/Los_Angeles", "seattle" to "America/Los_Angeles", "la" to "America/Los_Angeles",
            "california" to "America/Los_Angeles", "las vegas" to "America/Los_Angeles", "portland" to "America/Los_Angeles",
            "boston" to "America/New_York", "miami" to "America/New_York", "washington" to "America/New_York",
            "atlanta" to "America/New_York", "philadelphia" to "America/New_York", "nyc" to "America/New_York",
            "dallas" to "America/Chicago", "houston" to "America/Chicago", "austin" to "America/Chicago",
            "salt lake city" to "America/Denver", "montreal" to "America/Toronto",
            "beijing" to "Asia/Shanghai", "china" to "Asia/Shanghai", "japan" to "Asia/Tokyo",
            "india" to "Asia/Kolkata", "delhi" to "Asia/Kolkata", "new delhi" to "Asia/Kolkata",
            "mumbai" to "Asia/Kolkata", "bangalore" to "Asia/Kolkata", "bengaluru" to "Asia/Kolkata",
            "uk" to "Europe/London", "england" to "Europe/London", "france" to "Europe/Paris",
            "germany" to "Europe/Berlin", "spain" to "Europe/Madrid", "italy" to "Europe/Rome",
            "korea" to "Asia/Seoul", "south korea" to "Asia/Seoul", "vietnam" to "Asia/Ho_Chi_Minh",
            "hanoi" to "Asia/Bangkok", "uae" to "Asia/Dubai", "abu dhabi" to "Asia/Dubai",
            "melbourne" to "Australia/Melbourne", "canberra" to "Australia/Sydney", "new zealand" to "Pacific/Auckland",
            "hawaii" to "Pacific/Honolulu", "alaska" to "America/Anchorage",
            "christmas island" to "Indian/Christmas", "easter island" to "Pacific/Easter"
        )
    }
}
