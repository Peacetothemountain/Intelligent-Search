package com.pixel.intelligentsearch.core.ui

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Refresh-rate hints for the app's windows, used while gestures and animations run:
 * - Frame-rate category (Android 15+, API 35): the window's decor view asks for a high, normal or low rate, and
 *   scrolling reports its velocity, so an LTPO panel rises with motion and falls back when it stops.
 * - Touch boost (Android 15+): the rate rises as soon as a finger lands, for the length of a gesture.
 * - Mode lock (before Android 14, API 34, which have no per-view hint): a gesture locks the window to the fastest
 *   display mode at the current resolution, and the lock is released when it ends.
 */
object WindowFramePacing {

    /** The app's own category ids, translated to the platform's View.REQUESTED_FRAME_RATE_CATEGORY_* values. */
    const val FRAME_RATE_CATEGORY_HIGH = 4
    const val FRAME_RATE_CATEGORY_NORMAL = 2
    const val FRAME_RATE_CATEGORY_LOW = 1
    const val FRAME_RATE_CATEGORY_NO_PREFERENCE = 0

    /**
     * Safely retrieves the Display instance even if called early in Activity lifecycle before window attachment.
     */
    fun getDisplaySafely(activity: Activity?): Display? {
        if (activity == null) return null
        return try {
            activity.display ?: (activity.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Escalates display refresh rate to maximum (120Hz / 144Hz) for interactive gestures,
     * flings, predictive back animations, and search overlay expansions.
     * Uses seamless Adaptive Refresh Rate (ARR) on Android 14+ / 15+ / 17 without physical
     * display mode renegotiation stutter.
     */
    fun setHighRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, FRAME_RATE_CATEGORY_HIGH)
        enableHardwareTouchBoost(activity, true)
        // On Android 14+ (API 34+), ARR and requested frame rate categories handle
        // 120Hz/144Hz transitions seamlessly without mode renegotiation stutter.
        // Only fallback to preferredDisplayModeId on legacy Android 13 (API 33).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            lockMaxDisplayRefreshRate(activity)
        }
    }

    /**
     * Hands the window's refresh rate back to Android once a gesture that asked for the high rate is over: no
     * preferred category, touch boost on (Android's own default) and the panel's LTPO scaling unlocked. Pinning the
     * "normal" category here instead held the whole window near 60 Hz, without touch boost, for every animation and
     * scroll after the first back swipe, which is what made later motion feel sluggish.
     */
    fun restoreDefaultRefreshRate(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, FRAME_RATE_CATEGORY_NO_PREFERENCE)
        enableHardwareTouchBoost(activity, true)
        unlockDisplayRefreshRate(activity)
    }

    /**
     * Enables hardware touch boost on Android 15+ (API 35+ / Android 17 Canary ZP11).
     * Hardware dynamically boosts LTPO panel to 120Hz upon finger contact.
     */
    private fun enableHardwareTouchBoost(activity: Activity, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                val window = activity.window ?: return
                val params = window.attributes
                if (params.frameRateBoostOnTouchEnabled != enabled) {
                    params.setFrameRateBoostOnTouchEnabled(enabled)
                    window.attributes = params
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Applies the preferred frame rate category to the window and decor view.
     * Uses the Android 15+ (API 35+) platform API without reflection. Releases before API 35 expose no
     * public per-view frame rate hint, so there the display mode is steered by [lockMaxDisplayRefreshRate].
     */
    fun setFrameRateCategory(activity: Activity, category: Int) {
        val window = activity.window ?: return
        val decorView = window.decorView

        // Android 15+ (API 35+) official requested frame rate category
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                val targetRateCategory = when (category) {
                    FRAME_RATE_CATEGORY_HIGH -> View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                    FRAME_RATE_CATEGORY_LOW -> View.REQUESTED_FRAME_RATE_CATEGORY_LOW
                    FRAME_RATE_CATEGORY_NORMAL -> View.REQUESTED_FRAME_RATE_CATEGORY_NORMAL
                    else -> View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
                }
                decorView.setRequestedFrameRate(targetRateCategory)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Informs the display subsystem of dynamic scroll velocity (in pixels/second).
     * On LTPO panels, this drives smooth 120/144Hz ramp-up during fast flings and
     * power-efficient drop to 30Hz or 1Hz when the user halts motion.
     * Direct API 35+ call with zero reflection ([View.setFrameContentVelocity] does not exist before
     * API 35, so calling it on Android 14 would throw on every scroll frame).
     */
    fun setContentVelocity(view: View?, velocityPxPerSec: Float) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                view.setFrameContentVelocity(abs(velocityPxPerSec))
            } catch (_: Throwable) {}
        }
    }

    /**
     * Dynamically adapts LTPO OLED display pacing based on motion state.
     * If moving: sets high refresh rate and reports velocity.
     * If idle: requests low frame rate to allow the panel to ramp down to 1Hz.
     */
    fun setDynamicLtpoPacing(view: View?, isMoving: Boolean) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                if (isMoving) {
                    view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
                } else {
                    view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT)
                    view.setFrameContentVelocity(0f)
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Locks the panel to its fastest refresh rate at the current resolution (see [pickHighRefreshMode]) through
     * [WindowManager.LayoutParams.preferredDisplayModeId]. Leaves the window alone when no faster mode exists.
     */
    fun lockMaxDisplayRefreshRate(activity: Activity?) {
        if (activity == null) return
        try {
            val window = activity.window ?: return
            val display = getDisplaySafely(activity) ?: return

            fun Display.Mode.spec() = ModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)
            val modeId = pickHighRefreshMode(display.supportedModes.map { it.spec() }, display.mode.spec()) ?: return
            val params = window.attributes
            if (params.preferredDisplayModeId != modeId) {
                params.preferredDisplayModeId = modeId
                window.attributes = params
            }
        } catch (_: Throwable) {}
    }

    /** A display mode's id, physical size and refresh rate. */
    internal data class ModeSpec(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

    /**
     * The fastest mode at [current]'s resolution, or null unless it reaches 90 Hz and beats the current rate. Only
     * modes of the same size are considered: picking a mode by rate alone could switch the panel's resolution.
     */
    internal fun pickHighRefreshMode(modes: List<ModeSpec>, current: ModeSpec): Int? {
        val fastest = modes
            .filter { it.width == current.width && it.height == current.height }
            .maxByOrNull { it.refreshRate } ?: return null
        return fastest.id.takeIf { fastest.refreshRate >= 90f && fastest.refreshRate > current.refreshRate }
    }

    /**
     * Resets preferred display mode ID to default (0), allowing system LTPO OLED dynamic scaling (1Hz - 120Hz).
     */
    fun unlockDisplayRefreshRate(activity: Activity?) {
        if (activity == null) return
        try {
            val window = activity.window ?: return
            val params = window.attributes
            if (params.preferredDisplayModeId != 0) {
                params.preferredDisplayModeId = 0
                window.attributes = params
            }
        } catch (_: Throwable) {}
    }
}
