package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Singleton

/** A device with three apps, one event and one contact, standing in for the real providers. */
class FakeDeviceSearchSources : DeviceSearchSources {
    private fun app(name: String, pkg: String) = AppItem(name, pkg, ColorDrawable(0))
    val apps = listOf(app("Calendar", "com.example.calendar"), app("Camera", "com.example.camera"), app("Hidden Thing", "com.example.hidden"))
    var webSuggestions = listOf("weather today", "weather tomorrow")

    override suspend fun allApps(forceRefresh: Boolean) = apps
    override fun invalidateAppsCache() {}
    override fun appUsage() = emptyMap<String, com.pixel.intelligentsearch.core.data.SystemDataProvider.AppUsage>()
    override fun allShortcuts() = emptyList<com.pixel.intelligentsearch.core.data.AppShortcutItem>()
    override suspend fun allContacts() = contacts("")
    override suspend fun recentFiles(limit: Int, includeHidden: Boolean) = emptyList<com.pixel.intelligentsearch.core.data.FileItem>()
    override suspend fun recentApps(hiddenApps: Set<String>) = apps.filter { it.packageName !in hiddenApps }
    override suspend fun contextAwareQuickApps(hiddenApps: Set<String>) = recentApps(hiddenApps)
    override suspend fun upcomingEvents() = listOf(CalendarEvent("Dentist", "09:30", eventId = 7, beginMillis = 1_000))
    override suspend fun contacts(query: String) =
        listOf(ContactItem("Ada Lovelace", "+1 555 0100", "content://contacts/ada")).filter { it.name.contains(query, ignoreCase = true) }
    override fun cachedWebSuggestions(query: String, engine: String): List<String>? = null
    override suspend fun webSuggestions(query: String, engine: String) = webSuggestions.filter { it.startsWith(query) }
}

/**
 * The search ViewModel against a stand-in device: what it loads, filters and remembers. The stand-in replaces the
 * real sources for this test only; the screenshot tests keep rendering the real device.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SearchViewModelTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule val hilt = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<HiltTestActivity>
    private lateinit var viewModel: SearchViewModel
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    /** Creates the ViewModel the way the overlay does, after the test has set its settings. */
    private fun start() {
        scenario = ActivityScenario.launch(HiltTestActivity::class.java)
        scenario.onActivity { viewModel = ViewModelProvider(it)[SearchViewModel::class.java] }
    }

    /**
     * Work runs on real IO threads and on the main looper, whose delays (the suggestion debounce) only elapse as
     * its clock is moved; wait for the state to satisfy [condition].
     */
    private fun await(what: String, condition: (SearchUiState) -> Boolean): SearchUiState {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            val state = viewModel.uiState.value
            if (condition(state)) return state
            Thread.sleep(25)
        }
        throw AssertionError("timed out waiting for $what; state=${viewModel.uiState.value}")
    }

    @Test
    fun initialLoadListsAppsEventsAndRecentsWithoutHiddenApps() {
        prefs.edit().put(SettingsManager.HIDDEN_APPS, setOf("com.example.hidden")).put(SettingsManager.SEARCH_CALENDAR, true).commit()
        start()
        viewModel.loadInitialData()
        val state = await("initial data") { it.allApps.isNotEmpty() && it.calendarEvents.isNotEmpty() }
        assertEquals(listOf("Calendar", "Camera", "Hidden Thing"), state.allApps.map { it.name })
        assertEquals(listOf("Dentist"), state.calendarEvents.map { it.title })
        // Launcher predictions (real here) may add apps; the device's recents must be there and hidden apps never.
        val recents = state.recentApps.map { it.name }
        assertTrue("recents were $recents", recents.containsAll(listOf("Calendar", "Camera")))
        assertTrue("recents were $recents", "Hidden Thing" !in recents)
    }

    @Test
    fun aQueryFetchesWebSuggestionsAndClearingForgetsThem() {
        prefs.edit().put(SettingsManager.SEARCH_WEB, true).put(SettingsManager.SEARCH_WEB_SUGGESTIONS, true).commit()
        start()
        viewModel.onQueryChanged("weather")
        assertEquals(listOf("weather today", "weather tomorrow"), await("web suggestions") { it.webSuggestions.isNotEmpty() }.webSuggestions)

        viewModel.onQueryChanged("")
        val cleared = await("cleared") { it.query.isEmpty() && it.webSuggestions.isEmpty() }
        assertEquals("", cleared.query)
    }

    @Test
    fun typingFindsAnIndexedApp() {
        start()
        viewModel.onQueryChanged("cal")
        val apps = await("app results") { it.filteredApps.isNotEmpty() }.filteredApps.map { it.name }
        // The instant results are prefix matches only; the full search may add a near miss ("Camera", one letter
        // off) once the index is ready, which is when a slow machine first sees results. The match ranks first either way.
        assertEquals("apps were $apps", "Calendar", apps.first())
    }

    @Test
    fun arithmeticIsAnsweredInline() {
        start()
        viewModel.onQueryChanged("12*12")
        val result = await("math") { it.mathResult != null }.mathResult!!
        assertTrue("math result was $result", result.contains("144"))
    }

    @Test
    fun searchHistoryIsRememberedAndCanBeRemoved() {
        start()
        viewModel.addSearchHistory("first")
        viewModel.addSearchHistory("second")
        assertEquals(listOf("second", "first"), await("history") { it.recentSearches.size == 2 }.recentSearches)
        viewModel.removeSearchHistory("second")
        assertEquals(listOf("first"), await("removal") { it.recentSearches.size == 1 }.recentSearches)
    }

    /** Makes the Battery page open, as the Settings app would on a phone. */
    private fun batteryPageResolves() {
        val info = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.SubSettings"
            }
        }
        shadowOf(ApplicationProvider.getApplicationContext<Context>().packageManager)
            .addResolveInfoForIntent(android.content.Intent(android.content.Intent.ACTION_POWER_USAGE_SUMMARY), info)
    }

    @Test
    fun aSettingsPageIsOfferedAlongsideTheQuickToggle() {
        batteryPageResolves()
        start()
        viewModel.onQueryChanged("battery")
        val state = await("settings results") { it.androidSettings.isNotEmpty() }
        assertEquals("Battery", state.androidSettings.first().title)
        // The Battery Saver quick toggle is still there: settings pages complement it.
        assertEquals("battery", state.systemToggle?.id)
    }

    @Test
    fun turningTheAndroidSettingsSourceOffHidesSettingsPages() {
        batteryPageResolves()
        prefs.edit().put(SettingsManager.SEARCH_ANDROID_SETTINGS, false).commit()
        start()
        viewModel.onQueryChanged("battery")
        kotlinx.coroutines.runBlocking { viewModel.awaitSearch() }
        val state = await("local results") { it.systemToggle?.id == "battery" }
        assertEquals(emptyList<Any>(), state.androidSettings)
    }
}
