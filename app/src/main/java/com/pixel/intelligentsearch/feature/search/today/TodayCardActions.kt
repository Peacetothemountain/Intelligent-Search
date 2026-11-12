package com.pixel.intelligentsearch.feature.search.today

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.glance.GlanceAlarm
import com.pixel.intelligentsearch.core.glance.GlanceItem
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.glance.GlanceTimer
import com.pixel.intelligentsearch.core.security.SafeWebLaunch
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.feature.search.calendarEventIntent
import com.pixel.intelligentsearch.feature.search.openGlanceItem
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardData
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardState
import com.pixel.intelligentsearch.feature.search.today.cards.CardForm
import com.pixel.intelligentsearch.feature.search.today.cards.CardFrame
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.ShapedContent
import com.pixel.intelligentsearch.feature.search.today.cards.SiteCards
import com.pixel.intelligentsearch.feature.search.today.cards.SiteData
import com.pixel.intelligentsearch.feature.search.today.cards.SnapLayout
import com.pixel.intelligentsearch.feature.search.today.cards.WidgetCards
import com.pixel.intelligentsearch.feature.search.today.cards.shape.shapedFits
import com.pixel.intelligentsearch.feature.search.today.cards.widget.openAppCard
import java.util.UUID

/*
 * What Discover's cards do: what a tap on a shaped card opens, what a card is called, whether it reads at a size, and
 * the cards the user adds and removes.
 */

/** How long a private card shows after a tap before it covers itself again. */
internal const val PRIVATE_SHOWN_MS = 8_000L

/**
 * A new app card for the bound widget [widgetId] of [provider]: its live widget, as wide and tall as the app asks for on
 * a home screen ([info], its sizes in pixels at [density]): half the page for a widget two cells wide or less, the
 * whole page otherwise, about 90 dp a row. As a shape it takes a soft cookie.
 */
internal fun newAppCard(widgetId: Int, provider: String, info: android.appwidget.AppWidgetProviderInfo?, density: Float): CardSpec {
    val cellDp = 90f
    val minWidthDp = (info?.minWidth ?: 0) / density
    val minHeightDp = (info?.minHeight ?: 0) / density
    val columns = info?.targetCellWidth?.takeIf { it > 0 } ?: kotlin.math.ceil((minWidthDp + 16f) / cellDp).toInt().coerceAtLeast(2)
    val rows = info?.targetCellHeight?.takeIf { it > 0 } ?: kotlin.math.ceil((minHeightDp + 16f) / cellDp).toInt().coerceAtLeast(1)
    return newAppCard(widgetId, provider, if (columns <= 2) 0.5f else 1f, (rows * cellDp).coerceIn(110f, 400f))
}

/** A new app card for the bound widget [widgetId] of [provider], its live widget [width] of the page and [height] dp tall. */
internal fun newAppCard(widgetId: Int, provider: String, width: Float, height: Float): CardSpec = CardSpec(
    id = "app-" + UUID.randomUUID(),
    kind = CardKind.APP,
    frame = CardFrame(width = width, height = height),
    form = CardForm.CARD,
    shape = "cookie4",
    widgetId = widgetId,
    provider = provider
)

/** A new website card for [url]: half the page wide, in the flower the approved design gives sites. */
internal fun newSiteCard(url: String): CardSpec = CardSpec(
    id = "site-" + UUID.randomUUID(),
    kind = CardKind.SITE,
    frame = CardFrame(width = 0.5f),
    form = CardForm.SHAPE,
    shape = "flower",
    url = url
)

/** What the Website tab says when the page is already a card. */
internal const val SITE_ALREADY_ADDED = "That site is already on Discover."

/**
 * Lets go of what a removed custom card read: its widget is unbound, its website forgotten. Anything another card in
 * [layout] still uses is kept.
 */
internal fun forgetCard(spec: CardSpec, layout: DiscoverLayout, widgets: WidgetCards, sites: SiteCards) {
    val others = layout.cards.filter { it.id != spec.id }
    when (spec.kind) {
        CardKind.APP -> if (spec.widgetId != 0 && others.none { it.widgetId == spec.widgetId }) widgets.remove(spec.widgetId)
        CardKind.SITE -> spec.url?.takeIf { url -> others.none { it.url == url } }?.let(sites::remove)
        else -> Unit
    }
}

/**
 * Opens what a shaped card stands for, the same place its full design opens: the weather, the clock app, the player,
 * the battery's usage, a search for the word or the last search, the next event, the calendar, what there is to catch
 * up on, the widget's app or the web page. A countdown has nowhere to go and does nothing.
 */
internal fun openShapedCard(
    context: Context,
    content: ShapedContent,
    media: GlanceMedia?,
    timer: GlanceTimer?,
    alarm: GlanceAlarm?,
    upNext: CalendarEvent?,
    onCatchUp: () -> Unit,
    onSearch: (String) -> Unit
) {
    when (content) {
        is ShapedContent.Weather -> openWeather(context)
        is ShapedContent.Timer -> timer?.let { openGlanceItem(context, GlanceItem.Timer(it)) }
        is ShapedContent.Alarm -> alarm?.let { openGlanceItem(context, GlanceItem.Alarm(it)) }
        is ShapedContent.Media -> media?.controls?.open(context)
        is ShapedContent.Battery -> launchSafeIntent(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        is ShapedContent.Word -> onSearch(content.word)
        is ShapedContent.Search -> onSearch(content.query)
        is ShapedContent.Event -> upNext?.let { launchSafeIntent(context, calendarEventIntent(it)) }
        is ShapedContent.Agenda -> launchSafeIntent(
            context,
            Intent(Intent.ACTION_VIEW, "content://com.android.calendar/time/${System.currentTimeMillis()}".toUri())
        )
        is ShapedContent.CatchUp -> onCatchUp()
        is ShapedContent.Tomorrow -> Unit
        is ShapedContent.Countdown, is ShapedContent.Delivery -> Unit
        is ShapedContent.App -> openAppCard(context, content.data)
        is ShapedContent.Site -> openSiteCard(context, content.data.url)
    }
}

/**
 * Opens a website card's page the way a typed address opens: a browser for https pages, and only Tor Browser while Tor
 * is the search engine (never a normal browser, which would show the site the phone's address).
 */
private fun openSiteCard(context: Context, url: String) {
    val engine = SettingsManager.storedPrefs(context)[SettingsManager.SEARCH_ENGINE]
    when (val launch = SafeWebLaunch.forAddress(context, url, engine)) {
        is SafeWebLaunch.Launch.Open -> launchSafeIntent(context, launch.intent)
        SafeWebLaunch.Launch.NeedsTorBrowser ->
            Toast.makeText(context, "Install Tor Browser to open websites while Tor is your search engine.", Toast.LENGTH_LONG).show()
        SafeWebLaunch.Launch.NotAWebAddress -> Unit
    }
}

/** Whether a tap on [content]'s card has somewhere to go ([openShapedCard]); one that hasn't is no button. */
internal fun opensSomething(content: ShapedContent): Boolean =
    content !is ShapedContent.Tomorrow && content !is ShapedContent.Countdown && content !is ShapedContent.Delivery

/**
 * What a card in a shape shows, in words: TalkBack can't see the drawing, so the card says its values after its name.
 * Null when it shows nothing its name doesn't say.
 */
internal fun shapedSpoken(content: ShapedContent): String? {
    fun said(vararg parts: String?): String? = parts.filterNot { it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotEmpty() }
    return when (content) {
        is ShapedContent.Weather -> said(content.temperature, content.headline)
        is ShapedContent.Timer -> said(content.label, content.reading)
        is ShapedContent.Alarm -> said("${content.time} ${content.day}", content.until)
        is ShapedContent.Delivery -> said(content.headline, content.detail)
        is ShapedContent.Media -> said(content.title, content.artist, if (content.playing) "playing" else "paused")
        is ShapedContent.Battery -> said("${content.level} percent", content.detail)
        is ShapedContent.Word -> said(content.word, content.kind, content.meaning)
        is ShapedContent.Countdown -> if (content.days == 1) "1 day until ${content.name}" else "${content.days} days until ${content.name}"
        is ShapedContent.Event -> said(content.title, content.time, content.until)
        is ShapedContent.Agenda -> said(if (content.count == 1) "1 left today" else "${content.count} left today", "next ${content.nextTitle} at ${content.next}")
        is ShapedContent.CatchUp -> said(if (content.count == 1) "1 new" else "${content.count} new", content.who)
        is ShapedContent.Search -> said(content.query)
        is ShapedContent.Tomorrow -> said(content.day, content.temperatures, content.detail)
        is ShapedContent.App -> content.data.model?.let { model ->
            when (model.layout) {
                SnapLayout.HERO -> said(model.hero, model.labels.firstOrNull())
                SnapLayout.LIST -> said(model.title, *model.rows.take(3).map { said(it.name, it.value, it.change) }.toTypedArray())
                SnapLayout.MONTH -> said(model.title, model.day.takeIf { it > 0 }?.toString())
                SnapLayout.CLOCK -> said(model.labels.firstOrNull())
            }
        }
        is ShapedContent.Site -> if (content.data.failed) "not updated" else null
    }
}

/**
 * The least side (dp) [content]'s shaped design reads at, at the user's [fontScale]: its main value at 11 dp or more
 * ([shapedFits]). With nothing to measure, 96 dp, or for one of the page's own parts with nothing to show yet
 * ([emptyKind]), the least its smallest design will need, so it still reads once something comes.
 */
internal fun leastShapeDp(content: ShapedContent?, fontScale: Float, emptyKind: CardKind? = null): Float {
    val units = when {
        content != null -> com.pixel.intelligentsearch.feature.search.today.cards.shape.mainUnits(content)
        emptyKind != null && emptyKind.builtIn -> com.pixel.intelligentsearch.feature.search.today.cards.shape.leastMainUnits(emptyKind)
        else -> return 96f
    }
    var side = 64f
    while (side < 400f && !com.pixel.intelligentsearch.feature.search.today.cards.shape.mainFits(units, side.dp, fontScale)) side += 4f
    return side
}

/**
 * What TalkBack calls [spec]: the built-in part's name, the app and its widget (and its state when it isn't ready), or
 * the website. A private card is only "Private card", so its name doesn't say what it covers.
 */
internal fun cardLabel(spec: CardSpec, apps: Map<Int, AppCardData>, sites: Map<String, SiteData>): String {
    if (spec.private) return "Private card"
    return when (spec.kind) {
        CardKind.APP -> apps[spec.widgetId]?.let { data ->
            val state = when (data.state) {
                AppCardState.READY -> null
                AppCardState.UPDATING -> "getting the latest"
                AppCardState.STALE -> "kept from earlier"
                AppCardState.SETUP -> "needs setup"
                AppCardState.REMOVED -> "app removed"
                AppCardState.PAUSED -> "work apps paused"
            }
            listOfNotNull(data.appLabel, data.widgetLabel.takeIf { it.isNotBlank() && it != data.appLabel }, state).joinToString(", ")
        } ?: "App card"
        CardKind.SITE -> spec.url?.let { url -> sites[url]?.let { "${it.domain}, ${it.title}" } ?: url } ?: "Website card"
        else -> spec.kind.label
    }
}
