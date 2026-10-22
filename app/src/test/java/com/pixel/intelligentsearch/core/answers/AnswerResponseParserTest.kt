package com.pixel.intelligentsearch.core.answers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** The dictionary and weather services' answers, read from responses saved from the real services. */
class AnswerResponseParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("answers/$name")) { "missing fixture $name" }.readText()

    // ---------------------------------------------------------------------------------------------
    // Free Dictionary API
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aDefinitionWithItsPhoneticAndSource() {
        val entry = requireNotNull(DictionaryResponseParser.parse(fixture("dictionary_serendipity.json")))
        assertEquals("serendipity", entry.word)
        assertEquals("/ˌsɛ.ɹən.ˈdɪ.pɪ.ti/", entry.phonetic)
        assertEquals(2, entry.senses.size)
        assertEquals("noun", entry.senses[0].partOfSpeech)
        assertTrue(entry.senses[0].definition.startsWith("A combination of events which have come together by chance"))
        assertNull(entry.senses[0].example)
        assertEquals("https://en.wiktionary.org/wiki/serendipity", entry.sourceUrl)
    }

    @Test
    fun theTopSensesCoverEachPartOfSpeechBeforeASecondOfAny() {
        val entry = requireNotNull(DictionaryResponseParser.parse(fixture("dictionary_hello.json")))
        // No top-level phonetic here: the first phonetic with text stands in.
        assertEquals("/həˈləʊ/", entry.phonetic)
        assertEquals(listOf("noun", "verb", "interjection"), entry.senses.take(3).map { it.partOfSpeech })
        assertEquals("Hello, everyone.", entry.senses[2].example)
        assertEquals(7, entry.senses.size)
        assertEquals(listOf("interjection", "interjection"), entry.senses.drop(3).take(2).map { it.partOfSpeech })
    }

    @Test
    fun notFoundAndBrokenAnswersHaveNoDefinition() {
        assertNull(DictionaryResponseParser.parse(fixture("dictionary_not_found.json")))
        assertNull(DictionaryResponseParser.parse("error code: 1033"))
        assertNull(DictionaryResponseParser.parse("[]"))
        assertNull(DictionaryResponseParser.parse("[{\"word\":\"x\",\"meanings\":[]}]"))
        assertNull(DictionaryResponseParser.parse(""))
    }

    @Test
    fun theLookupAddressIsHttpsAndEncoded() {
        assertEquals("https://api.dictionaryapi.dev/api/v2/entries/en/serendipity", DictionaryResponseParser.urlFor("Serendipity"))
        assertEquals("https://api.dictionaryapi.dev/api/v2/entries/en/ice%20cream", DictionaryResponseParser.urlFor("ice cream"))
        assertEquals("https://api.dictionaryapi.dev/api/v2/entries/en/caf%C3%A9", DictionaryResponseParser.urlFor("café"))
    }

    // ---------------------------------------------------------------------------------------------
    // Open-Meteo
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aForecastWithConditionsNowHoursAndDays() {
        val data = requireNotNull(OpenMeteoParser.parseForecast(fixture("openmeteo_forecast_johnson_city.json")))
        assertEquals(LocalDateTime.of(2026, 10, 22, 13, 15), data.now)
        assertEquals(62.5, data.temperature, 0.0)
        assertEquals(60.8, data.feelsLike!!, 0.0)
        assertEquals(0, data.code)
        assertTrue(data.isDay)
        assertEquals(48, data.hourly.size)
        assertEquals(listOf(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23)), data.daily.map { it.date })
        assertEquals(68.4, data.daily[0].high, 0.0)
        assertEquals(38.9, data.daily[0].low, 0.0)
    }

    @Test
    fun todaysReportShowsTheNextSixHours() {
        val data = requireNotNull(OpenMeteoParser.parseForecast(fixture("openmeteo_forecast_johnson_city.json")))
        val report = requireNotNull(WeatherReport.from(data, "Johnson City, Tennessee", TemperatureUnit.FAHRENHEIT, ForecastDay.TODAY))
        assertEquals(62.5, report.temperature, 0.0)
        assertEquals(68.4, report.high, 0.0)
        assertEquals(38.9, report.low, 0.0)
        assertEquals((14..19).toList(), report.hours.map { it.time.hour })
        assertEquals(listOf(64.5, 66.4, 68.1, 68.4, 65.9, 59.8), report.hours.map { it.temperature })
        // Seven o'clock is after sunset in late October.
        assertEquals(false, report.hours.last().isDay)
    }

    @Test
    fun tomorrowsReportShowsTheDayEveryThreeHours() {
        val data = requireNotNull(OpenMeteoParser.parseForecast(fixture("openmeteo_forecast_johnson_city.json")))
        val report = requireNotNull(WeatherReport.from(data, "Home", TemperatureUnit.FAHRENHEIT, ForecastDay.TOMORROW))
        assertEquals(69.8, report.high, 0.0)
        assertEquals(40.2, report.low, 0.0)
        assertNull(report.feelsLike)
        assertEquals(listOf(6, 9, 12, 15, 18, 21), report.hours.map { it.time.hour })
        assertTrue(report.hours.all { it.time.toLocalDate() == LocalDate.of(2026, 10, 23) })
        assertEquals(listOf(40.6, 43.8, 64.6, 69.3, 66.7, 54.1), report.hours.map { it.temperature })
    }

    @Test
    fun brokenForecastsHaveNoReport() {
        assertNull(OpenMeteoParser.parseForecast("{}"))
        assertNull(OpenMeteoParser.parseForecast("{\"current\":{\"time\":\"not a time\"}}"))
        assertNull(OpenMeteoParser.parseForecast("<html>"))
    }

    @Test
    fun placesFoundByName() {
        val places = requireNotNull(OpenMeteoParser.parsePlaces(fixture("openmeteo_geocoding_johnson_city.json")))
        assertEquals(3, places.size)
        assertEquals("Johnson City, Texas, United States", places[0].fullName)
        assertEquals("Johnson City, Tennessee", places[1].shortName)
        assertEquals(36.31344, places[1].latitude, 0.0)
        assertEquals(-82.35347, places[1].longitude, 0.0)
        assertEquals(66027L, places[1].population)
    }

    @Test
    fun noPlacesIsAnEmptyListAndGarbageIsNull() {
        assertEquals(emptyList<GeoPlace>(), OpenMeteoParser.parsePlaces(fixture("openmeteo_geocoding_none.json")))
        assertNull(OpenMeteoParser.parsePlaces("error"))
    }

    @Test
    fun forecastRequestsCarryOnlyRoundedCoordinatesAndTheUnit() {
        val url = OpenMeteoParser.forecastUrl(36.31344, -82.35347, TemperatureUnit.FAHRENHEIT)
        assertTrue(url, url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=36.31&longitude=-82.35&"))
        assertTrue(url.contains("&temperature_unit=fahrenheit"))
        assertTrue(!OpenMeteoParser.forecastUrl(48.8566, 2.3522, TemperatureUnit.CELSIUS).contains("temperature_unit"))
        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=Johnson%20City&count=5&language=en&format=json",
            OpenMeteoParser.geocodeUrl(" Johnson City ", "en")
        )
    }

    @Test
    fun fahrenheitWhereItIsReadAndCelsiusElsewhere() {
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.forLocale(Locale.US))
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.forLocale(Locale.forLanguageTag("es-US")))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.forLocale(Locale.UK))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.forLocale(Locale.CANADA))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.forLocale(Locale.GERMANY))
    }

    @Test
    fun theHomeLocationIsStoredRoundedAndReadBack() {
        val place = GeoPlace("Johnson City", "Tennessee", "United States", 36.31344, -82.35347)
        val stored = HomeLocationCodec.encode(place)
        assertEquals("36.31,-82.35;Johnson City, Tennessee", stored)
        val back = requireNotNull(HomeLocationCodec.decode(stored))
        assertEquals("Johnson City, Tennessee", back.shortName)
        assertEquals(36.31, back.latitude, 0.0)
        assertNull(HomeLocationCodec.decode(""))
        assertNull(HomeLocationCodec.decode("91,0;Nowhere"))
        assertNull(HomeLocationCodec.decode("1,2;"))
        assertNull(HomeLocationCodec.decode("garbage"))
    }

    @Test
    fun weatherCodesInWords() {
        assertEquals("Clear", WeatherCodes.describe(0))
        assertEquals("Partly Cloudy", WeatherCodes.describe(2))
        assertEquals("Thunderstorms with Hail", WeatherCodes.describe(99))
        assertEquals(WeatherCodes.Kind.SNOW, WeatherCodes.kind(75))
        assertEquals(WeatherCodes.Kind.CLOUDY, WeatherCodes.kind(12345))
    }
}
