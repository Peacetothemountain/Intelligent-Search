package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.AppShortcutItem
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Building the search index from the device sources when the app starts. */
@RunWith(RobolectricTestRunner::class)
class CorpusIndexManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob())

    @After fun stop() = scope.cancel()

    /** A device with one recent file and nothing else. */
    private class OneFileSources : DeviceSearchSources {
        override suspend fun allApps(forceRefresh: Boolean) = emptyList<AppItem>()
        override fun invalidateAppsCache() {}
        override fun appUsage() = emptyMap<String, SystemDataProvider.AppUsage>()
        override fun allShortcuts() = emptyList<AppShortcutItem>()
        override suspend fun allContacts() = emptyList<ContactItem>()
        override suspend fun recentFiles(limit: Int, includeHidden: Boolean) =
            listOf(FileItem("report.pdf", "/storage/emulated/0/Download/report.pdf", "application/pdf", "content://media/external/file/1"))
        override suspend fun recentApps(hiddenApps: Set<String>) = emptyList<AppItem>()
        override suspend fun contextAwareQuickApps(hiddenApps: Set<String>) = emptyList<AppItem>()
        override suspend fun upcomingEvents() = emptyList<CalendarEvent>()
        override suspend fun contacts(query: String) = emptyList<ContactItem>()
        override fun cachedWebSuggestions(query: String, engine: String): List<String>? = null
        override suspend fun webSuggestions(query: String, engine: String) = emptyList<String>()
    }

    @Test
    fun filesAreIndexedOnTheFirstPassRightAfterBoot() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(SettingsManager.SEARCH_FILES.name, true).commit()
        // Shortly after boot the uptime clock is below the two-minute refresh interval, so "never indexed" (0)
        // used to read as "indexed moments ago" and the first pass skipped files.
        assertTrue(SystemClock.elapsedRealtime() < 2 * 60 * 1000L)

        val coordinator = UnifiedSearchCoordinator(
            context,
            SystemActionRouter(context, SystemToggleManager(context)),
            MultiProfileManager(context),
            SettingsManager(context)
        )
        val manager = CorpusIndexManager(context, OneFileSources(), coordinator, scope)
        manager.initialize()
        runBlocking { withTimeout(10_000) { manager.isIndexReady.first { it } } }

        assertEquals(listOf("report.pdf"), coordinator.executeSearchSync("report").files.map { it.name })
    }
}
