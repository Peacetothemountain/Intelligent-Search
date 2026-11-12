package com.pixel.intelligentsearch.feature.search.today

import android.graphics.RuntimeShader
import android.graphics.SweepGradient
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import com.pixel.intelligentsearch.core.ui.icons.filled.Event
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.intelligentsearch.core.performance.ADPFThermalManager
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.grid.PartStatus
import com.pixel.intelligentsearch.feature.search.today.cards.grid.partIcon
import com.pixel.intelligentsearch.feature.search.today.cards.shape.CardOutline
import com.pixel.intelligentsearch.feature.search.today.cards.shape.rememberMorphingOutline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * What one of Discover's own cards plays while it has nothing to show (approved 2026-11-11): liquid light in the
 * theme's own colors. A silky color field drifts slowly (primaryContainer, secondaryContainer and a little
 * tertiaryContainer over surfaceContainerHigh), three thin strands of light wave across it, breathing, with a brighter
 * signal running along each now and then (primary to tertiary), and a soft rim of light turns slowly around the edge,
 * following the card's shape. No icon and no words in it: the card keeps its name above, and a card that needs
 * something from the user says what on a button. Still with animations removed.
 *
 * NG Designs
 */

/**
 * The idle light's loop: 2000π seconds. Every motion in it turns a whole number of times in that, so the loop wraps
 * without a jump.
 */
private const val IDLE_PERIOD_S = 2000.0 * PI
private const val IDLE_PERIOD_MS = (IDLE_PERIOD_S * 1000.0).toInt()

/** The idle light steps at 30 frames a second: its motion is slow, and a faster screen gains nothing from more. */
private const val IDLE_FRAME_S = 1f / 30f

/** The moment the idle light holds at with animations removed, a pleasing one. */
private const val IDLE_STILL_S = 37f

/** How tall a full card's light is when the card is as tall as its content. */
private val IDLE_HEIGHT = 112.dp

/** The light's corners inside a full card (the card's 24 dp less its 6 dp padding). */
private val IDLE_CORNER = 18.dp
private val IdleShape = RoundedCornerShape(IDLE_CORNER)

/**
 * Seconds along the idle light's loop, for all of the page's empty cards at once, stepping at 30 a second. It moves
 * only while [moving] (the page is open), animations are on and the phone isn't badly overheating; otherwise it holds
 * still (at a set moment with animations removed). Read it in drawing.
 */
@Composable
internal fun rememberIdleTime(moving: Boolean): LoopReader {
    val animate by rememberAnimationsEnabled()
    val context = androidx.compose.ui.platform.LocalContext.current
    val thermal = remember(context) { ADPFThermalManager.getInstance(context) }
    val throttle by thermal.thermalThrottleLevel.collectAsStateWithLifecycle()
    val hot = throttle >= ADPFThermalManager.ThermalThrottleLevel.SEVERE
    val held = remember { HeldIdle() }
    if (!animate) return remember { LoopReader { IDLE_STILL_S } }
    // Held where it stopped, so it picks up from there rather than jumping back, even if no card drew while it held.
    if (!moving || hot) {
        SideEffect { held.stop() }
        return remember(held) { LoopReader { held.stop() } }
    }
    val loop = rememberLoop(IDLE_PERIOD_MS, LinearEasing)
    val frame = remember(loop) { derivedStateOf { (loop() * IDLE_PERIOD_S / IDLE_FRAME_S).toLong() } }
    return remember(frame, held) { LoopReader { held.go(frame.value * IDLE_FRAME_S.toDouble()) } }
}

/** Where the idle light is along its loop, kept across a pause so it carries on from there. */
private class HeldIdle {
    private var at = IDLE_STILL_S.toDouble()
    private var base = 0.0
    private var moving = false

    /** Holds still where it is. */
    fun stop(): Float {
        moving = false
        return at.toFloat()
    }

    /** Moves on with the loop, [loop] seconds along it, from where it last held. */
    fun go(loop: Double): Float {
        if (!moving) {
            base = at - loop
            moving = true
        }
        at = ((base + loop) % IDLE_PERIOD_S + IDLE_PERIOD_S) % IDLE_PERIOD_S
        return at.toFloat()
    }
}

/** The theme's colors the idle light is made of. */
private class IdleColors(scheme: ColorScheme) {
    val dark = scheme.surface.luminance() < 0.5f
    val base = scheme.surfaceContainerHigh
    val field1 = scheme.primaryContainer
    val field2 = scheme.secondaryContainer
    val field3 = scheme.tertiaryContainer
    val light1 = scheme.primary
    val light2 = scheme.tertiary
}

/**
 * A full card with nothing to show yet: its name, as every card has, over the idle light. When [status] has something
 * the user can do ([PartStatus.button] and [PartStatus.fix]: allow calendar access, notification access or the
 * location), a button on the light does it. As tall as [IDLE_HEIGHT] under its name, or the whole of a card given a
 * height of its own.
 */
@Composable
internal fun IdleCard(kind: CardKind, status: PartStatus, time: LoopReader) {
    val scheme = MaterialTheme.colorScheme
    Layout(
        content = {
            // The same name and icon the card has with something in it (the countdown's is a calendar day).
            CardLabel(if (kind == CardKind.YEAR) Icons.Filled.Event else partIcon(kind), kind.label, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp))
            Box(Modifier.clip(IdleShape)) {
                // What TalkBack hears for the light: what the card is waiting for, or what it needs.
                IdleLight(time, seed = kind.ordinal * 1.37f, outline = null, modifier = Modifier.fillMaxSize().semantics { contentDescription = status.note })
                val button = status.button
                val fix = status.fix
                if (button != null && fix != null) {
                    Button(
                        onClick = fix,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                    ) {
                        Text(button, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .padding(6.dp)
    ) { measurables, constraints ->
        // The light takes what the name leaves of the card's least height: its usual height, or more when the card has
        // a height of its own (the page asks for that height as the least).
        val width = constraints.maxWidth
        val label = measurables[0].measure(Constraints(minWidth = 0, maxWidth = width))
        val lightHeight = maxOf(IDLE_HEIGHT.roundToPx(), constraints.minHeight - label.height)
        val light = measurables[1].measure(Constraints.fixed(width, lightHeight))
        layout(width, label.height + lightHeight) {
            label.place(0, 0)
            light.place(0, label.height)
        }
    }
}

/**
 * A shaped card with nothing to show yet: the idle light inside its Material shape [shape], the largest square the
 * bounds hold, centered. When [action] is given (a card that needs something from the user, its tap asks), it says so
 * in the middle. The caller makes the tap and names the card.
 */
@Composable
internal fun IdleShapedCard(shape: String, kind: CardKind, time: LoopReader, action: String?, modifier: Modifier = Modifier) {
    val outline = rememberMorphingOutline(shape)
    Box(modifier, contentAlignment = Alignment.Center) {
        IdleLight(time, seed = kind.ordinal * 1.37f, outline = { outline.current }, modifier = Modifier.fillMaxSize().clearAndSetSemantics { })
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 28.dp).clearAndSetSemantics { }
            )
        }
    }
}

/**
 * The idle light itself, over the whole of its bounds, or inside [outline] (a card's shape, in the largest centered
 * square) when given. Drawn by a runtime shader where Android has them (13 and later), and by a lighter drawing of
 * the same field, strands and rim before that.
 */
@Composable
private fun IdleLight(time: LoopReader, seed: Float, outline: (() -> CardOutline)?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme) { IdleColors(scheme) }
    val painter = remember { IdlePainter() }
    // Off screen (scrolled away) it keeps its last frame and stops redrawing; the clock is only read while it shows.
    val onScreen = remember { mutableStateOf(true) }
    val last = remember { floatArrayOf(Float.NaN) }
    Spacer(
        modifier
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                val shows = bounds.width > 0f && bounds.height > 0f
                if (shows != onScreen.value) onScreen.value = shows
            }
            .drawWithCache {
            painter.prepare(this, colors)
            val path = Path()
            if (outline == null) {
                val corner = IDLE_CORNER.toPx()
                path.addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size), androidx.compose.ui.geometry.CornerRadius(corner)))
            }
            onDrawBehind {
                val t = if (onScreen.value || last[0].isNaN()) time().also { last[0] = it } else last[0]
                if (outline == null) {
                    painter.draw(this, path, t, seed, colors)
                } else {
                    val side = min(size.width, size.height)
                    if (side <= 0f) return@onDrawBehind
                    path.reset()
                    outline().addTo(path, side, (size.width - side) / 2f, (size.height - side) / 2f)
                    painter.draw(this, path, t, seed, colors)
                }
            }
        }
    )
}

/** How many strokes make the rim's falloff, and how much each adds (the light at d dp in is about e^(-d/3)). */
private const val RIM_LAYERS = 10
private val RIM_ALPHAS = FloatArray(RIM_LAYERS) { k ->
    // Layers k and wider cover the band k to k + 1 dp in, laid one over another: 1 - (1 - a_k)(1 - a_k+1)... = e^(-k/3).
    fun light(k: Int) = if (k >= RIM_LAYERS) 0f else kotlin.math.exp(-k / 3f)
    1f - (1f - light(k)) / (1f - light(k + 1))
}

/**
 * The idle light's shader, made once for every card (making one takes a moment): each card sets its own values just
 * before it draws, and its drawing keeps them. Null before Android 13, or if it can't be made.
 */
private val idleShader: RuntimeShader? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(IDLE_LIGHT_AGSL) }.getOrNull() else null
}

/** Draws the idle light. Made once per card; a frame allocates nothing past the first. */
private class IdlePainter {
    private val shader: RuntimeShader? = idleShader
    private val brush: ShaderBrush? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) ShaderBrush(shader) else null
    private val rimMatrix = android.graphics.Matrix()
    private var rim: SweepGradient? = null
    private var rimBrush: ShaderBrush? = null
    private var rimFor: IdleColors? = null
    private var rimCenter = Offset.Unspecified
    // The rim's soft falloff: strokes 1 dp apart in reach, each adding a little, so the light fades smoothly inward.
    private var rimStrokes: Array<Stroke> = emptyArray()

    // The drawing used without a shader: the field's three glows and the strands.
    private var glows: List<Brush> = emptyList()
    private var glowFor: IdleColors? = null
    private val strand = Path()
    private var strandWide = Stroke(1f)
    private var strandThin = Stroke(1f)

    fun prepare(scope: androidx.compose.ui.draw.CacheDrawScope, colors: IdleColors) = with(scope) {
        val step = 1.dp.toPx()
        if (rimStrokes.size != RIM_LAYERS || rimStrokes[0].width != 2f * step) {
            rimStrokes = Array(RIM_LAYERS) { Stroke(2f * step * (it + 1)) }
        }
        strandWide = Stroke(6.dp.toPx())
        strandThin = Stroke(1.2.dp.toPx())
        val center = Offset(size.width / 2f, size.height / 2f)
        if (rimFor !== colors || rimCenter != center) {
            val a = if (colors.dark) 0.55f else 0.4f
            val peak = colors.light1.copy(alpha = a).toArgb()
            val side = colors.light2.copy(alpha = a * 0.45f).toArgb()
            val clear = colors.light1.copy(alpha = 0f).toArgb()
            rim = SweepGradient(
                center.x, center.y,
                intArrayOf(peak, side, clear, clear, side, peak),
                floatArrayOf(0f, 0.1f, 0.22f, 0.78f, 0.9f, 1f)
            ).also { rimBrush = ShaderBrush(it) }
            rimFor = colors
            rimCenter = center
        }
        if (brush == null && glowFor !== colors) {
            val a = if (colors.dark) 0.85f else 0.75f
            glows = listOf(colors.field1, colors.field2, colors.field3).map {
                Brush.radialGradient(listOf(it.copy(alpha = a), it.copy(alpha = a * 0.35f), it.copy(alpha = 0f)), center = Offset.Zero, radius = 1f)
            }
            glowFor = colors
        }
    }

    fun draw(scope: DrawScope, path: Path, t: Float, seed: Float, colors: IdleColors) = with(scope) {
        clipPath(path) {
            val shader = shader
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                shader.setFloatUniform("res", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                shader.setFloatUniform("t", t)
                shader.setFloatUniform("seed", seed)
                shader.setFloatUniform("dark", if (colors.dark) 1f else 0f)
                shader.setColorUniform("base", colors.base.toArgb())
                shader.setColorUniform("c1", colors.field1.toArgb())
                shader.setColorUniform("c2", colors.field2.toArgb())
                shader.setColorUniform("c3", colors.field3.toArgb())
                shader.setColorUniform("hi", colors.light1.toArgb())
                shader.setColorUniform("hi2", colors.light2.toArgb())
                drawRect(brush)
            } else {
                drawField(t, seed, colors)
            }
            // The rim, turning slowly: inside the edge only, as the clip keeps the outer half of each stroke off.
            val rim = rim
            val rimBrush = rimBrush
            if (rim != null && rimBrush != null) {
                rimMatrix.setRotate((t * 0.3f + seed) * (180f / PI.toFloat()), size.width / 2f, size.height / 2f)
                rim.setLocalMatrix(rimMatrix)
                // Each stroke reaches 1 dp further in; together they light d dp in at about e^(-d/3).
                for (i in rimStrokes.indices) drawPath(path, rimBrush, alpha = RIM_ALPHAS[i], style = rimStrokes[i])
            }
        }
    }

    /** The field and strands without a shader: three soft glows drifting over the base, and three waving lines. */
    private fun DrawScope.drawField(t: Float, seed: Float, colors: IdleColors) {
        drawRect(colors.base)
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.55f
        glows.forEachIndexed { i, glow ->
            val ph = seed + i * 2.1f
            val x = w * (0.5f + 0.38f * sin(t * (0.07f + 0.02f * i) + ph))
            val y = h * (0.5f + 0.32f * cos(t * (0.05f + 0.02f * i) + ph * 1.4f))
            translate(x, y) { scale(r, r, Offset.Zero) { drawCircle(glow, radius = 1f, center = Offset.Zero) } }
        }
        val m = min(w, h)
        for (i in 0 until 3) {
            strand.reset()
            val steps = 40
            for (s in 0..steps) {
                val px = (s.toFloat() / steps - 0.5f) * w / m
                val y = 0.13f * sin(px * 2.1f + t * 0.314f + i * 2.1f + seed) + 0.08f * sin(px * 3.7f - t * 0.236f + i * 1.3f) +
                    (i - 1) * 0.11f * sin(t * 0.105f + i)
                val sx = w / 2f + px * m
                val sy = h / 2f + y * m
                if (s == 0) strand.moveTo(sx, sy) else strand.lineTo(sx, sy)
            }
            val breathe = 0.65f + 0.35f * sin(t * 0.288f + i * 2f)
            val light = if (i == 1) colors.light2 else colors.light1
            drawPath(strand, light, alpha = 0.08f * breathe, style = strandWide)
            drawPath(strand, light, alpha = (if (colors.dark) 0.5f else 0.4f) * breathe, style = strandThin)
        }
    }
}

/**
 * The idle light (AGSL). [t] is seconds along the 2000π s loop: every motion turns a whole number of times in it. The
 * field moves along a circle through the noise, so it never runs off; each strand is a smooth wave across the card
 * whose width breathes, lit faintly along its length and brightly where its signal runs.
 */
private const val IDLE_LIGHT_AGSL = """
uniform float2 res;
uniform float t;
uniform float seed;
uniform float dark;
layout(color) uniform float4 base;
layout(color) uniform float4 c1;
layout(color) uniform float4 c2;
layout(color) uniform float4 c3;
layout(color) uniform float4 hi;
layout(color) uniform float4 hi2;

float h(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h(i), h(i + float2(1, 0)), f.x), mix(h(i + float2(0, 1)), h(i + float2(1, 1)), f.x), f.y);
}
float fbm(float2 p) { return 0.6 * noise(p) + 0.3 * noise(p * 2.03 + 7.1) + 0.1 * noise(p * 4.1 + 3.3); }

half4 main(float2 fc) {
    float m = min(res.x, res.y);
    float2 p = (fc - 0.5 * res) / m;
    float2 drift = 2.3 * float2(cos(t * 0.026), sin(t * 0.026));
    float2 w = float2(fbm(p * 0.9 + float2(seed, 0.0) + drift), fbm(p * 0.9 + float2(0.0, seed + 4.0) + float2(drift.y, drift.x) * 0.8));
    float f = fbm(p * 1.1 + 1.8 * w - drift * 0.7);
    float g = fbm(p * 1.3 - 1.6 * w + float2(seed * 0.3, 2.0) + drift);
    float3 col = base.rgb;
    col = mix(col, c1.rgb, smoothstep(0.25, 0.75, f));
    col = mix(col, c2.rgb, smoothstep(0.35, 0.8, g) * 0.85);
    col = mix(col, c3.rgb, smoothstep(0.6, 0.9, w.x) * 0.3);
    col = mix(col, hi.rgb, smoothstep(0.55, 0.9, f * g * 1.6) * (dark > 0.5 ? 0.28 : 0.22));
    float aspect = res.x / m;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float x = p.x;
        float y = 0.13 * sin(x * 2.1 + t * 0.32 + fi * 2.1 + seed) + 0.08 * sin(x * 3.7 - t * 0.23 + fi * 1.3)
            + (fi - 1.0) * 0.11 * sin(t * 0.11 + fi);
        float d = abs(p.y - y);
        float width = 0.012 + 0.009 * sin(x * 1.7 + t * 0.4 + fi);
        float glow = exp(-d * d / (width * width * 18.0)) * 0.32 + exp(-d * d / (width * width * 0.6));
        float run = fract(t * 0.0700282 + fi * 0.33 + seed * 0.1) * (aspect + 0.8) - 0.5 * aspect - 0.4;
        float z = (x - run) * 3.2;
        float sig = exp(-z * z);
        float3 sc = mix(hi.rgb, hi2.rgb, smoothstep(-0.5 * aspect, 0.5 * aspect, x + 0.3 * sin(t * 0.2 + fi)));
        float a = glow * (0.22 + 0.9 * sig) * (dark > 0.5 ? 1.0 : 0.75) * (0.65 + 0.35 * sin(t * 0.3 + fi * 2.0));
        col = mix(col, sc, clamp(a, 0.0, 1.0));
    }
    return half4(col, 1.0);
}
"""
