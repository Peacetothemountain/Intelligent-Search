package com.pixel.intelligentsearch.core.ui

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable

/**
 * Keyed items for a LazyColumn or LazyRow that can never repeat a key. Compose throws
 * IllegalArgumentException ("Key was already used") when two items share one, which crashed the search
 * overlay in production; this keeps the first item for each key, so a repeat in the data drops a duplicate
 * row instead of the whole list.
 */
inline fun <T> LazyListScope.uniqueItems(
    items: List<T>,
    noinline key: (item: T) -> Any,
    noinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit
) {
    val unique = items.distinctBy(key)
    items(
        count = unique.size,
        key = { index -> key(unique[index]) },
        contentType = { index -> contentType(unique[index]) }
    ) { index -> itemContent(unique[index]) }
}
