package com.pixel.intelligentsearch.core.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.SettingsManager

/**
 * The Application Search pill profiles. The active profile's pills are the backed-up SEARCH_PILLS setting, so a
 * restored backup shows on screen; each profile also has a working copy (kept while another profile is active)
 * and a saved state the user can reset back to. The one place that knows these key names.
 */
class SearchPillProfiles(private val prefs: SharedPreferences) {

    val activeProfile: Int get() = prefs.getInt(ACTIVE_KEY, 1)

    /** The active profile's pills, in order. */
    fun pills(): List<String> = split(prefs[SettingsManager.SEARCH_PILLS])

    /**
     * Replaces the active profile's pills. Adding apps past Max Quick Panel Apps raises it to fit them, so an app just
     * added shows in the panel; removing or reordering apps, or a lower max chosen since, is left alone. With Context
     * Aware Quick Apps on the panel shows recent apps instead, so the max is left alone then too.
     */
    fun setPills(pills: List<String>) {
        val value = pills.joinToString(",")
        val added = pills.size > pills().size
        prefs.edit {
            putString(workingKey(activeProfile), value)
            put(SettingsManager.SEARCH_PILLS, value)
            if (added && !prefs[SettingsManager.CONTEXT_AWARE_QUICK_APPS] && pills.size > prefs[SettingsManager.SHORTCUT_RESULTS_COUNT]) {
                put(SettingsManager.SHORTCUT_RESULTS_COUNT, pills.size)
            }
        }
    }

    /**
     * Makes [id] the active profile, keeping the one being left so switching back restores it. Picking the active
     * profile again changes nothing: its working copy can be older than its pills (a restored backup brings only
     * the pills), and switching to it would put that older copy back.
     */
    fun switchTo(id: Int): List<String> {
        if (id == activeProfile) return pills()
        val pills = prefs.getString(workingKey(id), null) ?: savedState(id)
        val leaving = prefs[SettingsManager.SEARCH_PILLS]
        prefs.edit {
            putString(workingKey(activeProfile), leaving)
            putInt(ACTIVE_KEY, id)
            put(SettingsManager.SEARCH_PILLS, pills)
        }
        return split(pills)
    }

    /** Records the active profile's current pills as the state Reset returns to. */
    fun saveState(pills: List<String>) {
        prefs.edit { putString(savedKey(activeProfile), pills.joinToString(",")) }
    }

    /** Puts the active profile back to its saved state and returns it. */
    fun resetToSaved(): List<String> {
        val saved = savedState(activeProfile).ifBlank { if (activeProfile == 1) DEFAULT_PILLS else "" }
        return split(saved).also(::setPills)
    }

    /** Profile 1 starts with the default pills as its saved state. */
    fun ensureDefaultSaved() {
        if (!prefs.contains(savedKey(1))) prefs.edit { putString(savedKey(1), DEFAULT_PILLS) }
    }

    private fun savedState(id: Int): String =
        prefs.getString(savedKey(id), if (id == 1) DEFAULT_PILLS else "") ?: ""

    // Trimmed and each app once, as the overlay reads them: the page keys and reorders its list by package name.
    private fun split(value: String): List<String> = value.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()

    companion object {
        const val PROFILE_COUNT = 5
        const val MAX_PILLS = 20
        private const val ACTIVE_KEY = "active_app_search_profile"
        private fun workingKey(id: Int) = "app_search_profile_$id"
        private fun savedKey(id: Int) = "app_search_profile_${id}_saved"
        val DEFAULT_PILLS: String = SettingsManager.SEARCH_PILLS.default
    }
}
