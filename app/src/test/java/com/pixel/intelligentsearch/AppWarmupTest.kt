package com.pixel.intelligentsearch

import android.app.Application
import android.app.UiModeManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The night-mode clean-up [App] runs at every start. A process started only for a widget or the boot receiver must not
 * pay for the toggle manager's camera, NFC and network registration just to find the clean-up already done.
 */
@RunWith(RobolectricTestRunner::class)
class AppWarmupTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val uiMode get() = context.getSystemService(UiModeManager::class.java)

    /** Whether anything built the toggle manager, whose receiver listens for airplane mode among others. */
    private fun togglesWereBuilt() =
        shadowOf(context).registeredReceivers.any { it.intentFilter.hasAction(Intent.ACTION_AIRPLANE_MODE_CHANGED) }

    @Test
    fun theFirstStartClearsAnOldOverride() {
        uiMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
        SystemToggleManager.clearLegacyNightModeOverrideIfNeeded(context)
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, shadowOf(uiMode).applicationNightMode)
        assertFalse(togglesWereBuilt())
    }

    @Test
    fun laterStartsFindItDoneAndBuildNothing() {
        SystemToggleManager.clearLegacyNightModeOverrideIfNeeded(context)
        // Set after the clean-up ran, so only a clean-up that forgot it was done would reset it.
        uiMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
        SystemToggleManager.clearLegacyNightModeOverrideIfNeeded(context)
        assertEquals(UiModeManager.MODE_NIGHT_YES, shadowOf(uiMode).applicationNightMode)
        assertFalse(togglesWereBuilt())
    }
}
