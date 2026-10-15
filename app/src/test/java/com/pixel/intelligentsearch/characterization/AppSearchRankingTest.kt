package com.pixel.intelligentsearch.characterization

import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.search.SystemActionRouter
import com.pixel.intelligentsearch.core.search.UnifiedSearchCoordinator
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Instant (tier 0) app matching for short prefixes, including the package-name crowding regression. */
@RunWith(RobolectricTestRunner::class)
class AppSearchRankingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun coordinatorWith(apps: List<AppItem>): UnifiedSearchCoordinator {
        val coordinator = UnifiedSearchCoordinator(
            context,
            SystemActionRouter(context, SystemToggleManager(context)),
            MultiProfileManager(context),
            SettingsManager(context)
        )
        coordinator.bulkIndexApps(apps)
        return coordinator
    }

    private fun app(name: String, pkg: String) = AppItem(name = name, packageName = pkg, icon = ColorDrawable())

    @Test
    fun shortPrefixFindsAppsByNameDespiteManyComPackages() {
        // 60 apps whose package starts with "com." but whose names don't start with "c".
        val filler = (1..60).map { app("Zed App $it", "com.filler.app$it") }
        val coordinator = coordinatorWith(
            filler + app("Camera", "com.google.android.GoogleCamera") + app("Contacts", "com.google.android.contacts")
        )

        val names = coordinator.executeTier0Search("c").apps.map { it.name }
        assertTrue(names.toString(), "Camera" in names)
        assertTrue(names.toString(), "Contacts" in names)
        assertFalse(names.toString(), names.any { it.startsWith("Zed") })
    }

    @Test
    fun packageNameMatchesOnceTheQueryLooksLikeOne() {
        val coordinator = coordinatorWith(
            listOf(app("Spotify", "com.spotify.music"), app("Maps", "com.google.android.apps.maps"))
        )
        assertEquals(emptyList<String>(), coordinator.executeTier0Search("com").apps.map { it.name })
        assertTrue("Spotify" in coordinator.executeTier0Search("com.spotify").apps.map { it.name })
    }
}
