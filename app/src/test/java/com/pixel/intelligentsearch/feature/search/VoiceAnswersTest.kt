package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.answers.DictionaryEntry
import com.pixel.intelligentsearch.core.answers.DictionarySense
import com.pixel.intelligentsearch.core.answers.ForecastDay
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.answers.ScreenTimeReport
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.answers.WeatherReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which voice searches stay on their answer, and which go on to the web. */
class VoiceAnswersTest {

    private fun weather() = WeatherReport(
        placeName = "Denver", unit = TemperatureUnit.FAHRENHEIT, day = ForecastDay.TODAY, temperature = 71.6, feelsLike = 70.0,
        code = 2, isDay = true, high = 75.2, low = 58.8, hours = emptyList()
    )

    @Test
    fun answersOnTheScreenKeepTheVoiceSearchThere() {
        assertTrue(VoiceAnswers.shows(SearchUiState(query = "what's nine times nine", mathResult = "81")))
        // A sum written in another base is an answer too.
        assertTrue(VoiceAnswers.shows(SearchUiState(query = "255 in hex", mathResult = "0xFF")))
        assertTrue(VoiceAnswers.shows(SearchUiState(query = "10 miles in km", instantAnswer = InstantAnswer("10 mi = 16.09 km", "", "conversion"))))
        assertTrue(VoiceAnswers.shows(SearchUiState(query = "time in tokyo", instantAnswer = InstantAnswer("7:30 AM", "Current time in Tokyo", "time"))))
        assertTrue(VoiceAnswers.shows(SearchUiState(query = "weather", liveAnswer = LiveAnswer.Weather("weather", weather(), here = true))))
        val entry = DictionaryEntry("serendipity", null, listOf(DictionarySense("noun", "Luck in finding good things.", null)), null)
        assertTrue(VoiceAnswers.shows(SearchUiState(query = "define serendipity", liveAnswer = LiveAnswer.Definition("define serendipity", entry))))
        assertTrue(VoiceAnswers.shows(SearchUiState(query = "screen time", liveAnswer = LiveAnswer.ScreenTime("screen time", ScreenTimeReport(60_000L, emptyList())))))
    }

    @Test
    fun cardsThatOnlyOfferToLookSomethingUpGoOnToTheWeb() {
        for (type in listOf("weather", "dictionary", "url")) {
            assertFalse(type, VoiceAnswers.shows(SearchUiState(query = "x", instantAnswer = InstantAnswer("Title", "Tap for more", type))))
        }
        assertFalse(VoiceAnswers.shows(SearchUiState(query = "screen time", liveAnswer = LiveAnswer.ScreenTime("screen time", null))))
        assertFalse(VoiceAnswers.shows(SearchUiState(query = "weather", liveAnswer = LiveAnswer.WeatherNeedsLocation("weather"))))
        assertFalse(VoiceAnswers.shows(SearchUiState(query = "hello in spanish", liveAnswer = LiveAnswer.Translation("hello in spanish", "hello", null, "es"))))
    }

    @Test
    fun anAnswerForOtherWordsIsNotThisOne() {
        assertFalse(VoiceAnswers.shows(SearchUiState(query = "weather in paris", liveAnswer = LiveAnswer.Weather("weather in denver", weather()))))
    }

    @Test
    fun aQuestionWhoseAnswerIsOnItsWayWaitsForIt() {
        assertTrue(VoiceAnswers.awaitsLiveAnswer(SearchUiState(query = "weather in denver")))
        assertTrue(VoiceAnswers.mayAnswer(SearchUiState(query = "define serendipity")))
        // It has arrived.
        assertFalse(VoiceAnswers.awaitsLiveAnswer(SearchUiState(query = "weather in denver", liveAnswer = LiveAnswer.Weather("weather in denver", weather()))))
        // The lookup finished with nothing (an unknown word, no connection): there is nothing more to wait for.
        assertFalse(VoiceAnswers.awaitsLiveAnswer(SearchUiState(query = "define zzqx", liveAnswerSettled = "define zzqx")))
        // A translation is handed to another app, and a plain search has nothing coming.
        assertFalse(VoiceAnswers.awaitsLiveAnswer(SearchUiState(query = "hello in spanish")))
        assertFalse(VoiceAnswers.mayAnswer(SearchUiState(query = "pizza places near me")))
    }
}
