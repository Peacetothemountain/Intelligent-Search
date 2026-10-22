package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
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

/**
 * Search Behavior's At a Glance card: the section's own switch, then one per item in the order they show. Timers
 * come from notifications, so they need Search Your Notifications; Now Playing needs notification access and
 * Birthdays contacts access, and each says so.
 */
@Composable
internal fun AtAGlanceSettingsCard() {
    SettingsSectionHeader("At a Glance")
    SettingsCard {
        var enabled by rememberSetting(SettingsManager.GLANCE_ENABLED)
        SettingsRowToggle(
            title = "At a Glance",
            subtitle = "Show Timely Cards Above the Empty Search.",
            icon = Icons.Outlined.Today,
            isChecked = enabled,
            onCheckedChange = { enabled = it },
            showDivider = enabled
        )
        AnimatedVisibility(visible = enabled) {
            Column {
                var timers by rememberSetting(SettingsManager.GLANCE_TIMERS)
                SettingsRowToggle(
                    title = "Timers & Stopwatch",
                    subtitle = "Show Running Timers and Stopwatches. Needs Search Your Notifications.",
                    icon = Icons.Outlined.Timer,
                    isChecked = timers,
                    onCheckedChange = { timers = it }
                )
                var alarm by rememberSetting(SettingsManager.GLANCE_ALARM)
                SettingsRowToggle(
                    title = "Next Alarm",
                    subtitle = "Show Your Next Alarm Within 12 Hours.",
                    icon = Icons.Outlined.Alarm,
                    isChecked = alarm,
                    onCheckedChange = { alarm = it }
                )
                var media by rememberSetting(SettingsManager.GLANCE_MEDIA)
                // The same Notification Access grant as Search Your Notifications and the widget's Media Controls.
                val mediaAccess = rememberNotificationAccessRequest { granted -> if (granted) media = true }
                NotificationAccessDialog(mediaAccess)
                SettingsRowToggle(
                    title = "Now Playing",
                    subtitle = "Play, Pause and Skip What's Playing. Needs Notification Access.",
                    icon = Icons.Outlined.MusicNote,
                    isChecked = media,
                    onCheckedChange = { on -> if (on) mediaAccess.request() else media = false }
                )
                var birthdays by rememberSetting(SettingsManager.GLANCE_BIRTHDAYS)
                SettingsRowToggle(
                    title = "Birthdays",
                    subtitle = "Show Contacts' Birthdays This Week. Needs Contacts Access.",
                    icon = Icons.Outlined.Cake,
                    isChecked = birthdays,
                    onCheckedChange = { birthdays = it }
                )
                var battery by rememberSetting(SettingsManager.GLANCE_BATTERY)
                SettingsRowToggle(
                    title = "Battery",
                    subtitle = "Show the Battery When Low or Charging.",
                    icon = Icons.Outlined.BatteryChargingFull,
                    isChecked = battery,
                    onCheckedChange = { battery = it },
                    showDivider = false
                )
            }
        }
    }
}
