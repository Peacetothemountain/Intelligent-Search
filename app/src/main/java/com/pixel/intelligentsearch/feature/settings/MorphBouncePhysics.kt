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
 *   of its energy (its restitution), so each bounce is lower than the last, down to a minimum: like a trampoline,
 *   the bed always throws a shape back to a real height, so shapes never hop along, skitter or roll on the floor;
 * - contact friction turns sideways speed into spin, and spin into sideways speed, so shapes roll and tumble
 *   instead of turning at a constant rate;
 * - shapes collide with each other like balls sharing a trampoline, trading momentum by mass.
 *
 * Positions are body centres, y downwards, with the top of the animation area at 0 and the floor at [height].
 * Each new shape's sideways speed is chosen by simulating its flight beforehand, so it leaves the screen after
 * exactly the number of bounces it was given. Until it has made them, the side edges are soft walls, so a knock from
 * another shape can never send it out early; once it has, its next flight carries it off the screen. Bounces are
 * capped so a shape never rises above the top of the area.
 */
internal class MorphBouncePhysics(
    private val random: Random = Random.Default,
    val maxBodies: Int = 5,
    private val autoSpawn: Boolean = true
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
        /** 1 at the moment of a bounce, fading back to 0: the shape's brief brightening. */
        var glow = 0f
        /** True once the shape has come fully out from under the bottom card. */
        internal var entered = false
        /** Set by [step] on the step a shape leaves the trampoline. */
        internal var justLifted = false
        /** Seconds since the shape last left the trampoline. */
        internal var sinceLift = 10f
        /** Seconds the shape has been on the trampoline in its current contact. */
        internal var contactTime = 0f
        /** On its way off the screen after its planned bounces; nothing knocks it back. */
        internal var exiting = false
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
        if (first && autoSpawn && width > 0f && height > 0f) {
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
            if (autoSpawn) schedule(STEP)
            for (body in bodies) {
                step(body, STEP, listener)
                body.age += STEP
                if (body.justLifted && !body.exiting && (body.bounces >= body.plannedBounces || body.age > MAX_AGE)) sendOut(body)
            }
            collide()
            bodies.removeAll { it.x - it.radius > width || it.x + it.radius < 0f }
        }
    }

    /**
     * Shapes that touch bounce apart: an impulse along the line between their centres, shared by mass, with the
     * same springiness as the trampoline. Overlap is pushed out so they never sink into each other.
     */
    private fun collide() {
        for (i in 0 until bodies.size) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                // A shape whose centre has crossed a side edge is on its way out; nothing knocks it back or off.
                if (leaving(a) || leaving(b)) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val minDistance = a.radius + b.radius
                val distanceSquared = dx * dx + dy * dy
                if (distanceSquared >= minDistance * minDistance || distanceSquared < 1e-8f) continue
                val distance = sqrt(distanceSquared)
                val nx = dx / distance
                val ny = dy / distance
                val inverseA = 1f / a.mass
                val inverseB = 1f / b.mass
                // Push apart in proportion to how light each one is.
                val overlap = minDistance - distance
                val share = overlap / (inverseA + inverseB)
                a.x -= nx * share * inverseA; a.y -= ny * share * inverseA
                b.x += nx * share * inverseB; b.y += ny * share * inverseB
                // The push never lifts a shape that was inside the area out of it.
                for (body in arrayOf(a, b)) if (body.entered && body.y - body.radius < 0f) body.y = body.radius
                val approach = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if (approach >= 0f) continue
                val e = (a.restitution + b.restitution) / 2f
                val impulse = -(1f + e) * approach / (inverseA + inverseB)
                a.vx -= impulse * inverseA * nx; a.vy -= impulse * inverseA * ny
                b.vx += impulse * inverseB * nx; b.vy += impulse * inverseB * ny
                // A glancing hit sets them spinning in opposite senses.
                val tangential = (b.vx - a.vx) * -ny + (b.vy - a.vy) * nx
                a.spin += tangential / a.radius * 0.15f
                b.spin -= tangential / b.radius * 0.15f
                capRise(a)
                capRise(b)
            }
        }
    }

    /**
     * Keeps a knocked shape below the bottom card: one already fully in the area can rise only until its top edge
     * reaches the top of the area; one still coming in from under the card can't be knocked back up.
     */
    private fun capRise(body: Body) {
        val headroom = body.y - body.radius
        if (headroom < 0f) {
            if (body.vy < 0f) body.vy = 0f
            return
        }
        val maxUp = sqrt(2f * GRAVITY * headroom)
        if (-body.vy > maxUp) body.vy = -maxUp
    }

    /**
     * A shape that has made its planned bounces is given, as it leaves the trampoline, just enough sideways speed
     * to leave the screen before it lands again: in the direction it is going, or towards the nearer edge if it had
     * all but stopped.
     */
    private fun sendOut(body: Body) {
        val towardsRight = if (abs(body.vx) >= MIN_TRAVEL) body.vx > 0f else body.x > width / 2f
        val distance = if (towardsRight) width - body.x else body.x
        val needed = lowestSpeedFor(body, 0, distance, towardsRight, clearEdge = true) * 1.08f
        body.vx = (if (towardsRight) 1f else -1f) * maxOf(abs(body.vx), needed)
        body.exiting = true
    }

    private fun leaving(body: Body): Boolean = body.exiting || body.x < 0f || body.x > width

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
        body.vx = (if (towardsRight) 1f else -1f) * planSidewaysSpeed(body, bounces, distanceToEdge, towardsRight)
        bodies += body
        return body
    }

    /**
     * The sideways speed that makes [body] leave the screen during the flight after its [bounces]th bounce, found
     * by simulating the flight. Faster throws leave after fewer bounces, so the answer is the middle of the range
     * of speeds that give exactly [bounces].
     */
    internal fun planSidewaysSpeed(body: Body, bounces: Int, distanceToEdge: Float, towardsRight: Boolean): Float {
        if (distanceToEdge <= -body.radius) return 0.5f
        // Planned against touching the edge, where the wall would catch a shape that is not done yet.
        fun lowestSpeedFor(n: Int): Float = lowestSpeedFor(body, n, distanceToEdge, towardsRight, clearEdge = false)
        // No bounces left: just fast enough to leave before landing again.
        if (bounces <= 0) return (lowestSpeedFor(0) * 1.1f).coerceAtMost(8f)
        val atMostN = lowestSpeedFor(bounces)
        val atMostFewer = lowestSpeedFor(bounces - 1)
        return if (atMostFewer > atMostN) (atMostN + atMostFewer) / 2f else atMostN
    }

    /** The lowest sideways speed at which [body] leaves after at most [n] bounces (faster leaves sooner). */
    private fun lowestSpeedFor(body: Body, n: Int, distanceToEdge: Float, towardsRight: Boolean, clearEdge: Boolean): Float {
        fun bouncesAt(speed: Float): Int = simulateBouncesBeforeExit(body, speed, distanceToEdge, towardsRight, clearEdge)
        var lo = MIN_SIDEWAYS
        var hi = 8f
        if (bouncesAt(hi) > n) return hi
        repeat(24) {
            val mid = (lo + hi) / 2f
            if (bouncesAt(mid) <= n) hi = mid else lo = mid
        }
        return hi
    }

    /**
     * How many bounces [template] makes, thrown sideways at [speed], before it reaches the edge [distanceToEdge]
     * away: before it fully clears it if [clearEdge], otherwise before it first touches it.
     */
    private fun simulateBouncesBeforeExit(template: Body, speed: Float, distanceToEdge: Float, towardsRight: Boolean, clearEdge: Boolean): Int {
        // The flight is simulated moving right; a shape heading left is its mirror image, spin included (friction
        // with the trampoline couples spin and sideways speed, so an unmirrored spin would change the answer).
        val ghost = Body(-1, template.radius, template.mass, template.restitution, template.plannedBounces).apply {
            x = template.x; y = template.y; vx = speed; vy = template.vy
            spin = if (towardsRight) template.spin else -template.spin
            inContact = template.inContact
            exiting = true
        }
        // Only the distance to the edge matters: simulate moving right, that far from the right edge.
        ghost.x = width - distanceToEdge
        var t = 0f
        while (t < 30f) {
            step(ghost, STEP, null)
            if (if (clearEdge) ghost.x - ghost.radius > width else ghost.x + ghost.radius > width) return ghost.bounces
            if (abs(ghost.vx) < 1e-3f && !ghost.inContact && ghost.bounces > 20) return Int.MAX_VALUE
            t += STEP
        }
        return Int.MAX_VALUE
    }

    /** The upward speed of the smallest bounce the bed allows: a third of the height the shape could rise to. */
    private fun minimumRebound(body: Body): Float {
        val maxRise = (height - 2f * body.radius - TOP_MARGIN).coerceAtLeast(0f)
        return sqrt(2f * GRAVITY * maxRise * MIN_REBOUND_SHARE) * MIN_REBOUND_DRAG_ALLOWANCE
    }

    /** One integration step: air drag and gravity, then the trampoline when the body reaches the floor. */
    internal fun step(body: Body, dt: Float, listener: BounceListener?) {
        body.justLifted = false
        if (!body.entered && body.y - body.radius >= 0f) body.entered = true
        val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
        val k = body.dragK
        var ax = -k * speed * body.vx
        var ay = GRAVITY - k * speed * body.vy

        val floor = height - body.radius
        val penetration = body.y - floor
        if (penetration > 0f) {
            if (!body.inContact && body.vy > 0f) {
                body.inContact = true
                // A touch right after leaving the bed (a knock pushed it back down) is the same bounce, not a new
                // one: no new shape, no tap.
                if (body.sinceLift >= MIN_FLIGHT) {
                    val impact = body.vy
                    body.bounces++
                    body.shapeIndex++
                    body.morphProgress = 0f
                    body.glow = 1f
                    if (impact >= HAPTIC_MIN_SPEED) listener?.onBounce(body, impact)
                }
            }
            // Trampoline bed: a damped spring whose damping sets how much energy the bounce keeps.
            val zeta = dampingFor(body.restitution)
            ay -= BED_OMEGA * BED_OMEGA * penetration + 2f * zeta * BED_OMEGA * body.vy
            body.contactTime += dt
            // A trampoline never holds anything: a shape still on the bed after a normal bounce's time (one that
            // lost its speed to a knock) is thrown back up to the minimum bounce.
            if (body.contactTime > MAX_CONTACT_TIME && body.vy > -minimumRebound(body)) body.vy = -minimumRebound(body)
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
                body.justLifted = true
                body.sinceLift = 0f
                // Never rise above the top of the area: cap the rebound so the apex, even without drag, stays
                // below it.
                val maxRise = (height - 2f * body.radius - TOP_MARGIN).coerceAtLeast(0f)
                val maxUp = sqrt(2f * GRAVITY * maxRise)
                if (-body.vy > maxUp) body.vy = -maxUp
                // And never less than a real bounce: the bed throws it back to at least a third of the area.
                val minUp = minimumRebound(body)
                if (-body.vy < minUp) body.vy = -minOf(minUp, maxUp)
            }
            body.penetration = 0f
            body.contactTime = 0f
            body.spin *= exp(-SPIN_AIR_DECAY * dt)
        }

        if (!body.inContact) body.sinceLift += dt
        body.vx += ax * dt
        body.vy += ay * dt
        body.x += body.vx * dt
        body.y += body.vy * dt
        // Until it has made its planned bounces, a shape rebounds off the side edges rather than leaving.
        if (!body.exiting) {
            if (body.x < body.radius && body.vx < 0f) {
                body.x = body.radius
                body.vx = -body.vx * WALL_RESTITUTION
            } else if (body.x > width - body.radius && body.vx > 0f) {
                body.x = width - body.radius
                body.vx = -body.vx * WALL_RESTITUTION
            }
        }
        body.angle = (body.angle + Math.toDegrees((body.spin * dt).toDouble()).toFloat()) % 360f
        if (body.glow > 0f) {
            body.glow *= exp(-dt / GLOW_TIME_CONSTANT)
            if (body.glow < 0.01f) body.glow = 0f
        }
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
        /** The brightening on a bounce fades over roughly a fifth of a second. */
        const val GLOW_TIME_CONSTANT = 0.12f
        const val TOP_MARGIN = 0.02f
        const val STEP = 1f / 240f
        const val MAX_FRAME = 1f / 20f
        const val WAVE_GAP_MIN = 0.14f
        const val WAVE_GAP_MAX = 0.24f
        const val IDLE_FIRST_DELAY = 2.6f
        const val IDLE_GAP_MIN = 1.4f
        const val IDLE_GAP_MAX = 2.8f
        const val MAX_AGE = 8f
        const val MIN_SIDEWAYS = 0.02f
        /** A shape slowed by a knock keeps at least this sideways speed, so it always travels on. */
        const val MIN_TRAVEL = 0.35f
        const val WALL_RESTITUTION = 0.8f
        /** A trampoline touch sooner than this after leaving the bed belongs to the same bounce. */
        const val MIN_FLIGHT = 0.3f
        /** A normal bounce spends π/ω ≈ 80 ms on the bed; past this the bed throws the shape off. */
        const val MAX_CONTACT_TIME = 0.12f
        /** Every bounce reaches at least this share of the height a shape could rise to. */
        const val MIN_REBOUND_SHARE = 0.35f
        /** Launch a little faster than the drag-free figure, since air resistance takes some of the height. */
        const val MIN_REBOUND_DRAG_ALLOWANCE = 1.06f
        /** A shape turned back after a knock drifts at no more than this (or its own speed, if faster). */
        const val GENTLE_SIDEWAYS = 1.2f

        /** Damping ratio of a spring contact that returns [restitution] of the impact speed. */
        fun dampingFor(restitution: Float): Float {
            val l = ln(restitution.coerceIn(0.05f, 0.99f))
            return (-l / sqrt(Math.PI.toFloat() * Math.PI.toFloat() + l * l))
        }
    }
}
