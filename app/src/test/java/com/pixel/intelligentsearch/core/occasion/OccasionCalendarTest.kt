package com.pixel.intelligentsearch.core.occasion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OccasionCalendarTest {

    private val newYork = ZoneId.of("America/New_York")
    private val london = ZoneId.of("Europe/London")
    private val sydney = ZoneId.of("Australia/Sydney")

    private fun on(date: String, zone: ZoneId = newYork, region: String = "") = OccasionCalendar.on(LocalDate.parse(date), zone, region)

    @Test
    fun fixedDaysAreTheSameEverywhere() {
        for (zone in listOf(newYork, london, sydney)) {
            assertEquals(Occasion.NEW_YEARS_DAY, on("2027-01-01", zone))
            assertEquals(Occasion.VALENTINES_DAY, on("2027-02-14", zone))
            assertEquals(Occasion.EARTH_DAY, on("2027-04-22", zone))
            assertEquals(Occasion.HALLOWEEN, on("2026-10-31", zone))
            assertEquals(Occasion.HOLIDAYS, on("2026-12-24", zone))
            assertEquals(Occasion.HOLIDAYS, on("2026-12-25", zone))
            assertEquals(Occasion.NEW_YEARS_EVE, on("2026-12-31", zone))
        }
    }

    @Test
    fun leapDayOnlyInLeapYears() {
        assertEquals(Occasion.LEAP_DAY, on("2028-02-29"))
        assertNull(on("2027-02-28"))
        assertNull(on("2027-03-01"))
    }

    @Test
    fun ordinaryDaysHaveNoDoodle() {
        assertNull(on("2026-10-20"))
        assertNull(on("2026-03-03")) // the date unit tests pin (app/build.gradle.kts)
        assertNull(on("2026-12-26"))
        assertNull(on("2026-03-19"))
    }

    /** Published times (UTC) of the 2024 to 2026 equinoxes and solstices; the formula is good to a few minutes. */
    @Test
    fun seasonsStartWhenTheSunSaysSo() {
        val published = mapOf(
            (2024 to 3) to "2024-12-21T09:20:00Z",
            (2025 to 0) to "2025-03-20T09:01:00Z",
            (2025 to 1) to "2025-06-21T02:42:00Z",
            (2025 to 2) to "2025-09-22T18:19:00Z",
            (2025 to 3) to "2025-12-21T15:03:00Z",
            (2026 to 0) to "2026-03-20T14:46:00Z",
            (2026 to 1) to "2026-06-21T08:24:00Z",
            (2026 to 2) to "2026-09-23T00:05:00Z",
            (2026 to 3) to "2026-12-21T20:50:00Z"
        )
        for ((key, time) in published) {
            val (year, event) = key
            val error = Duration.between(Instant.parse(time), OccasionCalendar.seasonStart(year, event)).abs()
            assertTrue("$year event $event is off by $error", error <= Duration.ofMinutes(5))
        }
    }

    @Test
    fun theFirstDayOfASeasonIsTheLocalDateOfItsStart() {
        assertEquals(Occasion.FIRST_DAY_OF_SPRING, on("2026-03-20"))
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2026-06-21"))
        assertEquals(Occasion.FIRST_DAY_OF_WINTER, on("2026-12-21"))
        // The September 2026 equinox is just after midnight UTC: still the 22nd in New York, the 23rd in London.
        assertEquals(Occasion.FIRST_DAY_OF_AUTUMN, on("2026-09-22", newYork))
        assertNull(on("2026-09-23", newYork))
        assertEquals(Occasion.FIRST_DAY_OF_AUTUMN, on("2026-09-23", london))
        assertNull(on("2026-09-22", london))
    }

    @Test
    fun theSouthernHemisphereHasTheOtherSeason() {
        assertEquals(Occasion.FIRST_DAY_OF_AUTUMN, on("2026-03-21", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_WINTER, on("2026-06-21", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_SPRING, on("2026-09-23", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2026-12-22", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_SPRING, on("2026-09-22", ZoneId.of("America/Argentina/Buenos_Aires")))
    }

    @Test
    fun hemisphereFollowsTheTimeZone() {
        assertTrue(OccasionCalendar.isSouthern(ZoneId.of("Australia/Perth")))
        assertTrue(OccasionCalendar.isSouthern(ZoneId.of("Pacific/Auckland")))
        assertTrue(OccasionCalendar.isSouthern(ZoneId.of("America/Sao_Paulo")))
        assertTrue(OccasionCalendar.isSouthern(ZoneId.of("Africa/Johannesburg")))
        assertFalse(OccasionCalendar.isSouthern(newYork))
        assertFalse(OccasionCalendar.isSouthern(london))
        assertFalse(OccasionCalendar.isSouthern(ZoneId.of("Asia/Tokyo")))
        assertFalse(OccasionCalendar.isSouthern(ZoneId.of("UTC")))
    }

    @Test
    fun theDoodleNextChangesAtTheMidnightADoodleDayStartsOrEnds() {
        fun next(at: String) = OccasionCalendar.nextChange(LocalDate.parse(at).atTime(15, 30).atZone(newYork)).toString()
        assertEquals("2026-10-31T00:00-04:00[America/New_York]", next("2026-10-20"))
        assertEquals("2026-11-01T00:00-04:00[America/New_York]", next("2026-10-31"))
        // Christmas Eve and Day share a doodle, so it doesn't change in between.
        assertEquals("2026-12-26T00:00-05:00[America/New_York]", next("2026-12-24"))
        assertEquals("2027-01-02T00:00-05:00[America/New_York]", next("2027-01-01"))
    }

    @Test
    fun funDaysAreTheSameEverywhere() {
        for (region in listOf("US", "GB", "JP", "")) {
            assertEquals(Occasion.WOMENS_DAY, on("2027-03-08", region = region))
            assertEquals(Occasion.PI_DAY, on("2027-03-14", region = region))
            assertEquals(Occasion.ST_PATRICKS_DAY, on("2027-03-17", region = region))
            assertEquals(Occasion.APRIL_FOOLS_DAY, on("2027-04-01", region = region))
            assertEquals(Occasion.BEE_DAY, on("2027-05-20", region = region))
            assertEquals(Occasion.OCEANS_DAY, on("2027-06-08", region = region))
            // In Japan, July 7 is Tanabata (see childrensDayAndTanabataAreKeptInJapan).
            assertEquals(if (region == "JP") Occasion.TANABATA else Occasion.CHOCOLATE_DAY, on("2027-07-07", region = region))
            assertEquals(Occasion.EMOJI_DAY, on("2027-07-17", region = region))
            assertEquals(Occasion.MOON_DAY, on("2027-07-20", region = region))
            assertEquals(Occasion.CAT_DAY, on("2027-08-08", region = region))
            assertEquals(Occasion.DOG_DAY, on("2027-08-26", region = region))
            assertEquals(Occasion.PIRATE_DAY, on("2027-09-19", region = region))
            assertEquals(Occasion.COFFEE_DAY, on("2027-10-01", region = region))
            assertEquals(Occasion.TEACHERS_DAY, on("2027-10-05", region = region))
            assertEquals(Occasion.KINDNESS_DAY, on("2027-11-13", region = region))
        }
    }

    @Test
    fun easterFollowsTheComputus() {
        mapOf(2026 to "2026-04-05", 2027 to "2027-03-28", 2028 to "2028-04-16", 2029 to "2029-04-01", 2030 to "2030-04-21",
            2038 to "2038-04-25", 2285 to "2285-03-22")
            .forEach { (year, date) -> assertEquals(LocalDate.parse(date), OccasionCalendar.easter(year)) }
        assertEquals(Occasion.EASTER, on("2026-04-05"))
        // Easter on April 1 2029 wins over April Fools' Day.
        assertEquals(Occasion.EASTER, on("2029-04-01"))
    }

    @Test
    fun lunarNewYearComesFromItsTable() {
        assertEquals(Occasion.LUNAR_NEW_YEAR, on("2027-02-06"))
        assertEquals(Occasion.LUNAR_NEW_YEAR, on("2028-01-26"))
        assertNull(on("2027-02-07"))
    }

    @Test
    fun regionalDaysOnlyWhereTheyreCelebrated() {
        assertEquals(Occasion.INDEPENDENCE_DAY, on("2027-07-04", region = "US"))
        assertNull(on("2027-07-04", region = "GB"))
        assertEquals(Occasion.CANADA_DAY, on("2027-07-01", region = "CA"))
        assertNull(on("2027-07-01", region = "US"))
        assertEquals(Occasion.GROUNDHOG_DAY, on("2027-02-02", region = "US"))
        assertEquals(Occasion.GROUNDHOG_DAY, on("2027-02-02", region = "CA"))
        assertNull(on("2027-02-02", region = "FR"))
    }

    @Test
    fun familyAndHarvestDaysFollowTheRegionsCalendar() {
        // Thanksgiving: the fourth Thursday of November in the US, the second Monday of October in Canada.
        assertEquals(Occasion.THANKSGIVING, on("2026-11-26", region = "US"))
        assertEquals(Occasion.THANKSGIVING, on("2026-10-12", region = "CA"))
        assertNull(on("2026-11-26", region = "CA"))
        // Mother's Day: the second Sunday of May, or Mothering Sunday (three weeks before Easter) in the UK.
        assertEquals(Occasion.MOTHERS_DAY, on("2026-05-10", region = "US"))
        assertEquals(Occasion.MOTHERS_DAY, on("2026-03-15", region = "GB"))
        assertNull(on("2026-05-10", region = "GB"))
        assertNull(on("2026-05-10", region = "FR")) // France keeps it on another day
        // Father's Day: the third Sunday of June, or the first Sunday of September in Australia. In 2026 it's also
        // the June solstice; Father's Day wins.
        assertEquals(Occasion.FATHERS_DAY, on("2026-06-21", region = "US"))
        assertEquals(Occasion.FATHERS_DAY, on("2026-09-06", zone = sydney, region = "AU"))
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2026-06-21", region = "XX"))
    }

    // Global fun days.

    @Test
    fun globalFunDaysAreTheSameEverywhere() {
        for (zone in listOf(newYork, london, sydney)) {
            for (region in listOf("US", "GB", "JP", "")) {
                assertEquals(Occasion.HUGGING_DAY, on("2027-01-21", zone, region))
                // In 2027 these share their days with Pancake Day in Britain and Arbor Day in the US, which win there.
                assertEquals(if (region == "GB") Occasion.PANCAKE_DAY else Occasion.PIZZA_DAY, on("2027-02-09", zone, region))
                assertEquals(Occasion.BOOK_DAY, on("2027-04-23", zone, region))
                assertEquals(Occasion.PENGUIN_DAY, on("2027-04-25", zone, region))
                assertEquals(if (region == "US") Occasion.ARBOR_DAY else Occasion.JAZZ_DAY, on("2027-04-30", zone, region))
                assertEquals(Occasion.LIGHT_DAY, on("2027-05-16", zone, region))
                assertEquals(Occasion.BICYCLE_DAY, on("2027-06-03", zone, region))
                assertEquals(Occasion.FRIENDSHIP_DAY, on("2027-07-30", zone, region))
                assertEquals(Occasion.LION_DAY, on("2027-08-10", zone, region))
                assertEquals(Occasion.PHOTOGRAPHY_DAY, on("2027-08-19", zone, region))
                assertEquals(Occasion.HELLO_DAY, on("2027-11-21", zone, region))
            }
        }
        assertNull(on("2027-01-20"))
        assertNull(on("2027-11-20"))
    }

    @Test
    fun theDayOfTheProgrammerIsTheYearsDay256() {
        assertEquals(Occasion.PROGRAMMERS_DAY, on("2026-09-13"))
        assertNull(on("2026-09-12"))
        // A leap year's 256th day is a day earlier.
        assertEquals(Occasion.PROGRAMMERS_DAY, on("2028-09-12"))
        assertNull(on("2028-09-13"))
        assertEquals(Occasion.PROGRAMMERS_DAY, on("2028-09-12", sydney, "AU"))
    }

    @Test
    fun worldSmileDayIsTheFirstFridayOfOctoberUnlessThatsAnotherDoodleDay() {
        assertEquals(Occasion.SMILE_DAY, on("2026-10-02"))
        assertEquals(Occasion.SMILE_DAY, on("2028-10-06", london, "GB"))
        assertNull(on("2026-10-09"))
        // October 1 2027 is a Friday and Coffee Day; October 5 2029 is a Friday and Teachers' Day. They keep the day.
        assertEquals(Occasion.COFFEE_DAY, on("2027-10-01"))
        assertNull(on("2027-10-08"))
        assertEquals(Occasion.TEACHERS_DAY, on("2029-10-05"))
    }

    @Test
    fun theSeasonWinsWhenItStartsOnHappinessOrMusicDay() {
        // The March 2026 equinox is the 20th in New York but the 21st in Sydney.
        assertEquals(Occasion.FIRST_DAY_OF_SPRING, on("2026-03-20", newYork))
        assertEquals(Occasion.HAPPINESS_DAY, on("2026-03-20", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_AUTUMN, on("2026-03-21", sydney))
        // In 2028 it's the evening of the 19th in New York, so the 20th is Happiness Day there.
        assertEquals(Occasion.FIRST_DAY_OF_SPRING, on("2028-03-19", newYork))
        assertEquals(Occasion.HAPPINESS_DAY, on("2028-03-20", newYork))
        // The June 2026 solstice is the 21st almost everywhere; Father's Day still beats both where it's kept.
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2026-06-21", london))
        assertEquals(Occasion.FIRST_DAY_OF_WINTER, on("2026-06-21", sydney))
        assertEquals(Occasion.FATHERS_DAY, on("2026-06-21", newYork, "US"))
        // In 2028 it's the evening of the 20th in New York and London, so the 21st is World Music Day.
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2028-06-20", newYork))
        assertEquals(Occasion.MUSIC_DAY, on("2028-06-21", newYork))
        assertEquals(Occasion.MUSIC_DAY, on("2028-06-21", london, "GB"))
        // In 2027 it's just after midnight on the 22nd in Sydney.
        assertEquals(Occasion.MUSIC_DAY, on("2027-06-21", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_WINTER, on("2027-06-22", sydney))
    }

    @Test
    fun easterWinsOverWorldPenguinDay() {
        assertEquals(Occasion.EASTER, on("2038-04-25"))
        assertEquals(Occasion.PENGUIN_DAY, on("2039-04-25"))
    }

    @Test
    fun everyDoodleHasItsOwnArt() {
        assertEquals(Occasion.entries.size, Occasion.entries.map { it.layout }.toSet().size)
    }

    // Local festivals: each only in its own regions, and winning over anything else on its day.

    private val stockholm = ZoneId.of("Europe/Stockholm")
    private val helsinki = ZoneId.of("Europe/Helsinki")
    private val localRegions = listOf(
        "GB", "IE", "AU", "NZ", "CA", "BR", "TH", "US", "NL", "JP", "SE", "FI", "FR", "CN", "HK", "MO", "TW", "SG", "MY", "KR",
        "DE", "MX", "IN"
    )

    /** Every date from [from] to [to] on which [region] has [occasion]. */
    private fun daysOf(occasion: Occasion, region: String, from: String, to: String, zone: ZoneId = newYork): List<String> =
        generateSequence(LocalDate.parse(from)) { it.plusDays(1) }.takeWhile { it <= LocalDate.parse(to) }
            .filter { OccasionCalendar.on(it, zone, region) == occasion }.map { it.toString() }.toList()

    @Test
    fun theDateUnitTestsPinIsOrdinaryInEveryRegion() {
        for (region in localRegions + listOf("XX", "")) assertNull(region, on("2026-03-03", region = region))
    }

    @Test
    fun pancakeDayIsShroveTuesdayInBritainIrelandAustraliaNewZealandAndCanada() {
        // 47 days before Easter: Easter 2027 is March 28, 2030 April 21, 2038 April 25.
        for (region in listOf("GB", "IE", "AU", "NZ", "CA")) {
            assertEquals(region, Occasion.PANCAKE_DAY, on("2027-02-09", region = region))
            assertEquals(region, Occasion.PANCAKE_DAY, on("2030-03-05", region = region))
            assertEquals(region, Occasion.PANCAKE_DAY, on("2038-03-09", region = region))
        }
        assertEquals(listOf("2027-02-09"), daysOf(Occasion.PANCAKE_DAY, "GB", "2027-01-01", "2027-12-31"))
        // Elsewhere that day is National Pizza Day.
        assertEquals(Occasion.PIZZA_DAY, on("2027-02-09", region = "US"))
        assertEquals(Occasion.PIZZA_DAY, on("2027-02-09", region = "FR"))
        assertNull(on("2030-03-05", region = "FR"))
        // It wins over Lunar New Year (2026), Leap Day (2028) and Valentine's Day (2040).
        assertEquals(Occasion.PANCAKE_DAY, on("2026-02-17", region = "GB"))
        assertEquals(Occasion.LUNAR_NEW_YEAR, on("2026-02-17", region = "US"))
        assertEquals(Occasion.PANCAKE_DAY, on("2028-02-29", region = "IE"))
        assertEquals(Occasion.LEAP_DAY, on("2028-02-29", region = "US"))
        assertEquals(Occasion.PANCAKE_DAY, on("2040-02-14", region = "AU", zone = sydney))
        assertEquals(Occasion.VALENTINES_DAY, on("2040-02-14", region = "US"))
    }

    @Test
    fun carnivalIsShroveTuesdayInBrazil() {
        assertEquals(Occasion.CARNIVAL, on("2026-02-17", region = "BR"))
        assertEquals(Occasion.CARNIVAL, on("2027-02-09", region = "BR"))
        assertEquals(Occasion.CARNIVAL, on("2029-02-13", region = "BR"))
        assertEquals(listOf("2028-02-29"), daysOf(Occasion.CARNIVAL, "BR", "2028-01-01", "2028-12-31"))
        assertEquals(Occasion.PIZZA_DAY, on("2027-02-09", region = "PT"))
        // In 2029 it's also Lunar New Year, which Brazil's Carnival and Britain's Pancake Day win over.
        assertEquals(Occasion.PANCAKE_DAY, on("2029-02-13", region = "GB"))
        assertEquals(Occasion.LUNAR_NEW_YEAR, on("2029-02-13", region = "PT"))
    }

    @Test
    fun songkranIsApril13InThailand() {
        assertEquals(Occasion.SONGKRAN, on("2026-04-13", region = "TH"))
        assertEquals(Occasion.SONGKRAN, on("2027-04-13", region = "TH"))
        assertNull(on("2026-04-14", region = "TH"))
        assertNull(on("2026-04-13", region = "US"))
        // Easter falls on April 13 in 2031 and 2036; in Thailand Songkran wins.
        assertEquals(Occasion.SONGKRAN, on("2031-04-13", region = "TH"))
        assertEquals(Occasion.EASTER, on("2031-04-13", region = "US"))
        assertEquals(Occasion.SONGKRAN, on("2036-04-13", region = "TH"))
    }

    @Test
    fun arborDayIsTheLastFridayOfAprilInTheUs() {
        assertEquals(listOf("2026-04-24"), daysOf(Occasion.ARBOR_DAY, "US", "2026-01-01", "2026-12-31"))
        assertEquals(Occasion.ARBOR_DAY, on("2027-04-30", region = "US"))
        assertEquals(Occasion.ARBOR_DAY, on("2028-04-28", region = "US"))
        assertNull(on("2027-04-16", region = "US"))
        assertNull(on("2026-04-24", region = "CA"))
    }

    @Test
    fun kingsDayIsApril27InTheNetherlandsOrThe26thWhenThe27thIsASunday() {
        assertEquals(Occasion.KINGS_DAY, on("2026-04-27", region = "NL"))
        assertEquals(Occasion.KINGS_DAY, on("2027-04-27", region = "NL"))
        // April 27 is a Sunday in 2031 and 2036.
        assertEquals(listOf("2031-04-26"), daysOf(Occasion.KINGS_DAY, "NL", "2031-01-01", "2031-12-31"))
        assertEquals(listOf("2036-04-26"), daysOf(Occasion.KINGS_DAY, "NL", "2036-01-01", "2036-12-31"))
        assertNull(on("2026-04-27", region = "BE"))
    }

    @Test
    fun childrensDayAndTanabataAreKeptInJapan() {
        assertEquals(Occasion.CHILDRENS_DAY, on("2026-05-05", region = "JP"))
        assertEquals(Occasion.CHILDRENS_DAY, on("2027-05-05", region = "JP"))
        assertNull(on("2026-05-05", region = "US"))
        assertEquals(Occasion.TANABATA, on("2026-07-07", region = "JP"))
        assertEquals(Occasion.TANABATA, on("2027-07-07", region = "JP"))
        // Tanabata shares July 7 with World Chocolate Day, which everyone else gets.
        assertEquals(Occasion.CHOCOLATE_DAY, on("2026-07-07", region = "US"))
        assertEquals(Occasion.CHOCOLATE_DAY, on("2026-07-07", region = "CN"))
    }

    @Test
    fun midsummerIsAFridayInSwedenAndASaturdayInFinland() {
        // Sweden: the Friday from June 19 to 25. Finland: the Saturday from June 20 to 26.
        assertEquals(listOf("2026-06-19"), daysOf(Occasion.MIDSUMMER, "SE", "2026-01-01", "2026-12-31", stockholm))
        assertEquals(listOf("2027-06-25"), daysOf(Occasion.MIDSUMMER, "SE", "2027-01-01", "2027-12-31", stockholm))
        assertEquals(listOf("2028-06-23"), daysOf(Occasion.MIDSUMMER, "SE", "2028-01-01", "2028-12-31", stockholm))
        assertEquals(listOf("2026-06-20"), daysOf(Occasion.MIDSUMMER, "FI", "2026-01-01", "2026-12-31", helsinki))
        assertEquals(listOf("2027-06-26"), daysOf(Occasion.MIDSUMMER, "FI", "2027-01-01", "2027-12-31", helsinki))
        assertEquals(listOf("2028-06-24"), daysOf(Occasion.MIDSUMMER, "FI", "2028-01-01", "2028-12-31", helsinki))
        assertNull(on("2026-06-19", stockholm, "NO"))
        // When it falls on the solstice it wins over the first day of summer.
        assertEquals(Occasion.MIDSUMMER, on("2030-06-21", stockholm, "SE"))
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2030-06-21", stockholm, "XX"))
        assertEquals(Occasion.MIDSUMMER, on("2031-06-21", helsinki, "FI"))
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2031-06-21", helsinki, "XX"))
    }

    @Test
    fun bastilleDayIsJuly14InFrance() {
        assertEquals(Occasion.BASTILLE_DAY, on("2026-07-14", region = "FR"))
        assertEquals(Occasion.BASTILLE_DAY, on("2027-07-14", region = "FR"))
        assertNull(on("2026-07-14", region = "GB"))
    }

    @Test
    fun iceCreamDayIsTheThirdSundayOfJulyInTheUs() {
        assertEquals(listOf("2026-07-19"), daysOf(Occasion.ICE_CREAM_DAY, "US", "2026-01-01", "2026-12-31"))
        assertEquals(Occasion.ICE_CREAM_DAY, on("2027-07-18", region = "US"))
        assertEquals(Occasion.ICE_CREAM_DAY, on("2028-07-16", region = "US"))
        assertNull(on("2026-07-19", region = "GB"))
        // It wins over Moon Day in 2031 and World Emoji Day in 2033.
        assertEquals(Occasion.ICE_CREAM_DAY, on("2031-07-20", region = "US"))
        assertEquals(Occasion.MOON_DAY, on("2031-07-20", region = "GB"))
        assertEquals(Occasion.ICE_CREAM_DAY, on("2033-07-17", region = "US"))
        assertEquals(Occasion.EMOJI_DAY, on("2033-07-17", region = "GB"))
    }

    /** The 15th day of the 8th lunar month, from the Hong Kong Observatory's conversion tables. */
    private val midAutumn = listOf(
        "2026-09-25", "2027-09-15", "2028-10-03", "2029-09-22", "2030-09-12", "2031-10-01", "2032-09-19", "2033-09-08",
        "2034-09-27", "2035-09-16", "2036-10-04", "2037-09-24", "2038-09-13", "2039-10-02", "2040-09-20"
    )

    @Test
    fun theMidAutumnFestivalFollowsTheChineseCalendarWhereItsKept() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        assertEquals(midAutumn, daysOf(Occasion.MID_AUTUMN_FESTIVAL, "CN", "2026-01-01", "2041-12-31", shanghai))
        for (region in listOf("HK", "MO", "TW", "SG", "MY")) {
            assertEquals(region, Occasion.MID_AUTUMN_FESTIVAL, on("2026-09-25", shanghai, region))
        }
        assertNull(on("2026-09-25", region = "US"))
        assertNull(on("2026-09-25", region = "JP"))
        // It wins over International Coffee Day in 2031 and Talk Like a Pirate Day in 2032.
        assertEquals(Occasion.MID_AUTUMN_FESTIVAL, on("2031-10-01", shanghai, "CN"))
        assertEquals(Occasion.COFFEE_DAY, on("2031-10-01", region = "US"))
        assertEquals(Occasion.MID_AUTUMN_FESTIVAL, on("2032-09-19", shanghai, "TW"))
    }

    @Test
    fun chuseokIsTheSameLunarDateOnTheKoreanCalendar() {
        val seoul = ZoneId.of("Asia/Seoul")
        // The same days, except 2040: the new moon that starts the month is a little after midnight in Seoul.
        val chuseok = midAutumn.dropLast(1) + "2040-09-21"
        assertEquals(chuseok, daysOf(Occasion.CHUSEOK, "KR", "2026-01-01", "2041-12-31", seoul))
        assertNull(on("2040-09-20", seoul, "KR"))
        assertEquals(Occasion.MID_AUTUMN_FESTIVAL, on("2040-09-20", seoul, "CN"))
        assertNull(on("2026-09-25", seoul, "JP"))
    }

    @Test
    fun germanUnityDayIsOctober3InGermany() {
        assertEquals(Occasion.GERMAN_UNITY_DAY, on("2026-10-03", region = "DE"))
        assertEquals(Occasion.GERMAN_UNITY_DAY, on("2027-10-03", region = "DE"))
        assertNull(on("2026-10-03", region = "AT"))
    }

    @Test
    fun dayOfTheDeadIsNovember2InMexico() {
        assertEquals(Occasion.DAY_OF_THE_DEAD, on("2026-11-02", region = "MX"))
        assertEquals(Occasion.DAY_OF_THE_DEAD, on("2027-11-02", region = "MX"))
        assertNull(on("2026-11-01", region = "MX"))
        assertNull(on("2026-11-02", region = "US"))
    }

    @Test
    fun diwaliFollowsItsTableInIndia() {
        // Lakshmi Puja in New Delhi per Drik Panchang and AstroSage; no doodle after 2035.
        val diwali = listOf(
            "2026-11-08", "2027-10-29", "2028-10-17", "2029-11-05", "2030-10-26", "2031-11-14", "2032-11-02", "2033-10-22",
            "2034-11-10", "2035-10-30"
        )
        val kolkata = ZoneId.of("Asia/Kolkata")
        assertEquals(diwali, daysOf(Occasion.DIWALI, "IN", "2026-01-01", "2040-12-31", kolkata))
        assertNull(on("2026-11-08", region = "US"))
        // The doodle changes for it at midnight in India, earlier than for Veterans Day or World Kindness Day elsewhere.
        val from = LocalDate.parse("2026-11-03").atTime(9, 0).atZone(kolkata)
        fun next(region: String) = OccasionCalendar.nextChange(from, region).toLocalDate().toString()
        assertEquals("2026-11-05", next("GB"))
        assertEquals("2026-11-08", next("IN"))
        assertEquals("2026-11-11", next("US"))
        assertEquals("2026-11-13", next("FR"))
    }

    @Test
    fun bonfireNightIsNovember5InBritain() {
        assertEquals(Occasion.BONFIRE_NIGHT, on("2026-11-05", london, "GB"))
        assertEquals(Occasion.BONFIRE_NIGHT, on("2027-11-05", london, "GB"))
        assertNull(on("2026-11-05", region = "IE"))
        // Diwali falls on November 5 in 2029; each region gets its own.
        assertEquals(Occasion.BONFIRE_NIGHT, on("2029-11-05", london, "GB"))
        assertEquals(Occasion.DIWALI, on("2029-11-05", region = "IN"))
    }

    @Test
    fun localFestivalsWinOverGlobalFunDays() {
        // Arbor Day over World Penguin Day (April 25 2031) and International Jazz Day (April 30 2027).
        assertEquals(Occasion.ARBOR_DAY, on("2031-04-25", region = "US"))
        assertEquals(Occasion.PENGUIN_DAY, on("2031-04-25", region = "GB"))
        // German Unity Day over World Smile Day, the first Friday of October, in 2031 and 2036.
        assertEquals(Occasion.GERMAN_UNITY_DAY, on("2031-10-03", region = "DE"))
        assertEquals(Occasion.SMILE_DAY, on("2031-10-03", region = "FR"))
        assertEquals(Occasion.GERMAN_UNITY_DAY, on("2036-10-03", region = "DE"))
        // The Mid-Autumn Festival and Chuseok over the Day of the Programmer (September 13 2038).
        val shanghai = ZoneId.of("Asia/Shanghai")
        assertEquals(Occasion.MID_AUTUMN_FESTIVAL, on("2038-09-13", shanghai, "CN"))
        assertEquals(Occasion.CHUSEOK, on("2038-09-13", ZoneId.of("Asia/Seoul"), "KR"))
        assertEquals(Occasion.PROGRAMMERS_DAY, on("2038-09-13", region = "US"))
        // Midsummer over World Music Day: June 21 2036 is a Saturday, after the solstice on the 20th.
        assertEquals(Occasion.MIDSUMMER, on("2036-06-21", helsinki, "FI"))
        assertEquals(Occasion.MUSIC_DAY, on("2036-06-21", helsinki, "XX"))
    }

    // US federal holidays, by 5 U.S.C. 6103, shown only in the US.

    @Test
    fun usFederalHolidaysFollowTheirRules() {
        // Martin Luther King Jr. Day: the third Monday of January.
        assertEquals(listOf("2026-01-19"), daysOf(Occasion.MLK_DAY, "US", "2026-01-01", "2026-12-31"))
        assertEquals(Occasion.MLK_DAY, on("2027-01-18", region = "US"))
        assertEquals(Occasion.MLK_DAY, on("2028-01-17", region = "US"))
        // Presidents' Day (Washington's Birthday): the third Monday of February.
        assertEquals(listOf("2026-02-16"), daysOf(Occasion.PRESIDENTS_DAY, "US", "2026-01-01", "2026-12-31"))
        assertEquals(Occasion.PRESIDENTS_DAY, on("2027-02-15", region = "US"))
        assertEquals(Occasion.PRESIDENTS_DAY, on("2028-02-21", region = "US"))
        // Memorial Day: the last Monday of May.
        assertEquals(listOf("2026-05-25"), daysOf(Occasion.MEMORIAL_DAY, "US", "2026-01-01", "2026-12-31"))
        assertEquals(Occasion.MEMORIAL_DAY, on("2027-05-31", region = "US"))
        assertEquals(Occasion.MEMORIAL_DAY, on("2028-05-29", region = "US"))
        // Juneteenth: June 19, whatever the weekday.
        assertEquals(listOf("2026-06-19"), daysOf(Occasion.JUNETEENTH, "US", "2026-01-01", "2026-12-31"))
        assertEquals(Occasion.JUNETEENTH, on("2027-06-19", region = "US"))
        // Labor Day: the first Monday of September.
        assertEquals(listOf("2026-09-07"), daysOf(Occasion.LABOR_DAY, "US", "2026-01-01", "2026-12-31"))
        assertEquals(Occasion.LABOR_DAY, on("2027-09-06", region = "US"))
        assertEquals(Occasion.LABOR_DAY, on("2028-09-04", region = "US"))
        // Veterans Day: November 11, whatever the weekday.
        assertEquals(listOf("2026-11-11"), daysOf(Occasion.VETERANS_DAY, "US", "2026-01-01", "2026-12-31"))
        assertEquals(Occasion.VETERANS_DAY, on("2028-11-11", region = "US"))
    }

    @Test
    fun usFederalHolidaysAreOnlyInTheUs() {
        for (region in listOf("CA", "GB", "MX", "")) {
            assertNull(region, on("2026-01-19", region = region))
            assertNull(region, on("2026-02-16", region = region))
            assertNull(region, on("2026-05-25", region = region))
            assertNull(region, on("2026-06-19", region = region))
            assertNull(region, on("2026-09-07", region = region))
            assertNull(region, on("2026-11-11", region = region))
        }
    }

    @Test
    fun usFederalHolidaysWinOverOtherDaysInTheUs() {
        // Juneteenth is Father's Day too in 2033 and 2039, when June 19 is the third Sunday of June.
        assertEquals(Occasion.JUNETEENTH, on("2033-06-19", region = "US"))
        assertEquals(Occasion.JUNETEENTH, on("2039-06-19", region = "US"))
        assertEquals(Occasion.FATHERS_DAY, on("2033-06-19", region = "GB"))
        // Martin Luther King Jr. Day is National Hugging Day too in 2030 and 2036.
        assertEquals(Occasion.MLK_DAY, on("2030-01-21", region = "US"))
        assertEquals(Occasion.MLK_DAY, on("2036-01-21", region = "US"))
        assertEquals(Occasion.HUGGING_DAY, on("2030-01-21", region = "GB"))
    }
}
