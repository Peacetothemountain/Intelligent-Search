package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class QueryNormalizerTest {

    @Test
    fun canonicalInputIsReturnedUntouched() {
        val input = "google play music 2"
        assertSame(input, QueryNormalizer.normalize(input))
    }

    @Test
    fun nonCanonicalAsciiIsStillNormalized() {
        assertEquals("google chrome", QueryNormalizer.normalize("  Google   Chrome "))
        assertEquals("google chrome", QueryNormalizer.normalize("Google-Chrome"))
        assertEquals("abc", QueryNormalizer.normalize("abc "))
        assertEquals("abc", QueryNormalizer.normalize(" abc"))
        assertEquals("a b", QueryNormalizer.normalize("a  b"))
        assertEquals("a b", QueryNormalizer.normalize("a\tb"))
    }

    @Test
    fun diacriticsAndLigaturesAreFolded() {
        assertEquals("cafe", QueryNormalizer.normalize("Café"))
        assertEquals("muller", QueryNormalizer.normalize("Müller"))
        assertEquals("strasse", QueryNormalizer.normalize("Straße"))
    }

    @Test
    fun blankAndNullYieldEmpty() {
        assertEquals("", QueryNormalizer.normalize(null))
        assertEquals("", QueryNormalizer.normalize(""))
        assertEquals("", QueryNormalizer.normalize("   "))
        assertEquals("", QueryNormalizer.normalize("!!!"))
    }

    @Test
    fun tokenizeAndInitials() {
        assertEquals(listOf("google", "play", "music"), QueryNormalizer.tokenize("Google Play-Music"))
        assertEquals("gpm", QueryNormalizer.extractInitials("Google Play Music"))
        assertEquals("yt", QueryNormalizer.extractInitials("YouTube"))
    }
}
