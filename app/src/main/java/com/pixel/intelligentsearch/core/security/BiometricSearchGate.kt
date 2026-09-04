package com.pixel.intelligentsearch.core.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.util.Log

class BiometricSearchGate(private val context: Context) {

    companion object {
        private const val TAG = "BiometricSearchGate"
    }

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

    fun authenticate(
        activity: Activity,
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        title: String = "Authenticate to Access Hidden Apps",
        subtitle: String? = null,
        description: String? = null,
        cancellationSignal: CancellationSignal = CancellationSignal(),
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val executor = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        subtitle?.let { builder.setSubtitle(it) }
        description?.let { builder.setDescription(it) }

        if (cryptoObject != null) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            builder.setNegativeButton("Cancel", executor) { _, _ -> onCancel() }
        } else {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                super.onAuthenticationError(errorCode, errString)
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
            onError(-1, e.message ?: "Authentication initialization failed")
        }
    }

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
