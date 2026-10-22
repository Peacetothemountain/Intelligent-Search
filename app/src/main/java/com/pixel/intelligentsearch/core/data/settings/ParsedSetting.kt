package com.pixel.intelligentsearch.core.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A structured value (pins and nicknames, saved links, saved widget themes, countdown events) kept as one text
 * setting, so backups carry it like any other setting. Reads are synchronous (SharedPreferences holds the text in
 * memory) and decoded once per change, so search can read it on every keystroke; [update] applies a change and
 * writes it back atomically with respect to other updates through this object. Text that no longer decodes reads as
 * [decode] makes of it (each store decodes leniently, dropping what it can't read).
 */
class ParsedSetting<T>(
    private val prefs: () -> SharedPreferences,
    val key: StringSetting,
    private val decode: (String) -> T,
    private val encode: (T) -> String
) {
    @Volatile private var parsedFrom: String? = null
    @Volatile private var parsed: T? = null

    /** What is stored right now. */
    val current: T
        get() {
            val text = prefs()[key]
            val cached = parsed
            if (text == parsedFrom && cached != null) return cached
            return decode(text).also {
                parsed = it
                parsedFrom = text
            }
        }

    /** Applies [change] to what is stored and saves the result when it differs; returns what is stored after. */
    fun update(change: (T) -> T): T = synchronized(this) {
        val before = current
        val after = change(before)
        if (after != before) prefs().edit { put(key, encode(after)) }
        after
    }

    /** What is stored now, then every change (from any screen, a widget or a restore). */
    fun observe(settingsManager: SettingsManager): Flow<T> =
        settingsManager.observe(key).map(decode).distinctUntilChanged()
}
