package com.pixel.intelligentsearch.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference

/** [rememberAnimationsEnabled] follows Remove animations as it changes, not only when it is first read. */
@RunWith(RobolectricTestRunner::class)
class ReducedMotionTest {

    @get:Rule val compose = createComposeRule()

    @Test
    @Config(sdk = [33])
    fun followsTheAnimatorDurationScaleItself() {
        lateinit var enabled: State<Boolean>
        compose.setContent { enabled = rememberAnimationsEnabled() }
        compose.waitForIdle()
        assertTrue(enabled.value)

        // The scale changes without the setting's content URI being notified.
        compose.runOnIdle { setDurationScaleLikeThePlatform(0f) }
        compose.waitForIdle()

        assertFalse(enabled.value)
    }

    /**
     * What Android 13's ValueAnimator.setDurationScale does: store the scale, then tell the registered listeners.
     * Robolectric's version only stores it.
     */
    private fun setDurationScaleLikeThePlatform(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
        val field = ValueAnimator::class.java.getDeclaredField("sDurationScaleChangeListeners").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val listeners = (field.get(null) as List<WeakReference<ValueAnimator.DurationScaleChangeListener>>).toList()
        listeners.forEach { it.get()?.onChanged(scale) }
    }

    @Test
    @Config(sdk = [32])
    fun followsTheSettingBeforeAndroid13() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        lateinit var enabled: State<Boolean>
        compose.setContent { enabled = rememberAnimationsEnabled() }
        compose.waitForIdle()
        assertTrue(enabled.value)

        // The setting changes and is announced before the animator's own scale has caught up.
        compose.runOnIdle {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        }
        compose.waitForIdle()

        assertFalse(enabled.value)
    }
}
