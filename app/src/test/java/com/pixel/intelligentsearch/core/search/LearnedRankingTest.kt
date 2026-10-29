package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ResultShortcutStore
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Search ranks what the user keeps opening: picks for what was typed, contact and file opens, starred contacts. */
@RunWith(RobolectricTestRunner::class)
class LearnedRankingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsManager(context)
    private val prefs = context.getSharedPreferences("learned_ranking_test", Context.MODE_PRIVATE)
    private var now = System.currentTimeMillis()
    private val memory = SelectionMemory(store = { prefs }, clock = { now })

    private fun app(name: String, pkg: String) =
        AppItem(name = name, packageName = pkg, icon = ColorDrawable(), activityName = "$pkg.Main")

    private fun contact(name: String, key: String, starred: Boolean = false) =
        ContactItem(name, "555 01${key.hashCode().mod(100)}", "content://com.android.contacts/contacts/lookup/$key/1", starred = starred)

    private val camera = app("Camera", "com.google.android.GoogleCamera")
    private val calendar = app("Calendar", "com.google.android.calendar")
    private val calculator = app("Calculator", "com.google.android.calculator")

    private fun coordinator() = UnifiedSearchCoordinator(
        context,
        SystemActionRouter(context, SystemToggleManager(context)),
        MultiProfileManager(context),
        settings,
        memory
    ).apply { bulkIndexApps(listOf(camera, calendar, calculator)) }

    @Before fun clear() {
        prefs.edit().clear().commit()
        settings.set(SettingsManager.RESULT_SHORTCUTS, "[]")
    }

    @Test
    fun theAppPickedForWhatWasTypedComesFirst() {
        val coordinator = coordinator()
        // The shortest name starting "ca" leads before anything is learned.
        assertEquals("Camera", coordinator.executeSearchSync("ca").apps.first().name)

        memory.record(ResultKeys.app(calculator), "ca")
        assertEquals("Calculator", coordinator.executeSearchSync("ca").apps.first().name)
        assertEquals("Calculator", coordinator.executeTier0Search("ca").apps.first().name)
        // Typing more of what it was picked after keeps it first.
        assertEquals("Calculator", coordinator.executeSearchSync("calc").apps.first().name)
    }

    @Test
    fun aPickForOtherTextChangesNothing() {
        val coordinator = coordinator()
        memory.record(ResultKeys.app(calculator), "math")
        assertEquals("Camera", coordinator.executeSearchSync("ca").apps.first().name)
    }

    @Test
    fun aPinStillBeatsTheStrongestHabit() {
        val coordinator = coordinator()
        repeat(30) { memory.record(ResultKeys.app(calculator), "ca") }
        ResultShortcutStore(settings).update { it.withPinned(ResultKeys.app(calendar), calendar.name, true) }
        assertEquals("Calendar", coordinator.executeSearchSync("ca").apps.first().name)
    }

    @Test
    fun aMuchOpenedContactLeadsEvenBeyondTheFirstAlphabeticalMatches() {
        val coordinator = coordinator()
        // Thirty contacts starting "J" ahead of Julia alphabetically, each a closer match for "j" (a shorter name), and
        // only 20 contacts are shown.
        val others = (1..30).map { contact("Ja%02d Person".format(it), "ja$it") }
        val julia = contact("Julia Zedmontgomery", "julia")
        coordinator.replaceContacts(others + julia)
        assertTrue(coordinator.executeSearchSync("j").contacts.none { it.name == julia.name })

        repeat(5) { memory.record(ResultKeys.contact(julia)!!, null) }
        assertEquals(julia.name, coordinator.executeSearchSync("j").contacts.first().name)
    }

    @Test
    fun aStarredContactLeadsAnEqualMatch() {
        val coordinator = coordinator()
        coordinator.replaceContacts(listOf(contact("Sam Adams", "adams"), contact("Sam Brown", "brown", starred = true)))
        assertEquals("Sam Brown", coordinator.executeSearchSync("sam").contacts.first().name)
    }

    @Test
    fun aFileOpenedLatelyLeadsOneChangedLately() {
        val coordinator = coordinator()
        val report = FileItem("Report draft.pdf", "/Documents", "application/pdf", "content://files/1", lastModifiedMs = now - 60L * 24 * 60 * 60 * 1000)
        val receipt = FileItem("Receipt.pdf", "/Download", "application/pdf", "content://files/2", lastModifiedMs = now - 60 * 60 * 1000)
        coordinator.replaceFiles(listOf(report, receipt))
        assertEquals("Receipt.pdf", coordinator.executeSearchSync("re").files.first().name)

        memory.record(ResultKeys.file(report), null)
        assertEquals("Report draft.pdf", coordinator.executeSearchSync("re").files.first().name)
    }

    @Test
    fun rememberedResultsStillHaveToMatch() {
        val coordinator = coordinator()
        repeat(10) { memory.record(ResultKeys.app(calculator), null) }
        assertTrue(coordinator.executeSearchSync("camera").apps.none { it.name == "Calculator" })
    }
}
