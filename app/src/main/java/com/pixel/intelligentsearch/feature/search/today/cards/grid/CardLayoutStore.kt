package com.pixel.intelligentsearch.feature.search.today.cards.grid

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.pixel.intelligentsearch.feature.search.today.cards.CARD_SHAPES
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardForm
import com.pixel.intelligentsearch.feature.search.today.cards.CardFrame
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/*
 * Where Discover's layout is kept: an app-private preferences file of its own, so it stays out of the settings the
 * app's own backup carries.
 *
 * NG Designs
 */

/** The preferences file Discover's layout is kept in. */
private const val FILE = "discover_cards"
private const val KEY = "layout"
private const val VERSION = 2

/**
 * Discover's card layout, kept as JSON in its own app-private preferences file. [layout] starts at
 * [DiscoverLayout.DEFAULT] and takes what was saved once it has been read off the main thread. Saved data that can't be
 * read, or isn't a layout, falls back to the default; a built-in part the saved layout doesn't know (one added to the
 * page since) is added at the end, full width.
 *
 * Nothing written before the saved layout is read may replace it: [update] (a whole layout, made from what the page
 * showed) is ignored until then, and [edit] (a change, such as a card added when a widget's setup page answers just
 * after the page comes back) waits and is applied to the saved layout.
 */
internal class CardLayoutStore(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val state = MutableStateFlow(DiscoverLayout.DEFAULT)
    private val lock = Any()
    private var loaded = false
    private val waiting = ArrayList<(DiscoverLayout) -> DiscoverLayout>()

    /** The layout now: the default until the saved one has been read. */
    val layout: StateFlow<DiscoverLayout> = state.asStateFlow()

    /** Whether the saved layout has been read, so [layout] is the user's own. */
    val isLoaded: Boolean get() = synchronized(lock) { loaded }

    init {
        scope.launch {
            val saved = decodeLayout(prefs().getString(KEY, null))
            val edited = synchronized(lock) {
                var next = saved
                for (change in waiting) next = change(next)
                val any = waiting.isNotEmpty()
                waiting.clear()
                loaded = true
                state.value = normalizedLayout(next)
                any
            }
            if (edited) save()
        }
    }

    /** Makes [layout] Discover's layout, tidied ([normalizedLayout]) and saved; ignored until the saved one is read. */
    fun update(layout: DiscoverLayout) {
        synchronized(lock) {
            if (!loaded) return
            state.value = normalizedLayout(layout)
        }
        save()
    }

    /** Applies [change] to the layout, now or, before the saved layout is read, to it once it is. */
    fun edit(change: (DiscoverLayout) -> DiscoverLayout) {
        synchronized(lock) {
            if (!loaded) {
                waiting += change
                return
            }
            state.value = normalizedLayout(change(state.value))
        }
        save()
    }

    /** Writes run one at a time, in order, each saving the newest layout. */
    private fun save() {
        scope.launch { prefs().edit { putString(KEY, encodeLayout(state.value)) } }
    }

    private fun prefs() = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

@SuppressLint("StaticFieldLeak") // the application context, which lives as long as the process
@Volatile
private var instance: CardLayoutStore? = null

/** The app's one [CardLayoutStore]. */
internal fun cardLayoutStore(context: Context): CardLayoutStore =
    instance ?: synchronized(CardLayoutStore::class.java) {
        instance ?: CardLayoutStore(context.applicationContext).also { instance = it }
    }

/** A new id for one of the user's own cards, unique for the card's life. */
internal fun newCardId(): String = "card-" + UUID.randomUUID().toString()

/** The least a card's place can be, as a fraction of the page's width, and the most a box can be tall (dp). */
private const val MIN_WIDTH = 0.1f
private const val MAX_BOX_DP = 900f

/** The most space a card may leave above itself, in dp. */
private const val MAX_GAP_DP = 2_000f

/**
 * [layout] made whole: one card per id and per built-in kind, frames within the page, known shapes only, the shape
 * form only for kinds with a shaped design, site cards with a url and app cards with a widget, a kind both on the page
 * and hidden kept on the page, and every built-in kind either on the page or hidden; a kind in neither is added at the
 * end, full width.
 */
internal fun normalizedLayout(layout: DiscoverLayout): DiscoverLayout {
    val ids = HashSet<String>()
    val kinds = HashSet<CardKind>()
    val cards = ArrayList<CardSpec>()
    for (card in layout.cards) {
        if (card.kind.builtIn && card.kind in kinds) continue
        if (card.kind == CardKind.SITE && card.url.isNullOrBlank()) continue
        if (card.kind == CardKind.APP && card.widgetId == 0) continue
        val id = if (card.id.isBlank() || card.id in ids) (if (card.kind.builtIn) card.kind.key else newCardId()) else card.id
        if (id in ids) continue
        ids += id
        kinds += card.kind
        cards += card.copy(
            id = id,
            frame = tidyFrame(card.frame),
            form = if (card.kind.shaped) card.form else CardForm.CARD,
            shape = card.shape.takeIf { it in CARD_SHAPES } ?: CARD_SHAPES.first()
        )
    }
    val hidden = layout.hidden.filter { it.builtIn && it !in kinds }.toSet()
    for (kind in CardKind.PAGE_ORDER) {
        if (kind !in kinds && kind !in hidden && kind.key !in ids) {
            cards += CardSpec(id = kind.key, kind = kind)
            ids += kind.key
        }
    }
    return DiscoverLayout(cards, hidden)
}

/** [frame] within the page: a width from a tenth to all of it, starting where the card still fits. */
private fun tidyFrame(frame: CardFrame): CardFrame {
    fun Float.orZero() = if (isNaN()) 0f else this
    val width = frame.width.orZero().coerceIn(MIN_WIDTH, 1f)
    return CardFrame(
        x = frame.x.orZero().coerceIn(0f, 1f - width),
        width = width,
        height = frame.height.orZero().coerceIn(0f, MAX_BOX_DP),
        gap = frame.gap.orZero().coerceIn(0f, MAX_GAP_DP)
    )
}

/** [layout] as the JSON the store keeps. */
internal fun encodeLayout(layout: DiscoverLayout): String {
    val cards = JSONArray()
    for (card in layout.cards) {
        cards.put(
            JSONObject().apply {
                put("id", card.id)
                put("kind", card.kind.key)
                put("x", card.frame.x.toDouble())
                put("width", card.frame.width.toDouble())
                if (card.frame.height > 0f) put("height", card.frame.height.toDouble())
                if (card.frame.gap > 0f) put("gap", card.frame.gap.toDouble())
                if (card.form == CardForm.SHAPE) put("form", "shape")
                put("shape", card.shape)
                if (card.private) put("private", true)
                if (card.widgetId != 0) put("widgetId", card.widgetId)
                card.provider?.let { put("provider", it) }
                card.url?.let { put("url", it) }
            }
        )
    }
    return JSONObject()
        .put("version", VERSION)
        .put("cards", cards)
        .put("hidden", JSONArray(layout.hidden.map { it.key }))
        .toString()
}

/**
 * The layout in [json] ([encodeLayout]'s form, or the grid of cells version 1 kept), made whole ([normalizedLayout]);
 * [DiscoverLayout.DEFAULT] when there is none or it can't be read. Cards of a kind this version doesn't know are left out.
 */
internal fun decodeLayout(json: String?): DiscoverLayout {
    if (json.isNullOrBlank()) return DiscoverLayout.DEFAULT
    return try {
        val root = JSONObject(json)
        val version = root.optInt("version", 0)
        if (version > VERSION) return DiscoverLayout.DEFAULT
        val list = root.getJSONArray("cards")
        val cells = CellCursor()
        val cards = (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val kind = CardKind.of(o.optString("kind")) ?: return@mapNotNull null
            val (frame, form) = if (version >= 2) {
                CardFrame(
                    x = o.optDouble("x", 0.0).toFloat(),
                    width = o.optDouble("width", 1.0).toFloat(),
                    height = o.optDouble("height", 0.0).toFloat(),
                    gap = o.optDouble("gap", 0.0).toFloat()
                ) to (if (o.optString("form") == "shape") CardForm.SHAPE else CardForm.CARD)
            } else {
                cells.place(kind, o)
            }
            CardSpec(
                id = o.optString("id"),
                kind = kind,
                frame = frame,
                form = form,
                shape = o.optString("shape", CARD_SHAPES.first()),
                private = o.optBoolean("private", false),
                widgetId = o.optInt("widgetId", 0),
                provider = o.optString("provider").takeIf { o.has("provider") && it.isNotBlank() },
                url = o.optString("url").takeIf { o.has("url") && it.isNotBlank() }
            )
        }
        val hiddenList = root.optJSONArray("hidden") ?: JSONArray()
        val hidden = (0 until hiddenList.length()).mapNotNull { CardKind.of(hiddenList.optString(it)) }.toSet()
        normalizedLayout(DiscoverLayout(cards, hidden))
    } catch (_: Exception) {
        DiscoverLayout.DEFAULT
    }
}

/**
 * Version 1's cards on a 4-across grid, laid left to right into freeform places: a full-width card on a row of its
 * own, narrower ones side by side while they fit. A card that was about square in a shaped kind becomes a shape.
 */
private class CellCursor {
    private var used = 0

    fun place(kind: CardKind, o: JSONObject): Pair<CardFrame, CardForm> {
        val natural = o.has("natural")
        val columns = (if (natural) o.optInt("natural", 4) else o.optInt("columns", 2)).coerceIn(1, 4)
        val rows = if (natural) 0 else o.optInt("rows", 2).coerceIn(1, 8)
        if (used + columns > 4) used = 0
        val x = used / 4f
        used = (used + columns) % 4
        val form = if (!natural && kind.shaped && columns < rows * 1.5f) CardForm.SHAPE else CardForm.CARD
        val height = if (!natural && !kind.builtIn && form == CardForm.CARD) rows * 90f else 0f
        return CardFrame(x, columns / 4f, height) to form
    }
}
