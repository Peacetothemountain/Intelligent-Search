package com.pixel.intelligentsearch.core.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Military-grade biometric gating engine for private search, hidden applications,
 * and encrypted data access.
 *
 * Enforces true hardware cryptographic gating via KeyMint StrongBox CryptoObjects,
 * window-level FLAG_SECURE visual protection, and zero-memory-leak credential scrubbing.
 *
 * Engineered by NG Designs.
 */
@Singleton
class BiometricSearchGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: StrongBoxSecurityManager,
    private val encryptedVault: EncryptedDataVault
) {
    constructor(context: Context) : this(
        context = context,
        securityManager = StrongBoxSecurityManager(context),
        encryptedVault = EncryptedDataVault(context, StrongBoxSecurityManager(context))
    )

    companion object {
        private const val TAG = "BiometricSearchGate"
    }

    private val sessionLock = SecuritySessionLock.instance

    fun getActivity(): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    fun isBiometricHardwareAvailable(): Boolean {
        return try {
            val biometricManager = context.getSystemService(Context.BIOMETRIC_SERVICE) as? BiometricManager
            val status = biometricManager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            status == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query biometric hardware availability", e)
            false
        }
    }

    fun canAuthenticateWithDeviceCredential(): Boolean {
        return try {
            val biometricManager = context.getSystemService(Context.BIOMETRIC_SERVICE) as? BiometricManager
            val status = biometricManager?.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            status == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query device credential availability", e)
            false
        }
    }

    fun isDeviceSecure(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    fun isSessionUnlocked(timeoutMs: Long = 30_000L): Boolean {
        return sessionLock.isUnlocked(timeoutMs)
    }

    fun lockSession() {
        sessionLock.lockdown()
    }

    /**
     * Executes standard or crypto-bound biometric authentication with screen shielding.
     */
    fun authenticate(
        activity: Activity,
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        title: String = "Authenticate to Access Hidden Items",
        subtitle: String? = null,
        description: String? = null,
        cancellationSignal: CancellationSignal = CancellationSignal(),
        enableScreenShield: Boolean = true,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        if (enableScreenShield) {
            WindowSecurityGuard.enableScreenShield(activity)
        }

        val executor = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        subtitle?.let { builder.setSubtitle(it) }
        description?.let { builder.setDescription(it) }

        if (cryptoObject != null) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            builder.setNegativeButton("Cancel", executor) { _, _ ->
                if (enableScreenShield && !sessionLock.isUnlocked()) {
                    WindowSecurityGuard.disableScreenShield(activity)
                }
                onCancel()
            }
        } else {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                sessionLock.notifyAuthenticated()
                onSuccess(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                super.onAuthenticationError(errorCode, errString)
                if (enableScreenShield && !sessionLock.isUnlocked()) {
                    WindowSecurityGuard.disableScreenShield(activity)
                }
                if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                ) {
                    onCancel()
                } else {
                    onError(errorCode, errString?.toString() ?: "Authentication failed ($errorCode)")
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }

        try {
            val prompt = builder.build()
            if (cryptoObject != null) {
                prompt.authenticate(cryptoObject, cancellationSignal, executor, callback)
            } else {
                prompt.authenticate(cancellationSignal, executor, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BiometricPrompt", e)
            if (enableScreenShield && !sessionLock.isUnlocked()) {
                WindowSecurityGuard.disableScreenShield(activity)
            }
            onError(-1, e.message ?: "Authentication initialization failed")
        }
    }

    /**
     * Hardware Cryptographic Gating Pipeline:
     * Unlocks an [EncryptedVaultRecord] bound to a per-operation KeyMint biometric key.
     * Guarantees that data cannot be decrypted by hooking callbacks without authenticating
     * the underlying StrongBox / TEE Cipher.
     */
    fun authenticateForCryptographicAccess(
        activity: Activity,
        record: EncryptedVaultRecord,
        profileId: String = "default",
        title: String = "Biometric Verification Required",
        onDecrypted: (SecureByteArray) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        if (!isBiometricHardwareAvailable()) {
            onError("Biometric hardware is not available on this device.")
            return
        }

        try {
            val iv = encryptedVault.extractIv(record.payload)
            val cryptoObject = securityManager.initBiometricDecryptCryptoObject(iv)

            authenticate(
                activity = activity,
                cryptoObject = cryptoObject,
                title = title,
                subtitle = "Hardware-bound KeyMint verification",
                enableScreenShield = true,
                onSuccess = { result ->
                    val cipher = result.cryptoObject?.cipher
                    if (cipher == null) {
                        onError("Hardware cipher is null after authentication.")
                        return@authenticate
                    }
                    try {
                        val secureBytes = encryptedVault.decryptWithBiometricCipher(cipher, record, profileId)
                        onDecrypted(secureBytes)
                    } catch (e: Exception) {
                        Log.e(TAG, "Biometric decryption failed", e)
                        onError("Decryption failed: ${e.message}")
                    }
                },
                onError = { _, errString -> onError(errString) },
                onCancel = onCancel
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cryptographic gate preparation failed", e)
            onError("Gate initialization error: ${e.message}")
        }
    }

    /**
     * Backward-compatible private search gate.
     */
    fun authenticateForPrivateSearch(
        activity: Activity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isDeviceSecure() && !isBiometricHardwareAvailable()) {
            onError("No secure lock screen or biometric credential configured on device.")
            return
        }

        authenticate(
            activity = activity,
            title = "Unlock Private Search",
            subtitle = "Verify identity to view hidden items",
            onSuccess = { onSuccess() },
            onError = { _, errString -> onError(errString) },
            onCancel = { onError("Authentication cancelled") }
        )
    }
}
