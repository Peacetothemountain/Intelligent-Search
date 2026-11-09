package com.pixel.intelligentsearch.feature.search.today.cards.widget

import com.pixel.intelligentsearch.feature.search.today.cards.SnapLayout
import com.pixel.intelligentsearch.feature.search.today.cards.SnapModel
import com.pixel.intelligentsearch.feature.search.today.cards.SnapRow
import org.json.JSONArray
import org.json.JSONObject

/*
 * NG Designs
 */

/**
 * A [SnapModel] as a small JSON object, for the message from the reader process and for the app's cache, and back.
 * Reading one back cleans every string again and caps every list, so whatever arrives (a message, a cache file) is held
 * to the same rules as a fresh read.
 */
internal object SnapCodec {
    /** [model] as JSON; [withFrom] keeps the count-up start, which belongs to the moment it happened, not to the cache. */
    fun encode(model: SnapModel, withFrom: Boolean): JSONObject = JSONObject().apply {
        put("layout", model.layout.name)
        if (model.hero.isNotEmpty()) put("hero", model.hero)
        model.number?.takeIf { it.isFinite() }?.let { put("number", it) }
        if (withFrom) model.from?.takeIf { it.isFinite() }?.let { put("from", it) }
        if (model.unit.isNotEmpty()) put("unit", model.unit)
        if (model.decimals != 0) put("decimals", model.decimals)
        if (model.duration) put("duration", true)
        if (model.labels.isNotEmpty()) put("labels", JSONArray(model.labels))
        if (model.title.isNotEmpty()) put("title", model.title)
        if (model.rows.isNotEmpty()) {
            put("rows", JSONArray().apply {
                model.rows.forEach { row ->
                    put(JSONObject().put("v", row.value).put("n", row.name).apply { row.change?.let { put("c", it) } })
                }
            })
        }
        if (model.day != 0) put("day", model.day)
        if (model.weekday != 0) put("weekday", model.weekday)
    }

    /** The model in [json], cleaned and capped; null when it isn't one. */
    fun decode(json: JSONObject?): SnapModel? {
        if (json == null) return null
        val layout = runCatching { SnapLayout.valueOf(json.optString("layout")) }.getOrNull() ?: return null
        fun text(key: String) = cleanText(json.optString(key, ""))
        fun number(key: String) = if (json.has(key)) json.optDouble(key).takeIf { it.isFinite() } else null
        val labels = json.optJSONArray("labels")?.let { a -> (0 until minOf(a.length(), 3)).map { cleanText(a.optString(it)) } }
        val rows = json.optJSONArray("rows")?.let { a ->
            (0 until minOf(a.length(), MAX_ROWS)).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                SnapRow(
                    cleanText(o.optString("v")),
                    cleanText(o.optString("n")),
                    if (o.has("c")) cleanText(o.optString("c")) else null
                )
            }
        }
        return SnapModel(
            layout = layout,
            hero = text("hero"),
            number = number("number"),
            from = number("from"),
            unit = text("unit"),
            decimals = json.optInt("decimals").coerceIn(0, 6),
            duration = json.optBoolean("duration"),
            labels = labels.orEmpty().filter { it.isNotEmpty() },
            title = text("title"),
            rows = rows.orEmpty(),
            day = json.optInt("day").coerceIn(0, 31),
            weekday = json.optInt("weekday").coerceIn(0, 7)
        )
    }

    /** [decode] of a JSON string; null when it isn't valid. */
    fun decode(json: String?): SnapModel? = json?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }
}
