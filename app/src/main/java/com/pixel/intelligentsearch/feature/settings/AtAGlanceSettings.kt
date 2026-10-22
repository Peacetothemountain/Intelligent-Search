package com.pixel.intelligentsearch.feature.settings

import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import com.pixel.intelligentsearch.di.appEntryPoint

/** What the notification access dialog says before Android's own Notification Access screen opens. */
internal const val NOTIFICATION_ACCESS_RATIONALE =
    "Intelligent Search can read the notifications on your phone so you can search them and see running timers " +
        "above the search. It sees only what each notification shows, never your messages or emails " +
        "themselves. Nothing is stored: notifications are held in memory only while they are showing, and nothing " +
        "leaves your phone. On the next screen, allow notification access for Intelligent Search."

/**
 * Search Sources' Notifications row: Search Your Notifications, off by default. Turning it on asks for notification
 * access with the app's rationale first and sends the user to Android's Notification Access screen; coming back
 * with access granted switches it on. Revoked access switches it off, and off forgets everything read.
 */
@Composable
internal fun NotificationSearchRow(showDivider: Boolean) {
    val context = LocalContext.current
    var searchNotifications by rememberSetting(SettingsManager.SEARCH_NOTIFICATIONS)
    var showRationale by remember { mutableStateOf(false) }
    // Survives the trip to Android's settings even if this page is recreated meanwhile.
    var awaitingGrant by rememberSaveable { mutableStateOf(false) }
    val index = remember(context) { context.appEntryPoint.notificationIndex() }
    val turnOff = {
        searchNotifications = false
        NotificationAccess.stop(index)
    }
    val currentSearch by rememberUpdatedState(searchNotifications)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val granted = NotificationAccess.isGranted(context)
            if (awaitingGrant) {
                awaitingGrant = false
                if (granted) {
                    searchNotifications = true
                    NotificationAccess.start(context)
                }
            } else if (currentSearch && !granted) {
                // Access revoked in Android's settings: the switch follows, and what was read is forgotten.
                turnOff()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow Notification Access?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            text = { Text(NOTIFICATION_ACCESS_RATIONALE, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    awaitingGrant = true
                    if (!NotificationAccess.openSettings(context)) {
                        awaitingGrant = false
                        Toast.makeText(context, "Notification access settings aren't available on this phone.", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("Open Settings", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("Not Now", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    SettingsRowToggle(
        title = "Notifications",
        subtitle = "Search Your Notifications. Nothing Leaves the Phone and Nothing Is Stored. Messages and Gmail Show Only What Their Notifications Show.",
        icon = Icons.Outlined.Notifications,
        isChecked = searchNotifications,
        onCheckedChange = { on ->
            when {
                !on -> turnOff()
                NotificationAccess.isGranted(context) -> {
                    searchNotifications = true
                    NotificationAccess.start(context)
                }
                else -> showRationale = true
            }
        },
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
                SettingsRowToggle(
                    title = "Now Playing",
                    subtitle = "Play, Pause and Skip What's Playing. Needs Notification Access.",
                    icon = Icons.Outlined.MusicNote,
                    isChecked = media,
                    onCheckedChange = { media = it }
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
