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
import com.pixel.intelligentsearch.feature.search.rememberCalendarAllowed
import com.pixel.intelligentsearch.feature.search.rememberGlanceNow
import com.pixel.intelligentsearch.feature.search.today.TodayIcon
import com.pixel.intelligentsearch.feature.search.today.hourOfDay

/**
 * The last rows of Appearance's Search Overlay card: the switch for the Today button in the search bar, then, while
 * it is on, one switch per part of the Today page. Up Next reads the calendar, so it shows as on only while the
 * calendar is allowed, and turning it on without that asks for it the app's usual way.
 */
@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.TodaySettingsRows() {
    var button by rememberSetting(SettingsManager.TODAY_BUTTON)
    val scheme = MaterialTheme.colorScheme
    val now = rememberGlanceNow(60_000L)
    SettingsRowToggle(
        title = "Today Button",
        subtitle = "Adds a Button to the Search Bar That Opens Your Day.",
        customIcon = {
            TodayIcon(hour = hourOfDay(now), color = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        },
        isChecked = button,
        onCheckedChange = { button = it },
        showDivider = button
    )
    AttachedRows(visible = button) {
        Column {
            var upNext by rememberSetting(SettingsManager.TODAY_UP_NEXT)
            val calendarAllowed = rememberCalendarAllowed()
            val calendarRequest = rememberCalendarRequest { upNext = true }
            SettingsRowToggle(
                title = "Up Next and Rest of Your Day",
                subtitle = "Your Next Event, Directions to It, and What Follows. Uses Calendar Access.",
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
            PartRow(SettingsManager.TODAY_WEATHER, "Weather", "Weather Now, the Next Hours and Tomorrow. Follows Web Suggestions.", Symbols.Outlined.Today)
            PartRow(SettingsManager.TODAY_CATCH_UP, "Catch Up", "Missed Calls, Messages and Emails. Uses Notification Access.", Symbols.Outlined.Notifications)
            PartRow(SettingsManager.TODAY_CONTROLS, "Quick Controls", "Do Not Disturb, Flashlight, Hotspot and Battery Saver.", Symbols.Outlined.Tune)
            PartRow(SettingsManager.TODAY_PHONE, "Your Phone Today", "Battery and Screen Time.", Symbols.Outlined.Speed)
            PartRow(SettingsManager.TODAY_LEFT_OFF, "Pick Up Where You Left Off", "Your Last Searches, One Tap to Search Again.", Symbols.Outlined.History)
            PartRow(SettingsManager.TODAY_EXTRAS, "Word of the Day and Countdown", "A New Word Each Day, and Days Until the Next Holiday.", Symbols.Outlined.Spellcheck, last = true)
        }
    }
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
