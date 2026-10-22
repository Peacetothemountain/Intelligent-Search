package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The widget's Hue, Saturation and Lightness are an HCT seed once this version saves them; older versions stored them
 * as HSV. Restoring an older backup over a phone that has saved the seed reads the restored values as HSV again, and a
 * newer backup keeps them as the seed.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetColorBackupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), IntelligentSearchDatabase::class.java
    ).allowMainThreadQueries().build()
    private lateinit var backup: BackupManager
    private val uri get() = Uri.fromFile(File(context.cacheDir, "widget_colour.json"))
    private val flag = WidgetSettingsStore.hctFlagKey(WidgetDesign.MATERIAL)

    @Before fun setUp() {
        prefs.edit().clear().commit()
        backup = BackupManager(context, SettingsManager(context), db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
    }

    @After fun tearDown() = db.close()

    private fun sliders(hue: Int) = prefs.edit()
        .putInt("widget_material_custom_hue", hue)
        .putInt("widget_material_custom_saturation", 60)
        .putInt("widget_material_custom_lightness", 90)

    @Test
    fun anOlderBackupsSlidersAreReadAsHsvAgain() = runBlocking {
        sliders(200).commit()
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()

        sliders(40).putBoolean(flag, true).commit()
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(200, prefs.getInt("widget_material_custom_hue", 0))
        assertFalse(prefs.contains(flag))
    }

    @Test
    fun aNewerBackupsSlidersStayTheSeed() = runBlocking {
        sliders(200).putBoolean(flag, true).commit()
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()

        sliders(40).commit()
        prefs.edit().remove(flag).commit()
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(200, prefs.getInt("widget_material_custom_hue", 0))
        assertTrue(prefs.getBoolean(flag, false))
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery"
    }
}
