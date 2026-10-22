package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import org.junit.Assert.assertEquals
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
}
