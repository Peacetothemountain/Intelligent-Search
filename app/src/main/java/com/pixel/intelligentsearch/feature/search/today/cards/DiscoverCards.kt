package com.pixel.intelligentsearch.feature.search.today.cards

import android.content.ComponentName
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.StateFlow

/*
 * Discover's cards: every part of the page is a card the user can move, resize, give a Material shape, hide, and add
 * to from an app's home-screen widget or a website. This file is the contract the page, the grid, the shaped
 * renderer and the two readers share.
 *
 * NG Designs
 */

/**
 * What a card shows. The built-in kinds are the page's own parts; [APP] and [SITE] are added by the user. [shaped] kinds
 * have a small design that re-forms around a Material shape. [minCardDp] is the narrowest the full card design still
 * reads well at.
 */
internal enum class CardKind(val key: String, val builtIn: Boolean, val shaped: Boolean, val label: String, val minCardDp: Int) {
    UP_NEXT("up_next", true, true, "Up next", 280),
    REST_OF_DAY("rest_of_day", true, true, "Rest of your day", 290),
    WEATHER("weather", true, true, "Weather ahead", 270),
    TILES("tiles", true, true, "Timer, alarm and package", 290),
    NOW_PLAYING("now_playing", true, true, "Now playing", 250),
    CATCH_UP("catch_up", true, true, "Catch up", 260),
    CONTROLS("controls", true, false, "Quick controls", 120),
    PHONE("phone", true, true, "Your phone today", 290),
    LEFT_OFF("left_off", true, true, "Pick up where you left off", 160),
    WORD("word", true, true, "Word of the day", 300),
    YEAR("year", true, true, "Countdown", 290),
    TOMORROW("tomorrow", true, true, "Tomorrow", 170),
    APP("app", false, true, "App", 150),
    SITE("site", false, true, "Website", 150);

    companion object {
        fun of(key: String): CardKind? = entries.firstOrNull { it.key == key }

        /** The built-in cards in today's page order: Discover's default layout. */
        val PAGE_ORDER = listOf(UP_NEXT, REST_OF_DAY, WEATHER, TILES, NOW_PLAYING, CATCH_UP, CONTROLS, PHONE, LEFT_OFF, WORD, YEAR, TOMORROW)
    }
}

/** Whether a card is its full design or the small design inside a Material shape. */
internal enum class CardForm { CARD, SHAPE }

/**
 * Where a card sits on Discover's freeform page and how big it is. [x] and [width] are fractions of the page's width
 * (each card's place includes half the gap between cards on every side, so two halves sit side by side with the gap
 * between them). A card falls up the page until it meets a card above it (or the top), then sits [gap] dp further down,
 * so a card that is switched off or hidden leaves no hole. [height] (dp) is a height of the card's own: a full design
 * with none (0) is as tall as its content, an app's or website's box takes a usual height, and shapes are square.
 */
@Immutable
internal data class CardFrame(val x: Float = 0f, val width: Float = 1f, val height: Float = 0f, val gap: Float = 0f)

/** How a card draws. */
internal enum class CardMode {
    /** The built-in design, as tall as its content. */
    NATURAL,

    /** The small design inside a Material shape. */
    SHAPED,

    /** A box design (app and website cards in the card form). */
    BOX
}

/** The Material shapes a shaped card can take, by the names the layout stores. */
internal val CARD_SHAPES = listOf(
    "circle", "square", "cookie4", "cookie6", "cookie9", "cookie12", "clover4", "clover8",
    "sunny", "verysunny", "softburst", "flower", "pentagon", "gem", "arch"
)

/**
 * One card on the page. [id] is stable for the card's life. An [CardKind.APP] card holds its bound [widgetId] and the
 * widget's [provider] (flattened component name); a [CardKind.SITE] card holds its https [url]. [private] cards are
 * covered (never blurred) until tapped.
 */
@Immutable
internal data class CardSpec(
    val id: String,
    val kind: CardKind,
    val frame: CardFrame = CardFrame(),
    val form: CardForm = CardForm.CARD,
    val shape: String = "circle",
    val private: Boolean = false,
    val widgetId: Int = 0,
    val provider: String? = null,
    val url: String? = null
) {
    val mode: CardMode
        get() = when {
            form == CardForm.SHAPE && kind.shaped -> CardMode.SHAPED
            kind.builtIn -> CardMode.NATURAL
            else -> CardMode.BOX
        }
}

/**
 * The page's cards, top to bottom, and the built-in kinds the user hid (added back from Add card). Each card falls up
 * against the ones before it that it sits under.
 */
@Immutable
internal data class DiscoverLayout(val cards: List<CardSpec>, val hidden: Set<CardKind> = emptySet()) {
    companion object {
        /** Today's page: every built-in part, full width, in the page's order. */
        val DEFAULT = DiscoverLayout(CardKind.PAGE_ORDER.map { CardSpec(id = it.key, kind = it) })
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// App cards, read from home-screen widgets

/** Where an app card stands. */
internal enum class AppCardState {
    /** Fresh from the widget. */
    READY,

    /** Bound, first read on its way. */
    UPDATING,

    /** The last good values, kept because a later read couldn't be trusted (or the read failed); dimmed. */
    STALE,

    /** The widget wants a choice made in its app first. */
    SETUP,

    /** The app is gone; the card offers to remove itself. */
    REMOVED,

    /** The widget's app is in the work profile, which is paused. */
    PAUSED
}

/** How the reader understood a widget. */
internal enum class SnapLayout { HERO, LIST, MONTH, CLOCK }

/** One row of a list widget: its [value] ("51,285.73"), its [name] ("Dow Jones") and a [change] ("-0.46%"), if any. */
@Immutable
internal data class SnapRow(val value: String, val name: String = "", val change: String? = null)

/**
 * What a widget shows, read into a few slots. All text is plain, stripped of control and direction characters and capped
 * at 80 characters by the reader. A [HERO][SnapLayout.HERO] has its main [hero] text and, when that is a number, its
 * [number] (and [from], the number before an update, so the card counts from old to new), [unit] and [decimals]; a
 * [duration] number is minutes. A [LIST][SnapLayout.LIST] has a [title] and [rows]. A [MONTH][SnapLayout.MONTH] has the
 * month's [title], today's [day] and [weekday] (1 Monday to 7 Sunday). A [CLOCK][SnapLayout.CLOCK] shows the time now in
 * the phone's format. [labels] are the widget's other lines, most important first.
 */
@Immutable
internal data class SnapModel(
    val layout: SnapLayout,
    val hero: String = "",
    val number: Double? = null,
    val from: Double? = null,
    val unit: String = "",
    val decimals: Int = 0,
    val duration: Boolean = false,
    val labels: List<String> = emptyList(),
    val title: String = "",
    val rows: List<SnapRow> = emptyList(),
    val day: Int = 0,
    val weekday: Int = 0
)

/**
 * An app card's data: the widget [widgetId] of [packageName] ([appLabel], the widget's own [widgetLabel]), the card's
 * [accent] color (ARGB, from the app's icon), where it stands, and the last good [model] (kept while [state] is
 * [AppCardState.STALE]), read at [updatedAt] (wall clock, 0 before the first good read).
 */
@Immutable
internal data class AppCardData(
    val widgetId: Int,
    val packageName: String,
    val appLabel: String,
    val widgetLabel: String,
    val accent: Int,
    val state: AppCardState,
    val model: SnapModel?,
    val updatedAt: Long
)

/** A widget the user can make a card from: [provider] of [packageName], named [appLabel] and [widgetLabel]. */
@Immutable
internal data class WidgetChoice(
    val provider: ComponentName,
    val packageName: String,
    val appLabel: String,
    val widgetLabel: String,
    val accent: Int,
    val icon: ImageBitmap?
)

/**
 * Discover's app cards. As a full card a widget is shown live, as a launcher shows it, and its own buttons work
 * (LiveWidget). For a card in a shape, its updates are read in a separate, short-lived process ([refresh]) into
 * [SnapModel]s, which are cached app-private, out of every backup, and deleted when the card is removed or its app
 * uninstalled; a tap on a shape opens the app through the safe launch path.
 */
internal interface WidgetCards {
    /** Every bound card's data by widget id, from the cache at once and updated as reads come in. */
    val cards: StateFlow<Map<Int, AppCardData>>

    /** Reads [widgetIds] again in the reader process; returns at once. */
    fun refresh(widgetIds: Collection<Int>)

    /** The widgets that can become cards: home-screen widgets of apps in this profile, without search bars and shortcuts. */
    suspend fun choices(): List<WidgetChoice>

    /** Unbinds [widgetId] and deletes everything read from it. */
    fun remove(widgetId: Int)

    /**
     * Unbinds every widget that isn't one of [widgetIds] (the layout's) and deletes what was read from it, leaving
     * widgets an add in progress holds.
     */
    fun keepOnly(widgetIds: Set<Int>)
}

// ---------------------------------------------------------------------------------------------------------------------
// Website cards

/**
 * A website card's data: the page at [url] on [domain], with its [title], [description] and header [image] read once,
 * signed out, and again daily. [accent] (ARGB) is taken from the image, or from the domain when there is none.
 * [failed] when the last read failed (the card keeps what it had, dimmed, as an app card's stale state).
 */
@Immutable
internal data class SiteData(
    val url: String,
    val domain: String,
    val title: String,
    val description: String,
    val image: ImageBitmap?,
    val accent: Int,
    val readAt: Long,
    val failed: Boolean = false
)

/**
 * Discover's website cards. A page is read over https only, with no cookies and no scripts, only as far as its header,
 * within size and time limits, and never from a local or private address. What is read is cached app-private, out of
 * every backup, and deleted with the card.
 */
internal interface SiteCards {
    /** Every site card's data by url. */
    val sites: StateFlow<Map<String, SiteData>>

    /** Reads [urls] again when their last read is a day old; returns at once. */
    fun refresh(urls: Collection<String>)

    /**
     * Reads what the user typed ("weekend.recipes", "https://example.com/page") as a site card: the normalized https
     * url and its data, or a failure whose message says what to do, in the app's plain words.
     */
    suspend fun add(input: String): Result<SiteData>

    /** Forgets [url] and deletes what was read from it. */
    fun remove(url: String)
}

// ---------------------------------------------------------------------------------------------------------------------
// The shaped designs' data

/**
 * What a shaped card draws: one per kind with a shaped design. The page fills these from the same sources the
 * built-in designs read; app and site cards carry their reader's data.
 */
@Immutable
internal sealed interface ShapedContent {
    /** The weather now: [temperature] (with its degree sign), its [icon], the hours ahead ([hours] temperatures with [rain] flags), and a [headline] such as "Rain around 8 PM". */
    data class Weather(val temperature: String, val icon: ImageVector, val headline: String, val hours: List<Int>, val rain: List<Boolean>) : ShapedContent

    /** A running timer or stopwatch: its [reading] now ("4:59"), its [label], and how far through the minute it is ([sweep], 0 to 1). */
    data class Timer(val reading: String, val label: String, val sweep: Float) : ShapedContent

    /**
     * The next alarm at [time] ("7:00", drawn without AM or PM), on [day] ("Tomorrow"), [until] ("In 16 hr 22 min");
     * [fraction] of the way from now round the dial. [spokenTime] is the time in full ("7:00 AM"), for TalkBack.
     */
    data class Alarm(val time: String, val day: String, val until: String, val fraction: Float, val spokenTime: String = time) : ShapedContent

    /** A package on its way: [headline] ("Arriving today") and [detail]. */
    data class Delivery(val headline: String, val detail: String) : ShapedContent

    /** What is playing: [title], [artist], its [artwork], [progress] (0 to 1, null when unknown) and whether it is [playing]. */
    data class Media(val title: String, val artist: String, val artwork: ImageBitmap?, val progress: Float?, val playing: Boolean) : ShapedContent

    /** The battery at [level] percent, [charging] or not, with a [detail] ("Full by 4:10 PM"). */
    data class Battery(val level: Int, val charging: Boolean, val detail: String) : ShapedContent

    /** The word of the day: [word], its [kind] ("adjective") and [meaning]. */
    data class Word(val word: String, val kind: String, val meaning: String) : ShapedContent

    /** [days] until [name]. */
    data class Countdown(val days: Int, val name: String) : ShapedContent

    /** The next event, [title], at [time] ("3:30 PM"), [until] it starts ("in 52 min"); [fraction] of the last hour before it gone. */
    data class Event(val title: String, val time: String, val until: String, val fraction: Float) : ShapedContent

    /** The rest of the day: [count] things left, the next at [next] ("6:00 PM") named [nextTitle]; [hours] are their hours of the day (0 to 24). */
    data class Agenda(val count: Int, val next: String, val nextTitle: String, val hours: List<Float>) : ShapedContent

    /** [count] things to catch up on, the latest from [who]. */
    data class CatchUp(val count: Int, val who: String) : ShapedContent

    /** The last search, [query], to search again. */
    data class Search(val query: String) : ShapedContent

    /**
     * Tomorrow: its [day] ("Saturday"), the weather's [icon] and [temperatures] ("H 58°  L 41°") if known, and the first
     * [detail] ("Alarm · 7:00 AM"). [spokenTemperatures] is how TalkBack says them ("High 58°, low 41°").
     */
    data class Tomorrow(val day: String, val icon: ImageVector?, val temperatures: String, val detail: String, val spokenTemperatures: String = temperatures) : ShapedContent

    /** An app card. [icon] is the app's icon for states that show it. */
    data class App(val data: AppCardData, val icon: ImageBitmap?) : ShapedContent

    /** A website card. */
    data class Site(val data: SiteData) : ShapedContent
}
