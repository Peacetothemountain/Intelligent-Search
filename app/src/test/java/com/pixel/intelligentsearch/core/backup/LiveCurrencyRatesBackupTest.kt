package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Live Currency Rates is backed up like other switches, but a backup can only ever switch it off. */
@RunWith(RobolectricTestRunner::class)
class LiveCurrencyRatesBackupTest {

    @Test
    fun theSettingIsAKnownBoolean() {
        assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(SettingsManager.LIVE_CURRENCY_RATES.name))
        assertEquals(SettingsManager.LIVE_CURRENCY_RATES, SettingsManager.keyFor("live_currency_rates"))
    }

    @Test
    fun aBackupCanSwitchLiveRatesOffButNeverOn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "live_rates.json"))

        settingsManager.updateSetting(SettingsManager.LIVE_CURRENCY_RATES, true)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.updateSetting(SettingsManager.LIVE_CURRENCY_RATES, false)
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settingsManager[SettingsManager.LIVE_CURRENCY_RATES])

        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.updateSetting(SettingsManager.LIVE_CURRENCY_RATES, true)
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settingsManager[SettingsManager.LIVE_CURRENCY_RATES])

        db.close()
    }
}
