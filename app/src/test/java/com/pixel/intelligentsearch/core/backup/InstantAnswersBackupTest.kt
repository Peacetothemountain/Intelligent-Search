package com.pixel.intelligentsearch.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The old Use Approximate Location switch is dropped at start, and what it chose is kept. */
@RunWith(RobolectricTestRunner::class)
class InstantAnswersBackupTest {

    @Test
    fun someoneWhoUsedTheApproximateLocationKeepsUsingIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsManager(context)
        // The set location now wins over the phone's, so the migration clears it for someone who had chosen the phone's.
        settings.prefs.edit()
            .putBoolean("weather_use_device_location", true)
            .putString(SettingsManager.WEATHER_HOME_LOCATION.name, "36.31,-82.35;Johnson City, Tennessee")
            .commit()
        settings.migrateToSingleStore()
        assertEquals("", settings[SettingsManager.WEATHER_HOME_LOCATION])
        assertFalse(settings.prefs.contains("weather_use_device_location"))

        // Without it, the set location stays.
        settings.prefs.edit().putString(SettingsManager.WEATHER_HOME_LOCATION.name, "36.31,-82.35;Johnson City, Tennessee").commit()
        settings.migrateToSingleStore()
        assertEquals("36.31,-82.35;Johnson City, Tennessee", settings[SettingsManager.WEATHER_HOME_LOCATION])
    }
}
