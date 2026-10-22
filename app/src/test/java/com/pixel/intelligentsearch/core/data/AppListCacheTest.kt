package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The cached list of installed apps that search, the widget and the quick apps all read. */
@RunWith(RobolectricTestRunner::class)
class AppListCacheTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val deviceScanner = SystemDataProvider.appScanner
    private val scans = AtomicInteger()
    private val camera = AppItem("Camera", "com.example.camera", ColorDrawable())

    @Before fun countScans() {
        SystemDataProvider.invalidateAppsCache()
        SystemDataProvider.appScanner = { _, _ ->
            scans.incrementAndGet()
            listOf(camera)
        }
    }

    @After fun restore() {
        SystemDataProvider.appScanner = deviceScanner
        SystemDataProvider.invalidateAppsCache()
    }

    @Test
    fun callersThatArriveTogetherShareOneScan() = runBlocking {
        val release = CompletableDeferred<Unit>()
        SystemDataProvider.appScanner = { _, _ ->
            scans.incrementAndGet()
            release.await()
            listOf(camera)
        }
        // The overlay, the index and the widget all ask at start-up, before any scan has finished.
        val first = async(Dispatchers.Default) { SystemDataProvider.getAllApps(context) }
        val second = async(Dispatchers.Default) { SystemDataProvider.getAllApps(context) }
        // Long enough for the second caller to start a scan of its own if nothing stops it.
        withTimeoutOrNull(1_000) { while (scans.get() < 2) delay(10) }
        release.complete(Unit)

        assertEquals(listOf(camera), first.await())
        assertEquals(listOf(camera), second.await())
        assertEquals(1, scans.get())
    }

    @Test
    fun aForcedRefreshScansAgainWhileOthersReadTheCache() = runBlocking {
        SystemDataProvider.getAllApps(context)
        SystemDataProvider.getAllApps(context)
        assertEquals(1, scans.get())

        SystemDataProvider.getAllApps(context, forceRefresh = true)
        assertEquals(2, scans.get())
    }
}
