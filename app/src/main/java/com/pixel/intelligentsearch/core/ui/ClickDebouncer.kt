package com.pixel.intelligentsearch.core.ui

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Global debouncer for clicks and navigation events across the app.
 * Prevents rapid accidental double-taps while preserving instant touch response.
 * Supports keyed debouncing per widget to prevent dead zones across independent widgets.
 * Uses atomic CAS operations and concurrent structures for thread safety across all CPU architectures.
 */
object ClickDebouncer {
    private val globalLastClickTime = AtomicLong(0L)
    private val widgetClickTimes = ConcurrentHashMap<Any, Long>()

    private fun currentTimeMs(): Long {
        return try {
            val uptime = SystemClock.uptimeMillis()
            if (uptime > 0L) uptime else System.currentTimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    fun canClick(cooldownMs: Long = 180L, key: Any? = null): Boolean {
        val now = currentTimeMs()
        if (key != null) {
            val last = widgetClickTimes[key] ?: 0L
            if (last > 0L && (now - last) in 0 until cooldownMs) {
                return false
            }
            widgetClickTimes[key] = now
            if (widgetClickTimes.size > 256) {
                widgetClickTimes.entries.removeIf { now - it.value > 10_000L }
            }
            return true
        }

        while (true) {
            val prev = globalLastClickTime.get()
            if (prev > 0L && (now - prev) in 0 until cooldownMs) {
                return false
            }
            if (globalLastClickTime.compareAndSet(prev, if (now > 0L) now else System.currentTimeMillis())) {
                return true
            }
        }
    }

    fun recordClick(timestamp: Long = currentTimeMs(), key: Any? = null) {
        if (key != null) {
            widgetClickTimes[key] = timestamp
        } else {
            globalLastClickTime.set(timestamp)
        }
    }
}
