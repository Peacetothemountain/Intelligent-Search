package com.ngdesigns.intelligentsearch.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ngdesigns.intelligentsearch.ui.components.aura
import com.ngdesigns.intelligentsearch.ui.components.rememberHaptics

/**
 * The search bar: contained style (a filled, fully rounded container) paired with a vibrant voice FAB, the
 * floating-toolbar-with-FAB pattern Material uses for inputs in the rail region. Ghost text completes the best
 * match; Tab or → at the end of the text accepts it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchInput(
    state: TextFieldState,
    completion: String?,
    working: Boolean,
    animate: Boolean,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onLens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val accept = {
        if (!completion.isNullOrEmpty()) {
            state.setTextAndPlaceCursorAtEnd(state.text.toString() + completion)
            haptics.tick()
        }
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            shadowElevation = 3.dp,
            modifier = Modifier.weight(1f).height(64.dp).aura(active = working, cornerRadius = 32.dp, animate = animate),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(start = 14.dp)) {
                    BasicTextField(
                        state = state,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = { onSubmit() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Search" }
                            .onPreviewKeyEvent { e ->
                                val atEnd = state.selection.collapsed && state.selection.start == state.text.length
                                if (e.type == KeyEventType.KeyDown && !completion.isNullOrEmpty() &&
                                    (e.key == Key.Tab || e.key == Key.DirectionRight && atEnd)
                                ) { accept(); true } else false
                            },
                        decorator = { field ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (state.text.isEmpty()) {
                                    Text("Search apps, people and the web", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else if (!completion.isNullOrEmpty()) {
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(SpanStyle(color = colors.surfaceContainerHigh.copy(alpha = 0f))) { append(state.text.toString()) }
                                            withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(completion) }
                                        },
                                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Clip,
                                    )
                                }
                                field()
                            }
                        },
                    )
                }
                if (state.text.isNotEmpty()) {
                    IconButton(onClick = { state.clearText(); haptics.tick() }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
                } else {
                    IconButton(onClick = onLens) { Icon(Icons.Rounded.DocumentScanner, contentDescription = "Search with your camera") }
                }
            }
        }
        // The vibrant FAB that pairs with the bar (the toolbar FAB helpers assume a toolbar slot to size them).
        FloatingActionButton(
            onClick = { haptics.confirm(); onVoice() },
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = "Search by voice")
        }
    }
}

/** All, Apps, People, Files, Web: a connected button group; the selected button morphs to fully round. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScopeButtons(selected: Scope, onSelect: (Scope) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val scopes = Scope.entries
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        scopes.forEachIndexed { i, scope ->
            ToggleButton(
                checked = scope == selected,
                onCheckedChange = { if (it) { onSelect(scope); haptics.tick() } },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    scopes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Show ${scope.name.lowercase()}" },
            ) {
                Text(scope.name, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = MaterialTheme.typography.labelSmall.fontSize, maxFontSize = MaterialTheme.typography.labelLarge.fontSize))
            }
        }
    }
}

/** Puts the cursor at the end; used when the bar is filled from a recent search or a command template. */
fun TextFieldState.fill(text: String) = setTextAndPlaceCursorAtEnd(text)
