package com.pixel.intelligentsearch.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.uniqueItems

/**
 * Whether the results list's rows are closing up after the user took one away (swiped it off, removed it, pinned or
 * unpinned it): see [OverlayUiState.rowsClosingUp]. The results list provides it to its rows.
 */
internal val LocalResultRowsClosingUp = compositionLocalOf { false }

/** How long the rows keep gliding after a row is taken away: time for the glide to come to rest. */
internal const val ROWS_CLOSE_UP_MILLIS = 700L

/**
 * A row of the results list, with the way every row moves. While results come and go as the user types, every row
 * is at once in its new place: a row that glided would be crossed by the rows arriving where it was. For a moment
 * after the user takes a row away ([LocalResultRowsClosingUp]), that row fades out and the others glide to close
 * the gap. Every row of the list is in one of these ([resultRow], [resultRows]), dividers and headers too, so the
 * rows always move together.
 */
@Composable
internal fun LazyItemScope.ResultRowBox(content: @Composable () -> Unit) {
    val closingUp = LocalResultRowsClosingUp.current
    Box(
        Modifier.animateItem(
            fadeInSpec = null,
            placementSpec = if (closingUp) ExpressiveMotionTokens.Spatial.ExpressiveDefault() else null,
            fadeOutSpec = if (closingUp) ExpressiveMotionTokens.Effects.FastSpatial() else null
        )
    ) { content() }
}

/** One row of the results list, moving as every row does ([ResultRowBox]). */
internal fun LazyListScope.resultRow(key: Any, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
    item(key = key, contentType = contentType) {
        val row = this
        ResultRowBox { row.content() }
    }
}

/** Rows of the results list that can never repeat a key ([uniqueItems]), each moving as every row does. */
internal inline fun <T> LazyListScope.resultRows(
    items: List<T>,
    noinline key: (item: T) -> Any,
    noinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit
) {
    uniqueItems(items, key, contentType) { item ->
        val row = this
        ResultRowBox { row.itemContent(item) }
    }
}
