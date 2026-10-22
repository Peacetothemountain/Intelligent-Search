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

    /** A declared setting needs no literal entry in the schema: its own type is the one checked, old spellings too. */
    @Test
    fun everyDeclaredSettingCarriesItsOwnType() {
        for (setting in SettingsManager.ALL) {
            val type = BackupPreferenceSchema.typeOf(setting)
            for (name in setting.allNames) assertEquals(name, type, BackupPreferenceSchema.expectedType(name))
        }
        assertEquals("INT", BackupPreferenceSchema.expectedType(SettingsManager.EDGE_SWIPE_HEIGHT.name))
        assertEquals("STRING", BackupPreferenceSchema.expectedType(SettingsManager.SAVED_LINKS.name))
    }

    @Test
    fun androidSettingsSourceIsRegisteredAsABoolean() {
        assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(SettingsManager.SEARCH_ANDROID_SETTINGS.name))
        assertEquals("search.android.settings", SettingsManager.SEARCH_ANDROID_SETTINGS.name)
        assertTrue(SettingsManager.SEARCH_ANDROID_SETTINGS.default)
        assertTrue(SettingsManager.SEARCH_ANDROID_SETTINGS in SettingsManager.ALL)
    }

    @Test
    fun searchFromSelectedTextIsNoLongerASetting() {
        // The entry is always on now; older backups still carry the switch, which restores ignore.
        assertNull(SettingsManager.keyFor("search_from_selected_text"))
        assertNull(BackupPreferenceSchema.expectedType("search_from_selected_text"))
        assertTrue("search_from_selected_text" in SettingsManager.REMOVED_NAMES)
    }

    @Test
    fun keysNamedAtRuntimeCarryTheTypeTheAppReads() {
        // Per-shortcut custom actions and the pill profiles are read with getString.
        assertEquals("STRING", BackupPreferenceSchema.expectedType("Google Lens_custom_type"))
        assertEquals("STRING", BackupPreferenceSchema.expectedType("widget_shortcut_1_custom_value"))
        assertEquals("STRING", BackupPreferenceSchema.expectedType("app_search_profile_2"))
        assertEquals("STRING", BackupPreferenceSchema.expectedType("app_search_profile_2_saved"))
        assertNull(BackupPreferenceSchema.expectedType("app_search_profile_2_other"))
    }

    @Test
    fun theActiveProfileIsOneOfTheProfiles() {
        assertEquals(1..5, BackupPreferenceSchema.intRange("active_app_search_profile"))
    }

    @Test
    fun debugSwitchesAreNeverImported() {
        assertTrue(BackupPreferenceSchema.isDebugKey("debug_unlocked"))
        assertTrue(BackupPreferenceSchema.isDebugKey("debug.verbose_logging"))
        assertFalse(BackupPreferenceSchema.isDebugKey("diagnostics_overlay_enabled"))
    }
}
