package com.pixel.intelligentsearch.core.currency

import android.app.job.JobInfo
import android.content.Context
import android.os.Looper
import android.util.Log
import com.pixel.intelligentsearch.AppJob
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.net.HttpsClient
import com.pixel.intelligentsearch.core.net.HttpsGet
import com.pixel.intelligentsearch.core.net.fetch
import com.pixel.intelligentsearch.core.security.NetworkPolicy
import com.pixel.intelligentsearch.di.appEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exchange rates for currency answers: the European Central Bank's daily reference rates once they have been
 * fetched, the built-in table until then or whenever the saved copy can't be read.
 *
 * Nothing is fetched until the first currency conversion; after that the rates are refreshed once a day. Nothing is
 * fetched at all, and the daily refresh is cancelled, while the Calculator (which gives currency answers) or Live
 * Currency Rates is off, or Tor is the search engine (the request would leave Tor). The request is the same for
 * everyone and carries nothing about the user.
 *
 * [clock] says what today is, so that a table dated too far ahead is refused. A saved table first wanted on the main
 * thread ([onMainThread]) is read on [loader]; the built-in rates stand in until it has been.
 */
@Singleton
class CurrencyRateRepository internal constructor(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val store: CurrencyRateStore,
    private val http: HttpsGet,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val onMainThread: () -> Boolean = { false },
    private val loader: Executor = Dispatchers.IO.asExecutor()
) {

    @Inject
    constructor(@ApplicationContext context: Context, settingsManager: SettingsManager) : this(
        context, settingsManager, CurrencyRateStore(File(context.filesDir, FILE_NAME)), HttpsClient(),
        onMainThread = { Looper.myLooper() == Looper.getMainLooper() }
    )

    enum class RefreshResult { UPDATED, UNCHANGED, SKIPPED_OFF, SKIPPED_TOR, FAILED }

    @Volatile private var cached: CurrencyRates? = null
    private val loadLock = Any()
    private val loadStarted = AtomicBoolean(false)

    // Once per process is enough: both requests are unique work, so asking again would change nothing. Cleared when
    // the work is cancelled, so that turning fetching back on arranges it again.
    @Volatile private var dailyScheduled = false
    @Volatile private var firstFetchRequested = false
    private val scheduleLock = Any()

    /**
     * The rates to convert with now: the saved live rates, else the built-in ones. On the main thread, before the
     * saved rates have been read, the built-in ones answer while the file is read in the background.
     */
    fun current(): CurrencyRates {
        cached?.let { return it }
        if (onMainThread()) {
            loadInBackground()
            return CurrencyRates.BUILT_IN
        }
        return load()
    }

    /** The saved rates, read from the file the first time they are wanted. */
    private fun load(): CurrencyRates = synchronized(loadLock) {
        cached ?: (store.load() ?: CurrencyRates.BUILT_IN).also { cached = it }
    }

    private fun loadInBackground() {
        if (!loadStarted.compareAndSet(false, true)) return
        runCatching { loader.execute { load() } }.onFailure { loadStarted.set(false) }
    }

    /** Whether rates may be fetched ([NetworkPolicy.liveRatesAllowed]). */
    fun liveRatesAllowed(): Boolean = NetworkPolicy.liveRatesAllowed(settingsManager.prefs)

    /**
     * Called when a currency answer used [rates]. Starts the daily refresh, and when no rates have ever been fetched
     * (none are saved), asks for a first fetch as soon as there is a network.
     */
    fun onCurrencyConverted(rates: CurrencyRates) {
        // Called on keystrokes; once both are arranged there's nothing left to check.
        if (dailyScheduled && (firstFetchRequested || !rates.isBuiltIn)) return
        // Built-in rates may only mean the live table lacks a currency, so the saved table decides. On the main
        // thread it may not have been read yet; the first fetch is then left to a later answer.
        val saved = cached ?: if (onMainThread()) null else load()
        synchronized(scheduleLock) {
            if (!liveRatesAllowed()) return
            if (!dailyScheduled) {
                dailyScheduled = true
                CurrencyRateWork.scheduleDaily(context)
            }
            if (saved?.isBuiltIn == true && !firstFetchRequested) {
                firstFetchRequested = true
                CurrencyRateWork.fetchSoon(context)
            }
        }
    }

    /**
     * Brings the scheduled work in line with the settings: cancelled while fetching isn't allowed, kept daily once
     * rates have been fetched before. Runs at startup, when the Calculator, Live Currency Rates or search engine
     * setting is changed, and after a backup is restored.
     */
    fun syncSchedule() {
        synchronized(scheduleLock) {
            if (!liveRatesAllowed()) {
                dailyScheduled = false
                firstFetchRequested = false
                CurrencyRateWork.cancel(context)
            } else if (store.exists()) {
                dailyScheduled = true
                CurrencyRateWork.scheduleDaily(context)
            }
        }
    }

    /** Fetches, checks and saves today's rates, unless fetching isn't allowed. */
    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
        val prefs = settingsManager.prefs
        if (!prefs[SettingsManager.SEARCH_CALCULATOR] || !prefs[SettingsManager.LIVE_CURRENCY_RATES]) {
            return@withContext RefreshResult.SKIPPED_OFF
        }
        if (NetworkPolicy.torSelected(prefs)) return@withContext RefreshResult.SKIPPED_TOR
        val body = http.fetch(ECB_DAILY_URL, EcbRatesParser.MAX_BYTES) ?: return@withContext RefreshResult.FAILED
        val today = LocalDate.now(clock)
        val fetched = EcbRatesParser.parse(body, today) ?: return@withContext RefreshResult.FAILED
        val saved = load().date
        // A stale copy from a cache along the way never replaces newer rates. A saved table dated later than a real
        // one could be doesn't count as newer: it would hold off every update until that day.
        val stale = saved != null && saved <= today.plusDays(EcbRatesParser.MAX_DAYS_AHEAD) &&
            fetched.date != null && fetched.date < saved
        if (stale) return@withContext RefreshResult.UNCHANGED
        synchronized(loadLock) {
            if (!store.save(fetched)) Log.w(TAG, "Couldn't save currency rates; using them until the app restarts")
            cached = fetched
        }
        RefreshResult.UPDATED
    }

    companion object {
        private const val TAG = "CurrencyRates"
        const val ECB_DAILY_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
        const val FILE_NAME = "currency_rates.properties"
    }
}

/**
 * The scheduled side of [CurrencyRateRepository]: a daily refresh and a one-off first fetch, both on a network. A
 * failed download is tried again up to [MAX_RETRIES] times, 30 seconds after the first failure and twice as long after
 * each next one.
 */
object CurrencyRateWork {
    const val ATTEMPT = "attempt"
    private const val MAX_RETRIES = 3
    private val FIRST_RETRY_MS = TimeUnit.SECONDS.toMillis(30)

    /** Refreshes the rates once a day, the first time from half a day to a day from now (the first fetch is [fetchSoon]'s). */
    fun scheduleDaily(context: Context) {
        AppJob.CURRENCY_DAILY.schedule(context, keepExisting = true) {
            setPeriodic(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(12))
            setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            setPersisted(true)
        }
    }

    /** Fetches the rates once, as soon as there is a network. */
    fun fetchSoon(context: Context) {
        AppJob.CURRENCY_FIRST_FETCH.schedule(context, keepExisting = true) {
            setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            setPersisted(true)
        }
    }

    fun cancel(context: Context) {
        AppJob.CURRENCY_DAILY.cancel(context)
        AppJob.CURRENCY_FIRST_FETCH.cancel(context)
        AppJob.CURRENCY_RETRY.cancel(context)
    }

    /** Fetches the rates; [attempt] is 0 for a scheduled fetch and counts the retries after it. */
    suspend fun run(context: Context, attempt: Int) {
        val result = context.appEntryPoint.currencyRateRepository().refresh()
        if (result != CurrencyRateRepository.RefreshResult.FAILED || attempt >= MAX_RETRIES) return
        AppJob.CURRENCY_RETRY.schedule(context) {
            setMinimumLatency(FIRST_RETRY_MS shl attempt)
            setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            setExtras(android.os.PersistableBundle().apply { putInt(ATTEMPT, attempt + 1) })
            setPersisted(true)
        }
    }
}
