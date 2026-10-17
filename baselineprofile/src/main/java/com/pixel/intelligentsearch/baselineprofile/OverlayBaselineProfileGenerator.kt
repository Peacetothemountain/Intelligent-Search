package com.pixel.intelligentsearch.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code the overlay runs while opening and answering the first queries, so Android compiles it ahead
 * of time when the app is installed from the Play Store instead of interpreting it on every cold start.
 */
@RunWith(AndroidJUnit4::class)
class OverlayBaselineProfileGenerator {

    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        openOverlay()
        typeAQuery()
    }

    /** Settings navigation: the first layout of each page runs as its transition starts, the slowest frames measured. */
    @Test
    fun settings() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = false) {
        pressHome()
        browseSettings()
    }
}
