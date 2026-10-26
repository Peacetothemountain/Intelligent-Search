package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.AppSearchScreen
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Singleton

/**
 * The App & Shortcut Search page's Quick App Panel switch and Max Quick Panel Apps slider drive the quick-app pills
 * in the real overlay: the switch shows or hides them, the slider caps how many show.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class QuickPanelSettingsOverlayTest {

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

    /** Four chosen quick-launch apps, short enough that all four fit across the panel at full size. */
    private val pills = listOf("Alfa", "Bravo", "Kilo", "Lima")
    private fun pkg(label: String) = "org.example.${label.lowercase()}"

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit()
            .put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false)
            .put(SettingsManager.SEARCH_PILLS, pills.joinToString(",") { pkg(it) })
            .commit()
        shadowOf(context.packageManager).apply {
            for (label in pills) {
                installPackage(
                    PackageInfo().apply {
                        packageName = pkg(label)
                        applicationInfo = ApplicationInfo().apply {
                            packageName = pkg(label)
                            nonLocalizedLabel = label
                        }
                    }
                )
                setApplicationIcon(pkg(label), ColorDrawable(Color.GREEN))
            }
        }
    }

    private var showOverlay by mutableStateOf(false)

    /** The settings page first; [openOverlay] then swaps the real overlay in, as leaving settings does. */
    private fun showSettingsThenOverlay() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                if (!showOverlay) {
                    AppSearchScreen(prefs, {}, {})
                } else {
                    val viewModel: SearchViewModel = hiltViewModel()
                    SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun openOverlay() {
        showOverlay = true
        compose.waitForIdle()
    }

    private fun shown(label: String) = compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()

    /** Waits for the pills to load their icons off the main thread, then lists which of [pills] are on screen. */
    private fun shownPills(expected: Int): List<String> {
        compose.waitUntil(5_000) { pills.count(::shown) >= expected }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        compose.waitForIdle()
        return pills.filter(::shown)
    }

    private fun tap(title: String) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun theSwitchShowsThePanelInTheOverlay() {
        showSettingsThenOverlay()
        tap("Quick App Panel")
        assertEquals(true, prefs[SettingsManager.QUICK_SEARCH_HORIZONTAL])

        openOverlay()

        assertEquals(pills, shownPills(expected = pills.size))
    }

    @Test
    fun theSwitchHidesThePanelInTheOverlay() {
        prefs.edit().put(SettingsManager.QUICK_SEARCH_HORIZONTAL, true).commit()
        showSettingsThenOverlay()
        tap("Quick App Panel")
        assertEquals(false, prefs[SettingsManager.QUICK_SEARCH_HORIZONTAL])

        openOverlay()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1_000))
        compose.waitForIdle()

        assertEquals(emptyList<String>(), pills.filter(::shown))
    }

    @Test
    fun theSliderCapsHowManyAppsThePanelShows() {
        prefs.edit().put(SettingsManager.QUICK_SEARCH_HORIZONTAL, true).commit()
        showSettingsThenOverlay()
        compose.onNodeWithContentDescription("Max Quick Panel Apps").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        compose.settle(steps = 5)
        // Two is fewer than the apps chosen under Application Search, so the card asks before removing the rest.
        compose.onNodeWithText("Accept").performClick()
        compose.settle(steps = 5)
        assertEquals(2, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])

        openOverlay()

        // The first two in the Quick Launch order show; the last two were removed from the bottom up.
        assertEquals(listOf("Alfa", "Bravo"), shownPills(expected = 2))
        assertEquals(listOf(pkg("Alfa"), pkg("Bravo")).joinToString(","), prefs[SettingsManager.SEARCH_PILLS])
    }

    @Test
    fun byDefaultThePanelIsOffAndShowsUpToSixApps() {
        assertFalse(SettingsManager.QUICK_SEARCH_HORIZONTAL.default)
        assertEquals(6, SettingsManager.SHORTCUT_RESULTS_COUNT.default)
        assertEquals(1..20, SettingsManager.SHORTCUT_RESULTS_COUNT.range)

        showSettingsThenOverlay()
        openOverlay()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1_000))
        compose.waitForIdle()

        assertEquals(emptyList<String>(), pills.filter(::shown))
    }
}
