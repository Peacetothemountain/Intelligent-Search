package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTextReplacement
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.bangs.SearchBang
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.ui.LOADING_INDICATOR_TAG
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Quick Web Shortcuts editor: trigger symbol, restoring and removing shortcuts, and the add dialog's rules.
 *
 * Under Robolectric a Compose dialog holding a text field relays itself out forever, so the add dialog's rules
 * are tested through the functions it calls and its app list on its own.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WebQuickShortcutsCardTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val prefs: SharedPreferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(LocalSettingsViewModel provides hiltViewModel<SettingsViewModel>()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { WebQuickShortcutsCard() }
                }
            }
        }
        compose.settle(steps = 5)
    }

    private fun customTriggerField(): SemanticsNodeInteraction =
        compose.onNode(hasSetTextAction() and hasText("Custom Trigger Symbol"))

    private fun pickCustomTrigger() {
        compose.onNodeWithText("Shortcut Trigger Symbol").performClick()
        compose.settle(steps = 3)
        compose.onNodeWithText("Custom").performClick()
        compose.settle(steps = 3)
    }

    /** Saving replaces whatever has the trigger, so a clash must be refused before it deletes a shortcut. */
    @Test
    fun aTriggerAlreadyInUseIsRefused() {
        val foo = SearchBang(prefix = "!foo", name = "Foo", urlTemplate = "https://a.example/?q=%s")
        val youTube = SearchBangManager.BUILT_IN_BANGS.first { it.name == "YouTube" }
        val active = listOf(foo, youTube)

        assertTrue(webShortcutTriggerTaken("!foo", editing = null, active = active))
        assertTrue(webShortcutTriggerTaken("!yt", editing = null, active = active))
        assertFalse(webShortcutTriggerTaken("!bar", editing = null, active = active))
        // Editing a shortcut may keep its own trigger, but not take another's.
        assertFalse(webShortcutTriggerTaken("!foo", editing = foo, active = active))
        assertTrue(webShortcutTriggerTaken("!yt", editing = foo, active = active))
    }

    @Test
    fun aCustomTriggerIsSymbolsOnly() {
        show()
        pickCustomTrigger()

        customTriggerField().performTextReplacement("g")
        compose.settle(steps = 3)
        assertEquals("!", prefs[SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL])
    }

    /** "#" is also a preset; typing it must not swap the custom field away before "##" is finished. */
    @Test
    fun theCustomFieldStaysWhileAPresetSymbolIsTyped() {
        show()
        pickCustomTrigger()

        customTriggerField().performTextReplacement("#")
        compose.settle(steps = 3)
        customTriggerField().performTextReplacement("##")
        compose.settle(steps = 3)
        assertEquals("##", prefs[SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL])
        customTriggerField().assertExists()
    }

    @Test
    fun theAppListShowsProgressWhileTheAppsLoad() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { InstalledAppList(apps = null) { _, _ -> } } }
        compose.onNodeWithTag(LOADING_INDICATOR_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("No matching apps found").assertDoesNotExist()
    }

    @Test
    fun theAppListSaysWhenNothingMatches() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { InstalledAppList(apps = emptyList()) { _, _ -> } } }
        compose.onNodeWithText("No matching apps found").assertExists()
        compose.onNodeWithTag(LOADING_INDICATOR_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theAppListPicksAnApp() {
        var picked: Pair<String, String>? = null
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                InstalledAppList(apps = listOf("Alpha Notes" to "com.example.alpha")) { name, pkg -> picked = name to pkg }
            }
        }
        compose.onNodeWithText("Alpha Notes").performClick()
        assertEquals("Alpha Notes" to "com.example.alpha", picked)
    }

    @Test
    fun aBuiltInRemovedUnderAnOlderTriggerCanBeRestored() {
        prefs.edit().put(SettingsManager.DISABLED_WEB_SHORTCUTS, setOf("@yt")).commit()
        show()

        compose.onNode(hasText("Add") and hasClickAction() and hasAnyAncestor(hasAnyChild(hasText("YouTube")))).performClick()
        compose.settle(steps = 5)
        assertTrue(prefs[SettingsManager.DISABLED_WEB_SHORTCUTS].isEmpty())
    }

    @Test
    fun talkBackCanRemoveABuiltInShortcut() {
        show()
        compose.onNode(hasText("YouTube")).performCustomAccessibilityActionWithLabel("Remove")
        compose.settle(steps = 5)
        assertTrue("!yt" in prefs[SettingsManager.DISABLED_WEB_SHORTCUTS])
    }

    @Test
    fun activeShortcutsIsAHeading() {
        show()
        compose.onNode(hasText("Active Shortcuts") and isHeading()).assertExists()
    }
}
