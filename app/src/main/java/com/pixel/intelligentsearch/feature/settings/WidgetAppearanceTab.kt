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
import androidx.compose.ui.graphics.graphicsLayer
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

/** One colorable widget element as listed in the Appearance tab: its color key, label and current color. */
private data class CustomElementTarget(val key: String, val label: String, val color: Int)

/**
 * The label color for a subtheme pill filled with [fill]: black or white, whichever contrasts more with what
 * the label actually sits on. The fill can be translucent (the custom color's opacity, the unselected pill's
 * gradient), so it is composited over [surface], the page behind the pills, first.
 */
internal fun subthemePillLabelColor(fill: Color, surface: Color): Color {
    val shown = fill.compositeOver(surface).toArgb()
    val onBlack = MaterialYouPaletteHelper.calculateContrastRatio(Color.Black.toArgb(), shown)
    val onWhite = MaterialYouPaletteHelper.calculateContrastRatio(Color.White.toArgb(), shown)
    return if (onBlack >= onWhite) Color.Black else Color.White
}

/**
 * The Custom G style's Saturation track: from grey to the most colorful at the [hue] and [brightness] (0..360 and
 * 0..100) the other two sliders have set, so every point on it is the color the slider picks there.
 */
internal fun elementSaturationTrack(hue: Float, brightness: Float): List<Color> {
    val value = (brightness / 100f).coerceIn(0f, 1f)
    return listOf(0f, 1f).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, it, value))) }
}

/** The Custom G style's Brightness track: from black to the brightest at the [hue] and [saturation] set. */
internal fun elementBrightnessTrack(hue: Float, saturation: Float): List<Color> {
    val share = (saturation / 100f).coerceIn(0f, 1f)
    return listOf(0f, 1f).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, share, it))) }
}

/**
 * The Appearance tab of the widget editor: G icon, subtheme, Material options and element colors.
 */
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
    val customIconHue by colors::customIconHue
    val customIconSat by colors::customIconSat
    val customIconVal by colors::customIconVal
    val localThemeStyle by themeStyle

    // TAB 0: APPEARANCE
    val isMaterialYou = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"

    // G Icon options
    SettingsCard(lit = false) {
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
        AttachedRows(visible = localShowGIcon) {
            SettingsRowToggle(
                title = "Doodles",
                subtitle = "Show Mini Doodles Around the G Icon for Holidays and Special Events.",
                icon = DoodleBrushIcon,
                isChecked = localShowDoodles,
                onCheckedChange = { localShowDoodles = it },
                showDivider = false
            )
        }
    }

    if (isMaterialYou) {
        SettingsSectionHeader("Widget Action Icon")
        val actionOptions = listOf(
            Triple("None", "None", null),
            Triple("Search", "Search", com.pixel.intelligentsearch.R.drawable.ic_search_lens_expressive),
            Triple("Assistant", "Assistant", com.pixel.intelligentsearch.R.drawable.ic_lens_action),
            Triple("Now Playing", "Playing", com.pixel.intelligentsearch.R.drawable.ic_music)
        )
        val currentAction = if (localActionIcon == "Gemini") "Assistant" else localActionIcon
        val actionTile = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        // Taps on each option, so every tap, the chosen option's included, replays that icon's animation.
        val actionTaps = remember { mutableStateMapOf<String, Int>() }
        // In a settings card as it always was, each option its own soft tile and the chosen one primary.
        SettingsCard(lit = false) {
            ShellSelector(
                options = actionOptions,
                selected = actionOptions.firstOrNull { it.first == currentAction } ?: actionOptions.first(),
                onSelect = {
                    localActionIcon = it.first
                    actionTaps[it.first] = (actionTaps[it.first] ?: 0) + 1
                },
                itemHeight = 64.dp,
                contentPadding = 6.dp,
                gap = 6.dp,
                indicatorShape = RoundedCornerShape(20.dp),
                unselectedColor = { actionTile }
            ) { (optionKey, label, iconRes), isSel ->
                val tint = LocalContentColor.current
                // Choosing an option plays its icon's own short animation, clipped within the column's padding
                // so it never leaves the tile; the label stays still.
                val motion = rememberSelectionMotion(isSel, ActionIconMotion.durationMillis(optionKey), actionTaps[optionKey] ?: 0)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    if (iconRes != null) {
                        ComposeActionIcon(
                            iconType = optionKey,
                            modifier = Modifier.size(22.dp).clipAround(4.dp),
                            primaryColor = tint,
                            secondaryColor = tint,
                            tertiaryColor = tint,
                            motion = motion
                        )
                    } else {
                        val pose = remember { ActionIconPose() }
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "None",
                            modifier = Modifier
                                .size(22.dp)
                                .clipAround(4.dp)
                                .graphicsLayer {
                                    ActionIconMotion.pose(optionKey, motion(), pose)
                                    val unit = size.width / 24f
                                    rotationZ = pose.body.rotation
                                    scaleX = pose.body.scaleX
                                    scaleY = pose.body.scaleY
                                    translationX = pose.body.dx * unit
                                    translationY = pose.body.dy * unit
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        autoSize = oneLineAutoSize(MaterialTheme.typography.labelSmall)
                    )
                }
            }
        }
    }

    // Floating Theme Pills
    // The Custom pill is painted in the custom color, so its label is black or white to match. Unselected, the
    // pill is a gradient up from half opacity and the label sits on its midpoint, both shown over the page. The
    // other pills are each a soft tile, the chosen one primary, straight on the page with no track behind them.
    val pageColor = MaterialTheme.colorScheme.background
    val pillTile = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val dynColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
    val subthemeOptions = if (localThemeStyle == "System Default") {
        listOf("System", "Light", "Dark", "Custom")
    } else {
        listOf("Material", "Custom")
    }
    ShellSelector(
        options = subthemeOptions,
        selected = localSubtheme,
        onSelect = { opt ->
            localSubtheme = opt
            if (opt == "Material") {
                localLockBlack = true
            }
        },
        modifier = Modifier.padding(vertical = 4.dp),
        contentPadding = 0.dp,
        gap = 8.dp,
        indicatorShape = RoundedCornerShape(32.dp),
        indicatorColor = { opt -> if (opt == "Custom") dynColor else androidx.compose.ui.graphics.Color.Unspecified },
        unselectedColor = { opt -> if (opt == "Custom") androidx.compose.ui.graphics.Color.Transparent else pillTile }
    ) { opt, isSel ->
        val isCustom = opt == "Custom"
        if (isCustom && !isSel) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(dynColor.copy(alpha = 0.5f), dynColor)
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
            )
        }
        Text(
            opt,
            style = MaterialTheme.typography.labelMedium,
            color = if (isCustom) {
                subthemePillLabelColor(if (isSel) dynColor else dynColor.copy(alpha = 0.75f), pageColor)
            } else {
                androidx.compose.ui.graphics.Color.Unspecified
            },
            maxLines = 1,
            autoSize = oneLineAutoSize(MaterialTheme.typography.labelMedium)
        )
    }

    SettingsSectionHeader("G Icon Style")
    // G Icon Row with Custom G Icon button right beside Accented G Icon
    SettingsCard(lit = false) {
        ShellSelector(
            options = listOf("System G Icon", "Material G Icon", "Accented G Icon", "Custom G Icon"),
            selected = localMaterialGIconTheme,
            onSelect = { localMaterialGIconTheme = it },
            indicatorShape = RoundedCornerShape(20.dp)
        ) { opt, isSel ->
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
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
            )
        }
    }

    // The Material design's Inner Pill & Circle switch lives in Color Studio, with the color it forces them to.
    val isMaterialDesign = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"

    WidgetBarShapeSection(fields = fields, isMaterialDesign = isMaterialDesign)

    if (localMaterialGIconTheme == "Custom G Icon") {
        SettingsSectionHeader("Custom Icon Colors")
        SettingsCard(lit = false) {
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

                // The sliders follow the color being edited wherever it was set: picking another element, Sync All
                // Colors, the hex field, another profile or Reset all put a different color here. A color the
                // sliders picked themselves leaves them where they are.
                LaunchedEffect(colors, activeColor) { colors.show(activeColor) }

                // 3. Hue Slider (matching Color Studio)
                GradientSliderRow(
                    icon = Icons.Default.Palette,
                    label = "Hue",
                    valueText = "${customIconHue.toInt()}°",
                    gradient = HUE_GRADIENT,
                    value = customIconHue,
                    onValueChange = { updateTargetColor(colors.pick(hue = it)) },
                    valueRange = 0f..360f
                )

                Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

                // 4. Saturation Slider (matching Color Studio)
                GradientSliderRow(
                    icon = Icons.Default.WaterDrop,
                    label = "Saturation",
                    valueText = "${customIconSat.toInt()}%",
                    gradient = elementSaturationTrack(customIconHue, customIconVal),
                    value = customIconSat,
                    onValueChange = { updateTargetColor(colors.pick(saturation = it)) },
                    valueRange = 0f..100f
                )

                Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

                // 5. Brightness Slider (matching Color Studio)
                GradientSliderRow(
                    icon = Icons.Outlined.BrightnessMedium,
                    label = "Brightness",
                    valueText = "${customIconVal.toInt()}%",
                    gradient = elementBrightnessTrack(customIconHue, customIconSat),
                    value = customIconVal,
                    onValueChange = { updateTargetColor(colors.pick(brightness = it)) },
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

/**
 * The bar's Outline Thickness and Corner Radius ([WidgetBarShape]). Corner Radius is the search bar's own corners (the
 * pill's, and the Material design's circle's), up to fully round, whatever the outline; the outline follows them.
 */
@Composable
private fun WidgetBarShapeSection(fields: WidgetDraftFields, isMaterialDesign: Boolean) {
    var outline by fields::outlineThickness
    var corner by fields::cornerRadius
    val design = if (isMaterialDesign) com.pixel.intelligentsearch.core.data.widget.WidgetDesign.MATERIAL else com.pixel.intelligentsearch.core.data.widget.WidgetDesign.SYSTEM
    val maxCorner = com.pixel.intelligentsearch.feature.widget.WidgetBarShape.maxCornerRadius(isMaterialDesign)
    // The widget draws outlines and corners in whole steps (WidgetBarShape.STEP), so the sliders move in them too.
    val step = com.pixel.intelligentsearch.feature.widget.WidgetBarShape.STEP
    SettingsSectionHeader("Bar Shape")
    SettingsCard(lit = false) {
        SettingsSliderRow(
            title = "Outline Thickness",
            value = outline.toFloat(),
            onValueChange = { outline = kotlin.math.round(it).toInt() },
            valueRange = 0f..com.pixel.intelligentsearch.core.data.widget.MAX_OUTLINE_THICKNESS.toFloat(),
            steps = com.pixel.intelligentsearch.core.data.widget.MAX_OUTLINE_THICKNESS / step - 1,
            icon = Icons.Outlined.LineWeight,
            showDivider = true,
            onReset = { outline = design.outlineThicknessKey.default },
            valueText = { v -> if (kotlin.math.round(v).toInt() == 0) "None" else "${kotlin.math.round(v).toInt()} dp" }
        )
        SettingsSliderRow(
            title = "Corner Radius",
            value = corner.coerceAtMost(maxCorner).toFloat(),
            onValueChange = { corner = kotlin.math.round(it).toInt() },
            valueRange = 0f..maxCorner.toFloat(),
            steps = (maxCorner / step - 1).coerceAtLeast(0),
            icon = Icons.Outlined.RoundedCorner,
            showDivider = false,
            onReset = { corner = design.cornerRadiusKey.default },
            valueText = { v -> kotlin.math.round(v).toInt().let { if (it >= maxCorner) "Fully Round" else "$it dp" } }
        )
    }
}
