package com.pixel.intelligentsearch.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pixel.intelligentsearch.core.currency.CurrencyRateWork
import com.pixel.intelligentsearch.core.currency.CurrencyRates
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.di.appEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Live Currency Rates sits under the Calculator, which gives currency answers: it is shown only while the Calculator
 * is on, and switching either off, or picking Tor as the search engine, cancels the daily rates download straight
 * away (the switches hand that to a background thread, so the tap itself never waits on WorkManager).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LiveCurrencyRatesSettingTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val currencyRates get() = context.appEntryPoint.currencyRateRepository()

    private fun dailyWork(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(CurrencyRateWork.DAILY_WORK).get()

    private fun dailyWorkEnqueued() = dailyWork().any { it.state == WorkInfo.State.ENQUEUED }
    private fun dailyWorkCancelled() = eventually { dailyWork().let { it.isNotEmpty() && it.all { work -> work.state == WorkInfo.State.CANCELLED } } }

    /** Whether [check] holds within a second, for work a switch hands to a background thread. */
    private fun eventually(check: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < deadline) {
            if (check()) return true
            Thread.sleep(10)
        }
        return check()
    }

    /** Scrolls to and taps the row titled [title], after the rows' tap debounce has passed. */
    private fun tap(title: String) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Before
    fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun theRowIsShownOnlyWhileTheCalculatorIsOn() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { SearchSourcesScreen({}, {}) } }
        compose.onNodeWithText("Live Currency Rates").assertExists()
        compose.onNodeWithText("Update Currency Rates Daily from the European Central Bank.").assertExists()

        tap("Calculator")
        assertEquals(false, prefs[SettingsManager.SEARCH_CALCULATOR])
        compose.onNodeWithText("Live Currency Rates").assertDoesNotExist()

        tap("Calculator")
        compose.onNodeWithText("Live Currency Rates").assertExists()
    }

    @Test
    fun switchingTheCalculatorOrLiveRatesOffCancelsTheDailyDownload() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { SearchSourcesScreen({}, {}) } }

        currencyRates.onCurrencyConverted(CurrencyRates.BUILT_IN)
        assertTrue(dailyWorkEnqueued())
        tap("Calculator")
        assertTrue(dailyWorkCancelled())

        tap("Calculator")
        // Back on, the next currency answer arranges it again.
        currencyRates.onCurrencyConverted(CurrencyRates.BUILT_IN)
        assertTrue(dailyWorkEnqueued())
        tap("Live Currency Rates")
        assertEquals(false, prefs[SettingsManager.LIVE_CURRENCY_RATES])
        assertTrue(dailyWorkCancelled())
    }

    @Test
    fun pickingTorCancelsTheDailyDownload() {
        // Tor Browser installed, so picking it is allowed.
        val tor = ComponentName("org.torproject.torbrowser", "org.torproject.torbrowser.App")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(tor)
            addIntentFilterForActivity(tor, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WebSearchScreen(prefs, onBack = {}) } }

        currencyRates.onCurrencyConverted(CurrencyRates.BUILT_IN)
        assertTrue(dailyWorkEnqueued())
        tap("Primary Search App")
        tap("Tor Project")
        assertEquals("Tor Project", prefs[SettingsManager.SEARCH_ENGINE])
        assertTrue(dailyWorkCancelled())
    }
}
