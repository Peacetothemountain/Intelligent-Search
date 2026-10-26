package com.pixel.intelligentsearch.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.widget.CountdownSetupScreen
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider
import org.junit.After
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
import java.time.LocalDate

/**
 * Goldens of the countdown widget's setup screen, light and dark, set to Halloween, with the widget itself previewed
 * at the top in the search bar's design. See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class CountdownSetupGoldenTest(private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    private val defaultNow = CountdownWidgetProvider.now

    @After fun restoreClock() {
        CountdownWidgetProvider.now = defaultNow
    }

    @Test
    fun setup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences(com.pixel.intelligentsearch.core.data.SettingsManager.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(com.pixel.intelligentsearch.core.data.SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light").commit()
        CountdownWidgetProvider.now = { java.time.ZonedDateTime.parse("2026-10-22T09:00-04:00[America/New_York]") }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    CountdownSetupScreen(
                        initial = CountdownEvent("Halloween", LocalDate.of(2026, 10, 31), yearly = true),
                        today = LocalDate.of(2026, 10, 22),
                        // The widget's own views, as the screen shows them.
                        previewViews = { draft -> CountdownWidgetProvider.buildSizes(context, 1, draft, prefs) },
                        onSave = {},
                        onBack = {}
                    )
                }
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/widget/countdown_setup_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
