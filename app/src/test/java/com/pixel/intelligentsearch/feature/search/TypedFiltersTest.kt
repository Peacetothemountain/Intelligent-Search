package com.pixel.intelligentsearch.feature.search

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.search.ScopedQuery
import com.pixel.intelligentsearch.core.search.SearchScope
import com.pixel.intelligentsearch.core.search.UnifiedSearchCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A device with the stand-in's three apps (Calendar, Camera, Hidden Thing), two contacts and two files, where "cam"
 * names an app, a contact and a file at once.
 */
class TypedFilterSources(val device: FakeDeviceSearchSources = FakeDeviceSearchSources()) : DeviceSearchSources by device {
    init {
        device.contactBook = CONTACTS
        device.webSuggestions = listOf("cam", "camera shop", "themes")
    }

    override suspend fun recentFiles(limit: Int, includeHidden: Boolean) = FILES

    companion object {
        val CAMILLE = ContactItem("Camille Roy", "+1 202-555-0143", "content://contacts/camille")
        val THEMBA = ContactItem("Themba Nkosi", "+1 202-555-0187", "content://contacts/themba")
        val CONTACTS = listOf(CAMILLE, THEMBA)

        private fun file(name: String, id: Int) =
            FileItem(name, "/storage/emulated/0/Documents/$name", "application/pdf", "content://media/external/file/$id", id * 1_000L)

        val CAMPING = file("Camping List.pdf", 1)
        val INVOICE = file("Invoice March.pdf", 2)
        val FILES = listOf(CAMPING, INVOICE)
    }
}

/**
 * Typed filters: a query that starts with "@" searches contacts only, "#" files only and ">" settings pages only, and
 * shows nothing else. The symbol that starts the user's web shortcuts keeps meaning shortcuts.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TypedFiltersTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object SourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = TypedFilterSources()
    }

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var coordinator: UnifiedSearchCoordinator
    @Inject lateinit var sources: DeviceSearchSources
    private val device get() = (sources as TypedFilterSources).device

    private lateinit var viewModel: SearchViewModel
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        hilt.inject()
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        prefs.edit().clear()
            .put(SettingsManager.SEARCH_CONTACTS, true)
            .put(SettingsManager.SEARCH_FILES, true)
            .put(SettingsManager.SEARCH_WEB, true)
            .put(SettingsManager.SEARCH_WEB_SUGGESTIONS, true)
            .commit()
    }

    /** Creates the ViewModel the way the overlay does, after the test has set its settings, and indexes the device. */
    private fun start() {
        coordinator.replaceContacts(TypedFilterSources.CONTACTS)
        coordinator.replaceFiles(TypedFilterSources.FILES)
        val scenario = ActivityScenario.launch(HiltTestActivity::class.java)
        scenario.onActivity { viewModel = ViewModelProvider(it)[SearchViewModel::class.java] }
        // The apps are indexed as the overlay opens; an ordinary search finds Camera once they are.
        viewModel.loadInitialData()
        await("the apps to be listed") { it.allApps.isNotEmpty() }
        finishBackgroundWork()
    }

    /** Searches [query] and waits for its local results. */
    private fun search(query: String): SearchUiState {
        viewModel.onQueryChanged(query)
        var done = false
        viewModel.viewModelScope.launch { viewModel.awaitSearch(); done = true }
        return await(query) { done && it.query == query }
    }

    private fun await(what: String, condition: (SearchUiState) -> Boolean) = viewModel.awaitState(what, condition)

    /** Nothing but [state]'s own kind of result is in it: no app, answer, action, suggestion or offer. */
    private fun assertNothingElse(state: SearchUiState) {
        assertEquals(emptyList<String>(), state.filteredApps.map { it.name })
        assertEquals(emptyList<String>(), state.webSuggestions.map { it.query })
        assertEquals(emptyList<DirectAction>(), state.directActions)
        assertNull(state.enterAction)
        assertNull(state.guessedAction)
        assertNull(state.mathResult)
        assertNull(state.instantAnswer)
        assertNull(state.liveAnswer)
        assertNull(state.systemToggle)
        assertNull(state.volumeSlider)
        assertNull(state.spellingSuggestion)
        assertNull(state.aiOffer)
        assertNull(state.fileTypeFilter)
        assertNull(state.detectedBangQuery)
        assertEquals(emptyList<String>(), state.bangSuggestions.map { it.prefix })
    }

    @Test
    fun anAtSignSearchesContactsOnly() {
        start()
        val state = search("@cam")
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "cam"), state.scoped)
        assertFalse(state.scopedSourceOff)
        assertEquals(listOf(TypedFilterSources.CAMILLE), state.contacts)
        assertEquals(emptyList<FileItem>(), state.files)
        assertEquals(emptyList<String>(), state.androidSettings.map { it.title })
        assertNothingElse(state)
        assertNull(scopeNotice(state))

        // Nothing about it is asked of the web: not with its marker, and not without.
        finishBackgroundWork()
        assertEquals(emptyList<String>(), device.suggestionRequests.toList())
        assertEquals(emptyList<String>(), viewModel.uiState.value.webSuggestions.map { it.query })
    }

    @Test
    fun withoutTheMarkerTheSameWordsAreSearchedEverywhereAgain() {
        start()
        assertNotNull(search("@cam").scoped)

        // What removing the chip does: the words after the marker become the query.
        val plain = search("cam")
        assertNull(plain.scoped)
        assertEquals(listOf("Camera"), plain.filteredApps.map { it.name })
        assertEquals(listOf(TypedFilterSources.CAMILLE), plain.contacts)
        assertEquals(listOf(TypedFilterSources.CAMPING), plain.files)
        assertEquals(listOf("cam", "camera shop"), await("web suggestions") { it.webSuggestions.isNotEmpty() }.webSuggestions.map { it.query })
        assertEquals(listOf("cam"), device.suggestionRequests.toList())
    }

    @Test
    fun aHashSearchesFilesOnly() {
        start()
        val state = search("#cam")
        assertEquals(ScopedQuery(SearchScope.FILES, "cam"), state.scoped)
        assertEquals(listOf(TypedFilterSources.CAMPING), state.files)
        assertEquals(emptyList<ContactItem>(), state.contacts)
        assertEquals(emptyList<String>(), state.androidSettings.map { it.title })
        assertNothingElse(state)

        // A file type word is only a word here: every type is searched for it.
        val typed = search("#pdf")
        assertNull(typed.fileTypeFilter)
        assertEquals(setOf(TypedFilterSources.CAMPING, TypedFilterSources.INVOICE), typed.files.toSet())
    }

    @Test
    fun aGreaterThanSignSearchesSettingsPagesOnly() {
        start()
        val state = search(">search sources")
        assertEquals(ScopedQuery(SearchScope.SETTINGS, "search sources"), state.scoped)
        assertEquals("Search Sources", state.androidSettings.first().title)
        assertEquals(emptyList<ContactItem>(), state.contacts)
        assertEquals(emptyList<FileItem>(), state.files)
        assertNothingElse(state)
    }

    @Test
    fun aSettingsSearchIsNotHeldBackByAContactsName() {
        start()
        // "them" starts Themba's name, so an ordinary search leaves the Appearance page ("theme") out...
        assertEquals(listOf(TypedFilterSources.THEMBA), search("them").contacts)
        assertEquals(emptyList<String>(), viewModel.uiState.value.androidSettings.map { it.title })
        // ...and a search for settings alone lists it.
        assertEquals(listOf("Appearance"), search(">them").androidSettings.map { it.title })
    }

    @Test
    fun nothingIsWorkedOutForTheWordsAfterTheMarker() {
        start()
        // Each of these is a timer, a sum or a switch as an ordinary search.
        assertNotNull(search("15 min").enterAction)
        assertNotNull(search("2+2").mathResult)
        for (query in listOf("#15 min", "#2+2", ">15 min", ">2+2", "@call camille", "#weather")) {
            val state = search(query)
            assertNotNull(query, state.scoped)
            assertNothingElse(state)
        }
        finishBackgroundWork()
        assertNull(viewModel.uiState.value.liveAnswer)
        assertNull(viewModel.uiState.value.spellingSuggestion)
    }

    @Test
    fun theMarkerAloneAsksWhatToType() {
        start()
        val state = search("@")
        assertEquals(ScopedQuery(SearchScope.CONTACTS, ""), state.scoped)
        assertEquals(emptyList<ContactItem>(), state.contacts)
        assertEquals(ScopeNotice("Type a name"), scopeNotice(state))
        assertEquals(ScopeNotice("Type a file name"), scopeNotice(search("#")))
        assertEquals(ScopeNotice("Type a setting"), scopeNotice(search("> ")))
    }

    @Test
    fun wordsThatMatchNothingSaySo() {
        start()
        assertEquals(ScopeNotice("No contacts match \"zzqx\""), scopeNotice(search("@zzqx")))
        assertEquals(ScopeNotice("No files match \"zzqx\""), scopeNotice(search("#zzqx")))
        assertEquals(ScopeNotice("No settings match \"zzqx\""), scopeNotice(search(">zzqx")))
    }

    @Test
    fun withContactSearchSwitchedOffAnAtSignIsAnOrdinarySearch() {
        // Someone who never turned contact search on and types "@name" is searching, not filtering.
        prefs.edit().put(SettingsManager.SEARCH_CONTACTS, false).commit()
        start()
        val state = search("@cam")
        assertNull(state.scoped)
        assertFalse(state.scopedSourceOff)
        assertNull(scopeNotice(state))
        // The other two are still read.
        assertEquals(SearchScope.SETTINGS, search(">search sources").scoped?.scope)
        assertEquals(SearchScope.FILES, search("#cam").scoped?.scope)
    }

    @Test
    fun withoutAccessToContactsTheFilterSaysWhereToTurnItOn() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        start()
        val state = search("@cam")
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "cam"), state.scoped)
        assertTrue(state.scopedSourceOff)
        assertEquals(emptyList<ContactItem>(), state.contacts)
        assertNothingElse(state)
        assertEquals(
            ScopeNotice("Contact search is off. Turn it on in Search Sources.", opensSearchSources = true),
            scopeNotice(state)
        )
        // The marker alone says the same: there is nothing to type a name for.
        assertEquals(scopeNotice(state), scopeNotice(search("@")))
    }

    @Test
    fun backFromAllowingContactsTheSameWordsFindTheirResults() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        start()
        assertTrue(search("@cam").scopedSourceOff)

        // The line opened Search Sources, the user allowed contacts there (which indexes them) and came back.
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        coordinator.replaceContacts(TypedFilterSources.CONTACTS)
        finishBackgroundWork()
        viewModel.reloadOnResume()
        val back = await("the contacts for the words still typed") { !it.scopedSourceOff && it.contacts.isNotEmpty() }
        assertEquals("@cam", back.query)
        assertEquals(listOf(TypedFilterSources.CAMILLE), back.contacts)
        assertNull(scopeNotice(back))
    }

    @Test
    fun withFileSearchSwitchedOffAHashIsAnOrdinarySearch() {
        // "#news" from someone who never turned file search on is a search for a hashtag.
        prefs.edit().put(SettingsManager.SEARCH_FILES, false).commit()
        start()
        val state = search("#cam")
        assertNull(state.scoped)
        assertFalse(state.scopedSourceOff)
        assertNull(scopeNotice(state))
        // Settings pages have no switch, and the contacts' is still on.
        assertFalse(search(">search sources").scopedSourceOff)
        assertEquals(listOf(TypedFilterSources.CAMILLE), search("@cam").contacts)
    }

    @Test
    fun theSymbolThatStartsWebShortcutsKeepsMeaningShortcuts() {
        prefs.edit().put(SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL, "@").commit()
        start()
        val shortcut = search("@g cats")
        assertNull(shortcut.scoped)
        assertEquals("cats", shortcut.detectedBangQuery?.extractedQuery)

        // With "@" taken there is no contacts filter: "@cam" is searched as it always was.
        val plain = search("@cam")
        assertNull(plain.scoped)
        assertEquals(listOf("Camera"), plain.filteredApps.map { it.name })

        // The other two markers still work.
        assertEquals(ScopedQuery(SearchScope.FILES, "cam"), search("#cam").scoped)
        assertEquals(ScopedQuery(SearchScope.SETTINGS, "search sources"), search(">search sources").scoped)
    }

    @Test
    fun withWebShortcutsOffTheirSymbolFiltersAgain() {
        prefs.edit()
            .put(SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL, "@")
            .put(SettingsManager.SEARCH_QUICK_SHORTCUTS, false)
            .commit()
        start()
        val state = search("@cam")
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "cam"), state.scoped)
        assertEquals(listOf(TypedFilterSources.CAMILLE), state.contacts)
    }

    @Test
    fun aFirstWordThatCallsAShortcutStaysAShortcut() {
        // The user's own shortcut "@w", saved under another symbol than the "!" shortcuts start with now. The app reads
        // it as a shortcut only in a query that also has a "!" in it; where it does, it still does.
        prefs.edit().put(
            SettingsManager.CUSTOM_BANGS_JSON,
            """[{"prefix":"@w","name":"Work Wiki","urlTemplate":"https://wiki.example/?q=%s"}]"""
        ).commit()
        start()
        val shortcut = search("@w cats!")
        assertNull(shortcut.scoped)
        assertEquals("Work Wiki", shortcut.detectedBangQuery?.bang?.name)
        // Any other "@" word is a contacts search, and a shortcut after the words doesn't take it over.
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "cam"), search("@cam").scoped)
        val trailing = search("@cam !g")
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "cam !g"), trailing.scoped)
        assertNull(trailing.detectedBangQuery)
    }

    @Test
    fun textHandedInFromAnotherAppIsNeverAFilter() {
        start()
        // "#camping", selected on a page and sent to search: searched as it is, like any other handed-in text.
        viewModel.seedQuery("#cam")
        var done = false
        viewModel.viewModelScope.launch { viewModel.awaitSearch(); done = true }
        val state = await("the handed-in text's results") { done && it.query == "#cam" }
        assertNull(state.scoped)
        assertEquals(listOf("Camera"), state.filteredApps.map { it.name })
    }

    @Test
    fun whatWasTypedForAResultIsRememberedWithoutItsMarker() {
        start()
        assertEquals(listOf(TypedFilterSources.CAMILLE), search("@cam").contacts)
        // Twice: one stray tap doesn't make a learned pick.
        repeat(2) {
            viewModel.recordOpened(TypedFilterSources.CAMILLE)
            finishBackgroundWork()
        }

        // The contact picked for "@cam" is the learned pick for "cam", with or without the marker.
        val key = com.pixel.intelligentsearch.core.search.ResultKeys.contact(TypedFilterSources.CAMILLE)
        assertEquals(key, search("cam").learnedPick)
        assertEquals(key, search("@cam").learnedPick)
    }
}
