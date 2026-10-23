package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins as stored: stable keys, the JSON round trip, what an older version wrote, and what a crafted file can't do. */
class ResultShortcutsTest {

    private val spotify = "app:com.spotify.music/com.spotify.music.MainActivity@0"
    private val dad = "contact:0r1-ABC"

    @Test
    fun contactsAreKeyedByTheirLookupKeyNotTheirRowId() {
        val before = ContactItem("John", "555", "content://com.android.contacts/contacts/lookup/0r1-ABC/12")
        val after = before.copy(lookupUri = "content://com.android.contacts/contacts/lookup/0r1-ABC/99")
        assertEquals(dad, ResultKeys.contact(before))
        assertEquals(ResultKeys.contact(before), ResultKeys.contact(after))
        assertNull(ResultKeys.contact(before.copy(lookupUri = "")))
    }

    @Test
    fun filesAreKeyedByTheirDocumentUri() {
        val file = FileItem("Resume.pdf", "/Documents", "application/pdf", "content://media/external/file/42")
        assertEquals("file:content://media/external/file/42", ResultKeys.file(file))
        assertEquals(ResultKind.FILE, ResultKeys.kindOf(ResultKeys.file(file)))
    }

    @Test
    fun keysNameTheirKind() {
        assertEquals(ResultKind.APP, ResultKeys.kindOf(spotify))
        assertEquals("com.spotify.music", ResultKeys.appPackage(spotify))
        assertEquals("battery", ResultKeys.settingId("setting:battery"))
        assertNull(ResultKeys.kindOf("web:cats"))
        // Saved Links were removed; their keys name nothing now.
        assertNull(ResultKeys.kindOf("link:https://bank.example.com/"))
        assertNull(ResultKeys.kindOf("app:"))
    }

    @Test
    fun pinsRoundTripThroughJson() {
        val set = ResultShortcuts.EMPTY
            .withPinned(spotify, "Spotify", true)
            .withPinned(dad, "John", true)
            .withPinned("setting:battery", "Battery", true)

        val restored = ResultShortcuts.fromJson(set.toJson())
        assertEquals(set, restored)
        assertTrue(restored.isPinned(spotify))
        // Pinned in the order they were pinned.
        assertEquals(listOf(spotify, dad, "setting:battery"), restored.pinned.map { it.key })
    }

    @Test
    fun anEntryWithNothingLeftIsDropped() {
        val set = ResultShortcuts.EMPTY.withPinned(spotify, "Spotify", true).withPinned(spotify, "Spotify", false)
        assertTrue(set.entries.isEmpty())
        assertTrue(ResultShortcuts.EMPTY.withPinned(spotify, "Spotify", true).without(spotify).entries.isEmpty())
    }

    /**
     * Nicknames and trigger words were removed: what an older version saved still reads, its pins kept and its
     * nicknames and trigger words dropped (an entry that only had those goes), and they are never written again.
     */
    @Test
    fun anOlderVersionsNicknamesAndTriggerWordsAreDroppedAndItsPinsKept() {
        val older = """
            [
              {"key":"$spotify","title":"Spotify","pinned":true,"nicknames":["music"],"trigger":"sp"},
              {"key":"$dad","title":"John","nicknames":["dad","Father"],"trigger":"jo"},
              {"key":"setting:battery","title":"Battery","pinned":true}
            ]
        """.trimIndent()
        val set = ResultShortcuts.fromJson(older)
        assertEquals(listOf(spotify, "setting:battery"), set.pinned.map { it.key })
        assertEquals(listOf(spotify, "setting:battery"), set.entries.map { it.key })
        assertEquals("Spotify", set[spotify]?.title)
        val written = set.toJson()
        assertFalse(written, "nicknames" in written)
        assertFalse(written, "trigger" in written)
        assertEquals(set, ResultShortcuts.fromJson(written))
    }

    @Test
    fun aCraftedFileReadsAsNothingOrOnlyItsValidEntries() {
        assertEquals(ResultShortcuts.EMPTY, ResultShortcuts.fromJson("not json"))
        assertEquals(ResultShortcuts.EMPTY, ResultShortcuts.fromJson("{\"key\":1}"))
        assertEquals(ResultShortcuts.EMPTY, ResultShortcuts.fromJson(""))
        val crafted = """
            [
              {"key":"web:anything","title":"x","pinned":true},
              {"key":"link:https://bank.example.com/","title":"Bank","pinned":true},
              {"key":"$spotify","title":"Spotify","pinned":true},
              {"key":"$spotify","title":"Again","pinned":true},
              {"key":"$dad","title":"John"}
            ]
        """.trimIndent()
        val set = ResultShortcuts.fromJson(crafted)
        // Unknown kinds (and a removed Saved Link's pin) go, a key appears once, and an entry with nothing set goes.
        assertEquals(listOf(spotify), set.entries.map { it.key })
        assertEquals("Spotify", set[spotify]?.title)
    }
}
