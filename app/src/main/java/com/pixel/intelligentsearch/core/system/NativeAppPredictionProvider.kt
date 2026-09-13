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

        // 3. Local persistent on-device launch history (covers all non-Pixel devices without usage access)
        try {
            val prefs = context.getSharedPreferences("NATIVE_APP_LAUNCH_PREDICTIONS", Context.MODE_PRIVATE)
            val allKeys = prefs.all.keys.filter { it.startsWith("count_") }
            if (allKeys.isNotEmpty()) {
                val scoredList = allKeys.mapNotNull { countKey ->
                    val pkg = countKey.removePrefix("count_")
                    val app = allAppsMap[pkg] ?: return@mapNotNull null
                    val count = prefs.getInt(countKey, 0)
                    val lastTime = prefs.getLong("time_$pkg", 0L)
                    val recencyHours = (System.currentTimeMillis() - lastTime).coerceAtLeast(0L) / (1000 * 60 * 60)
                    val recencyScore = (100f / (recencyHours + 1f))
                    val totalScore = count * 10f + recencyScore
                    Pair(app, totalScore)
                }.sortedByDescending { it.second }
                .map { it.first }

                if (scoredList.isNotEmpty()) {
                    val combined = (scoredList + allApps).distinctBy { it.packageName }.take(8)
                    return@withContext combined
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Local launch history fallback error: ${e.message}")
        }

        // 4. Fallback: all installed apps
        allApps.take(8)
    }

    fun notifyAppLaunch(packageName: String, className: String? = null) {
        try {
            val prefs = context.getSharedPreferences("NATIVE_APP_LAUNCH_PREDICTIONS", Context.MODE_PRIVATE)
            val currentCount = prefs.getInt("count_$packageName", 0)
            prefs.edit()
                .putInt("count_$packageName", currentCount + 1)
                .putLong("time_$packageName", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record local app launch prediction", e)
        }
    }

    fun destroy() {
        // Cleanup if needed
    }
}
