package com.ngdesigns.intelligentsearch.engine.text

import com.ngdesigns.intelligentsearch.engine.nameWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TextTest {
    @Test fun distanceCountsTranspositionsAsOneEdit() {
        assertEquals(1, EditDistance.between("teh", "the", 3))
        assertEquals(3, EditDistance.between("kitten", "sitting", 5))
        assertEquals(3, EditDistance.between("abcdef", "uvwxyz", 2)) // capped at limit + 1
    }

    @Test fun prefixDistanceMeasuresAgainstTheStartOfAName() {
        assertEquals(0, EditDistance.toPrefix("you", "youtube", 2))
        assertEquals(1, EditDistance.toPrefix("yuotu", "youtube", 2))
        assertEquals(1, EditDistance.toPrefix("calcul8", "calculator", 2))
        assertEquals(3, EditDistance.toPrefix("qqqq", "youtube", 2))
    }

    @Test fun soundAlikeNamesShareKeys() {
        for ((a, b) in listOf("steven" to "stephen", "catherine" to "kathryn", "jon" to "john", "philip" to "filip", "shawn" to "shaun")) {
            assertEquals("$a ~ $b", Soundalike.key(a), Soundalike.key(b))
        }
        assertNotEquals(Soundalike.key("mark"), Soundalike.key("mary"))
    }

    @Test fun nameWordsSplitCamelCase() {
        assertEquals(listOf("you", "tube"), nameWords("YouTube"))
        assertEquals(listOf("whats", "app"), nameWords("WhatsApp"))
        assertEquals(listOf("bbc", "news"), nameWords("BBC News"))
        assertEquals(listOf("cafe", "muller"), nameWords("Café Müller"))
    }
}
