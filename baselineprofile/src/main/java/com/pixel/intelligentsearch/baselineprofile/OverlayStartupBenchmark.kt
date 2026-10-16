package com.pixel.intelligentsearch.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long the overlay takes to open from cold, and how smooth the first queries are, with no ahead-of-time
 * compilation versus with the Baseline Profile. The difference is what Play Store users gain.
 */
@RunWith(AndroidJUnit4::class)
class OverlayStartupBenchmark {

    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun withoutProfile() = measure(CompilationMode.None())

    @Test fun withBaselineProfile() = measure(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measure(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() }
    ) {
        openOverlay()
        typeAQuery()
    }
}
