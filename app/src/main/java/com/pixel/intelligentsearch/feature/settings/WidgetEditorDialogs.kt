package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.get
import android.content.SharedPreferences
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val HEX_COLOR_REGEX = Regex("^#?[0-9A-Fa-f]{6}$")
private const val HEX_COLOR_HINT = "Enter six hex digits, e.g. #4285F4"

/** The opaque colour [raw] spells as six hex digits, with or without a leading '#', or null if it doesn't. */
internal fun parseHexColor(raw: String): Int? {
    val hex = raw.trim()
    if (!HEX_COLOR_REGEX.matches(hex)) return null
    return (0xFF000000.toInt() or hex.removePrefix("#").toInt(16))
}

/** The widget editor's shortcut sheet and its hex, URL and app dialogs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetEditorDialogs(
    fields: WidgetDraftFields,
    ui: WidgetEditorState,
    colors: WidgetColorSelection,
    shortcutOptions: List<Pair<String, ImageVector>>,
    prefs: SharedPreferences
) {
    var localSubtheme by fields::subtheme
    var localCustomGIconColor by fields::customGIconColor
    var localCustomMicColor by fields::customMicColor
    var localCustomShortcut1Color by fields::customShortcut1Color
    var localCustomShortcut2Color by fields::customShortcut2Color
    var localCustomShortcut3Color by fields::customShortcut3Color
    var localCustomActionColor by fields::customActionColor
    var localCustomSyncColors by fields::customSyncColors
    var localHue by fields::hue
    var localSaturation by fields::saturation
    var localLightness by fields::lightness
    var localShortcut1 by fields::shortcut1
    var localShortcut2 by fields::shortcut2
    var localShortcut3 by fields::shortcut3
    val pendingCustomShortcutTypes = ui.pendingCustomShortcutTypes
    val pendingCustomShortcutValues = ui.pendingCustomShortcutValues
    var showShortcutSheet by ui::showShortcutSheet
    var showCustomUrlDialogFor by ui::showCustomUrlDialogFor
    var showCustomAppDialogFor by ui::showCustomAppDialogFor
    var customInputValue by ui::customInputValue
    var expandedDropdownFor by ui::expandedDropdownFor
    var showHexInput by ui::showHexInput
    var tempHexInput by ui::tempHexInput
    var showCustomHexDialog by ui::showCustomHexDialog
    var tempCustomHexInput by ui::tempCustomHexInput
    var activeShortcutSlot by ui::activeShortcutSlot
    var activeCustomColorTarget by colors::activeCustomColorTarget
    var customIconHue by colors::customIconHue
    var customIconSat by colors::customIconSat
    var customIconVal by colors::customIconVal

    if (showShortcutSheet) {
        ModalBottomSheet(onDismissRequest = { showShortcutSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select Widget Shortcut", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(shortcutOptions.size) { index ->
                        val option = shortcutOptions[index]
                        val isCustomizable = option.first in listOf("Weather", "Sports", "Dictionary")
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.bouncyClickable {
                                if (isCustomizable) {
                                    expandedDropdownFor = option.first
                                } else {
                                    when (activeShortcutSlot) {
                                        1 -> localShortcut1 = option.first
                                        2 -> localShortcut2 = option.first
                                        3 -> localShortcut3 = option.first
                                    }
                                    showShortcutSheet = false
                                }
                            }
                        ) {
                            val currentShortcutSlotVal = when (activeShortcutSlot) {
                                1 -> localShortcut1
                                2 -> localShortcut2
                                else -> localShortcut3
                            }
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = option.second,
                                    contentDescription = option.first,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            if (currentShortcutSlotVal == option.first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                        .padding(16.dp),
                                    tint = if (currentShortcutSlotVal == option.first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isCustomizable) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Options",
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(20.dp)
                                            .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = expandedDropdownFor == option.first,
                                        onDismissRequest = { expandedDropdownFor = null }
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Default (Google)") },
                                            onClick = {
                                                // Drop any custom URL/app override so the slot really returns to the default target.
                                                val shortcutKey = option.first
                                                if (prefs.getString("${shortcutKey}_custom_type", "default") == "default") {
                                                    pendingCustomShortcutTypes.remove(shortcutKey)
                                                    pendingCustomShortcutValues.remove(shortcutKey)
                                                } else {
                                                    pendingCustomShortcutTypes[shortcutKey] = "default"
                                                    pendingCustomShortcutValues.remove(shortcutKey)
                                                }
                                                when (activeShortcutSlot) {
                                                    1 -> localShortcut1 = option.first
                                                    2 -> localShortcut2 = option.first
                                                    3 -> localShortcut3 = option.first
                                                }
                                                showShortcutSheet = false
                                                expandedDropdownFor = null
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Custom Website (URL)") },
                                            onClick = {
                                                customInputValue = ""
                                                showCustomUrlDialogFor = option.first
                                                expandedDropdownFor = null
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Custom App (APK)") },
                                            onClick = {
                                                customInputValue = ""
                                                showCustomAppDialogFor = option.first
                                                expandedDropdownFor = null
                                            }
                                        )
                                    }
                                }
                            }
                            Text(
                                text = option.first,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHexInput) {
        val parsed = parseHexColor(tempHexInput)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHexInput = false },
            title = { Text("Enter Hex Color") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = tempHexInput,
                    onValueChange = { tempHexInput = it },
                    label = { Text("Hex Code") },
                    singleLine = true,
                    isError = tempHexInput.isNotBlank() && parsed == null,
                    supportingText = if (tempHexInput.isNotBlank() && parsed == null) {
                        { Text(HEX_COLOR_HINT) }
                    } else null
                )
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = {
                    if (parsed != null) {
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(parsed, hsv)
                        // Saved as whole numbers, so they're rounded here: the preview then shows the colour that
                        // will be saved.
                        localHue = Math.round(hsv[0]).toFloat()
                        localSaturation = Math.round(hsv[1] * 100).toFloat()
                        localLightness = Math.round(hsv[2] * 100).toFloat()
                        localSubtheme = "Custom"
                    }
                    showHexInput = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showHexInput = false }) { Text("Cancel") }
            }
        )
    }

    if (showCustomHexDialog) {
        val parsed = parseHexColor(tempCustomHexInput)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustomHexDialog = false },
            title = { Text("Enter Hex Color") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = tempCustomHexInput,
                    onValueChange = { tempCustomHexInput = it },
                    label = { Text("Hex Code (e.g. #4285F4)") },
                    singleLine = true,
                    isError = tempCustomHexInput.isNotBlank() && parsed == null,
                    supportingText = if (tempCustomHexInput.isNotBlank() && parsed == null) {
                        { Text(HEX_COLOR_HINT) }
                    } else null
                )
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = {
                    if (parsed != null) {
                        val color = parsed
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(color, hsv)
                        if (hsv[1] > 0.01f || hsv[2] > 0.01f) {
                            customIconHue = hsv[0]
                        }
                        customIconSat = hsv[1] * 100f
                        customIconVal = hsv[2] * 100f
                        if (localCustomSyncColors) {
                            localCustomGIconColor = color
                            localCustomMicColor = color
                            localCustomShortcut1Color = color
                            localCustomShortcut2Color = color
                            localCustomShortcut3Color = color
                            localCustomActionColor = color
                        } else {
                            when (activeCustomColorTarget) {
                                "g_icon" -> localCustomGIconColor = color
                                "mic" -> localCustomMicColor = color
                                "shortcut1" -> localCustomShortcut1Color = color
                                "shortcut2" -> localCustomShortcut2Color = color
                                "shortcut3" -> localCustomShortcut3Color = color
                                "action" -> localCustomActionColor = color
                                else -> localCustomGIconColor = color
                            }
                        }
                    }
                    showCustomHexDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomHexDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCustomUrlDialogFor != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustomUrlDialogFor = null },
            title = { Text("Custom URL for ${showCustomUrlDialogFor}") },
            text = {
                val candidate = customInputValue.trim()
                androidx.compose.material3.OutlinedTextField(
                    value = customInputValue,
                    onValueChange = { customInputValue = it },
                    label = { Text("Enter URL") },
                    singleLine = true,
                    isError = candidate.isNotEmpty() && !isValidShortcutUrl(candidate),
                    supportingText = if (candidate.isNotEmpty() && !isValidShortcutUrl(candidate)) {
                        { Text("Enter a web address, e.g. example.com or https://example.com") }
                    } else null
                )
            },
            confirmButton = {
                TextButton(enabled = isValidShortcutUrl(customInputValue.trim()), onClick = {
                    val slot = showCustomUrlDialogFor
                    if (slot != null) {
                        pendingCustomShortcutTypes[slot] = "url"
                        pendingCustomShortcutValues[slot] = customInputValue.trim()
                        when (activeShortcutSlot) {
                            1 -> localShortcut1 = slot
                            2 -> localShortcut2 = slot
                            3 -> localShortcut3 = slot
                        }
                    }
                    showShortcutSheet = false
                    showCustomUrlDialogFor = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialogFor = null }) { Text("Cancel") }
            }
        )
    }

    if (showCustomAppDialogFor != null) {
        // The widget opens the app through its launch intent and falls back to the default target without one,
        // so only an installed app that can be launched is accepted.
        val packageManager = LocalContext.current.packageManager
        val packageName = customInputValue.trim()
        val installed = remember(packageName) {
            PACKAGE_NAME_REGEX.matches(packageName) && packageManager.getLaunchIntentForPackage(packageName) != null
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustomAppDialogFor = null },
            title = { Text("Custom App for ${showCustomAppDialogFor}") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = customInputValue,
                    onValueChange = { customInputValue = it },
                    label = { Text("Enter Package Name") },
                    singleLine = true,
                    isError = packageName.isNotEmpty() && !installed,
                    supportingText = if (packageName.isNotEmpty() && !installed) {
                        { Text("No installed app with that package name") }
                    } else null
                )
            },
            confirmButton = {
                TextButton(enabled = installed, onClick = {
                    val slot = showCustomAppDialogFor
                    if (slot != null) {
                        pendingCustomShortcutTypes[slot] = "app"
                        pendingCustomShortcutValues[slot] = customInputValue.trim()
                        when (activeShortcutSlot) {
                            1 -> localShortcut1 = slot
                            2 -> localShortcut2 = slot
                            3 -> localShortcut3 = slot
                        }
                    }
                    showShortcutSheet = false
                    showCustomAppDialogFor = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomAppDialogFor = null }) { Text("Cancel") }
            }
        )
    }
}
