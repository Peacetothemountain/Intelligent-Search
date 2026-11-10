package com.pixel.intelligentsearch.core.backup

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.pixel.intelligentsearch.di.appEntryPoint
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The passkey that protects encrypted backups. Creating it is the only time the user is asked: the key its PRF gives
 * ([BackupKeySlots.passkeyKek]) is kept here wrapped by a non-exportable Keystore key, so manual exports and the weekly
 * automatic backup encrypt without a prompt. Restoring asks for the passkey, which the user's password manager syncs
 * to their other phones, so a backup restores on any phone signed in to it.
 *
 * Kept in its own preferences file, which backups never include. Forgetting it here leaves the passkey in the password
 * manager, so backups already made with it still restore.
 */
class BackupPasskey internal constructor(
    context: Context,
    /** The Keystore key the passkey's backup key is wrapped with; tests pass a software key. */
    private val wrapKey: () -> SecretKey,
    private val prf: PasskeyPrf = CredentialManagerPasskeyPrf(),
    /** Deletes the Keystore key above, once nothing is wrapped with it any more. */
    private val forgetWrapKey: () -> Unit = {}
) {

    constructor(context: Context) : this(
        context,
        { context.appEntryPoint.strongBoxSecurityManager().getOrCreateSymmetricKey(WRAP_KEY_ALIAS, purgeIfUnrecoverable = false).first },
        forgetWrapKey = { context.appEntryPoint.strongBoxSecurityManager().deleteKey(WRAP_KEY_ALIAS) }
    )

    private val state = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)

    val isSetUp: Boolean get() = state.contains(KEY_CREDENTIAL) && state.contains(KEY_WRAPPED)

    /**
     * Whether this phone can still read the passkey's kept key, which a new backup's passkey slot needs. False for a
     * passkey that is set up but whose Keystore key was lost or replaced: only setting the passkey up again mends that.
     */
    val canWrap: Boolean get() = isSetUp && runCatching { backupKey() }.getOrNull() != null
    val createdMs: Long get() = state.getLong(KEY_CREATED, 0L)

    /**
     * Creates the backup passkey (the system's passkey sheet, then the screen lock) and keeps its backup key. Throws
     * [PasskeyException] with a message for the user when it can't.
     */
    suspend fun create(activity: Activity) {
        val userId = ByteArray(16).also(random::nextBytes)
        val prfSalt = ByteArray(32).also(random::nextBytes)
        val result = prf.create(activity, userId, prfSalt)
        keep(result.credentialId, prfSalt, result.prfOutput)
    }

    /** Forgets the passkey on this phone. Backups already made still restore with it. */
    fun forget() {
        state.edit { clear() }
        // Nothing is wrapped with the Keystore key any more. One that exists but can no longer be loaded is never
        // replaced while it protects something, so left in place it made every new passkey fail to be kept.
        runCatching(forgetWrapKey)
    }

    /** The key-slot for a new backup's [contentKey], or null when no passkey is set up (or its key can't be read). */
    fun slotFor(contentKey: ByteArray): KeySlot? {
        val credential = state.getString(KEY_CREDENTIAL, null) ?: return null
        val prfSalt = state.getString(KEY_PRF_SALT, null) ?: return null
        val kek = try {
            backupKey()
        } catch (e: Exception) {
            Log.w(TAG, "The backup passkey's key can't be read: ${e.javaClass.simpleName}")
            return null
        } ?: return null
        val (iv, wrapped) = BackupKeySlots.wrap(contentKey, kek, KeySlot.TYPE_PASSKEY)
        return KeySlot(
            type = KeySlot.TYPE_PASSKEY,
            wrapIvBase64 = BackupCryptoEngine.encodeBase64(iv),
            wrappedKeyBase64 = BackupCryptoEngine.encodeBase64(wrapped),
            credentialId = credential,
            rpId = RP_ID,
            prfSaltBase64 = prfSalt
        )
    }

    /**
     * The key that opens [slot]: this phone's kept key when the slot is this phone's passkey, otherwise asked of the
     * passkey through the system sheet. Throws [PasskeyException] with a message for the user.
     */
    suspend fun unlock(activity: Activity, slot: KeySlot): SecretKey {
        val credential = slot.credentialId ?: throw PasskeyException("This backup's passkey details are missing.")
        val prfSalt = slot.prfSaltBase64?.let(BackupCryptoEngine::decodeBase64) ?: throw PasskeyException("This backup's passkey details are missing.")
        if (slot.rpId != null && slot.rpId != RP_ID) throw PasskeyException("This backup was made with a passkey for ${slot.rpId.take(60)}.")
        // This phone's own passkey: its key is already kept here, behind the page's own unlock.
        if (credential == state.getString(KEY_CREDENTIAL, null)) {
            runCatching { backupKey() }.getOrNull()?.let { return it }
        }
        val result = prf.evaluate(activity, listOf(credential), prfSalt)
        val output = result.prfOutput
        return try {
            BackupKeySlots.passkeyKek(output)
        } finally {
            output.fill(0)
        }
    }

    private fun keep(credentialId: String, prfSalt: ByteArray, prfOutput: ByteArray) {
        val kek = try {
            BackupKeySlots.passkeyKek(prfOutput)
        } finally {
            prfOutput.fill(0)
        }
        val raw = kek.encoded
        // A Keystore key left by an earlier Remove, wrapping nothing now: one that exists but can't be loaded is never
        // replaced while it protects something, so it would refuse every new passkey.
        if (!state.contains(KEY_WRAPPED)) runCatching(forgetWrapKey)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
            val wrapped = cipher.doFinal(raw)
            state.edit {
                putString(KEY_CREDENTIAL, credentialId)
                putString(KEY_PRF_SALT, BackupCryptoEngine.encodeBase64(prfSalt))
                putString(KEY_WRAPPED, BackupCryptoEngine.encodeBase64(wrapped))
                putString(KEY_WRAP_IV, BackupCryptoEngine.encodeBase64(cipher.iv))
                putLong(KEY_CREATED, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            throw PasskeyException("The passkey was made, but this phone couldn't protect its key. Try again.", cause = e)
        } finally {
            raw.fill(0)
        }
    }

    private fun backupKey(): SecretKey? {
        val wrapped = state.getString(KEY_WRAPPED, null)?.let(BackupCryptoEngine::decodeBase64) ?: return null
        val iv = state.getString(KEY_WRAP_IV, null)?.let(BackupCryptoEngine::decodeBase64) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, iv))
        val raw = cipher.doFinal(wrapped)
        return SecretKeySpec(raw, "AES").also { raw.fill(0) }
    }

    companion object {
        private const val TAG = "BackupPasskey"

        /** What the user is told when the passkey's kept key can't be read ([canWrap]). */
        const val UNREADABLE = "This phone can't read its backup passkey's key. Set up the passkey again."

        /**
         * The website passkeys are made for. Its /.well-known/assetlinks.json names this app's signing certificates,
         * which is how the password manager knows the app may use passkeys for it. Changing it orphans every passkey
         * made so far, so it never changes.
         */
        const val RP_ID = "intelligentsearchapp.github.io"

        private const val STATE_FILE = "backup_passkey_state"
        private const val WRAP_KEY_ALIAS = "backup_passkey_wrap_key"
        private const val KEY_CREDENTIAL = "credential_id"
        private const val KEY_PRF_SALT = "prf_salt"
        private const val KEY_WRAPPED = "wrapped_key"
        private const val KEY_WRAP_IV = "wrapped_key_iv"
        private const val KEY_CREATED = "created_ms"

        private val random = SecureRandom()
    }
}
