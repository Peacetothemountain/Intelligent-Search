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
import com.pixel.intelligentsearch.core.data.widget.WidgetProfileDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetProfiles
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
 * The widget's favourite apps, outline, corners and profiles are settings like any other: a backup carries them
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
        val profiles = WidgetProfiles(prefs, defaults)
        val dusk = WidgetProfileDesign(WidgetDesign.MATERIAL, store.load(WidgetDesign.SYSTEM, defaults), material.copy(showMedia = false, cornerRadius = 20))
        profiles.saveState(3, dusk)
        prefs.edit().also { profiles.commit(it, active = 2, left = mapOf(3 to dusk), previousLive = dusk) }.commit()
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
        // The profiles come back: which one is on the widget, each one's design and the state its Reset returns to.
        val restoredProfiles = WidgetProfiles(prefs, defaults)
        assertEquals(2, restoredProfiles.activeProfile)
        val live = WidgetProfileDesign(WidgetDesign.SYSTEM, store.load(WidgetDesign.SYSTEM, defaults), restored)
        assertEquals(dusk, restoredProfiles.design(3, live))
        assertEquals(dusk, restoredProfiles.savedState(3, live))
        // Profile 1 was on the widget when profile 2 replaced it, so it keeps the design the widget had.
        assertEquals(dusk, restoredProfiles.design(1, live))
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
