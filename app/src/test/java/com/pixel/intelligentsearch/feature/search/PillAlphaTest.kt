package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Search Pill Opacity setting maps straight onto the pill's alpha: more opacity is never more transparent. */
class PillAlphaTest {

    @Test
    fun alphaIsTheSettingAsAFraction() {
        assertEquals(0f, pillAlpha(0), 0f)
        assertEquals(0.4f, pillAlpha(40), 1e-6f)
        assertEquals(0.5f, pillAlpha(50), 1e-6f)
        assertEquals(0.6f, pillAlpha(60), 1e-6f)
        assertEquals(1f, pillAlpha(100), 0f)
    }

    @Test
    fun alphaRisesWithEveryStepOfTheSlider() {
        for (opacity in 1..100) {
            assertTrue("$opacity% should be more opaque than ${opacity - 1}%", pillAlpha(opacity) > pillAlpha(opacity - 1))
        }
    }

    @Test
    fun valuesOutsideTheSliderAreClamped() {
        assertEquals(0f, pillAlpha(-20), 0f)
        assertEquals(1f, pillAlpha(250), 0f)
    }
}
