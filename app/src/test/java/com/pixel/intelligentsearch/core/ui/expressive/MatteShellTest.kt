package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The matte shell's grain textures a surface without changing its tone, and text on a shell stays readable
 * wherever the grain and glow fall.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MatteShellTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun specksAreLightOrDarkByDistanceFromTheMiddle() {
        assertEquals(0, MatteGrain.speck(0.5f) ushr 24)
        assertEquals(0xFF000000.toInt(), MatteGrain.speck(0f))
        assertEquals(0xFFFFFFFF.toInt(), MatteGrain.speck(1f))
    }

    @Test
    fun grainCellsAreAboutThreeQuartersOfADp() {
        assertEquals(1, MatteGrain.cellPxFor(0.5f))
        assertEquals(1, MatteGrain.cellPxFor(1f))
        assertEquals(2, MatteGrain.cellPxFor(2f))
        assertEquals(3, MatteGrain.cellPxFor(3.5f))
    }

    @Test
    fun theGrainIsAsOftenLightAsDarkInCellsOfTheGivenSize() {
        val cellPx = 3
        val bitmap = MatteGrain.tile(cellPx).asAndroidBitmap()
        assertEquals(64 * cellPx, bitmap.width)
        var light = 0
        var dark = 0
        for (y in 0 until bitmap.height step cellPx) {
            for (x in 0 until bitmap.width step cellPx) {
                val pixel = bitmap.getPixel(x, y)
                // A cell is one colour throughout.
                assertEquals(pixel, bitmap.getPixel(x + cellPx - 1, y + cellPx - 1))
                if (pixel ushr 24 == 0) continue
                if (pixel and 0xFFFFFF == 0xFFFFFF) light++ else dark++
            }
        }
        val lightShare = light.toFloat() / (light + dark)
        assertTrue("light share $lightShare", lightShare in 0.45f..0.55f)
    }

    /** The app theme's colour scheme (see IntelligentSearchTheme): the dynamic one for [dark] or light. */
    @Composable
    private fun appScheme(dark: Boolean) =
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)

    @Test
    fun textStaysReadableOnTheGrainAndGlowInBothThemes() {
        val looks = HashMap<Boolean, MatteShellLook>()
        val texts = HashMap<Boolean, List<Color>>()
        compose.setContent {
            for (dark in listOf(false, true)) {
                MaterialTheme(colorScheme = appScheme(dark)) {
                    looks[dark] = rememberMatteShellLook()
                    texts[dark] = listOf(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        compose.waitForIdle()
        for (dark in listOf(false, true)) {
            val look = looks.getValue(dark)
            // The worst a spot can get: both glows at their brightest, then the strongest light or dark speck.
            val lit = look.glowTertiary.compositeOver(look.glowPrimary.compositeOver(look.container))
            for (base in listOf(look.container, lit)) {
                for (speck in listOf(Color.White, Color.Black)) {
                    val shown = speck.copy(alpha = look.grainAlpha).compositeOver(base).toArgb()
                    for (text in texts.getValue(dark)) {
                        val contrast = MaterialYouPaletteHelper.calculateContrastRatio(text.toArgb(), shown)
                        assertTrue("dark=$dark contrast $contrast", contrast >= 4.5)
                    }
                }
            }
        }
    }
}
