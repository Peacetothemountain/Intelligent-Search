package com.pixel.intelligentsearch.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

/** The key-slot scheme: HKDF matches RFC 5869, and a slot opens only with its own key and as its own type. */
class BackupKeySlotsTest {

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun hkdfMatchesRfc5869TestCases() {
        val ikm = ByteArray(22) { 0x0b }
        // Test case 1.
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            BackupKeySlots.hkdfSha256(ikm, hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42).hex()
        )
        // Test case 3: no salt, no info.
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            BackupKeySlots.hkdfSha256(ikm, ByteArray(0), ByteArray(0), 42).hex()
        )
    }

    @Test
    fun aSlotOpensWithItsKeyOnly() {
        val contentKey = BackupKeySlots.newContentKey()
        val kek = BackupKeySlots.passkeyKek(ByteArray(32) { 7 })
        val (iv, wrapped) = BackupKeySlots.wrap(contentKey, kek, KeySlot.TYPE_PASSKEY)
        val slot = KeySlot(KeySlot.TYPE_PASSKEY, BackupCryptoEngine.encodeBase64(iv), BackupCryptoEngine.encodeBase64(wrapped))

        assertArrayEquals(contentKey, BackupKeySlots.unwrap(slot, kek))
        assertThrows(AEADBadTagException::class.java) {
            BackupKeySlots.unwrap(slot, BackupKeySlots.passkeyKek(ByteArray(32) { 8 }))
        }
        // The slot's type is authenticated: relabeling a passkey slot as a passphrase slot breaks it.
        assertThrows(AEADBadTagException::class.java) {
            BackupKeySlots.unwrap(slot.copy(type = KeySlot.TYPE_PASSPHRASE), kek)
        }
    }

    @Test
    fun thePasskeyKeyIsDerivedNotThePrfOutputItself() {
        val prf = ByteArray(32) { it.toByte() }
        val kek = BackupKeySlots.passkeyKek(prf)
        assertEquals(32, kek.encoded.size)
        assertFalse(kek.encoded.contentEquals(prf))
        assertArrayEquals("same PRF output, same key", kek.encoded, BackupKeySlots.passkeyKek(prf.copyOf()).encoded)
        assertThrows(IllegalArgumentException::class.java) { BackupKeySlots.passkeyKek(ByteArray(16)) }
    }
}
