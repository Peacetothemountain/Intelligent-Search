package com.pixel.intelligentsearch.core.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks for the user's fingerprint, face or screen lock before protected content (hidden apps, the
 * backup screen) is shown, and blocks screenshots of that window while the session is unlocked.
 *
 * Engineered by NG Designs.
 */
@Singleton
class BiometricSearchGate @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BiometricSearchGate"
    }

    private val sessionLock = SecuritySessionLock.instance

    @Volatile
    private var shieldedActivityRef: WeakReference<Activity>? = null

    init {
        // When the session locks (app backgrounded or timed out), lift the screenshot block again.
        sessionLock.registerLockListener {
            val activity = shieldedActivityRef?.get()
            shieldedActivityRef = null
            if (activity != null) {
                Handler(Looper.getMainLooper()).post { WindowSecurityGuard.disableScreenShield(activity) }
            }
        }
    }

    /** Whether the device has a screen lock (PIN, pattern, password), which biometrics also require. */
    fun isDeviceSecure(): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    /** Whether a prompt can be shown at all: there is a screen lock or an enrolled strong biometric. */
    fun canPrompt(): Boolean = isDeviceSecure() || try {
        (context.getSystemService(Context.BIOMETRIC_SERVICE) as? BiometricManager)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) {
        Log.w(TAG, "Failed to query biometric availability: ${e.message}")
        false
    }

    /**
     * Shows the system prompt (strong biometric or screen lock). [onSuccess] runs once the user is verified;
     * [onFailure] runs if they cancel, fail too many times, or the prompt can't be shown.
     */
    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        shieldedActivityRef = WeakReference(activity)
        WindowSecurityGuard.enableScreenShield(activity)
        val unshieldIfLocked = {
            if (!sessionLock.isUnlocked()) {
                shieldedActivityRef = null
                WindowSecurityGuard.disableScreenShield(activity)
            }
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                sessionLock.notifyAuthenticated()
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                unshieldIfLocked()
                onFailure()
            }
        }

        try {
            BiometricPrompt.Builder(activity)
                .setTitle(title)
                .apply { subtitle?.let(::setSubtitle) }
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
                .authenticate(CancellationSignal(), activity.mainExecutor, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BiometricPrompt", e)
            unshieldIfLocked()
            onFailure()
        }
    }
}
