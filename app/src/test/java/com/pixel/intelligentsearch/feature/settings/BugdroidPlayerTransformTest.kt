package com.pixel.intelligentsearch.feature.settings

import android.graphics.Matrix
import android.view.TextureView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The clips are stored cropped. Their on-screen placement must equal what the original full-frame player
 * produced: the full 1920x1080 frame stretched over the view, then fill-scaled about the centre.
 */
@RunWith(RobolectricTestRunner::class)
class BugdroidPlayerTransformTest {

    private fun originalFullFrameMatrix(vw: Float, vh: Float, scaleFactor: Float): Matrix {
        val aspect = 1920f / 1080f
        var sx = 1f
        var sy = 1f
        if (vw / vh > aspect) sy = (vw / aspect) / vh else sx = (vh * aspect) / vw
        return Matrix().apply { setScale(sx * scaleFactor, sy * scaleFactor, vw / 2f, vh / 2f) }
    }

    @Test
    fun croppedClipsLandWhereTheFullFramePutThem() {
        val sizes = listOf(1440 to 1190, 560 to 700, 1440 to 962, 300 to 900)
        for (clip in BugdroidClip.entries) for ((w, h) in sizes) for (scale in listOf(0.69f, 0.95f, 1f)) {
            val view = TextureView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, w, h)
            applyClipTransform(view, clip, scale)
            val actual = view.getTransform(null)
            val expected = originalFullFrameMatrix(w.toFloat(), h.toFloat(), scale)

            // Corners of the crop: in the cropped stream they're the view corners (TextureView stretches the
            // buffer over the view); in the full frame they're the crop rectangle, stretched by vw/1920, vh/1080.
            val cropCorners = floatArrayOf(0f, 0f, w.toFloat(), 0f, 0f, h.toFloat(), w.toFloat(), h.toFloat())
            val fullFrameCorners = floatArrayOf(
                clip.cropLeft.toFloat(), clip.cropTop.toFloat(),
                (clip.cropLeft + clip.cropWidth).toFloat(), clip.cropTop.toFloat(),
                clip.cropLeft.toFloat(), (clip.cropTop + clip.cropHeight).toFloat(),
                (clip.cropLeft + clip.cropWidth).toFloat(), (clip.cropTop + clip.cropHeight).toFloat()
            ).also { pts -> for (i in pts.indices) pts[i] *= if (i % 2 == 0) w / 1920f else h / 1080f }
            actual.mapPoints(cropCorners)
            expected.mapPoints(fullFrameCorners)
            for (i in cropCorners.indices) assertEquals("$clip $w x $h @$scale [$i]", fullFrameCorners[i], cropCorners[i], 0.5f)
        }
    }
}
