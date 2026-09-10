package com.pixel.intelligentsearch.core.ui

import android.app.Activity
import android.os.Build
import android.view.Choreographer
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Hardware-Synchronized Window & Surface Frame Pacing Engine.
 *
 * Implements 120Hz / 144Hz LTPO OLED dynamic refresh rate adaptation,
 * Velocity-Driven frame rate category escalation (Android 14+),
 * Surface.CHANGE_FRAME_RATE_ALWAYS overrides, and Choreographer VSYNC deadline tracking.
 *
 * Engineered by NG Designs.
 */
object WindowFramePacing {

    const val FRAME_RATE_CATEGORY_HIGH = 4
    const val FRAME_RATE_CATEGORY_NORMAL = 2
    const val FRAME_RATE_CATEGORY_LOW = 1
    const val FRAME_RATE_CATEGORY_NO_PREFERENCE = 0

    fun setHighRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, FRAME_RATE_CATEGORY_HIGH)
        lockMaxDisplayRefreshRate(activity)
    }

    fun setNormalRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, FRAME_RATE_CATEGORY_NORMAL)
    }

    fun setLowRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, FRAME_RATE_CATEGORY_LOW)
    }

    private val preferredFrameRateCategoryField: java.lang.reflect.Field? by lazy {
        try {
            WindowManager.LayoutParams::class.java.getField("preferredFrameRateCategory").apply {
                isAccessible = true
            }
        } catch (_: Throwable) {
            null
        }
    }

    private val setFrameRateCategoryMethod: java.lang.reflect.Method? by lazy {
        try {
            View::class.java.getMethod("setFrameRateCategory", Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        } catch (_: Throwable) {
            null
        }
    }

    private val setFrameContentVelocityMethod: java.lang.reflect.Method? by lazy {
        try {
            View::class.java.getMethod("setFrameContentVelocity", Float::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Applies the preferred frame rate category to the window and decor view.
     */
    fun setFrameRateCategory(activity: Activity, category: Int) {
        val window = activity.window ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val params = window.attributes
                val field = preferredFrameRateCategoryField
                if (field != null) {
                    val current = field.getInt(params)
                    if (current != category) {
                        field.setInt(params, category)
                        window.attributes = params
                    }
                }
                setFrameRateCategoryMethod?.invoke(window.decorView, category)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Informs the display subsystem of dynamic scroll velocity (in pixels/second).
     * On LTPO panels, this drives smooth 120/144Hz ramp-up during fast flings and
     * power-efficient drop to 30Hz or 1Hz when the user halts motion.
     */
    fun setContentVelocity(view: View?, velocityPxPerSec: Float) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                setFrameContentVelocityMethod?.invoke(view, abs(velocityPxPerSec))
            } catch (_: Throwable) {}
        }
    }

    /**
     * Detects and locks the display panel to its highest supported refresh rate (e.g. 144Hz / 120Hz).
     */
    fun lockMaxDisplayRefreshRate(activity: Activity?) {
        if (activity == null) return
        try {
            val window = activity.window ?: return
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                window.windowManager?.defaultDisplay
            } ?: return

            val modes = display.supportedModes
            val highestMode = modes.maxByOrNull { it.refreshRate } ?: return
            if (highestMode.refreshRate >= 90f) {
                val params = window.attributes
                if (params.preferredRefreshRate != highestMode.refreshRate) {
                    params.preferredRefreshRate = highestMode.refreshRate
                    window.attributes = params
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Returns the maximum hardware refresh rate supported by the current display (e.g. 144.0, 120.0, 90.0, 60.0).
     */
    fun getDisplayMaxRefreshRate(activity: Activity?): Float {
        if (activity == null) return 60f
        return try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager?.defaultDisplay
            }
            display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f
        } catch (_: Throwable) {
            60f
        }
    }

    /**
     * Configures hardware-level Surface frame pacing with [Surface.CHANGE_FRAME_RATE_ALWAYS] override.
     */
    fun setSurfacePacing(surface: Surface?, frameRate: Float = 120f) {
        if (surface == null || !surface.isValid) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val strategy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Surface.CHANGE_FRAME_RATE_ALWAYS
                } else {
                    0
                }
                surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, strategy)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Posts a callback synchronized with the hardware VSYNC pulse.
     */
    fun postPacedFrame(onFrame: (frameTimeNanos: Long) -> Unit) {
        Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
            onFrame(frameTimeNanos)
        }
    }

    /**
     * High-precision VSYNC timeline callback on Android 13+ (API 33), exposing vsyncId,
     * expected presentation time, and deadline nanoseconds.
     */
    fun postVsyncPacedFrame(
        onVsync: (vsyncId: Long, expectedPresentationTimeNanos: Long, deadlineNanos: Long) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                Choreographer.getInstance().postVsyncCallback(object : Choreographer.VsyncCallback {
                    override fun onVsync(frameData: Choreographer.FrameData) {
                        val timeline = frameData.preferredFrameTimeline
                        onVsync(
                            timeline.vsyncId,
                            timeline.expectedPresentationTimeNanos,
                            timeline.deadlineNanos
                        )
                    }
                })
                return
            } catch (_: Throwable) {}
        }
        // Fallback for API < 33
        Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
            val simulatedDeadline = frameTimeNanos + 8_333_333L // 8.33ms (120Hz)
            onVsync(0L, frameTimeNanos, simulatedDeadline)
        }
    }
}
