package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.data.settings.forgetHeldListeners
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The in-memory search as the overlay calls it: which contacts and apps a query reaches, and in what order. */
@RunWith(RobolectricTestRunner::class)
class UnifiedSearchCoordinatorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun coordinator() = UnifiedSearchCoordinator(
        context,
        SystemActionRouter(context, SystemToggleManager(context)),
        MultiProfileManager(context),
        SettingsManager(context)
    )

    private fun app(name: String, pkg: String) = AppItem(name = name, packageName = pkg, icon = ColorDrawable())

    @Test
    fun anInternationalNumberFindsAContactSavedInNationalFormat() {
        val alex = ContactItem("Alex", "(415) 555-2671", "content://contacts/alex")
        val coordinator = coordinator().apply { replaceContacts(listOf(alex)) }
        assertEquals(listOf(alex), coordinator.executeSearchSync("+14155552671").contacts)
        assertEquals(listOf(alex), coordinator.executeSearchSync("+1 415 555 2671").contacts)
    }

    @Test
    fun aNationalNumberStillFindsAContactSavedInInternationalFormat() {
        val alex = ContactItem("Alex", "+1 415-555-2671", "content://contacts/alex")
        val coordinator = coordinator().apply { replaceContacts(listOf(alex)) }
        assertEquals(listOf(alex), coordinator.executeSearchSync("4155552671").contacts)
        assertEquals(listOf(alex), coordinator.executeSearchSync("(415) 555-2671").contacts)
    }

    @Test
    fun aTrunkPrefixedNumberMatchesTheSameLineWrittenInternationally() {
        // UK numbers are saved with a leading 0 and typed with +44; the last ten digits are the same line.
        val sam = ContactItem("Sam", "07700 900123", "content://contacts/sam")
        val coordinator = coordinator().apply { replaceContacts(listOf(sam)) }
        assertEquals(listOf(sam), coordinator.executeSearchSync("+447700900123").contacts)
    }

    @Test
    fun aChanceAnswerIsKeptUntilTheQueryChanges() {
        // Moving the cursor, a pin change or the index finishing runs the same query again; the draw used to change.
        val coordinator = coordinator()
        val query = "random 1 to 999999999999"
        val drawn = coordinator.executeSearchSync(query).randomAnswer
        assertNotNull(drawn)
        repeat(5) { assertEquals(drawn, coordinator.executeSearchSync(query).randomAnswer) }
        assertEquals(drawn, coordinator.executeSearchSync("$query ").randomAnswer)

        // Changing the text draws again: typing it anew is a new draw.
        val redrawn = (1..3).map {
            coordinator.executeSearchSync("random 1 to 10000000000").randomAnswer
            coordinator.executeSearchSync(query).randomAnswer
        }
        assertTrue("a changed query draws again", redrawn.any { it != drawn })
    }

    @Test
    fun clearingTheFieldLetsTheSameChanceQuestionLandAnew() {
        // The coordinator outlives the overlay: flipping again after clearing the field used to land the same way.
        val coordinator = coordinator()
        val query = "random 1 to 999999999999"
        val afterEmptyQuery = (1..4).map {
            coordinator.executeSearchSync("")
            coordinator.executeSearchSync(query).randomAnswer
        }
        assertTrue("a cleared field draws again: $afterEmptyQuery", afterEmptyQuery.toSet().size > 1)

        // The overlay clears the field without searching it, and calls this directly.
        val afterForgetting = (1..4).map {
            coordinator.forgetChanceDraw()
            coordinator.executeSearchSync(query).randomAnswer
        }
        assertTrue("a forgotten draw draws again: $afterForgetting", afterForgetting.toSet().size > 1)
    }

    @Test
    fun aShortPrefixRanksTheMostUsedAppFirstHoweverManyOthersShareIt() {
        // More apps start with "Sa" than the candidate lookup used to keep, and they were indexed first.
        val filler = (1..45).map { app("Sa App $it", "org.example.sa$it") }
        val coordinator = coordinator()
        coordinator.bulkIndexApps(filler + app("Spotify", "com.spotify.music"))
        coordinator.updateAppUsage(
            mapOf("com.spotify.music" to SystemDataProvider.AppUsage(daysUsed = 14, lastUsedMs = System.currentTimeMillis()))
        )

        assertEquals("Spotify", coordinator.executeTier0Search("s").apps.first().name)
        assertEquals("Spotify", coordinator.executeSearchSync("s").apps.first().name)
    }

    @Test
    fun aNumberFindsNoAppItIsOneDigitFrom() {
        val coordinator = coordinator()
        coordinator.bulkIndexApps(listOf(app("2048", "com.example.game2048"), app("Calculator", "com.example.calc")))
        assertEquals(emptyList<String>(), coordinator.executeSearchSync("210").apps.map { it.name })
        assertEquals(emptyList<String>(), coordinator.executeTier0Search("210").apps.map { it.name })
        // The number itself, or its start, still finds it; and a name with a digit in it keeps its slips.
        assertEquals("2048", coordinator.executeSearchSync("204").apps.first().name)
        assertEquals("Calculator", coordinator.executeSearchSync("calcul8").apps.first().name)
        // Longer words keep their typo tolerance.
        assertEquals("Calculator", coordinator.executeSearchSync("calculater").apps.first().name)
    }

    @Test
    fun aQueryWithANumberInItMatchesNoContactByTheSoundOfItsWords() {
        // Sounds are read from letters only: "call 5551234567" sounded like "Kyle", "$50 to eur" like "Dr.".
        val kyle = ContactItem("Kyle Shotwell", "(212) 555-0100", "content://contacts/kyle")
        val office = ContactItem("Dr. Miller's Office", "(212) 555-0199", "content://contacts/office")
        val coordinator = coordinator().apply { replaceContacts(listOf(kyle, office)) }
        assertEquals(emptyList<ContactItem>(), coordinator.executeSearchSync("call 5551234567").contacts)
        assertEquals(emptyList<ContactItem>(), coordinator.executeSearchSync("$50 to eur").contacts)
        // Without a number, a name that sounds the same is still found.
        assertEquals(listOf(kyle), coordinator.executeSearchSync("kile").contacts)
    }

    @Test
    fun aCallOrMessageGoesOnlyToTheOneContactTheWordsName() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(SettingsManager.SEARCH_CONTACTS.name, true).commit()
        val coordinator = coordinator().apply {
            replaceContacts(listOf(
                ContactItem("Dad", "+14155550214", "content://c/214"), ContactItem("Dad Work", "+14155550215", "content://c/215"),
                ContactItem("Sarah Connor", "+14155550230", "content://c/230"), ContactItem("Sarah Lee", "+14155550231", "content://c/231")
            ))
        }
        fun action(query: String) = when (val parsed = coordinator.executeSearchSync(query).parsedIntent) {
            is NaturalLanguageIntentParser.ParsedIntent.PhoneCall -> "Call ${parsed.contactNameOrNumber}"
            is NaturalLanguageIntentParser.ParsedIntent.DirectMessage -> "Message ${parsed.recipientName}: ${parsed.body}"
            else -> null
        }
        // With two Sarahs, these went to whichever ranked first: Sarah Lee, "c hi" as the words.
        assertNull(action("call sarah"))
        assertNull(action("text sarah running late"))
        assertEquals("Message Sarah Connor: hi", action("text sarah c hi"))
        assertEquals("Call Sarah Connor", action("call sarah c"))
        // Both Sarahs are still found.
        assertEquals(2, coordinator.executeSearchSync("sarah").contacts.size)
        // A whole name beats one it only starts.
        assertEquals("Call Dad", action("call dad"))
        assertEquals("Call Dad Work", action("call dad work"))
        assertEquals("Call Sarah Lee", action("call sarah lee"))
    }

    /** "Call <name> <number>" or "Message <name> <number>", or null when the query offers neither. */
    private fun UnifiedSearchCoordinator.action(query: String): String? = when (val parsed = executeSearchSync(query).parsedIntent) {
        is NaturalLanguageIntentParser.ParsedIntent.PhoneCall -> "Call ${parsed.contactNameOrNumber} ${parsed.resolvedPhoneNumber}"
        is NaturalLanguageIntentParser.ParsedIntent.DirectMessage -> "Message ${parsed.recipientName}: ${parsed.body}"
        else -> null
    }

    private fun contactSearch(on: Boolean) {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(SettingsManager.SEARCH_CONTACTS.name, on).commit()
    }

    @Test
    fun theContactsSwitchReachesCallAndMessageRowsWithoutAListenerToKeep() {
        // In the release build the listener that refreshed a cached switch was garbage collected: "call zelda" kept
        // the switch as it was when the app started, still naming contacts after Contacts was turned off.
        contactSearch(on = true)
        val coordinator = coordinator().apply { replaceContacts(listOf(ContactItem("Zelda Auditson", "+14155550600", "content://c/600"))) }
        forgetHeldListeners(coordinator)
        assertEquals("Call Zelda Auditson +14155550600", coordinator.action("call zelda"))

        contactSearch(on = false)
        assertNull("Contacts off: no contact is looked up", coordinator.action("call zelda"))
        assertNull(coordinator.action("text zelda on my way"))

        contactSearch(on = true)
        assertEquals("Call Zelda Auditson +14155550600", coordinator.action("call zelda"))
        assertEquals("Message Zelda Auditson: on my way", coordinator.action("text zelda on my way"))
    }

    @Test
    fun aNamesakeRankedBelowOtherMatchesStillMakesTheNameAmbiguous() {
        // Nine "Sam …" contacts kept only for their email ranked above both callable Sams, and only the top ten were
        // checked: Sam Lee fell out and "call sam" called Sam Wu.
        contactSearch(on = true)
        for (emailOnlyCount in listOf(9, 10, 30)) {
            val emailOnly = (1..emailOnlyCount).map { ContactItem("Sam E$it", "", "content://c/e$it", email = "sam$it@example.com") }
            val coordinator = coordinator().apply {
                replaceContacts(emailOnly + listOf(
                    ContactItem("Sam Lee", "+14155550301", "content://c/301"), ContactItem("Sam Wu", "+14155550302", "content://c/302")
                ))
            }
            assertNull("$emailOnlyCount email-only Sams: two callable Sams", coordinator.action("call sam"))
            assertNull(coordinator.action("text sam see you soon"))
            assertEquals("Call Sam Wu +14155550302", coordinator.action("call sam wu"))
            assertEquals("Call Sam Lee +14155550301", coordinator.action("call sam l"))
        }
    }

    @Test
    fun theRowNamesTheContactItCalls() {
        contactSearch(on = true)
        val coordinator = coordinator().apply {
            replaceContacts(listOf(
                ContactItem("Dad", "+14155550214", "content://c/214"), ContactItem("Dad Work", "+14155550215", "content://c/215"),
                ContactItem("Sarah Connor", "+14155550230", "content://c/230"), ContactItem("Sarah Lee", "+14155550231", "content://c/231"),
                ContactItem("Björn Berg", "+46700000001", "content://c/b1"), ContactItem("Bjørn", "+47900000001", "content://c/b2")
            ))
        }
        assertEquals("Call Dad +14155550214", coordinator.action("call dad"))
        assertEquals("Call Dad Work +14155550215", coordinator.action("call dad work"))
        assertEquals("Call Sarah Connor +14155550230", coordinator.action("call sarah c"))
        assertEquals("Call Sarah Lee +14155550231", coordinator.action("call lee"))
        assertEquals("Call Bjørn +47900000001", coordinator.action("call bjorn"))
        assertNull(coordinator.action("call sarah"))
        assertNull(coordinator.action("text sarah hi"))
        // The search itself still lists both, cut to its count as before.
        assertEquals(2, coordinator.searchContacts("sarah").size)
    }

    @Test
    fun aCallToOneOfManyMatchesStillFindsTheWholeName() {
        // Twelve "Mom …" contacts above the old cut of ten: the one named just "Mom" is still the one meant.
        contactSearch(on = true)
        val coordinator = coordinator().apply {
            replaceContacts((1..12).map { ContactItem("Mom E$it", "+1415555${1000 + it}", "content://c/m$it", starred = true) } +
                ContactItem("Mom", "+14155550400", "content://c/400"))
        }
        assertEquals("Call Mom +14155550400", coordinator.action("call mom"))
    }

    @Test
    fun accentsAndExtraSpacesFindTheApp() {
        val coordinator = coordinator()
        coordinator.bulkIndexApps(listOf(app("Café Finder", "com.example.cafe"), app("Google Maps", "com.google.maps")))
        assertEquals("Café Finder", coordinator.executeSearchSync("cafe").apps.first().name)
        assertEquals("Google Maps", coordinator.executeSearchSync("google  maps").apps.first().name)
    }
}
