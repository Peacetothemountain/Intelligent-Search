package com.pixel.intelligentsearch.core.data

import com.pixel.intelligentsearch.core.data.settings.ParsedSetting
import com.pixel.intelligentsearch.core.search.ResultShortcuts
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pinned results, kept as one setting ([SettingsManager.RESULT_SHORTCUTS]) so backups carry them
 * like every other setting. Reads are synchronous and decoded once per change ([ParsedSetting]), so search can read
 * them on every keystroke. The overlay's menu and Pinned row change them through here.
 */
@Singleton
class ResultShortcutStore @Inject constructor(private val settingsManager: SettingsManager) {

    private val setting = ParsedSetting(
        { settingsManager.prefs }, SettingsManager.RESULT_SHORTCUTS, { ResultShortcuts.fromJson(it) }, { it.toJson() }
    )

    /** What is set right now. */
    val current: ResultShortcuts get() = setting.current

    /** What is set now, then every change (from the overlay or a restore). */
    val flow: Flow<ResultShortcuts> = setting.observe(settingsManager)

    /** Applies [change] to what is set and saves the result. */
    fun update(change: (ResultShortcuts) -> ResultShortcuts) {
        setting.update(change)
    }
}
