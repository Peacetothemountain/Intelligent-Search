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
import java.time.LocalDate
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
