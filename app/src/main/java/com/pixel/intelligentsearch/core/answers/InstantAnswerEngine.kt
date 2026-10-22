package com.pixel.intelligentsearch.core.answers

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.net.HttpsClient
import com.pixel.intelligentsearch.core.net.HttpsGet
import com.pixel.intelligentsearch.core.security.NetworkPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** An instant answer shown inline in the overlay, for the query it answers ([query], as typed). */
@Immutable
sealed interface LiveAnswer {
    val query: String

    data class Definition(override val query: String, val entry: DictionaryEntry) : LiveAnswer

    data class Weather(override val query: String, val report: WeatherReport) : LiveAnswer

    /** Weather for "here" with no home location set and no approximate location to use. */
    data class WeatherNeedsLocation(override val query: String) : LiveAnswer

    /** [text] to translate from [source] (null: let Google Translate tell) to [target], in Google Translate. */
    data class Translation(override val query: String, val text: String, val source: String?, val target: String) : LiveAnswer

    /** Today's screen time, or null [report] when Usage Access isn't granted. */
    data class ScreenTime(override val query: String, val report: ScreenTimeReport?) : LiveAnswer
}

/**
 * Works out the overlay's instant answers: definitions, forecasts, translations (handed to Google Translate) and
 * screen time. Each answer is a flow, collected per keystroke and cancelled by the next, that emits nothing (the
 * overlay keeps its tap-through card) or the answer as it becomes known. Network answers wait [debounceMs] after the last keystroke before going
 * out, and come from the cache at once when they can.
 */
@Singleton
class InstantAnswerEngine internal constructor(
    private val prefs: () -> SharedPreferences,
    private val dictionary: DictionaryRepository,
    private val weather: WeatherRepository,
    private val screenTime: () -> ScreenTimeReport?,
    private val location: DeviceLocation,
    private val locale: () -> Locale = Locale::getDefault,
    private val debounceMs: Long = DEBOUNCE_MS
) {
    enum class Feature { DICTIONARY, WEATHER }

    @Inject
    constructor(@ApplicationContext context: Context, settingsManager: SettingsManager) : this(
        context = context.applicationContext,
        settingsManager = settingsManager,
        http = HttpsClient(timeoutMs = 8_000, totalTimeoutMs = 12_000L, accept = HttpsClient.ACCEPT_JSON)
    )

    private constructor(context: Context, settingsManager: SettingsManager, http: HttpsGet) : this(
        prefs = { settingsManager.prefs },
        dictionary = DictionaryRepository(
            http,
            AnswerCache(DICTIONARY_TTL_MS, 64, DiskAnswerCache(cacheDir(context, "dictionary"), 100)),
            AnswerCache(MISS_TTL_MS, 64)
        ),
        weather = WeatherRepository(
            http,
            AnswerCache(FORECAST_TTL_MS, 16, DiskAnswerCache(cacheDir(context, "forecast"), 8)),
            AnswerCache(PLACE_TTL_MS, 32, DiskAnswerCache(cacheDir(context, "places"), 32))
        ),
        screenTime = ScreenTimeRepository(context)::today,
        location = AndroidDeviceLocation(context)
    )

    /** The answer to [question], which was read from [query]. */
    fun answer(question: InstantAnswerQuery, query: String): Flow<LiveAnswer> = flow {
        when (question) {
            is InstantAnswerQuery.Define -> define(question, query)
            is InstantAnswerQuery.Weather -> weather(question, query)
            is InstantAnswerQuery.Translate -> emit(translation(question, query))
            InstantAnswerQuery.ScreenTime -> emit(LiveAnswer.ScreenTime(query, screenTime()))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<LiveAnswer>.define(question: InstantAnswerQuery.Define, query: String) {
        if (!NetworkPolicy.webAnswersAllowed(prefs(), SettingsManager.DICTIONARY_ANSWERS)) return
        val cached = dictionary.peek(question.word) ?: dictionary.cached(question.word)
        if (cached != null) return emit(LiveAnswer.Definition(query, cached))
        if (dictionary.recentlyMissed(question.word)) return
        delay(debounceMs)
        dictionary.fetch(question.word)?.let { emit(LiveAnswer.Definition(query, it)) }
    }

    private suspend fun FlowCollector<LiveAnswer>.weather(question: InstantAnswerQuery.Weather, query: String) {
        val settings = prefs()
        if (!NetworkPolicy.webAnswersAllowed(settings, SettingsManager.WEATHER_ANSWERS)) return
        val unit = TemperatureUnit.forLocale(locale())
        var debounced = false
        suspend fun once() {
            if (!debounced) delay(debounceMs)
            debounced = true
        }

        val place: GeoPlace = if (question.place != null) {
            val language = locale().language
            val found = weather.peekPlaces(question.place, language) ?: weather.cachedPlaces(question.place, language)
                ?: run { once(); weather.fetchPlaces(question.place, language) }
            // "Weather in Paris" means the Paris most people mean: the most populous of the places by that name.
            found?.maxByOrNull { it.population } ?: return
        } else {
            homePlace(settings) ?: return emit(LiveAnswer.WeatherNeedsLocation(query))
        }
        val forecast = weather.peekForecast(place.latitude, place.longitude, unit)
            ?: weather.cachedForecast(place.latitude, place.longitude, unit)
            ?: run { once(); weather.fetchForecast(place.latitude, place.longitude, unit) }
            ?: return
        WeatherReport.from(forecast, place.shortName, unit, question.day)?.let { emit(LiveAnswer.Weather(query, it)) }
    }

    /** Where "the weather" is for: the phone's approximate location when chosen and allowed, else the home location. */
    private suspend fun homePlace(settings: SharedPreferences): GeoPlace? {
        if (settings[SettingsManager.WEATHER_USE_DEVICE_LOCATION] && location.permitted()) {
            location.approximate()?.let { (latitude, longitude) ->
                return GeoPlace(YOUR_LOCATION, null, null, latitude, longitude)
            }
        }
        return HomeLocationCodec.decode(settings[SettingsManager.WEATHER_HOME_LOCATION])
    }

    /** The Google Translate card for [question]: into the language named, else the phone's, else English. */
    private fun translation(question: InstantAnswerQuery.Translate, query: String) = LiveAnswer.Translation(
        query = query,
        text = question.text,
        source = question.sourceLanguage,
        target = question.targetLanguage ?: TranslationLanguages.codeFor(locale()) ?: ENGLISH
    )

    /** Forgets saved answers for [feature], for when the user turns it off. */
    suspend fun clearCache(feature: Feature) = withContext(Dispatchers.IO) {
        when (feature) {
            Feature.DICTIONARY -> dictionary.clear()
            Feature.WEATHER -> weather.clear()
        }
    }

    /** Places called [name], for choosing a home location in settings; null when the lookup failed or isn't allowed. */
    suspend fun findPlaces(name: String): List<GeoPlace>? = withContext(Dispatchers.IO) {
        if (!NetworkPolicy.webAnswersAllowed(prefs(), SettingsManager.WEATHER_ANSWERS) || name.isBlank()) return@withContext null
        val language = locale().language
        weather.cachedPlaces(name, language) ?: weather.fetchPlaces(name, language)
    }

    companion object {
        const val DEBOUNCE_MS = 300L
        const val DICTIONARY_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val MISS_TTL_MS = 2L * 60 * 1000
        const val FORECAST_TTL_MS = 15L * 60 * 1000
        const val PLACE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val YOUR_LOCATION = "Your Location"
        private const val ENGLISH = "en"

        private fun cacheDir(context: Context, name: String) = File(File(context.cacheDir, "answers"), name)
    }
}
