package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A physical keyboard in the overlay: the arrow keys move through the results and Enter opens the highlighted one,
 * Escape clears then closes, keys with Ctrl stay the field's, and Show Keyboard When Search Opens decides whether the
 * field has focus when search opens.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class OverlayKeyboardInputTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    /** The Settings pages a search for "battery" finds, by what TalkBack reads for each. */
    private val batteryPages = mapOf(
        "Battery settings" to Intent.ACTION_POWER_USAGE_SUMMARY,
        "Battery Saver settings" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "Battery Optimization settings" to Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
    )

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        com.pixel.intelligentsearch.feature.settings.TutorialManager.completeTutorial(prefs)
        // Live web suggestions come from the network; this test must not depend on them.
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
        for (action in batteryPages.values) {
            val info = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.android.settings"
                    name = "com.android.settings.SubSettings"
                }
            }
            shadowOf(context.packageManager).addResolveInfoForIntent(Intent(action), info)
        }
    }

    private fun showOverlay(query: String, keyboardDisabled: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                if (query.isNotEmpty()) LaunchedEffect(Unit) { viewModel.onQueryChanged(query) }
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { _, _, _, _ -> },
                    viewModel = viewModel,
                    isKeyboardDisabled = keyboardDisabled
                )
            }
        }
        compose.settle()
    }

    private val field get() = compose.onNode(hasSetTextAction())

    private fun press(keys: List<Key>) {
        field.requestFocus()
        for (key in keys) field.performKeyInput { pressKey(key) }
        compose.settle(steps = 4)
    }

    /** The pages shown, top to bottom, as the actions they open. */
    private fun shownPages(): List<String> = batteryPages.keys
        .mapNotNull { label ->
            runCatching { compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot.top to batteryPages.getValue(label) }.getOrNull()
        }
        .sortedBy { it.first }
        .map { it.second }

    private fun startedAction(): String? =
        (shadowOf(compose.activity).nextStartedActivity
            ?: shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity)?.action

    @Test
    fun downAndEnterOpenTheFirstResult() {
        showOverlay("battery")
        val pages = shownPages()
        assertTrue("found $pages", pages.size >= 2)

        press(listOf(Key.DirectionDown, Key.Enter))
        assertEquals(pages[0], startedAction())
    }

    @Test
    fun downMovesOnStopsAtTheLastAndUpComesBack() {
        showOverlay("battery")
        val pages = shownPages()

        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.Enter))
        assertEquals(pages[1], startedAction())

        press(List(pages.size + 3) { Key.DirectionDown } + Key.Enter)
        assertEquals(pages.last(), startedAction())

        // Opening a result takes the highlight away, so this starts again from the field.
        press(List(pages.size + 3) { Key.DirectionDown } + Key.DirectionUp + Key.Enter)
        assertEquals(pages[pages.size - 2], startedAction())
    }

    @Test
    fun tabEntersTheResultsAndShiftTabLeavesThem() {
        showOverlay("battery")
        val pages = shownPages()

        press(listOf(Key.Tab, Key.Tab, Key.Enter))
        assertEquals(pages[1], startedAction())

        // Shift+Tab from the first result goes back to the field; Enter is then the field's own (a web search).
        field.requestFocus()
        field.performKeyInput { pressKey(Key.Tab) }
        field.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        field.performKeyInput { pressKey(Key.Enter) }
        compose.settle(steps = 4)
        assertNotEquals(pages[0], startedAction())
    }

    @Test
    fun escapeClearsTheQueryThenClosesSearch() {
        showOverlay("battery")
        press(listOf(Key.DirectionDown, Key.Escape))
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertTrue(!compose.activity.isFinishing)

        press(listOf(Key.Escape))
        compose.settle()
        assertTrue(compose.activity.isFinishing)
    }

    @Test
    fun ctrlBackspaceStaysTheFieldsAndDeletesAWord() {
        showOverlay("battery saver")
        field.requestFocus()
        field.performKeyInput { pressKey(Key.MoveEnd) }
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Backspace) } }
        compose.settle(steps = 4)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("battery ")))
    }

    @Test
    fun searchOpensWithTheFieldFocusedByDefault() {
        showOverlay("", keyboardDisabled = false)
        field.assertIsFocused()
    }

    @Test
    fun withShowKeyboardOffSearchOpensWithTheFieldUnfocused() {
        prefs.edit().put(SettingsManager.SHOW_KEYBOARD_ON_OPEN, false).commit()
        showOverlay("", keyboardDisabled = false)
        field.assertIsNotFocused()
        assertEquals(false, keyboardUpOnOpen(prefs))
    }
}
