package com.pixel.intelligentsearch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pixel.intelligentsearch.core.currency.CurrencyRateRepository
import com.pixel.intelligentsearch.core.currency.CurrencyRateStore
import com.pixel.intelligentsearch.core.currency.CurrencyRateWork
import com.pixel.intelligentsearch.core.currency.RatesHttpClient
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.feature.search.SelectedTextSearchActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * What [App] brings in line with the settings at every start ([StartupSync], which App calls once the user is
 * unlocked): the selected-text entry, the next doodle redraw and the currency refresh. Tested without booting the real
 * App, whose production services would keep running for the rest of the test run.
 */
@RunWith(RobolectricTestRunner::class)
class StartupSyncTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settings by lazy { SettingsManager(context) }
    private val workManager get() = WorkManager.getInstance(context)
    private val entry get() = ComponentName(context, SelectedTextSearchActivity::class.java)

    private fun repository() = CurrencyRateRepository(
        context, settings, CurrencyRateStore(File(context.filesDir, "startup_sync_rates.properties")),
        RatesHttpClient { _, _ -> error("startup never downloads") }
    )

    private fun pending(work: String) =
        workManager.getWorkInfosForUniqueWork(work).get().filter { it.state == WorkInfo.State.ENQUEUED }

    @Before fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    @Test
    fun theSelectedTextEntryFollowsTheSetting() {
        settings.prefs.edit().put(SettingsManager.SEARCH_FROM_SELECTED_TEXT, false).commit()
        StartupSync.afterUnlock(context, repository())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, context.packageManager.getComponentEnabledSetting(entry))

        settings.prefs.edit().put(SettingsManager.SEARCH_FROM_SELECTED_TEXT, true).commit()
        StartupSync.afterUnlock(context, repository())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(entry))
    }

    @Test
    fun theNextDoodleRedrawIsArranged() {
        StartupSync.afterUnlock(context, repository())
        assertTrue(pending("widget_doodle_change").isNotEmpty())
    }

    @Test
    fun aDailyRatesDownloadTheSettingsNoLongerAllowIsCancelled() {
        CurrencyRateWork.scheduleDaily(context)
        assertTrue(pending(CurrencyRateWork.DAILY_WORK).isNotEmpty())

        settings.prefs.edit().put(SettingsManager.SEARCH_CALCULATOR, false).commit()
        StartupSync.afterUnlock(context, repository())

        assertTrue(pending(CurrencyRateWork.DAILY_WORK).isEmpty())
    }
}
