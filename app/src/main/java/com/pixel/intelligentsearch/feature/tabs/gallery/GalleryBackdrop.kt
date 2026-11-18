package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.core.graphics.drawable.toDrawable
import com.pixel.intelligentsearch.feature.tabs.ui.*
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import com.pixel.intelligentsearch.feature.tabs.ui.findActivity

/**
 * What the windows in the page show: each look on the wallpaper made for it (drawn by the app), Android's real
 * wallpaper behind the window, or a mesh of the real wallpaper's colors.
 */
internal enum class Backdrop(val label: String) {
    LookWallpaper("The Look's Wallpaper"), RealWallpaper("Your Wallpaper"), ColorsOnly("Colors Only");

    /** Where a preview of [look] is shown, in words. A look of the owner's own has no wallpaper: it is on theirs. */
    fun where(look: Look): String = when {
        this == ColorsOnly -> "on your wallpaper's colors"
        this == LookWallpaper && wallpaperOf(look) != null -> "on its wallpaper"
        else -> "on your wallpaper"
    }

    /** What is behind a preview of [look], as a thing to tap. */
    fun behind(look: Look): String = when {
        this == ColorsOnly -> "the background"
        this == LookWallpaper && wallpaperOf(look) != null -> "the wallpaper"
        else -> "your wallpaper"
    }
}

/** The backdrop the page is on, for the words on its cards. */
internal val LocalGalleryBackdrop = staticCompositionLocalOf { Backdrop.LookWallpaper }

/** Screenshot tests set the backdrop and the wallpaper's colors (Robolectric has no wallpaper). */
internal val LocalGalleryBackdropOverride = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalGalleryWallpaperColors = staticCompositionLocalOf<List<Color>?> { null }


/**
 * While Gallery's windows look through to the real wallpaper the window's background turns transparent, so the
 * wallpaper shows in them; it is put back when Gallery leaves or no window needs it. Gallery never sets or clears the
 * window's show-wallpaper flag or its translucency: the window is translucent from its start (MainActivity) and the
 * shell puts the wallpaper behind it while Gallery is in sight and asks for it (PageChrome.showWallpaper,
 * WallpaperBehindWindow). No window blur is set here: setting one before the window has content crashed the probe.
 */
@Composable
internal fun ShowWallpaperBehindWindow(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        val window = context.findActivity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose { }
        val oldBackground: Drawable? = window.decorView.background
        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        onDispose {
            setWindowBlur(window, 0)
            window.setBackgroundDrawable(oldBackground)
        }
    }
}

/** The window's background blur (the real search page's way of blurring the wallpaper). Only once the window has content. */
internal fun setWindowBlur(window: Window?, radius: Int) {
    if (window == null || window.decorView.isAttachedToWindow.not()) return
    runCatching { window.setBackgroundBlurRadius(radius.coerceAtLeast(0)) }
}

/** The wallpaper's three colors (WallpaperManager.getWallpaperColors needs no permission); the phone's palette without. */
@Composable
internal fun rememberWallpaperColors(): List<Color> {
    LocalGalleryWallpaperColors.current?.let { return it }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    return remember(context, scheme) {
        val wc = runCatching { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
        if (wc != null) {
            val p = Color(wc.primaryColor.toArgb())
            val s = wc.secondaryColor?.let { Color(it.toArgb()) } ?: lerp(p, Color.White, 0.3f)
            val t = wc.tertiaryColor?.let { Color(it.toArgb()) } ?: lerp(p, Color.Black, 0.4f)
            listOf(p, s, t)
        } else listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.surfaceContainerLowest)
    }
}

/** Whether dark status bar icons suit the wallpaper (its colors say dark text reads on it). */
@Composable
internal fun rememberWallpaperWantsDarkIcons(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val wc = runCatching { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
        wc != null && (wc.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    }
}

/**
 * Colors only: one smooth mesh made from the wallpaper's three colors, screen-fixed behind every window, as the
 * approved mockup draws it (soft overlapping pools of the primary and secondary colors on the tertiary). No grain.
 */
@Composable
internal fun WallMesh(colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawWallMesh(colors, size) }
}

/** The mesh over a screen of [screen] size, at the screen's origin; [blendMode] DstOver draws it behind what is there. */
internal fun DrawScope.drawWallMesh(colors: List<Color>, screen: Size, blendMode: BlendMode = BlendMode.SrcOver) {
    val (p, s, t) = colors
    // The mockup's 340 × 720 frame, covering the screen ("slice").
    val k = maxOf(screen.width / 340f, screen.height / 720f)
    val dx = (screen.width - 340f * k) / 2f
    val dy = (screen.height - 720f * k) / 2f
    fun DrawScope.pool(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) {
        scale(1f, ry / rx, pivot = Offset(cx, cy)) {
            drawCircle(
                Brush.radialGradient(
                    0f to c, 0.35f to c.copy(alpha = 0.85f), 0.7f to c.copy(alpha = 0.35f), 1f to c.copy(alpha = 0f),
                    center = Offset(cx, cy), radius = rx * 1.35f
                ),
                radius = rx * 1.35f, center = Offset(cx, cy), blendMode = blendMode
            )
        }
    }
    if (blendMode == BlendMode.DstOver) {
        // Behind what is there: the top pools first, the base last.
        translate(dx, dy) {
            scale(k, k, pivot = Offset.Zero) {
                pool(300f, 700f, 160f, 90f, lerp(t, p, 0.3f))
                pool(70f, 600f, 190f, 130f, p)
                pool(240f, 470f, 180f, 140f, s)
                pool(30f, 330f, 170f, 150f, lerp(p, t, 0.45f))
                pool(300f, 150f, 190f, 170f, p)
            }
        }
        drawRect(t, size = screen, blendMode = blendMode)
        return
    }
    drawRect(t, size = screen)
    translate(dx, dy) {
        scale(k, k, pivot = Offset.Zero) {
            pool(300f, 150f, 190f, 170f, p)
            pool(30f, 330f, 170f, 150f, lerp(p, t, 0.45f))
            pool(240f, 470f, 180f, 140f, s)
            pool(70f, 600f, 190f, 130f, p)
            pool(300f, 700f, 160f, 90f, lerp(t, p, 0.3f))
        }
    }
}

/**
 * A window in the page: clears its shape in the page's offscreen layer, so Android's wallpaper (or, with Colors only,
 * the mesh drawn behind the page) shows through. What the composable draws after this sits on it.
 */
internal fun Modifier.windowHole(shape: androidx.compose.ui.graphics.Shape): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawBehind { drawOutline(outline, Color.Black, blendMode = androidx.compose.ui.graphics.BlendMode.Clear) }
}

/**
 * A window onto [look]: on its own wallpaper where the page shows looks that way (the band of the picture a card
 * looks at, as wide as the window), otherwise a hole to what is behind the page. What is drawn after sits on it.
 */
@Composable
internal fun Modifier.lookWindow(look: Look, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val store = LocalWallpaperStore.current
    val wallpaper = if (store != null) wallpaperOf(look, look.appDark(MaterialTheme.colorScheme.dark)) else null
    if (store == null || wallpaper == null) return windowHole(shape)
    KeepWallpaper(store, wallpaper, "card")
    return this.drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val place = store.size.placeIn(size.width, size.height, wallpaper.focus)
        onDrawBehind { clipPath(path) { drawWallpaper(store, wallpaper, place[0], place[1], place[2], whole = false) } }
    }
}

/**
 * [wallpaper] with the top left of its whole picture at [left], [top], [scale] px here to one of its own: the best
 * of what is in memory. The whole-screen picture when [whole] asks for it and it is there; otherwise the small
 * picture (or the wallpaper's base color before even that is drawn) with the sharp band a card looks at over it.
 */
internal fun DrawScope.drawWallpaper(store: WallpaperStore, wallpaper: Wallpaper, left: Float, top: Float, scale: Float, whole: Boolean) {
    val w = store.size.width * scale
    val h = store.size.height * scale
    val at = IntOffset(left.roundToInt(), top.roundToInt())
    val area = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
    val sharp = if (whole) store.fulls[wallpaper.id] else null
    if (sharp != null) {
        drawImage(sharp, dstOffset = at, dstSize = area)
        return
    }
    val small = store.thumbs[wallpaper.id]
    if (small != null) drawImage(small, dstOffset = at, dstSize = area)
    else drawRect(Color(wallpaper.base), Offset(at.x.toFloat(), at.y.toFloat()), Size(area.width.toFloat(), area.height.toFloat()))
    val band = store.cards[wallpaper.id] ?: return
    val bandTop = (top + store.size.cardTop(wallpaper.focus) * scale).roundToInt()
    val bandBottom = (top + (store.size.cardTop(wallpaper.focus) + store.size.cardHeight) * scale).roundToInt()
    drawImage(band, dstOffset = IntOffset(at.x, bandTop), dstSize = IntSize(area.width, (bandBottom - bandTop).coerceAtLeast(1)))
}
