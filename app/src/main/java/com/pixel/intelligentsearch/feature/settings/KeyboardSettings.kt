package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.EnterKeyAction
import com.pixel.intelligentsearch.core.data.settings.rememberSetting

/** The test tag of the Enter Key Action selector. */
internal const val ENTER_KEY_ACTION_TAG = "enter_key_action_selector"

/**
 * Search Behavior's Keyboard card: whether the assistant gesture opens search listening instead of typing, whether the
 * keyboard comes up when search opens, and what Enter does when no result is highlighted (Open Top Result or Search
 * the Web).
 */
@Composable
internal fun KeyboardSettingsCard() {
    SettingsSectionHeader("Keyboard")
    SettingsCard {
        // Voice or keyboard is the first choice search opens with, so it leads the card.
        var assistantVoice by rememberSetting(SettingsManager.ASSISTANT_STARTS_WITH_VOICE)
        SettingsRowToggle(
            title = "Start Assistant with Voice",
            subtitle = "Open Search from the Assistant Gesture with the Microphone On. Only When It's Your Default Digital Assistant.",
            icon = Icons.Outlined.Mic,
            isChecked = assistantVoice,
            onCheckedChange = { assistantVoice = it },
            showDivider = true
        )
        var showKeyboard by rememberSetting(SettingsManager.SHOW_KEYBOARD_ON_OPEN)
        SettingsRowToggle(
            title = "Show Keyboard When Search Opens",
            subtitle = "Open Search with the Keyboard Up. Off, Tap the Search Bar to Bring It Up.",
            icon = Icons.Outlined.Keyboard,
            isChecked = showKeyboard,
            onCheckedChange = { showKeyboard = it },
            showDivider = true
        )
        var enterAction by rememberSetting(SettingsManager.ENTER_KEY_ACTION)
        SettingsSelectorRow(
            title = "Enter Key Action",
            subtitle = "What Enter Does When No Result Is Highlighted.",
            icon = Icons.AutoMirrored.Outlined.KeyboardReturn,
            options = EnterKeyAction.entries,
            selected = EnterKeyAction.from(enterAction),
            onSelect = { enterAction = it.storedValue },
            label = { it.label },
            showDivider = false,
            selectorModifier = Modifier.testTag(ENTER_KEY_ACTION_TAG)
        )
    }
}
