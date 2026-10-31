package com.pixel.intelligentsearch.core.answers

import com.pixel.intelligentsearch.core.util.Durations
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
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
 * ([ABBREVIATIONS]: PST, CET, IST for India, and so on). Anything else is left to ordinary search rather than answered
 * with the wrong clock. [clock] says what time it is and [locale] how times are written; both default to the phone's,
 * read at each call, and are fixed in tests.
 */
class WorldClock(private val clock: Clock? = null, private val locale: Locale? = null) {

    /** An answer: [title] for the card's large line (a time), [subtitle] under it saying where and which day. */
    data class TimeAnswer(val title: String, val subtitle: String)

    /**
     * A zone a query named, with how its name is shown ("Tokyo", "PST"). A zone abbreviation for a region that keeps
     * daylight saving has [daylightLabel], the name its clock goes by in summer ("PDT"), shown whenever it is in force.
     */
    private data class Place(val zone: ZoneId, val label: String, val daylightLabel: String? = null) {
        fun labelAt(instant: Instant): String =
            if (daylightLabel != null && zone.rules.isDaylightSavings(instant)) daylightLabel else label
    }

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
        // "time in davis" asks for a place; "davis time" may not, so there everyday words aren't zones.
        val place = resolve(placeText, explicit = PLACE_FIRST_REGEX.matches(query)) ?: return null
        val now = now()
        val there = now.atZone(place.zone)
        val here = now.atZone(localZone)
        val day = when (ChronoUnit.DAYS.between(here.toLocalDate(), there.toLocalDate())) {
            1L -> " • Tomorrow"
            -1L -> " • Yesterday"
            else -> ""
        }
        return TimeAnswer(time(there.toLocalTime(), is24Hour), "Current time in ${place.labelAt(now)}$day")
    }

    /** "3pm PST in IST", "15:00 utc to est", "9:30 am tokyo time to london": the time of day today in the first zone. */
    private fun conversion(query: String, is24Hour: Boolean): TimeAnswer? {
        val match = CONVERSION_REGEX.matchEntire(query) ?: return null
        val timeOfDay = clockTime(match.groupValues[1]) ?: return null
        val from = resolve(match.groupValues[2], explicit = false) ?: return null
        val to = resolve(match.groupValues[3], explicit = false) ?: return null
        val fromDate = now().atZone(from.zone).toLocalDate()
        val start = ZonedDateTime.of(fromDate, timeOfDay, from.zone)
        val end = start.withZoneSameInstant(to.zone)
        val day = when (ChronoUnit.DAYS.between(fromDate, end.toLocalDate())) {
            0L -> ""
            1L -> ", next day"
            -1L -> ", previous day"
            else -> return null
        }
        val instant = start.toInstant()
        return TimeAnswer(time(end.toLocalTime(), is24Hour), "In ${to.labelAt(instant)}$day • ${time(timeOfDay, is24Hour)} ${from.labelAt(instant)}")
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

    /**
     * The zone [text] names, or null. A trailing "time" ("tokyo time", "pacific time") is part of how it's said, and a
     * leading "the" ("the us") too. Unless the query was [explicit] ("time in davis"), a place that is also an
     * everyday word or name ([EVERYDAY_ZONE_WORDS]: "wake", "center", "davis") isn't taken for the place.
     */
    private fun resolve(text: String, explicit: Boolean): Place? {
        val name = text.removeSuffix(" time").removeSuffix(" local").replace(NOT_LETTERS, "").trim()
            .replace(WHITESPACE, " ").removePrefix("the ")
        if (name.isEmpty() || name in NOT_PLACES) return null
        if (!explicit && name in EVERYDAY_ZONE_WORDS) return null
        ABBREVIATIONS[name]?.let { zone -> return Place(zone.zone, zone.standard ?: name.uppercase(Locale.ROOT), zone.daylight) }
        // A country spanning zones is answered for one city's, and the label names that city.
        MULTI_ZONE_COUNTRIES[name]?.let { (zone, label) -> return Place(ZoneId.of(zone), label) }
        // Asked as a place, a name the zone database gives to a lesser-known town means the famous one.
        if (explicit) BETTER_KNOWN[name]?.let { (zone, label) -> return Place(ZoneId.of(zone), label) }
        val zoneId = ALIASES[name] ?: US_STATES[name]
            ?: name.let { city ->
                ZoneId.getAvailableZoneIds().firstOrNull { id ->
                    isCityZone(id) && id.substringAfterLast('/').replace('_', ' ').lowercase(Locale.ROOT) == city
                }
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

    internal companion object {
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
        val ACRONYMS = setOf("uk", "uae", "usa", "nyc", "la", "dc", "sf")

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

        /**
         * Zone names typed for a place, not timekeeping: Christmas Island's zone is only "christmas" in the database,
         * and "west" (Lisbon's summer abbreviation, WEST) is a direction: "west time", "time in the west".
         */
        val NOT_PLACES = setOf("christmas", "easter", "west")

        /**
         * Zone places whose name is also an everyday word, a person's name or a better-known place ("wake", "center",
         * "davis", "jersey", "petersburg"): "wake time" is about waking, so these are places only when asked as one,
         * "time in wake".
         */
        val EVERYDAY_ZONE_WORDS = setOf(
            "wake", "midway", "center", "davis", "casey", "mawson", "vostok", "troll", "palmer", "rothera", "macquarie",
            "chatham", "johnston", "knox", "beulah", "new salem", "marengo", "vincennes", "winamac", "petersburg",
            "tell city", "vevay", "monticello", "stanley", "jersey", "victoria", "reunion", "resolute", "virgin", "oral",
            "norfolk", "canary", "dawson", "creston", "currie", "wallis", "yap", "truk", "cordoba", "lindeman",
            // Countries and cities that are also a bird, a name, a pepper or a dog: "turkey time" is about roasting.
            "turkey", "jordan", "cayenne", "chihuahua", "regina", "darwin"
        )

        /**
         * Places the zone database names for a lesser-known town ("Petersburg" is in Indiana, "Jersey" the Channel
         * Island, "Cordoba" in Argentina), answered for the place people mean when they ask "time in petersburg":
         * name to zone and label.
         */
        val BETTER_KNOWN: Map<String, Pair<String, String>> = mapOf(
            "petersburg" to ("Europe/Moscow" to "St Petersburg"),
            "jersey" to ("America/New_York" to "New Jersey"),
            "cordoba" to ("Europe/Madrid" to "Córdoba, Spain")
        )

        /**
         * US states, each answered for the zone most of it keeps; Arizona's own, which skips daylight saving. Georgia
         * is left out: it is a country too.
         */
        val US_STATES: Map<String, String> = buildMap {
            val eastern = "America/New_York"
            val central = "America/Chicago"
            val mountain = "America/Denver"
            val pacific = "America/Los_Angeles"
            for (state in listOf(
                "connecticut", "delaware", "florida", "kentucky", "maine", "maryland", "massachusetts", "new hampshire",
                "new jersey", "north carolina", "ohio", "pennsylvania", "rhode island", "south carolina", "vermont",
                "virginia", "west virginia"
            )) put(state, eastern)
            for (state in listOf(
                "alabama", "arkansas", "illinois", "iowa", "kansas", "louisiana", "minnesota", "mississippi", "missouri",
                "nebraska", "north dakota", "oklahoma", "south dakota", "tennessee", "texas", "wisconsin"
            )) put(state, central)
            for (state in listOf("colorado", "montana", "new mexico", "utah", "wyoming")) put(state, mountain)
            for (state in listOf("nevada", "oregon", "washington state")) put(state, pacific)
            put("arizona", "America/Phoenix")
            put("idaho", "America/Boise")
            put("indiana", "America/Indiana/Indianapolis")
            put("michigan", "America/Detroit")
        }

        /**
         * Countries spanning several zones, answered for their capital's or largest city's zone, with that city in the
         * label so the time isn't read as all of the country's: name to zone and label.
         */
        val MULTI_ZONE_COUNTRIES: Map<String, Pair<String, String>> = buildMap {
            val unitedStates = "America/New_York" to "United States (New York)"
            for (name in listOf("us", "usa", "united states", "united states of america", "america")) put(name, unitedStates)
            put("canada", "America/Toronto" to "Canada (Toronto)")
            put("australia", "Australia/Sydney" to "Australia (Sydney)")
            put("brazil", "America/Sao_Paulo" to "Brazil (São Paulo)")
            put("russia", "Europe/Moscow" to "Russia (Moscow)")
            put("mexico", "America/Mexico_City" to "Mexico (Mexico City)")
            put("indonesia", "Asia/Jakarta" to "Indonesia (Jakarta)")
            put("kazakhstan", "Asia/Almaty" to "Kazakhstan (Almaty)")
        }

        /**
         * A zone abbreviation's region clock, and for a region with daylight saving its [standard] and [daylight]
         * names; null names show the abbreviation as typed.
         */
        class Abbreviation(val zone: ZoneId, val standard: String? = null, val daylight: String? = null)

        /**
         * Zone abbreviations, each the clock of the region that uses it. People type "PST" or "EST" for Pacific or
         * Eastern time all year, so in summer "PST" is Los Angeles's clock, an hour off UTC-8, and is shown as "PDT"
         * (the name of the time actually kept) rather than an hour wrong. UTC and GMT are UTC; IST is India's.
         */
        val ABBREVIATIONS: Map<String, Abbreviation> = buildMap {
            fun region(zone: String, vararg names: String) = names.forEach { put(it, Abbreviation(ZoneId.of(zone))) }
            fun seasonal(zone: String, standard: String, daylight: String) {
                val abbreviation = Abbreviation(ZoneId.of(zone), standard.uppercase(Locale.ROOT), daylight.uppercase(Locale.ROOT))
                put(standard, abbreviation)
                put(daylight, abbreviation)
            }
            // GMT is said for UTC as often as for London's winter clock, so it stays UTC; BST is London's clock.
            put("utc", Abbreviation(ZoneOffset.UTC)); put("gmt", Abbreviation(ZoneOffset.UTC))
            put("bst", Abbreviation(ZoneId.of("Europe/London"), "GMT", "BST"))
            seasonal("Europe/Lisbon", "wet", "west")
            seasonal("Europe/Paris", "cet", "cest"); seasonal("Europe/Athens", "eet", "eest")
            region("Europe/Moscow", "msk"); region("Asia/Kolkata", "ist"); region("Asia/Singapore", "sgt")
            region("Asia/Hong_Kong", "hkt"); region("Australia/Perth", "awst"); region("Asia/Tokyo", "jst")
            region("Asia/Seoul", "kst")
            seasonal("Australia/Adelaide", "acst", "acdt"); seasonal("Australia/Sydney", "aest", "aedt")
            seasonal("Pacific/Auckland", "nzst", "nzdt"); region("Pacific/Honolulu", "hst")
            seasonal("America/Anchorage", "akst", "akdt"); seasonal("America/Los_Angeles", "pst", "pdt")
            seasonal("America/Denver", "mst", "mdt"); seasonal("America/Chicago", "cst", "cdt")
            seasonal("America/New_York", "est", "edt")
            // The bare names are said for the region all year and are shown as typed.
            region("America/Los_Angeles", "pt"); region("America/Denver", "mt")
            region("America/Chicago", "ct"); region("America/New_York", "et")
        }

        /** Whether [word] is a zone abbreviation ("pst", "IST"), which a spell checker should leave as typed. */
        fun isZoneAbbreviation(word: String): Boolean = word.lowercase(Locale.ROOT) in ABBREVIATIONS

        /**
         * Places the zone database doesn't name by their own city ("india" is Asia/Kolkata's): countries, by their
         * capital's or largest city's zone, and big cities that share another city's zone.
         */
        val ALIASES = mapOf(
            // Countries
            "israel" to "Asia/Jerusalem", "turkey" to "Europe/Istanbul", "turkiye" to "Europe/Istanbul",
            "egypt" to "Africa/Cairo", "iceland" to "Atlantic/Reykjavik", "south africa" to "Africa/Johannesburg",
            "nigeria" to "Africa/Lagos", "kenya" to "Africa/Nairobi",
            "argentina" to "America/Argentina/Buenos_Aires", "ireland" to "Europe/Dublin", "scotland" to "Europe/London",
            "wales" to "Europe/London", "britain" to "Europe/London", "great britain" to "Europe/London",
            "united kingdom" to "Europe/London", "netherlands" to "Europe/Amsterdam", "holland" to "Europe/Amsterdam",
            "belgium" to "Europe/Brussels", "switzerland" to "Europe/Zurich", "austria" to "Europe/Vienna",
            "portugal" to "Europe/Lisbon", "greece" to "Europe/Athens", "sweden" to "Europe/Stockholm",
            "norway" to "Europe/Oslo", "denmark" to "Europe/Copenhagen", "finland" to "Europe/Helsinki",
            "poland" to "Europe/Warsaw", "czechia" to "Europe/Prague", "czech republic" to "Europe/Prague",
            "hungary" to "Europe/Budapest", "romania" to "Europe/Bucharest", "ukraine" to "Europe/Kiev",
            "saudi arabia" to "Asia/Riyadh", "qatar" to "Asia/Qatar", "iran" to "Asia/Tehran",
            "iraq" to "Asia/Baghdad", "pakistan" to "Asia/Karachi", "bangladesh" to "Asia/Dhaka",
            "nepal" to "Asia/Kathmandu", "sri lanka" to "Asia/Colombo", "thailand" to "Asia/Bangkok",
            "philippines" to "Asia/Manila", "malaysia" to "Asia/Kuala_Lumpur",
            "taiwan" to "Asia/Taipei", "colombia" to "America/Bogota", "peru" to "America/Lima",
            "chile" to "America/Santiago", "venezuela" to "America/Caracas", "cuba" to "America/Havana",
            "morocco" to "Africa/Casablanca", "ethiopia" to "Africa/Addis_Ababa", "ghana" to "Africa/Accra",
            "samoa" to "Pacific/Apia", "st petersburg" to "Europe/Moscow", "saint petersburg" to "Europe/Moscow",
            "san juan" to "America/Puerto_Rico",
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
            "christmas island" to "Indian/Christmas", "easter island" to "Pacific/Easter",
            // More countries, by their capital's zone
            "croatia" to "Europe/Zagreb", "serbia" to "Europe/Belgrade", "kosovo" to "Europe/Belgrade",
            "bosnia" to "Europe/Sarajevo", "slovenia" to "Europe/Ljubljana", "slovakia" to "Europe/Bratislava",
            "bulgaria" to "Europe/Sofia", "estonia" to "Europe/Tallinn", "latvia" to "Europe/Riga",
            "lithuania" to "Europe/Vilnius", "belarus" to "Europe/Minsk", "moldova" to "Europe/Chisinau",
            "albania" to "Europe/Tirane", "montenegro" to "Europe/Podgorica", "north macedonia" to "Europe/Skopje",
            "macedonia" to "Europe/Skopje", "cyprus" to "Asia/Nicosia", "armenia" to "Asia/Yerevan",
            "azerbaijan" to "Asia/Baku", "jordan" to "Asia/Amman", "lebanon" to "Asia/Beirut", "syria" to "Asia/Damascus",
            "oman" to "Asia/Muscat", "yemen" to "Asia/Aden", "afghanistan" to "Asia/Kabul", "uzbekistan" to "Asia/Tashkent",
            "myanmar" to "Asia/Yangon", "burma" to "Asia/Yangon", "cambodia" to "Asia/Phnom_Penh", "laos" to "Asia/Vientiane",
            "mongolia" to "Asia/Ulaanbaatar", "north korea" to "Asia/Pyongyang", "bhutan" to "Asia/Thimphu",
            "dominican republic" to "America/Santo_Domingo", "haiti" to "America/Port-au-Prince",
            "honduras" to "America/Tegucigalpa", "nicaragua" to "America/Managua", "ecuador" to "America/Guayaquil",
            "bolivia" to "America/La_Paz", "paraguay" to "America/Asuncion", "uruguay" to "America/Montevideo",
            "algeria" to "Africa/Algiers", "tunisia" to "Africa/Tunis", "libya" to "Africa/Tripoli",
            "sudan" to "Africa/Khartoum", "tanzania" to "Africa/Dar_es_Salaam", "uganda" to "Africa/Kampala",
            "rwanda" to "Africa/Kigali", "zimbabwe" to "Africa/Harare", "zambia" to "Africa/Lusaka",
            "angola" to "Africa/Luanda", "senegal" to "Africa/Dakar", "ivory coast" to "Africa/Abidjan",
            "cameroon" to "Africa/Douala",
            // More cities and islands sharing another city's zone
            "bali" to "Asia/Makassar", "denpasar" to "Asia/Makassar", "sf" to "America/Los_Angeles",
            "san jose" to "America/Los_Angeles", "san antonio" to "America/Chicago", "nashville" to "America/Chicago",
            "st louis" to "America/Chicago", "saint louis" to "America/Chicago", "pittsburgh" to "America/New_York",
            "baltimore" to "America/New_York", "cleveland" to "America/New_York", "wellington" to "Pacific/Auckland",
            "goa" to "Asia/Kolkata", "pune" to "Asia/Kolkata", "phuket" to "Asia/Bangkok",
            "saigon" to "Asia/Ho_Chi_Minh", "ho chi minh city" to "Asia/Ho_Chi_Minh", "mecca" to "Asia/Riyadh",
            "krakow" to "Europe/Warsaw", "dubrovnik" to "Europe/Zagreb", "marrakech" to "Africa/Casablanca",
            "zanzibar" to "Africa/Dar_es_Salaam"
        )
    }
}
