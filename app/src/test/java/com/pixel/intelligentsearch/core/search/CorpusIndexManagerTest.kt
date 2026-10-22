package com.pixel.intelligentsearch.core.search

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Building the search index from the device sources when the app starts. */
@RunWith(RobolectricTestRunner::class)
class CorpusIndexManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob())

    @After fun stop() = scope.cancel()

    private fun coordinator() = UnifiedSearchCoordinator(
        context,
        SystemActionRouter(context, SystemToggleManager(context)),
        MultiProfileManager(context),
        SettingsManager(context)
    )

    /** A device with one recent file and nothing else. */
    private class OneFileSources : DeviceSearchSources {
        override suspend fun allApps(forceRefresh: Boolean) = emptyList<AppItem>()
        override fun invalidateAppsCache() {}
        override fun appUsage() = emptyMap<String, SystemDataProvider.AppUsage>()
        override suspend fun allContacts() = emptyList<ContactItem>()
        override suspend fun recentFiles(limit: Int, includeHidden: Boolean) =
            listOf(FileItem("report.pdf", "/storage/emulated/0/Download/report.pdf", "application/pdf", "content://media/external/file/1"))
        override suspend fun recentApps(hiddenApps: Set<String>) = emptyList<AppItem>()
        override suspend fun contextAwareQuickApps(hiddenApps: Set<String>) = emptyList<AppItem>()
        override suspend fun upcomingEvents() = emptyList<CalendarEvent>()
        override suspend fun contacts(query: String) = emptyList<ContactItem>()
        override fun cachedWebSuggestions(query: String, engine: String): List<com.pixel.intelligentsearch.core.data.WebSuggestion>? = null
        override suspend fun webSuggestions(query: String, engine: String) = emptyList<com.pixel.intelligentsearch.core.data.WebSuggestion>()
    }

    /** A device with one contact that, like the real provider, reads back only once READ_CONTACTS is granted. */
    private class OneContactSources(private val context: Context) : DeviceSearchSources by OneFileSources() {
        override suspend fun allContacts() =
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                listOf(ContactItem("Alex", "(415) 555-2671", "content://contacts/alex"))
            } else {
                emptyList()
            }
    }

    @Test
    fun filesAreIndexedOnTheFirstPassRightAfterBoot() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(SettingsManager.SEARCH_FILES.name, true).commit()
        // Shortly after boot the uptime clock is below the two-minute refresh interval, so "never indexed" (0)
        // used to read as "indexed moments ago" and the first pass skipped files.
        assertTrue(SystemClock.elapsedRealtime() < 2 * 60 * 1000L)

        val coordinator = coordinator()
        val manager = CorpusIndexManager(context, OneFileSources(), coordinator, scope)
        manager.initialize()
        runBlocking { withTimeout(10_000) { manager.isIndexReady.first { it } } }

        assertEquals(listOf("report.pdf"), coordinator.executeSearchSync("report").files.map { it.name })
    }

    @Test
    fun contactsAreReadOnceThePermissionIsGrantedRightAfterBoot() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(SettingsManager.SEARCH_CONTACTS.name, true).commit()
        // The first pass runs before READ_CONTACTS is granted, so it records no read (0). Shortly after boot the
        // uptime clock is below the one-minute refresh interval, so that 0 used to read as "read moments ago".
        assertTrue(SystemClock.elapsedRealtime() < 60 * 1000L)

        val coordinator = coordinator()
        val manager = CorpusIndexManager(context, OneContactSources(context), coordinator, scope)
        manager.initialize()
        runBlocking { withTimeout(10_000) { manager.isIndexReady.first { it } } }
        assertTrue(coordinator.executeSearchSync("Alex").contacts.isEmpty())

        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        manager.reindexContactsIfStale()

        runBlocking {
            withTimeout(5_000) { while (coordinator.executeSearchSync("Alex").contacts.isEmpty()) delay(10) }
        }
    }
}
