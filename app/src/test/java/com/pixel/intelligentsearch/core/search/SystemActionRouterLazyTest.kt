package com.pixel.intelligentsearch.core.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The router runs on every keystroke. The toggle manager behind it registers camera, network and broadcast listeners
 * when built, so it is built only once a query names a toggle.
 */
@RunWith(RobolectricTestRunner::class)
class SystemActionRouterLazyTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var builds = 0
    private val router by lazy { SystemActionRouter(context, dagger.Lazy { builds++; SystemToggleManager(context) }) }

    @Test
    fun appNamesSumsAndVolumeNeverBuildTheToggleManager() {
        router.matchAction("chrome")
        router.matchAction("12*4")
        router.matchAction("volume")
        assertEquals(0, builds)
    }

    @Test
    fun aToggleKeywordDoes() {
        assertTrue(router.matchAction("torch") is SystemActionRouter.ActionResult.Toggle)
        assertTrue(builds > 0)
    }
}
