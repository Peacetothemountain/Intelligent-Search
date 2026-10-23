package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The widget editor's preview stands on the user's wallpaper colours, so a see-through bar previews over what it will
 * sit on at home rather than over the page.
 */
class WidgetWallpaperStageTest {

    @Test
    fun theStageBlendsTheWallpapersOwnColours() {
        assertEquals(
            listOf(Color(0xFF223344.toInt()), Color(0xFF556677.toInt()), Color(0xFF8899AA.toInt())),
            wallpaperStage(0xFF223344.toInt(), 0xFF556677.toInt(), 0xFF8899AA.toInt())
        )
    }

    @Test
    fun aWallpaperWithOneColourStillGivesAStage() {
        // A gradient needs two colours: one colour is used for both ends.
        assertEquals(listOf(Color(0xFF223344.toInt()), Color(0xFF223344.toInt())), wallpaperStage(0xFF223344.toInt(), null, null))
    }
}
