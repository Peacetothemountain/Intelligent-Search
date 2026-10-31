package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.*
import com.pixel.intelligentsearch.core.theme.pageTrack
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import kotlinx.coroutines.launch

/** The Widget Shortcuts tab of the widget editor: the four slots, their order and customization. */
@Composable
internal fun WidgetShortcutsTab(
    fields: WidgetDraftFields,
    ui: WidgetEditorState,
    view: android.view.View,
    hapticEngine: PixelHapticEngine,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    density: androidx.compose.ui.unit.Density,
    shortcutOptions: List<Pair<String, ImageVector>>,
    themeStyle: MutableState<String>
) {
    var localShowVoice by fields::showVoice
    var localShortcut1 by fields::shortcut1
    var localShortcut2 by fields::shortcut2
    var localShortcut3 by fields::shortcut3
    var localSlotOrderStr by fields::slotOrderStr
    var showShortcutSheet by ui::showShortcutSheet
    var activeShortcutSlot by ui::activeShortcutSlot
    var draggingSlotKey by ui::draggingSlotKey
    var slotItemHeightPx by ui::slotItemHeightPx
    var itemDragOffset by ui::itemDragOffset
    var localThemeStyle by themeStyle
    val isSystem = localThemeStyle == "System Default"

    // TAB 2: WIDGET SHORTCUTS
    SettingsSectionHeader("Widget Shortcuts")
    // Inset like the section header above it.
    Text("Drag Handle to Reorder, Swipe to Disable, and Tap to Customize.", style = MaterialTheme.typography.bodyMedium, fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val currentOrderList = localSlotOrderStr.split(",").filter { it.isNotBlank() }
        // Swaps a slot with its neighbour, as dragging it past half a card does.
        val moveSlot: (String, Int) -> Unit = { slot, delta ->
            val updated = localSlotOrderStr.split(",").filter { it.isNotBlank() }.toMutableList()
            val from = updated.indexOf(slot)
            if (from != -1 && from + delta in updated.indices) {
                java.util.Collections.swap(updated, from, from + delta)
                localSlotOrderStr = updated.joinToString(",")
            }
        }
        currentOrderList.forEach { slotKey ->
            key(slotKey) {
                val isDragging = draggingSlotKey == slotKey
                val elevation by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 0.dp,
                    label = "elevation"
                )
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isDragging) 1.03f else 1f,
                    label = "scale"
                )
                val swipeOffsetX = remember { androidx.compose.animation.core.Animatable(0f) }

                val currentPositionIndex = currentOrderList.indexOf(slotKey)
                val isMic = slotKey == "mic"
                val actionName = when (slotKey) {
                    "mic" -> if (localShowVoice) "Voice Search" else "None"
                    "shortcut1" -> localShortcut1
                    "shortcut2" -> localShortcut2
                    else -> localShortcut3
                }
                val isEmpty = actionName == "None"
                // "Slot 1 · Google Lens": the position, then what the slot does. Under it, what kind of slot it is,
                // or for an empty one, what tapping it adds.
                val cardTitle = "Slot ${currentPositionIndex + 1} · ${if (isEmpty) "Empty" else actionName}"
                val cardSubtext = when {
                    isEmpty && isMic -> "Tap to Add Voice Search"
                    isEmpty -> "Tap to Add a Shortcut"
                    isMic -> "Microphone"
                    else -> "Shortcut"
                }

                // An empty slot shows Add, which is what tapping it does; an X there read as delete.
                val cardIcon = when {
                    isEmpty -> Icons.Outlined.Add
                    isMic -> Icons.Default.Mic
                    else -> shortcutOptions.find { it.first == actionName }?.second ?: Icons.Default.Close
                }

                val cardModifier = if (isDragging) {
                    Modifier
                        .zIndex(10f)
                        .graphicsLayer {
                            translationY = itemDragOffset
                            scaleX = scale
                            scaleY = scale
                        }
                } else {
                    Modifier
                        .zIndex(0f)
                        .graphicsLayer {
                            translationX = swipeOffsetX.value
                            scaleX = scale
                            scaleY = scale
                        }
                }

                Box(
                    modifier = cardModifier
                        .fillMaxWidth()
                        .onSizeChanged {
                            if (it.height > 0) {
                                slotItemHeightPx = it.height.toFloat() + with(density) { 12.dp.toPx() }
                            }
                        }
                        .pointerInput(slotKey, isDragging, isSystem) {
                            if (!isDragging) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        coroutineScope.launch {
                                            if (kotlin.math.abs(swipeOffsetX.value) > 200f) {
                                                hapticEngine.performPredictiveBackHaptic(view)
                                                when (slotKey) {
                                                    "mic" -> localShowVoice = false
                                                    "shortcut1" -> localShortcut1 = "None"
                                                    "shortcut2" -> localShortcut2 = "None"
                                                    "shortcut3" -> localShortcut3 = "None"
                                                }
                                            }
                                            swipeOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                                )
                                            )
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            swipeOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                                )
                                            )
                                        }
                                    },
                                    onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                                        change.consume()
                                        coroutineScope.launch {
                                            swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount)
                                        }
                                    }
                                )
                            }
                        }
                ) {
                    // The card's own colour, outline and drag shadow, with the matte grain on its surface.
                    com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation, RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.pageTrack(0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Trimmed around the drag handle's 48dp touch target, so the card keeps its size and
                                // the handle's icon stays 16dp in from the edges.
                                .padding(start = 16.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    // Dragging the handle is touch-only, so the card also offers Move up and Move
                                    // down to TalkBack and switch access.
                                    .semantics {
                                        customActions = buildList {
                                            if (currentPositionIndex > 0) {
                                                add(CustomAccessibilityAction("Move up") { moveSlot(slotKey, -1); true })
                                            }
                                            if (currentPositionIndex in 0 until currentOrderList.lastIndex) {
                                                add(CustomAccessibilityAction("Move down") { moveSlot(slotKey, 1); true })
                                            }
                                        }
                                    }
                                    .clickable {
                                        hapticEngine.performPredictiveBackHaptic(view)
                                        if (slotKey == "mic") {
                                            localShowVoice = !localShowVoice
                                        } else {
                                            activeShortcutSlot = when (slotKey) {
                                                "shortcut1" -> 1
                                                "shortcut2" -> 2
                                                else -> 3
                                            }
                                            showShortcutSheet = true
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = cardIcon,
                                    // A filled slot's action is named in its title right beside the icon.
                                    contentDescription = when {
                                        isEmpty && isMic -> "Add Voice Search"
                                        isEmpty -> "Add Shortcut"
                                        else -> null
                                    },
                                    modifier = Modifier.size(36.dp),
                                    tint = if (isEmpty) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = cardTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = cardSubtext,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            val currentSlot by rememberUpdatedState(slotKey)
                            // The handle's touch target is a full 48dp around its 24dp icon.
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = "Drag to reorder" }
                                    .pointerInput(currentSlot, isSystem) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                hapticEngine.performPredictiveBackHaptic(view)
                                                draggingSlotKey = currentSlot
                                                itemDragOffset = 0f
                                            },
                                            onDragEnd = {
                                                draggingSlotKey = null
                                                itemDragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggingSlotKey = null
                                                itemDragOffset = 0f
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                itemDragOffset += dragAmount

                                                val currentDraggingSlot = draggingSlotKey ?: return@detectVerticalDragGestures
                                                val currentList = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                                                val from = currentList.indexOf(currentDraggingSlot)
                                                val itemHeight = slotItemHeightPx

                                                if (from != -1 && itemHeight > 0f) {
                                                    if (itemDragOffset > itemHeight / 2f && from < currentList.size - 1) {
                                                        hapticEngine.performPredictiveBackHaptic(view)
                                                        val updated = currentList.toMutableList()
                                                        java.util.Collections.swap(updated, from, from + 1)
                                                        localSlotOrderStr = updated.joinToString(",")
                                                        itemDragOffset -= itemHeight
                                                    } else if (itemDragOffset < -itemHeight / 2f && from > 0) {
                                                        hapticEngine.performPredictiveBackHaptic(view)
                                                        val updated = currentList.toMutableList()
                                                        java.util.Collections.swap(updated, from, from - 1)
                                                        localSlotOrderStr = updated.joinToString(",")
                                                        itemDragOffset += itemHeight
                                                    }
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DragHandle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
