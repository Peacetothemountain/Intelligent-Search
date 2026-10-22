package com.pixel.intelligentsearch.core.answers

import com.pixel.intelligentsearch.core.net.HttpsGet
import com.pixel.intelligentsearch.core.net.HttpsCall
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections
import java.util.Locale

/**
 * The instant-answer engine against stand-in services: what it fetches, when it may not fetch at all, what it keeps,
 * and how a keystroke cancels the last one's request.
 */
@RunWith(RobolectricTestRunner::class)
class InstantAnswerEngineTest {

    private lateinit var prefs: SharedPreferences

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("answers/$name")) { "missing fixture $name" }.readText()

    /** Answers by address prefix and records every request, so a test can tell whether anything was sent. */
    private inner class FakeHttp : HttpsGet {
        val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun get(url: String, maxBytes: Int, call: HttpsCall): ByteArray? {
            requests += url
            return when {
                url.endsWith("/en/serendipity") -> fixture("dictionary_serendipity.json")
                url.startsWith("https://api.dictionaryapi.dev/") -> null
                url.startsWith("https://geocoding-api.open-meteo.com/") && "johnson" in url.lowercase() -> fixture("openmeteo_geocoding_johnson_city.json")
                url.startsWith("https://geocoding-api.open-meteo.com/") -> fixture("openmeteo_geocoding_none.json")
                url.startsWith("https://api.open-meteo.com/") -> fixture("openmeteo_forecast_johnson_city.json")
                else -> null
            }?.toByteArray()
        }
    }

    private class FakeLocation(var allowed: Boolean = false) : DeviceLocation {
        override fun permitted() = allowed
        override suspend fun approximate() = 48.8566 to 2.3522
    }

    private val http = FakeHttp()
    private val location = FakeLocation()
    private var screenTime: ScreenTimeReport? = null

    private fun engine(debounceMs: Long = 0L) = InstantAnswerEngine(
        prefs = { prefs },
        dictionary = DictionaryRepository(http, AnswerCache(60_000, 16), AnswerCache(60_000, 16)),
        weather = WeatherRepository(http, AnswerCache(60_000, 16), AnswerCache(60_000, 16)),
        screenTime = { screenTime },
        location = location,
        locale = { Locale.US },
        debounceMs = debounceMs
    )

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun answers(engine: InstantAnswerEngine, query: String): List<LiveAnswer> = runBlocking {
        engine.answer(requireNotNull(InstantAnswerParser.parse(query)), query).toList()
    }

    private fun allowWebAnswers() {
        prefs.edit().put(SettingsManager.DICTIONARY_ANSWERS, true).put(SettingsManager.WEATHER_ANSWERS, true).commit()
    }

    // ---------------------------------------------------------------------------------------------
    // Privacy: nothing is sent unless every switch allows it
    // ---------------------------------------------------------------------------------------------

    @Test
    fun theAnswersAreOffUntilTurnedOnAndSendNothing() {
        assertFalse(SettingsManager.DICTIONARY_ANSWERS.default)
        assertFalse(SettingsManager.WEATHER_ANSWERS.default)
        val engine = engine()
        assertEquals(emptyList<LiveAnswer>(), answers(engine, "define serendipity"))
        assertEquals(emptyList<LiveAnswer>(), answers(engine, "weather in johnson city"))
        assertEquals(emptyList<String>(), http.requests)
    }

    @Test
    fun withWebSuggestionsOrWebOffOrUnderTorNothingIsSent() {
        allowWebAnswers()
        prefs.edit().put(SettingsManager.WEATHER_HOME_LOCATION, "36.31,-82.35;Johnson City, Tennessee").commit()
        val blockers = listOf<(SharedPreferences.Editor) -> Unit>(
            { it.put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false) },
            { it.put(SettingsManager.SEARCH_WEB, false) },
            { it.put(SettingsManager.SEARCH_ENGINE, "Tor Project") },
            { it.put(SettingsManager.SEARCH_ENGINE, "Tor Browser") }
        )
        for (block in blockers) {
            prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, true).put(SettingsManager.SEARCH_WEB, true)
                .put(SettingsManager.SEARCH_ENGINE, "Google").also(block).commit()
            val engine = engine()
            assertEquals(emptyList<LiveAnswer>(), answers(engine, "define serendipity"))
            assertEquals(emptyList<LiveAnswer>(), answers(engine, "weather"))
            assertEquals(emptyList<LiveAnswer>(), answers(engine, "weather in johnson city"))
            runBlocking { assertEquals(null, engine.findPlaces("Johnson City")) }
        }
        assertEquals(emptyList<String>(), http.requests)
    }

    // ---------------------------------------------------------------------------------------------
    // Definitions
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aDefinitionIsFetchedOnceAndThenComesFromTheCache() {
        allowWebAnswers()
        val engine = engine()
        val first = answers(engine, "define serendipity").single() as LiveAnswer.Definition
        assertEquals("serendipity", first.entry.word)
        assertEquals("define serendipity", first.query)
        assertEquals(listOf("https://api.dictionaryapi.dev/api/v2/entries/en/serendipity"), http.requests)

        val again = answers(engine, "serendipity meaning").single() as LiveAnswer.Definition
        assertEquals("serendipity meaning", again.query)
        assertEquals(1, http.requests.size)
    }

    @Test
    fun turningAnAnswerOffForgetsWhatWasKept() = runBlocking {
        allowWebAnswers()
        prefs.edit().put(SettingsManager.WEATHER_HOME_LOCATION, "36.31,-82.35;Johnson City, Tennessee").commit()
        val engine = engine()
        answers(engine, "define serendipity")
        answers(engine, "weather")
        assertEquals(2, http.requests.size)

        engine.clearCache(InstantAnswerEngine.Feature.DICTIONARY)
        engine.clearCache(InstantAnswerEngine.Feature.WEATHER)
        answers(engine, "define serendipity")
        answers(engine, "weather")
        assertEquals(4, http.requests.size)
    }

    @Test
    fun aWordWithNoDefinitionIsNotAskedForAgainWhileTyping() {
        allowWebAnswers()
        val engine = engine()
        assertEquals(emptyList<LiveAnswer>(), answers(engine, "define qwzxv"))
        assertEquals(emptyList<LiveAnswer>(), answers(engine, "define qwzxv"))
        assertEquals(1, http.requests.size)
    }

    @Test
    fun eachKeystrokeCancelsTheLastOnesRequest() = runBlocking {
        allowWebAnswers()
        val engine = engine(debounceMs = 400L)
        val shown = mutableListOf<LiveAnswer>()
        flow {
            for (query in listOf("define s", "define se", "define ser", "define serendipity")) {
                emit(query)
                delay(40)
            }
        }.collectLatest { query ->
            engine.answer(requireNotNull(InstantAnswerParser.parse(query)), query).collect { shown += it }
        }
        assertEquals(listOf("https://api.dictionaryapi.dev/api/v2/entries/en/serendipity"), http.requests)
        assertEquals(listOf("define serendipity"), shown.map { it.query })
    }

    // ---------------------------------------------------------------------------------------------
    // Weather
    // ---------------------------------------------------------------------------------------------

    @Test
    fun weatherAtHomeInTheLocalesUnitAndCachedForAWhile() {
        allowWebAnswers()
        prefs.edit().put(SettingsManager.WEATHER_HOME_LOCATION, "36.31,-82.35;Johnson City, Tennessee").commit()
        val engine = engine()
        val answer = answers(engine, "weather").single() as LiveAnswer.Weather
        assertEquals("Johnson City, Tennessee", answer.report.placeName)
        assertEquals(TemperatureUnit.FAHRENHEIT, answer.report.unit)
        assertEquals(ForecastDay.TODAY, answer.report.day)
        assertEquals(1, http.requests.size)
        assertTrue(http.requests.single(), http.requests.single().contains("latitude=36.31&longitude=-82.35") && "temperature_unit=fahrenheit" in http.requests.single())

        val tomorrow = answers(engine, "weather tomorrow").single() as LiveAnswer.Weather
        assertEquals(ForecastDay.TOMORROW, tomorrow.report.day)
        assertEquals(1, http.requests.size)
    }

    @Test
    fun weatherForANamedPlaceLooksThePlaceUpFirst() {
        allowWebAnswers()
        val engine = engine()
        val answer = answers(engine, "weather in Johnson City").single() as LiveAnswer.Weather
        // The most populous Johnson City, not the first listed.
        assertEquals("Johnson City, Tennessee", answer.report.placeName)
        assertEquals(2, http.requests.size)
        assertTrue(http.requests[0].startsWith("https://geocoding-api.open-meteo.com/v1/search?name=johnson%20city"))

        answers(engine, "johnson city weather")
        assertEquals(2, http.requests.size)
        // A place that isn't found gets no card, and no forecast is asked for.
        assertEquals(emptyList<LiveAnswer>(), answers(engine, "weather in nowhereville"))
        assertEquals(3, http.requests.size)
    }

    @Test
    fun withNoHomeTheCardAsksWhereAndSendsNothing() {
        allowWebAnswers()
        assertEquals(listOf(LiveAnswer.WeatherNeedsLocation("weather")), answers(engine(), "weather"))
        assertEquals(emptyList<String>(), http.requests)
    }

    @Test
    fun approximateLocationIsUsedOnlyWhenChosenAndAllowed() {
        allowWebAnswers()
        prefs.edit().put(SettingsManager.WEATHER_HOME_LOCATION, "36.31,-82.35;Johnson City, Tennessee").commit()
        prefs.edit().put(SettingsManager.WEATHER_USE_DEVICE_LOCATION, true).commit()
        // Chosen but not allowed: home.
        assertEquals("Johnson City, Tennessee", (answers(engine(), "weather").single() as LiveAnswer.Weather).report.placeName)
        location.allowed = true
        val here = answers(engine(), "weather").single() as LiveAnswer.Weather
        assertEquals(InstantAnswerEngine.YOUR_LOCATION, here.report.placeName)
        assertTrue(http.requests.last(), http.requests.last().contains("latitude=48.86&longitude=2.35"))
    }

    // ---------------------------------------------------------------------------------------------
    // Translation
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aTranslationIsHandedToGoogleTranslateAtOnceAndSendsNothing() {
        // Debounce left on: the card needs nothing fetched, so it doesn't wait for typing to pause.
        val answer = answers(engine(debounceMs = 60_000), "translate hello to spanish").single()
        assertEquals(LiveAnswer.Translation("translate hello to spanish", "hello", null, "es"), answer)
        assertEquals(emptyList<String>(), http.requests)
    }

    @Test
    fun aTranslationWithoutATargetIsIntoThePhonesLanguage() {
        val answer = answers(engine(), "translate bonjour tout le monde").single() as LiveAnswer.Translation
        assertEquals("en", answer.target)
        assertEquals(null, answer.source)
    }

    @Test
    fun aNamedSourceIsKept() {
        val answer = answers(engine(), "translate gato from spanish to english").single() as LiveAnswer.Translation
        assertEquals("es", answer.source)
        assertEquals("en", answer.target)
    }

    // ---------------------------------------------------------------------------------------------
    // Screen time
    // ---------------------------------------------------------------------------------------------

    @Test
    fun screenTimeIsReadOnThePhone() {
        assertEquals(listOf(LiveAnswer.ScreenTime("screen time", null)), answers(engine(), "screen time"))
        screenTime = ScreenTimeReport(60_000, listOf(ScreenTimeApp("maps", "Maps", 60_000)))
        assertEquals(listOf(LiveAnswer.ScreenTime("screen time", screenTime)), answers(engine(), "screen time"))
        assertEquals(emptyList<String>(), http.requests)
    }

    /**
     * The next keystroke cancels a definition being fetched, and that ends the request at once rather than at its
     * timeout. The stand-in blocks the way a socket read does, ignoring interrupts, until its call is cancelled; the
     * cancelled lookup isn't remembered as "not found".
     */
    @Test(timeout = 15_000)
    fun aKeystrokeEndsTheRequestInFlight() {
        allowWebAnswers()
        val reached = java.util.concurrent.CountDownLatch(1)
        val released = java.util.concurrent.CountDownLatch(1)
        val blocking = HttpsGet { _, _, call ->
            call.onCancel { released.countDown() }
            reached.countDown()
            while (!call.isCancelled) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000)
            null
        }
        val dictionary = DictionaryRepository(blocking, AnswerCache(60_000, 16), AnswerCache(60_000, 16))
        val engine = InstantAnswerEngine(
            prefs = { prefs },
            dictionary = dictionary,
            weather = WeatherRepository(blocking, AnswerCache(60_000, 16), AnswerCache(60_000, 16)),
            screenTime = { null },
            location = location,
            locale = { Locale.US },
            debounceMs = 0
        )
        runBlocking {
            val job = launch(kotlinx.coroutines.Dispatchers.Default) {
                engine.answer(requireNotNull(InstantAnswerParser.parse("define serendipity")), "define serendipity").toList()
            }
            assertTrue("the request never went out", reached.await(5, java.util.concurrent.TimeUnit.SECONDS))
            job.cancel()
            assertTrue("cancelling didn't end the request", released.await(5, java.util.concurrent.TimeUnit.SECONDS))
            job.join()
        }
        assertFalse("a cancelled lookup isn't a miss", dictionary.recentlyMissed("serendipity"))
    }
}
