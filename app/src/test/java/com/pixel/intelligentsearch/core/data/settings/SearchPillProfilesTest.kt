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

    /** Max Quick Panel Apps caps the panel; an app just added to Quick Launch must not be the one left out. */
    @Test
    fun addingAppsPastTheMaxRaisesItSoTheyShow() {
        assertEquals(6, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
        profiles.setPills(defaults + listOf("g", "h"))
        assertEquals(8, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    @Test
    fun removingOrReorderingAppsLeavesTheMaxAlone() {
        prefs.edit().put(SettingsManager.SHORTCUT_RESULTS_COUNT, 3).commit()
        profiles.setPills(defaults.reversed())
        profiles.setPills(defaults.take(2))
        assertEquals(3, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    @Test
    fun aMaxTheUserLoweredHoldsUntilAnAppIsAdded() {
        prefs.edit().put(SettingsManager.SHORTCUT_RESULTS_COUNT, 2).commit()
        profiles.setPills(defaults.take(4))
        assertEquals(2, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
        profiles.setPills(defaults.take(5))
        assertEquals(5, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    /** Only adding apps raises it: switching profiles back and forth keeps a max the user chose. */
    @Test
    fun switchingProfilesKeepsTheMax() {
        profiles.switchTo(2)
        profiles.setPills((1..9).map { "p$it" })
        assertEquals(9, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
        prefs.edit().put(SettingsManager.SHORTCUT_RESULTS_COUNT, 4).commit()
        profiles.switchTo(1)
        profiles.switchTo(2)
        assertEquals(4, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    /** With Context Aware Quick Apps on the panel shows recent apps, so editing the chosen list changes nothing there. */
    @Test
    fun contextAwarePanelsKeepTheirMax() {
        prefs.edit().put(SettingsManager.CONTEXT_AWARE_QUICK_APPS, true).commit()
        profiles.setPills(defaults + listOf("g", "h"))
        assertEquals(6, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    @Test
    fun aRestoredBackupShowsInTheActiveProfile() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "r1,r2").putInt("active_app_search_profile", 4).commit()
        assertEquals(4, profiles.activeProfile)
        assertEquals(listOf("r1", "r2"), profiles.pills())
    }

    /** A lower Max Quick Panel Apps removes the apps past it from the bottom of the list, and only those. */
    @Test
    fun aLowerMaxRemovesTheLastAppsFromTheBottomUp() {
        profiles.setPills(listOf("a", "b", "c", "d"))
        assertEquals(listOf("c", "d"), profiles.appsRemovedByMax(2))
        assertEquals(emptyList<String>(), profiles.appsRemovedByMax(4))

        profiles.setMaxQuickPanelApps(2)
        assertEquals(listOf("a", "b"), profiles.pills())
        assertEquals(2, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])

        // Raising it again brings nothing back and removes nothing.
        profiles.setMaxQuickPanelApps(5)
        assertEquals(listOf("a", "b"), profiles.pills())
        assertEquals(5, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    /** With Context Aware Quick Apps on the max caps recent apps, so the chosen apps are left alone. */
    @Test
    fun contextAwareQuickAppsKeepsTheChosenApps() {
        profiles.setPills(listOf("a", "b", "c"))
        prefs.edit().put(SettingsManager.CONTEXT_AWARE_QUICK_APPS, true).commit()
        assertEquals(emptyList<String>(), profiles.appsRemovedByMax(1))
        profiles.setMaxQuickPanelApps(1)
        assertEquals(listOf("a", "b", "c"), profiles.pills())
        assertEquals(1, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }
}
