package com.pixel.intelligentsearch.core.theme

import android.content.Context
import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import kotlin.math.abs

/**
 * The search overlay's recipe for its search bar's Material icons, written out here from the overlay as the owner
 * chose it (not from the code under test), and where each part of each icon is, so tests can check that the widget,
 * the editor's preview and the overlay all draw the same icons in the same colours.
 *
 * From the theme's primary, secondary and tertiary for a light or dark bar: the lens in the Material G's four colours
 * (rim right arc, top of the rim top arc, handle bottom arc, sparkle left arc), the sparkle mic in the three accent
 * tiers (capsule and sparkle third, stem and left of the cradle second, right of the cradle first). The theme's
 * colours are the wallpaper's; a custom colour themes the icons as a wallpaper of its hue would. The sparkle is the
 * overlay's alone: the widget and its preview draw the same mic parts in the same colours, centred, with no sparkle.
 */
object OverlayIconRecipe {

    data class Colors(
        val gRight: Int, val gBottom: Int, val gLeft: Int, val gTop: Int,
        val first: Int, val second: Int, val third: Int
    ) {
        val lensRim get() = gRight
        val lensTop get() = gTop
        val lensHandle get() = gBottom
        val lensSparkle get() = gLeft
        val micCapsule get() = third
        val micStem get() = second
        val micLeftCradle get() = second
        val micRightCradle get() = first
        val micSparkle get() = third
    }

    fun colors(primary: Int, secondary: Int, tertiary: Int, isLight: Boolean): Colors {
        val g = MaterialYouPaletteHelper.getFourColorMaterialGPalette(primary, secondary, tertiary, isLight)
        val tiers = MaterialYouPaletteHelper.getThreeTierAccentedPalette(primary, secondary, isPillLight = isLight)
        return Colors(g.rightArcAndBar, g.bottomArc, g.leftArc, g.topArc, tiers.primaryDarker, tiers.secondLighter, tiers.thirdLighter)
    }

    /** The overlay's colours in the app's theme for a light or dark bar: the wallpaper's dynamic colours. */
    fun theme(context: Context, isLight: Boolean): Colors {
        val widget = DynamicThemeDetector.resolveWidgetColors(context, isDark = !isLight)
        assertTrue("the tests run with the wallpaper's dynamic colours", widget.isDynamicActive)
        return colors(widget.primary, widget.secondary, widget.tertiary, isLight)
    }

    /** The overlay's colours as a wallpaper of the custom colour's [hue] and [saturation] would theme it. */
    fun seeded(hue: Float, saturation: Float, isLight: Boolean): Colors {
        val scheme = MaterialYouPaletteHelper.getMaterialYouTonalColors(hue, saturation, isDarkSurface = !isLight)
        return colors(scheme.primary, scheme.secondary, scheme.tertiary, isLight)
    }

    /** A point inside one part of an icon, in the icon's own units (24 across, 100 for Lens), and its colour. */
    data class Part(val name: String, val x: Float, val y: Float, val color: Int, val tolerance: Int = 2)

    /** The overlay's lens (the circle button's Search and the Live shortcut). */
    fun lens(c: Colors) = listOf(
        Part("lens rim", 4.25f, 10.75f, c.lensRim),
        Part("lens top", 12.75f, 4.25f, c.lensTop),
        Part("lens handle", 17.75f, 17.75f, c.lensHandle),
        Part("lens sparkle", 18.25f, 6.75f, c.lensSparkle)
    )

    /** Where the overlay's mic has its sparkle. */
    private const val MIC_SPARKLE_X = 19.75f
    private const val MIC_SPARKLE_Y = 5.75f

    /** The overlay's sparkle mic: the mic a little left of centre, a sparkle at its top right. */
    fun mic(c: Colors) = listOf(
        Part("mic capsule", 9.75f, 8.75f, c.micCapsule),
        Part("mic stem", 9.25f, 21.25f, c.micStem),
        Part("mic left of the cradle", 3.75f, 13.25f, c.micLeftCradle),
        Part("mic right of the cradle", 15.25f, 13.25f, c.micRightCradle),
        Part("mic sparkle", MIC_SPARKLE_X, MIC_SPARKLE_Y, c.micSparkle)
    )

    /** The widget's mic: the overlay's mic parts in the overlay's colours, centred, without the sparkle (see [assertNoMicSparkle]). */
    fun widgetMic(c: Colors) = listOf(
        Part("mic capsule", 12.25f, 8.25f, c.micCapsule),
        Part("mic stem", 11.75f, 20.75f, c.micStem),
        Part("mic left of the cradle", 6.25f, 12.75f, c.micLeftCradle),
        Part("mic right of the cradle", 17.75f, 12.75f, c.micRightCradle)
    )

    /** Checks a mic drawn 24 units wide into [bitmap] has nothing where the overlay's mic has its sparkle. */
    fun assertNoMicSparkle(label: String, bitmap: Bitmap) {
        val scale = bitmap.width / 24f
        // The icon's top left corner is empty in every icon, so it shows what lies behind it.
        val behind = bitmap.getPixel(0, 0)
        val pixel = bitmap.getPixel((MIC_SPARKLE_X * scale).toInt(), (MIC_SPARKLE_Y * scale).toInt())
        assertTrue("$label: ${hex(pixel)} drawn where the overlay's mic has its sparkle, behind it ${hex(behind)}", pixel == behind)
    }

    /** The Material G: its bar is solid, its arcs a sweep through the four colours (so a point is near, not on, one). */
    fun g(c: Colors) = listOf(
        Part("G bar", 17.25f, 12.25f, c.gRight),
        Part("G bottom", 12.25f, 21.75f, c.gBottom, tolerance = 8),
        Part("G left", 2.25f, 12.25f, c.gLeft, tolerance = 8),
        Part("G top", 12.25f, 2.25f, c.gTop, tolerance = 8)
    )

    /** Google Lens's camera, in the mic's tiers: aperture first, dot second, the brackets third. */
    fun camera(c: Colors) = listOf(
        Part("Lens aperture", 51f, 53f, c.first),
        Part("Lens dot", 76f, 76f, c.second),
        Part("Lens bracket", 15.6f, 65.6f, c.third)
    )

    private fun hex(color: Int) = "#%08X".format(color)

    /** Checks each of [parts] of an icon drawn [across] units wide into [bitmap]. */
    fun assertDrawn(label: String, bitmap: Bitmap, parts: List<Part>, across: Float = 24f) {
        val scale = bitmap.width / across
        for (part in parts) {
            val pixel = bitmap.getPixel((part.x * scale).toInt(), (part.y * scale).toInt())
            val close = (0..24 step 8).all { shift -> abs((pixel shr shift and 0xFF) - (part.color shr shift and 0xFF)) <= part.tolerance }
            assertTrue("$label ${part.name}: drawn ${hex(pixel)}, the overlay's ${hex(part.color)}", close)
        }
    }
}
