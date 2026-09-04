package com.pixel.intelligentsearch.core.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager

object WindowFramePacing {

    private const val FRAME_RATE_CATEGORY_HIGH = 4
    private const val FRAME_RATE_CATEGORY_NORMAL = 2
    private const val FRAME_RATE_CATEGORY_LOW = 1

    private val preferredFrameRateCategoryField: java.lang.reflect.Field? by lazy {
        try {
            WindowManager.LayoutParams::class.java.getField("preferredFrameRateCategory").apply {
                isAccessible = true
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun setHighRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, FRAME_RATE_CATEGORY_HIGH)
    }

    fun setNormalRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, FRAME_RATE_CATEGORY_NORMAL)
    }

    fun setLowRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, FRAME_RATE_CATEGORY_LOW)
    }

    private fun setFrameRateCategory(activity: Activity, category: Int) {
        // Paces LTPO OLED display dynamic refresh rate (1Hz - 120Hz adaptive) on Pixel hardware
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val window = activity.window ?: return
                val field = preferredFrameRateCategoryField ?: return
                val params = window.attributes
                field.setInt(params, category)
                window.attributes = params
            } catch (e: Throwable) {
                // Fallback for earlier SDK API targets
            }
        }
    }
}
