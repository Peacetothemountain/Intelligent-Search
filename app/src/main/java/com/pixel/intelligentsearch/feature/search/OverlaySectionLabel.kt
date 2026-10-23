package com.pixel.intelligentsearch.feature.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Where a section label sits on its own: across the list, inset like the rows under it. */
internal val SectionLabelPadding = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)

/**
 * The overlay's one small section heading ("Pinned", "At a Glance", "Recent", "Notifications", "Trending"): the same
 * type and colour everywhere, and a heading for TalkBack, so its users can jump between sections.
 */
@Composable
internal fun OverlaySectionLabel(text: String, modifier: Modifier = SectionLabelPadding) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.semantics { heading() }
    )
}
