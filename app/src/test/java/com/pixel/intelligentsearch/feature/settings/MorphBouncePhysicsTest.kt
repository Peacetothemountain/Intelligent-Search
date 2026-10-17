package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The rules of the Material Morph animation, checked against the physics directly. */
class MorphBouncePhysicsTest {

    /** An area like the one under the cards on Search Behavior: 4.1 m wide (411 dp), 1.6 m tall (160 dp). */
    private fun world(seed: Int = 7) = MorphBouncePhysics(Random(seed)).apply {
        resize(4.11f, 1.6f, listOf(0.085f, 0.10f, 0.115f, 0.13f))
    }

    /** The same area with nothing dropping in by itself, for tests that study one shape. */
    private fun quietWorld(seed: Int = 7) = MorphBouncePhysics(Random(seed), autoSpawn = false).apply {
        resize(4.11f, 1.6f, listOf(0.085f, 0.10f, 0.115f, 0.13f))
    }

    private fun run(physics: MorphBouncePhysics, seconds: Float, onFrame: () -> Unit = {}, listener: MorphBouncePhysics.BounceListener? = null) {
        var t = 0f
        while (t < seconds) {
            physics.advance(1f / 120f, listener)
            onFrame()
            t += 1f / 120f
        }
    }

    @Test
    fun noShapeEverHopsSkittersOrRollsAlongTheFloor() {
        for (seed in 1..40) {
            val physics = world(seed)
            val contactTime = mutableMapOf<Int, Float>()
            val lastBounceAt = mutableMapOf<Int, Float>()
            var t = 0f
            run(physics, 30f, onFrame = {
                t += 1f / 120f
                for (b in physics.bodies) {
                    // One trampoline contact lasts about π/ω ≈ 80 ms (a little more squeezed under another shape);
                    // never long enough to roll.
                    contactTime[b.id] = if (b.penetration > 0f) (contactTime[b.id] ?: 0f) + 1f / 120f else 0f
                    assertTrue("seed $seed: shape ${b.id} on the floor for ${contactTime[b.id]}s", contactTime[b.id]!! < 0.35f)
                }
            }, listener = { b, _ ->
                // Bounces, and so shape changes, are at least a real flight apart.
                val previous = lastBounceAt[b.id]
                if (previous != null) assertTrue("seed $seed: shape ${b.id} bounced again after ${t - previous}s", t - previous > 0.35f)
                lastBounceAt[b.id] = t
            })
        }
    }

    @Test
    fun everyShapeLeavesTheScreenSoonAfterItsPlannedBounces() {
        for (seed in 1..40) {
            val physics = world(seed)
            run(physics, 40f, onFrame = {
                for (b in physics.bodies) {
                    assertTrue("seed $seed: shape ${b.id} still on screen after ${b.age}s", b.age < 10f)
                    assertTrue("seed $seed: shape ${b.id} made ${b.bounces} bounces, planned ${b.plannedBounces}", b.bounces <= b.plannedBounces + 2)
                }
            })
        }
    }

    @Test
    fun theOpeningWaveDropsFourOrFiveShapes() {
        val physics = world()
        var peak = 0
        run(physics, 1.5f, onFrame = { peak = maxOf(peak, physics.bodies.size) })
        assertTrue("opening wave had $peak shapes", peak in 4..5)
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
    fun withoutKnocksEveryShapeLeavesAfterExactlyItsPlannedBounces() {
        for (seed in 1..40) {
            val physics = quietWorld(seed)
            repeat(6) { i ->
                val body = physics.spawn(bounces = 2 + (seed + i) % 3, slot = i % 5, slots = 5)!!
                var t = 0f
                while (body in physics.bodies && t < 20f) { physics.advance(1f / 120f); t += 1f / 120f }
                assertEquals("seed $seed shape $i", body.plannedBounces, body.bounces)
            }
        }
    }

    @Test
    fun knocksNeverCostAShapeItsPlannedBouncesAndEveryShapeLeaves() {
        for (seed in 1..40) {
            val physics = world(seed)
            val planned = mutableMapOf<Int, Int>()
            val counted = mutableMapOf<Int, Int>()
            run(physics, 25f, onFrame = {
                for (b in physics.bodies) assertTrue("seed $seed: shape ${b.id} still on screen after ${b.age}s", b.age < 24f)
                val alive = physics.bodies.map { it.id }.toSet()
                for (b in physics.bodies) {
                    planned[b.id] = b.plannedBounces
                    counted[b.id] = b.bounces
                }
                // Shapes that just left: compare their bounce count with the plan.
                for (id in planned.keys - alive) {
                    val p = planned.remove(id)!!
                    val c = counted.remove(id)!!
                    // Knocks can add bounces, never take them away.
                    assertTrue("seed $seed: shape $id planned $p bounces, made $c", c >= p)
                }
            })
        }
    }

    @Test
    fun shapesKnockIntoEachOtherInsteadOfPassingThrough() {
        val physics = MorphBouncePhysics(Random(1)).apply { resize(4f, 1.6f, listOf(0.1f)) }
        val a = physics.spawn(bounces = 3, slot = 0, slots = 2)!!
        val b = physics.spawn(bounces = 3, slot = 1, slots = 2)!!
        // Head-on, same height, same size: they meet in the middle.
        a.x = 1.5f; a.y = 0.8f; a.vx = 2f; a.vy = 0f
        b.x = 2.5f; b.y = 0.8f; b.vx = -2f; b.vy = 0f
        val momentumBefore = a.mass * a.vx + b.mass * b.vx
        var minGap = Float.MAX_VALUE
        var collided = false
        var momentumAfter = 0f
        var t = 0f
        while (t < 0.6f && a in physics.bodies && b in physics.bodies) {
            physics.advance(1f / 120f)
            val gap = kotlin.math.hypot(b.x - a.x, b.y - a.y) - a.radius - b.radius
            minGap = minOf(minGap, gap)
            if (!collided && a.vx < 0f && b.vx > 0f) { collided = true; momentumAfter = a.mass * a.vx + b.mass * b.vx }
            t += 1f / 120f
        }
        assertTrue("they bounced apart", collided)
        assertTrue("overlap $minGap", minGap > -0.01f)
        // The impulse itself keeps momentum; both re-plan their exits afterwards, so compare at the moment of impact.
        assertTrue("momentum before $momentumBefore after $momentumAfter", kotlin.math.abs(momentumAfter - momentumBefore) < 0.2f)
    }

    @Test
    fun aBounceMakesTheShapeGlowBriefly() {
        val physics = quietWorld(4)
        val body = physics.spawn(bounces = 2, slot = 0, slots = 1)!!
        var peak = 0f
        var afterHalfSecond = 1f
        var sinceBounce = -1f
        run(physics, 4f, onFrame = {
            if (body !in physics.bodies) return@run
            if (body.glow > 0.95f) sinceBounce = 0f
            if (sinceBounce >= 0f) { sinceBounce += 1f / 120f; if (sinceBounce in 0.5f..0.52f) afterHalfSecond = body.glow }
            peak = maxOf(peak, body.glow)
        })
        assertTrue("peak glow $peak", peak > 0.95f)
        assertTrue("glow half a second later $afterHalfSecond", afterHalfSecond < 0.05f)
    }

    @Test
    fun eachBounceIsLowerThanTheLastLikeARealBall() {
        val physics = quietWorld(3)
        val body = physics.spawn(bounces = 4, slot = 0, slots = 1)!!
        val apexes = mutableListOf<Float>()
        var rising = false
        var top = Float.MAX_VALUE
        run(physics, 6f, onFrame = {
            if (body !in physics.bodies) return@run
            if (body.vy < 0f) { rising = true; top = minOf(top, body.y) }
            if (rising && body.vy > 0f) { apexes += top; rising = false; top = Float.MAX_VALUE }
        })
        assertTrue("apexes $apexes", apexes.size >= 2)
        // y grows downwards: each apex sits lower (larger y) than the one before, or at the bed's minimum bounce.
        val minimumApex = physics.height - body.radius - (physics.height - 2f * body.radius - MorphBouncePhysics.TOP_MARGIN) * MorphBouncePhysics.MIN_REBOUND_SHARE
        for (i in 1 until apexes.size) assertTrue("apexes $apexes", apexes[i] > apexes[i - 1] || apexes[i] <= minimumApex + 0.02f)
    }

    @Test
    fun theShapeStartsChangingAtTheMomentOfEachBounce() {
        val physics = quietWorld(5)
        val body = physics.spawn(bounces = 3, slot = 0, slots = 1)!!
        var lastShape = body.shapeIndex
        var bounceSeen = 0
        run(physics, 6f, onFrame = {
            if (body !in physics.bodies) return@run
            if (body.shapeIndex != lastShape) {
                // The new shape begins exactly when the shape touches the trampoline.
                assertTrue(body.penetration > 0f || body.y + body.radius >= physics.height - 1e-3f)
                assertTrue(body.morphProgress < 0.5f)
                lastShape = body.shapeIndex
                bounceSeen++
            }
        })
        assertEquals(3, bounceSeen)
    }

    @Test
    fun theNewShapeHasFormedBeforeTheShapeLeavesTheTrampoline() {
        val physics = quietWorld(9)
        val body = physics.spawn(bounces = 3, slot = 0, slots = 1)!!
        var wasInContact = false
        run(physics, 6f, onFrame = {
            if (body !in physics.bodies) return@run
            val inContact = body.penetration > 0f
            if (wasInContact && !inContact) assertTrue("morph at lift-off ${body.morphProgress}", body.morphProgress > 0.75f)
            wasInContact = inContact
        })
    }

    @Test
    fun shapesKeepComingWhileThePageStaysOpenButNeverMoreThanFive() {
        val physics = world(11)
        var maxAlive = 0
        val bouncesByWindow = IntArray(6)
        var t = 0f
        run(physics, 60f, onFrame = {
            t += 1f / 120f
            maxAlive = maxOf(maxAlive, physics.bodies.size)
        }, listener = { _, _ -> bouncesByWindow[(t / 10f).toInt().coerceAtMost(5)]++ })
        assertTrue("max alive $maxAlive", maxAlive <= 5)
        // Bounces keep happening in every 10-second window, long after the opening wave has left.
        for ((i, n) in bouncesByWindow.withIndex()) assertTrue("window $i had no bounces: ${bouncesByWindow.toList()}", n > 0)
    }

    @Test
    fun shapesSpinFromHittingTheTrampolineWhileMovingSideways() {
        val physics = quietWorld(13)
        val body = physics.spawn(bounces = 3, slot = 0, slots = 1)!!
        val spinBefore = body.spin
        run(physics, 3f)
        assertEquals(0f, spinBefore, 0f)
        assertTrue("spin ${body.spin}", body.bounces == 0 || kotlin.math.abs(body.spin) > 0.1f || body !in physics.bodies)
    }

    @Test
    fun aRestitutionOfPoint75KeepsThreeQuartersOfTheImpactSpeed() {
        val physics = MorphBouncePhysics(Random(1)).apply { resize(4f, 2f, listOf(0.1f)) }
        val body = MorphBouncePhysics.Body(0, 0.1f, 0.06f, 0.75f, 1).apply { x = 2f; y = 1.9f - 0.0001f; vy = 6f }
        var t = 0f
        while (body.vy > 0f || body.penetration > 0f) { physics.step(body, MorphBouncePhysics.STEP, null); t += MorphBouncePhysics.STEP; if (t > 1f) break }
        // Air drag takes a little too, so allow a small margin below 0.75.
        // A firm landing, so the bed's minimum bounce doesn't come into it.
        val ratio = -body.vy / 6f
        assertTrue("rebound ratio $ratio", ratio in 0.66f..0.76f)
    }
}
