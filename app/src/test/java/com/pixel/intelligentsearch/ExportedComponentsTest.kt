package com.pixel.intelligentsearch

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The activities, receivers and services other apps can reach, from the merged manifest of the build under test. Each
 * is an entry point that has to be safe to open from outside, so one appearing here (a library can add one) needs a
 * deliberate decision; every exported service but the speech recognizer is bound only by the system's permission.
 */
@RunWith(RobolectricTestRunner::class)
class ExportedComponentsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun onlyTheIntendedActivitiesAreExported() {
        val exported = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            .activities.orEmpty().filter { it.exported }.map { it.name }.toSortedSet()
        assertEquals(
            sortedSetOf(
                // Launcher search bars, the web search and assistant intents.
                "com.pixel.intelligentsearch.feature.search.SearchActivity",
                // Other apps' text selection menu and share sheet.
                "com.pixel.intelligentsearch.feature.search.SelectedTextSearchActivity",
                // The app icon, widget configuration and the system's search settings links.
                "com.pixel.intelligentsearch.feature.settings.SettingsActivity",
                // Debug builds only: ui-test-manifest's host for the Compose tests.
                "androidx.activity.ComponentActivity",
            ),
            exported
        )
    }

    private val manifest get() = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS
    )

    @Test
    fun onlyTheWidgetsReceiveBroadcastsFromOutside() {
        val exported = manifest.receivers.orEmpty().filter { it.exported }.associate { it.name to it.permission }
        assertEquals(
            mapOf(
                // Libraries' receivers, which only a shell with DUMP (adb) may send to.
                "androidx.profileinstaller.ProfileInstallReceiver" to "android.permission.DUMP",
                "androidx.work.impl.diagnostics.DiagnosticsReceiver" to "android.permission.DUMP",
                // The widgets: the launcher's widget broadcasts and the clock, time zone and language changes.
                "com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider" to null,
                "com.pixel.intelligentsearch.feature.widget.SearchBoxWidgetProvider" to null,
                "com.pixel.intelligentsearch.feature.widget.SearchWidgetMaterialProvider" to null,
                "com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider" to null,
            ),
            exported
        )
    }

    @Test
    fun everyWidgetDrawsBeforeTheFirstUnlock() {
        val widgets = manifest.receivers.orEmpty().filter { it.name.endsWith("WidgetProvider") || it.name.endsWith("MaterialProvider") }
        assertEquals(4, widgets.size)
        for (widget in widgets) assertEquals(widget.name, true, widget.directBootAware)
    }

    @Test
    fun exportedServicesAreBoundOnlyByTheSystem() {
        val exported = manifest.services.orEmpty().filter { it.exported }.associate { it.name to it.permission }
        assertEquals(
            mapOf(
                "com.pixel.intelligentsearch.feature.widget.SearchTileService" to "android.permission.BIND_QUICK_SETTINGS_TILE",
                "com.pixel.intelligentsearch.feature.voice.IntelligentVoiceInteractionService" to "android.permission.BIND_VOICE_INTERACTION",
                "com.pixel.intelligentsearch.feature.voice.IntelligentVoiceInteractionSessionService" to "android.permission.BIND_VOICE_INTERACTION",
                // Any app may bind a speech recognizer; callers still need RECORD_AUDIO.
                "com.pixel.intelligentsearch.feature.voice.IntelligentRecognitionService" to null,
                "com.pixel.intelligentsearch.core.glance.NotificationAccessListener" to "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                "com.pixel.intelligentsearch.feature.appfunctions.IntelligentSearchAppFunctionService" to "android.permission.BIND_APP_FUNCTION_SERVICE",
                // WorkManager's job service (the daily currency rates).
                "androidx.work.impl.background.systemjob.SystemJobService" to "android.permission.BIND_JOB_SERVICE",
            ),
            exported
        )
    }
}
