package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.ui.icons.filled.History
import com.pixel.intelligentsearch.core.ui.icons.outlined.Tune
import com.pixel.intelligentsearch.feature.search.today.CARD_ALPHA
import com.pixel.intelligentsearch.feature.search.today.CardShape
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardData
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardState
import com.pixel.intelligentsearch.feature.search.today.cards.SnapLayout
import com.pixel.intelligentsearch.feature.search.today.cards.SnapModel
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.max

/*
 * An app card wider than it is tall: the widget's snapshot as a box on the page's card, lit from its top start corner
 * by the app's accent, with every state of the card designed.
 *
 * NG Designs
 */

/**
 * Draws app card [data] as a box filling the size it is given, with the page's 24 dp corners: the app's [icon] and
 * name, then the widget's hero, list, month or clock, or what the card is waiting for (its first read, setup, its app
 * back, the work profile). Stale values show dimmed with when they were read. A [private] card is covered by stripes
 * until [revealed]. The caller sets the card's content description.
 */
@Composable
internal fun AppCardBox(
    data: AppCardData,
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
    private: Boolean = false,
    revealed: Boolean = false
) {
    val colors = rememberShapedColors()
    val accent = Color(data.accent)
    val model = data.model
    val hasData = (data.state == AppCardState.READY || data.state == AppCardState.STALE) && model != null
    val stale = hasData && data.state == AppCardState.STALE
    val removed = !hasData && data.state == AppCardState.REMOVED
    val covered = private && !revealed
    val ago = rememberAgo(data.updatedAt)
    CoveredBox(
        covered,
        CardShape,
        modifier
            .clip(CardShape)
            .drawWithCache {
                val lit = Brush.radialGradient(
                    0f to mix(colors.cardSolid, accent, 0.24f), 0.62f to colors.cardSolid, 1f to colors.cardSolid,
                    center = Offset(size.width * 0.1f, 0f), radius = max(size.width, size.height) * 1.2f
                )
                val inset = 6.dp.toPx()
                val dashed = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                val track = colors.on.copy(alpha = if (colors.dark) 0.25f else 0.19f)
                val corner = CornerRadius(18.dp.toPx())
                onDrawBehind {
                    if (removed) {
                        drawRect(colors.card)
                        drawRoundRect(track, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), corner, style = dashed)
                    } else {
                        drawRect(lit, alpha = CARD_ALPHA)
                    }
                }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .boxDrawIn("${data.state}:${model?.layout}", !covered)
                .padding(14.dp)
        ) {
            Head(data, icon, accent, if (stale) updatedNote(ago) else null)
            if (hasData) {
                Column(Modifier.alpha(if (stale) 0.78f else 1f)) { AppBody(data, model, accent, !covered) }
            } else {
                AppWaiting(data, icon, accent)
            }
        }
    }
}

/** The app's icon and name in small capitals, and the stale [note] at the end when there is one. */
@Composable
private fun Head(data: AppCardData, icon: ImageBitmap?, accent: Color, note: String?) {
    val colors = rememberShapedColors()
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, accent, 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(data.appLabel.uppercase(locale), style = labelStyle(), color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (note != null) {
            Icon(Icons.Filled.History, contentDescription = null, tint = colors.amber, modifier = Modifier.padding(start = 6.dp).size(13.dp))
            Text(note, style = smallStyle(), color = colors.amber, maxLines = 1, modifier = Modifier.padding(start = 3.dp))
        }
    }
}

/** The widget's snapshot: a hero value and its label, up to three rows of a list, a month, or a clock. */
@Composable
private fun AppBody(data: AppCardData, model: SnapModel, accent: Color, start: Boolean) {
    val colors = rememberShapedColors()
    val locale = LocalConfiguration.current.locales[0]
    when (model.layout) {
        SnapLayout.HERO -> {
            val format = remember(locale, model.decimals) { numberFormat(locale, model.decimals) }
            CountText(model, format, start, bigStyle(), colors.on, Modifier.padding(top = 8.dp))
            model.labels.firstOrNull()?.let { Text(it, style = smallStyle(), color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        SnapLayout.LIST -> if (model.rows.isEmpty()) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CardIcons.TaskAlt, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Column(Modifier.padding(start = 10.dp)) {
                    Text("All done", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = colors.on, maxLines = 1)
                    if (model.title.isNotBlank()) Text(model.title, style = smallStyle(), color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            val rowStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            for (row in model.rows.take(3)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name.ifBlank { row.value }, style = rowStyle, color = colors.on, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (row.name.isNotBlank()) Text(row.value, style = rowStyle, color = colors.on, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    row.change?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = rowStyle, color = if (isFall(it)) colors.neg else colors.pos, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
        SnapLayout.MONTH -> {
            val weekday = model.weekday.takeIf { it in 1..7 }?.let { DayOfWeek.of(it) }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                if (model.day > 0) Text(model.day.toString(), style = bigStyle(), color = colors.on, maxLines = 1)
                val detail = listOfNotNull(model.title.takeIf { it.isNotBlank() }, weekday?.getDisplayName(TextStyle.FULL, locale)).joinToString(" · ")
                Text(detail, style = smallStyle(), color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp, bottom = 5.dp))
            }
            val first = WeekFields.of(locale).firstDayOfWeek
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until 7) {
                    val day = first.plus(i.toLong())
                    val today = day == weekday
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).then(if (today) Modifier.background(accent) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.getDisplayName(TextStyle.NARROW, locale),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal),
                            color = if (today) Color(0xFF1B1C21) else colors.sub
                        )
                    }
                }
            }
        }
        SnapLayout.CLOCK -> {
            Text(rememberClockText(true), style = bigStyle(), color = colors.on, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
            model.labels.firstOrNull()?.let { Text(it, style = smallStyle(), color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** What an app card without data says: getting the latest, finish setup, app removed or work apps paused. */
@Composable
private fun AppWaiting(data: AppCardData, icon: ImageBitmap?, accent: Color) {
    val colors = rememberShapedColors()
    val state = if (data.state == AppCardState.READY || data.state == AppCardState.STALE) AppCardState.UPDATING else data.state
    val (title, sub) = when (state) {
        AppCardState.SETUP -> "Finish setup" to "Choose what ${data.appLabel} shows"
        AppCardState.REMOVED -> "App removed" to "Tap to remove this card"
        AppCardState.PAUSED -> "Work apps are paused" to "Tap to turn them on"
        else -> "Getting the latest" to "The card fills in as soon as the app sends it"
    }
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        val tint = if (state == AppCardState.REMOVED) colors.mut else accent
        when (state) {
            AppCardState.SETUP -> Icon(Icons.Outlined.Tune, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            AppCardState.REMOVED -> Icon(CardIcons.HideSource, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            AppCardState.PAUSED -> Icon(CardIcons.Work, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            else -> AppIcon(icon, accent, 28.dp)
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = colors.on, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = smallStyle(), color = colors.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The locale's number format with exactly [decimals] decimals. */
internal fun numberFormat(locale: Locale, decimals: Int): NumberFormat = NumberFormat.getNumberInstance(locale).apply {
    minimumFractionDigits = decimals.coerceIn(0, 6)
    maximumFractionDigits = decimals.coerceIn(0, 6)
}
