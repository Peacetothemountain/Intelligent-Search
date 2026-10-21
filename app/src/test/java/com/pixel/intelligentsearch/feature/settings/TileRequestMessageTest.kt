package com.pixel.intelligentsearch.feature.settings

import android.app.StatusBarManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tapping Search Tile always ends in visible feedback: Android's own dialog, or a message from the app. */
@RunWith(RobolectricTestRunner::class)
class TileRequestMessageTest {

    @Test
    fun aTileAlreadyAddedSaysSo() {
        assertEquals(
            "Search tile is already in Quick Settings",
            tileRequestMessage(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
        )
    }

    @Test
    fun aFailedRequestExplainsHowToAddTheTileByHand() {
        val message = tileRequestMessage(StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND)
        assertTrue(message.orEmpty(), message.orEmpty().contains("add it yourself"))
    }

    @Test
    fun androidsOwnDialogNeedsNoMessage() {
        assertNull(tileRequestMessage(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED))
        assertNull(tileRequestMessage(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED))
    }
}
