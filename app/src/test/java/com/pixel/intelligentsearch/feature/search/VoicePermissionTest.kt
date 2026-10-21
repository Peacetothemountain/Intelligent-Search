package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the microphone request comes back refused, App Info opens only if Android won't ask again. A first request
 * the user just dismissed must ask again next time instead.
 */
class VoicePermissionTest {

    @Test
    fun aFirstRequestDismissedWithoutAnAnswerIsNotPermanent() {
        assertFalse(isPermanentDenial(askedBefore = false, rationaleBefore = false, rationaleAfter = false))
    }

    @Test
    fun aFirstDenialIsNotPermanent() {
        assertFalse(isPermanentDenial(askedBefore = false, rationaleBefore = false, rationaleAfter = true))
    }

    @Test
    fun aSecondDenialIsPermanent() {
        assertTrue(isPermanentDenial(askedBefore = true, rationaleBefore = true, rationaleAfter = false))
    }

    @Test
    fun aRequestAndroidNoLongerShowsIsPermanent() {
        // Denied for good earlier: Android answers at once, with no rationale before or after.
        assertTrue(isPermanentDenial(askedBefore = true, rationaleBefore = false, rationaleAfter = false))
    }
}
