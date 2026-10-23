package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting

/**
 * Search Behavior's Keyboard card: whether the assistant gesture opens search listening instead of typing, and whether
 * the keyboard comes up when search opens. Enter opens the top result (OverlayEnterKey).
 */
@Composable
internal fun KeyboardSettingsCard() {
    SettingsSectionHeader("Keyboard")
    SettingsCard {
        // Voice or keyboard is the first choice search opens with, so it leads the card.
        var assistantVoice by rememberSetting(SettingsManager.ASSISTANT_STARTS_WITH_VOICE)
        SettingsRowToggle(
            title = "Start Assistant with Voice",
            subtitle = "The Assistant Gesture Opens Search with the Microphone On. Works When Intelligent Search Is Your Default Assistant.",
            icon = Icons.Outlined.Mic,
            isChecked = assistantVoice,
            onCheckedChange = { assistantVoice = it },
            showDivider = true
        )
        var showKeyboard by rememberSetting(SettingsManager.SHOW_KEYBOARD_ON_OPEN)
        SettingsRowToggle(
            title = "Show Keyboard When Search Opens",
            subtitle = "Open Search with the Keyboard Up. When Off, Tap the Search Bar to Show It.",
            icon = Icons.Outlined.Keyboard,
            isChecked = showKeyboard,
            onCheckedChange = { showKeyboard = it },
            showDivider = false
        )
    }
}
