package com.pixel.intelligentsearch.core.ui

import android.os.SystemClock
import android.view.RoundedCorner
import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where a page is drawn during the back animation: the full page scaled by [scale] from its top start corner and moved
 * so that corner sits at [left], [top] (pixels).
 */
@Immutable
internal data class BackFrame(val left: Float, val top: Float, val scale: Float) {
    /** This frame scaled by [factor] about its own center. */
    fun scaledCentered(factor: Float, width: Float, height: Float): BackFrame = BackFrame(
        left = left + width * scale * (1f - factor) / 2f,
        top = top + height * scale * (1f - factor) / 2f,
        scale = scale * factor
    )

    fun offsetY(dy: Float) = copy(top = top + dy)

    companion object {
        val Identity = BackFrame(0f, 0f, 1f)

        fun lerp(start: BackFrame, end: BackFrame, fraction: Float) = BackFrame(
            start.left + (end.left - start.left) * fraction,
            start.top + (end.top - start.top) * fraction,
            start.scale + (end.scale - start.scale) * fraction
        )
    }
}

/**
 * Android's own back animation between two screens of an app, as the system plays it between activities
 * (WindowManager Shell's DefaultCrossActivityBackAnimation, Android 17 QPR3), so a page inside the app goes back exactly
 * as Settings' pages do.
 *
 * While the finger moves, the closing page shrinks to 90% toward the far side, leaving an 8 dp margin (to the middle
 * for a swipe from the right edge), and follows the finger up and down; the page underneath waits 96 dp to the start,
 * shrinking with it, under a scrim. Once let go, the closing page slides on 96 dp and fades out within the first fifth,
 * and the page underneath settles full size in 450 ms on the emphasized curve, while a low-bouncy spring started at the
 * swipe's speed dips both a little and lets them bounce back.
 */
internal object NativeBackMotion {
    const val MAX_SCALE = 0.9f
    const val MARGIN_DP = 8f
    const val ENTERING_OFFSET_DP = 96f
    const val SCRIM_ALPHA_DARK = 0.8f
    const val SCRIM_ALPHA_LIGHT = 0.2f
    const val POST_COMMIT_MS = 450

    /** The fling spring's resting value; its dips below are the pages' extra shrink. */
    const val SPRING_SCALE = 100f
    const val MAX_FLING_VELOCITY = 1000f
    const val DEFAULT_FLING_VELOCITY = 120f

    /** android.view.animation.BackGestureInterpolator: decelerating, with a slight start. */
    val GestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    /** The emphasized curve (fast-out-extra-slow-in) the post-commit movement follows. */
    val EmphasizedEasing: Easing = EmphasizedPath

    /**
     * Android's path, M0,0 C0.05,0 0.133333,0.06 0.166666,0.4 C0.208333,0.82 0.25,1 1,1, as its two cubic segments, each
     * scaled to fill the unit square.
     */
    private object EmphasizedPath : Easing {
        private const val JOIN_X = 0.166666f
        private const val JOIN_Y = 0.4f
        private val first = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        private val second = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

        override fun transform(fraction: Float): Float =
            if (fraction < JOIN_X) JOIN_Y * first.transform(fraction / JOIN_X)
            else JOIN_Y + (1f - JOIN_Y) * second.transform((fraction - JOIN_X) / (1f - JOIN_X))
    }

    /** SpringForce.STIFFNESS_LOW with DAMPING_RATIO_LOW_BOUNCY. */
    val FlingSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 200f)

    /** The closing page at gesture progress [progress] (already eased), before following the finger vertically. */
    fun closing(progress: Float, width: Float, height: Float, marginPx: Float, fromRightEdge: Boolean): BackFrame {
        var targetLeft = width * (1f - MAX_SCALE) / 2f
        val targetTop = height * (1f - MAX_SCALE) / 2f
        // Scaled into the middle for a swipe from the right edge, and toward the right margin for one from the left.
        if (!fromRightEdge) targetLeft = width - marginPx - width * MAX_SCALE
        return BackFrame.lerp(BackFrame.Identity, BackFrame(targetLeft, targetTop, MAX_SCALE), progress)
    }

    /** The page underneath at gesture progress [progress]: 96 dp to the start, shrinking in step with the closing page. */
    fun entering(progress: Float, width: Float, height: Float, offsetPx: Float): BackFrame {
        val start = BackFrame(-offsetPx, 0f, 1f)
        return BackFrame.lerp(start, start.scaledCentered(MAX_SCALE, width, height), progress)
    }

    /**
     * How far the pages follow the finger's [dy] up or down: decelerating over half the screen's height, and never past
     * the 8 dp margin for a page drawn at [scale].
     */
    fun verticalShift(dy: Float, scale: Float, height: Float, marginPx: Float): Float {
        val half = height / 2f
        val ratio = min(half, abs(dy)) / half
        val eased = 1f - (1f - ratio) * (1f - ratio)
        val room = max(0f, (height - height * scale) / 2f - marginPx)
        return room * eased * (if (dy < 0) -1f else 1f)
    }

    /**
     * The fling spring's start velocity for a gesture let go at [velocity] (eased progress per second) and [progress]:
     * twice as strong for an edge swipe, at least a small push for a quick flick.
     */
    fun flingVelocity(velocity: Float, progress: Float, edgeSwipe: Boolean): Float {
        var start = velocity * SPRING_SCALE * (1f - MAX_SCALE) * (if (edgeSwipe) 2f else 1f)
        if (progress < 0.1f) start = max(start, DEFAULT_FLING_VELOCITY)
        return -start.coerceIn(0f, MAX_FLING_VELOCITY)
    }

    /** The closing page's opacity [linear] of the way through the post-commit animation: gone within its first fifth. */
    fun postCommitClosingAlpha(linear: Float): Float = max(1f - linear * 5f, 0f)
}

/**
 * One back gesture between two pages, playing [NativeBackMotion]: [track] each event, then [commit] or [cancel], then
 * [end] once the page underneath is really shown. [entering] is the page underneath as it was last seen, drawn while
 * the real one isn't composed. Everything is read while drawing, so the gesture never recomposes the pages.
 */
@Stable
internal class NativeBackState {
    var active by mutableStateOf(false)
        private set
    var entering: ImageBitmap? by mutableStateOf(null)
        private set

    private var progress by mutableFloatStateOf(0f)
    private var touchDy by mutableFloatStateOf(0f)
    private var fromRightEdge = false
    private var edgeSwipe = true
    private var startTouchY = Float.NaN
    private val velocity = VelocityTracker1D(isDataDifferential = false)

    private var committing by mutableStateOf(false)
    private val postCommit = Animatable(0f)
    private val fling = Animatable(NativeBackMotion.SPRING_SCALE)
    private var commitClosing = BackFrame.Identity
    private var commitEntering = BackFrame.Identity

    /** The pages' size and the density's pixel lengths, set by the page as it is laid out. */
    var size by mutableStateOf(IntSize.Zero)
    var marginPx = 0f
    var offsetPx = 0f

    fun begin(event: BackEventCompat, underneath: ImageBitmap?) {
        entering = underneath
        fromRightEdge = event.swipeEdge == BackEventCompat.EDGE_RIGHT
        edgeSwipe = event.swipeEdge == BackEventCompat.EDGE_LEFT || event.swipeEdge == BackEventCompat.EDGE_RIGHT
        startTouchY = event.touchY
        velocity.resetTracking()
        committing = false
        progress = 0f
        touchDy = 0f
        active = true
        track(event)
    }

    fun track(event: BackEventCompat) {
        progress = NativeBackMotion.GestureEasing.transform(event.progress.coerceIn(0f, 1f))
        touchDy = if (startTouchY.isNaN()) 0f else event.touchY - startTouchY
        val time = event.frameTimeMillis.takeIf { it > 0L } ?: SystemClock.uptimeMillis()
        velocity.addDataPoint(time, progress)
    }

    /** Plays the post-commit animation to its end; the caller then shows the page underneath and calls [end]. */
    suspend fun commit() {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        commitClosing = closingDuringGesture(w, h)
        commitEntering = enteringDuringGesture(w, h)
        val speed = runCatching { velocity.calculateVelocity() * 1000f }.getOrDefault(0f)
        val flingStart = NativeBackMotion.flingVelocity(speed, progress, edgeSwipe)
        postCommit.snapTo(0f)
        fling.snapTo(NativeBackMotion.SPRING_SCALE)
        committing = true
        coroutineScope {
            val bounce = launch {
                fling.animateTo(NativeBackMotion.SPRING_SCALE, NativeBackMotion.FlingSpring, initialVelocity = flingStart)
            }
            postCommit.animateTo(1f, tween(NativeBackMotion.POST_COMMIT_MS, easing = LinearEasing))
            bounce.cancel()
        }
        fling.snapTo(NativeBackMotion.SPRING_SCALE)
    }

    /** The gesture was abandoned: the pages ease back to where they were. */
    suspend fun cancel() {
        val from = progress
        val back = Animatable(from)
        back.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1500f)) { progress = value }
        end()
    }

    fun end() {
        active = false
        committing = false
        entering = null
        progress = 0f
        touchDy = 0f
    }

    private fun closingDuringGesture(w: Float, h: Float): BackFrame {
        val frame = NativeBackMotion.closing(progress, w, h, marginPx, fromRightEdge)
        return frame.offsetY(NativeBackMotion.verticalShift(touchDy, frame.scale, h, marginPx))
    }

    private fun enteringDuringGesture(w: Float, h: Float): BackFrame {
        val closing = NativeBackMotion.closing(progress, w, h, marginPx, fromRightEdge)
        val dy = NativeBackMotion.verticalShift(touchDy, closing.scale, h, marginPx)
        return NativeBackMotion.entering(progress, w, h, offsetPx).offsetY(dy)
    }

    private fun flingScale(): Float = min(fling.value / NativeBackMotion.SPRING_SCALE, 1f)

    fun closingFrame(): BackFrame {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        if (!committing) return closingDuringGesture(w, h)
        val target = BackFrame(commitClosing.left + offsetPx, 0f, 1f)
        val eased = NativeBackMotion.EmphasizedEasing.transform(postCommit.value)
        return BackFrame.lerp(commitClosing, target, eased).scaledCentered(flingScale(), w, h)
    }

    fun enteringFrame(): BackFrame {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        if (!committing) return enteringDuringGesture(w, h)
        val eased = NativeBackMotion.EmphasizedEasing.transform(postCommit.value)
        return BackFrame.lerp(commitEntering, BackFrame.Identity, eased).scaledCentered(flingScale(), w, h)
    }

    fun closingAlpha(): Float = if (committing) NativeBackMotion.postCommitClosingAlpha(postCommit.value) else 1f

    /** The scrim over the page underneath, as a share of its full strength: fading out as the page settles. */
    fun scrimFraction(): Float = if (committing) 1f - postCommit.value else 1f
}

/** The screen's corner radius in pixels (0 where the display has square corners), which the moving pages take. */
internal fun screenCornerRadius(view: android.view.View): Float =
    view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f

/** Draws a page at [frame] (see [BackFrame]) with [cornerPx] screen corners, at [alpha]. */
internal fun Modifier.backFrame(frame: () -> BackFrame?, cornerPx: () -> Float, alpha: () -> Float = { 1f }): Modifier =
    graphicsLayer {
        val f = frame()
        if (f == null) {
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
            this.alpha = 1f
            clip = false
            shape = RectangleShape
            return@graphicsLayer
        }
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = f.scale
        scaleY = f.scale
        translationX = f.left
        translationY = f.top
        this.alpha = alpha()
        // The corners are drawn before scaling, so they're grown to match the screen's once scaled.
        val radius = cornerPx() / f.scale.coerceAtLeast(0.01f)
        shape = if (radius > 0f) RoundedCornerShape(radius) else RectangleShape
        clip = true
    }

/** The scrim's color over the page underneath: black, stronger on a dark theme. */
internal fun backScrim(dark: Boolean, fraction: Float): Color =
    Color.Black.copy(alpha = (if (dark) NativeBackMotion.SCRIM_ALPHA_DARK else NativeBackMotion.SCRIM_ALPHA_LIGHT) * fraction)
