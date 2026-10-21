package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which apps the quick-app panel shows: the chosen Quick Launch apps, or recent apps, up to Max Quick Panel Apps. */
class QuickPanelAppsTest {

    private val chosen = "a, b,c,a,,d,e,f,g,h"
    private val recents = listOf("r1", "r2", "r1", "r3", "r4")

    @Test
    fun chosenAppsAreCappedInTheirOrder() {
        assertEquals(listOf("a", "b", "c"), quickPanelApps(contextAware = false, chosenApps = chosen, recentApps = recents, maxApps = 3))
    }

    @Test
    fun recentAppsAreCappedTooWhenContextAware() {
        assertEquals(listOf("r1", "r2"), quickPanelApps(contextAware = true, chosenApps = chosen, recentApps = recents, maxApps = 2))
    }

    @Test
    fun aMaxAboveTheListShowsTheWholeList() {
        assertEquals(listOf("r1", "r2", "r3", "r4"), quickPanelApps(contextAware = true, chosenApps = chosen, recentApps = recents, maxApps = 20))
    }

    @Test
    fun theDefaultsShowEveryDefaultQuickLaunchApp() {
        val defaults = SettingsManager.SEARCH_PILLS.default.split(",")
        assertEquals(6, defaults.size)
        assertEquals(
            defaults,
            quickPanelApps(
                contextAware = SettingsManager.CONTEXT_AWARE_QUICK_APPS.default,
                chosenApps = SettingsManager.SEARCH_PILLS.default,
                recentApps = recents,
                maxApps = SettingsManager.SHORTCUT_RESULTS_COUNT.default
            )
        )
    }
}
