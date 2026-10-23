package com.pixel.intelligentsearch.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pixel.intelligentsearch.core.data.SavedLink
import com.pixel.intelligentsearch.di.appEntryPoint

/**
 * [link]'s saved site icon: at once when it is in memory, otherwise read off the main thread; null while it loads and
 * when it has none. A fetched or deleted icon shows everywhere straight away.
 */
@Composable
fun rememberSavedLinkIcon(link: SavedLink): ImageBitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icons = remember(context) { runCatching { context.appEntryPoint.savedLinkIcons() }.getOrNull() } ?: return null
    val changes by icons.changes.collectAsState()
    return androidx.compose.runtime.produceState(icons.peek(link.url)?.asImageBitmap(), link.url, changes) {
        value = icons.load(link.url)?.asImageBitmap()
    }.value
}
