package com.pixel.intelligentsearch

import android.app.Application
import android.util.Log
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.system.DirectBootManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        setupCrashHandler()
        warmupServices()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val dir = getExternalFilesDir(null) ?: filesDir
                val file = File(dir, "crash_log.txt")
                file.writeText(sw.toString())
                Log.e("CrashLogger", "Crash caught", throwable)
            } catch (e: Exception) {
                Log.e("CrashLogger", "Failed to write crash log: ${e.message}")
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Non-blocking background warmup to achieve sub-50ms cold-start overlay presentation.
     */
    private fun warmupServices() {
        appScope.launch {
            try {
                if (directBootManager.checkIsUserUnlocked()) {
                    multiProfileManager.refreshProfiles()
                    SystemDataProvider.getAllApps(this@App, forceRefresh = false)
                }
            } catch (e: Exception) {
                Log.w("AppWarmup", "Background service warm-up non-fatal error", e)
            }
        }
    }
}
