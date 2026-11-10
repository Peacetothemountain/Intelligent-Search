package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The tutorial's last two steps show over Settings with the page hidden behind the tutorial's backdrop, so they
 * mustn't point at rows "below" that the user can't see. The last one names its button from the button's own label
 * (`TUTORIAL_FINISH_LABEL`), so the two can't drift apart.
 */
class SettingsTutorialStepsTest {

    private val steps = SETTINGS_TUTORIAL_STEPS

    @Test
    fun theStepsOverSettingsPointAtNothingHidden() {
        assertEquals(setOf(3, 4), steps.keys)
        for ((_, step) in steps) {
            assertFalse(step.title, Regex("""\b(below|above|explore)\b""", RegexOption.IGNORE_CASE).containsMatchIn(step.text))
        }
    }

    @Test
    fun titlesUseTitleCase() {
        assertEquals("Make It Yours", steps.getValue(4).title)
        assertEquals("Settings & Features", steps.getValue(3).title)
    }
}
