package com.pixel.intelligentsearch.core.data.settings

import android.content.SharedPreferences

/**
 * One app setting, declared once: its storage [name], type, [default], and the [legacyNames] older
 * versions wrote it under. Reads fall back to the legacy spellings until the one-time migration folds them
 * into [name]; writes only ever use [name].
 */
sealed class SettingKey<T : Any>(
    val name: String,
    val default: T,
    val legacyNames: List<String>
) {
    /** Every name this setting may be stored under, canonical first. */
    val allNames: List<String> = listOf(name) + legacyNames

    /** Reads the value stored under [storedName], or null if it is missing or of another type. */
    internal abstract fun readRaw(prefs: SharedPreferences, storedName: String): T?
    internal abstract fun writeRaw(editor: SharedPreferences.Editor, value: T)

    /** Brings a value into this setting's allowed domain (e.g. clamps an int to its range). */
    internal open fun sanitize(value: T): T = value

    override fun toString(): String = name
}

class BooleanSetting(name: String, default: Boolean, vararg legacyNames: String) :
    SettingKey<Boolean>(name, default, legacyNames.toList()) {
    override fun readRaw(prefs: SharedPreferences, storedName: String): Boolean? =
        readTyped { prefs.getBoolean(storedName, default) }
    override fun writeRaw(editor: SharedPreferences.Editor, value: Boolean) {
        editor.putBoolean(name, value)
    }
}

class IntSetting(
    name: String,
    default: Int,
    val range: IntRange? = null,
    vararg legacyNames: String
) : SettingKey<Int>(name, default, legacyNames.toList()) {
    override fun readRaw(prefs: SharedPreferences, storedName: String): Int? =
        readTyped { prefs.getInt(storedName, default) }?.let(::sanitize)
    override fun writeRaw(editor: SharedPreferences.Editor, value: Int) {
        editor.putInt(name, sanitize(value))
    }
    override fun sanitize(value: Int): Int = range?.let { value.coerceIn(it) } ?: value
}

class StringSetting(name: String, default: String, vararg legacyNames: String) :
    SettingKey<String>(name, default, legacyNames.toList()) {
    override fun readRaw(prefs: SharedPreferences, storedName: String): String? =
        readTyped { prefs.getString(storedName, null) }
    override fun writeRaw(editor: SharedPreferences.Editor, value: String) {
        editor.putString(name, value)
    }
}

class StringSetSetting(name: String, default: Set<String>, vararg legacyNames: String) :
    SettingKey<Set<String>>(name, default, legacyNames.toList()) {
    override fun readRaw(prefs: SharedPreferences, storedName: String): Set<String>? =
        readTyped { prefs.getStringSet(storedName, null)?.toSet() }
    override fun writeRaw(editor: SharedPreferences.Editor, value: Set<String>) {
        // SharedPreferences keeps a reference to the set it is given; store a copy.
        editor.putStringSet(name, HashSet(value))
    }
}

/** A value stored with a different type (e.g. from an old version or a crafted backup) reads as missing. */
private inline fun <T> readTyped(read: () -> T): T? = try {
    read()
} catch (_: ClassCastException) {
    null
}

/** The setting's value: canonical name, then legacy spellings, then the default. Wrong types read as missing. */
operator fun <T : Any> SharedPreferences.get(key: SettingKey<T>): T {
    for (storedName in key.allNames) {
        if (contains(storedName)) key.readRaw(this, storedName)?.let { return it }
    }
    return key.default
}

/** Writes [value] under the canonical name and drops any legacy spellings so they can't go stale. */
fun <T : Any> SharedPreferences.Editor.put(key: SettingKey<T>, value: T): SharedPreferences.Editor {
    key.writeRaw(this, value)
    key.legacyNames.forEach(::remove)
    return this
}
