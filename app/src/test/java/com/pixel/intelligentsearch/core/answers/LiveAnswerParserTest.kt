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
    fun aClockTimeInAWeatherQuestionIsNoPlace() {
        // Each was the weather for a place called "5pm", "noon", "3" or "boston at 3pm".
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather at 5pm"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather at noon"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather at 3"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather today at 5"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather tonight at 8"))
        assertEquals(Weather(null, ForecastDay.TOMORROW), parse("weather tomorrow at 3"))
        assertEquals(Weather(null, ForecastDay.TOMORROW), parse("weather tomorrow at 3pm"))
        assertEquals(Weather("boston", ForecastDay.TODAY), parse("weather in boston at 3pm"))
        assertEquals(Weather("paris", ForecastDay.TODAY), parse("weather in paris at 5pm"))
        // A postcode is still a place.
        assertEquals(Weather("37601", ForecastDay.TODAY), parse("weather 37601"))
    }

    @Test
    fun whatIsDoneInALanguageIsNoWordToTranslate() {
        // "Taught in english" was a translation of "taught".
        for (query in listOf("taught in english", "written in english", "fluent in french", "spoken in spanish")) {
            assertNull(query, parse(query))
        }
        assertEquals(Translate("hola", "en", null), parse("hola in english"))
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
    fun aDayAfterThePlaceOrAPartOfTheDayIsNoPartOfThePlace() {
        // These looked up places called "boston tomorrow" and "the morning", and "weather in nice" no place at all.
        assertEquals(Weather("boston", ForecastDay.TOMORROW), parse("weather boston tomorrow"))
        assertEquals(Weather("10001", ForecastDay.TOMORROW), parse("weather 10001 tomorrow"))
        assertEquals(Weather("seattle", ForecastDay.TODAY), parse("weather seattle tonight"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather in the morning"))
        assertEquals(Weather(null, ForecastDay.TODAY), parse("weather at night"))
        assertEquals(Weather("nice", ForecastDay.TODAY), parse("weather in nice"))
        // "Nice weather" and "weather nice" are still no place.
        assertNull(parse("nice weather"))
        assertNull(parse("weather nice"))
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
    fun aLoneTitleOrNameInALanguageIsNotATranslation() {
        // Each was offered as a Translate card.
        for (query in listOf(
            "avengers in hindi", "pushpa in hindi", "bahubali in tamil", "frozen in spanish", "netflix in korean",
            "restaurants in turkish"
        )) {
            assertNull(query, parse(query))
        }
        // Everyday words still translate on their own, and any word does when asked for in so many words.
        assertEquals(Translate("water", "hi", null), parse("water in hindi"))
        assertEquals(Translate("beautiful", "es", null), parse("beautiful in spanish"))
        assertEquals(Translate("hello", "fr", null), parse("hello in french"))
        assertEquals(Translate("danke", "en", null), parse("danke in english"))
        assertEquals(Translate("frozen", "es", null), parse("translate frozen to spanish"))
        assertEquals(Translate("frozen", "es", null), parse("frozen meaning in spanish"))
    }

    @Test
    fun weatherForADayTheCardDoesntShowOrAPlaceOnlyTheUserKnowsStaysASearch() {
        // Each was a forecast for a real place: Thursday Island, Friday Harbor, Saturdaygua, Worksop, Manila.
        for (query in listOf(
            "weather thursday", "thursday weather", "weather friday", "weather saturday", "saturday weather",
            "what's the weather on friday", "weather this saturday", "weather sunday in london", "weather monday in paris",
            "weather yesterday", "weather in july", "weather at work", "weather man", "weather girl", "weather balloon",
            "weather vane", "weather stripping", "weather proof", "weather the storm", "weather vs climate"
        )) {
            assertNull(query, parse(query))
        }
        // Home and the user's own city or town are here ("weather at home" found Homyel, Belarus).
        for (query in listOf("weather at home", "weather in my city", "weather in my town", "weather where i live")) {
            assertEquals(query, Weather(null, ForecastDay.TODAY), parse(query))
        }
        // Real places with those words in a longer name are still places.
        assertEquals(Weather("isle of man", ForecastDay.TODAY), parse("weather isle of man"))
        assertEquals(Weather("mayfield", ForecastDay.TODAY), parse("weather mayfield"))
    }

    @Test
    fun aTownNamedForADayOrAWeatherWordIsAPlace() {
        // These real towns got no card: "friday", "storm" and "sunday" are blocked on their own.
        assertEquals(Weather("friday harbor", ForecastDay.TODAY), parse("weather in friday harbor"))
        assertEquals(Weather("storm lake", ForecastDay.TODAY), parse("weather in storm lake"))
        assertEquals(Weather("sunday river", ForecastDay.TODAY), parse("weather in sunday river"))
        // A month's weather somewhere is still no place ("july in rome" was looked up as one).
        assertNull(parse("weather in july in rome"))
        assertNull(parse("weather in marseille in march"))
        assertEquals(Weather("isle of may", ForecastDay.TODAY), parse("weather isle of may"))
    }

    @Test
    fun aQuestionToTranslateKeepsItsQuestionWords() {
        // This translated "your name".
        assertEquals(Translate("what is your name", "ja", null), parse("what is your name in japanese"))
        assertEquals(Translate("what's this", "es", null), parse("what's this in spanish"))
        assertEquals(Translate("love", "es", null), parse("what is love in spanish"))
    }

    @Test
    fun aHowToSearchInALanguageIsNoTranslation() {
        // Each offered to translate the whole search: it asks for the answer in that language.
        for (query in listOf("how to make tea in hindi", "how to lose weight in hindi", "how to earn money in tamil", "how do i cook rice in telugu")) {
            assertNull(query, parse(query))
        }
        assertEquals(Translate("how are you", "hi", null), parse("how are you in hindi"))
    }

    @Test
    fun howToSayAndAQuestionPutToSomeoneAreTranslated() {
        // The how-to rule took these for searches and gave no card.
        assertEquals(Translate("thank you", "ja", null), parse("how to say thank you in japanese"))
        assertEquals(Translate("good morning", "hi", null), parse("how to say good morning in hindi"))
        assertEquals(Translate("how can i help you", "es", null), parse("how can i help you in spanish"))
        // A how-to with no one spoken to is still a search.
        assertNull(parse("how can i learn english in hindi"))
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
