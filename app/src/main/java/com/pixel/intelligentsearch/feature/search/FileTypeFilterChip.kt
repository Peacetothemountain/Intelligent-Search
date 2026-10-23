package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.fileTypeIcon
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.haptics.rememberTactileSonicEngine

/**
 * Above the file results when the query names a type ("pdf invoice"): says the files are limited to [group], in the
 * same primary-container chip as a contact's action chips; tapping it removes the limit for this query.
 */
@Composable
internal fun FileTypeFilterChip(
    group: FileTypeGroup,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    /** The keyboard's highlight ring, drawn on the chip itself. */
    highlight: Modifier = Modifier
) {
    val view = LocalView.current
    val sensoryEngine = rememberTactileSonicEngine()
    AssistChip(
        onClick = {
            sensoryEngine.click(view)
            onRemove()
        },
        label = {
            // The chip's description says this and what tapping does.
            Text("${group.label} Only", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics { })
        },
        leadingIcon = {
            Icon(fileTypeIcon(group), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
        trailingIcon = {
            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(highlight)
            .semantics { contentDescription = "Showing only ${group.label}. Remove filter" },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            trailingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = null,
        shape = FileTypeFilterChipShape
    )
}

/** The chip's shape, which the keyboard's highlight ring follows. */
internal val FileTypeFilterChipShape = RoundedCornerShape(16.dp)
