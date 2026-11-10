package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.search.today.cards.SnapModel
import java.text.NumberFormat

/*
 * What the app and website box designs share: drawing in, the Private cover's fade, the small label, the big number
 * that counts up, and the app's icon.
 *
 * NG Designs
 */

/** The box designs' big number, as the approved design sets it (30 at 1.1 line height). */
@Composable
internal fun bigStyle(): TextStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 33.sp)

/** The box designs' small secondary line. */
@Composable
internal fun smallStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp)

/** The box designs' small capital label ("GOOGLE FINANCE"). */
@Composable
internal fun labelStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)

/**
 * A box design drawing in: from transparent, 14 dp low and a little small, with a slight overshoot, over 0.7 seconds,
 * once per [key] and only once [start] (at once with animations off). Read in the layer, so it never recomposes.
 */
internal fun Modifier.boxDrawIn(key: Any, start: Boolean): Modifier = composed {
    val animate by rememberAnimationsEnabled()
    val shown = remember(key) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(shown, animate, start) {
        when {
            !animate -> shown.snapTo(1f)
            start -> shown.animateTo(1f, tween(700, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.15f)))
        }
    }
    graphicsLayer {
        val p = shown.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 14.dp.toPx()
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}

/**
 * The Private cover's opacity for a card [covered] or not: 1 at once when covered (nothing private ever shows), fading
 * to 0 over a quarter second when uncovered (at once with animations off).
 */
@Composable
internal fun rememberCoverFade(covered: Boolean): Animatable<Float, *> {
    val animate by rememberAnimationsEnabled()
    val cover = remember { Animatable(if (covered) 1f else 0f) }
    LaunchedEffect(covered, animate) {
        when {
            covered -> cover.snapTo(1f)
            animate -> cover.animateTo(0f, tween(250))
            else -> cover.snapTo(0f)
        }
    }
    return cover
}

/**
 * A box design's content under the Private cover: [content] while any of it can show, the cover over it while any
 * of the cover shows. Recomposes only as the cover starts or ends, never on each frame of its fade.
 */
@Composable
internal fun CoveredBox(covered: Boolean, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, content: @Composable () -> Unit) {
    val cover = rememberCoverFade(covered)
    val hidden by remember(cover) { derivedStateOf { cover.value >= 1f } }
    val showing by remember(cover) { derivedStateOf { cover.value > 0f } }
    Box(modifier) {
        if (!hidden) content()
        if (showing) PrivateCover(shape, Modifier.matchParentSize().graphicsLayer { alpha = cover.value })
    }
}

/**
 * One of the page's own cards under the Private cover. Unlike [CoveredBox], [content] stays laid out while covered (a
 * full card is as tall as its content, and the cover takes that size), but none of it is drawn, TalkBack hears none of
 * it, and no touch or key reaches it: a tap is the card's own, to show it.
 */
@Composable
internal fun CoveredCard(covered: Boolean, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, content: @Composable () -> Unit) {
    val cover = rememberCoverFade(covered)
    val hidden by remember(cover) { derivedStateOf { cover.value >= 1f } }
    val showing by remember(cover) { derivedStateOf { cover.value > 0f } }
    Box(modifier, propagateMinConstraints = true) {
        // A group that refuses focus: turning focus off would reach only the first button down, not the ones inside it.
        val under = if (hidden) {
            Modifier.drawWithContent { }.clearAndSetSemantics { }.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup()
        } else Modifier
        Box(under, propagateMinConstraints = true) { content() }
        if (showing) {
            PrivateCover(
                shape,
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = cover.value }
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            )
        }
    }
}

/**
 * The hero's number, counting to the widget's [SnapModel.number] from its number before the update (or 0) once
 * [start], then showing the widget's own text. Only this text recomposes as the shown number changes.
 */
@Composable
internal fun CountText(model: SnapModel, format: NumberFormat?, start: Boolean, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val animate by rememberAnimationsEnabled()
    val to = model.number
    val last = remember { mutableStateOf<Double?>(null) }
    val from = remember(to) { model.from ?: last.value ?: 0.0 }
    val progress = remember(to) { Animatable(if (animate && to != null && to != last.value) 0f else 1f) }
    LaunchedEffect(progress, animate, start) {
        when {
            !animate -> progress.snapTo(1f)
            start && progress.value < 1f -> progress.animateTo(1f, tween(900, delayMillis = if (last.value == null) 150 else 0, easing = LinearEasing))
        }
        last.value = to
    }
    val text by remember(model, format) {
        derivedStateOf {
            val p = progress.value
            if (to == null || p >= 1f) model.hero else snapNumber(model, from + (to - from) * easeOut(p), format)
        }
    }
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** The app's icon in a circle [size] across, or a dot of its [accent] when there is none. */
@Composable
internal fun AppIcon(icon: ImageBitmap?, accent: Color, size: Dp, modifier: Modifier = Modifier) {
    if (icon != null) {
        Image(icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape))
    } else {
        Box(modifier.size(size).clip(CircleShape).background(accent.copy(alpha = 0.3f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Box(Modifier.size(size / 2).clip(CircleShape).background(accent))
        }
    }
}
