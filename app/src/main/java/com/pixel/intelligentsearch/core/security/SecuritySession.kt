package com.pixel.intelligentsearch.core.security

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Window security helper to toggle [WindowManager.LayoutParams.FLAG_SECURE]
 * dynamically on activities to prevent OS screenshots, screen recording,
 * and recent apps snapshot caching of sensitive data.
 */
object WindowSecurityGuard {

    fun enableScreenShield(activity: Activity?) {
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            try {
                activity.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } catch (_: Exception) {}
        }
    }

    fun disableScreenShield(activity: Activity?) {
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            try {
                activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } catch (_: Exception) {}
        }
    }
}

/**
 * Dynamic session controller that enforces auto-lock upon app backgrounding
 * or idle inactivity, scrubbing decrypted caches from active memory.
 */
class SecuritySessionLock private constructor() : DefaultLifecycleObserver {

    companion object {
        val instance: SecuritySessionLock by lazy { SecuritySessionLock() }
        private const val DEFAULT_AUTO_LOCK_TIMEOUT_MS = 30_000L // 30 seconds
    }

    private var lastUnlockTimestamp: Long = 0L
    private var isSessionUnlocked: Boolean = false
    private val lockListeners = CopyOnWriteArrayList<() -> Unit>()

    init {
        try {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                try {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(this@SecuritySessionLock)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
            // ProcessLifecycleOwner might not be initialized in non-UI test runners
        }
    }

    fun registerLockListener(listener: () -> Unit) {
        lockListeners.add(listener)
    }

    fun unregisterLockListener(listener: () -> Unit) {
        lockListeners.remove(listener)
    }

    @Synchronized
    fun notifyAuthenticated() {
        isSessionUnlocked = true
        lastUnlockTimestamp = SystemClock.elapsedRealtime()
    }

    fun isUnlocked(timeoutMs: Long = DEFAULT_AUTO_LOCK_TIMEOUT_MS): Boolean {
        synchronized(this) {
            if (!isSessionUnlocked) return false
            if (SystemClock.elapsedRealtime() - lastUnlockTimestamp <= timeoutMs) return true
        }
        // Session expired: lock down outside the monitor so listeners never run while it is held.
        lockdown()
        return false
    }

    fun lockdown() {
        synchronized(this) {
            isSessionUnlocked = false
            lastUnlockTimestamp = 0L
        }
        // Listeners run outside the monitor so a listener that re-enters the session lock from
        // another thread (e.g. to re-check isUnlocked) can never deadlock against lockdown().
        for (listener in lockListeners) {
            try {
                listener.invoke()
            } catch (_: Throwable) {}
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        lockdown()
    }
}
