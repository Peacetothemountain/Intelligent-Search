package com.pixel.intelligentsearch.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * For a field of several lines (a pasted code): a keyboard's Tab and Shift+Tab move on to the next or previous control
 * rather than type a tab character, so what follows the field can be reached. Its text never holds a tab.
 */
fun Modifier.tabMovesFocus(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (event.key != Key.Tab) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) {
            focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
        }
        true
    }
}
