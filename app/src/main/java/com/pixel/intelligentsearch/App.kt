package com.pixel.intelligentsearch

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var directBootManager: DirectBootManager

    @Inject
    lateinit var multiProfileManager: MultiProfileManager

    @Inject
    lateinit var systemToggleManager: SystemToggleManager

    @Inject
    lateinit var settingsManager: SettingsManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private companion object {
        const val MAINTENANCE_PREFS = "app_maintenance"
        const val KEY_RETIRED_SECRETS_REMOVED = "retired_secrets_removed_v1"
    }

    override fun onCreate() {
        super.onCreate()

        setupStrictMode()
        setupCrashHandler()
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

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val deContext = createDeviceProtectedStorageContext()
                val dir = deContext.filesDir
                val file = File(dir, "crash_log.txt")
                file.writeText(sw.toString().take(16_384))
                Log.e("CrashLogger", "Crash caught", throwable)
            } catch (e: Exception) {
                Log.e("CrashLogger", "Failed to write crash log: ${e.message}")
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
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
                com.pixel.intelligentsearch.core.system.SearchRoleManager.ensureUserSetupSearchEngineAllowlist(this@App)
                if (!directBootManager.checkIsUserUnlocked()) {
                    directBootManager.isUserUnlocked.filter { it }.first()
                    // Widgets drawn before unlock could only read device-protected (default) settings;
                    // redraw them from the user's real settings now that they are readable.
                    com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.updateAllWidgets(this@App)
                }
                // DataStore is the source of truth for settings; carry over any values that only
                // exist in the legacy SharedPreferences before the overlay first reads them.
                settingsManager.syncSharedPreferencesToDataStore()
                systemToggleManager.clearLegacyAppNightModeOverride()
                multiProfileManager.refreshProfiles()
                removeRetiredSecretsOnce()
                SystemDataProvider.getAllApps(this@App, forceRefresh = false)
            } catch (e: Exception) {
                Log.w("AppWarmup", "Background service warm-up non-fatal error", e)
            }
        }
    }
}
