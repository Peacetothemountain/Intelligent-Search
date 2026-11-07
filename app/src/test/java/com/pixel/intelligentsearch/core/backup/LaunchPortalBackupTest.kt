package com.pixel.intelligentsearch.core.backup

import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Launch Portal's Notification Shortcut and floating button each need a permission, so they start off. */
@RunWith(RobolectricTestRunner::class)
class LaunchPortalBackupTest {

    private val optIns = listOf(SettingsManager.NOTIFICATION_SHORTCUT, SettingsManager.FLOATING_SEARCH_BUTTON)

    @Test
    fun theyAreOffUntilTheUserSwitchesThemOn() {
        for (key in optIns) assertEquals(key.name, false, key.default)
    }
}
