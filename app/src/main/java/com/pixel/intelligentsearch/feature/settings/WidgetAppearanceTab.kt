package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper

/** One colourable widget element as listed in the Appearance tab: its colour key, label and current colour. */
private data class CustomElementTarget(val key: String, val label: String, val color: Int)

/**
 * The label colour for a subtheme pill filled with [fill]: black or white, whichever contrasts more with what
 * the label actually sits on. The fill can be translucent (the custom colour's opacity, the unselected pill's
 * gradient), so it is composited over [surface], the page behind the pills, first.
 */
internal fun subthemePillLabelColor(fill: Color, surface: Color): Color {
    val shown = fill.compositeOver(surface).toArgb()
    val onBlack = MaterialYouPaletteHelper.calculateContrastRatio(Color.Black.toArgb(), shown)
    val onWhite = MaterialYouPaletteHelper.calculateContrastRatio(Color.White.toArgb(), shown)
    return if (onBlack >= onWhite) Color.Black else Color.White
}

/** The Appearance tab of the widget editor: G icon, subtheme, Material options and element colours. */
@Composable
internal fun WidgetAppearanceTab(
    fields: WidgetDraftFields,
    ui: WidgetEditorState,
    colors: WidgetColorSelection,
    themeStyle: MutableState<String>,
    computedCustomColorInt: Int,
    view: android.view.View,
    hapticEngine: PixelHapticEngine
) {
    var localSubtheme by fields::subtheme
    var localMaterialGIconTheme by fields::materialGIconTheme
    var localCustomGIconColor by fields::customGIconColor
    var localCustomMicColor by fields::customMicColor
    var localCustomShortcut1Color by fields::customShortcut1Color
    var localCustomShortcut2Color by fields::customShortcut2Color
    var localCustomShortcut3Color by fields::customShortcut3Color
    var localCustomActionColor by fields::customActionColor
    var localCustomSyncColors by fields::customSyncColors
    var localShowGIcon by fields::showGIcon
    var localShowDoodles by fields::showDoodles
    var localShowVoice by fields::showVoice
    var localShortcut1 by fields::shortcut1
    var localShortcut2 by fields::shortcut2
    var localShortcut3 by fields::shortcut3
    var localSlotOrderStr by fields::slotOrderStr
    var localKeepSystemG by fields::keepSystemG
    var localLockBlack by fields::lockBlack
    var localActionIcon by fields::actionIcon
    var showCustomHexDialog by ui::showCustomHexDialog
    var tempCustomHexInput by ui::tempCustomHexInput
    var activeCustomColorTarget by colors::activeCustomColorTarget
    var customIconHue by colors::customIconHue
    var customIconSat by colors::customIconSat
    var customIconVal by colors::customIconVal
    var localThemeStyle by themeStyle
    val isSystem = localThemeStyle == "System Default"

    // TAB 0: APPEARANCE
    val isMaterialYou = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"

    // G Icon options
    SettingsCard {
        SettingsRowToggle(
            title = "Display G Icon",
            subtitle = "Show Google Logo on Search Bar Widget.",
            icon = null,
            customIcon = {
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_g_logo_colored),
                    contentDescription = "G Icon",
                    modifier = Modifier.size(24.dp)
                )
            },
            isChecked = localShowGIcon,
            onCheckedChange = { localShowGIcon = it },
            showDivider = true
        )
        AnimatedVisibility(visible = localShowGIcon) {
            SettingsRowToggle(
                title = "Doodles",
                subtitle = "Show Mini Doodles Around the G Icon for Holidays and Special Events.",
                icon = Icons.Outlined.Celebration,
                isChecked = localShowDoodles,
                onCheckedChange = { localShowDoodles = it },
                showDivider = false
            )
        }
    }

    if (isMaterialYou) {
        SettingsSectionHeader("Widget Action Icon")
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val actionOptions = listOf(
                    Triple("None", "None", null),
                    Triple("Search", "Search", com.pixel.intelligentsearch.R.drawable.ic_search_lens_expressive),
                    Triple("Assistant", "Assistant", com.pixel.intelligentsearch.R.drawable.ic_lens_action),
                    Triple("Now Playing", "Playing", com.pixel.intelligentsearch.R.drawable.ic_music)
                )
                val currentAction = if (localActionIcon == "Gemini") "Assistant" else localActionIcon
                actionOptions.forEach { (optionKey, label, iconRes) ->
                    val isSel = currentAction == optionKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .semantics { selected = isSel }
                            .bouncyClickable(shape = RoundedCornerShape(20.dp)) {
                                localActionIcon = optionKey
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            if (iconRes != null) {
                                ComposeActionIcon(
                                    iconType = optionKey,
                                    modifier = Modifier.size(22.dp),
                                    primaryColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    secondaryColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    tertiaryColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "None",
                                    modifier = Modifier.size(22.dp),
                                    tint = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Floating Theme Pills
    // The Custom pill is painted in the custom colour, so its label is black or white to match. Unselected, the
    // pill is a gradient up from half opacity and the label sits on its midpoint, both shown over the page.
    val pageColor = MaterialTheme.colorScheme.background
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (localThemeStyle == "System Default") {
            val systemOpts = listOf("System", "Light", "Dark", "Custom")
            systemOpts.forEach { opt ->
                val isSel = localSubtheme == opt
                val dynColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
                val bgModifier = if (opt == "Custom") {
                    if (isSel) {
                        Modifier.background(dynColor, RoundedCornerShape(32.dp))
                    } else {
                        Modifier.background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    dynColor.copy(alpha = 0.5f),
                                    dynColor
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                    }
                } else {
                    Modifier.background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .then(bgModifier)
                        .clip(RoundedCornerShape(32.dp))
                        .semantics { selected = isSel }
                        .bouncyClickable(shape = RoundedCornerShape(32.dp)) { localSubtheme = opt },
                    contentAlignment = Alignment.Center
                ) {
                    val textColor = if (opt == "Custom") {
                        subthemePillLabelColor(if (isSel) dynColor else dynColor.copy(alpha = 0.75f), pageColor)
                    } else if (isSel) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(opt, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.labelMedium))
                }
            }
        } else {
            val matOpts = listOf("Material", "Custom")
            matOpts.forEach { opt ->
                val isSel = localSubtheme == opt
                val dynColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
                val bgModifier = if (opt == "Custom") {
                    if (isSel) {
                        Modifier.background(dynColor, RoundedCornerShape(32.dp))
                    } else {
                        Modifier.background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    dynColor.copy(alpha = 0.5f),
                                    dynColor
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                    }
                } else {
                    Modifier.background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .then(bgModifier)
                        .clip(RoundedCornerShape(32.dp))
                        .semantics { selected = isSel }
                        .bouncyClickable(shape = RoundedCornerShape(32.dp)) {
                            localSubtheme = opt
                            if (opt == "Material") {
                                localLockBlack = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val textColor = if (opt == "Custom") {
                        subthemePillLabelColor(if (isSel) dynColor else dynColor.copy(alpha = 0.75f), pageColor)
                    } else if (isSel) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(opt, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.labelMedium))
                }
            }
        }
    }

    SettingsSectionHeader("G Icon Style")
    // G Icon Row with Custom G Icon button right beside Accented G Icon
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val opts = listOf("System G Icon", "Material G Icon", "Accented G Icon", "Custom G Icon")
            opts.forEach { opt ->
                val isSel = localMaterialGIconTheme == opt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .semantics { selected = isSel }
                        .bouncyClickable(shape = RoundedCornerShape(20.dp)) {
                            localMaterialGIconTheme = opt
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val label = when (opt) {
                        "System G Icon" -> "System"
                        "Material G Icon" -> "Material"
                        "Accented G Icon" -> "Accented"
                        "Custom G Icon" -> "Custom"
                        else -> opt
                    }
                    Text(
                        text = label,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        autoSize = oneLineAutoSize(MaterialTheme.typography.labelMedium),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Material Design Inner Pill & Circle Switcher
    val isMaterialDesign = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"
    if (isMaterialDesign) {
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            SettingsRowToggle(
                title = "Inner Pill & Circle State",
                subtitle = "Force Inner Pill & Circle to #121212.",
                isChecked = localLockBlack,
                onCheckedChange = { checked ->
                    localLockBlack = checked
                }
            )
        }
    }

    if (localMaterialGIconTheme == "Custom G Icon") {
        SettingsSectionHeader("Custom Icon Colors")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                // 0. Default Google Color Palette Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Google Color Palette", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Preserve multi-colored Google logo while customizing shortcuts and action button",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    com.pixel.intelligentsearch.core.ui.expressive.GoogleMessagesSwitch(
                        checked = localKeepSystemG,
                        onCheckedChange = { keep ->
                            localKeepSystemG = keep
                        },
                        label = "Default Google Color Palette"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Sync All Colors Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync All Colors", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (localKeepSystemG) "Set shortcuts and action icon to the same color" else "Set G icon, shortcuts, and action icon to the same color",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    com.pixel.intelligentsearch.core.ui.expressive.GoogleMessagesSwitch(
                        checked = localCustomSyncColors,
                        onCheckedChange = { synced ->
                            localCustomSyncColors = synced
                            if (synced) {
                                localCustomMicColor = localCustomGIconColor
                                localCustomShortcut1Color = localCustomGIconColor
                                localCustomShortcut2Color = localCustomGIconColor
                                localCustomShortcut3Color = localCustomGIconColor
                                localCustomActionColor = localCustomGIconColor
                            }
                        },
                        label = "Sync All Colors"
                    )
                }

                // 2. Intelligent Shortcut / Element Selector (When Sync All is disabled)
                val activeElements = remember(localShowGIcon, localKeepSystemG, localShowVoice, localShortcut1, localShortcut2, localShortcut3, localActionIcon, localSlotOrderStr, isMaterialYou, localCustomGIconColor, localCustomMicColor, localCustomShortcut1Color, localCustomShortcut2Color, localCustomShortcut3Color, localCustomActionColor) {
                    val list = mutableListOf<CustomElementTarget>()
                    if (localShowGIcon && !localKeepSystemG) {
                        list.add(CustomElementTarget("g_icon", "G Icon", localCustomGIconColor))
                    }
                    val slotOrder = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                    slotOrder.forEach { key ->
                        when (key) {
                            "mic" -> if (localShowVoice) list.add(CustomElementTarget("mic", "Voice Search", localCustomMicColor))
                            "shortcut1" -> if (localShortcut1 != "None") list.add(CustomElementTarget("shortcut1", localShortcut1, localCustomShortcut1Color))
                            "shortcut2" -> if (localShortcut2 != "None") list.add(CustomElementTarget("shortcut2", localShortcut2, localCustomShortcut2Color))
                            "shortcut3" -> if (localShortcut3 != "None") list.add(CustomElementTarget("shortcut3", localShortcut3, localCustomShortcut3Color))
                        }
                    }
                    if (isMaterialYou && localActionIcon != "None") {
                        list.add(CustomElementTarget("action", "Action: $localActionIcon", localCustomActionColor))
                    }
                    if (list.isEmpty()) {
                        if (!localKeepSystemG) {
                            list.add(CustomElementTarget("g_icon", "G Icon", localCustomGIconColor))
                        } else {
                            list.add(CustomElementTarget("mic", "Voice Search", localCustomMicColor))
                        }
                    }
                    list
                }

                LaunchedEffect(activeElements) {
                    if (activeElements.none { it.key == activeCustomColorTarget }) {
                        activeCustomColorTarget = activeElements.firstOrNull()?.key ?: "g_icon"
                    }
                }

                if (!localCustomSyncColors) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "SELECT ELEMENT TO CUSTOMIZE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var elementDropdownExpanded by remember { mutableStateOf(false) }
                    val currentSelectedElement = activeElements.firstOrNull { it.key == activeCustomColorTarget } ?: activeElements.first()

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .bouncyClickable(shape = RoundedCornerShape(20.dp)) {
                                    elementDropdownExpanded = true
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(currentSelectedElement.color))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = currentSelectedElement.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (elementDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        MaterialTheme(
                            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(24.dp))
                        ) {
                            androidx.compose.material3.DropdownMenu(
                                expanded = elementDropdownExpanded,
                                onDismissRequest = { elementDropdownExpanded = false },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                                    .clip(RoundedCornerShape(24.dp))
                            ) {
                                activeElements.forEach { element ->
                                    val isSelected = element.key == activeCustomColorTarget
                                    androidx.compose.material3.DropdownMenuItem(
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(androidx.compose.ui.graphics.Color(element.color))
                                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = element.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            hapticEngine.performPredictiveBackHaptic(view)
                                            activeCustomColorTarget = element.key
                                            elementDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val (activeColor, activeLabel) = when {
                    localCustomSyncColors -> Pair(localCustomGIconColor, "All Icons")
                    activeCustomColorTarget == "g_icon" -> Pair(localCustomGIconColor, "G Icon")
                    activeCustomColorTarget == "mic" -> Pair(localCustomMicColor, "Voice Search")
                    activeCustomColorTarget == "shortcut1" -> Pair(localCustomShortcut1Color, localShortcut1)
                    activeCustomColorTarget == "shortcut2" -> Pair(localCustomShortcut2Color, localShortcut2)
                    activeCustomColorTarget == "shortcut3" -> Pair(localCustomShortcut3Color, localShortcut3)
                    activeCustomColorTarget == "action" -> Pair(localCustomActionColor, "Action: $localActionIcon")
                    else -> Pair(localCustomGIconColor, "G Icon")
                }

                val updateTargetColor: (Int) -> Unit = { newColor ->
                    if (localCustomSyncColors) {
                        localCustomGIconColor = newColor
                        localCustomMicColor = newColor
                        localCustomShortcut1Color = newColor
                        localCustomShortcut2Color = newColor
                        localCustomShortcut3Color = newColor
                        localCustomActionColor = newColor
                    } else {
                        when (activeCustomColorTarget) {
                            "g_icon" -> localCustomGIconColor = newColor
                            "mic" -> localCustomMicColor = newColor
                            "shortcut1" -> localCustomShortcut1Color = newColor
                            "shortcut2" -> localCustomShortcut2Color = newColor
                            "shortcut3" -> localCustomShortcut3Color = newColor
                            "action" -> localCustomActionColor = newColor
                            else -> localCustomGIconColor = newColor
                        }
                    }
                }

                LaunchedEffect(activeCustomColorTarget, localCustomSyncColors, isSystem, localMaterialGIconTheme) {
                    val h = FloatArray(3)
                    android.graphics.Color.colorToHSV(activeColor, h)
                    if (h[1] > 0.01f || h[2] > 0.01f) {
                        customIconHue = h[0]
                    }
                    customIconSat = h[1] * 100f
                    customIconVal = h[2] * 100f
                }

                // 3. Hue Slider (matching Color Studio)
                GradientSliderRow(
                    icon = Icons.Default.Palette,
                    label = "Hue",
                    valueText = "${customIconHue.toInt()}°",
                    gradient = HUE_GRADIENT,
                    value = customIconHue,
                    onValueChange = {
                        customIconHue = it
                        val newCol = android.graphics.Color.HSVToColor(
                            floatArrayOf(it, (customIconSat / 100f).coerceIn(0f, 1f), (customIconVal / 100f).coerceIn(0f, 1f))
                        )
                        updateTargetColor(newCol)
                    },
                    valueRange = 0f..360f
                )

                Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

                // 4. Saturation Slider (matching Color Studio)
                GradientSliderRow(
                    icon = Icons.Default.WaterDrop,
                    label = "Saturation",
                    valueText = "${customIconSat.toInt()}%",
                    gradient = listOf(
                        androidx.compose.ui.graphics.Color.White,
                        androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(customIconHue, 1f, 1f)))
                    ),
                    value = customIconSat,
                    onValueChange = {
                        customIconSat = it
                        val newCol = android.graphics.Color.HSVToColor(
                            floatArrayOf(customIconHue, (it / 100f).coerceIn(0f, 1f), (customIconVal / 100f).coerceIn(0f, 1f))
                        )
                        updateTargetColor(newCol)
                    },
                    valueRange = 0f..100f
                )

                Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

                // 5. Brightness Slider (matching Color Studio)
                GradientSliderRow(
                    icon = Icons.Outlined.BrightnessMedium,
                    label = "Brightness",
                    valueText = "${customIconVal.toInt()}%",
                    gradient = listOf(
                        androidx.compose.ui.graphics.Color.Black,
                        androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(customIconHue, (customIconSat / 100f).coerceIn(0f, 1f), 1f)))
                    ),
                    value = customIconVal,
                    onValueChange = {
                        customIconVal = it
                        val newCol = android.graphics.Color.HSVToColor(
                            floatArrayOf(customIconHue, (customIconSat / 100f).coerceIn(0f, 1f), (it / 100f).coerceIn(0f, 1f))
                        )
                        updateTargetColor(newCol)
                    },
                    valueRange = 0f..100f
                )

                Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

                // 6. Hex Color Pill Bar (matching Color Studio)
                val activeHex = String.format("#%06X", (0xFFFFFF and activeColor))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .clickable {
                            tempCustomHexInput = activeHex
                            showCustomHexDialog = true
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(androidx.compose.ui.graphics.Color(activeColor))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "$activeLabel  •  $activeHex",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Hex",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
