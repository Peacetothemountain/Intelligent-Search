package com.pixel.intelligentsearch.feature.search.today.cards.widget

import com.pixel.intelligentsearch.feature.search.today.cards.AppCardData
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardState
import com.pixel.intelligentsearch.feature.search.today.cards.SnapLayout
import com.pixel.intelligentsearch.feature.search.today.cards.SnapModel

/*
 * NG Designs
 */

/** How long a number that turned into something else is held before the new reading is believed. */
internal const val HOLD_MS = 30_000L

/**
 * What the app keeps for one app card: what the card shows ([data]), the widget's [provider], the [fingerprint] of the
 * views the last good model was read from, a held change ([pendingKey], first seen at [pendingAt]) and whether the
 * widget still has to be set up in its app ([needsSetup]).
 */
internal data class CardRecord(
    val data: AppCardData,
    val provider: String,
    val fingerprint: String = "",
    val pendingKey: String? = null,
    val pendingAt: Long = 0,
    val needsSetup: Boolean = false
)

/** What kind of value [this] model shows, for telling when a number turns into something that isn't one. */
internal fun SnapModel.kind(): String = when (layout) {
    SnapLayout.HERO -> when {
        duration -> "duration"
        number != null -> "number"
        else -> "text"
    }
    else -> layout.name
}

/**
 * [record] after the read [read] at [now], or null when nothing changes (so nothing is shown or saved again). The
 * rules the card can rely on:
 * - A read that can't be trusted (loading, an error, a placeholder, the widget's initial layout, only pictures, a
 *   failed apply) never replaces the last good model: the card keeps it and is marked stale. With nothing good yet,
 *   it is still updating.
 * - A number that turns into something that isn't one is how a loading or error state looks in a language the reader
 *   doesn't know ("Cargando…"). The old number is held until the new reading is seen again at least [HOLD_MS] later,
 *   kept in [CardRecord.pendingKey] so it holds across opens.
 * - A new number of the same kind and unit counts up from the old one ([SnapModel.from]).
 * - An identical read changes nothing.
 */
internal fun applyRead(record: CardRecord, read: SnapCandidate, now: Long): CardRecord? {
    val last = record.data.model
    val state = record.data.state
    fun shown(newState: AppCardState, model: SnapModel? = last): CardRecord? =
        if (newState == state && model == last) null else record.copy(data = record.data.copy(state = newState, model = model))
    fun untrusted(): CardRecord? = when {
        record.needsSetup -> shown(AppCardState.SETUP)
        last != null -> shown(AppCardState.STALE)
        else -> shown(AppCardState.UPDATING)
    }
    return when (read.verdict) {
        ReadVerdict.REMOVED -> if (state == AppCardState.REMOVED && last == null) null else
            record.copy(data = record.data.copy(state = AppCardState.REMOVED, model = null), fingerprint = "", pendingKey = null, pendingAt = 0)
        ReadVerdict.SETUP -> shown(AppCardState.SETUP)
        // Nothing new: keep what's shown.
        ReadVerdict.EMPTY -> if (last != null) null else untrusted()
        ReadVerdict.WAITING, ReadVerdict.PLACEHOLDER, ReadVerdict.INITIAL, ReadVerdict.PICTURE -> untrusted()
        ReadVerdict.GOOD -> good(record, read.model ?: return untrusted(), read.fingerprint, now)
    }
}

private fun good(record: CardRecord, model: SnapModel, fingerprint: String, now: Long): CardRecord? {
    val last = record.data.model
    var m = model
    if (last != null) {
        if (record.data.state == AppCardState.READY && fingerprint == record.fingerprint && m.copy(from = null) == last.copy(from = null)) {
            return null
        }
        val was = last.kind()
        if ((was == "number" || was == "duration") && m.kind() != was) {
            val key = "$fingerprint|${m.layout}|${m.kind()}"
            val held = if (key == record.pendingKey) record else record.copy(pendingKey = key, pendingAt = now)
            if (now - held.pendingAt < HOLD_MS) {
                return if (held == record && record.data.state == AppCardState.STALE) null
                else held.copy(data = held.data.copy(state = AppCardState.STALE))
            }
        }
        val old = last.number
        val new = m.number
        if (old != null && new != null && old != new && m.kind() == was && m.unit == last.unit) m = m.copy(from = old)
    }
    return record.copy(
        data = record.data.copy(state = AppCardState.READY, model = m, updatedAt = now),
        fingerprint = fingerprint,
        pendingKey = null,
        pendingAt = 0,
        needsSetup = false
    )
}
