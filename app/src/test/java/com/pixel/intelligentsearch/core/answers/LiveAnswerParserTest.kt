package com.pixel.intelligentsearch.core.answers

import com.pixel.intelligentsearch.core.answers.LiveAnswerQuery.Define
import com.pixel.intelligentsearch.core.answers.LiveAnswerQuery.ScreenTime
import com.pixel.intelligentsearch.core.answers.LiveAnswerQuery.Translate
import com.pixel.intelligentsearch.core.answers.LiveAnswerQuery.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Which queries are read as instant-answer questions, and which are left as ordinary searches. */
class LiveAnswerParserTest {

    private fun parse(query: String) = LiveAnswerParser.parse(query)

    // ---------------------------------------------------------------------------------------------
    // Definitions
    // ---------------------------------------------------------------------------------------------

    @Test
    fun definitionsAskedForInSoManyWords() {
        for (query in listOf(
            "define serendipity", "Define Serendipity", "definition of serendipity", "meaning of serendipity",
            "serendipity meaning", "serendipity definition", "what does serendipity mean", "what does serendipity mean?",
            "what's the meaning of serendipity", "what is the meaning of serendipity", "define \"serendipity\"",
            "  define   serendipity  "
        )) {
            assertEquals(query, Define("serendipity", explicit = true), parse(query))
        }
    }

    @Test
    fun aSingleWordWithAQuestionMarkIsADefinitionButNotAnExplicitOne() {
        assertEquals(Define("serendipity", explicit = false), parse("serendipity?"))
        assertEquals(Define("ephemeral", explicit = false), parse("Ephemeral?"))
    }

    @Test
    fun shortPhrasesCanBeDefinedButSentencesCannot() {
        assertEquals(Define("ice cream", explicit = true), parse("define ice cream"))
        assertEquals(Define("hello", explicit = true), parse("define hello"))
        assertNull(parse("define the word that means a happy accident"))
        assertNull(parse("define"))
        assertNull(parse("define 42"))
        assertNull(parse("meaning"))
    }

    @Test
    fun questionsThatAreNotWordsToLookUpStaySearches() {
        for (query in listOf(
            "why?", "what?", "hello?", "ok?", "what does it mean", "serendipity", "two words?", "c3po?", "a?",
            "is it raining?", "spotify"
        )) {
            assertNull(query, parse(query))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Weather
    // ---------------------------------------------------------------------------------------------

    @Test
    fun weatherHereTodayAndTomorrow() {
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("Weather today"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather now"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("what's the weather"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("what's the weather like?"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather forecast"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("today's weather"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("local weather"))
        assertEquals(Weather(null, ForecastDay.TOMORROW), parse("weather tomorrow"))
        assertEquals(Weather(null, ForecastDay.TOMORROW), parse("tomorrow's weather"))
        assertEquals(Weather(null, ForecastDay.TOMORROW), parse("what's the weather like tomorrow"))
    }

    @Test
    fun weatherForANamedPlace() {
        assertEquals(Weather("johnson city", ForecastDay.TODAY), parse("weather in Johnson City"))
        assertEquals(Weather("johnson city", ForecastDay.TODAY), parse("weather johnson city"))
        assertEquals(Weather("johnson city", ForecastDay.TODAY), parse("Johnson City weather"))
        assertEquals(Weather("johnson city", ForecastDay.TOMORROW), parse("weather in johnson city tomorrow"))
        assertEquals(Weather("johnson city", ForecastDay.TOMORROW), parse("weather tomorrow in johnson city"))
        assertEquals(Weather("johnson city", ForecastDay.TOMORROW), parse("johnson city weather tomorrow"))
        assertEquals(Weather("paris, france", ForecastDay.TODAY), parse("weather in Paris, France"))
        assertEquals(Weather("37601", ForecastDay.TODAY), parse("weather 37601"))
        assertEquals(Weather("st. louis", ForecastDay.TODAY), parse("weather for St. Louis"))
    }

    @Test
    fun weatherNearMeAndItsLikesAreTheForecastForHereNotAPlace() {
        for (query in listOf(
            "weather near me", "weather here", "weather outside", "weather around me", "weather in my area",
            "weather at my location", "weather nearby", "nearby weather", "what's the weather like outside",
            "weather around here", "whats the weather near me", "weather close to me", "weather at my current location"
        )) {
            assertEquals(query, Weather(null, ForecastDay.TODAY), parse(query))
        }
        assertEquals(Weather(null, ForecastDay.TOMORROW), parse("weather near me tomorrow"))
        assertEquals(Weather(null, ForecastDay.TOMORROW), parse("weather tomorrow near me"))
        assertEquals(Weather("london", ForecastDay.TODAY), parse("weather here in london"))
    }

    @Test
    fun weatherDayPartsAndScalesAreNotPlaces() {
        assertEquals(Weather(null, ForecastDay.TOMORROW), parse("weather tomorrow morning"))
        assertEquals(Weather(null, ForecastDay.TOMORROW), parse("weather for tomorrow"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather this evening"))
        assertEquals(Weather("paris", ForecastDay.TOMORROW), parse("weather in paris tomorrow night"))
        assertEquals(Weather(null, ForecastDay.TODAY, TemperatureUnit.CELSIUS), parse("weather in celsius"))
        assertEquals(Weather(null, ForecastDay.TODAY, TemperatureUnit.CELSIUS), parse("weather celsius"))
        assertEquals(Weather(null, ForecastDay.TODAY, TemperatureUnit.FAHRENHEIT), parse("weather in fahrenheit"))
        assertEquals(Weather("paris", ForecastDay.TODAY, TemperatureUnit.CELSIUS), parse("weather in paris in celsius"))
    }

    @Test
    fun weatherOverManyDaysStaysASearchRatherThanAPlace() {
        // The card shows one day, so a stretch of days is left to the web search, as "weather 10 day" is.
        for (query in listOf(
            "weather 10 day forecast", "weather the weekend", "weather for the weekend", "weather next 7 days",
            "weather this week", "weather in paris this weekend", "10 day weather forecast"
        )) {
            assertNull(query, parse(query))
        }
    }

    @Test
    fun weatherThingsThatAreNotPlacesStaySearches() {
        for (query in listOf(
            "weather radar", "weather channel", "the weather channel", "weather app", "weather underground",
            "weather alerts", "weather map", "weatherman", "bad weather", "weather 10 day"
        )) {
            assertNull(query, parse(query))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Translation
    // ---------------------------------------------------------------------------------------------

    @Test
    fun translationsWithTheTargetNamed() {
        assertEquals(Translate("hello", "es", null), parse("translate hello to spanish"))
        assertEquals(Translate("hello", "es", null), parse("translate hello into Spanish"))
        assertEquals(Translate("Good Morning", "fr", null), parse("translate Good Morning to french"))
        assertEquals(Translate("hola", "en", null), parse("hola in english"))
        assertEquals(Translate("thank you", "fr", null), parse("thank you in french"))
        assertEquals(Translate("thank you", "ja", null), parse("how do you say thank you in japanese?"))
        assertEquals(Translate("cat", "de", null), parse("what is cat in german"))
        assertEquals(Translate("gato", "en", "es"), parse("translate gato from spanish to english"))
        assertEquals(Translate("bonjour", "en", null), parse("translate \"bonjour\" to English language"))
        assertEquals(Translate("hello", "es", null), parse("translate hello to español"))
    }

    @Test
    fun translationsKeepTheTextAsTyped() {
        assertEquals(Translate("Where Is The Station", "de", null), parse("Where Is The Station in German"))
        assertEquals(Translate("I'm Late", "it", null), parse("translate I'm Late to italian"))
    }

    @Test
    fun translateWithoutATargetMeansIntoThePhonesLanguage() {
        assertEquals(Translate("bonjour tout le monde", null, null), parse("translate bonjour tout le monde"))
    }

    @Test
    fun placesAndNonLanguagesAreNotTranslations() {
        assertEquals(Weather("paris", ForecastDay.TODAY), parse("weather in paris"))
        // "Japan" is a place, "Japanese" the language.
        assertNull(parse("made in japan"))
        assertNull(parse("100 usd in eur"))
        assertNull(parse("in french"))
        assertNull(parse("translate"))
        assertNull(parse("translate english"))
        assertNull(parse("translate hello to klingon").let { it as? Translate }?.targetLanguage)
    }

    @Test
    fun searchesForThingsInALanguageAreNotTranslations() {
        for (query in listOf(
            "harry potter in hindi", "songs in spanish", "movies in tamil", "learn python in english",
            "news in hindi", "best places to eat in italian", "cartoons in french"
        )) {
            assertNull(query, parse(query))
        }
        // Asking for a translation in so many words still translates them.
        assertEquals(Translate("harry potter", "hi", null), parse("translate harry potter to hindi"))
        assertEquals(Translate("songs", "es", null), parse("what is songs in spanish"))
        assertEquals(Translate("love", "hi", null), parse("love meaning in hindi"))
        assertEquals(Translate("good morning", "es", null), parse("good morning in spanish"))
        assertEquals(Translate("i love you", "ko", null), parse("i love you in korean"))
    }

    @Test
    fun aLanguageNameWinsOverAPlace() {
        // "weather in french" asks for a word, not a forecast in a town called French.
        assertEquals(Translate("weather", "fr", null), parse("weather in french"))
    }

    @Test
    fun theTextToTranslateIsCapped() {
        val long = "a".repeat(LiveAnswerParser.MAX_TRANSLATE_CHARS + 1)
        assertNull(parse("translate $long to spanish"))
    }

    @Test
    fun theLanguagesAQueryCanNameStayTheSame() {
        // The same languages as before translation moved to Google Translate, which takes every one of them.
        val codes = ("af ar be bg bn ca cs cy da de el en eo es et fa fi fr ga gl gu he hi hr ht hu id is it ja ka kn " +
            "ko lt lv mk mr ms mt nl no pl pt ro ru sk sl sq sv sw ta te th tl tr uk ur vi zh").split(' ').toSet()
        assertEquals(codes, TranslationLanguages.CODES)
        assertEquals("he", TranslationLanguages.codeFor(Locale.forLanguageTag("iw")))
        assertEquals("no", TranslationLanguages.codeFor(Locale.forLanguageTag("nb-NO")))
        assertEquals("en", TranslationLanguages.codeFor(Locale.US))
        assertNull(TranslationLanguages.codeFor(Locale.forLanguageTag("haw")))
        assertEquals("Spanish", TranslationLanguages.displayName("es", Locale.US))
    }

    // ---------------------------------------------------------------------------------------------
    // Screen time
    // ---------------------------------------------------------------------------------------------

    @Test
    fun screenTimeQuestions() {
        for (query in listOf(
            "screen time", "Screen time today", "screentime", "my screen time", "today's screen time",
            "what's my screen time", "how long on my phone", "how long have i been on my phone today",
            "how much time have i spent on my phone", "phone usage", "how long have I been on my phone?"
        )) {
            assertEquals(query, ScreenTime, parse(query))
        }
    }

    @Test
    fun otherScreenQueriesStaySearches() {
        for (query in listOf("screen timeout", "screen time limits", "screen", "how long is a phone", "phone")) {
            assertNull(query, parse(query))
        }
    }

    @Test
    fun ordinaryQueriesAreNotQuestions() {
        for (query in listOf("", "   ", "spotify", "12*4", "timer 5m", "call mom", "directions to work", "time in tokyo", "cats")) {
            assertNull(query, parse(query))
        }
    }
}
