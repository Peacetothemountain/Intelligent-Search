package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.get
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectable
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.feature.widget.WidgetColorRules

/** The hue slider's track: the color wheel unrolled, red to red. */
internal val HUE_GRADIENT = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** The Color Studio's hue track: the custom color's HCT hues round the wheel, at full saturation and mid lightness. */
private val SEED_HUE_GRADIENT: List<Color> by lazy {
    (0..12).map { Color(MaterialYouPaletteHelper.seedColor(it * 30f, 100f, 60f)) }
}

/** The custom color's [hue] and [saturation] at each of [lightnesses]. */
private fun seedTrack(hue: Float, saturation: Float, lightnesses: List<Float>): List<Color> =
    lightnesses.map { Color(MaterialYouPaletteHelper.seedColor(hue, saturation, it)) }

/** The space between two of the editor's color sliders, below the room the slider leaves under its track. */
internal val SLIDER_ROW_GAP = 4.dp

/**
 * The Transparency slider's track, left to right as the slider runs from 0 to 100: the bar's background as it is
 * drawn at Transparency 0 ([color], at the custom color's opacity for the Custom subtheme), fading to nothing. The
 * color keeps its hue all the way, rather than darkening towards black.
 */
internal fun transparencyTrack(color: Color): List<Color> = listOf(color, color.copy(alpha = 0f))

/**
 * The Color Opacity slider's track: the custom color from fully see-through to fully opaque. It fades from the color
 * itself at no opacity; fading from transparent black darkened the left half of the track.
 */
internal fun colorOpacityTrack(color: Color): List<Color> = listOf(color.copy(alpha = 0f), color.copy(alpha = 1f))

/**
 * A small grey and white checkerboard behind a track that fades out, so how see-through each point is shows on its own.
 * [cell] is the size of each square.
 */
private fun Modifier.checkerboard(light: Color, dark: Color, cell: androidx.compose.ui.unit.Dp = 4.dp): Modifier =
    drawBehind {
        val side = cell.toPx()
        drawRect(light)
        val columns = kotlin.math.ceil(size.width / side).toInt()
        val rows = kotlin.math.ceil(size.height / side).toInt()
        for (column in 0 until columns) for (row in 0 until rows) {
            if ((column + row) % 2 == 0) {
                drawRect(dark, topLeft = Offset(column * side, row * side), size = Size(side, side))
            }
        }
    }

/**
 * One of the widget editor's color sliders: an icon, the [label] and [valueText] above, and the slider over an 8dp
 * [gradient] track, drawn over a [checkerboard] when the track fades out. The slider keeps its full 48dp height, with
 * the track centerd in it, so its 36dp thumb stays clear of the text above; it is named [label] for TalkBack, so the
 * icon beside it isn't announced, and reads [valueText] as its value ("180°" rather than a share of the range).
 */
@Composable
internal fun GradientSliderRow(
    icon: ImageVector,
    label: String,
    valueText: String,
    gradient: List<Color>,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    checkerboard: Boolean = false
) {
    val trackShape = RoundedCornerShape(8.dp)
    val trackBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(colors = gradient)
    val track = Modifier.fillMaxWidth().height(8.dp)
    val checkerLight = MaterialTheme.colorScheme.surfaceContainerHighest
    val checkerDark = MaterialTheme.colorScheme.outlineVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.weight(1f))
                Text(valueText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = if (checkerboard) {
                        track.clip(trackShape).checkerboard(checkerLight, checkerDark).background(trackBrush)
                    } else {
                        track.background(brush = trackBrush, shape = trackShape)
                    }
                )
                Android17Slider(
                    showTrack = false,
                    enableHaptics = false,
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    modifier = Modifier.fillMaxWidth(),
                    label = label,
                    valueDescription = valueText
                )
            }
        }
    }
}

/**
 * The Color Studio tab of the widget editor: the search bar's palette (the custom color sliders, Transparency with
 * them, and hex entry) and, on the Material design ([isMaterialDesign]), the Inner Pill & Circle's own palette.
 * [barBackground] is the bar's background as the preview draws it at Transparency 0, for the Transparency track.
 */
@Composable
internal fun WidgetColorStudioTab(
    fields: WidgetDraftFields,
    ui: WidgetEditorState,
    computedCustomColorInt: Int,
    isMaterialDesign: Boolean,
    barBackground: Color
) {
    val localSubtheme by fields::subtheme
    val dynamicCustomColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)

    // The search bar's palette on top (the Custom subtheme's own color); the Inner Pill & Circle's under it on the
    // Material design. Each is named in the accent color.
    if (localSubtheme == "Custom") SearchBarPalette(
        fields = fields,
        ui = ui,
        computedCustomColorInt = computedCustomColorInt,
        barBackground = barBackground,
        dynamicCustomColor = dynamicCustomColor
    )

    if (isMaterialDesign) InnerPillPalette(fields)
}

/** The search bar's palette: the custom color's sliders, Transparency with them, and hex entry. */
@Composable
private fun SearchBarPalette(
    fields: WidgetDraftFields,
    ui: WidgetEditorState,
    computedCustomColorInt: Int,
    barBackground: Color,
    dynamicCustomColor: Color
) {
    var localSubtheme by fields::subtheme
    var localHue by fields::hue
    var localSaturation by fields::saturation
    var localLightness by fields::lightness
    var localColorOpacity by fields::colorOpacity
    var localTransparency by fields::transparency
    var showHexInput by ui::showHexInput
    var tempHexInput by ui::tempHexInput
    SettingsSectionHeader("Search Bar Color Palette")
    SettingsCard(lit = false) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Hue Slider
            GradientSliderRow(
                icon = Icons.Default.Palette,
                label = "Hue",
                valueText = "${localHue.toInt()}°",
                gradient = SEED_HUE_GRADIENT,
                value = localHue,
                onValueChange = {
                    localSubtheme = "Custom"
                    localHue = it
                },
                valueRange = 0f..360f
            )

            Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

            // 2. Saturation Slider
            GradientSliderRow(
                icon = Icons.Default.WaterDrop,
                label = "Saturation",
                valueText = "${localSaturation.toInt()}%",
                // Grey to the most colorful at this lightness (kept off black and white, where no hue shows).
                gradient = listOf(0f, 100f).map {
                    Color(MaterialYouPaletteHelper.seedColor(localHue, it, localLightness.coerceIn(15f, 85f)))
                },
                value = localSaturation,
                onValueChange = {
                    localSubtheme = "Custom"
                    localSaturation = it
                },
                valueRange = 0f..100f
            )

            Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

            // 3. Lightness Slider
            GradientSliderRow(
                icon = Icons.Outlined.BrightnessMedium,
                label = "Lightness",
                valueText = "${localLightness.toInt()}%",
                // Black through the color to white.
                gradient = seedTrack(localHue, localSaturation, listOf(0f, 25f, 50f, 75f, 100f)),
                value = localLightness,
                onValueChange = {
                    localSubtheme = "Custom"
                    localLightness = it
                },
                valueRange = 0f..100f
            )

            Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

            // 4. Color Opacity Slider
            GradientSliderRow(
                icon = Icons.Default.Contrast,
                label = "Color Opacity",
                valueText = "${localColorOpacity.toInt()}%",
                gradient = colorOpacityTrack(dynamicCustomColor),
                value = localColorOpacity,
                onValueChange = {
                    localSubtheme = "Custom"
                    localColorOpacity = it
                },
                valueRange = 0f..100f,
                checkerboard = true
            )

            Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

            // 5. Transparency, under Color Opacity: the bar's background fading to nothing, over a checkerboard.
            GradientSliderRow(
                icon = Icons.Default.Opacity,
                label = "Transparency",
                valueText = "${localTransparency.toInt()}%",
                gradient = transparencyTrack(barBackground),
                value = localTransparency,
                onValueChange = { localTransparency = it },
                valueRange = 0f..100f,
                checkerboard = true
            )

            Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

            // 6. Hex Color Pill Bar (pill bar under custom color pallet sliders in the same container)
            val customColorHex = String.format("#%06X", (0xFFFFFF and computedCustomColorInt))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .clickable {
                        tempHexInput = customColorHex
                        showHexInput = true
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(dynamicCustomColor)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    customColorHex,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Hex",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * The Inner Pill & Circle's palette (Material design only): the switch that forces the pill and circle to one color,
 * and under it that color: the default #121212, or one picked with Hue, Saturation and Lightness (the same seed
 * sliders as the search bar's) or typed as hex.
 */
@Composable
private fun InnerPillPalette(fields: WidgetDraftFields) {
    var lockBlack by fields::lockBlack
    var pillColor by fields::lockedPillColor
    val opaque = pillColor or 0xFF000000.toInt()
    val isDefault = opaque == WidgetColorRules.LOCKED_PILL
    // The sliders keep their own positions (a grey has no hue, so the color alone can't say where Hue was); a color
    // set some other way (Default, hex, a discarded draft) moves them to match.
    var sliders by remember { mutableStateOf(MaterialYouPaletteHelper.wholeSeedSliders(opaque, greyHue = 0f)) }
    LaunchedEffect(opaque) {
        val shown = MaterialYouPaletteHelper.seedColor(sliders.hue, sliders.saturation, sliders.lightness) or 0xFF000000.toInt()
        if (shown != opaque) sliders = MaterialYouPaletteHelper.wholeSeedSliders(opaque, greyHue = sliders.hue)
    }
    fun pick(next: MaterialYouPaletteHelper.SeedSliders) {
        sliders = next
        pillColor = MaterialYouPaletteHelper.seedColor(next.hue, next.saturation, next.lightness) or 0xFF000000.toInt()
    }
    var showHex by remember { mutableStateOf(false) }
    var hexInput by remember { mutableStateOf("") }
    val hex = String.format("#%06X", 0xFFFFFF and opaque)

    SettingsSectionHeader("Inner Pill & Circle Color Palette")
    SettingsCard(lit = false) {
        SettingsRowToggle(
            title = "Inner Pill & Circle State",
            subtitle = "Force the Inner Pill & Circle to One Color.",
            isChecked = lockBlack,
            onCheckedChange = { lockBlack = it },
            showDivider = false
        )
        AttachedRows(visible = lockBlack) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                // The default first: the near-black the pill and circle have always been forced to.
                PaletteChoiceRow(
                    swatch = Color(WidgetColorRules.LOCKED_PILL),
                    title = "Default",
                    detail = "#121212",
                    selected = isDefault,
                    onClick = { pillColor = WidgetColorRules.LOCKED_PILL }
                )

                Spacer(modifier = Modifier.height(SLIDER_ROW_GAP + 4.dp))

                GradientSliderRow(
                    icon = Icons.Default.Palette,
                    label = "Hue",
                    valueText = "${sliders.hue.toInt()}°",
                    gradient = SEED_HUE_GRADIENT,
                    value = sliders.hue,
                    onValueChange = { pick(sliders.copy(hue = it.toInt().toFloat())) },
                    valueRange = 0f..360f
                )
                Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))
                GradientSliderRow(
                    icon = Icons.Default.WaterDrop,
                    label = "Saturation",
                    valueText = "${sliders.saturation.toInt()}%",
                    gradient = listOf(0f, 100f).map {
                        Color(MaterialYouPaletteHelper.seedColor(sliders.hue, it, sliders.lightness.coerceIn(15f, 85f)))
                    },
                    value = sliders.saturation,
                    onValueChange = { pick(sliders.copy(saturation = it.toInt().toFloat())) },
                    valueRange = 0f..100f
                )
                Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))
                GradientSliderRow(
                    icon = Icons.Outlined.BrightnessMedium,
                    label = "Lightness",
                    valueText = "${sliders.lightness.toInt()}%",
                    gradient = seedTrack(sliders.hue, sliders.saturation, listOf(0f, 25f, 50f, 75f, 100f)),
                    value = sliders.lightness,
                    onValueChange = { pick(sliders.copy(lightness = it.toInt().toFloat())) },
                    valueRange = 0f..100f
                )
                Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))
                PaletteChoiceRow(
                    swatch = Color(opaque),
                    title = hex,
                    detail = null,
                    selected = false,
                    trailing = Icons.Default.Edit,
                    trailingDescription = "Edit Inner Pill & Circle Hex",
                    onClick = {
                        hexInput = hex
                        showHex = true
                    }
                )
            }
        }
    }

    if (showHex) {
        val parsed = parseHexColor(hexInput)
        AlertDialog(
            onDismissRequest = { showHex = false },
            title = { Text("Inner Pill & Circle Hex") },
            text = {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("Hex Code") },
                    singleLine = true,
                    isError = hexInput.isNotBlank() && parsed == null,
                    supportingText = if (hexInput.isNotBlank() && parsed == null) {
                        { Text("Enter six hex digits, e.g. #121212") }
                    } else null
                )
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = {
                    // Exactly the color typed; the sliders move to the nearest they can show.
                    if (parsed != null) pillColor = parsed
                    showHex = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showHex = false }) { Text("Cancel") } }
        )
    }
}

/**
 * One choice in a palette: a pill with the color's [swatch], its [title] and [detail], and a check while [selected]
 * or a [trailing] icon (the hex field's pencil).
 */
@Composable
private fun PaletteChoiceRow(
    swatch: Color,
    title: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: ImageVector? = null,
    trailingDescription: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val textColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceVariant.copy(alpha = 0.7f))
            .then(if (selected) Modifier.border(1.dp, scheme.primary, shape) else Modifier)
            // A choice reads as a radio button; the hex row, which opens an editor, as a button.
            .then(
                if (trailing != null) Modifier.clickable(onClick = onClick, role = androidx.compose.ui.semantics.Role.Button)
                else Modifier.selectable(selected = selected, onClick = onClick, role = androidx.compose.ui.semantics.Role.RadioButton)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(1.dp, scheme.outlineVariant, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)
        if (detail != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = textColor)
        }
        Spacer(modifier = Modifier.weight(1f))
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            trailing != null -> Icon(trailing, contentDescription = trailingDescription, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
