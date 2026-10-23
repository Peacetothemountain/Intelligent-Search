package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.data.widget.WidgetProfileDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetProfiles
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The widget editor's sliders are where they apply and show the design being edited. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WidgetEditorSlidersTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private fun openEditor() {
        disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WidgetSettingsScreen(prefs) {} } }
        compose.settle()
    }

    private fun tap(label: String) {
        compose.onAllNodesWithText(label, useUnmergedTree = true)[0].performClick()
        compose.settle(steps = 5)
    }

    private val isAdjustable = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun slider(name: String) = compose.onNode(hasContentDescription(name) and isAdjustable)

    private fun assertReads(name: String, value: String) =
        slider(name).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))

    /**
     * Transparency fades every subtheme's bar, so it is on the Appearance tab for every subtheme; it was only in Color
     * Studio, which only the Custom subtheme has, so a bar made see-through there stayed see-through with no control.
     */
    @Test
    fun transparencyIsOnTheAppearanceTabForEverySubtheme() {
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putInt("widget_system_background_transparency", 40)
            .commit()
        openEditor()

        for (subtheme in listOf("System", "Light", "Dark", "Custom")) {
            tap(subtheme)
            slider("Transparency").assertExists()
            assertReads("Transparency", "40%")
        }
        tap("Material Design")
        for (subtheme in listOf("Material", "Custom")) {
            tap(subtheme)
            slider("Transparency").assertExists()
        }
    }

    /**
     * The Custom G style's Hue, Saturation and Brightness show the colour of the profile being edited. Their positions
     * were only worked out again when the element or style changed, so after switching profile they showed the last
     * profile's colour, and the first touch picked from it.
     */
    @Test
    fun customIconSlidersFollowAProfileSwitch() {
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_material_g_icon", "Custom G Icon")
            .putBoolean("widget_system_custom_sync_colors", true)
            .putInt("widget_system_custom_g_icon_color", 0xFF00FFFF.toInt())
            .commit()
        // Profile 2 is the same design with every icon a dark red.
        val defaults = WidgetDefaults(0xFF1973E8.toInt(), isDynamicColorActive = false)
        val store = WidgetSettingsStore(prefs)
        val red = 0xFF800000.toInt()
        val system = store.load(WidgetDesign.SYSTEM, defaults).copy(elementColors = WidgetElementColors(red, red, red, red, red, red))
        WidgetProfiles(prefs, defaults).saveState(2, WidgetProfileDesign(WidgetDesign.SYSTEM, system, store.load(WidgetDesign.MATERIAL, defaults)))
        openEditor()
        assertReads("Hue", "180°")

        tap("Profile 2")

        assertReads("Hue", "0°")
        assertReads("Saturation", "100%")
        assertReads("Brightness", "50%")
    }
}
