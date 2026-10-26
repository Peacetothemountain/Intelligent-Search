package com.pixel.intelligentsearch.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The App Animations switch is gone: apps opened from search always grow out of their icon. What an older build
 * stored (under either spelling) is dropped at start, and a backup never brings it back.
 */
@RunWith(RobolectricTestRunner::class)
class RetiredAppAnimationsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val retired = listOf("app_animations", "app.animation")

    @Test
    fun theSwitchIsGone() {
        for (name in retired) {
            assertNull("$name is not declared", SettingsManager.keyFor(name))
            assertNull("$name has no backup type", BackupPreferenceSchema.expectedType(name))
            assertTrue("$name is dropped and never restored", name in SettingsManager.REMOVED_NAMES)
        }
    }

    @Test
    fun anOlderBuildsValueIsDroppedAtStart() = runBlocking {
        val settings = SettingsManager(context)
        val edit = settings.prefs.edit()
        for (name in retired) edit.putBoolean(name, false)
        edit.commit()
        settings.migrateToSingleStore()
        for (name in retired) assertFalse("$name is dropped", settings.prefs.contains(name))
    }
}
