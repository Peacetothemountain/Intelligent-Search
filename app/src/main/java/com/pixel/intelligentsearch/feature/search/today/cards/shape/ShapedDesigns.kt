package com.pixel.intelligentsearch.feature.search.today.cards.shape

import android.graphics.Paint
import androidx.compose.material.icons.Icons
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.pixel.intelligentsearch.core.ui.icons.filled.Alarm
import com.pixel.intelligentsearch.core.ui.icons.filled.LocalShipping
import com.pixel.intelligentsearch.core.ui.icons.filled.Lock
import com.pixel.intelligentsearch.core.ui.icons.filled.Timer
import com.pixel.intelligentsearch.feature.search.today.cards.ShapedContent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The shaped designs of Discover's own parts, each drawn on its 180-unit square round whatever shape the card has:
 * edge gauges along the outline, curved labels inside it, count-ups and the living light, as the approved design
 * draws them. App and website cards are in AppSlots.kt.
 *
 * NG Designs
 */

/** The degree sign and anything after a temperature's number ("°", "°F"), and the number itself, if it has one. */
internal fun splitNumber(s: String): Pair<Int, String>? {
    val m = Regex("^\\s*(-?\\d+)(.*)$").find(s) ?: return null
    val n = m.groupValues[1].toIntOrNull() ?: return null
    return n to m.groupValues[2]
}

/** The battery: its level as an edge gauge counting up round the outline, the living light, and a shimmer while charging. */
internal fun Stage.battery(c: ShapedContent.Battery) {
    fill(colors.card)
    living(colors.acc, 0.16f)
    val level = c.level.coerceIn(0, 100) / 100f
    val shown = ((count ?: c.level.toDouble()) / 100.0).toFloat().coerceIn(0f, 1f)
    gauge(0.84f, colors.acc, 7f, shown)
    if (c.charging && ambient && countDone) {
        val ph = ((now % 2_500f) / 2_500f) * 1.5f * level
        val len = min(0.07f, ph)
        if (ph <= level) edgeSpan(0.84f, Color.White.copy(alpha = 0.7f), 7f, ph - len, ph)
    }
    content {
        percent((count ?: c.level.toDouble()).roundToInt())
        val detail = c.detail.ifBlank { if (c.charging) "Charging" else "" }
        bottomLine(if (c.charging) CardIcons.Bolt else null, colors.amber, detail)
    }
}

/** A big number with a small percent sign beside it, centered as a pair. */
private fun Stage.percent(value: Int) {
    val num = value.toString()
    val wn = width(num, 42f)
    val wp = width("%", 16f)
    val total = wn + 3f + wp
    val left = MID - total / 2f
    if (rtl) {
        text("%", left, MID - 2f, 16f, colors.sub, align = Paint.Align.LEFT, main = true)
        text(num, left + wp + 3f, MID + 12f, 42f, colors.on, align = Paint.Align.LEFT, maxWidth = 140f, main = true)
    } else {
        text(num, left, MID + 12f, 42f, colors.on, align = Paint.Align.LEFT, maxWidth = 140f, main = true)
        text("%", left + wn + 3f, MID - 2f, 16f, colors.sub, align = Paint.Align.LEFT, main = true)
    }
}

/** A line under the main value: an optional small icon and [text], centered as a pair. */
private fun Stage.bottomLine(icon: androidx.compose.ui.graphics.vector.ImageVector?, iconColor: Color, text: String) {
    if (text.isEmpty() || !legible(11.5f)) return
    val tw = min(width(text, 11.5f), 110f)
    val iw = if (icon != null && iconLegible(13f)) 16f else 0f
    val left = MID - (iw + tw) / 2f
    if (rtl) {
        if (iw > 0f && icon != null) icon(icon, left + tw + 3f + 6.5f, MID + 30f, 13f, iconColor)
        text(text, left + tw, MID + 34f, 11.5f, colors.sub, align = Paint.Align.RIGHT, maxWidth = 110f)
    } else {
        if (iw > 0f && icon != null) icon(icon, left + 6.5f, MID + 30f, 13f, iconColor)
        text(text, left + iw, MID + 34f, 11.5f, colors.sub, align = Paint.Align.LEFT, maxWidth = 110f)
    }
}

/** The weather: a warm glow, the hours ahead as a ring of colored arcs drawing in one by one, the temperature counting up. */
internal fun Stage.weather(c: ShapedContent.Weather) {
    clipped {
        scope.drawRect(colors.card, Offset.Zero, Size(DESIGN, DESIGN))
        glow(colors.amber, 0.45f, 0.72f, 0.2f, 0.85f, middle = Color(0xFFC9845A) to 0.14f)
    }
    val n = min(8, min(c.hours.size, c.rain.size.takeIf { it > 0 } ?: c.hours.size))
    if (n > 0) {
        val r = outline.inner * 0.8f
        val lo = c.hours.take(n).min()
        val hi = c.hours.take(n).max()
        val seg = 270f / n
        val cool = Color(0xFF9EC3F0)
        for (k in 0 until n) {
            val a0 = 225f + k * seg + 3f
            val a1 = 225f + (k + 1) * seg - 3f
            val rain = c.rain.getOrElse(k) { false }
            val col = if (rain) colors.rain else mix(cool, colors.amber, if (hi > lo) (c.hours[k] - lo).toFloat() / (hi - lo) else 0.5f)
            val f = p(150f + k * 90f, 500f)
            if (f <= 0f) continue
            scope.drawArc(dim(col), a0 - 90f, (a1 - a0) * f, false, Offset(MID - r, MID - r), Size(2 * r, 2 * r), style = Stroke(6f, cap = StrokeCap.Round))
        }
        scope.drawCircle(dim(colors.on), 4f, dialPoint(228f, r))
    }
    content {
        icon(c.icon, MID, MID - 32f, 24f, colors.amber)
        val parts = splitNumber(c.temperature)
        val temp = if (!countDone && parts != null) "${(count ?: parts.first.toDouble()).roundToInt()}${parts.second}" else c.temperature
        text(temp, MID + 3f, MID + 14f, 38f, colors.on, maxWidth = 120f, main = true)
        text(c.headline, MID, MID + 34f, 11.5f, if (c.rain.take(max(n, 1)).any { it }) colors.rain else colors.sub, maxWidth = 118f)
    }
}

/** A timer: how far through the minute it is as an edge gauge, the reading in the middle. */
internal fun Stage.timer(c: ShapedContent.Timer) {
    fill(colors.card)
    gauge(0.86f, colors.acc, 6f, c.sweep.coerceIn(0f, 1f) * p(150f, 900f))
    content {
        icon(Icons.Filled.Timer, MID, MID - 28f, 18f, colors.acc)
        text(c.reading, MID, MID + 12f, 36f, colors.on, maxWidth = 130f, main = true)
        text(c.label, MID, MID + 30f, 11f, colors.sub, maxWidth = 110f)
    }
}

/**
 * The next alarm: a night glow rising from the bottom, a 24-hour dial with the hours from now to the alarm sweeping
 * round it from warm to cool, and a bell where it rings. [nowDegrees] is now on the dial (noon at the top).
 */
internal fun Stage.alarm(c: ShapedContent.Alarm, nowDegrees: Float) {
    clipped {
        scope.drawRect(
            Brush.radialGradient(0f to colors.night, 0.7f to colors.cardSolid, 1f to colors.cardSolid, center = Offset(MID, 0.85f * DESIGN), radius = 0.9f * DESIGN),
            Offset.Zero,
            Size(DESIGN, DESIGN),
            alpha = alpha
        )
    }
    val r = outline.inner * 0.8f
    val tick = outline.inner * 0.93f
    for (k in 0 until 48) {
        val big = k % 12 == 0
        scope.drawLine(
            dim(colors.on, if (big) 0.35f else 0.15f),
            dialPoint(k * 7.5f, tick - if (big) 7f else 3f),
            dialPoint(k * 7.5f, tick),
            strokeWidth = if (big) 1.6f else 1f,
            cap = StrokeCap.Round
        )
    }
    val a0 = nowDegrees
    val a1 = a0 + (c.fraction.takeIf { !it.isNaN() } ?: 0f).coerceIn(0.02f, 1f) * 360f
    val segments = 64
    val v = p(150f, 1200f)
    for (k in 0 until segments) {
        val f = k.toFloat() / segments
        if (f >= v) break
        val from = a0 + (a1 - a0) * f
        val to = a0 + (a1 - a0) * (k + 1) / segments + 0.6f
        val col = if (f < 0.2f) mix(colors.amber, colors.rose, f / 0.2f) else mix(colors.rose, colors.acc, min(1f, (f - 0.2f) / 0.5f))
        scope.drawArc(dim(col), from - 90f, to - from, false, Offset(MID - r, MID - r), Size(2 * r, 2 * r), style = Stroke(5f))
    }
    scope.drawCircle(dim(colors.on), 4.5f, dialPoint(a0, r))
    if (v >= 1f) {
        val bell = dialPoint(a1, r)
        scope.drawCircle(dim(colors.acc), 9.5f, bell)
        icon(Icons.Filled.Alarm, bell.x, bell.y, 12f, colors.ink, keep = true)
    }
    content {
        text(c.day, MID, MID - 20f, 11f, colors.sub, maxWidth = 110f)
        text(c.time, MID, MID + 14f, 36f, colors.on, maxWidth = 120f, main = true)
        text(c.until, MID, MID + 32f, 11f, colors.sub, maxWidth = 110f)
    }
}

/** A package on its way: the truck, the headline and the detail curving along the bottom. */
internal fun Stage.delivery(c: ShapedContent.Delivery) {
    fill(colors.card)
    clipped { glow(colors.acc, 0.14f, 0.5f, 0.3f, 0.7f) }
    content {
        icon(Icons.Filled.LocalShipping, MID, MID - 26f, 26f, colors.acc)
        val lines = lines(c.headline, 15f, 124f, 2, W_MEDIUM)
        lines.forEachIndexed { i, line ->
            val y = if (lines.size == 1) MID + 10f else MID + 4f + i * 17f
            text(line, MID, y, 15f, colors.on, weight = W_MEDIUM, maxWidth = 124f, main = true)
        }
        curved(c.detail, 10.5f, colors.sub, top = false, inset = 0.74f)
    }
}

/**
 * What is playing: the artwork as a record spinning while it plays, a shade at the bottom, the progress as an edge
 * gauge, Play or Pause in the middle and the title curving along the bottom.
 */
internal fun Stage.media(c: ShapedContent.Media) {
    // The record turns with the card's ambient time, which holds still (at no turn at all) without an ambient slot.
    val turn = if (c.playing) (now % AMBIENT_PERIOD_MS) / AMBIENT_PERIOD_MS * 7f * 360f else 0f
    clipped {
        scope.rotate(turn, Offset(MID, MID)) {
            val art = c.artwork
            if (art != null) {
                image(art, MID, MID, DESIGN)
            } else {
                drawCircle(
                    Brush.radialGradient(0f to colors.acc, 0.6f to mix(colors.acc, Color.Black, 0.55f), 1f to mix(colors.acc, Color.Black, 0.8f), center = Offset(0.4f * DESIGN, 0.35f * DESIGN), radius = 0.8f * DESIGN),
                    110f,
                    Offset(MID, MID),
                    alpha = alpha
                )
            }
            var g = 28f
            while (g < 92f) {
                drawCircle(Color.Black.copy(alpha = 0.13f * alpha), g, Offset(MID, MID), style = Stroke(0.8f))
                g += 5f
            }
        }
        scope.drawRect(
            Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f), startY = 0f, endY = DESIGN),
            Offset.Zero,
            Size(DESIGN, DESIGN),
            alpha = alpha
        )
    }
    c.progress?.let { gauge(0.9f, Color.White, 4f, it.coerceIn(0f, 1f) * p(100f, 900f), trackAlpha = 0.19f, track = Color.White) }
    content {
        if (iconLegible(42f)) {
            scope.drawCircle(dim(colors.acc), 21f, Offset(MID, MID))
            if (c.playing) {
                scope.drawRoundRect(dim(colors.ink), Offset(MID - 6f, MID - 7f), Size(4f, 14f), androidx.compose.ui.geometry.CornerRadius(1.5f))
                scope.drawRoundRect(dim(colors.ink), Offset(MID + 2f, MID - 7f), Size(4f, 14f), androidx.compose.ui.geometry.CornerRadius(1.5f))
            } else {
                val play = Path().apply {
                    moveTo(MID - 4.5f, MID - 8f); lineTo(MID + 8f, MID); lineTo(MID - 4.5f, MID + 8f); close()
                }
                scope.drawPath(play, dim(colors.ink))
            }
        }
        curved(c.title, 12f, Color.White, top = false, inset = 0.74f, weight = W_MEDIUM, main = true)
    }
}

/** The word of the day curving over the top, its kind and meaning below, in the living light. */
internal fun Stage.word(c: ShapedContent.Word) {
    fill(colors.card)
    living(colors.acc, 0.14f)
    content {
        curved(c.word, 23f, colors.acc, top = true, inset = 0.62f, main = true)
        text(c.kind, MID, MID + 2f, 11f, colors.sub, maxWidth = 120f)
        lines(c.meaning, 12f, 120f, 2).forEachIndexed { i, line -> text(line, MID, MID + 22f + i * 15f, 12f, colors.on, maxWidth = 124f) }
    }
}

/** Days until something: a dot round the outline for each day (a heart on the last), the count, the name curving below. */
internal fun Stage.countdown(c: ShapedContent.Countdown) {
    fill(colors.card)
    clipped { glow(colors.rose, 0.26f, 0.5f, 0.62f, 0.6f) }
    val n = c.days.coerceIn(1, 30)
    forEachEdgePoint(0.83f, n) { k, at ->
        val a = p(200f + k * 28f, 260f)
        if (a <= 0f) return@forEachEdgePoint
        if (k == n - 1) {
            icon(CardIcons.Favorite, at.x, at.y, 12f, colors.rose.copy(alpha = a))
        } else {
            scope.drawCircle(dim(if (k == 0) colors.acc else colors.on.copy(alpha = 0.25f), a), 2.6f, at)
        }
    }
    content {
        if (c.days <= 0) {
            text("Today", MID, MID + 12f, 30f, colors.on, maxWidth = 120f, main = true)
        } else {
            text(if (c.days == 1) "DAY UNTIL" else "DAYS UNTIL", MID, MID - 22f, 10f, colors.sub, weight = W_SEMIBOLD, spacing = 1.4f)
            text("${(count ?: c.days.toDouble()).roundToInt()}", MID, MID + 18f, 46f, colors.on, maxWidth = 130f, main = true)
        }
        curved(c.name, 12.5f, colors.rose, top = false, inset = 0.72f, weight = W_MEDIUM)
    }
}

/** The Private cover in the shape: opaque stripes, a lock, "Private" and "Tap to show". Never a blur. */
internal fun Stage.privateCover(opacity: Float) {
    val before = alpha
    alpha = opacity
    clipped { stripes(colors.cover.copy(alpha = opacity), colors.on.copy(alpha = 0.07f * opacity)) }
    icon(Icons.Filled.Lock, MID, MID - 12f, 22f, colors.on)
    text("Private", MID, MID + 15f, 13f, colors.on, maxWidth = 130f, main = true)
    text("Tap to show", MID, MID + 31f, 10f, colors.sub, maxWidth = 130f)
    alpha = before
}
