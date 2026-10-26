package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.util.clampSafely
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The physics behind the Material Morph animation: heavy shapes that drop in from under the bottom card onto a floor
 * of rubber at the bottom of the screen, bounce on it a few times and leave past a side of the screen.
 *
 * The scene is simulated in metres, drawn at one dp per centimetre (smaller in an area too short for that, see
 * [morphSceneFor]), so gravity, 9.81 m/s², is 981 dp/s² on screen. Everything is real physics:
 * - every shape weighs, at random, between [MIN_POUNDS] and [MAX_POUNDS] whatever its size, a slab [SLAB_THICKNESS]
 *   thick, so a small heavy shape is dense and a big light one less so; air drag (quadratic, a rounded body's drag
 *   coefficient) barely slows it;
 * - shapes spin as solid discs do (moment of inertia ½mr²): friction with the floor and between shapes trades
 *   sideways speed for spin and back, so a shape that lands moving sideways leaves rolling;
 * - shapes knock into each other like the hard objects they are, trading momentum by mass;
 * - the floor is a rubber sheet stretched across the area (at least [MIN_SHEET_NODES] nodes, never farther apart than
 *   the smallest shape's radius, so every shape always presses some: a string under tension that stiffens
 *   as it stretches), integrated implicitly so it is stable however hard it is struck. A shape presses into it along
 *   its own outline: each node under it is pushed by a simultaneous contact impulse, with Coulomb friction, so a heavy
 *   shape sinks deeper and comes back up slower than a light one, and the sheet's ringing carries the blow across;
 * - the energy the rubber's damping takes becomes heat where it is taken. Warm, loaded rubber creeps: it keeps some of
 *   the shape's dent, in the shape's outline, and heals it once the load has gone. Blows on the same spot damage it, so
 *   it damps harder and creeps more there, giving back less of a bounce, and heals that too, more slowly.
 * - nothing adds energy: there are no walls at the side edges and no ceiling, and nothing but gravity, the air, the
 *   rubber and the other shapes ever pushes a shape.
 * The sheet's tension and damping are found once per area ([calibrate]) so a reference stone shape sinks about half its
 * height and comes back at about [REFERENCE_REBOUND] of its landing speed.
 *
 * Shapes come at random, never more than [MAX_SHAPES] at a time: sometimes one, sometimes a burst, with uneven gaps,
 * each from a random place under the card, thrown a random way with a random spin. Each drop is planned a second ahead
 * ([PLAN_LEAD]): the whole scene is forecast under exactly these physics, the shapes already in it included, and a
 * throw is taken only if every shape then bounces at least twice and touches down at most [MAX_LANDINGS] times, its first two
 * landings on screen, never rises back above the card, never sits on the floor, and leaves within [MAX_LIFETIME]. The
 * simulation is deterministic, so the scene that plays is the scene that was forecast. The planning runs a few
 * milliseconds of each frame at a time ([work]), or all at once when [planBudgetNanos] is 0, as in tests.
 *
 * The simulation steps at a fixed [STEP] whatever the frame rate, so a run is the same at 60, 90 or 120 Hz, and a
 * seed always gives the same run; [renderFraction] lets drawing interpolate between steps. How each shape looks as it
 * moves (its morph and scale-in, on Material 3 Expressive's spatial springs, and the light inside it, which sloshes
 * with the acceleration it feels) is stepped with the physics but never feeds back into the motion, except that the
 * morphing outline is the one that touches the rubber.
 *
 * Positions are body centers, y downwards, with the top of the area at 0, the floor line (the rubber at rest) at
 * [floor] and the bottom of the area at [height].
 *
 * [outlines] are the shape sequence's outlines as radii by angle ([MorphOutlines]), so the rubber is pressed in the
 * shape's own outline.
 */
internal class MorphBouncePhysics(
    private val random: Random = Random.Default,
    val maxBodies: Int = MAX_SHAPES,
    private val autoSpawn: Boolean = true,
    outlines: List<FloatArray> = MorphOutlines.forSequence(MaterialMorphShapes.sequence(0)),
    private val planBudgetNanos: Long = 0L
) {
    private val outlines: Array<FloatArray> = outlines.ifEmpty { listOf(MorphOutlines.circle) }.toTypedArray()

    class Body(val id: Int, val radius: Float, val mass: Float) {
        /** Its density, from its weight and size: a small heavy shape is dense, a big light one less so. */
        val density: Float = mass / (SLAB_THICKNESS * PI.toFloat() * radius * radius)
        val inertia = INERTIA_SHARE * mass * radius * radius
        internal val dragK: Float = 0.5f * AIR_DENSITY * DRAG_COEFFICIENT * (2f * radius * SLAB_THICKNESS) / mass
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        /** Radians per second, clockwise on screen. */
        var spin = 0f
        var angle = 0f
        /** Where it was at the last step, so drawing can interpolate. */
        internal var prevX = 0f
        internal var prevY = 0f
        internal var prevAngle = 0f
        /** Landings counted as bounces (a touch sooner than [MIN_FLIGHT] after leaving the floor is the same one). */
        var bounces = 0
        /** Every touch of the floor, bounces or not. */
        var contacts = 0
        internal var landCount = 0
        internal var landX0 = 0f
        internal var landX1 = 0f
        /** Seconds since it appeared. */
        var age = 0f
        /** How far its lowest point is below the floor line. */
        var penetration = 0f
        /** Index into the shape sequence of the shape it is morphing to (from the one before). */
        var shapeIndex = 0
        var morphProgress = 1f
        internal var morphVelocity = 0f
        internal var morphOnLift = false
        /** Its scale-in from under the card, 0 to 1 (overshooting a touch). */
        var appear = 0f
        internal var appearVelocity = 0f
        /** Which way it was thrown, 1 to the right, -1 to the left. */
        var turnDirection = 1f
        /** Whether it has come fully out from under the card; from then on it never rises back above it. */
        internal var entered = false
        internal var sinceLift = 10f
        internal var contactTime = 0f
        internal var stuck = false
        internal var touched = false
        internal var profiled = false
        var inContact = false
            internal set
        internal var real = true
        internal var imprint: Imprint? = null

        /** The light inside: where it pools (in radii from the center), how wide, a flash on impact and its swirl. */
        var lightX = 0f
        var lightY = 0.3f
        internal var lightVX = 0f
        internal var lightVY = 0f
        var lightSpread = 0.6f
        var flash = 0f
        var swirl = 0f
        internal var lastVX = 0f
        internal var lastVY = 0f

        /** Weight in pounds. */
        val pounds: Float get() = mass / POUND

        internal fun copyFrom(o: Body) {
            x = o.x; y = o.y; vx = o.vx; vy = o.vy; spin = o.spin; angle = o.angle
            prevX = o.prevX; prevY = o.prevY; prevAngle = o.prevAngle
            bounces = o.bounces; contacts = o.contacts; landCount = o.landCount; landX0 = o.landX0; landX1 = o.landX1
            age = o.age; penetration = o.penetration; shapeIndex = o.shapeIndex; morphProgress = o.morphProgress
            morphVelocity = o.morphVelocity; morphOnLift = o.morphOnLift; appear = o.appear; appearVelocity = o.appearVelocity
            turnDirection = o.turnDirection; entered = o.entered; sinceLift = o.sinceLift; contactTime = o.contactTime
            stuck = o.stuck; touched = o.touched; inContact = o.inContact
            lightX = o.lightX; lightY = o.lightY; lightVX = o.lightVX; lightVY = o.lightVY; lightSpread = o.lightSpread
            flash = o.flash; swirl = o.swirl; lastVX = o.lastVX; lastVY = o.lastVY
        }

        /** A copy for a forecast: the same physics, drawn nowhere and felt by no one. */
        internal fun ghost(): Body = Body(id, radius, mass).also { it.copyFrom(this); it.real = false }
    }

    /**
     * The rubber floor: its nodes across the area, fixed at both ends. [u] is each node's depth below the
     * floor line, [rest] the dent the rubber has kept there, [heat] its warmth above the room's (kelvin) and [damage]
     * how worn it is, 0 to 1. [by] is the body that last pressed each node, for its color.
     */
    class Sheet internal constructor(n: Int) {
        val u = DoubleArray(n)
        internal val v = DoubleArray(n)
        val rest = DoubleArray(n)
        val heat = DoubleArray(n)
        val damage = DoubleArray(n)
        internal val load = BooleanArray(n)
        val by = IntArray(n)
        internal val rhs = DoubleArray(n)
        internal val cp = DoubleArray(n)

        internal fun copyFrom(o: Sheet) {
            o.u.copyInto(u); o.v.copyInto(v); o.rest.copyInto(rest); o.heat.copyInto(heat); o.damage.copyInto(damage)
            o.load.copyInto(load); o.by.copyInto(by)
        }
    }

    /**
     * The silhouette a landing presses into the rubber: the shape's outline at its deepest, held while the shape is in
     * it. Released, it closes as the rubber's dent there recovers, never growing back: [heal] runs from 0 to 1, the
     * hollow being [MorphFloorEffects]' healShrink of it, and its last moment breaking into pixels. Pressed over by a
     * newer silhouette, it heals on out on its own clock instead, so an old dent never comes back under a new one.
     */
    class Imprint {
        var active = false
        /** The body that made it, for its color. */
        var bodyId = 0
        var x = 0f
        var y = 0f
        var radius = 0f
        /** How far below the floor line the shape's lowest point reached. */
        var depth = 0f
        var shapeIndex = 0
        var morphProgress = 1f
        var rotationDegrees = 0f
        /** True while its shape is still in it. */
        var holding = false
        /** A first landing (soft, three ripples) rather than a later one (one sharp ripple). */
        var soft = true
        /** Seconds since its shape first pressed into it: the ripples spread from that moment. */
        var sincePressed = 0f
        internal var restAtRelease = 0.0
        internal var onClock = false
        /** How far its healing has got, 0 to 1. */
        var heal = 0f
        /** Pressed by its shape's second bounce: the one silhouette of that shape that sparkles. */
        var sparkle = false
        /** Seconds since its sparkles began, as its shape left it (negative before). */
        var sparkleAge = -1f
        /** How far its sparkles have got, 0 to 1, or -1 if it has none (or they haven't begun). */
        val sparklePhase: Float get() = if (!sparkle || sparkleAge < 0f) -1f else (sparkleAge / SPARKLE_TIME).coerceAtMost(1f)
        /** How much of the hollow is left, as a share of the silhouette: 1 fresh, 0 closed. */
        val shrink: Float get() = if (holding) 1f else if (heal >= HEAL_CLOSE) 0f else healShrink(heal).coerceAtLeast(0f)
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

    /** A landing counted as a bounce, on a shape on screen, with the speed it landed at. */
    fun interface BounceListener {
        fun onBounce(body: Body, impactSpeed: Float)
    }

    var width = 0f
        private set
    var height = 0f
        private set
    /** The floor line: the rubber at rest. */
    var floor = 0f
        private set
    /** How far below the floor line the bottom of the area is. */
    var floorDepth = 0f
        private set
    /** The node spacing across the sheet, metres. */
    var nodeSpacing = 0f
        private set
    val bodies = mutableListOf<Body>()
    val imprints = Array(MAX_IMPRINTS) { Imprint() }
    /** How many node spacings cross the area. */
    var nodes = MIN_SHEET_NODES
        private set
    var sheet = Sheet(MIN_SHEET_NODES + 1)
        private set
    /** The sheet hanging at rest under its own weight, which the drawing measures the floor's movement from. */
    var sheetBase = DoubleArray(MIN_SHEET_NODES + 1)
        private set
    /** How far through the next step the scene is, 0 to 1, for drawing. */
    var renderFraction = 0f
        private set

    private var nextId = 0
    private var burstLeft = 0
    private var accumulator = 0.0
    private var stepCount = 0
    private var desired = 0
    private var radii: List<Float> = emptyList()
    private val scratchShadow = Shadow()
    private var tension = 0.0
    private var damping = 0.0
    private var lineDensity = RUBBER_DENSITY * SHEET_THICKNESS * SLAB_THICKNESS.toDouble()
    private var nodeMass = 0.0

    private class Pending(val step: Int, val body: Body)
    private val pending = ArrayList<Pending>()
    private inner class Plan(val step: Int, start: Int, snapshot: List<Body>, sheetCopy: Sheet, queued: List<Pending>) {
        var result: Body? = null
        val iterator: Iterator<Unit> = iterator { result = planDrop(step, start, snapshot, sheetCopy, queued) }
    }
    private var plan: Plan? = null
    /** After a missed planning window the next plan gets twice the lead, up to [MAX_LEAD_BOOST] times. */
    private var leadBoost = 1
    /** The step at which to check, once the area has stopped changing, that every shape still leaves (0: none due). */
    private var checkLeavingAt = 0
    private var calibratedFor = ""

    // Scratch for the contact solve, made once: each body's outline, and its lowest point over each sheet node.
    private val ox = DoubleArray(OUTLINE_POINTS)
    private val oy = DoubleArray(OUTLINE_POINTS)
    private val slots = maxBodies + 2
    private val pCount = IntArray(slots)
    private var low = DoubleArray(0)
    private var pIdx = emptyArray<IntArray>()
    private var pRx = emptyArray<DoubleArray>()
    private var pRy = emptyArray<DoubleArray>()
    private var tIdx = IntArray(0)
    private var tRx = DoubleArray(0)
    private var tRy = DoubleArray(0)
    private var tJ = DoubleArray(0)
    private var tRel = DoubleArray(0)

    /** Scratch for [n] + 1 nodes, made only when the node count changes. */
    private fun allocateScratch(n: Int) {
        low = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        pIdx = Array(slots) { IntArray(n + 1) }
        pRx = Array(slots) { DoubleArray(n + 1) }
        pRy = Array(slots) { DoubleArray(n + 1) }
        tIdx = IntArray(n + 1)
        tRx = DoubleArray(n + 1)
        tRy = DoubleArray(n + 1)
        tJ = DoubleArray(n + 1)
        tRel = DoubleArray(n + 1)
    }

    /**
     * Sizes the area, in metres. The first call schedules the first drop. A later one (the phone turned, the text grew,
     * or a card above opened or closed while the page is open) keeps the shapes inside the new area: one now under the
     * floor is set on it, one now past a side edge is moved in to it, and one too big for the area leaves. With the
     * width unchanged the rubber, and the dents in it, stay as they are. It is cheap, so it can follow an area resizing
     * every frame; once the area has held still a moment, every shape's flight is checked again ([checkEveryShapeLeaves]).
     */
    fun resize(widthMetres: Float, heightMetres: Float, radiiMetres: List<Float>) {
        if (widthMetres == width && heightMetres == height && radiiMetres == radii) return
        val first = width == 0f
        val oldWidth = width
        val oldFloor = floor
        val oldNodes = nodes
        width = widthMetres
        height = heightMetres
        radii = radiiMetres
        if (width <= 0f || height <= 0f || radii.isEmpty()) return
        val largest = radii.max().coerceAtMost(height * MAX_RADIUS_SHARE)
        floorDepth = FLOOR_DEPTH_SHARE * 2f * largest
        floor = height - floorDepth
        // Nodes never farther apart than the smallest shape's radius, so every shape always presses some.
        nodes = max(MIN_SHEET_NODES, kotlin.math.ceil(width / radii.min().coerceAtLeast(1e-3f)).toInt())
        nodeSpacing = width / nodes
        nodeMass = lineDensity * nodeSpacing
        if (nodes != oldNodes || low.isEmpty()) allocateScratch(nodes)
        calibrate()
        val keepSheet = !first && width == oldWidth && nodes == oldNodes && sheet.u.size == nodes + 1
        if (keepSheet) {
            for (i in sheet.u.indices) {
                sheet.u[i] = min(sheet.u[i], floorDepth.toDouble())
                sheet.rest[i] = min(sheet.rest[i], floorDepth.toDouble())
            }
            val shift = floor - oldFloor
            for (imprint in imprints) if (imprint.active) imprint.y += shift
        } else {
            sheet = newSheet()
            for (imprint in imprints) imprint.active = false
        }
        sheetBase = newSheet().u
        plan = null
        pending.clear()
        desired = if (first && autoSpawn) stepCount + ((FIRST_DROP_MIN + random.nextFloat() * (FIRST_DROP_MAX - FIRST_DROP_MIN)) / STEP).roundToInt()
        else max(desired, stepCount)
        // A shape bigger than the new area's largest leaves with the change.
        val biggest = radii.max().coerceAtMost(height * MAX_RADIUS_SHARE) * 1.001f
        bodies.removeAll { it.radius > biggest }
        for (body in bodies) {
            if (!keepSheet) body.imprint = null
            if (body.y + body.radius > floor) {
                // Set on the new floor line, keeping any upward speed it had; it isn't thrown anywhere.
                body.y = floor - body.radius
                body.vy = body.vy.coerceAtMost(0f)
                body.penetration = 0f
                body.contactTime = 0f
                body.sinceLift = 0f
            }
            if (!shapeOffScreen(body)) body.x = body.x.clampSafely(body.radius, width - body.radius)
            body.prevX = body.x
            body.prevY = body.y
        }
        checkLeavingAt = if (bodies.isEmpty()) 0 else stepCount + SETTLE_STEPS
    }

    /**
     * The shapes' throws were planned for the area as it was. Once it has held still, the scene as it stands is run
     * forward, all of them together: any shape that wouldn't now fly off by itself, in time, leaves now (and the run is
     * tried again without it), so no shape is ever left sitting on the floor.
     */
    private fun checkEveryShapeLeaves() {
        var removed = false
        while (bodies.isNotEmpty()) {
            val ghosts = bodies.map { b -> b.ghost().apply { contactTime = 0f; stuck = false } }
            val running = ghosts.toMutableList()
            val s = Sheet(nodes + 1).also { it.copyFrom(sheet) }
            val stays = HashSet<Int>()
            var t = 0f
            while (running.isNotEmpty() && t < MAX_LIFETIME) {
                stepScene(running, s, null)
                running.removeAll { gone(it) }
                for (g in running) if (g.stuck) stays += g.id
                if (stays.isNotEmpty()) break
                t += STEP
            }
            if (t >= MAX_LIFETIME) for (g in running) stays += g.id
            if (stays.isEmpty()) break
            bodies.removeAll { it.id in stays }
            removed = true
        }
        if (removed) {
            plan = null
            pending.clear()
            desired = max(desired, stepCount)
        }
    }

    private fun newSheet(): Sheet {
        val s = Sheet(nodes + 1)
        // Hanging under its own weight: a shallow parabola.
        for (i in 1 until nodes) {
            val x = i * nodeSpacing.toDouble()
            s.u[i] = lineDensity * GRAVITY * x * (width - x) / (2.0 * tension)
        }
        return s
    }

    /**
     * Finds the sheet's tension and damping for this area: a shape of the middle size and the middle weight
     * ([REFERENCE_POUNDS]), dropped from the top, sinks [REFERENCE_SINK] of its height and comes back at [REFERENCE_REBOUND] of its landing
     * speed. Found again only when the width or the shapes' size (to within a tenth) changes, so an area resizing
     * every frame under a card that is opening never recalibrates; remembered per width and size, as it is the same
     * every time.
     */
    private fun calibrate() {
        val raw = radii[min(1, radii.size - 1)].coerceAtMost(height * MAX_RADIUS_SHARE).coerceAtLeast(1e-3f)
        val step = ln(SIZE_STEP)
        val r = exp((ln(raw) / step).roundToInt() * step)
        val key = "$width,$nodes,$r"
        if (key == calibratedFor) return
        calibratedFor = key
        calibrated[key]?.let { tension = it.first; damping = it.second; return }
        val target = REFERENCE_SINK * 2f * r
        val v0 = sqrt(2f * GRAVITY * floor)
        val one = ArrayList<Body>(1)
        fun run(): Pair<Float, Float> {
            val s = newSheet()
            val b = Body(-1, r, REFERENCE_POUNDS * POUND).apply {
                x = width / 2f; y = floor - r - 1e-3f; vy = v0; real = false; shapeIndex = 1
            }
            one.clear(); one += b
            var sink = 0f
            var out = 0f
            var landed = false
            var steps = 0
            while (steps < 480 && out == 0f) {
                stepScene(one, s, null)
                b.spin = 0f; b.vx = 0f
                sink = max(sink, b.penetration)
                if (b.inContact) landed = true else if (landed && b.vy < 0f) out = -b.vy
                steps++
            }
            return sink to out / v0
        }
        damping = 2.0
        repeat(2) {
            var lo = 1e2
            var hi = 1e8
            repeat(20) {
                tension = sqrt(lo * hi)
                if (run().first > target) lo = tension else hi = tension
            }
            tension = sqrt(lo * hi)
            var dLo = 0.0
            var dHi = 400.0
            repeat(16) {
                damping = (dLo + dHi) / 2
                if (run().second > REFERENCE_REBOUND) dLo = damping else dHi = damping
            }
            damping = (dLo + dHi) / 2
        }
        calibrated[key] = tension to damping
    }

    /**
     * Advances the scene by [frameSeconds] (clamped), in fixed steps of [STEP] so results don't depend on frame rate,
     * planning the next drops first within this frame's share of time ([planBudgetNanos]).
     */
    fun advance(frameSeconds: Float, listener: BounceListener? = null) {
        if (width <= 0f || height <= 0f || radii.isEmpty()) return
        if (planBudgetNanos > 0L) work(planBudgetNanos)
        accumulator += frameSeconds.coerceIn(0f, MAX_FRAME).toDouble()
        while (accumulator >= STEP_SECONDS - STEP_TOLERANCE) {
            accumulator -= STEP_SECONDS
            // A planned drop enters on exactly the step it was forecast from.
            while (pending.isNotEmpty() && pending[0].step <= stepCount) {
                val p = pending.removeAt(0)
                if (p.step == stepCount) bodies += Body(p.body.id, p.body.radius, p.body.mass).also { it.copyFrom(p.body) }
            }
            plan?.let { if (it.step <= stepCount) abandonPlan() }
            if (checkLeavingAt in 1..stepCount) {
                checkLeavingAt = 0
                checkEveryShapeLeaves()
            }
            if (autoSpawn) startPlans()
            stepScene(bodies, sheet, listener)
            floorEffects(STEP)
            var i = bodies.size - 1
            var lost = false
            while (i >= 0) {
                val b = bodies[i]
                if (isGone(b)) bodies.removeAt(i)
                else if (b.stuck || b.age > MAX_LIFETIME + LIFETIME_SLACK) {
                    // The last resort, for a scene the forecast never saw (none should reach it): a shape that would
                    // sit on the floor, or outstay its time, leaves rather than sit there for good.
                    bodies.removeAt(i)
                    lost = true
                }
                i--
            }
            if (lost) {
                plan = null
                pending.clear()
                desired = max(desired, stepCount)
            }
            stepCount++
        }
        renderFraction = (accumulator / STEP_SECONDS).toFloat().coerceIn(0f, 1f)
    }

    /** Starts planning the next drop once it is near ([PLAN_LEAD]) and there is room; synchronous plans finish here. */
    private fun startPlans() {
        while (plan == null && bodies.size + pending.size < maxBodies && stepCount >= desired - LEAD_STEPS * leadBoost) {
            val step = max(desired, stepCount + MIN_LEAD_STEPS * leadBoost)
            val snapshot = bodies.map { it.ghost() }
            val copy = Sheet(nodes + 1).also { it.copyFrom(sheet) }
            plan = Plan(step, stepCount, snapshot, copy, pending.toList())
            if (planBudgetNanos > 0L) return
            finishPlan()
        }
    }

    /** Works on the planned drop for up to [budgetNanos], and at least one slice of it, so it always moves on. */
    fun work(budgetNanos: Long) {
        val end = System.nanoTime() + budgetNanos
        do {
            val p = plan ?: return
            if (p.iterator.hasNext()) p.iterator.next() else settlePlan(p)
        } while (System.nanoTime() < end)
    }

    private fun finishPlan() {
        val p = plan ?: return
        while (p.iterator.hasNext()) p.iterator.next()
        settlePlan(p)
    }

    private fun settlePlan(p: Plan) {
        plan = null
        val body = p.result
        if (body != null) {
            pending += Pending(p.step, Body(nextId++, body.radius, body.mass).also { it.copyFrom(body); it.real = true })
            desired = p.step + gapSteps()
            leadBoost = 1
        } else {
            desired = p.step + RETRY_STEPS
        }
    }

    /** A plan not done in time is dropped; the next gets more time, as this phone needs it. */
    private fun abandonPlan() {
        plan = null
        leadBoost = min(leadBoost * 2, MAX_LEAD_BOOST)
        desired = stepCount + LEAD_STEPS * leadBoost
    }

    /** The steps to the next drop: a short gap within a burst, a long and uneven one between bursts. */
    private fun gapSteps(): Int {
        if (burstLeft <= 0) {
            val roll = random.nextFloat()
            burstLeft = when {
                roll < BURST_ONE -> 1
                roll < BURST_ONE + BURST_TWO -> 2
                roll < BURST_THREE_BELOW -> 3
                else -> 4
            }
        }
        burstLeft--
        val gap = if (burstLeft > 0) BURST_GAP_MIN + random.nextFloat() * (BURST_GAP_MAX - BURST_GAP_MIN)
        else min(QUIET_GAP_MIN - ln(1f - random.nextFloat() * 0.999f) * QUIET_GAP_MEAN, QUIET_GAP_MAX)
        return (gap / STEP).roundToInt()
    }

    /**
     * Plans one drop for [spawnStep]: runs the scene as it stands (the drops already planned entering on theirs) up to
     * it, then tries throws until the forecast keeps every rule, yielding every few steps.
     */
    private suspend fun SequenceScope<Unit>.planDrop(spawnStep: Int, start: Int, snapshot: List<Body>, sheetCopy: Sheet, queued: List<Pending>): Body? {
        val ghosts = snapshot.toMutableList()
        val queue = ArrayDeque(queued)
        var n = start
        while (n < spawnStep) {
            while (queue.isNotEmpty() && queue.first().step == n) ghosts += queue.removeFirst().body.ghost()
            stepScene(ghosts, sheetCopy, null)
            ghosts.removeAll { gone(it) }
            n++
            if (n % PLAN_SLICE == 0) yield(Unit)
        }
        while (queue.isNotEmpty() && queue.first().step == n) ghosts += queue.removeFirst().body.ghost()
        if (ghosts.size >= maxBodies) return null
        val template = newDrop() ?: return null
        val attemptSheet = Sheet(nodes + 1)
        repeat(PLAN_TRIES) {
            val thrown = aim(template) ?: return@repeat
            attemptSheet.copyFrom(sheetCopy)
            if (forecast(ghosts.map { it.ghost() }.toMutableList(), attemptSheet, thrown.ghost())) return thrown
        }
        return null
    }

    /** A new shape, not yet thrown: its size from the area's four, its weight at random, a random shape and turn. */
    private fun newDrop(): Body? {
        if (radii.isEmpty()) return null
        val r = radii[random.nextInt(radii.size)].coerceAtMost(height * MAX_RADIUS_SHARE)
        val pounds = MIN_POUNDS + random.nextFloat() * (MAX_POUNDS - MIN_POUNDS)
        return Body(-1, r, pounds * POUND).apply {
            angle = random.nextFloat() * 360f
            shapeIndex = random.nextInt(outlines.size) + outlines.size * 100
            y = -r
        }
    }

    /**
     * A throw for [template]: from a random place under the card, at a random downward speed, sideways towards the
     * nearer edge more often than not, fast enough to have left after [MAX_LANDINGS] flights and slow enough to land
     * first on screen, spinning the way it flies. Null if no throw from that place can.
     */
    private fun aim(template: Body): Body? {
        val r = template.radius
        val margin = r * PLAN_EDGE_MARGIN
        val reach = r * SHAPE_REACH
        val x = (width * random.nextFloat()).clampSafely(margin, width - margin)
        val vy = DROP_MIN + random.nextFloat() * (DROP_MAX - DROP_MIN)
        val v1 = sqrt(vy * vy + 2f * GRAVITY * floor)
        val tFall = (v1 - vy) / GRAVITY
        val towardRight = (x > width / 2f) != (random.nextFloat() < 0.25f)
        val dir = if (towardRight) 1f else -1f
        val toEdge = if (towardRight) width - x else x
        val least = min((toEdge + reach) / (tFall + 4f * v1 / GRAVITY), MAX_THROW)
        val most = min((toEdge - margin) / tFall, MAX_THROW)
        if (least > most) return null
        val vx = dir * (least + random.nextFloat() * (most - least))
        val spin = vx / r * (0.75f + 0.25f * random.nextFloat())
        return Body(-1, r, template.mass).apply {
            copyFrom(template)
            this.x = x; this.vy = vy; this.vx = vx; this.spin = spin; turnDirection = dir
            lastVX = vx; lastVY = vy; prevX = x; prevY = y; prevAngle = angle
        }
    }

    /** Runs [ghosts] and [candidate] on [s] until all have left; false as soon as any breaks a rule. */
    private suspend fun SequenceScope<Unit>.forecast(ghosts: MutableList<Body>, s: Sheet, candidate: Body): Boolean {
        ghosts += candidate
        var time = 0f
        var steps = 0
        while (ghosts.isNotEmpty()) {
            if (time > MAX_LIFETIME) return false
            stepScene(ghosts, s, null)
            var i = ghosts.size - 1
            while (i >= 0) {
                val g = ghosts[i]
                if (!keepsTheRules(g)) return false
                if (gone(g)) {
                    if (g.bounces < 2) return false
                    ghosts.removeAt(i)
                } else if (g.age > MAX_LIFETIME) return false
                i--
            }
            time += STEP
            if (++steps % PLAN_SLICE == 0) yield(Unit)
        }
        return true
    }

    private fun keepsTheRules(g: Body): Boolean {
        if (g.contacts > MAX_LANDINGS || g.stuck) return false
        if (g.entered && g.y - g.radius < -1e-3f) return false
        // Nor is one still coming in knocked back up under the card.
        if (g.y + g.radius < -1e-3f) return false
        // Pressed in, never out of sight: a small heavy shape that would sink right under the floor isn't thrown so.
        if (g.penetration > MAX_SINK_SHARE * 2f * g.radius) return false
        if (g.landCount >= 1 && (g.landX0 < g.radius || g.landX0 > width - g.radius)) return false
        if (g.landCount >= 2 && (g.landX1 < g.radius || g.landX1 > width - g.radius)) return false
        return true
    }

    /**
     * Drops a shape in now, planned as every drop is but from the scene as it stands, at [fraction] of the width if
     * given; null if no throw keeps the rules. For tests.
     */
    internal fun spawn(fraction: Float? = null): Body? {
        if (radii.isEmpty() || bodies.size >= maxBodies) return null
        val template = newDrop() ?: return null
        repeat(PLAN_TRIES * 2) {
            val thrown = aim(template) ?: return@repeat
            if (fraction != null) {
                thrown.x = (width * fraction).clampSafely(thrown.radius * PLAN_EDGE_MARGIN, width - thrown.radius * PLAN_EDGE_MARGIN)
                thrown.prevX = thrown.x
            }
            val s = Sheet(nodes + 1).also { it.copyFrom(sheet) }
            val run = iterator { spawnForecastKept = forecast(bodies.map { it.ghost() }.toMutableList(), s, thrown.ghost()) }
            while (run.hasNext()) run.next()
            if (spawnForecastKept || fraction != null) {
                val body = Body(nextId++, thrown.radius, thrown.mass).also { it.copyFrom(thrown); it.real = true }
                bodies += body
                return body
            }
        }
        return null
    }
    private var spawnForecastKept = false

    /** One fixed step of [list] on [s]: contact with the rubber in substeps, the rubber itself, then knocks. */
    private fun stepScene(list: List<Body>, s: Sheet, listener: BounceListener?) {
        val dt = STEP / SUBSTEPS
        for (j in list.indices) {
            val b = list[j]
            b.prevX = b.x; b.prevY = b.y; b.prevAngle = b.angle; b.touched = false
            b.profiled = !shapeOffScreen(b) && j < slots
            if (b.profiled) profileOf(b, j) else if (j < slots) pCount[j] = 0
        }
        repeat(SUBSTEPS) {
            s.load.fill(false)
            for (j in list.indices) {
                val b = list[j]
                if (b.profiled && contact(b, j, s)) b.touched = true
                val speed = sqrt(b.vx * b.vx + b.vy * b.vy)
                b.vx += -b.dragK * speed * b.vx * dt
                b.vy += (GRAVITY - b.dragK * speed * b.vy) * dt
                b.x += b.vx * dt
                b.y += b.vy * dt
                b.angle = (b.angle + b.spin * dt * 180f / PI.toFloat()) % 360f
                // The bottom of the area is hard ground under the rubber.
                if (!shapeOffScreen(b) && b.y + b.radius > height) {
                    b.y = height - b.radius
                    if (b.vy > 0f) b.vy = -b.vy * hardRestitution(b.density, b.vy)
                }
            }
            sheetStep(s, dt.toDouble())
        }
        for (b in list) {
            afterStep(b, listener)
            animateLook(b, STEP)
        }
        collide(list)
    }

    /** The rubber over [dt]: tension (stiffening with stretch) and damping solved implicitly, heat, creep and healing. */
    private fun sheetStep(s: Sheet, dt: Double) {
        val n = s.u.size
        val dx = nodeSpacing.toDouble()
        var length = 0.0
        for (i in 0 until n - 1) {
            val d = s.u[i + 1] - s.u[i]
            length += sqrt(dx * dx + d * d)
        }
        val strain = max(length / width - 1.0, 0.0)
        val t = tension * (1.0 + STIFFENING * strain)
        val k = t / (lineDensity * dx * dx)
        val kdt = dt * dt * k
        val damp = damping * dt
        val off = -kdt
        for (i in 1 until n - 1) {
            val e0 = s.u[i - 1] - s.rest[i - 1]
            val e1 = s.u[i] - s.rest[i]
            val e2 = s.u[i + 1] - s.rest[i + 1]
            s.rhs[i] = s.v[i] + dt * (k * (e0 - 2.0 * e1 + e2) + GRAVITY)
        }
        var diag = 1.0 + 2.0 * kdt + damp * (1.0 + (DAMAGE_DAMPING - 1.0) * s.damage[1])
        s.cp[1] = off / diag
        s.rhs[1] /= diag
        for (i in 2 until n - 1) {
            diag = 1.0 + 2.0 * kdt + damp * (1.0 + (DAMAGE_DAMPING - 1.0) * s.damage[i])
            val m = diag - off * s.cp[i - 1]
            s.cp[i] = off / m
            s.rhs[i] = (s.rhs[i] - off * s.rhs[i - 1]) / m
        }
        s.v[n - 2] = s.rhs[n - 2]
        for (i in n - 3 downTo 1) s.v[i] = s.rhs[i] - s.cp[i] * s.v[i + 1]
        val heatPer = 1.0 / (HEAT_CAPACITY * dx)
        val damagePer = 1.0 / (DAMAGE_ENERGY * dx)
        for (i in 1 until n - 1) {
            val lost = damping * (1.0 + (DAMAGE_DAMPING - 1.0) * s.damage[i]) * nodeMass * s.v[i] * s.v[i] * dt
            s.heat[i] += lost * heatPer
            s.damage[i] = min(1.0, s.damage[i] + lost * damagePer)
        }
        for (i in 1 until n - 1) {
            s.u[i] += s.v[i] * dt
            if (s.u[i] > floorDepth) {
                s.u[i] = floorDepth.toDouble()
                if (s.v[i] > 0.0) s.v[i] = 0.0
            }
        }
        val spread = HEAT_SPREAD * dt / (dx * dx)
        val cool = exp(-dt / COOLING_TIME)
        val heal = dt / RUBBER_HEAL_TIME
        val damageHeal = dt / DAMAGE_HEAL_TIME
        var prev = s.heat[0]
        for (i in 1 until n - 1) {
            val here = s.heat[i]
            s.heat[i] = (here + spread * (prev - 2.0 * here + s.heat[i + 1])) * cool
            prev = here
            val warmth = s.heat[i] / CREEP_WARMTH
            if (s.load[i]) {
                val creep = CREEP_RATE * (1.0 + (DAMAGE_CREEP - 1.0) * s.damage[i]) * warmth * warmth * dt
                s.rest[i] += (s.u[i] - s.rest[i]) * min(creep, 1.0)
            } else {
                s.rest[i] -= s.rest[i] * heal
            }
            s.damage[i] -= s.damage[i] * damageHeal
        }
    }

    /**
     * The shape's underside over the sheet: for every node under it, the lowest point of its (morphing, turned)
     * outline there, into slot [j]. Walks the outline's edges once; each covers a node or two.
     */
    private fun profileOf(b: Body, j: Int) {
        val to = outlines[Math.floorMod(b.shapeIndex, outlines.size)]
        val from = outlines[Math.floorMod(b.shapeIndex - 1, outlines.size)]
        val m = b.morphProgress.coerceIn(0f, 1.2f).toDouble()
        val rot = (drawnRotation(b) * PI / 180.0)
        val c = cos(rot)
        val sn = sin(rot)
        val dx = nodeSpacing.toDouble()
        for (k in 0 until OUTLINE_POINTS) {
            val rr = b.radius * (from[k] + (to[k] - from[k]) * m)
            ox[k] = (COS[k] * c - SIN[k] * sn) * rr
            oy[k] = (COS[k] * sn + SIN[k] * c) * rr
        }
        var first = nodes
        var last = 0
        for (k in 0 until OUTLINE_POINTS) {
            val k2 = if (k + 1 == OUTLINE_POINTS) 0 else k + 1
            val x1 = ox[k]
            val y1 = oy[k]
            val x2 = ox[k2]
            val y2 = oy[k2]
            if (x1 == x2) continue
            val a = max(kotlin.math.ceil((b.x + min(x1, x2)) / dx).toInt(), 1)
            val z = min(kotlin.math.floor((b.x + max(x1, x2)) / dx).toInt(), nodes - 1)
            val slope = (y2 - y1) / (x2 - x1)
            for (i in a..z) {
                val y = y1 + slope * (i * dx - b.x - x1)
                if (y > low[i]) low[i] = y
                if (i < first) first = i
                if (i > last) last = i
            }
        }
        val idx = pIdx[j]
        val prx = pRx[j]
        val pry = pRy[j]
        var count = 0
        for (i in first..last) {
            if (low[i] > Double.NEGATIVE_INFINITY) {
                idx[count] = i; prx[count] = i * dx - b.x; pry[count] = low[i]
                count++
                low[i] = Double.NEGATIVE_INFINITY
            }
        }
        pCount[j] = count
    }

    /**
     * Where [b] (profiled into slot [j]) presses into [s]: the nodes under it move with it, and every one it pushes
     * pushes back at once (a simultaneous impulse each, sharing the body's mass), with Coulomb friction at their center.
     * The energy the contact takes warms and wears the rubber there. True if it touches.
     */
    private fun contact(b: Body, j: Int, s: Sheet): Boolean {
        val idx = pIdx[j]
        val prx = pRx[j]
        val pry = pRy[j]
        val count = pCount[j]
        var share = 0
        for (q in 0 until count) {
            val i = idx[q]
            val surface = b.y + pry[q]
            if (surface <= floor + s.u[i]) continue
            s.load[i] = true
            s.by[i] = b.id
            s.u[i] = min(surface - floor, floorDepth.toDouble())
            tIdx[share] = i; tRx[share] = prx[q]; tRy[share] = pry[q]
            share++
        }
        if (share == 0) return false
        val invMass = 1.0 / b.mass
        val invInertia = 1.0 / b.inertia
        for (q in 0 until share) {
            val rx = tRx[q]
            val rel = b.vy + b.spin * rx - s.v[tIdx[q]]
            tRel[q] = rel
            tJ[q] = if (rel <= 0.0) 0.0 else rel / (1.0 / nodeMass + share * (invMass + rx * rx * invInertia))
        }
        var total = 0.0
        var atY = 0.0
        val heatPer = 1.0 / (HEAT_CAPACITY * nodeSpacing)
        val damagePer = 1.0 / (DAMAGE_ENERGY * nodeSpacing)
        var vy = b.vy.toDouble()
        var spin = b.spin.toDouble()
        for (q in 0 until share) {
            val impulse = tJ[q]
            if (impulse == 0.0) continue
            val i = tIdx[q]
            s.v[i] += impulse / nodeMass
            vy -= impulse * invMass
            spin -= tRx[q] * impulse * invInertia
            val lost = 0.5 * impulse * tRel[q]
            s.heat[i] += lost * heatPer
            s.damage[i] = min(1.0, s.damage[i] + lost * damagePer)
            total += impulse
            atY += impulse * tRy[q]
        }
        var vx = b.vx.toDouble()
        if (total > 0.0) {
            val ry = atY / total
            val slip = vx - spin * ry
            val f = (slip / (invMass + ry * ry * invInertia)).coerceIn(-SHEET_FRICTION * total, SHEET_FRICTION * total)
            vx -= f * invMass
            spin += ry * f * invInertia
        }
        b.vx = vx.toFloat(); b.vy = vy.toFloat(); b.spin = spin.toFloat()
        return true
    }

    /** After a step: landings counted, the morph begun as it lifts off, contact time, the light inside. */
    private fun afterStep(b: Body, listener: BounceListener?) {
        if (b.touched && !b.inContact) {
            b.contacts++
            when (b.landCount) {
                0 -> b.landX0 = b.x
                1 -> b.landX1 = b.x
            }
            b.landCount++
            if (b.bounces == 0 || b.sinceLift >= MIN_FLIGHT) {
                b.bounces++
                b.morphOnLift = true
                if (b.real) {
                    press(b)
                    if (!shapeOffScreen(b)) listener?.onBounce(b, max(b.lastVY, b.vy))
                }
            }
            b.contactTime = 0f
        }
        if (b.touched) {
            b.contactTime += STEP
            if (b.contactTime > STUCK_TIME) b.stuck = true
        } else if (b.inContact) {
            b.sinceLift = 0f
            // The new shape begins as it leaves the rubber, never while the rubber holds it.
            if (b.morphOnLift) {
                b.morphOnLift = false
                b.shapeIndex++
                b.morphProgress = 0f
            }
        }
        b.inContact = b.touched
        if (!b.inContact) {
            b.sinceLift += STEP
            b.spin *= exp(-SPIN_AIR_DECAY * STEP)
        }
        b.penetration = max(b.y + b.radius - floor, 0f)
        if (!b.entered && b.y - b.radius >= 0f) b.entered = true
        b.age += STEP
    }

    /**
     * Shapes that touch bounce apart: an impulse along the line between their centers, shared by mass, and a
     * friction impulse across it that sets them spinning. Overlap is pushed out so they never sink into each other.
     */
    private fun collide(list: List<Body>) {
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                // A shape already gone from the screen knocks nothing, as the forecast, which drops it then, has it.
                if (gone(a) || gone(b)) continue
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
                val share = (minDistance - distance) / (inverseA + inverseB)
                a.x -= nx * share * inverseA; a.y -= ny * share * inverseA
                b.x += nx * share * inverseB; b.y += ny * share * inverseB
                val approach = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if (approach >= 0f) continue
                val impulse = -(1f + BODY_RESTITUTION) * approach / (inverseA + inverseB)
                a.vx -= impulse * inverseA * nx; a.vy -= impulse * inverseA * ny
                b.vx += impulse * inverseB * nx; b.vy += impulse * inverseB * ny
                val tx = -ny
                val ty = nx
                val slide = (b.vx - a.vx) * tx + (b.vy - a.vy) * ty - (a.spin * a.radius + b.spin * b.radius)
                val tangentialMass = 1f / (inverseA + inverseB + a.radius * a.radius / a.inertia + b.radius * b.radius / b.inertia)
                val friction = (slide * tangentialMass).coerceIn(-BODY_FRICTION * impulse, BODY_FRICTION * impulse)
                a.vx += friction * tx * inverseA; a.vy += friction * ty * inverseA
                b.vx -= friction * tx * inverseB; b.vy -= friction * ty * inverseB
                a.spin += friction * a.radius / a.inertia
                b.spin += friction * b.radius / b.inertia
            }
        }
        // A knock never pushes a shape through the hard ground at the bottom of the area.
        for (b in list) {
            if (!shapeOffScreen(b) && b.y + b.radius > height) {
                b.y = height - b.radius
                if (b.vy > 0f) b.vy = 0f
            }
        }
    }

    /** A new silhouette for [b]'s landing: a free one, or else the most healed. Older ones it covers heal on out. */
    private fun press(b: Body) {
        var pick = imprints[0]
        for (imprint in imprints) {
            if (!imprint.active) { pick = imprint; break }
            if (!imprint.holding && (pick.holding || imprint.sincePressed > pick.sincePressed)) pick = imprint
        }
        for (old in imprints) {
            if (!old.active || old.holding || old === pick) continue
            if (abs(old.x - b.x) < old.radius + b.radius) old.onClock = true
        }
        pick.active = true
        pick.holding = true
        pick.soft = b.bounces == 1
        pick.bodyId = b.id
        pick.radius = b.radius
        pick.depth = b.penetration
        pick.x = b.x
        pick.y = b.y
        pick.shapeIndex = b.shapeIndex
        pick.morphProgress = b.morphProgress
        pick.rotationDegrees = drawnRotation(b)
        pick.sincePressed = 0f
        pick.restAtRelease = 0.0
        pick.onClock = false
        pick.heal = 0f
        pick.sparkle = b.bounces == SPARKLE_BOUNCE
        pick.sparkleAge = -1f
        b.imprint = pick
    }

    /** The deepest dent the rubber keeps under [imprint]. */
    private fun restUnder(imprint: Imprint): Double {
        val a = max(1, ((imprint.x - imprint.radius * 0.9f) / nodeSpacing).toInt())
        val z = min(nodes - 1, kotlin.math.ceil((imprint.x + imprint.radius * 0.9f) / nodeSpacing).toInt())
        var most = 0.0
        for (i in a..z) if (sheet.rest[i] > most) most = sheet.rest[i]
        return most
    }

    /**
     * The silhouettes: one held takes its shape's outline at its deepest; released, it closes as the rubber's dent
     * there recovers, only ever closing, and once that dent is all but gone (or a newer silhouette has covered it) it
     * finishes on the healing's own clock, its last moment breaking into pixels.
     */
    private fun floorEffects(dt: Float) {
        for (b in bodies) {
            val imprint = b.imprint ?: continue
            if (!imprint.holding || !b.inContact) continue
            if (b.penetration > imprint.depth) {
                imprint.depth = b.penetration
                imprint.x = b.x
                imprint.y = b.y
                imprint.shapeIndex = b.shapeIndex
                imprint.morphProgress = b.morphProgress
                imprint.rotationDegrees = drawnRotation(b)
            }
        }
        for (imprint in imprints) {
            if (!imprint.active) continue
            imprint.sincePressed += dt
            if (imprint.holding) {
                if (bodies.any { it.imprint === imprint && it.inContact }) continue
                imprint.holding = false
                if (imprint.sparkle) imprint.sparkleAge = 0f
                imprint.restAtRelease = min(restUnder(imprint), imprint.depth.toDouble())
                if (imprint.restAtRelease < 1e-4) imprint.onClock = true
            }
            if (!imprint.onClock) {
                val left = (restUnder(imprint) / imprint.restAtRelease).toFloat().coerceIn(0f, 1f)
                if (left < CLOSED_SHARE) imprint.onClock = true
                else imprint.heal = max(imprint.heal, healForShrink(left))
            }
            if (imprint.onClock) imprint.heal = min(1f, imprint.heal + dt / HEAL_TIME)
            if (imprint.sparkleAge >= 0f && imprint.sparkleAge < SPARKLE_TIME) imprint.sparkleAge += dt
            val sparkled = !imprint.sparkle || imprint.sparkleAge >= SPARKLE_TIME
            if (imprint.heal >= 1f && imprint.sincePressed > RIPPLES_DONE && sparkled) imprint.active = false
        }
    }

    /** Whether the shape is fully past a side edge, however it is turned. */
    internal fun shapeOffScreen(body: Body): Boolean {
        val reach = body.radius * SHAPE_REACH
        return body.x - reach > width || body.x + reach < 0f
    }

    private fun gone(body: Body): Boolean = body.y - body.radius > height || shapeOffScreen(body)

    /** Whether nothing of the shape, nor of its shadow, is left on screen. */
    internal fun isGone(body: Body): Boolean {
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
     * Moves what the shape looks like on by [dt]; nothing here changes how it moves.
     * - The morph to its new shape springs on Material 3 Expressive's fast spatial spring, overshooting a touch.
     * - It scales in from nothing as it comes out from under the card, on the default spatial spring.
     * - The light inside pools where the shape's felt gravity (gravity less its acceleration) holds it, on a soft
     *   spring, so it sloshes as the shape falls, lands and spins; it tightens under a hard push and flashes on an
     *   impact, and its swirl turns with the spin.
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
        if (body.appear < 1f || body.appearVelocity != 0f) {
            body.appearVelocity += springAcceleration(body.appear - 1f, body.appearVelocity, APPEAR_STIFFNESS, APPEAR_DAMPING) * dt
            body.appear = (body.appear + body.appearVelocity * dt).coerceAtMost(MAX_APPEAR)
            if (abs(body.appear - 1f) < 1e-3f && abs(body.appearVelocity) < 1e-2f) {
                body.appear = 1f
                body.appearVelocity = 0f
            }
        }
        if (!body.real) return
        val ax = (body.vx - body.lastVX) / dt
        val ay = (body.vy - body.lastVY) / dt
        body.lastVX = body.vx
        body.lastVY = body.vy
        val fx = -ax / GRAVITY
        val fy = (GRAVITY - ay) / GRAVITY
        val felt = min(hypot(fx, fy), 6f)
        val target = 0.42f * min(felt, 1.6f) / 1.6f
        val direction = atan2(fy, fx)
        val tx = cos(direction) * target
        val ty = sin(direction) * target
        val c = 2f * LIGHT_DAMPING * sqrt(LIGHT_STIFFNESS)
        body.lightVX += (LIGHT_STIFFNESS * (tx - body.lightX) - c * body.lightVX) * dt
        body.lightVY += (LIGHT_STIFFNESS * (ty - body.lightY) - c * body.lightVY) * dt
        body.lightX = (body.lightX + body.lightVX * dt).coerceIn(-0.6f, 0.6f)
        body.lightY = (body.lightY + body.lightVY * dt).coerceIn(-0.6f, 0.6f)
        val spreadTarget = 0.95f - 0.45f * min(felt, 1.5f) / 1.5f
        body.lightSpread += (spreadTarget - body.lightSpread) * min(6f * dt, 1f)
        if (felt > 1.5f) body.flash = max(body.flash, min((felt - 1.5f) / 4f, 1f))
        body.flash *= exp(-dt / FLASH_TIME)
        body.swirl += (0.6f + 0.075f * abs(body.spin)) * dt + body.spin * 0.4f * dt
    }

    companion object {
        const val MAX_SHAPES = 4
        /** At most this many landings before a shape has left. */
        const val MAX_LANDINGS = 4
        const val GRAVITY = 9.81f
        const val AIR_DENSITY = 1.2f
        const val DRAG_COEFFICIENT = 0.47f
        const val SLAB_THICKNESS = 0.04f
        /** A solid disc's moment of inertia is ½mr². */
        const val INERTIA_SHARE = 0.5f
        /** Every shape weighs, at random, between these, whatever its size. */
        const val MIN_POUNDS = 50f
        const val MAX_POUNDS = 100f
        const val POUND = 0.45359237f

        /** The rubber: density, thickness, how much it stiffens as it stretches, and how many nodes cross the area. */
        const val RUBBER_DENSITY = 1100.0
        const val SHEET_THICKNESS = 0.006
        const val STIFFENING = 6.0
        const val MIN_SHEET_NODES = 56
        const val SUBSTEPS = 6
        /** The floor line sits this share of the largest shape's height above the bottom of the area. */
        const val FLOOR_DEPTH_SHARE = 0.66f
        /** The sheet is tuned so a shape of the middle weight sinks half its height and comes back at 60% of its speed. */
        const val REFERENCE_POUNDS = 75f
        /** No shape is thrown so that it would sink deeper than this share of its height. */
        const val MAX_SINK_SHARE = 0.9f
        /** The hard ground's restitution is given for a stone shape (density, kg/m³). */
        const val REFERENCE_DENSITY = 2600f
        const val REFERENCE_SINK = 0.5f
        const val REFERENCE_REBOUND = 0.6f
        /** Heat: the rubber's heat capacity (J/kg·K, per metre of sheet here), how it spreads, and how it cools. */
        const val HEAT_CAPACITY = 900.0
        const val HEAT_SPREAD = 0.004
        const val COOLING_TIME = 2.2
        /** Warm loaded rubber creeps towards its dent at this rate (per second, at [CREEP_WARMTH] K above the room's). */
        const val CREEP_RATE = 9.0
        const val CREEP_WARMTH = 20.0
        /** Unloaded, a dent heals with this time constant. */
        const val RUBBER_HEAL_TIME = 0.9
        /** Wear: the energy (J per metre) that wears a node out, and how much more a worn one damps and creeps. */
        const val DAMAGE_ENERGY = 150000.0
        const val DAMAGE_DAMPING = 12.0
        const val DAMAGE_CREEP = 5.0
        const val DAMAGE_HEAL_TIME = 4.0
        /** The hard ground under the rubber: Johnson's elastic-plastic restitution, e ∝ (ρv²)^(-1/8). */
        const val HARD_E0 = 0.8f
        const val HARD_V0 = 6f
        const val HARD_E_MAX = 0.9f
        const val HARD_E_MIN = 0.25f

        const val SHEET_FRICTION = 0.15
        const val BODY_RESTITUTION = 0.55f
        const val BODY_FRICTION = 0.3f
        const val SPIN_AIR_DECAY = 0.08f
        /** However turned or morphing, no part of a shape is drawn farther than this many radii out. */
        const val SHAPE_REACH = 1.6f
        /** A floor touch sooner than this after leaving it belongs to the same bounce. */
        const val MIN_FLIGHT = 0.3f
        /** In touch with the floor this long, a shape would be sitting on it: no throw may let that happen. */
        const val STUCK_TIME = 1f
        /** No shape's radius is more than this share of the area's height. */
        const val MAX_RADIUS_SHARE = 0.3f

        /** Shapes come in from under the card at 0.4 to 3.5 m/s, thrown sideways at up to 6 m/s. */
        const val DROP_MIN = 0.4f
        const val DROP_MAX = 3.5f
        const val MAX_THROW = 6f
        const val PLAN_TRIES = 16
        /** Both first landings are planned at least this many radii inside the side edges. */
        const val PLAN_EDGE_MARGIN = 1.1f
        /** Each drop is planned this far ahead, and never less than [MIN_LEAD] ahead. */
        const val PLAN_LEAD = 1f
        const val MIN_LEAD = 0.25f
        /** A missed plan doubles the lead, up to this many times. */
        const val MAX_LEAD_BOOST = 4
        /** Planning yields every this many steps. */
        const val PLAN_SLICE = 8
        /** The animation gives planning this much of each frame. */
        const val PLAN_BUDGET_NANOS = 3_000_000L
        const val RETRY = 0.15f

        /** The first drop comes 0.15 to 0.8 s after the page opens. */
        const val FIRST_DROP_MIN = 0.15f
        const val FIRST_DROP_MAX = 0.8f
        /** A burst is one shape 45% of the time, two 30%, three 17%, otherwise four. */
        const val BURST_ONE = 0.45f
        const val BURST_TWO = 0.3f
        const val BURST_THREE_BELOW = 0.92f
        const val BURST_GAP_MIN = 0.07f
        const val BURST_GAP_MAX = 0.55f
        /** Between bursts: at least 0.45 s, on average 1.7 s, at most 4.5 s. */
        const val QUIET_GAP_MIN = 0.45f
        const val QUIET_GAP_MEAN = 1.25f
        const val QUIET_GAP_MAX = 4.5f
        /** Every shape has left by this age: every drop is planned to. */
        const val MAX_LIFETIME = 9f
        /** A shape the forecast never saw is let go this long after [MAX_LIFETIME] at the latest. */
        const val LIFETIME_SLACK = 1f
        /** The area must hold still this long before its shapes' flights are checked again. */
        const val SETTLE_SECONDS = 0.25f
        /** The rubber is calibrated afresh only when the shapes' size changes by this factor. */
        const val SIZE_STEP = 1.1f

        /** The simulation's fixed step: 240 Hz, two steps a frame at 120 Hz, four at 60 Hz. */
        const val STEP = 1f / 240f
        private const val STEP_SECONDS = 1.0 / 240.0
        private const val STEP_TOLERANCE = 1e-7
        const val MAX_FRAME = 1f / 20f
        private val LEAD_STEPS = (PLAN_LEAD / STEP).roundToInt()
        private val MIN_LEAD_STEPS = (MIN_LEAD / STEP).roundToInt()
        private val RETRY_STEPS = (RETRY / STEP).roundToInt()
        private val SETTLE_STEPS = (SETTLE_SECONDS / STEP).roundToInt()

        /** Silhouettes kept at once. */
        const val MAX_IMPRINTS = 8
        /** A released silhouette whose dent is down to this share finishes on the healing's own clock. */
        const val CLOSED_SHARE = 0.05f
        /** Healing on its own clock, a silhouette takes this long from fresh to gone. */
        const val HEAL_TIME = 1.6f
        /** A later (hard) landing's one ripple runs across the floor this long, seconds: sharp and quick. */
        const val HARD_RIPPLE_TIME = 0.34f
        /** Only the silhouette of a shape's second bounce sparkles, once, for this long, as the shape leaves it. */
        const val SPARKLE_BOUNCE = 2
        const val SPARKLE_TIME = 0.45f
        /** A silhouette is kept at least this long, so its ripples run their course. */
        const val RIPPLES_DONE = 1.2f

        /** The morph's spring: Material 3 Expressive's fast spatial spring (damping 0.6, stiffness 800). */
        const val MORPH_DAMPING = 0.6f
        const val MORPH_STIFFNESS = 800f
        const val MORPH_SETTLE = 0.01f
        const val MORPH_SETTLE_SPEED = 0.1f
        /** The scale-in from under the card: the expressive default spatial spring (0.8, 380), about 0.4 s. */
        const val APPEAR_DAMPING = 0.8f
        const val APPEAR_STIFFNESS = 380f
        /** The scale-in's overshoot (about 1.5% for this spring) is never more than this. */
        const val MAX_APPEAR = 1.03f
        /** The light's spring inside a shape, and how fast an impact's flash fades. */
        const val LIGHT_STIFFNESS = 120f
        const val LIGHT_DAMPING = 0.35f
        const val FLASH_TIME = 0.18f

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

        const val OUTLINE_POINTS = MorphOutlines.POINTS
        private val COS = DoubleArray(OUTLINE_POINTS) { cos(it * 2.0 * PI / OUTLINE_POINTS) }
        private val SIN = DoubleArray(OUTLINE_POINTS) { sin(it * 2.0 * PI / OUTLINE_POINTS) }
        private val calibrated = HashMap<String, Pair<Double, Double>>()

        /** How much of its landing speed the hard ground under the rubber gives back to a shape of [density]. */
        fun hardRestitution(density: Float, speed: Float): Float {
            if (speed <= 1e-3f) return HARD_E_MAX
            val e = HARD_E0 * (REFERENCE_DENSITY * HARD_V0 * HARD_V0 / (density * speed * speed)).pow(1f / 8f)
            return e.coerceIn(HARD_E_MIN, HARD_E_MAX)
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

        /** The angle [body] is drawn at, and presses the rubber at: its roll. */
        fun drawnRotation(body: Body, angle: Float = body.angle): Float = angle % 360f

        /**
         * The acceleration of a spring of [stiffness] and [dampingRatio] (as Compose's `spring()` takes them, unit
         * mass) at [offset] from its rest with [velocity].
         */
        fun springAcceleration(offset: Float, velocity: Float, stiffness: Float, dampingRatio: Float): Float =
            -stiffness * offset - 2f * dampingRatio * sqrt(stiffness) * velocity
    }
}
