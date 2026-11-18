package com.pixel.intelligentsearch.feature.tabs.wellbeing.ui

import com.pixel.intelligentsearch.feature.tabs.ui.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.wellbeing.Attribution
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Fmt
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Range
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.WellbeingModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Tone { P, S, T, H }
private class Slide(val title: String, val tone: Tone, val body: @Composable () -> Unit)

/**
 * "Your Week in Review": the last full week (from the phone's first day of the week) as swipeable full-screen slides. They follow the finger
 * (with resistance past either end) and spring to the nearest slide; nothing advances on its own. Glass chrome floats
 * over them: a top bar (close and where you are) and a floating toolbar.
 */
@Composable
internal fun RecapLayer(m: WellbeingModel, view: ViewState, actions: WellbeingActions, pad: PaddingValues) {
    val open = view.recap
    var shown by remember { mutableStateOf(false) }
    if (open) shown = true
    val y by animateFloatAsState(if (open) 0f else 1f, Springs.spatial(), label = "recap") { if (!open) shown = false }
    if (!shown || !m.recapReady) return
    val slides = remember(m) { recapSlides(m, view, actions) }
    val n = slides.size
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { translationY = y * size.height }.background(c.background)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .semantics { contentDescription = Fmt.say("Your Week in Review, ${m.recapName()}") }
    ) {
        val w = constraints.maxWidth.toFloat()
        // The slides are a window wide: when the width changes (a fold opening, a window resized) the strip is laid
        // out again at the new width, still on the same slide.
        val x = remember(w) { Animatable(-view.recapSlide * w) }
        fun to(i: Int) {
            val t = i.coerceIn(0, n - 1)
            view.recapSlide = t
            scope.launch { x.animateTo(-t * w, Springs.spatial()) }
        }
        val content = rememberGraphicsLayer()
        val blur by rememberSystemBlurEnabled()
        // The slides. Only when the glass blurs what is under it are they recorded into a layer for it to read; with
        // blur off the bars are solid and the slides draw straight to the screen, with no second copy of them.
        Box(
            Modifier.fillMaxSize()
                .then(if (blur) Modifier.drawWithContent { content.record { this@drawWithContent.drawContent() }; drawLayer(content) } else Modifier)
                // A tap on the left third goes back, anywhere else on: the slides read like a story.
                .pointerInput(n, w) {
                    detectTapGestures { o ->
                        if (o.x < w / 3f) { if (view.recapSlide > 0) to(view.recapSlide - 1) }
                        else if (view.recapSlide < n - 1) to(view.recapSlide + 1)
                    }
                }
                .pointerInput(n, w) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            val raw = -x.value / w
                            to(if (abs(v) > 400f) (if (v < 0) kotlin.math.ceil(raw).toInt() else kotlin.math.floor(raw).toInt()) else raw.roundToInt())
                        },
                        onDragCancel = { to(view.recapSlide) }
                    ) { change, dx ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val min = -(n - 1) * w
                        val cur = x.value
                        val k = if (cur > 0f || cur < min) 0.3f else 1f
                        scope.launch { x.snapTo(cur + dx * k) }
                        change.consume()
                    }
                }
        ) {
            slides.forEachIndexed { i, s ->
                val tertiary = aiContainer()
                val (bg, fg) = when (s.tone) {
                    Tone.P -> c.primaryContainer to c.onPrimaryContainer
                    Tone.S -> c.secondaryContainer to c.onSecondaryContainer
                    Tone.T -> tertiary
                    Tone.H -> c.surfaceContainerHigh to c.onSurface
                }
                Box(
                    Modifier.fillMaxSize().graphicsLayer { translationX = x.value + i * w }.background(bg)
                        .then(if (i != view.recapSlide) Modifier.clearAndSetSemantics {} else Modifier.semantics { contentDescription = "${i + 1} of $n: ${s.title}" })
                ) {
                    CompositionLocalProvider(LocalContentColor provides fg) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(start = 24.dp, end = 24.dp, top = pad.calculateTopPadding() + 88.dp, bottom = 112.dp),
                            verticalArrangement = Arrangement.Center
                        ) { s.body() }
                    }
                }
            }
        }
        // Top bar: close, and where you are.
        GlassBar(content, blur, { x.value }, RoundedCornerShape(28.dp), Modifier.align(Alignment.TopCenter).padding(top = pad.calculateTopPadding() + 10.dp, start = 12.dp, end = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                PreviewIconButton(Symbols.Close, "Close", tint = c.onSurface, onClick = { view.recap = false })
                HGap(12.dp)
                Row(Modifier.weight(1f).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until n) {
                        val grow by animateFloatAsState(if (i == view.recapSlide) 3f else 1f, Springs.spatial(), label = "seg")
                        Box(
                            Modifier.weight(grow).height(6.dp).clip(RoundedCornerShape(3.dp)).background(
                                when { i == view.recapSlide -> c.primary; i < view.recapSlide -> c.onSurface.copy(alpha = 0.64f); else -> c.onSurface.copy(alpha = 0.22f) }
                            )
                        )
                    }
                }
            }
        }
        // Floating toolbar: back, where you are, next (Done on the last).
        GlassBar(content, blur, { x.value }, RoundedCornerShape(32.dp), Modifier.align(Alignment.BottomCenter).padding(bottom = pad.calculateBottomPadding().coerceAtLeast(0.dp) + 16.dp, start = 16.dp, end = 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PreviewIconButton(Symbols.ArrowBack, "Previous slide", enabled = view.recapSlide > 0, tint = c.onSurface, onClick = { to(view.recapSlide - 1) })
                Text("${view.recapSlide + 1} of $n", style = WbType.label14, color = c.onSurface, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "${view.recapSlide + 1} of $n: ${slides[view.recapSlide].title}" })
                val last = view.recapSlide == n - 1
                PreviewButton(if (last) "Done" else "Next", ButtonKind.Filled, trailingIcon = if (last) null else Symbols.ArrowForward, contentDescription = if (last) "Done, close the recap" else "Next slide", onClick = { if (last) view.recap = false else to(view.recapSlide + 1) })
            }
        }
    }
}

/**
 * Floating chrome over the slides, in the preview's one frosted glass ([frostedGlass]): the slides passing under it
 * ([content], recorded once a frame), blurred and made richer under the neutral scrim and rim. With blur off it is the
 * solid surface container high.
 */
@Composable
private fun GlassBar(
    content: androidx.compose.ui.graphics.layer.GraphicsLayer, blur: Boolean, track: () -> Float, shape: RoundedCornerShape,
    modifier: Modifier, inner: @Composable () -> Unit
) {
    var pos by remember { mutableStateOf(Offset.Zero) }
    val backdrop = remember(content) {
        GlassBackdrop(size = { content.size.let { androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) } }) {
            drawLayer(content)
        }
    }
    Box(
        modifier.fillMaxWidth().onGloballyPositioned { pos = it.positionInParent() }
            .frostedGlass(shape, blur, backdrop, positionInBackdrop = { track(); pos })
    ) { inner() }
}

// ---------------------------------------------------------------------- slides
private fun recapSlides(m: WellbeingModel, view: ViewState, actions: WellbeingActions): List<Slide> {
    val r = m.recap()
    val ld = r.lightestDay
    return listOf(
        Slide("Your Week", Tone.P) {
            val c = MaterialTheme.colorScheme
            Eye("Your Week · ${r.name}")
            Text(bigNumber(r.avgSec, 0.45f), style = WbType.display57, maxLines = 1)
            Unit("a day")
            Line(r.cmp.text)
            Gap(24.dp)
            val top = maxOf(1, r.daysSec.maxOrNull() ?: 1)
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalAlignment = Alignment.Bottom) {
                r.daysSec.forEachIndexed { i, sec ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val lo = m.recapWeek[i] == r.lightest
                        Box(Modifier.width(32.dp).height((168f * sec / top).dp.coerceAtLeast(4.dp)).clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 4.dp)).background(if (lo) LocalContentColor.current else c.primary))
                        Gap(6.dp)
                        Text(Fmt.weekdayLetter(m.recapWeek[i].dayOfWeek), style = WbType.label12)
                    }
                }
            }
            Small("${Fmt.dur(r.totalSec)} in all. ${Fmt.weekday(r.lightest)} was the lightest.")
        },
        Slide("Lightest Day", Tone.T) {
            Eye("Lightest Day")
            // The day's time inside its shape, at a scale that fills the slide.
            InShape(Range.Week, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary) {
                Text(bigNumber(ld.totalSec, 0.45f), style = WbType.display36, maxLines = 1)
            }
            Gap(20.dp)
            Text(Fmt.weekday(r.lightest), style = WbType.display36)
            Unit("${Fmt.month(r.lightest)} ${r.lightest.dayOfMonth}")
            Line(if (r.lightest4) "Your lightest day in 4 weeks." else "Your lightest day of the week.")
            val aiSec = ld.apps.filterKeys { com.pixel.intelligentsearch.core.wellbeing.AiApps.isAi(it) }.values.sumOf { it.totalSec }
            Small(listOf(Fmt.pl(ld.unlockTotal, "unlock"), Fmt.pl(ld.notificationTotal, "notification"), if (aiSec >= 60) "${Fmt.dur(aiSec)} in AI apps" else "No AI apps").joinToString(" · "))
        },
        Slide("Most Opened", Tone.S) {
            val k = r.topOpen
            Eye("Most Opened")
            if (k != null) {
                InShape(Range.Month, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary) { AppIcon(k, 96.dp) }
                Gap(20.dp)
                Text(m.h.label(k), style = WbType.display36)
                val o = r.opens[k] ?: 0
                Line("Opened ${Fmt.pl(o, "time")}, about ${(o / 7.0).roundToInt()} a day.")
                val tt = r.topTime
                Small(if (tt == null || tt == k) "Also where most of your time went: ${Fmt.dur(r.time[k] ?: 0)}." else "Most time went to ${m.h.label(tt)}: ${Fmt.dur(r.time[tt] ?: 0)}. ${m.h.label(k)} had ${Fmt.dur(r.time[k] ?: 0)}.")
            }
        },
        Slide("AI Apps", Tone.T) {
            Eye("AI Apps")
            if (r.aiSec < 60) {
                Text("None", style = WbType.display57)
                Line("No time in AI apps last week.")
            } else {
                InShape(Range.Day, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, 168.dp) {
                    Icon(Symbols.AutoAwesome, null, Modifier.size(72.dp))
                }
                Gap(20.dp)
                Text(bigNumber(r.aiSec, 0.45f), style = WbType.display45, maxLines = 1)
                Unit("${r.aiPct}% of your screen time")
                Gap(20.dp)
                RowList(r.aiRows.map { it.key to Fmt.dur(it.sec) }, m)
                Small(listOf("${Fmt.pl(r.aiVisits, "visit")}.", r.aiMostly, r.aiVsUsual).filter { it.isNotEmpty() }.joinToString(" "))
            }
            Small("AI inside other apps isn't counted.")
        },
        Slide("Interruptions", Tone.S) {
            Eye("Interruptions")
            InShape(Range.Month, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, 168.dp) {
                Text(Fmt.num(r.received), style = WbType.display45, maxLines = 1)
            }
            Gap(12.dp)
            Unit("notifications")
            Line(openedAfter(m, r.followed, r.senders))
            Gap(20.dp)
            RowList(r.senders.map { it.key to Fmt.num(it.received) }, m)
            r.never?.let { s ->
                Gap(12.dp)
                InsightRow(s.key, "${m.h.label(s.key)} sent ${Fmt.num(s.received)}. You opened none.", m.h.label(s.key), LocalContentColor.current.copy(alpha = 0.09f), actions)
            }
        },
        Slide("That's Your Week", Tone.H) {
            StillShape(Range.Day, 168.dp, MaterialTheme.colorScheme.primary)
            Gap(20.dp)
            Text("That's Your Week", style = WbType.display36, modifier = Modifier.semantics { heading() })
            Line("Your next recap is ready ${Fmt.weekday(r.nextOn)}, ${Fmt.shortDate(r.nextOn)}.")
            Gap(24.dp)
            PreviewButton("See the Week's Details", ButtonKind.Filled, modifier = Modifier.fillMaxWidth(), tall = true, onClick = { view.recap = false; view.go(com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Period(Range.Week, m.recapWeek[0])) })
            Gap(12.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbols.Lock, null, Modifier.size(16.dp))
                HGap(6.dp)
                Text("Worked out on your phone. Nothing leaves it.", style = WbType.body14)
            }
        }
    )
}

/** A slide's figure inside its range shape, at a size that fills the slide. */
@Composable
private fun InShape(range: Range, color: androidx.compose.ui.graphics.Color, on: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 200.dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        StillShape(range, size, color)
        CompositionLocalProvider(LocalContentColor provides on) { content() }
    }
}

@Composable private fun Eye(t: String) { Text(t, style = WbType.value14, modifier = Modifier.semantics { heading() }); Gap(12.dp) }
@Composable private fun Unit(t: String) { Gap(2.dp); Text(t, style = WbType.name16) }
@Composable private fun Line(t: String) { Gap(16.dp); Text(t, style = WbType.title20) }
@Composable private fun Small(t: String) { Gap(12.dp); Text(t, style = WbType.body14) }

@Composable
private fun RowList(rows: List<Pair<String, String>>, m: WellbeingModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, (k, v) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).background(LocalContentColor.current.copy(alpha = 0.09f), segmentShape(i, rows.size)).padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(k, 40.dp, fallback = if (k == Attribution.GEMINI) Symbols.AutoAwesome else null)
                HGap(12.dp)
                Text(m.h.label(k), style = WbType.name16, modifier = Modifier.weight(1f))
                Text(v, style = WbType.title16)
            }
        }
    }
}
