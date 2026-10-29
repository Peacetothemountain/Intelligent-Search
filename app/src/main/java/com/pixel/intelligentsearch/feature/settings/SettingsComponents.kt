package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.util.clampSafely
import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.draw.*
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.*
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.core.theme.pageContainer
import com.pixel.intelligentsearch.core.ui.expressive.expressivePress

// REUSABLE COMPONENTS
// -----------------------------------------------------------------------------------------
/**
 * A group of settings rows: a flat card in the page's container color ([pageContainer]: surfaceContainer in dark,
 * the light palette's near-white on the light theme's tinted page) with a faint outline-variant hairline, drawn as a
 * matte shell ([MatteShell]), so it keeps exactly that colour and only gains the matte grain on its surface.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    // A page's main cards, the ones holding its settings, have the light behind them; a card that only holds a
    // selector (a design switch, an icon picker) doesn't.
    lit: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
        // Not clipped here: the shell clips its own content, and the light behind it has to show around the edge.
        modifier = modifier,
        shape = ExpressiveCornerTokens.ShapeExtraLarge,
        containerColor = MaterialTheme.colorScheme.pageContainer,
        border = com.pixel.intelligentsearch.core.ui.expressive.MatteShellDefaults.border(),
        lit = lit
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
/**
 * Rows attached under a switch in the same card ([visible] while it is on): they drop down out of it as it turns on
 * and rise back up into it as it turns off, on [ExpressiveMotionTokens.Reveal]'s quick, settled motion. Every switch
 * with rows of its own uses this, so they all move alike.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.AttachedRows(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = ExpressiveMotionTokens.Reveal.enter,
        exit = ExpressiveMotionTokens.Reveal.exit,
        content = content
    )
}

/**
 * A settings page's top bar colors: none of its own, so the page and its glow run unbroken from the top of the screen
 * down, and the bar never shows as a band cut off from the page it heads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun settingsTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)

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
    title: String,
    subtitle: String,
    icon: Any? = null,
    customIcon: (@Composable () -> Unit)? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
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
                sensoryEngine.toggle(view)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Android 17's press: the row's shape morphs on the fast spatial spring instead of shrinking.
                .expressivePress(interactionSource, null)
                .then(
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.material3.ripple(bounded = true),
                        role = rowRole,
                        onClick = toggleAction
                    )
                )
                .then(
                    if (isPlainToggle) Modifier.semantics { toggleableState = ToggleableState(isChecked) } else Modifier
                )
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
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null || customIcon != null) 56.dp else 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.8.dp
            )
        }
    }
}

/** How far a [SettingsAppRow]'s pill highlight sits in from its container's sides. */
private val APP_ROW_HIGHLIGHT_INSET = 8.dp

/** The shape of a [SettingsAppRow]'s selection highlight and press ripple: a pill. */
internal val AppRowHighlightShape = RoundedCornerShape(percent = 50)

/**
 * An app in a list of apps (Application Search's picker): drawn as Manage Hidden Apps draws its rows ([SettingsRowToggle]:
 * the app's icon, its name and a line under it, the same press and hairline), with no switch, since tapping the row is
 * the action. [selected] tints it while it is picked in multi-select.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsAppRow(
    title: String,
    subtitle: String,
    icon: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    showDivider: Boolean = true
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(modifier = modifier.fillMaxWidth()) {
        // The highlight and ripple are a pill inset from the rounded container's edges, never a full-bleed rectangle.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = APP_ROW_HIGHLIGHT_INSET)
                .expressivePress(interactionSource, AppRowHighlightShape)
                .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(bounded = true),
                    role = Role.Button,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .semantics { this.selected = selected }
                .padding(horizontal = 16.dp - APP_ROW_HIGHLIGHT_INSET, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (icon) {
                is ImageVector -> Icon(
                    imageVector = icon,
                    contentDescription = null, // decorative: the title is right beside it
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                is androidx.compose.ui.graphics.ImageBitmap -> Image(
                    bitmap = icon,
                    contentDescription = null, // decorative: the title is right beside it
                    modifier = Modifier.size(24.dp)
                )
                else -> Spacer(modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
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
            // Selection shows as a check too, not by the tint alone, which some people can't tell apart.
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = androidx.compose.animation.scaleIn(initialScale = 0.6f) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut(targetScale = 0.6f) + androidx.compose.animation.fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null, // the row says it's selected
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp).size(24.dp)
                )
            }
        }
        // The highlight is its own pill: a divider under a selected row would cut across it.
        if (showDivider && !selected) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.8.dp
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    showDivider: Boolean,
    steps: Int = 0,
    onReset: (() -> Unit)? = null,
    /** The value as shown beside the title and read by TalkBack ("8 dp"); without it TalkBack reads a percentage. */
    valueText: ((Float) -> String)? = null
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
                // The value (and reset) sit beside the title when they fit, and move under it when large text or a
                // narrow window leaves no room, so neither is ever clipped (see TitleWithTrailing).
                TitleWithTrailing {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    if (valueText != null) {
                        Text(
                            text = valueText(value),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
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
                }
                Android17Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    steps = steps,
                    label = title,
                    valueDescription = valueText?.invoke(value) ?: "${value.toInt()}%"
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
    /** Accessible name; without it TalkBack announces an unlabeled adjustable. */
    label: String? = null,
    /** What TalkBack reads as the value ("Medium", "5 results"); without it TalkBack reads a percentage of the range. */
    valueDescription: String? = null,
    /** Called once a tap, a drag or an accessibility adjustment has finished setting the value. */
    onValueChangeFinished: (() -> Unit)? = null
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
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
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
                        currentOnValueChangeFinished?.invoke()
                        true
                    } else false
                }
            }
            // A tap or a sideways drag sets the value. The down event is left unconsumed until the drag passes the
            // horizontal touch slop, so a vertical swipe that starts on the slider still scrolls the page.
            .pointerInput(steps, valueRange) {
                detectTapGestures(onTap = {
                    processChange(fractionAt(it.x))
                    currentOnValueChangeFinished?.invoke()
                })
            }
            .pointerInput(steps, valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { processChange(fractionAt(it.x)) },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragPhaseOffset.floatValue += dragAmount * 0.04f
                        processChange(fractionAt(change.position.x))
                    },
                    onDragEnd = { currentOnValueChangeFinished?.invoke() },
                    onDragCancel = { currentOnValueChangeFinished?.invoke() }
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

/** The app's one haptic for a click with no view to hand; the vibrator plays it as a touch vibration. */
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

/**
 * A title and what trails it (a value, a reset button): side by side, the trailing part at the end, when both fit
 * at their full size; otherwise the trailing part on its own line under the title. Unlike a flow row, the trailing
 * part is always measured against the whole row, never squeezed into what's left beside the title.
 */
@Composable
private fun TitleWithTrailing(content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // Measured once, at the row's full width: never squeezed into the space beside the title.
        val trailing = measurables.getOrNull(1)?.measure(loose)
        val trailingWidth = trailing?.width ?: 0
        val gap = 8.dp.roundToPx()
        val sideBySide = trailing == null || measurables[0].maxIntrinsicWidth(loose.maxHeight) + gap + trailingWidth <= constraints.maxWidth
        val title = measurables[0].measure(
            if (sideBySide && trailing != null) loose.copy(maxWidth = (constraints.maxWidth - gap - trailingWidth).coerceAtLeast(0)) else loose
        )
        val width = constraints.maxWidth
        if (sideBySide) {
            val height = maxOf(title.height, trailing?.height ?: 0)
            layout(width, height) {
                title.placeRelative(0, (height - title.height) / 2)
                trailing?.placeRelative(width - trailingWidth, (height - trailing.height) / 2)
            }
        } else {
            // The trailing part on its own line.
            layout(width, title.height + trailing.height) {
                title.placeRelative(0, 0)
                trailing.placeRelative(0, title.height)
            }
        }
    }
}
