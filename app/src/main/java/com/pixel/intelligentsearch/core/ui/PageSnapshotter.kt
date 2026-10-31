package com.pixel.intelligentsearch.core.ui

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.draw

/**
 * Takes a picture of a page as another page opens over it (the back animation shows it underneath). Asked for with
 * [arm], the page's next draw is recorded into a render node ([record]), and the picture is made from it by one
 * renderer kept for as long as the page is, then handed to [arm]'s callback.
 *
 * Nothing here ever waits for the picture. Waiting for the renderer to finish, from the main thread while Android's
 * render thread was busy with the window's own frame, froze the app until Android called it not responding (three
 * times on the owner's phone, in Settings). So the picture is asked for after the page's draw is done, and arrives a
 * few milliseconds later on its own. Only that one draw is recorded: recording every frame doubled the work of each
 * frame. Making a renderer for each picture, as Compose's own GraphicsLayer.toImageBitmap does, held every tap.
 */
internal class PageSnapshotter {
    private val node = RenderNode("SettingsPage")
    private val main = Handler(Looper.getMainLooper())
    private var renderer: HardwareRenderer? = null
    private var reader: ImageReader? = null
    // Off for good once recording or a picture has failed (a test environment without a GPU), so the page still draws.
    private var broken = false
    private var armed: ((ImageBitmap?) -> Unit)? = null
    private var waiting: ((ImageBitmap?) -> Unit)? = null

    /** Asks for a picture of the page's next draw, handed to [onPicture] (null when none could be made). */
    fun arm(onPicture: (ImageBitmap?) -> Unit) {
        armed = onPicture
    }

    /** When a picture was asked for, records what [scope] draws as its content, beside drawing it as usual. */
    fun record(scope: ContentDrawScope) {
        val onPicture = armed ?: return
        armed = null
        val width = scope.size.width.toInt()
        val height = scope.size.height.toInt()
        if (broken || width <= 0 || height <= 0) return onPicture(null)
        runCatching {
            node.setPosition(0, 0, width, height)
            val canvas = node.beginRecording(width, height)
            try {
                scope.draw(scope, scope.layoutDirection, androidx.compose.ui.graphics.Canvas(canvas), scope.size) {
                    scope.drawContent()
                }
            } finally {
                node.endRecording()
            }
        }.onFailure {
            broken = true
            return onPicture(null)
        }
        // Asked for once the window's frame is drawn, never from inside it.
        main.post { render(onPicture) }
    }

    private fun render(onPicture: (ImageBitmap?) -> Unit) {
        val width = node.width
        val height = node.height
        runCatching {
            if (reader?.let { it.width != width || it.height != height } != false) newReader(width, height)
            // A picture still on its way is dropped for the newer one.
            waiting?.invoke(null)
            waiting = onPicture
            renderer?.createRenderRequest()?.setWaitForPresent(false)?.syncAndDraw()
        }.onFailure {
            broken = true
            waiting = null
            onPicture(null)
        }
    }

    /** The picture has been drawn into [imageReader]: hands a copy of it to whoever asked. */
    private fun deliver(imageReader: ImageReader) {
        val onPicture = waiting ?: return run { runCatching { imageReader.acquireLatestImage()?.close() } }
        waiting = null
        val picture = runCatching {
            val image = imageReader.acquireLatestImage() ?: return@runCatching null
            try {
                val buffer = image.hardwareBuffer ?: return@runCatching null
                try {
                    val shown = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)) ?: return@runCatching null
                    // A copy of its own, so the next picture, drawn into the same buffers, can't paint over this one.
                    (shown.copy(Bitmap.Config.HARDWARE, false) ?: shown.copy(Bitmap.Config.ARGB_8888, false))?.asImageBitmap()
                } finally {
                    buffer.close()
                }
            } finally {
                image.close()
            }
        }.getOrNull()
        onPicture(picture)
    }

    private fun newReader(width: Int, height: Int) {
        reader?.close()
        renderer?.destroy()
        val newReader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        newReader.setOnImageAvailableListener(::deliver, main)
        reader = newReader
        renderer = HardwareRenderer().apply {
            setSurface(newReader.surface)
            setContentRoot(node)
            isOpaque = false
        }
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        waiting = null
        armed = null
        renderer?.destroy()
        renderer = null
        reader?.close()
        reader = null
        node.discardDisplayList()
    }
}
