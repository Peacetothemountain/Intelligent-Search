package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
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

/** The battery and memory page reads the phone only while it is on screen, and its pressure chip is legible. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class DebugDiagnosticsPageTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    @Test
    fun stopsReadingWhileThePageIsInTheBackground() {
        val owner = TestOwner()
        var samples = 0
        val sampler = DiagnosticsSampler { samples++; SAMPLE }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                IntelligentSearchTheme(darkTheme = false) {
                    BatteryAndMemoryDiagnosticsPage(ApplicationProvider.getApplicationContext(), newSampler = { sampler })
                }
            }
        }
        compose.settle(steps = 5)
        assertTrue("reads while visible", samples > 0)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        val whenHidden = samples
        compose.settle(steps = 100)
        assertEquals(whenHidden, samples)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.settle(steps = 5)
        assertTrue("reads again once visible", samples > whenHidden)
    }

    @Test
    fun everyPressureChipIsLegibleInBothThemes() {
        for (scheme in listOf(lightColorScheme(), darkColorScheme())) {
            for (pressure in listOf("Critical", "High Load", "Moderate", "Normal")) {
                val (container, content) = pressureChipColors(pressure, scheme)
                val chip = container.compositeOver(scheme.surfaceContainerLow)
                assertTrue("$pressure on ${scheme.surface}", contrast(content, chip) >= 4.5)
            }
        }
    }

    private fun contrast(foreground: Color, background: Color): Double =
        MaterialYouPaletteHelper.calculateContrastRatio(foreground.compositeOver(background).toArgb(), background.toArgb())

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        val SAMPLE = DiagnosticsSample(
            battery = BatteryTelemetrySnapshot(
                level = 80, temp = 30f, volt = 4f, watts = null, health = "Good",
                healthSeverity = DiagnosticsReadings.HealthSeverity.Ok, cycles = -1, technology = "Li-ion",
                thermalStatus = "Normal", isCharging = false, chargingRateStr = "Discharging", timeEstimateStr = "Estimating..."
            ),
            memory = MemorySnapshot(
                totalGb = 8.0, usedGb = 4.0, availGb = 4.0, pct = 50, zRamUsedGb = 0.0, zRamTotalGb = 0.0, cachedGb = 1.0,
                isLowMem = false, thresholdMb = 0, memPressure = "Normal", processes = emptyList(),
                breakdownAvailable = false, physicalGb = 8
            )
        )
    }
}
