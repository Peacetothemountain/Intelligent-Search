package com.pixel.intelligentsearch.core.data.widget

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A widget design the user saved under a [name]: everything Widget Customization sets for that [design]. */
data class SavedWidgetTheme(val name: String, val design: WidgetDesign, val settings: WidgetDesignSettings)

/**
 * The user's named widget designs, stored as one JSON setting ([SettingsManager.WIDGET_SAVED_THEMES]) so backups
 * carry them. A theme is the design's own stored values, written and read back by [WidgetSettingsStore] itself, so it
 * keeps every option the editor has, now and as options are added. Names are unique per design.
 */
class WidgetThemeStore(private val prefs: SharedPreferences) {

    fun list(defaults: WidgetDefaults): List<SavedWidgetTheme> = read().mapNotNull { entry ->
        val design = WidgetDesign.entries.firstOrNull { it.name == entry.optString("design") } ?: return@mapNotNull null
        val name = entry.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val values = entry.optJSONObject("values") ?: return@mapNotNull null
        SavedWidgetTheme(name, design, settingsFrom(design, values, defaults))
    }

    /** Saves [settings] as [name] for [design], replacing a theme of that name; at most [MAX_THEMES] are kept. */
    fun save(name: String, design: WidgetDesign, settings: WidgetDesignSettings) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        require(trimmed.isNotEmpty()) { "A saved theme needs a name" }
        val entry = JSONObject().put("name", trimmed).put("design", design.name).put("values", valuesOf(design, settings))
        val kept = read().filterNot { it.matches(trimmed, design) }
        write((kept + entry).takeLast(MAX_THEMES))
    }

    /** Renames [design]'s theme [from] to [to]; false when [to] is blank or another of its themes has it. */
    fun rename(from: String, design: WidgetDesign, to: String): Boolean {
        val trimmed = to.trim().take(MAX_NAME_LENGTH)
        val entries = read()
        if (trimmed.isEmpty() || entries.any { it.matches(trimmed, design) && trimmed != from }) return false
        write(entries.map { if (it.matches(from, design)) JSONObject(it.toString()).put("name", trimmed) else it })
        return true
    }

    fun delete(name: String, design: WidgetDesign) {
        write(read().filterNot { it.matches(name, design) })
    }

    private fun JSONObject.matches(name: String, design: WidgetDesign) = optString("name") == name && optString("design") == design.name

    private fun read(): List<JSONObject> = try {
        val array = JSONArray(prefs[SettingsManager.WIDGET_SAVED_THEMES])
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    } catch (e: JSONException) {
        Log.w(TAG, "Unreadable saved widget themes", e)
        emptyList()
    }

    private fun write(entries: List<JSONObject>) {
        prefs.edit { put(SettingsManager.WIDGET_SAVED_THEMES, JSONArray(entries).toString()) }
    }

    companion object {
        private const val TAG = "WidgetThemeStore"
        const val MAX_THEMES = 20
        const val MAX_NAME_LENGTH = 40

        /** What [WidgetSettingsStore.save] writes for [settings], by key without the design's prefix. */
        internal fun valuesOf(design: WidgetDesign, settings: WidgetDesignSettings): JSONObject {
            val recorded = RecordingEditor()
            WidgetSettingsStore(EmptyPreferences).save(design, settings, recorded)
            val json = JSONObject()
            for ((key, value) in recorded.values) json.put(key.removePrefix("${design.prefix}_"), value)
            return json
        }

        /** The settings [values] hold, read as [WidgetSettingsStore] reads a design it saved. */
        internal fun settingsFrom(design: WidgetDesign, values: JSONObject, defaults: WidgetDefaults): WidgetDesignSettings {
            val stored = values.keys().asSequence().associate { key ->
                val value = when (val raw = values.get(key)) {
                    is Number -> raw.toInt()
                    else -> raw
                }
                "${design.prefix}_$key" to value
            }
            return WidgetSettingsStore(MapPreferences(stored)).load(design, defaults)
        }
    }
}

/** Collects what's written to it, for [WidgetThemeStore.valuesOf]. */
private class RecordingEditor : SharedPreferences.Editor {
    val values = linkedMapOf<String, Any>()
    override fun putString(key: String, value: String?) = apply { if (value != null) values[key] = value else values.remove(key) }
    override fun putStringSet(key: String, values: MutableSet<String>?) = this
    override fun putInt(key: String, value: Int) = apply { values[key] = value }
    override fun putLong(key: String, value: Long) = apply { values[key] = value }
    override fun putFloat(key: String, value: Float) = apply { values[key] = value }
    override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
    override fun remove(key: String) = apply { values.remove(key) }
    override fun clear() = apply { values.clear() }
    override fun commit() = true
    override fun apply() = Unit
}

/** Read-only preferences holding [values], so [WidgetSettingsStore] can read a saved theme as it reads its own keys. */
private open class MapPreferences(private val values: Map<String, Any?>) : SharedPreferences {
    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = (values[key] as? Number)?.toLong() ?: defValue
    override fun getFloat(key: String, defValue: Float) = (values[key] as? Number)?.toFloat() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = key in values
    override fun edit(): SharedPreferences.Editor = RecordingEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

private object EmptyPreferences : MapPreferences(emptyMap())
