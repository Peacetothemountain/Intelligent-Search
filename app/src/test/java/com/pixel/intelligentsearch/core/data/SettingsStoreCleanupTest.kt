package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings nothing reads any more are not copied to device-protected storage for the widgets. */
@RunWith(RobolectricTestRunner::class)
class SettingsStoreCleanupTest {

    /** Device-protected storage kept in its own file, so the test sees exactly what the mirror writes there. */
    private class SeparateDeviceStorageContext(base: Context) : ContextWrapper(base) {
        override fun createDeviceProtectedStorageContext(): Context = object : ContextWrapper(baseContext) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                super.getSharedPreferences("device_protected_$name", mode)
        }
    }

    @Test
    fun theUnusedGIconSwitchIsNotMirroredForWidgets() {
        val context = SeparateDeviceStorageContext(ApplicationProvider.getApplicationContext())
        val deviceProtected = context.createDeviceProtectedStorageContext()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        val settingsManager = SettingsManager(context)
        settingsManager.set(SettingsManager.G_ICON_ENABLED, false)

        settingsManager.startDirectBootMirror()
        settingsManager.set(SettingsManager.G_ICON_ENABLED, true)
        settingsManager.set(SettingsManager.WIDGET_SHOW_VOICE, false)

        assertFalse(deviceProtected.contains(SettingsManager.G_ICON_ENABLED.name))
        // The mirror itself is running: settings the widgets draw with are copied.
        assertEquals(false, deviceProtected.getBoolean(SettingsManager.WIDGET_SHOW_VOICE.name, true))
    }

    /** Settings removed with their features are dropped, so an older build's leftovers don't linger or get backed up. */
    @Test
    fun anOlderBuildsRemovedSwitchesAreDropped() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsManager = SettingsManager(context)
        settingsManager.prefs.edit().putBoolean("glance.codes", true).putBoolean("on_device_translation", true).commit()
        settingsManager.migrateToSingleStore()
        assertFalse(settingsManager.prefs.contains("glance.codes"))
        assertFalse(settingsManager.prefs.contains("on_device_translation"))
    }

    /** Saved Links were removed: an older build's links are dropped at start, and the site icons it fetched deleted. */
    @Test
    fun anOlderBuildsSavedLinksAndTheirIconsAreDropped() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsManager = SettingsManager(context)
        settingsManager.prefs.edit().putString("saved_links_json", "[{\"name\":\"Bank\",\"url\":\"https://bank.example.com/\"}]").commit()
        val icons = java.io.File(context.filesDir, "saved_link_icons").apply { mkdirs() }
        java.io.File(icons, "0123456789abcdef.png").writeBytes(byteArrayOf(1, 2, 3))
        settingsManager.migrateToSingleStore()
        assertFalse(settingsManager.prefs.contains("saved_links_json"))
        assertFalse("the icon folder is gone", icons.exists())
        assertNull(SettingsManager.keyFor("saved_links_json"))
    }

    /** Launch Portal's Edge Swipe was removed: an older build's switch, edge, position and height are dropped at start. */
    @Test
    fun anOlderBuildsEdgeSwipeSettingsAreDropped() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsManager = SettingsManager(context)
        val names = listOf("launch.edge_swipe", "launch.edge_swipe_edge", "launch.edge_swipe_position", "launch.edge_swipe_height")
        settingsManager.prefs.edit().putBoolean(names[0], true).putString(names[1], "left")
            .putInt(names[2], 30).putInt(names[3], 40).commit()
        settingsManager.migrateToSingleStore()
        for (name in names) {
            assertFalse(name, settingsManager.prefs.contains(name))
            assertNull(name, SettingsManager.keyFor(name))
        }
        // The floating button, which stays, keeps its settings.
        assertEquals(SettingsManager.FLOATING_SEARCH_BUTTON, SettingsManager.keyFor("launch.floating_button"))
    }
}
