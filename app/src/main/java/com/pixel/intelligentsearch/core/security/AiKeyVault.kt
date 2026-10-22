package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.ai.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The AI Answers API keys the user pastes in, one per provider, encrypted with an AES-256-GCM key that lives in the
 * Android Keystore (StrongBox where the phone has one), as the saved backup passphrase is. The ciphertext sits in its
 * own credential-protected preferences file, outside the settings the app's backups export and outside Android's
 * backup and device transfer (data_extraction_rules.xml excludes every preferences file). A key is decrypted only to
 * send a question the user tapped Ask on, is never logged, and Remove Key deletes it.
 *
 * Unlike the passphrase's, this Keystore key isn't bound to a recent unlock: Ask runs from the search overlay, where
 * asking for the fingerprint before every question would make the feature unusable. The phone's storage encryption and
 * the Keystore still keep the key unreadable to anything but this app.
 *
 * Engineered by NG Designs.
 */
@Singleton
class AiKeyVault internal constructor(
    private val context: Context,
    private val keys: Keys
) {

    /** The Keystore steps; tests supply a software key, since Robolectric has no AndroidKeyStore. */
    internal interface Keys {
        fun getOrCreate(): SecretKey
        /** The key if it exists; null when none was ever made (or it was deleted). */
        fun existing(): SecretKey?
        fun delete()
    }

    @Inject constructor(@ApplicationContext context: Context, security: StrongBoxSecurityManager) : this(
        context,
        object : Keys {
            override fun getOrCreate(): SecretKey = security.getOrCreateSymmetricKey(KEY_ALIAS, purgeIfUnrecoverable = false).first
            override fun existing(): SecretKey? = security.getExistingSymmetricKey(KEY_ALIAS)
            override fun delete() {
                security.deleteKey(KEY_ALIAS)
            }
        }
    )

    private val prefs get() = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Whether a key is saved for [provider], without decrypting it. */
    fun has(provider: AiProvider): Boolean = runCatching { prefs.contains(cipherName(provider)) }.getOrDefault(false)

    /** Encrypts and saves [apiKey] for [provider]; false if it couldn't be stored. */
    @Synchronized
    fun save(provider: AiProvider, apiKey: String): Boolean {
        val clean = apiKey.trim()
        if (clean.isEmpty()) return false
        val bytes = clean.toByteArray(Charsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, keys.getOrCreate())
            val encrypted = cipher.doFinal(bytes)
            val sp = prefs
            val cipherText = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            // Written at once (a Remove Key or a save right after must see it), and checked rather than assumed.
            sp.edit(commit = true) {
                putString(cipherName(provider), cipherText)
                putString(ivName(provider), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            }
            sp.getString(cipherName(provider), null) == cipherText
        } catch (e: Exception) {
            // The exception's class only: a message could quote what was being encrypted.
            Log.w(TAG, "Couldn't save the AI key: ${e.javaClass.simpleName}")
            false
        } finally {
            MemorySanitizer.wipe(bytes)
        }
    }

    /** The saved key for [provider], decrypted, or null when there is none or it can't be read. */
    @Synchronized
    fun get(provider: AiProvider): String? {
        var decrypted: ByteArray? = null
        return try {
            val cipherText = prefs.getString(cipherName(provider), null) ?: return null
            val iv = prefs.getString(ivName(provider), null) ?: return null
            val key = keys.existing() ?: return null
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            decrypted = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP))
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the AI key: ${e.javaClass.simpleName}")
            null
        } finally {
            MemorySanitizer.wipe(decrypted)
        }
    }

    /** Remove Key: forgets [provider]'s key; once no provider has one, the Keystore key goes too. */
    @Synchronized
    fun remove(provider: AiProvider): Boolean {
        prefs.edit(commit = true) {
            remove(cipherName(provider))
            remove(ivName(provider))
        }
        if (AiProvider.entries.none(::has)) keys.delete()
        return !has(provider)
    }

    /** The key as the settings page shows it: its last four characters, the rest masked. */
    fun masked(provider: AiProvider): String? = get(provider)?.let(::mask)

    companion object {
        private const val TAG = "AiKeyVault"
        private const val KEY_ALIAS = "com.pixel.intelligentsearch.vault.ai_answers_v1"
        /** Credential-protected storage only, never read before the first unlock. */
        internal const val PREFS_FILE = "ai_answers_vault"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private fun cipherName(provider: AiProvider) = "ai_key_${provider.id}_cipher"
        private fun ivName(provider: AiProvider) = "ai_key_${provider.id}_iv"

        /** "••••••••abcd": never more than the last four characters, and nothing of a short key. */
        fun mask(key: String): String {
            val tail = if (key.length >= 12) key.takeLast(4) else ""
            return "•".repeat(8) + tail
        }
    }
}
