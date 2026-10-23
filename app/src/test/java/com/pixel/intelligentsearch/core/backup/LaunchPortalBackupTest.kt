package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Launch Portal's Notification Shortcut and floating button are backed up, but each needs a permission and the button
 * draws over other apps, so a restore can switch them off and never on. Where the button sits comes back. The removed
 * Edge Swipe's settings are neither known nor backed up.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchPortalBackupTest {

    private val optIns = listOf(SettingsManager.NOTIFICATION_SHORTCUT, SettingsManager.FLOATING_SEARCH_BUTTON)
    private val edgeSwipeNames = listOf("launch.edge_swipe", "launch.edge_swipe_edge", "launch.edge_swipe_position", "launch.edge_swipe_height")

    @Test
    fun theSettingsAreKnownWithTheirTypesAndRanges() {
        for (key in optIns) {
            assertEquals(key.name, "BOOLEAN", BackupPreferenceSchema.expectedType(key.name))
            assertEquals(key, SettingsManager.keyFor(key.name))
        }
        assertEquals("STRING", BackupPreferenceSchema.expectedType(SettingsManager.FLOATING_BUTTON_EDGE.name))
        assertEquals("INT", BackupPreferenceSchema.expectedType(SettingsManager.FLOATING_BUTTON_POSITION.name))
        assertEquals(0..1000, BackupPreferenceSchema.intRange(SettingsManager.FLOATING_BUTTON_POSITION.name))
    }

    @Test
    fun theEdgeSwipesSettingsAreGone() {
        for (name in edgeSwipeNames) {
            assertNull(name, SettingsManager.keyFor(name))
            assertNull(name, BackupPreferenceSchema.expectedType(name))
            assertTrue(name, name in SettingsManager.REMOVED_NAMES)
        }
    }

    @Test
    fun theyAreOffUntilTheUserSwitchesThemOn() {
        for (key in optIns) assertEquals(key.name, false, key.default)
    }

    @Test
    fun aBackupCanSwitchThemOffButNeverOnAndKeepsWhereTheySit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backups = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "launch_portal.json"))

        // Backed up on, with the button on the left, and an older build's Edge Swipe settings still in the store.
        for (key in optIns) settings.updateSetting(key, true)
        settings.updateSetting(SettingsManager.FLOATING_BUTTON_EDGE, "left")
        settings.updateSetting(SettingsManager.FLOATING_BUTTON_POSITION, 250)
        settings.prefs.edit().putBoolean(edgeSwipeNames[0], true).putString(edgeSwipeNames[1], "left").commit()
        backups.exportToFile(uri, "correct horse battery").getOrThrow()

        // Restored onto a phone where they are off and placed elsewhere: they stay off, the places come back.
        for (key in optIns) settings.updateSetting(key, false)
        settings.updateSetting(SettingsManager.FLOATING_BUTTON_EDGE, "right")
        settings.updateSetting(SettingsManager.FLOATING_BUTTON_POSITION, 600)
        settings.prefs.edit().apply { edgeSwipeNames.forEach(::remove) }.commit()
        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) assertEquals(key.name, false, settings[key])
        assertEquals("left", settings[SettingsManager.FLOATING_BUTTON_EDGE])
        assertEquals(250, settings[SettingsManager.FLOATING_BUTTON_POSITION])
        for (name in edgeSwipeNames) assertFalse(name, settings.prefs.contains(name))

        // Backed up off: restoring switches them off.
        backups.exportToFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) settings.updateSetting(key, true)
        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) assertEquals(key.name, false, settings[key])

        db.close()
    }
}
