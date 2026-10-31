package com.pixel.intelligentsearch.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.WidgetSettingsScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the widget settings views beyond the default one: each case taps through the screen (design, then
 * subtheme, then tab) before capturing. See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WidgetScreenGoldenTest(private val case: String, private val taps: List<String>, private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    @Test
    fun widgetView() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) { WidgetSettingsScreen(prefs) {} }
            }
        }
        compose.settle()
        for (label in taps) {
            // The first match is the control higher up the page (e.g. the "Custom" subtheme pill, not the G icon style).
            compose.onAllNodesWithText(label, useUnmergedTree = true)[0].performClick()
            compose.settle()
        }
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/widget/${case}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={2}")
        fun parameters(): List<Array<Any>> = listOf(
            "system_shortcuts" to listOf("Shortcuts"),
            "system_custom_appearance" to listOf("Custom"),
            "system_color_studio" to listOf("Custom", "Color Studio"),
            "material_appearance" to listOf("Material Design"),
            "material_shortcuts" to listOf("Material Design", "Shortcuts"),
        ).flatMap { (case, taps) -> listOf(arrayOf<Any>(case, taps, false), arrayOf<Any>(case, taps, true)) }
    }
}
