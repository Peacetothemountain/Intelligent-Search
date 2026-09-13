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
}
