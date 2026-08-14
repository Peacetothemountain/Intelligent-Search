package com.pixel.intelligentsearch.core.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

class BiometricSearchGate(private val context: Context) {

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val biometricManager = context.getSystemService(Context.BIOMETRIC_SERVICE) as? BiometricManager
                val status = biometricManager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                status == BiometricManager.BIOMETRIC_SUCCESS
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    fun isDeviceSecure(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    fun authenticateForPrivateSearch(
        activity: Activity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isBiometricHardwareAvailable()) {
            val cancellationSignal = CancellationSignal()
            val executor = activity.mainExecutor

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString?.toString() ?: "Authentication Error")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric authentication failed.")
                }
            }

            try {
                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle("Authenticate to Access Hidden Apps")
                    .setNegativeButton("Cancel", executor) { _, _ -> onError("Cancelled") }
                    .build()

                prompt.authenticate(cancellationSignal, executor, callback)
            } catch (e: Exception) {
                e.printStackTrace()
                if (isDeviceSecure()) {
                    val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    val intent = keyguardManager?.createConfirmDeviceCredentialIntent(
                        "Unlock Hidden Apps",
                        "Enter device PIN, Pattern, or Password"
                    )
                    if (intent != null) {
                        activity.startActivity(intent)
                    } else {
                        onSuccess()
                    }
                } else {
                    onSuccess()
                }
            }
        } else if (isDeviceSecure()) {
            val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val intent = keyguardManager?.createConfirmDeviceCredentialIntent(
                "Unlock Hidden Apps",
                "Enter device PIN, Pattern, or Password"
            )
            if (intent != null) {
                activity.startActivity(intent)
            } else {
                onSuccess()
            }
        } else {
            onSuccess()
        }
    }
}
