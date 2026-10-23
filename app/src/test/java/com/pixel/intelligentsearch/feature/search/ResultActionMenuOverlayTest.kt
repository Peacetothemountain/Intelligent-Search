package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.ResultShortcutStore
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.search.ResultShortcuts
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
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

/**
 * The results' long-press menus in the overlay (opened by a long press or TalkBack's More options), what they set,
 * the empty overlay's Pinned row, and that a trigger word an older version saved no longer opens anything.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class ResultActionMenuOverlayTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val store get() = ResultShortcutStore(SettingsManager(context))
    private val battery = "setting:battery"

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        com.pixel.intelligentsearch.feature.settings.TutorialManager.completeTutorial(prefs)
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
        // The Battery page opens, as the Settings app's would on a phone.
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.SubSettings"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), info)
    }

    private fun showOverlay(query: String, history: List<String> = emptyList()) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    history.forEach(viewModel::addSearchHistory)
                    viewModel.onQueryChanged(query)
                }
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { _, _, _, _ -> },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
    }

    private fun startedAction(): String? =
        (shadowOf(compose.activity).nextStartedActivity
            ?: shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity)?.action

    @Test
    fun aLongPressOpensASettingsPagesMenuAndPinToTopPinsIt() {
        showOverlay("battery")
        compose.onNodeWithContentDescription("Battery settings").performTouchInput { longClick() }
        compose.settle(steps = 4)

        compose.onNodeWithTag(RESULT_MENU_TAG).assertExists()
        // Nicknames and trigger words were removed.
        compose.onNodeWithText("Set Trigger Word").assertDoesNotExist()
        compose.onNodeWithText("Add Nickname").assertDoesNotExist()
        compose.onNodeWithText("Pin to Top").performClick()
        compose.settle(steps = 4)

        assertTrue(store.current.isPinned(battery))
        assertEquals("Battery", store.current[battery]?.title)
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun talkBackOpensTheMenuAsMoreOptions() {
        showOverlay("battery")
        compose.onNodeWithContentDescription("Battery settings").performCustomAccessibilityActionWithLabel(MORE_OPTIONS_LABEL)
        compose.settle(steps = 4)
        compose.onNodeWithText("Pin to Top").assertExists()
    }

    @Test
    fun aPinnedPageIsOnTheEmptyOverlaysPinnedRow() {
        store.update { it.withPinned(battery, "Battery", true) }
        showOverlay("")
        compose.onNodeWithTag(PINNED_ROW_TAG).assertExists()
        compose.onNodeWithContentDescription("Battery settings").performClick()
        compose.settle(steps = 2)
        assertEquals(Intent.ACTION_POWER_USAGE_SUMMARY, startedAction())
    }

    @Test
    fun nothingPinnedLeavesThePinnedRowOut() {
        store.update { ResultShortcuts.EMPTY }
        showOverlay("")
        compose.onNodeWithTag(PINNED_ROW_TAG).assertDoesNotExist()
    }

    @Test
    fun aTriggerWordFromAnOlderVersionNoLongerOpensItsResultAsItIsTyped() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(SettingsManager.RESULT_SHORTCUTS.name, """[{"key":"$battery","title":"Battery","trigger":"bat"}]""")
            .commit()
        showOverlay("")
        compose.onNode(hasSetTextAction()).performTextInput("bat ")
        compose.settle(steps = 2)
        assertNull(startedAction())
        assertTrue(store.current.entries.isEmpty())
    }

    @Test
    fun aPastSearchsMenuRemovesItFromHistory() {
        showOverlay("", history = listOf("cats"))
        compose.onNodeWithText("cats").performTouchInput { longClick() }
        compose.settle(steps = 4)
        compose.onNodeWithText("Remove from History").performClick()
        compose.settle(steps = 4)
        compose.onNodeWithText("cats").assertDoesNotExist()
    }
}
