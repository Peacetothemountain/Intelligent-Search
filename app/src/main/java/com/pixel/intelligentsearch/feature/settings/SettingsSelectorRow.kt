package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A settings row's leading icon, title and optional subtitle, laid out as every row lays them out. */
@Composable
private fun SettingsRowLabel(title: String, subtitle: String?, icon: ImageVector, modifier: Modifier) {
    // One stop for TalkBack: the label reads as a whole, decorative icon aside.
    Row(modifier = modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * A setting chosen from a few [options] on the app's one selector ([ShellSelector]): the row's icon, [title] and
 * [subtitle], then the choices under it, each named by [label] on one line that shrinks to fit at large text sizes,
 * after its [leading] mark when there is one (a provider's logo, told whether its option is selected).
 */
@Composable
internal fun <T> SettingsSelectorRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    selectorModifier: Modifier = Modifier,
    leading: (@Composable (option: T, isSelected: Boolean) -> Unit)? = null
) {
    Column(modifier = modifier) {
        SettingsRowLabel(
            title, subtitle, icon,
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
        )
        ShellSelector(
            options = options,
            selected = selected,
            onSelect = onSelect,
            modifier = selectorModifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            itemHeight = 48.dp,
            indicatorShape = RoundedCornerShape(24.dp)
        ) { option, isSelected ->
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leading != null) {
                    leading(option, isSelected)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    autoSize = oneLineAutoSize(MaterialTheme.typography.labelLarge)
                )
            }
        }
        if (showDivider) SettingsRowDivider()
    }
}

/**
 * A row that only tells (how something works, what is sent where): laid out like the other rows, with no chevron and
 * nothing to tap.
 */
@Composable
internal fun SettingsInfoRow(title: String, subtitle: String, icon: ImageVector, showDivider: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SettingsRowLabel(title, subtitle, icon, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp))
        if (showDivider) SettingsRowDivider()
    }
}
