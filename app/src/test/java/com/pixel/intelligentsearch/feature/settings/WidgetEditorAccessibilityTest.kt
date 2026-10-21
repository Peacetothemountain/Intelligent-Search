package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget editor works with TalkBack: controls are named and announce what is selected, sections are headings,
 * and slots can be reordered without dragging.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WidgetEditorAccessibilityTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private fun openEditor() {
        // The screen has never-ending animations, so the clock is driven by hand instead of waiting for idle.
        disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WidgetSettingsScreen(prefs) {} } }
        compose.settle()
    }

    /** Taps the first control showing [label], the one higher up the page. */
    private fun tap(label: String) {
        compose.onAllNodesWithText(label, useUnmergedTree = true)[0].performClick()
        compose.settle(steps = 5)
    }

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val isAdjustable = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun designSwitcherAnnouncesTheSelectedDesign() {
        openEditor()

        compose.onNodeWithText("System Design").assertIsSelected()
        compose.onNodeWithText("Material Design").assertIsNotSelected()
    }

    @Test
    fun tabsAnnounceSelectionAndTheirNameOnce() {
        openEditor()

        compose.onNodeWithText("Appearance").assertIsSelected()
        compose.onNodeWithText("Widget Shortcuts").assertIsNotSelected()
        // The tab's icon repeated its label, so TalkBack read "Appearance, Appearance".
        compose.onAllNodesWithContentDescription("Appearance").assertCountEquals(0)
    }

    @Test
    fun subthemeAndIconStyleOptionsAnnounceSelection() {
        openEditor()
        tap("Dark")
        tap("Accented")

        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("Light").assertIsNotSelected()
        compose.onNodeWithText("Accented").assertIsSelected()
        compose.onNodeWithText("G ICON STYLE").assert(isHeading)
    }

    @Test
    fun actionIconOptionsAnnounceSelection() {
        openEditor()
        tap("Material Design")

        compose.onNodeWithText("Search").assertIsSelected()
        compose.onNodeWithText("Playing").assertIsNotSelected()
        compose.onNodeWithText("WIDGET ACTION ICON").assert(isHeading)
    }

    @Test
    fun colorStudioSlidersAreNamedFullHeightAdjustables() {
        openEditor()
        tap("Custom")
        tap("Color Studio")

        for (name in listOf("Hue", "Saturation", "Lightness", "Color Opacity", "Transparency")) {
            compose.onNode(hasContentDescription(name) and isAdjustable).assertExists()
        }
        // The 36dp thumb was drawn inside an 8dp box, over the value text above it.
        compose.onNodeWithContentDescription("Hue").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("CUSTOM COLOR PALETTE").assert(isHeading)
    }

    @Test
    fun colorStudioHueIsReadInDegrees() {
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_subtheme", "Custom")
            .putInt("widget_system_custom_hue", 180)
            .commit()
        openEditor()
        tap("Color Studio")

        // Without its own value, TalkBack read the share of 0..360: "50 percent".
        compose.onNode(hasContentDescription("Hue") and isAdjustable)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "180°"))
    }

    @Test
    fun customIconHueIsReadInDegrees() {
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_material_g_icon", "Custom G Icon")
            .putBoolean("widget_system_custom_sync_colors", true)
            .putInt("widget_system_custom_g_icon_color", 0xFF00FFFF.toInt())
            .commit()
        openEditor()

        // Cyan is hue 180.
        compose.onNode(hasContentDescription("Hue") and isAdjustable)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "180°"))
    }

    @Test
    fun customIconColorControlsAreNamed() {
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_material_g_icon", "Custom G Icon")
            .commit()
        openEditor()

        compose.onNode(hasContentDescription("Sync All Colors") and isToggleable()).assertExists()
        compose.onNode(hasContentDescription("Default Google Color Palette") and isToggleable()).assertExists()
        compose.onNodeWithText("Default Google Color Palette").assertExists()
        for (name in listOf("Hue", "Saturation", "Brightness")) {
            compose.onNode(hasContentDescription(name) and isAdjustable).assertExists()
        }
        compose.onNodeWithText("CUSTOM ICON COLORS").assert(isHeading)
    }

    @Test
    fun switchingDesignKeepsTheOpenTab() {
        // The System design has the Custom subtheme, which adds a Color Studio tab the Material design lacks.
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_subtheme", "Custom")
            .putString(SettingsManager.WIDGET_THEME_STYLE.name, "Material You (Minimal)")
            .commit()
        openEditor()
        tap("Widget Shortcuts")
        tap("System Design")

        compose.onNodeWithText("WIDGET SHORTCUTS").assertExists()
        compose.onNodeWithText("CUSTOM COLOR PALETTE").assertDoesNotExist()
    }

    @Test
    fun aTabThatComesBackDoesNotReopenOnItsOwn() {
        // The System design has the Custom subtheme; the Material design keeps its default Material subtheme.
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_subtheme", "Custom")
            .commit()
        openEditor()
        tap("Color Studio")
        // Material has no Color Studio tab, so the editor shows Appearance.
        tap("Material Design")
        // Picking Material's Custom subtheme brings Color Studio back, but the user is still on Appearance.
        tap("Custom")

        compose.onNodeWithText("Appearance").assertIsSelected()
        compose.onNodeWithText("CUSTOM COLOR PALETTE").assertDoesNotExist()
    }

    @Test
    fun shortcutsSectionHeaderIsAHeading() {
        openEditor()
        tap("Widget Shortcuts")

        compose.onNodeWithText("WIDGET SHORTCUTS").assert(isHeading)
    }

    @Test
    fun slotsAreNumberedByPosition() {
        openEditor()
        tap("Widget Shortcuts")

        // Default order: shortcut1, mic, shortcut2, shortcut3.
        compose.onNodeWithText("Microphone Slot:").assertDoesNotExist()
        compose.onNodeWithText("Microphone Slot: 2").assertExists()
        compose.onNodeWithText("Shortcut Slot: 1").assertExists()
        compose.onNodeWithText("Shortcut Slot: 3").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun slotsCanBeMovedWithoutDragging() {
        openEditor()
        tap("Widget Shortcuts")

        // The first slot can only move down.
        compose.onNode(hasText("Google Lens") and hasCustomAction("Move down")).assertExists()
        compose.onNode(hasText("Google Lens") and hasCustomAction("Move up")).assertDoesNotExist()
        compose.onNodeWithText("Voice Search").performCustomAccessibilityActionWithLabel("Move up")
        compose.settle(steps = 5)
        compose.onNodeWithText("Save").performClick()
        compose.settle(steps = 5)

        assertEquals("mic,shortcut1,shortcut2,shortcut3", prefs.getString("widget_system_shortcut_order", null))
    }

    @Test
    fun dragHandleIsAFullSizeTouchTarget() {
        openEditor()
        tap("Widget Shortcuts")

        compose.onAllNodesWithContentDescription("Drag to reorder")[0]
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    private fun hasCustomAction(label: String) = SemanticsMatcher("has custom action '$label'") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
    }
}
