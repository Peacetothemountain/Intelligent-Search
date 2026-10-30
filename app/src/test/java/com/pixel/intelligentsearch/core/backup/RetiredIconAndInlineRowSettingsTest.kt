package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.flow.first
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
 * The icon shape and icon masking settings, which no screen offered or read, and the switch for the Lens, Voice and
 * Assistant rows under results, which had no place in settings, are gone. What an older build stored is dropped at
 * start, and an older backup that carries them still restores, without bringing them back.
 */
@RunWith(RobolectricTestRunner::class)
class RetiredIconAndInlineRowSettingsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val retired = listOf("adaptive_icon_shape", "dynamic_icon_masking", "shortcut.inline", "shortcut_inline")

    private fun storeOldValues(edit: android.content.SharedPreferences.Editor) {
        edit.putString("adaptive_icon_shape", "SQUIRCLE")
            .putBoolean("dynamic_icon_masking", false)
            .putBoolean("shortcut.inline", false)
            .putBoolean("shortcut_inline", false)
    }

    @Test
    fun theSettingsAreGone() {
        for (name in retired) {
            assertNull("$name is not declared", SettingsManager.keyFor(name))
            assertNull("$name has no backup type", BackupPreferenceSchema.expectedType(name))
            assertTrue("$name is dropped and never restored", name in SettingsManager.REMOVED_NAMES)
        }
    }

    @Test
    fun anOlderBuildsValuesAreDroppedAtStart() = runBlocking {
        val settings = SettingsManager(context)
        val edit = settings.prefs.edit()
        storeOldValues(edit)
        edit.commit()
        settings.migrateToSingleStore()
        for (name in retired) assertFalse("$name is dropped", settings.prefs.contains(name))
    }

    @Test
    fun anOlderBackupStillRestoresWithoutThem() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backup = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "retired_icon_settings.json"))

        // A file made by an older build still carries the settings, beside one that is restored.
        val edit = settings.prefs.edit()
        storeOldValues(edit)
        edit.commit()
        settings.updateSetting(SettingsManager.BACKGROUND_BLUR, 33)
        backup.exportToFile(uri, "correct horse battery").getOrThrow()
        val clear = settings.prefs.edit()
        for (name in retired) clear.remove(name)
        clear.commit()
        settings.updateSetting(SettingsManager.BACKGROUND_BLUR, 80)

        backup.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(33, settings.settingsFlow.first().backgroundBlur)
        for (name in retired) assertFalse("$name is not restored", settings.prefs.contains(name))

        db.close()
    }
}
