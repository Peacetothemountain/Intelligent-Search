package com.pixel.intelligentsearch.feature.search.today.cards.shape

import android.graphics.Paint
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import com.pixel.intelligentsearch.core.ui.icons.outlined.Tune
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardState
import com.pixel.intelligentsearch.feature.search.today.cards.ShapedContent
import com.pixel.intelligentsearch.feature.search.today.cards.SnapLayout
import com.pixel.intelligentsearch.feature.search.today.cards.SnapModel
import com.pixel.intelligentsearch.feature.search.today.cards.SnapRow
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The one slot renderer every app card draws with, in any shape: the widget's snapshot fills a few slots (a hero
 * value, a list, a month, a clock) and the shape's class picks how they sit round it. Round shapes curve the app's name
 * along the bottom; lobed shapes give each row of a list a lobe of its own; spiky shapes keep to their smaller middle.
 * Also the website card's shaped design.
 *
 * NG Designs
 */

/**
 * What an app or site card shows besides its data, worked out as the card composes: the app's [icon], the time now
 * for a clock ([clock], in the phone's format without AM or PM), how long ago the data was read ([ago], "2 hr ago"),
 * the week's [weekLetters] from the locale's first day and [today]'s place among them, and the [number] format for the
 * hero's count-up.
 */
@Immutable
internal class CardExtras(
    val icon: ImageBitmap?,
    val clock: String,
    val ago: String,
    val weekLetters: List<String>,
    val today: Int,
    val number: NumberFormat?,
    val locale: Locale
)

/** [v] written as the widget writes its number: minutes as "1h 5m", others with its decimals and unit. */
internal fun snapNumber(m: SnapModel, v: Double, format: NumberFormat?): String {
    if (m.duration) {
        val total = v.roundToInt().coerceAtLeast(0)
        val h = total / 60
        val mm = total % 60
        return if (h > 0) "${h}h ${mm}m" else "${mm}m"
    }
    val n = format?.format(v) ?: String.format(Locale.ROOT, "%.${m.decimals.coerceIn(0, 6)}f", v)
    return n + m.unit
}

/** Whether a change reads as a fall ("-0.46%", or with a true minus sign). */
internal fun isFall(change: String): Boolean = change.trimStart().let { it.startsWith("-") || it.startsWith("−") }

/** "Updated 2 hr ago", or "Updated just now". */
internal fun updatedNote(ago: String): String = if (ago == "Now") "Updated just now" else "Updated $ago"

/** An app card in any shape, in any of its states. */
internal fun Stage.app(c: ShapedContent.App, x: CardExtras) {
    val d = c.data
    val m = d.model
    val accent = Color(d.accent)
    val hasData = (d.state == AppCardState.READY || d.state == AppCardState.STALE) && m != null
    fill(colors.card)
    if (!hasData) {
        appState(c, x, accent)
        return
    }
    val stale = d.state == AppCardState.STALE
    val cls = CardShapes.shapeClass(shapeName)
    val tight = CardShapes.isTight(shapeName)
    val note = updatedNote(x.ago)
    living(accent, if (stale) 0.1f else 0.24f)
    val outer = alpha
    val dimmed = if (stale) 0.78f else 1f
    var lobedList = false
    alpha = outer * dimmed * p(80f, 500f)
    content {
        when (m.layout) {
            SnapLayout.HERO -> {
                val spiky = cls == ShapeClass.SPIKY
                if (!spiky) appIcon(x.icon, accent, MID, MID - 32f, 20f)
                val value = count
                val hero = if (!countDone && value != null && m.number != null) snapNumber(m, value, x.number) else m.hero
                text(hero, MID, MID + if (spiky) 8f else 10f, if (hero.length > 5) 28f else 34f, colors.on, maxWidth = 128f, main = true)
                val labelY = MID + if (spiky) 26f else 30f
                if (stale && tight) {
                    text(note, MID, labelY, 11f, colors.amber, maxWidth = 116f)
                } else {
                    m.labels.firstOrNull()?.let { text(it, MID, labelY, 11.5f, colors.sub, maxWidth = 116f) }
                }
            }
            SnapLayout.LIST -> {
                val title = m.title.ifBlank { d.appLabel }.uppercase(x.locale)
                when {
                    m.rows.isEmpty() -> {
                        icon(CardIcons.TaskAlt, MID, MID - 16f, 24f, accent)
                        text("All done", MID, MID + 14f, 15f, colors.on, maxWidth = 124f, main = true)
                        text(m.title, MID, MID + 31f, 11f, colors.sub, maxWidth = 116f)
                    }
                    cls == ShapeClass.LOBED -> {
                        // The rows fill the lobes, so the app's name sits under the title rather than curving below.
                        lobedList = true
                        text(title, MID, MID - 1f, 9.5f, colors.sub, weight = W_SEMIBOLD, maxWidth = 70f, spacing = 0.8f)
                        text(d.appLabel, MID, MID + 11f, 8.5f, colors.mut, maxWidth = 70f)
                    }
                    else -> {
                        text(title, MID, MID - 28f, 9.5f, colors.sub, weight = W_SEMIBOLD, maxWidth = 110f, spacing = 0.8f)
                        m.rows.take(if (cls == ShapeClass.SPIKY) 2 else 3).forEachIndexed { i, row -> listRow(row, MID - 4f + i * 20f, i == 0) }
                    }
                }
            }
            SnapLayout.MONTH -> {
                text(m.title.uppercase(x.locale), MID, MID - 26f, 10f, colors.sub, weight = W_SEMIBOLD, maxWidth = 120f, spacing = 1.2f)
                text(if (m.day > 0) m.day.toString() else "", MID, MID + 12f, 40f, colors.on, maxWidth = 100f, main = true)
                week(x, accent)
            }
            SnapLayout.CLOCK -> {
                appIcon(x.icon, accent, MID, MID - 30f, 18f)
                text(x.clock, MID, MID + 10f, 36f, colors.on, maxWidth = 130f, main = true)
                m.labels.firstOrNull()?.let { text(it, MID, MID + 30f, 11.5f, colors.sub, maxWidth = 116f) }
            }
        }
        // Tight middles have no room for the curved name: it steps aside (a stale hero's note took the label's line).
        if (!tight && (stale || !lobedList)) {
            curved(if (stale) note else d.appLabel, 10.5f, if (stale) colors.amber else colors.mut, top = false, inset = 0.74f, spacing = 0.4f)
        }
    }
    if (lobedList) {
        alpha = outer * dimmed * p(250f, 600f)
        lobeRows(m.rows)
    }
    alpha = outer
}

/** One row of a list in a round or spiky shape: its name (or value) at the start, its change at the end. */
private fun Stage.listRow(row: SnapRow, y: Float, first: Boolean) {
    val change = row.change?.takeIf { it.isNotBlank() }
    val cw = if (change != null) min(width(change, 11f), 40f) + 4f else 0f
    val start = if (rtl) MID + 42f else MID - 42f
    val end = if (rtl) MID - 42f else MID + 42f
    text(
        row.name.ifBlank { row.value }, start, y, 12f, colors.on,
        align = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT, maxWidth = 84f - cw, main = first
    )
    if (change != null) {
        text(change, end, y, 11f, if (isFall(change)) colors.neg else colors.pos, align = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT, maxWidth = 40f)
    }
}

/** A list in a lobed shape: one row per lobe, reading from the top start lobe across and down. */
private fun Stage.lobeRows(rows: List<SnapRow>) {
    val lobes = CardShapes.lobes(shapeName)
    if (lobes.isEmpty()) return
    val wanted = if (rtl) floatArrayOf(45f, 315f, 135f, 225f) else floatArrayOf(315f, 45f, 225f, 135f)
    val used = BooleanArray(lobes.size)
    val r = min(58f, outline.inner * 0.78f + 8f)
    for (i in wanted.indices) {
        val row = rows.getOrNull(i) ?: break
        var best = -1
        var bestGap = Float.MAX_VALUE
        for (j in lobes.indices) {
            if (used[j]) continue
            val gap = abs(((lobes[j] - wanted[i] + 540f) % 360f) - 180f)
            if (gap < bestGap) { bestGap = gap; best = j }
        }
        if (best < 0) break
        used[best] = true
        val at = dialPoint(lobes[best], r)
        text(row.name, at.x, at.y - 8f, 9f, colors.sub, maxWidth = 52f)
        text(row.value.replace(Regex("\\.\\d+$"), ""), at.x, at.y + 6f, 12f, colors.on, maxWidth = 56f, main = true)
        row.change?.takeIf { it.isNotBlank() }?.let { text(it, at.x, at.y + 19f, 9f, if (isFall(it)) colors.neg else colors.pos, maxWidth = 52f) }
    }
}

/** The week under a month's day: a letter per day, today on the card's accent. */
private fun Stage.week(x: CardExtras, accent: Color) {
    if (x.weekLetters.size != 7) return
    val showLetters = legible(9f)
    for (i in 0 until 7) {
        val slot = if (rtl) 6 - i else i
        val cx = MID - 42f + slot * 14f
        if (i == x.today) scope.drawCircle(dim(accent), 6.5f, Offset(cx, MID + 30f))
        if (showLetters) {
            text(
                x.weekLetters[i], cx, MID + 33.3f, 9f, if (i == x.today) Color(0xFF1B1C21) else colors.sub,
                weight = if (i == x.today) W_BOLD else W_REGULAR, maxWidth = 14f
            )
        }
    }
}

/** The app's icon in a circle, or a dot of its accent when there is none. */
private fun Stage.appIcon(icon: ImageBitmap?, accent: Color, cx: Float, cy: Float, size: Float) {
    if (!iconLegible(size)) return
    if (icon != null) {
        imageCircle(icon, cx, cy, size / 2f)
    } else {
        scope.drawCircle(dim(accent, 0.3f), size / 2f, Offset(cx, cy))
        scope.drawCircle(dim(accent), size / 4f, Offset(cx, cy))
    }
}

/**
 * An app card without data to show: getting its first read (a shimmer running along the edge), waiting for setup or
 * gone (a dashed edge), or paused with the work profile.
 */
private fun Stage.appState(c: ShapedContent.App, x: CardExtras, accent: Color) {
    val d = c.data
    val state = if (d.state == AppCardState.READY || d.state == AppCardState.STALE) AppCardState.UPDATING else d.state
    val color = if (state == AppCardState.REMOVED) colors.mut else accent
    when (state) {
        AppCardState.UPDATING -> if (ambient || (animate && t < 2_800f)) {
            val phase = if (ambient) (now % 1_500f) / 1_500f else (t % 1_400f) / 1_400f
            val shimmer = color.copy(alpha = 0.9f)
            edgeSpan(0.86f, shimmer, 5f, phase, phase + 0.18f)
            if (phase + 0.18f > 1f) edgeSpan(0.86f, shimmer, 5f, 0f, phase + 0.18f - 1f)
        }
        AppCardState.SETUP, AppCardState.REMOVED ->
            edge(0.86f, color.copy(alpha = 0.8f), 2f, PathEffect.dashPathEffect(floatArrayOf(3f, 7f)))
        else -> Unit
    }
    val (title, sub) = when (state) {
        AppCardState.SETUP -> "Finish setup" to "Tap to choose"
        AppCardState.REMOVED -> "App removed" to "Tap to remove"
        AppCardState.PAUSED -> "Work apps paused" to "Tap to turn on"
        else -> "Getting the latest" to ""
    }
    val outer = alpha
    alpha = outer * p(80f, 500f)
    content {
        when (state) {
            AppCardState.SETUP -> icon(Icons.Outlined.Tune, MID, MID - 18f, 24f, color)
            AppCardState.REMOVED -> icon(CardIcons.HideSource, MID, MID - 18f, 24f, color)
            AppCardState.PAUSED -> icon(CardIcons.Work, MID, MID - 18f, 24f, color)
            else -> appIcon(x.icon, accent, MID, MID - 18f, 24f)
        }
        text(
            title, MID, MID + 12f, if (state == AppCardState.PAUSED) 12.5f else 14f,
            if (state == AppCardState.REMOVED) colors.sub else colors.on, maxWidth = 124f, main = true
        )
        text(sub, MID, MID + 29f, 11f, colors.sub, maxWidth = 116f)
        if (!CardShapes.isTight(shapeName)) curved(d.appLabel, 10.5f, colors.mut, top = false, inset = 0.74f, spacing = 0.4f)
    }
    alpha = outer
}

/**
 * A website card in a shape: the page's header image filling it and slowly drifting (a gradient of its accent when
 * there is none), a shade, the site's letter, the title and the domain curving along the bottom. A failed read keeps
 * what it had, dimmed, with when it was read in place of the domain.
 */
internal fun Stage.site(c: ShapedContent.Site, x: CardExtras) {
    val d = c.data
    val accent = Color(d.accent)
    val zoom = 1.08f + 0.06f * sin(2f * PI.toFloat() * now / 30_000f)
    clipped {
        val image = d.image
        if (image != null) {
            image(image, MID, MID, DESIGN, zoom)
        } else {
            scope.drawRect(
                Brush.linearGradient(
                    0f to mix(accent, Color.White, 0.35f), 0.55f to accent, 1f to mix(accent, Color.Black, 0.6f),
                    start = Offset.Zero, end = Offset(0.4f * DESIGN, DESIGN)
                ),
                Offset.Zero, Size(DESIGN, DESIGN), alpha = alpha
            )
        }
        scope.drawRect(
            // Dark enough behind the title (at the middle) for white text on a bright header image.
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.18f), 0.42f to Color.Black.copy(alpha = 0.5f), 1f to Color.Black.copy(alpha = 0.72f),
                startY = 0f, endY = DESIGN
            ),
            Offset.Zero, Size(DESIGN, DESIGN), alpha = alpha
        )
    }
    val outer = alpha
    alpha = outer * (if (d.failed) 0.78f else 1f) * p(80f, 500f)
    content {
        val letter = d.domain.removePrefix("www.").firstOrNull()?.uppercase(x.locale).orEmpty()
        if (letter.isNotEmpty() && iconLegible(26f)) {
            scope.drawCircle(dim(Color.White), 13f, Offset(MID, MID - 30f))
            text(letter, MID, MID - 25.5f, 13f, mix(accent, Color.Black, 0.5f), weight = W_BOLD, maxWidth = 24f)
        }
        val title = lines(d.title.ifBlank { d.domain }, 14f, 124f, 2, W_MEDIUM)
        title.forEachIndexed { i, line -> text(line, MID, MID + 8f + i * 16f, 14f, Color.White, weight = W_MEDIUM, maxWidth = 124f, main = true) }
        if (d.failed) {
            curved(updatedNote(x.ago), 10.5f, ShapedColors.warm(dark = true), top = false, inset = 0.74f, spacing = 0.6f)
        } else {
            curved(d.domain, 10.5f, Color.White.copy(alpha = 0.8f), top = false, inset = 0.74f, spacing = 0.6f)
        }
    }
    alpha = outer
}
