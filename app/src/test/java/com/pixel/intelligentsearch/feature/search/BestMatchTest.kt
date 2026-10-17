package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BestMatchTest {

    private data class Item(val kind: String, val name: String)

    private fun domains(
        apps: List<String>, contacts: List<String>, files: List<String>,
        weights: Triple<Int, Int, Int> = Triple(50, 50, 50),
        enabled: Triple<Boolean, Boolean, Boolean> = Triple(true, true, true)
    ) = listOf(
        BestMatch.Domain(weights.first, enabled.first, apps.map { Item("app", it) }) { it.name },
        BestMatch.Domain(weights.second, enabled.second, contacts.map { Item("contact", it) }) { it.name },
        BestMatch.Domain(weights.third, enabled.third, files.map { Item("file", it) }) { it.name }
    )

    @Test
    fun blankQueryHasNoMatch() {
        assertNull(BestMatch.find("  ", domains(listOf("Maps"), emptyList(), emptyList())))
    }

    @Test
    fun aWholeNameBeatsAPrefixInAHeavierDomain() {
        val d = domains(listOf("Mapsforge"), listOf("maps"), emptyList(), weights = Triple(90, 10, 10))
        assertEquals(Item("contact", "maps"), BestMatch.find("Maps", d))
    }

    @Test
    fun withinATierTheHeaviestDomainWins() {
        val d = domains(listOf("Notes app"), listOf("Notes Person"), listOf("Notes.txt"), weights = Triple(10, 80, 50))
        assertEquals(Item("contact", "Notes Person"), BestMatch.find("note", d))
    }

    @Test
    fun tiesKeepAppsContactsFilesOrder() {
        val d = domains(listOf("Camera"), listOf("Cameron"), listOf("Cam.jpg"))
        assertEquals(Item("app", "Camera"), BestMatch.find("cam", d))
    }

    @Test
    fun aWordInsideTheNameMatchesLast() {
        val d = domains(listOf("Keep Notes"), listOf("Notary Public"), emptyList())
        // "Notary Public" starts with "no"; "Keep Notes" only has a later word that does.
        assertEquals(Item("contact", "Notary Public"), BestMatch.find("no", d))
        assertEquals(Item("app", "Keep Notes"), BestMatch.find("notes", d))
    }

    @Test
    fun disabledDomainsAreSkipped() {
        val d = domains(listOf("Maps"), listOf("Maps"), emptyList(), enabled = Triple(false, true, true))
        assertEquals(Item("contact", "Maps"), BestMatch.find("maps", d))
    }
}
