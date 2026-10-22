package com.pixel.intelligentsearch.feature.launch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine

/**
 * The edge swipe strip: an invisible, thin window along one edge. A swipe in from it opens search; the predictive back
 * haptic plays as the swipe crosses its threshold and the cancel haptic if it is pulled back.
 *
 * Android gives a window every touch that lands on it, and no app can hand a touch on to the app below, so a tap on
 * the strip itself doesn't reach that app: the strip is kept thin, clear of the system bars and only as tall as the
 * user sets it. With gesture navigation it asks Android to keep the back gesture off its own part of the edge
 * (Android allows 200dp per edge), or Back would take every swipe; the setting's subtitle says so.
 *
 * TalkBack users explore by touch rather than swiping from edges, so the strip is hidden from accessibility.
 */
@SuppressLint("ViewConstructor")
internal class EdgeSwipeStripView(context: Context, private val host: Host) : View(context) {

    interface Host {
        fun open()
    }

    var edge: ScreenEdge = ScreenEdge.RIGHT
        set(value) {
            if (field != value) gesture = newGesture(value)
            field = value
        }

    private val density = context.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var gesture = newGesture(edge)
    private val exclusion = Rect()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun newGesture(edge: ScreenEdge) = EdgeSwipeGesture(edge, FloatingSearchOverlays.SWIPE_THRESHOLD_DP * density, slop)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        exclusion.set(0, 0, width, height)
        systemGestureExclusionRects = listOf(exclusion)
    }

    @SuppressLint("ClickableViewAccessibility") // Not a control: hidden from accessibility (see the class comment).
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val haptics = PixelHapticEngine.get(context)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> gesture.down(event.rawX, event.rawY)
            MotionEvent.ACTION_MOVE -> when (gesture.move(event.rawX, event.rawY)) {
                EdgeSwipeGesture.Event.ARMED -> haptics.performPredictiveBackHaptic(this)
                EdgeSwipeGesture.Event.DISARMED -> haptics.performPredictiveBackCancelHaptic(this)
                else -> Unit
            }
            MotionEvent.ACTION_UP -> if (gesture.up(event.rawX, event.rawY) == EdgeSwipeGesture.Event.OPEN) host.open()
            MotionEvent.ACTION_CANCEL -> if (gesture.cancel() == EdgeSwipeGesture.Event.DISARMED) haptics.performPredictiveBackCancelHaptic(this)
        }
        return true
    }
}
