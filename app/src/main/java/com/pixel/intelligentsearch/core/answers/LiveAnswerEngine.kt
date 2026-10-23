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
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/** An instant answer shown inline in the overlay, for the query it answers ([query], as typed). */
@Immutable
sealed interface LiveAnswer {
    val query: String

    data class Definition(override val query: String, val entry: DictionaryEntry) : LiveAnswer

    /** A forecast; [here] when it is for the set location or the phone's, not a place the query named. */
    data class Weather(override val query: String, val report: WeatherReport, val here: Boolean = false) : LiveAnswer

    /** Weather for "here" with no location set and no approximate location to use. */
    data class WeatherNeedsLocation(override val query: String) : LiveAnswer

    /** [text] to translate from [source] (null: let Google Translate tell) to [target], in Google Translate. */
    data class Translation(override val query: String, val text: String, val source: String?, val target: String) : LiveAnswer

    /** Today's screen time, or null [report] when Usage Access isn't granted. */
    data class ScreenTime(override val query: String, val report: ScreenTimeReport?) : LiveAnswer
}

/**
 * Works out the overlay's instant answers: definitions, forecasts, translations (handed to Google Translate) and
 * screen time. Each answer is a flow, collected per keystroke and cancelled by the next, that emits nothing (the
 * overlay keeps its tap-through card) or the answer as it becomes known. Definitions and forecasts have no switch of
 * their own: they follow Web Suggestions ([NetworkPolicy.webAnswersAllowed]). They wait [debounceMs] after the last
 * keystroke before going out, and come from the cache at once when they can.
 */
@Singleton
class LiveAnswerEngine internal constructor(
    private val prefs: () -> SharedPreferences,
    private val dictionary: DictionaryRepository,
    private val weather: WeatherRepository,
    private val screenTime: () -> ScreenTimeReport?,
    private val location: DeviceLocation,
    private val locale: () -> Locale = Locale::getDefault,
    private val debounceMs: Long = DEBOUNCE_MS,
    /** Where a switched-off feature's saved answers are deleted, off the main thread the change arrives on. */
    private val cacheCleaner: Executor = Dispatchers.IO.asExecutor()
) {
    /** The answers that keep what they fetched on the phone. */
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
    fun answer(question: LiveAnswerQuery, query: String): Flow<LiveAnswer> = flow {
        when (question) {
            is LiveAnswerQuery.Define -> define(question, query)
            is LiveAnswerQuery.Weather -> weather(question, query)
            is LiveAnswerQuery.Translate -> emit(translation(question, query))
            LiveAnswerQuery.ScreenTime -> emit(LiveAnswer.ScreenTime(query, screenTime()))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<LiveAnswer>.define(question: LiveAnswerQuery.Define, query: String) {
        if (!NetworkPolicy.webAnswersAllowed(prefs())) return
        val cached = dictionary.peek(question.word) ?: dictionary.cached(question.word)
        if (cached != null) return emit(LiveAnswer.Definition(query, cached))
        if (dictionary.recentlyMissed(question.word)) return
        delay(debounceMs)
        dictionary.fetch(question.word)?.let { emit(LiveAnswer.Definition(query, it)) }
    }

    private suspend fun FlowCollector<LiveAnswer>.weather(question: LiveAnswerQuery.Weather, query: String) {
        val settings = prefs()
        if (!NetworkPolicy.webAnswersAllowed(settings)) return
        val unit = TemperatureUnit.forLocale(locale())
        var debounced = false
        suspend fun once() {
            if (!debounced) delay(debounceMs)
            debounced = true
        }

        // The phone's own location is never written to disk: its forecast is kept in memory only.
        var onDisk = true
        val place: GeoPlace = if (question.place != null) {
            val language = locale().language
            val found = weather.peekPlaces(question.place, language) ?: weather.cachedPlaces(question.place, language)
                ?: run { once(); weather.fetchPlaces(question.place, language) }
            // "Weather in Paris" means the Paris most people mean: the most populous of the places by that name.
            found?.maxByOrNull { it.population } ?: return
        } else {
            // The location set from the weather card wins; without one, the phone's approximate location if allowed.
            HomeLocationCodec.decode(settings[SettingsManager.WEATHER_HOME_LOCATION])
                ?: deviceLocation()?.also { onDisk = false }
                ?: return emit(LiveAnswer.WeatherNeedsLocation(query))
        }
        val forecast = weather.peekForecast(place.latitude, place.longitude, unit)
            ?: (if (onDisk) weather.cachedForecast(place.latitude, place.longitude, unit) else null)
            ?: run { once(); weather.fetchForecast(place.latitude, place.longitude, unit, onDisk) }
            ?: return
        WeatherReport.from(forecast, place.shortName, unit, question.day)
            ?.let { emit(LiveAnswer.Weather(query, it, here = question.place == null)) }
    }

    /** The phone's approximate location for "the weather", when the user allowed it. */
    private suspend fun deviceLocation(): GeoPlace? {
        if (!location.permitted()) return null
        return location.approximate()?.let { (latitude, longitude) -> GeoPlace(YOUR_LOCATION, null, null, latitude, longitude) }
    }

    /** The Google Translate card for [question]: into the language named, else the phone's, else English. */
    private fun translation(question: LiveAnswerQuery.Translate, query: String) = LiveAnswer.Translation(
        query = query,
        text = question.text,
        source = question.sourceLanguage,
        target = question.targetLanguage ?: TranslationLanguages.codeFor(locale()) ?: ENGLISH
    )

    /** Forgets saved answers for [feature], for when it is turned off. */
    suspend fun clearCache(feature: Feature) = withContext(Dispatchers.IO) { clearNow(feature) }

    private fun clearNow(feature: Feature) {
        when (feature) {
            Feature.DICTIONARY -> dictionary.clear()
            Feature.WEATHER -> weather.clear()
        }
    }

    /**
     * Brings the saved answers in line with the settings, at every start: with Web or Web Suggestions off nothing is
     * kept, and answers past their lifetime are deleted rather than left on disk.
     */
    suspend fun syncCaches() = withContext(Dispatchers.IO) {
        val kept = NetworkPolicy.webAnswersKept(prefs())
        for (feature in Feature.entries) {
            if (!kept) clearNow(feature) else when (feature) {
                Feature.DICTIONARY -> dictionary.pruneExpired()
                Feature.WEATHER -> weather.pruneExpired()
            }
        }
    }

    /**
     * Off means off, wherever it was switched (the settings page, a restore, the debug menu): when Web or Web
     * Suggestions turns off, the definitions and forecasts kept go too. Held here, as SharedPreferences only keeps
     * listeners weakly.
     */
    private val switchedOff = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
        // A null key means every setting was cleared.
        val relevant = key == null || key in SettingsManager.SEARCH_WEB.allNames || key in SettingsManager.SEARCH_WEB_SUGGESTIONS.allNames
        if (relevant && !NetworkPolicy.webAnswersKept(changed)) {
            runCatching { cacheCleaner.execute { Feature.entries.forEach(::clearNow) } }
        }
    }

    init {
        runCatching { prefs().registerOnSharedPreferenceChangeListener(switchedOff) }
    }

    /** Places called [name], for setting the weather's location; null when the lookup failed or isn't allowed. */
    suspend fun findPlaces(name: String): List<GeoPlace>? = withContext(Dispatchers.IO) {
        if (!NetworkPolicy.webAnswersAllowed(prefs()) || name.isBlank()) return@withContext null
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
