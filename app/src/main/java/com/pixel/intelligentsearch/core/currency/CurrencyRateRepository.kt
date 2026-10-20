package com.pixel.intelligentsearch.core.currency

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.security.SafeWebLaunch
import com.pixel.intelligentsearch.di.appEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exchange rates for currency answers: the European Central Bank's daily reference rates once they have been
 * fetched, the built-in table until then or whenever the saved copy can't be read.
 *
 * Nothing is fetched until the first currency conversion; after that the rates are refreshed once a day. Nothing is
 * fetched at all while Tor is the search engine (the request would leave Tor) or Live Currency Rates is off. The
 * request is the same for everyone and carries nothing about the user.
 */
@Singleton
class CurrencyRateRepository internal constructor(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val store: CurrencyRateStore,
    private val http: RatesHttpClient
) {

    @Inject
    constructor(@ApplicationContext context: Context, settingsManager: SettingsManager) : this(
        context, settingsManager, CurrencyRateStore(File(context.filesDir, FILE_NAME)), HttpsRatesClient()
    )

    enum class RefreshResult { UPDATED, UNCHANGED, SKIPPED_OFF, SKIPPED_TOR, FAILED }

    @Volatile private var cached: CurrencyRates? = null
    // Once per process is enough: both requests are unique work, so asking again would change nothing.
    @Volatile private var dailyScheduled = false
    @Volatile private var firstFetchRequested = false

    /** The rates to convert with now: the saved live rates, else the built-in ones. */
    fun current(): CurrencyRates = cached ?: (store.load() ?: CurrencyRates.BUILT_IN).also { cached = it }

    /** Whether rates may be fetched: Live Currency Rates is on and the search engine isn't Tor. */
    fun liveRatesAllowed(): Boolean {
        val prefs = settingsManager.prefs
        return prefs[SettingsManager.LIVE_CURRENCY_RATES] && !SafeWebLaunch.isTorEngine(prefs[SettingsManager.SEARCH_ENGINE])
    }

    /**
     * Called when a currency answer used [rates]. Starts the daily refresh, and when the rates are still the
     * built-in ones (never fetched), asks for a first fetch as soon as there is a network.
     */
    fun onCurrencyConverted(rates: CurrencyRates) {
        // Called on keystrokes; once both are arranged there's nothing left to check.
        if (dailyScheduled && (firstFetchRequested || !rates.isBuiltIn)) return
        if (!liveRatesAllowed()) return
        if (!dailyScheduled) {
            dailyScheduled = true
            CurrencyRateWork.scheduleDaily(context)
        }
        if (rates.isBuiltIn && !firstFetchRequested) {
            firstFetchRequested = true
            CurrencyRateWork.fetchSoon(context)
        }
    }

    /**
     * Brings the scheduled work in line with the settings at startup: cancelled while fetching isn't allowed, kept
     * daily once rates have been fetched before.
     */
    fun syncSchedule() {
        if (!liveRatesAllowed()) {
            dailyScheduled = false
            firstFetchRequested = false
            CurrencyRateWork.cancel(context)
        } else if (store.exists()) {
            dailyScheduled = true
            CurrencyRateWork.scheduleDaily(context)
        }
    }

    /** Fetches, checks and saves today's rates, unless fetching isn't allowed. */
    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
        val prefs = settingsManager.prefs
        if (!prefs[SettingsManager.LIVE_CURRENCY_RATES]) return@withContext RefreshResult.SKIPPED_OFF
        if (SafeWebLaunch.isTorEngine(prefs[SettingsManager.SEARCH_ENGINE])) return@withContext RefreshResult.SKIPPED_TOR
        val body = http.get(ECB_DAILY_URL, EcbRatesParser.MAX_BYTES) ?: return@withContext RefreshResult.FAILED
        val fetched = EcbRatesParser.parse(body) ?: return@withContext RefreshResult.FAILED
        val saved = current().date
        // A stale copy from a cache along the way never replaces newer rates.
        if (saved != null && fetched.date != null && fetched.date < saved) return@withContext RefreshResult.UNCHANGED
        if (!store.save(fetched)) Log.w(TAG, "Couldn't save currency rates; using them until the app restarts")
        cached = fetched
        RefreshResult.UPDATED
    }

    companion object {
        private const val TAG = "CurrencyRates"
        const val ECB_DAILY_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
        const val FILE_NAME = "currency_rates.properties"
    }
}

/** The WorkManager side of [CurrencyRateRepository]: a daily refresh and a one-off first fetch, both on a network. */
object CurrencyRateWork {
    const val DAILY_WORK = "currency_rates_daily"
    const val FIRST_FETCH_WORK = "currency_rates_first_fetch"
    private const val TAG = "CurrencyRateWork"

    private val onNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Refreshes the rates once a day, starting a day from now (the first fetch is [fetchSoon]'s). */
    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<CurrencyRatesWorker>(1, TimeUnit.DAYS)
            .setConstraints(onNetwork)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }.onFailure { Log.w(TAG, "Couldn't schedule the daily currency rates", it) }
    }

    /** Fetches the rates once, as soon as there is a network. */
    fun fetchSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<CurrencyRatesWorker>().setConstraints(onNetwork).build()
        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(FIRST_FETCH_WORK, ExistingWorkPolicy.KEEP, request)
        }.onFailure { Log.w(TAG, "Couldn't schedule the first currency rates", it) }
    }

    fun cancel(context: Context) {
        runCatching {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(DAILY_WORK)
            workManager.cancelUniqueWork(FIRST_FETCH_WORK)
        }.onFailure { Log.w(TAG, "Couldn't cancel the currency rates work", it) }
    }
}

/** Fetches the rates. A failed download is retried a few times with WorkManager's backoff. */
class CurrencyRatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when (applicationContext.appEntryPoint.currencyRateRepository().refresh()) {
            CurrencyRateRepository.RefreshResult.FAILED -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            else -> Result.success()
        }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
