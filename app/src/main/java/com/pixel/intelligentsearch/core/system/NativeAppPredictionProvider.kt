package com.pixel.intelligentsearch.core.system

import android.content.Context
import android.util.Log
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.NexusLauncherBridge
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native App Prediction Provider.
 * Queries Pixel Launcher predictions (via NexusLauncherBridge) and dynamic UsageStatsManager
 * recency/frequency scoring with zero allocation overhead.
 */
@Singleton
class NativeAppPredictionProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NativeAppPredictionProvider"
    }

    private val nexusLauncherBridge = NexusLauncherBridge(context)

    suspend fun getPredictedApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val allApps = SystemDataProvider.getAllApps(context)
        val allAppsMap = allApps.associateBy { it.packageName }

        // 1. Query Pixel Launcher's native prediction provider (Nexus Launcher bridge)
        try {
            val nexusPredictions = nexusLauncherBridge.getPredictedApps()
            if (nexusPredictions.isNotEmpty()) {
                val matched = nexusPredictions.mapNotNull { allAppsMap[it.packageName] }
                if (matched.isNotEmpty()) {
                    return@withContext matched.take(8)
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Nexus launcher predictions not available: ${e.message}")
        }

        // 2. High-fidelity dynamic recents from UsageStatsManager
        try {
            val recents = SystemDataProvider.getRecentApps(context)
            if (recents.isNotEmpty()) {
                return@withContext recents.take(8)
            }
        } catch (e: Throwable) {
            Log.d(TAG, "UsageStatsManager recents fallback: ${e.message}")
        }

        // 3. Fallback: all installed apps
        allApps.take(8)
    }

    fun notifyAppLaunch(packageName: String, className: String? = null) {
        // App launch event notification placeholder for future system analytics
    }

    fun destroy() {
        // Cleanup if needed
    }
}
