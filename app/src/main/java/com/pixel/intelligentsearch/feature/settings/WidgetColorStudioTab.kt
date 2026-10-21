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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The hue slider's track: the colour wheel unrolled, red to red. */
internal val HUE_GRADIENT = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** The space between two of the editor's colour sliders, below the room the slider leaves under its track. */
internal val SLIDER_ROW_GAP = 4.dp

/**
 * One of the widget editor's colour sliders: an icon, the [label] and [valueText] above, and the slider over an 8dp
 * [gradient] track. The slider keeps its full 48dp height, with the track centred in it, so its 36dp thumb stays
 * clear of the text above; it is named [label] for TalkBack, so the icon beside it isn't announced.
 */
@Composable
internal fun GradientSliderRow(
    icon: ImageVector,
    label: String,
    valueText: String,
    gradient: List<Color>,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(brush = androidx.compose.ui.graphics.Brush.horizontalGradient(colors = gradient), shape = RoundedCornerShape(8.dp))
                )
                Android17Slider(
                    showTrack = false,
                    enableHaptics = false,
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    modifier = Modifier.fillMaxWidth(),
                    label = label
                )
            }
        }
    }
}

/** The Color Studio tab of the widget editor: custom colour sliders and hex entry. */
@Composable
internal fun WidgetColorStudioTab(
    fields: WidgetDraftFields,
    ui: WidgetEditorState,
    computedCustomColorInt: Int
) {
    var localSubtheme by fields::subtheme
    var localHue by fields::hue
    var localSaturation by fields::saturation
    var localLightness by fields::lightness
    var localColorOpacity by fields::colorOpacity
    var localTransparency by fields::transparency
    var showHexInput by ui::showHexInput
    var tempHexInput by ui::tempHexInput

    // TAB 1: COLOR STUDIO
    val dynamicCustomColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
    val transparencyTrackGradient = remember(computedCustomColorInt) {
        listOf(
            dynamicCustomColor,
            dynamicCustomColor.copy(alpha = 0.7f),
            dynamicCustomColor.copy(alpha = 0.35f),
            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f),
            androidx.compose.ui.graphics.Color(0xFF3C4043).copy(alpha = 0.6f),
            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)
        )
    }

    // Custom Color Palette Container on Top
    Text("CUSTOM COLOR PALETTE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 4.dp).semantics { heading() })
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Hue Slider
            GradientSliderRow(
                icon = Icons.Default.Palette,
                label = "Hue",
                valueText = "${localHue.toInt()}°",
                gradient = HUE_GRADIENT,
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
                gradient = listOf(
                    androidx.compose.ui.graphics.Color.White,
                    androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, 1f, 1f)))
                ),
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
                gradient = listOf(
                    androidx.compose.ui.graphics.Color.Black,
                    androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, (localSaturation / 100f).coerceIn(0f, 1f), 1f)))
                ),
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
                gradient = listOf(
                    androidx.compose.ui.graphics.Color.Transparent,
                    androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, localSaturation / 100f, 1f)))
                ),
                value = localColorOpacity,
                onValueChange = {
                    localSubtheme = "Custom"
                    localColorOpacity = it
                },
                valueRange = 0f..100f
            )

            Spacer(modifier = Modifier.height(SLIDER_ROW_GAP))

            // 5. Transparency Slider (under color opacity in the same container)
            GradientSliderRow(
                icon = Icons.Default.Opacity,
                label = "Transparency",
                valueText = "${localTransparency.toInt()}%",
                gradient = transparencyTrackGradient,
                value = localTransparency,
                onValueChange = { localTransparency = it },
                valueRange = 0f..100f
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
