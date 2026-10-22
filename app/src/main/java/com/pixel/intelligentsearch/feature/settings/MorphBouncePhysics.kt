package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.util.clampSafely
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The physics behind the Material Morph animation: heavy shapes that drop in from under the bottom card onto a floor
 * at the bottom of the screen, sink into it the first time as if it weren't really there, are sprung back up by it,
 * and the second time bounce off it, hard, out past a side of the screen.
 *
 * The scene is simulated in metres, drawn at one dp per centimetre (smaller in an area too short for that, see
 * [morphSceneFor]), so gravity, 9.81 m/s², is 981 dp/s² on screen: real gravity, scaled with the screen's density.
 * Everything else is real physics too:
 * - the shapes are dense and heavy, stone slabs [SLAB_THICKNESS] thick, so their mass follows their area and air
 *   drag (quadratic, a rounded body's drag coefficient) barely slows them;
 * - they spin as solid discs do: contact friction with the floor, and between shapes, trades sideways speed for
 *   spin and back (moment of inertia ½mr²), so a shape that lands moving sideways leaves the floor rolling;
 * - shapes knock into each other like the hard objects they are, trading momentum by mass.
 *
 * The floor, like the street the first time Neo jumps in The Matrix, isn't hard until a shape has fallen through it
 * once. Each shape's first landing is on a soft membrane: a spring and damper that gives under its weight, so the
 * shape sinks about half its height through the floor line ([SINK_MIN] to [SINK_MAX] of it), leaving its silhouette
 * in the floor (an [Imprint]), before the membrane springs back, overshooting past its rest line, and throws the
 * shape back up. Its second landing is on a real floor: a hard, lively, instantaneous bounce (restitution about
 * 0.8, Coulomb friction) that sends it flying up and off the screen past a side edge, spinning. It leaves only that
 * way, by flying out of the area: nothing fades or shrinks, and a shape is removed only once it, and its shadow,
 * are fully past the edge. The silhouette it left in the floor heals after it has gone.
 *
 * The floor line sits [FLOOR_DEPTH_SHARE] of the largest shape's height above the bottom of the area, so a shape
 * sunk into the floor, and its silhouette, are still on screen. Positions are body centres, y downwards, with the top
 * of the area at 0, the floor line at [floor] and the bottom of the area at [height].
 *
 * Shapes come at random, never more than [MAX_SHAPES] at a time: sometimes one, sometimes a burst of two or three,
 * with uneven gaps, each from a random place under the card, thrown a random way with a random spin. Each throw is
 * checked by simulating it beforehand, so a shape makes both landings on screen; until it has made them, the side
 * edges are walls, so a knock from another shape can never send it out early. No shape rises above the top of the
 * area, and every one has left within [MAX_LIFETIME] of appearing.
 *
 * The simulation steps at a fixed [STEP] whatever the frame rate, so a run is the same at 60, 90 or 120 Hz, and a
 * seed always gives the same run; [renderFraction] lets drawing interpolate between steps.
 *
 * How each shape looks as it moves (its morph, squash and scale-in, see [animateLook]) runs on the springs of
 * Material 3 Expressive's motion scheme (androidx.compose.material3 ExpressiveMotionTokens; spatial springs, which
 * overshoot; https://m3.material.io/styles/motion/overview), stepped with the physics. None of it feeds back into the
 * motion: the landings, the haptics and every rule above are the physics' alone.
 */
internal class MorphBouncePhysics(
    private val random: Random = Random.Default,
    val maxBodies: Int = MAX_SHAPES,
    private val autoSpawn: Boolean = true
) {
    class Body(val id: Int, val radius: Float, val mass: Float) {
        /** A solid disc's moment of inertia, kg m². */
        val inertia = INERTIA_SHARE * mass * radius * radius
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        /** Clockwise spin, radians per second. */
        var spin = 0f
        var angle = 0f
        /** Where the body was one [STEP] ago, for drawing between steps. */
        internal var prevX = 0f
        internal var prevY = 0f
        internal var prevAngle = 0f
        /** Landings so far: the first on the soft membrane, every one after it on the hard floor. */
        var bounces = 0
        /** Seconds since the body appeared. */
        var age = 0f
        /** How far the body's lowest point is below the floor line, metres; 0 above it. */
        var penetration = 0f
        /** How deep the body sinks into the membrane, as a share of its height (drawn at spawn). */
        internal var sinkShare = 0.5f
        /** The share of its landing speed the hard floor gives back (drawn at spawn). */
        internal var liveliness = 0.8f
        /** Decides which edge a shape that has all but stopped leaves by (drawn at spawn). */
        internal var exitBias = 0f
        /** The membrane's natural frequency for this landing, rad/s, set as the shape lands on it. */
        internal var membraneOmega = 0f
        /** Which shape the body is changing into, counted up on every bounce. */
        var shapeIndex = 0
        /**
         * 0 at the moment of a bounce, springing to 1 once the new shape has formed, on Material 3 Expressive's fast
         * spatial spring ([MORPH_DAMPING], [MORPH_STIFFNESS]): it overshoots 1 a little and settles, as Google's
         * morphing loading indicator does. Exactly 1 once settled.
         */
        var morphProgress = 1f
        internal var morphVelocity = 0f
        /** 1 at the moment of a bounce, fading back to 0: the shape's brief brightening. */
        var glow = 0f
        /**
         * How flat the shape is drawn: positive squashed flat and wide, negative stretched tall in the wobble after a
         * hard bounce; never past [MAX_SQUASH] or [MAX_JELLY_STRETCH]. Drawn with its area kept. Dense as they are,
         * the shapes give little: it is the floor that gives.
         */
        var squash = 0f
        internal var squashVelocity = 0f
        /** 0 as the shape appears from under the card, springing to 1 (its full size) on the default spatial spring. */
        var appear = 0f
        internal var appearVelocity = 0f
        /** +1 if the shape turns clockwise as it morphs (it came in heading right), -1 if anticlockwise. */
        var turnDirection = 1f
        /** True once the shape has come fully out from under the bottom card. */
        internal var entered = false
        /** Seconds since the shape last left the floor. */
        internal var sinceLift = 10f
        /** Seconds the shape has been in the membrane in its current landing. */
        internal var contactTime = 0f
        /** Bounced off the hard floor, on its way off the screen; nothing knocks it back. */
        internal var exiting = false
        /** Which side an [exiting] shape leaves by. */
        internal var exitTowardsRight = false
        internal var inContact = false
        /** False for the planning ghosts [spawn] simulates: no side walls, no silhouettes. */
        internal var real = true
        /** The silhouette this body is pressing into the membrane, while it is in it. */
        internal var imprint: Imprint? = null
        internal val dragK: Float = 0.5f * AIR_DENSITY * DRAG_COEFFICIENT * (2f * radius * SLAB_THICKNESS) / mass
    }

    /**
     * The silhouette a shape leaves where it sank into the floor: its outline at its deepest, and the floor's
     * surface there, which follows the shape down and, once the shape is thrown back up, springs back past its rest
     * line and rings out. The silhouette lingers for [HEAL_DELAY], then heals over [HEAL_TIME].
     */
    class Imprint {
        var active = false
        /** The body that made it, for its colour. */
        var bodyId = 0
        var x = 0f
        var y = 0f
        var radius = 0f
        /** How far below the floor line the shape's lowest point reached. */
        var depth = 0f
        /** The shape it was pressed by, as [Body.shapeIndex] and [Body.morphProgress], and how it was turned. */
        var shapeIndex = 0
        var morphProgress = 1f
        var rotationDegrees = 0f
        var squash = 0f
        /** True while its shape is still in it. */
        var holding = false
        /** Seconds since its shape first pressed into it: the soft landing's ripples spread from that moment. */
        var sincePressed = 0f
        var sinceRelease = 0f
        /** 1 fresh, falling to 0 as it heals. */
        var strength = 0f
        /** The floor's surface at the silhouette, metres below the floor line (negative bulging above it). */
        var surface = 0f
        var surfaceVelocity = 0f
    }

    /**
     * The one sharp ripple a hard landing sends across the floor: where the shape struck and its outline then (as
     * [Body.shapeIndex], [Body.morphProgress] and how it was turned), and how long ago, for drawing only. Gone after
     * [HARD_RIPPLE_TIME].
     */
    class Ripple {
        var active = false
        /** The body that struck, for its colour. */
        var bodyId = 0
        var x = 0f
        var radius = 0f
        var shapeIndex = 0
        var morphProgress = 1f
        var rotationDegrees = 0f
        var age = 0f
    }

    /** A shape's contact shadow on the floor, in metres, filled in by [shadowFor] so drawing allocates nothing. */
    class Shadow {
        /** The whole shadow's width, penumbra included, and the sharp core's. */
        var width = 0f
        var coreWidth = 0f
        /** The penumbra's reach beyond the core, each side. */
        var blur = 0f
        /** How dark it is at its darkest, 0 to 1. */
        var alpha = 0f
        /** 0 for a sharp shadow (a shape on the floor), towards 1 for a soft one (a shape high above it). */
        var softness = 0f
        /** Sideways shift away from the light. */
        var offsetX = 0f
        /** The tight, dark ambient occlusion right where a shape touches the floor. */
        var aoWidth = 0f
        var aoAlpha = 0f
    }

    /** A bounce worth feeling: emitted when a body lands faster than [HAPTIC_MIN_SPEED]. */
    fun interface BounceListener {
        fun onBounce(body: Body, impactSpeed: Float)
    }

    var width = 0f
        private set
    var height = 0f
        private set
    /** The floor line, metres from the top of the area. */
    var floor = 0f
        private set
    /** How far the floor line sits above the bottom of the area: room for a shape sunk into it. */
    var floorDepth = 0f
        private set

    val bodies = mutableListOf<Body>()
    val imprints = Array(MAX_IMPRINTS) { Imprint() }
    /** The hard landings' ripples, one per shape at most. */
    val ripples = Array(MAX_SHAPES) { Ripple() }
    /** How far, as a share of [STEP], the time shown is past the last step; drawing interpolates by it. */
    var renderFraction = 0f
        private set
    private var nextId = 0
    private var dropTimer = 0f
    private var burstLeft = 0
    private var accumulator = 0.0
    private var radii: List<Float> = emptyList()
    private val scratchShadow = Shadow()

    /**
     * Sizes the area, in metres. The first call schedules the first drop. A later one (the phone turned, or the text
     * grew while the page is open) keeps the shapes in the air inside the new area: one now under the floor is thrown
     * back up from it, one past a side wall is set against it, and one too big for the area leaves.
     */
    fun resize(widthMetres: Float, heightMetres: Float, radiiMetres: List<Float>) {
        if (widthMetres == width && heightMetres == height && radiiMetres == radii) return
        val first = width == 0f
        width = widthMetres
        height = heightMetres
        radii = radiiMetres
        val largest = (radii.maxOrNull() ?: 0f).coerceAtMost(height * MAX_RADIUS_SHARE)
        floorDepth = FLOOR_DEPTH_SHARE * 2f * largest
        floor = height - floorDepth
        if (first && autoSpawn && width > 0f && height > 0f) {
            dropTimer = FIRST_DROP_MIN + random.nextFloat() * (FIRST_DROP_MAX - FIRST_DROP_MIN)
        }
        for (imprint in imprints) imprint.active = false
        for (ripple in ripples) ripple.active = false
        var i = bodies.size - 1
        while (i >= 0) {
            if (bodies[i].radius > height * MAX_RADIUS_SHARE) bodies.removeAt(i)
            i--
        }
        for (body in bodies) {
            body.imprint = null
            if (body.y + body.radius > floor) {
                // Thrown straight back up, like any bounce, so it never lands softly from nowhere.
                body.y = floor - body.radius
                body.vy = -minimumRebound(body, MIN_REBOUND_SHARE)
                body.penetration = 0f
                body.inContact = false
                body.contactTime = 0f
                body.sinceLift = 0f
            }
            if (!body.exiting) body.x = body.x.clampSafely(body.radius, width - body.radius)
            body.prevX = body.x
            body.prevY = body.y
            capRise(body)
        }
    }

    /** Advances the scene by [frameSeconds] (clamped), in fixed steps of [STEP] so results don't depend on frame rate. */
    fun advance(frameSeconds: Float, listener: BounceListener? = null) {
        if (width <= 0f || height <= 0f) return
        accumulator += frameSeconds.coerceIn(0f, MAX_FRAME).toDouble()
        while (accumulator >= STEP_SECONDS - STEP_TOLERANCE) {
            accumulator -= STEP_SECONDS
            if (autoSpawn) schedule(STEP)
            for (i in 0 until bodies.size) {
                val body = bodies[i]
                step(body, STEP, listener)
                animateLook(body, STEP)
                body.age += STEP
            }
            collide()
            healImprints(STEP)
            ageRipples(STEP)
            removeGone()
        }
        renderFraction = (accumulator / STEP_SECONDS).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Shapes that touch bounce apart: an impulse along the line between their centres, shared by mass, and a
     * friction impulse across it that sets them spinning. Overlap is pushed out so they never sink into each other.
     */
    private fun collide() {
        for (i in 0 until bodies.size) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                // A shape on its way out is never knocked back, nor one past its planned time: knocks could keep it
                // up for ever.
                if (untouchable(a) || untouchable(b)) continue
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
                val share = (minDistance - distance) / (inverseA + inverseB)
                a.x -= nx * share * inverseA; a.y -= ny * share * inverseA
                b.x += nx * share * inverseB; b.y += ny * share * inverseB
                // The push never lifts a shape that was inside the area out of it, nor one still coming in back
                // under the card.
                keepBelowCard(a)
                keepBelowCard(b)
                val approach = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if (approach >= 0f) continue
                val impulse = -(1f + BODY_RESTITUTION) * approach / (inverseA + inverseB)
                a.vx -= impulse * inverseA * nx; a.vy -= impulse * inverseA * ny
                b.vx += impulse * inverseB * nx; b.vy += impulse * inverseB * ny
                // Friction across the contact: the surfaces' relative speed there, spin included, pulled towards
                // zero, never by more than the friction the knock allows.
                val tx = -ny
                val ty = nx
                val slide = (b.vx - a.vx) * tx + (b.vy - a.vy) * ty - (a.spin * a.radius + b.spin * b.radius)
                val tangentialMass = 1f / (inverseA + inverseB + a.radius * a.radius / a.inertia + b.radius * b.radius / b.inertia)
                val friction = (slide * tangentialMass).coerceIn(-BODY_FRICTION * impulse, BODY_FRICTION * impulse)
                a.vx += friction * tx * inverseA; a.vy += friction * ty * inverseA
                b.vx -= friction * tx * inverseB; b.vy -= friction * ty * inverseB
                a.spin += friction * a.radius / a.inertia
                b.spin += friction * b.radius / b.inertia
                capRise(a)
                capRise(b)
            }
        }
    }

    private fun keepBelowCard(body: Body) {
        if (body.y + body.radius > height && !shapeOffScreen(body)) body.y = height - body.radius
        if (body.entered && body.y - body.radius < 0f) body.y = body.radius
        if (!body.entered && body.y < -body.radius) body.y = -body.radius
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

    private fun untouchable(body: Body): Boolean = body.exiting || body.x < 0f || body.x > width || body.age > PLAN_TIME

    /**
     * Drops come at random: after an uneven quiet gap, a burst of one (most often), two or three shapes, a random
     * moment apart. A drop due while [maxBodies] shapes are on screen is skipped, so a burst can come up short.
     */
    private fun schedule(dt: Float) {
        dropTimer -= dt
        if (dropTimer > 0f) return
        if (burstLeft <= 0) {
            val roll = random.nextFloat()
            burstLeft = if (roll < BURST_ONE) 1 else if (roll < BURST_ONE + BURST_TWO) 2 else 3
        }
        if (bodies.size < maxBodies) spawn()
        burstLeft--
        dropTimer = if (burstLeft > 0) {
            BURST_GAP_MIN + random.nextFloat() * (BURST_GAP_MAX - BURST_GAP_MIN)
        } else {
            // Exponentially distributed, like independent events: mostly short, now and then long.
            (QUIET_GAP_MIN - ln(1f - random.nextFloat() * 0.999f) * QUIET_GAP_MEAN).coerceAtMost(QUIET_GAP_MAX)
        }
    }

    /**
     * Drops a new shape in from just under the bottom card, at [fraction] of the width (random if null), thrown a
     * random way: a random size, speed and spin, and a sideways speed checked by simulating its flight so it makes
     * both landings on screen.
     */
    internal fun spawn(fraction: Float? = null): Body? {
        if (bodies.size >= maxBodies || radii.isEmpty() || width <= 0f) return null
        val radius = radii[random.nextInt(radii.size)].coerceAtMost(height * MAX_RADIUS_SHARE)
        val body = Body(nextId++, radius, massFor(radius))
        body.sinkShare = SINK_MIN + random.nextFloat() * (SINK_MAX - SINK_MIN)
        body.liveliness = LIVELINESS_MIN + random.nextFloat() * (LIVELINESS_MAX - LIVELINESS_MIN)
        body.exitBias = random.nextFloat()
        body.x = (width * (fraction ?: random.nextFloat())).clampSafely(radius, width - radius)
        body.y = -radius
        body.vy = ENTRY_SPEED_MIN + random.nextFloat() * (ENTRY_SPEED_MAX - ENTRY_SPEED_MIN)
        body.spin = (if (random.nextBoolean()) 1f else -1f) * (ENTRY_SPIN_MIN + random.nextFloat() * (ENTRY_SPIN_MAX - ENTRY_SPIN_MIN))
        body.angle = random.nextFloat() * 360f
        body.shapeIndex = body.id * 7
        val maxSideways = minOf(MAX_ENTRY_SIDEWAYS, width * ENTRY_SIDEWAYS_PER_METRE)
        var chosen = Float.NaN
        for (attempt in 0 until PLAN_TRIES) {
            // Smaller throws more often than larger ones, either way.
            val candidate = (if (random.nextBoolean()) 1f else -1f) * maxSideways * random.nextFloat().pow(1.4f)
            if (landsTwiceOnScreen(body, candidate)) {
                chosen = candidate
                break
            }
        }
        // No throw fits (a narrow area): straight down, drifting towards the middle.
        body.vx = if (chosen.isNaN()) (if (body.x < width / 2f) 0.05f else -0.05f) else chosen
        // A shape heading right turns clockwise, so its quarter turn on each morph goes the way it travels.
        body.turnDirection = if (body.vx >= 0f) 1f else -1f
        body.prevX = body.x
        body.prevY = body.y
        body.prevAngle = body.angle
        bodies += body
        return body
    }

    /** Whether [template], thrown sideways at [vx], makes its first two landings well inside the side edges. */
    private fun landsTwiceOnScreen(template: Body, vx: Float): Boolean {
        val ghost = Body(-1, template.radius, template.mass).apply {
            x = template.x; y = template.y; this.vx = vx; vy = template.vy; spin = template.spin
            sinkShare = template.sinkShare; liveliness = template.liveliness; exitBias = template.exitBias
            real = false
        }
        val margin = ghost.radius * PLAN_EDGE_MARGIN
        var t = 0f
        while (t < PLAN_HORIZON && ghost.bounces < 2) {
            step(ghost, STEP, null)
            if (ghost.x < margin || ghost.x > width - margin) return false
            t += STEP
        }
        return ghost.bounces >= 2
    }

    /** The upward speed of the lowest bounce allowed: [share] of the height the shape could rise to. */
    private fun minimumRebound(body: Body, share: Float): Float = sqrt(2f * GRAVITY * maxRise(body) * share)

    /** How high a shape on the floor can rise before its top reaches the top of the area. */
    private fun maxRise(body: Body): Float = (floor - 2f * body.radius - TOP_MARGIN).coerceAtLeast(0f)

    /** One integration step: air drag and gravity, then the floor when the body reaches it. */
    internal fun step(body: Body, dt: Float, listener: BounceListener?) {
        body.prevX = body.x
        body.prevY = body.y
        body.prevAngle = body.angle
        if (body.glow > 0f) {
            body.glow *= exp(-dt / GLOW_TIME_CONSTANT)
            if (body.glow < 0.01f) body.glow = 0f
        }
        if (!body.entered && body.y - body.radius >= 0f) body.entered = true
        val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
        val k = body.dragK
        val ax = -k * speed * body.vx
        var ay = GRAVITY - k * speed * body.vy

        // A shape already past the edge it leaves by falls on out of sight, untouched by the floor.
        val gone = body.exiting && shapeOffScreen(body)
        val penetration = if (gone) 0f else body.y + body.radius - floor
        if (penetration > 0f && (body.inContact || body.vy > 0f)) {
            if (!body.inContact) {
                // A touch right after leaving the floor (a knock pushed it back down) is the same bounce, not a new
                // one: no new shape, no tap.
                val newBounce = body.bounces == 0 || body.sinceLift >= MIN_FLIGHT
                val impact = body.vy
                if (newBounce) {
                    body.bounces++
                    body.shapeIndex++
                    body.morphProgress = 0f
                    body.glow = 1f
                    if (impact >= HAPTIC_MIN_SPEED) listener?.onBounce(body, impact)
                }
                if (body.bounces >= 2) {
                    hardBounce(body, impact)
                } else {
                    body.inContact = true
                    body.contactTime = 0f
                    if (newBounce || body.membraneOmega == 0f) body.membraneOmega = membraneOmega(body, impact)
                    if (newBounce && body.real) body.imprint = newImprint(body)
                }
            }
            if (body.inContact) {
                // The membrane: a damped spring under the shape, stiff enough to stop it [Body.sinkShare] of its
                // height down, soft enough to let it through the floor line on the way.
                val omega = body.membraneOmega
                ay -= omega * omega * penetration + 2f * MEMBRANE_DAMPING * omega * body.vy
                body.contactTime += dt
                // The membrane never holds anything: one that lost its speed in it (a knock) is thrown out.
                if (body.contactTime > MAX_MEMBRANE_TIME && body.vy > -minimumRebound(body, MIN_REBOUND_SHARE)) {
                    body.vy = -minimumRebound(body, MIN_REBOUND_SHARE)
                }
                // Friction with the membrane it is sunk in: sideways speed and spin pull towards rolling.
                val slip = body.vx - body.spin * body.radius
                val grip = (MEMBRANE_GRIP * dt * (penetration / (2f * body.radius)).coerceIn(0f, 1f)).coerceAtMost(1f)
                val slipChange = slip * grip
                body.vx -= slipChange * INERTIA_SHARE / (1f + INERTIA_SHARE)
                body.spin += slipChange / (1f + INERTIA_SHARE) / body.radius
            }
        } else {
            if (body.inContact) {
                body.inContact = false
                body.sinceLift = 0f
                if (body.vy < 0f) {
                    // Sprung back up: never above the top of the area, never less than a real flight.
                    val maxUp = sqrt(2f * GRAVITY * maxRise(body))
                    val minUp = minOf(minimumRebound(body, MIN_REBOUND_SHARE), maxUp)
                    body.vy = -(-body.vy).coerceIn(minUp, maxUp)
                }
                body.imprint?.let { imprint ->
                    imprint.holding = false
                    imprint.sinceRelease = 0f
                }
                body.imprint = null
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
        // Never through the bottom of the area, however hard a knock pressed it in.
        if (!gone && body.y + body.radius > height) {
            body.y = height - body.radius
            if (body.vy > 0f) body.vy = 0f
        }
        // Until it has bounced off the hard floor, a shape rebounds off the side edges rather than leaving.
        if (!body.exiting && body.real) {
            if (body.x < body.radius && body.vx < 0f) {
                body.x = body.radius
                body.vx = -body.vx * WALL_RESTITUTION
            } else if (body.x > width - body.radius && body.vx > 0f) {
                body.x = width - body.radius
                body.vx = -body.vx * WALL_RESTITUTION
            }
        }
        body.angle = (body.angle + Math.toDegrees((body.spin * dt).toDouble()).toFloat()) % 360f
        // How deep it is now, after the step.
        body.penetration = if (body.inContact) (body.y + body.radius - floor).coerceAtLeast(0f) else 0f
        body.imprint?.let { follow(it, body) }
    }

    /**
     * The membrane's natural frequency that stops a shape landing at [impact] [Body.sinkShare] of its height below
     * the floor line: a damped spring hit at speed v from rest reaches v/ω·[SINK_FACTOR], and gravity, pressing on,
     * takes it about g/ω² further.
     */
    private fun membraneOmega(body: Body, impact: Float): Float {
        val target = (body.sinkShare * 2f * body.radius).coerceAtMost(floorDepth * 0.92f).coerceAtLeast(1e-3f)
        val v = impact.coerceAtLeast(0.1f)
        var omega = v * SINK_FACTOR / target
        repeat(3) {
            val reach = (target - GRAVITY / (omega * omega)).coerceAtLeast(target * 0.5f)
            omega = v * SINK_FACTOR / reach
        }
        return omega.coerceIn(MEMBRANE_OMEGA_MIN, MEMBRANE_OMEGA_MAX)
    }

    /**
     * The hard floor: the shape bounces off it at once, keeping [Body.liveliness] of its landing speed, its spin and
     * sideways speed traded by Coulomb friction. It leaves rising and towards the edge it is heading for (or, all but
     * stopped, usually the nearer one), fast enough to be off the screen before it would land again; where the floor's
     * friction alone wouldn't throw it that fast, the floor kicks it on, and it leaves rolling, spinning the way it
     * flies. In an area too wide for one flight, it does the same on every landing until it is out.
     */
    private fun hardBounce(body: Body, impact: Float) {
        body.y = floor - body.radius
        if (body.real && !shapeOffScreen(body)) newRipple(body)
        body.penetration = 0f
        val landing = impact.coerceAtLeast(0f)
        val normalImpulse = body.mass * (1f + body.liveliness) * landing
        val slip = body.vx - body.spin * body.radius
        val friction = (slip / (1f / body.mass + body.radius * body.radius / body.inertia))
            .coerceIn(-FLOOR_FRICTION * normalImpulse, FLOOR_FRICTION * normalImpulse)
        body.vx -= friction / body.mass
        body.spin += friction * body.radius / body.inertia
        val maxUp = sqrt(2f * GRAVITY * maxRise(body))
        val minUp = minOf(minimumRebound(body, HARD_MIN_REBOUND_SHARE), maxUp)
        val up = (body.liveliness * landing).coerceIn(minUp, maxUp)
        body.vy = -up
        if (!body.exiting) {
            body.exitTowardsRight = if (abs(body.vx) >= MIN_TRAVEL) {
                body.vx > 0f
            } else if (body.exitBias < NEARER_EDGE_SHARE) {
                body.x > width / 2f
            } else {
                body.x <= width / 2f
            }
            body.exiting = true
        }
        val direction = if (body.exitTowardsRight) 1f else -1f
        val flight = (2f * up / GRAVITY).coerceAtLeast(0.05f)
        val distance = (if (body.exitTowardsRight) width - body.x else body.x) + body.radius * EXIT_CLEARANCE
        val needed = (distance / flight * EXIT_SPEED_MARGIN).coerceAtMost(MAX_EXIT_SPEED)
        if (body.vx * direction < needed) {
            body.vx = direction * needed
            body.spin = body.vx / body.radius
        }
        body.squashVelocity += landing * HARD_SQUASH_KICK
        body.inContact = false
        body.sinceLift = 0f
        body.contactTime = 0f
    }

    /** A silhouette for [body] to press into the floor: a free one, or else the most healed. */
    private fun newImprint(body: Body): Imprint {
        var pick = imprints[0]
        for (imprint in imprints) {
            if (!imprint.active) {
                pick = imprint
                break
            }
            if (!imprint.holding && (pick.holding || imprint.sinceRelease > pick.sinceRelease)) pick = imprint
        }
        pick.active = true
        pick.holding = true
        pick.bodyId = body.id
        pick.radius = body.radius
        pick.depth = 0f
        pick.sinceRelease = 0f
        pick.sincePressed = 0f
        pick.strength = 1f
        pick.surface = 0f
        pick.surfaceVelocity = 0f
        pick.x = body.x
        pick.y = body.y
        pick.shapeIndex = body.shapeIndex
        pick.morphProgress = body.morphProgress
        pick.squash = body.squash
        pick.rotationDegrees = drawnRotation(body)
        return pick
    }

    /** While a shape is in the membrane, its silhouette takes its outline at its deepest, and the surface sags with it. */
    private fun follow(imprint: Imprint, body: Body) {
        if (body.penetration > imprint.depth) {
            imprint.depth = body.penetration
            imprint.x = body.x
            imprint.y = body.y
            imprint.shapeIndex = body.shapeIndex
            imprint.morphProgress = body.morphProgress
            imprint.squash = body.squash
            imprint.rotationDegrees = drawnRotation(body)
        }
        // The surface moves with the shape, so as the shape leaves it is still moving up, and springs on past its rest.
        imprint.surface = SURFACE_SAG * body.penetration
        imprint.surfaceVelocity = SURFACE_SAG * body.vy
    }

    /** Released silhouettes: the surface rings back past its rest line and settles; the silhouette heals. */
    private fun healImprints(dt: Float) {
        for (imprint in imprints) {
            if (!imprint.active) continue
            imprint.sincePressed += dt
            if (imprint.holding) continue
            imprint.sinceRelease += dt
            imprint.surfaceVelocity += springAcceleration(imprint.surface, imprint.surfaceVelocity, SURFACE_STIFFNESS, SURFACE_DAMPING) * dt
            imprint.surface += imprint.surfaceVelocity * dt
            val healing = ((imprint.sinceRelease - HEAL_DELAY) / HEAL_TIME).coerceIn(0f, 1f)
            imprint.strength = 1f - healing * healing * (3f - 2f * healing)
            if (imprint.strength <= 0f && abs(imprint.surface) < 1e-4f && abs(imprint.surfaceVelocity) < 1e-3f) imprint.active = false
        }
    }

    /** A ripple for [body] striking the hard floor: a free one, or else the oldest. Drawing only; nothing moves by it. */
    private fun newRipple(body: Body) {
        var pick = ripples[0]
        for (ripple in ripples) {
            if (!ripple.active) {
                pick = ripple
                break
            }
            if (ripple.age > pick.age) pick = ripple
        }
        pick.active = true
        pick.bodyId = body.id
        pick.x = body.x
        pick.radius = body.radius
        pick.shapeIndex = body.shapeIndex
        pick.morphProgress = body.morphProgress
        pick.rotationDegrees = drawnRotation(body)
        pick.age = 0f
    }

    private fun ageRipples(dt: Float) {
        for (ripple in ripples) {
            if (!ripple.active) continue
            ripple.age += dt
            if (ripple.age >= HARD_RIPPLE_TIME) ripple.active = false
        }
    }

    private fun removeGone() {
        var i = bodies.size - 1
        while (i >= 0) {
            if (isGone(bodies[i])) bodies.removeAt(i)
            i--
        }
    }

    /** Whether the shape is fully past a side edge, however it is squashed, stretched or turned. */
    internal fun shapeOffScreen(body: Body): Boolean {
        val reach = body.radius * SHAPE_REACH
        return body.x - reach > width || body.x + reach < 0f
    }

    /** Whether nothing of the shape, nor of its shadow, is left on screen. */
    internal fun isGone(body: Body): Boolean {
        if (!body.exiting) return false
        if (body.y - body.radius > height) return true
        if (!shapeOffScreen(body)) return false
        val shadow = shadowOf(body, scratchShadow)
        if (shadow.alpha < SHADOW_VISIBLE) return true
        val centre = body.x + shadow.offsetX
        val half = shadow.width / 2f
        return centre - half > width || centre + half < 0f
    }

    /** [body]'s shadow, into [out]. */
    fun shadowOf(body: Body, out: Shadow): Shadow =
        shadowFor(floor - body.y - body.radius, body.radius, body.penetration, out)

    /**
     * Moves what the shape looks like on by [dt], on Material 3 Expressive springs; nothing here changes how it moves.
     * - The morph to its new shape springs on the fast spatial spring, overshooting a touch and settling.
     * - In the membrane it flattens a little as it sinks; off the hard floor the landing kicks the squash, which then
     *   rings on the fast spatial spring, so the shape wobbles once, softly, and settles round.
     * - It scales in from nothing as it comes out from under the card, on the default spatial spring.
     */
    internal fun animateLook(body: Body, dt: Float) {
        if (body.morphProgress != 1f || body.morphVelocity != 0f) {
            body.morphVelocity += springAcceleration(body.morphProgress - 1f, body.morphVelocity, MORPH_STIFFNESS, MORPH_DAMPING) * dt
            body.morphProgress += body.morphVelocity * dt
            if (abs(body.morphProgress - 1f) < MORPH_SETTLE && abs(body.morphVelocity) < MORPH_SETTLE_SPEED) {
                body.morphProgress = 1f
                body.morphVelocity = 0f
            }
        }

        if (body.penetration > 0f) {
            val pressed = (body.penetration / (2f * body.radius) * SQUASH_PER_DEPTH).coerceIn(0f, MAX_SQUASH)
            // Pressed into the membrane the shape is as flat as it is deep; the spring takes over as it lets go.
            body.squashVelocity = (pressed - body.squash) / dt * JELLY_CARRY
            body.squash = pressed
        } else if (body.squash != 0f || body.squashVelocity != 0f) {
            body.squashVelocity += springAcceleration(body.squash, body.squashVelocity, JELLY_STIFFNESS, JELLY_DAMPING) * dt
            body.squash = (body.squash + body.squashVelocity * dt).coerceIn(-MAX_JELLY_STRETCH, MAX_SQUASH)
            if (abs(body.squash) < 1e-3f && abs(body.squashVelocity) < 1e-2f) {
                body.squash = 0f
                body.squashVelocity = 0f
            }
        }

        if (body.appear < 1f || body.appearVelocity != 0f) {
            body.appearVelocity += springAcceleration(body.appear - 1f, body.appearVelocity, APPEAR_STIFFNESS, APPEAR_DAMPING) * dt
            body.appear = (body.appear + body.appearVelocity * dt).coerceAtMost(MAX_APPEAR)
            if (abs(body.appear - 1f) < 1e-3f && abs(body.appearVelocity) < 1e-2f) {
                body.appear = 1f
                body.appearVelocity = 0f
            }
        }
    }

    companion object {
        const val MAX_SHAPES = 3
        const val GRAVITY = 9.81f
        const val AIR_DENSITY = 1.2f
        const val DRAG_COEFFICIENT = 0.47f
        /** The shapes are stone (2600 kg/m³) slabs 4 cm thick: a 20 cm one weighs 3.3 kg. */
        const val SLAB_DENSITY = 2600f
        const val SLAB_THICKNESS = 0.04f
        /** A solid disc's moment of inertia is ½mr². */
        const val INERTIA_SHARE = 0.5f

        /** The membrane's damping ratio: it gives back about 64% of a landing's speed, and rings as it lets go. */
        const val MEMBRANE_DAMPING = 0.14f
        /** How deep a shape sinks into the membrane, as a share of its height. */
        const val SINK_MIN = 0.42f
        const val SINK_MAX = 0.58f
        /** The floor line sits this share of the largest shape's height above the bottom of the area. */
        const val FLOOR_DEPTH_SHARE = 0.66f
        const val MEMBRANE_OMEGA_MIN = 8f
        const val MEMBRANE_OMEGA_MAX = 90f
        /** The membrane's grip on the sideways motion of a shape sunk into it, per second at full depth. */
        const val MEMBRANE_GRIP = 9f
        /** A landing in the membrane lasts π/ω, 35 to 250 ms; past this it throws the shape out. */
        const val MAX_MEMBRANE_TIME = 0.6f
        /** The membrane surface around a sunk shape sags this share of the shape's depth. */
        const val SURFACE_SAG = 0.3f
        /** Released, the surface springs back on a lightly damped spring (5 Hz), overshooting by about half. */
        const val SURFACE_STIFFNESS = 900f
        const val SURFACE_DAMPING = 0.22f
        /** A silhouette stays this long after its shape has left it, then heals over [HEAL_TIME]. */
        const val HEAL_DELAY = 0.35f
        const val HEAL_TIME = 1.6f
        const val MAX_IMPRINTS = 6
        /** A hard landing's ripple runs across the floor this long, seconds: sharp and quick. */
        const val HARD_RIPPLE_TIME = 0.34f

        /** The hard floor gives back this share of a landing's speed: a lively bounce. */
        const val LIVELINESS_MIN = 0.74f
        const val LIVELINESS_MAX = 0.84f
        const val FLOOR_FRICTION = 0.55f
        const val BODY_RESTITUTION = 0.55f
        const val BODY_FRICTION = 0.3f
        const val WALL_RESTITUTION = 0.5f
        const val SPIN_AIR_DECAY = 0.08f
        /** A shape bounced off the hard floor is thrown at most this fast sideways, m/s. */
        const val MAX_EXIT_SPEED = 10f
        /** It is planned to clear the edge by this many radii before it would land again, with this margin on speed. */
        const val EXIT_CLEARANCE = 1.8f
        const val EXIT_SPEED_MARGIN = 1.12f
        /** Slower than this sideways, a shape bouncing off the hard floor leaves by the nearer edge this often. */
        const val MIN_TRAVEL = 0.35f
        const val NEARER_EDGE_SHARE = 0.7f
        /** However squashed, stretched or turned, no part of a shape is drawn farther than this many radii out. */
        const val SHAPE_REACH = 1.6f

        /** Every bounce reaches at least this share of the height a shape could rise to; off the hard floor, more. */
        const val MIN_REBOUND_SHARE = 0.35f
        const val HARD_MIN_REBOUND_SHARE = 0.45f
        /** A floor touch sooner than this after leaving it belongs to the same bounce. */
        const val MIN_FLIGHT = 0.3f
        const val TOP_MARGIN = 0.02f
        /** No shape's radius is more than this share of the area's height. */
        const val MAX_RADIUS_SHARE = 0.3f

        /** Shapes come in from under the card at 0.3 to 2.2 m/s, spinning at 0.4 to 4.5 rad/s either way. */
        const val ENTRY_SPEED_MIN = 0.3f
        const val ENTRY_SPEED_MAX = 2.2f
        const val ENTRY_SPIN_MIN = 0.4f
        const val ENTRY_SPIN_MAX = 4.5f
        const val MAX_ENTRY_SIDEWAYS = 2.4f
        const val ENTRY_SIDEWAYS_PER_METRE = 0.5f
        const val PLAN_TRIES = 16
        const val PLAN_HORIZON = 12f
        /** Both landings are planned at least this many radii inside the side edges. */
        const val PLAN_EDGE_MARGIN = 1.1f

        /** The first drop comes 0.15 to 0.8 s after the page opens. */
        const val FIRST_DROP_MIN = 0.15f
        const val FIRST_DROP_MAX = 0.8f
        /** A burst is one shape half the time, two shapes a third of the time, otherwise three. */
        const val BURST_ONE = 0.5f
        const val BURST_TWO = 0.32f
        const val BURST_GAP_MIN = 0.07f
        const val BURST_GAP_MAX = 0.55f
        /** Between bursts: at least 0.45 s, on average 1.7 s, at most 4.5 s. */
        const val QUIET_GAP_MIN = 0.45f
        const val QUIET_GAP_MEAN = 1.25f
        const val QUIET_GAP_MAX = 4.5f
        /** Past this age knocks no longer reach a shape. */
        const val PLAN_TIME = 5f
        /**
         * Every shape has left by this age, having made both its landings, in areas from 40 dp to 900 dp tall and
         * 320 dp to 1280 dp wide (checked over many random runs of each in MorphBouncePhysicsTest).
         */
        const val MAX_LIFETIME = 9f

        /** The simulation's fixed step: 240 Hz, two steps a frame at 120 Hz, four at 60 Hz. */
        const val STEP = 1f / 240f
        private const val STEP_SECONDS = 1.0 / 240.0
        private const val STEP_TOLERANCE = 1e-7
        const val MAX_FRAME = 1f / 20f

        /**
         * The morph's spring: Material 3 Expressive's fast spatial spring (MotionScheme.expressive() fastSpatialSpec,
         * damping ratio 0.6, stiffness 800). Its damping is that of Google's morphing LoadingIndicator (0.6, there at
         * stiffness 200, one morph every 650 ms); here a morph should be all but formed as a shape leaves the
         * membrane, so it is the expressive scheme's faster spring of the same feel: it overshoots by under 10% and
         * has settled about 0.35 s after the bounce.
         */
        const val MORPH_DAMPING = 0.6f
        const val MORPH_STIFFNESS = 800f
        /**
         * The morph counts as settled, and is set to exactly 1, this close to it and this slow (Google's loading
         * indicator likewise raises its morph spring's visibility threshold so the low-damping spring ends sooner).
         */
        const val MORPH_SETTLE = 0.01f
        const val MORPH_SETTLE_SPEED = 0.1f
        /** A shape in the membrane is squashed by this share of its depth there, over its height. */
        const val SQUASH_PER_DEPTH = 0.22f
        /** The flattest a shape gets: 20% shorter, and wider to keep its area. */
        const val MAX_SQUASH = 0.2f
        /** The tallest it gets in the wobble after a bounce: 10% taller, and narrower. */
        const val MAX_JELLY_STRETCH = 0.1f
        /** The share of its unsquashing speed a shape carries into the wobble after leaving the membrane. */
        const val JELLY_CARRY = 0.5f
        /** How hard a landing on the hard floor kicks the squash, per m/s of landing speed. */
        const val HARD_SQUASH_KICK = 0.9f
        /** The wobble's spring: the expressive fast spatial spring again, so it rings once, softly, and settles. */
        const val JELLY_DAMPING = 0.6f
        const val JELLY_STIFFNESS = 800f
        /** The scale-in from under the card: the expressive default spatial spring (0.8, 380), about 0.4 s. */
        const val APPEAR_DAMPING = 0.8f
        const val APPEAR_STIFFNESS = 380f
        /** The scale-in's overshoot (about 1.5% for this spring) is never more than this. */
        const val MAX_APPEAR = 1.03f
        const val HAPTIC_MIN_SPEED = 1.0f
        /** The brightening on a bounce fades over roughly a fifth of a second. */
        const val GLOW_TIME_CONSTANT = 0.12f

        /**
         * The light: a broad soft box above and a little to the left, so shadows fall slightly right and spread as
         * a shape rises: the penumbra grows with height ([PENUMBRA_BASE] radii at contact, [PENUMBRA_GROWTH] more per
         * radius of height), the sharp core shrinks, and the shadow's darkness, spread ever wider, falls off with the
         * square of height ([SHADOW_FALLOFF]).
         */
        const val SHADOW_ALPHA = 0.36f
        const val SHADOW_FALLOFF = 0.9f
        const val PENUMBRA_BASE = 0.1f
        const val PENUMBRA_GROWTH = 0.55f
        const val UMBRA_SHRINK = 0.35f
        const val LIGHT_SLANT = 0.18f
        /** A shape's footprint is this share of its width. */
        const val FOOTPRINT_SHARE = 0.92f
        /** The ambient occlusion where a shape touches: this dark at contact, gone by [AO_REACH] radii up. */
        const val AO_ALPHA = 0.5f
        const val AO_REACH = 0.35f
        const val AO_WIDTH_SHARE = 0.55f
        /** A shadow fainter than this isn't drawn. */
        const val SHADOW_VISIBLE = 0.004f

        /** How far a damped spring of [MEMBRANE_DAMPING] hit at speed v from rest reaches, times ω/v. */
        val SINK_FACTOR: Float = run {
            val z = MEMBRANE_DAMPING
            val root = sqrt(1f - z * z)
            exp(-z / root * atan(root / z))
        }

        /**
         * The contact shadow of a shape of [radius] whose lowest point is [heightAboveFloor] above the floor line, or
         * [sink] below it, into [out]. Close to the floor it is sharp, dark and as wide as the shape's footprint, with
         * a dark ambient occlusion right at the contact; higher, it is wider, softer, fainter and shifted away from
         * the light. A shape sunk into the floor casts no shadow beside it, only the darkness of the floor closing
         * round it, as wide as it is where it crosses the floor line.
         */
        fun shadowFor(heightAboveFloor: Float, radius: Float, sink: Float, out: Shadow): Shadow {
            val h = heightAboveFloor.coerceAtLeast(0f)
            val s = h / radius
            val footprint = if (sink > 0f) {
                // The width of a disc of [radius] where it crosses the floor line, [sink] above its lowest point.
                val d = sink.coerceAtMost(radius)
                2f * sqrt((2f * radius * d - d * d).coerceAtLeast(0f)).coerceAtLeast(radius * 0.35f)
            } else {
                2f * radius * FOOTPRINT_SHARE
            }
            out.coreWidth = footprint / (1f + UMBRA_SHRINK * s)
            out.blur = radius * (PENUMBRA_BASE + PENUMBRA_GROWTH * s)
            out.width = out.coreWidth + 2f * out.blur
            out.alpha = SHADOW_ALPHA / ((1f + SHADOW_FALLOFF * s) * (1f + SHADOW_FALLOFF * s))
            out.softness = s / (s + 0.6f)
            out.offsetX = h * LIGHT_SLANT
            val contact = (1f - s / AO_REACH).coerceAtLeast(0f)
            out.aoAlpha = AO_ALPHA * contact * contact
            out.aoWidth = if (sink > 0f) footprint * 1.1f else footprint * AO_WIDTH_SHARE / FOOTPRINT_SHARE
            return out
        }

        /** The mass of a dense slab of [radius]: it grows with its area. */
        fun massFor(radius: Float): Float = SLAB_DENSITY * SLAB_THICKNESS * PI.toFloat() * radius * radius

        /** The angle [body] is drawn at: its roll plus a quarter turn for each morph, turned with the morph's spring. */
        fun drawnRotation(body: Body, angle: Float = body.angle): Float {
            val morphsDone = if (body.bounces == 0) 0f else body.bounces - 1 + body.morphProgress
            return (angle + body.turnDirection * MORPH_TURN_DEGREES * morphsDone) % 360f
        }

        /**
         * The acceleration of a spring of [stiffness] and [dampingRatio] (as Compose's `spring()` takes them, unit
         * mass) at [offset] from its rest with [velocity].
         */
        fun springAcceleration(offset: Float, velocity: Float, stiffness: Float, dampingRatio: Float): Float =
            -stiffness * offset - 2f * dampingRatio * sqrt(stiffness) * velocity
    }
}
