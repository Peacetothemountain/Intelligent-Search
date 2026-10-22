package com.pixel.intelligentsearch.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.settings.put
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Max Quick Panel Apps (the old Max Shortcut Suggestions) used to cap only recent apps; every chosen Quick Launch app
 * showed. Now it caps both, so an install that chose more apps than its max gets the max raised once, and the panel
 * looks the same after the update.
 */
@RunWith(RobolectricTestRunner::class)
class QuickPanelCountMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val count get() = SettingsManager(context)[SettingsManager.SHORTCUT_RESULTS_COUNT]
    private val eightApps = (1..8).joinToString(",") { "com.example.app$it" }

    @Before fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun moreChosenAppsThanTheMaxRaiseIt() = runBlocking {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, eightApps).commit()

        SettingsManager(context).migrateToSingleStore()

        assertEquals(8, count)
    }

    @Test
    fun aMaxThatAlreadyFitsIsUntouched() = runBlocking {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a,b,c").put(SettingsManager.SHORTCUT_RESULTS_COUNT, 4).commit()

        SettingsManager(context).migrateToSingleStore()

        assertEquals(4, count)
    }

    @Test
    fun contextAwarePanelsAlreadyFollowedTheMax() = runBlocking {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, eightApps).put(SettingsManager.CONTEXT_AWARE_QUICK_APPS, true).commit()

        SettingsManager(context).migrateToSingleStore()

        assertEquals(6, count)
    }

    @Test
    fun aFreshInstallWritesNoMax() = runBlocking {
        SettingsManager(context).migrateToSingleStore()

        assertFalse(prefs.contains(SettingsManager.SHORTCUT_RESULTS_COUNT.name))
        assertEquals(6, count)
    }

    @Test
    fun itRunsOnlyOnce() = runBlocking {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, eightApps).commit()
        val settings = SettingsManager(context)
        settings.migrateToSingleStore()
        assertEquals(8, count)

        // A max lowered after the update stays lowered.
        settings.set(SettingsManager.SHORTCUT_RESULTS_COUNT, 3)
        prefs.edit().commit() // flush the apply()
        settings.migrateToSingleStore()
        SettingsManager(context).migrateToSingleStore()

        assertEquals(3, count)
    }
}
