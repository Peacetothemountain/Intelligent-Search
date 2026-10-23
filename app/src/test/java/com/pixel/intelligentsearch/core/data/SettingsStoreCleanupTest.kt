package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
