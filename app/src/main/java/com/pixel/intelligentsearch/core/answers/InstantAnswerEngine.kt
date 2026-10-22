package com.pixel.intelligentsearch.core.answers

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Immutable
import com.pixel.intelligentsearch.core.currency.HttpsRatesClient
import com.pixel.intelligentsearch.core.currency.RatesHttpClient
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.BooleanSetting
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.security.SafeWebLaunch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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

    /**
     * Translating [text] from [source] (null: not identified) to [target]; [detected] when the source was identified
     * rather than named. [status] is where it has got to.
     */
    data class Translation(
        override val query: String,
        val text: String,
        val source: String?,
        val detected: Boolean,
        val target: String,
        val status: TranslationStatus
    ) : LiveAnswer

    /** Today's screen time, or null [report] when Usage Access isn't granted. */
    data class ScreenTime(override val query: String, val report: ScreenTimeReport?) : LiveAnswer
}

@Immutable
sealed interface TranslationStatus {
    /** On-Device Translation is off: the card only offers Google Translate. */
    data object Off : TranslationStatus
    data object Working : TranslationStatus
    data class Done(val translated: String) : TranslationStatus

    /**
     * [languages] still need downloading. Downloads wait for Wi-Fi unless the user says otherwise, which [onWifi]
     * tells the card; [blocked] while Tor is the search engine, when nothing is downloaded at all.
     */
    data class NeedsDownload(val languages: List<String>, val onWifi: Boolean, val blocked: Boolean) : TranslationStatus
    data object Downloading : TranslationStatus

    /** The language couldn't be told, or ML Kit can't translate it. */
    data object Unidentified : TranslationStatus

    /** The text is already in the language asked for. */
    data object SameLanguage : TranslationStatus
    data object Failed : TranslationStatus
}

/**
 * When an instant answer may reach the network. Definitions and forecasts send what was typed (a word, a place) to a
 * third party, so they follow the same switches as live web suggestions: Web and Web Suggestions on, the feature's
 * own switch on, and never while Tor is the search engine. Language models come from Google, so they are only
 * downloaded while On-Device Translation is on and Tor isn't the engine.
 */
object AnswerPrivacy {
    fun webAnswersAllowed(prefs: SharedPreferences, feature: BooleanSetting): Boolean =
        prefs[feature] && prefs[SettingsManager.SEARCH_WEB] && prefs[SettingsManager.SEARCH_WEB_SUGGESTIONS] &&
            !SafeWebLaunch.isTorEngine(prefs[SettingsManager.SEARCH_ENGINE])

    fun modelDownloadsAllowed(prefs: SharedPreferences): Boolean =
        prefs[SettingsManager.ON_DEVICE_TRANSLATION] && !SafeWebLaunch.isTorEngine(prefs[SettingsManager.SEARCH_ENGINE])
}

/**
 * Works out the overlay's instant answers: definitions, forecasts, translations and screen time. Each answer is a
 * flow, collected per keystroke and cancelled by the next, that emits nothing (the overlay keeps its tap-through
 * card) or the answer as it becomes known. Network answers wait [debounceMs] after the last keystroke before going
 * out, and come from the cache at once when they can.
 */
@Singleton
class InstantAnswerEngine internal constructor(
    private val prefs: () -> SharedPreferences,
    private val dictionary: DictionaryRepository,
    private val weather: WeatherRepository,
    private val translation: Lazy<TranslationEngine>,
    private val screenTime: () -> ScreenTimeReport?,
    private val location: DeviceLocation,
    private val onUnmeteredNetwork: () -> Boolean,
    private val locale: () -> Locale = Locale::getDefault,
    private val debounceMs: Long = DEBOUNCE_MS
) {
    enum class Feature { DICTIONARY, WEATHER }

    @Inject
    constructor(@ApplicationContext context: Context, settingsManager: SettingsManager) : this(
        context = context.applicationContext,
        settingsManager = settingsManager,
        http = HttpsRatesClient(timeoutMs = 8_000, totalTimeoutMs = 12_000L, accept = HttpsRatesClient.ACCEPT_JSON)
    )

    private constructor(context: Context, settingsManager: SettingsManager, http: RatesHttpClient) : this(
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
        translation = lazy { MlKitTranslationEngine(context) },
        screenTime = ScreenTimeRepository(context)::today,
        location = AndroidDeviceLocation(context),
        onUnmeteredNetwork = { isOnUnmeteredNetwork(context) }
    )

    /** The answer to [question], which was read from [query]. */
    fun answer(question: InstantAnswerQuery, query: String): Flow<LiveAnswer> = flow {
        when (question) {
            is InstantAnswerQuery.Define -> define(question, query)
            is InstantAnswerQuery.Weather -> weather(question, query)
            is InstantAnswerQuery.Translate -> translate(question, query)
            InstantAnswerQuery.ScreenTime -> emit(LiveAnswer.ScreenTime(query, screenTime()))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<LiveAnswer>.define(question: InstantAnswerQuery.Define, query: String) {
        if (!AnswerPrivacy.webAnswersAllowed(prefs(), SettingsManager.DICTIONARY_ANSWERS)) return
        val cached = dictionary.peek(question.word) ?: dictionary.cached(question.word)
        if (cached != null) return emit(LiveAnswer.Definition(query, cached))
        if (dictionary.recentlyMissed(question.word)) return
        delay(debounceMs)
        dictionary.fetch(question.word)?.let { emit(LiveAnswer.Definition(query, it)) }
    }

    private suspend fun FlowCollector<LiveAnswer>.weather(question: InstantAnswerQuery.Weather, query: String) {
        val settings = prefs()
        if (!AnswerPrivacy.webAnswersAllowed(settings, SettingsManager.WEATHER_ANSWERS)) return
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

    private suspend fun FlowCollector<LiveAnswer>.translate(question: InstantAnswerQuery.Translate, query: String) {
        val target = question.targetLanguage ?: TranslationLanguages.codeFor(locale()) ?: ENGLISH
        fun answer(source: String?, status: TranslationStatus) =
            LiveAnswer.Translation(query, question.text, source, question.sourceLanguage == null, target, status)

        if (!prefs()[SettingsManager.ON_DEVICE_TRANSLATION]) return emit(answer(question.sourceLanguage, TranslationStatus.Off))
        delay(debounceMs)
        val engine = translation.value
        val source = question.sourceLanguage ?: runCatchingCancellable { engine.identifyLanguage(question.text) }
            ?: return emit(answer(null, TranslationStatus.Unidentified))
        if (source == target) return emit(answer(source, TranslationStatus.SameLanguage))

        val missing = listOf(source, target).distinct().filterNot { runCatchingCancellable { engine.isDownloaded(it) } == true }
        if (missing.isNotEmpty()) {
            val blocked = !AnswerPrivacy.modelDownloadsAllowed(prefs())
            return emit(answer(source, TranslationStatus.NeedsDownload(missing, onUnmeteredNetwork(), blocked)))
        }
        emit(answer(source, TranslationStatus.Working))
        val translated = runCatchingCancellable { engine.translate(question.text, source, target) }
        emit(answer(source, if (translated != null) TranslationStatus.Done(translated) else TranslationStatus.Failed))
    }

    /**
     * Downloads the languages [answer] needs, on Wi-Fi only unless [useMobileData]. Refuses (false) while downloads
     * aren't allowed. Returns once the download finishes or fails.
     */
    suspend fun downloadLanguages(answer: LiveAnswer.Translation, useMobileData: Boolean): Boolean = withContext(Dispatchers.IO) {
        val source = answer.source ?: return@withContext false
        if (!AnswerPrivacy.modelDownloadsAllowed(prefs())) return@withContext false
        runCatchingCancellable { translation.value.download(source, answer.target, wifiOnly = !useMobileData); true } == true
    }

    /** Forgets saved answers for [feature], for when the user turns it off. */
    suspend fun clearCache(feature: Feature) = withContext(Dispatchers.IO) {
        when (feature) {
            Feature.DICTIONARY -> dictionary.clear()
            Feature.WEATHER -> weather.clear()
        }
    }

    /** Places called [name], for choosing a home location in settings; null when the lookup failed or isn't allowed. */
    suspend fun findPlaces(name: String): List<GeoPlace>? = withContext(Dispatchers.IO) {
        if (!AnswerPrivacy.webAnswersAllowed(prefs(), SettingsManager.WEATHER_ANSWERS) || name.isBlank()) return@withContext null
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

        private fun isOnUnmeteredNetwork(context: Context): Boolean {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
    }
}

/** [block]'s result, or null if it threw; cancellation still cancels. */
private inline fun <T> runCatchingCancellable(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
