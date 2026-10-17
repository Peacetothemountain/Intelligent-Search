package com.ngdesigns.intelligentsearch.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeTest {
    @Test fun lowercasesAndStripsAccents() = assertEquals("cafe muller", normalize("Café Müller"))

    @Test fun punctuationBecomesSingleSpaces() = assertEquals("keep notes", normalize("  Keep -- Notes!! "))

    @Test fun apostrophesJoinWords() = assertEquals("dominos pizza", normalize("Domino’s Pizza"))

    @Test fun compatibilityFormsFold() = assertEquals("ffi 2", normalize("ﬃ ²"))

    @Test fun emptyStaysEmpty() = assertEquals("", normalize(""))

    @Test fun wordsSplitsNormalizedText() = assertEquals(listOf("google", "maps"), words(normalize("Google Maps")))
}
