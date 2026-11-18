package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import com.pixel.intelligentsearch.feature.tabs.ui.Symbols
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * All Looks as a paint swatch fan deck (the approved mockup, Direction B swatch fan): forty paper strips on one rivet,
 * stacked in order and never restacked. Strip i+1 always lies on strip i. The deck is read from the top: the first look
 * (Yours Now) is the top strip and the last (Seasonal) the bottom one, so look n is strip 39 - n ([stripOfLook]). One
 * strip stands upright (p, fractional while the fan turns); a strip's angle depends only on how far it is from p: under
 * it, the looks still to come fan out to the left; over it, the looks already seen are spread out to the right and down,
 * the last one seen only far enough to clear the upright strip's picture, the ones before it at growing angles, the rest
 * tucked close under the first. Turning to the next look swings the upright strip off to the right, off the one below;
 * turning back swings the nearest of the spread back over the upright strip.
 *
 * This file is the fan's model and motion, a straight port of the mockup's setupFan: the same angles, lens, flex,
 * lift, shadows, settle, pace and haptics. Lengths are the mockup's px in a 360 px wide box.
 */

/** A kind of look and the looks filed under it, in the order they sit in the fan. */
internal class FanGroup(val f: String, val name: String, val icon: ImageVector, val ids: List<String>)

internal val FAN_GROUPS: List<FanGroup> by lazy {
    listOf(
        // Yours Now (the search bar as it is set, GalleryLive.now) stands where Classic stood. Classic is still a look
        // (BUILT_IN_LOOKS): a saved copy, a look of the week or a kept look that names it resolves as before.
        FanGroup("colors", "Your Colors", Symbols.PaletteFilled, listOf(GalleryLive.NOW_ID, "square", "tide", "clay", "meadow")),
        FanGroup("light", "Light", Symbols.LightModeFilled, listOf("paper", "pastel", "linen", "sky", "peach", "mint")),
        FanGroup("dark", "Dark", Symbols.DarkModeFilled, listOf("ink", "neon", "midnight", "ember", "forest", "carbon")),
        FanGroup("glass", "Glass", Symbols.BlurOn, listOf("glass", "sunset", "lagoon", "rose", "smoke")),
        FanGroup("bold", "Bold", Symbols.Contrast, listOf("ring", "bold", "signal", "cobalt", "pop", "stencil")),
        FanGroup("quiet", "Quiet", Symbols.SelfImprovement, listOf("bare", "mono", "fog", "sand", "dusk")),
        FanGroup("seasonal", "Seasonal", Symbols.EcoFilled, listOf("harvest", "maple", "frost", "aurora", "bloom", "summer", "shore")),
    )
}

/** The looks in the fan's order, from the top of the deck down (the full preview steps through them in it). */
internal val FAN_ORDER: List<String> by lazy { FAN_GROUPS.flatMap { it.ids } }

internal fun fanLook(id: String): Look = if (id == GalleryLive.NOW_ID) GalleryLive.now else BUILT_IN_LOOKS.first { it.id == id }

/** The strip look [n] (its place in [FAN_ORDER]) is printed on: the first look is the top strip of the deck. */
internal fun stripOfLook(n: Int): Int = FAN_ORDER.size - 1 - n

/** The place in [FAN_ORDER] of the look on strip [i]. */
internal fun lookOfStrip(i: Int): Int = FAN_ORDER.size - 1 - i

/** The look printed on strip [i]. */
internal fun stripLookId(i: Int): String = FAN_ORDER[lookOfStrip(i)]
internal fun fanGroupOf(id: String): FanGroup = FAN_GROUPS.first { id in it.ids }

/** The fan's geometry and feel, as the mockup has them. */
internal object FanDims {
    const val SW = 116f
    const val SLEN = 267f
    const val FOOT = 15f
    /** The rivet, in the fan's 360 wide box, and the box's height. */
    const val RX = 113f
    const val RY = 280f
    const val FAN_H = 468f
    /** Where the rivet sits on a strip (from its top left). */
    const val OX = SW / 2f
    const val OY = SLEN - FOOT
    const val PIC_H = 158f
    /** The lens: eye distance, lean (degrees), lift, paper thickness, where the eye looks (from the rivet). */
    const val P = 900f
    const val TX = 4f
    const val TY = 18f
    const val L = 36f
    const val H = 0.3f
    const val CX = 60f
    const val CY = -60f
    /**
     * A strip per 88 dp of finger travel (on a strip, travel around the rivet at the finger's own distance from it); a
     * flick adds strips only from 1800 dp/s, one more per 1600 dp/s, three at most, and none once the drag has earned one.
     */
    const val STEP_DP = 88f
    const val SLOP = 8f
    const val FLING = 1800f
    const val PER_EXTRA = 1600f
    const val MAX_EXTRA = 3
    const val PACE = 1000f / 140f
    const val UNDER_SEEN = 9
    const val OVER_SEEN = 16
    const val A_UNDER = 8f
    /** Over the top strips of the deck (the first looks, which have no spread yet) the ones under open out further on the left, so the first look never stands alone. */
    const val A_UNDER_END = 16f
    const val UNDER_END_OVER = 6f
    const val S_UNDER = 4.2f
    const val A_OVER = 78f
    const val A_LAST = 116f
    const val S_OVER = 12.5f
    const val EMAX = 2.3f
    const val EGAP = 3.4f
    const val FLEX_MAX = 2.2f
    const val FLEX_AT = 600f
    const val FLEX_TAU = 0.045f
    const val SWING_LIFT = 7f
    const val SWING_SHADOW = 1.2f
    const val SETTLE = 0.9f
    const val SETTLE_W = 30f
    const val RAISE_LIFT = 26f
    const val RAISE_SHADOW = 1.6f
    /** Inside this distance (mockup px) of the rivet a drag counts sideways travel: the angle there is too jumpy. */
    const val ROUND_MIN = 26f
    /** A strip turned further than this (degrees) lies on the spread: pushing it up or down turns the fan. */
    const val SPREAD_A = 35f
    /**
     * Up or down on the spread turns the fan only as a push: setting off slower than this (dp per ms), and at least
     * [PUSH_ALONG] of the way along the arc. Faster, it is the page's scroll.
     */
    const val PUSH_SPEED = 0.45f
    const val PUSH_ALONG = 0.7f
    /** A push released past this much of a strip turns one look, unless the finger was pulling back at [BACK_V] dp/s. */
    const val COMMIT = 0.35f
    const val BACK_V = 250f
    /**
     * The gesture hint: a double-headed arrow on an arc [HINT_R] from the rivet, from [HINT_A0] to [HINT_A1] degrees
     * (clockwise from upright), in the wedge between the upright strip and the spread. The fan turns [HINT_NUDGE] of a
     * strip each way as its light runs along the arc. It goes for good after [HINT_TURNS] turns that change the look.
     */
    const val HINT_R = 140f
    const val HINT_A0 = 27f
    const val HINT_A1 = 51f
    /** The curve's control point lies this far from the rivet, midway: it bows about 12 px over its 58 px. */
    const val HINT_BOW_R = 162f
    /** Demo runs per rest of the fan before the hint sleeps until the next one. */
    const val HINT_RUNS = 3
    const val HINT_NUDGE = 0.13f
    const val HINT_TURNS = 3
}

/** A strip's angle (degrees) [d] strips above (+) or below (-) the upright one; [under] is how far the under-fan opens. */
internal fun fanAngle(d: Float, under: Float = FanDims.A_UNDER): Float {
    with(FanDims) {
        if (d <= 0f) return -under * (1f - exp(d * S_UNDER / under))
        if (d >= 1f) return A_OVER + (A_LAST - A_OVER) * (1f - exp(-(d - 1f) * S_OVER / (A_LAST - A_OVER)))
        // The swing between: it leaves and arrives as slowly as its neighbors move.
        val d2 = d * d
        val d3 = d2 * d
        return (d3 - 2f * d2 + d) * S_UNDER + (3f * d2 - 2f * d3) * A_OVER + (d3 - d2) * S_OVER
    }
}

/** 4 x 4 matrices, row-major, acting on column vectors: the mockup's CSS 3D transforms. */
private object M4 {
    fun id() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    fun t(x: Float, y: Float, z: Float = 0f) = id().also { it[3] = x; it[7] = y; it[11] = z }
    fun persp(d: Float) = id().also { it[14] = -1f / d }
    fun rx(deg: Float): FloatArray {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return id().also { it[5] = c; it[6] = -s; it[9] = s; it[10] = c }
    }

    fun ry(deg: Float): FloatArray {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return id().also { it[0] = c; it[2] = s; it[8] = -s; it[10] = c }
    }

    fun rz(deg: Float): FloatArray {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return id().also { it[0] = c; it[1] = -s; it[4] = s; it[5] = c }
    }

    fun skx(deg: Float) = id().also { it[1] = tan(Math.toRadians(deg.toDouble())).toFloat() }

    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(16)
        for (r in 0 until 4) for (c in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[r * 4 + k] * b[k * 4 + c]
            o[r * 4 + c] = s
        }
        return o
    }

    fun chain(vararg ms: FloatArray): FloatArray = ms.reduce { a, b -> mul(a, b) }

    /** What it does to the plane z = 0, as a 3 x 3 projective matrix (Android's order: scaleX, skewX, transX, ...). */
    fun plane(m: FloatArray) = floatArrayOf(m[0], m[1], m[3], m[4], m[5], m[7], m[12], m[13], m[15])
}

/** 3 x 3 projective helpers, in Android's value order. */
internal object M3 {
    fun ap(m: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val w = m[6] * x + m[7] * y + m[8]
        return (m[0] * x + m[1] * y + m[2]) / w to (m[3] * x + m[4] * y + m[5]) / w
    }

    fun inv(m: FloatArray): FloatArray? {
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val A = e * i - f * h
        val B = -(d * i - f * g)
        val C = d * h - e * g
        val det = a * A + b * B + c * C
        if (abs(det) < 1e-9f) return null
        return floatArrayOf(
            A / det, -(b * i - c * h) / det, (b * f - c * e) / det,
            B / det, (a * i - c * g) / det, -(a * f - c * d) / det,
            C / det, -(a * h - b * g) / det, (a * e - b * d) / det
        )
    }
}

/** What a strip shows this frame, written by [FanEngine.layout] and read while drawing only. */
internal class StripOut {
    var seen = true
    /** Its projective matrix in the fan box (mockup px), Android's value order. */
    val m = FloatArray(9).also { it[0] = 1f; it[4] = 1f; it[8] = 1f }
    var leftShadow = FanDims.EMAX
    var rightShadow = FanDims.EMAX
    var shade = 0f
    /** The further shade on its picture alone, the spread turned away from the light. */
    var away = 0f
    var a = 0f
}

/** How much of a strip's printed name shows [d] strips from the upright one: names on the spread are covered, so none. */
private fun nameShown(d: Float): Float = ((1f - d) / 0.4f).coerceIn(0f, 1f).let { (it * 4f).roundToInt() / 4f }

/**
 * The fan: [M] is where it stands (the upright strip, fractional while it turns), on a spring integrated at 960 Hz on
 * the frame clock; [Q] the give of the strips under one that lands; [Rz] a strip raised out of the deck for the full
 * preview.
 */
@Stable
internal class FanEngine(val clock: MotionClock, start: Int = 0) {
    val n = FAN_ORDER.size
    val out = List(n) { StripOut() }
    val frame = mutableIntStateOf(0)
    private val at0 = start.coerceIn(0, n - 1)
    /** The upright strip, to the nearest whole one; the side text follows it. */
    var top by mutableIntStateOf(at0)
        private set
    /** The strip the fan stands on or is turning to (a chip, a fling, a tap): the selected chip follows it. */
    var dest by mutableIntStateOf(at0)
        private set
    /** The strips that may be in sight around [top] (their pictures are kept in memory). */
    var near by mutableStateOf(max(0, at0 - FanDims.UNDER_SEEN)..min(n - 1, at0 + FanDims.OVER_SEEN))
        private set
    /** How much of each strip's printed name shows (all of it upright and under, none on the spread), in quarter steps. */
    val nameAlpha = List(n) { mutableFloatStateOf(nameShown((it - at0).toFloat())) }
    /** Haptics, from the page's View. */
    var onTick: () -> Unit = {}
    /** The fan came to rest on another look; [byHand] when a finger's drag turned it there (not a chip or TalkBack). */
    var onLand: (byHand: Boolean) -> Unit = {}
    /** The turn under way was started by a finger's drag. */
    private var byHand = false
    var onGrab: () -> Unit = {}

    private var quiet = true
    private var drawnP = at0.toFloat()
    private var lastNanos = 0L
    private var lastTick = 0L
    private var lastUp = at0
    /** The strip the fan last came to rest on: landing back on it is no news, so it plays nothing. */
    private var restAt = at0
    /** A long chip jump ticks only every so many strips, so it stays a riffle and not a buzz. */
    private var tickEvery = 1
    private var passes = 0
    /**
     * Under the finger, a strip's passing tick waits a moment ([HOLD_TICK]): a drag let go just past a strip springs
     * back onto it, and its landing then says it alone (grab, then CONFIRM), not a tick and a CONFIRM for one strip.
     */
    private var heldTick = -1
    private var heldAt = 0L
    private val th = FloatArray(n)
    private val pa = FloatArray(n) { Float.NaN }
    private val fx = FloatArray(n)
    private val seenBefore = BooleanArray(n) { true }

    /* The lens, once for the whole deck: the deck seen from close by, a little from its left and from below. */
    private val viewPre = M4.chain(
        M4.t(FanDims.CX, FanDims.CY), M4.persp(FanDims.P), M4.t(-FanDims.CX, -FanDims.CY), M4.t(0f, 0f, FanDims.L),
        M4.rx(FanDims.TX), M4.ry(FanDims.TY)
    )

    /** The settle: one push, critically damped, so the strips under a landing give and come back without ringing. */
    private var qx = 0f
    private var qv = 0f
    private var qOn = false
    private var qDir = 1
    private var qT = 0

    private fun settle(dir: Int, t: Int, amp: Float) {
        qDir = dir
        qT = t
        qv += amp * FanDims.SETTLE_W * E.toFloat()
        qOn = true
        clock.kick()
    }

    /** A strip raised out of the deck for the full preview, and dropped back into its slot. */
    private val Rz = Spr(0f, SP.spatial, clock, 0.002f)
    var raised = -1
        private set
    private var dropping = false

    /** The fan's place, on a spring. */
    private inner class Motion : Stepper {
        var x = 0f
        var v = 0f
        var t = 0f
        var goal = Float.NaN
        var pace = FanDims.PACE
        var total = 1f
        var k = 0f
        var c = 0f
        var landing = false
        var dirn = 1
        var vmax = 0f
        var from = 0f

        fun use(o: SpringTok) {
            k = o.k
            c = 2f * o.z * sqrt(o.k)
        }

        val active: Boolean get() = clock.has(this)

        fun hold() {
            clock.remove(this)
            v = 0f
            t = x
            goal = Float.NaN
            landing = false
        }

        fun set(nx: Float) {
            hold()
            x = nx
            t = nx
            clock.kick()
        }

        /**
         * Turns to [to]. A turn of a strip or so lands on the landing spring; a paced turn (a fling's extra strips, a far
         * tap, a chip) sends the target there at about one strip every 140 ms and the fan follows it on [travel].
         */
        fun go(to: Float, vel: Float = 0f, pace: Float = FanDims.PACE, travel: SpringTok = SP.spatial, pacedIn: Boolean = false) {
            val dd = to - x
            val dir = if (dd != 0f) sign(dd) else if (vel != 0f) sign(vel) else 1f
            val dist = abs(dd)
            val toward = dir * vel
            val paced = pacedIn || dist > 1.5f
            use(if (paced) travel else SP.land)
            landing = true
            dirn = dir.toInt()
            from = x
            if (paced) {
                // It leaves the finger at the pace it will keep, so every strip on the way takes its 140 ms or so.
                v = dir * toward.coerceIn(0f, pace * 1.1f)
                goal = to
                this.pace = pace
                total = dist
                t = x + dir * min(dist, c / k * dir * v)
            } else {
                // The finger's speed carries over, but never more than the landing can take up without running past.
                v = if (toward >= 0f) dir * min(toward, 0.3f * sqrt(k) * dist + 0.5f) else -dir * min(-toward, max(0f, (0.46f - dist) / 0.017f))
                goal = Float.NaN
                t = to
            }
            vmax = abs(v)
            clock.add(this)
            clock.kick()
        }

        override fun step(dt: Float) {
            val steps = max(1, ceil(dt * 960f).toInt())
            val h = dt / steps
            for (i in 0 until steps) {
                if (!goal.isNaN()) {
                    val g = goal - t
                    val m = pace * (0.9f + 0.2f * min(1f, abs(g) / total)) * h
                    if (abs(g) <= m) {
                        t = goal
                        goal = Float.NaN
                        use(SP.land)
                    } else t += sign(g) * m
                }
                v += (-k * (x - t) - c * v) * h
                x += v * h
            }
            vmax = max(vmax, abs(v))
            if (goal.isNaN()) {
                val e = abs(x - t)
                if (landing && e < 0.06f) {
                    // Within a degree of upright: it is down.
                    landing = false
                    val T = t.roundToInt()
                    lastUp = T
                    if (T != restAt) {
                        restAt = T
                        onLand(byHand)
                    }
                    if (abs(t - from) > 0.15f) settle(dirn, T, FanDims.SETTLE * (vmax / FanDims.PACE).coerceIn(0.3f, 1f))
                }
                if (e < 0.0004f && abs(v) < 0.01f) {
                    x = t
                    v = 0f
                    clock.remove(this)
                }
            }
            clock.dirty = true
        }
    }

    private val M = Motion().also { it.x = at0.toFloat(); it.t = at0.toFloat() }

    private fun end() = if (M.goal.isNaN()) M.t else M.goal

    val p: Float get() = M.x
    val moving: Boolean get() = M.active

    /** The hint's small turn of the whole fan, on top of where it stands (no ticks: it never reaches another strip). */
    var ghost = 0f
        set(v) {
            if (field != v) {
                field = v
                clock.kick()
            }
        }
    /** Nothing going on: no finger on the fan, the fan at rest and no strip raised. The hint plays only then. */
    var calm by mutableStateOf(true)
        private set
    /** A finger is turning the fan: the hint's light follows it. */
    var pulling by mutableStateOf(false)
        private set
    /** How far that drag has turned the fan toward the next look (strips; negative, back), from the look it started on. */
    val pull: Float get() = from - M.x
    /** The hint's arc in the fan box: the rivet's plane through the lens, as the strips' own matrices are made. */
    val hintPlane: FloatArray = M4.plane(M4.chain(M4.t(FanDims.RX, FanDims.RY), viewPre))

    private fun seenAt(i: Int, p: Float) = i == n - 1 || (i - p > -FanDims.UNDER_SEEN && i - p < FanDims.OVER_SEEN)

    private fun shadowTurn(gap: Float) = FanDims.EMAX * (1f - exp(-gap / FanDims.EGAP))

    /** One frame: every strip where the fan puts it. */
    fun layout() {
        val p = if (ghost == 0f) M.x else (M.x + ghost).coerceIn(-0.2f, n - 0.8f)
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L || now - lastNanos > 50_000_000L) 1f / 60f else max(0.001f, (now - lastNanos) / 1e9f)
        lastNanos = now
        if (qOn) {
            val steps = ceil(dt * 960f).toInt()
            val h = dt / steps
            for (s in 0 until steps) {
                qv += (-FanDims.SETTLE_W * FanDims.SETTLE_W * qx - 2f * FanDims.SETTLE_W * qv) * h
                qx += qv * h
            }
            if (abs(qx) < 0.003f && abs(qv) < 0.1f) {
                qx = 0f
                qv = 0f
                qOn = false
            }
        }
        // A strip landing from the right pushes the upright strip and the ones under it a little to the left; one landing
        // on the spread pushes the spread a little further round. Less and less with every strip away from it.
        // Over the top strips the under-fan opens out, so the looks still to come show on the left.
        val endT = ((p - (n - 1 - FanDims.UNDER_END_OVER)) / FanDims.UNDER_END_OVER).coerceIn(0f, 1f)
        val under = FanDims.A_UNDER + (FanDims.A_UNDER_END - FanDims.A_UNDER) * endT * endT * (3f - 2f * endT)
        for (i in 0 until n) {
            val q = when {
                qx == 0f -> 0f
                qDir > 0 -> if (i <= qT) -qx * exp((i - qT) * 0.55f) else 0f
                else -> if (i > qT) qx * exp((qT + 1 - i) * 0.55f) else 0f
            }
            th[i] = fanAngle(i - p, under) + q
        }
        if (dropping && Rz.x < 0.12f) {
            dropping = false
            settle(1, raised, FanDims.SETTLE * 0.45f)
        }
        val ease = 1f - exp(-dt / FanDims.FLEX_TAU)
        val up = max(0f, Rz.x)
        var busy = qOn
        for (i in 0 until n) {
            val o = out[i]
            val a = th[i]
            val seen = seenAt(i, p)
            if (seen != seenBefore[i]) {
                seenBefore[i] = seen
                pa[i] = Float.NaN
            }
            o.seen = seen
            if (!seen) {
                pa[i] = a
                fx[i] = 0f
                continue
            }
            val w = if (quiet || pa[i].isNaN()) 0f else (a - pa[i]) / dt
            pa[i] = a
            fx[i] = if (quiet) 0f else fx[i] + ((w / FanDims.FLEX_AT).coerceIn(-1f, 1f) * FanDims.FLEX_MAX - fx[i]) * ease
            if (abs(fx[i]) < 0.004f && abs(w) < 0.5f) fx[i] = 0f else busy = true
            val m = abs(fx[i]) / FanDims.FLEX_MAX
            val held = if (i == raised) up else 0f
            val lift = FanDims.SWING_LIFT * m + FanDims.RAISE_LIFT * held
            val mat = M4.chain(
                M4.t(FanDims.RX, FanDims.RY), viewPre, M4.t(0f, 0f, i * FanDims.H + lift), M4.rz(a), M4.skx(fx[i]), M4.t(-FanDims.OX, -FanDims.OY)
            )
            M4.plane(mat).copyInto(o.m)
            o.a = a
            // Its left edge throws a shadow on the strip under it, its right edge on the page where the strip over it has
            // swung clear: each as wide as the gap on that side, and wider while the strip is off the deck.
            val more = FanDims.SWING_SHADOW * m + FanDims.RAISE_SHADOW * held
            o.leftShadow = (if (i == 0 || !seenAt(i - 1, p)) FanDims.EMAX else shadowTurn(a - th[i - 1])) + more
            o.rightShadow = (if (i == n - 1) FanDims.EMAX else shadowTurn(th[if (seenAt(i + 1, p)) i + 1 else n - 1] - a)) + more
            // It stands in the shade of the strips that lie close over it.
            var over = 0f
            for (j in i + 1 until n) over += exp(-(th[j] - a) / 8f)
            // The spread lies turned away from the light, the further round the darker, so the strip lying on top of it
            // (the last look) doesn't outshine the upright one.
            // That turning away dims the picture only: the paper keeps its tone against the page.
            val d = i - p
            val away = if (d > 1f) 0.2f * (1f - exp(-(d - 1f) / 3f)) else 0f
            o.shade = min(0.38f, 0.075f * over)
            o.away = min(0.38f, o.shade + away) - o.shade
            val na = nameShown(d)
            if (nameAlpha[i].floatValue != na) nameAlpha[i].floatValue = na
        }
        // A tick as a strip passes upright on the way (the one the fan lands on gets the landing instead).
        if (!quiet && p != drawnP) {
            val k = if (p > drawnP) floor(p).toInt() else ceil(p).toInt()
            if ((drawnP - k) * (p - k) <= 0f && drawnP != k.toFloat() && k in 0 until n && k != lastUp && !(M.landing && k == end().roundToInt())) {
                val t = System.nanoTime()
                lastUp = k
                passes++
                if (passes % tickEvery == 0 && t - lastTick >= 40_000_000L) {
                    lastTick = t
                    if (dragging) {
                        if (heldTick >= 0) onTick()
                        heldTick = k
                        heldAt = t
                    } else onTick()
                }
            }
        }
        // A held tick plays once the fan is well past its strip, or the moment is over with the finger still down.
        if (heldTick >= 0) {
            if (abs(p - heldTick) > 0.5f || System.nanoTime() - heldAt >= HOLD_TICK) {
                heldTick = -1
                onTick()
            } else busy = true
        }
        drawnP = p
        val topNow = p.roundToInt().coerceIn(0, n - 1)
        if (topNow != top) {
            top = topNow
            near = max(0, topNow - FanDims.UNDER_SEEN)..min(n - 1, topNow + FanDims.OVER_SEEN)
        }
        val destNow = end().roundToInt().coerceIn(0, n - 1)
        if (destNow != dest) dest = destNow
        val c = !held && !M.active && raised < 0 && !Rz.active
        if (c != calm) calm = c
        quiet = false
        frame.intValue++
        if (busy) clock.kick()
    }

    /** Past either end the fan gives a little and comes back: never a hard stop, never as far as the next strip. */
    private fun band(p: Float): Float {
        val last = (n - 1).toFloat()
        return when {
            p < 0f -> -(1f - 1f / (1f - p * 1.4f)) * 0.2f
            p > last -> last + (1f - 1f / (1f + (p - last) * 1.4f)) * 0.3f
            else -> p
        }
    }

    /**
     * A chip: turn to that look a strip at a time on the slow spring. A long way goes quicker, so even the whole deck
     * takes about a second, and ticks only every few strips (twelve ticks at most).
     */
    fun show(i: Int) {
        val to = i.coerceIn(0, n - 1)
        val dist = abs(to - M.x)
        tickEvery = max(1, ceil(dist / 12f).toInt())
        passes = 0
        byHand = false
        M.go(to.toFloat(), 0f, pace = max(FanDims.PACE, dist / 0.85f), travel = SP.slow)
        dest = to
    }

    /** Turns to the look before or after (TalkBack's actions), as a tap on its strip would. */
    fun turnTo(i: Int) {
        val to = i.coerceIn(0, n - 1)
        tickEvery = 1
        byHand = false
        M.go(to.toFloat())
        dest = to
    }

    /** Whether a mouse wheel or trackpad can turn the fan [dir] (1, the next look): not past either end of the deck. */
    fun canWheel(dir: Int): Boolean = (dest - dir) in 0 until n

    /**
     * A notch of a mouse wheel or a step of a trackpad swipe: one look on ([dir] 1, the strip under the upright one) or
     * back, landing as a turn by hand does. A finger on the fan keeps it.
     */
    fun wheel(dir: Int) {
        if (held || !canWheel(dir)) return
        val to = dest - dir
        tickEvery = 1
        byHand = false
        M.go(to.toFloat())
        dest = to
    }

    /** Stands [i] upright at once, without a tick (closing the preview on another look comes home to its strip). */
    fun toFront(i: Int) {
        quiet = true
        lastUp = i
        restAt = i
        M.set(i.toFloat())
        layout()
    }

    /** The strip [i] lifts out of the deck (or at once, [now]) for the full preview, or drops back into its slot. */
    fun raise(on: Boolean, i: Int = raised, now: Boolean = false) {
        if (i >= 0) raised = i
        dropping = !on && !now && raised >= 0
        if (now) Rz.set(if (on) 1f else 0f)
        else if (on) Rz.to(1f, null, SP.spatial)
        else Rz.to(0f, null, SP.land) { if (!dropping) raised = -1 }
        clock.kick()
    }

    /** Where the upright strip's picture lies in the fan box (mockup px), as the corners' bounding box. */
    fun pictureBox(i: Int): Rect {
        val m = out[i].m
        val pts = listOf(0f to 0f, FanDims.SW to 0f, 0f to FanDims.PIC_H, FanDims.SW to FanDims.PIC_H).map { M3.ap(m, it.first, it.second) }
        return Rect(pts.minOf { it.first }, pts.minOf { it.second }, pts.maxOf { it.first }, pts.maxOf { it.second })
    }

    /** The strip under the point (mockup px in the fan box), the topmost one there. */
    fun stripAt(x: Float, y: Float): Int {
        for (i in n - 1 downTo 0) {
            val o = out[i]
            if (!o.seen) continue
            val inv = M3.inv(o.m) ?: continue
            val (lx, ly) = M3.ap(inv, x, y)
            if (lx in 0f..FanDims.SW && ly in 0f..FanDims.SLEN) return i
        }
        return -1
    }

    // ---- The finger ----

    /** Dp per mockup px: the fan's scale on this screen (set by the page), for finding the rivet and the strips. */
    var scale = 1f
    private var held = false
    private var x0 = 0f
    private var y0 = 0f
    private var ox = 0f
    private var p0 = 0f
    private var from = 0
    /** On a strip, and on one of the spread. */
    private var onStrip = false
    private var onSpread = false
    /** The finger around the rivet: its angle (radians, clockwise from upright) and distance (dp). */
    private var ang = 0f
    private var rad = 0f
    private var lastX = 0f
    /** When the finger was last still (within a dp of where it went down): how fast it then set off tells a push from a scroll. */
    private var stillAt = 0L
    /** How far the finger has gone around the rivet (dp, clockwise positive), and its last tenth of a second of it. */
    private var travel = 0f
    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var lastMove = 0L
    var dragging = false
        private set
    var dead = false
        private set
    private var caught = false

    /** The rivet, where the lens puts it, in dp in the fan box. */
    private fun rivet(): Pair<Float, Float> {
        val s = FanDims.P / (FanDims.P - FanDims.L)
        return (FanDims.RX + FanDims.CX - FanDims.CX * s) * scale to (FanDims.RY + FanDims.CY - FanDims.CY * s) * scale
    }

    private fun polar(x: Float, y: Float): Pair<Float, Float> {
        val (rx, ry) = rivet()
        val dx = x - rx
        val dy = y - ry
        return atan2(dx, -dy) to sqrt(dx * dx + dy * dy)
    }

    /** A finger down at ([x], [y]) dp at [t] ms. A hand on a turning fan stops it where it is (unless it has as good as landed). */
    fun down(x: Float, y: Float, t: Long) {
        held = true
        dragging = false
        dead = false
        calm = false
        x0 = x
        y0 = y
        lastX = x
        stillAt = t
        travel = 0f
        samples.clear()
        samples.addLast(t to 0f)
        lastMove = t
        val i = stripAt(x / scale, y / scale)
        onStrip = i >= 0
        onSpread = i >= 0 && out[i].a > FanDims.SPREAD_A
        from = M.x.roundToInt().coerceIn(0, n - 1)
        polar(x, y).let { ang = it.first; rad = it.second }
        caught = M.active && !(M.goal.isNaN() && abs(M.x - M.t) < 0.05f && abs(M.v) < 1.5f)
        if (caught) M.hold()
    }

    /**
     * Returns true while the fan has the finger. On a strip what counts is how far the finger goes around the rivet, at
     * its own distance from it, so pushing a strip of the spread up brings it upright and pulling the upright one down
     * swings it away; off the strips (or right at the rivet) a sideways swipe turns it the same way. Up or down belongs
     * to the page, except on a strip of the spread.
     */
    fun move(x: Float, y: Float, t: Long): Boolean {
        if (!held || dead) return false
        val (a, r) = polar(x, y)
        var da = a - ang
        if (da > PI) da -= (2 * PI).toFloat()
        if (da < -PI) da += (2 * PI).toFloat()
        val minR = FanDims.ROUND_MIN * scale
        travel += if (onStrip && r > minR && rad > minR) da * (r + rad) / 2f else x - lastX
        ang = a
        rad = r
        lastX = x
        samples.addLast(t to travel)
        while (samples.size > 32) samples.removeFirst()
        lastMove = t
        val dx = x - x0
        val dy = y - y0
        if (!dragging) {
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= 1f) stillAt = t
            if (dist <= FanDims.SLOP) return false
            if (abs(dy) > abs(dx) && !(onSpread && isPush(dist, t))) {
                dead = true
                return false
            }
            dragging = true
            pulling = true
            ox = travel
            p0 = M.x
            tickEvery = 1
            onGrab()
        }
        // The fan follows the finger: 88 dp a strip, no snapping.
        M.set(band(p0 - (travel - ox) / FanDims.STEP_DP))
        return true
    }

    /**
     * Up or down on the spread is a push on a swatch, not a page scroll, only when it sets off slowly (under
     * [FanDims.PUSH_SPEED] from where the finger was last still), follows the arc around the rivet, and the fan can
     * still turn that way. A quick flick, or a push past either end of the deck, scrolls the page.
     */
    private fun isPush(dist: Float, t: Long): Boolean {
        val speed = dist / (t - stillAt).coerceAtLeast(1L)
        if (speed > FanDims.PUSH_SPEED) return false
        if (abs(travel) < FanDims.PUSH_ALONG * dist) return false
        val towards = M.x - sign(travel)
        return towards >= -0.5f && towards <= n - 0.5f
    }

    /** The finger lifts at [t] ms. Returns whether it was a tap (the caller finds what it was on). */
    fun up(t: Long): Boolean {
        if (!held) return false
        held = false
        pulling = false
        clock.kick()
        if (!dragging) {
            if (caught) {
                M.go(M.x.roundToInt().coerceIn(0, n - 1).toFloat())
                return false
            }
            return !dead
        }
        dragging = false
        byHand = true
        // The release speed (dp/s around the rivet) over the finger's last tenth of a second; a finger that rested a
        // tenth of a second before lifting hands over none.
        val recent = samples.filter { lastMove - it.first <= 100 }
        val v = if (t - lastMove > 100 || recent.size < 2) 0f
        else (recent.last().second - recent.first().second) / ((recent.last().first - recent.first().first).coerceAtLeast(8) / 1000f)
        // Let go: it settles on the nearest strip. A real fling (1800 dp/s and up) turns one look more, and one more for
        // every further 1600 dp/s, three at most, each passing in its own 140 ms or so; but none once the drag itself
        // has turned the fan a strip or more.
        val a = abs(v)
        val near = M.x.roundToInt().coerceIn(0, n - 1)
        val extra = if (near == from && a >= FanDims.FLING) min(FanDims.MAX_EXTRA, 1 + ((a - FanDims.FLING) / FanDims.PER_EXTRA).toInt()) else 0
        // A deliberate push that has carried the strip past [FanDims.COMMIT] of the way, and isn't being pulled back,
        // turns one look that way, rather than dropping back.
        val moved = M.x - from
        val committed = near == from && extra == 0 && abs(moved) >= FanDims.COMMIT && !(a >= FanDims.BACK_V && sign(-v) != sign(moved))
        val target = (if (committed) from + sign(moved).toInt() else near - sign(v).toInt() * extra).coerceIn(0, n - 1)
        lastSwipe = target - p0.roundToInt()
        // The strip just passed under the finger is where it lands: its landing says so, without the passing tick.
        if (heldTick >= 0) {
            if (heldTick != target || target == restAt) onTick()
            heldTick = -1
        }
        M.go(target.toFloat(), -v / FanDims.STEP_DP, pacedIn = extra > 0)
        return false
    }

    /** The strips the last swipe turned the fan by, in all (for checking the mapping in the log). */
    var lastSwipe = 0
        private set

    /** A tap on strip [i]: the upright one opens ([open]); one peeking out comes upright, the fan turning from its side. */
    fun tap(i: Int, open: (Int) -> Unit) {
        if (i < 0) return
        if (i == top && abs(M.x - i) < 0.05f) open(i)
        else if (i.toFloat() != end()) {
            tickEvery = 1
            byHand = false
            M.go(i.toFloat())
        }
    }

    init { layout() }

    private companion object {
        const val HOLD_TICK = 150_000_000L
    }
}
