package com.pixel.intelligentsearch.core.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap

/**
 * Takes a picture of a page as another page opens over it (the back animation shows it underneath): a copy of the
 * window as it was last drawn, made by Android's own [PixelCopy], as the system takes its screenshots.
 *
 * Nothing here ever waits on the main thread. A renderer of the app's own, drawing the page again into a buffer of its
 * own, froze the app on the owner's phone: waiting for it held the main thread until Android called the app not
 * responding, and not waiting, its first draw into a fresh buffer timed out after four seconds, in which no frame was
 * drawn. The copy is asked for from a thread of its own, which waits the few milliseconds the render thread takes to
 * make it, keeps it in graphics memory and hands it over on the main thread.
 */
internal class PageSnapshotter(private val window: () -> Window?) {
    private val thread = HandlerThread("PagePicture").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    // The copy's destination, kept from one picture to the next (16 MB at the owner's phone's size); the worker's only.
    private var scratch: Bitmap? = null
    @Volatile private var released = false
    // Robolectric's PixelCopy draws the views itself, on the thread that asks, which Compose can't have off the main
    // thread: no pictures there, as there were none from the renderer before it.
    private val pictureless = Build.FINGERPRINT == "robolectric"

    /**
     * Takes a picture of [bounds] (in window coordinates) of the window as it was last drawn, handed to [onPicture] on
     * the main thread a few milliseconds later (null when none could be made).
     */
    fun take(bounds: Rect, onPicture: (ImageBitmap?) -> Unit) {
        val window = window()
        if (window == null || bounds.isEmpty || released || pictureless) return onPicture(null)
        val copyOf = Rect(bounds)
        worker.post { copy(window, copyOf, onPicture) }
    }

    private fun copy(window: Window, bounds: Rect, onPicture: (ImageBitmap?) -> Unit) {
        val dest = runCatching {
            if (released) return deliver(null, onPicture)
            scratch?.takeIf { it.width == bounds.width() && it.height == bounds.height() }
                ?: createBitmap(bounds.width(), bounds.height()).also {
                    scratch?.recycle()
                    scratch = it
                }
        }.getOrNull() ?: return deliver(null, onPicture)
        runCatching {
            PixelCopy.request(window, bounds, dest, { result ->
                // Back on the worker once the render thread has made the copy.
                val picture = if (result == PixelCopy.SUCCESS && !released) {
                    // A copy of its own, in graphics memory: the next picture is made in the same bitmap.
                    runCatching { dest.copy(Bitmap.Config.HARDWARE, false)?.asImageBitmap() }.getOrNull()
                } else {
                    null
                }
                deliver(picture, onPicture)
            }, worker)
        }.onFailure { deliver(null, onPicture) }
    }

    private fun deliver(picture: ImageBitmap?, onPicture: (ImageBitmap?) -> Unit) {
        main.post { onPicture(picture) }
    }

    fun release() {
        released = true
        main.removeCallbacksAndMessages(null)
        worker.post {
            scratch?.recycle()
            scratch = null
            thread.quitSafely()
        }
    }
}
