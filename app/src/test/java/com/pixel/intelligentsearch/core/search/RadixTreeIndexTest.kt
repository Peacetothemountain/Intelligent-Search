package com.pixel.intelligentsearch.core.search

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RadixTreeIndexTest {

    private lateinit var index: RadixTreeIndex<String>

    @Before
    fun setup() {
        index = RadixTreeIndex()
    }

    @Test
    fun testPrefixRetrieval_exactAndPartial() {
        index.insert("spotify", "Spotify")
        index.insert("slack", "Slack")
        index.insert("snapchat", "Snapchat")
        index.insert("settings", "Settings")
        index.insert("chrome", "Google Chrome")

        val sResults = index.searchPrefix("s")
        assertEquals(4, sResults.size)

        val spResults = index.searchPrefix("sp")
        assertEquals(1, spResults.size)
        assertEquals("Spotify", spResults[0])

        val snResults = index.searchPrefix("sn")
        assertEquals(1, snResults.size)
        assertEquals("Snapchat", snResults[0])

        val chResults = index.searchPrefix("ch")
        assertEquals(1, chResults.size)
        assertEquals("Google Chrome", chResults[0])
    }

    @Test
    fun testEdgeSplitting() {
        index.insert("cat", "Cat")
        index.insert("caterpillar", "Caterpillar")
        index.insert("cattle", "Cattle")
        index.insert("dog", "Dog")

        val catResults = index.searchPrefix("cat")
        assertEquals(3, catResults.size)
        assertTrue(catResults.contains("Cat"))
        assertTrue(catResults.contains("Caterpillar"))
        assertTrue(catResults.contains("Cattle"))

        val cattleResults = index.searchPrefix("catt")
        assertEquals(1, cattleResults.size)
        assertEquals("Cattle", cattleResults[0])
    }

    @Test
    fun testFuzzySearch_typoTolerance() {
        index.insert("spotify", "Spotify")
        index.insert("chrome", "Chrome")
        index.insert("netflix", "Netflix")

        // 1 edit distance ("spotfy" -> missing 'i')
        val fuzzy1 = index.searchFuzzy("spotfy", maxDistance = 1)
        assertEquals(1, fuzzy1.size)
        assertEquals("Spotify", fuzzy1[0].value)
        assertEquals(1, fuzzy1[0].distance)

        // 2 edit distance ("chrme" -> missing 'o')
        val fuzzy2 = index.searchFuzzy("chrme", maxDistance = 2)
        assertTrue(fuzzy2.any { it.value == "Chrome" })
    }

    @Test
    fun testSubMillisecondLookupBenchmark() {
        // Ingest 1,000 synthetic entities
        for (i in 1..1000) {
            index.insert("app_$i", "App $i")
            index.insert("contact_$i", "Contact $i")
        }

        // Benchmark 100 queries
        val startTime = System.nanoTime()
        val queryCount = 100
        for (i in 1..queryCount) {
            val results = index.searchPrefix("app_1", limit = 10)
            assertTrue(results.isNotEmpty())
        }
        val elapsedNano = System.nanoTime() - startTime
        val avgMicroseconds = (elapsedNano / queryCount) / 1000.0

        println("RadixTreeIndex Benchmark: Average lookup time = ${avgMicroseconds}µs")
        // Verify average lookup time is well below 1,000µs (1ms)
        assertTrue("Prefix lookup took ${avgMicroseconds}µs, expected < 1000µs", avgMicroseconds < 1000.0)
    }

    @Test
    fun testEmptyAndZeroIndexResilience() {
        index.insert("", "Empty")
        index.insert("   ", "Spaces")
        assertEquals(0, index.size)

        assertTrue(index.searchPrefix("").isEmpty())
        assertTrue(index.searchPrefix("   ").isEmpty())
        assertTrue(index.searchFuzzy("").isEmpty())
    }

    /** Equality on [id] only, mirroring how SearchItem identifies an entity independent of its payload. */
    private class Entity(val id: String, val label: String) {
        override fun equals(other: Any?) = other is Entity && other.id == id
        override fun hashCode() = id.hashCode()
    }

    @Test
    fun testReinsertReplacesStaleEqualValue() {
        val tree = RadixTreeIndex<Entity>()
        tree.insert("maps", Entity("app:maps", "Maps v1"))
        tree.insert("maps", Entity("app:maps", "Maps v2"))
        // Same key again through a split path and a descended path
        tree.insert("map", Entity("app:maps", "Maps v2"))
        tree.insert("mapsy", Entity("app:mapsy", "Mapsy"))
        tree.insert("maps", Entity("app:maps", "Maps v3"))

        val results = tree.searchPrefix("maps")
        val maps = results.single { it.id == "app:maps" }
        assertEquals("Maps v3", maps.label)
        // Counting is by distinct (key, value) pairs: maps, map, mapsy
        assertEquals(3, tree.size)
    }

    @Test
    fun testReplaceAllSwapsContentsAtomically() {
        index.insert("alpha", "A")
        index.insert("beta", "B")

        index.replaceAll(listOf("gamma" to "G", "  " to "ignored", "gam" to "G2"))

        assertTrue(index.searchPrefix("alpha").isEmpty())
        assertTrue(index.searchPrefix("beta").isEmpty())
        assertEquals(setOf("G", "G2"), index.searchPrefix("gam").toSet())
        assertEquals(2, index.size)

        index.replaceAll(emptyList())
        assertEquals(0, index.size)
        assertTrue(index.searchPrefix("gam").isEmpty())
    }

    @Test
    fun testConcurrentReadersNeverObserveEmptyIndexDuringReplaceAll() {
        index.replaceAll((1..200).map { "app$it" to "v$it" })
        val failed = java.util.concurrent.atomic.AtomicBoolean(false)
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)

        val readers = (1..3).map {
            Thread {
                while (!stop.get()) {
                    if (index.searchPrefix("app", limit = 1).isEmpty()) failed.set(true)
                }
            }.also { it.start() }
        }
        repeat(200) { round ->
            index.replaceAll((1..200).map { "app$it" to "r$round-$it" })
        }
        stop.set(true)
        readers.forEach { it.join() }

        assertFalse("A reader saw an empty index while replaceAll was running", failed.get())
    }

    @Test
    fun testFuzzyDeduplication() {
        index.insert("calculator", "CalcApp")
        index.insert("calc", "CalcApp")
        index.insert("calcapp", "CalcApp")

        val results = index.searchFuzzy("cal", maxDistance = 2)
        // CalcApp must appear exactly once despite matching multiple indexed keys
        assertEquals(1, results.count { it.value == "CalcApp" })
    }
}
