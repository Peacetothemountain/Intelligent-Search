package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the user opened and what they typed for it: weights, decay, the Best Match pick, clearing and storage. */
@RunWith(RobolectricTestRunner::class)
class SelectionMemoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs: SharedPreferences = context.getSharedPreferences("selection_memory_test", Context.MODE_PRIVATE)
    private var now = 1_000_000_000_000L

    private fun memory(storage: SharedPreferences? = prefs) = SelectionMemory(store = { storage }, clock = { now })

    private val chrome = "app:com.android.chrome/Main@0"
    private val calculator = "app:com.google.android.calculator/Main@0"
    private val mom = "contact:mom"

    @Before fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun aPickCountsFullyForItsOwnTextAndPartlyForRelatedText() {
        val memory = memory()
        memory.record(chrome, "ch")
        assertEquals(1f, memory.learnedWeight(chrome, "ch"), 0.001f)
        // Typed less than when it was picked.
        assertEquals(0.5f, memory.learnedWeight(chrome, "c"), 0.001f)
        // Typed more.
        assertEquals(0.75f, memory.learnedWeight(chrome, "chr"), 0.001f)
        assertEquals(0f, memory.learnedWeight(chrome, "ma"), 0.001f)
        assertEquals(0f, memory.learnedWeight(calculator, "ch"), 0.001f)
    }

    @Test
    fun queriesAreComparedFolded() {
        val memory = memory()
        memory.record(chrome, "  CHR ")
        assertEquals(1f, memory.learnedWeight(chrome, SelectionMemory.normalizeQuery("chr")), 0.001f)
    }

    @Test
    fun weightsHalveEachHalfLife() {
        val memory = memory()
        memory.record(chrome, "ch")
        now += SelectionMemory.HALF_LIFE_MS
        assertEquals(0.5f, memory.learnedWeight(chrome, "ch"), 0.001f)
        assertEquals(0.5f, memory.usage(chrome)!!.opens, 0.001f)
    }

    @Test
    fun opensAreCountedWithOrWithoutAQuery() {
        val memory = memory()
        memory.record(mom, null)
        memory.record(mom, "")
        memory.record(mom, "mo")
        val usage = memory.usage(mom)!!
        assertEquals(3f, usage.opens, 0.001f)
        assertEquals(now, usage.lastMs)
        assertEquals(1f, memory.learnedWeight(mom, "mo"), 0.001f)
        assertNull(memory.usage(chrome))
    }

    @Test
    fun oneStrayTapDoesNotTakeTheBestMatchButAHabitDoes() {
        val memory = memory()
        memory.record(calculator, "c")
        assertNull(memory.topPick("c"))
        memory.record(calculator, "c")
        assertEquals(calculator, memory.topPick("c"))
        // Only exactly that text: "ca" was never typed before picking it.
        assertNull(memory.topPick("ca"))
    }

    @Test
    fun theMostPickedResultIsTheTopPick() {
        val memory = memory()
        repeat(2) { memory.record(calculator, "c") }
        repeat(3) { memory.record(chrome, "c") }
        assertEquals(chrome, memory.topPick("c"))
    }

    @Test
    fun clearingForgetsWhatWasTypedButKeepsOpensAndUndoBringsItBack() {
        val memory = memory()
        repeat(2) { memory.record(chrome, "ch") }
        val snapshot = memory.clearPicks()
        assertEquals(0f, memory.learnedWeight(chrome, "ch"), 0.001f)
        assertNull(memory.topPick("ch"))
        assertEquals(2f, memory.usage(chrome)!!.opens, 0.001f)
        // Not even on disk.
        assertEquals(0f, memory().learnedWeight(chrome, "ch"), 0.001f)

        memory.restore(snapshot)
        assertEquals(2f, memory.learnedWeight(chrome, "ch"), 0.001f)
    }

    @Test
    fun removingOneSearchForgetsOnlyThatText() {
        val memory = memory()
        memory.record(chrome, "ch")
        memory.record(chrome, "chrome")
        memory.forgetQuery("Ch")
        // Only the pick after typing "chrome" (more than "ch") is left.
        assertEquals(0.5f, memory.learnedWeight(chrome, "ch"), 0.001f)
        assertEquals(1f, memory.learnedWeight(chrome, "chrome"), 0.001f)
    }

    @Test
    fun whatIsRememberedSurvivesARestart() {
        memory().record(chrome, "ch")
        assertEquals(1f, memory().learnedWeight(chrome, "ch"), 0.001f)
    }

    @Test
    fun aDamagedFileReadsAsNothingRemembered() {
        prefs.edit().putString("entries", "{not json").commit()
        val memory = memory()
        assertNull(memory.usage(chrome))
        memory.record(chrome, "ch")
        assertEquals(1f, memory.learnedWeight(chrome, "ch"), 0.001f)
    }

    @Test
    fun unknownKindsAreNotRemembered() {
        val memory = memory()
        memory.record("link:https://example.com", "ex")
        assertNull(memory.usage("link:https://example.com"))
    }

    @Test
    fun nothingIsReadOrWrittenBeforeTheFirstUnlock() {
        val locked = memory(storage = null)
        locked.record(chrome, "ch")
        assertNull(locked.usage(chrome))
        assertEquals(emptySet<String>(), locked.candidateKeys("ch"))
        assertNull(memory().usage(chrome))
    }

    @Test
    fun theFileKeepsOnlyTheMostOpenedResults() {
        val memory = memory()
        repeat(SelectionMemory.MAX_RESULTS + 20) { i ->
            memory.record("file:content://files/$i", null)
            now += 1_000
        }
        repeat(3) { memory.record(chrome, null) }
        memory.record("file:content://files/new", null)
        val reopened = memory()
        assertTrue(reopened.usage(chrome) != null)
        assertTrue(reopened.usage("file:content://files/new") != null)
        assertNull(reopened.usage("file:content://files/0"))
    }

    @Test
    fun eachResultKeepsItsStrongestPicks() {
        val memory = memory()
        repeat(3) { memory.record(chrome, "chrome") }
        repeat(SelectionMemory.MAX_PICKS_PER_RESULT + 4) { i -> memory.record(chrome, "q$i") }
        assertEquals(3f, memory.learnedWeight(chrome, "chrome"), 0.001f)
    }

    @Test
    fun candidatesAreRelatedPicksAndTheMostOpened() {
        val memory = memory()
        memory.record(chrome, "ch")
        memory.record(mom, null)
        val keys = memory.candidateKeys("c")
        assertTrue(chrome in keys)
        assertTrue(mom in keys)
        assertEquals(setOf(chrome), memory.candidateKeys("c", frequent = 0))
    }
}
