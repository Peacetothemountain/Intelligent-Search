package com.pixel.intelligentsearch.core.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchPillProfilesTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var profiles: SearchPillProfiles
    private val defaults = SearchPillProfiles.DEFAULT_PILLS.split(",")

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("pills_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        profiles = SearchPillProfiles(prefs)
    }

    /** The page keys its list by package name, so a repeated pill in storage would crash it. */
    @Test
    fun storedPillsAreReadTrimmedAndOnceEach() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a, b,a,,b ").commit()
        assertEquals(listOf("a", "b"), profiles.pills())
    }

    @Test
    fun freshInstallShowsTheDefaultsInProfileOne() {
        assertEquals(1, profiles.activeProfile)
        assertEquals(defaults, profiles.pills())
    }

    @Test
    fun switchingKeepsEachProfilesPills() {
        profiles.setPills(listOf("a", "b"))
        assertEquals(emptyList<String>(), profiles.switchTo(2))
        profiles.setPills(listOf("c"))
        assertEquals(listOf("a", "b"), profiles.switchTo(1))
        assertEquals(listOf("a", "b"), profiles.pills())
        assertEquals(listOf("c"), profiles.switchTo(2))
        assertEquals(2, profiles.activeProfile)
        assertEquals("c", prefs[SettingsManager.SEARCH_PILLS])
    }

    /** Tapping the profile already shown must not swap in its older working copy. */
    @Test
    fun switchingToTheActiveProfileKeepsItsPills() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "r1,r2").putString("app_search_profile_1", "a,b").commit()
        assertEquals(listOf("r1", "r2"), profiles.switchTo(1))
        assertEquals(listOf("r1", "r2"), profiles.pills())
    }

    @Test
    fun resetReturnsToTheSavedState() {
        profiles.setPills(listOf("x"))
        assertEquals(defaults, profiles.resetToSaved())
        profiles.setPills(listOf("y", "z"))
        profiles.saveState(listOf("y", "z"))
        profiles.setPills(emptyList())
        assertEquals(listOf("y", "z"), profiles.resetToSaved())
        assertEquals(listOf("y", "z"), profiles.pills())
    }

    @Test
    fun anUnsavedProfileResetsToEmpty() {
        profiles.switchTo(3)
        profiles.setPills(listOf("q"))
        assertEquals(emptyList<String>(), profiles.resetToSaved())
    }

    @Test
    fun aRestoredBackupShowsInTheActiveProfile() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "r1,r2").putInt("active_app_search_profile", 4).commit()
        assertEquals(4, profiles.activeProfile)
        assertEquals(listOf("r1", "r2"), profiles.pills())
    }
}
