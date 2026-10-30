package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
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
import javax.inject.Inject
import javax.inject.Singleton

/** What Enter does in the real overlay: it acts on the results for the query in the field, and dials whole numbers. */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class SubmitQueryTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    @Inject lateinit var sources: DeviceSearchSources

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private lateinit var viewModel: SearchViewModel
    private val launchedApps = mutableListOf<String>()

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        // Live web suggestions come from the network; these tests must not depend on them.
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
        // A phone with a browser: a web search is handed to another app, never back to this one.
        installBrowser()
    }

    private fun installBrowser() {
        val browser = android.content.ComponentName("com.example.browser", "com.example.browser.Search")
        val packages = shadowOf(context.packageManager)
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
                    onOpenSettings = {},
                    onLaunchApp = { packageName, _, _, _ -> launchedApps += packageName },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
    }

    private fun startedActivity(): Intent? = shadowOf(compose.activity).nextStartedActivity
        ?: shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity

    @Test
    fun enterActsOnTheQueryTypedNotOnTheResultsBeforeIt() {
        // No typo tolerance, so "camx" names no app (with it, Camera is one letter off and Quick Launch rightly opens it).
        prefs.edit().put(SettingsManager.APP_QUICK_LAUNCH, true).put(SettingsManager.APP_FUZZY_SEARCH, false).commit()
        showOverlay()
        viewModel.onQueryChanged("cam")
        compose.settle()

        // One more letter, and Enter before its results are in: Camera, the last query's match, must not open.
        viewModel.onQueryChanged("camx")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.settle()

        assertEquals(emptyList<String>(), launchedApps)
        val search = startedActivity()
        assertTrue("expected a web search, got $search", search?.action == Intent.ACTION_WEB_SEARCH || search?.action == Intent.ACTION_VIEW)
    }

    @Test
    fun enterStartsTheTimerAShortDurationAsksFor() {
        showOverlay()
        viewModel.onQueryChanged("15 min")
        compose.settle()
        assertEquals("Start 15 min Timer", viewModel.uiState.value.directActions.first().title)

        compose.onNode(hasSetTextAction()).performImeAction()
        compose.settle()

        val timer = startedActivity()
        assertEquals(android.provider.AlarmClock.ACTION_SET_TIMER, timer?.action)
        assertEquals(900, timer?.getIntExtra(android.provider.AlarmClock.EXTRA_LENGTH, 0))
    }

    @Test
    fun enterRunsAFullTimerPhraseToo() {
        showOverlay()
        viewModel.onQueryChanged("set a timer for 10 minutes")
        compose.settle()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.settle()
        assertEquals(android.provider.AlarmClock.ACTION_SET_TIMER, startedActivity()?.action)
    }

    @Test
    fun enterOnATimerWithNoClockAppSaysSo() {
        // Only installed apps open: there is no clock. Enter used to leave the overlay as it was, with no word why.
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).checkActivities(true)
        showOverlay()
        viewModel.onQueryChanged("set a timer for 10 minutes")
        compose.settle()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.settle()
        assertEquals("Install a clock app to set timers.", org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun aBareClockTimeOffersAnAlarmButEnterStillSearches() {
        showOverlay()
        viewModel.onQueryChanged("7:30")
        compose.settle()
        assertTrue(viewModel.uiState.value.directActions.first().title.startsWith("Set Alarm for "))

        compose.onNode(hasSetTextAction()).performImeAction()
        compose.settle()

        val search = startedActivity()
        assertTrue("expected a web search, got $search", search?.action == Intent.ACTION_WEB_SEARCH || search?.action == Intent.ACTION_VIEW)
    }

    @Test
    fun enterDialsTheBestMatchContactsWholeNumber() {
        prefs.edit().put(SettingsManager.SEARCH_CONTACTS, true).put(SettingsManager.CONTACT_DIRECT_CALL, true).commit()
        (sources as FakeDeviceSearchSources).contactBook = listOf(ContactItem("Voicemail", "*86#", "content://contacts/voicemail"))
        showOverlay()
        viewModel.onQueryChanged("Voicemail")
        compose.settle()

        compose.onNode(hasSetTextAction()).performImeAction()
        compose.settle(steps = 5)

        val dial = startedActivity()
        assertEquals(Intent.ACTION_DIAL, dial?.action)
        // A tel: link read as a web address loses everything after '#'.
        assertEquals("*86#", dial?.data?.schemeSpecificPart)
    }
}
