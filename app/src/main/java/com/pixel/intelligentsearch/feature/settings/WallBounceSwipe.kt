package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sign

/**
 * The geometry and timing of a card swiped away against the screen's walls, as pure functions so they can be checked
 * without drawing. Offsets are the card's horizontal move in pixels (negative is left); [gap] is the distance from the
 * card's edge to the wall at rest; squash is how much of its width the card is pressed in against a wall (0 to
 * [MAX_SQUASH]).
 */
internal object WallBounce {
    /** The most a card presses in against a wall, as a share of its width: the platform's overscroll stretch is as subtle. */
    const val MAX_SQUASH = 0.08f

    /** Pressing past the wall by this share of the card's width (with the finger) is a dismiss. */
    const val COMMIT_FRACTION = 0.22f

    /** A flick toward a wall at least this fast (dp per second) dismisses, however short the drag. */
    const val FLING_DP_PER_S = 900f

    /** How long the card takes from leaving the wall to being clear of the far edge. */
    const val EXIT_MILLIS = 420

    /** How long the card takes to arrive from off the right edge, at most, before it settles. */
    const val ENTER_STAGGER_MILLIS = 40L
    const val MAX_ENTER_STAGGER_MILLIS = 240L

    /** How far past the wall (in pixels of finger travel) the card is pressed in for [over] pixels of push. */
    private fun squashScale(width: Float): Float = width * 0.35f

    /** The squash for a finger [over] pixels past the wall: quick at first, then stiffer, never past [MAX_SQUASH]. */
    fun squashFor(over: Float, width: Float): Float {
        if (over <= 0f || width <= 0f) return 0f
        return MAX_SQUASH * (1f - exp(-over / squashScale(width)))
    }

    /** The finger travel past the wall that gives [squash]: [squashFor]'s inverse, so a caught card doesn't jump. */
    fun overFor(squash: Float, width: Float): Float {
        if (squash <= 0f || width <= 0f) return 0f
        val share = (squash / MAX_SQUASH).coerceAtMost(0.999f)
        return -squashScale(width) * ln(1f - share)
    }

    /** Where the card sits for a finger at [position] (its move were there no wall), and how far it is pressed in. */
    fun pose(position: Float, gap: Float, width: Float): Pair<Float, Float> {
        if (abs(position) <= gap) return position to 0f
        val side = sign(position)
        return side * gap to squashFor(abs(position) - gap, width)
    }

    /** Whether letting go of a finger at [position] moving at [velocity] (px/s) dismisses the card. */
    fun commits(position: Float, velocity: Float, gap: Float, width: Float, density: Float): Boolean {
        if (width <= 0f) return false
        val over = abs(position) - gap
        if (over > COMMIT_FRACTION * width) return true
        val towardWall = position != 0f && sign(velocity) == sign(position)
        return towardWall && abs(velocity) >= FLING_DP_PER_S * density && abs(position) >= gap * 0.5f
    }

    /** How far the card travels from the wall it bounced off until it is clear of the far edge. */
    fun exitDistance(gap: Float, width: Float, margin: Float): Float = 2f * gap + width + margin

    /**
     * The exit's easing: the card leaves the wall at [startShare] of the average speed and is pushed on at a constant
     * rate, so it slows least where it is slowest (just off the wall, back through the middle) and is fastest leaving
     * the screen. Position under constant acceleration is s = v0 t + a t² / 2; normalised, that is
     * f(t) = k t + (1 - k) t² with k the starting share, which keeps f(0) = 0, f(1) = 1 and f rising throughout.
     */
    fun exitEasing(startShare: Float): Easing {
        val k = startShare.coerceIn(0f, 1f)
        return Easing { t -> k * t + (1f - k) * t * t }
    }

    /** How fast the card leaves the wall, as a share of the exit's average speed, for a hit at [impactSpeed] px/s. */
    fun reboundShare(impactSpeed: Float, distance: Float): Float {
        if (distance <= 0f) return 0f
        val average = distance / (EXIT_MILLIS / 1000f)
        // An inelastic bounce: about a third of the speed it hit with, and never more than the exit's average.
        return (0.35f * impactSpeed / average).coerceIn(0.15f, 0.6f)
    }

    /** How far past the middle an entering card from [from] may swing: about 4% of the way, at most most of [gap]. */
    fun enterOvershoot(from: Float, gap: Float): Float =
        if (from <= 0f) ENTER_OVERSHOOT else minOf(ENTER_OVERSHOOT, 0.8f * gap / from)

    /**
     * The damping ratio of a spring that, released from rest, overshoots by [overshoot] of its travel: a damped
     * oscillator's first overshoot is e^(-ζπ/√(1-ζ²)), so ζ = -ln(o) / √(π² + ln²(o)).
     */
    fun dampingFor(overshoot: Float): Float {
        val l = ln(overshoot.coerceIn(1e-4f, 0.99f))
        return (-l / kotlin.math.sqrt(PI_F * PI_F + l * l)).coerceIn(0.05f, 1f)
    }

    /** An entering card's overshoot past the middle, as a share of its way in. */
    const val ENTER_OVERSHOOT = 0.038f
    private const val PI_F = 3.14159265f

    /** The delay before card [index] of a group starts entering, so a group arrives as a quick ripple. */
    fun enterDelay(index: Int): Long = (index * ENTER_STAGGER_MILLIS).coerceIn(0L, MAX_ENTER_STAGGER_MILLIS)
}

/** Where an entering card waits before it is measured: far past any screen's right edge. */
private const val OFF_SCREEN = 100_000f

/** What a [WallBounceState] card is doing. */
internal enum class WallBouncePhase { Idle, Dragging, Settling, Entering, Exiting }

/**
 * One card that can be swiped against a wall and away (see [WallBounce]), and can slide in from the right. Drives
 * [offset] and [squash] (read them while drawing, through [wallBounce]); [onCommit] runs as a dismissal starts (the
 * card is gone as far as the user is concerned) and [onExited] once the card is off screen.
 * Every animation runs in [scope], cancelling the one before, so a card caught mid-move continues from exactly where
 * it is. With [animate] off, a dismissal removes the card at once and nothing slides.
 */
@Stable
internal class WallBounceState(
    private val scope: CoroutineScope,
    private val gap: Float,
    private val margin: Float,
    private val density: Float,
    entering: Boolean,
    private val animate: () -> Boolean,
    private val onThreshold: (past: Boolean) -> Unit,
    private val onCommit: () -> Unit,
    private val onExited: () -> Unit
) {
    var width by mutableFloatStateOf(0f)
    /**
     * An entering card starts far off the right edge, so it can't show in the middle before it is measured. The
     * springs settle to within half a pixel (and a twentieth of a percent of the card's width for the press), so no
     * animation ends on a visible snap.
     */
    val offset = Animatable(if (entering) OFF_SCREEN else 0f, visibilityThreshold = 0.5f)
    val squash = Animatable(0f, visibilityThreshold = 0.0005f)

    /** Which wall the card is pressed against or bounced off: -1 left, 1 right. */
    var side by mutableFloatStateOf(-1f)
        private set
    var phase by mutableStateOf(if (entering) WallBouncePhase.Entering else WallBouncePhase.Idle)
        private set

    /** The finger's position as the card's move were there no wall; [WallBounce.pose] turns it into the card's. */
    private var position = 0f
    /** Caught outside the walls (still sliding in): it follows the finger freely until it is back between them. */
    private var outside = false
    private var pastThreshold = false
    private var job: Job? = null
    /** Puts the card where the finger is; one at a time, so a frame's drag events coalesce into one move. */
    private var follow: Job? = null

    val acceptsDrag: Boolean get() = phase != WallBouncePhase.Exiting

    /**
     * Whether a touch should catch the card at once, without waiting for the drag to pass the touch slop: only while it
     * is visibly on its way somewhere. A card all but home lets the touch through, so the list scrolls as usual.
     */
    val catchesImmediately: Boolean by derivedStateOf {
        (phase == WallBouncePhase.Settling || phase == WallBouncePhase.Entering) &&
            (abs(offset.value) > density || squash.value > 0.002f)
    }

    /** The press easing off after a catch away from the wall, while the finger moves the card. */
    private var relax: Job? = null

    /** Starts the card off the right edge and brings it in after [delayMillis], settling with a small bounce. */
    fun enter(delayMillis: Long) {
        if (!animate()) {
            phase = WallBouncePhase.Idle
            job?.cancel()
            job = scope.launch { offset.snapTo(0f) }
            return
        }
        phase = WallBouncePhase.Entering
        job?.cancel()
        job = scope.launch {
            // Just off the right edge, now that the card's width is known.
            offset.snapTo(entryOffset())
            squash.snapTo(0f)
            delay(delayMillis)
            // A light spring: it overshoots the middle by about 4% of the way and settles, as a damped spring does;
            // never by more than most of the gap, so even on a wide screen it doesn't pass through the far wall.
            val overshoot = WallBounce.enterOvershoot(offset.value, gap)
            offset.animateTo(0f, spring(dampingRatio = WallBounce.dampingFor(overshoot), stiffness = 320f))
            phase = WallBouncePhase.Idle
        }
    }

    private fun entryOffset(): Float = (if (width > 0f) width else 1080f) + gap + margin

    fun onDragStart() {
        if (phase == WallBouncePhase.Exiting) return
        job?.cancel()
        job = null
        relax?.cancel()
        relax = null
        val x = offset.value
        val s = squash.value.coerceAtLeast(0f)
        outside = abs(x) > gap + 0.5f
        val atWall = abs(x) >= gap - 0.5f
        position = when {
            outside -> x
            // Still against the wall: the finger takes up the press where it is.
            s > 0f && atWall -> sign(x).let { if (it == 0f) side else it } * (gap + WallBounce.overFor(s, width))
            else -> x
        }
        // Caught on its way back from the wall with some press left: the card stays where it is and the press eases
        // off by itself, rather than snapping either way.
        if (s > 0f && !atWall) relax = scope.launch { squash.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f)) }
        phase = WallBouncePhase.Dragging
    }

    fun onDrag(delta: Float) {
        if (phase != WallBouncePhase.Dragging) return
        position += delta
        if (outside && abs(position) <= gap) outside = false
        if (follow?.isActive == true) return
        follow = scope.launch {
            // Reads the finger's latest position when it runs, however many drag events came before it.
            if (outside) {
                offset.snapTo(position)
                squash.snapTo(0f)
                crossThreshold(false)
            } else {
                val (x, s) = WallBounce.pose(position, gap, width)
                if (abs(position) > gap) {
                    side = sign(position)
                    relax?.cancel()
                    relax = null
                }
                offset.snapTo(x)
                if (relax?.isActive != true) squash.snapTo(s)
                crossThreshold(WallBounce.commits(position, 0f, gap, width, density))
            }
        }
    }

    /** Stops following the finger, so a move still queued can't cut short the animation that starts now. */
    private fun stopFollowing() {
        follow?.cancel()
        follow = null
        relax?.cancel()
        relax = null
    }

    fun onDragStop(velocity: Float) {
        if (phase != WallBouncePhase.Dragging) return
        if (!outside && WallBounce.commits(position, velocity, gap, width, density)) {
            if (position != 0f) side = sign(position)
            crossThreshold(true)
            // Only speed towards the wall drives into it; a finger already pulling back adds nothing.
            exit((velocity * side).coerceAtLeast(0f))
        } else {
            settle()
        }
    }

    /** Puts the card back in the middle, unpressed, with a small bounce. */
    fun settle() {
        stopFollowing()
        crossThreshold(false)
        phase = WallBouncePhase.Settling
        job?.cancel()
        job = scope.launch {
            coroutineScope {
                launch { offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                // Critically damped, so it never stretches the other way and a catch never jumps.
                launch { squash.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f)) }
            }
            phase = WallBouncePhase.Idle
        }
    }

    /** Dismisses the card against the wall on [towardSide] (-1 left, 1 right), as a swipe that way would. */
    fun dismiss(towardSide: Float) {
        if (phase == WallBouncePhase.Exiting) return
        side = if (towardSide < 0f) -1f else 1f
        exit(0f)
    }

    private fun exit(impactSpeed: Float) {
        stopFollowing()
        phase = WallBouncePhase.Exiting
        job?.cancel()
        onCommit()
        if (!animate() || width <= 0f) {
            job = null
            onExited()
            return
        }
        val wall = side * gap
        val distance = WallBounce.exitDistance(gap, width, margin)
        val target = wall - side * distance
        job = scope.launch {
            // 1. Still short of the wall: carry on into it at the speed it was let go with.
            val left = abs(wall - offset.value)
            var speed = impactSpeed
            if (left > 0.5f) {
                speed = speed.coerceAtLeast(1200f * density)
                val millis = (left / speed * 1000f).toInt().coerceIn(16, 160)
                offset.animateTo(wall, tween(millis, easing = LinearEasing))
            }
            coroutineScope {
                var leaving: Job? = null
                fun leave() {
                    if (leaving != null) return
                    // 3. Off the wall, back through the middle and out past the far edge, pushed at a constant rate.
                    leaving = launch {
                        offset.animateTo(
                            target,
                            tween(WallBounce.EXIT_MILLIS, easing = WallBounce.exitEasing(WallBounce.reboundShare(speed, distance)))
                        )
                    }
                }
                // 2. The hit: it presses in a little more for the speed it arrives with, springs back out and leaves
                // as it comes clear of the wall; the spring's small overshoot is the stretch of the rebound.
                squash.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
                    initialVelocity = (speed / width).coerceAtMost(0.6f)
                ) { if (value <= 0f) leave() }
                leave()
            }
            onExited()
        }
    }

    private fun crossThreshold(past: Boolean) {
        if (past == pastThreshold) return
        pastThreshold = past
        onThreshold(past)
    }
}

/**
 * A [WallBounceState] for one card: [gap] from the card to the screen's edge, [entering] to slide it in from the
 * right when first shown.
 */
@Composable
internal fun rememberWallBounce(
    gap: Dp,
    entering: Boolean,
    animate: () -> Boolean,
    onThreshold: (past: Boolean) -> Unit,
    onCommit: () -> Unit,
    onExited: () -> Unit
): WallBounceState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentOnExited = androidx.compose.runtime.rememberUpdatedState(onExited)
    val currentOnThreshold = androidx.compose.runtime.rememberUpdatedState(onThreshold)
    val currentOnCommit = androidx.compose.runtime.rememberUpdatedState(onCommit)
    return remember {
        WallBounceState(
            scope = scope,
            gap = with(density) { gap.toPx() },
            margin = with(density) { gap.toPx() },
            density = density.density,
            entering = entering,
            animate = animate,
            onThreshold = { currentOnThreshold.value(it) },
            onCommit = { currentOnCommit.value() },
            onExited = { currentOnExited.value() }
        )
    }
}

/**
 * Moves and presses the card as [state] says, and lets it be dragged sideways. Pressed in against a wall, it shrinks
 * toward that wall and grows a touch taller, as something pushed against a wall bulges; at rest nothing is applied.
 */
@Composable
internal fun Modifier.wallBounce(state: WallBounceState, enabled: Boolean): Modifier {
    val draggable = rememberDraggableState { state.onDrag(it) }
    // Turned off mid-drag (a reorder began), the drag never reports its end: the card goes home rather than freezing.
    LaunchedEffect(enabled) { if (!enabled && state.phase == WallBouncePhase.Dragging) state.settle() }
    return this
        .onSizeChanged { state.width = it.width.toFloat() }
        .graphicsLayer {
            translationX = state.offset.value
            val s = state.squash.value
            if (s != 0f) {
                transformOrigin = TransformOrigin(if (state.side < 0f) 0f else 1f, 0.5f)
                scaleX = 1f - s
                scaleY = 1f + 0.25f * s
            }
        }
        .draggable(
            state = draggable,
            orientation = Orientation.Horizontal,
            enabled = enabled && state.acceptsDrag,
            startDragImmediately = state.catchesImmediately,
            onDragStarted = { state.onDragStart() },
            onDragStopped = { state.onDragStop(it) }
        )
}
