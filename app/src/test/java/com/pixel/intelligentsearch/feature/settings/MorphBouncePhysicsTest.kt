package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.random.Random

/** The rules of the Material Morph animation, checked against the physics directly. */
class MorphBouncePhysicsTest {

    /** An area like the one under the cards on a settings page: 411 by 160 dp, 4.11 m by 1.6 m. */
    private val phone = morphSceneFor(411f * 3f, 160f * 3f, 3f)!!

    private fun world(seed: Int = 7) = MorphBouncePhysics(Random(seed)).apply {
        resize(phone.widthMetres, phone.heightMetres, phone.radiiMetres)
    }

    /** The same area with nothing dropping in by itself, for tests that study one shape. */
    private fun quietWorld(seed: Int = 7, width: Float = phone.widthMetres, height: Float = phone.heightMetres, radii: List<Float> = phone.radiiMetres) =
        MorphBouncePhysics(Random(seed), autoSpawn = false).apply { resize(width, height, radii) }

    private fun run(physics: MorphBouncePhysics, seconds: Float, onFrame: () -> Unit = {}, listener: MorphBouncePhysics.BounceListener? = null) {
        repeat((seconds * 120f).toInt()) {
            physics.advance(1f / 120f, listener)
            onFrame()
        }
    }

    /** A shape of [pounds] put straight into [physics] at [x], its lowest point just above the floor, falling at [vy]. */
    private fun drop(physics: MorphBouncePhysics, id: Int, radius: Float, pounds: Float, x: Float, vy: Float): MorphBouncePhysics.Body {
        val body = MorphBouncePhysics.Body(id, radius, pounds * MorphBouncePhysics.POUND).apply {
            this.x = x; y = physics.floor - radius - 1e-3f; this.vy = vy; appear = 1f; entered = true
            prevX = this.x; prevY = y; shapeIndex = 1
        }
        physics.bodies += body
        return body
    }

    @Test
    fun neverMoreThanFourShapesAndTheyKeepComingWhileThePageStaysOpen() {
        assertEquals(4, MorphBouncePhysics.MAX_SHAPES)
        for (seed in 1..8) {
            val physics = world(seed)
            var maxAlive = 0
            val bouncesByWindow = IntArray(6)
            var t = 0f
            run(physics, 60f, onFrame = {
                t += 1f / 120f
                maxAlive = maxOf(maxAlive, physics.bodies.size)
            }, listener = { _, _ -> bouncesByWindow[(t / 10f).toInt().coerceAtMost(5)]++ })
            assertTrue("seed $seed: max alive $maxAlive", maxAlive <= 4)
            for ((i, n) in bouncesByWindow.withIndex()) assertTrue("seed $seed: window $i had no bounces: ${bouncesByWindow.toList()}", n > 0)
        }
    }

    @Test
    fun dropsComeAtRandomFromAnywhereThrownAnyWayAndWeighFiftyToAHundredPounds() {
        val physics = world(3)
        val seen = HashSet<Int>()
        val dropTimes = ArrayList<Float>()
        val xs = ArrayList<Float>()
        val vxs = ArrayList<Float>()
        val pounds = ArrayList<Float>()
        val sizes = HashSet<Float>()
        var t = 0f
        run(physics, 180f, onFrame = {
            t += 1f / 120f
            for (b in physics.bodies) {
                if (seen.add(b.id)) {
                    dropTimes += t
                    xs += b.x / physics.width
                    vxs += b.vx
                    pounds += b.pounds
                    sizes += b.radius
                }
            }
        })
        assertTrue("only ${dropTimes.size} drops", dropTimes.size > 40)
        val gaps = dropTimes.zipWithNext { a, b -> b - a }
        assertTrue("no bursts: shortest gap ${gaps.min()}", gaps.count { it < 0.6f } >= 5)
        assertTrue("no long gaps: longest ${gaps.max()}", gaps.count { it > 2f } >= 3)
        assertTrue("xs ${xs.min()}..${xs.max()}", xs.min() < 0.2f && xs.max() > 0.8f)
        assertTrue("thrown left ${vxs.count { it < -0.2f }}, right ${vxs.count { it > 0.2f }}", vxs.count { it < -0.2f } > 5 && vxs.count { it > 0.2f } > 5)
        // Every weight its own, at random across the whole range, whatever the size.
        for (p in pounds) assertTrue("$p lb", p in 50f..100f)
        assertTrue("weights ${pounds.min()}..${pounds.max()}", pounds.min() < 60f && pounds.max() > 90f)
        assertTrue("weights repeat: ${pounds.size} drops, ${pounds.toSet().size} weights", pounds.toSet().size == pounds.size)
        assertEquals(4, sizes.size)
    }

    @Test
    fun inAreasOfEverySizeEveryShapeBouncesTwiceToFourTimesAndLeavesInTime() {
        // Width x height in dp, and pixels per dp: the smallest area, a phone's, a tall phone's, a wide and short one,
        // a tablet's in landscape and a tall tablet's.
        val areas = listOf(
            Triple(320, 40, 3f), Triple(360, 160, 4f), Triple(411, 600, 3f),
            Triple(1280, 160, 2f), Triple(1280, 500, 2f), Triple(840, 900, 3f)
        )
        for ((widthDp, heightDp, density) in areas) {
            val scene = morphSceneFor(widthDp * density, heightDp * density, density)!!
            for (seed in 1..6) {
                val where = "${widthDp}x$heightDp dp, seed $seed"
                val physics = MorphBouncePhysics(Random(seed)).apply { resize(scene.widthMetres, scene.heightMetres, scene.radiiMetres) }
                val alive = mutableMapOf<Int, MorphBouncePhysics.Body>()
                var left = 0
                run(physics, 20f, onFrame = {
                    for (b in physics.bodies) {
                        assertTrue("$where: shape ${b.id} still on screen after ${b.age}s", b.age < MorphBouncePhysics.MAX_LIFETIME)
                        if (!physics.shapeOffScreen(b)) assertTrue("$where: shape ${b.id} below the area", b.y + b.radius <= physics.height + 1e-4f)
                        assertTrue("$where: shape ${b.id} touched down ${b.contacts} times", b.contacts <= MorphBouncePhysics.MAX_LANDINGS)
                        alive[b.id] = b
                    }
                    val ids = physics.bodies.map { it.id }.toSet()
                    for (id in alive.keys - ids) {
                        val b = alive.remove(id)!!
                        assertTrue("$where: shape $id left after ${b.bounces} bounces", b.bounces >= 2)
                        left++
                    }
                })
                assertTrue("$where: only $left shapes came and went", left >= 3)
            }
        }
    }

    @Test
    fun theFirstTwoLandingsAreOnScreen() {
        var checked = 0
        for (seed in 1..10) {
            val physics = world(seed)
            val alive = HashMap<Int, MorphBouncePhysics.Body>()
            run(physics, 30f, onFrame = {
                for (b in physics.bodies) alive[b.id] = b
                for (id in alive.keys - physics.bodies.map { it.id }.toSet()) {
                    val b = alive.remove(id)!!
                    assertTrue("seed $seed: shape $id touched down ${b.landCount} times", b.landCount >= 2)
                    for (x in listOf(b.landX0, b.landX1)) assertTrue("seed $seed: shape $id landed at $x", x >= b.radius && x <= physics.width - b.radius)
                    checked++
                }
            })
        }
        assertTrue("only $checked shapes", checked > 50)
    }

    @Test
    fun shapesNeverRiseAboveTheBottomCard() {
        for (seed in 1..12) {
            val physics = world(seed)
            val entered = mutableSetOf<Int>()
            run(physics, 30f, onFrame = {
                for (b in physics.bodies) {
                    // A shape enters from just under the card (center at -radius); once fully in, its top stays in.
                    if (b.y - b.radius >= 0f) entered += b.id
                    if (b.id in entered) assertTrue("seed $seed: shape ${b.id} rose to ${b.y - b.radius}", b.y - b.radius >= -1e-3f)
                }
            })
        }
    }

    @Test
    fun noShapeSitsOnTheFloorAndBouncesAreRealFlightsApart() {
        for (seed in 1..12) {
            val physics = world(seed)
            val contactTime = mutableMapOf<Int, Float>()
            val lastBounceAt = mutableMapOf<Int, Float>()
            var t = 0f
            run(physics, 30f, onFrame = {
                t += 1f / 120f
                for (b in physics.bodies) {
                    contactTime[b.id] = if (b.inContact) (contactTime[b.id] ?: 0f) + 1f / 120f else 0f
                    assertTrue("seed $seed: shape ${b.id} on the floor for ${contactTime[b.id]}s", contactTime[b.id]!! <= MorphBouncePhysics.STUCK_TIME)
                }
            }, listener = { b, _ ->
                val previous = lastBounceAt[b.id]
                if (previous != null) assertTrue("seed $seed: shape ${b.id} bounced again after ${t - previous}s", t - previous >= MorphBouncePhysics.MIN_FLIGHT)
                lastBounceAt[b.id] = t
            })
        }
    }

    @Test
    fun theShapesFallUnderRealGravity() {
        val physics = quietWorld(1, width = 4f, height = 20f, radii = listOf(0.1f))
        val body = MorphBouncePhysics.Body(0, 0.1f, 75f * MorphBouncePhysics.POUND).apply { x = 2f; y = 1f; prevX = x; prevY = y }
        physics.bodies += body
        run(physics, 0.5f)
        // Half a second of free fall: 4.9 m/s, 1.23 m; heavy as it is, the air takes well under 1% of that.
        assertEquals(4.905f, body.vy, 0.05f)
        assertEquals(2.226f, body.y, 0.02f)
    }

    @Test
    fun aHeavierShapeSinksDeeperIntoTheRubberAndComesBackSlower() {
        fun landing(pounds: Float): Pair<Float, Float> {
            val physics = quietWorld(1)
            val body = drop(physics, 0, 0.23f, pounds, 2f, 5f)
            var deepest = 0f
            var rebound = 0f
            var t = 0f
            while (t < 1.5f && rebound == 0f) {
                physics.advance(1f / 120f)
                t += 1f / 120f
                deepest = maxOf(deepest, body.penetration)
                if (deepest > 0f && !body.inContact && body.vy < 0f) rebound = -body.vy
            }
            return deepest to rebound
        }
        val (lightSink, lightRebound) = landing(50f)
        val (heavySink, heavyRebound) = landing(100f)
        assertTrue("50 lb sinks $lightSink, 100 lb $heavySink", heavySink > lightSink * 1.15f)
        assertTrue("rebounds 50 lb $lightRebound, 100 lb $heavyRebound", lightRebound > 0f && heavyRebound > 0f)
        // Nothing gives back more than it was given.
        assertTrue(lightRebound < 5f && heavyRebound < 5f)
    }

    @Test
    fun warmLoadedRubberKeepsTheShapesDentAndHealsItAfterward() {
        val physics = quietWorld(2)
        val body = drop(physics, 0, phone.radiiMetres.last(), 100f, 2f, 6f)
        var kept = 0.0
        var t = 0f
        while (t < 6f) {
            physics.advance(1f / 120f)
            t += 1f / 120f
            kept = maxOf(kept, physics.sheet.rest.max())
            // Once it has been thrown back up, it is taken away, so nothing presses the rubber while it heals.
            if (body.bounces >= 1 && !body.inContact && body.vy < 0f) physics.bodies.remove(body)
        }
        assertTrue("the rubber kept a dent of only $kept m", kept > 0.01)
        assertTrue("still dented ${physics.sheet.rest.max()} m after 6 s", physics.sheet.rest.max() < kept * 0.05)
    }

    @Test
    fun aReleasedDentOnlyEverClosesEvenWhenANewShapeLandsOnIt() {
        var checked = 0
        for (seed in 1..10) {
            val physics = world(seed)
            val last = IdentityHashMap<MorphBouncePhysics.Imprint, Pair<Float, Float>>()
            run(physics, 40f, onFrame = {
                for (imprint in physics.imprints) {
                    if (!imprint.active || imprint.holding) { last.remove(imprint); continue }
                    val before = last[imprint]
                    // The same silhouette (not one reused for a new landing): its hollow never grows back.
                    if (before != null && imprint.sincePressed >= before.second) {
                        assertTrue("seed $seed: a dent grew back from ${before.first} to ${imprint.shrink}", imprint.shrink <= before.first + 1e-6f)
                        checked++
                    }
                    last[imprint] = imprint.shrink to imprint.sincePressed
                }
            })
        }
        assertTrue("only $checked checks", checked > 1000)
    }

    @Test
    fun anOldDentUnderANewLandingHealsOnOut() {
        val physics = quietWorld(3)
        val first = drop(physics, 0, 0.23f, 100f, 2f, 6f)
        var t = 0f
        while (t < 3f && first.bounces < 1 || first.inContact) { physics.advance(1f / 120f); t += 1f / 120f }
        physics.bodies.remove(first)
        physics.advance(1f / 120f)
        val old = physics.imprints.first { it.active && !it.holding }
        val shrinkBefore = old.shrink
        drop(physics, 1, 0.23f, 100f, 2f, 6f)
        var healed = false
        t = 0f
        while (t < 3f && !healed) {
            physics.advance(1f / 120f)
            t += 1f / 120f
            if (!old.active || old.heal >= 1f) healed = true
            else assertTrue("the old dent grew from $shrinkBefore to ${old.shrink}", !old.holding && old.shrink <= shrinkBefore + 1e-6f)
        }
        assertTrue("the old dent never finished healing", healed)
    }

    @Test
    fun theShadowIsSharpDarkAndTightOnTheFloorAndWideSoftAndFaintHigherUp() {
        val r = 0.1f
        var previous: MorphBouncePhysics.Shadow? = null
        for (i in 0..60) {
            val h = i * 0.05f
            val s = MorphBouncePhysics.shadowFor(h, r, 0f, MorphBouncePhysics.Shadow())
            val p = previous
            if (p != null) {
                assertTrue("width at $h", s.width > p.width)
                assertTrue("blur at $h", s.blur > p.blur)
                assertTrue("softness at $h", s.softness > p.softness)
                assertTrue("alpha at $h", s.alpha < p.alpha)
                assertTrue("core at $h", s.coreWidth <= p.coreWidth)
                assertTrue("ao at $h", s.aoAlpha <= p.aoAlpha)
                assertTrue("offset at $h", s.offsetX > p.offsetX)
            }
            previous = s
        }
        val onTheFloor = MorphBouncePhysics.shadowFor(0f, r, 0f, MorphBouncePhysics.Shadow())
        assertEquals(0f, onTheFloor.softness, 0f)
        assertEquals(MorphBouncePhysics.AO_ALPHA, onTheFloor.aoAlpha, 0f)
        assertEquals(0f, onTheFloor.offsetX, 0f)
        assertEquals(2f * r * MorphBouncePhysics.FOOTPRINT_SHARE, onTheFloor.coreWidth, 1e-5f)
        assertTrue(onTheFloor.blur < 0.15f * r)
        val shallow = MorphBouncePhysics.shadowFor(0f, r, 0.02f, MorphBouncePhysics.Shadow())
        val deep = MorphBouncePhysics.shadowFor(0f, r, 0.1f, MorphBouncePhysics.Shadow())
        assertTrue(shallow.coreWidth < deep.coreWidth)
        assertEquals(2f * r, deep.coreWidth, 1e-4f)
        assertTrue(deep.aoAlpha > 0.4f)
    }

    @Test
    fun aSeedAlwaysGivesTheSameRun() {
        val a = world(21)
        val b = world(21)
        val c = world(22)
        var differs = false
        run(a, 20f, onFrame = {
            b.advance(1f / 120f)
            c.advance(1f / 120f)
            assertEquals(a.bodies.size, b.bodies.size)
            for (i in a.bodies.indices) assertSame(a.bodies[i], b.bodies[i])
            if (a.bodies.size != c.bodies.size || a.bodies.indices.any { a.bodies[it].x != c.bodies[it].x }) differs = true
        })
        assertTrue("seeds 21 and 22 gave the same run", differs)
    }

    @Test
    fun theSameRunAt60And120Hz() {
        for (seed in 1..4) {
            val at60 = world(seed)
            val at120 = world(seed)
            var bounces60 = 0
            var bounces120 = 0
            repeat(60 * 20) {
                at60.advance(1f / 60f) { _, _ -> bounces60++ }
                at120.advance(1f / 120f) { _, _ -> bounces120++ }
                at120.advance(1f / 120f) { _, _ -> bounces120++ }
                assertEquals("seed $seed", at60.bodies.size, at120.bodies.size)
                for (i in at60.bodies.indices) assertSame(at60.bodies[i], at120.bodies[i])
                for (i in at60.imprints.indices) assertEquals(at60.imprints[i].heal, at120.imprints[i].heal, 0f)
            }
            assertEquals("seed $seed", bounces60, bounces120)
            assertTrue(bounces60 > 10)
        }
    }

    /** The same body state, to the bit. */
    private fun assertSame(a: MorphBouncePhysics.Body, b: MorphBouncePhysics.Body) {
        assertEquals(a.id, b.id)
        assertEquals(a.x, b.x, 0f)
        assertEquals(a.y, b.y, 0f)
        assertEquals(a.vx, b.vx, 0f)
        assertEquals(a.vy, b.vy, 0f)
        assertEquals(a.angle, b.angle, 0f)
        assertEquals(a.bounces, b.bounces)
        assertEquals(a.mass, b.mass, 0f)
        assertEquals(a.morphProgress, b.morphProgress, 0f)
    }

    @Test
    fun shapesKnockIntoEachOtherInsteadOfPassingThrough() {
        val physics = quietWorld(1, width = 4f, radii = listOf(0.1f))
        val a = drop(physics, 0, 0.1f, 75f, 1.5f, 0f)
        val b = drop(physics, 1, 0.1f, 75f, 2.5f, 0f)
        a.y = 0.6f; a.vx = 2f; a.vy = 0f
        b.y = 0.6f; b.vx = -2f; b.vy = 0f
        val momentumBefore = a.mass * a.vx + b.mass * b.vx
        var minGap = Float.MAX_VALUE
        var collided = false
        var momentumAfter = 0f
        var t = 0f
        while (t < 0.5f && a in physics.bodies && b in physics.bodies) {
            physics.advance(1f / 120f)
            minGap = minOf(minGap, kotlin.math.hypot(b.x - a.x, b.y - a.y) - a.radius - b.radius)
            if (!collided && a.vx < 0f && b.vx > 0f) { collided = true; momentumAfter = a.mass * a.vx + b.mass * b.vx }
            t += 1f / 120f
        }
        assertTrue("they bounced apart", collided)
        assertTrue("overlap $minGap", minGap > -0.01f)
        assertTrue("momentum before $momentumBefore after $momentumAfter", abs(momentumAfter - momentumBefore) < 0.05f * a.mass)
    }

    @Test
    fun aGlancingKnockSetsShapesSpinning() {
        val physics = quietWorld(1, width = 4f, radii = listOf(0.1f))
        val a = drop(physics, 0, 0.1f, 75f, 1.5f, 0f)
        val b = drop(physics, 1, 0.1f, 75f, 2.5f, 0f)
        a.y = 0.6f; a.vx = 2f; a.vy = 0f
        b.y = 0.72f; b.vx = -2f; b.vy = 0f
        run(physics, 0.4f)
        assertTrue("spins ${a.spin} ${b.spin}", abs(a.spin) > 0.5f && abs(b.spin) > 0.5f)
    }

    @Test
    fun theShapeChangesAsItLeavesTheRubberAfterEveryBounce() {
        for (seed in 1..6) {
            val physics = world(seed)
            val lastShape = HashMap<Int, Int>()
            val wasInContact = HashMap<Int, Boolean>()
            var changes = 0
            run(physics, 30f, onFrame = {
                for (b in physics.bodies) {
                    val before = lastShape[b.id]
                    if (before != null && b.shapeIndex != before) {
                        assertEquals("seed $seed: shape ${b.id} skipped a shape", before + 1, b.shapeIndex)
                        // Never while the rubber holds it: it changes as it leaves.
                        assertTrue("seed $seed: shape ${b.id} changed in the rubber", !b.inContact)
                        assertTrue("seed $seed: morph ${b.morphProgress}", b.morphProgress < 0.5f)
                        changes++
                    }
                    lastShape[b.id] = b.shapeIndex
                    wasInContact[b.id] = b.inContact
                }
            })
            assertTrue("seed $seed: only $changes changes", changes > 10)
        }
    }

    @Test
    fun theLightInsideSettlesInAFallAndFlashesOnALanding() {
        val physics = quietWorld(4, height = 3f)
        val body = MorphBouncePhysics.Body(0, 0.23f, 80f * MorphBouncePhysics.POUND).apply { x = 2f; y = 0.3f; prevX = x; prevY = y; appear = 1f }
        physics.bodies += body
        var fallingLight = 1f
        var flash = 0f
        var t = 0f
        while (t < 1.2f && body in physics.bodies) {
            physics.advance(1f / 120f)
            t += 1f / 120f
            if (t in 0.45f..0.47f && !body.inContact) fallingLight = kotlin.math.hypot(body.lightX, body.lightY)
            flash = maxOf(flash, body.flash)
        }
        // Weightless in free fall, the light drifts to the middle; a landing's push flashes it.
        assertTrue("falling, the light is $fallingLight radii off centre", fallingLight < 0.12f)
        assertTrue("peak flash $flash", flash > 0.3f)
    }

    @Test
    fun planningAFewMomentsAFrameStillBringsDropsOnASlowPhone() {
        // The least a frame can give: one slice of planning. A missed window gives the next plan more time.
        val physics = MorphBouncePhysics(Random(5), planBudgetNanos = 1L).apply { resize(phone.widthMetres, phone.heightMetres, phone.radiiMetres) }
        val seen = HashSet<Int>()
        run(physics, 60f, onFrame = { for (b in physics.bodies) seen += b.id })
        assertTrue("only ${seen.size} drops", seen.size >= 5)
    }

    @Test
    fun anAreaResizingEveryFrameKeepsItsRubberAndDents() {
        val physics = world(9)
        while (physics.imprints.none { it.active && !it.holding }) physics.advance(1f / 120f)
        val dents = physics.imprints.count { it.active }
        val floorBefore = physics.floor
        // A card opening above: the area a few millimetres shorter every frame.
        repeat(20) { i ->
            physics.resize(phone.widthMetres, phone.heightMetres - 0.005f * (i + 1), phone.radiiMetres)
            physics.advance(1f / 120f)
        }
        assertTrue("the dents went: $dents before, ${physics.imprints.count { it.active }} after", physics.imprints.count { it.active } >= 1)
        assertEquals(floorBefore - 0.1f, physics.floor, 1e-3f)
    }

    @Test
    fun afterTheAreaChangesNoShapeIsLeftSittingOnTheFloor() {
        for (seed in 1..8) {
            val physics = world(seed)
            run(physics, 4f)
            val shorter = morphSceneFor(411f * 3f, 100f * 3f, 3f)!!
            physics.resize(shorter.widthMetres, shorter.heightMetres, shorter.radiiMetres)
            val there = physics.bodies.toList()
            run(physics, MorphBouncePhysics.MAX_LIFETIME + 1f, onFrame = {
                for (b in physics.bodies) assertTrue("seed $seed: shape ${b.id} on the floor for ${b.contactTime}s", b.contactTime <= MorphBouncePhysics.STUCK_TIME)
            })
            for (b in there) assertTrue("seed $seed: shape ${b.id} still there", b !in physics.bodies)
        }
    }

    @Test
    fun whenTheAreaShrinksEveryShapeIsKeptInsideIt() {
        for (seed in 1..8) {
            val physics = world(seed)
            run(physics, 3f)
            if (physics.bodies.isEmpty()) run(physics, 2f)
            assertTrue("seed $seed: no shapes in the air", physics.bodies.isNotEmpty())
            // As when the phone turns to landscape or the text grows while the page is open.
            val shorter = morphSceneFor(411f * 3f, 100f * 3f, 3f)!!
            physics.resize(shorter.widthMetres, shorter.heightMetres, shorter.radiiMetres)
            for (b in physics.bodies) {
                assertTrue("seed $seed: shape ${b.id} too big for the area", b.radius <= physics.height * MorphBouncePhysics.MAX_RADIUS_SHARE)
                assertTrue("seed $seed: shape ${b.id} at ${b.y} under a ${physics.floor} m floor", b.y + b.radius <= physics.floor + 1e-4f)
            }
            val bouncesBefore = physics.bodies.associateWith { it.bounces }
            val seen = physics.bodies.toMutableSet()
            var felt = 0
            run(physics, 10f, onFrame = {
                seen += physics.bodies
                for (b in physics.bodies) assertTrue("seed $seed: shape ${b.id} below the area", b.y + b.radius <= physics.height + 1e-4f)
            }, listener = { _, _ -> felt++ })
            assertEquals("seed $seed: bounces felt", seen.sumOf { it.bounces - (bouncesBefore[it] ?: 0) }, felt)
        }
    }
}
