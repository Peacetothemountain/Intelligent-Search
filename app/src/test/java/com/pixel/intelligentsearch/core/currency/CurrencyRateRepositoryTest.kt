package com.pixel.intelligentsearch.core.currency

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pixel.intelligentsearch.core.currency.CurrencyRateRepository.RefreshResult
import com.pixel.intelligentsearch.core.data.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Live rates are fetched (through a fake HTTP layer, never the network), saved and used offline; the built-in rates
 * stand in until then. Nothing is fetched or scheduled under Tor or with Live Currency Rates off.
 */
@RunWith(RobolectricTestRunner::class)
class CurrencyRateRepositoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settings by lazy { SettingsManager(context) }
    private val file by lazy { File(context.filesDir, "currency_rates_test.properties") }
    private val requested = mutableListOf<String>()

    private fun repository(body: () -> ByteArray? = { EcbRatesParserTest.ecbFile().toByteArray() }) =
        CurrencyRateRepository(context, settings, CurrencyRateStore(file), RatesHttpClient { url, maxBytes ->
            assertEquals(EcbRatesParser.MAX_BYTES, maxBytes)
            requested += url
            body()
        })

    private fun uniqueWork(name: String): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    @Before fun setUp() {
        settings.prefs.edit().clear().commit()
        file.delete()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun theBuiltInRatesStandInUntilRatesAreFetched() {
        assertFalse(file.exists())
        assertSame(CurrencyRates.BUILT_IN, repository().current())
        assertTrue(requested.isEmpty())
    }

    @Test
    fun anUnreadableSavedFileFallsBackToTheBuiltInRates() {
        file.writeText("this is not a rate table")
        assertSame(CurrencyRates.BUILT_IN, repository().current())
    }

    @Test
    fun fetchedRatesAreSavedAndUsedOfflineAfterARestart() = runBlocking {
        val repository = repository()
        assertEquals(RefreshResult.UPDATED, repository.refresh())
        assertEquals(listOf(CurrencyRateRepository.ECB_DAILY_URL), requested)
        assertEquals(LocalDate.of(2026, 10, 19), repository.current().date)

        // A new process, offline: the saved rates are read back without a request.
        val offline = repository { throw AssertionError("no request expected") }
        assertEquals(1.0812, offline.current().perBase.getValue("usd"), 0.0)
        assertEquals("Rates from Oct 19, 2026", offline.current().sourceLabel(java.util.Locale.US))
    }

    @Test
    fun aFailedOrHostileDownloadChangesNothing() = runBlocking {
        assertEquals(RefreshResult.FAILED, repository { null }.refresh())
        assertEquals(RefreshResult.FAILED, repository { "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>".toByteArray() }.refresh())
        assertEquals(RefreshResult.FAILED, repository { ByteArray(EcbRatesParser.MAX_BYTES + 1) }.refresh())
        assertFalse(file.exists())
        assertSame(CurrencyRates.BUILT_IN, repository().current())
    }

    @Test
    fun olderRatesNeverReplaceNewerOnes() = runBlocking {
        repository().refresh()
        val older = repository { EcbRatesParserTest.ecbFile(time = "2026-10-15").toByteArray() }
        assertEquals(RefreshResult.UNCHANGED, older.refresh())
        assertEquals(LocalDate.of(2026, 10, 19), repository().current().date)
    }

    @Test
    fun nothingIsFetchedWhileTorIsTheSearchEngine() = runBlocking {
        for (tor in listOf("Tor Browser", "Tor Project")) {
            settings.set(SettingsManager.SEARCH_ENGINE, tor)
            assertEquals(RefreshResult.SKIPPED_TOR, repository().refresh())
        }
        assertTrue(requested.isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun nothingIsFetchedWithLiveCurrencyRatesOff() = runBlocking {
        assertTrue("on by default", settings[SettingsManager.LIVE_CURRENCY_RATES])
        settings.set(SettingsManager.LIVE_CURRENCY_RATES, false)
        assertEquals(RefreshResult.SKIPPED_OFF, repository().refresh())
        assertTrue(requested.isEmpty())
    }

    @Test
    fun theFirstConversionAsksForRatesSoonAndDailyAfter() {
        val repository = repository()
        repository.onCurrencyConverted(repository.current())

        val first = uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        assertEquals(0L, first.initialDelayMillis)

        val daily = uniqueWork(CurrencyRateWork.DAILY_WORK).single()
        assertEquals(WorkInfo.State.ENQUEUED, daily.state)
        assertEquals(NetworkType.CONNECTED, daily.constraints.requiredNetworkType)
        assertEquals(TimeUnit.DAYS.toMillis(1), daily.periodicityInfo?.repeatIntervalMillis)
        assertEquals(TimeUnit.DAYS.toMillis(1), daily.initialDelayMillis)
        // Nothing was fetched here: the work does that, with a network.
        assertTrue(requested.isEmpty())
    }

    @Test
    fun onceRatesAreSavedNoFirstFetchIsAskedFor() = runBlocking {
        repository().refresh()
        val repository = repository()
        repository.onCurrencyConverted(repository.current())
        assertTrue(uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).isEmpty())
        assertEquals(1, uniqueWork(CurrencyRateWork.DAILY_WORK).size)
    }

    @Test
    fun nothingIsScheduledUnderTorOrWithTheSettingOff() {
        settings.set(SettingsManager.SEARCH_ENGINE, "Tor Browser")
        repository().onCurrencyConverted(CurrencyRates.BUILT_IN)
        settings.set(SettingsManager.SEARCH_ENGINE, "Google")
        settings.set(SettingsManager.LIVE_CURRENCY_RATES, false)
        repository().onCurrencyConverted(CurrencyRates.BUILT_IN)

        assertTrue(uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).isEmpty())
        assertTrue(uniqueWork(CurrencyRateWork.DAILY_WORK).isEmpty())
    }

    @Test
    fun startupCancelsTheWorkOnceFetchingIsNoLongerAllowed() {
        repository().onCurrencyConverted(CurrencyRates.BUILT_IN)
        settings.set(SettingsManager.SEARCH_ENGINE, "Tor Project")
        repository().syncSchedule()

        assertEquals(WorkInfo.State.CANCELLED, uniqueWork(CurrencyRateWork.DAILY_WORK).single().state)
        assertEquals(WorkInfo.State.CANCELLED, uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).single().state)
    }

    /** A repository whose today is [today], reading on the main thread when [onMainThread] says so, on [loader]. */
    private fun repositoryAt(
        today: String = "2026-10-20",
        onMainThread: () -> Boolean = { false },
        loader: Executor = Executor { it.run() }
    ) = CurrencyRateRepository(
        context, settings, CurrencyRateStore(file),
        RatesHttpClient { url, _ -> requested += url; EcbRatesParserTest.ecbFile().toByteArray() },
        clock = Clock.fixed(LocalDate.parse(today).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
        onMainThread = onMainThread,
        loader = loader
    )

    @Test
    fun nothingIsFetchedOrScheduledWithTheCalculatorOff() = runBlocking {
        settings.set(SettingsManager.SEARCH_CALCULATOR, false)
        assertEquals(RefreshResult.SKIPPED_OFF, repository().refresh())
        repository().onCurrencyConverted(CurrencyRates.BUILT_IN)
        assertTrue(requested.isEmpty())
        assertTrue(uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).isEmpty())
        assertTrue(uniqueWork(CurrencyRateWork.DAILY_WORK).isEmpty())
    }

    @Test
    fun switchingFetchingOffCancelsTheWorkAndOnAgainArrangesItAgain() {
        val switches = listOf<Pair<() -> Unit, () -> Unit>>(
            { settings.set(SettingsManager.SEARCH_CALCULATOR, false) } to { settings.set(SettingsManager.SEARCH_CALCULATOR, true) },
            { settings.set(SettingsManager.LIVE_CURRENCY_RATES, false) } to { settings.set(SettingsManager.LIVE_CURRENCY_RATES, true) },
            { settings.set(SettingsManager.SEARCH_ENGINE, "Tor Browser") } to { settings.set(SettingsManager.SEARCH_ENGINE, "Google") }
        )
        // One repository throughout, as in the app, so what it remembers having arranged must be forgotten too.
        val repository = repository()
        for ((off, on) in switches) {
            repository.onCurrencyConverted(repository.current())
            assertTrue(uniqueWork(CurrencyRateWork.DAILY_WORK).any { it.state == WorkInfo.State.ENQUEUED })
            assertTrue(uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).any { it.state == WorkInfo.State.ENQUEUED })

            off()
            repository.syncSchedule()
            assertTrue(uniqueWork(CurrencyRateWork.DAILY_WORK).all { it.state == WorkInfo.State.CANCELLED })
            assertTrue(uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).all { it.state == WorkInfo.State.CANCELLED })
            on()
            repository.syncSchedule()
        }
        repository.onCurrencyConverted(repository.current())
        assertTrue(uniqueWork(CurrencyRateWork.DAILY_WORK).any { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun aFetchedTableDatedFarAheadIsRefused() = runBlocking {
        // The test file is dated Oct 19: three days ahead of Oct 16.
        assertEquals(RefreshResult.FAILED, repositoryAt(today = "2026-10-16").refresh())
        assertFalse(file.exists())
        assertEquals(RefreshResult.UPDATED, repositoryAt(today = "2026-10-17").refresh())
    }

    @Test
    fun aSavedTableDatedFarAheadNeverHoldsOffUpdates() = runBlocking {
        val future = CurrencyRates("eur", mapOf("eur" to 1.0, "usd" to 2.0), LocalDate.of(2030, 1, 1))
        assertTrue(CurrencyRateStore(file).save(future))
        val repository = repositoryAt(today = "2026-10-20")
        assertEquals(RefreshResult.UPDATED, repository.refresh())
        assertEquals(LocalDate.of(2026, 10, 19), repository.current().date)
        // A saved table only a day ahead (a time zone away) still counts.
        CurrencyRateStore(file).save(future.copy(date = LocalDate.of(2026, 10, 21)))
        assertEquals(RefreshResult.UNCHANGED, repositoryAt(today = "2026-10-20").refresh())
    }

    @Test
    fun theFirstAnswerOnTheMainThreadReadsTheSavedRatesInTheBackground() = runBlocking {
        repository().refresh()
        val queued = mutableListOf<Runnable>()
        val repository = repositoryAt(onMainThread = { true }, loader = { queued += it })

        // The file isn't read on the main thread: the built-in rates answer while it is read elsewhere, once.
        assertSame(CurrencyRates.BUILT_IN, repository.current())
        assertSame(CurrencyRates.BUILT_IN, repository.current())
        assertEquals(1, queued.size)
        // Not knowing yet whether rates were ever fetched, it asks for no first fetch.
        repository.onCurrencyConverted(CurrencyRates.BUILT_IN)
        assertTrue(uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).isEmpty())

        queued.single().run()
        assertEquals(LocalDate.of(2026, 10, 19), repository.current().date)
        assertEquals(1, queued.size)
    }

    @Test
    fun aCurrencyTheLiveRatesLackAsksForNoFirstFetchOnceRatesAreSaved() = runBlocking {
        repository().refresh()
        // AED isn't in the ECB's table, so its answer used the built-in rates.
        repository().onCurrencyConverted(CurrencyRates.BUILT_IN)
        assertTrue(uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).isEmpty())
        assertEquals(1, uniqueWork(CurrencyRateWork.DAILY_WORK).size)
    }

    @Test
    fun startupKeepsTheDailyRefreshOnlyOnceRatesWereFetched() = runBlocking {
        repository().syncSchedule()
        assertTrue(uniqueWork(CurrencyRateWork.DAILY_WORK).isEmpty())

        repository().refresh()
        repository().syncSchedule()
        assertEquals(WorkInfo.State.ENQUEUED, uniqueWork(CurrencyRateWork.DAILY_WORK).single().state)
        assertNull(uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).firstOrNull())
    }
}
