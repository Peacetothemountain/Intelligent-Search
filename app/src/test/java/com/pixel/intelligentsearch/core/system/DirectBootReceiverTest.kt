package com.pixel.intelligentsearch.core.system

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DirectBootReceiverTest {

    @Test
    fun marksUnlockedAndRedrawsWidgetsOnlyOnBootCompleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directBootManager = DirectBootManager(context)
        var redraws = 0
        val receiver = DirectBootReceiver().apply {
            this.directBootManager = directBootManager
            widgetUpdater = WidgetUpdater { redraws++ }
        }
        // Mark the Hilt base class as injected so the test needs no Hilt component.
        Hilt_DirectBootReceiver::class.java.getDeclaredField("injected").apply { isAccessible = true }.set(receiver, true)

        receiver.onReceive(context, Intent())
        receiver.onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(0, redraws)

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, redraws)
        assertTrue(directBootManager.isUserUnlocked.value)
    }
}
