package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.answers.DictionaryEntry
import com.pixel.intelligentsearch.core.answers.DictionarySense
import com.pixel.intelligentsearch.core.answers.ForecastDay
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.answers.ScreenTimeReport
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.answers.WeatherReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenAnswersTest {

    private fun weather(day: ForecastDay = ForecastDay.TODAY) = WeatherReport(
        placeName = "Denver", unit = TemperatureUnit.FAHRENHEIT, day = day, temperature = 71.6, feelsLike = 70.0,
        code = 2, isDay = true, high = 75.2, low = 58.8, hours = emptyList()
    )

    @Test
    fun aCalculationIsSaidAsTheAnswer() {
        assertEquals("The answer is 144.", SpokenAnswers.sentence(SearchUiState(query = "12 times 12", mathResult = "144")))
    }

    @Test
    fun aResultInAnotherBaseIsForReadingNotHearing() {
        assertNull(SpokenAnswers.math("0b11111111"))
        assertNull(SpokenAnswers.math("255 (0xFF)"))
    }

    @Test
    fun aConversionIsSaidAsASentence() {
        val state = SearchUiState(query = "10 miles in km", instantAnswer = InstantAnswer("10 mi = 16.09 km", "Unit Conversion", "conversion"))
        assertEquals("10 mi is 16.09 km", SpokenAnswers.sentence(state))
    }

    @Test
    fun storageIsSaidWithoutThePercentage() {
        val answer = InstantAnswer("118 GB free", "of 256 GB • 53% used", "storage")
        assertEquals("118 GB free of 256 GB.", SpokenAnswers.instant(answer))
    }

    @Test
    fun cardsThatOnlyOfferToLookSomethingUpAreNotSaid() {
        for (type in listOf("weather", "dictionary", "url", "ip")) {
            assertNull(type, SpokenAnswers.instant(InstantAnswer("Title", "Tap for the current forecast", type)))
        }
    }

    @Test
    fun aForecastSaysTheTemperatureTheSkyAndThePlace() {
        assertEquals(
            "72 degrees and partly cloudy in Denver. High 75, low 59.",
            SpokenAnswers.live(LiveAnswer.Weather("weather in denver", weather()))
        )
        // The phone's own place needs no naming.
        assertEquals(
            "72 degrees and partly cloudy. High 75, low 59.",
            SpokenAnswers.live(LiveAnswer.Weather("weather", weather(), here = true))
        )
        assertEquals(
            "Tomorrow in Denver: partly cloudy, with a high of 75 and a low of 59.",
            SpokenAnswers.live(LiveAnswer.Weather("weather tomorrow in denver", weather(ForecastDay.TOMORROW)))
        )
    }

    @Test
    fun aDefinitionSaysTheWordAndItsFirstMeaning() {
        val entry = DictionaryEntry(
            "serendipity", null,
            listOf(DictionarySense("noun", "A combination of events which are not individually beneficial, but occurring together produce a good outcome.", null)),
            null
        )
        assertEquals(
            "serendipity: A combination of events which are not individually beneficial, but occurring together produce a good outcome.",
            SpokenAnswers.live(LiveAnswer.Definition("define serendipity", entry))
        )
    }

    @Test
    fun aLongDefinitionStopsAtASentence() {
        val long = "A first sentence that says what the word means in enough words to matter. " + "More and more detail follows it. ".repeat(12)
        val said = SpokenAnswers.live(LiveAnswer.Definition("define x", DictionaryEntry("x", null, listOf(DictionarySense("noun", long, null)), null)))!!
        assertTrue(said, said.length < 240)
        assertTrue(said, said.endsWith("."))
    }

    @Test
    fun screenTimeIsSaidAndAPromptForAccessIsNot() {
        assertEquals(
            "3 hr 12 min of screen time today.",
            SpokenAnswers.live(LiveAnswer.ScreenTime("screen time", ScreenTimeReport(192 * 60_000L, emptyList())))
        )
        assertNull(SpokenAnswers.live(LiveAnswer.ScreenTime("screen time", null)))
        assertNull(SpokenAnswers.live(LiveAnswer.WeatherNeedsLocation("weather")))
        assertNull(SpokenAnswers.live(LiveAnswer.Translation("hello in spanish", "hello", null, "es")))
    }

    @Test
    fun anAnswerForOtherWordsIsNotSaid() {
        val stale = SearchUiState(query = "weather in paris", liveAnswer = LiveAnswer.Weather("weather in denver", weather()))
        assertNull(SpokenAnswers.sentence(stale))
    }

    @Test
    fun aQuestionWhoseAnswerIsOnItsWayWaitsForIt() {
        assertTrue(SpokenAnswers.awaitsLiveAnswer(SearchUiState(query = "weather in denver")))
        assertTrue(SpokenAnswers.mayAnswer(SearchUiState(query = "define serendipity")))
        // It has arrived.
        assertFalse(
            SpokenAnswers.awaitsLiveAnswer(SearchUiState(query = "weather in denver", liveAnswer = LiveAnswer.Weather("weather in denver", weather())))
        )
        // A translation is handed to another app, and a plain search has nothing coming.
        assertFalse(SpokenAnswers.awaitsLiveAnswer(SearchUiState(query = "hello in spanish")))
        assertFalse(SpokenAnswers.mayAnswer(SearchUiState(query = "pizza places near me")))
    }
}
