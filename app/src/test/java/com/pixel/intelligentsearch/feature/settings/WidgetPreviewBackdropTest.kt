package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.theme.SearchIconColors
import com.pixel.intelligentsearch.feature.widget.WidgetBarShape
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The live preview draws the bar's background as the widget draws its backdrop: the rim, and the pill and circle over
 * it, as one group faded once, so at half opacity the pill and circle show their own colour at half opacity, not
 * mixed with the rim under them, and the rim around them is at half opacity too.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetPreviewBackdropTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theBackdropIsFadedAsOneGroup() {
        val colors = WidgetPreviewColors(
            backdropAlpha = 0.5f,
            rim = Color.Blue,
            pill = PreviewPill(Color.Red, isLight = false),
            accentPrimary = Color.White,
            materialIconAccents = SearchIconColors.themeAccents(context, false),
            systemIconTint = Color.White,
            customIcons = WidgetElementColors(0, 0, 0, 0, 0, 0),
            usesMaterialYouIcons = true
        )
        // The Material design's bar: an 8dp rim round a 48dp pill and circle, 8dp apart.
        val shape = WidgetBarShape.of(isMaterialDesign = true, outlineThickness = 8, cornerRadius = 24)
        compose.setContent {
            Box(Modifier.size(300.dp, shape.height.dp).background(Color.White).testTag("bar")) {
                WidgetPreviewBackdrop(colors, shape, showsCircle = true, modifier = Modifier.matchParentSize())
            }
        }
        val drawn = compose.onNodeWithTag("bar").captureToImage().asAndroidBitmap()
        val density = context.resources.displayMetrics.density
        fun at(xDp: Int, yDp: Int) = drawn.getPixel((xDp * density).toInt(), (yDp * density).toInt())

        val halfRed = 0xFFFF8080.toInt()
        val halfBlue = 0xFF8080FF.toInt()
        assertNear("rim above the pill", halfBlue, at(150, 4))
        assertNear("pill", halfRed, at(100, 32))
        assertNear("rim between the pill and circle", halfBlue, at(300 - 8 - 48 - 4, 32))
        assertNear("circle", halfRed, at(300 - 8 - 24, 32))
    }

    private fun assertNear(what: String, expected: Int, actual: Int) {
        val close = listOf(16, 8, 0).all { abs((expected shr it and 0xFF) - (actual shr it and 0xFF)) <= 3 }
        assertTrue("$what: expected #%06X, drawn #%06X".format(expected and 0xFFFFFF, actual and 0xFFFFFF), close)
    }
}
