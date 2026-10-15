package com.pixel.intelligentsearch.core.security

import java.util.Arrays

/**
 * Zeroes byte and char arrays that held key material or passphrases once they are no longer needed.
 * This clears only the arrays passed in; copies held elsewhere (for example in immutable Strings or by
 * the garbage collector) are outside its reach.
 */
object MemorySanitizer {

    /**
     * Observable sink written after every wipe. The volatile store (which depends on a value read
     * back from the wiped region) makes the zeroing an observable side effect, so neither R8 nor the
     * ART optimizing compiler can treat the fill as a dead store and drop it.
     */
    @Volatile
    private var wipeSink: Int = 0

    /** Overwrites a [ByteArray] in place with zeroes. */
    fun wipe(array: ByteArray?) {
        if (array != null && array.isNotEmpty()) {
            Arrays.fill(array, 0.toByte())
            wipeSink = array[0].toInt()
        }
    }

    /** Overwrites a [CharArray] in place with null characters. */
    fun wipe(array: CharArray?) {
        if (array != null && array.isNotEmpty()) {
            Arrays.fill(array, '\u0000')
            wipeSink = array[0].code
        }
    }
}
