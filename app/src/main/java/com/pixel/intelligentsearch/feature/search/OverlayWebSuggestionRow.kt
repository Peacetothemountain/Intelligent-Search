package com.pixel.intelligentsearch.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import com.pixel.intelligentsearch.feature.settings.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.data.WebSuggestion
import com.pixel.intelligentsearch.core.data.highlightFor
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.feature.settings.focusRing
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable

/** The size of a suggestion row's leading picture, and of the slot every row's icon sits in, so all text lines up. */
internal val SUGGESTION_LEADING_SIZE = 40.dp

/**
 * The text a suggestion row shows, with what Google shows bold: an entity's own name ("Barack Obama" for "obama") as
 * Google lists it, else the suggestion with the part the user didn't type in bold.
 */
internal fun suggestionLabel(suggestion: WebSuggestion, typed: String): AnnotatedString {
    val title = suggestion.title
    if (title != null && !title.equals(suggestion.query, ignoreCase = true)) {
        return AnnotatedString(title)
    }
    val text = title ?: suggestion.query
    val bold = if (title == null) suggestion.highlightFor(typed) else WebSuggestion(text).highlightFor(typed)
    return buildAnnotatedString {
        append(text)
        for (range in bold) {
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (end > start) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
        }
    }
}

/**
 * One web suggestion, laid out as Google lists them: a clock for a past search, the entity's picture in a matte
 * circle (when [showThumbnail] and Google sent one) or the search icon, the suggestion with the completion bold and
 * an entity's description under it, then the arrow that fills the suggestion into the search box.
 */
@Composable
internal fun OverlayWebSuggestionRow(
    suggestion: WebSuggestion,
    typed: String,
    isRecent: Boolean,
    showThumbnail: Boolean,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit,
    onRemove: () -> Unit,
    onFill: () -> Unit,
    /** A past search's long-press menu (Remove from History); suggestions from the engine have none. */
    onLongClick: (() -> Unit)? = null,
    /** Whether the keyboard's highlight is on this row (read while drawing); it draws the focus ring then. */
    highlighted: () -> Boolean = { false }
) {
    val label = remember(suggestion, typed) { suggestionLabel(suggestion, typed) }
    val subtitle = suggestion.subtitle?.takeIf { it.isNotBlank() && !isRecent }
    val imageUrl = suggestion.imageUrl?.takeIf { showThumbnail && !isRecent }
    val pill = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, pill)
            .clip(pill)
            .focusRing(pill, highlighted)
            .moreOptions(onLongClick)
            .expressiveRowClickable(onLongClick = onLongClick, onClick = onSearch)
            // The leading slot sits as far in from the start as from the top and bottom of a two-line row (6dp plus
            // the 48dp buttons' 4dp around its 40dp), so a picture follows the pill's rounded end. The end's 4dp keeps
            // the arrow where it sat with 40dp buttons.
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = @Composable {
            Box(modifier = Modifier.size(SUGGESTION_LEADING_SIZE), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isRecent) Icons.Default.History else Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (imageUrl != null) ResultThumbnail(ThumbnailSource.Web(imageUrl), SUGGESTION_LEADING_SIZE, fallback = icon) else icon()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontFamily = GoogleSansFlex,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = GoogleSansFlex,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isRecent) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    // Named for its row: TalkBack reaches the button on its own, where "Remove" alone says nothing.
                    contentDescription = "Remove ${suggestion.query} from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        IconButton(onClick = onFill) {
            Icon(
                imageVector = Icons.Default.NorthWest,
                contentDescription = "Edit search ${suggestion.query}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
