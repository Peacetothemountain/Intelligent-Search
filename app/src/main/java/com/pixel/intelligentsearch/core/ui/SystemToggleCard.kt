package com.pixel.intelligentsearch.core.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens

@androidx.compose.runtime.Immutable
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

/**
 * Modern Material 3 Expressive System Toggle Card.
 * Conforms to Android 17 (Canary ZP11) and Pixel Quick Settings design guidelines:
 * - Continuous squircle corner geometry (ExpressiveCornerTokens.ShapeExtraLarge)
 * - 120Hz/144Hz LTPO dynamic display frame pacing via WindowFramePacing
 * - Material 3 Expressive spring physics with tactile sonic feedback
 * - Fluid color transitions without chromatic aberration
 * - Single-node accessible TalkBack semantics preventing dual-focus conflicts
 */
@Composable
fun SystemToggleCard(
    toggleState: SystemToggleUiState,
    modifier: Modifier = Modifier
) {
    var isChecked by remember(toggleState.id) { mutableStateOf(toggleState.isEnabled) }

    // Reconcile optimistic local state whenever external system service state changes
    LaunchedEffect(toggleState.isEnabled) {
        isChecked = toggleState.isEnabled
    }
    // Wi-Fi (and Bluetooth on 13+) only open a system panel. If it is closed without a change the state above
    // never changes, so drop the optimistic flip when the screen comes back.
    androidx.lifecycle.compose.LifecycleResumeEffect(toggleState.isEnabled) {
        isChecked = toggleState.isEnabled
        onPauseOrDispose { }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var wasPressed by remember { mutableStateOf(false) }

    // 120Hz / 144Hz LTPO OLED Dynamic Refresh Rate Pacing
    LaunchedEffect(isPressed) {
        if (isPressed) {
            wasPressed = true
            sensoryEngine.tick(view)
            WindowFramePacing.setDynamicLtpoPacing(view, isMoving = true)
        } else if (wasPressed) {
            wasPressed = false
            WindowFramePacing.setDynamicLtpoPacing(view, isMoving = false)
        }
    }

    // Material 3 Expressive Card Press Scale Micro-Physics
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) ExpressiveMotionTokens.PRESS_SCALE_CARD else 1.0f,
        animationSpec = if (isPressed) {
            ExpressiveMotionTokens.Effects.FastSpatial()
        } else {
            ExpressiveMotionTokens.Spatial.ExpressiveDefault()
        },
        label = "systemToggleCardScale"
    )

    // Tactile Icon Scale Elasticity
    val iconScale by animateFloatAsState(
        targetValue = if (isChecked) 1.15f else 1.0f,
        animationSpec = ExpressiveMotionTokens.Spatial.FastSpatial(),
        label = "systemToggleIconScale"
    )

    val iconVector: ImageVector = remember(toggleState.iconType, isChecked) {
        when (toggleState.iconType) {
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
    }

    // Material 3 Expressive Liquid Color Transitions
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isChecked) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
        label = "toggleCardContainerColor"
    )

    val targetBorderColor = when {
        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        isChecked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
        label = "toggleCardBorderColor"
    )

    val animatedIconBgColor by animateColorAsState(
        targetValue = if (isChecked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
        label = "toggleIconBgColor"
    )

    val animatedIconTint by animateColorAsState(
        targetValue = if (isChecked) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
        label = "toggleIconTint"
    )

    // Spatial Elevation Depth Shift
    val targetElevation = when {
        isPressed -> 6.dp
        isChecked -> 3.dp
        else -> 1.dp
    }
    val animatedElevation by animateDpAsState(
        targetValue = targetElevation,
        animationSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
        label = "toggleCardElevation"
    )

    val stateDesc = if (toggleState.isActionOnly) "Setting" else if (isChecked) "On" else "Off"

    // Derive dynamic subtitle synchronized with live toggle state to prevent visual contradiction
    val dynamicSubtitle = remember(toggleState.id, toggleState.subtitle, isChecked) {
        if (toggleState.isActionOnly) {
            toggleState.subtitle
        } else {
            when (toggleState.iconType) {
                "flashlight" -> if (isChecked) "Flashlight is on" else "Flashlight is off"
                "bluetooth" -> if (isChecked) "Bluetooth is on" else "Bluetooth is off"
                "dnd" -> if (isChecked) "Do Not Disturb is on • Calls & Alerts silenced" else "Do Not Disturb is off"
                "darkmode" -> if (isChecked) "Dark theme is active" else "Light theme is active"
                "autorotate" -> if (isChecked) "Auto-rotate is on (Portrait & Landscape)" else "Portrait orientation locked"
                "sound" -> if (isChecked) "Sound is muted" else "Sound is active"
                else -> toggleState.subtitle
            }
        }
    }

    val cardShape = remember { ExpressiveCornerTokens.ShapeExtraLarge }
    val cardBorder = remember(animatedBorderColor) {
        BorderStroke(1.dp, animatedBorderColor)
    }

    // Unified single-target semantics for accessibility
    val accessibilityModifier = if (toggleState.isActionOnly) {
        Modifier.semantics {
            role = Role.Button
            stateDescription = stateDesc
        }
    } else {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(isChecked)
            stateDescription = stateDesc
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(accessibilityModifier)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                shadowElevation = animatedElevation.toPx()
                shape = cardShape
                clip = true
            }
            .background(animatedContainerColor, cardShape)
            .border(cardBorder, cardShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (toggleState.isActionOnly) {
                        sensoryEngine.click(view)
                        toggleState.onOpenSettings()
                    } else {
                        val newState = !isChecked
                        isChecked = newState
                        sensoryEngine.toggle(view)
                        toggleState.onToggle(newState)
                    }
                },
                onLongClick = {
                    sensoryEngine.click(view)
                    toggleState.onOpenSettings()
                }
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Android 17 Material Outline Icon Box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                    .background(
                        color = animatedIconBgColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null, // Semantics covered by parent card
                    tint = animatedIconTint,
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
                    targetState = dynamicSubtitle,
                    transitionSpec = {
                        (fadeIn(ExpressiveMotionTokens.Spatial.FastSpatial()) + slideInVertically(ExpressiveMotionTokens.Spatial.FastSpatial()) { it / 2 })
                            .togetherWith(fadeOut(ExpressiveMotionTokens.Spatial.FastSpatial()) + slideOutVertically(ExpressiveMotionTokens.Spatial.FastSpatial()) { -it / 2 })
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
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                // Google Messages 20261002 Material 3 Expressive Switch
                com.pixel.intelligentsearch.core.ui.expressive.GoogleMessagesSwitch(
                    checked = isChecked,
                    onCheckedChange = { newState ->
                        isChecked = newState
                        sensoryEngine.toggle(view)
                        toggleState.onToggle(newState)
                    },
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
        }
    }
}

/**
 * Inline volume card for "volume", "ring volume" and "alarm volume" queries. The slider moves the
 * stream level directly; [onVolumeChange] applies each step to AudioManager.
 */
@Composable
fun VolumeSliderCard(
    title: String,
    streamType: Int,
    currentLevel: Int,
    maxLevel: Int,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }
    val safeMax = maxLevel.coerceAtLeast(1)
    var level by remember(streamType) { mutableFloatStateOf(currentLevel.coerceIn(0, safeMax).toFloat()) }
    LaunchedEffect(currentLevel, safeMax) {
        level = currentLevel.coerceIn(0, safeMax).toFloat()
    }
    val icon = when (streamType) {
        android.media.AudioManager.STREAM_RING -> Icons.Outlined.NotificationsActive
        android.media.AudioManager.STREAM_ALARM -> Icons.Outlined.Alarm
        else -> if (level <= 0f) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = ExpressiveCornerTokens.ShapeExtraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // The slider below carries the title as its name, so TalkBack reads it once, with the level.
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GoogleSansFlex,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Slider(
                    value = level,
                    onValueChange = { newValue ->
                        val stepped = newValue.roundToInt().coerceIn(0, safeMax)
                        if (stepped != level.toInt()) {
                            sensoryEngine.tick(view)
                            onVolumeChange(stepped)
                        }
                        level = stepped.toFloat()
                    },
                    valueRange = 0f..safeMax.toFloat(),
                    steps = (safeMax - 1).coerceAtLeast(0),
                    modifier = Modifier.semantics {
                        contentDescription = title
                        stateDescription = "${level.toInt()} of $safeMax"
                    }
                )
            }
        }
    }
}
