package com.ngdesigns.intelligentsearch.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/** Search runs on every keystroke, so it must stay far inside one frame even with a large phone's worth of data. */
class SpeedTest {
    @Test fun fiveThousandCandidatesSearchWellInsideAFrame() {
        val syllables = listOf("ka", "lo", "mi", "ra", "te", "su", "no", "vi", "pa", "de", "zu", "ri")
        val random = java.util.Random(7)
        fun word() = (1..(2 + random.nextInt(3))).joinToString("") { syllables[random.nextInt(syllables.size)] }
        val candidates = (1..5000).map { i ->
            val kind = if (i % 3 == 0) Kind.Contact else Kind.App
            Candidate("$i", kind, "${word().replaceFirstChar(Char::uppercase)} ${word()}", soundsLike = kind == Kind.Contact)
        }
        val catalogs = candidates.groupBy { it.kind }.mapValues { Catalog(it.value) }
        val queries = listOf("k", "ka", "kal", "kalo", "kalom", "mira", "tesu", "vipa", "dezur", "rino", "kalpo", "mirz")
        repeat(30) { queries.forEach { Search.run(it, catalogs) } } // warm up the JIT
        val start = System.nanoTime()
        repeat(10) { queries.forEach { Search.run(it, catalogs) } }
        val perQueryMs = (System.nanoTime() - start) / 1e6 / (10 * queries.size)
        println("search: average ${"%.3f".format(perQueryMs)} ms per query over 5000 candidates")
        assertTrue("average ${"%.2f".format(perQueryMs)} ms per query", perQueryMs < 16.0)
    }
}
