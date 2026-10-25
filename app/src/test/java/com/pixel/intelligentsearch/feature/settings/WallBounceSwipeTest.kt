package com.pixel.intelligentsearch.feature.settings

import android.animation.ValueAnimator
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A card swiped against the screen's walls ([WallBounce], [WallBounceState]): it presses into the wall it is pushed
 * against, and is dismissed by a long enough push or a flick toward the wall, bouncing back through the middle and out
 * past the far edge; short of that it settles back. A card caught mid-move carries on from exactly where it is, and
 * with animations off a dismissal is immediate.
 */
@RunWith(RobolectricTestRunner::class)
class WallBounceSwipeTest {

    @get:Rule val compose = createComposeRule()

    // ---- The pure geometry and timing ----

    @Test
    fun squashIsZeroUntilPastTheWallAndNeverPastTheMost() {
        assertEquals(0f, WallBounce.squashFor(0f, WIDTH), 0f)
        assertEquals(0f, WallBounce.squashFor(-20f, WIDTH), 0f)
        assertEquals("an unmeasured card isn't pressed", 0f, WallBounce.squashFor(50f, 0f), 0f)
        var last = 0f
        for (over in listOf(1f, 10f, 50f, 100f, 200f, 400f, 1_000f, 10_000f)) {
            val squash = WallBounce.squashFor(over, WIDTH)
            assertTrue("pushing further ($over) presses in more", squash > last)
            assertTrue("never past the most ($squash)", squash <= WallBounce.MAX_SQUASH)
            last = squash
        }
        assertEquals("a long push gets close to the most", WallBounce.MAX_SQUASH, WallBounce.squashFor(10 * WIDTH, WIDTH), 1e-4f)
    }

    /** [WallBounce.overFor] undoes [WallBounce.squashFor], so catching a pressed card doesn't make it jump. */
    @Test
    fun overForIsSquashForsInverse() {
        assertEquals(0f, WallBounce.overFor(0f, WIDTH), 0f)
        assertEquals(0f, WallBounce.overFor(0.05f, 0f), 0f)
        for (over in listOf(1f, 10f, 50f, 100f, 300f, 600f)) {
            val back = WallBounce.overFor(WallBounce.squashFor(over, WIDTH), WIDTH)
            assertEquals("over $over", over, back, over * 1e-3f + 0.05f)
        }
        // At (or, from a spring's overshoot, past) the most it stays finite.
        assertTrue(WallBounce.overFor(WallBounce.MAX_SQUASH, WIDTH).isFinite())
        assertTrue(WallBounce.overFor(WallBounce.MAX_SQUASH * 2, WIDTH).isFinite())
    }

    @Test
    fun poseFollowsTheFingerBetweenTheWallsAndPressesInPastThem() {
        assertEquals(0f to 0f, WallBounce.pose(0f, GAP, WIDTH))
        assertEquals(-30f to 0f, WallBounce.pose(-30f, GAP, WIDTH))
        assertEquals(GAP to 0f, WallBounce.pose(GAP, GAP, WIDTH))
        for (side in listOf(-1f, 1f)) {
            val (x, squash) = WallBounce.pose(side * (GAP + 100f), GAP, WIDTH)
            assertEquals("stops at the wall on its side ($side)", side * GAP, x, 0f)
            assertEquals(WallBounce.squashFor(100f, WIDTH), squash, 0f)
        }
    }

    @Test
    fun aLongEnoughPushPastTheWallDismissesWhicheverWayTheFingerMoves() {
        val over = WallBounce.COMMIT_FRACTION * WIDTH
        for (side in listOf(-1f, 1f)) {
            assertTrue(WallBounce.commits(side * (GAP + over + 1f), 0f, GAP, WIDTH, DENSITY))
            // Even moving back from the wall as it is let go.
            assertTrue(WallBounce.commits(side * (GAP + over + 1f), -side * 5_000f, GAP, WIDTH, DENSITY))
            assertFalse(WallBounce.commits(side * (GAP + over - 1f), 0f, GAP, WIDTH, DENSITY))
            assertFalse(WallBounce.commits(side * GAP * 0.8f, 0f, GAP, WIDTH, DENSITY))
        }
        assertFalse("an unmeasured card never goes", WallBounce.commits(-10_000f, -10_000f, GAP, 0f, DENSITY))
    }

    @Test
    fun aFlickTowardTheWallDismissesHoweverShortTheDrag() {
        val fast = WallBounce.FLING_DP_PER_S * DENSITY
        for (side in listOf(-1f, 1f)) {
            val short = side * GAP * 0.5f
            assertTrue(WallBounce.commits(short, side * fast, GAP, WIDTH, DENSITY))
            assertFalse("too slow", WallBounce.commits(short, side * (fast - 1f), GAP, WIDTH, DENSITY))
            assertFalse("away from the wall", WallBounce.commits(short, -side * fast * 3, GAP, WIDTH, DENSITY))
            assertFalse("too short a drag", WallBounce.commits(side * GAP * 0.4f, side * fast * 3, GAP, WIDTH, DENSITY))
        }
        assertFalse("not moved at all", WallBounce.commits(0f, 10_000f, GAP, WIDTH, DENSITY))
        // The speed is in dp per second: a denser screen needs more pixels per second.
        assertFalse(WallBounce.commits(-GAP, -fast, GAP, WIDTH, DENSITY * 2))
        assertTrue(WallBounce.commits(-GAP, -fast * 2, GAP, WIDTH, DENSITY * 2))
    }

    @Test
    fun theExitCrossesBothGapsTheCardAndTheMargin() {
        assertEquals(2 * GAP + WIDTH + 30f, WallBounce.exitDistance(GAP, WIDTH, 30f), 0f)
    }

    @Test
    fun exitEasingRisesFromZeroToOneLeavingAtItsStartingShare() {
        for (share in listOf(0f, 0.15f, 0.35f, 0.6f, 1f)) {
            val easing = WallBounce.exitEasing(share)
            assertEquals(0f, easing.transform(0f), 1e-6f)
            assertEquals(1f, easing.transform(1f), 1e-6f)
            var last = 0f
            for (i in 1..100) {
                val f = easing.transform(i / 100f)
                assertTrue("share $share: rises at ${i / 100f}", f > last)
                last = f
            }
            // It leaves the wall at [share] of the average speed (the slope at the start).
            val t = 1e-3f
            assertEquals("share $share: starting speed", share, easing.transform(t) / t, 2e-3f)
            // And speeds up after: never faster at the start than at the end.
            assertTrue(easing.transform(t) / t <= (1f - easing.transform(1f - t)) / t + 1e-3f)
        }
        // A share outside 0..1 is held to it.
        assertEquals(WallBounce.exitEasing(0f).transform(0.5f), WallBounce.exitEasing(-1f).transform(0.5f), 0f)
        assertEquals(WallBounce.exitEasing(1f).transform(0.5f), WallBounce.exitEasing(3f).transform(0.5f), 0f)
    }

    @Test
    fun reboundShareIsAThirdOfTheHitWithinItsBounds() {
        assertEquals(0f, WallBounce.reboundShare(5_000f, 0f), 0f)
        assertEquals(0f, WallBounce.reboundShare(5_000f, -10f), 0f)
        // A distance covered in EXIT_MILLIS at 1000 px/s on average.
        val distance = 1_000f * WallBounce.EXIT_MILLIS / 1000f
        assertEquals(0.35f, WallBounce.reboundShare(1_000f, distance), 1e-4f)
        assertEquals("a slow hit still leaves the wall", 0.15f, WallBounce.reboundShare(0f, distance), 0f)
        assertEquals("never faster than the exit's average", 0.6f, WallBounce.reboundShare(100_000f, distance), 0f)
        for (speed in listOf(0f, 100f, 500f, 1_000f, 3_000f, 50_000f)) {
            assertTrue(WallBounce.reboundShare(speed, distance) in 0.15f..0.6f)
        }
    }

    @Test
    fun enterDelayRipplesAndIsCapped() {
        assertEquals(0L, WallBounce.enterDelay(0))
        assertEquals(WallBounce.ENTER_STAGGER_MILLIS, WallBounce.enterDelay(1))
        assertEquals(2 * WallBounce.ENTER_STAGGER_MILLIS, WallBounce.enterDelay(2))
        assertEquals(WallBounce.MAX_ENTER_STAGGER_MILLIS, WallBounce.enterDelay(6))
        assertEquals(WallBounce.MAX_ENTER_STAGGER_MILLIS, WallBounce.enterDelay(100))
        assertEquals(0L, WallBounce.enterDelay(-3))
    }

    // ---- One card ----

    private lateinit var state: WallBounceState
    private var commits = 0
    private var exits = 0
    private val thresholds = mutableListOf<Boolean>()

    private fun show(entering: Boolean = false, animate: Boolean = true) {
        // The scale is static, and the screenshot tests that may run first in this JVM leave animations off.
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember {
                WallBounceState(
                    scope = scope, gap = GAP, margin = GAP, density = DENSITY, entering = entering,
                    animate = { animate },
                    onThreshold = { thresholds += it },
                    onCommit = { commits++ },
                    onExited = { exits++ }
                ).also { it.width = WIDTH }
            }
        }
        frames()
    }

    /** Publishes the test thread's writes (with auto-advance off nothing else would) and runs [n] frames. */
    private fun frames(n: Int = 2) {
        Snapshot.sendApplyNotifications()
        repeat(n) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun runOut() {
        frames()
        compose.mainClock.advanceTimeBy(2_000)
    }

    @Test
    fun aLongPushDismissesThenLeavesPastTheFarEdge() {
        show()
        state.onDragStart()
        state.onDrag(-(GAP + 0.3f * WIDTH))
        frames()
        assertEquals("pressed against the left wall", -GAP, state.offset.value, 0.01f)
        assertTrue(state.squash.value > 0f)
        assertEquals(listOf(true), thresholds)

        state.onDragStop(0f)
        assertEquals("gone as far as the user is concerned", 1, commits)
        assertEquals(0, exits)
        assertEquals(WallBouncePhase.Exiting, state.phase)
        assertFalse(state.acceptsDrag)

        frames(6)
        assertEquals("still on its way", 0, exits)
        runOut()
        assertEquals(1, commits)
        assertEquals(1, exits)
        // Off the left wall, back through the middle and clear of the right edge.
        assertEquals(-GAP + WallBounce.exitDistance(GAP, WIDTH, GAP), state.offset.value, 0.5f)
        assertTrue(state.offset.value > WIDTH)
    }

    @Test
    fun aHalfSwipeSettlesBackWithoutDismissing() {
        show()
        state.onDragStart()
        state.onDrag(-(GAP + 0.1f * WIDTH))
        frames()
        assertTrue(state.squash.value > 0f)
        state.onDragStop(0f)
        assertEquals(WallBouncePhase.Settling, state.phase)
        runOut()

        assertEquals(0, commits)
        assertEquals(0, exits)
        assertEquals(0f, state.offset.value, 0.5f)
        assertEquals(0f, state.squash.value, 1e-3f)
        assertEquals(WallBouncePhase.Idle, state.phase)
        assertTrue(thresholds.none { it })
    }

    @Test
    fun crossingBackOverTheThresholdUndoesIt() {
        show()
        state.onDragStart()
        state.onDrag(GAP + 0.3f * WIDTH)
        frames()
        state.onDrag(-0.2f * WIDTH)
        frames()
        assertEquals(listOf(true, false), thresholds)
        state.onDragStop(0f)
        runOut()
        assertEquals(0, commits)
        assertEquals(0f, state.offset.value, 0.5f)
    }

    @Test
    fun aFlickDismissesAShortDrag() {
        show()
        state.onDragStart()
        state.onDrag(GAP * 0.6f)
        frames()
        state.onDragStop(WallBounce.FLING_DP_PER_S * DENSITY * 2)
        assertEquals(1, commits)
        runOut()
        assertEquals(1, exits)
        // Bounced off the right wall, so it leaves past the left edge.
        assertTrue(state.offset.value < -WIDTH)
    }

    /** TalkBack's Remove: the same flight as a swipe, toward the wall asked for. */
    @Test
    fun dismissFliesTheCardOff() {
        show()
        state.dismiss(-1f)
        assertEquals(1, commits)
        assertEquals(0, exits)
        runOut()
        assertEquals(1, exits)
        assertTrue(state.offset.value > WIDTH)
        // A second request once it has gone does nothing.
        state.dismiss(-1f)
        assertEquals(1, commits)
    }

    @Test
    fun anEnteringCardSlidesInFromTheRightAndSettles() {
        show(entering = true)
        assertTrue("waits past the right edge until it is brought in", state.offset.value > WIDTH)
        state.enter(0)
        frames(4)
        val partway = state.offset.value
        assertTrue("on its way in ($partway)", partway > 0f && partway < WIDTH + 2 * GAP)
        runOut()
        assertEquals(0f, state.offset.value, 0.5f)
        assertEquals(WallBouncePhase.Idle, state.phase)
        assertEquals(0, commits)
    }

    @Test
    fun aCaughtEnteringCardCarriesOnFromWhereItIs() {
        show(entering = true)
        state.enter(0)
        frames(5)
        val caughtAt = state.offset.value
        assertTrue("caught outside the walls ($caughtAt)", caughtAt > GAP + 1f && caughtAt < WIDTH + 2 * GAP)
        assertTrue(state.catchesImmediately)

        state.onDragStart()
        frames(4)
        assertEquals("holds still under the finger", caughtAt, state.offset.value, 0.01f)
        state.onDrag(-10f)
        frames()
        assertEquals("then follows it from there", caughtAt - 10f, state.offset.value, 0.01f)

        // Let go out there, it isn't a dismissal: it settles in.
        state.onDragStop(0f)
        runOut()
        assertEquals(0, commits)
        assertEquals(0f, state.offset.value, 0.5f)
        assertEquals(WallBouncePhase.Idle, state.phase)
    }

    /** Caught as it springs back through the middle (no longer pressed in), it stays under the finger. */
    @Test
    fun aCaughtSettlingCardDoesNotJump() {
        show()
        state.onDragStart()
        state.onDrag(-(GAP + 0.15f * WIDTH))
        frames()
        state.onDragStop(0f)
        var frame = 0
        while (frame < 40 && !(state.squash.value == 0f && kotlin.math.abs(state.offset.value) > 0.5f)) {
            frames(1)
            frame++
        }
        val x = state.offset.value
        assertTrue("caught while still moving ($x)", state.phase == WallBouncePhase.Settling && kotlin.math.abs(x) > 0.5f)
        assertTrue(state.catchesImmediately)

        state.onDragStart()
        frames(3)
        assertEquals(x, state.offset.value, 0.01f)
        state.onDrag(5f)
        frames()
        assertEquals(x + 5f, state.offset.value, 0.01f)
    }

    @Test
    fun withAnimationsOffADismissalIsImmediate() {
        show(animate = false)
        state.dismiss(1f)
        assertEquals(1, commits)
        assertEquals("removed at once, without waiting for a frame", 1, exits)
    }

    @Test
    fun withAnimationsOffASwipeDismissalIsImmediate() {
        show(animate = false)
        state.onDragStart()
        state.onDrag(-(GAP + 0.3f * WIDTH))
        frames()
        state.onDragStop(0f)
        assertEquals(1, commits)
        assertEquals(1, exits)
    }

    @Test
    fun withAnimationsOffAnEnteringCardJustAppears() {
        show(entering = true, animate = false)
        state.enter(WallBounce.enterDelay(3))
        assertEquals(WallBouncePhase.Idle, state.phase)
        frames(1)
        assertEquals(0f, state.offset.value, 0f)
    }

    private companion object {
        const val WIDTH = 400f
        const val GAP = 40f
        const val DENSITY = 1f
    }
}
