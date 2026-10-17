package com.ngdesigns.intelligentsearch.engine

import com.ngdesigns.intelligentsearch.engine.actions.Action
import com.ngdesigns.intelligentsearch.engine.toggles.Toggle
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private fun c(kind: Kind, title: String) = Candidate(id = "$kind:$title", kind = kind, title = title)
    private val catalogs = mapOf(
        Kind.App to Catalog(listOf(c(Kind.App, "Calendar"), c(Kind.App, "Calculator"), c(Kind.App, "Keep Notes"))),
        Kind.Contact to Catalog(listOf(c(Kind.Contact, "Callum Price"), c(Kind.Contact, "Ada Lovelace"))),
        Kind.History to Catalog(listOf(c(Kind.History, "calendar 2027"))),
    )
    private val now = LocalDateTime.of(2026, 3, 10, 12, 0)
    private fun run(q: String, prefs: SearchPreferences = SearchPreferences()) = Search.run(q, catalogs, prefs, now, 0L)

    @Test fun bestMatchAndGhostCompletion() {
        val r = run("cal")
        assertEquals("Calendar", r.best?.candidate?.title)
        assertEquals("endar", r.completion)
        assertEquals("Ada Lovelace", run("ada").best?.candidate?.title)
        assertEquals(" Lovelace", run("Ada").completion)
    }

    @Test fun historyIsNeverTheBestMatch() {
        val r = run("calendar 2")
        assertEquals(Kind.History, r.sections.first().kind)
        assertNull(r.best)
    }

    @Test fun prioritiesReorderSectionsAndLimitsCut() {
        assertEquals(Kind.App, run("ca").sections.first().kind)
        val peopleFirst = run("ca", SearchPreferences(priority = mapOf(Kind.Contact to 2, Kind.App to -2)))
        assertEquals(Kind.Contact, peopleFirst.sections.first().kind)
        val oneApp = run("ca", SearchPreferences(limits = mapOf(Kind.App to 1, Kind.Contact to 0)))
        assertEquals(1, oneApp.sections.single { it.kind == Kind.App }.items.size)
        assertTrue(oneApp.sections.none { it.kind == Kind.Contact })
    }

    @Test fun instantAnswers() {
        assertTrue(run("12*4+7").instants.single() is Instant.Calculation)
        assertTrue(run("12*4+7", SearchPreferences(calculator = false)).instants.isEmpty())
        assertEquals(Action.Timer(600), (run("timer 10 minutes").instants.single() as Instant.Command).action)
        assertEquals(Toggle.Flashlight, (run("torch").instants.single() as Instant.Switch).request.toggle)
        assertEquals("YouTube", (run("cats !yt").instants.single() as Instant.Shortcut).use.shortcut.name)
    }

    @Test fun webAddressesButNotFileNames() {
        assertEquals("https://example.com", Search.webAddress("example.com"))
        assertEquals("https://news.ycombinator.com/item?id=1", Search.webAddress("news.ycombinator.com/item?id=1"))
        assertEquals("https://x.io", Search.webAddress("http://x.io"))
        assertNull(Search.webAddress("report.pdf"))
        assertNull(Search.webAddress("holiday.mov"))
        assertNull(Search.webAddress("1.5"))
        assertNull(Search.webAddress("hello world.com"))
    }

    @Test fun emptyQueryGivesNothing() {
        val r = run("  ")
        assertTrue(r.instants.isEmpty() && r.sections.isEmpty() && r.best == null)
    }
}
