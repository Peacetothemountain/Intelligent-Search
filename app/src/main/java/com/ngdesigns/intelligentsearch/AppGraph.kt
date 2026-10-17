package com.ngdesigns.intelligentsearch

import android.app.Application
import android.content.Context
import com.ngdesigns.intelligentsearch.data.DeviceSources
import com.ngdesigns.intelligentsearch.data.SnapshotBuilder
import com.ngdesigns.intelligentsearch.data.device.LauncherAppSource
import com.ngdesigns.intelligentsearch.data.device.LauncherPredictions
import com.ngdesigns.intelligentsearch.data.device.PixelLauncherPredictions
import com.ngdesigns.intelligentsearch.data.device.SharedFileSource
import com.ngdesigns.intelligentsearch.data.device.SuggestionSource
import com.ngdesigns.intelligentsearch.data.device.SystemCalendarSource
import com.ngdesigns.intelligentsearch.data.device.SystemContactSource
import com.ngdesigns.intelligentsearch.data.device.SystemUsageSource
import com.ngdesigns.intelligentsearch.data.device.WebSuggestionSource
import com.ngdesigns.intelligentsearch.data.history.HistoryStore
import com.ngdesigns.intelligentsearch.data.settings.SettingsStore
import com.ngdesigns.intelligentsearch.launch.Launcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app's long-lived objects, built once and handed to whatever needs them. Wired by hand: there are few enough
 * that a dependency injection framework would add build time without removing code.
 */
class AppGraph(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore.create(context)
    val history = HistoryStore.create(context)
    val device = DeviceSources(
        apps = LauncherAppSource(context),
        usage = SystemUsageSource(context),
        contacts = SystemContactSource(context),
        calendar = SystemCalendarSource(context),
        files = SharedFileSource(context),
    )
    val snapshots = SnapshotBuilder(device)
    val suggestions: SuggestionSource = WebSuggestionSource()
    val predictions: LauncherPredictions = PixelLauncherPredictions(context)
    val launcher = Launcher(context)
}

class SearchApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.graph: AppGraph get() = (applicationContext as SearchApp).graph
