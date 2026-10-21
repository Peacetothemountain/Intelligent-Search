package com.pixel.intelligentsearch.feature.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The themed icons the Material G style draws for the widget's shortcuts and its circle button. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetBitmapsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val defaultPalette = themedIconColors

    @Before fun emptyTheCache() = clearBitmapCache()

    @After fun restoreThePalette() {
        themedIconColors = defaultPalette
    }

    @Test
    fun aRepeatDrawComesFromTheCacheWithoutWorkingOutThePaletteAgain() {
        var palettes = 0
        themedIconColors = { accents, light -> palettes++; defaultPalette(accents, light) }

        val first = createThemedShortcutBitmap(context, R.drawable.ic_mic, BLUE, GREY, isPillLight = false)
        val again = createThemedShortcutBitmap(context, R.drawable.ic_mic, BLUE, GREY, isPillLight = false)

        assertNotNull(first)
        assertSame(first, again)
        assertEquals(1, palettes)
    }

    @Test
    fun theCircleButtonDrawsAnIconAsAShortcutDoesSaveTheMusicNotesBolderStrokes() {
        for (icon in listOf(R.drawable.ic_lens_action, R.drawable.ic_search_lens_expressive)) {
            val shortcut = createThemedShortcutBitmap(context, icon, BLUE, GREY, isPillLight = true)!!
            val circle = createThemedActionIconBitmap(context, icon, BLUE, GREY, isPillLight = true)!!
            assertTrue(context.resources.getResourceEntryName(icon), shortcut.sameAs(circle))
        }
        val shortcutNote = createThemedShortcutBitmap(context, R.drawable.ic_music, BLUE, GREY, isPillLight = true)!!
        val circleNote = createThemedActionIconBitmap(context, R.drawable.ic_music, BLUE, GREY, isPillLight = true)!!
        assertFalse(shortcutNote.sameAs(circleNote))
    }

    private companion object {
        const val BLUE = 0xFF1973E8.toInt()
        const val GREY = 0xFF5F6368.toInt()
    }
}
