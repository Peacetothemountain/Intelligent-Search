package com.pixel.intelligentsearch.core.backup

import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** At a Glance's switches (the section, each item, Show Clock) and its Clock Style are typed settings. */
@RunWith(RobolectricTestRunner::class)
class AtAGlanceSettingsBackupTest {

    private val switches = listOf(
        SettingsManager.GLANCE_ENABLED, SettingsManager.GLANCE_TIMERS, SettingsManager.GLANCE_ALARM,
        SettingsManager.GLANCE_MEDIA, SettingsManager.GLANCE_BIRTHDAYS, SettingsManager.GLANCE_CLOCK
    )

    /** Known booleans: the section starts on, each of its items off until chosen. */
    @Test
    fun theSwitchesAreKnownBooleansAndOnlyTheSectionStartsOn() {
        for (key in switches) {
            assertTrue("$key is declared", key in SettingsManager.ALL)
            assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(key.name))
            assertEquals("$key's default", key == SettingsManager.GLANCE_ENABLED, key.default)
        }
        assertTrue(SettingsManager.GLANCE_CLOCK_STYLE in SettingsManager.ALL)
        assertEquals("STRING", BackupPreferenceSchema.expectedType(SettingsManager.GLANCE_CLOCK_STYLE.name))
        assertEquals("cookie", SettingsManager.GLANCE_CLOCK_STYLE.default)
    }
}
