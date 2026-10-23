package com.pixel.intelligentsearch.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.widget.MAX_FAVOURITE_APPS
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Widget Shortcuts tab's rows above the bar: the favourite apps row (chosen in a sheet of installed apps) and the
 * media row, which needs notification access: turning it on without it asks through the app's shared rationale
 * ([rememberNotificationAccessRequest], the same grant as Search Your Notifications and At a Glance's Media Controls), and it stays off
 * unless the user comes back with access granted. Revoked access switches it off (the widget never shows it without).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetExtrasSection(fields: WidgetDraftFields, ui: WidgetEditorState) {
    val context = LocalContext.current
    var favouriteApps by fields::favouriteApps
    var showMedia by fields::showMedia
    var showFavouritesSheet by ui::showFavouritesSheet
    val mediaAccess = rememberNotificationAccessRequest { granted -> if (granted) showMedia = true }
    NotificationAccessDialog(mediaAccess)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, fields) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && showMedia && !NotificationAccess.isGranted(context)) showMedia = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSectionHeader("Above the Bar")
    Text(
        "Shown When the Widget Is Resized to Two or More Rows.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
    )
    SettingsCard(lit = false) {
        SettingsRow(
            title = "Favorite Apps",
            subtitle = if (favouriteApps.isEmpty()) {
                "Pick Up to $MAX_FAVOURITE_APPS Apps to Open from a Row of Icons."
            } else {
                "${favouriteApps.size} of $MAX_FAVOURITE_APPS Apps, with Your Icon Pack."
            },
            icon = Icons.Outlined.GridView,
            onClick = { showFavouritesSheet = true },
            showDivider = true
        )
        SettingsRowToggle(
            title = "Media Controls",
            subtitle = "Show What's Playing with Play, Pause and Next. Uses Notification Access.",
            icon = Icons.Outlined.LibraryMusic,
            isChecked = showMedia,
            onCheckedChange = { on -> if (on) mediaAccess.request() else showMedia = false },
            showDivider = false
        )
    }

    if (showFavouritesSheet) {
        ModalBottomSheet(onDismissRequest = { showFavouritesSheet = false }) {
            FavouriteAppsPicker(
                chosen = favouriteApps,
                onToggle = { packageName, add ->
                    favouriteApps = when {
                        !add -> favouriteApps - packageName
                        favouriteApps.size >= MAX_FAVOURITE_APPS -> {
                            Toast.makeText(context, "Up to $MAX_FAVOURITE_APPS apps fit in the row.", Toast.LENGTH_SHORT).show()
                            favouriteApps
                        }
                        else -> favouriteApps + packageName
                    }
                }
            )
        }
    }
}

/** Installed apps as rows to switch in or out of the favourite apps row, the chosen ones first in their order. */
@Composable
private fun FavouriteAppsPicker(chosen: List<String>, onToggle: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AppPickerItem>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            runCatching {
                pm.queryIntentActivities(launcher, 0).asSequence()
                    .distinctBy { it.activityInfo.packageName }
                    .filter { it.activityInfo.packageName != context.packageName }
                    .map { AppPickerItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }.getOrDefault(emptyList())
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Favorite Apps",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val ordered = remember(list, chosen) {
                chosen.mapNotNull { pkg -> list.firstOrNull { it.packageName == pkg } } + list.filter { it.packageName !in chosen }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(ordered, key = { it.packageName }) { app ->
                    val position = chosen.indexOf(app.packageName)
                    val icon by produceState(peekNativeAppIcon(app.packageName), app.packageName) {
                        value = withContext(Dispatchers.IO) { getNativeAppIcon(context, app.packageName) }
                    }
                    SettingsRowToggle(
                        title = app.label,
                        subtitle = if (position >= 0) "Number ${position + 1} in the Row." else "Not in the Row.",
                        icon = icon ?: Icons.Outlined.Apps,
                        isChecked = position >= 0,
                        onCheckedChange = { onToggle(app.packageName, it) },
                        showDivider = true
                    )
                }
            }
        }
    }
}
