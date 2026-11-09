package com.pixel.intelligentsearch.feature.search.today

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.glance.GlanceAlarm
import com.pixel.intelligentsearch.core.glance.GlanceItem
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.glance.GlanceTimer
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.feature.search.openGlanceItem
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardData
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardState
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardMode
import com.pixel.intelligentsearch.feature.search.today.cards.CardSize
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.GRID_COLUMNS
import com.pixel.intelligentsearch.feature.search.today.cards.ShapedContent
import com.pixel.intelligentsearch.feature.search.today.cards.SiteCards
import com.pixel.intelligentsearch.feature.search.today.cards.SiteData
import com.pixel.intelligentsearch.feature.search.today.cards.WidgetCards
import com.pixel.intelligentsearch.feature.search.today.cards.modeFor
import com.pixel.intelligentsearch.feature.search.today.cards.shape.boxFits
import com.pixel.intelligentsearch.feature.search.today.cards.shape.shapedFits
import com.pixel.intelligentsearch.feature.search.today.cards.widget.openAppCard
import java.util.UUID

/*
 * What Discover's cards do: what a tap on a shaped card opens, what a card is called, whether it reads at a size, and
 * the cards the user adds and removes.
 */

/** How long a private card shows after a tap before it covers itself again. */
internal const val PRIVATE_SHOWN_MS = 8_000L

/** Discover's side padding, each side, and its gap between cards: the grid's width is the page's less both sides. */
private val PAGE_SIDE = 14.dp
private val CARD_GAP = 10.dp

/** A new app card for the bound widget [widgetId] of [provider]: square, two cells by two, in a soft shape. */
internal fun newAppCard(widgetId: Int, provider: String): CardSpec = CardSpec(
    id = "app-" + UUID.randomUUID(),
    kind = CardKind.APP,
    size = CardSize.Cells(2, 2),
    shape = "cookie4",
    widgetId = widgetId,
    provider = provider
)

/** A new website card for [url]: square, two cells by two, in the flower the approved design gives sites. */
internal fun newSiteCard(url: String): CardSpec = CardSpec(
    id = "site-" + UUID.randomUUID(),
    kind = CardKind.SITE,
    size = CardSize.Cells(2, 2),
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
 * the battery's usage, a search for the word, the photo, the widget's app or the web page. A countdown has nowhere to
 * go and does nothing.
 */
internal fun openShapedCard(
    context: Context,
    content: ShapedContent,
    media: GlanceMedia?,
    timer: GlanceTimer?,
    alarm: GlanceAlarm?,
    onSearch: (String) -> Unit
) {
    when (content) {
        is ShapedContent.Weather -> openWeather(context)
        is ShapedContent.Timer -> timer?.let { openGlanceItem(context, GlanceItem.Timer(it)) }
        is ShapedContent.Alarm -> alarm?.let { openGlanceItem(context, GlanceItem.Alarm(it)) }
        is ShapedContent.Media -> media?.controls?.open(context)
        is ShapedContent.Battery -> launchSafeIntent(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        is ShapedContent.Word -> onSearch(content.word)
        is ShapedContent.Memory, is ShapedContent.Year, is ShapedContent.Countdown, is ShapedContent.Delivery -> Unit
        is ShapedContent.App -> openAppCard(context, content.data)
        is ShapedContent.Site -> launchSafeIntent(context, Intent(Intent.ACTION_VIEW, content.data.url.toUri()))
    }
}

/**
 * Whether [spec] reads well at [size]: a built-in design needs its narrowest width; a shaped design needs its main value
 * at 11 dp or more ([shapedFits]); a box design needs its content to fit ([boxFits]). With no [content] to measure (a
 * card with nothing to show yet), a shaped or box size is allowed from one cell.
 */
internal fun cardFits(spec: CardSpec, size: CardSize, content: ShapedContent?, density: Density, pageWidthDp: Float = 0f): Boolean {
    if (size.columns !in 1..GRID_COLUMNS) return false
    val cell = cellDp(pageWidthDp, density)
    return when (modeFor(spec.kind, size)) {
        CardMode.NATURAL -> size.columns >= spec.kind.minNaturalColumns
        CardMode.SHAPED -> {
            val rows = (size as? CardSize.Cells)?.rows ?: return false
            val side = minOf(span(size.columns, cell), span(rows, cell))
            content == null || shapedFits(content, side.dp, density.fontScale)
        }
        CardMode.BOX -> {
            val rows = (size as? CardSize.Cells)?.rows ?: return false
            content == null || boxFits(content, span(size.columns, cell).dp, span(rows, cell).dp, density.fontScale)
        }
    }
}

/** A cell's side in dp on a page [pageWidthDp] wide (the screen's width when it isn't known yet). */
private fun cellDp(pageWidthDp: Float, density: Density): Float {
    val width = if (pageWidthDp > 0f) pageWidthDp else android.content.res.Resources.getSystem().displayMetrics.widthPixels / density.density
    val grid = width - 2 * PAGE_SIDE.value
    return (grid - (GRID_COLUMNS - 1) * CARD_GAP.value) / GRID_COLUMNS
}

/** [n] cells and the gaps between them, in dp. */
private fun span(n: Int, cell: Float): Float = n * cell + (n - 1) * CARD_GAP.value

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
