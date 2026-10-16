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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    Text("CUSTOM COLOR PALETTE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Hue Slider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Palette, contentDescription = "Hue", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hue", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${localHue.toInt()}°", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Red,
                                androidx.compose.ui.graphics.Color.Yellow,
                                androidx.compose.ui.graphics.Color.Green,
                                androidx.compose.ui.graphics.Color.Cyan,
                                androidx.compose.ui.graphics.Color.Blue,
                                androidx.compose.ui.graphics.Color.Magenta,
                                androidx.compose.ui.graphics.Color.Red
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )) {
                        Android17Slider(
                            showTrack = false,
                            enableHaptics = false,
                            value = localHue,
                            onValueChange = { 
                                localSubtheme = "Custom"
                                localHue = it 
                            },
                            valueRange = 0f..360f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Saturation Slider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.WaterDrop, contentDescription = "Saturation", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Saturation", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${localSaturation.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.White,
                                androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, 1f, 1f)))
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )) {
                        Android17Slider(
                            showTrack = false,
                            enableHaptics = false,
                            value = localSaturation,
                            onValueChange = { 
                                localSubtheme = "Custom"
                                localSaturation = it 
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Lightness Slider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.BrightnessMedium, contentDescription = "Lightness", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lightness", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${localLightness.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Black,
                                androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, (localSaturation / 100f).coerceIn(0f, 1f), 1f)))
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )) {
                        Android17Slider(
                            showTrack = false,
                            enableHaptics = false,
                            value = localLightness,
                            onValueChange = { 
                                localSubtheme = "Custom"
                                localLightness = it 
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Color Opacity Slider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Contrast, contentDescription = "Color Opacity", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Color Opacity", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${localColorOpacity.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, localSaturation / 100f, 1f)))
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )) {
                        Android17Slider(
                            value = localColorOpacity,
                            enableHaptics = false,
                            onValueChange = { 
                                localSubtheme = "Custom"
                                localColorOpacity = it 
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            showTrack = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Transparency Slider (under color opacity in the same container)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Opacity, contentDescription = "Transparency", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Transparency", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${localTransparency.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = transparencyTrackGradient
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )) {
                        Android17Slider(
                            value = localTransparency,
                            enableHaptics = false,
                            onValueChange = { localTransparency = it },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            showTrack = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
