package com.pixel.intelligentsearch.feature.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Best Match completion drawn behind the search field. Its hidden part sits under the typed letters, so it must be
 * those letters exactly; the name's own capitals are wider or narrower and pushed the visible rest out of line.
 */
class GhostTextTest {

    private fun AnnotatedString.part(hidden: Boolean): String {
        val span = spanStyles.single { (it.item.color == Color.Transparent) == hidden }
        return text.substring(span.start, span.end)
    }

    @Test
    fun theHiddenPartIsWhatWasTyped() {
        val ghost = ghostText("gm", "Gmail")!!
        assertEquals("gm", ghost.part(hidden = true))
        assertEquals("ail", ghost.part(hidden = false))
    }

    @Test
    fun matchingCaseIsDrawnAsBefore() {
        val ghost = ghostText("Rob", "Robin Example")!!
        assertEquals("Rob", ghost.part(hidden = true))
        assertEquals("in Example", ghost.part(hidden = false))
    }

    @Test
    fun noGhostWhenTheNameDoesNotGoOnFromTheTypedText() {
        assertNull(ghostText("gx", "Gmail"))
        assertNull(ghostText("gmail", "Gmail"))
        assertNull(ghostText("gm", null))
    }
}
