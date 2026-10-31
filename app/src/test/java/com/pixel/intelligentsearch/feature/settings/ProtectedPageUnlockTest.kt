package com.pixel.intelligentsearch.feature.settings

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.util.findActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager

/** A protected page (Manage Hidden Apps, Backup) keeps its window from other eyes while shown, and only then. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ProtectedPageUnlockTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        hilt.inject()
        // No screen lock or biometrics on this device, so the page opens without a prompt.
        Shadow.extract<ShadowBiometricManager>(context.getSystemService(BiometricManager::class.java)).setCanAuthenticate(false)
    }

    @Test
    fun onlyAssistiveToolsCanReadThePageWhileItIsShown() {
        var shown by mutableStateOf(true)
        var activity: Activity? = null
        compose.setContent {
            activity = LocalContext.current.findActivity()
            if (shown) rememberProtectedPageUnlock(title = "Unlock", subtitle = "", onBack = {})
        }
        compose.waitForIdle()
        val window = activity!!.window
        assertTrue(window.decorView.isAccessibilityDataSensitive)
        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)

        // Leaving the page gives the rest of the app back to every service.
        shown = false
        compose.waitForIdle()
        assertFalse(window.decorView.isAccessibilityDataSensitive)
        assertFalse(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
}
