package com.pixel.intelligentsearch.core.data

import com.pixel.intelligentsearch.core.search.ResultShortcuts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pins, nicknames and trigger words, kept as one setting ([SettingsManager.RESULT_SHORTCUTS]) so backups carry them
 * like every other setting. Reads are synchronous (SharedPreferences holds the text in memory) and parsed once per
 * change, so search can read them on every keystroke.
 */
@Singleton
class ResultShortcutStore @Inject constructor(private val settingsManager: SettingsManager) {

    @Volatile private var parsedFrom: String? = null
    @Volatile private var parsed: ResultShortcuts = ResultShortcuts.EMPTY

    /** What is set right now. */
    val current: ResultShortcuts
        get() {
            val json = settingsManager[SettingsManager.RESULT_SHORTCUTS]
            if (json != parsedFrom) {
                parsed = ResultShortcuts.fromJson(json)
                parsedFrom = json
            }
            return parsed
        }

    /** What is set now, then every change (from the overlay, the settings page or a restore). */
    val flow: Flow<ResultShortcuts> = settingsManager.observe(SettingsManager.RESULT_SHORTCUTS)
        .map { ResultShortcuts.fromJson(it) }
        .distinctUntilChanged()

    /** Applies [change] to what is set and saves the result. */
    fun update(change: (ResultShortcuts) -> ResultShortcuts) {
        synchronized(this) {
            val before = current
            val after = change(before)
            if (after != before) settingsManager.set(SettingsManager.RESULT_SHORTCUTS, after.toJson())
        }
    }
}
