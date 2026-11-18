package com.pixel.intelligentsearch.feature.tabs.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// The controls every tab shares, so Settings, Gallery and Wellbeing press, vibrate, move and read as one app.

/**
 * The M3 Expressive springs everything moves on (Intelligent Search's ExpressiveMotionTokens). Spatial ones overshoot
 * a touch; effects ones don't. Nothing in the preview moves on a timed curve, and nothing fades.
 */
internal object Springs {
    /** Spatial default: pages, the pill, sheets, cards opening and closing. */
    fun <T> spatial(visibilityThreshold: T? = null): SpringSpec<T> = spring(0.8f, 380f, visibilityThreshold)

    /** Fast spatial: indicators, a press's shape morph. */
    fun <T> fast(visibilityThreshold: T? = null): SpringSpec<T> = spring(0.6f, 800f, visibilityThreshold)

    /** Slow spatial: large things travelling far. */
    fun <T> slowSpatial(visibilityThreshold: T? = null): SpringSpec<T> = spring(0.8f, 200f, visibilityThreshold)

    /** Effects (colors, no overshoot). */
    fun <T> effects(visibilityThreshold: T? = null): SpringSpec<T> = spring(1f, 1600f, visibilityThreshold)
    fun <T> fastEffects(visibilityThreshold: T? = null): SpringSpec<T> = spring(1f, 3800f, visibilityThreshold)

    /** Shape morphs. */
    fun <T> morph(visibilityThreshold: T? = null): SpringSpec<T> = spring(0.74f, 420f, visibilityThreshold)
    fun <T> bouncy(visibilityThreshold: T? = null): SpringSpec<T> = spring(0.68f, 460f, visibilityThreshold)

    /** Critically damped: things leaving or settling without a bounce (a snackbar going, a card closing up). */
    fun <T> damped(visibilityThreshold: T? = null): SpringSpec<T> = spring(1f, 700f, visibilityThreshold)
}

/**
 * Whether animations are on (Settings > Accessibility > Remove animations off), following changes as they happen, as
 * the app's ReducedMotion.kt does. With them off the glow stands still and presses don't morph.
 */
@Composable
internal fun rememberAnimationsEnabled(): State<Boolean> = com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled()

/**
 * The app's button behavior, the one every tappable thing in every tab uses (Intelligent Search's bouncyClickable): the
 * theme's ripple, Android 17's Material 3 Expressive press ([expressivePress]) on [shape], and the app's one vibration
 * ([rememberTick]) on touch down and again when the tap lands.
 *
 * [shape] is the control's resting shape: content after this modifier is clipped to it. Null is a square-cornered row
 * whose corners round while held.
 */
internal fun Modifier.bouncyClickable(
    shape: Shape? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    ripple: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val tick = rememberTick()
    LaunchedEffect(pressed) { if (pressed && enabled) tick() }
    val currentOnClick by rememberUpdatedState(onClick)
    this
        .expressivePress(interactions, shape, enabled)
        .clickable(
            interactionSource = interactions,
            indication = if (ripple) ripple() else null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = {
                tick()
                currentOnClick()
            }
        )
}

/**
 * The press of Android 17's Material 3 Expressive controls: nothing shrinks; a rounded control's corners tighten on the
 * fast spatial spring while held and spring back on release, and a square-cornered row rounds its corners so its
 * pressed highlight reads as a rounded key. With animations off the shape simply stays at rest.
 */
internal fun Modifier.expressivePress(interactionSource: InteractionSource, rest: Shape?, enabled: Boolean = true): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val animate by rememberAnimationsEnabled()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pressed, enabled, animate) {
        val target = if (pressed && enabled && animate) 1f else 0f
        progress.animateTo(target, if (target > 0f) Springs.fast() else Springs.spatial())
    }
    val restShape = rest ?: RectangleShape
    graphicsLayer {
        val p = progress.value
        shape = if (p == 0f) restShape else PressedShape(restShape, p)
        clip = rest != null || p != 0f
    }
}

private class PressedShape(private val rest: Shape, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val maxR = minOf(size.width, size.height) / 2f
        if (rest is CornerBasedShape) {
            val keep = (1f - 0.35f * progress).coerceIn(0f, 1.2f)
            fun r(c: CornerSize) = CornerRadius((c.toPx(size, density) * keep).coerceIn(0f, maxR))
            return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, r(rest.topStart), r(rest.topEnd), r(rest.bottomEnd), r(rest.bottomStart)))
        }
        if (rest == RectangleShape) {
            val c = CornerRadius((with(density) { 20.dp.toPx() } * progress.coerceIn(0f, 1.2f)).coerceIn(0f, maxR))
            return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, c, c, c, c))
        }
        return rest.createOutline(size, layoutDirection, density)
    }
}

/** The buttons' kinds: M3's filled, tonal, text and outlined. */
internal enum class ButtonKind { Filled, Tonal, Text, Outlined }

/**
 * The preview's button, the same in every tab: M3 Expressive, fully round, 40 dp in a 48 dp touch target ([tall]: 56
 * dp), filled, tonal, text or outlined, with an optional icon before or after its label.
 */
@Composable
internal fun PreviewButton(
    text: String,
    kind: ButtonKind,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    container: Color? = null,
    content: Color? = null,
    textStyle: TextStyle? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg = container ?: when (kind) {
        ButtonKind.Filled -> scheme.primary
        ButtonKind.Tonal -> scheme.secondaryContainer
        ButtonKind.Text, ButtonKind.Outlined -> Color.Transparent
    }
    val fg = content ?: when (kind) {
        ButtonKind.Filled -> scheme.onPrimary
        ButtonKind.Tonal -> scheme.onSecondaryContainer
        ButtonKind.Text, ButtonKind.Outlined -> scheme.primary
    }
    val h = if (tall) 56.dp else 40.dp
    val shape = RoundedCornerShape(h / 2)
    Box(
        modifier
            .padding(vertical = if (tall) 0.dp else 4.dp)
            .defaultMinSize(minWidth = if (kind == ButtonKind.Text) 48.dp else 76.dp, minHeight = h)
            .bouncyClickable(shape, enabled = enabled) { onClick() }
            .background(bg)
            .then(if (kind == ButtonKind.Outlined) Modifier.border(1.dp, scheme.outlineVariant, shape) else Modifier)
            .then(if (contentDescription != null) Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription } else Modifier)
            .padding(horizontal = if (kind == ButtonKind.Text) 12.dp else if (tall) 24.dp else 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (enabled) fg else fg.copy(alpha = 0.38f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(
                text,
                color = tint,
                style = textStyle ?: if (tall) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                fontWeight = if (textStyle != null) null else if (kind == ButtonKind.Filled && tall) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            if (trailingIcon != null) Icon(trailingIcon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * An icon button, the same in every tab and in every top bar: [size] (40 dp) round in a 48 dp target, the icon at 24
 * dp in on-surface-variant (a top bar passes its own content color), its corners tightening while pressed.
 */
@Composable
internal fun PreviewIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = Color.Transparent,
    size: Dp = 40.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(modifier.size(maxOf(size, 48.dp)), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .bouncyClickable(RoundedCornerShape(size / 2), enabled = enabled, onClick = onClick)
                .background(container)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(24.dp))
        }
    }
}
