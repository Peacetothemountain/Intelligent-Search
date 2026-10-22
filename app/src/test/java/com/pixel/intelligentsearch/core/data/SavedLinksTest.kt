package com.pixel.intelligentsearch.core.data

import com.pixel.intelligentsearch.core.net.HttpsGet
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.backup.BackupManager
import com.pixel.intelligentsearch.core.backup.BackupPreferenceSchema
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.search.ResultKeys
import com.pixel.intelligentsearch.core.search.ResultKind
import com.pixel.intelligentsearch.core.search.ResultShortcuts
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Saved Links: https addresses only, kept in settings and backups, found by name with pins and nicknames applied. */
@RunWith(RobolectricTestRunner::class)
class SavedLinksTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsManager(context)

    private val bank = SavedLink("Bank", "https://bank.example.com/")
    private val timesheet = SavedLink("Timesheet", "https://work.example.com/timesheet")
    private val bandcamp = SavedLink("Bandcamp Feed", "https://bandcamp.com/feed")

    @Test
    fun addressesAreCheckedAndStoredOverHttps() {
        assertEquals(SavedLinks.Check.Valid("https://github.com/notifications"), SavedLinks.check("github.com/notifications"))
        assertEquals(SavedLinks.Check.Valid("https://github.com/"), SavedLinks.check("https://GitHub.com"))
        for (bad in listOf("http://example.com", "javascript:alert(1)", "https://bank.com@evil.example", "intent:#Intent;end", "not a site", "", "https://x.com/?q=%s")) {
            assertTrue(bad, SavedLinks.check(bad) is SavedLinks.Check.Invalid)
        }
    }

    @Test
    fun linksFromABackupAreCheckedAsTheEditorChecksThem() {
        val json = """[{"name":"Bank","url":"https://bank.example.com/"},{"name":"Evil","url":"javascript:alert(1)"},
            {"name":"  ","url":"https://blank.example.com/"},{"name":"Again","url":"https://bank.example.com/"},{"name":"Plain","url":"http://plain.example.com"}]"""
        assertEquals(listOf(bank), SavedLinks.fromJson(json))
        assertEquals(emptyList<SavedLink>(), SavedLinks.fromJson("not json"))
        assertEquals(listOf(bank, timesheet), SavedLinks.fromJson(SavedLinks.toJson(listOf(bank, timesheet))))
    }

    @Test
    fun aQueryFindsLinksByNameAndWord() {
        val links = listOf(bank, timesheet, bandcamp)
        assertEquals(listOf(bandcamp, bank), SavedLinks.search("ban", links, ResultShortcuts.EMPTY).sortedBy { it.name })
        assertEquals(listOf(bandcamp), SavedLinks.search("feed", links, ResultShortcuts.EMPTY))
        assertEquals(listOf(timesheet), SavedLinks.search("Time", links, ResultShortcuts.EMPTY))
        assertEquals(emptyList<SavedLink>(), SavedLinks.search("xyz", links, ResultShortcuts.EMPTY))
        assertEquals(emptyList<SavedLink>(), SavedLinks.search("", links, ResultShortcuts.EMPTY))
    }

    @Test
    fun pinsRankFirstAndNicknamesFindLinks() {
        val links = listOf(bank, bandcamp)
        val pinned = ResultShortcuts.EMPTY.withPinned(ResultKeys.link(bandcamp), bandcamp.name, true)
        assertEquals(bandcamp, SavedLinks.search("ban", links, pinned).first())

        val nicknamed = ResultShortcuts.EMPTY.withNickname(ResultKeys.link(timesheet), timesheet.name, "hours")
        assertEquals(listOf(timesheet), SavedLinks.search("hou", listOf(bank, timesheet), nicknamed))
    }

    @Test
    fun aLinkIsAResultThatCanBePinnedAndNamed() {
        assertEquals("link:https://bank.example.com/", ResultKeys.link(bank))
        assertEquals(ResultKind.LINK, ResultKeys.kindOf(ResultKeys.link(bank)))
        // Pins and nicknames on links survive the shortcuts' own round trip.
        val shortcuts = ResultShortcuts.EMPTY.withPinned(ResultKeys.link(bank), "Bank", true).withTrigger(ResultKeys.link(bank), "Bank", "bk")
        assertEquals(shortcuts, ResultShortcuts.fromJson(shortcuts.toJson()))
    }

    @Test
    fun theStoreKeepsOneLinkPerAddress() {
        val store = SavedLinkStore(settings)
        store.update { it + bank + timesheet + bank.copy(name = "Bank Again") }
        assertEquals(listOf(bank, timesheet), store.current)
        assertEquals(timesheet, store.forKey(ResultKeys.link(timesheet)))
        store.update { it - bank }
        assertEquals(listOf(timesheet), store.current)
    }

    @Test
    fun linksTravelInBackups() = runBlocking {
        assertEquals("STRING", BackupPreferenceSchema.expectedType(SettingsManager.SAVED_LINKS.name))
        val store = SavedLinkStore(settings)
        store.update { listOf(bank, timesheet) }
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val backups = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "links.json"))
        backups.exportToFile(uri, "correct horse battery").getOrThrow()

        store.update { emptyList() }
        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(listOf(bank, timesheet), store.current)
        db.close()
    }

    @Test
    fun anIconIsFetchedOnlyWhenAskedAndNeverUnderTor() = kotlinx.coroutines.runBlocking {
        val asked = mutableListOf<String>()
        val png = run {
            val bitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
            java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
        val icons = SavedLinkIcons(File(context.cacheDir, "icons"), HttpsGet { url, _, _ -> asked += url; png }, settings)
        assertFalse(icons.load(bank.url) != null)
        assertTrue(asked.isEmpty())

        val before = icons.changes.value
        assertTrue(icons.fetch(bank.url))
        assertEquals(listOf("https://bank.example.com/favicon.ico"), asked)
        // Shown at once from memory, and from disk after that.
        assertTrue(icons.peek(bank.url) != null)
        assertTrue(icons.load(bank.url) != null)
        assertTrue("a fetch tells the rows to read again", icons.changes.value > before)
        icons.delete(bank.url)
        assertFalse(icons.peek(bank.url) != null)
        assertFalse(icons.load(bank.url) != null)

        settings.prefs.edit().put(SettingsManager.SEARCH_ENGINE, "Tor Project").commit()
        asked.clear()
        assertFalse(icons.fetch(timesheet.url))
        assertTrue("nothing is fetched under Tor", asked.isEmpty())
    }
}
