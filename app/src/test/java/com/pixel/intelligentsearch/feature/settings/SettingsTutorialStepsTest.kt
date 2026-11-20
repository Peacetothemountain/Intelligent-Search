package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tutorial's last two steps show over Settings' own tabs, Gallery and Wellbeing, with the part each is about in a
 * spotlight; Just Type It and Discover play on the search screen, over the real search bar. The last step names its
 * button from the button's own label (`TUTORIAL_FINISH_LABEL`), so the two can't drift apart.
 */
class SettingsTutorialStepsTest {

    private val steps = SETTINGS_TUTORIAL_STEPS
    private val overlaySteps = com.pixel.intelligentsearch.feature.search.OverlayTutorialSteps

    @Test
    fun theStepsOverSettingsPointAtNothingHidden() {
        assertEquals(setOf(TutorialManager.GALLERY_STEP, TutorialManager.WELLBEING_STEP), steps.keys)
        for ((_, step) in steps) {
            assertFalse(step.title, Regex("""(below|above|explore)""", RegexOption.IGNORE_CASE).containsMatchIn(step.text))
        }
    }

    @Test
    fun titlesUseTitleCase() {
        assertEquals("Just Type It", overlaySteps.getValue(2).title)
        assertEquals("Discover", overlaySteps.getValue(3).title)
        assertEquals("Gallery", steps.getValue(TutorialManager.GALLERY_STEP).title)
        assertEquals("Wellbeing", steps.getValue(TutorialManager.WELLBEING_STEP).title)
    }

    @Test
    fun eachStepPlaysThePartOfTheAppItNames() {
        assertEquals(TutorialDemo.Discover, overlaySteps.getValue(3).demo)
        assertEquals(TutorialDemo.Answers, overlaySteps.getValue(2).demo)
    }

    /** The words Just Type It quotes are words its demo types, so what is read is what is seen. */
    @Test
    fun justTypeItQuotesAnExampleItsDemoTypes() {
        val quoted = Regex("\"([^\"]+)\"").find(overlaySteps.getValue(2).text)!!.groupValues[1]
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
