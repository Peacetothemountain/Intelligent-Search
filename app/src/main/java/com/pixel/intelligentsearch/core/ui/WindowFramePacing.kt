package com.pixel.intelligentsearch.core.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager

object WindowFramePacing {

    fun setHighRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, 4) // 4 = FRAME_RATE_CATEGORY_HIGH
    }

    fun setNormalRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, 2) // 2 = FRAME_RATE_CATEGORY_NORMAL
    }

    fun setLowRefreshRateCategory(activity: Activity?) {
        if (activity == null) return
        setFrameRateCategory(activity, 1) // 1 = FRAME_RATE_CATEGORY_LOW
    }

    private fun setFrameRateCategory(activity: Activity, category: Int) {
        // Paces LTPO OLED display dynamic refresh rate (1Hz - 120Hz adaptive) on Pixel 11 Pro series hardware
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val window = activity.window ?: return
                val params = window.attributes
                val field = WindowManager.LayoutParams::class.java.getField("preferredFrameRateCategory")
                field.setInt(params, category)
                window.attributes = params
            } catch (e: Exception) {
                // Fallback for earlier SDK API targets
            }
        }
    }
}
