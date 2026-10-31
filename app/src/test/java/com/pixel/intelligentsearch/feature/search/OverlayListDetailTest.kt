package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * On an expanded screen the overlay shows the results beside a detail pane for the top or highlighted result, and the
 * pane's actions open it; on a phone there is no pane.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w1280dp-h800dp-xhdpi")
class OverlayListDetailTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        com.pixel.intelligentsearch.feature.settings.TutorialManager.completeTutorial(prefs)
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
        for (action in listOf(Intent.ACTION_POWER_USAGE_SUMMARY, Settings.ACTION_BATTERY_SAVER_SETTINGS, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) {
            val info = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.android.settings"
                    name = "com.android.settings.SubSettings"
                }
            }
            shadowOf(context.packageManager).addResolveInfoForIntent(Intent(action), info)
        }
    }

    private fun showOverlay(query: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                if (query.isNotEmpty()) LaunchedEffect(Unit) { viewModel.onQueryChanged(query) }
                SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
            }
        }
        compose.settle()
    }

    private fun inPane(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(DETAIL_PANE_TAG)), useUnmergedTree = true)

    /** The Settings page titles in the order the results list shows them. */
    private fun shownTitles(): List<String> = listOf("Battery", "Battery Saver", "Battery Optimization")
        .mapNotNull { title -> runCatching { title to compose.onNodeWithContentDescription("$title settings").fetchSemanticsNode().boundsInRoot.top }.getOrNull() }
        .sortedBy { it.second }
        .map { it.first }

    @Test
    fun thePaneShowsTheTopResultAndFollowsTheHighlight() {
        showOverlay("battery")
        compose.onNodeWithTag(DETAIL_PANE_TAG).assertExists()
        val titles = shownTitles()
        inPane(titles[0]).assertExists()
        inPane("Open Settings").assertExists()

        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput { pressKey(Key.DirectionDown) }
        field.performKeyInput { pressKey(Key.DirectionDown) }
        compose.settle(steps = 4)
        inPane(titles[1]).assertExists()

        inPane("Open Settings").performClick()
        compose.settle(steps = 4)
        val started = shadowOf(compose.activity).nextStartedActivity
            ?: shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        val expected = mapOf("Battery" to Intent.ACTION_POWER_USAGE_SUMMARY, "Battery Saver" to Settings.ACTION_BATTERY_SAVER_SETTINGS, "Battery Optimization" to Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        assertEquals(expected[titles[1]], started?.action)
    }

    @Test
    fun aCalculationsPaneShowsOnlyItsOtherFormsNotTheListsCardAgain() {
        showOverlay("12*12")
        inPane("Other Forms").assertExists()
        inPane("Hexadecimal").assertExists()
        // The calculator card is the list's; the pane doesn't repeat it.
        inPane("= 144").assertDoesNotExist()
        inPane("Open Calculator").assertDoesNotExist()
        compose.onNode(hasText("= 144"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun withNothingTypedThePaneSaysWhatWillShowThere() {
        showOverlay("")
        inPane("Type to See the Top Result Here. Use the Arrow Keys to Move Through the Results.").assertExists()
    }

    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    @Test
    fun aPhoneHasNoDetailPane() {
        showOverlay("battery")
        compose.onNode(isRoot()).assertExists()
        compose.onNodeWithTag(DETAIL_PANE_TAG).assertDoesNotExist()
    }

    @Config(qualifiers = "w673dp-h841dp-xhdpi")
    @Test
    fun anUnfoldedFoldableHeldUprightKeepsOnePane() {
        showOverlay("battery")
        compose.onNodeWithTag(DETAIL_PANE_TAG).assertDoesNotExist()
    }
}
