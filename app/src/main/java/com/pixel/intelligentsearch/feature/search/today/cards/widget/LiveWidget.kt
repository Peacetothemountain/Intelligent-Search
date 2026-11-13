package com.pixel.intelligentsearch.feature.search.today.cards.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pixel.intelligentsearch.feature.search.today.CARD_ALPHA
import com.pixel.intelligentsearch.feature.search.today.cards.shape.CoveredBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * An app card as its full card: the app's own widget, live, as a launcher shows it. It updates as the app updates it
 * and its buttons work (a music widget plays and skips). Told its size as the user resizes it, so widgets that change
 * their layout with their size do.
 *
 * NG Designs
 */

/** A full card's corners, as the page's other cards have. */
private val LiveShape = RoundedCornerShape(24.dp)

/**
 * The live widget [widgetId], filling [modifier]'s bounds as it draws itself on a home screen, cut to the card's
 * rounded corners. A [private] card is covered until [revealed]. [onGone] draws instead
 * when the widget no longer exists (its app was removed).
 */
@Composable
internal fun LiveWidget(
    widgetId: Int,
    modifier: Modifier = Modifier,
    private: Boolean = false,
    revealed: Boolean = false,
    onGone: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val cards = remember(context) { androidWidgetCards(context) }
    val info by produceState<android.appwidget.AppWidgetProviderInfo?>(initialValue = UNLOOKED, widgetId) {
        value = withContext(Dispatchers.IO) { runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) }.getOrNull() }
    }
    val provider = info
    if (provider === UNLOOKED) {
        Box(modifier.clip(LiveShape).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = CARD_ALPHA)))
        return
    }
    if (provider == null) {
        onGone(modifier)
        return
    }
    DisposableEffect(cards) {
        cards.startLive()
        onDispose { cards.stopLive() }
    }
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var view by remember { mutableStateOf<AppWidgetHostView?>(null) }
    val told = remember { arrayOf(IntSize.Zero) }
    // Told its size in dp, as a launcher tells it: at once the first time, and after that only once a resize has
    // settled. Each telling is a call to Android and wakes the widget's app to draw again, far too much per finger move.
    LaunchedEffect(size, view) {
        val host = view ?: return@LaunchedEffect
        if (size == told[0] || size.width <= 0 || size.height <= 0) return@LaunchedEffect
        if (told[0] != IntSize.Zero) delay(SIZE_SETTLED_MS)
        told[0] = size
        runCatching { host.updateAppWidgetSize(Bundle(), listOf(SizeF(size.width / density.density, size.height / density.density))) }
    }
    // No backing of the page's own: a widget draws its own background and margins, as it does on the home screen.
    CoveredBox(private && !revealed, LiveShape, modifier.clip(LiveShape)) {
        // Another app's widget, laid out the way the phone reads, as on the home screen.
        com.pixel.intelligentsearch.core.theme.PhoneLayoutDirection {
            AndroidView(
                factory = { ctx ->
                    cards.host.createView(ctx, widgetId, provider).apply {
                        setPadding(0, 0, 0, 0)
                        view = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size = it }
            )
        }
    }
}

/** How long a live widget's size holds still before the widget is told it. */
private const val SIZE_SETTLED_MS = 250L

/** The info a widget has before it is looked up: a marker, never a real widget. */
private val UNLOOKED = android.appwidget.AppWidgetProviderInfo()
