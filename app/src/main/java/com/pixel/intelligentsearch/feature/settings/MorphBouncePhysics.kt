package com.pixel.intelligentsearch.feature.settings

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The physics behind the Material Morph animation: shapes that drop in from under the bottom card, bounce on the
 * bottom edge of the screen as if it were a trampoline, change shape on every bounce and leave by the sides.
 *
 * The scene is simulated in metres, drawn at one dp per centimetre, so a 20 dp shape behaves like a light
 * inflatable ball 20 cm across:
 * - gravity is real gravity, 9.81 m/s²;
 * - air resistance grows with the square of speed (drag coefficient of a sphere, density of air), so shapes have a
 *   natural top speed and slow down sideways as they fly;
 * - the floor is a spring-and-damper "trampoline bed": a shape sinks into it, is pushed back, and keeps only part
 *   of its energy (its restitution), so each bounce is lower than the last;
 * - contact friction turns sideways speed into spin, and spin into sideways speed, so shapes roll and tumble
 *   instead of turning at a constant rate.
 *
 * Positions are body centres, y downwards, with the top of the animation area at 0 and the floor at [height].
 * Each new shape's sideways speed is chosen by simulating its flight beforehand, so it leaves the screen after
 * exactly the number of bounces it was given. Bounces are capped so a shape never rises above the top of the area.
 */
internal class MorphBouncePhysics(
    private val random: Random = Random.Default,
    val maxBodies: Int = 5
) {
    class Body(
        val id: Int,
        val radius: Float,
        val mass: Float,
        val restitution: Float,
        val plannedBounces: Int
    ) {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        /** Clockwise spin, radians per second. */
        var spin = 0f
        var angle = 0f
        var bounces = 0
        /** Seconds since the body appeared; a safety limit removes any shape that somehow never leaves. */
        var age = 0f
        /** How far the body has sunk into the trampoline bed, metres; 0 in the air. */
        var penetration = 0f
        /** Which shape the body is changing into, counted up on every bounce. */
        var shapeIndex = 0
        /** 0 at the moment of a bounce, easing to 1 once the new shape has formed. */
        var morphProgress = 1f
        internal val dragK: Float get() = 0.5f * AIR_DENSITY * DRAG_COEFFICIENT * (Math.PI.toFloat() * radius * radius) / mass
        internal var inContact = false
    }

    /** A bounce worth feeling: emitted when a body lands on the trampoline faster than [HAPTIC_MIN_SPEED]. */
    fun interface BounceListener {
        fun onBounce(body: Body, impactSpeed: Float)
    }

    var width = 0f
        private set
    var height = 0f
        private set

    val bodies = mutableListOf<Body>()
    private var nextId = 0
    private var pendingWave = 0
    private var waveSlots: List<Int> = emptyList()
    private var waveTimer = 0f
    private var idleTimer = 0f
    private var accumulator = 0f

    /** Sizes the area, in metres. The first call starts the opening wave. */
    fun resize(widthMetres: Float, heightMetres: Float, radiiMetres: List<Float>) {
        val first = width == 0f
        width = widthMetres
        height = heightMetres
        radii = radiiMetres
        if (first && width > 0f && height > 0f) {
            pendingWave = (4 + random.nextInt(2)).coerceAtMost(maxBodies)
            waveSlots = (0 until pendingWave).shuffled(random)
            waveTimer = 0f
            idleTimer = IDLE_FIRST_DELAY
        }
    }

    private var radii: List<Float> = emptyList()

    /** Advances the scene by [frameSeconds] (clamped), in fixed small steps so results don't depend on frame rate. */
    fun advance(frameSeconds: Float, listener: BounceListener? = null) {
        if (width <= 0f || height <= 0f) return
        accumulator += frameSeconds.coerceIn(0f, MAX_FRAME)
        while (accumulator >= STEP) {
            accumulator -= STEP
            schedule(STEP)
            for (body in bodies) {
                step(body, STEP, listener)
                body.age += STEP
            }
            bodies.removeAll { it.x - it.radius > width || it.x + it.radius < 0f || it.age > MAX_AGE }
        }
    }

    private fun schedule(dt: Float) {
        if (pendingWave > 0) {
            waveTimer -= dt
            if (waveTimer <= 0f) {
                spawn(bounces = 2 + random.nextInt(3), slot = waveSlots.getOrElse(pendingWave - 1) { 0 }, slots = waveSlots.size)
                pendingWave--
                waveTimer = WAVE_GAP_MIN + random.nextFloat() * (WAVE_GAP_MAX - WAVE_GAP_MIN)
            }
            return
        }
        idleTimer -= dt
        if (idleTimer <= 0f) {
            val count = if (random.nextFloat() < 0.3f) 2 else 1
            repeat(count) { if (bodies.size < maxBodies) spawn(bounces = 2 + random.nextInt(2), slot = null, slots = 0) }
            idleTimer = IDLE_GAP_MIN + random.nextFloat() * (IDLE_GAP_MAX - IDLE_GAP_MIN)
        }
    }

    /** Drops a new shape in from just under the bottom card, aimed to leave after [bounces] bounces. */
    internal fun spawn(bounces: Int, slot: Int?, slots: Int): Body? {
        if (bodies.size >= maxBodies || radii.isEmpty()) return null
        val radius = radii[nextId % radii.size].coerceAtMost(height * 0.3f)
        // Light inflatable shells: mass grows with surface area.
        val mass = BASE_MASS * (radius / 0.1f) * (radius / 0.1f)
        val body = Body(
            id = nextId++,
            radius = radius,
            mass = mass,
            restitution = 0.82f + random.nextFloat() * 0.06f,
            plannedBounces = bounces
        )
        // The opening wave spreads across the width; later shapes come in anywhere.
        val fraction = if (slot != null && slots > 0) {
            (slot + 0.5f) / slots + (random.nextFloat() - 0.5f) * 0.08f
        } else {
            0.12f + random.nextFloat() * 0.76f
        }
        body.x = (width * fraction).coerceIn(radius, width - radius)
        body.y = -radius
        body.vy = 0.4f + random.nextFloat() * 0.8f
        body.shapeIndex = body.id * 7
        body.angle = random.nextFloat() * 360f
        // Usually towards the farther edge, so the shape crosses the screen.
        val towardsRight = if (body.x < width / 2f) random.nextFloat() < 0.75f else random.nextFloat() < 0.25f
        val distanceToEdge = if (towardsRight) width - body.x else body.x
        body.vx = (if (towardsRight) 1f else -1f) * planSidewaysSpeed(body, bounces, distanceToEdge)
        bodies += body
        return body
    }

    /**
     * The sideways speed that makes [body] leave the screen during the flight after its [bounces]th bounce, found
     * by simulating the flight. Faster throws leave after fewer bounces, so the answer is the middle of the range
     * of speeds that give exactly [bounces].
     */
    internal fun planSidewaysSpeed(body: Body, bounces: Int, distanceToEdge: Float): Float {
        if (distanceToEdge <= 0f) return 0f
        fun bouncesAt(speed: Float): Int = simulateBouncesBeforeExit(body, speed, distanceToEdge)
        // Lowest speed that leaves after at most n bounces.
        fun lowestSpeedFor(n: Int): Float {
            var lo = 0.02f
            var hi = 8f
            if (bouncesAt(hi) > n) return hi
            repeat(24) {
                val mid = (lo + hi) / 2f
                if (bouncesAt(mid) <= n) hi = mid else lo = mid
            }
            return hi
        }
        val atMostN = lowestSpeedFor(bounces)
        val atMostFewer = lowestSpeedFor(bounces - 1)
        return if (atMostFewer > atMostN) (atMostN + atMostFewer) / 2f else atMostN
    }

    private fun simulateBouncesBeforeExit(template: Body, speed: Float, distanceToEdge: Float): Int {
        val ghost = Body(-1, template.radius, template.mass, template.restitution, template.plannedBounces).apply {
            x = template.x; y = template.y; vx = speed; vy = template.vy
        }
        // Only the distance to the edge matters: simulate moving right, that far from the right edge.
        ghost.x = width - distanceToEdge
        var t = 0f
        while (t < 30f) {
            step(ghost, STEP, null)
            if (ghost.x - ghost.radius > width) return ghost.bounces
            if (abs(ghost.vx) < 1e-3f && !ghost.inContact && ghost.bounces > 20) return Int.MAX_VALUE
            t += STEP
        }
        return Int.MAX_VALUE
    }

    /** One integration step: air drag and gravity, then the trampoline when the body reaches the floor. */
    internal fun step(body: Body, dt: Float, listener: BounceListener?) {
        val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
        val k = body.dragK
        var ax = -k * speed * body.vx
        var ay = GRAVITY - k * speed * body.vy

        val floor = height - body.radius
        val penetration = body.y - floor
        if (penetration > 0f) {
            if (!body.inContact && body.vy > 0f) {
                body.inContact = true
                val impact = body.vy
                body.bounces++
                body.shapeIndex++
                body.morphProgress = 0f
                if (impact >= HAPTIC_MIN_SPEED) listener?.onBounce(body, impact)
            }
            // Trampoline bed: a damped spring whose damping sets how much energy the bounce keeps.
            val zeta = dampingFor(body.restitution)
            ay -= BED_OMEGA * BED_OMEGA * penetration + 2f * zeta * BED_OMEGA * body.vy
            // Friction with the bed: sideways speed and spin pull towards rolling.
            val rollSpeed = body.spin * body.radius
            val slip = body.vx - rollSpeed
            val grip = (CONTACT_GRIP * dt).coerceAtMost(1f)
            body.vx -= slip * grip * 0.35f
            body.spin += (slip / body.radius) * grip * 0.65f
            body.penetration = penetration
        } else {
            if (body.inContact && body.vy < 0f) {
                body.inContact = false
                // Never rise above the top of the area: cap the rebound so the apex, even without drag, stays
                // below it.
                val maxRise = (height - 2f * body.radius - TOP_MARGIN).coerceAtLeast(0f)
                val maxUp = sqrt(2f * GRAVITY * maxRise)
                if (-body.vy > maxUp) body.vy = -maxUp
            }
            body.penetration = 0f
            body.spin *= exp(-SPIN_AIR_DECAY * dt)
        }

        body.vx += ax * dt
        body.vy += ay * dt
        body.x += body.vx * dt
        body.y += body.vy * dt
        body.angle = (body.angle + Math.toDegrees((body.spin * dt).toDouble()).toFloat()) % 360f
        if (body.morphProgress < 1f) {
            body.morphProgress += (1f - body.morphProgress) * (1f - exp(-dt / MORPH_TIME_CONSTANT))
            if (body.morphProgress > 0.999f) body.morphProgress = 1f
        }
    }

    companion object {
        const val GRAVITY = 9.81f
        const val AIR_DENSITY = 1.2f
        const val DRAG_COEFFICIENT = 0.47f
        /** A 20 cm shape weighs 60 g, like a thin inflatable ball. */
        const val BASE_MASS = 0.06f
        /** How springy the bed is: a bounce lasts about π/ω ≈ 80 ms. */
        const val BED_OMEGA = 40f
        const val CONTACT_GRIP = 30f
        const val SPIN_AIR_DECAY = 0.35f
        /** The new shape is 90% formed about 100 ms after touching down, i.e. during the bounce. */
        const val MORPH_TIME_CONSTANT = 0.045f
        const val HAPTIC_MIN_SPEED = 1.0f
        const val TOP_MARGIN = 0.02f
        const val STEP = 1f / 240f
        const val MAX_FRAME = 1f / 20f
        const val WAVE_GAP_MIN = 0.14f
        const val WAVE_GAP_MAX = 0.24f
        const val IDLE_FIRST_DELAY = 2.6f
        const val IDLE_GAP_MIN = 1.4f
        const val IDLE_GAP_MAX = 2.8f
        const val MAX_AGE = 20f

        /** Damping ratio of a spring contact that returns [restitution] of the impact speed. */
        fun dampingFor(restitution: Float): Float {
            val l = ln(restitution.coerceIn(0.05f, 0.99f))
            return (-l / sqrt(Math.PI.toFloat() * Math.PI.toFloat() + l * l))
        }
    }
}
