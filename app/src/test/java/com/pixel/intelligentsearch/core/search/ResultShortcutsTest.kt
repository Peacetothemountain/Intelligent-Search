package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins, nicknames and trigger words as stored: stable keys, the JSON round trip, and what a crafted file can't do. */
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
    fun everythingSetRoundTripsThroughJson() {
        val set = ResultShortcuts.EMPTY
            .withPinned(spotify, "Spotify", true)
            .withNickname(spotify, "Spotify", "music")
            .withTrigger(spotify, "Spotify", "SP")
            .withNickname(dad, "John", "  dad ")
            .withNickname(dad, "John", "Father")
            .withPinned("setting:battery", "Battery", true)

        val restored = ResultShortcuts.fromJson(set.toJson())
        assertEquals(set, restored)
        assertTrue(restored.isPinned(spotify))
        assertEquals("sp", restored[spotify]?.trigger)
        assertEquals(listOf("dad", "Father"), restored.nicknamesOf(dad))
        // Pinned in the order they were pinned.
        assertEquals(listOf(spotify, "setting:battery"), restored.pinned.map { it.key })
    }

    @Test
    fun anEntryWithNothingLeftIsDropped() {
        val set = ResultShortcuts.EMPTY.withPinned(spotify, "Spotify", true).withPinned(spotify, "Spotify", false)
        assertTrue(set.entries.isEmpty())
        // As the settings page removes a result's last nickname.
        val named = ResultShortcuts.EMPTY.withNickname(dad, "John", "dad").update(dad, "John") { it.copy(nicknames = emptyList()) }
        assertTrue(named.entries.isEmpty())
    }

    @Test
    fun nicknamesAreNotRepeatedInAnyCase() {
        val set = ResultShortcuts.EMPTY.withNickname(dad, "John", "Dad").withNickname(dad, "John", "dad")
        assertEquals(listOf("Dad"), set.nicknamesOf(dad))
        val blank = ResultShortcuts.EMPTY.withNickname(dad, "John", "   ")
        assertTrue(blank.entries.isEmpty())
    }

    @Test
    fun nicknamesAreFoundWholeOrByTheirStart() {
        val set = ResultShortcuts.EMPTY.withNickname(dad, "John", "Dad").withNickname(spotify, "Spotify", "my music")
        assertEquals(setOf(dad), set.keysWithNicknameFor("dad"))
        assertEquals(setOf(dad), set.keysWithNicknameFor("DA"))
        assertEquals(setOf(spotify), set.keysWithNicknameFor("my mu"))
        assertEquals(emptySet<String>(), set.keysWithNicknameFor("ad"))
        assertEquals(emptySet<String>(), set.keysWithNicknameFor("music"))
    }

    @Test
    fun aCraftedFileReadsAsNothingOrOnlyItsValidEntries() {
        assertEquals(ResultShortcuts.EMPTY, ResultShortcuts.fromJson("not json"))
        assertEquals(ResultShortcuts.EMPTY, ResultShortcuts.fromJson("{\"key\":1}"))
        val crafted = """
            [
              {"key":"web:anything","title":"x","pinned":true},
              {"key":"link:https://bank.example.com/","title":"Bank","pinned":true,"trigger":"bk"},
              {"key":"$spotify","title":"Spotify","trigger":"!yt"},
              {"key":"$spotify","title":"Again","pinned":true},
              {"key":"$dad","title":"John","trigger":"sp","nicknames":["dad","DAD",""]},
              {"key":"setting:battery","title":"Battery","trigger":"SP"}
            ]
        """.trimIndent()
        val set = ResultShortcuts.fromJson(crafted)
        // Unknown kinds (and a removed Saved Link's pin and trigger) go, a key appears once, a bang-like trigger is dropped, and a word claimed twice keeps its first.
        assertEquals(listOf(dad), set.entries.map { it.key })
        assertEquals("sp", set[dad]?.trigger)
        assertEquals(listOf("dad"), set.nicknamesOf(dad))
    }

    @Test
    fun triggerWordsAreLettersAndDigitsAndNeverAWebShortcut() {
        val set = ResultShortcuts.EMPTY.withTrigger(spotify, "Spotify", "sp")
        assertEquals(QuickKeys.Check.Ok, QuickKeys.check("yt2", dad, set))
        assertEquals(QuickKeys.Check.ReservedForWebShortcuts, QuickKeys.check("!yt", dad, set))
        assertEquals(QuickKeys.Check.ReservedForWebShortcuts, QuickKeys.check("#yt", dad, set, bangSymbol = "#"))
        assertEquals(QuickKeys.Check.LettersAndDigitsOnly, QuickKeys.check("s p", dad, set))
        assertEquals(QuickKeys.Check.LettersAndDigitsOnly, QuickKeys.check("sp.", dad, set))
        assertEquals(QuickKeys.Check.TooLong, QuickKeys.check("a".repeat(QuickKeys.MAX_LENGTH + 1), dad, set))
        assertEquals(QuickKeys.Check.Empty, QuickKeys.check("  ", dad, set))
    }

    /** A trigger word opens its result as it is typed, so it may not be a word search already starts on. */
    @Test
    fun wordsSearchReadsFirstAreNotTriggerWords() {
        val set = ResultShortcuts.EMPTY
        for (word in listOf("pdf", "photos", "sheets", "ask", "define", "translate", "weather", "forecast",
            "remind", "timer", "alarm", "call", "text", "set", "Weather")) {
            assertEquals(word, QuickKeys.Check.ReservedForSearch, QuickKeys.check(word, dad, set))
        }
        assertEquals("Already Used by Search.", QuickKeys.message(QuickKeys.Check.ReservedForSearch))
        assertEquals(set, set.withTrigger(dad, "John", "remind"))
        // Words that merely start like them are fine.
        assertEquals(QuickKeys.Check.Ok, QuickKeys.check("pdfs2", dad, set))
        assertEquals(QuickKeys.Check.Ok, QuickKeys.check("caller", dad, set))
        // One saved by an older version is dropped as the set is read.
        val older = """[{"key":"$dad","title":"John","nicknames":["dad"],"trigger":"weather"}]"""
        assertEquals(listOf("dad"), ResultShortcuts.fromJson(older).nicknamesOf(dad))
        assertEquals("", ResultShortcuts.fromJson(older)[dad]?.trigger)
    }

    @Test
    fun triggerWordsAreUnique() {
        val set = ResultShortcuts.EMPTY.withTrigger(spotify, "Spotify", "sp")
        assertEquals(QuickKeys.Check.Taken("Spotify"), QuickKeys.check("SP", dad, set))
        // A result may keep its own word.
        assertEquals(QuickKeys.Check.Ok, QuickKeys.check("sp", spotify, set))
        // Setting a taken word changes nothing.
        assertEquals(set, set.withTrigger(dad, "John", "sp"))
        assertEquals("Already Opens Spotify.", QuickKeys.message(QuickKeys.Check.Taken("Spotify")))
    }

    @Test
    fun aTriggerOpensWhenTypedWithASpaceOrSentAlone() {
        val set = ResultShortcuts.EMPTY.withTrigger(spotify, "Spotify", "sp")
        assertEquals(spotify, QuickKeys.matchTyped("sp ", set)?.key)
        assertEquals(spotify, QuickKeys.matchTyped("SP ", set)?.key)
        assertNull(QuickKeys.matchTyped("sp", set))
        assertNull(QuickKeys.matchTyped(" sp ", set))
        assertNull(QuickKeys.matchTyped("sp  ", set))
        assertNull(QuickKeys.matchTyped("spo ", set))
        assertNull(QuickKeys.matchTyped("go sp ", set))

        assertEquals(spotify, QuickKeys.matchSubmitted("sp", set)?.key)
        assertEquals(spotify, QuickKeys.matchSubmitted(" sp ", set)?.key)
        assertNull(QuickKeys.matchSubmitted("sp music", set))
    }

    @Test
    fun aBangIsNeverTakenForATrigger() {
        // Even a file that somehow holds "yt" as a trigger can't make "!yt " open it: the "!" belongs to bangs.
        val set = ResultShortcuts.EMPTY.withTrigger(spotify, "Spotify", "yt")
        assertNull(QuickKeys.matchTyped("!yt ", set))
        assertNull(QuickKeys.matchSubmitted("!yt", set))
        assertNull(QuickKeys.matchSubmitted("!yt cats", set))
    }

    /** Both nickname editors (the long-press menu and the settings page) check a new nickname the same way. */
    @Test
    fun aNewNicknameIsCheckedOneWay() {
        assertEquals("Dad", ResultShortcuts.canAddNickname("  Dad ", emptyList()))
        assertNull(ResultShortcuts.canAddNickname("   ", emptyList()))
        assertNull(ResultShortcuts.canAddNickname("dad", listOf("Dad")))
        assertEquals("John Already Has This Nickname.", ResultShortcuts.nicknameError("DAD", listOf("Dad"), "John"))
        val full = (1..ResultShortcuts.MAX_NICKNAMES).map { "n$it" }
        assertNull(ResultShortcuts.canAddNickname("one more", full))
        assertEquals("Up to ${ResultShortcuts.MAX_NICKNAMES} Nicknames.", ResultShortcuts.nicknameError("one more", full, "John"))
        assertNull(ResultShortcuts.nicknameError("", emptyList(), "John"))
        assertEquals("Letters and Digits, Up to ${QuickKeys.MAX_LENGTH}.", QuickKeys.HINT)
    }
}
