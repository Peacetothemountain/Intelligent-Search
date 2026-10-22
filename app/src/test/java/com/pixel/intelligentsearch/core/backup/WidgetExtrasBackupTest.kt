package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.core.data.widget.CountdownEventStore
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.data.widget.WidgetThemeStore
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * The widget's favourite apps, outline, corners and saved themes are settings like any other: a backup carries them
 * back (the media row only ever off, countdown events not at all), and a backup can't plant an outline or corner the
 * sliders couldn't set.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetExtrasBackupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
    private lateinit var backup: BackupManager
    private val uri get() = Uri.fromFile(File(context.cacheDir, "widget_extras.json"))
    private val defaults = WidgetDefaults(0xFF6750A4.toInt(), isDynamicColorActive = true)

    @Before fun setUp() {
        prefs.edit().clear().commit()
        backup = BackupManager(context, SettingsManager(context), db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
    }

    @After fun tearDown() = db.close()

    @Test
    fun theNewWidgetSettingsRoundTrip() = runBlocking {
        val store = WidgetSettingsStore(prefs)
        val material = store.load(WidgetDesign.MATERIAL, defaults).copy(
            favouriteApps = listOf("com.example.mail", "com.example.maps"),
            showMedia = true,
            outlineThickness = 4,
            cornerRadius = 12
        )
        prefs.edit().also { store.save(WidgetDesign.MATERIAL, material, it) }.commit()
        WidgetThemeStore(prefs).save("Dusk", WidgetDesign.MATERIAL, material)
        CountdownEventStore(prefs).put(5, CountdownEvent("Halloween", LocalDate.of(2026, 10, 31), yearly = true))
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()

        prefs.edit().clear().commit()
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        val restored = store.load(WidgetDesign.MATERIAL, defaults)
        assertEquals(material.favouriteApps, restored.favouriteApps)
        // Media Controls read other apps' media sessions: a restore can't switch them on (RestorePrivacyGuardsTest).
        assertFalse(restored.showMedia)
        assertEquals(4, restored.outlineThickness)
        assertEquals(12, restored.cornerRadius)
        assertEquals(listOf("Dusk"), WidgetThemeStore(prefs).list(defaults).map { it.name })
        // A countdown belongs to a widget placed on this phone, by its id: it stays with the phone.
        assertEquals(null, CountdownEventStore(prefs).get(5))
    }

    @Test
    fun anOutlineOrCornerOutOfRangeIsNotRestored() = runBlocking {
        // Values written straight to storage, as a crafted file would carry them; the sliders can't set them.
        prefs.edit()
            .putInt(SettingsManager.WIDGET_SYSTEM_OUTLINE_THICKNESS.name, 999)
            .putInt(SettingsManager.WIDGET_SYSTEM_CORNER_RADIUS.name, -4)
            .commit()
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()
        prefs.edit().clear().commit()
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertFalse(prefs.contains(SettingsManager.WIDGET_SYSTEM_OUTLINE_THICKNESS.name))
        assertFalse(prefs.contains(SettingsManager.WIDGET_SYSTEM_CORNER_RADIUS.name))
        assertEquals(SettingsManager.WIDGET_SYSTEM_OUTLINE_THICKNESS.default, prefs[SettingsManager.WIDGET_SYSTEM_OUTLINE_THICKNESS])
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery"
    }
}
