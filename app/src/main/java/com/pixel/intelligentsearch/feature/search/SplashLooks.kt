package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Process
import android.os.UserHandle
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withSave
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What an app's splash screen shows: its [background] color, and its icon, a square [iconSizePx] wide centered on
 * the screen (Android's splash icon view), drawn by [paint] into a canvas whose origin is the square's corner.
 * [visiblePx] is how wide the icon's visible shape is, for the overlay to grow the tapped icon into it.
 */
internal class SplashLook(
    val background: Int,
    val iconSizePx: Int,
    val visiblePx: Float,
    val paint: (Canvas) -> Unit
)

/**
 * Works out an app's splash screen exactly as Android 17's own does, so the splash the overlay draws is the one the
 * app then shows, 1:1. Ported from WM Shell's SplashscreenContentDrawer and SplashscreenIconDrawableFactory and the
 * launcher icon library's BaseIconFactory, checked against those classes decompiled from the Pixel's SystemUI:
 *
 * - The background is the theme's windowSplashScreenBackground, else the main color of its window background when
 *   at least half of it is opaque, else Android's default (black in dark theme, white in light).
 * - The icon is the theme's windowSplashScreenAnimatedIcon, else the app's icon. A custom icon with no icon
 *   background, and an adaptive icon shown as its foreground alone (its background plain and like the splash, or gray
 *   under a foreground that stands out, and a foreground covering under 44% of its layer), draw 1.2 times the 160dp
 *   icon size; anything else draws at 160dp. An old-style icon is wrapped as the launcher wraps it, on white.
 * - Every icon but an animated one is drawn into Android's 108dp default icon box and scaled up from there, a
 *   foreground laid out half as large again as the box and clipped to the device's icon shape.
 */
internal object SplashLooks {
    /** starting_surface_icon_size. */
    private const val ICON_DP = 160f

    /** starting_surface_default_icon_size: the box icons are drawn into before they are scaled to the icon size. */
    private const val DEFAULT_ICON_DP = 108f

    /** WM Shell's splash_icon_no_background_scale_factor. */
    private const val NO_BACKGROUND_SCALE = 1.2f

    /** WM Shell's splash_icon_enlarge_foreground_threshold. */
    private const val ENLARGE_FOREGROUND_THRESHOLD = 0.44f

    /** MaskBackgroundDrawable.DEFAULT_VIEW_PORT_SCALE: 1 / (1 + 2 x 1/4). */
    private const val DEFAULT_VIEW_PORT_SCALE = 1f / 1.5f

    /** BaseIconFactory.LEGACY_ICON_SCALE: an old-style icon's share of the adaptive icon it is wrapped in. */
    internal val LEGACY_ICON_SCALE = (1f / (AdaptiveIconDrawable.getExtraInsetFraction() * 2f + 1f)) * 0.56481004f

    fun resolve(context: Context, packageName: String, user: UserHandle?, activityName: String?): SplashLook? {
        val metrics = context.resources.displayMetrics
        val iconSize = (ICON_DP * metrics.density + 0.5f).toInt()
        val defaultIconSize = (DEFAULT_ICON_DP * metrics.density + 0.5f).toInt()
        // Android loads the app's icon at the density it will show at, so bitmap icons stay sharp.
        val iconDpi = (ICON_DP / DEFAULT_ICON_DP * metrics.densityDpi * NO_BACKGROUND_SCALE + 0.5f).toInt()
        val (info, appIcon) = launchActivity(context, packageName, user, activityName, iconDpi) ?: return null
        val packageContext = runCatching { context.createPackageContext(packageName, 0) }.getOrNull()
        val theme = packageContext?.resources?.newTheme()?.apply { applyStyle(info.themeResource, true) }
        fun color(attr: Int, default: Int): Int = theme?.obtainStyledAttributes(intArrayOf(attr))?.let { ta ->
            try { ta.getColor(0, default) } catch (_: Exception) { default } finally { ta.recycle() }
        } ?: default
        fun drawable(attr: Int): Drawable? = theme?.obtainStyledAttributes(intArrayOf(attr))?.let { ta ->
            try { ta.getDrawable(0) } catch (_: Exception) { null } finally { ta.recycle() }
        }
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val themeColor = windowColor(color(android.R.attr.windowSplashScreenBackground, 0), drawable(android.R.attr.windowBackground), night)
        val iconBackground = color(android.R.attr.windowSplashScreenIconBackgroundColor, Color.TRANSPARENT)
        val custom = drawable(android.R.attr.windowSplashScreenAnimatedIcon)
        return lookFor(themeColor, iconBackground, custom, appIcon, iconSize, defaultIconSize)
    }

    /** The splash for this theme color, icon background, custom splash icon and app icon, at Android's icon sizes. */
    internal fun lookFor(
        themeColor: Int,
        iconBackground: Int,
        custom: Drawable?,
        appIcon: Drawable?,
        iconSize: Int,
        defaultIconSize: Int
    ): SplashLook? {
        if (custom != null) {
            val noBackground = iconBackground == Color.TRANSPARENT || iconBackground == themeColor
            val size = if (noBackground) (iconSize * NO_BACKGROUND_SCALE).toInt() else iconSize
            return SplashLook(themeColor, size, size.toFloat(), iconPainter(custom.mutate(), iconBackground, themeColor, size, defaultIconSize))
        }
        val icon = appIcon?.mutate() ?: return null
        if (icon is AdaptiveIconDrawable) {
            val foreground = icon.foreground
            val front = testColors(foreground, opaqueOnly = true)
            val back = testColors(icon.background, opaqueOnly = false)
            val foregroundOnly = foreground != null && !back.complex && iconBackground == Color.TRANSPARENT &&
                (similarInHsv(themeColor, back.dominant) || (back.grayscale && !similarInHsv(themeColor, front.dominant)))
            if (foregroundOnly) {
                val scale = if (front.opaqueShare < ENLARGE_FOREGROUND_THRESHOLD) NO_BACKGROUND_SCALE else 1f
                val size = (iconSize * scale + 0.5f).toInt()
                return SplashLook(themeColor, size, size.toFloat(), iconPainter(foreground!!.mutate(), iconBackground, themeColor, size, defaultIconSize))
            }
            return SplashLook(themeColor, iconSize, iconSize.toFloat(), iconPainter(icon, iconBackground, themeColor, iconSize, defaultIconSize))
        }
        return legacyLook(themeColor, icon, iconSize, defaultIconSize)
    }

    /** SplashscreenIconDrawableFactory.makeIconDrawable: [icon] in a [size] square, on its icon background if any. */
    private fun iconPainter(icon: Drawable, iconBackground: Int, themeColor: Int, size: Int, defaultIconSize: Int): (Canvas) -> Unit {
        val drawBackground = iconBackground != Color.TRANSPARENT && iconBackground != themeColor && icon !is AdaptiveIconDrawable
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = iconBackground }
        return { canvas ->
            if (drawBackground) canvas.drawPath(iconMask(size), backgroundPaint)
            when (icon) {
                // AnimatableIconAnimateListener: drawn straight into the icon view, as a masked foreground.
                is Animatable -> drawAdaptiveForeground(canvas, icon, size)
                // ImmobileIconDrawable over the whole adaptive icon.
                is AdaptiveIconDrawable -> prescaled(canvas, size, defaultIconSize) { box ->
                    icon.setBounds(0, 0, box, box)
                    icon.draw(this)
                }
                // ImmobileIconDrawable over an AdaptiveForegroundDrawable.
                else -> prescaled(canvas, size, defaultIconSize) { box -> drawAdaptiveForeground(this, icon, box) }
            }
        }
    }

    /**
     * An old-style icon, as BaseIconFactory wraps it for the splash: an adaptive icon on white with the icon inset to
     * [LEGACY_ICON_SCALE] of it (keeping its aspect), drawn into the icon square less a margin.
     */
    private fun legacyLook(themeColor: Int, icon: Drawable, iconSize: Int, defaultIconSize: Int): SplashLook {
        val margin = max(ceil(iconSize * 0.035f).toInt(), (iconSize * 0.07999998f / 2f).roundToInt())
        val shape = iconSize - margin * 2
        val wrapped = AdaptiveIconDrawable(Color.WHITE.toDrawable(), wrapIntoSquare(icon, LEGACY_ICON_SCALE))
        return SplashLook(themeColor, iconSize, shape.toFloat()) { canvas ->
            // The launcher draws it into an icon-sized bitmap, which the splash then draws through the default box.
            prescaled(canvas, iconSize, defaultIconSize) { box ->
                val scaledMargin = margin * box / iconSize.toFloat()
                withSave {
                    translate(scaledMargin, scaledMargin)
                    val inner = (box - scaledMargin * 2).roundToInt()
                    wrapped.setBounds(0, 0, inner, inner)
                    wrapped.draw(this)
                }
            }
        }
    }

    /** BaseIconFactory.wrapIntoSquareDrawable: [icon] inset to [scale] of a square, keeping its aspect. */
    private fun wrapIntoSquare(icon: Drawable, scale: Float): Drawable {
        val h = icon.intrinsicHeight.toFloat()
        val w = icon.intrinsicWidth.toFloat()
        var scaleX = scale
        var scaleY = scale
        if (h > w && w > 0f) scaleX = w / h * scale else if (w > h && h > 0f) scaleY = h / w * scale
        val insetX = (1f - scaleX) / 2f
        val insetY = (1f - scaleY) / 2f
        return InsetDrawable(icon, insetX, insetY, insetX, insetY)
    }

    /** ImmobileIconDrawable: drawn into the [defaultIconSize] box, then scaled up to [size]. */
    private inline fun prescaled(canvas: Canvas, size: Int, defaultIconSize: Int, block: Canvas.(Int) -> Unit) {
        canvas.withSave {
            val scale = size.toFloat() / defaultIconSize
            scale(scale, scale)
            block(defaultIconSize)
        }
    }

    /** AdaptiveForegroundDrawable: [foreground] laid out half as large again as the [box], clipped to the icon shape. */
    private fun drawAdaptiveForeground(canvas: Canvas, foreground: Drawable, box: Int) {
        canvas.withSave {
            clipPath(iconMask(box))
            val center = box / 2
            val inset = (box / (DEFAULT_VIEW_PORT_SCALE * 2)).toInt()
            foreground.setBounds(center - inset, center - inset, center + inset, center + inset)
            foreground.draw(this)
        }
    }

    /** The device's icon shape (config_icon_mask) filling a [size] square. */
    private fun iconMask(size: Int): Path =
        AdaptiveIconDrawable(Color.TRANSPARENT.toDrawable(), Color.TRANSPARENT.toDrawable()).run {
            setBounds(0, 0, size, size)
            Path(iconMask)
        }

    /**
     * SplashscreenContentDrawer.estimateWindowBGColor: the splash background color, else the window background's main
     * color when at least half of it is opaque, else Android's default.
     */
    internal fun windowColor(splashBackground: Int, windowBackground: Drawable?, night: Boolean): Int {
        val default = if (night) Color.BLACK else Color.WHITE
        val drawable = if (splashBackground != 0) splashBackground.toDrawable() else windowBackground ?: return default
        val tested = testColors(drawable, opaqueOnly = true)
        return if (tested.opaqueShare < 0.5f) default else tested.dominant
    }

    /** What DrawableColorTester reads from a drawable: its main color, whether it is many colors or gray, how opaque. */
    internal class TestedColors(val dominant: Int, val complex: Boolean, val grayscale: Boolean, val opaqueShare: Float)

    /**
     * DrawableColorTester: a plain color as it is; anything else drawn at most 40px square (only its bottom layer for
     * a layer list) and read in Palette's five-bit color buckets, skipping pixels not fully opaque when [opaqueOnly].
     */
    internal fun testColors(drawable: Drawable?, opaqueOnly: Boolean): TestedColors {
        var target = drawable
        if (target is LayerDrawable && target.numberOfLayers > 0) target = target.getDrawable(0)
        if (target == null) return TestedColors(Color.BLACK, complex = false, grayscale = true, opaqueShare = 1f)
        if (target is ColorDrawable) {
            val c = target.color
            return TestedColors(c, complex = false, grayscale = isGray(c), opaqueShare = target.alpha / 255f)
        }
        return runCatching {
            val w = target.intrinsicWidth.let { if (it <= 0) 40 else minOf(it, 40) }
            val h = target.intrinsicHeight.let { if (it <= 0) 40 else minOf(it, 40) }
            val bitmap = createBitmap(w, h)
            val bounds = target.copyBounds()
            target.setBounds(0, 0, w, h)
            target.draw(Canvas(bitmap))
            target.bounds = bounds
            val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
            bitmap.recycle()
            val kept = if (opaqueOnly) pixels.filter { (it ushr 24) == 0xFF } else pixels.toList()
            if (kept.isEmpty()) return@runCatching TestedColors(Color.BLACK, complex = false, grayscale = true, opaqueShare = 0f)
            val buckets = kept.groupingBy { quantize(it) }.eachCount()
            val main = buckets.maxBy { it.value }.key
            TestedColors(
                dominant = approximate(main),
                complex = buckets.size > 1,
                grayscale = buckets.keys.all { isGray(approximate(it)) },
                opaqueShare = kept.size / pixels.size.toFloat()
            )
        }.getOrElse { TestedColors(Color.BLACK, complex = true, grayscale = false, opaqueShare = 1f) }
    }

    /** Palette's ColorCutQuantizer bucket: five bits a channel. */
    private fun quantize(c: Int) = (((c shr 16) and 0xFF) shr 3 shl 10) or (((c shr 8) and 0xFF) shr 3 shl 5) or ((c and 0xFF) shr 3)

    private fun approximate(q: Int): Int {
        fun widen(v: Int) = (v shl 3) or (v shr 2)
        return Color.rgb(widen((q shr 10) and 0x1F), widen((q shr 5) and 0x1F), widen(q and 0x1F))
    }

    private fun isGray(c: Int) = Color.red(c) == Color.green(c) && Color.green(c) == Color.blue(c)

    /** SplashscreenContentDrawer.isRgbSimilarInHsv. */
    internal fun similarInHsv(a: Int, b: Int): Boolean {
        if (a == b) return true
        val lumA = Color.luminance(a)
        val lumB = Color.luminance(b)
        val contrast = if (lumA > lumB) (lumA + 0.05f) / (lumB + 0.05f) else (lumB + 0.05f) / (lumA + 0.05f)
        if (contrast < 2f) return true
        val hsvA = FloatArray(3).also { Color.colorToHSV(a, it) }
        val hsvB = FloatArray(3).also { Color.colorToHSV(b, it) }
        var minAngle = abs(hsvA[0] - hsvB[0]).toInt()
        minAngle = (minAngle + 180) % 360 - 180
        val h = (minAngle / 180f).toDouble().pow(2)
        val s = (hsvA[1] - hsvB[1]).toDouble().pow(2)
        val v = (hsvA[2] - hsvB[2]).toDouble().pow(2)
        return sqrt((h + s + v) / 3) < 0.1
    }

    /** The activity that opens and its own (unbadged) icon at [iconDpi]. */
    private fun launchActivity(context: Context, packageName: String, user: UserHandle?, activityName: String?, iconDpi: Int): Pair<ActivityInfo, Drawable?>? {
        if (user != null && user != Process.myUserHandle()) {
            val apps = context.getSystemService(LauncherApps::class.java) ?: return null
            val activity = runCatching { apps.getActivityList(packageName, user) }.getOrNull()
                ?.let { list -> list.firstOrNull { it.componentName.className == activityName } ?: list.firstOrNull() }
                ?: return null
            return activity.activityInfo to runCatching { activity.getIcon(iconDpi) }.getOrNull()
        }
        val pm = context.packageManager
        val intent = activityName?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(packageName, it) }
            ?: runCatching { pm.getLaunchIntentForPackage(packageName) }.getOrNull()
            ?: return null
        val info = runCatching { pm.resolveActivity(intent, 0)?.activityInfo }.getOrNull() ?: return null
        val icon = runCatching {
            val resources = pm.getResourcesForApplication(info.applicationInfo)
            info.iconResource.takeIf { it != 0 }?.let { resources.getDrawableForDensity(it, iconDpi, null) }
        }.getOrNull() ?: runCatching { info.loadUnbadgedIcon(pm) }.getOrNull()
        return info to icon
    }
}
