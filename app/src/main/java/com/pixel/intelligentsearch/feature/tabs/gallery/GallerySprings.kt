package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/*
 * The swatch fan's and the full preview's motion, as the approved mockup (Direction B, refined) runs it: damped harmonic
 * oscillators integrated at 240 Hz on the frame clock, with staged moves ([After]) counted on the same clock, so a slow
 * frame can never pull a stage and the springs apart. Many springs, one draw per frame.
 */

/** A spring's stiffness and damping ratio: the M3 Expressive tokens. */
internal class SpringTok(val k: Float, val z: Float)

/*
 * The cards move as stiff paper with weight: Android 18's M3 Expressive spatial springs at 0.9 damping (no wobble, no
 * jelly), and [land] for the last settle into a slot, which overshoots once, slightly, and stops.
 */
internal object SP {
    /** Spatial default: a card picked up, slid out, turned toward the pull, nudged by the card filed over it. */
    val spatial = SpringTok(700f, 0.9f)
    /** Spatial fast: a card turning after the finger. */
    val fast = SpringTok(1400f, 0.9f)
    /** Spatial slow: the stack stepping back, the lapping edge, a full-screen card sliding in. */
    val slow = SpringTok(300f, 0.9f)
    /** Effects: a lift's shadow growing and shrinking (never overshoots). */
    val effect = SpringTok(1600f, 1f)
    /** The landing: a card coming down into its slot and turning straight, one slight overshoot (about 1%). */
    val land = SpringTok(600f, 0.82f)
}

internal interface Stepper {
    fun step(dt: Float)
}

/**
 * The frame clock everything here moves on. [run] steps the active springs and timers once a frame, then calls the
 * frame's draw once if anything moved. Idle, it waits without asking for frames.
 */
internal class MotionClock {
    private val active = LinkedHashSet<Stepper>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var kickedAt = -1L
    /** Something moved: the frame's draw runs. */
    var dirty = false

    fun add(s: Stepper) {
        if (active.add(s)) kick()
    }

    fun remove(s: Stepper) { active.remove(s) }
    fun has(s: Stepper) = s in active

    /** Asks for a frame (the draw runs in it). */
    fun kick() {
        dirty = true
        if (kickedAt < 0) kickedAt = System.nanoTime()
        wake.trySend(Unit)
    }

    suspend fun run(onFrame: () -> Unit) {
        var last = -1L
        while (true) {
            if (active.isEmpty() && !dirty) {
                last = -1L
                kickedAt = -1L
                wake.receive()
            }
            withFrameNanos { t ->
                val from = if (last >= 0) last else if (kickedAt >= 0) kickedAt else t - 16_000_000L
                val dt = ((t - from) / 1e9f).coerceIn(0.001f, 0.05f)
                last = t
                kickedAt = -1L
                for (s in active.toList()) if (s in active) s.step(dt)
                if (dirty) {
                    dirty = false
                    onFrame()
                }
            }
        }
    }
}

/**
 * A spring from [x] toward its target t. [glide] makes a long run: the target itself travels toward a goal at up to
 * `lim` a second (starting at the spring's own speed, so nothing jerks), and the spring follows it.
 */
internal class Spr(var x: Float, o: SpringTok, private val clock: MotionClock, private val eps: Float = 0.002f) : Stepper {
    var v = 0f
    var t = x
    private var goal = Float.NaN
    private var cv = 0f
    private var lim = 0f
    private var k = 0f
    private var c = 0f
    private var done: (() -> Unit)? = null
    /** A riffle: the target steps one card at a time toward the goal, every [stepDt] seconds. */
    private var stepDt = 0f
    private var stepLeft = 0f
    private var stepDir = 0f

    init { use(o) }

    fun use(o: SpringTok): Spr {
        k = o.k
        c = 2f * o.z * sqrt(o.k)
        return this
    }

    val active: Boolean get() = clock.has(this)

    fun to(target: Float, vel: Float? = null, o: SpringTok? = null, done: (() -> Unit)? = null): Spr {
        if (o != null) use(o)
        t = target
        goal = Float.NaN
        stepDt = 0f
        if (vel != null) v = vel
        this.done = done
        clock.add(this)
        clock.kick()
        return this
    }

    /**
     * A riffle toward [goal]: the target moves one whole step at a time, [rate] steps a second, and the spring takes each
     * step on its own, so a run of cards reads as separate cards, each tipping in its turn. [done] runs at rest.
     */
    fun riffle(goal: Float, vel: Float?, rate: Float, o: SpringTok?, done: (() -> Unit)? = null): Spr {
        to(x, vel, o, done)
        stepDir = sign(goal - x).let { if (it == 0f) 1f else it }
        stepDt = 1f / rate
        stepLeft = stepDt
        this.goal = goal
        // The first step is to the next whole card on the way.
        val first = if (stepDir > 0f) kotlin.math.floor(x) + 1f else kotlin.math.ceil(x) - 1f
        t = if (stepDir * (first - goal) >= 0f) goal.also { this.goal = Float.NaN } else first
        return this
    }

    fun glide(goal: Float, vel: Float?, lim: Float, o: SpringTok?): Spr {
        stepDt = 0f
        to(x, vel, o)
        val dir = sign(goal - x).let { if (it == 0f) 1f else it }
        cv = (dir * v).coerceIn(0f, 3f * lim)
        this.lim = lim
        this.goal = goal
        t = x + dir * min(abs(goal - x), c / k * cv)
        return this
    }

    /** Where it comes to rest. */
    val end: Float get() = if (goal.isNaN()) t else goal

    fun set(nx: Float): Spr {
        x = nx
        t = nx
        v = 0f
        goal = Float.NaN
        stepDt = 0f
        clock.remove(this)
        clock.kick()
        return this
    }

    fun stop() = clock.remove(this)

    override fun step(dt: Float) {
        val n = max(1, ceil(dt * 240f).toInt())
        val h = dt / n
        for (i in 0 until n) {
            if (!goal.isNaN() && stepDt > 0f) {
                stepLeft -= h
                if (stepLeft <= 0f) {
                    stepLeft += stepDt
                    t += stepDir
                    if (stepDir * (t - goal) >= 0f) {
                        t = goal
                        goal = Float.NaN
                    }
                }
            } else if (!goal.isNaN()) {
                cv += (lim - cv) * min(1f, h / 0.06f)
                val g = goal - t
                val m = cv * h
                if (abs(g) <= m) {
                    t = goal
                    goal = Float.NaN
                } else t += sign(g) * m
            }
            val a = -k * (x - t) - c * v
            v += a * h
            x += v * h
        }
        clock.dirty = true
        if (goal.isNaN() && abs(x - t) < eps && abs(v) < eps * 20f) {
            x = t
            v = 0f
            clock.remove(this)
            val d = done
            done = null
            d?.invoke()
        }
    }
}

