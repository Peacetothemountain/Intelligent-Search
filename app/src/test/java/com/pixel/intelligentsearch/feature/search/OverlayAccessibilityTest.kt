package com.pixel.intelligentsearch.feature.search

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How TalkBack meets the real overlay: results it can't act on aren't buttons, and the undo bar is announced. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class OverlayAccessibilityTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        // Live web suggestions come from the network; these tests must not depend on them.
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
    }

    /** As if TalkBack were on and asked for every timed message to stay up for [timeoutMs]. */
    private class SlowReader(private val timeoutMs: Long) : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long, containsIcons: Boolean, containsText: Boolean, containsControls: Boolean
        ) = timeoutMs
    }

    private fun showOverlay(query: String = "", history: List<String> = emptyList(), accessibility: AccessibilityManager? = null) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    history.forEach(viewModel::addSearchHistory)
                    if (query.isNotEmpty()) viewModel.onQueryChanged(query)
                }
                val screen = @androidx.compose.runtime.Composable {
                    SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
                }
                if (accessibility != null) CompositionLocalProvider(LocalAccessibilityManager provides accessibility) { screen() } else screen()
            }
        }
        compose.settle()
    }

    @Test
    fun anAnswerCardIsNotPartOfAButton() {
        showOverlay("100 usd to eur")
        // The panel under the results used to be one clickable, so TalkBack read every plain card as a button in it.
        compose.onAllNodes(hasText("Built-in rates", substring = true) and hasClickAction()).assertCountEquals(0)

        // A tap on the card still stays inside the panel instead of closing the overlay.
        compose.onNodeWithText("Built-in rates", substring = true).performClick()
        compose.settle()
        assertFalse(compose.activity.isFinishing)
    }

    private val eightSearches = (1..8).map { "search $it" }

    private fun clearHistory() {
        compose.onNodeWithText("Delete All").performClick()
        compose.settle(steps = 5)
    }

    private val historyClearedBar = hasAnyDescendant(hasText("Search History Cleared"))

    @Test
    fun theHistoryClearedBarIsAnnounced() {
        showOverlay(history = eightSearches)
        clearHistory()
        compose.onAllNodes(historyClearedBar and SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertCountEquals(1)
    }

    @Test
    fun theHistoryClearedBarStaysAsLongAsTalkBackAsks() {
        showOverlay(history = eightSearches, accessibility = SlowReader(timeoutMs = 20_000))
        clearHistory()
        // Well past the usual five seconds.
        compose.settle(steps = 80)
        compose.onAllNodes(hasText("Search History Cleared")).assertCountEquals(1)
    }
}
