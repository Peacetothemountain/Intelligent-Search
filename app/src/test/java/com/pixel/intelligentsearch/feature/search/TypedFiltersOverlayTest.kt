package com.pixel.intelligentsearch.feature.search

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.search.SearchScope
import com.pixel.intelligentsearch.core.search.UnifiedSearchCoordinator
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Typed filters in the real overlay ("@" contacts, "#" files, ">" settings pages): the chip and the line under it,
 * what Enter opens, and where the arrow keys stop. Enter never leaves the kind of result asked for: no web search, no
 * Quick Launch app.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class TypedFiltersOverlayTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var sources: DeviceSearchSources
    @Inject lateinit var coordinator: UnifiedSearchCoordinator

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private lateinit var viewModel: SearchViewModel
    private val launchedApps = mutableListOf<String>()
    private val openedSettings = mutableListOf<String>()
    private val camille = ContactItem("Camille Roy", "+1 202-555-0143", "content://contacts/camille")

    private val contactsChip = "Showing only contacts. Remove filter"
    private val contactSearchOff = "Contact search is off. Turn it on in Search Sources."

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        prefs.edit()
            // Live web suggestions come from the network; these tests must not depend on them.
            .put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false)
            .put(SettingsManager.SEARCH_CONTACTS, true)
            .put(SettingsManager.SEARCH_FILES, true)
            // With Quick Launch on, Enter on "cam" opens Camera: a filtered search must never fall through to that.
            .put(SettingsManager.APP_QUICK_LAUNCH, true)
            .commit()
        (sources as FakeDeviceSearchSources).contactBook = listOf(camille)
        coordinator.replaceContacts(listOf(camille))
        installBrowser()
    }

    /** A phone with a browser, so a web search would show up as a started activity. */
    private fun installBrowser() {
        val browser = android.content.ComponentName("com.example.browser", "com.example.browser.Search")
        val packages = shadowOf(app.packageManager)
        packages.addActivityIfNotPresent(browser)
        packages.addIntentFilterForActivity(browser, android.content.IntentFilter(Intent.ACTION_WEB_SEARCH).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        packages.addIntentFilterForActivity(
            browser,
            android.content.IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            }
        )
    }

    private fun showOverlay() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                viewModel = hiltViewModel()
                SearchOverlayScreen(
                    onOpenSettings = { openedSettings += it },
                    onLaunchApp = { packageName, _, _, _ -> launchedApps += packageName },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
    }

    private val field get() = compose.onNode(hasSetTextAction())

    private fun type(query: String) {
        viewModel.onQueryChanged(query)
        compose.settle()
    }

    private fun pressEnterOnTheKeyboard() {
        field.performImeAction()
        compose.settle(steps = 5)
    }

    private fun press(keys: List<Key>) {
        field.requestFocus()
        for (key in keys) field.performKeyInput { pressKey(key) }
        compose.settle(steps = 4)
    }

    private fun startedActivity(): Intent? = shadowOf(compose.activity).nextStartedActivity ?: shadowOf(app).nextStartedActivity

    private fun assertFieldSays(text: String) =
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))

    @Test
    fun aContactsSearchShowsItsChipAndTheContact() {
        showOverlay()
        type("@cam")
        compose.onNodeWithContentDescription(contactsChip).assertExists()
        assertEquals(true, compose.onAllNodesWithText("Camille Roy").fetchSemanticsNodes().isNotEmpty())
        // The Camera app, which "cam" finds in an ordinary search, is not a contact.
        assertEquals(emptyList<String>(), viewModel.uiState.value.filteredApps.map { it.name })
        assertEquals(0, compose.onAllNodesWithText("Camera").fetchSemanticsNodes().size)
    }

    @Test
    fun tappingTheChipTakesTheMarkerOutOfTheField() {
        showOverlay()
        type("@cam")
        compose.onNodeWithContentDescription(contactsChip).performClick()
        compose.settle()

        assertFieldSays("cam")
        assertEquals("cam", viewModel.uiState.value.query)
        assertNull(viewModel.uiState.value.scoped)
        assertEquals(0, compose.onAllNodesWithContentDescription(contactsChip).fetchSemanticsNodes().size)
        // The words are searched everywhere again.
        assertEquals(listOf("Camera"), viewModel.uiState.value.filteredApps.map { it.name })
    }

    @Test
    fun enterOpensTheContactAndNeverTheQuickLaunchApp() {
        prefs.edit().put(SettingsManager.CONTACT_DIRECT_CALL, true).commit()
        showOverlay()
        type("@cam")
        pressEnterOnTheKeyboard()

        val dial = startedActivity()
        assertEquals(Intent.ACTION_DIAL, dial?.action)
        assertEquals(emptyList<String>(), launchedApps)
    }

    @Test
    fun enterWithNothingOfThatKindToOpenDoesNothing() {
        showOverlay()
        // No such file, contact or setting, only the marker, and a shortcut after the words.
        for (query in listOf("#cam", "@zzqx", ">zzqx", "@", "#", "@zzqx !g", "#example.com", "#15 min")) {
            type(query)
            pressEnterOnTheKeyboard()
            assertEquals(query, emptyList<String>(), launchedApps)
            assertNull(query, startedActivity())
            assertEquals(query, emptyList<String>(), openedSettings)
        }

        // The same words without a marker do what Enter always did: Quick Launch opens Camera.
        type("cam")
        pressEnterOnTheKeyboard()
        assertEquals(listOf("com.example.camera"), launchedApps)
    }

    @Test
    fun enterOpensTheFirstSettingsPageOfASettingsSearch() {
        showOverlay()
        type(">search sources")
        assertEquals(SearchScope.SETTINGS, viewModel.uiState.value.scoped?.scope)
        pressEnterOnTheKeyboard()
        assertEquals(listOf("search_sources"), openedSettings)
        assertNull(startedActivity())
    }

    @Test
    fun theLineForASearchThatIsOffOpensSearchSources() {
        // Contact search is switched on, but the phone no longer lets the app read contacts.
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        showOverlay()
        type("@cam")
        compose.onNodeWithContentDescription(contactsChip).assertExists()
        compose.onNodeWithText(contactSearchOff).performClick()
        compose.settle()
        assertEquals(listOf("search_sources"), openedSettings)
    }

    @Test
    fun theOtherLinesOnlySaySomething() {
        showOverlay()
        type("#")
        compose.onNodeWithText("Type a file name").assertExists()
        type("@")
        compose.onNodeWithText("Type a name").performClick()
        type("@zzqx")
        compose.onNodeWithText("No contacts match \"zzqx\"").performClick()
        type(">")
        compose.onNodeWithText("Type a setting").assertExists()
        compose.settle()
        assertEquals(emptyList<String>(), openedSettings)
        assertNull(startedActivity())
    }

    @Test
    fun theArrowKeysStopOnTheChipFirstAndEnterRemovesIt() {
        showOverlay()
        type("@cam")
        press(listOf(Key.DirectionDown, Key.Enter))

        assertFieldSays("cam")
        assertNull(viewModel.uiState.value.scoped)
        // Removing the chip stays in search: nothing opened.
        assertNull(startedActivity())
        assertEquals(emptyList<String>(), launchedApps)
    }

    @Test
    fun theArrowKeysReachTheContactAfterTheChip() {
        prefs.edit().put(SettingsManager.CONTACT_DIRECT_CALL, true).commit()
        showOverlay()
        type("@cam")
        // The chip, then the contact (its Best Match card).
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.Enter))
        assertEquals(Intent.ACTION_DIAL, startedActivity()?.action)
    }

    @Test
    fun theArrowKeysReachTheLineThatOpensSearchSources() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        showOverlay()
        type("@cam")
        // The chip, then the line; there is nothing after it to move on to.
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionDown, Key.Enter))
        assertEquals(listOf("search_sources"), openedSettings)
    }

    @Test
    fun aLineThatOnlySaysSomethingIsNoKeyboardStop() {
        showOverlay()
        type("@zzqx")
        // Only the chip can be reached: a second Down stays on it, and Enter removes it.
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.Enter))
        assertFieldSays("zzqx")
        assertEquals(emptyList<String>(), openedSettings)
    }
}
