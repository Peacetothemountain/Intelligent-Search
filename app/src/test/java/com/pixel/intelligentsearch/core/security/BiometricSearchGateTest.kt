package com.pixel.intelligentsearch.core.security

import android.app.Application
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager

/** Protected pages only offer a prompt that can actually be shown. */
@RunWith(RobolectricTestRunner::class)
class BiometricSearchGateTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val gate = BiometricSearchGate(context)
    private val keyguard = shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
    private val biometrics = Shadow.extract<ShadowBiometricManager>(context.getSystemService(Context.BIOMETRIC_SERVICE) as BiometricManager)

    @Test
    fun aScreenLockIsEnoughToPrompt() {
        keyguard.setIsDeviceSecure(true)
        biometrics.setCanAuthenticate(false)
        assertTrue(gate.isDeviceSecure())
        assertTrue(gate.canPrompt())
    }

    @Test
    fun aStrongBiometricIsEnoughToPrompt() {
        keyguard.setIsDeviceSecure(false)
        biometrics.setCanAuthenticate(true)
        assertFalse(gate.isDeviceSecure())
        assertTrue(gate.canPrompt())
    }

    @Test
    fun neitherLeavesNoPrompt() {
        keyguard.setIsDeviceSecure(false)
        biometrics.setCanAuthenticate(false)
        assertFalse(gate.canPrompt())
    }
}
