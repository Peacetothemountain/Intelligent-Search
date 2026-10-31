package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The tutorial's last two steps show over Settings with the page hidden behind the tutorial's backdrop, so they
 * mustn't point at rows "below" that the user can't see; the last one says what its Got it button opens.
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
    fun theLastStepNamesItsOwnButton() {
        // The button the last step shows (TutorialSpotlightOverlay), as the step names it.
        val overlay = File("src/main/java/com/pixel/intelligentsearch/feature/settings/TutorialSpotlightOverlay.kt").readText()
        assertTrue(overlay.contains("if (isFinalStep) \"Got it\""))
        assertTrue(steps.getValue(4).text.contains("Got it"))
    }

    @Test
    fun titlesUseTitleCase() {
        assertEquals("Make It Yours", steps.getValue(4).title)
        assertEquals("Settings & Features", steps.getValue(3).title)
    }
}
