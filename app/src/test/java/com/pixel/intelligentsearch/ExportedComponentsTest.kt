package com.pixel.intelligentsearch

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The activities other apps can start, from the merged manifest of the build under test. Each is an entry point that
 * has to be safe to open from outside, so one appearing here (a library can add one) needs a deliberate decision.
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
}
