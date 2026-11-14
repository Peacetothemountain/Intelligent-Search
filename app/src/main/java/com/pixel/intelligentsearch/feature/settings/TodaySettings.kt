package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.BooleanSetting
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.ui.symbols.Symbols
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import com.pixel.intelligentsearch.feature.search.rememberCalendarAllowed
import com.pixel.intelligentsearch.feature.search.rememberNotificationAccessGranted
import com.pixel.intelligentsearch.feature.search.today.TodayIcon

/**
 * Appearance's Discover Overlay card, under Search Overlay: the switch for the button in the search bar that opens the
 * Discover Overlay (the Today page), then, while it is on, one switch per part of the page. Up Next reads the calendar, so it shows as on only while the
 * calendar is allowed, and turning it on without that asks for it the app's usual way.
 */
@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.TodaySettingsRows() {
    val button by rememberSetting(SettingsManager.TODAY_BUTTON)
    val context = androidx.compose.ui.platform.LocalContext.current
    DiscoverOverlaySwitchRow(showDivider = button)
    AttachedRows(visible = button) {
        Column {
            var upNext by rememberSetting(SettingsManager.TODAY_UP_NEXT)
            val calendarAllowed = rememberCalendarAllowed()
            val calendarRequest = rememberCalendarRequest { upNext = true }
            SettingsRowToggle(
                title = "Up Next and Rest of Your Day",
                subtitle = "Your Next Event, Directions to It and What Follows. Uses Calendar Access.",
                icon = Symbols.Outlined.Event,
                isChecked = upNext && calendarAllowed,
                onCheckedChange = { on ->
                    when {
                        !on -> upNext = false
                        calendarAllowed -> upNext = true
                        else -> calendarRequest.request()
                    }
                }
            )
            var leaveBy by rememberSetting(SettingsManager.TODAY_LEAVE_BY)
            val locationAllowed = rememberPermissionState { com.pixel.intelligentsearch.core.answers.AndroidDeviceLocation(it).permitted() }
            val locationRequest = rememberTodayLocationRequest { leaveBy = true }
            SettingsRowToggle(
                title = "Leave-By Time",
                subtitle = "When to Leave for Your Next Event and About How Long It Takes. Uses Approximate Location and Web Search.",
                icon = Symbols.Outlined.Schedule,
                isChecked = leaveBy && locationAllowed,
                onCheckedChange = { on ->
                    when {
                        !on -> leaveBy = false
                        locationAllowed -> leaveBy = true
                        else -> locationRequest.request()
                    }
                }
            )
            PartRow(SettingsManager.TODAY_WEATHER, "Weather", "Weather Now, the Next Hours and Tomorrow. Needs Web Suggestions On.", Symbols.Filled.WbSunny)
            var catchUp by rememberSetting(SettingsManager.TODAY_CATCH_UP)
            val notificationsAllowed = rememberNotificationAccessGranted()
            val notificationsRequest = rememberNotificationAccessRequest { granted ->
                if (granted) {
                    catchUp = true
                    NotificationAccess.start(context)
                }
            }
            NotificationAccessDialog(notificationsRequest)
            SettingsRowToggle(
                title = "Catch Up",
                subtitle = "Missed Calls, Messages, Emails and Packages on Their Way. Uses Notification Access.",
                icon = Symbols.Outlined.Notifications,
                isChecked = catchUp && notificationsAllowed,
                onCheckedChange = { on ->
                    when {
                        !on -> catchUp = false
                        notificationsAllowed -> catchUp = true
                        else -> notificationsRequest.request()
                    }
                }
            )
            PartRow(SettingsManager.TODAY_CONTROLS, "Quick Controls", "The Switches You Choose, from Do Not Disturb and Internet to Auto-Rotate.", Symbols.Outlined.Tune)
            PartRow(SettingsManager.TODAY_PHONE, "Your Phone Today", "Battery and Screen Time.", Symbols.Outlined.Speed)
            PartRow(SettingsManager.TODAY_LEFT_OFF, "Pick Up Where You Left Off", "Your Last Searches, One Tap to Search Again.", Symbols.Outlined.History)
            PartRow(SettingsManager.TODAY_EXTRAS, "Word of the Day and Countdown", "A New Word Each Day and the Days Until the Next Holiday.", Symbols.Outlined.Spellcheck, last = true)
        }
    }
}

/**
 * The switch for the Discover button in the search bar. One row for both places it shows, Appearance and the
 * tutorial's Discover step, so the tutorial teaches the setting by its own name.
 */
@Composable
internal fun DiscoverOverlaySwitchRow(showDivider: Boolean) {
    var button by rememberSetting(SettingsManager.TODAY_BUTTON)
    val scheme = MaterialTheme.colorScheme
    SettingsRowToggle(
        title = "Discover Overlay",
        subtitle = "Adds a Button to the Search Bar That Opens Your Day.",
        customIcon = {
            // A still afternoon sun: the row names the button, it needn't tell the time.
            TodayIcon(hour = 14.5f, color = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        },
        isChecked = button,
        onCheckedChange = { button = it },
        showDivider = showDivider
    )
}

@Composable
private fun PartRow(key: BooleanSetting, title: String, subtitle: String, icon: Any, last: Boolean = false) {
    var on by rememberSetting(key)
    SettingsRowToggle(
        title = title,
        subtitle = subtitle,
        icon = icon,
        isChecked = on,
        onCheckedChange = { on = it },
        showDivider = !last
    )
}

/** Whether [allowed] says yes, read again each time the page resumes (the user can change it in Android's settings). */
@Composable
internal fun rememberPermissionState(allowed: (android.content.Context) -> Boolean): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by androidx.compose.runtime.remember(context) { androidx.compose.runtime.mutableStateOf(allowed(context)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = allowed(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return granted
}
