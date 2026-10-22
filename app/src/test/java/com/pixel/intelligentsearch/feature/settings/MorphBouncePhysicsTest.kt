package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** The rules of the Material Morph animation, checked against the physics directly. */
class MorphBouncePhysicsTest {

    /** An area like the one under the cards on Search Behavior: 4.1 m wide (411 dp), 1.6 m tall (160 dp). */
    private fun world(seed: Int = 7) = MorphBouncePhysics(Random(seed)).apply {
        resize(4.11f, 1.6f, listOf(0.085f, 0.10f, 0.115f, 0.13f))
    }

    /** The same area with nothing dropping in by itself, for tests that study one shape. */
    private fun quietWorld(seed: Int = 7, radii: List<Float> = listOf(0.085f, 0.10f, 0.115f, 0.13f)) =
        MorphBouncePhysics(Random(seed), autoSpawn = false).apply { resize(4.11f, 1.6f, radii) }

    private fun run(physics: MorphBouncePhysics, seconds: Float, onFrame: () -> Unit = {}, listener: MorphBouncePhysics.BounceListener? = null) {
        val frames = (seconds * 120f).toInt()
        repeat(frames) {
            physics.advance(1f / 120f, listener)
            onFrame()
        }
    }

    /** Steps [physics] until [body] has gone, or [seconds] have passed, calling [onFrame] after every frame. */
    private fun follow(physics: MorphBouncePhysics, body: MorphBouncePhysics.Body, seconds: Float = 12f, onFrame: () -> Unit = {}) {
        var t = 0f
        while (body in physics.bodies && t < seconds) {
            physics.advance(1f / 120f)
            onFrame()
            t += 1f / 120f
        }
    }

    @Test
    fun neverMoreThanThreeShapesAndTheyKeepComingWhileThePageStaysOpen() {
        for (seed in 1..20) {
            val physics = world(seed)
            var maxAlive = 0
            val bouncesByWindow = IntArray(6)
            var t = 0f
            run(physics, 60f, onFrame = {
                t += 1f / 120f
                maxAlive = maxOf(maxAlive, physics.bodies.size)
            }, listener = { _, _ -> bouncesByWindow[(t / 10f).toInt().coerceAtMost(5)]++ })
            assertTrue("seed $seed: max alive $maxAlive", maxAlive <= 3)
            assertEquals(3, MorphBouncePhysics.MAX_SHAPES)
            // Bounces keep happening in every 10-second window.
            for ((i, n) in bouncesByWindow.withIndex()) assertTrue("seed $seed: window $i had no bounces: ${bouncesByWindow.toList()}", n > 0)
        }
    }

    @Test
    fun dropsComeAtRandomInBurstsOfOneToThreeFromAnywhereThrownAnyWay() {
        val physics = world(3)
        val seen = HashSet<Int>()
        val dropTimes = ArrayList<Float>()
        val xs = ArrayList<Float>()
        val vxs = ArrayList<Float>()
        val spins = ArrayList<Float>()
        val sizes = HashSet<Float>()
        var t = 0f
        run(physics, 240f, onFrame = {
            t += 1f / 120f
            for (b in physics.bodies) {
                if (seen.add(b.id)) {
                    dropTimes += t
                    xs += b.x / physics.width
                    vxs += b.vx
                    spins += b.spin
                    sizes += b.radius
                }
            }
        })
        assertTrue("only ${dropTimes.size} drops", dropTimes.size > 60)
        val gaps = dropTimes.zipWithNext { a, b -> b - a }
        // Bursts (drops close together) and long quiet spells, unevenly spaced.
        assertTrue("no bursts: shortest gap ${gaps.min()}", gaps.count { it < 0.6f } >= 10)
        assertTrue("no long gaps: longest ${gaps.max()}", gaps.count { it > 2f } >= 5)
        val mean = gaps.average()
        val spread = kotlin.math.sqrt(gaps.sumOf { (it - mean) * (it - mean) } / gaps.size)
        assertTrue("gaps too regular: mean $mean, spread $spread", spread > mean * 0.5)
        // In from anywhere across the width, thrown either way, spinning either way, of every size.
        assertTrue("xs ${xs.min()}..${xs.max()}", xs.min() < 0.15f && xs.max() > 0.85f)
        assertTrue("thrown left ${vxs.count { it < -0.2f }}, right ${vxs.count { it > 0.2f }}", vxs.count { it < -0.2f } > 10 && vxs.count { it > 0.2f } > 10)
        assertTrue("spins", spins.count { it < 0f } > 10 && spins.count { it > 0f } > 10)
        assertEquals(4, sizes.size)
    }

    @Test
    fun theFirstLandingSinksIntoTheFloorLeavingASilhouetteAndTheFloorSpringsItBackUp() {
        for (seed in 1..30) {
            val physics = quietWorld(seed)
            val body = physics.spawn()!!
            var deepest = 0f
            var sprungUp = false
            var imprint: MorphBouncePhysics.Imprint? = null
            var surfaceOvershoot = 0f
            var healed = false
            var t = 0f
            while (t < 8f) {
                physics.advance(1f / 120f)
                t += 1f / 120f
                if (body.bounces == 1) {
                    deepest = maxOf(deepest, body.penetration)
                    if (body.penetration > 0f) {
                        // Partly through the floor line, never through the bottom of the area.
                        assertTrue("seed $seed: centre at ${body.y}, floor at ${physics.floor}", body.y + body.radius > physics.floor)
                        assertTrue("seed $seed: below the area", body.y + body.radius <= physics.height + 1e-4f)
                        imprint = imprint ?: physics.imprints.first { it.active && it.holding }
                    }
                    if (deepest > 0f && body.penetration == 0f && body.vy < 0f) sprungUp = true
                }
                val i = imprint
                if (i != null && !i.holding) {
                    surfaceOvershoot = minOf(surfaceOvershoot, i.surface)
                    if (!i.active) healed = true
                }
            }
            val diameter = 2f * body.radius
            assertTrue("seed $seed: sank only ${deepest / diameter} of its height", deepest >= diameter * MorphBouncePhysics.SINK_MIN * 0.8f)
            assertTrue("seed $seed: sank ${deepest / diameter} of its height", deepest <= diameter * MorphBouncePhysics.SINK_MAX * 1.25f)
            assertTrue("seed $seed: never sprung back up", sprungUp)
            val silhouette = imprint!!
            // The silhouette is the shape at its deepest (found step by step, so a touch deeper than any frame shows).
            assertTrue("seed $seed: silhouette ${silhouette.depth} deep, shape $deepest", silhouette.depth >= deepest - 1e-5f && silhouette.depth < deepest + 0.01f)
            // Released, the floor's surface springs back past its rest line before it settles.
            assertTrue("seed $seed: surface overshoot $surfaceOvershoot", surfaceOvershoot < -0.004f)
            assertTrue("seed $seed: the silhouette never healed", healed)
        }
    }

    @Test
    fun theSilhouetteLingersThenHealsAfterItsShapeLeaves() {
        val physics = quietWorld(4)
        val body = physics.spawn()!!
        var released = -1f
        var strengthAtHalfSecond = -1f
        var t = 0f
        while (t < 6f) {
            physics.advance(1f / 120f)
            t += 1f / 120f
            val imprint = physics.imprints.firstOrNull { it.active } ?: continue
            if (!imprint.holding && released < 0f) released = t
            if (released >= 0f && strengthAtHalfSecond < 0f && t - released >= 0.3f) strengthAtHalfSecond = imprint.strength
        }
        assertTrue(body.bounces >= 1)
        assertTrue("released at $released", released > 0f)
        assertTrue("strength 0.3 s after ${strengthAtHalfSecond}", strengthAtHalfSecond > 0.95f)
        assertTrue("still there: ${physics.imprints.filter { it.active }.map { it.strength }}", physics.imprints.none { it.active })
    }

    @Test
    fun theSecondLandingIsAHardBounceThatSendsTheShapeOffTheScreen() {
        for (seed in 1..40) {
            val physics = quietWorld(seed)
            val body = physics.spawn()!!
            var lastX = body.x
            var vyAfterHard = 0f
            var hardContactSeen = false
            follow(physics, body) {
                if (body.bounces >= 2) {
                    // The floor is real now: nothing sinks into it.
                    if (body.penetration > 0f) hardContactSeen = true
                    if (vyAfterHard == 0f) vyAfterHard = body.vy
                }
                lastX = body.x
            }
            assertTrue("seed $seed: still on screen", body !in physics.bodies)
            assertEquals("seed $seed: bounces before leaving", 2, body.bounces)
            assertTrue("seed $seed: sank into the hard floor", !hardContactSeen)
            assertTrue("seed $seed: left the hard floor at $vyAfterHard", vyAfterHard < -1f)
            assertTrue("seed $seed: left at $lastX", lastX - body.radius > physics.width || lastX + body.radius < 0f)
        }
    }

    @Test
    fun aShapeIsDrawnWholeUntilItIsFullyOffTheScreenAndOnlyThenRemoved() {
        var left = 0
        for (seed in 1..40) {
            val physics = world(seed)
            val lastSeen = HashMap<Int, MorphBouncePhysics.Body>()
            run(physics, 30f, onFrame = {
                for (b in physics.bodies) {
                    lastSeen[b.id] = b
                    // Crossing the edge, it is as big as ever: nothing shrinks or fades.
                    val atTheEdge = b.x + b.radius > physics.width || b.x - b.radius < 0f
                    if (atTheEdge && b.exiting) assertEquals("seed $seed: shape ${b.id} scale at the edge", 1f, morphPoseOf(b).scale, 0.03f)
                }
                val alive = physics.bodies.map { it.id }.toSet()
                for (id in lastSeen.keys - alive) {
                    val b = lastSeen.remove(id)!!
                    left++
                    // Removed only once nothing of it is left on screen.
                    assertTrue("seed $seed: shape $id removed at x ${b.x} (radius ${b.radius}) in a ${physics.width} m area", physics.shapeOffScreen(b) || b.y - b.radius > physics.height)
                    assertTrue("seed $seed: shape $id removed after ${b.bounces} bounces", b.bounces >= 2)
                }
            })
        }
        assertTrue("only $left shapes left", left > 200)
    }

    @Test
    fun inAreasOfEveryShapeEveryShapeLeavesInTimeHavingMadeBothLandings() {
        // Width x height in dp, and pixels per dp: the smallest area, a phone's, a tall phone's, a wide and short one,
        // a tablet's in landscape and a tall tablet's. Wide areas take more than one flight to cross; in tall ones
        // every flight is long.
        val areas = listOf(
            Triple(320, 40, 3f), Triple(360, 160, 4f), Triple(411, 600, 3f),
            Triple(1280, 160, 2f), Triple(1280, 500, 2f), Triple(840, 900, 3f)
        )
        for ((widthDp, heightDp, density) in areas) {
            val scene = morphSceneFor(widthDp * density, heightDp * density, density)!!
            for (seed in 1..200) {
                val where = "${widthDp}x$heightDp dp, seed $seed"
                val physics = MorphBouncePhysics(Random(seed)).apply { resize(scene.widthMetres, scene.heightMetres, scene.radiiMetres) }
                val alive = mutableMapOf<Int, MorphBouncePhysics.Body>()
                var left = 0
                run(physics, 20f, onFrame = {
                    for (b in physics.bodies) {
                        assertTrue("$where: shape ${b.id} still on screen after ${b.age}s", b.age < MorphBouncePhysics.MAX_LIFETIME)
                        if (!physics.shapeOffScreen(b)) assertTrue("$where: shape ${b.id} at ${b.y + b.radius} under a ${physics.height} m area", b.y + b.radius <= physics.height + 1e-4f)
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
    fun withoutKnocksEveryShapeMakesBothLandingsOnScreen() {
        for (seed in 1..60) {
            val physics = quietWorld(seed)
            repeat(4) {
                val body = physics.spawn()!!
                val landedAt = ArrayList<Float>()
                var bounces = 0
                follow(physics, body) {
                    if (body.bounces != bounces) {
                        bounces = body.bounces
                        landedAt += body.x
                    }
                }
                assertTrue("seed $seed: ${body.bounces} bounces", body.bounces == 2)
                for (x in landedAt) assertTrue("seed $seed: landed at $x", x - body.radius >= -1e-3f && x + body.radius <= physics.width + 1e-3f)
            }
        }
    }

    @Test
    fun shapesNeverRiseAboveTheBottomCard() {
        for (seed in 1..40) {
            val physics = world(seed)
            val entered = mutableSetOf<Int>()
            run(physics, 30f, onFrame = {
                for (b in physics.bodies) {
                    // A shape enters from just under the card (centre at -radius); once fully in, its top stays in.
                    if (b.y - b.radius >= 0f) entered += b.id
                    if (b.id in entered) assertTrue("seed $seed: shape ${b.id} rose to ${b.y - b.radius}", b.y - b.radius >= -1e-3f)
                }
            })
        }
    }

    @Test
    fun noShapeLingersOnTheFloorAndBouncesAreRealFlightsApart() {
        for (seed in 1..40) {
            val physics = world(seed)
            val contactTime = mutableMapOf<Int, Float>()
            val lastBounceAt = mutableMapOf<Int, Float>()
            var t = 0f
            run(physics, 30f, onFrame = {
                t += 1f / 120f
                for (b in physics.bodies) {
                    contactTime[b.id] = if (b.penetration > 0f) (contactTime[b.id] ?: 0f) + 1f / 120f else 0f
                    assertTrue("seed $seed: shape ${b.id} in the floor for ${contactTime[b.id]}s", contactTime[b.id]!! <= MorphBouncePhysics.MAX_MEMBRANE_TIME + 0.02f)
                }
            }, listener = { b, _ ->
                val previous = lastBounceAt[b.id]
                if (previous != null) assertTrue("seed $seed: shape ${b.id} bounced again after ${t - previous}s", t - previous > 0.35f)
                lastBounceAt[b.id] = t
            })
        }
    }

    @Test
    fun theShapesAreHeavyAndFallUnderRealGravity() {
        // Mass follows area.
        assertEquals(4f, MorphBouncePhysics.massFor(0.2f) / MorphBouncePhysics.massFor(0.1f), 1e-4f)
        assertTrue("a 20 cm shape weighs ${MorphBouncePhysics.massFor(0.1f)} kg", MorphBouncePhysics.massFor(0.1f) > 2f)
        val physics = MorphBouncePhysics(Random(1), autoSpawn = false).apply { resize(4f, 20f, listOf(0.1f)) }
        val body = MorphBouncePhysics.Body(0, 0.1f, MorphBouncePhysics.massFor(0.1f)).apply { x = 2f; y = 1f }
        repeat(120) { physics.step(body, MorphBouncePhysics.STEP, null) }
        // Half a second of free fall: 4.9 m/s, 1.23 m; dense as it is, the air takes under 1% of that.
        assertEquals(4.905f, body.vy, 0.05f)
        assertEquals(2.226f, body.y, 0.02f)
    }

    @Test
    fun theMembraneGivesBackAboutTwoThirdsOfTheLandingSpeed() {
        // A tall area, so the rebound is neither raised to the lowest bounce nor capped at the top.
        val physics = MorphBouncePhysics(Random(1), autoSpawn = false).apply { resize(4f, 6f, listOf(0.1f)) }
        val body = MorphBouncePhysics.Body(0, 0.1f, MorphBouncePhysics.massFor(0.1f)).apply {
            x = 2f; y = physics.floor - radius - 1e-4f; vy = 12f; sinkShare = 0.5f
        }
        var t = 0f
        while ((body.vy > 0f || body.penetration > 0f) && t < 1f) { physics.step(body, MorphBouncePhysics.STEP, null); t += MorphBouncePhysics.STEP }
        assertEquals(1, body.bounces)
        val ratio = -body.vy / 12f
        assertTrue("rebound ratio $ratio", ratio in 0.55f..0.68f)
    }

    @Test
    fun offTheHardFloorAShapeLeavesRollingSpinningTheWayItFlies() {
        for (seed in 1..20) {
            val physics = quietWorld(seed)
            val body = physics.spawn()!!
            var checked = false
            follow(physics, body) {
                if (body.bounces == 2 && !checked) {
                    checked = true
                    assertTrue("seed $seed: vx ${body.vx}, spin ${body.spin}", body.vx * body.spin > 0f)
                    // The contact point's speed is all but gone: it rolled off the floor.
                    assertTrue("seed $seed: slipping at ${body.vx - body.spin * body.radius}", abs(body.vx - body.spin * body.radius) < abs(body.vx) * 0.35f)
                }
            }
            assertTrue(checked)
        }
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
        // As wide as the shape's footprint on the floor, the penumbra just a rim.
        assertEquals(2f * r * MorphBouncePhysics.FOOTPRINT_SHARE, onTheFloor.coreWidth, 1e-5f)
        assertTrue(onTheFloor.blur < 0.15f * r)
        // Sunk into the floor, it is only as wide as the shape is where it crosses the floor line.
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
        for (seed in 1..10) {
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
                for (i in at60.imprints.indices) assertEquals(at60.imprints[i].surface, at120.imprints[i].surface, 0f)
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
        assertEquals(a.morphProgress, b.morphProgress, 0f)
    }

    @Test
    fun shapesKnockIntoEachOtherInsteadOfPassingThrough() {
        val physics = MorphBouncePhysics(Random(1), autoSpawn = false).apply { resize(4f, 1.6f, listOf(0.1f)) }
        val a = physics.spawn(0.3f)!!
        val b = physics.spawn(0.7f)!!
        // Head-on, same height, same size: they meet in the middle.
        a.x = 1.5f; a.y = 0.6f; a.vx = 2f; a.vy = 0f; a.spin = 0f
        b.x = 2.5f; b.y = 0.6f; b.vx = -2f; b.vy = 0f; b.spin = 0f
        val momentumBefore = a.mass * a.vx + b.mass * b.vx
        var minGap = Float.MAX_VALUE
        var collided = false
        var momentumAfter = 0f
        var t = 0f
        while (t < 0.5f && a in physics.bodies && b in physics.bodies) {
            physics.advance(1f / 120f)
            val gap = kotlin.math.hypot(b.x - a.x, b.y - a.y) - a.radius - b.radius
            minGap = minOf(minGap, gap)
            if (!collided && a.vx < 0f && b.vx > 0f) { collided = true; momentumAfter = a.mass * a.vx + b.mass * b.vx }
            t += 1f / 120f
        }
        assertTrue("they bounced apart", collided)
        assertTrue("overlap $minGap", minGap > -0.01f)
        assertTrue("momentum before $momentumBefore after $momentumAfter", abs(momentumAfter - momentumBefore) < 0.05f * a.mass)
    }

    @Test
    fun aGlancingKnockSetsShapesSpinning() {
        val physics = MorphBouncePhysics(Random(1), autoSpawn = false).apply { resize(4f, 1.6f, listOf(0.1f)) }
        val a = physics.spawn(0.3f)!!
        val b = physics.spawn(0.7f)!!
        a.x = 1.5f; a.y = 0.6f; a.vx = 2f; a.vy = 0f; a.spin = 0f
        b.x = 2.5f; b.y = 0.72f; b.vx = -2f; b.vy = 0f; b.spin = 0f
        run(physics, 0.4f)
        assertTrue("spins ${a.spin} ${b.spin}", abs(a.spin) > 0.5f && abs(b.spin) > 0.5f)
    }

    @Test
    fun aBounceMakesTheShapeGlowBriefly() {
        val physics = quietWorld(4)
        val body = physics.spawn()!!
        var peak = 0f
        var afterHalfSecond = 1f
        var sinceBounce = -1f
        follow(physics, body, 4f) {
            if (body.glow > 0.95f) sinceBounce = 0f
            if (sinceBounce >= 0f) { sinceBounce += 1f / 120f; if (sinceBounce in 0.5f..0.52f) afterHalfSecond = body.glow }
            peak = maxOf(peak, body.glow)
        }
        assertTrue("peak glow $peak", peak > 0.95f)
        assertTrue("glow half a second later $afterHalfSecond", afterHalfSecond < 0.05f)
    }

    @Test
    fun theShapeStartsChangingAtTheMomentOfEachBounce() {
        val physics = quietWorld(5)
        val body = physics.spawn()!!
        var lastShape = body.shapeIndex
        var bounceSeen = 0
        follow(physics, body) {
            if (body.shapeIndex != lastShape) {
                // The new shape begins exactly when the shape reaches the floor.
                assertTrue(body.penetration > 0f || body.y + body.radius >= physics.floor - 0.05f)
                assertTrue(body.morphProgress < 0.5f)
                lastShape = body.shapeIndex
                bounceSeen++
            }
        }
        assertEquals(2, bounceSeen)
    }

    @Test
    fun theNewShapeIsWellFormedAsTheShapeLeavesTheMembrane() {
        for (seed in 1..20) {
            val physics = quietWorld(seed)
            val body = physics.spawn()!!
            var wasInContact = false
            follow(physics, body) {
                val inContact = body.penetration > 0f
                if (wasInContact && !inContact) assertTrue("seed $seed: morph at lift-off ${body.morphProgress}", body.morphProgress > 0.5f)
                wasInContact = inContact
            }
        }
    }

    @Test
    fun aKnockNeverPushesAShapeComingInBackUnderTheCard() {
        val physics = quietWorld(2)
        val coming = physics.spawn(0.5f)!!
        val below = physics.spawn(0.5f)!!
        // A shape just in the area, rising fast, right under one that is still coming in.
        coming.x = 2f; coming.y = -coming.radius; coming.vx = 0f; coming.vy = 0.5f
        below.x = 2f; below.y = below.radius + 0.01f; below.vx = 0f; below.vy = -4f
        var highest = coming.y
        run(physics, 0.5f, onFrame = { highest = minOf(highest, coming.y) })
        assertTrue("pushed up to ${highest + coming.radius} m above where it came in", highest >= -coming.radius - 1e-3f)
    }

    @Test
    fun whenTheAreaShrinksEveryShapeIsKeptInsideIt() {
        for (seed in 1..20) {
            val physics = world(seed)
            run(physics, 3f)
            if (physics.bodies.isEmpty()) run(physics, 2f)
            assertTrue("seed $seed: no shapes in the air", physics.bodies.isNotEmpty())
            // As when the phone turns to landscape or the text grows while the page is open.
            physics.resize(4.11f, 0.9f, listOf(0.085f, 0.10f, 0.115f, 0.13f))
            for (b in physics.bodies) {
                assertTrue("seed $seed: shape ${b.id} too big for the area", b.radius <= physics.height * MorphBouncePhysics.MAX_RADIUS_SHARE)
                assertTrue("seed $seed: shape ${b.id} at ${b.y} under a ${physics.floor} m floor", b.y + b.radius <= physics.floor + 1e-4f)
                if (!b.exiting) assertTrue("seed $seed: shape ${b.id} at ${b.x}", b.x >= b.radius - 1e-4f && b.x <= physics.width - b.radius + 1e-4f)
            }
            // Every bounce after it, the first included, is a real one: felt as a tap.
            val bouncesBefore = physics.bodies.associateWith { it.bounces }
            val seen = physics.bodies.toMutableSet()
            var felt = 0
            run(physics, 10f, onFrame = {
                seen += physics.bodies
                for (b in physics.bodies) {
                    assertTrue("seed $seed: shape ${b.id} below the area", b.y + b.radius <= physics.height + 1e-4f)
                    if (b.entered) assertTrue("seed $seed: shape ${b.id} rose to ${b.y - b.radius}", b.y - b.radius >= -1e-3f)
                }
            }, listener = { _, _ -> felt++ })
            assertEquals("seed $seed: bounces felt", seen.sumOf { it.bounces - (bouncesBefore[it] ?: 0) }, felt)
        }
    }
}
