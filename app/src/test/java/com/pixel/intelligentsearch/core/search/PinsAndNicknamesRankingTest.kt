package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ResultShortcutStore
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pinned results rank first when they match; nicknames find their results; nothing else moves. */
@RunWith(RobolectricTestRunner::class)
class PinsAndNicknamesRankingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsManager(context)
    private val store = ResultShortcutStore(settings)

    private fun app(name: String, pkg: String) =
        AppItem(name = name, packageName = pkg, icon = ColorDrawable(), activityName = "$pkg.Main")

    private val chrome = app("Chrome", "com.android.chrome")
    private val chess = app("Chess", "com.chess")
    private val cheddar = app("Cheddar Bank", "com.cheddar.bank")
    private val john = ContactItem("John Smith", "555 0100", "content://com.android.contacts/contacts/lookup/john/1")
    private val joan = ContactItem("Joan", "555 0101", "content://com.android.contacts/contacts/lookup/joan/2")
    private val notes = FileItem("Notes.txt", "/Documents", "text/plain", "content://files/1")
    private val notebook = FileItem("Notebook.pdf", "/Documents", "application/pdf", "content://files/2")

    private fun coordinator() = UnifiedSearchCoordinator(
        context,
        SystemActionRouter(context, SystemToggleManager(context)),
        MultiProfileManager(context),
        settings
    ).apply {
        bulkIndexApps(listOf(chrome, chess, cheddar))
        replaceContacts(listOf(john, joan))
        replaceFiles(listOf(notes, notebook))
    }

    @Before fun clear() = settings.set(SettingsManager.RESULT_SHORTCUTS, "[]")

    @Test
    fun aPinnedAppRanksFirstEvenAboveAMuchUsedOne() {
        val coordinator = coordinator()
        coordinator.updateAppUsage(mapOf(chrome.packageName to SystemDataProvider.AppUsage(14, System.currentTimeMillis())))
        assertEquals("Chrome", coordinator.searchApps("ch").first().name)

        store.update { it.withPinned(ResultKeys.app(cheddar), cheddar.name, true) }
        assertEquals("Cheddar Bank", coordinator.searchApps("ch").first().name)
        assertEquals("Cheddar Bank", coordinator.executeTier0Search("ch").apps.first().name)
        assertEquals("Cheddar Bank", coordinator.executeSearchSync("ch").apps.first().name)
    }

    @Test
    fun aPinnedPrefixMatchBeatsAnExactName() {
        val coordinator = coordinator()
        val chessboard = app("Chess Board", "com.chessboard")
        coordinator.bulkIndexApps(listOf(chrome, chess, cheddar, chessboard))
        assertEquals("Chess", coordinator.searchApps("chess").first().name)
        store.update { it.withPinned(ResultKeys.app(chessboard), chessboard.name, true) }
        assertEquals("Chess Board", coordinator.searchApps("chess").first().name)
    }

    @Test
    fun aPinnedResultThatDoesntMatchStaysOut() {
        val coordinator = coordinator()
        store.update { it.withPinned(ResultKeys.app(cheddar), cheddar.name, true) }
        assertTrue(coordinator.searchApps("zzz").isEmpty())
        assertEquals(emptyList<String>(), coordinator.executeSearchSync("joan").apps.map { it.name })
    }

    @Test
    fun pinnedContactsAndFilesLeadTheirSections() {
        val coordinator = coordinator()
        assertEquals("Notes.txt", coordinator.executeSearchSync("no").files.first().name)
        store.update { it.withPinned(ResultKeys.contact(john)!!, john.name, true).withPinned(ResultKeys.file(notebook), notebook.name, true) }
        val results = coordinator.executeSearchSync("no")
        assertEquals("Notebook.pdf", results.files.first().name)
        assertEquals("John Smith", coordinator.executeSearchSync("jo").contacts.first().name)
    }

    @Test
    fun unpinnedResultsKeepTheirOrder() {
        val coordinator = coordinator()
        val before = coordinator.executeSearchSync("ch").apps.map { it.name }
        store.update { it.withPinned(ResultKeys.contact(john)!!, john.name, true).withNickname(ResultKeys.contact(joan)!!, joan.name, "mum") }
        assertEquals(before, coordinator.executeSearchSync("ch").apps.map { it.name })
    }

    @Test
    fun aNicknameFindsItsResultWholeOrByItsStart() {
        val coordinator = coordinator()
        assertTrue(coordinator.searchContacts("dad").isEmpty())
        store.update { it.withNickname(ResultKeys.contact(john)!!, john.name, "Dad") }
        assertEquals(listOf(john), coordinator.searchContacts("dad"))
        assertEquals(john, coordinator.executeSearchSync("da").contacts.first())
        // Not by a typo or by the middle of it.
        assertTrue(coordinator.searchContacts("ad").isEmpty())

        store.update { it.withNickname(ResultKeys.app(cheddar), cheddar.name, "bank") }
        assertEquals("Cheddar Bank", coordinator.executeTier0Search("bank").apps.first().name)
        assertEquals("Cheddar Bank", coordinator.searchApps("ban").first().name)
    }

    @Test
    fun aMultiWordNicknameIsFoundToo() {
        val coordinator = coordinator()
        store.update { it.withNickname(ResultKeys.app(chess), chess.name, "my games") }
        assertEquals(listOf("Chess"), coordinator.executeSearchSync("my games").apps.map { it.name })
    }

    @Test
    fun aNicknameOutranksAnotherResultsTitle() {
        val coordinator = coordinator()
        // "chess" is Chess's whole name, but a nickname the user chose means that result more surely.
        store.update { it.withNickname(ResultKeys.app(chrome), chrome.name, "chess") }
        assertEquals("Chrome", coordinator.searchApps("chess").first().name)
    }

    @Test
    fun callingANicknameFindsTheContact() {
        settings.set(SettingsManager.SEARCH_CONTACTS, true)
        val coordinator = coordinator()
        store.update { it.withNickname(ResultKeys.contact(john)!!, john.name, "dad") }
        val intent = coordinator.executeSearchSync("call dad").parsedIntent
        assertTrue("$intent", intent is NaturalLanguageIntentParser.ParsedIntent.PhoneCall)
        assertEquals("555 0100", (intent as NaturalLanguageIntentParser.ParsedIntent.PhoneCall).resolvedPhoneNumber)
        settings.set(SettingsManager.SEARCH_CONTACTS, false)
    }

    @Test
    fun indexedResultsAreFoundByTheirKey() {
        val coordinator = coordinator()
        assertEquals(chess, coordinator.indexedResult(ResultKeys.app(chess)))
        assertEquals(john, coordinator.indexedResult(ResultKeys.contact(john)!!))
        assertEquals(notes, coordinator.indexedResult(ResultKeys.file(notes)))
        coordinator.replaceFiles(emptyList())
        assertNull(coordinator.indexedResult(ResultKeys.file(notes)))
        assertEquals(chess, coordinator.indexedResult(ResultKeys.app(chess)))
    }
}
