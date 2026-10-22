package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.SavedLink
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import com.pixel.intelligentsearch.core.ui.uniqueItems
import com.pixel.intelligentsearch.feature.settings.bouncyClickable

/** Marks the empty overlay's Pinned row, for tests. */
internal const val PINNED_ROW_TAG = "pinned_results_row"

/** The launch icon key of a pinned app in the Pinned row (the app row's own key belongs to its results). */
internal fun pinnedIconKey(app: AppItem) = "pinned_${LaunchIconKeys.app(app)}"

/**
 * Opens a result the way its own row does: an app in its profile (out of [from]'s icon), a contact's card (or the
 * dialler with Direct Call), a file in its app, a Settings page. For the Pinned row and trigger words.
 */
internal fun openResult(context: Context, s: OverlayScope, result: Any, from: LaunchSource) {
    s.ui.hasStartedTyping = false
    when (result) {
        is AppItem -> s.performAppLaunch(result, from)
        is ContactItem -> launchSafeIntent(context, ContactActions.openIntent(result, s.settingsState.contactDirectCall))
        is FileItem -> openFileResult(context, s.prefs, result)
        is AndroidSettingResult -> openAndroidSetting(context, result)
        // In the browser or the site's own app; under Tor, only in Tor Browser.
        is SavedLink -> s.openWebAddress(result.url)
    }
}

/** The menu target for a pinned [result]. */
private fun menuTargetFor(result: Any): ResultMenuTarget? = when (result) {
    is AppItem -> ResultMenuTarget.App(result)
    is ContactItem -> ResultMenuTarget.Contact(result)
    is FileItem -> ResultMenuTarget.File(result)
    is AndroidSettingResult -> ResultMenuTarget.Setting(result)
    is SavedLink -> ResultMenuTarget.Link(result)
    else -> null
}

/**
 * The empty overlay's Pinned row: what the user pinned, in pin order, laid out like the app row (an icon over its
 * name). Each opens with a tap and keeps its long-press menu, where Unpin takes it off.
 */
@Composable
internal fun OverlayPinnedRow(s: OverlayScope) {
    val context = LocalContext.current
    val pinned = s.uiState.pinned
    Column(modifier = Modifier.fillMaxWidth().testTag(PINNED_ROW_TAG)) {
        Text(
            text = "Pinned",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .semantics { heading() }
        )
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        val rowKey: (Any) -> String = { menuTargetFor(it)?.key ?: it.hashCode().toString() }
        // A pin the arrow keys highlight is scrolled into view along the row.
        val highlightedKey = s.ui.highlightedKey
        val animationsOn by com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled()
        LaunchedEffect(highlightedKey) {
            val result = pinned.firstOrNull { ResultListKeys.pinned(it) == highlightedKey } ?: return@LaunchedEffect
            val key = rowKey(result)
            rowState.scrollIntoView(key, isTowardEnd(rowState, pinned.map(rowKey), key), animationsOn)
        }
        LazyRow(state = rowState, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Start) {
            uniqueItems(pinned, key = rowKey, contentType = { it::class.simpleName }) { result ->
                val highlight = Modifier.keyboardHighlight(s, ResultListKeys.pinned(result), ResultHighlightTileShape)
                ResultMenuBox(s, menuTargetFor(result)) { openMenu ->
                    when (result) {
                        is AppItem -> {
                            val iconKey = pinnedIconKey(result)
                            AppGridItem(result, s.settingsState.activeIconPack, launchIcon = LaunchIconTarget(s.launchIcons, iconKey), onLongClick = openMenu, modifier = highlight) {
                                openResult(context, s, result, LaunchSource.tap(iconKey))
                            }
                        }
                        is ContactItem -> PinnedTile(result.name.ifBlank { result.phoneNumber }, openMenu, { openResult(context, s, result, LaunchSource.keyboard()) }, modifier = highlight) {
                            ContactAvatar(result, 48.dp) { TileSymbol(Icons.Outlined.Person) }
                        }
                        is FileItem -> PinnedTile(result.name, openMenu, { openResult(context, s, result, LaunchSource.keyboard()) }, modifier = highlight) {
                            if (s.settingsState.filesThumbnails) FileIconThumbnail(result.uri, result.mimeType, size = 48.dp)
                            else TileSymbol(Icons.AutoMirrored.Outlined.InsertDriveFile)
                        }
                        is AndroidSettingResult -> PinnedTile(result.title, openMenu, { openResult(context, s, result, LaunchSource.keyboard()) }, spokenLabel = result.spokenLabel, modifier = highlight) {
                            TileSymbol(androidSettingIcon(result.iconType))
                        }
                        is SavedLink -> PinnedTile(result.name, openMenu, { openResult(context, s, result, LaunchSource.keyboard()) }) {
                            SavedLinkIcon(result, rememberSavedLinkIcon(result), 48.dp)
                        }
                    }
                }
            }
        }
    }
}

/** A round 48dp symbol in the row's quiet container colour, as the app row's Search Settings tile draws it. */
@Composable
private fun TileSymbol(icon: ImageVector) {
    Box(
        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}

/** A pinned contact, file or Settings page, laid out as [AppGridItem] lays out an app. */
@Composable
private fun PinnedTile(
    label: String,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spokenLabel: String = label,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .semantics(mergeDescendants = true) { contentDescription = spokenLabel }
            .moreOptions(onLongClick)
            .bouncyClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
