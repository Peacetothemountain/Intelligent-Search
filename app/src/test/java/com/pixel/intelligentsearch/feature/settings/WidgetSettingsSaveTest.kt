package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import com.pixel.intelligentsearch.screenshots.confinedEffects
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
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

/** Saving widget settings writes the edited design only, an untouched design keeps its legacy fallbacks, and Save puts the picked profile on the widget. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WidgetSettingsSaveTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    @Test
    fun savingWritesOnlyTheEditedDesign() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        // The screen has never-ending animations, so the clock is driven by hand instead of waiting for idle.
        com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WidgetSettingsScreen(prefs) {} } }
        compose.settle()

        compose.onAllNodesWithText("Dark", useUnmergedTree = true)[0].performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.settle(steps = 5)

        assertTrue(prefs.getBoolean("widget_system_design_saved", false))
        assertEquals("Dark", prefs.getString("widget_system_subtheme", null))
        assertEquals("Google Lens", prefs.getString("widget_system_shortcut_1", null))
        assertEquals("System Default", prefs.getString(SettingsManager.WIDGET_THEME_STYLE.name, null))
        // The Material design was never touched, so it must not be marked saved (its legacy fallbacks stay).
        assertFalse(prefs.getBoolean("widget_material_design_saved", false))
        assertFalse(prefs.contains("widget_material_subtheme"))
    }

    @Test
    fun switchingToMaterialKeepsItsInnerPillState() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("widget_material_design_saved", true)
            .putBoolean("widget_material_lock_black", false)
            .putString(SettingsManager.WIDGET_THEME_STYLE.name, "System Default")
            .commit()
        com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WidgetSettingsScreen(prefs) {} } }
        compose.settle()

        compose.onNodeWithText("Material Design", useUnmergedTree = true).performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.settle(steps = 5)

        assertEquals("Material You (Minimal)", prefs.getString(SettingsManager.WIDGET_THEME_STYLE.name, null))
        // Switching design only shows the Material design; its "Inner Pill & Circle State" stays as the user left it.
        assertFalse(prefs.getBoolean("widget_material_lock_black", true))
    }

    /** Profile 2 holding a System design with the Dark subtheme, while profile 1 is on the widget. */
    private fun withDarkProfile2(): android.content.SharedPreferences {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(SettingsManager.WIDGET_PROFILE_DESIGNS[1].name, "{\"design\":\"SYSTEM\",\"SYSTEM\":{\"design_saved\":true,\"subtheme\":\"Dark\"}}")
            .commit()
        com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        return prefs
    }

    @Test
    fun savingAnotherProfilePutsItsDesignOnTheWidget() {
        val prefs = withDarkProfile2()
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WidgetSettingsScreen(prefs) {} } }
        compose.settle()

        compose.onNodeWithText("Profile 2", useUnmergedTree = true).performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.settle(steps = 5)

        assertEquals("Dark", prefs.getString("widget_system_subtheme", null))
        assertEquals(2, prefs.getInt(SettingsManager.WIDGET_ACTIVE_PROFILE.name, 0))
        // Profile 1 keeps the design the widget had (the System design's System subtheme) until it's picked again.
        assertTrue(prefs.getString(SettingsManager.WIDGET_PROFILE_DESIGNS[0].name, "")!!.contains("\"subtheme\":\"System\""))
        // The first visit records profile 1's design as the state its Reset returns to.
        assertTrue(prefs.contains(SettingsManager.WIDGET_PROFILE_SAVED_DESIGNS[0].name))
    }

    @Test
    fun switchingProfileIsAnUnsavedChangeThatLeavingCanDiscard() {
        val prefs = withDarkProfile2()
        var left = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WidgetSettingsScreen(prefs) { left = true } } }
        compose.settle()

        compose.onNodeWithText("Profile 2", useUnmergedTree = true).performClick()
        compose.settle(steps = 5)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Discard").performClick()
        compose.settle(steps = 5)

        assertTrue(left)
        // Nothing reached the widget or the profiles: profile 1 is still on it, with its own design.
        assertEquals(1, prefs.getInt(SettingsManager.WIDGET_ACTIVE_PROFILE.name, 1))
        assertFalse(prefs.contains("widget_system_subtheme"))
        assertFalse(prefs.contains(SettingsManager.WIDGET_PROFILE_DESIGNS[0].name))
    }
}
