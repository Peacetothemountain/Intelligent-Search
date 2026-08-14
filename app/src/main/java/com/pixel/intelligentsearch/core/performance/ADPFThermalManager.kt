package com.pixel.intelligentsearch.core.performance

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process

class ADPFThermalManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    fun getThermalHeadroom(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                val headroom = powerManager.getThermalHeadroom(10)
                if (headroom in 0.0f..3.0f) {
                    return headroom
                }
            } catch (e: Exception) {
                // Ignore API reflection failures
            }
        }
        return 1.0f // Normal thermal state
    }

    fun shouldThrottleBlurEffects(): Boolean {
        return getThermalHeadroom() > 1.2f
    }

    fun applyTopAppThreadPriority() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
        } catch (e: Exception) {
            // Ignore thread priority failures
        }
    }
}
