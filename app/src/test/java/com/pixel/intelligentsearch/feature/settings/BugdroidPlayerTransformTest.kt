package com.pixel.intelligentsearch.feature.settings

import android.graphics.Matrix
import android.view.TextureView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            val expected = originalFullFrameMatrix(w.toFloat(), h.toFloat(), clip.fittedScale(w.toFloat(), h.toFloat(), scale))

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

    /** Narrow views used to crop the backup bugdroid's fan; the whole character must always stay inside. */
    @Test
    fun theWholeCharacterStaysInsideTheView() {
        // The last two: the backup page's short, wide bugdroid at its smallest (120 dp) and as fitted on a 412 dp phone.
        val sizes = listOf(1440 to 1190, 560 to 700, 1440 to 962, 300 to 900, 984 to 1020, 1140 to 1020, 280 to 280, 760 to 240, 760 to 286)
        for (clip in BugdroidClip.entries) for ((w, h) in sizes) for (scale in listOf(0.69f, 0.95f, 1f)) {
            val view = TextureView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, w, h)
            applyClipTransform(view, clip, scale)
            // The character rectangle in the cropped stream's view coordinates (the buffer is stretched over the view).
            val corners = floatArrayOf(
                (clip.characterLeft - clip.cropLeft).toFloat() / clip.cropWidth * w,
                (clip.characterTop - clip.cropTop).toFloat() / clip.cropHeight * h,
                (clip.characterRight - clip.cropLeft).toFloat() / clip.cropWidth * w,
                (clip.characterBottom - clip.cropTop).toFloat() / clip.cropHeight * h
            )
            view.getTransform(null).mapPoints(corners)
            val where = "$clip $w x $h @$scale"
            assertTrue("$where left ${corners[0]}", corners[0] >= -0.5f)
            assertTrue("$where top ${corners[1]}", corners[1] >= -0.5f)
            assertTrue("$where right ${corners[2]}", corners[2] <= w + 0.5f)
            assertTrue("$where bottom ${corners[3]}", corners[3] <= h + 0.5f)
        }
    }

    /** The backup bugdroid's shadows are drawn at this scale, so it must be the scale the video is drawn at. */
    @Test
    fun framePixelsScaleLikeTheVideo() {
        for ((w, h) in listOf(760 to 240, 760 to 680, 1440 to 680, 300 to 900)) {
            val view = TextureView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, w, h)
            val clip = BugdroidClip.BACKUP
            applyClipTransform(view, clip, 1f)
            // Two frame points 1000 px apart, as they sit in the cropped stream stretched over the view.
            val points = floatArrayOf(clip.cropLeft.toFloat(), clip.cropTop.toFloat(), clip.cropLeft + 1000f, clip.cropTop.toFloat())
            for (i in points.indices step 2) {
                points[i] = (points[i] - clip.cropLeft) / clip.cropWidth * w
                points[i + 1] = (points[i + 1] - clip.cropTop) / clip.cropHeight * h
            }
            view.getTransform(null).mapPoints(points)
            assertEquals("$w x $h", clip.viewPixelsPerFramePixel(w.toFloat(), h.toFloat(), 1f) * 1000f, points[2] - points[0], 0.5f)
        }
    }

    @Test
    fun aCharacterThatAlreadyFitsKeepsItsSize() {
        // The welcome card on a 527 dp wide phone at 2.55x: 1140 x 701 px with the clip at 0.95.
        assertEquals(0.95f, BugdroidClip.WELCOME.fittedScale(1140f, 701f, 0.95f), 0.0001f)
    }

    /** The poster stands in for the video until it draws, so it must cover exactly the video's place. */
    @Test
    fun thePosterIsDrawnWhereTheVideoIs() {
        for (clip in BugdroidClip.entries) for ((w, h) in listOf(1440 to 1190, 560 to 700, 760 to 240)) {
            val view = TextureView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, w, h)
            applyClipTransform(view, clip, 0.95f)
            val corners = floatArrayOf(0f, 0f, w.toFloat(), h.toFloat())
            view.getTransform(null).mapPoints(corners)
            val rect = clip.videoRectInView(w.toFloat(), h.toFloat(), 0.95f)
            val where = "$clip $w x $h"
            assertEquals(where, corners[0], rect.left, 0.5f)
            assertEquals(where, corners[1], rect.top, 0.5f)
            assertEquals(where, corners[2], rect.right, 0.5f)
            assertEquals(where, corners[3], rect.bottom, 0.5f)
        }
    }

    /** The shadows are cast onto the floor the feet stand on: just above the lowest point the character reaches. */
    @Test
    fun theGroundIsWhereTheFeetAre() {
        for (clip in BugdroidClip.entries) {
            assertTrue("$clip", clip.groundY <= clip.characterBottom)
            assertTrue("$clip", clip.groundY >= clip.characterBottom - 40)
        }
    }

    /** Android turns a blur radius into a sigma of 0.57735 * radius + 0.5; the shadows are specified as sigmas. */
    @Test
    fun blurRadiiGiveTheShadowsTheirSigma() {
        for (sigma in listOf(0.5f, 4f, 10f, 42f)) assertEquals(sigma, 0.57735f * blurRadiusForSigma(sigma) + 0.5f, 0.001f)
        assertEquals(0f, blurRadiusForSigma(0.2f), 0f)
    }
}
