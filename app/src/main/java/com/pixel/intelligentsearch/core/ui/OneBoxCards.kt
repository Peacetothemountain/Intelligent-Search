package com.pixel.intelligentsearch.core.ui

import android.content.ClipData
import com.pixel.intelligentsearch.core.theme.raisedContainer
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.expressive.SquircleShape
import kotlinx.coroutines.delay

/**
 * Pixel Native OneBox Cards.
 * Indistinguishable from Google Search and Pixel Launcher instant answer modules,
 * adhering to Android 18 / Material 3 Expressive guidelines with continuous squircle curvature,
 * fluid color transitions, tactile sensory feedback, dynamic LTPO display frame pacing,
 * chromatic dual-tone border illumination, and 2.5D specular top reflections.
 */

/**
 * Reusable Material 3 Expressive squircle card container for OneBox modules.
 * Supports both static cards and interactive clickable cards with dynamic 120Hz LTPO frame pacing,
 * micro-press scale, elevation depth shift, chromatic lighting borders, and specular top sheens.
 */
@Composable
internal fun ExpressiveOneBoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = ExpressiveCornerTokens.ExtraLarge,
    containerColor: Color = MaterialTheme.colorScheme.raisedContainer,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    pressedBorderColor: Color? = null,
    idleElevation: Dp = 2.dp,
    pressedElevation: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }

    val isClickable = onClick != null
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardShape = remember(cornerRadius) { SquircleShape(cornerRadius) }
    var wasPressed by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(isPressed, isClickable) {
        if (isPressed && isClickable) {
            wasPressed = true
            sensoryEngine.tick(view)
            WindowFramePacing.setDynamicLtpoPacing(view, isMoving = true)
        } else if (wasPressed) {
            wasPressed = false
            WindowFramePacing.setDynamicLtpoPacing(view, isMoving = false)
        }
    }

    val currentOnClick by rememberUpdatedState(onClick)

    val scale by animateFloatAsState(
        targetValue = if (isPressed && isClickable) ExpressiveMotionTokens.PRESS_SCALE_CARD else 1.0f,
        animationSpec = if (isPressed) {
            ExpressiveMotionTokens.Effects.FastSpatial()
        } else {
            ExpressiveMotionTokens.Spatial.ExpressiveDefault()
        },
        label = "oneBoxCard_press_scale"
    )

    val currentElevation by animateDpAsState(
        targetValue = if (isPressed && isClickable) pressedElevation else idleElevation,
        animationSpec = ExpressiveMotionTokens.criticallyDampedSpring(),
        label = "oneBoxCard_elevation"
    )

    val resolvedPressedBorderColor = pressedBorderColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isPressed && isClickable) resolvedPressedBorderColor else borderColor,
        animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
        label = "oneBoxCard_border_color"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val resolvedBorder = remember(isPressed, isClickable, animatedBorderColor, resolvedPressedBorderColor) {
        BorderStroke(
            1.dp,
            if (isPressed && isClickable) {
                resolvedPressedBorderColor.copy(alpha = 0.50f)
            } else {
                animatedBorderColor.copy(alpha = 0.20f)
            }
        )
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = {
                if (ClickDebouncer.canClick(120L)) {
                    sensoryEngine.click(view)
                    currentOnClick?.invoke()
                }
            }
        )
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = currentElevation.toPx()
                shape = cardShape
                clip = true
            }
            .background(containerColor, cardShape)
            .border(resolvedBorder, cardShape)
            .then(clickModifier)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Modern Material 3 Expressive Animated Copy Chip.
 * Transitions smoothly with spring physics between the idle copy state and the confirmed
 * "Copied!" state, triggering tactile sonic feedback, frame rate pacing, and adhering to Android 13+ clipboard standards.
 */
@Composable
fun AnimatedCopyChip(
    textToCopy: String,
    label: String = "Copy",
    copiedLabel: String = "Copied!",
    modifier: Modifier = Modifier,
    clipLabel: String = "Search Result"
) {
    var isCopied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            WindowFramePacing.setDynamicLtpoPacing(view, isMoving = true)
            try {
                delay(2000L)
                isCopied = false
            } finally {
                // Also runs if the card leaves composition mid-delay, so the view never stays at high refresh.
                WindowFramePacing.setDynamicLtpoPacing(view, isMoving = false)
            }
        }
    }

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isCopied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
        label = "copyChipContainerColor"
    )

    val animatedContentColor by animateColorAsState(
        targetValue = if (isCopied) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
        label = "copyChipContentColor"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isCopied) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
        label = "copyChipBorderColor"
    )

    val chipShape = remember { ExpressiveCornerTokens.ShapeFull }
    val chipBorder = remember(animatedBorderColor) {
        BorderStroke(1.dp, animatedBorderColor)
    }

    AssistChip(
        onClick = {
            if (textToCopy.isBlank()) return@AssistChip
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText(clipLabel, textToCopy)
                cm?.setPrimaryClip(clip)
                isCopied = true
                sensoryEngine.click(view)
                // On Android 13+ (API 33+), system displays its own native clipboard popup preview; suppress redundant Toast.
                // The toast names what was copied ("Calculation copied"); the button's own label would read "Copy copied".
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context.applicationContext, "$clipLabel copied", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                android.util.Log.e("OneBoxCards", "Failed to set primary clip", e)
                try {
                    Toast.makeText(context.applicationContext, "Failed to copy to clipboard", Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {}
            }
        },
        label = {
            AnimatedContent(
                targetState = isCopied,
                transitionSpec = {
                    (fadeIn(ExpressiveMotionTokens.Spatial.FastSpatial()) + scaleIn(ExpressiveMotionTokens.Spatial.FastSpatial(), initialScale = 0.85f))
                        .togetherWith(fadeOut(ExpressiveMotionTokens.Spatial.FastSpatial()) + scaleOut(ExpressiveMotionTokens.Spatial.FastSpatial(), targetScale = 0.85f))
                },
                label = "copyChipLabelAnimation"
            ) { copied ->
                Text(
                    text = if (copied) copiedLabel else label,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    color = animatedContentColor
                )
            }
        },
        leadingIcon = {
            AnimatedContent(
                targetState = isCopied,
                transitionSpec = {
                    (fadeIn(ExpressiveMotionTokens.Spatial.FastSpatial()) + scaleIn(ExpressiveMotionTokens.Spatial.FastSpatial(), initialScale = 0.8f))
                        .togetherWith(fadeOut(ExpressiveMotionTokens.Spatial.FastSpatial()) + scaleOut(ExpressiveMotionTokens.Spatial.FastSpatial(), targetScale = 0.8f))
                },
                label = "copyChipIconAnimation"
            ) { copied ->
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = animatedContentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        shape = chipShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = animatedContainerColor,
            labelColor = animatedContentColor,
            leadingIconContentColor = animatedContentColor
        ),
        border = chipBorder,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            role = Role.Button
            contentDescription = if (isCopied) "$clipLabel copied" else "$label, $clipLabel"
        }
    )
}

@Composable
fun MathResultOneBox(
    expression: String,
    result: String,
    modifier: Modifier = Modifier,
    onOpenCalculator: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = remember(context) { TactileSonicEngine.get(context) }
    val calcChipShape = remember { ExpressiveCornerTokens.ShapeFull }
    val calcChipBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val calcChipBorder = remember(calcChipBorderColor) {
        BorderStroke(1.dp, calcChipBorderColor)
    }

    ExpressiveOneBoxCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = ExpressiveCornerTokens.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.raisedContainer,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        idleElevation = 2.dp,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = expression,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = GoogleSansFlex,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = ExpressiveCornerTokens.ShapeFull
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "= $result",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = GoogleSansFlex,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Flows onto a second line when both chips don't fit (narrow phones, large text) instead of squeezing one.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedCopyChip(
                    textToCopy = result,
                    label = "Copy result",
                    copiedLabel = "Copied!",
                    clipLabel = "Calculation"
                )

                if (onOpenCalculator != null) {
                    AssistChip(
                        onClick = {
                            sensoryEngine.click(view)
                            onOpenCalculator()
                        },
                        label = {
                            Text(
                                "Open Calculator",
                                fontFamily = GoogleSansFlex,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = calcChipShape,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = calcChipBorder
                    )
                }
            }
        }
    }
}

/**
 * A unit or currency conversion. [ratesLabel], for currencies, says where the rates come from; [header] names the
 * kind of answer above it, and [clipLabel] names what the Copy chip copies.
 */
@Composable
fun ConversionOneBox(
    conversionText: String,
    modifier: Modifier = Modifier,
    ratesLabel: String? = null,
    header: String = "UNIT CONVERSION",
    clipLabel: String = "Unit Conversion"
) {
    ExpressiveOneBoxCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = ExpressiveCornerTokens.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.raisedContainer,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        idleElevation = 2.dp,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = ExpressiveCornerTokens.ShapeFull
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SyncAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = conversionText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = GoogleSansFlex,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (ratesLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ratesLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = GoogleSansFlex,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedCopyChip(
                textToCopy = conversionText,
                label = "Copy",
                copiedLabel = "Copied!",
                clipLabel = clipLabel
            )
        }
    }
}

@Composable
fun DictionaryOneBox(
    word: String,
    providerName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val iconShape = remember { ExpressiveCornerTokens.ShapeMedium }

    ExpressiveOneBoxCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = ExpressiveCornerTokens.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.raisedContainer,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        pressedBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
        idleElevation = 2.dp,
        pressedElevation = 6.dp,
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = iconShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DICTIONARY DEFINITION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = word.replaceFirstChar { it.uppercase() },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = GoogleSansFlex,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap for definitions on $providerName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = GoogleSansFlex,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun UrlNavigationOneBox(
    url: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cleanUrl = remember(url) {
        url.removePrefix("http://").removePrefix("https://").removeSuffix("/")
    }
    val iconShape = remember { ExpressiveCornerTokens.ShapeMedium }

    ExpressiveOneBoxCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = ExpressiveCornerTokens.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.raisedContainer,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        pressedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        idleElevation = 2.dp,
        pressedElevation = 6.dp,
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = iconShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WEBSITE NAVIGATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanUrl,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = GoogleSansFlex,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to open website",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = GoogleSansFlex,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private enum class OneBoxThemeContainer {
    Primary, Secondary, Tertiary
}

private data class OneBoxCategoryConfig(
    val icon: ImageVector,
    val containerType: OneBoxThemeContainer,
    val label: String
)

@Composable
fun TimeWeatherOneBox(
    title: String,
    subtitle: String,
    iconType: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isWeather = iconType == "weather"
    val isTime = iconType == "time"
    // A date answer's title is a full date ("Sunday, January 17, 2027"), which needs room for two lines.
    val isDate = iconType == "date"

    val categoryConfig = remember(iconType) {
        when {
            isWeather -> OneBoxCategoryConfig(
                icon = Icons.Default.WbSunny,
                containerType = OneBoxThemeContainer.Tertiary,
                label = "WEATHER FORECAST"
            )
            isTime -> OneBoxCategoryConfig(
                icon = Icons.Default.AccessTime,
                containerType = OneBoxThemeContainer.Secondary,
                label = "WORLD CLOCK & TIME"
            )
            isDate -> OneBoxCategoryConfig(
                icon = Icons.Default.CalendarMonth,
                containerType = OneBoxThemeContainer.Secondary,
                label = "DATE CALCULATOR"
            )
            else -> OneBoxCategoryConfig(
                icon = Icons.Outlined.Info,
                containerType = OneBoxThemeContainer.Primary,
                label = "INSTANT ANSWER"
            )
        }
    }

    val iconBoxBg = when (categoryConfig.containerType) {
        OneBoxThemeContainer.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
        OneBoxThemeContainer.Secondary -> MaterialTheme.colorScheme.secondaryContainer
        OneBoxThemeContainer.Primary -> MaterialTheme.colorScheme.primaryContainer
    }
    val iconTint = when (categoryConfig.containerType) {
        OneBoxThemeContainer.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
        OneBoxThemeContainer.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
        OneBoxThemeContainer.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val categoryColor = when (categoryConfig.containerType) {
        OneBoxThemeContainer.Tertiary -> MaterialTheme.colorScheme.tertiary
        OneBoxThemeContainer.Secondary -> MaterialTheme.colorScheme.secondary
        OneBoxThemeContainer.Primary -> MaterialTheme.colorScheme.primary
    }

    val iconShape = remember { ExpressiveCornerTokens.ShapeMedium }

    ExpressiveOneBoxCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = ExpressiveCornerTokens.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.raisedContainer,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        pressedBorderColor = categoryColor.copy(alpha = 0.55f),
        idleElevation = 2.dp,
        pressedElevation = 6.dp,
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = iconBoxBg,
                        shape = iconShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryConfig.icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryConfig.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColor,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = GoogleSansFlex,
                    maxLines = if (isDate) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = GoogleSansFlex,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
