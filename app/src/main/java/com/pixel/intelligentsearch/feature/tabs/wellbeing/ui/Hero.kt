package com.pixel.intelligentsearch.feature.tabs.wellbeing.ui

import com.pixel.intelligentsearch.feature.tabs.ui.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Dir
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Fmt
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Glyph
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Period
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.WellbeingModel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** "3hr 42min" with small units, in [size]. */
internal fun bigNumber(sec: Int, unitScale: Float, gap: Float = 10f) = buildAnnotatedString {
    val m = (sec / 60).coerceAtLeast(0)
    val h = m / 60
    val r = m % 60
    fun unit(u: String, last: Boolean) {
        withStyle(SpanStyle(fontSize = unitScale.em())) { append(" $u") }
        if (!last) append(" ")
    }
    if (h > 0) { append("$h"); unit("hr", r == 0) }
    if (r > 0 || h == 0) { append("$r"); unit("min", true) }
}

private fun Float.em() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)

/**
 * The hero: the period's number, one plain sentence against your usual, and the pace gauge. A plain primary-container
 * matte card, the page's one colored hero, joined to the chart card below (4 inner corners, 2 apart).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeroCard(m: WellbeingModel, p: Period, intro: Boolean) {
    val c = MaterialTheme.colorScheme
    val hero = m.hero(p)
    val large = largeText()
    val spoken = Fmt.say("${hero.label}: ${Fmt.dur(hero.valueSec)}. ${hero.verdict}" + hero.captions.joinToString("") { " ${it.label}, ${it.value}${if (it.note.isNotEmpty()) ", ${it.note.replaceFirstChar { ch -> ch.lowercase() }}" else ""}." })
    MatteCard(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        color = c.primaryContainer, contentColor = c.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp, 28.dp, 4.dp, 4.dp), padding = 24.dp
    ) {
        // Which way the period went against your usual day; with nothing to compare yet, an hourglass: the comparison is
        // still to come (three dots read as a menu that does nothing).
        // A past period with nothing to compare with has no comparison to come either: the shape alone.
        val icon = when (hero.dir) { Dir.Less -> Symbols.ArrowDownward; Dir.More -> Symbols.ArrowUpward; Dir.Same -> Symbols.DragHandle; Dir.None, Dir.New -> if (hero.gauge) Symbols.HourglassTop else null }
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().heightIn(min = if (large) 56.dp else 20.dp).padding(end = 72.dp), contentAlignment = Alignment.CenterStart) {
                    Text(hero.label, style = WbType.label14)
                }
                if (!large) Gap(4.dp)
                FittedNumber(hero.valueSec, reserve = if (large) 0.dp else 72.dp)
                Gap(8.dp)
                Text(hero.verdict, style = WbType.title22)
            }
            ShapeMark(p.range, 64.dp, c.primary, icon, c.onPrimary, 28.dp, Modifier.align(Alignment.TopEnd).offset(8.dp, (-8).dp))
        }
        if (!hero.gauge) return@MatteCard
        Gap(20.dp)
        val anim = remember { Animatable(if (intro) 0f else 1f) }
        val animate by rememberAnimationsEnabled()
        val target = hero.valueSec.toFloat()
        val v = remember { Animatable(if (intro) 0f else target) }
        val mx = remember { Animatable(hero.maxSec.toFloat()) }
        LaunchedEffect(target, hero.maxSec) {
            if (!animate) { v.snapTo(target); mx.snapTo(hero.maxSec.toFloat()); anim.snapTo(1f); return@LaunchedEffect }
            kotlinx.coroutines.coroutineScope {
                launch { v.animateTo(target, Springs.spatial()) }
                launch { mx.animateTo(hero.maxSec.toFloat(), Springs.spatial()) }
                launch { anim.animateTo(1f, Springs.spatial()) }
            }
        }
        Gauge(
            Modifier.fillMaxWidth().height(24.dp), value = v.value.toDouble(), max = mx.value.toDouble(), mark = hero.markSec, stop = hero.stopSec,
            wavy = hero.wavy, waveScale = anim.value, line = c.primary, track = c.onPrimaryContainer, cut = c.primaryContainer, stopOn = c.onPrimary
        )
        if (hero.captions.isNotEmpty()) {
            Gap(8.dp)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hero.captions.forEachIndexed { i, cap ->
                    Column(Modifier.padding(end = if (i < hero.captions.size - 1) 16.dp else 0.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (cap.glyph) {
                                Glyph.Mark -> Box(Modifier.size(4.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(c.onPrimaryContainer))
                                Glyph.Stop -> Box(Modifier.size(6.dp).clip(CircleShape).background(c.onPrimaryContainer))
                                null -> {}
                            }
                            if (cap.glyph != null) HGap(6.dp)
                            Text(cap.label, style = WbType.label12)
                        }
                        Text(cap.value, style = WbType.value14)
                        if (cap.note.isNotEmpty()) Text(cap.note, style = WbType.body12)
                    }
                }
            }
        }
    }
}


/** The hero number on one line: it steps down the type scale until it fits. */
@Composable
private fun FittedNumber(sec: Int, reserve: androidx.compose.ui.unit.Dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val text = bigNumber(sec, 0.49f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = with(density) { (maxWidth - reserve).toPx() }
        val styles = listOf(WbType.display57, WbType.display45, WbType.display36, WbType.headline28.copy(fontSize = 32.sp, lineHeight = 40.sp), WbType.headline28)
        val style = styles.firstOrNull { measurer.measure(text, it, maxLines = 1, softWrap = false, constraints = Constraints()).size.width <= room } ?: styles.last()
        Text(text, style = style, maxLines = 1, softWrap = false)
    }
}

/**
 * The pace gauge, drawn like M3's thick wavy linear indicator: a thick line for the time so far (wavy while the period
 * is still running), a flat track to the end of the scale, a pill at the usual mark and a dot at the usual full day.
 */
@Composable
internal fun Gauge(
    modifier: Modifier, value: Double, max: Double, mark: Double?, stop: Double?, wavy: Boolean, waveScale: Float = 1f,
    line: Color, track: Color, cut: Color, stopOn: Color
) {
    Canvas(modifier) {
        val w = size.width
        val cy = size.height / 2
        val dp = density
        fun x(v: Double) = (4 * dp + (w - 8 * dp) * (v / max).coerceIn(0.0, 1.0)).toFloat()
        val xa = x(value)
        val amp = if (wavy) 3 * dp * waveScale.coerceIn(0f, 1.2f) else 0f
        val ts = xa + 10 * dp
        val te = w - 12 * dp
        if (te > ts) drawLine(track.copy(alpha = 0.2f), Offset(ts, cy), Offset(te, cy), 4 * dp, StrokeCap.Round)
        val path = Path()
        var px = 4 * dp
        path.moveTo(px, cy)
        while (px < xa) {
            px = minOf(px + 2 * dp, xa)
            path.lineTo(px, cy + amp * sin(((px - 4 * dp) / (40 * dp) * 2 * PI)).toFloat())
        }
        if (xa <= 4.5f * dp) path.lineTo(4 * dp + 0.01f, cy)
        drawPath(path, line, style = Stroke(8 * dp, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val xs = if (stop == null) w - 4 * dp else x(stop)
        drawCircle(if (xs < xa + 6 * dp) stopOn else track, 2 * dp, Offset(xs, cy))
        if (mark != null) {
            val xm = x(mark)
            drawRoundRect(cut, Offset(xm - 4 * dp, 0f - 2 * dp), Size(8 * dp, 28 * dp), CornerRadius(4 * dp))
            drawRoundRect(track, Offset(xm - 2 * dp, 0f), Size(4 * dp, 24 * dp), CornerRadius(2 * dp))
        }
    }
}
