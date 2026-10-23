package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Keyboard
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
 * Search Behavior's Keyboard card: whether the keyboard comes up when search opens, and what Enter does when no
 * result is highlighted (Open Top Result or Search the Web).
 */
@Composable
internal fun KeyboardSettingsCard() {
    SettingsSectionHeader("Keyboard")
    SettingsCard {
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
