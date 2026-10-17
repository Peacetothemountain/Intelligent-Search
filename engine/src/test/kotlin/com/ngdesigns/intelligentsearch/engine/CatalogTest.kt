package com.ngdesigns.intelligentsearch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private fun app(name: String, pkg: String = "com.example.${name.lowercase().filter(Char::isLetter)}", usage: Usage = Usage.None) =
        Candidate(id = pkg, kind = Kind.App, title = name, keywords = listOf(pkg), usage = usage)

    private val apps = Catalog(
        listOf("YouTube", "Spotify", "Instagram", "WhatsApp", "Calculator", "Google Maps", "Gmail", "Netflix", "Settings", "Keep Notes")
            .map { app(it) },
    )

    private fun top(query: String, catalog: Catalog = apps) = catalog.search(query, limit = 1).firstOrNull()?.candidate?.title

    @Test fun findsAppsThroughTypos() {
        assertEquals("YouTube", top("yuotube"))
        assertEquals("Spotify", top("spotfy"))
        assertEquals("Instagram", top("instagarm"))
        assertEquals("Calculator", top("calculater"))
        assertEquals("Netflix", top("netflx"))
    }

    @Test fun findsAppsThroughPartlyTypedTypos() {
        assertEquals("YouTube", top("yuotu"))
        assertEquals("Spotify", top("sptoif"))
        assertEquals("Instagram", top("instgr"))
        assertEquals("WhatsApp", top("whtas"))
        assertEquals("Calculator", top("calcul8"))
        assertEquals("Google Maps", top("gogle ma"))
    }

    @Test fun findsNothingForUnrelatedWords() = assertNull(top("xylophone"))

    @Test fun typosCanBeTurnedOff() = assertNull(apps.search("yuotube", options = RankingOptions(typos = false)).firstOrNull())

    @Test fun wordStartsAndAcronymsMatch() {
        assertEquals("Keep Notes", top("notes"))
        assertEquals("YouTube", top("yt"))
        assertEquals("WhatsApp", top("wa"))
        assertEquals(MatchKind.Acronym, apps.search("yt").first().match.kind)
    }

    @Test fun exactBeatsPrefixBeatsTypo() {
        val catalog = Catalog(listOf(app("Spot"), app("Spotify"), app("Sptify Lite")))
        assertEquals(listOf("Spot", "Spotify", "Sptify Lite"), catalog.search("spot").map { it.candidate.title })
        assertEquals(MatchKind.Typo, catalog.search("spot")[2].match.kind)
        assertTrue(catalog.search("spot")[0].score > catalog.search("spot")[1].score)
    }

    @Test fun usageBreaksTiesBetweenEqualMatches() {
        val now = 1_000_000_000_000L
        val used = Usage(count = 12, lastUsedMillis = now)
        val chromeUsed = Catalog(listOf(app("Chrome", usage = used), app("Chess")))
        val chessUsed = Catalog(listOf(app("Chrome"), app("Chess", usage = used)))
        assertEquals("Chrome", chromeUsed.search("ch", options = RankingOptions(now = now)).first().candidate.title)
        assertEquals("Chess", chessUsed.search("ch", options = RankingOptions(now = now)).first().candidate.title)
    }

    @Test fun recencyHalvesEachHalfLife() {
        val now = 10_000_000_000L
        val halfLife = RankingOptions().recencyHalfLifeMillis
        fun scoreAt(age: Long) = Catalog(listOf(app("App", usage = Usage(lastUsedMillis = now - age))))
            .search("app", options = RankingOptions(now = now)).first().score - 1000f
        assertEquals(175f, scoreAt(halfLife), 1f)
        assertEquals(87.5f, scoreAt(2 * halfLife), 1f)
    }

    @Test fun packageNamesMatchOnlyDottedQueries() {
        val filler = (1..60).map { app("Zed App $it", "com.filler.app$it") }
        val catalog = Catalog(filler + app("Camera", "com.google.android.GoogleCamera") + app("Spotify", "com.spotify.music"))
        assertTrue(catalog.search("com").isEmpty())
        assertEquals("Spotify", catalog.search("com.spotify").first().candidate.title)
        val c = catalog.search("c").map { it.candidate.title }
        assertTrue(c.toString(), "Camera" in c && c.none { it.startsWith("Zed") })
    }

    @Test fun peopleMatchBySound() {
        val people = Catalog(listOf("Stephen Hale", "Kathryn Moss", "Jon Price").map {
            Candidate(id = it, kind = Kind.Contact, title = it, soundsLike = true)
        })
        assertEquals("Stephen Hale", top("steven", people))
        assertEquals("Kathryn Moss", top("catherine", people))
        assertEquals("Jon Price", top("john", people))
    }

    @Test fun limitAndEmptyQuery() {
        assertEquals(2, apps.search("s", limit = 2).size)
        assertTrue(apps.search("   ").isEmpty())
        assertFalse(apps.search("g").isEmpty())
    }
}
