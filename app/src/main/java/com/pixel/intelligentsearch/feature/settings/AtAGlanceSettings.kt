package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting

/**
 * Search Behavior's At a Glance card: the section's switch, and Show Clock under it. The items have no switches of
 * their own: each shows when it has something to show and what it reads from is already on (timers with Search Your
 * Notifications, media with notification access, birthdays with contact search; the next alarm always).
 */
@Composable
internal fun AtAGlanceSettingsCard() {
    SettingsSectionHeader("At a Glance")
    SettingsCard {
        var enabled by rememberSetting(SettingsManager.GLANCE_ENABLED)
        SettingsRowToggle(
            title = "At a Glance",
            subtitle = "Show Timers, Your Next Alarm, What's Playing and Birthdays Above the Empty Search.",
            icon = Icons.Outlined.Today,
            isChecked = enabled,
            onCheckedChange = { enabled = it },
            showDivider = enabled
        )
        AnimatedVisibility(visible = enabled) {
            var showClock by rememberSetting(SettingsManager.GLANCE_CLOCK)
            SettingsRowToggle(
                title = "Show Clock",
                subtitle = "Show a Clock in the Corner of At a Glance.",
                icon = Icons.Outlined.Schedule,
                isChecked = showClock,
                onCheckedChange = { showClock = it },
                showDivider = false
            )
        }
    }
}
