package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overlay's scrim over the wallpaper: Background Transparency is how see-through the background is (0 dims it the
 * most, 100 not at all), and where the system can't blur behind windows, Background Blur dims it a little more instead.
 */
class OverlayBackdropTest {

    @Test
    fun transparencyZeroDimsTheMostAndAHundredNotAtAll() {
        for (blur in listOf(0, 50, 100)) {
            assertEquals(MAX_OVERLAY_SCRIM, overlayScrimAlpha(transparency = 0, blur = blur, blurAvailable = true), 1e-6f)
            assertEquals(0f, overlayScrimAlpha(transparency = 100, blur = blur, blurAvailable = true), 1e-6f)
        }
        assertEquals(0.7f, MAX_OVERLAY_SCRIM, 0f)
    }

    /** The default 50 dims as much as it always has. */
    @Test
    fun theDefaultDimsAsBefore() {
        assertEquals(0.35f, overlayScrimAlpha(transparency = 50, blur = 50, blurAvailable = true), 1e-6f)
    }

    @Test
    fun moreTransparencyIsNeverMoreDim() {
        for (blurAvailable in listOf(true, false)) {
            for (t in 1..100) {
                assertTrue(
                    "transparency $t blurAvailable=$blurAvailable",
                    overlayScrimAlpha(t, 50, blurAvailable) < overlayScrimAlpha(t - 1, 50, blurAvailable)
                )
            }
        }
    }

    /** With real blur behind the window, the Blur setting doesn't touch the dim. */
    @Test
    fun blurDoesNotDimWhereTheSystemBlurs() {
        for (t in listOf(0, 30, 50, 100)) {
            val base = overlayScrimAlpha(t, 0, blurAvailable = true)
            for (blur in listOf(25, 50, 100)) assertEquals(base, overlayScrimAlpha(t, blur, blurAvailable = true), 0f)
        }
    }

    /** Without blur (Battery Saver, or a phone without it), Blur dims a little more instead, and grows with the setting. */
    @Test
    fun blurFallbackDimsOnlyWhereBlurIsUnavailable() {
        for (t in listOf(0, 30, 50, 90)) {
            // Blur 0 adds nothing.
            assertEquals(overlayScrimAlpha(t, 0, true), overlayScrimAlpha(t, 0, false), 1e-6f)
            for (blur in 1..100) {
                assertTrue("t=$t blur=$blur", overlayScrimAlpha(t, blur, false) > overlayScrimAlpha(t, blur - 1, false))
                assertTrue("t=$t blur=$blur", overlayScrimAlpha(t, blur, false) > overlayScrimAlpha(t, blur, true))
            }
        }
        // At Blur 100 it dims away BLUR_FALLBACK_DIM of what the scrim left of the wallpaper.
        assertEquals(0.35f + 0.65f * BLUR_FALLBACK_DIM, overlayScrimAlpha(50, 100, false), 1e-6f)
        assertEquals(BLUR_FALLBACK_DIM, overlayScrimAlpha(100, 100, false), 1e-6f)
    }

    @Test
    fun theScrimStaysAnAlpha() {
        for (t in listOf(-50, 0, 50, 100, 250)) {
            for (blur in listOf(-50, 0, 50, 100, 250)) {
                for (blurAvailable in listOf(true, false)) {
                    val alpha = overlayScrimAlpha(t, blur, blurAvailable)
                    assertTrue("t=$t blur=$blur blurAvailable=$blurAvailable: $alpha", alpha in 0f..1f)
                }
            }
        }
        // Out-of-range settings read as the nearest end of their slider.
        assertEquals(overlayScrimAlpha(0, 100, false), overlayScrimAlpha(-50, 250, false), 0f)
        assertEquals(overlayScrimAlpha(100, 0, false), overlayScrimAlpha(250, -50, false), 0f)
    }
}
