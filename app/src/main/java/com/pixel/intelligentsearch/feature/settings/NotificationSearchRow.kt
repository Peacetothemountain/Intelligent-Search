package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import com.pixel.intelligentsearch.di.appEntryPoint

/**
 * Search Sources' Notifications row: Search Your Notifications, off by default. Turning it on asks for notification
 * access through the app's shared rationale ([rememberNotificationAccessRequest]) and Android's Notification Access
 * screen; coming back with access granted switches it on. Revoked access switches it off, and off forgets everything
 * read.
 */
@Composable
internal fun NotificationSearchRow(showDivider: Boolean) {
    val context = LocalContext.current
    var searchNotifications by rememberSetting(SettingsManager.SEARCH_NOTIFICATIONS)
    val index = remember(context) { context.appEntryPoint.notificationIndex() }
    val turnOff = {
        searchNotifications = false
        NotificationAccess.stop(index)
    }
    val access = rememberNotificationAccessRequest { granted ->
        if (granted) {
            searchNotifications = true
            NotificationAccess.start(context)
        }
    }
    NotificationAccessDialog(access)
    val currentSearch by rememberUpdatedState(searchNotifications)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Access revoked in Android's settings: the switch follows, and what was read is forgotten.
            if (event == Lifecycle.Event.ON_RESUME && currentSearch && !NotificationAccess.isGranted(context)) turnOff()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsRowToggle(
        title = "Notifications",
        subtitle = "Search Your Notifications. Nothing Leaves the Phone and Nothing Is Stored. Messages and Gmail Show Only What Their Notifications Show.",
        icon = Icons.Outlined.Notifications,
        isChecked = searchNotifications,
        onCheckedChange = { on -> if (on) access.request() else turnOff() },
        showDivider = showDivider
    )
}
