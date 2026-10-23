package com.pixel.intelligentsearch.core.data.widget

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.StringSetting
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** One widget profile's whole design: the [design] the widget shows, and both designs' customization. */
data class WidgetProfileDesign(
    val design: WidgetDesign,
    val system: WidgetDesignSettings,
    val material: WidgetDesignSettings
) {
    fun settings(of: WidgetDesign): WidgetDesignSettings = if (of == WidgetDesign.SYSTEM) system else material
}

/**
 * The widget's design profiles, as the Application Search pill profiles work ([com.pixel.intelligentsearch.core.data.settings.SearchPillProfiles]).
 * The active profile's design is the widget's own settings ([WidgetSettingsStore] and [SettingsManager.WIDGET_THEME_STYLE]),
 * so a restored backup shows on the widget; each other profile keeps a copy of its design while another is active,
 * and every profile has a saved state its Reset returns to. The one place that knows how they're stored.
 */
class WidgetProfiles(private val prefs: SharedPreferences, private val defaults: WidgetDefaults) {

    val activeProfile: Int get() = prefs[SettingsManager.WIDGET_ACTIVE_PROFILE]

    /**
     * [id]'s design: [live], the design the widget has, for the active profile; for another, the copy kept when it was
     * left, else its saved state.
     */
    fun design(id: Int, live: WidgetProfileDesign): WidgetProfileDesign =
        if (id == activeProfile) live else read(working(id), live) ?: savedState(id, live)

    /** What Reset returns [id] to: its saved state, else the default design. */
    fun savedState(id: Int, live: WidgetProfileDesign): WidgetProfileDesign = read(saved(id), live) ?: defaultDesign()

    /** Records [design] as the state [id]'s Reset returns to. */
    fun saveState(id: Int, design: WidgetProfileDesign) {
        prefs.edit { put(saved(id), encode(design)) }
    }

    /**
     * Writes into [editor], with the widget's Save: [active] becomes the active profile (the caller writes its design
     * as the widget's settings), and each profile in [left] keeps its design until it's picked again. The profile
     * that was active keeps [previousLive], the design the widget had, unless [left] has a newer one for it.
     */
    fun commit(editor: SharedPreferences.Editor, active: Int, left: Map<Int, WidgetProfileDesign>, previousLive: WidgetProfileDesign) {
        val leaving = activeProfile
        if (active != leaving && leaving !in left) editor.put(working(leaving), encode(previousLive))
        for ((id, design) in left) if (id != active) editor.put(working(id), encode(design))
        editor.put(SettingsManager.WIDGET_ACTIVE_PROFILE, active)
    }

    /** Profile 1 starts with the design it has the first time the profiles are opened as its saved state. */
    fun ensureDefaultSaved(live: WidgetProfileDesign) {
        if (!prefs.contains(saved(1).name)) saveState(1, design(1, live))
    }

    /** A profile that has never been saved: both designs' defaults, showing the default design. */
    fun defaultDesign(): WidgetProfileDesign {
        val store = WidgetSettingsStore(EmptyPreferences)
        val design = WidgetDesign.entries.firstOrNull { it.themeStyle == SettingsManager.WIDGET_THEME_STYLE.default } ?: WidgetDesign.SYSTEM
        return WidgetProfileDesign(design, store.load(WidgetDesign.SYSTEM, defaults), store.load(WidgetDesign.MATERIAL, defaults))
    }

    /** The profile stored under [key]; a design it doesn't hold is [live]'s. Unreadable or missing reads as null. */
    private fun read(key: StringSetting, live: WidgetProfileDesign): WidgetProfileDesign? {
        val json = parse(prefs[key]) as? JsonObject ?: return null
        val design = WidgetDesign.entries.firstOrNull { it.name == (json[DESIGN_FIELD] as? JsonPrimitive)?.contentOrNull } ?: return null
        fun settings(of: WidgetDesign) = (json[of.name] as? JsonObject)?.let { settingsFrom(of, it, defaults) } ?: live.settings(of)
        return WidgetProfileDesign(design, settings(WidgetDesign.SYSTEM), settings(WidgetDesign.MATERIAL))
    }

    /** One theme as the retired Saved Themes stored it: its name, its design and the design's own stored values. */
    @Serializable
    private data class SavedTheme(val name: String, val design: String, val values: JsonObject)

    companion object {
        private const val TAG = "WidgetProfiles"
        const val PROFILE_COUNT = 5
        /** The retired Saved Themes setting ([migrateSavedThemes]); in [SettingsManager.REMOVED_NAMES]. */
        const val SAVED_THEMES_KEY = "widget_saved_themes"
        private const val DESIGN_FIELD = "design"

        private val JSON = Json { ignoreUnknownKeys = true }

        private fun working(id: Int): StringSetting = SettingsManager.WIDGET_PROFILE_DESIGNS[id - 1]
        private fun saved(id: Int): StringSetting = SettingsManager.WIDGET_PROFILE_SAVED_DESIGNS[id - 1]

        private fun parse(text: String): JsonElement? {
            if (text.isBlank()) return null
            return try {
                JSON.parseToJsonElement(text)
            } catch (e: SerializationException) {
                Log.w(TAG, "Unreadable widget profile", e)
                null
            }
        }

        private fun encode(design: WidgetDesign, values: Map<WidgetDesign, JsonObject>): String = buildJsonObject {
            put(DESIGN_FIELD, design.name)
            for ((of, stored) in values) put(of.name, stored)
        }.toString()

        internal fun encode(design: WidgetProfileDesign): String =
            encode(design.design, WidgetDesign.entries.associateWith { valuesOf(it, design.settings(it)) })

        /**
         * Once, from the retired Saved Themes: the first [PROFILE_COUNT] themes become profiles 1 to 5, in order, as each
         * one's design and saved state (a profile already stored is left alone), and the old setting is removed. A
         * theme holds one design, so its profile shows that design and keeps the widget's own for the other. Profile 1
         * stays the active one, so the widget keeps its design; its first theme is what its Reset returns to.
         */
        fun migrateSavedThemes(prefs: SharedPreferences) {
            val stored = prefs.getString(SAVED_THEMES_KEY, null) ?: return
            val themes = (parse(stored) as? JsonArray).orEmpty().mapNotNull { entry ->
                val theme = runCatching { JSON.decodeFromJsonElement(SavedTheme.serializer(), entry) }.getOrNull()
                val design = WidgetDesign.entries.firstOrNull { it.name == theme?.design }
                if (theme == null || design == null) null else design to theme.values
            }.take(PROFILE_COUNT)
            prefs.edit {
                themes.forEachIndexed { index, (design, values) ->
                    val id = index + 1
                    if (prefs.contains(working(id).name) || prefs.contains(saved(id).name)) return@forEachIndexed
                    val profile = encode(design, mapOf(design to values))
                    put(working(id), profile)
                    put(saved(id), profile)
                }
                remove(SAVED_THEMES_KEY)
            }
        }

        /** The profile settings (each profile's working design and its saved state) a backup can carry. */
        fun isProfileKey(key: String): Boolean =
            SettingsManager.WIDGET_PROFILE_DESIGNS.any { it.name == key } ||
                SettingsManager.WIDGET_PROFILE_SAVED_DESIGNS.any { it.name == key }

        /**
         * A restored profile [text] with the media row switched off in both designs. Like the widget's own Media
         * Controls switch, a restore never switches it on; only the user does. Text that isn't a profile is returned
         * as it is (reading it later falls back to the live design).
         */
        fun withoutRestoreOptIns(text: String): String {
            val json = parse(text) as? JsonObject ?: return text
            var changed = false
            val cleaned = JsonObject(json.mapValues { (_, element) ->
                val values = element as? JsonObject
                if (values != null && (values[SHOW_MEDIA_FIELD] as? JsonPrimitive)?.booleanOrNull == true) {
                    changed = true
                    JsonObject(values + (SHOW_MEDIA_FIELD to JsonPrimitive(false)))
                } else element
            })
            return if (changed) cleaned.toString() else text
        }

        /** The media row's field in a stored design, its setting's name without the design's prefix. */
        private const val SHOW_MEDIA_FIELD = "show_media"

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

/** Collects what's written to it, for [WidgetProfiles.valuesOf]. */
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

/** Read-only preferences holding [values], so [WidgetSettingsStore] can read a stored profile as it reads its own keys. */
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
