package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardState
import com.pixel.intelligentsearch.feature.search.today.cards.ShapedContent
import com.pixel.intelligentsearch.feature.search.today.cards.SnapLayout
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.min

/*
 * A shaped card: one of Discover's small designs drawn inside a Material shape that morphs when the shape changes. It
 * draws in once when it first comes on screen (count-ups included) and then holds still, unless it holds one of the
 * page's ambient slots (see AmbientBudget).
 *
 * NG Designs
 */

/** How long a shaped design's drawing in lasts at most; past this everything is in place. */
private const val TIMELINE_MS = 3_000f

/** How long a count-up runs. */
private const val COUNT_MS = 900

/**
 * Draws [content]'s shaped design inside the Material shape [shape] (a name from CARD_SHAPES), filling the largest
 * square the [modifier]'s bounds hold, centered. The design is laid out on 180 units and scaled to that square; text
 * too small to read steps aside, the main value always stays. A [private] card is covered by stripes (never a blur)
 * until it is [revealed]. The caller sets the card's content description: the drawing itself has no semantics.
 */
@Composable
internal fun ShapedCard(
    content: ShapedContent,
    shape: String,
    modifier: Modifier = Modifier,
    private: Boolean = false,
    revealed: Boolean = false
) {
    val animate by rememberAnimationsEnabled()
    val colors = rememberShapedColors()
    val type = rememberCardType()
    val outline = rememberMorphingOutline(shape)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val covered = private && !revealed
    var onScreen by remember { mutableStateOf(false) }

    // Drawing in: a clock from 0 to TIMELINE_MS each part of the design reads its own delay and length from. It runs
    // only while the card is on screen and uncovered, and starts again when the card shows something else.
    val timeline = remember(designOf(content)) { Animatable(if (animate) 0f else TIMELINE_MS) }
    LaunchedEffect(timeline, animate, onScreen, covered) {
        when {
            !animate -> timeline.snapTo(TIMELINE_MS)
            onScreen && !covered && timeline.value < TIMELINE_MS ->
                timeline.animateTo(TIMELINE_MS, tween((TIMELINE_MS - timeline.value).toInt().coerceAtLeast(1), easing = LinearEasing))
        }
    }
    val counter = rememberCountUp(content, animate, onScreen && !covered)

    // Covering is instant, so nothing private ever shows; uncovering fades the stripes away.
    val cover = remember { Animatable(if (covered) 1f else 0f) }
    LaunchedEffect(covered, animate) {
        when {
            covered -> cover.snapTo(1f)
            animate -> cover.animateTo(0f, tween(250))
            else -> cover.snapTo(0f)
        }
    }

    val ambient = rememberAmbientTime(wants = !covered && wantsAmbient(content), onScreen = onScreen)
    val extras = rememberCardExtras(content)
    val alarmDegrees = remember(content) {
        val now = LocalTime.now()
        (((now.hour + now.minute / 60f) - 12f) / 24f * 360f + 360f) % 360f
    }
    val stage = remember(type) { Stage(type) }
    val held = remember { HeldTime() }
    val minText = with(density) { 8.5.dp.toPx() }
    val minIcon = with(density) { 9.dp.toPx() }
    val textScale = shapedTextScale(density.fontScale)

    Box(
        modifier.onGloballyPositioned {
            val bounds = it.boundsInWindow()
            val shown = bounds.width > 0f && bounds.height > 0f
            if (shown != onScreen) onScreen = shown
        }
    ) {
        Spacer(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .drawBehind {
                    val side = min(size.width, size.height)
                    if (side <= 0f) return@drawBehind
                    val k = side / DESIGN
                    stage.scope = this
                    stage.colors = colors
                    stage.outline = outline.current
                    stage.k = k
                    stage.t = timeline.value
                    stage.ambient = ambient != null
                    stage.now = held.at(ambient?.invoke())
                    stage.count = counter.value
                    stage.countDone = counter.done
                    stage.animate = animate
                    stage.rtl = rtl
                    stage.textScale = textScale
                    stage.minText = minText
                    stage.minIcon = minIcon
                    stage.alpha = 1f
                    stage.shapeName = shape
                    val coverAlpha = cover.value
                    translate((size.width - side) / 2f, (size.height - side) / 2f) {
                        scale(k, Offset.Zero) {
                            if (coverAlpha < 1f) stage.design(content, extras, alarmDegrees)
                            if (coverAlpha > 0f) stage.privateCover(coverAlpha)
                        }
                    }
                }
        )
    }
}

/**
 * A card's ambient time, held where its motion stopped: still at 0 until the card first holds an ambient slot, moving
 * with the page's loop while it holds one, and kept where it was when it lets the slot go, so the light, the record and
 * the drift pick up where they left off rather than jumping. Drawing bookkeeping only, never state.
 */
private class HeldTime {
    private var base = 0f
    private var held = 0f
    private var moving = false

    /** The time to draw at, given the loop's time [loop] now (null while the card holds no slot). */
    fun at(loop: Float?): Float {
        if (loop == null) {
            moving = false
            return held
        }
        if (!moving) {
            base = held - loop
            moving = true
        }
        held = base + loop
        return held
    }
}

/** Draws [content]'s design. */
private fun Stage.design(content: ShapedContent, extras: CardExtras, alarmDegrees: Float) {
    when (content) {
        is ShapedContent.Weather -> weather(content)
        is ShapedContent.Timer -> timer(content)
        is ShapedContent.Alarm -> alarm(content, alarmDegrees)
        is ShapedContent.Delivery -> delivery(content)
        is ShapedContent.Media -> media(content)
        is ShapedContent.Battery -> battery(content)
        is ShapedContent.Word -> word(content)
        is ShapedContent.Countdown -> countdown(content)
        is ShapedContent.Event -> event(content)
        is ShapedContent.Agenda -> agenda(content)
        is ShapedContent.CatchUp -> catchUp(content)
        is ShapedContent.Search -> search(content)
        is ShapedContent.Tomorrow -> tomorrow(content)
        is ShapedContent.App -> app(content, extras)
        is ShapedContent.Site -> site(content, extras)
    }
}

/** What the card shows, as far as drawing in again goes: a new kind, or an app card in a new state or layout. */
private fun designOf(content: ShapedContent): String = when (content) {
    is ShapedContent.App -> {
        val d = content.data
        "app:${d.state}:${d.model?.layout}"
    }
    else -> content::class.java.simpleName
}

/** Whether [content]'s design has ambient motion to keep (the living light, a spinning record, a shimmer). */
private fun wantsAmbient(content: ShapedContent): Boolean = when (content) {
    is ShapedContent.Battery, is ShapedContent.Word, is ShapedContent.Search -> true
    is ShapedContent.Media -> content.playing
    is ShapedContent.App -> content.data.state == AppCardState.READY || content.data.state == AppCardState.STALE || content.data.state == AppCardState.UPDATING
    else -> false
}

/** The number a design counts up to, if it has one. */
internal fun mainNumber(content: ShapedContent): Double? = when (content) {
    is ShapedContent.Battery -> content.level.toDouble()
    is ShapedContent.Countdown -> content.days.toDouble().takeIf { content.days > 0 }
    is ShapedContent.Weather -> splitNumber(content.temperature)?.first?.toDouble()
    is ShapedContent.App -> content.data.model?.takeIf { it.layout == SnapLayout.HERO }?.number
    else -> null
}

/** A count-up: from [from] to [to] as [progress] runs, eased. */
@Stable
private class CountUp(from: Double, to: Double?, start: Float) {
    var from by mutableDoubleStateOf(from)
    var to by mutableStateOf(to)
    var first = true
    val progress = Animatable(start)

    /** The number now, or null with nothing to count. Read in drawing. */
    val value: Double?
        get() = to?.let { from + (it - from) * easeOut(progress.value) }

    val done: Boolean get() = progress.value >= 1f
}

/**
 * Counts up to [content]'s number: from 0 (or the widget's number before its update) when the card first shows,
 * and from the old number to the new one on each change. At once with animations off; only while [canStart].
 */
@Composable
private fun rememberCountUp(content: ShapedContent, animate: Boolean, canStart: Boolean): CountUp {
    val target = mainNumber(content)
    val modelFrom = (content as? ShapedContent.App)?.data?.model?.from
    val count = remember { CountUp(modelFrom ?: 0.0, target, if (animate && target != null) 0f else 1f) }
    LaunchedEffect(target, animate, canStart) {
        if (target != count.to) {
            val old = count.to
            count.from = modelFrom ?: (if (old == null) 0.0 else count.value ?: 0.0)
            count.first = old == null
            count.to = target
            count.progress.snapTo(if (target == null) 1f else 0f)
        }
        when {
            !animate -> count.progress.snapTo(1f)
            canStart && count.progress.value < 1f ->
                count.progress.animateTo(1f, tween(COUNT_MS, delayMillis = if (count.first) 150 else 0, easing = LinearEasing))
        }
    }
    return count
}

/**
 * The time now as a clock widget shows it, in the phone's 12- or 24-hour format without AM or PM, changing on the
 * minute while [enabled] (empty otherwise). Only this recomposes, once a minute.
 */
@Composable
internal fun rememberClockText(enabled: Boolean): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val clock by produceState("", enabled, locale) {
        if (!enabled) {
            value = ""
            return@produceState
        }
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
        val format = java.time.format.DateTimeFormatter.ofPattern(pattern, locale)
        while (true) {
            val now = LocalTime.now()
            value = format.format(now)
            delay(((60 - now.second) * 1000L - now.nano / 1_000_000L).coerceAtLeast(200L))
        }
    }
    return clock
}

/** How long ago [readAt] (wall clock) was, as "2 hr ago"; empty before a first read. */
@Composable
internal fun rememberAgo(readAt: Long): String =
    remember(readAt) { if (readAt > 0L) Durations.ago(System.currentTimeMillis() - readAt) else "" }

/** The extras an app or site card needs as it composes: its icon, the clock, how long ago it was read, the week. */
@Composable
private fun rememberCardExtras(content: ShapedContent): CardExtras {
    val locale = LocalConfiguration.current.locales[0]
    val app = content as? ShapedContent.App
    val model = app?.data?.model
    val clock = rememberClockText(model?.layout == SnapLayout.CLOCK)
    val readAt = app?.data?.updatedAt ?: (content as? ShapedContent.Site)?.data?.readAt ?: 0L
    val ago = rememberAgo(readAt)
    val week = remember(locale, model?.weekday) {
        val first = WeekFields.of(locale).firstDayOfWeek
        val letters = (0 until 7).map { first.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale) }
        val weekday = model?.weekday ?: 0
        val today = if (weekday in 1..7) (0 until 7).firstOrNull { first.plus(it.toLong()) == DayOfWeek.of(weekday) } ?: -1 else -1
        letters to today
    }
    val number = remember(locale, model?.decimals) { model?.let { numberFormat(locale, it.decimals) } }
    return remember(app?.icon, clock, ago, week, number, locale) {
        CardExtras(app?.icon, clock, ago, week.first, week.second, number, locale)
    }
}
