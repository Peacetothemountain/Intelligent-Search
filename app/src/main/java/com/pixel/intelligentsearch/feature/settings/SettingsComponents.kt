package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.util.clampSafely
import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import com.pixel.intelligentsearch.App
import android.content.Context
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import android.graphics.drawable.Icon
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.*
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.*
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens

// REUSABLE COMPONENTS
// -----------------------------------------------------------------------------------------
/**
 * A group of settings rows, in the matte shell every settings page groups its controls in ([MatteShell]): grainy
 * surface-container tone, soft Material You glow, top rim light and shadow, and the same entrance on every page.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveCornerTokens.ShapeExtraLarge,
    content: @Composable ColumnScope.() -> Unit
) {
    com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
        modifier = modifier,
        shape = shape,
        containerColor = containerColor
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            content()
        }
    }
}

/**
 * The title above a group of settings, in the one style every settings page uses: primary-coloured, bold
 * titleMedium text in Title Case, inset to line up with the rows in the [SettingsCard] below it. It is a heading, so
 * TalkBack can jump from section to section. Titles inside a card are the card's own and don't use this.
 */
@Composable
fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@Composable
fun SettingsRow(
    onLongClick: (() -> Unit)? = null,
    title: String,
    subtitle: String,
    icon: Any?,
    onClick: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier
) {
    val iconBitmap = remember(icon) {
        if (icon is android.graphics.drawable.Drawable) {
            runCatching { icon.toBitmap().asImageBitmap() }.getOrNull()
        } else null
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                }
                .bouncyClickable(
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                when (icon) {
                    is ImageVector -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = null, // decorative: the title is right beside it
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is android.graphics.drawable.Drawable -> {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null, // decorative: the title is right beside it
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    is androidx.compose.ui.graphics.ImageBitmap -> {
                        Image(
                            bitmap = icon,
                            contentDescription = null, // decorative: the title is right beside it
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) 56.dp else 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

/**
 * A setting picked from a menu. [optionLeadingIcon] draws an option's icon, shown beside the current choice under
 * the title and beside each option in the menu; it fills the size it is given.
 */
@Composable
fun SettingsDropdownRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    showDivider: Boolean,
    optionLeadingIcon: (@Composable (String) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.DropdownList
                }
                // bouncyClickable already gives the click haptic.
                .bouncyClickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // decorative: the title is right beside it
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    if (optionLeadingIcon != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(modifier = Modifier.size(18.dp)) { optionLeadingIcon(subtitle) }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null, // the row's DropdownList role already says it opens a menu
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            MaterialTheme(
                shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(24.dp))
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                ) {
                    options.forEach { option ->
                        val isSelected = option == selectedOption
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option,
                                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            leadingIcon = optionLeadingIcon?.let { leading ->
                                { Box(modifier = Modifier.size(24.dp)) { leading(option) } }
                            },
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) 56.dp else 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsRowToggle(
    onLongClick: (() -> Unit)? = null,
    title: String,
    subtitle: String,
    icon: Any? = null,
    customIcon: (@Composable () -> Unit)? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    containerColor: Color = Color.Transparent,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveCornerTokens.ShapeLarge,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val sensoryEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.TactileSonicEngine.get(context) }
    val iconBitmap = remember(icon) {
        if (icon is android.graphics.drawable.Drawable) {
            runCatching { icon.toBitmap().asImageBitmap() }.getOrNull()
        } else null
    }

    val currentIsChecked by rememberUpdatedState(isChecked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val currentOnClick by rememberUpdatedState(onClick)

    // Rows with a sub-page (onClick != null) navigate when the row body is tapped, while the switch
    // itself must always toggle the setting. Both paths share one debounce key so a single tap that
    // reaches both handlers cannot toggle twice.
    val debounceKey = remember { Any() }
    val applyToggle: (Boolean) -> Unit = remember(view, sensoryEngine, debounceKey) {
        { next ->
            if (ClickDebouncer.canClick(120L, debounceKey)) {
                sensoryEngine.toggle(view, next)
                currentOnCheckedChange(next)
            }
        }
    }
    val toggleAction = remember(view, sensoryEngine, applyToggle) {
        {
            val navigate = currentOnClick
            if (navigate != null) {
                navigate()
            } else {
                applyToggle(!currentIsChecked)
            }
        }
    }

    // A row that only toggles is one switch to TalkBack: its title, subtitle and on/off state in a single stop. A row
    // that opens a sub-page is a button, and its switch stays a separate stop named after the row.
    val isPlainToggle = onClick == null
    val rowRole = if (isPlainToggle) Role.Switch else Role.Button

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.PRESS_SCALE_ROW else 1f,
        animationSpec = if (isPressed) {
            com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.Effects.FastSpatial()
        } else {
            com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.Spatial.ExpressiveDefault()
        },
        label = "messages_row_press_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (containerColor != Color.Transparent) {
                    Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (containerColor != Color.Transparent) {
                        Modifier
                            .clip(shape)
                            .background(color = containerColor, shape = shape)
                    } else Modifier
                )
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple(bounded = true),
                            role = rowRole,
                            onLongClick = onLongClick,
                            onClick = toggleAction
                        )
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple(bounded = true),
                            role = rowRole,
                            onClick = toggleAction
                        )
                    }
                )
                .then(
                    if (isPlainToggle) Modifier.semantics { toggleableState = ToggleableState(isChecked) } else Modifier
                )
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (customIcon != null) {
                customIcon()
                Spacer(modifier = Modifier.width(16.dp))
            } else if (icon != null) {
                when (icon) {
                    is ImageVector -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = null, // decorative: the title is right beside it
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is android.graphics.drawable.Drawable -> {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null, // decorative: the title is right beside it
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    is androidx.compose.ui.graphics.ImageBitmap -> {
                        Image(
                            bitmap = icon,
                            contentDescription = null, // decorative: the title is right beside it
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            // On a plain toggle row the switch still takes taps and drags, but TalkBack hears the row instead.
            Box(modifier = if (isPlainToggle) Modifier.clearAndSetSemantics {} else Modifier) {
                com.pixel.intelligentsearch.core.ui.expressive.GoogleMessagesSwitch(
                    checked = isChecked,
                    onCheckedChange = { next -> applyToggle(next) },
                    label = title
                )
            }
        }
        if (showDivider && containerColor == Color.Transparent) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null || customIcon != null) 56.dp else 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.8.dp
            )
        }
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    showDivider: Boolean,
    steps: Int = 0,
    onReset: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // decorative: the title is right beside it
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (onReset != null) {
                        // Material's own IconButton: the click haptic below is this button's one haptic.
                        androidx.compose.material3.IconButton(
                            onClick = {
                                performClickHaptic(context)
                                onReset()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset $title",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Android17Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    steps = steps,
                    label = title,
                    valueDescription = "${value.toInt()}%"
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

/**
 * The "<label> Results: N" block shared by the Contact, File and Web search screens: title with a reset button
 * (back to [defaultCount]) and a 1 to 20 slider. The title uses the settings row title style, like
 * [PrioritySetting] below it.
 */
@Composable
fun ResultsCountSetting(key: com.pixel.intelligentsearch.core.data.settings.SettingKey<Int>, label: String, defaultCount: Int) {
    val context = LocalContext.current
    var count by com.pixel.intelligentsearch.core.data.settings.rememberSetting(key)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label Results: $count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // Material's own IconButton: the click haptic below is this button's one haptic.
            androidx.compose.material3.IconButton(
                onClick = {
                    performClickHaptic(context)
                    count = defaultCount
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = "Reset $label Results",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Android17Slider(
            label = "$label results",
            valueDescription = "$count results",
            value = count.toFloat(),
            onValueChange = { count = it.toInt() },
            valueRange = 1f..20f,
            steps = 18,
            isSquiggly = false,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )
    }
}

@Composable
fun Android17Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    showTrack: Boolean = true,
    isSquiggly: Boolean = true,
    enableHaptics: Boolean = false,
    /** Accessible name; without it TalkBack announces an unlabeled adjustable. */
    label: String? = null,
    /** What TalkBack reads as the value ("Medium", "5 results"); without it TalkBack reads a percentage of the range. */
    valueDescription: String? = null
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    val infiniteTransition = rememberInfiniteTransition(label = "squiggle")
    // Kept as State and read only inside the Canvas below: reading it here recomposed the slider (and its
    // semantics and gesture setup) on every frame for as long as the screen was open.
    val phase: State<Float> = if (isSquiggly && showTrack) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val dragPhaseOffset = remember { mutableFloatStateOf(0f) }

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

    val stepSize = if (steps > 0) {
        (valueRange.endInclusive - valueRange.start) / (steps + 1)
    } else {
        1.0f
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)

    fun snapValue(rawFraction: Float): Float {
        val raw = valueRange.start + rawFraction * (valueRange.endInclusive - valueRange.start)
        return if (steps > 0 && stepSize > 0f) {
            val stepIndex = Math.round((raw - valueRange.start) / stepSize)
            (valueRange.start + stepIndex * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            if (valueRange.endInclusive - valueRange.start >= 10f) {
                Math.round(raw).toFloat().coerceIn(valueRange.start, valueRange.endInclusive)
            } else {
                raw.coerceIn(valueRange.start, valueRange.endInclusive)
            }
        }
    }

    fun processChange(rawFraction: Float) {
        val snapped = snapValue(rawFraction)
        if (snapped != currentValue) {
            currentOnValueChange(snapped)
            if (enableHaptics && steps > 0) {
                hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.TICK)
            }
        }
    }

    // Where along the track a pointer at [x] sits, as a fraction; the thumb's half-width is kept clear at each end.
    fun androidx.compose.ui.input.pointer.PointerInputScope.fractionAt(x: Float): Float {
        val thumbW = 6.dp.toPx()
        val trackAvailableWidth = (size.width - thumbW).coerceAtLeast(1f)
        return ((x - thumbW / 2f) / trackAvailableWidth).coerceIn(0f, 1f)
    }

    val cachedPath = remember { androidx.compose.ui.graphics.Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics(mergeDescendants = true) {
                if (label != null) contentDescription = label
                if (valueDescription != null) stateDescription = valueDescription
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                    steps = steps
                )
                setProgress { targetValue: Float ->
                    val snapped = snapValue(((targetValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f))
                    if (snapped != currentValue) {
                        currentOnValueChange(snapped)
                        true
                    } else false
                }
            }
            // A tap or a sideways drag sets the value. The down event is left unconsumed until the drag passes the
            // horizontal touch slop, so a vertical swipe that starts on the slider still scrolls the page.
            .pointerInput(steps, valueRange) {
                detectTapGestures(onTap = { processChange(fractionAt(it.x)) })
            }
            .pointerInput(steps, valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { processChange(fractionAt(it.x)) },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragPhaseOffset.floatValue += dragAmount * 0.04f
                        processChange(fractionAt(change.position.x))
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 6.dp.toPx()
            val baseAmplitude = 3.5.dp.toPx()
            val waveLength = 32.dp.toPx()
            val transitionLen = 16.dp.toPx()
            val thumbWidth = 6.dp.toPx()
            val thumbHeight = 36.dp.toPx()

            val thumbX = (fraction * (size.width - thumbWidth) + thumbWidth / 2f).clampSafely(thumbWidth / 2f, size.width - thumbWidth / 2f)
            val centerY = size.height / 2f
            val yDots = centerY + 16.dp.toPx()
            val effectivePhase = phase.value + dragPhaseOffset.floatValue

            // Draw active track
            if (showTrack) {
                if (isSquiggly) {
                    cachedPath.rewind()
                    cachedPath.moveTo(0f, centerY)
                    var x = 0f
                    val step = 1.5f
                    while (x <= thumbX) {
                        // Smooth envelope tapering at track start (0) and thumb end (thumbX)
                        val envLeft = if (x < transitionLen) {
                            val t = (x / transitionLen).coerceIn(0f, 1f)
                            t * t * (3f - 2f * t)
                        } else 1f

                        val distToThumb = thumbX - x
                        val envRight = if (distToThumb < transitionLen) {
                            val t = (distToThumb / transitionLen).coerceIn(0f, 1f)
                            t * t * (3f - 2f * t)
                        } else 1f

                        val envelope = envLeft * envRight

                        // Silky unidirectional sine wave without breathing distortion
                        val wave = Math.sin(x * (2.0 * Math.PI / waveLength) - effectivePhase).toFloat()
                        val y = centerY + wave * baseAmplitude * envelope

                        cachedPath.lineTo(x, y)
                        x += step
                    }
                    cachedPath.lineTo(thumbX, centerY)
                    
                    drawPath(
                        path = cachedPath,
                        color = activeColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = trackHeight,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                } else {
                    if (thumbX > 0f) {
                        drawLine(
                            color = activeColor,
                            start = androidx.compose.ui.geometry.Offset(0f, centerY),
                            end = androidx.compose.ui.geometry.Offset(thumbX, centerY),
                            strokeWidth = trackHeight,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
    
                // Draw inactive track (Straight line)
                if (thumbX < size.width) {
                    drawLine(
                        color = inactiveColor,
                        start = androidx.compose.ui.geometry.Offset(thumbX, centerY),
                        end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                        strokeWidth = trackHeight,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            // Draw tick marks (dots underneath track aligned with steps)
            if (steps > 0) {
                val tickRadius = 2.5.dp.toPx()
                val segments = steps + 1
                val availableWidth = size.width - thumbWidth
                val startX = thumbWidth / 2f
                
                for (i in 0..segments) {
                    val cx = startX + i * (availableWidth / segments)
                    drawCircle(
                        color = if (cx <= thumbX + 1f) activeColor.copy(alpha = 0.55f) else inactiveColor.copy(alpha = 0.8f),
                        radius = tickRadius,
                        center = androidx.compose.ui.geometry.Offset(cx, yDots)
                    )
                }
            }

            // Draw thumb (vertical pill)
            drawRoundRect(
                color = thumbColor,
                topLeft = androidx.compose.ui.geometry.Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth / 2f)
            )
        }
    }
}

fun performClickHaptic(context: android.content.Context) {
    com.pixel.intelligentsearch.core.haptics.TactileSonicEngine.get(context).click()
}

/**
 * Auto-size for one-line text with a fixed space: a selector pill, a tab, a title bar. At large text sizes on narrow
 * screens such text would clip or split mid-word; this shrinks it just enough to stay on one line, never below 60%
 * of its size. Where it already fits it keeps its size, so nothing changes there.
 */
fun oneLineAutoSize(style: androidx.compose.ui.text.TextStyle): androidx.compose.foundation.text.TextAutoSize {
    // Steps run up from the minimum, so the minimum sits a whole number of steps below the normal size; otherwise
    // the largest size reachable would fall just short of it and fitting text would render a little small.
    val step = 0.25f
    val max = style.fontSize.value
    val min = max - kotlin.math.floor(max * 0.4f / step) * step
    return androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = min.sp, maxFontSize = max.sp, stepSize = step.sp)
}
