package com.pixel.intelligentsearch.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The app's long-press and pop-up menus' panel: the 24dp rounded corners of its other menus (SettingsDropdownRow),
 * which cast a clean shadow, on the high surface container with a hairline border. The search results' menu and the
 * saved widget themes' menu both use it.
 */
object MenuStyle {
    val shape = RoundedCornerShape(24.dp)
    @Composable fun containerColor() = MaterialTheme.colorScheme.surfaceContainerHigh
    @Composable fun border() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
