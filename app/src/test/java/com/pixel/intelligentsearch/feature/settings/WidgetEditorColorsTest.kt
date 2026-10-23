package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Colours in the widget editor stay readable: pill labels contrast with their fill, preview icons with the pill. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WidgetEditorColorsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private fun contrast(foreground: Color, background: Color) =
        MaterialYouPaletteHelper.calculateContrastRatio(foreground.toArgb(), background.toArgb())

    @Test
    fun customPillLabelContrastsWithItsFill() {
        val surface = Color(0xFFFAF9FD)
        for (custom in listOf(Color(0xFF78A7FF), Color(0xFF73A6FF))) {
            // Selected, the pill is the custom colour; unselected, the label sits on the gradient's midpoint.
            for (fill in listOf(custom, custom.copy(alpha = 0.75f))) {
                val label = subthemePillLabelColor(fill, surface)
                val ratio = contrast(label, fill.compositeOver(surface))
                assertTrue("label on ${fill.toArgb().toUInt().toString(16)} has contrast $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun unselectedCustomPillLabelIsReadable() {
        // A light custom colour (about #78A6FF), with the System subtheme selected so the Custom pill is unselected.
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_subtheme", "System")
            .putInt("widget_system_custom_hue", 220)
            .putInt("widget_system_custom_saturation", 53)
            .putInt("widget_system_custom_lightness", 100)
            .putInt("widget_system_custom_color_opacity", 100)
            .commit()
        disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        var background = Color.Unspecified
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                background = MaterialTheme.colorScheme.background
                WidgetSettingsScreen(prefs) {}
            }
        }
        compose.settle()

        val node = compose.onAllNodesWithText("Custom", useUnmergedTree = true)[0].fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val label = layouts.single().layoutInput.style.color
        val custom = Color(android.graphics.Color.HSVToColor(floatArrayOf(220f, 0.53f, 1f)))
        val ratio = contrast(label, custom.copy(alpha = 0.75f).compositeOver(background))
        assertTrue("Custom pill label has contrast $ratio", ratio >= 4.5)
    }

    /**
     * Without dynamic colour the System subtheme's fixed light and dark pills follow the app's theme, as the widget's
     * do (it draws in the app's theme); the preview followed the system's, so with the app's theme set against the
     * system's it showed the other pill.
     */
    @Test
    fun fixedSystemPillFollowsTheAppThemeAsTheWidgetDoes() {
        for (appDark in listOf(false, true)) {
            val pill = previewPill(
                isMaterialDesign = false,
                subtheme = "System",
                lockBlack = false,
                accentColor = Color.Blue,
                isDynamicColorActive = false,
                themedSurface = Color.Magenta,
                appDark = appDark
            )
            // The widget's pill without dynamic colour (DynamicThemeDetector's surface container in the app's theme).
            val widgetPill = if (appDark) 0xFF303134.toInt() else 0xFFF1F3F4.toInt()
            assertEquals("appDark=$appDark", widgetPill, pill.fill.toArgb())
            assertEquals("appDark=$appDark", !appDark, pill.isLight)
        }
    }

    @Test
    fun previewPillShadeFollowsTheAppTheme() {
        data class Case(val material: Boolean, val subtheme: String, val lockBlack: Boolean, val dynamic: Boolean)
        val cases = listOf(
            Case(material = false, subtheme = "System", lockBlack = false, dynamic = true),
            Case(material = false, subtheme = "System", lockBlack = false, dynamic = false),
            Case(material = false, subtheme = "Light", lockBlack = false, dynamic = true),
            Case(material = false, subtheme = "Dark", lockBlack = false, dynamic = true),
            Case(material = true, subtheme = "Material", lockBlack = false, dynamic = true),
            Case(material = true, subtheme = "Material", lockBlack = true, dynamic = true),
        )
        val mismatches = mutableSetOf<String>()
        compose.setContent {
            for (appDark in listOf(false, true)) {
                for (systemDark in listOf(false, true)) {
                    val base = LocalConfiguration.current
                    val night = if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    val config = Configuration(base).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                    }
                    CompositionLocalProvider(LocalConfiguration provides config) {
                        IntelligentSearchTheme(darkTheme = appDark) {
                            for (case in cases) {
                                val pill = previewPill(
                                    isMaterialDesign = case.material,
                                    subtheme = case.subtheme,
                                    lockBlack = case.lockBlack,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    isDynamicColorActive = case.dynamic
                                )
                                if ((pill.fill.luminance() > 0.5f) != pill.isLight) {
                                    mismatches += "$case appDark=$appDark systemDark=$systemDark"
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        assertEquals(emptySet<String>(), mismatches)
    }
}
