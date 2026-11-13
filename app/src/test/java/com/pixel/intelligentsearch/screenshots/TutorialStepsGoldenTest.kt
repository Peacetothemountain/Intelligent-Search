package com.pixel.intelligentsearch.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.GlanceClock
import com.pixel.intelligentsearch.feature.search.LocalGlanceClock
import com.pixel.intelligentsearch.feature.settings.SETTINGS_TUTORIAL_STEPS
import com.pixel.intelligentsearch.feature.settings.TutorialSpotlightOverlay
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * Golden images of the tutorial's last two steps, the ones shown over Settings: Discover, with its switch off and
 * on, and Just Type It, in both themes. Animations are off, so each demo rests on its still moment (the Discover
 * page open; the weather typed and answered), at a fixed time of day. See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class TutorialStepsGoldenTest(private val name: String, private val step: Int, private val discoverOn: Boolean, private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val zone = TimeZone.getDefault()

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun tearDown() = TimeZone.setDefault(zone)

    @Test
    fun step() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(SettingsManager.TUTORIAL_COMPLETED.name, false)
            .putInt(SettingsManager.TUTORIAL_STEP.name, step)
            .putBoolean(SettingsManager.TODAY_BUTTON.name, discoverOn)
            .commit()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalGlanceClock provides GlanceClock({ NOW }, ticks = false)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        TutorialSpotlightOverlay(prefs = prefs, stepsInfo = SETTINGS_TUTORIAL_STEPS, onComplete = {}, searchBarBehind = false)
                    }
                }
            }
        }
        compose.settle(steps = 10)
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/settings/tutorial_${name}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        /** 2:30 PM UTC on January 15, 2027: the sun well along its arc, and "Good afternoon". */
        private const val NOW = 1_800_023_400_000L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={3}")
        fun parameters(): List<Array<Any>> = listOf(
            Triple("discover", 3, false),
            Triple("discover_on", 3, true),
            Triple("answers", 4, false)
        ).flatMap { (name, step, on) -> listOf(arrayOf<Any>(name, step, on, false), arrayOf<Any>(name, step, on, true)) }
    }
}
