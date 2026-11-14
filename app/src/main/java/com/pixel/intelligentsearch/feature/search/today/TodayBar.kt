package com.pixel.intelligentsearch.feature.search.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import com.pixel.intelligentsearch.core.ui.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import com.pixel.intelligentsearch.feature.settings.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import kotlinx.coroutines.delay
import com.pixel.intelligentsearch.feature.search.today.cards.grid.EditIcons
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.CornerRadius
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.feature.settings.drawSearchPill
import com.pixel.intelligentsearch.feature.settings.SearchEasing
import com.pixel.intelligentsearch.feature.settings.SEARCH_MILLIS
import com.pixel.intelligentsearch.feature.settings.PILL_TEXT
import com.pixel.intelligentsearch.feature.settings.PILL_SIDE
import com.pixel.intelligentsearch.feature.settings.PILL_MARGIN
import com.pixel.intelligentsearch.feature.settings.PILL_HEIGHT
import com.pixel.intelligentsearch.feature.settings.FlashDecay
import com.pixel.intelligentsearch.feature.settings.FLASH_MILLIS
import kotlin.math.roundToInt

/*
 * Discover's bar at the bottom of the page, and the button that starts editing it.
 *
 * NG Designs
 */

/** How long the bar stays full before it goes down to Back and Settings. */
private const val BAR_SHRINK_DELAY_MS = 1_500L

/** The compact bar: Back and Settings, each a 48 dp button 12 dp in from its end. */
private val BAR_COMPACT_WIDTH = 120.dp
private val BAR_BUTTON_INSET = 12.dp
private val BAR_BUTTON = 48.dp

/**
 * Today's bar, in the look of the search pill on Manage Hidden Apps and Add Applications ([BottomSearchPill]): the
 * same body color, 72 dp, 14 dp from the sides, 20 sp text, no shadow. It is there as the page opens ("Back to
 * Search", the back arrow and the Settings bars), then after a moment goes down the way that pill closes: its right end dissolves back toward the left, the light leading it and
 * Settings riding it, until only Back and Settings are left, where it flashes again. Same timing and curve as the
 * search pill; at once with animations off. A tap on it while full, or on Back, returns to the search; Settings opens
 * Today's settings.
 */
@Composable
internal fun TodayBar(onBack: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val animate by rememberAnimationsEnabled()
    // The bar is there as the page opens; only its going down to Back and Settings is animated.
    val reveal = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    val shrink = remember { Animatable(0f) }
    val landing = remember { Animatable(0f) }
    // Whether the full bar still takes a tap: read here, the shrink would rebuild the bar on every frame of it.
    val tappable by remember { derivedStateOf { shrink.value < 0.5f } }
    LaunchedEffect(animate) {
        delay(BAR_SHRINK_DELAY_MS)
        if (!animate) {
            shrink.snapTo(1f)
            return@LaunchedEffect
        }
        shrink.animateTo(1f, tween(SEARCH_MILLIS, easing = SearchEasing))
        landing.snapTo(1f)
        landing.animateTo(0f, tween(FLASH_MILLIS, easing = FlashDecay))
    }
    val body = scheme.surfaceVariant
    val light = scheme.primary
    val onPill = scheme.onSurfaceVariant
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(start = PILL_SIDE, end = PILL_SIDE, bottom = PILL_MARGIN)
            .height(PILL_HEIGHT)
    ) {
        val fullPx = with(density) { maxWidth.toPx() }
        val compactPx = with(density) { BAR_COMPACT_WIDTH.toPx() }
        // How far the pill reaches from its start (the left), as it shrinks.
        fun edge(s: Float) = fullPx - s * (fullPx - compactPx)
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val s = shrink.value
                    when {
                        s <= 0f -> drawSearchPill(size, reveal.value, fade.value, flash.value, body, light, mirrored = false)
                        s < 1f -> {
                            // The compact pill firming up as the edge comes back to it.
                            drawRoundRect(
                                body,
                                size = Size(compactPx, size.height),
                                cornerRadius = CornerRadius(size.height / 2f),
                                alpha = smoothStep(0.6f, 1f, s)
                            )
                            drawSearchPill(size, edge(s) / fullPx, 1f, 0f, body, light, mirrored = true)
                        }
                        else -> drawSearchPill(Size(compactPx, size.height), 1f, 1f, landing.value, body, light, mirrored = true)
                    }
                }
                .then(
                    if (tappable) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures {
                                PixelHapticEngine.playButtonTap(view.context, view)
                                onBack()
                            }
                        }
                    } else Modifier
                )
        )
        val formed = { fade.value * smoothStep(0.82f, 1f, reveal.value) }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = BAR_BUTTON_INSET)
                .size(BAR_BUTTON)
                .graphicsLayer { alpha = formed() }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Search", tint = onPill)
        }
        Text(
            "Back to Search",
            color = onPill,
            fontSize = PILL_TEXT,
            fontFamily = GoogleSansFlex,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = BAR_BUTTON_INSET + BAR_BUTTON + 4.dp)
                .graphicsLayer { alpha = formed() * (1f - smoothStep(0f, 0.35f, shrink.value)) }
                // The back button beside it has the same name; read here too, and after the bar shrinks, it would be said twice.
                .clearAndSetSemantics {}
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    // At the pill's right end, riding its edge as it shrinks.
                    val x = edge(shrink.value) - (BAR_BUTTON_INSET + BAR_BUTTON).toPx()
                    IntOffset(x.roundToInt(), 0)
                }
                .size(BAR_BUTTON)
                .graphicsLayer { alpha = formed() }
        ) {
            Icon(painterResource(R.drawable.ic_three_bars), contentDescription = "Discover settings", tint = onPill)
        }
    }
}

/**
 * Edit Discover: a round button at the bottom right, in the bar's body color, that comes in as the bar goes down to
 * Back and Settings (with animations off, when the bar does: sooner it would sit on the full bar's Settings button)
 * and steps aside while editing.
 */
@Composable
internal fun EditButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BAR_SHRINK_DELAY_MS + if (animate) SEARCH_MILLIS / 2 else 0)
        ready = true
    }
    val shown by animateFloatAsState(
        if (visible && ready) 1f else 0f,
        if (animate) spring(dampingRatio = 0.7f, stiffness = 500f) else snap(),
        label = "edit button"
    )
    if (shown <= 0f && !(visible && ready)) return
    Box(
        modifier.padding(end = PILL_SIDE, bottom = PILL_MARGIN).height(PILL_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(56.dp)
                .graphicsLayer {
                    alpha = shown.coerceIn(0f, 1f)
                    val s = 0.7f + 0.3f * shown
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(scheme.surfaceVariant)
                .bouncyClickable(shape = CircleShape, onClick = onClick)
                .semantics { contentDescription = "Edit Discover" },
            contentAlignment = Alignment.Center
        ) {
            Icon(EditIcons.Edit, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}

/** Hermite smoothing of [x] between [edge0] and [edge1], as the search pill's own. */
private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
