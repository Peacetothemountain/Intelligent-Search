package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.material.icons.Icons
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import com.pixel.intelligentsearch.core.ui.icons.filled.Event
import com.pixel.intelligentsearch.core.ui.icons.filled.Forum
import com.pixel.intelligentsearch.core.ui.icons.filled.History
import com.pixel.intelligentsearch.feature.search.today.cards.ShapedContent
import kotlin.math.min

/*
 * The shaped designs of Discover's plans and follow-ups: the next event, the rest of the day, what to catch up on, the
 * last search and tomorrow, each drawn on its 180-unit square round whatever shape the card has, in the same hand as
 * the page's other shaped designs.
 *
 * NG Designs
 */

/** The next event: the hour before it filling round the outline, the time large, its name and how long until it. */
internal fun Stage.event(c: ShapedContent.Event) {
    fill(colors.card)
    clipped { glow(colors.acc, 0.2f, 0.5f, 0.18f, 0.75f) }
    gauge(0.85f, colors.acc, 6f, c.fraction.coerceIn(0f, 1f) * p(150f, 900f))
    content {
        icon(Icons.Filled.Event, MID, MID - 36f, 16f, colors.acc)
        text(c.time, MID, MID + 4f, 30f, colors.on, maxWidth = 130f, main = true)
        text(c.title, MID, MID + 24f, 12.5f, colors.on, weight = W_MEDIUM, maxWidth = 118f)
        curved(c.until, 11f, colors.acc, top = false, inset = 0.74f, weight = W_MEDIUM)
    }
}

/** The rest of the day: a 24-hour dial with a dot at each thing left, how many there are, and the next one along the bottom. */
internal fun Stage.agenda(c: ShapedContent.Agenda) {
    fill(colors.card)
    val r = outline.inner * 0.82f
    for (k in 0 until 24) {
        scope.drawCircle(dim(colors.on, 0.12f), 1.4f, dialPoint(k * 15f, r))
    }
    c.hours.forEachIndexed { k, hour ->
        val a = p(200f + k * 120f, 360f)
        if (a <= 0f) return@forEachIndexed
        val at = dialPoint((hour % 24f) * 15f, r)
        scope.drawCircle(dim(if (k == 0) colors.acc else colors.amber, a), 5f, at)
    }
    content {
        text(if (c.count == 1) "THING LEFT" else "THINGS LEFT", MID, MID - 24f, 10f, colors.sub, weight = W_SEMIBOLD, spacing = 1.4f)
        text("${c.count}", MID, MID + 16f, 44f, colors.on, maxWidth = 120f, main = true)
        text("Next ${c.next}", MID, MID + 34f, 11.5f, colors.sub, maxWidth = 112f)
        curved(c.nextTitle, 11.5f, colors.acc, top = false, inset = 0.74f, weight = W_MEDIUM)
    }
}

/** What to catch up on: a soft glow, how many, and who the latest is from. */
internal fun Stage.catchUp(c: ShapedContent.CatchUp) {
    fill(colors.card)
    clipped { glow(colors.acc, 0.18f, 0.5f, 0.32f, 0.7f) }
    val n = min(c.count, 12)
    forEachEdgePoint(0.84f, 12) { k, at ->
        val a = p(150f + k * 40f, 260f)
        if (a > 0f) scope.drawCircle(dim(if (k < n) colors.acc else colors.on, if (k < n) a else 0.1f * a), 2.6f, at)
    }
    content {
        icon(Icons.Filled.Forum, MID, MID - 34f, 18f, colors.acc)
        text("${c.count}", MID, MID + 12f, 44f, colors.on, maxWidth = 120f, main = true)
        text("to catch up on", MID, MID + 30f, 11f, colors.sub, maxWidth = 112f)
        curved(c.who, 11.5f, colors.on, top = false, inset = 0.74f, weight = W_MEDIUM)
    }
}

/** The last search, in the living light: the clock-arrow icon, the words, and "Search again" along the bottom. */
internal fun Stage.search(c: ShapedContent.Search) {
    fill(colors.card)
    living(colors.acc, 0.13f)
    content {
        icon(Icons.Filled.History, MID, MID - 30f, 20f, colors.acc)
        val lines = lines(c.query, 15f, 124f, 2, W_MEDIUM)
        lines.forEachIndexed { i, line ->
            val y = if (lines.size == 1) MID + 8f else MID + 2f + i * 18f
            text(line, MID, y, 15f, colors.on, weight = W_MEDIUM, maxWidth = 124f, main = true)
        }
        curved("Search again", 10.5f, colors.sub, top = false, inset = 0.74f)
    }
}

/** Tomorrow: the day curving over the top, its weather, and the first thing in it. */
internal fun Stage.tomorrow(c: ShapedContent.Tomorrow) {
    fill(colors.card)
    clipped { glow(colors.night, 0.5f, 0.5f, 0.95f, 0.8f) }
    val r = outline.inner * 0.8f
    val sweep = p(150f, 900f)
    if (sweep > 0f) {
        val from = dialPoint(-60f, r)
        val to = dialPoint(60f, r)
        scope.drawLine(dim(colors.amber, 0.35f), Offset(from.x, from.y), Offset(from.x + (to.x - from.x) * sweep, to.y), 2f, cap = StrokeCap.Round)
    }
    content {
        curved(c.day, 13f, colors.sub, top = true, inset = 0.7f, weight = W_SEMIBOLD, spacing = 0.6f)
        c.icon?.let { icon(it, MID, MID - 12f, 26f, colors.amber) }
        if (c.temperatures.isNotBlank()) {
            text(c.temperatures, MID, MID + 20f, 20f, colors.on, maxWidth = 130f, main = true)
            text(c.detail, MID, MID + 38f, 11f, colors.sub, maxWidth = 112f)
        } else {
            text(c.detail, MID, MID + 20f, 15f, colors.on, weight = W_MEDIUM, maxWidth = 124f, main = true)
        }
    }
}
