package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
        // A label for the selector under it rather than a control of its own, so TalkBack reads it as one stop.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardReturn,
                contentDescription = null, // decorative: the title is right beside it
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enter Key Action",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "What Enter Does When No Result Is Highlighted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ShellSelector(
            options = EnterKeyAction.entries,
            selected = EnterKeyAction.from(enterAction),
            onSelect = { enterAction = it.storedValue },
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                .testTag(ENTER_KEY_ACTION_TAG),
            itemHeight = 48.dp,
            indicatorShape = RoundedCornerShape(24.dp)
        ) { option, isSelected ->
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                autoSize = oneLineAutoSize(MaterialTheme.typography.labelLarge),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
