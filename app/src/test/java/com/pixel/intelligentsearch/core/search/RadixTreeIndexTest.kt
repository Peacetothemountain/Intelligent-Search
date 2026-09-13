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
}
