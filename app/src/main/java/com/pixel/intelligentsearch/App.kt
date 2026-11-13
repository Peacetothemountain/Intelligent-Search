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
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import androidx.core.content.edit

@HiltAndroidApp
class App : Application() {

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
        /** The Material You palette ([DynamicThemeDetector.paletteFingerprint]) the widgets were last drawn in. */
        const val KEY_WIDGET_PALETTE = "widget_palette"
        const val APPS_SETTLED_MS = 2_000L
    }

    /** The languages the cached app list's labels are in; null until known. */
    private var appListLocales: LocaleList? = null

    /**
     * The Material You palette this process last drew the widgets in; null until known, and in Discover's reader
     * process, which draws no widgets and builds no search index.
     */
    @Volatile
    private var widgetPalette: String? = null

    override fun onCreate() {
        super.onCreate()
        // Discover's widget reader runs in its own short-lived process; it needs none of the app's start-up work, and
        // settings written from a second process could clash with the app's own.
        if (getProcessName().endsWith(":reader")) return
        appListLocales = resources.configuration.locales
        widgetPalette = DynamicThemeDetector.paletteFingerprint(this)

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
            // Typed searches match the search index's copy of the labels, so it reads the apps again too.
            if (widgetPalette != null) appEntryPoint.corpusIndexManager().let { if (it.isIndexReady.value) it.reindexApps() }
        }
        // A new wallpaper's colors reach this process as a configuration change once they are in effect. The theme
        // observer can hear the setting change before that and keep the old colors, so the widgets are drawn again
        // from here. Light and dark read the same palette, so switching between them redraws nothing here.
        val drawn = widgetPalette ?: return
        val palette = DynamicThemeDetector.paletteFingerprint(this)
        if (palette != drawn) {
            widgetPalette = palette
            redrawWidgetsInNewPalette(palette)
        }
    }

    private fun redrawWidgetsInNewPalette(palette: String) {
        DynamicThemeDetector.invalidateCache()
        com.pixel.intelligentsearch.feature.widget.clearBitmapCache()
        widgetUpdater.updateAll()
        appScope.launch {
            // Before the first unlock this store can't be read; the next start compares again then.
            runCatching { getSharedPreferences(MAINTENANCE_PREFS, MODE_PRIVATE).edit { putString(KEY_WIDGET_PALETTE, palette) } }
        }
    }

    /**
     * The palette may have changed while this process wasn't running (a new wallpaper overnight), and with the Pixel
     * Launcher nothing else redraws the widgets as the process starts: they would keep the old colors for days. Run
     * at every start, and by [WidgetPaletteWatch] when a new palette is set while the process isn't running.
     */
    @Synchronized
    internal fun redrawWidgetsIfPaletteChanged() {
        if (widgetPalette == null) return
        val palette = DynamicThemeDetector.paletteFingerprint(this)
        widgetPalette = palette
        val maintenance = getSharedPreferences(MAINTENANCE_PREFS, MODE_PRIVATE)
        val drawn = maintenance.getString(KEY_WIDGET_PALETTE, null)
        if (drawn == palette) return
        maintenance.edit { putString(KEY_WIDGET_PALETTE, palette) }
        if (drawn == null) return
        DynamicThemeDetector.invalidateCache()
        com.pixel.intelligentsearch.feature.widget.clearBitmapCache()
        widgetUpdater.updateAll()
    }

    /**
     * A widget's taps point at other apps as they were when it was drawn, so a tap on a shortcut whose app has since
     * been removed or disabled did nothing. The widgets are drawn again once a burst of changes (an evening of app
     * updates) has been quiet for a moment. An app removed while this process wasn't running went unheard: with the
     * Pixel Launcher the failed tap itself redraws the widgets (WidgetLaunchReceiver); with another home app taps open
     * their targets directly, so there the widgets are drawn once as the process starts.
     */
    private fun redrawWidgetsWhenAppsChange() {
        appScope.launch {
            val changes = multiProfileManager.get().packageChanges
            val heard = if (com.pixel.intelligentsearch.feature.widget.WidgetLaunchReceiver.homeIsPixelLauncher(this@App)) changes else changes.onStart { emit(Unit) }
            heard.collectLatest {
                delay(APPS_SETTLED_MS)
                widgetUpdater.updateAll()
            }
        }
    }

    /** Deletes Keystore keys and stores left by removed features, once per install. */
    private fun removeRetiredSecretsOnce() {
        val maintenance = getSharedPreferences(MAINTENANCE_PREFS, MODE_PRIVATE)
        if (maintenance.getBoolean(KEY_RETIRED_SECRETS_REMOVED, false)) return
        appEntryPoint.strongBoxSecurityManager().removeRetiredSecrets()
        maintenance.edit { putBoolean(KEY_RETIRED_SECRETS_REMOVED, true) }
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
                redrawWidgetsWhenAppsChange()
                removeRetiredSecretsOnce()
                redrawWidgetsIfPaletteChanged()
                WidgetPaletteWatch.sync(this@App, keepExisting = true)
                SystemDataProvider.getAllApps(this@App, forceRefresh = false)
            } catch (e: Exception) {
                Log.w("AppWarmup", "Background service warm-up non-fatal error", e)
            }
        }
    }
}
