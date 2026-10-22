package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.FailingCommitContext
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager.PassphraseSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The passphrase saved on the security chip survives a re-save that doesn't finish: the working key is only
 * replaced once the new passphrase is stored.
 */
@RunWith(RobolectricTestRunner::class)
class SavedPassphraseTest {

    /** Software stand-ins for Keystore keys; while [needsAuthentication] is set they are refused like bound keys. */
    private class SoftwareKeys : StrongBoxSecurityManager.PassphraseKeys {
        val keys = mutableMapOf<String, SecretKey>()
        var needsAuthentication = false
        // A Keystore delete can fail; the key is then left behind.
        var deletesFail = false

        override fun find(alias: String): SecretKey? = keys[alias]
        override fun create(alias: String): SecretKey =
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { keys[alias] = it }
        override fun delete(alias: String) {
            if (!deletesFail) keys.remove(alias)
        }
        override fun initCipher(cipher: Cipher, mode: Int, key: SecretKey, spec: GCMParameterSpec?) {
            if (needsAuthentication) throw UserNotAuthenticatedException()
            if (spec == null) cipher.init(mode, key) else cipher.init(mode, key, spec)
        }
    }

    private val context = FailingCommitContext(ApplicationProvider.getApplicationContext<Context>())
    private val keys = SoftwareKeys()
    private val manager = StrongBoxSecurityManager(context, keys)

    @Test
    fun aReSaveThatNeedsAuthenticationKeepsTheSavedPassphrase() {
        assertEquals(PassphraseSave.SAVED, manager.savePassphrase("first passphrase 1"))

        keys.needsAuthentication = true
        assertEquals(PassphraseSave.NEEDS_AUTHENTICATION, manager.savePassphrase("second passphrase"))

        keys.needsAuthentication = false
        assertEquals("first passphrase 1", manager.getSavedPassphrase())

        // After the prompt the new one replaces it, and only its key is left.
        assertEquals(PassphraseSave.SAVED, manager.savePassphrase("second passphrase"))
        assertEquals("second passphrase", manager.getSavedPassphrase())
        assertEquals(1, keys.keys.size)
    }

    @Test
    fun aReSaveThatCantBeStoredKeepsTheSavedPassphrase() {
        assertEquals(PassphraseSave.SAVED, manager.savePassphrase("first passphrase 1"))

        context.failCommits = true
        assertEquals(PassphraseSave.FAILED, manager.savePassphrase("second passphrase"))

        assertEquals("first passphrase 1", manager.getSavedPassphrase())
        assertEquals(1, keys.keys.size)
    }

    @Test
    fun aFailedReSaveOnAnOriginalKeyInstallStillReadsItThroughThatKey() {
        saveUnderOriginalKey("first passphrase 1")

        keys.needsAuthentication = true
        assertEquals(PassphraseSave.NEEDS_AUTHENTICATION, manager.savePassphrase("second passphrase"))

        keys.needsAuthentication = false
        assertEquals("first passphrase 1", manager.getSavedPassphrase())
        // Reading it moved it onto a bound key and retired the original one.
        assertFalse(V1_ALIAS in keys.keys)
        assertEquals("first passphrase 1", manager.getSavedPassphrase())
    }

    @Test
    fun aKeyLeftBehindByAFailedReSaveNeverHidesTheOriginalKey() {
        saveUnderOriginalKey("first passphrase 1")

        keys.needsAuthentication = true
        keys.deletesFail = true
        assertEquals(PassphraseSave.NEEDS_AUTHENTICATION, manager.savePassphrase("second passphrase"))
        assertTrue("the new key was left behind", keys.keys.size > 1)

        keys.needsAuthentication = false
        keys.deletesFail = false
        assertEquals("first passphrase 1", manager.getSavedPassphrase())
    }

    @Test
    fun clearingForgetsThePassphraseAndEveryKey() {
        assertEquals(PassphraseSave.SAVED, manager.savePassphrase("first passphrase 1"))
        assertEquals(PassphraseSave.SAVED, manager.savePassphrase("second passphrase"))

        assertTrue(manager.clearSavedPassphrase())
        assertFalse(manager.hasSavedPassphrase())
        assertNull(manager.getSavedPassphrase())
        assertTrue(keys.keys.isEmpty())
    }

    /** What versions before the authentication-bound key stored: the passphrase under the original unbound key. */
    private fun saveUnderOriginalKey(passphrase: String) {
        val key = keys.create(V1_ALIAS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val encrypted = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences("secure_hardware_vault", Context.MODE_PRIVATE).edit()
            .putString("saved_passphrase_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("saved_passphrase_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }

    private companion object {
        const val V1_ALIAS = "com.pixel.intelligentsearch.vault.saved_passphrase_v1"
    }
}
