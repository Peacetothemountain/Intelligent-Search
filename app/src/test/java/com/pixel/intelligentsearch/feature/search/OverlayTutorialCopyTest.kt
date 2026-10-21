package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertFalse
import org.junit.Test

/** The overlay tutorial's words name the buttons it shows: Next on every step here (Got it only ends the tutorial). */
class OverlayTutorialCopyTest {

    @Test
    fun noStepAsksForAnOkButton() {
        for ((step, info) in OverlayTutorialSteps) {
            assertFalse("step $step says \"${info.text}\"", info.text.contains("Press OK"))
        }
    }
}
