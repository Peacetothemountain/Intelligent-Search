package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ProfileType
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What is left on screen when the Private Space locks: nothing of its apps, App Info rows included. */
@RunWith(RobolectricTestRunner::class)
class PrivateSpaceLockTest {

    private val chrome = AppItem("Chrome", "com.android.chrome", ColorDrawable(0))
    private val privateChrome = AppItem("Chrome", "com.android.chrome", ColorDrawable(0), profileType = ProfileType.PRIVATE)
    private val privateNotes = AppItem("Notes", "com.example.notes", ColorDrawable(0), isPrivateProfile = true)

    private fun appInfo(app: AppItem, profile: Int) = AndroidSettingResult(
        "app_info:${app.packageName}:$profile", "${app.name} App Info", "Settings › Apps", "app_info", Intent(), app, isStrongMatch = true
    )

    @Test
    fun lockingThePrivateSpaceDropsItsAppsAndTheirAppInfoRows() {
        val battery = AndroidSettingResult("battery", "Battery", "Settings", "battery", Intent())
        val state = SearchUiState(
            query = "app info chrome",
            allApps = listOf(chrome, privateChrome, privateNotes),
            recentApps = listOf(privateNotes, chrome),
            filteredApps = listOf(privateChrome, chrome),
            androidSettings = listOf(appInfo(chrome, 0), appInfo(privateChrome, 11), appInfo(privateNotes, 11), battery)
        )

        val locked = state.withoutPrivateProfile()

        assertEquals(listOf(chrome), locked.allApps)
        assertEquals(listOf(chrome), locked.recentApps)
        assertEquals(listOf(chrome), locked.filteredApps)
        // The personal Chrome's App Info and the Settings pages stay; the private apps' App Info rows go.
        assertEquals(listOf(appInfo(chrome, 0), battery), locked.androidSettings)
        assertEquals("app info chrome", locked.query)
    }
}
