package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.net.HttpsClient
import com.pixel.intelligentsearch.core.net.HttpsGet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

/** Pictures for result rows: entity images and video frames, decoded at the row's size and cached. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResultThumbnailsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val originalDownload = ResultThumbnails.download
    private val originalHttp = ResultThumbnails.http

    @Before fun startEmpty() = ResultThumbnails.clear()

    @After fun restore() {
        ResultThumbnails.download = originalDownload
        ResultThumbnails.http = originalHttp
        ResultThumbnails.clear()
        ShadowMediaMetadataRetriever.reset()
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test
    fun anEntityPictureIsFetchedOnceAndKeptAtTheRowsSize() = runBlocking {
        val fetches = AtomicInteger()
        val bytes = png(400, 400)
        ResultThumbnails.download = { fetches.incrementAndGet(); bytes }
        val source = ThumbnailSource.Web("https://encrypted-tbn0.gstatic.com/images?q=tbn:obama")

        val image = ResultThumbnails.load(context, source, px = 80)
        assertNotNull(image)
        // Sampled down toward the row's size rather than decoded at full size, never below it.
        assertEquals(100, image!!.width)
        assertNotNull(ResultThumbnails.peek(source, 80))
        ResultThumbnails.load(context, source, px = 80)
        assertEquals(1, fetches.get())
    }

    @Test
    fun anEntityPictureIsFetchedWithTheAppsHttpsClientUpToHalfAMegabyte() = runBlocking {
        // No redirects, a fixed User-Agent and a deadline for the whole download, as for every other request.
        assertTrue(ResultThumbnails.http is HttpsClient)
        val asked = mutableListOf<Pair<String, Int>>()
        val bytes = png(80, 80)
        ResultThumbnails.http = HttpsGet { url, maxBytes, _ -> asked += url to maxBytes; bytes }
        val url = "https://encrypted-tbn0.gstatic.com/images?q=tbn:client"
        assertNotNull(ResultThumbnails.load(context, ThumbnailSource.Web(url), px = 80))
        assertEquals(listOf(url to 512 * 1024), asked)
    }

    @Test
    fun anInlinePictureNeedsNoNetwork() = runBlocking {
        ResultThumbnails.download = { error("no network for a data: image") }
        val inline = "data:image/png;base64," + android.util.Base64.encodeToString(png(48, 48), android.util.Base64.NO_WRAP)
        assertNotNull(ResultThumbnails.load(context, ThumbnailSource.Web(inline), px = 48))
    }

    @Test
    fun offlineTheRowKeepsItsIconAndDoesNotRetryAtOnce() = runBlocking {
        val fetches = AtomicInteger()
        ResultThumbnails.download = { fetches.incrementAndGet(); throw java.io.IOException("offline") }
        val source = ThumbnailSource.Web("https://encrypted-tbn0.gstatic.com/images?q=tbn:offline")
        assertNull(ResultThumbnails.load(context, source, px = 80))
        assertNull(ResultThumbnails.load(context, source, px = 80))
        assertEquals(1, fetches.get())
    }

    @Test
    fun aVideoInAChosenFolderShowsAFrameFromTheVideo() = runBlocking {
        // The storage provider thumbnails only images, so a folder video has no provider thumbnail at all.
        val video = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies/document/primary%3AMovies%2Fclip.mp4")
        val frame = Bitmap.createBitmap(80, 45, Bitmap.Config.ARGB_8888)
        ShadowMediaMetadataRetriever.addScaledFrame(DataSource.toDataSource(context, video), -1, 80, 80, frame)
        val image = ResultThumbnails.load(context, ThumbnailSource.MediaFile(video.toString(), "video/mp4"), px = 80)
        assertNotNull(image)
        assertEquals(80, image!!.width)
    }

    @Test
    fun onlyImagesAndVideosGetAFilePicture() {
        assertNotNull(fileThumbnailSource("content://media/external/file/1", "image/jpeg"))
        assertNotNull(fileThumbnailSource("content://media/external/file/2", "video/mp4"))
        assertNull(fileThumbnailSource("content://media/external/file/3", "application/pdf"))
    }
}
