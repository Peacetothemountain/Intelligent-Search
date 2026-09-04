package com.pixel.intelligentsearch.core.ui

import android.app.Activity
import android.os.Build
import android.view.Choreographer
import android.view.Surface
import android.view.WindowManager

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
            android.view.View::class.java.getMethod("setFrameRateCategory", Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun setFrameRateCategory(activity: Activity, category: Int) {
        // Paces LTPO OLED display dynamic refresh rate (1Hz - 120Hz/144Hz adaptive) on Pixel hardware
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val window = activity.window ?: return
                val params = window.attributes
                val field = preferredFrameRateCategoryField
                if (field != null) {
                    val current = field.getInt(params)
                    if (current != category) {
                        field.setInt(params, category)
                        window.attributes = params
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    setFrameRateCategoryMethod?.invoke(window.decorView, category)
                }
            } catch (_: Throwable) {}
        }
    }

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
                if (params.preferredDisplayModeId != highestMode.modeId) {
                    params.preferredDisplayModeId = highestMode.modeId
                    window.attributes = params
                }
            }
        } catch (_: Throwable) {}
    }

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

    fun postPacedFrame(onFrame: (frameTimeNanos: Long) -> Unit) {
        Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
            onFrame(frameTimeNanos)
        }
    }
}
