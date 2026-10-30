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
 * Keeps what a page draws, so a picture of it can be taken the moment another page opens over it (the back animation
 * shows it underneath). The page is recorded into a render node as it draws, and the picture is made by one renderer
 * kept for as long as the page is: making a renderer for each picture (as Compose's own GraphicsLayer.toImageBitmap
 * does) held every tap that opened a page for a noticeable moment.
 */
internal class PageSnapshotter {
    private val node = RenderNode("SettingsPage")
    private var renderer: HardwareRenderer? = null
    private var reader: ImageReader? = null
    // Off for good once recording or a picture has failed (a test environment without a GPU), so the page still draws.
    private var broken = false

    /** Records what [scope] draws as its content, beside drawing it as usual. */
    fun record(scope: ContentDrawScope) {
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
    }

    /** A picture of the page as last drawn, or null when none can be made. */
    fun capture(): ImageBitmap? {
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
