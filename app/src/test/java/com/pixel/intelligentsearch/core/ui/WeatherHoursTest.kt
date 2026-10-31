package com.pixel.intelligentsearch.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.InstantAnswerSamples
import com.pixel.intelligentsearch.screenshots.confinedEffects
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

/** The forecast's hours: in one row when they fit, and wrapped into even rows rather than cut off when they don't. */
@HiltAndroidTest
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w240dp-h2000dp-xhdpi")
class WeatherHoursTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    private val isHour = SemanticsMatcher("an hour of the forecast") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.matches(Regex("\\d{1,2} [AP]M, .*")) == true
    }

    private fun showWeather(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IntelligentSearchTheme(darkTheme = false) {
                    WeatherOneBox(report = InstantAnswerSamples.weather, is24Hour = false, onOpenWeather = {})
                }
            }
        }
        compose.settle()
    }

    private fun hourBounds() = compose.onAllNodes(isHour).fetchSemanticsNodes().map { it.boundsInRoot }

    @Test
    fun atLargeTextEveryHourShowsWholeInEvenRows() {
        showWeather(fontScale = 2f)
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val hours = hourBounds()
        assertEquals(InstantAnswerSamples.weather.hours.size, hours.size)
        for (hour in hours) assertTrue("$hour inside 0..$width", hour.left >= 0f && hour.right <= width)
        val rows = hours.groupBy { it.top }.values.map { it.size }
        assertTrue("rows $rows", rows.size > 1 && rows.toSet().size == 1)
    }

    @Config(qualifiers = "w360dp-h2000dp-xhdpi")
    @Test
    fun atNormalTextTheHoursShareOneRow() {
        showWeather(fontScale = 1f)
        assertEquals(1, hourBounds().map { it.top }.toSet().size)
    }
}
