package com.pixel.intelligentsearch.core.search

import org.junit.Assert.*
import org.junit.Test

class DoubleMetaphoneTest {

    @Test
    fun testStephenAndSteven() {
        val stephen = DoubleMetaphone.encode("Stephen")
        val steven = DoubleMetaphone.encode("Steven")
        assertTrue("Stephen ($stephen) should match Steven ($steven)", stephen.matches(steven))
    }

    @Test
    fun testSmithAndSmyth() {
        val smith = DoubleMetaphone.encode("Smith")
        val smyth = DoubleMetaphone.encode("Smyth")
        assertTrue("Smith ($smith) should match Smyth ($smyth)", smith.matches(smyth))
    }

    @Test
    fun testCatherineAndKatherine() {
        val catherine = DoubleMetaphone.encode("Catherine")
        val katherine = DoubleMetaphone.encode("Katherine")
        assertTrue("Catherine ($catherine) should match Katherine ($katherine)", catherine.matches(katherine))
    }

    @Test
    fun testSilentLetters() {
        val knight = DoubleMetaphone.encode("Knight")
        val night = DoubleMetaphone.encode("Night")
        assertTrue("Knight ($knight) should match Night ($night)", knight.matches(night))
    }

    @Test
    fun testMatthew() {
        val matthew = DoubleMetaphone.encode("Matthew")
        val mathew = DoubleMetaphone.encode("Mathew")
        assertEquals("M0", matthew.primary)
        assertEquals("MTF", matthew.alternate)
        assertTrue("Matthew ($matthew) should match Mathew ($mathew)", matthew.matches(mathew))
    }

    @Test
    fun testDiacriticsNormalization() {
        val joseAccent = DoubleMetaphone.encode("José")
        val josePlain = DoubleMetaphone.encode("Jose")
        assertEquals(josePlain, joseAccent)

        val francoisAccent = DoubleMetaphone.encode("François")
        val francoisPlain = DoubleMetaphone.encode("Francois")
        assertEquals(francoisPlain, francoisAccent)
    }

    @Test
    fun testEmptyAndSpecialInputZeroOutOfBounds() {
        assertEquals(DoubleMetaphone.MetaphoneResult("", ""), DoubleMetaphone.encode(null))
        assertEquals(DoubleMetaphone.MetaphoneResult("", ""), DoubleMetaphone.encode(""))
        assertEquals(DoubleMetaphone.MetaphoneResult("", ""), DoubleMetaphone.encode("   "))

        // Single character boundary checks (must never throw IndexOutOfBoundsException)
        assertNotNull(DoubleMetaphone.encode("X"))
        assertNotNull(DoubleMetaphone.encode("W"))
        assertNotNull(DoubleMetaphone.encode("C"))
        assertNotNull(DoubleMetaphone.encode("123"))
    }

    @Test
    fun testGermanEszettAndLigatures() {
        val strasseEszett = DoubleMetaphone.encode("Straße")
        val strasseSs = DoubleMetaphone.encode("Strasse")
        assertTrue("Straße ($strasseEszett) must match Strasse ($strasseSs)", strasseEszett.matches(strasseSs))
    }
}
