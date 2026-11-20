package com.pixel.intelligentsearch.feature.tabs.wellbeing.ui

import androidx.compose.runtime.mutableIntStateOf
import com.pixel.intelligentsearch.feature.tabs.ui.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.animateScrollBy
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.feature.settings.tutorialSpot
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.feature.tabs.PageChrome
import com.pixel.intelligentsearch.feature.tabs.ui.Snack
import com.pixel.intelligentsearch.feature.tabs.ui.pageContainer
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Period
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Range
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.WellbeingModel
import kotlinx.coroutines.launch

/** Where the page is: waiting for its first look, no Usage Access, reading, or ready. */
sealed interface Load {
    data object Waiting : Load
    data object NoAccess : Load
    data class Loading(val done: Int, val total: Int, val first: Boolean) : Load
    class Ready(val model: WellbeingModel, val fresh: Boolean) : Load
}

/**
 * What the page's buttons hand to the phone. [openDigitalWellbeing] and [openTimer] are null where nothing on the phone
 * opens them, and their buttons aren't shown.
 */
class WellbeingActions(
    val grantAccess: () -> Unit = {},
    val openDigitalWellbeing: (() -> Unit)? = {},
    val openTimer: ((String) -> Unit)? = {},
    val openNotifications: (String) -> Unit = {},
    val clearHistory: () -> Unit = {}
)

enum class Sheet { About, Clear }

/**
 * The page's own state: the period, the selected bar, the open app row, the list's toggles, what is shown over the page
 * and how far it is scrolled. It is saved with the screen ([Saver]), so Android recreating the activity ("Don't keep
 * activities", the app stopped in the background) brings the page back exactly as it was left.
 */
@Stable
class ViewState(
    range: Range = Range.Day, day: java.time.LocalDate? = null, sel: Int? = null, open: String? = null, recap: Boolean = false,
    recapSlide: Int = 0, sheet: Sheet? = null, showAll: Boolean = false, leastFollowed: Boolean = false,
    lastToday: java.time.LocalDate? = null, scrollTo: Int? = null
) {
    var period by mutableStateOf<Period?>(day?.let { Period(range, it) })
    private var selected by mutableStateOf(sel)
    /** The selected bar or hour. Picking another (or none) closes an open app row: the list below it is then another. */
    var sel: Int?
        get() = selected
        set(v) {
            if (v != selected) open = null
            selected = v
        }
    var open by mutableStateOf(open)
    var showAll by mutableStateOf(showAll)
    var leastFollowed by mutableStateOf(leastFollowed)
    var recap by mutableStateOf(recap)
    var recapSlide by mutableIntStateOf(recapSlide)
    var sheet by mutableStateOf(sheet)
    /** The day that was today when the page last showed; when the date moves on, a page on that day follows it. */
    var lastToday: java.time.LocalDate? = lastToday
    val scroll = ScrollState(0)
    /** A scroll position to go back to once the page has its content again (after the activity was recreated). */
    var scrollTo: Int? = scrollTo
    /** The period whose chart last rose: coming back to the tab, it doesn't rise again from empty. */
    var risen: Period? = null

    fun go(p: Period) { period = p; sel = null; open = null; showAll = false }

    companion object {
        val Saver: Saver<ViewState, Any> = listSaver(
            save = { v ->
                listOf(
                    v.period?.range?.name, v.period?.day?.toString(), v.sel, v.open, v.recap, v.recapSlide, v.sheet?.name,
                    v.showAll, v.leastFollowed, v.lastToday?.toString(), v.scrollTo ?: v.scroll.value
                )
            },
            restore = { l ->
                ViewState(
                    range = (l[0] as String?)?.let { Range.valueOf(it) } ?: Range.Day,
                    day = (l[1] as String?)?.let { java.time.LocalDate.parse(it) },
                    sel = l[2] as Int?, open = l[3] as String?, recap = l[4] as Boolean, recapSlide = l[5] as Int,
                    sheet = (l[6] as String?)?.let { Sheet.valueOf(it) }, showAll = l[7] as Boolean, leastFollowed = l[8] as Boolean,
                    lastToday = (l[9] as String?)?.let { java.time.LocalDate.parse(it) }, scrollTo = (l[10] as Int).takeIf { it > 0 }
                )
            }
        )
    }
}

/**
 * The Wellbeing page. Its top bar is the one every page shares (back, the title, About on the right), pinned, with the
 * page scrolling beneath it; once the period's own header has scrolled away the bar names the period, and a tap on its
 * title springs the page back to the top. [snack], when set, shows the preview's snackbar ([onSnackDone] once it goes).
 */
@Composable
fun WellbeingScreen(
    load: Load,
    chrome: PageChrome,
    actions: WellbeingActions,
    modifier: Modifier = Modifier,
    view: ViewState = remember { ViewState() },
    snack: Snack? = null,
    onSnackDone: () -> Unit = {}
) {
    val scroll = view.scroll
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pad = chrome.contentPadding
    val model = (load as? Load.Ready)?.model
    if (model != null && view.period == null) view.period = Period(Range.Day, model.today)
    // The date moved on (midnight, or the clock or time zone changed) while the page showed what was today: it shows the
    // new today, rather than staying on that day under the name "Yesterday".
    if (model != null) {
        val was = view.lastToday
        val p = view.period
        if (was != null && was != model.today && p != null && p.day == was) view.go(Period(p.range, model.today))
        view.lastToday = model.today
    }
    // A period the history no longer has (after Clear history, or a day dated after today): back to today.
    if (model != null && view.period?.let { it.day.isAfter(model.today) || (it.range == Range.Day && !model.h.has(it.day)) } == true) view.go(Period(Range.Day, model.today))
    val period = view.period
    // Back where it was after the activity was recreated, once there is something to scroll.
    LaunchedEffect(model != null) {
        val to = view.scrollTo ?: return@LaunchedEffect
        if (model == null) return@LaunchedEffect
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        scroll.scrollTo(minOf(to, scroll.maxValue))
        view.scrollTo = null
    }

    // The tutorial's Wellbeing step: the page scrolls, if it must and can, to keep the part in the step's spotlight
    // clear of the step's card, never taking it up under the top bar.
    val tourHere = TutorialManager.liveStep == TutorialManager.WELLBEING_STEP
    LaunchedEffect(tourHere) {
        if (!tourHere) return@LaunchedEffect
        val (spot, cardTop) = snapshotFlow { TutorialManager.targetStates[TutorialManager.WELLBEING_STEP]?.rect to TutorialManager.cardTop }
            .first { it.first != null && it.second > 0f }
        val target = spot ?: return@LaunchedEffect
        val over = target.bottom + with(density) { 16.dp.toPx() } - cardTop
        val room = target.top - with(density) { (pad.calculateTopPadding() + 72.dp).toPx() }
        val by = minOf(over, room)
        if (by > 0f) scroll.animateScrollBy(by, Springs.spatial())
    }

    val covered = view.recap || view.sheet != null
    LaunchedEffect(covered) { chrome.setCovered(covered) }
    BackHandler(view.recap && view.sheet == null) { view.recap = false }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val headerGone by remember { derivedStateOf { scroll.value > with(density) { 64.dp.toPx() } } }
            PageTopBar(
                if (headerGone && model != null && period != null) model.title(period) else "Wellbeing",
                onBack = chrome.onBack,
                modifier = Modifier.padding(top = pad.calculateTopPadding()),
                onTitleClick = { scope.launch { scroll.animateScrollTo(0, Springs.spatial()) } }
            ) {
                TopBarAction(Symbols.Info, "About These Numbers", { view.sheet = Sheet.About })
            }
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .blurBands(bottom = LocalPillBlur.current)
                    .verticalScroll(scroll)
                    .padding(start = PageGutter, end = PageGutter, bottom = pad.calculateBottomPadding() + 24.dp)
            ) {
                when (load) {
                    Load.Waiting -> {}
                    Load.NoAccess -> { FirstRunCard(actions.grantAccess); LimitsCard(actions); Footer(view) }
                    is Load.Loading -> { LoadingCard(load); LimitsCard(actions); Footer(view) }
                    is Load.Ready -> if (period != null) {
                        PeriodHeader(model!!, period, view)
                        ReadyPage(model, period, view, actions, intro = load.fresh)
                        LimitsCard(actions)
                        Footer(view)
                    }
                }
            }
        }
        if (model != null) RecapLayer(model, view, actions, pad)
        SheetLayer(view, actions)
        PreviewSnackbar(snack, bottom = pad.calculateBottomPadding() + 8.dp, onDismiss = onSnackDone, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ---------------------------------------------------------------------- header
@Composable
private fun PeriodHeader(m: WellbeingModel, p: Period, view: ViewState) {
    val noun = p.range.name.lowercase()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 4.dp, bottom = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(m.title(p), style = WbType.title30, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).semantics { heading() })
        val prev = m.stepTarget(p, -1)
        val next = m.stepTarget(p, 1)
        PreviewIconButton(Symbols.ChevronLeft, "Previous $noun", enabled = prev != null, onClick = { prev?.let { view.go(Period(p.range, it)) } })
        PreviewIconButton(Symbols.ChevronRight, "Next $noun", enabled = next != null, onClick = { next?.let { view.go(Period(p.range, it)) } })
    }
    RangeSelector(p.range) { r -> if (r != p.range) view.go(Period(r, p.day)) }
    Gap(12.dp)
}

/** Day, Week, Month: the chosen one on an indicator that slides to it on the spatial spring (no fade). */
@Composable
private fun RangeSelector(selected: Range, onSelect: (Range) -> Unit) {
    val c = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    val x = remember { Animatable(selected.ordinal.toFloat()) }
    LaunchedEffect(selected) { if (animate) x.animateTo(selected.ordinal.toFloat(), Springs.spatial()) else x.snapTo(selected.ordinal.toFloat()) }
    val dark = c.surface.let { (it.red + it.green + it.blue) / 3 < 0.5f }
    val track = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().matteCard(track, lit = false, border = false).drawBehind {
            val pad = 4.dp.toPx()
            val w = (size.width - 2 * pad) / 3
            drawRoundRect(c.primaryContainer, Offset(pad + x.value * w, pad), Size(w, size.height - 2 * pad), CornerRadius(24.dp.toPx()))
        }.padding(4.dp)
    ) {
        Range.entries.forEach { r ->
            val on = r == selected
            val color by animateColorAsState(if (on) c.onPrimaryContainer else c.onSurfaceVariant, Springs.effects(), label = "range")
            val shape = RoundedCornerShape(24.dp)
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(shape).semantics { this.selected = on }.bouncyClickable(shape, role = Role.Tab) { onSelect(r) },
                contentAlignment = Alignment.Center
            ) { Text(r.name, style = if (on) WbType.value14 else WbType.label14, color = color, textAlign = TextAlign.Center) }
        }
    }
}

// ---------------------------------------------------------------------- the page
@Composable
private fun ReadyPage(m: WellbeingModel, p: Period, view: ViewState, actions: WellbeingActions, intro: Boolean) {
    Box(Modifier.tutorialSpot(TutorialManager.WELLBEING_STEP)) { HeroCard(m, p, intro) }
    Gap(2.dp)
    ChartCard(m, p, view, intro)
    Gap(12.dp)
    if (m.isCurrent(p) && p.range != Range.Month) { RecapEntry(m) { view.recapSlide = 0; view.recap = true }; Gap(12.dp) }
    AppsCard(m, p, view, actions)
    Gap(12.dp)
    AiCard(m, p, view, actions)
    Gap(12.dp)
    InterruptionsCard(m, p, view, actions)
}

@Composable
internal fun LimitsCard(actions: WellbeingActions) {
    // Limits are set in Digital Wellbeing: on a phone without it, there is nothing for this card to open.
    val openDigitalWellbeing = actions.openDigitalWellbeing ?: return
    Gap(12.dp)
    MatteCard(Modifier.fillMaxWidth()) {
        CardTitle("Set Limits")
        Gap(4.dp)
        Text("App timers, Focus mode and Bedtime mode are set in Digital Wellbeing.", style = WbType.body14, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(16.dp)
        PreviewButton("Open Digital Wellbeing", ButtonKind.Tonal, modifier = Modifier.fillMaxWidth(), tall = true, trailingIcon = Symbols.OpenInNew, onClick = openDigitalWellbeing)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(view: ViewState) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Symbols.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        HGap(8.dp)
        Text("Worked out on your phone. Nothing leaves it.", style = WbType.body12, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        PreviewButton("About These Numbers", ButtonKind.Text, textStyle = WbType.label12, onClick = { view.sheet = Sheet.About })
        PreviewButton("Clear Wellbeing History", ButtonKind.Text, textStyle = WbType.label12, onClick = { view.sheet = Sheet.Clear })
    }
}

// ---------------------------------------------------------------------- first run and loading
@Composable
private fun FirstRunCard(grant: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val name = LocalContext.current.applicationInfo.loadLabel(LocalContext.current.packageManager).toString()
    MatteCard(Modifier.fillMaxWidth().tutorialSpot(TutorialManager.WELLBEING_STEP), color = c.primaryContainer, contentColor = c.onPrimaryContainer, padding = 24.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeMark(Range.Day, 96.dp, c.primary, Symbols.HourglassTop, c.onPrimary, 40.dp)
            Gap(12.dp)
            Text("See Where Your Time Goes", style = WbType.headline28, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Gap(8.dp)
            Text("Turn on usage access to see your screen time, unlocks and AI use, worked out on your phone.", style = WbType.body16, textAlign = TextAlign.Center)
            Gap(12.dp)
            Text(
                "Your history stays on this phone for up to 400 days. Clear it any time at the bottom of this page.",
                style = WbType.body14, textAlign = TextAlign.Center
            )
            Gap(20.dp)
            PreviewButton("Turn On Usage Access", ButtonKind.Filled, modifier = Modifier.fillMaxWidth(), tall = true, onClick = grant)
            Gap(8.dp)
            Text("On the next screen, choose $name and switch it on.", style = WbType.body12, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LoadingCard(load: Load.Loading) {
    val c = MaterialTheme.colorScheme
    MatteCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, color = c.primaryContainer, contentColor = c.onPrimaryContainer, padding = 24.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeMark(Range.Day, 96.dp, c.primary, Symbols.History, c.onPrimary, 40.dp)
            Gap(12.dp)
            Text("Catching Up", style = WbType.headline28, textAlign = TextAlign.Center)
            Gap(8.dp)
            Text(
                if (load.total > 2) "Reading the days your phone has kept. This takes about a second, once." else "Reading today so far.",
                style = WbType.body16, textAlign = TextAlign.Center
            )
            Gap(8.dp)
            val target = if (load.total > 0) load.done.toFloat() / load.total else 0f
            val v by animateFloatAsState(target, Springs.fast(), label = "catch")
            Gauge(Modifier.fillMaxWidth().height(24.dp), value = v.toDouble(), max = 1.0, mark = null, stop = null, wavy = true, line = c.primary, track = c.onPrimaryContainer, cut = c.primaryContainer, stopOn = c.onPrimary)
            Gap(6.dp)
            if (load.total > 0) Text("${load.done} of ${load.total} days", style = WbType.label12)
        }
    }
}

// ---------------------------------------------------------------------- sheets
/** What each number means, results first: a bold name, then a line or two. */
private val ABOUT = listOf(
    "Screen time" to "Time with an app on screen. The home screen, lock screen, and music or calls with the screen off don't count. Two apps side by side count once.",
    "Digital Wellbeing" to "It can show more. It may count time on the home screen, and apps in a work profile or Private Space, which aren't counted here.",
    "Your usual day" to "Your average full day over the 4 weeks before. Until there are 4 weeks, it's from the days so far.",
    "Phone down" to "Your longest break of 3 hours or more that ends in the morning. A quick look at night counts as a pick-up.",
    "AI apps" to "Apps made for AI chat, plus Gemini wherever it opens. AI built into other apps isn't counted.",
    "Notifications" to "“Opened” means you went to the app within 10 minutes. “After a notification” means one arrived just before you unlocked.",
    "Your history" to "Kept only on this phone, for up to 400 days. Clear it any time at the bottom of the page."
)

/** About these numbers, and Clear Wellbeing history: the preview's one sheet ([PreviewSheet]). */
@Composable
private fun SheetLayer(view: ViewState, actions: WellbeingActions) {
    val c = MaterialTheme.colorScheme
    PreviewSheet(
        view.sheet,
        onDismiss = { view.sheet = null },
        paneTitle = { if (it == Sheet.About) "About These Numbers" else "Clear Wellbeing History" }
    ) { s ->
        if (s == Sheet.About) {
            SheetTitle("About These Numbers")
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ABOUT.forEach { (name, line) ->
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = c.onSurface)) { append("$name. ") }
                            append(line)
                        },
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant
                    )
                }
            }
            SheetButtons { PreviewButton("Done", ButtonKind.Filled, onClick = { view.sheet = null }) }
        } else {
            SheetTitle("Clear Wellbeing History?")
            SheetText(
                "This deletes your Wellbeing history from this phone. About the last 10 days come back from your phone's own records. Older days are gone for good."
            )
            SheetButtons {
                PreviewButton("Cancel", ButtonKind.Text, onClick = { view.sheet = null })
                PreviewButton("Clear History", ButtonKind.Filled, onClick = { view.sheet = null; actions.clearHistory() })
            }
        }
    }
}
