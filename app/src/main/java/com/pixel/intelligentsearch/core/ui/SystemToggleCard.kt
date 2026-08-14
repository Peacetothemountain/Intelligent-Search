package com.pixel.intelligentsearch.core.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.feature.settings.bouncyClickable

data class SystemToggleUiState(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconType: String,
    val isEnabled: Boolean,
    val isActionOnly: Boolean = false,
    val onToggle: (Boolean) -> Unit,
    val onOpenSettings: () -> Unit = {}
)

@Composable
fun SystemToggleCard(
    toggleState: SystemToggleUiState,
    modifier: Modifier = Modifier
) {
    var isChecked by remember(toggleState.id, toggleState.isEnabled) { mutableStateOf(toggleState.isEnabled) }

    val iconScale by animateFloatAsState(
        targetValue = if (isChecked) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconScale"
    )

    val iconVector: ImageVector = when (toggleState.iconType) {
        "flashlight" -> if (isChecked) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff
        "bluetooth" -> if (isChecked) Icons.Outlined.BluetoothConnected else Icons.Outlined.Bluetooth
        "wifi" -> if (isChecked) Icons.Outlined.Wifi else Icons.Outlined.WifiOff
        "cellular" -> if (isChecked) Icons.Outlined.SignalCellularAlt else Icons.Outlined.SignalCellularOff
        "airplane" -> if (isChecked) Icons.Outlined.AirplanemodeActive else Icons.Outlined.AirplanemodeInactive
        "autorotate" -> Icons.Outlined.ScreenRotation
        "dnd" -> if (isChecked) Icons.Outlined.DoNotDisturbOn else Icons.Outlined.DoNotDisturbOff
        "battery" -> if (isChecked) Icons.Outlined.BatterySaver else Icons.Outlined.BatteryStd
        "location" -> if (isChecked) Icons.Outlined.LocationOn else Icons.Outlined.LocationOff
        "hotspot" -> if (isChecked) Icons.Outlined.WifiTethering else Icons.Outlined.WifiTetheringOff
        "darkmode" -> if (isChecked) Icons.Outlined.DarkMode else Icons.Outlined.LightMode
        "nightlight" -> Icons.Outlined.Nightlight
        "nfc" -> Icons.Outlined.Nfc
        "sound" -> if (isChecked) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp
        "cast" -> Icons.Outlined.Cast
        "brightness" -> Icons.Outlined.BrightnessMedium
        "privacy" -> Icons.Outlined.Security
        else -> Icons.Outlined.Tune
    }

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .bouncyClickable(
                onClick = {
                    if (toggleState.isActionOnly) {
                        toggleState.onOpenSettings()
                    } else {
                        val newState = !isChecked
                        isChecked = newState
                        toggleState.onToggle(newState)
                    }
                },
                onLongClick = {
                    toggleState.onOpenSettings()
                }
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isChecked) activeColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isChecked) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Android 17 Material Outline Icon Box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(iconScale)
                    .background(
                        color = if (isChecked) activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = toggleState.title,
                    tint = if (isChecked) activeColor else inactiveColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title and Live Connection Status Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toggleState.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GoogleSansFlex
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                AnimatedContent(
                    targetState = toggleState.subtitle,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 2 })
                            .togetherWith(fadeOut() + slideOutVertically { -it / 2 })
                    },
                    label = "connectionSubtitle"
                ) { currentSubtitle ->
                    Text(
                        text = currentSubtitle,
                        color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                        fontFamily = GoogleSansFlex,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (toggleState.isActionOnly) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Open Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                // Material 3 Expressive Switch (Tap directly toggles state, does NOT open settings)
                Switch(
                    checked = isChecked,
                    onCheckedChange = { newState ->
                        isChecked = newState
                        toggleState.onToggle(newState)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = activeColor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}
