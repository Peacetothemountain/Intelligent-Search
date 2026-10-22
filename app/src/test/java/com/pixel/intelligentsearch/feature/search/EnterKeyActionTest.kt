package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.EnterKeyAction
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.screenshots.confinedEffects
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Singleton

/**
 * Enter Key Action against a device with apps: Open Top Result (the default) opens the Best Match, Search the Web
 * searches whatever matched; a result highlighted with the arrow keys opens either way.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class EnterKeyActionTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private lateinit var viewModel: SearchViewModel
    private val launchedApps = mutableListOf<String>()

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
    }

    private fun showOverlay(query: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                viewModel = hiltViewModel()
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { packageName, _, _, _ -> launchedApps += packageName },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
        viewModel.onQueryChanged(query)
        compose.settle()
    }

    private fun press(keys: List<Key>) {
        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        for (key in keys) field.performKeyInput { pressKey(key) }
        compose.settle(steps = 5)
    }

    private fun startedActivity(): Intent? = shadowOf(compose.activity).nextStartedActivity
        ?: shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity

    @Test
    fun byDefaultEnterOpensTheBestMatch() {
        assertEquals(EnterKeyAction.OPEN_TOP_RESULT, EnterKeyAction.from(prefs.getString(SettingsManager.ENTER_KEY_ACTION.name, null)))
        showOverlay("camera")
        press(listOf(Key.Enter))
        assertEquals(listOf("com.example.camera"), launchedApps)
    }

    @Test
    fun searchTheWebSearchesInsteadOfOpeningTheBestMatch() {
        prefs.edit().put(SettingsManager.ENTER_KEY_ACTION, EnterKeyAction.SEARCH_WEB.storedValue).commit()
        showOverlay("camera")
        press(listOf(Key.Enter))
        assertEquals(emptyList<String>(), launchedApps)
        val search = startedActivity()
        assertTrue("expected a web search, got $search", search?.action == Intent.ACTION_WEB_SEARCH || search?.action == Intent.ACTION_VIEW)
    }

    @Test
    fun aHighlightedResultOpensWhateverEnterKeyActionSays() {
        prefs.edit().put(SettingsManager.ENTER_KEY_ACTION, EnterKeyAction.SEARCH_WEB.storedValue).commit()
        showOverlay("camera")
        // Down onto the Best Match card, the first result, and Enter opens it.
        press(listOf(Key.DirectionDown, Key.Enter))
        assertEquals(listOf("com.example.camera"), launchedApps)
    }

    @Test
    fun byDefaultEnterRunsTheShortPhrasingsAction() {
        showOverlay("15 min")
        press(listOf(Key.Enter))
        assertEquals(android.provider.AlarmClock.ACTION_SET_TIMER, startedActivity()?.action)
    }

    @Test
    fun searchTheWebSearchesTheShortPhrasingInsteadOfRunningItsAction() {
        prefs.edit().put(SettingsManager.ENTER_KEY_ACTION, EnterKeyAction.SEARCH_WEB.storedValue).commit()
        showOverlay("15 min")
        press(listOf(Key.Enter))
        val search = startedActivity()
        assertTrue("expected a web search, got $search", search?.action == Intent.ACTION_WEB_SEARCH || search?.action == Intent.ACTION_VIEW)
    }

    @Test
    fun unknownStoredValuesReadAsTheDefault() {
        assertEquals(EnterKeyAction.OPEN_TOP_RESULT, EnterKeyAction.from("something else"))
        assertEquals(EnterKeyAction.OPEN_TOP_RESULT, EnterKeyAction.from(null))
        assertEquals(EnterKeyAction.SEARCH_WEB, EnterKeyAction.from("search_web"))
    }
}
