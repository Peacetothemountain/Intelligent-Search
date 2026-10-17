package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug menu's stand-in data must be what its rows promise, and safe to show and tap. */
class DebugMocksTest {

    @Test
    fun mockLargeDatasetHasFiftyContactsAndFiftyFiles() {
        assertEquals(50, DebugMocks.contacts.size)
        assertEquals(50, DebugMocks.files.size)
    }

    @Test
    fun mockContactsUseOnlyNumbersReservedForFiction() {
        // 555-0100 to 555-0199 are never assigned, so a tap can't dial a real person.
        DebugMocks.contacts.forEach { assertTrue(it.phoneNumber, it.phoneNumber.matches(Regex("\\+1 555-01\\d\\d"))) }
    }

    @Test
    fun everyMockRowHasItsOwnListKey() {
        // The overlay keys contacts by name and number when there is no lookup URI, and files by URI.
        assertEquals(50, DebugMocks.contacts.map { "${it.name}_${it.phoneNumber}" }.toSet().size)
        assertEquals(50, DebugMocks.files.map { it.uri }.toSet().size)
        assertEquals(DebugMocks.trendingQueries.size, DebugMocks.trendingQueries.toSet().size)
    }
}
