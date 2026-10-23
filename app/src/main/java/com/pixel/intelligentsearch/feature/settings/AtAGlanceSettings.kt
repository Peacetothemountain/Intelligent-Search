package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import com.pixel.intelligentsearch.di.appEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Whether each At a Glance item can show, given its own switch and what it reads from: timers need Search Your
 * Notifications and Notification Access, Media Controls Notification Access, Birthdays contact search and the contacts.
 * A switch shows as on only when its item can actually show.
 */
internal data class GlancePrerequisites(
    val notificationAccess: Boolean,
    val searchNotifications: Boolean,
    val contactsAllowed: Boolean,
    val searchContacts: Boolean
) {
    val timers: Boolean get() = searchNotifications && notificationAccess
    val media: Boolean get() = notificationAccess
    val birthdays: Boolean get() = contactsAllowed && searchContacts
}

/**
 * Search Behavior's At a Glance card: the section's own switch, then one per item in the order they show. A switch
 * shows as on only when its item can show ([GlancePrerequisites]); turning one on without what it needs asks for it
 * the app's usual way (Notification Access, the contacts) and switches on what that needs too.
 */
@Composable
internal fun AtAGlanceSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchNotifications by rememberSetting(SettingsManager.SEARCH_NOTIFICATIONS)
    var searchContacts by rememberSetting(SettingsManager.SEARCH_CONTACTS)
    // Granted or revoked in Android's settings while away: read again on the way back.
    var notificationAccess by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    var contactsPermission by remember { mutableStateOf(contactsAllowed(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccess = NotificationAccess.isGranted(context)
                contactsPermission = contactsAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val ready = GlancePrerequisites(notificationAccess, searchNotifications, contactsPermission, searchContacts)

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
                // Timers come from notifications: the one Notification Access grant, then Search Your Notifications.
                val timersAccess = rememberNotificationAccessRequest { granted ->
                    notificationAccess = NotificationAccess.isGranted(context)
                    if (granted) {
                        searchNotifications = true
                        timers = true
                        NotificationAccess.start(context)
                    }
                }
                NotificationAccessDialog(timersAccess)
                SettingsRowToggle(
                    title = "Timers & Stopwatch",
                    subtitle = "Show Running Timers and Stopwatches. Uses Search Your Notifications.",
                    icon = Icons.Outlined.Timer,
                    isChecked = timers && ready.timers,
                    onCheckedChange = { on ->
                        when {
                            !on -> timers = false
                            ready.timers -> timers = true
                            else -> timersAccess.request()
                        }
                    }
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
                val mediaAccess = rememberNotificationAccessRequest { granted ->
                    notificationAccess = NotificationAccess.isGranted(context)
                    if (granted) media = true
                }
                NotificationAccessDialog(mediaAccess)
                SettingsRowToggle(
                    title = "Media Controls",
                    subtitle = "Play, Pause and Skip What's Playing. Uses Notification Access.",
                    icon = Icons.Outlined.LibraryMusic,
                    isChecked = media && ready.media,
                    onCheckedChange = { on -> if (on) mediaAccess.request() else media = false }
                )
                var birthdays by rememberSetting(SettingsManager.GLANCE_BIRTHDAYS)
                val contactsRequest = rememberContactsRequest { granted ->
                    contactsPermission = contactsAllowed(context)
                    if (granted) {
                        searchContacts = true
                        birthdays = true
                        val index = context.appEntryPoint.corpusIndexManager()
                        scope.launch(Dispatchers.IO) { index.reindexContacts() }
                    }
                }
                SettingsRowToggle(
                    title = "Birthdays",
                    subtitle = "Show Contacts' Birthdays This Week. Uses Contact Search.",
                    icon = Icons.Outlined.Cake,
                    isChecked = birthdays && ready.birthdays,
                    onCheckedChange = { on -> if (on) contactsRequest.request() else birthdays = false }
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
