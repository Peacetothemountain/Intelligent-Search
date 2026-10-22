package com.pixel.intelligentsearch.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.answers.DictionaryResponseParser
import com.pixel.intelligentsearch.core.answers.ForecastDay
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.answers.OpenMeteoParser
import com.pixel.intelligentsearch.core.answers.ScreenTimeApp
import com.pixel.intelligentsearch.core.answers.ScreenTimeReport
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.ui.DefinitionOneBox
import com.pixel.intelligentsearch.core.ui.ScreenTimeOneBox
import com.pixel.intelligentsearch.core.ui.TranslationOneBox
import com.pixel.intelligentsearch.core.ui.WeatherLocationOneBox
import com.pixel.intelligentsearch.core.ui.WeatherOneBox
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

/** The instant-answer cards with the data they're shown with, from saved service responses; nothing is fetched. */
internal object InstantAnswerSamples {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("answers/$name")) { "missing fixture $name" }.readText()

    val definition by lazy { requireNotNull(DictionaryResponseParser.parse(fixture("dictionary_hello.json"))) }

    val weather by lazy {
        val data = requireNotNull(OpenMeteoParser.parseForecast(fixture("openmeteo_forecast_johnson_city.json")))
        requireNotNull(WeatherReport.from(data, "Johnson City, Tennessee", TemperatureUnit.FAHRENHEIT, ForecastDay.TODAY))
    }

    val translation = LiveAnswer.Translation("translate good morning to spanish", "good morning", source = null, target = "es")

    val translationFromNamedSource = LiveAnswer.Translation("translate gato from spanish to english", "gato", source = "es", target = "en")

    // Made-up packages, so each app shows its initial rather than whatever this machine has installed.
    val screenTime = ScreenTimeReport(
        totalMs = (3 * 60 + 12) * 60_000L,
        topApps = listOf(
            ScreenTimeApp("com.example.chat", "Messages", 64 * 60_000L),
            ScreenTimeApp("com.example.video", "Videos", 51 * 60_000L),
            ScreenTimeApp("com.example.maps", "Maps", 33 * 60_000L),
            ScreenTimeApp("com.example.browser", "Browser", 21 * 60_000L),
            ScreenTimeApp("com.example.photos", "Photos", 9 * 60_000L)
        )
    )

    /** Every card in [kind]'s states, one under the other. */
    @Composable
    fun Cards(kind: String) {
        when (kind) {
            "definition" -> DefinitionOneBox(entry = definition, onOpenFullDefinition = {})
            "weather" -> {
                WeatherOneBox(report = weather, is24Hour = false, onOpenWeather = {})
                WeatherLocationOneBox(onUseApproximateLocation = {}, onSetHomeLocation = {})
            }
            "translation" -> {
                TranslationOneBox(answer = translation, onOpenGoogleTranslate = {})
                TranslationOneBox(answer = translationFromNamedSource, onOpenGoogleTranslate = {})
            }
            "screen_time" -> {
                ScreenTimeOneBox(report = screenTime, onAllowUsageAccess = {}, onOpenDetails = {})
                ScreenTimeOneBox(report = null, onAllowUsageAccess = {}, onOpenDetails = null)
            }
        }
    }
}

/**
 * Golden images of the overlay's live instant-answer cards, light and dark: a definition, a forecast (and the card
 * asking where "here" is), translations to hand to Google Translate (into a language, and from one named) and screen
 * time (and the card asking for Usage Access). See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h1000dp-xhdpi")
class InstantAnswerGoldenTest(private val kind: String, private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    @Test
    fun card() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                    InstantAnswerSamples.Cards(kind)
                }
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/overlay/answer_${kind}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> = listOf("definition", "weather", "translation", "screen_time")
            .flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
}

/**
 * The same cards on a 240dp-wide screen at twice the text size, the narrowest and largest the overlay supports: chips
 * flow onto new lines, the hours scroll, and no text is cut off.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w240dp-h4400dp-xhdpi")
class InstantAnswerLargeTextGoldenTest(private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    @Test
    fun cards() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                IntelligentSearchTheme(darkTheme = dark) {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                        DefinitionOneBox(entry = InstantAnswerSamples.definition, onOpenFullDefinition = {})
                        WeatherOneBox(report = InstantAnswerSamples.weather, is24Hour = false, onOpenWeather = {})
                        TranslationOneBox(answer = InstantAnswerSamples.translation, onOpenGoogleTranslate = {})
                        ScreenTimeOneBox(report = InstantAnswerSamples.screenTime, onAllowUsageAccess = {}, onOpenDetails = {})
                    }
                }
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/overlay/answers_large_text_narrow_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
