package com.pixel.intelligentsearch.feature.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.currency.CurrencyRateWork
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** A device with three apps (or [apps]), one event and one contact, standing in for the real providers. */
class FakeDeviceSearchSources(val apps: List<AppItem> = DEFAULT_APPS) : DeviceSearchSources {
    companion object {
        private fun app(name: String, pkg: String) = AppItem(name, pkg, ColorDrawable(0))
        val DEFAULT_APPS = listOf(app("Calendar", "com.example.calendar"), app("Camera", "com.example.camera"), app("Hidden Thing", "com.example.hidden"))
    }
    var webSuggestions = listOf("weather today", "weather tomorrow")
    /** The calendar, which a test may change while the overlay is away. */
    @Volatile var events = listOf(CalendarEvent("Dentist", "09:30", eventId = 7, beginMillis = 1_000))
    @Volatile var contactBook = listOf(ContactItem("Ada Lovelace", "+1 555 0100", "content://contacts/ada"))
    /** How many times app usage has been read for ranking. */
    val appUsageReads = java.util.concurrent.atomic.AtomicInteger()

    override suspend fun allApps(forceRefresh: Boolean) = apps
    override fun invalidateAppsCache() {}
    override fun appUsage(): Map<String, com.pixel.intelligentsearch.core.data.SystemDataProvider.AppUsage> {
        appUsageReads.incrementAndGet()
        return emptyMap()
    }
    override suspend fun allContacts() = contacts("")
    override suspend fun recentFiles(limit: Int, includeHidden: Boolean) = emptyList<com.pixel.intelligentsearch.core.data.FileItem>()
    override suspend fun recentApps(hiddenApps: Set<String>) = apps.filter { it.packageName !in hiddenApps }
    override suspend fun contextAwareQuickApps(hiddenApps: Set<String>) = recentApps(hiddenApps)
    override suspend fun upcomingEvents() = events
    override suspend fun contacts(query: String) = contactBook.filter { it.name.contains(query, ignoreCase = true) }
    /** When set, the local search waits here (on its own thread) until a test lets it go on. */
    @Volatile var holdCachedSuggestionLookup: java.util.concurrent.CountDownLatch? = null
    override fun cachedWebSuggestions(query: String, engine: String): List<com.pixel.intelligentsearch.core.data.WebSuggestion>? {
        holdCachedSuggestionLookup?.await(10, java.util.concurrent.TimeUnit.SECONDS)
        return null
    }
    override suspend fun webSuggestions(query: String, engine: String) =
        webSuggestions.filter { it.startsWith(query) }.map { com.pixel.intelligentsearch.core.data.WebSuggestion(it) }
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

    @Inject lateinit var sources: DeviceSearchSources
    private val device get() = sources as FakeDeviceSearchSources

    private lateinit var scenario: ActivityScenario<HiltTestActivity>
    private lateinit var viewModel: SearchViewModel
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    @After
    fun resumeFrames() = ShadowChoreographer.setPaused(false)

    /** Creates the ViewModel the way the overlay does, after the test has set its settings. */
    private fun start() {
        scenario = ActivityScenario.launch(HiltTestActivity::class.java)
        scenario.onActivity { viewModel = ViewModelProvider(it)[SearchViewModel::class.java] }
    }

    /**
     * Shows the real overlay on that ViewModel. Its animations never end, so frames run only when asked for (these
     * tests check what is loaded, not what is drawn); otherwise the looper would never go idle.
     */
    private fun showOverlay() {
        com.pixel.intelligentsearch.feature.settings.TutorialManager.completeTutorial(prefs)
        start()
        ShadowChoreographer.setPaused(true)
        scenario.onActivity { activity ->
            activity.setContent {
                IntelligentSearchTheme(darkTheme = false) {
                    SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
                }
            }
        }
    }

    /** The user leaves the overlay for another app and comes back to it. */
    private fun leaveAndReturn() {
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
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
        prefs.edit().put(SettingsManager.HIDDEN_APPS, setOf("com.example.hidden")).commit()
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
        assertEquals(listOf("weather today", "weather tomorrow"), await("web suggestions") { it.webSuggestions.isNotEmpty() }.webSuggestions.map { it.query })

        viewModel.onQueryChanged("")
        val cleared = await("cleared") { it.query.isEmpty() && it.webSuggestions.isEmpty() }
        assertEquals("", cleared.query)
    }

    @Test
    fun suggestionsThatArriveBeforeTheLocalResultsAreKept() {
        prefs.edit().put(SettingsManager.SEARCH_WEB, true).put(SettingsManager.SEARCH_WEB_SUGGESTIONS, true).commit()
        // The local search is held at its cached-suggestion lookup until the engine's answer has landed.
        val localSearch = java.util.concurrent.CountDownLatch(1)
        device.holdCachedSuggestionLookup = localSearch
        start()
        viewModel.onQueryChanged("weather")
        await("web suggestions") { it.webSuggestions.isNotEmpty() }
        localSearch.countDown()

        val local = await("local results") { it.instantAnswer != null }
        assertEquals(listOf("weather today", "weather tomorrow"), local.webSuggestions.map { it.query })
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
    fun aCurrencyAnswerSaysWhereItsRatesComeFrom() {
        start()
        viewModel.onQueryChanged("100 usd to eur")
        // Nothing has been fetched here, so the built-in rates answer and the card says so.
        val answer = await("currency answer") { it.instantAnswer != null }.instantAnswer!!
        assertEquals("currency", answer.iconType)
        assertEquals("100 USD ≈ 92 EUR", answer.title)
        assertEquals("Built-in rates", answer.subtitle)
    }

    @Test
    fun aDateQuestionIsAnsweredInline() {
        start()
        viewModel.onQueryChanged("how many days between 2026-03-01 and 2026-06-30")
        val answer = await("date answer") { it.instantAnswer != null }.instantAnswer!!
        assertEquals("date", answer.iconType)
        assertEquals("121 days", answer.title)
        assertEquals("Between Sunday, March 1, 2026 and Tuesday, June 30, 2026", answer.subtitle)
    }

    /** Waits until the latest query's local results, instant answer included, have been published. */
    private fun awaitLocalResults(): SearchUiState {
        var done = false
        viewModel.viewModelScope.launch { viewModel.awaitSearch(); done = true }
        return await("local results") { done }
    }

    private fun uniqueWork(name: String) = WorkManager.getInstance(ApplicationProvider.getApplicationContext()).getWorkInfosForUniqueWork(name).get()

    @Test
    fun dateAnswersFollowTheCalculatorSwitch() {
        prefs.edit().put(SettingsManager.SEARCH_CALCULATOR, false).commit()
        start()
        viewModel.onQueryChanged("how many days between 2026-03-01 and 2026-06-30")
        assertNull(awaitLocalResults().instantAnswer)
    }

    @Test
    fun aCurrencyAnswerStartsTheRatesDownload() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())
        start()
        viewModel.onQueryChanged("100 usd to eur")
        assertEquals("currency", awaitLocalResults().instantAnswer?.iconType)
        assertEquals(1, uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).size)
        assertEquals(1, uniqueWork(CurrencyRateWork.DAILY_WORK).size)
    }

    @Test
    fun withTheCalculatorOffACurrencyQueryNeverStartsTheRatesDownload() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())
        prefs.edit().put(SettingsManager.SEARCH_CALCULATOR, false).commit()
        start()
        viewModel.onQueryChanged("100 usd to eur")
        assertNull(awaitLocalResults().instantAnswer)
        assertTrue(uniqueWork(CurrencyRateWork.FIRST_FETCH_WORK).isEmpty())
        assertTrue(uniqueWork(CurrencyRateWork.DAILY_WORK).isEmpty())
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
    fun anOlderBuildsSwitchedOffSourceStillOffersSettingsPages() {
        // Android Settings pages are always searched now; a switch an older build stored changes nothing.
        batteryPageResolves()
        prefs.edit().putBoolean("search.android.settings", false).commit()
        start()
        viewModel.onQueryChanged("battery")
        val state = await("settings results") { it.androidSettings.isNotEmpty() }
        assertEquals("Battery", state.androidSettings.first().title)
    }

    /** Makes [intent] open a Settings activity. */
    private fun opens(intent: android.content.Intent) {
        val info = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.SubSettings"
            }
        }
        shadowOf(ApplicationProvider.getApplicationContext<Context>().packageManager).addResolveInfoForIntent(intent, info)
    }

    private fun appDetailsOpens() = opens(
        com.pixel.intelligentsearch.core.search.AndroidSettingsSearch.appDetailsIntent(ApplicationProvider.getApplicationContext<Context>().packageName)
    )

    /** Types [query] and waits until its search has finished. */
    private fun search(query: String): SearchUiState {
        viewModel.onQueryChanged(query)
        kotlinx.coroutines.runBlocking { viewModel.awaitSearch() }
        return await("results for $query") { it.query == query }
    }

    private fun appInfoTitles(state: SearchUiState) = state.androidSettings.filter { it.app != null }.map { it.title }

    @Test
    fun appInfoLeavesOutHiddenApps() {
        appDetailsOpens()
        prefs.edit().put(SettingsManager.HIDDEN_APPS, setOf("com.example.hidden")).commit()
        start()
        // A visible app's App Info is found, so the app index is ready...
        viewModel.onQueryChanged("app info camera")
        assertEquals(listOf("Camera App Info"), appInfoTitles(await("camera app info") { appInfoTitles(it).isNotEmpty() }))
        // ...and the hidden app's isn't.
        assertEquals(emptyList<String>(), appInfoTitles(search("app info hidden thing")))
        assertEquals(emptyList<String>(), appInfoTitles(search("hidden thing")))
    }

    @Test
    fun appInfoNeedsTheAppsSourceToo() {
        appDetailsOpens()
        start()
        // With both sources on, asking for an app's settings finds its App Info.
        viewModel.onQueryChanged("calendar settings")
        assertEquals(listOf("Calendar App Info"), appInfoTitles(await("calendar app info") { appInfoTitles(it).isNotEmpty() }))

        prefs.edit().put(SettingsManager.SEARCH_APPS, false).commit()
        shadowOf(Looper.getMainLooper()).idle()
        val state = search("app info camera")
        assertEquals(emptyList<String>(), appInfoTitles(state))
        assertEquals(emptyList<String>(), appInfoTitles(search("camera")))
    }

    /** What a quick toggle card shows, without its callbacks (which compare by identity). */
    private fun shown(state: SearchUiState): List<Any?> {
        val toggle = state.systemToggle
        return listOf(
            toggle?.id, toggle?.title, toggle?.subtitle, toggle?.iconType, toggle?.isEnabled, toggle?.isActionOnly,
            state.filteredApps.map { it.name }, state.directActions.map { it.title }, state.mathResult, state.instantAnswer
        )
    }

    @Test
    fun quickTogglesShowAlongsideTheirSettingsPages() {
        opens(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        opens(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
        opens(android.content.Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        start()
        val toggles = listOf("wifi" to "Wi-Fi", "bluetooth" to "Bluetooth", "flashlight" to "Flashlight", "airplane mode" to "Airplane Mode")
        val withPages = toggles.map { (query, title) ->
            val state = search(query)
            assertEquals("toggle for \"$query\"", title, state.systemToggle?.title)
            // Wi-Fi, Bluetooth and Airplane Mode also have a Settings page; the flashlight has none.
            assertEquals("pages for \"$query\"", query != "flashlight", state.androidSettings.isNotEmpty())
            shown(state)
        }
        // Every query kept its own toggle.
        assertEquals(toggles.map { it.second }, withPages.map { it[1] })
    }

    /** Installs a browser for https links. */
    private fun installBrowser() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = android.content.ComponentName("org.example.browser", "org.example.browser.Main")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, android.content.IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("https")
        })
    }

    private fun copy(text: String) {
        ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("copied", text))
    }

    @Test
    fun copiedTextIsSearchedWithTheChosenEngine() {
        prefs.edit().put(SettingsManager.SEARCH_ENGINE, "DuckDuckGo").put(SettingsManager.SMART_CLIPBOARD_SUGGESTIONS, true).commit()
        installBrowser()
        copy("cats")
        start()
        // What the overlay does as its window gains focus.
        kotlinx.coroutines.runBlocking { viewModel.refreshClipboardActions() }
        val search = viewModel.uiState.value.directActions.single { it.title == "Search Copied Text" }
        // Never the system web search, which would hand the copied text to Google whatever the chosen engine.
        assertEquals(Intent.ACTION_VIEW, search.intent?.action)
        assertEquals("duckduckgo.com", search.intent?.data?.host)
    }

    @Test
    fun aDismissedClipboardSuggestionStaysDismissedWhenTheOverlayComesBack() {
        prefs.edit().put(SettingsManager.SMART_CLIPBOARD_SUGGESTIONS, true).commit()
        copy("cats")
        start()
        kotlinx.coroutines.runBlocking { viewModel.refreshClipboardActions() }
        val suggestion = viewModel.uiState.value.directActions.single { it.subtitle == "cats" }

        viewModel.dismissDirectAction(suggestion)
        // The window gains focus again on the way back, and the same clip is read again.
        kotlinx.coroutines.runBlocking { viewModel.refreshClipboardActions() }
        assertEquals(emptyList<DirectAction>(), viewModel.uiState.value.directActions)

        // Something new copied is offered.
        copy("dogs")
        kotlinx.coroutines.runBlocking { viewModel.refreshClipboardActions() }
        assertEquals(listOf("dogs"), viewModel.uiState.value.directActions.map { it.subtitle })
    }

    @Test
    fun comingBackToTheOverlayReloadsUpcomingEvents() {
        showOverlay()
        await("first events") { state -> state.calendarEvents.map { it.title } == listOf("Dentist") }

        device.events = listOf(CalendarEvent("Standup", "10:00", eventId = 8, beginMillis = 2_000))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        leaveAndReturn()

        await("fresh events") { state -> state.calendarEvents.map { it.title } == listOf("Standup") }
    }

    @Test
    fun comingBackToTheOverlayRefreshesAppUsageWithFileSearchOff() {
        prefs.edit().put(SettingsManager.SEARCH_FILES, false).commit()
        showOverlay()
        await("first usage read") { device.appUsageReads.get() > 0 }
        val reads = device.appUsageReads.get()

        // Usage is re-read at most every five minutes.
        ShadowSystemClock.advanceBy(Duration.ofMinutes(6))
        leaveAndReturn()

        await("usage re-read") { device.appUsageReads.get() > reads }
    }
}
