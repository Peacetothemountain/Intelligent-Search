package com.pixel.intelligentsearch.core.answers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Answers are kept for their time to live, in memory and on disk, and the oldest give way first. */
class AnswerCacheTest {

    @get:Rule val folder = TemporaryFolder()

    private var now = 1_000_000L

    @Test
    fun aFreshAnswerComesBackAndAStaleOneDoesNot() {
        val cache = AnswerCache<String>(ttlMs = 60_000, memoryEntries = 4, now = { now })
        cache.put("k", "value")
        assertEquals("value", cache.peek("k"))
        now += 59_999
        assertEquals("value", cache.peek("k"))
        now += 1
        assertNull(cache.peek("k"))
    }

    @Test
    fun theLeastRecentlyUsedAnswerGoesFirst() {
        val cache = AnswerCache<String>(ttlMs = 60_000, memoryEntries = 2, now = { now })
        cache.put("a", "1")
        cache.put("b", "2")
        cache.peek("a")
        cache.put("c", "3")
        assertEquals("1", cache.peek("a"))
        assertNull(cache.peek("b"))
        assertEquals("3", cache.peek("c"))
    }

    @Test
    fun answersOnDiskOutliveTheProcessUntilTheyGoStale() {
        val dir = folder.newFolder("answers")
        AnswerCache<String>(60_000, 4, DiskAnswerCache(dir, 10), now = { now }).put("word", "parsed", body = "raw body")

        // A new process: nothing in memory, the body read back from disk and decoded.
        val reopened = AnswerCache<String>(60_000, 4, DiskAnswerCache(dir, 10), now = { now })
        assertNull(reopened.peek("word"))
        assertEquals("decoded raw body", reopened.get("word") { "decoded $it" })
        // Now it is in memory as well.
        assertEquals("decoded raw body", reopened.peek("word"))

        now += 60_000
        val later = AnswerCache<String>(60_000, 4, DiskAnswerCache(dir, 10), now = { now })
        assertNull(later.get("word") { it })
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun aDiskCopyThatNoLongerDecodesIsDropped() {
        val dir = folder.newFolder("answers")
        AnswerCache<String>(60_000, 4, DiskAnswerCache(dir, 10), now = { now }).put("word", "parsed", body = "raw")
        assertNull(AnswerCache<String>(60_000, 4, DiskAnswerCache(dir, 10), now = { now }).get("word") { null })
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun theDiskKeepsOnlyTheNewestEntriesAndNamesNoQuery() {
        val dir = folder.newFolder("answers")
        val disk = DiskAnswerCache(dir, maxEntries = 3)
        for (i in 1..5) {
            disk.write("secret word $i", "body $i", now)
            // Modification times must differ for the oldest to be told apart.
            Thread.sleep(20)
        }
        val files = dir.listFiles()!!
        assertEquals(3, files.size)
        assertEquals(0, files.count { "secret" in it.name })
        assertNull(disk.read("secret word 1", 60_000, now))
        assertEquals("body 5", disk.read("secret word 5", 60_000, now)?.body)

        disk.clear()
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun aMissIsRememberedInMemoryOnly() {
        val dir = folder.newFolder("answers")
        val cache = AnswerCache<Boolean>(60_000, 4, DiskAnswerCache(dir, 10), now = { now })
        cache.putInMemory("nothing", true)
        assertEquals(true, cache.peek("nothing"))
        assertEquals(0, dir.listFiles()!!.size)
    }
}
