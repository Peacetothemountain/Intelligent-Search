package com.pixel.intelligentsearch.core.backup

import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPreferenceSchemaTest {

    @Test
    fun resultCountsRejectNegativeAndTooLarge() {
        // A negative count reached take() during composition and crashed the overlay on every open.
        val range = BackupPreferenceSchema.intRange("shortcut_results_count")!!
        assertFalse(-1 in range)
        assertTrue(0 in range) // empty pill profiles store 0
        assertTrue(6 in range)
        assertFalse(21 in range)
    }

    @Test
    fun percentAndColorComponentRangesMatchTheSliders() {
        assertEquals(0..100, BackupPreferenceSchema.intRange("search.background.blur"))
        assertEquals(0..100, BackupPreferenceSchema.intRange("widget_system_background_transparency"))
        assertEquals(0..100, BackupPreferenceSchema.intRange("search.pill.opacity"))
        assertEquals(0..360, BackupPreferenceSchema.intRange("widget_material_custom_hue"))
        assertEquals(0..100, BackupPreferenceSchema.intRange("search_weight_files"))
    }

    @Test
    fun packedColorIntsAreNotRangeChecked() {
        // ARGB colors are legitimately negative.
        assertNull(BackupPreferenceSchema.intRange("widget_system_custom_color_int"))
    }

    @Test
    fun knownKeysCarryTheTypeTheAppReads() {
        assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType("search.web.suggestions"))
        assertEquals("INT", BackupPreferenceSchema.expectedType("web_results_count"))
        assertEquals("STRING", BackupPreferenceSchema.expectedType("widget_system_shortcut_1"))
        assertEquals("STRING_SET", BackupPreferenceSchema.expectedType("hidden_apps"))
        assertNull(BackupPreferenceSchema.expectedType("some_future_key"))
    }

    @Test
    fun androidSettingsSourceIsRegisteredAsABoolean() {
        assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(SettingsManager.SEARCH_ANDROID_SETTINGS.name))
        assertEquals("search.android.settings", SettingsManager.SEARCH_ANDROID_SETTINGS.name)
        assertTrue(SettingsManager.SEARCH_ANDROID_SETTINGS.default)
        assertTrue(SettingsManager.SEARCH_ANDROID_SETTINGS in SettingsManager.ALL)
    }

    @Test
    fun debugSwitchesAreNeverImported() {
        assertTrue(BackupPreferenceSchema.isDebugKey("debug_unlocked"))
        assertTrue(BackupPreferenceSchema.isDebugKey("debug.verbose_logging"))
        assertFalse(BackupPreferenceSchema.isDebugKey("diagnostics_overlay_enabled"))
    }
}
