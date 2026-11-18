package com.pixel.intelligentsearch.feature.tabs.gallery

import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * The fan's gesture hint (Gesture Hints in Settings): a double-headed arrow on an arc around the rivet, drawn through
 * the deck's lens, with a small light that runs up it while the fan turns a little toward the next look, then down as it
 * turns back, then rests. It starts 1.2 s after the fan is left alone and stops at a touch. After three turns that
 * change the look it shrinks away for good; the switch in Settings brings it back with its count started again.
 */
@Stable
internal class FanHint(private val prefs: SharedPreferences) {
    var on by mutableStateOf(prefs[SettingsManager.GALLERY_HINTS])
    /** Its size: 1 shown, 0 gone (it shrinks away; it never fades). */
    val size = Animatable(if (on) 1f else 0f)
    /** The small turn it gives the fan, in strips. */
    val ghost = Animatable(0f)
    /** Where its light is on the arc, in degrees clockwise from upright. */
    val dot = Animatable(MID)

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

    companion object {
        const val MID = (FanDims.HINT_A0 + FanDims.HINT_A1) / 2f
        /** The light's run stops short of the heads, so it never sits on them. */
        const val DOT_A0 = FanDims.HINT_A0 + 6f
        const val DOT_A1 = FanDims.HINT_A1 - 6f
    }
}

private val hintSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 300f)

@Composable
internal fun rememberFanHint(engine: FanEngine): FanHint {
    val context = LocalContext.current
    val hint = remember { FanHint(GalleryLive.prefs(context.applicationContext)) }
    // The switch in Settings (or a restore) turns it on or off while Gallery is kept.
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
    LaunchedEffect(engine, hint) {
        snapshotFlow { hint.on && engine.calm }.collectLatest { idle ->
            if (!idle) {
                coroutineScope {
                    launch { hint.ghost.animateTo(0f, hintSpring) }
                    hint.dot.animateTo(FanHint.MID, hintSpring)
                }
                return@collectLatest
            }
            delay(1200)
            // Up the arc as the fan turns a little toward the next look, down as it turns back, then a rest. Only the
            // first run turns the fan (that redraws every strip); the later ones run the light alone, which redraws
            // just the hint, and after a few it sleeps until the fan is next left alone.
            for (run in 0 until FanDims.HINT_RUNS) {
                val nudge = if (run == 0) FanDims.HINT_NUDGE else 0f
                hintPhase(hint, nudge, FanHint.DOT_A0, 900)
                hintPhase(hint, -nudge * 0.75f, FanHint.DOT_A1, 1000)
                hintPhase(hint, 0f, FanHint.MID, if (run == 0) 3700 else 6000)
            }
        }
    }
    return hint
}

private suspend fun hintPhase(hint: FanHint, ghost: Float, dot: Float, ms: Long) = coroutineScope {
    val a = if (hint.ghost.value != ghost || hint.ghost.isRunning) launch { hint.ghost.animateTo(ghost, hintSpring) } else null
    val b = launch { hint.dot.animateTo(dot, hintSpring) }
    delay(ms)
    a?.cancel()
    b.cancel()
}

/** The arc's point [deg] degrees clockwise from upright, [r] from the rivet, in the rivet's plane (mockup px). */
private fun arcPoint(deg: Float, r: Float = FanDims.HINT_R): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset((r * sin(a)).toFloat(), (-r * cos(a)).toFloat())
}

/** The hint, drawn through the lens ([FanEngine.hintPlane]) at [unit] px per mockup px. */
internal fun DrawScope.drawFanHint(engine: FanEngine, hint: FanHint, unit: Float, primary: Color, tertiary: Color) {
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
    val e0 = arcPoint(FanDims.HINT_A0)
    val e1 = arcPoint(FanDims.HINT_A1)
    val ctl = arcPoint(FanHint.MID, FanDims.HINT_BOW_R)
    fun at(t: Float): Offset = e0 * ((1 - t) * (1 - t)) + ctl * (2 * t * (1 - t)) + e1 * (t * t)
    val path = Path()
    val steps = 24
    for (j in 0..steps) {
        val pt = map(at(j / steps.toFloat()))
        if (j == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    // The two heads: wings trailing back along the curve's own direction at each end.
    fun head(tip: Offset, back: Offset) {
        val len = back.getDistance().coerceAtLeast(0.001f)
        val tx = back.x / len
        val ty = back.y / len
        for (w in listOf(1f, -1f)) {
            val r = Math.toRadians(32.0 * w)
            val c = cos(r).toFloat()
            val n = sin(r).toFloat()
            val wing = Offset(tip.x + 9f * (tx * c - ty * n), tip.y + 9f * (tx * n + ty * c))
            val p0 = map(tip)
            val p1 = map(wing)
            path.moveTo(p0.x, p0.y)
            path.lineTo(p1.x, p1.y)
        }
    }
    head(e0, ctl - e0)
    head(e1, ctl - e1)
    drawPath(path, primary.copy(alpha = 0.78f), style = Stroke(width = 3.2f * unit * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // The light on the arc, with its glow.
    val d = map(at(((hint.dot.value - FanDims.HINT_A0) / (FanDims.HINT_A1 - FanDims.HINT_A0)).coerceIn(0f, 1f)))
    val r = 5f * unit * s
    drawCircle(Brush.radialGradient(listOf(tertiary.copy(alpha = 0.55f), Color.Transparent), d, r * 3.4f), r * 3.4f, d)
    drawCircle(tertiary.copy(alpha = 0.28f), r + 2f * unit * s, d)
    drawCircle(tertiary, r, d)
}
