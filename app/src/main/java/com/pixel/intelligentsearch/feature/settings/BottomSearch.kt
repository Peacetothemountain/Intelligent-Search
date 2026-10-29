package com.pixel.intelligentsearch.feature.settings

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.ProgressiveBlurDirection
import com.pixel.intelligentsearch.core.ui.progressiveEdgeBlur
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.intellij.lang.annotations.Language
import kotlin.math.exp
import kotlin.math.max

// Search's open and close on the pages that search at the bottom (Manage Hidden Apps, Add Applications). The pill's
// slide uses a curve that is its own mirror image, so the close is exactly the open played backward; it moves from
// the first frame (no slow start) and eases only gently at either end. Each fade takes the 40% of it nearest the closed
// state (the start of the open, the end of the close), and the magnifier and X trade places over the whole of it.
internal const val SEARCH_MILLIS = 200
internal const val SEARCH_FADE_MILLIS = 80
internal val SearchEasing = CubicBezierEasing(0.25f, 0.1f, 0.75f, 0.9f)
private val SearchFadeIcon = tween<Float>(SEARCH_MILLIS, easing = SearchEasing)

/** How far up the keyboard is when the pill starts its slide, so the two land together (about 80 ms into its rise). */
internal const val KEYBOARD_LEAD = 0.6f

/** How long the pill waits for a keyboard that doesn't come (a hardware keyboard, or none), before sliding anyway. */
internal const val KEYBOARD_WAIT_MS = 220L

/** The flash on landing: it dies away like the morph's flash on impact, most of it in the first ~110 ms. */
private const val FLASH_MILLIS = 450
private val FlashDecay = Easing { t -> ((1f - exp(-t * 4.1f)) / (1f - exp(-4.1f))) }

/**
 * The pill is the search overlay's own bar in size: 72dp tall (its 48dp buttons and 12dp above and below them), 14dp
 * from the screen's sides (the overlay panel's 4dp and the bar's 10dp), with the bar's 24dp inside and 20sp text. It
 * sits 12dp above the keyboard (or the navigation bar).
 */
private val PILL_HEIGHT = 72.dp
private val PILL_SIDE = 14.dp
private val PILL_INSIDE = 24.dp
private val PILL_TEXT = 20.sp
private val PILL_MARGIN = 12.dp

/** How much of the list's bottom edge dissolves into the pill while search is open, and how strongly. */
internal val PILL_BLUR_EDGE = 120.dp
internal val PILL_BLUR_MAX = 16.dp

/** Room a list needs at its bottom so its last row can scroll clear of the pill. */
internal val PILL_CLEARANCE = PILL_HEIGHT + PILL_MARGIN + 8.dp

/** The magnifier that opens search, turning into the X that closes it as the pill opens, and back as it closes. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun SearchToggleButton(open: Boolean, onToggle: () -> Unit) {
    val transition = updateTransition(open, label = "search")
    IconButton(onClick = onToggle) {
        transition.Crossfade(animationSpec = SearchFadeIcon) { shown ->
            Icon(if (shown) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = if (shown) "Close search" else "Search")
        }
    }
}

/**
 * A page's search field, living at the bottom. Opening it raises the keyboard, and the pill waits for it: once the
 * keyboard is most of the way up the pill slides out from the right edge, already in its place just above where the
 * keyboard will stop, and lands as the keyboard does. It never rides up or down with the keyboard while it opens or
 * closes. Closing, it slides back to the right as the keyboard goes down.
 *
 * As it slides it manifests out of the morph's halftone ([BOTTOM_SEARCH_SHADER], Android 13 and up): the morph's pool of
 * light leads its rounded edge, carrying the diamond dot grid, and behind the light the pill forms out of the same
 * grid, its dots swelling and merging. When the edge reaches the left side the last gaps close and the morph's flash
 * on impact blooms at that end, a rim catching the light along the top, and both fade, leaving the plain search
 * bar. Below Android 13 the pill is simply uncovered from the right.
 *
 * Place it in a box covering the page's content area (above the navigation bar), aligned to the bottom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BottomSearchPill(
    open: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val reveal = remember { Animatable(if (open) 1f else 0f) }
    val fade = remember { Animatable(if (open) 1f else 0f) }
    val flash = remember { Animatable(0f) }
    val shown by remember { derivedStateOf { reveal.value > 0f || fade.value > 0f } }
    if (!open && !shown) return

    val density = LocalDensity.current
    val imeTarget = WindowInsets.imeAnimationTarget.getBottom(density)
    val ime = WindowInsets.ime.getBottom(density)
    val nav = WindowInsets.navigationBars.getBottom(density)
    val keyboard by rememberUpdatedState(ime to imeTarget)
    LaunchedEffect(open) {
        if (open) {
            // The keyboard is on its way: start once it's most of the way up, so the pill lands as it does.
            withTimeoutOrNull(KEYBOARD_WAIT_MS) {
                snapshotFlow { keyboard }.first { (now, target) -> pillMayStart(now, target) }
            }
            launch { fade.animateTo(1f, tween(SEARCH_FADE_MILLIS, easing = LinearEasing)) }
            reveal.animateTo(1f, tween(SEARCH_MILLIS, easing = SearchEasing))
            flash.snapTo(1f)
            flash.animateTo(0f, tween(FLASH_MILLIS, easing = FlashDecay))
        } else {
            flash.snapTo(0f)
            launch { fade.animateTo(0f, tween(SEARCH_FADE_MILLIS, delayMillis = SEARCH_MILLIS - SEARCH_FADE_MILLIS, easing = LinearEasing)) }
            reveal.animateTo(0f, tween(SEARCH_MILLIS, easing = SearchEasing))
        }
    }

    // Where the pill sits: while it opens, just above where the keyboard is going; once open, on the keyboard as it
    // comes and goes; while it closes, where it was.
    val settled by remember(open) { derivedStateOf { open && reveal.value >= 1f } }
    var heldLift by remember { mutableIntStateOf(0) }
    val lift = when {
        settled -> max(ime - nav, 0)
        open -> max(max(imeTarget, ime) - nav, 0)
        else -> heldLift
    }
    SideEffect { if (open) heldLift = lift }

    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(open) {
        if (open) {
            focus.requestFocus()
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val body = MaterialTheme.colorScheme.surfaceVariant
    val light = MaterialTheme.colorScheme.primary
    val margin = with(density) { PILL_MARGIN.roundToPx() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PILL_SIDE)
            .height(PILL_HEIGHT)
            .offset { IntOffset(0, -(lift + margin)) }
            .manifestingPill({ reveal.value }, { fade.value }, { flash.value }, body, light)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PILL_INSIDE)
                // The text shows only once the pill has formed.
                .graphicsLayer { alpha = fade.value * smoothStep(0.82f, 1f, reveal.value) }
        ) {
            val onPill = MaterialTheme.colorScheme.onSurfaceVariant
            if (query.isEmpty()) {
                Text(placeholder, color = onPill.copy(alpha = 0.6f), fontSize = PILL_TEXT, fontFamily = GoogleSansFlex, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = onPill, fontSize = PILL_TEXT, fontFamily = GoogleSansFlex),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = placeholder }
            )
        }
    }
}

/**
 * For the list under a [BottomSearchPill] while search is [open]: it ends at the keyboard rather than behind it, and
 * its bottom edge dissolves into the pill with a progressive blur. Pair it with [PILL_CLEARANCE] of bottom content
 * padding so the last row can scroll clear of the pill.
 */
@Composable
internal fun Modifier.underBottomSearch(open: Boolean): Modifier {
    if (!open) return this
    val density = LocalDensity.current
    val aboveNav = max(WindowInsets.ime.getBottom(density) - WindowInsets.navigationBars.getBottom(density), 0)
    return this
        .padding(bottom = with(density) { aboveNav.toDp() })
        .progressiveEdgeBlur(edge = ProgressiveBlurDirection.BottomToTop, edgeHeight = PILL_BLUR_EDGE, maxBlur = PILL_BLUR_MAX)
}

/** Whether the pill may start its slide: the keyboard is [KEYBOARD_LEAD] of the way up, or already there. */
internal fun pillMayStart(keyboardNow: Int, keyboardTarget: Int): Boolean =
    keyboardTarget > 0 && keyboardNow >= keyboardTarget * KEYBOARD_LEAD

private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Draws the pill behind its content: formed, forming out of the halftone, or uncovered from the right below Android 13. */
private fun Modifier.manifestingPill(
    reveal: () -> Float,
    fade: () -> Float,
    flash: () -> Float,
    body: Color,
    light: Color
): Modifier = drawWithCache {
    val shader = bottomSearchShader
    val brush = if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ShaderBrush(shader) else null
    val pad = PILL_GLOW_PAD.toPx()
    val corner = CornerRadius(size.height / 2f)
    onDrawBehind {
        val p = reveal()
        val a = fade()
        val f = flash()
        if (a <= 0f || p <= 0f) return@onDrawBehind
        if (p >= 1f && f <= 0.003f) {
            drawRoundRect(body, cornerRadius = corner, alpha = a)
        } else if (shader != null && brush != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader.setPillUniforms(size, density, p, a, f, body, light)
            drawRect(brush, topLeft = Offset(-pad, -pad), size = Size(size.width + 2 * pad, size.height + 2 * pad))
        } else {
            clipRect(left = size.width * (1f - p)) { drawRoundRect(body, cornerRadius = corner, alpha = a) }
        }
    }
}

/** Room around the pill for its light to spill past its edge. */
private val PILL_GLOW_PAD = 30.dp

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun RuntimeShader.setPillUniforms(size: Size, density: Float, progress: Float, alpha: Float, flash: Float, body: Color, light: Color) {
    setFloatUniform("uSize", size.width, size.height)
    setFloatUniform("uDp", density)
    setFloatUniform("uProgress", progress)
    setFloatUniform("uAlpha", alpha)
    setFloatUniform("uFlash", flash)
    setFloatUniform("uBody", body.red, body.green, body.blue, 1f)
    setFloatUniform("uLight", light.red, light.green, light.blue, 1f)
}

/** The shader, compiled once; null below Android 13 or where it fails to compile. */
private val bottomSearchShader: RuntimeShader? by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null
    else runCatching { RuntimeShader(BOTTOM_SEARCH_SHADER) }.getOrNull()
}

/**
 * The pill manifesting, per pixel, in the pill's own coordinates (its top left at 0, 0; drawn with room around it):
 * - Its forming edge has the pill's rounded end, [uProgress] of the way from the right side to the left.
 * - Behind the edge the body grows out of a true halftone screen (the morph's diamond grid, a square grid turned 45°):
 *   dots swell, merge into a mesh and close their last holes 88dp behind it; it all closes once the edge lands.
 * - The morph's pool of light leads the edge, tinting the body it passes, with its own halftone glints on a grid
 *   that travels with it, a dot only in its brighter core, as big as the light there.
 * - [uFlash], from the landing: the flash on impact at the left end and the rim catching the light along the top.
 * The output is premultiplied: the body over the page, the light added on top.
 */
@Language("AGSL")
internal const val BOTTOM_SEARCH_SHADER = """
uniform float2 uSize;
uniform float uDp;
uniform float uProgress;
uniform float uAlpha;
uniform float uFlash;
uniform half4 uBody;
uniform half4 uLight;

float screen(float2 q, float k) {
    return 0.5 + 0.25 * (cos(k * (q.x + q.y)) + cos(k * (q.x - q.y)));
}

half4 main(float2 p) {
    float W = uSize.x;
    float R = uSize.y * 0.5;
    float yy = p.y - R;
    float qx = clamp(p.x, R, W - R);
    float d = length(float2(p.x - qx, yy)) - R;
    float bleed = 8.0 * uDp;
    if (d > bleed) return half4(0.0);
    float m = clamp(0.5 - d, 0.0, 1.0);
    float ms = clamp(1.0 - d / bleed, 0.0, 1.0);

    float k = 6.2831853 / (1.41421356 * 4.7 * uDp);
    float e = W * (1.0 - uProgress);
    float capX = e + R - sqrt(max(0.0, R * R - yy * yy));
    float behind = p.x - capX;
    float solid = smoothstep(0.8, 1.0, uProgress);
    float c = behind > 0.0 ? max(pow(clamp(behind / (88.0 * uDp), 0.0, 1.0), 0.9), solid) : 0.0;
    float bodyA = 0.0;
    if (c >= 0.999) {
        bodyA = 1.0;
    } else if (c > 0.002) {
        float thr = 1.0 - c;
        bodyA = smoothstep(thr - 0.09, thr + 0.09, screen(p, k));
    }

    float lightAmt = smoothstep(0.0, 0.1, uProgress) * (1.0 - smoothstep(0.8, 1.0, uProgress));
    float lr = 63.0 * uDp;
    float lx = e + 9.6 * uDp;
    float L = 0.0;
    float glint = 0.0;
    if (lightAmt > 0.001) {
        float r = length(float2((p.x - lx) / lr, yy / (lr * 0.55)));
        if (r < 0.9) {
            L = pow(1.0 - r / 0.9, 1.6) * lightAmt;
            if (L > 0.04) {
                float gt = 1.0 - L * 0.62;
                glint = smoothstep(gt - 0.06, gt + 0.06, screen(float2(p.x - lx, yy), k)) * sqrt(L);
            }
        }
    }

    float F = 0.0;
    float rim = 0.0;
    if (uFlash > 0.003) {
        float fr = length(float2((p.x - (R + 5.5 * uDp)) / (55.0 * uDp), yy / (27.0 * uDp)));
        float f1 = max(0.0, 1.0 - fr);
        F = uFlash * f1 * f1 * 0.5;
        if (d > -2.7 * uDp && yy < 0.0) {
            rim = max(0.0, uFlash * (1.0 - abs(d + 1.1 * uDp) / (1.6 * uDp)) * clamp(-yy / R, 0.0, 1.0) * 0.55);
        }
    }

    float bA = bodyA * m;
    float add = L * 0.36 * ms + (glint * 0.8 + F + rim) * m;
    float a = min(1.0, bA + add);
    if (a <= 0.002) return half4(0.0);
    half3 lit = mix(uBody.rgb, mix(uBody.rgb, uLight.rgb, 0.3), L * 0.7);
    half3 col = min(lit * bA + uLight.rgb * add, half3(a));
    return half4(col, a) * uAlpha;
}
"""
