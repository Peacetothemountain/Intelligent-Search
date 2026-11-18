package com.pixel.intelligentsearch.feature.tabs.wellbeing.ui

import androidx.core.content.edit
import com.pixel.intelligentsearch.feature.tabs.ui.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Fmt
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Period
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Range
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.WellbeingModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val PLOT = 132.dp

/**
 * When the time went: one chart on a shared axis for screen time (bars), unlocks (circles) and notifications (squares),
 * scrubbed with a finger. Day: 24 hours. Week: 7 days with the usual day. Month: a calendar of circles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChartCard(m: WellbeingModel, p: Period, view: ViewState, intro: Boolean) {
    val c = MaterialTheme.colorScheme
    MatteCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp, 4.dp, 28.dp, 28.dp)) {
        // The page's header already names the period: the card says what the chart is.
        CardTitle(if (p.range == Range.Day) "By Hour" else "By Day")
        Readout(m, p, view)
        when (p.range) {
            Range.Day, Range.Week -> BarPlot(m, p, view)
            Range.Month -> MonthPlot(m, p, view)
        }
        // Legend: the totals live here.
        val ids = m.periodDays(p)
        Gap(14.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (p.range != Range.Month) {
                LegendItem("Unlocks", Fmt.num(ids.sumOf { m.h.days[it]!!.unlockTotal })) { Box(Modifier.size(10.dp).clip(CircleShape).background(c.tertiary)) }
                LegendItem("Notifications", Fmt.num(ids.sumOf { m.h.days[it]!!.notificationTotal })) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c.secondary)) }
            }
            if (p.range == Range.Week && m.weekUsual(p) != null) LegendItem("Your Usual Day", Fmt.dur(m.weekUsual(p)!!)) { DashGlyph(c.tertiary) }
            if (p.range == Range.Month) LegendItem("A bigger circle is more screen time", null) { Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary)) }
            if (p.range != Range.Day && m.allDays(p).any { it.isBefore(m.h.first) }) LegendItem("No Record", null) { Ring(c.outlineVariant) }
        }
        val moments = m.moments(p)
        if (moments.isNotEmpty()) {
            Gap(16.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                moments.forEach { mo ->
                    Column {
                        Text(mo.label, style = WbType.label12, color = c.onSurfaceVariant)
                        Text(mo.value, style = WbType.value14)
                        if (mo.note.isNotEmpty()) Text(mo.note, style = WbType.body12, color = c.onSurfaceVariant)
                    }
                }
            }
        }
        if (p.range == Range.Month && m.allDays(p).any { it.isBefore(m.h.first) }) {
            Gap(8.dp)
            Text("Since ${Fmt.shortDate(m.h.first)}. Fills in as days pass.", style = WbType.body12, color = c.onSurfaceVariant)
        }
    }
}

/** A legend key: its glyph, then label and value as one text, so at large text it wraps as words (a value never splits). */
@Composable
private fun LegendItem(label: String, value: String?, glyph: @Composable () -> Unit) {
    val strong = WbType.value14.toSpanStyle().copy(color = MaterialTheme.colorScheme.onSurface)
    val text = buildAnnotatedString {
        append(label)
        if (value != null) {
            append(" ")
            withStyle(strong) { append(value.replace(' ', '\u00A0')) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 24.dp).then(if (largeText()) Modifier.fillMaxWidth() else Modifier)) {
        glyph()
        HGap(6.dp)
        Text(text, style = WbType.label12, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun DashGlyph(color: Color) = Canvas(Modifier.size(16.dp, 10.dp)) {
    drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
}
@Composable private fun Ring(color: Color) = Canvas(Modifier.size(8.dp)) {
    drawCircle(color, size.minDimension / 2 - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
}

// ---------------------------------------------------------------------- readout
@Composable
private fun Readout(m: WellbeingModel, p: Period, view: ViewState) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val minH = with(density) { WbType.title16.lineHeight.toDp() + WbType.body14.lineHeight.toDp() } + 22.dp
    val sel = view.sel
    // A tapped day (Week and Month) lands with a small spring rather than swapping in place. The day chart's hours are
    // scrubbed, so there the reading follows the finger without one.
    val animate by rememberAnimationsEnabled()
    val pop = remember { Animatable(0f) }
    // The hint goes once the chart has been used.
    val context = LocalContext.current
    var hinted by remember { mutableStateOf(ChartHint.used(context)) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(sel) {
        if (sel != null && !hinted) { ChartHint.markUsed(context); hinted = true }
        // Coming back to the page with a day picked isn't a new pick: no spring.
        val back = firstRun
        firstRun = false
        if (back || !animate || p.range == Range.Day) { pop.snapTo(0f); return@LaunchedEffect }
        pop.snapTo(1f)
        pop.animateTo(0f, Springs.bouncy())
    }
    Row(Modifier.fillMaxWidth().heightIn(min = minH).padding(top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).graphicsLayer {
            translationY = pop.value * 10.dp.toPx()
            val k = 1f - 0.04f * pop.value
            scaleX = k; scaleY = k
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
        }) {
            if (sel == null) {
                val line = m.restingLine(p)
                if (line.isNotEmpty()) Text(line, style = WbType.body14, color = c.onSurfaceVariant)
                if (!hinted) Text(if (p.range == Range.Day) "Drag across the chart to read any hour." else "Tap a day to read it.", style = WbType.body12, color = c.onSurfaceVariant)
            } else if (p.range == Range.Day) {
                val d = m.h.days[p.day]!!
                // The apps the list below shows for the hour: a minute or more.
                val top = d.apps.filter { it.value.hours[sel] >= 60 }.entries.sortedByDescending { it.value.hours[sel] }.take(3).map { it.key }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.span(sel, 1, m.h.is24), style = WbType.title16)
                    HGap(10.dp)
                    top.forEach { AppIcon(it, 24.dp, Modifier.padding(end = 4.dp)) }
                }
                Text(
                    listOfNotNull(if (d.hours[sel] >= 30) Fmt.dur(d.hours[sel]) else "Nothing on screen", d.unlocks[sel].takeIf { it > 0 }?.let { Fmt.pl(it, "unlock") }, d.notifications[sel].takeIf { it > 0 }?.let { Fmt.pl(it, "notification") }).joinToString(" · "),
                    style = WbType.body14, color = c.onSurfaceVariant
                )
            } else {
                val day = m.selectedDay(p, sel)
                val d = m.h.days[day]
                Text(Fmt.weekday(day) + if (p.range == Range.Month) ", ${Fmt.shortDate(day)}" else "", style = WbType.title16)
                if (d != null) Text(
                    listOfNotNull(Fmt.dur(d.totalSec) + if (day == m.today) " so far" else "", if (p.range == Range.Week) Fmt.pl(d.unlockTotal, "unlock") else null, if (p.range == Range.Week) Fmt.pl(d.notificationTotal, "notification") else null).joinToString(" · "),
                    style = WbType.body14, color = c.onSurfaceVariant
                )
            }
        }
        if (sel != null) {
            if (p.range == Range.Day) PreviewIconButton(Symbols.Close, "Clear selection", onClick = { view.sel = null; view.open = null })
            else PreviewButton("Open Day", ButtonKind.Tonal, onClick = { view.go(Period(Range.Day, m.selectedDay(p, sel))) })
        }
    }
}

// ---------------------------------------------------------------------- bars
@Composable
private fun BarPlot(m: WellbeingModel, p: Period, view: ViewState) {
    val c = MaterialTheme.colorScheme
    val day = p.range == Range.Day
    val n = if (day) 24 else 7
    val isToday = day && p.day == m.today
    val nowHour = (m.h.nowMin / 60).toInt().coerceIn(0, 23)
    val weekIds: List<LocalDate> = if (day) emptyList() else m.weekDays(p.day)
    val s = m.h.days[p.day]
    // Bar values in seconds, the top of the scale, unlock and notification counts.
    val values: IntArray = if (day) s!!.hours else IntArray(7) { m.h.days[weekIds[it]]?.totalSec ?: 0 }
    val usual = if (day) null else m.weekUsual(p)
    val top = if (day) 3600.0 else max(7200.0, ceil(max(usual ?: 0.0, (values.maxOrNull() ?: 0).toDouble()) / 3600.0) * 3600.0)
    val unlocks = if (day) s!!.unlocks else IntArray(7) { m.h.days[weekIds[it]]?.unlockTotal ?: 0 }
    val notifs = if (day) s!!.notifications else IntArray(7) { m.h.days[weekIds[it]]?.notificationTotal ?: 0 }
    fun selectable(i: Int) = if (day) !(isToday && i > nowHour) else m.h.has(weekIds[i])
    fun near(i: Int): Int? { for (k in 0 until n) { if (i - k >= 0 && selectable(i - k)) return i - k; if (i + k < n && selectable(i + k)) return i + k }; return null }

    val animate by rememberAnimationsEnabled()
    // The bars rise for a period not shown before; coming back to the tab, they are already up, as everything else is.
    val rise = remember(p) { List(n) { Animatable(if (animate && view.risen != p) 0f else 1f) } }
    LaunchedEffect(p) { view.risen = p; rise.forEachIndexed { i, a -> launch { delay(i * if (day) 10L else 30L); a.animateTo(1f, Springs.bouncy()) } } }
    val colX = remember { Animatable(0f) }
    val colOn = remember { Animatable(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(0f) }
    val sel = view.sel
    val gutter = if (day) 0.dp else with(LocalDensity.current) { (WbType.label12.fontSize * 2.9f).toDp() }
    val gutterPx = with(LocalDensity.current) { gutter.toPx() }
    val dens = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    LaunchedEffect(sel, width) {
        val slot = (width - gutterPx) / n
        if (sel != null && !scrubbing) { val target = gutterPx + sel * slot - 3 * dens; if (colOn.value == 0f) colX.snapTo(target) else launch { colX.animateTo(target, Springs.fast()) } }
        colOn.animateTo(if (sel != null) 1f else 0f, Springs.spatial())
    }
    val currentSel by rememberUpdatedState(sel)
    val measurer = rememberTextMeasurer()
    val labelStyle = WbType.label12.copy(color = c.onSurfaceVariant)
    val headroom = with(LocalDensity.current) { (WbType.label12.fontSize * 1.2f).toDp() } + 8.dp
    val nightPainter = rememberVectorPainter(Symbols.Bedtime)
    val night = if (day) m.night(p.day) else null
    val described = if (day) Fmt.say("Screen time by hour. ${if (!isToday && s!!.totalSec > 0) "Busiest ${Fmt.span(m.busiest2(s.hours), 2, m.h.is24)}. " else ""}${Fmt.pl(unlocks.sum(), "unlock")}. ${Fmt.pl(notifs.sum(), "notification")}.")
    else Fmt.say("Screen time by day, ${m.title(p)}.${usual?.let { " Your usual day ${Fmt.dur(it)}." } ?: ""}")
    val state = if (sel == null) "No ${if (day) "hour" else "day"} selected" else if (day) Fmt.say("${Fmt.span(sel, 1, m.h.is24)}: ${if (values[sel] >= 60) Fmt.dur(values[sel]) else "nothing on screen"}, ${Fmt.pl(unlocks[sel], "unlock")}, ${Fmt.pl(notifs[sel], "notification")}")
    else Fmt.say("${Fmt.weekday(weekIds[sel])}: ${Fmt.dur(values[sel])}, ${Fmt.pl(unlocks[sel], "unlock")}, ${Fmt.pl(notifs[sel], "notification")}")

    Column(
        Modifier.fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat() }
            .clearAndSetSemantics {
                contentDescription = described
                stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo((currentSel ?: 0).toFloat(), 0f..(n - 1).toFloat(), n - 2)
                setProgress { v -> near(v.roundToInt().coerceIn(0, n - 1))?.let { view.sel = it; true } ?: false }
            }
            .pointerInput(p) {
                detectTapGestures { o ->
                    val slot = (size.width - gutterPx) / n
                    val i = near(((o.x - gutterPx) / slot).toInt().coerceIn(0, n - 1)) ?: return@detectTapGestures
                    view.sel = if (currentSel == i) null else i
                }
            }
            .pointerInput(p) {
                detectHorizontalDragGestures(
                    onDragStart = { scrubbing = true },
                    onDragEnd = { scrubbing = false; currentSel?.let { s0 -> val slot = (size.width - gutterPx) / n; scope.launch { colX.animateTo(gutterPx + s0 * slot - 3 * dens, Springs.fast()) } } },
                    onDragCancel = { scrubbing = false }
                ) { change, _ ->
                    // Scrub: the column follows the finger exactly; the reading snaps hour by hour.
                    val slot = (size.width - gutterPx) / n
                    val x = change.position.x
                    val cx = (x - (slot + 6 * dens) / 2).coerceIn(gutterPx - 3 * dens, size.width - slot - 3 * dens)
                    scope.launch { colX.snapTo(cx) }
                    val i = near(((x - gutterPx) / slot).toInt().coerceIn(0, n - 1))
                    if (i != null && i != currentSel) view.sel = i
                    change.consume()
                }
            }
            .drawBehind {
                // Phone-down night, behind the bars and under the selection column.
                night?.let { (a, b) ->
                    val base = (PLOT + headroom).toPx()
                    val plotH = PLOT.toPx()
                    val x0 = (max(0, a) / 1440f) * size.width
                    val x1 = (b.coerceAtMost(1440) / 1440f) * size.width
                    // A night that began the evening before runs off the chart's start: square there, round where it ends.
                    val r = CornerRadius(8.dp.toPx())
                    val open = if (a < 0) CornerRadius.Zero else r
                    drawPath(Path().apply { addRoundRect(RoundRect(x0, base - plotH, x1, base, open, r, r, open)) }, c.surfaceContainerHigh)
                    val ic = 16.dp.toPx()
                    translate((x0 + x1) / 2 - ic / 2, base - plotH + 8.dp.toPx()) { with(nightPainter) { draw(Size(ic, ic), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(c.onSurfaceVariant)) } }
                }
                // The column that ties bars, unlocks and notifications into one reading.
                val k = colOn.value
                if (k > 0.001f) {
                    val slot = (size.width - gutterPx) / n
                    val top0 = 16.dp.toPx()
                    val h = (size.height - top0 + 4.dp.toPx()) * k.coerceIn(0f, 1.2f)
                    drawRoundRect(c.surfaceContainerHighest, Offset(colX.value, size.height + 4.dp.toPx() - h), Size(slot + 6.dp.toPx(), h), CornerRadius(8.dp.toPx()))
                }
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(PLOT + headroom)) {
            val base = size.height
            val plotH = PLOT.toPx()
            val left = gutterPx
            val slot = (size.width - left) / n
            // Hairlines.
            if (day) {
                for (f in listOf(1f, 0.5f)) drawLine(c.outlineVariant, Offset(0f, base - f * plotH), Offset(size.width, base - f * plotH), 1.dp.toPx())
                val t = measurer.measure("60 min", labelStyle)
                drawText(t, topLeft = Offset(0f, base - plotH - 2.dp.toPx() - t.size.height))
            } else {
                var v = 7200.0
                while (v <= top) {
                    val y = (base - v / top * plotH).toFloat()
                    drawLine(c.outlineVariant, Offset(left, y), Offset(size.width, y), 1.dp.toPx())
                    val t = measurer.measure("${(v / 3600).toInt()} hr", labelStyle)
                    drawText(t, topLeft = Offset(left - 6.dp.toPx() - t.size.width, y - t.size.height / 2))
                    v += 7200.0
                }
            }
            // Bars: round tops, square-ish feet; selected one wider, the rest in outline.
            for (i in 0 until n) {
                val cx = left + (i + 0.5f) * slot
                val future = if (day) isToday && i > nowHour else weekIds[i].isAfter(m.today)
                val noRecord = !day && !future && !m.h.has(weekIds[i])
                if (future) { drawCircle(c.outline, 1.dp.toPx(), Offset(cx, base)); continue }
                if (noRecord) { drawCircle(c.outlineVariant, 3.dp.toPx(), Offset(cx, base - 4.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())); continue }
                val v = values[i]
                if (v >= 30) {
                    // Never past the top of the scale (the hour the clocks go back is two hours long).
                    val hgt = max(4.dp.toPx(), (minOf(v.toDouble(), top) / top * plotH).toFloat()) * rise[i].value.coerceAtLeast(0f)
                    val selected = sel == i
                    val w = if (day) (if (selected) slot else slot - (if (slot < 10.dp.toPx()) 2 else 4).dp.toPx()) else minOf(if (selected) 32.dp.toPx() else 28.dp.toPx(), slot - (if (selected) 2 else 6).dp.toPx())
                    val topR = if (day) w / 2 else 14.dp.toPx()
                    val botR = if (day) 2.dp.toPx() else 4.dp.toPx()
                    val color = if (sel != null && !selected) c.outline else c.primary
                    val path = Path().apply {
                        addRoundRect(RoundRect(cx - w / 2, base - hgt, cx + w / 2, base, CornerRadius(minOf(topR, hgt / 2)), CornerRadius(minOf(topR, hgt / 2)), CornerRadius(botR), CornerRadius(botR)))
                    }
                    drawPath(path, color)
                }
            }
            // The usual day (week): a dashed line in tertiary, labeled at its end.
            if (usual != null) {
                val y = (base - usual / top * plotH).toFloat()
                drawLine(c.tertiary, Offset(left, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }
            if (isToday) drawCircle(c.primary, 3.dp.toPx(), Offset(left + (nowHour + 0.5f) * slot, base + 7.dp.toPx()))
        }
        // Axis.
        Gap(14.dp)
        Row(Modifier.fillMaxWidth().padding(start = gutter)) {
            if (day) {
                val labels = if (m.h.is24) listOf("00", "06", "12", "18") else listOf("12 AM", "6 AM", "12 PM", "6 PM")
                val shown = if (largeText()) listOf(labels[0], labels[2]) else labels
                shown.forEach { Text(it, style = WbType.label12, color = c.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1) }
            } else weekIds.forEach { d ->
                val t = d == m.today
                Text(Fmt.weekdayLetter(d.dayOfWeek), style = WbType.label12.copy(fontWeight = if (t) FontWeight.Bold else FontWeight.Medium), color = if (t) c.onSurface else c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Gap(8.dp)
        Strip(unlocks, c.tertiary, round = true, big = !day, rise = rise, gutter = gutter)
        Gap(4.dp)
        Strip(notifs, c.secondary, round = false, big = !day, rise = rise, gutter = gutter)
    }
}


/** One row of marks sized by count (area in proportion), sharing the bars' slots: circles for unlocks, squares for notifications. */
@Composable
internal fun Strip(counts: IntArray, color: Color, round: Boolean, big: Boolean, rise: List<Animatable<Float, *>>? = null, gutter: androidx.compose.ui.unit.Dp = 0.dp) {
    val max = max(1, counts.maxOrNull() ?: 0)
    Canvas(Modifier.fillMaxWidth().height(20.dp).padding(start = gutter)) {
        val slot = size.width / counts.size
        counts.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val k = rise?.getOrNull(i)?.value?.coerceIn(0f, 1.2f) ?: 1f
            val d = minOf(slot - 2.dp.toPx(), maxOf((if (big) 4f else 3f).dp.toPx(), (if (big) 16f else 9.5f).dp.toPx() * sqrt(v.toFloat() / max))) * k
            val cx = (i + 0.5f) * slot
            if (round) drawCircle(color, d / 2, Offset(cx, size.height / 2))
            else drawRoundRect(color, Offset(cx - d / 2, size.height / 2 - d / 2), Size(d, d), CornerRadius(2.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------------- month
@Composable
private fun MonthPlot(m: WellbeingModel, p: Period, view: ViewState) {
    val c = MaterialTheme.colorScheme
    val days = m.monthDays(p.day)
    val first = days.first()
    val lead = ((first.dayOfWeek.value - m.h.weekStart.value) + 7) % 7
    val rows = ceil((lead + days.size) / 7.0).toInt()
    val max = max(1, days.filter { m.h.has(it) }.maxOfOrNull { m.h.days[it]!!.totalSec } ?: 1)
    val cmax = if (largeText()) 24.dp else 30.dp
    val animate by rememberAnimationsEnabled()
    val rise = remember(p) { Animatable(if (animate && view.risen != p) 0f else 1f) }
    LaunchedEffect(p) { view.risen = p; rise.animateTo(1f, Springs.bouncy()) }
    val gutter = with(LocalDensity.current) { (WbType.label12.fontSize * 2.2f).toDp() }
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "${Fmt.month(p.day)}, one circle per day. A bigger circle is more screen time." }) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(gutter))
            (0 until 7).forEach { k ->
                Text(Fmt.weekdayLetter(m.h.weekStart.plus(k.toLong())), style = WbType.label12, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(bottom = 4.dp).clearAndSetSemantics {})
            }
        }
        for (r in 0 until rows) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${maxOf(1, r * 7 - lead + 1)}", style = WbType.label12, color = c.onSurfaceVariant, modifier = Modifier.width(gutter).clearAndSetSemantics {})
            for (col in 0 until 7) {
                val k = r * 7 + col - lead
                val d = days.getOrNull(k)
                val mod = Modifier.weight(1f).height(48.dp)
                if (k < 0 || d == null) { Box(mod); continue }
                val name = "${Fmt.weekday(d)}, ${Fmt.month(d)} ${d.dayOfMonth}"
                when {
                    d.isAfter(m.today) -> Box(mod, contentAlignment = Alignment.Center) { Box(Modifier.size(2.dp).clip(CircleShape).background(c.outline)) }
                    !m.h.has(d) -> Box(mod.semantics { contentDescription = "$name: no record" }, contentAlignment = Alignment.Center) { Ring(c.outlineVariant) }
                    else -> {
                        val selected = view.sel == k
                        val shape = RoundedCornerShape(if (selected) 16.dp else 8.dp)
                        val sec = m.h.days[d]!!.totalSec
                        Box(
                            mod.clip(shape).background(if (selected) c.surfaceContainerHighest else Color.Transparent)
                                .bouncyClickable(shape, role = Role.Button, onClickLabel = null) { view.sel = if (selected) null else k }
                                .semantics { contentDescription = Fmt.say("$name: ${Fmt.dur(sec)}${if (d == m.today) " so far" else ""}") },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.size(cmax + 8.dp)) {
                                val dia = max(6.dp.toPx(), sqrt(sec.toFloat() / max) * cmax.toPx()) * rise.value.coerceAtLeast(0f)
                                drawCircle(if (view.sel != null && !selected) c.outline else c.primary, dia / 2)
                                if (d == m.today) drawCircle(c.primary, size.minDimension / 2 - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Whether the chart has been used once (a bar picked or an hour read): its hint is shown until then. */
internal object ChartHint {
    private const val PREFS = "wellbeing"
    private const val KEY = "chart_used"
    @Volatile private var used: Boolean? = null

    fun used(context: android.content.Context): Boolean =
        used ?: runCatching { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY, false) }.getOrDefault(false).also { used = it }

    fun markUsed(context: android.content.Context) {
        used = true
        runCatching { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit { putBoolean(KEY, true) } }
    }
}
