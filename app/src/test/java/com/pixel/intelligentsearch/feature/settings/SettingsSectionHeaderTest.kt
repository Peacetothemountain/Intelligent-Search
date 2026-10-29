package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every settings page titles its sections with [SettingsSectionHeader]: one style (primary, bold titleMedium, Title
 * Case), one inset, and always a heading for TalkBack.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class SettingsSectionHeaderTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private var primary = Color.Unspecified
    private var titleMedium = TextStyle.Default

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        disableHandRolledAnimations()
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                primary = MaterialTheme.colorScheme.primary
                titleMedium = MaterialTheme.typography.titleMedium
                content()
            }
        }
        compose.settle()
    }

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun SemanticsNodeInteraction.textStyle(): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.first().layoutInput.style
    }

    /** The one node showing [text] that is a heading, checked to be in the shared section header style. */
    private fun assertSectionHeader(text: String) {
        val header = compose.onNode(hasText(text) and isHeading, useUnmergedTree = true)
        header.assert(isHeading)
        val style = header.textStyle()
        assertEquals("$text colour", primary, style.color)
        assertEquals("$text weight", FontWeight.Bold, style.fontWeight)
        assertEquals("$text size", titleMedium.fontSize, style.fontSize)
        assertNotEquals("$text is not all caps", text.uppercase(), text)
    }

    @Test
    fun headerIsAPrimaryBoldTitleMediumHeading() {
        show { SettingsSectionHeader("Section Title") }
        assertSectionHeader("Section Title")
    }

    @Test
    fun headerKeepsTheSettingsInset() {
        show { Column { SettingsSectionHeader("Section Title") } }
        val bounds = compose.onAllNodesWithText("Section Title")[0].getUnclippedBoundsInRoot()
        assertEquals(16.dp, bounds.left)
        assertEquals(8.dp, bounds.top)
    }

    @Test
    fun appSearchHeadersUseTheSharedStyle() {
        show { AppSearchScreen(prefs, {}, {}) }
        listOf("App Settings", "Quick Panel Apps", "Results Preferences").forEach(::assertSectionHeader)
    }

    @Test
    fun webSearchHeaderUsesTheSharedStyle() {
        show { WebSearchScreen(prefs, onBack = {}) }
        assertSectionHeader("Quick Web Shortcuts")
    }

    @Test
    fun developerSettingsHeaderIsTitleCaseInTheSharedStyle() {
        show { DebugScreen(prefs, {}, {}) }
        assertSectionHeader("Secret Debug Menu")
        compose.onAllNodesWithText("SECRET DEBUG MENU").fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    private fun tap(label: String) {
        compose.onAllNodesWithText(label, useUnmergedTree = true)[0].performClick()
        compose.settle(steps = 5)
    }

    @Test
    fun widgetEditorAppearanceHeadersUseTheSharedStyle() {
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_material_g_icon", "Custom G Icon")
            .commit()
        show { WidgetSettingsScreen(prefs) {} }
        assertSectionHeader("G Icon Style")
        assertSectionHeader("Custom Icon Colors")
        tap("Material Design")
        assertSectionHeader("Widget Action Icon")
    }

    @Test
    fun widgetEditorColorStudioAndShortcutsHeadersUseTheSharedStyle() {
        prefs.edit().putBoolean("widget_system_design_saved", true).putString("widget_system_subtheme", "Custom").commit()
        show { WidgetSettingsScreen(prefs) {} }
        tap("Color Studio")
        assertSectionHeader("Search Bar Color Palette")
        tap("Shortcuts")
        assertSectionHeader("Widget Shortcuts")
    }
}
