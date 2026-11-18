@file:Suppress("RestrictedApi")

package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.core.graphics.scale
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Parcel
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.Score
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Where Gallery's wallpapers live once drawn.
 *
 * Each is drawn on the phone at the screen's own size, on a background thread, and kept as a lossless PNG in the
 * app's cache folder, so it is drawn once. Three sizes of each are used:
 * - a small one of the whole picture, drawn in a few milliseconds and kept in memory: what shows first;
 * - the band a card's window looks at, at full sharpness (drawn alone: a third of the work);
 * - the whole screen, for the full preview.
 * The wider picture the home screen can slide across is drawn only when the owner sets it as the phone's wallpaper.
 */

/** Bump when any wallpaper's drawing changes: pictures cached by an older build are dropped. */
internal const val WALLPAPER_VERSION = 1

/** How much of the picture's height a card's band holds. */
private const val CARD_SHARE = 0.34f

/** The small picture's height as a share of the screen's. */
private const val THUMB_SHARE = 1f / 6f

/** The screen the wallpapers are for: [width] by [height] px, and [fullWidth] px with the part the home screen slides to. */
internal data class WallpaperSize(val width: Int, val height: Int, val fullWidth: Int = width) {
    val cardHeight: Int get() = (height * CARD_SHARE).roundToInt()

    /** The top of the band a card shows, for a picture whose interest sits at [focus]; on a multiple of 8 px, so wide blurs line up. */
    fun cardTop(focus: Float): Int = ((focus * height - cardHeight / 2f).roundToInt().coerceIn(0, height - cardHeight) / 8) * 8

    /**
     * Where the whole picture lies when a window [windowWidth] by [windowHeight] looks at its band: as wide as the
     * window, the band centered. Left, top, and the scale from picture px to window px.
     */
    fun placeIn(windowWidth: Float, windowHeight: Float, focus: Float): FloatArray {
        val scale = max(windowWidth / width, windowHeight / cardHeight)
        val center = cardTop(focus) + cardHeight / 2f
        return floatArrayOf((windowWidth - width * scale) / 2f, windowHeight / 2f - center * scale, scale)
    }

    companion object {
        /**
         * The phone's screen at its sharpest setting (a Pixel set to draw at a lower resolution still gets a
         * wallpaper for the panel's real one), upright, and the width Android asks wallpapers to have.
         */
        fun of(context: Context): WallpaperSize {
            val wm = context.getSystemService(WindowManager::class.java)
            val bounds = wm.maximumWindowMetrics.bounds
            val nowW = min(bounds.width(), bounds.height()).coerceAtLeast(1)
            val nowH = max(bounds.width(), bounds.height()).coerceAtLeast(1)
            var w = nowW
            var h = nowH
            // Only a screen's own context knows its display; any other leaves the size as the window reports it.
            val modes = runCatching { context.display.supportedModes }.getOrNull() ?: emptyArray()
            for (mode in modes) {
                val mw = min(mode.physicalWidth, mode.physicalHeight)
                val mh = max(mode.physicalWidth, mode.physicalHeight)
                // The same panel at a finer setting: same shape, more pixels.
                if (mw > w && kotlin.math.abs(mw.toFloat() / mh - nowW.toFloat() / nowH) < 0.01f) {
                    w = mw
                    h = mh
                }
            }
            val asked = runCatching { WallpaperManager.getInstance(context).desiredMinimumWidth }.getOrDefault(0)
            val full = if (asked <= 0) w else (asked.toFloat() / nowW * w).roundToInt().coerceIn(w, w * 5 / 2)
            return WallpaperSize(w, h, full)
        }
    }
}

/**
 * What a wallpaper gives: the theme color Android would take from it ([seed]), the color of the part on screen taken
 * as a whole ([main]), and whether its top, under the status bar, is light.
 */
internal class WallpaperPalette(val seed: Int, val main: Int, val lightTop: Boolean)

internal object WallpaperColorMath {
    /** WallpaperColors.MAX_WALLPAPER_EXTRACTION_AREA: Android looks at no more than this many pixels. */
    private const val MAX_AREA = 112 * 112

    /**
     * The theme color Android takes from [bitmap]. Android's own WallpaperColors reads the picture: it shrinks it and
     * gathers its colors into at most 128, each with how much of the picture it covers. The system theme then scores
     * those, by how much of the picture a hue covers and how colorful it is, and takes the best. Material's Score is
     * that scoring.
     */
    fun seed(bitmap: Bitmap): Int {
        val counts = runCatching { counts(WallpaperColors.fromBitmap(bitmap)) }.getOrNull() ?: gather(bitmap)
        return Score.score(counts).first() or 0xFF000000.toInt()
    }

    /**
     * Each color WallpaperColors found and how many pixels it stands for. Android hands these to the system theme but
     * has no public getter for them; they are in what it writes to a Parcel: its main colors, then pairs of color and
     * count, then its hints. Null if an Android writes anything else, and [gather] is used instead.
     */
    fun counts(colors: WallpaperColors): Map<Int, Int>? {
        val parcel = Parcel.obtain()
        try {
            colors.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val mains = parcel.readInt()
            if (mains !in 1..256) return null
            repeat(mains) { parcel.readInt() }
            val n = parcel.readInt()
            if (n !in 1..1024 || parcel.dataAvail() != (n * 2 + 1) * 4) return null
            val map = LinkedHashMap<Int, Int>(n)
            repeat(n) {
                val color = parcel.readInt()
                map[color] = parcel.readInt()
            }
            return if (map.values.all { it >= 0 } && map.values.any { it > 0 }) map else null
        } catch (_: RuntimeException) {
            return null
        } finally {
            parcel.recycle()
        }
    }

    /**
     * The same gathering done here, for an Android whose WallpaperColors can't be read as above: the picture shrunk
     * as WallpaperColors shrinks it, and Material's Celebi quantizer, the one WallpaperColors is built on.
     */
    fun gather(bitmap: Bitmap): Map<Int, Int> {
        val area = bitmap.width * bitmap.height
        val small = if (area > MAX_AREA) {
            val k = sqrt(MAX_AREA / area.toDouble())
            bitmap.scale((bitmap.width * k).toInt().coerceAtLeast(1), (bitmap.height * k).toInt().coerceAtLeast(1), false)
        } else bitmap
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        if (small !== bitmap) small.recycle()
        return QuantizerCelebi.quantize(pixels, 128)
    }

    fun palette(bitmap: Bitmap, screenShare: Float): WallpaperPalette {
        val w = (bitmap.width * screenShare).toInt().coerceIn(1, bitmap.width)
        val screen = IntArray(w * bitmap.height)
        bitmap.getPixels(screen, 0, w, 0, 0, w, bitmap.height)
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in screen) {
            r += p shr 16 and 0xFF
            g += p shr 8 and 0xFF
            b += p and 0xFF
        }
        val n = screen.size
        val main = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        // Under the status bar: the top twentieth of the part that is on screen.
        val rows = (bitmap.height / 20).coerceAtLeast(1)
        var light = 0.0
        for (i in 0 until w * rows) light += Hct.fromInt(screen[i]).tone
        return WallpaperPalette(seed(bitmap), main, light / (w * rows) > 60.0)
    }

    /** The phone's colors for [seed]: Material's tonal spot scheme, the one Android builds from a wallpaper. */
    fun schemes(seed: Int): GallerySchemes {
        val source = Hct.fromInt(seed)
        val dark = SchemeTonalSpot(source, true, 0.0).compose(true)
        // As the app's theme: the light page is surfaceContainer.
        val light = SchemeTonalSpot(source, false, 0.0).compose(false).let { it.copy(background = it.surfaceContainer) }
        return GallerySchemes(dark, light)
    }

    private fun DynamicScheme.compose(dark: Boolean): ColorScheme {
        val s = this
        fun c(argb: Int) = Color(argb or 0xFF000000.toInt())
        return (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = c(s.primary), onPrimary = c(s.onPrimary), primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
            inversePrimary = c(s.inversePrimary),
            secondary = c(s.secondary), onSecondary = c(s.onSecondary), secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer),
            tertiary = c(s.tertiary), onTertiary = c(s.onTertiary), tertiaryContainer = c(s.tertiaryContainer), onTertiaryContainer = c(s.onTertiaryContainer),
            background = c(s.background), onBackground = c(s.onBackground),
            surface = c(s.surface), onSurface = c(s.onSurface), surfaceVariant = c(s.surfaceVariant), onSurfaceVariant = c(s.onSurfaceVariant),
            surfaceTint = c(s.primary), inverseSurface = c(s.inverseSurface), inverseOnSurface = c(s.inverseOnSurface),
            error = c(s.error), onError = c(s.onError), errorContainer = c(s.errorContainer), onErrorContainer = c(s.onErrorContainer),
            outline = c(s.outline), outlineVariant = c(s.outlineVariant), scrim = c(s.scrim),
            surfaceBright = c(s.surfaceBright), surfaceDim = c(s.surfaceDim),
            surfaceContainer = c(s.surfaceContainer), surfaceContainerHigh = c(s.surfaceContainerHigh), surfaceContainerHighest = c(s.surfaceContainerHighest),
            surfaceContainerLow = c(s.surfaceContainerLow), surfaceContainerLowest = c(s.surfaceContainerLowest)
        )
    }
}

/** The Your Colors looks of the fan: they take the owner's wallpaper and its colors, as Yours Now does. */
internal val ON_YOUR_WALLPAPER = setOf("square", "tide", "clay", "meadow")

/**
 * The wallpaper [look] comes with: a built-in look's own, or the one of the look a saved copy was made from. With
 * [dark] false, its twin for the light theme where it has one. Null for a look the owner made: that has none.
 */
internal fun wallpaperOf(look: Look, dark: Boolean = true): Wallpaper? {
    // Your Colors are shown on the owner's own wallpaper, in its colors: they have no picture of their own.
    if (look.id in ON_YOUR_WALLPAPER || look.base in ON_YOUR_WALLPAPER) return null
    val own = WALLPAPERS[look.id] ?: look.base?.let { WALLPAPERS[it] } ?: return null
    return if (dark) own else WALLPAPERS[own.id + ".light"] ?: own
}

/**
 * Draws, keeps and hands out the wallpapers for one screen size. The maps are Compose state, read while drawing and
 * written on the main thread only; the drawing and the files happen on one background thread.
 */
internal class WallpaperStore(private val dir: File, val size: WallpaperSize) {
    val thumbs = mutableStateMapOf<String, ImageBitmap>()
    val cards = mutableStateMapOf<String, ImageBitmap>()
    val fulls = mutableStateMapOf<String, ImageBitmap>()
    /** The whole picture, small: what a strip of the swatch fan shows (most of the picture, at a strip's width). */
    val strips = mutableStateMapOf<String, ImageBitmap>()
    val palettes = mutableStateMapOf<String, WallpaperPalette>()

    private val main = Handler(Looper.getMainLooper())
    private val lock = ReentrantLock()
    private val work = lock.newCondition()
    private val jobs = ArrayDeque<Pair<String, () -> Unit>>()
    private val waiting = HashSet<String>()
    private var worker: Thread? = null

    /** How many windows show each picture now ("card:ink"), and the ones nobody shows, oldest first. */
    private val users = HashMap<String, Int>()
    private val spare = ArrayDeque<String>()
    private val schemes = HashMap<Int, GallerySchemes>()
    private var started = false

    /** Set when the screen changed size and another store took over: nothing more is drawn or written for this one. */
    @Volatile private var closed = false

    /** Stops this store: the pictures still waiting aren't drawn, and its background thread ends. */
    fun close() {
        closed = true
        lock.withLock {
            jobs.clear()
            work.signalAll()
        }
    }

    /** How many times a picture has been drawn rather than read back (tests check that a second ask draws nothing). */
    @Volatile var drawn = 0
        private set

    private fun file(id: String, kind: String) = File(dir, "v$WALLPAPER_VERSION-$id-${size.width}x${size.height}-$kind.png")

    private fun publish(action: () -> Unit) {
        if (inline || Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    private fun submit(key: String, urgent: Boolean, job: () -> Unit) {
        if (inline) return job()
        lock.withLock {
            if (!waiting.add(key)) {
                // Already waiting: an urgent ask moves it to the front.
                if (urgent) jobs.firstOrNull { it.first == key }?.let { jobs.remove(it); jobs.addFirst(it) }
                return
            }
            if (urgent) jobs.addFirst(key to job) else jobs.addLast(key to job)
            if (worker == null) {
                worker = Thread({ loop() }, "gallery-wallpapers").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY - 1
                    start()
                }
            }
            work.signalAll()
        }
    }

    private fun loop() {
        while (!closed) {
            val job = lock.withLock {
                while (jobs.isEmpty() && !closed) work.await()
                if (closed) return
                jobs.removeFirst()
            }
            runCatching { job.second() }
            lock.withLock { waiting.remove(job.first) }
        }
    }

    private fun read(file: File): Bitmap? {
        if (!file.isFile) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) }.getOrNull()
        if (bitmap == null) file.delete()
        return bitmap
    }

    /** Written beside the file and moved into place, so a half-written picture is never read back. */
    private fun write(file: File, bitmap: Bitmap) {
        if (closed) return
        runCatching {
            dir.mkdirs()
            val part = File(dir, file.name + ".part")
            part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!part.renameTo(file)) part.delete()
        }
    }

    private fun draw(wallpaper: Wallpaper, region: Rect?, scale: Float = 1f, fullWidth: Int = size.width): Bitmap {
        drawn++
        return WallpaperRenderer.render(wallpaper.design, size.width, size.height, fullWidth, region, scale)
    }

    private fun cardRegion(wallpaper: Wallpaper): Rect {
        val top = size.cardTop(wallpaper.focus)
        return Rect(0, top, size.width, top + size.cardHeight)
    }

    /** Reads [kind] of [wallpaper] back from the cache folder, or draws it and puts it there. */
    private fun obtain(wallpaper: Wallpaper, kind: String, onReady: (Bitmap) -> Unit) {
        val file = file(wallpaper.id, kind)
        val cached = read(file)
        if (cached != null) return onReady(cached)
        val bitmap = when (kind) {
            "card" -> draw(wallpaper, cardRegion(wallpaper))
            "strip" -> draw(wallpaper, null, STRIP_SHARE)
            else -> draw(wallpaper, null)
        }
        onReady(bitmap)
        write(file, bitmap)
    }

    /**
     * Called once the page shows: drops pictures an older build left, draws the small pictures and works out each
     * wallpaper's colors, then fills the cache folder in the background so nothing has to be drawn while scrolling.
     */
    fun start(ids: List<String>) {
        if (started) return
        started = true
        // In the page's order, each with its light-theme twin where it has one.
        val all = ids.flatMap { listOfNotNull(WALLPAPERS[it], WALLPAPERS["$it.light"]) }
        // The small pictures first, ahead of anything a card has already asked for: they take a few milliseconds
        // each and give every window something to show at once.
        submit("thumbs", urgent = true) {
            for (w in all) {
                val bitmap = draw(w, null, THUMB_SHARE).asImageBitmap()
                publish { thumbs[w.id] = bitmap }
            }
        }
        submit("palettes", urgent = false) {
            // Pictures an older build left, pictures for another screen size (a foldable's other screen: the app
            // draws them again if it goes back to it), and any that were cut off half written.
            val stale = System.currentTimeMillis() - 60_000
            val mine = "-${size.width}x${size.height}-"
            dir.listFiles()?.forEach {
                if (!it.name.startsWith("v$WALLPAPER_VERSION-") || !it.name.contains(mine) || (it.name.endsWith(".part") && it.lastModified() < stale)) it.delete()
            }
            for (w in all) {
                val palette = palette(w)
                publish { palettes[w.id] = palette }
            }
        }
        if (inline) return
        for (kind in listOf("strip", "card", "full")) for (w in all) {
            submit("fill:$kind:${w.id}", urgent = false) {
                val file = file(w.id, kind)
                if (!file.isFile) write(file, when (kind) {
                    "card" -> draw(w, cardRegion(w))
                    "strip" -> draw(w, null, STRIP_SHARE)
                    else -> draw(w, null)
                })
            }
        }
    }

    /**
     * The colors Android would take from [wallpaper]: from the whole picture it would be handed, slide-to part and
     * all, drawn small (Android shrinks what it is given before it looks).
     */
    fun palette(wallpaper: Wallpaper): WallpaperPalette {
        val small = draw(wallpaper, null, 256f / size.height, size.fullWidth)
        return WallpaperColorMath.palette(small, size.width.toFloat() / size.fullWidth).also { small.recycle() }
    }

    fun schemes(seed: Int): GallerySchemes = synchronized(schemes) { schemes.getOrPut(seed) { WallpaperColorMath.schemes(seed) } }

    /** The colors for [wallpaper]: worked out from its picture once that is done, the reference screen's until then. */
    fun schemes(wallpaper: Wallpaper): GallerySchemes = schemes(palettes[wallpaper.id]?.seed ?: wallpaper.seed)

    private fun map(kind: String) = when (kind) {
        "card" -> cards
        "strip" -> strips
        else -> fulls
    }

    /** A window starts showing [kind] ("card" or "full") of [id]: it is read or drawn if it isn't in memory. */
    fun retain(kind: String, id: String) {
        val key = "$kind:$id"
        users[key] = (users[key] ?: 0) + 1
        spare.remove(key)
        val wallpaper = WALLPAPERS[id] ?: return
        if (map(kind).containsKey(id)) return
        submit(key, urgent = true) {
            obtain(wallpaper, kind) { bitmap ->
                val image = bitmap.asImageBitmap()
                publish { if (users.containsKey(key) || spare.contains(key)) map(kind)[id] = image }
            }
        }
    }

    /** The window is gone. A few pictures nobody shows are kept for coming back to; older ones leave memory. */
    fun release(kind: String, id: String) {
        val key = "$kind:$id"
        val n = (users[key] ?: 0) - 1
        if (n > 0) {
            users[key] = n
            return
        }
        users.remove(key)
        spare.addLast(key)
        val keep = when (kind) {
            "card" -> SPARE_CARDS
            "strip" -> SPARE_STRIPS
            else -> SPARE_FULLS
        }
        while (spare.count { it.startsWith("$kind:") } > keep) {
            val old = spare.first { it.startsWith("$kind:") }
            spare.remove(old)
            map(kind).remove(old.substringAfter(':'))
        }
    }

    /** Whether [kind] of [id] is already in the cache folder. */
    fun cached(kind: String, id: String): Boolean = file(id, kind).isFile

    /**
     * The picture to hand Android as the phone's wallpaper, as a PNG file: the screen's part on the left, and to its
     * right the part the home screen can slide to. Drawn on first use; call off the main thread.
     */
    fun wallpaperFile(wallpaper: Wallpaper): File {
        if (size.fullWidth <= size.width) {
            val file = file(wallpaper.id, "full")
            if (!file.isFile) write(file, draw(wallpaper, null))
            return file
        }
        val file = file(wallpaper.id, "wide${size.fullWidth}")
        if (!file.isFile) {
            val bitmap = draw(wallpaper, null, fullWidth = size.fullWidth)
            write(file, bitmap)
            bitmap.recycle()
        }
        return file
    }

    companion object {
        private const val SPARE_CARDS = 6
        private const val SPARE_FULLS = 1
        private const val SPARE_STRIPS = 8
        /** A strip's picture: the whole wallpaper at this share of the screen's resolution (a strip is about a third of its width). */
        const val STRIP_SHARE = 0.36f

        /** Tests draw on the calling thread, so a screenshot never waits on a background one. */
        @Volatile var inline = false

        private val stores = HashMap<WallpaperSize, WallpaperStore>()

        /**
         * The store for [size]: one at a time, for the screen the app is on now. Asked for another size (the phone was
         * unfolded or folded), the old one leaves memory and its pictures leave the cache folder once the new one starts.
         */
        fun get(context: Context, size: WallpaperSize): WallpaperStore = synchronized(stores) {
            stores.keys.filter { it != size }.forEach { stores.remove(it)?.close() }
            stores.getOrPut(size) { WallpaperStore(File(context.applicationContext.cacheDir, "gallery_wallpapers"), size) }
        }

        /** Tests start from nothing in memory. */
        fun forget() = synchronized(stores) { stores.clear() }
    }
}

/** The store while Gallery shows each look on its own wallpaper; null on "Your wallpaper" and "Colors only". */
internal val LocalWallpaperStore = staticCompositionLocalOf<WallpaperStore?> { null }

/** Screenshot tests fix the wallpaper's size (a tall capture's screen is not a phone's). */
internal val LocalGalleryWallpaperSize = staticCompositionLocalOf<WallpaperSize?> { null }

/**
 * The store for the screen the app is on now. Keyed on the window's size, so unfolding or folding the phone (which
 * doesn't restart the app) gets wallpapers drawn for the new screen, sharp, rather than the old screen's scaled up.
 */
@Composable
internal fun rememberWallpaperStore(): WallpaperStore {
    val context = LocalContext.current
    val fixed = LocalGalleryWallpaperSize.current
    val window = LocalWindowInfo.current.containerSize
    val configuration = LocalConfiguration.current
    return remember(context, fixed, window, configuration.densityDpi) {
        WallpaperStore.get(context, fixed ?: WallpaperSize.of(context))
    }
}

/** Keeps [kind] of [wallpaper] in memory while this is on screen. */
@Composable
internal fun KeepWallpaper(store: WallpaperStore, wallpaper: Wallpaper, kind: String) {
    DisposableEffect(store, wallpaper.id, kind) {
        store.retain(kind, wallpaper.id)
        onDispose { store.release(kind, wallpaper.id) }
    }
}

/**
 * The colors [look] is drawn in: with its own wallpaper behind it, the colors the phone would take from that
 * wallpaper, through the same rules the phone uses; otherwise the phone's colors as they are now.
 */
@Composable
internal fun rememberLookSchemes(look: Look): GallerySchemes {
    val store = LocalWallpaperStore.current
    val wallpaper = if (store != null) wallpaperOf(look, look.appDark(MaterialTheme.colorScheme.dark)) else null
    if (store == null || wallpaper == null) return rememberGallerySchemes()
    return store.schemes(wallpaper)
}
