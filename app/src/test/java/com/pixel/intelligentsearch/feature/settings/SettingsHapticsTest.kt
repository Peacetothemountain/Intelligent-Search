package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSystemVibrator
import org.robolectric.shadows.ShadowViewRootImpl

/**
 * Every tap gives exactly one haptic, whichever way a control was written: a reset button that adds its own
 * click haptic must not also get the wrapper's, and Back arrows get the wrapper's predictive-back haptic.
 * The test vibrator supports every primitive, so each haptic ends in one vibration or one view haptic; both are
 * counted.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(
    application = HiltTestApplication::class,
    qualifiers = "w412dp-h2600dp-xhdpi",
    shadows = [SettingsHapticsTest.CountingVibrator::class, SettingsHapticsTest.CountingViewRoot::class]
)
class SettingsHapticsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().put(SettingsManager.VIBRATION, true).commit()
        shadowOf(context.getSystemService(VibratorManager::class.java).defaultVibrator).setSupportedPrimitives((1..8).toList())
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { content() } }
        compose.waitForIdle()
        haptics = 0
    }

    @Test
    fun openingADropdownGivesOneHaptic() {
        show { AppearanceScreen(prefs, {}, {}) }
        compose.onNodeWithText("App Theme").performClick()
        compose.waitForIdle()
        assertEquals(1, haptics)
    }

    /** Counts every vibration the app asks the vibrator for. */
    @Implements(className = "android.os.SystemVibrator", isInAndroidSdk = false)
    class CountingVibrator : ShadowSystemVibrator() {
        @Implementation(minSdk = 31)
        protected fun vibrate(uid: Int, opPkg: String?, effect: VibrationEffect, reason: String?, attributes: VibrationAttributes) {
            haptics++
            super.vibrate(uid, opPkg, effect as Any, reason, attributes as Any)
        }
    }

    /** Counts haptic feedback performed through a view, which the window plays instead of the app's vibrator. */
    @Implements(className = "android.view.ViewRootImpl", isInAndroidSdk = false)
    class CountingViewRoot : ShadowViewRootImpl() {
        @Implementation
        protected fun performHapticFeedback(effectId: Int, always: Boolean): Boolean {
            haptics++
            return true
        }
    }

    companion object {
        @JvmStatic var haptics = 0
    }
}
