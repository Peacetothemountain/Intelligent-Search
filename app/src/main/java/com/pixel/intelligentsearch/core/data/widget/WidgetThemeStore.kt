package com.pixel.intelligentsearch.core.data.widget

import kotlinx.serialization.json.put
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import com.pixel.intelligentsearch.core.data.settings.ParsedSetting
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put

/** A widget design the user saved under a [name]: everything Widget Customization sets for that [design]. */
data class SavedWidgetTheme(val name: String, val design: WidgetDesign, val settings: WidgetDesignSettings)

/**
 * The user's named widget designs, stored as one JSON setting ([SettingsManager.WIDGET_SAVED_THEMES]) so backups
 * carry them. A theme is the design's own stored values, written and read back by [WidgetSettingsStore] itself, so it
 * keeps every option the editor has, now and as options are added. Names are unique per design.
 */
class WidgetThemeStore(prefs: SharedPreferences) {

    /** One saved theme as stored: its name, its design and the design's own stored values. */
    @Serializable
    internal data class StoredTheme(val name: String, val design: String, val values: JsonObject) {
        fun matches(name: String, design: WidgetDesign) = this.name == name && this.design == design.name
    }

    private val themes = ParsedSetting({ prefs }, SettingsManager.WIDGET_SAVED_THEMES, ::decode, ::encode)

    fun list(defaults: WidgetDefaults): List<SavedWidgetTheme> = themes.current.mapNotNull { entry ->
        val design = WidgetDesign.entries.firstOrNull { it.name == entry.design } ?: return@mapNotNull null
        val name = entry.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        SavedWidgetTheme(name, design, settingsFrom(design, entry.values, defaults))
    }

    /** Saves [settings] as [name] for [design], replacing a theme of that name; at most [MAX_THEMES] are kept. */
    fun save(name: String, design: WidgetDesign, settings: WidgetDesignSettings) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        require(trimmed.isNotEmpty()) { "A saved theme needs a name" }
        val entry = StoredTheme(trimmed, design.name, valuesOf(design, settings))
        themes.update { stored -> (stored.filterNot { it.matches(trimmed, design) } + entry).takeLast(MAX_THEMES) }
    }

    /** Renames [design]'s theme [from] to [to]; false when [to] is blank or another of its themes has it. */
    fun rename(from: String, design: WidgetDesign, to: String): Boolean {
        val trimmed = to.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty() || themes.current.any { it.matches(trimmed, design) && trimmed != from }) return false
        themes.update { stored -> stored.map { if (it.matches(from, design)) it.copy(name = trimmed) else it } }
        return true
    }

    fun delete(name: String, design: WidgetDesign) {
        themes.update { stored -> stored.filterNot { it.matches(name, design) } }
    }

    companion object {
        private const val TAG = "WidgetThemeStore"
        const val MAX_THEMES = 20
        const val MAX_NAME_LENGTH = 40

        private val JSON = Json { ignoreUnknownKeys = true }

        /** The stored themes; an unreadable list reads as none, and an unreadable theme is left out. */
        private fun decode(text: String): List<StoredTheme> {
            val array = try {
                JSON.parseToJsonElement(text) as? JsonArray
            } catch (e: SerializationException) {
                Log.w(TAG, "Unreadable saved widget themes", e)
                null
            } ?: return emptyList()
            return array.mapNotNull { runCatching { JSON.decodeFromJsonElement(StoredTheme.serializer(), it) }.getOrNull() }
        }

        private fun encode(themes: List<StoredTheme>): String = JSON.encodeToString(ListSerializer(StoredTheme.serializer()), themes)

        /** What [WidgetSettingsStore.save] writes for [settings], by key without the design's prefix. */
        internal fun valuesOf(design: WidgetDesign, settings: WidgetDesignSettings): JsonObject {
            val recorded = RecordingEditor()
            WidgetSettingsStore(EmptyPreferences).save(design, settings, recorded)
            return buildJsonObject {
                for ((key, value) in recorded.values) {
                    val name = key.removePrefix("${design.prefix}_")
                    when (value) {
                        is Boolean -> put(name, value)
                        is Number -> put(name, value)
                        else -> put(name, value.toString())
                    }
                }
            }
        }

        /** The settings [values] hold, read as [WidgetSettingsStore] reads a design it saved. */
        internal fun settingsFrom(design: WidgetDesign, values: JsonObject, defaults: WidgetDefaults): WidgetDesignSettings {
            val stored = values.mapNotNull { (key, element) ->
                val primitive = element as? JsonPrimitive ?: return@mapNotNull null
                val value: Any = when {
                    primitive.isString -> primitive.content
                    primitive.booleanOrNull != null -> primitive.boolean
                    else -> primitive.doubleOrNull?.toInt() ?: return@mapNotNull null
                }
                "${design.prefix}_$key" to value
            }.toMap()
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
