package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.ui.splitsAWord
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
import org.robolectric.annotation.GraphicsMode

/**
 * Widget Customization on a phone on its side and at the largest text size: every control can be reached, and no
 * label is cut off or spills out of its chip. Held upright at the default size, the preview, profiles and selectors
 * stay pinned at the top as before. Text is measured with real fonts (native graphics), as on a phone.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
class WidgetSettingsLayoutTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        disableHandRolledAnimations()
    }

    private fun showPage() {
        // The screen has never-ending animations, so the clock is driven by hand instead of waiting for idle.
        compose.mainClock.autoAdvance = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WidgetSettingsScreen(prefs) {} } }
        compose.settle()
    }

    private val isScrollable = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private val insideScroll = hasAnyAncestor(isScrollable)

    private fun node(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true)[0]

    private fun layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    @Test
    @Config(qualifiers = "w915dp-h411dp-land-xhdpi")
    fun onAPhoneOnItsSideEveryControlCanBeReached() {
        showPage()
        // The page scrolls as one: the preview, profiles and tabs go up with it, out of the way of the controls,
        // instead of staying pinned over the whole window with the tabs and controls below its edge.
        for (text in listOf("Profile 1", "System Design", "Shortcuts", "Display G Icon", "Corner Radius")) {
            node(text).assert(insideScroll)
        }
        val scrolls = compose.onAllNodes(isScrollable, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("the page has more than the window to scroll", scrolls.any { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f })
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun heldUprightThePreviewAndProfilesStayPinned() {
        showPage()
        // As before: the preview, profiles and tabs stay put, and only the open tab's controls scroll under them.
        for (text in listOf("Profile 1", "System Design", "Shortcuts")) node(text).assert(insideScroll.not())
        node("Display G Icon").assert(insideScroll)
        // At the default size the title sits in the bar, as on every page.
        compose.onNodeWithText("Widget Customization").assert(isHeading())
        assertEquals(1, layoutOf("Widget Customization").lineCount)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi", fontScale = 2f)
    fun atTheLargestTextNothingIsCutOff() {
        showPage()
        // The title has no room beside the bar's buttons: it gets a line of its own, whole, and is still the heading.
        val title = layoutOf("Widget Customization")
        assertFalse("the title is cut short", title.hasVisualOverflow)
        assertFalse("the title splits a word", splitsAWord("Widget Customization", title))
        compose.onNodeWithText("Widget Customization")
            .assert(isHeading())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Widget Customization"))
        // Profile chips and the design selector wrap between words rather than spill over their outlines.
        for (label in listOf("Profile 1", "Profile 5", "System Design", "Material Design")) {
            val layout = layoutOf(label)
            assertFalse("$label spills out", layout.hasVisualOverflow)
            assertFalse("$label splits a word", splitsAWord(label, layout))
        }
    }
}
