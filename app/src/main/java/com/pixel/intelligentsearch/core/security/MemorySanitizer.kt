package com.pixel.intelligentsearch.core.security

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.Arrays
import java.util.concurrent.CopyOnWriteArrayList

/**
 * High-performance, zero-allocation memory sanitization utilities for military-grade
 * ephemeral data handling. Ensures cryptographic keys, decrypted search queries,
 * private app package names, and biometric tokens leave zero trace in JVM heap or swap.
 *
 * Developed by NG Designs.
 */
object MemorySanitizer {

    /**
     * Observable sink written after every wipe. The volatile store (which depends on a value read
     * back from the wiped region) makes the zeroing an observable side effect, so neither R8 nor the
     * ART optimizing compiler can treat the fill as a dead store and drop it.
     */
    @Volatile
    private var wipeSink: Int = 0

    /**
     * Overwrites a [ByteArray] in-place with zeroes.
     */
    fun wipe(array: ByteArray?) {
        if (array != null && array.isNotEmpty()) {
            Arrays.fill(array, 0.toByte())
            wipeSink = array[0].toInt()
        }
    }

    /**
     * Overwrites a [CharArray] in-place with null characters.
     */
    fun wipe(array: CharArray?) {
        if (array != null && array.isNotEmpty()) {
            Arrays.fill(array, '\u0000')
            wipeSink = array[0].code
        }
    }

    /**
     * Overwrites the entire visible region of a [ByteBuffer] (position 0 up to capacity) with zeroes
     * and resets its position and limit. Honors [ByteBuffer.arrayOffset] so sliced buffers never zero
     * bytes outside their own window. Safely ignores read-only buffers without throwing
     * [java.nio.ReadOnlyBufferException].
     */
    fun wipe(buffer: ByteBuffer?) {
        if (buffer == null || buffer.isReadOnly) return
        try {
            buffer.clear()
            if (buffer.hasArray()) {
                val start = buffer.arrayOffset()
                Arrays.fill(buffer.array(), start, start + buffer.capacity(), 0.toByte())
                wipeSink = buffer.array()[start].toInt()
            } else {
                while (buffer.hasRemaining()) {
                    buffer.put(0.toByte())
                }
            }
            buffer.clear()
        } catch (_: Exception) {}
    }

    /**
     * Overwrites the entire visible region of a [CharBuffer] (position 0 up to capacity) with null
     * characters and resets its position and limit. Honors [CharBuffer.arrayOffset] for sliced buffers.
     */
    fun wipe(buffer: CharBuffer?) {
        if (buffer == null || buffer.isReadOnly) return
        try {
            buffer.clear()
            if (buffer.hasArray()) {
                val start = buffer.arrayOffset()
                Arrays.fill(buffer.array(), start, start + buffer.capacity(), '\u0000')
                wipeSink = buffer.array()[start].code
            } else {
                while (buffer.hasRemaining()) {
                    buffer.put('\u0000')
                }
            }
            buffer.clear()
        } catch (_: Exception) {}
    }
}

/**
 * Scoped, auto-zeroing byte array container. Automatically wipes underlying memory
 * on [close] or scope exit.
 */
class SecureByteArray(size: Int) : AutoCloseable {
    val data: ByteArray = ByteArray(size)
    @Volatile
    private var isWiped = false

    constructor(initialBytes: ByteArray) : this(initialBytes.size) {
        System.arraycopy(initialBytes, 0, data, 0, initialBytes.size)
    }

    val size: Int
        get() = data.size

    operator fun get(index: Int): Byte {
        checkNotWiped()
        return data[index]
    }

    operator fun set(index: Int, value: Byte) {
        checkNotWiped()
        data[index] = value
    }

    private fun checkNotWiped() {
        check(!isWiped) { "SecureByteArray has already been zeroized and closed." }
    }

    override fun close() {
        if (!isWiped) {
            MemorySanitizer.wipe(data)
            isWiped = true
        }
    }
}

/**
 * Scoped, auto-zeroing char array container for sensitive passwords, tokens, and queries.
 */
class SecureCharArray(size: Int) : AutoCloseable {
    val data: CharArray = CharArray(size)
    @Volatile
    private var isWiped = false

    constructor(initialChars: CharArray) : this(initialChars.size) {
        System.arraycopy(initialChars, 0, data, 0, initialChars.size)
    }

    val size: Int
        get() = data.size

    operator fun get(index: Int): Char {
        checkNotWiped()
        return data[index]
    }

    operator fun set(index: Int, value: Char) {
        checkNotWiped()
        data[index] = value
    }

    private fun checkNotWiped() {
        check(!isWiped) { "SecureCharArray has already been zeroized and closed." }
    }

    override fun close() {
        if (!isWiped) {
            MemorySanitizer.wipe(data)
            isWiped = true
        }
    }
}

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
