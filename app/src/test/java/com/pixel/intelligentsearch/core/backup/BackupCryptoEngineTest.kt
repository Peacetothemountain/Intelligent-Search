package com.pixel.intelligentsearch.core.backup

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupCryptoEngineTest {

    @Test
    fun testPassphraseEncryptionDecryptionRoundTrip() {
        val passphrase = "SuperSecretPassword123!".toCharArray()
        val salt = BackupCryptoEngine.generateRandomSalt()
        val iv = BackupCryptoEngine.generateRandomIv()
        val key = BackupCryptoEngine.deriveKeyFromPassphrase(passphrase, salt)

        val testPayload = """{"schemaVersion":2,"testSetting":true,"colorHue":277}"""
        val encryptedBytes = BackupCryptoEngine.encryptPayload(testPayload, key, iv)
        assertNotNull(encryptedBytes)
        assertTrue(encryptedBytes.isNotEmpty())

        val decryptedText = BackupCryptoEngine.decryptPayload(encryptedBytes, key, iv)
        assertEquals(testPayload, decryptedText)
    }

    @Test
    fun testIncorrectPassphraseFailsDecryption() {
        val correctPass = "CorrectPassword123".toCharArray()
        val wrongPass = "WrongPassword456".toCharArray()
        val salt = BackupCryptoEngine.generateRandomSalt()
        val iv = BackupCryptoEngine.generateRandomIv()

        val correctKey = BackupCryptoEngine.deriveKeyFromPassphrase(correctPass, salt)
        val wrongKey = BackupCryptoEngine.deriveKeyFromPassphrase(wrongPass, salt)

        val testPayload = """{"sensitiveData":"Restricted"}"""
        val encryptedBytes = BackupCryptoEngine.encryptPayload(testPayload, correctKey, iv)

        try {
            BackupCryptoEngine.decryptPayload(encryptedBytes, wrongKey, iv)
            fail("Decryption with incorrect key should throw AEADBadTagException")
        } catch (e: Exception) {
            assertTrue(
                "Expected AEADBadTagException or BadPaddingException, got: ${e::class.java.simpleName}",
                e is AEADBadTagException || e.cause is AEADBadTagException || e.message?.contains("tag", ignoreCase = true) == true
            )
        }
    }

    @Test
    fun testSha256Integrity() {
        val data = "IntelligentSearchBackupVerificationPayload".toByteArray(Charsets.UTF_8)
        val sha1 = BackupCryptoEngine.calculateSha256(data)
        val sha2 = BackupCryptoEngine.calculateSha256(data)

        assertEquals(sha1, sha2)
        assertEquals(64, sha1.length) // 256 bits = 64 hex chars
    }

    @Test
    fun testBase64RoundTrip() {
        val raw = "TestBase64Serialization".toByteArray(Charsets.UTF_8)
        val encoded = BackupCryptoEngine.encodeBase64(raw)
        val decoded = BackupCryptoEngine.decodeBase64(encoded)

        assertArrayEquals(raw, decoded)
    }

    @Test
    fun testHardwareBackedEnvelopeMetadata() {
        val envelope = EncryptedBackupEnvelope(
            isHardwareBacked = true,
            hardwareChip = "Google Titan M3",
            deviceModel = "Google Pixel 11 Pro XL",
            kdf = KdfMetadata(saltBase64 = "dGVzdFNhbHQ="),
            cipher = CipherMetadata(ivBase64 = "dGVzdElWMTIzNA=="),
            encryptedPayloadBase64 = "ZW5jcnlwdGVkRGF0YQ==",
            payloadSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )

        assertTrue(envelope.isHardwareBacked)
        assertEquals("Google Titan M3", envelope.hardwareChip)
        assertEquals("Google Pixel 11 Pro XL", envelope.deviceModel)
        assertEquals("INTELLIGENT_SEARCH_ENCRYPTED_BACKUP", envelope.format)
        assertEquals(1, envelope.schemaVersion)
    }

    @Test
    fun testLegacyIterationBackupsStillDecrypt() {
        val passphrase = "LegacyPassphrase!".toCharArray()
        val salt = BackupCryptoEngine.generateRandomSalt()
        val iv = BackupCryptoEngine.generateRandomIv()
        val legacyKey = BackupCryptoEngine.deriveKeyFromPassphrase(passphrase, salt, BackupCryptoEngine.LEGACY_PBKDF2_ITERATIONS)
        val cipher = BackupCryptoEngine.encryptPayload("""{"legacy":true}""", legacyKey, iv)

        val reDerived = BackupCryptoEngine.deriveKeyFromPassphrase(passphrase, salt, BackupCryptoEngine.LEGACY_PBKDF2_ITERATIONS)
        assertEquals("""{"legacy":true}""", BackupCryptoEngine.decryptPayload(cipher, reDerived, iv))

        val modernKey = BackupCryptoEngine.deriveKeyFromPassphrase(passphrase, salt)
        assertThrows(AEADBadTagException::class.java) {
            BackupCryptoEngine.decryptPayload(cipher, modernKey, iv)
        }
    }

    @Test
    fun testOutOfRangeIterationCountRejected() {
        val salt = BackupCryptoEngine.generateRandomSalt()
        assertThrows(IllegalArgumentException::class.java) {
            BackupCryptoEngine.deriveKeyFromPassphrase("x".toCharArray(), salt, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCryptoEngine.deriveKeyFromPassphrase("x".toCharArray(), salt, Int.MAX_VALUE)
        }
    }
}
