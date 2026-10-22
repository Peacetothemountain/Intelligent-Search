package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The matte shell is the container it always was with only a matte texture added: its colour is exactly the
 * container's, it casts no shadow, its grain textures the surface without changing its tone, and text on the
 * containers that use it stays readable wherever the grain falls.
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
    fun grainCellsAreOneScreenPixelAtEveryDensity() {
        // The owner asked for a matte finish with no visible speckle, so a cell never grows past one pixel.
        for (density in listOf(0.5f, 1f, 2f, 3.5f)) assertEquals(1, MatteGrain.cellPxFor(density))
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
    fun aShellIsExactlyItsContainersColourWithNoGlowRimOrShadow() {
        val wasDrawn = MatteGrain.drawn
        MatteGrain.drawn = false
        try {
            val containers = HashMap<Boolean, Color>()
            val pages = HashMap<Boolean, Color>()
            compose.setContent {
                Column {
                    for (dark in listOf(false, true)) {
                        MaterialTheme(colorScheme = appScheme(dark)) {
                            val page = MaterialTheme.colorScheme.background
                            containers[dark] = MaterialTheme.colorScheme.surfaceContainer
                            pages[dark] = page
                            Box(Modifier.testTag("page_$dark").background(page).padding(24.dp)) {
                                MatteShell(Modifier.size(120.dp, 80.dp), shape = RoundedCornerShape(24.dp)) {}
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            for (dark in listOf(false, true)) {
                val container = containers.getValue(dark)
                val page = pages.getValue(dark)
                val bitmap = compose.onNodeWithTag("page_$dark").captureToImage().asAndroidBitmap()
                val w = bitmap.width
                val h = bitmap.height
                // The middle and the top edge just inside the border are the container's colour: no glow, no rim.
                assertEquals("dark=$dark centre", container.toArgb(), bitmap.getPixel(w / 2, h / 2))
                val edgeY = (h * (24f + 4f) / (24f + 80f + 24f)).toInt()
                assertEquals("dark=$dark top edge", container.toArgb(), bitmap.getPixel(w / 2, edgeY))
                // Around the shell the page is untouched: no shadow or halo.
                for ((x, y) in listOf(w / 2 to 2, w / 2 to h - 3, 2 to h / 2, w - 3 to h / 2)) {
                    assertEquals("dark=$dark around ($x, $y)", page.toArgb(), bitmap.getPixel(x, y))
                }
            }
        } finally {
            MatteGrain.drawn = wasDrawn
        }
    }

    @Test
    fun aShellTakesTheCallersColourAndBorderAndNoGrainWhereThereIsNoFill() {
        val looks = HashMap<String, MatteShellLook>()
        var tint = Color.Unspecified
        compose.setContent {
            MaterialTheme(colorScheme = appScheme(true)) {
                tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                looks["tint"] = rememberMatteShellLook(tint, border = null)
                looks["clear"] = rememberMatteShellLook(Color.Transparent, border = null)
                looks["card"] = rememberMatteShellLook()
            }
        }
        compose.waitForIdle()
        assertEquals(tint, looks.getValue("tint").container)
        assertEquals(null, looks.getValue("tint").border)
        assertEquals(0f, looks.getValue("clear").grainAlpha)
        assertEquals(1.dp, looks.getValue("card").border?.width)
    }

    @Test
    fun textStaysReadableOnTheGrainOfEveryContainerInBothThemes() {
        val looks = HashMap<Boolean, List<MatteShellLook>>()
        val pages = HashMap<Boolean, Color>()
        val texts = HashMap<Boolean, List<Color>>()
        compose.setContent {
            for (dark in listOf(false, true)) {
                MaterialTheme(colorScheme = appScheme(dark)) {
                    val scheme = MaterialTheme.colorScheme
                    // The containers the settings pages put text on, in the colours they use.
                    looks[dark] = listOf(
                        scheme.surfaceContainer,
                        scheme.surfaceContainerLow,
                        scheme.surfaceContainerHighest,
                        scheme.surfaceVariant.copy(alpha = 0.4f),
                        scheme.surfaceVariant.copy(alpha = 0.5f),
                        scheme.surfaceVariant.copy(alpha = 0.6f)
                    ).map { rememberMatteShellLook(it) }
                    pages[dark] = scheme.background
                    texts[dark] = listOf(scheme.onSurface, scheme.onSurfaceVariant)
                }
            }
        }
        compose.waitForIdle()
        for (dark in listOf(false, true)) {
            for (look in looks.getValue(dark)) {
                val base = look.container.compositeOver(pages.getValue(dark))
                for (speck in listOf(Color.White, Color.Black)) {
                    val shown = speck.copy(alpha = look.grainAlpha).compositeOver(base).toArgb()
                    for (text in texts.getValue(dark)) {
                        val contrast = MaterialYouPaletteHelper.calculateContrastRatio(text.toArgb(), shown)
                        assertTrue("dark=$dark contrast $contrast on ${look.container}", contrast >= 4.5)
                    }
                }
            }
        }
    }
}
