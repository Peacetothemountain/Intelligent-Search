package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.util.Durations
import androidx.compose.runtime.getValue
import com.pixel.intelligentsearch.core.ui.rememberAppIcon
import com.pixel.intelligentsearch.core.ui.AppIconImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import com.pixel.intelligentsearch.core.glance.NotificationIndex
import com.pixel.intelligentsearch.core.system.openPendingOrApp
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.uniqueItems
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The notifications matching [query] while Search Your Notifications is on, searched off the main thread; each
 * keystroke cancels the search for the one before. Opening the search first forgets everything read if notification
 * access has been revoked since.
 */
@Composable
internal fun rememberNotificationHits(query: String, hiddenApps: Set<String>): List<IndexedNotification> {
    val enabled by rememberSetting(SettingsManager.SEARCH_NOTIFICATIONS)
    val context = LocalContext.current
    val index = remember(context) { context.appEntryPoint.notificationIndex() }
    LaunchedEffect(index, enabled) {
        withContext(Dispatchers.Default) { NotificationAccess.syncIndex(context.applicationContext, index) }
    }
    val entries by index.entries.collectAsState()
    return produceState(emptyList(), query, entries, enabled, hiddenApps) {
        value = if (!enabled || query.isBlank()) emptyList() else withContext(Dispatchers.Default) {
            NotificationIndex.search(entries.filterNot { it.packageName in hiddenApps }, query)
        }
    }.value
}

/** The Notifications section of a search's results. [onOpened] runs as a result opens. */
internal fun LazyListScope.notificationResultItems(hits: List<IndexedNotification>, iconPack: String?, onOpened: () -> Unit) {
    if (hits.isEmpty()) return
    item(key = "notifications_divider", contentType = "divider") {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.8.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item(key = "notifications_header", contentType = "recent_label") { NotificationsHeader() }
    uniqueItems(hits, key = { "notification_${it.key}" }, contentType = { "notification" }) { entry ->
        NotificationResultRow(
            entry,
            now = System.currentTimeMillis(),
            iconPack = iconPack,
            onOpened = onOpened,
            modifier = Modifier.animateItem(
                placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
            )
        )
    }
}

@Composable
internal fun NotificationsHeader() {
    OverlaySectionLabel("Notifications")
}

/**
 * One matching notification as the file and contact rows are laid out: the app's icon, the title, then one smaller
 * line (at most two at large text sizes) with which app, how long ago and the text. A tap does what tapping the
 * notification does, or opens the app.
 */
@Composable
internal fun NotificationResultRow(entry: IndexedNotification, now: Long, iconPack: String?, onOpened: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .expressiveRowClickable {
                onOpened()
                openPendingOrApp(context, entry.contentIntent, entry.packageName)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotificationAppIcon(entry.packageName, iconPack)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title.ifBlank { entry.appLabel },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = notificationDetail(entry, now),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A notification row's second line: "Chat · 5 min ago · Dinner at the café at 8?", leaving out what is blank. */
internal fun notificationDetail(entry: IndexedNotification, now: Long): String =
    listOf(entry.appLabel, Durations.ago(now - entry.postedAt), entry.text.trim())
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/** The app's themed icon in a 40 dp circle, as the quick apps show it; a bell until it has loaded. */
@Composable
private fun NotificationAppIcon(packageName: String, iconPack: String?) {
    Box(
        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AppIconImage(rememberAppIcon(packageName, iconPack), Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


