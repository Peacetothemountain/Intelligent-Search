package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TutorialMascotDecodeTest {

    @Test
    fun theLayersDecodeAtHalfSizeWithoutDensityScaling() {
        val options = mascotDecodeOptions()
        assertEquals(2, options.inSampleSize)
        assertFalse(options.inScaled)
    }
}
