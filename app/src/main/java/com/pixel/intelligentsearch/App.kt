package com.pixel.intelligentsearch

import com.pixel.intelligentsearch.di.ApplicationScope
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.di.appEntryPoint
import android.app.Application
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
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
class App : Application(), androidx.work.Configuration.Provider {

    @Inject
    lateinit var directBootManager: DirectBootManager

    @Inject
    lateinit var multiProfileManager: dagger.Lazy<MultiProfileManager>

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

    /**
     * WorkManager starts the first time the app asks for it, not with every process (the manifest removes its start-up
     * initializer). Android can start the process while the phone is still locked after a reboot, when WorkManager's
     * storage can't be opened yet, and its start-up check then crashed the app. The app asks for it only once the user
     * has unlocked ([com.pixel.intelligentsearch.core.system.workManagerOnceUnlocked], and [StartupSync] after the
     * unlock); should a start still fail, it is logged instead of crashing, and the next process tries again.
     */
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setInitializationExceptionHandler { Log.w("AppWorkManager", "WorkManager couldn't start", it) }
            .build()

    /** The languages the cached app list's labels are in; null until known. */
    private var appListLocales: LocaleList? = null

    override fun onCreate() {
        super.onCreate()
        appListLocales = resources.configuration.locales

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
        // UI_HIDDEN arrives every time the overlay is dismissed, so the app list stays: dropping it there made every
        // reopen rescan every installed app. Icons and thumbnails go once the process is in the background.
        if (level >= TRIM_MEMORY_BACKGROUND) {
            com.pixel.intelligentsearch.feature.search.clearAllUiMemoryCaches()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // App labels are read in the current language, so a new language needs a fresh app list; other changes
        // (rotation, dark theme) keep it.
        if (newConfig.locales != appListLocales) {
            appListLocales = newConfig.locales
            SystemDataProvider.invalidateAppsCache()
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
                StartupSync.afterUnlock(this@App, currencyRates.get(), appEntryPoint.liveAnswerEngine())
                // A process started only for a widget or the boot receiver shouldn't pay for camera, network and
                // profile registration on the main thread: the night-mode clean-up needs no toggle manager, and the
                // profile manager is built lazily.
                SystemToggleManager.clearLegacyNightModeOverrideIfNeeded(this@App)
                multiProfileManager.get().refreshProfiles()
                removeRetiredSecretsOnce()
                SystemDataProvider.getAllApps(this@App, forceRefresh = false)
            } catch (e: Exception) {
                Log.w("AppWarmup", "Background service warm-up non-fatal error", e)
            }
        }
    }
}
