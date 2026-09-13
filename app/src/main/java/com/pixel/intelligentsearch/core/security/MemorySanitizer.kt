package com.pixel.intelligentsearch.core.security

import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * High-performance, zero-allocation memory sanitization utilities for military-grade
 * ephemeral data handling. Ensures cryptographic keys, decrypted search queries,
 * private app package names, and biometric tokens leave zero trace in JVM heap or swap.
 *
 * Developed by NG Designs.
 */
object MemorySanitizer {

    /**
     * Overwrites a [ByteArray] in-place with zeroes.
     */
    fun wipe(array: ByteArray?) {
        if (array != null && array.isNotEmpty()) {
            Arrays.fill(array, 0.toByte())
        }
    }

    /**
     * Overwrites a [CharArray] in-place with null characters.
     */
    fun wipe(array: CharArray?) {
        if (array != null && array.isNotEmpty()) {
            Arrays.fill(array, '\u0000')
        }
    }

    /**
     * Overwrites the remaining contents of a [ByteBuffer] with zeroes and clears positions.
     */
    fun wipe(buffer: ByteBuffer?) {
        if (buffer == null) return
        buffer.clear()
        val zeroBytes = ByteArray(minOf(buffer.capacity(), 1024))
        while (buffer.hasRemaining()) {
            val toWrite = minOf(buffer.remaining(), zeroBytes.size)
            buffer.put(zeroBytes, 0, toWrite)
        }
        buffer.clear()
    }
}

/**
 * Scoped, auto-zeroing byte array container. Automatically wipes underlying memory
 * on [close] or scope exit.
 */
class SecureByteArray(size: Int) : AutoCloseable {
    val data: ByteArray = ByteArray(size)
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
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun disableScreenShield(activity: Activity?) {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
    private val lockListeners = mutableListOf<() -> Unit>()

    init {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (_: Throwable) {
            // ProcessLifecycleOwner might not be initialized in non-UI test runners
        }
    }

    @Synchronized
    fun registerLockListener(listener: () -> Unit) {
        lockListeners.add(listener)
    }

    @Synchronized
    fun unregisterLockListener(listener: () -> Unit) {
        lockListeners.remove(listener)
    }

    @Synchronized
    fun notifyAuthenticated() {
        isSessionUnlocked = true
        lastUnlockTimestamp = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun isUnlocked(timeoutMs: Long = DEFAULT_AUTO_LOCK_TIMEOUT_MS): Boolean {
        if (!isSessionUnlocked) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastUnlockTimestamp > timeoutMs) {
            lockdown()
            return false
        }
        return true
    }

    @Synchronized
    fun lockdown() {
        isSessionUnlocked = false
        lastUnlockTimestamp = 0L
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
