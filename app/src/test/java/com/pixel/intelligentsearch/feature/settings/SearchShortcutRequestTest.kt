package com.pixel.intelligentsearch.feature.settings

import android.app.Application
import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Home Screen Shortcut asks the launcher to pin Search, and sends nothing any other app could receive. */
@RunWith(RobolectricTestRunner::class)
class SearchShortcutRequestTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val shortcuts get() = app.getSystemService(ShortcutManager::class.java)

    @Test
    fun theShortcutIsPinnedWithoutAResultBroadcast() {
        shadowOf(shortcuts).setIsRequestPinShortcutSupported(true)
        assertTrue(requestSearchShortcut(app))
        assertEquals(listOf("intelligent_search_main"), shortcuts.pinnedShortcuts.map { it.id })
        // The launcher's own dialog is the feedback; a result broadcast would go to whichever app listened for it.
        assertEquals(emptyList<android.content.Intent>(), shadowOf(app).broadcastIntents)
    }

    @Test
    fun aLauncherThatCantPinSaysSo() {
        shadowOf(shortcuts).setIsRequestPinShortcutSupported(false)
        assertFalse(requestSearchShortcut(app))
        assertTrue(shortcuts.pinnedShortcuts.isEmpty())
    }
}
