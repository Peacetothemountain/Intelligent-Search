package com.pixel.intelligentsearch.feature.tabs.wellbeing.ui

import com.pixel.intelligentsearch.feature.tabs.ui.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.feature.tabs.ui.pageContainer
import com.pixel.intelligentsearch.core.wellbeing.Attribution
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.AppRow
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Dir
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Fmt
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Period
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Range
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.WellbeingModel
import kotlin.math.abs
import kotlin.math.max

private val panelEnter = expandVertically(spring(0.8f, 380f, IntSize.VisibilityThreshold), expandFrom = Alignment.Top)
private val panelExit = shrinkVertically(spring(0.8f, 380f, IntSize.VisibilityThreshold), shrinkTowards = Alignment.Top)

/** Colors of a list of fill rows: the row, its fill, the open row's fill and text. */
private class RowColors(val row: Color, val fill: Color, val openFill: Color, val openText: Color, val text: Color, val ring: Color?)

// ---------------------------------------------------------------------- most used
@Composable
internal fun AppsCard(m: WellbeingModel, p: Period, view: ViewState, actions: WellbeingActions) {
    val c = MaterialTheme.colorScheme
    val model = m.apps(p, view.sel)
    val shown = if (view.showAll || model.scoped) model.rows else model.rows.take(5)
    MatteCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            CardTitle(model.head, Modifier.padding(horizontal = 8.dp))
            Gap(8.dp)
            if (shown.isEmpty()) {
                val hourSec = view.sel?.let { m.h.days[p.day]?.hours?.getOrNull(it) } ?: 0
                val empty = when {
                    p.range != Range.Day || view.sel == null -> "No apps on screen yet."
                    hourSec >= 30 -> "No app had a minute or more in this hour."
                    else -> "No apps on screen in this hour."
                }
                Text(empty, style = WbType.body14, color = c.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
            val colors = RowColors(c.surfaceContainerHigh, c.secondaryContainer, c.primaryContainer, c.onPrimaryContainer, c.onSurface, null)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEachIndexed { i, r ->
                    FillRow(m, p, "apps", r, model.max, i, shown.size, view, actions, colors, 40.dp, showDelta = !model.scoped)
                }
            }
            if (!model.scoped && model.rows.size > 5) PreviewButton(if (view.showAll) "Show Fewer" else "Show All ${model.rows.size} Apps", ButtonKind.Text, onClick = { view.showAll = !view.showAll })
        }
    }
}

/** An app as a row that is its own bar: the fill's width is its share of the top app's time. Tap to open it in place. */
@Composable
private fun FillRow(
    m: WellbeingModel, p: Period, list: String, r: AppRow, max: Int, index: Int, count: Int, view: ViewState, actions: WellbeingActions,
    colors: RowColors, iconSize: Dp, showDelta: Boolean
) {
    val id = "$list:${r.key}"
    val open = view.open == id
    val base = segmentShape(index, count)
    val shape: Shape = if (open) RoundedCornerShape(16.dp) else base
    // The gap an open row takes from its neighbors springs open and shut. The spatial spring overshoots, and a padding
    // can't be negative (closing a row crashed the app): the gap never goes below none.
    val gap by animateDpAsState(if (open) 6.dp else 0.dp, Springs.spatial(), label = "gap")
    val margin = gap.coerceAtLeast(0.dp)
    val frac by animateFloatAsState(r.sec.toFloat() / max(1, max), Springs.spatial(), label = "fill")
    val fillColor by animateColorAsState(if (open && colors.ring == null) colors.openFill else colors.fill, Springs.effects(), label = "fillc")
    val textColor by animateColorAsState(if (open && colors.ring == null) colors.openText else colors.text, Springs.effects(), label = "text")
    val d = r.delta
    val moved = showDelta && d != null && (d.dir == Dir.Less || d.dir == Dir.More)
    // An app with no time on your usual days is new, not "more than usual" by all of its time.
    val isNew = showDelta && d != null && d.dir == Dir.New
    val name = m.h.label(r.key)
    val unit = if (r.perDay) " a day" else ""
    val spoken = Fmt.say("$name, ${Fmt.dur(r.sec)}$unit${if (moved) ", ${Fmt.durMin(abs(d.diffMin).toDouble())}${if (d.perDay) " a day" else ""} ${if (d.dir == Dir.Less) "less" else "more"} than your usual day" else if (isNew) ", ${d.text.removeSuffix(".").replaceFirstChar { ch -> ch.lowercase() }}" else ""}")
    // The row's top: the list segment's corners, and its bottom ones too while it is closed.
    val rowShape: Shape = if (open) RoundedCornerShape(16.dp, 16.dp, 0.dp, 0.dp) else base
    Column(
        Modifier.fillMaxWidth().padding(vertical = if (index == 0 || index == count - 1) 0.dp else margin / 2).padding(top = if (index == 0) 0.dp else margin / 2)
            .background(colors.row, shape)
            .then(if (open && colors.ring != null) Modifier.border(2.dp, colors.ring, shape) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .bouncyClickable(null, onClickLabel = if (open) "Close" else "Open") { view.open = if (open) null else id }
                .clearAndSetSemantics { contentDescription = spoken; stateDescription = if (open) "Expanded" else "Collapsed" }
                .drawWithCache {
                    // One fill, its end rounded, its start following the row's own corners: cut in drawing, so the
                    // row needs no clip.
                    val outline = Path().apply { addOutline(rowShape.createOutline(size, layoutDirection, this@drawWithCache)) }
                    val minW = (iconSize + 24.dp).toPx()
                    val rr = 12.dp.toPx()
                    onDrawBehind {
                        val w = max(minW, size.width * frac)
                        val bar = Path().apply { addRoundRect(RoundRect(-rr, 0f, w, size.height, CornerRadius(rr))) }
                        drawPath(Path().apply { op(outline, bar, PathOperation.Intersect) }, fillColor)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(r.key, iconSize, fallback = if (r.key == Attribution.GEMINI) Symbols.AutoAwesome else null)
            HGap(12.dp)
            FlowRow(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, style = WbType.name16, color = textColor, maxLines = 2, modifier = Modifier.weight(1f, fill = true).padding(vertical = 2.dp).align(Alignment.CenterVertically))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Text(Fmt.dur(r.sec) + unit, style = WbType.title16, color = textColor, maxLines = 1)
                    // The change is in the row's own unit: a day when the row is an average day.
                    if (moved) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (d.dir == Dir.Less) Symbols.ArrowDownward else Symbols.ArrowUpward, null, Modifier.size(16.dp), tint = LocalContentColor.current.copy(alpha = 0.8f))
                        Text(Fmt.durMin(abs(d.diffMin).toDouble()) + if (d.perDay && !r.perDay) " a day" else "", style = WbType.label12, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    if (isNew) Text(d.text.removeSuffix("."), style = WbType.label12, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        // With a day's bar picked on Week or Month the app rows are that day's, and so is what opens under one.
        val scope = view.sel?.takeIf { p.range != Range.Day && list == "apps" }?.let { Period(Range.Day, m.selectedDay(p, it)) } ?: p
        AnimatedVisibility(open, enter = panelEnter, exit = panelExit) { Panel(m, scope, r.key, actions, colors.ring != null, rowShowsChange = moved || isNew) }
    }
}

/**
 * What opens under an app row: its own usual (when the row doesn't already show the change), its own hours, a few facts
 * and the two hand-offs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Panel(m: WellbeingModel, p: Period, key: String, actions: WellbeingActions, ai: Boolean, rowShowsChange: Boolean) {
    val c = MaterialTheme.colorScheme
    val panel = m.panel(p, key)
    val muted = if (ai) LocalContentColor.current else c.onSurfaceVariant
    val bar = if (ai) c.tertiary else c.primary
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        if (panel.sentenceShown && !rowShowsChange) {
            Text(panel.sentence, style = WbType.body14)
            Gap(12.dp)
        }
        // A clean top ("60 min", "15 min", "2 hr"), as the day chart's, not the largest bar's own odd value.
        val max = max(60, panel.topSec)
        Column(Modifier.clearAndSetSemantics { contentDescription = panel.described }) {
            Text(Fmt.dur(max), style = WbType.label12, color = muted)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant))
            Canvas(Modifier.fillMaxWidth().height(57.dp)) {
                val slot = size.width / panel.series.size
                panel.series.forEachIndexed { i, v ->
                    if (v < 30) return@forEachIndexed
                    val w = minOf(24.dp.toPx(), slot - 3.dp.toPx())
                    val hgt = max(3.dp.toPx(), v.toFloat() / max * 56.dp.toPx())
                    val cx = (i + 0.5f) * slot
                    drawRoundRect(bar, Offset(cx - w / 2, size.height - hgt), Size(w, hgt), CornerRadius(minOf(w / 2, hgt / 2)))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant))
            Gap(4.dp)
            Row(Modifier.fillMaxWidth()) {
                panel.axis.forEach { Text(it, style = WbType.label12, color = muted, modifier = Modifier.weight(1f), textAlign = if (panel.axis.size == 7) TextAlign.Center else TextAlign.Start) }
            }
        }
        Gap(12.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.range != Range.Day) Fact(if (m.isCurrent(p)) "Total So Far" else "Total", Fmt.dur(panel.totalSec), muted = muted)
            Fact("Opened", Fmt.pl(panel.opens, "time"), muted = muted)
            if (panel.opens > 1) Fact("Average Visit", Fmt.dur(panel.averageVisitSec), muted = muted)
            Fact("Longest Stretch", Fmt.dur(panel.longestSec), muted = muted)
            Fact("Notifications", Fmt.num(panel.received), if (panel.received > 0) openedAfterNote(panel.followed) else "", muted)
        }
        Gap(12.dp)
        // On the AI apps card, the card's own content color (see aiContainer), so the buttons read in dark theme too.
        val onAi = aiContainer().second
        val tonal = if (ai) onAi.copy(alpha = 0.11f) else null
        val onTonal = if (ai) onAi else null
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // A timer is set in Digital Wellbeing: the button says it leaves for another app.
            PreviewButton("Set a Timer", ButtonKind.Tonal, modifier = Modifier.weight(1f), icon = Symbols.Timer, trailingIcon = Symbols.OpenInNew, container = tonal, content = onTonal, onClick = { actions.openTimer(timerPackage(key)) })
            PreviewButton("Notifications", ButtonKind.Tonal, modifier = Modifier.weight(1f), icon = Symbols.Notifications, container = tonal, content = onTonal, onClick = { actions.openNotifications(key) })
        }
    }
}

/** Gemini's time inside the Google app is timed there, under the Google app, when the Gemini app isn't the one in use. */
private fun timerPackage(key: String) = key

@Composable
private fun Fact(label: String, value: String, note: String = "", muted: Color) {
    Column {
        Text(label, style = WbType.label12, color = muted)
        Text(value, style = WbType.value14)
        if (note.isNotEmpty()) Text(note, style = WbType.body12, color = muted)
    }
}

// ---------------------------------------------------------------------- AI apps
@Composable
internal fun AiCard(m: WellbeingModel, p: Period, view: ViewState, actions: WellbeingActions) {
    val c = MaterialTheme.colorScheme
    val ai = m.ai(p)
    val (container, onContainer) = aiContainer()
    MatteCard(Modifier.fillMaxWidth(), color = container, contentColor = onContainer) {
        CardTitle("AI Apps", color = c.tertiary.takeIf { !c.isDark } ?: onContainer)
        if (ai == null) {
            Gap(4.dp)
            Text("No AI app use ${m.whenWords(p)}.", style = WbType.body14)
            return@MatteCard
        }
        val share = if (ai.pct == 0) "under 1" else "${ai.pct}"
        Column(Modifier.clearAndSetSemantics { contentDescription = Fmt.say("AI apps: ${Fmt.dur(ai.totalSec)}, $share percent of your screen time ${m.whenWords(p)}.") }) {
            Gap(2.dp)
            Text(bigNumber(ai.totalSec, 0.62f), style = WbType.display36, maxLines = 1)
            Text("${share.replaceFirstChar { it.uppercase() }}% of your screen time ${m.whenWords(p)}", style = WbType.body14)
            Gap(10.dp)
            val frac by animateFloatAsState(ai.pct / 100f, Springs.spatial(), label = "share")
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val r = CornerRadius(8.dp.toPx())
                drawRoundRect(onContainer.copy(alpha = 0.16f), cornerRadius = r)
                drawRoundRect(c.tertiary, size = Size(max(16.dp.toPx(), size.width * frac), size.height), cornerRadius = r)
            }
        }
        Gap(14.dp)
        val colors = RowColors(onContainer.copy(alpha = 0.08f), c.tertiary.copy(alpha = 0.12f), c.tertiary.copy(alpha = 0.12f), onContainer, onContainer, c.tertiary)
        val count = ai.rows.size + if (ai.othersSec > 0) 1 else 0
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ai.rows.forEachIndexed { i, r -> FillRow(m, p, "ai", r, ai.rows.first().sec, i, count, view, actions, colors, 32.dp, showDelta = false) }
            if (ai.othersSec > 0) Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(colors.row, segmentShape(count - 1, count)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { Text("Others", style = WbType.body14, modifier = Modifier.weight(1f)); Text(Fmt.dur(ai.othersSec), style = WbType.value14) }
        }
        ai.strip?.let { strip ->
            Gap(14.dp)
            Column(Modifier.clearAndSetSemantics { contentDescription = ai.stripDescribed }) {
                Strip(strip, c.tertiary, round = true, big = p.range == Range.Week)
                Gap(4.dp)
                Row(Modifier.fillMaxWidth()) {
                    val labels = if (p.range == Range.Day) (if (m.h.is24) listOf("00", "06", "12", "18") else listOf("12 AM", "6 AM", "12 PM", "6 PM")).let { if (largeText()) listOf(it[0], it[2]) else it }
                    else m.weekDays(p.day).map { Fmt.weekdayLetter(it.dayOfWeek) }
                    labels.forEach { Text(it, style = WbType.label12, modifier = Modifier.weight(1f), textAlign = if (p.range == Range.Week) TextAlign.Center else TextAlign.Start) }
                }
            }
        }
        if (ai.facts.isNotEmpty()) {
            Gap(12.dp)
            ai.facts.forEach { Text(it, style = WbType.body14) }
        }
    }
}

/**
 * The AI apps card's container and content colors. Light: the scheme's tertiary container. Dark: the dynamic palette's
 * dark tertiary container (its tertiary tone 30, with tone 90 on it), as the hero's primary container is dark; Android
 * 17's own dark tertiary container is a light tone that lit the card up like a light-theme card on the dark page.
 */
@Composable
internal fun aiContainer(): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    if (!c.isDark) return c.tertiaryContainer to c.onTertiaryContainer
    return colorResource(android.R.color.system_accent3_700) to colorResource(android.R.color.system_accent3_100)
}

// ---------------------------------------------------------------------- interruptions
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterruptionsCard(m: WellbeingModel, p: Period, view: ViewState, actions: WellbeingActions) {
    val c = MaterialTheme.colorScheme
    val it = m.interruptions(p, view.leastFollowed)
    MatteCard(Modifier.fillMaxWidth()) {
        CardTitle("Interruptions")
        Gap(8.dp)
        // Headed the way unlocks are: the count, then one line about it.
        Text(if (it.received == 0) "No notifications ${m.whenWords(p)}" else Fmt.pl(it.received, "notification"), style = WbType.title16)
        if (it.received > 0) {
            Gap(2.dp)
            Text(
                listOf(it.receivedNote.removeSuffix("."), openedAfterNote(it.followed), if (it.whileDown > 0) "${Fmt.num(it.whileDown)} while your phone was down" else "")
                    .filter { s -> s.isNotEmpty() }.joinToString(" · "),
                style = WbType.body14, color = c.onSurfaceVariant
            )
        }
        it.insight?.let { s ->
            Gap(14.dp)
            InsightRow(s.key, "${m.h.label(s.key)} sent ${Fmt.num(s.received)} in the last 7 days. You opened none.", m.h.label(s.key), c.surfaceContainerHigh, actions)
        }
        if (it.received > 0) {
            Gap(16.dp)
            // A connected button group; at large text the two stack, so neither label breaks inside a word.
            val stacked = largeText()
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp), maxItemsInEachRow = if (stacked) 1 else 2) {
                // Rarely Opened: the apps whose notifications you least often went to afterwards.
                listOf(false to "Most Received", true to "Rarely Opened").forEachIndexed { i, (least, label) ->
                    val on = view.leastFollowed == least
                    val shape = when {
                        on -> RoundedCornerShape(24.dp)
                        stacked -> if (i == 0) RoundedCornerShape(24.dp, 24.dp, 8.dp, 8.dp) else RoundedCornerShape(8.dp, 8.dp, 24.dp, 24.dp)
                        i == 0 -> RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp)
                        else -> RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp)
                    }
                    Box(
                        Modifier.weight(1f).fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                            .semantics { stateDescription = if (on) "Selected" else "Not selected" }
                            .bouncyClickable(shape) { view.leastFollowed = least },
                        contentAlignment = Alignment.Center
                    ) { Text(label, style = if (on) WbType.value14 else WbType.label14, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
                }
            }
            Gap(8.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                it.top.forEachIndexed { i, s ->
                    // The whole row opens the app's notification settings.
                    val shape = segmentShape(i, it.top.size)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .bouncyClickable(shape, onClickLabel = "Open notification settings") { actions.openNotifications(s.key) }
                            .background(c.surfaceContainerHigh)
                            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(s.key, 40.dp)
                        HGap(12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(m.h.label(s.key), style = WbType.name16)
                            Text("${Fmt.num(s.received)}\u00A0received · ${openedAfterNote(s.followed).replace(' ', '\u00A0')}", style = WbType.body14, color = c.onSurfaceVariant)
                        }
                        HGap(8.dp)
                        Icon(Symbols.ChevronRight, null, tint = c.onSurfaceVariant)
                    }
                }
            }
        }
        Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(1.dp).background(c.outlineVariant))
        val own = it.unlocks - it.after
        val unlockNotes = listOf(it.unlockNote, it.unlockWhen).filter { s -> s.isNotEmpty() }.joinToString(" ")
        Column(Modifier.clearAndSetSemantics { contentDescription = Fmt.say("${Fmt.pl(it.unlocks, "unlock")}: ${Fmt.num(own)} on your own, ${Fmt.num(it.after)} after a notification. $unlockNotes".trim()) }) {
            Text(Fmt.pl(it.unlocks, "unlock"), style = WbType.title16)
            if (it.unlocks > 0) {
                if (unlockNotes.isNotEmpty()) { Gap(2.dp); Text(unlockNotes, style = WbType.body14, color = c.onSurfaceVariant) }
                Gap(8.dp)
                Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (own > 0) Box(Modifier.weight(own.toFloat()).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.tertiary))
                    if (it.after > 0) Box(Modifier.weight(it.after.toFloat()).height(12.dp).clip(RoundedCornerShape(3.dp)).background(c.secondary))
                }
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(c.tertiary)); HGap(6.dp); Text("${Fmt.num(own)} on your own", style = WbType.body14, color = c.onSurfaceVariant) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c.secondary)); HGap(6.dp); Text("${Fmt.num(it.after)} after a notification", style = WbType.body14, color = c.onSurfaceVariant) }
                }
            }
        }
        it.firstApp?.let { k ->
            Gap(8.dp)
            Text("On your own, you open ${m.h.label(k)} first ${it.firstIn10} times in 10.", style = WbType.body14)
        }
    }
}

// ---------------------------------------------------------------------- weekly recap entry
/** How many of the notifications the person followed by opening the app that sent them ("17 opened", "none opened"). */
internal fun openedAfterNote(followed: Int): String =
    if (followed > 0) "${Fmt.num(followed)} opened" else "none opened"

/**
 * The one insight with a fix: an app whose notifications are never followed, its line and one Manage button side by side
 * (stacked at large text, so the line keeps its width).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InsightRow(key: String, line: String, name: String, container: Color, actions: WellbeingActions) {
    val stacked = largeText()
    FlowRow(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(container).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = if (stacked) 1 else 2
    ) {
        Row(Modifier.weight(1f).align(Alignment.CenterVertically).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(key, 40.dp)
            HGap(12.dp)
            Text(line, style = WbType.body14)
        }
        PreviewButton("Manage", ButtonKind.Tonal, modifier = Modifier.align(Alignment.CenterVertically), contentDescription = "Notification settings for $name", onClick = { actions.openNotifications(key) })
    }
}

internal fun openedAfter(m: WellbeingModel, followed: Int, senders: List<com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Sender>): String {
    val app = senders.singleOrNull()?.let { m.h.label(it.key) } ?: "the sending app"
    val count = if (followed == 0) "none" else Fmt.num(followed)
    return "You opened $app after $count of them."
}

@Composable
internal fun RecapEntry(m: WellbeingModel, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val large = largeText()
    if (!m.recapReady) {
        val ready = m.recapReadyOn()
        MatteCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!large) { ShapeStack(40.dp); HGap(16.dp) }
                Text("Your first week in review is ready on ${Fmt.weekday(ready)}, ${Fmt.shortDate(ready)}.", style = WbType.body14, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        return
    }
    val r = m.recap()
    val line = if (r.cmp.dir == Dir.None) "${Fmt.dur(r.avgSec)} a day." else "${Fmt.dur(r.avgSec)} a day, ${if (r.cmp.dir == Dir.Same) "about the same as the week before" else "${Fmt.durMin(abs(r.cmp.diffMin).toDouble())} ${if (r.cmp.dir == Dir.Less) "less" else "more"} than the week before"}."
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().matteCard(shape)
            .bouncyClickable(shape, onClickLabel = "Open") { onOpen() }
            .clearAndSetSemantics { contentDescription = Fmt.say("Your week in review, ${r.name}. $line") }
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!large) { ShapeStack(56.dp); HGap(16.dp) }
        Column(Modifier.weight(1f)) {
            Text("Your Week in Review", style = WbType.label12.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.primary)
            Text(r.name, style = WbType.title16, color = c.onSurface)
            Text(line, style = WbType.body14, color = c.onSurfaceVariant)
        }
        Icon(Symbols.ChevronRight, null, tint = c.onSurfaceVariant)
    }
}

/** The three range shapes stacked: the recap's art. */
@Composable
internal fun ShapeStack(size: Dp) {
    val c = MaterialTheme.colorScheme
    val s = size * 34f / 56f
    Box(Modifier.size(size)) {
        StillShape(Range.Day, s, c.primary, Modifier.align(Alignment.TopStart))
        StillShape(Range.Week, s, c.tertiary, Modifier.align(Alignment.TopEnd).padding(top = size * 10f / 56f))
        StillShape(Range.Month, s, c.secondary, Modifier.align(Alignment.BottomStart).padding(start = size * 9f / 56f))
    }
}
