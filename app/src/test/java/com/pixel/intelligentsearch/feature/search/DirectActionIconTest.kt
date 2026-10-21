package com.pixel.intelligentsearch.feature.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The icon on each suggested action at the top of the results. */
class DirectActionIconTest {

    /** Every iconType [SearchViewModel] gives a [DirectAction]. */
    private val emitted = listOf("link", "phone", "search", "calendar", "message", "timer", "alarm", "navigation", "private_space")

    @Test
    fun noEmittedActionFallsBackToTheClipboardIcon() {
        for (type in emitted) {
            assertNotEquals(type, Icons.Default.ContentPaste, directActionIcon(type))
        }
        assertEquals(Icons.Default.ContentPaste, directActionIcon("unknown"))
    }

    @Test
    fun actionsShowWhatTheyDo() {
        assertEquals(Icons.Default.Timer, directActionIcon("timer"))
        assertEquals(Icons.Default.Alarm, directActionIcon("alarm"))
        assertEquals(Icons.Default.Directions, directActionIcon("navigation"))
        assertEquals(Icons.Default.Lock, directActionIcon("private_space"))
    }
}
