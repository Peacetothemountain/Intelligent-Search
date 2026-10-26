package com.pixel.intelligentsearch.core.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The Material outline drawn for apps that have no monochrome icon. An old-style icon (a plate of one color with
 * transparent rounded corners, a logo on it, as Geeni's) used to come out as the whole plate, a blank square once
 * tinted: the corners' transparency was taken for the background. Now the logo is kept, and an icon whose logo can't
 * be told apart from its plate shows in full color instead of as a blank plate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialOutlineSilhouetteTest {

    private val size = 192
    /**
     * A rounded-square plate icon (transparent corners), with [logo] drawn on it in white. The plate shades from light
     * to dark blue, top to bottom, as Geeni's does.
     */
    private fun plateIcon(logo: Canvas.(Paint) -> Unit = {}): BitmapDrawable {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val gradient = LinearGradient(0f, 0f, 0f, size.toFloat(), Color.rgb(0, 175, 236), Color.rgb(0, 121, 185), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient })
        canvas.logo(Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return BitmapDrawable(ApplicationProvider.getApplicationContext<android.content.Context>().resources, bitmap)
    }

    /** Geeni's face: two eyes and a smile. */
    private val face: Canvas.(Paint) -> Unit = { paint ->
        drawCircle(60f, 80f, 22f, paint)
        drawCircle(132f, 80f, 22f, paint)
        drawRect(50f, 130f, 142f, 146f, paint)
    }

    /** Alpha of the outline at ([x], [y]) of the source icon (the outline is scaled into the 72/108 safe zone). */
    private fun Bitmap.alphaAtSource(x: Int, y: Int): Int {
        val scale = width / size.toFloat()
        val cx = width / 2f
        val px = (cx + (x * scale - cx) * 0.72f).toInt()
        val py = (cx + (y * scale - cx) * 0.72f).toInt()
        return Color.alpha(getPixel(px, py))
    }

    @Test
    fun aPlateIconKeepsItsLogoNotThePlate() {
        val outline = MaterialOutlineManager.extractMonochromeSilhouette(plateIcon(face))
        assertNotNull(outline)
        assertTrue("the eyes are drawn", outline!!.alphaAtSource(60, 80) > 200)
        assertTrue("the smile is drawn", outline.alphaAtSource(96, 138) > 200)
        assertEquals("the plate around the face is left out, light at the top", 0, outline.alphaAtSource(96, 30))
        assertEquals("and dark at the bottom", 0, outline.alphaAtSource(96, 170))
        assertEquals("so is the plate between the eyes", 0, outline.alphaAtSource(96, 80))
    }

    @Test
    fun aPlateWithNoLogoShowsInFullColorRatherThanAsABlankPlate() {
        assertNull(MaterialOutlineManager.extractMonochromeSilhouette(plateIcon()))
    }

    @Test
    fun aLogoOnTransparencyKeepsItsShape() {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(96f, 96f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 60, 40) })
        val outline = MaterialOutlineManager.extractMonochromeSilhouette(
            BitmapDrawable(ApplicationProvider.getApplicationContext<android.content.Context>().resources, bitmap)
        )
        assertNotNull(outline)
        assertTrue("the logo is drawn", outline!!.alphaAtSource(96, 96) > 200)
        assertEquals("around it stays clear", 0, outline.alphaAtSource(20, 20))
    }

    /** Pizza Hut's foreground, which it also declares as its monochrome layer: a gray plate, a red roof, black lettering. */
    private fun pizzaHutPicture(): BitmapDrawable {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(28f, 28f, 164f, 164f, Paint().apply { color = Color.rgb(243, 243, 243) })
        canvas.drawRect(50f, 55f, 142f, 80f, Paint().apply { color = Color.rgb(230, 30, 40) })
        canvas.drawRect(60f, 95f, 132f, 140f, Paint().apply { color = Color.rgb(27, 27, 27) })
        return BitmapDrawable(ApplicationProvider.getApplicationContext<android.content.Context>().resources, bitmap)
    }

    @Test
    fun aFullColorPictureDeclaredAsMonochromeIsToldApart() {
        assertTrue(MaterialOutlineManager.isColorPicture(pizzaHutPicture()))
        val oneColor = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(oneColor).drawCircle(96f, 96f, 50f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        assertFalse(
            "a true monochrome layer is one color",
            MaterialOutlineManager.isColorPicture(BitmapDrawable(ApplicationProvider.getApplicationContext<android.content.Context>().resources, oneColor))
        )
    }

    @Test
    fun theLogoIsCutOutOfSuchAPicture() {
        val outline = MaterialOutlineManager.extractMonochromeSilhouette(pizzaHutPicture())
        assertNotNull(outline)
        assertTrue("the roof is drawn", outline!!.alphaAtSource(96, 67) > 200)
        assertTrue("the lettering is drawn", outline.alphaAtSource(96, 118) > 200)
        assertEquals("the gray plate is left out", 0, outline.alphaAtSource(40, 150))
    }
}
