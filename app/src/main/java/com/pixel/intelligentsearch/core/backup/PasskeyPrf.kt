package com.pixel.intelligentsearch.core.backup

import android.app.Activity
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * A passkey's PRF (the WebAuthn "prf" extension, hmac-secret underneath): a 32-byte secret the passkey gives back for
 * a given input, the same on every phone the passkey syncs to and impossible to get without the passkey and the
 * user's screen lock. Encrypted backups wrap their key with it ([BackupKeySlots.passkeyKek]).
 */
interface PasskeyPrf {
    /** Creates a passkey for [userId] and returns its id with the PRF output for [prfSalt]. */
    suspend fun create(activity: Activity, userId: ByteArray, prfSalt: ByteArray): PasskeyPrfResult
    /** Asks the user for one of [credentialIds] and returns the PRF output for [prfSalt]. */
    suspend fun evaluate(activity: Activity, credentialIds: List<String>, prfSalt: ByteArray): PasskeyPrfResult
}

/** A passkey's id (base64url) and its PRF output for the salt asked about. */
class PasskeyPrfResult(val credentialId: String, val prfOutput: ByteArray)

/** Why a passkey couldn't be used, worded for the user. */
class PasskeyException(message: String, val cancelled: Boolean = false, cause: Throwable? = null) : Exception(message, cause)

/** [PasskeyPrf] through Android's Credential Manager (Android 14+), with the passkey in the user's password manager. */
class CredentialManagerPasskeyPrf : PasskeyPrf {

    override suspend fun create(activity: Activity, userId: ByteArray, prfSalt: ByteArray): PasskeyPrfResult {
        val response = try {
            CredentialManager.create(activity).createCredential(activity, CreatePublicKeyCredentialRequest(PasskeyRequests.creation(userId, prfSalt)))
        } catch (e: CreateCredentialCancellationException) {
            throw PasskeyException("Passkey setup was canceled.", cancelled = true, cause = e)
        } catch (e: CreateCredentialException) {
            throw PasskeyException("Couldn't create a passkey: ${e.errorMessage ?: e.type}", cause = e)
        }
        val json = (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
            ?: throw PasskeyException("Your password manager didn't return a passkey.")
        val created = PasskeyRequests.parseRegistration(json)
        if (!created.prfEnabled) {
            throw PasskeyException(
                "Your password manager can't encrypt with passkeys. Create it in Google Password Manager instead. You can delete the passkey it just saved."
            )
        }
        // Some password managers give the PRF output only when the passkey is used, not when it's made.
        return created.prfOutput?.let { PasskeyPrfResult(created.credentialId, it) }
            ?: evaluate(activity, listOf(created.credentialId), prfSalt)
    }

    override suspend fun evaluate(activity: Activity, credentialIds: List<String>, prfSalt: ByteArray): PasskeyPrfResult {
        val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(PasskeyRequests.assertion(credentialIds, prfSalt))))
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw PasskeyException("Passkey check was canceled.", cancelled = true, cause = e)
        } catch (e: NoCredentialException) {
            throw PasskeyException(
                "This backup's passkey isn't on this phone. Sign in to the password manager that holds it, or use the passphrase.",
                cause = e
            )
        } catch (e: GetCredentialException) {
            throw PasskeyException("Couldn't use the passkey: ${e.errorMessage ?: e.type}", cause = e)
        }
        val json = (credential as? PublicKeyCredential)?.authenticationResponseJson
            ?: throw PasskeyException("Your password manager didn't return a passkey.")
        return PasskeyRequests.parseAssertion(json)
            ?: throw PasskeyException("Your password manager can't encrypt with passkeys. Use Google Password Manager.")
    }

    companion object {
        /** Credential Manager's passkeys need Android 14 here: older versions would need the Play services library. */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}

/** The WebAuthn JSON sent to and read back from Credential Manager. Pure, so it is unit tested. */
internal object PasskeyRequests {

    private val random = SecureRandom()

    /** What the user sees in their password manager. */
    const val USER_NAME = "Intelligent Search Encrypted Backup"

    class Registration(val credentialId: String, val prfEnabled: Boolean, val prfOutput: ByteArray?)

    fun creation(userId: ByteArray, prfSalt: ByteArray): String = JSONObject().apply {
        put("challenge", b64url(challenge()))
        put("rp", JSONObject().put("id", BackupPasskey.RP_ID).put("name", "Intelligent Search"))
        put("user", JSONObject().put("id", b64url(userId)).put("name", USER_NAME).put("displayName", USER_NAME))
        put("pubKeyCredParams", JSONArray().put(JSONObject().put("type", "public-key").put("alg", -7))
            .put(JSONObject().put("type", "public-key").put("alg", -257)))
        put("timeout", 180_000)
        put("attestation", "none")
        // A discoverable passkey that needs the screen lock each time, synced by the password manager.
        put("authenticatorSelection", JSONObject().put("residentKey", "required").put("requireResidentKey", true)
            .put("userVerification", "required"))
        put("extensions", prfExtension(prfSalt))
    }.toString()

    fun assertion(credentialIds: List<String>, prfSalt: ByteArray): String = JSONObject().apply {
        put("challenge", b64url(challenge()))
        put("rpId", BackupPasskey.RP_ID)
        put("timeout", 180_000)
        put("userVerification", "required")
        put("allowCredentials", JSONArray().apply {
            credentialIds.forEach { put(JSONObject().put("type", "public-key").put("id", it)) }
        })
        put("extensions", prfExtension(prfSalt))
    }.toString()

    fun parseRegistration(json: String): Registration {
        val root = JSONObject(json)
        val prf = root.optJSONObject("clientExtensionResults")?.optJSONObject("prf")
        val output = prf?.optJSONObject("results")?.optString("first")?.takeIf { it.isNotBlank() }?.let(::fromB64url)
        return Registration(
            credentialId = root.getString("id"),
            // A password manager that returned an output plainly supports PRF, even if it left "enabled" out.
            prfEnabled = prf?.optBoolean("enabled", false) == true || output != null,
            prfOutput = output
        )
    }

    /** The passkey used and its PRF output, or null when the password manager gave no PRF output. */
    fun parseAssertion(json: String): PasskeyPrfResult? {
        val root = JSONObject(json)
        val first = root.optJSONObject("clientExtensionResults")?.optJSONObject("prf")?.optJSONObject("results")
            ?.optString("first")?.takeIf { it.isNotBlank() } ?: return null
        return PasskeyPrfResult(root.getString("id"), fromB64url(first))
    }

    private fun prfExtension(prfSalt: ByteArray) =
        JSONObject().put("prf", JSONObject().put("eval", JSONObject().put("first", b64url(prfSalt))))

    // Nothing checks the signature (there is no server); the PRF output is what matters, and only the passkey can make it.
    private fun challenge(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun b64url(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun fromB64url(text: String): ByteArray = java.util.Base64.getUrlDecoder().decode(text.trim().trimEnd('='))
}
