package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import com.pixel.intelligentsearch.screenshots.confinedEffects
import androidx.compose.ui.test.onAllNodesWithText
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

/** Saving widget settings writes the edited design only, and an untouched design keeps its legacy fallbacks. */
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
}
