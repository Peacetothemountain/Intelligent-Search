package com.pixel.intelligentsearch.core.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        MemorySanitizer.wipe(null as ByteBuffer?)
        MemorySanitizer.wipe(null as CharBuffer?)
        MemorySanitizer.wipe(ByteArray(0))
        MemorySanitizer.wipe(CharArray(0))
    }

    @Test
    fun wipeDirectByteBufferZeroesWholeBufferAndResetsPosition() {
        val buffer = ByteBuffer.allocateDirect(8)
        buffer.put(byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9))
        buffer.position(3)
        MemorySanitizer.wipe(buffer)
        assertEquals(0, buffer.position())
        assertEquals(8, buffer.limit())
        for (i in 0 until 8) assertEquals(0.toByte(), buffer.get(i))
    }

    @Test
    fun wipeSlicedByteBufferOnlyTouchesItsOwnWindow() {
        val backing = ByteArray(10) { 7 }
        val slice = ByteBuffer.wrap(backing, 4, 3).slice() // arrayOffset = 4, capacity = 3
        assertEquals(4, slice.arrayOffset())

        MemorySanitizer.wipe(slice)

        val expected = ByteArray(10) { 7 }
        expected[4] = 0
        expected[5] = 0
        expected[6] = 0
        assertArrayEquals(expected, backing)
    }

    @Test
    fun wipeSlicedCharBufferOnlyTouchesItsOwnWindow() {
        val backing = CharArray(10) { 'x' }
        val slice = CharBuffer.wrap(backing, 2, 4).slice() // arrayOffset = 2, capacity = 4
        assertEquals(2, slice.arrayOffset())

        MemorySanitizer.wipe(slice)

        val expected = CharArray(10) { 'x' }
        for (i in 2..5) expected[i] = '\u0000'
        assertArrayEquals(expected, backing)
    }

    @Test
    fun wipeReadOnlyBufferIsIgnoredWithoutThrowing() {
        val backing = byteArrayOf(1, 2, 3)
        val readOnly = ByteBuffer.wrap(backing).asReadOnlyBuffer()
        MemorySanitizer.wipe(readOnly)
        assertArrayEquals(byteArrayOf(1, 2, 3), backing)
    }

    @Test
    fun secureByteArrayCloseZeroizesAndBlocksFurtherAccess() {
        val source = byteArrayOf(5, 6, 7)
        val secure = SecureByteArray(source)
        val view = secure.data
        assertEquals(6.toByte(), secure[1])

        secure.close()

        assertArrayEquals(ByteArray(3), view)
        assertThrows(IllegalStateException::class.java) { secure[0] }
        // Closing twice is harmless and the source copy is untouched.
        secure.close()
        assertArrayEquals(byteArrayOf(5, 6, 7), source)
    }

    @Test
    fun secureCharArrayCloseZeroizesAndBlocksFurtherAccess() {
        val secure = SecureCharArray("pw".toCharArray())
        val view = secure.data
        assertEquals('p', secure[0])

        secure.close()

        assertArrayEquals(CharArray(2), view)
        assertThrows(IllegalStateException::class.java) { secure[0] }
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
