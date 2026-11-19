package com.pixel.intelligentsearch.feature.tabs.gallery

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import org.json.JSONArray
import org.json.JSONObject

/*
 * What Gallery keeps between visits: the look that is on, the one before it (the lasting undo), the saved looks and
 * the bar's row in the full preview. One small JSON value in the app's settings ([SettingsManager.GALLERY_LOOKS]),
 * replaced whole on every change, so it is in the app's encrypted backups with the rest. Anything that can't be read
 * leaves Gallery as it starts on a new phone.
 */

/** Gallery's lasting state, as written to and read from [GalleryDisk]. */
internal class GalleryKept(
    /** Null when the stored one couldn't be read; the person's design as the settings have it is on then. */
    val current: Look?,
    val before: Look?,
    val saved: List<Look>,
    val seq: Int,
    val row: Int,
    /** The look of the week and the week it was picked for ("2026-W47" to "meadow"). */
    val hero: Pair<String, String>? = null,
    /** Every setting a look touches as it was before [before] was replaced: what Undo and the Before tile put back. */
    val undoTo: Look? = null,
    /**
     * Whether some of what was stored couldn't be read (a look from a newer version, a damaged entry): then the value
     * is never written over, so nothing in it is lost.
     */
    val partial: Boolean = false
)

internal object GalleryDisk {
    private const val VERSION = SettingsManager.GALLERY_LOOKS_VERSION
    /** The stored value as it is, to tell whether something else (a restore) has replaced it. */
    fun raw(prefs: SharedPreferences): String = prefs[SettingsManager.GALLERY_LOOKS]

    /** What [kept] is stored as. */
    fun json(kept: GalleryKept): String =
        JSONObject().apply {
            put("version", VERSION)
            kept.current?.let { put("current", it.toJson()) }
            kept.before?.let { put("before", it.toJson()) }
            put("saved", JSONArray().apply { kept.saved.forEach { put(it.toJson()) } })
            put("seq", kept.seq)
            put("row", kept.row)
            kept.hero?.let { (week, id) -> put("heroWeek", week); put("hero", id) }
            kept.undoTo?.let { put("undoTo", it.toJson()) }
        }.toString()

    /** Stores [json] (made by [json]). */
    fun put(prefs: SharedPreferences, json: String) {
        prefs.edit { put(SettingsManager.GALLERY_LOOKS, json) }
    }

    /**
     * What is stored, or null when nothing is or none of it can be read (another format, a newer version's). Each look
     * is read on its own, so one that can't be read (a built-in look or a value a newer version added) costs only that
     * look, and [GalleryKept.partial] says some were left out.
     */
    fun read(prefs: SharedPreferences): GalleryKept? = runCatching {
        val o = JSONObject(prefs[SettingsManager.GALLERY_LOOKS])
        if (o.optInt("version") != VERSION) return null
        var partial = false
        fun JSONObject.look(): Look? = runCatching { toLook() }.getOrNull().also { if (it == null) partial = true }
        val stored = o.optJSONArray("saved")
        val saved = if (stored == null) emptyList() else List(stored.length()) { stored.optJSONObject(it)?.look() }
            .also { looks -> if (looks.any { it == null }) partial = true }
            .filterNotNull()
            .take(5)
        GalleryKept(
            current = o.optJSONObject("current")?.look(),
            before = o.optJSONObject("before")?.look(),
            saved = saved,
            seq = o.optInt("seq", 2),
            row = o.optInt("row", 5).coerceIn(0, 5),
            hero = o.optString("heroWeek").ifEmpty { null }?.let { w -> o.optString("hero").ifEmpty { null }?.let { w to it } },
            undoTo = o.optJSONObject("undoTo")?.look(),
            partial = partial
        )
    }.getOrNull()

    private fun StyleSpec.toJson() = JSONObject().apply {
        subtheme?.let { put("subtheme", it) }
        gIcon?.let { put("gIcon", it) }
        hue?.let { put("hue", it) }
        saturation?.let { put("saturation", it) }
        lightness?.let { put("lightness", it) }
        colorOpacity?.let { put("colorOpacity", it) }
        transparency?.let { put("transparency", it) }
        outline?.let { put("outline", it) }
        corner?.let { put("corner", it) }
        lockBlack?.let { put("lockBlack", it) }
        lockedPill?.let { put("lockedPill", it) }
    }

    private fun JSONObject.toStyle(): StyleSpec {
        fun int(k: String) = if (has(k)) getInt(k) else null
        return StyleSpec(
            subtheme = optString("subtheme").ifEmpty { null }, gIcon = optString("gIcon").ifEmpty { null },
            hue = int("hue"), saturation = int("saturation"), lightness = int("lightness"), colorOpacity = int("colorOpacity"),
            transparency = int("transparency"), outline = int("outline"), corner = int("corner"),
            lockBlack = if (has("lockBlack")) getBoolean("lockBlack") else null, lockedPill = int("lockedPill")
        )
    }

    /** A built-in look by its id alone (its words and values come from this build); any other look in full. */
    private fun Look.toJson(): JSONObject {
        if (origin == Origin.BuiltIn && BUILT_IN_LOOKS.any { it.id == id }) return JSONObject().put("ref", id)
        val q = search
        return JSONObject().apply {
            put("id", id); put("name", name); put("desc", desc); put("dyn", dyn); put("hero", hero.name)
            put("tags", JSONArray(tags.toList()))
            put("system", system.toJson()); put("material", material.toJson())
            put("search", JSONObject().apply {
                put("theme", q.theme.name); put("wallpaper", q.wallpaper); put("blur", q.blur); put("transparency", q.transparency)
                put("pillOpacity", q.pillOpacity); put("stars", q.stars); q.clock?.let { put("clock", it) }
                q.appTheme?.let { put("appTheme", it) }
            })
            season?.let { put("season", it.name) }
            note?.let { put("note", it) }
            put("origin", origin.name)
            base?.let { put("base", it) }
        }
    }

    private fun JSONObject.toLook(): Look {
        if (has("ref")) return BUILT_IN_LOOKS.first { it.id == getString("ref") }
        val q = getJSONObject("search")
        return Look(
            id = getString("id"), name = getString("name"), desc = optString("desc"), dyn = getBoolean("dyn"),
            hero = Design.valueOf(getString("hero")),
            tags = getJSONArray("tags").let { a -> List(a.length()) { a.getString(it) }.toSet() },
            system = getJSONObject("system").toStyle(), material = getJSONObject("material").toStyle(),
            search = SearchLook(
                theme = ThemeChoice.valueOf(q.getString("theme")), wallpaper = q.getBoolean("wallpaper"), blur = q.getInt("blur"),
                transparency = q.getInt("transparency"), pillOpacity = q.getInt("pillOpacity"), stars = q.getBoolean("stars"),
                clock = q.optString("clock").ifEmpty { null },
                appTheme = q.optString("appTheme").ifEmpty { null }
            ),
            season = optString("season").ifEmpty { null }?.let { Season.valueOf(it) },
            note = optString("note").ifEmpty { null },
            origin = Origin.valueOf(getString("origin")),
            base = optString("base").ifEmpty { null }
        )
    }
}
