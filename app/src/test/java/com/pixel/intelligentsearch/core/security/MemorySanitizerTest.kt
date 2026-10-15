package com.pixel.intelligentsearch.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySanitizerTest {

    @Test
    fun wipeByteArrayZeroesEveryByte() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        MemorySanitizer.wipe(data)
        assertArrayEquals(ByteArray(5), data)
    }

    @Test
    fun wipeCharArrayZeroesEveryChar() {
        val data = "secret".toCharArray()
        MemorySanitizer.wipe(data)
        assertArrayEquals(CharArray(6), data)
    }

    @Test
    fun wipeNullAndEmptyAreNoOps() {
        MemorySanitizer.wipe(null as ByteArray?)
        MemorySanitizer.wipe(null as CharArray?)
        MemorySanitizer.wipe(ByteArray(0))
        MemorySanitizer.wipe(CharArray(0))
    }

    @Test
    fun lockdownListenersMayReenterTheSessionLock() {
        val lock = SecuritySessionLock.instance
        lock.notifyAuthenticated()
        assertTrue(lock.isUnlocked())

        var observedUnlockedInsideListener: Boolean? = null
        val listener: () -> Unit = { observedUnlockedInsideListener = lock.isUnlocked() }
        lock.registerLockListener(listener)
        try {
            lock.lockdown()
        } finally {
            lock.unregisterLockListener(listener)
        }

        assertFalse(lock.isUnlocked())
        assertEquals(false, observedUnlockedInsideListener)
    }
}
