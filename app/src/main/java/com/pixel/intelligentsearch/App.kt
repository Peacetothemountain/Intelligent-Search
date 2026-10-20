package com.pixel.intelligentsearch

import com.pixel.intelligentsearch.di.ApplicationScope
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.di.appEntryPoint
import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.system.DirectBootManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var directBootManager: DirectBootManager

    @Inject
    lateinit var multiProfileManager: dagger.Lazy<MultiProfileManager>

    @Inject
    lateinit var systemToggleManager: dagger.Lazy<SystemToggleManager>

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var currencyRates: dagger.Lazy<com.pixel.intelligentsearch.core.currency.CurrencyRateRepository>

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    private companion object {
        const val MAINTENANCE_PREFS = "app_maintenance"
        const val KEY_RETIRED_SECRETS_REMOVED = "retired_secrets_removed_v1"
    }

    override fun onCreate() {
        super.onCreate()

        setupStrictMode()
        deleteLegacyCrashLog()
        warmupServices()
    }

    private fun setupStrictMode() {
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            val builder = StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectUnsafeIntentLaunch()
                .detectCredentialProtectedWhileLocked()

            // Android 18 stops granting URI access implicitly for ACTION_SEND / ACTION_IMAGE_CAPTURE.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                builder.detectImplicitUriPermissionGrant()
            }

            StrictMode.setVmPolicy(builder.penaltyLog().build())
        }
    }

    /**
     * Older versions wrote crash stack traces (which can name files and folders) to device-protected storage,
     * readable before the first unlock, and nothing ever read them. The system already logs crashes; remove
     * any leftover file.
     */
    private fun deleteLegacyCrashLog() {
        try {
            File(createDeviceProtectedStorageContext().filesDir, "crash_log.txt").delete()
        } catch (_: Exception) {}
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            com.pixel.intelligentsearch.core.data.SystemDataProvider.invalidateAppsCache()
            com.pixel.intelligentsearch.feature.search.clearAllUiMemoryCaches()
        }
    }

    /** Deletes Keystore keys and stores left by removed features, once per install. */
    private fun removeRetiredSecretsOnce() {
        val maintenance = getSharedPreferences(MAINTENANCE_PREFS, MODE_PRIVATE)
        if (maintenance.getBoolean(KEY_RETIRED_SECRETS_REMOVED, false)) return
        appEntryPoint.strongBoxSecurityManager().removeRetiredSecrets()
        maintenance.edit().putBoolean(KEY_RETIRED_SECRETS_REMOVED, true).apply()
    }

    /**
     * Non-blocking background warmup to achieve sub-50ms cold-start overlay presentation.
     */
    private fun warmupServices() {
        appScope.launch {
            try {
                // Keep qualifying for the search role only while holding it, so Android doesn't revoke it; never claim
                // the slot on start otherwise (that would override a later choice made in system settings).
                if (com.pixel.intelligentsearch.core.system.SearchRoleManager.isSearchRoleHeld(this@App)) {
                    com.pixel.intelligentsearch.core.system.SearchRoleManager.ensureUserSetupSearchEngineAllowlist(this@App)
                }
                if (!directBootManager.checkIsUserUnlocked()) {
                    directBootManager.isUserUnlocked.filter { it }.first()
                    // Widgets drawn before unlock could only read device-protected (default) settings;
                    // redraw them from the user's real settings now that they are readable.
                    widgetUpdater.updateAll()
                }
                // One-time: fold old setting spellings and the retired DataStore copy into the single store.
                settingsManager.migrateToSingleStore()
                settingsManager.startDirectBootMirror()
                // Component states are kept by Android, outside the app's data; keep the selection entry matching the setting.
                com.pixel.intelligentsearch.feature.search.SelectedTextSearchActivity.syncWithSetting(this@App)
                // Redraw the widgets when the day's doodle next changes (a doodle day starts or ends at midnight).
                com.pixel.intelligentsearch.feature.widget.WidgetDoodles.scheduleNextChange(this@App)
                // Keep the daily currency refresh only while the Calculator and Live Currency Rates are on and Tor
                // isn't the engine.
                currencyRates.get().syncSchedule()
                // Built lazily: a process started only for a widget or the boot receiver shouldn't pay for
                // camera, network and profile registration on the main thread.
                systemToggleManager.get().clearLegacyAppNightModeOverride()
                multiProfileManager.get().refreshProfiles()
                removeRetiredSecretsOnce()
                SystemDataProvider.getAllApps(this@App, forceRefresh = false)
            } catch (e: Exception) {
                Log.w("AppWarmup", "Background service warm-up non-fatal error", e)
            }
        }
    }
}
