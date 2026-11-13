package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tutorial's last two steps show over Settings with the page hidden behind the tutorial's backdrop, so they
 * mustn't point at rows "below" that the user can't see. Each plays the part of the app it names. The last one names
 * its button from the button's own label (`TUTORIAL_FINISH_LABEL`), so the two can't drift apart.
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
        assertEquals("Just Type It", steps.getValue(4).title)
        assertEquals("Discover", steps.getValue(3).title)
    }

    @Test
    fun eachStepPlaysThePartOfTheAppItNames() {
        assertEquals(TutorialDemo.Discover, steps.getValue(3).demo)
        assertEquals(TutorialDemo.Answers, steps.getValue(4).demo)
    }

    /** The words the last step quotes are words its demo types, so what is read is what is seen. */
    @Test
    fun theLastStepQuotesAnExampleItsDemoTypes() {
        val quoted = Regex("\"([^\"]+)\"").find(steps.getValue(4).text)!!.groupValues[1]
        assertTrue(quoted, quoted in ANSWER_EXAMPLES)
    }

    @Test
    fun theDemoTypesEachExampleOutAndClearsItBeforeTheNext() {
        ANSWER_EXAMPLES.forEachIndexed { index, text ->
            val start = index * ANSWER_EACH
            assertEquals("", typedAt(start))
            assertEquals(text, typedAt(start + ANSWER_EACH * 0.6f))
            assertEquals("", typedAt(start + ANSWER_EACH - 0.05f))
        }
    }
}
