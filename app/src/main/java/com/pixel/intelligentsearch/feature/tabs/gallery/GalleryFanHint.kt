package com.pixel.intelligentsearch.feature.tabs.gallery

import android.content.SharedPreferences
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The fan's gesture hint (Gesture Hints in How Looks Work): a double-headed arrow on a curve around the rivet, drawn
 * through the deck's lens over the strips, with a light that glides along it. Left alone, the light glides from the
 * upright strip's end to the spread's while the fan turns a little toward the next look, draws in at the head, rests,
 * glides back while the fan turns a little back, then rests longer. Under a finger the light follows the drag along the
 * curve and reaches the head when the push is far enough to turn a look. It is one steady light: it grows out of
 * nothing and draws in to nothing, and never changes brightness. The demo starts 1.2 s after the fan is left alone and
 * stops at a touch. After three turns that change the look the arrow shrinks away for good; the switch brings it back
 * with its count started again.
 */
@Stable
internal class FanHint(private val prefs: SharedPreferences) {
    var on by mutableStateOf(prefs[SettingsManager.GALLERY_HINTS])
    /** Its size: 1 shown, 0 gone (it shrinks away; it never fades). */
    val size = Animatable(if (on) 1f else 0f)
    /** The small turn it gives the fan, in strips. */
    val ghost = Animatable(0f)
    /** The light: where its head and the tail it trails are on the curve (percent of it, from the upright strip's end), and its own size. */
    var head by mutableFloatStateOf(F0)
        private set
    var tail by mutableFloatStateOf(F0)
        private set
    var light by mutableFloatStateOf(0f)
        private set
    private val headF = Follower(F0)
    private val tailF = Follower(F0)
    private val lightF = Follower(0f)

    /** The fan came to rest on another look: one of the three turns that put the hint away. */
    fun turned() {
        if (!on) return
        val turns = prefs[SettingsManager.GALLERY_HINT_TURNS] + 1
        prefs.edit {
            put(SettingsManager.GALLERY_HINT_TURNS, turns.coerceAtMost(FanDims.HINT_TURNS))
            if (turns >= FanDims.HINT_TURNS) put(SettingsManager.GALLERY_HINTS, false)
        }
        if (turns >= FanDims.HINT_TURNS) on = false
    }

    /**
     * One frame of the light, [lit] or going out, its head making for [to]: the head follows where it should be, the
     * tail follows the head, so the light is a comet that grows as it sets off and draws in as it arrives. While it is
     * nothing it is wherever it sets off from next. Returns whether it is still moving.
     */
    fun step(dt: Float, lit: Boolean, to: Float): Boolean {
        if (lightF.x < 0.02f) {
            headF.at(to)
            tailF.at(to)
        }
        var busy = lightF.step(if (lit) 1f else 0f, dt, 500f, 0.004f)
        busy = headF.step(to, dt, 900f, 0.02f) || busy
        busy = tailF.step(headF.x, dt, 40f, 0.02f) || busy
        light = lightF.x.coerceIn(0f, 1f)
        head = headF.x
        tail = tailF.x
        return busy
    }

    companion object {
        /** The light's run stops short of the heads; its glow is also wiped away around them ([HintPaints.clear]). */
        const val F0 = 14f
        const val F1 = 86f
        /** Seconds: a glide, the tail drawing in after it, when the glide back starts, and the whole loop. */
        const val GLIDE = 1.1f
        const val TAIL = 0.5f
        const val BACK = 2.1f
        const val LOOP = 6.4f
    }
}

/** A value that follows its target on a critically damped spring (stiffness [k]), integrated at 480 Hz. */
private class Follower(var x: Float) {
    var v = 0f

    fun at(to: Float) {
        x = to
        v = 0f
    }

    fun step(to: Float, dt: Float, k: Float, eps: Float): Boolean {
        val n = max(1, ceil(dt * 480f).toInt())
        val h = dt / n
        val c = 2f * sqrt(k)
        for (i in 0 until n) {
            v += (-k * (x - to) - c * v) * h
            x += v * h
        }
        if (abs(x - to) > eps || abs(v) > eps * 10f) return true
        at(to)
        return false
    }
}

private val hintSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 300f)

/** Away slowly, and slowly in to the head. */
private fun glideEase(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
}

/**
 * Runs the light for [ms], [lit] or going out, its head making for [to] (given the seconds since this began); with
 * [ms] negative, until it has come to rest. Only the hint's own layer is drawn again while it runs.
 */
private suspend fun FanHint.light(ms: Long, lit: Boolean, to: (Float) -> Float) {
    var start = 0L
    var last = 0L
    while (true) {
        val moving = withFrameNanos { now ->
            if (start == 0L) {
                start = now
                last = now
            }
            val dt = ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
            last = now
            step(dt, lit, to((now - start) / 1e9f))
        }
        if (ms >= 0) {
            if ((last - start) / 1_000_000L >= ms) return
        } else if (!moving) return
    }
}

/** One glide of the demo, the fan turned [nudge] of a strip meanwhile: from [from] to [to], then the tail draws in and the light goes. */
private suspend fun FanHint.glide(nudge: Float, from: Float, to: Float) = coroutineScope {
    val turn = if (ghost.value != nudge || ghost.isRunning) launch { ghost.animateTo(nudge, hintSpring) } else null
    light((FanHint.GLIDE * 1000).toLong(), true) { t -> from + (to - from) * glideEase(t / FanHint.GLIDE) }
    turn?.cancel()
    if (ghost.value != 0f) launch { ghost.animateTo(0f, hintSpring) }
    light((FanHint.TAIL * 1000).toLong(), true) { to }
    light(-1, false) { to }
}

@Composable
internal fun rememberFanHint(engine: FanEngine): FanHint {
    val context = LocalContext.current
    val hint = remember { FanHint(GalleryLive.prefs(context.applicationContext)) }
    val animate by rememberAnimationsEnabled()
    // The switch in How Looks Work (or a restore) turns it on or off while Gallery is kept.
    DisposableEffect(hint) {
        val prefs = GalleryLive.prefs(context.applicationContext)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, k ->
            if (k == null || k in SettingsManager.GALLERY_HINTS.allNames) hint.on = sp[SettingsManager.GALLERY_HINTS]
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        hint.on = prefs[SettingsManager.GALLERY_HINTS]
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(hint) { snapshotFlow { hint.on }.collect { hint.size.animateTo(if (it) 1f else 0f, spring(dampingRatio = 0.82f, stiffness = 600f)) } }
    LaunchedEffect(engine, hint) { snapshotFlow { hint.ghost.value }.collect { engine.ghost = it } }
    LaunchedEffect(engine, hint, animate) {
        snapshotFlow { Triple(hint.on, engine.pulling, engine.calm) }.collectLatest { (on, pulling, calm) ->
            if (on && pulling) {
                // Under a finger the light is the finger's: from the middle of the curve toward the head the fan is being
                // turned to, there when the push is far enough to turn a look (FanDims.COMMIT of a strip).
                coroutineScope {
                    launch { hint.ghost.animateTo(0f, hintSpring) }
                    hint.light(Long.MAX_VALUE, true) { 50f + (engine.pull / FanDims.COMMIT * 0.5f).coerceIn(-0.5f, 0.5f) * (FanHint.F1 - FanHint.F0) }
                }
                return@collectLatest
            }
            // A touch, a turn or the switch: the light goes out where it is and the fan is left as it stands.
            coroutineScope {
                launch { hint.ghost.animateTo(0f, hintSpring) }
                hint.light(-1, false) { hint.head }
            }
            if (!(on && calm && animate)) return@collectLatest
            delay(1200)
            // Toward the next look, a rest, back, a longer rest. Only the first run turns the fan (that redraws every
            // strip); the later ones run the light alone, which redraws just the hint, and after a few it sleeps until
            // the fan is next left alone.
            for (run in 0 until FanDims.HINT_RUNS) {
                val nudge = if (run == 0) FanDims.HINT_NUDGE else 0f
                val began = System.nanoTime()
                suspend fun until(seconds: Float) = delay(((seconds * 1e9f - (System.nanoTime() - began)) / 1e6f).toLong().coerceAtLeast(0L))
                hint.glide(-nudge, FanHint.F0, FanHint.F1)
                until(FanHint.BACK)
                hint.glide(nudge * 0.75f, FanHint.F1, FanHint.F0)
                until(FanHint.LOOP)
            }
        }
    }
    return hint
}

/** The arc's point [deg] degrees clockwise from upright, [r] from the rivet, in the rivet's plane (mockup px). */
private fun arcPoint(deg: Float, r: Float = FanDims.HINT_R): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset((r * sin(a)).toFloat(), (-r * cos(a)).toFloat())
}

/** The hint's colors: the arrow (solid, so the light can't tint its heads), the light's glow and its bright core. */
internal class HintColors(val arrow: Color, val glow: Color, val core: Color)

/** What the light is painted with: made once, its gradients set each frame the light moves. */
internal class HintPaints {
    val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    val core = android.graphics.Paint(glow)
    var blur = 0f
    /** Wipes the light away around each head, softly, so its glow never tints them. */
    val clear = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
    }
    val bounds = android.graphics.RectF()
}

/** The hint, drawn through the lens ([FanEngine.hintPlane]) at [unit] px per mockup px. */
internal fun DrawScope.drawFanHint(engine: FanEngine, hint: FanHint, unit: Float, colors: HintColors, paints: HintPaints) {
    val s = hint.size.value
    if (s < 0.004f) return
    // It grows and shrinks about a point in the wedge, as the mockup's does.
    val origin = Offset(88f, -109f)
    fun map(p: Offset): Offset {
        val q = origin + (p - origin) * s
        val (x, y) = M3.ap(engine.hintPlane, q.x, q.y)
        return Offset(x * unit, y * unit)
    }
    // A curve around the rivet, bowed out more than the circle through its ends, so it reads as a turn.
    val mid = (FanDims.HINT_A0 + FanDims.HINT_A1) / 2f
    val e0 = arcPoint(FanDims.HINT_A0)
    val e1 = arcPoint(FanDims.HINT_A1)
    val ctl = arcPoint(mid, FanDims.HINT_BOW_R)
    fun at(t: Float): Offset = e0 * ((1 - t) * (1 - t)) + ctl * (2 * t * (1 - t)) + e1 * (t * t)
    fun run(from: Float, to: Float, steps: Int) = Path().apply {
        for (j in 0..steps) {
            val pt = map(at(from + (to - from) * j / steps))
            if (j == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
    }
    val stroke = Stroke(width = 3.2f * unit * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(run(0f, 1f, 24), colors.arrow, style = stroke)
    // The light on the shaft: a soft glow and a bright core along the same stretch of the curve, each painted from
    // nothing at its tail to full at its head, so it is one smooth comet.
    val lz = hint.light
    if (lz >= 0.01f) {
        val h = hint.head / 100f
        val t = hint.tail / 100f
        val p1 = map(at(h))
        val p0 = if (abs(h - t) < 0.006f) p1 - Offset(0.01f, 0f) else map(at(t))
        val path = run(t, h, 12).asAndroidPath()
        fun shade(c: Color, o: Float) = LinearGradient(
            p0.x, p0.y, p1.x, p1.y,
            intArrayOf(c.copy(alpha = 0f).toArgb(), c.copy(alpha = o * 0.45f).toArgb(), c.copy(alpha = o).toArgb()), floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val blur = 2.4f * unit
        if (paints.blur != blur) {
            paints.blur = blur
            paints.glow.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        paints.glow.shader = shade(colors.glow, 0.62f)
        paints.glow.strokeWidth = 13f * unit * s * lz
        paints.core.shader = shade(colors.core, 1f)
        paints.core.strokeWidth = 3.4f * unit * s * lz
        val nc = drawContext.canvas.nativeCanvas
        // In a layer of its own, wiped away around the heads: the glow is wider than the gap the run leaves them.
        path.computeBounds(paints.bounds, false)
        val reach = 16f * unit * s
        paints.bounds.inset(-reach, -reach)
        val layer = nc.saveLayer(paints.bounds, null)
        nc.drawPath(path, paints.glow)
        nc.drawPath(path, paints.core)
        val clearR = 12f * unit * s
        val fadeR = clearR + 8f * unit * s
        for (tip in listOf(map(e0), map(e1))) {
            paints.clear.shader = android.graphics.RadialGradient(
                tip.x, tip.y, fadeR, intArrayOf(android.graphics.Color.BLACK, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT),
                floatArrayOf(0f, clearR / fadeR, 1f), Shader.TileMode.CLAMP
            )
            nc.drawCircle(tip.x, tip.y, fadeR, paints.clear)
        }
        nc.restoreToCount(layer)
    }
    // The two heads, over the light: wings trailing back along the curve's own direction at each end.
    val heads = Path()
    fun head(tip: Offset, back: Offset) {
        val len = back.getDistance().coerceAtLeast(0.001f)
        val tx = back.x / len
        val ty = back.y / len
        for (w in listOf(1f, -1f)) {
            val r = Math.toRadians(32.0 * w)
            val c = cos(r).toFloat()
            val n = sin(r).toFloat()
            val wing = Offset(tip.x + 9f * (tx * c - ty * n), tip.y + 9f * (tx * n + ty * c))
            val a = map(tip)
            val b = map(wing)
            heads.moveTo(a.x, a.y)
            heads.lineTo(b.x, b.y)
        }
    }
    head(e0, ctl - e0)
    head(e1, ctl - e1)
    drawPath(heads, colors.arrow, style = stroke)
}
