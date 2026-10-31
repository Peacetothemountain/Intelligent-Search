package com.pixel.intelligentsearch.core.ui

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.draw

/**
 * Takes a picture of a page as another page opens over it (the back animation shows it underneath). Asked for with
 * [arm], the page's next draw is recorded into a render node and pictured at once ([record]), then [take]n. Only that
 * one draw is recorded: recording every frame doubled the work of each frame, and on Main, under the bugdroid's
 * shadows, frames missed their time. The picture is made by one renderer kept for as long as the page is: making a
 * renderer for each picture (as Compose's own GraphicsLayer.toImageBitmap does) held every tap that opened a page.
 */
internal class PageSnapshotter {
    private val node = RenderNode("SettingsPage")
    private var renderer: HardwareRenderer? = null
    private var reader: ImageReader? = null
    // Off for good once recording or a picture has failed (a test environment without a GPU), so the page still draws.
    private var broken = false
    private var armed = false
    private var taken: ImageBitmap? = null

    /** Asks for a picture of the page's next draw. */
    fun arm() {
        armed = true
        taken = null
    }

    /** The picture asked for with [arm], once the page has drawn; null when none could be made. */
    fun take(): ImageBitmap? = taken.also { taken = null }

    /** When a picture was asked for, records what [scope] draws as its content (beside drawing it as usual) and pictures it. */
    fun record(scope: ContentDrawScope) {
        if (!armed) return
        armed = false
        if (broken) return
        val width = scope.size.width.toInt()
        val height = scope.size.height.toInt()
        if (width <= 0 || height <= 0) return
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
        }.onFailure { broken = true }
        taken = capture()
    }

    /** A picture of the page as last recorded, or null when none can be made. */
    private fun capture(): ImageBitmap? {
        if (broken) return null
        val width = node.width
        val height = node.height
        if (width <= 0 || height <= 0) return null
        return runCatching {
            val imageReader = reader?.takeIf { it.width == width && it.height == height } ?: newReader(width, height)
            renderer?.createRenderRequest()?.setWaitForPresent(true)?.syncAndDraw()
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
        }.onFailure { broken = true }.getOrNull()
    }

    private fun newReader(width: Int, height: Int): ImageReader {
        reader?.close()
        renderer?.destroy()
        val newReader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        reader = newReader
        renderer = HardwareRenderer().apply {
            setSurface(newReader.surface)
            setContentRoot(node)
            isOpaque = false
        }
        return newReader
    }

    fun release() {
        renderer?.destroy()
        renderer = null
        reader?.close()
        reader = null
        node.discardDisplayList()
    }
}
