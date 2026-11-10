package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.compose.ui.unit.IntRect
import kotlin.math.abs

/*
 * Discover's freeform page, as plain math with no Compose in it. Every card has a place: a left edge and a width
 * anywhere across the page, and a height (its content's, or its own). Going down the list, each card falls up until it
 * meets a card before it that it sits under (any overlap across), or the top of the page, then stays its own gap below
 * that. So cards can sit anywhere, side by side or staggered, never overlap, and a card that is hidden or has nothing to
 * show today leaves no hole: what was under it moves up.
 *
 * Places are in pixels and include half the space between cards on every side, so places touch where cards have the
 * usual space between them.
 *
 * NG Designs
 */

/** One card's place: [left] and [width] across the page, its [height], and the extra [gap] it keeps above it. */
internal class FlowCard(val id: String, val left: Int, val width: Int, val height: Int, val gap: Int = 0) {
    val right: Int get() = left + width
}

/** Where each card went ([rects], by id), the top of the card it fell against ([floors]), and the page's [height]. */
internal class Flow(val rects: Map<String, IntRect>, val floors: Map<String, Int>, val height: Int)

/** Whether a place from [left] to [right] sits under or over [rect]: they share more than a pixel across. */
internal fun overlapsAcross(rect: IntRect, left: Int, right: Int): Boolean = rect.left < right - 1 && left < rect.right - 1

/**
 * Lays [cards] down the page in order. The card [free], if any, is placed at [freeTop] instead of its gap, as near to
 * it as the cards above allow; less than [snap] below the card it falls against, it sits right against it.
 */
internal fun flowCards(cards: List<FlowCard>, free: String? = null, freeTop: Int = 0, snap: Int = 0): Flow {
    val rects = LinkedHashMap<String, IntRect>(cards.size)
    val floors = HashMap<String, Int>(cards.size)
    var height = 0
    for (card in cards) {
        var floor = 0
        for (rect in rects.values) {
            if (rect.bottom > floor && overlapsAcross(rect, card.left, card.right)) floor = rect.bottom
        }
        val top = if (card.id == free) {
            val extra = (freeTop - floor).coerceAtLeast(0)
            floor + if (extra < snap) 0 else extra
        } else {
            floor + card.gap.coerceAtLeast(0)
        }
        val rect = IntRect(card.left, top, card.right, top + card.height)
        rects[card.id] = rect
        floors[card.id] = floor
        if (rect.bottom > height) height = rect.bottom
    }
    return Flow(rects, floors, height)
}

/**
 * Where a card being moved to [moving] goes among [others] (laid out without it as [rects]): before the first one it
 * sits over whose middle is lower than its own, or last.
 */
internal fun insertionIndex(others: List<FlowCard>, rects: Map<String, IntRect>, moving: IntRect): Int {
    val middle = (moving.top + moving.bottom) / 2
    for ((i, card) in others.withIndex()) {
        val rect = rects[card.id] ?: continue
        if (overlapsAcross(rect, moving.left, moving.right) && (rect.top + rect.bottom) / 2 > middle) return i
    }
    return others.size
}

/**
 * The page with one card [active] being moved or resized: the others laid out with it put where it would land if let go
 * at [activeTop], in [order]. [rects] are where everything goes (the active card's, where it would land), and
 * [activeGap] the gap it would keep above it.
 */
internal class Arranged(val order: List<String>, val rects: Map<String, IntRect>, val activeGap: Int, val height: Int)

/**
 * [cards] (in order, the active one among them with its place now) arranged around [active] ([Arranged]). Where it goes
 * among the others is judged against [reference], where they were as it was picked up, when given: against where they
 * are without it, the card under it would rise into its place and a sideways move would put it below that card.
 */
internal fun arrange(cards: List<FlowCard>, active: String?, activeTop: Int, snap: Int, reference: Map<String, IntRect>? = null): Arranged {
    val moving = cards.firstOrNull { it.id == active }
    if (moving == null) {
        val flow = flowCards(cards)
        return Arranged(cards.map { it.id }, flow.rects, 0, flow.height)
    }
    val others = cards.filter { it.id != active }
    val base = flowCards(others)
    val judged = if (reference != null) others.associate { it.id to (reference[it.id] ?: base.rects.getValue(it.id)) } else base.rects
    val at = insertionIndex(others, judged, IntRect(moving.left, activeTop, moving.right, activeTop + moving.height))
    val order = ArrayList(others).apply { add(at, moving) }
    val flow = flowCards(order, free = moving.id, freeTop = activeTop, snap = snap)
    val rect = flow.rects.getValue(moving.id)
    return Arranged(order.map { it.id }, flow.rects, rect.top - flow.floors.getValue(moving.id), flow.height)
}

/**
 * [items] in the order of their tops ([top], null for an item not on the page today, which stays right after the one
 * before it), keeping the order of any at the same height. Cards that sit over one another are already in this order,
 * so the page lays out the same, and a later move finds its place by height.
 */
internal fun <T> sortedDown(items: List<T>, top: (T) -> Int?): List<T> {
    var last = Int.MIN_VALUE
    val keys = items.map { item -> top(item)?.also { last = it } ?: last }
    return items.indices.sortedWith(compareBy({ keys[it] }, { it })).map { items[it] }
}

/** A value snapped to a line, and the [guides] (page x) it lined up with, if any. */
internal class Snapped(val value: Int, val guides: List<Int>)

/**
 * A card's left edge for [left] and [width] on a page [page] wide, pulled to the nearest line within [reach]: the page's
 * edges and middle, and the edges of [others] (lined up with them or just beside them).
 */
internal fun snapLeft(left: Int, width: Int, page: Int, others: Collection<IntRect>, reach: Int): Snapped {
    var best = left
    var bestDistance = reach + 1
    var guides = emptyList<Int>()
    fun consider(candidate: Int, guide: List<Int>) {
        if (candidate < 0 || candidate + width > page) return
        val d = abs(candidate - left)
        if (d < bestDistance) {
            best = candidate
            bestDistance = d
            guides = guide
        }
    }
    consider(0, listOf(0))
    consider(page - width, listOf(page))
    consider((page - width) / 2, listOf(page / 2))
    for (rect in others) {
        consider(rect.left, listOf(rect.left))
        consider(rect.right, listOf(rect.right))
        consider(rect.left - width, listOf(rect.left))
        consider(rect.right - width, listOf(rect.right))
    }
    return if (bestDistance <= reach) Snapped(best, guides) else Snapped(left, emptyList())
}

/**
 * A card's width for [width] on a page [page] wide, pulled within [reach] to the page's whole, half, thirds and quarters,
 * and to the widths of [others]; never under [least] or over [most] (the room it has to its right).
 */
internal fun snapWidth(width: Int, page: Int, others: Collection<IntRect>, reach: Int, least: Int, most: Int = page): Int {
    var best = width
    var bestDistance = reach + 1
    val candidates = listOf(page, page / 2, page / 3, page * 2 / 3, page / 4, page * 3 / 4) + others.map { it.width }
    for (candidate in candidates) {
        if (candidate < least || candidate > most) continue
        val d = abs(candidate - width)
        if (d < bestDistance) {
            best = candidate
            bestDistance = d
        }
    }
    return if (bestDistance <= reach) best else width
}
