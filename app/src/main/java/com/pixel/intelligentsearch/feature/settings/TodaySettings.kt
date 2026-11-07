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
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.theme.pageContainer
import com.pixel.intelligentsearch.core.ui.symbols.Symbols
import com.pixel.intelligentsearch.feature.search.rememberCalendarAllowed
import com.pixel.intelligentsearch.feature.search.rememberGlanceNow
import com.pixel.intelligentsearch.feature.search.today.TodayIcon
import com.pixel.intelligentsearch.feature.search.today.hourOfDay

/**
 * Search Behavior's Today card: the switch for the button that opens Today from the search's top start corner, then
 * one switch per part of the page. Up Next reads the calendar, so it shows as on only while the calendar is allowed,
 * and turning it on without that asks for it the app's usual way.
 */
@Composable
internal fun TodaySettingsCard() {
    SettingsSectionHeader("Today")
    SettingsCard {
        var button by rememberSetting(SettingsManager.TODAY_BUTTON)
        val scheme = MaterialTheme.colorScheme
        val now = rememberGlanceNow(60_000L)
        SettingsRowToggle(
            title = "Today Button",
            subtitle = "Adds a Button to the Top Corner of Search That Opens Your Day.",
            customIcon = {
                TodayIcon(
                    hour = hourOfDay(now),
                    color = scheme.onSurfaceVariant,
                    background = scheme.pageContainer,
                    modifier = Modifier.size(24.dp)
                )
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
                    },
                    showDivider = false
                )
            }
        }
    }
}
