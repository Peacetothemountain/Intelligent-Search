package com.pixel.intelligentsearch.core.bangs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's "Available Direct Shortcuts" list: built-ins the user removed, under whatever trigger they had then. */
class AvailableBuiltInsTest {

    @Test
    fun aBuiltInRemovedUnderAnOlderTriggerCanBeRestored() {
        val available = SearchBangManager.availableBuiltIns(setOf("@yt"))
        assertEquals(listOf("YouTube"), available.map { it.name })
    }

    @Test
    fun builtInsRemovedUnderTheDefaultTriggerAreListed() {
        val available = SearchBangManager.availableBuiltIns(setOf("!yt", "!w"))
        assertEquals(listOf("YouTube", "Wikipedia"), available.map { it.name })
    }

    @Test
    fun nothingRemovedListsNothing() {
        assertTrue(SearchBangManager.availableBuiltIns(emptySet()).isEmpty())
    }
}
