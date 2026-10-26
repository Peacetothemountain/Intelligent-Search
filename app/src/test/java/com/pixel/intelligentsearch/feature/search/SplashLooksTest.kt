package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The splash the overlay draws before an app opens is the one Android 17 then shows: the same icon form at the same
 * size, by the rules of WM Shell's SplashscreenContentDrawer (decompiled from the Pixel's SystemUI). Sizes here are
 * at the Pixel 11's 375dpi: the 160dp icon is 375px and the 108dp default box 253px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplashLooksTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val iconSize = 375
    private val defaultIconSize = 253
    private val dark = Color.rgb(31, 31, 33)

    /** A small foreground (a dot) on [background], as most app icons are drawn. */
    private fun adaptive(background: android.graphics.drawable.Drawable) =
        AdaptiveIconDrawable(background, GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.RED)
            setSize(108, 108)
        }.let { android.graphics.drawable.InsetDrawable(it, 0.35f) })

    @Test
    fun aCustomSplashIconWithNothingBehindItDrawsLarger() {
        val look = SplashLooks.lookFor(dark, Color.TRANSPARENT, ColorDrawable(Color.RED), null, iconSize, defaultIconSize)
        assertEquals("(int)(375 x 1.2)", 450, look!!.iconSizePx)
    }

    @Test
    fun aCustomSplashIconOnItsOwnBackgroundDrawsAtTheIconSize() {
        val look = SplashLooks.lookFor(dark, Color.WHITE, ColorDrawable(Color.RED), null, iconSize, defaultIconSize)
        assertEquals(375, look!!.iconSizePx)
    }

    @Test
    fun anIconBackgroundLikeTheSplashCountsAsNone() {
        val look = SplashLooks.lookFor(dark, dark, ColorDrawable(Color.RED), null, iconSize, defaultIconSize)
        assertEquals(450, look!!.iconSizePx)
    }

    @Test
    fun anAdaptiveIconOnWhiteShowsItsForegroundAloneAndLarger() {
        // White is gray, and the red dot stands out from the dark splash, and covers well under 44% of its layer.
        val look = SplashLooks.lookFor(dark, Color.TRANSPARENT, null, adaptive(ColorDrawable(Color.WHITE)), iconSize, defaultIconSize)
        assertEquals("(int)(0.5 + 375 x 1.2)", 450, look!!.iconSizePx)
    }

    @Test
    fun anAdaptiveIconOnABusyBackgroundShowsWhole() {
        val busy = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.BLUE, Color.YELLOW))
        val look = SplashLooks.lookFor(dark, Color.TRANSPARENT, null, adaptive(busy), iconSize, defaultIconSize)
        assertEquals(375, look!!.iconSizePx)
        assertEquals(375f, look.visiblePx)
    }

    @Test
    fun anOldStyleIconSitsInAWhiteShapeWithTheLaunchersMargin() {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val look = SplashLooks.lookFor(dark, Color.TRANSPARENT, null, BitmapDrawable(context.resources, bitmap), iconSize, defaultIconSize)
        assertNotNull(look)
        assertEquals(375, look!!.iconSizePx)
        // BaseIconFactory: a margin of max(ceil(375 x 0.035), round(375 x 0.04)) = 15 on each side.
        assertEquals(345f, look.visiblePx)
        // Drawn: white around the icon inside the shape, the icon itself in the middle.
        val canvasBitmap = Bitmap.createBitmap(375, 375, Bitmap.Config.ARGB_8888)
        look.paint(Canvas(canvasBitmap))
        assertEquals(Color.WHITE, canvasBitmap.getPixel(187, 40) or 0xFF000000.toInt())
        assertEquals(Color.BLUE, canvasBitmap.getPixel(187, 187) or 0xFF000000.toInt())
        assertEquals("outside the shape stays clear", 0, Color.alpha(canvasBitmap.getPixel(2, 2)))
    }

    @Test
    fun theOldStyleIconFillsTheLaunchersShareOfItsShape() {
        assertEquals(0.37654f, SplashLooks.LEGACY_ICON_SCALE, 0.0001f)
    }

    @Test
    fun aSplashBackgroundColorIsUsedAsItIs() {
        assertEquals(Color.RED, SplashLooks.windowColor(Color.RED, ColorDrawable(Color.BLUE), night = true))
    }

    @Test
    fun anOpaqueWindowBackgroundGivesItsColor() {
        assertEquals(Color.BLUE, SplashLooks.windowColor(0, ColorDrawable(Color.BLUE), night = true))
    }

    @Test
    fun aTransparentWindowBackgroundFallsBackToAndroidsDefault() {
        assertEquals(Color.BLACK, SplashLooks.windowColor(0, ColorDrawable(Color.TRANSPARENT), night = true))
        assertEquals(Color.WHITE, SplashLooks.windowColor(0, null, night = false))
    }

    @Test
    fun colorsWithLittleContrastAreTheSame() {
        assertTrue(SplashLooks.similarInHsv(Color.rgb(30, 31, 34), Color.rgb(20, 20, 20)))
        assertFalse(SplashLooks.similarInHsv(Color.WHITE, dark))
    }

    @Test
    fun theAppOpensWithoutAndroidsOwnMotionWhenTheOverlayDrewItsSplash() {
        val view = android.view.View(context)
        assertEquals(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION, AppLaunchAnimation.launch(view, null, drawnSplash = true).intentFlags)
        assertEquals(0, AppLaunchAnimation.launch(view, null, drawnSplash = false).intentFlags)
    }
}
