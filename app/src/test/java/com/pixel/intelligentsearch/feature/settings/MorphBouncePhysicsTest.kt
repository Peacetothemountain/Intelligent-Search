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

    private fun run(physics: MorphBouncePhysics, seconds: Float, onFrame: () -> Unit = {}, listener: MorphBouncePhysics.BounceListener? = null) {
        var t = 0f
        while (t < seconds) {
            physics.advance(1f / 120f, listener)
            onFrame()
            t += 1f / 120f
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
        for (seed in 1..20) {
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
    fun everyShapeLeavesAfterItsPlannedBounces() {
        for (seed in 1..20) {
            val physics = world(seed)
            val planned = mutableMapOf<Int, Int>()
            val counted = mutableMapOf<Int, Int>()
            run(physics, 25f, onFrame = {
                val alive = physics.bodies.map { it.id }.toSet()
                for (b in physics.bodies) {
                    planned[b.id] = b.plannedBounces
                    counted[b.id] = b.bounces
                }
                // Shapes that just left: compare their bounce count with the plan.
                for (id in planned.keys - alive) {
                    val p = planned.remove(id)!!
                    val c = counted.remove(id)!!
                    assertEquals("seed $seed: shape $id planned $p bounces, made $c", p, c)
                }
            })
        }
    }

    @Test
    fun eachBounceIsLowerThanTheLastLikeARealBall() {
        val physics = world(3)
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
        // y grows downwards: each apex sits lower (larger y) than the one before.
        for (i in 1 until apexes.size) assertTrue("apexes $apexes", apexes[i] > apexes[i - 1])
    }

    @Test
    fun theShapeStartsChangingAtTheMomentOfEachBounce() {
        val physics = world(5)
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
        val physics = world(9)
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
        val physics = world(13)
        val body = physics.spawn(bounces = 3, slot = 0, slots = 1)!!
        val spinBefore = body.spin
        run(physics, 3f)
        assertEquals(0f, spinBefore, 0f)
        assertTrue("spin ${body.spin}", body.bounces == 0 || kotlin.math.abs(body.spin) > 0.1f || body !in physics.bodies)
    }

    @Test
    fun aRestitutionOfPoint75KeepsThreeQuartersOfTheImpactSpeed() {
        val physics = MorphBouncePhysics(Random(1)).apply { resize(4f, 2f, listOf(0.1f)) }
        val body = MorphBouncePhysics.Body(0, 0.1f, 0.06f, 0.75f, 1).apply { x = 2f; y = 1.9f - 0.0001f; vy = 3f }
        var t = 0f
        while (body.vy > 0f || body.penetration > 0f) { physics.step(body, MorphBouncePhysics.STEP, null); t += MorphBouncePhysics.STEP; if (t > 1f) break }
        // Air drag takes a little too, so allow a small margin below 0.75.
        val ratio = -body.vy / 3f
        assertTrue("rebound ratio $ratio", ratio in 0.66f..0.76f)
    }
}
