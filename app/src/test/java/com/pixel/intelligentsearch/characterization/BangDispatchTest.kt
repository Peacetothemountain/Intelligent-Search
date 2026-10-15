package com.pixel.intelligentsearch.characterization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.bangs.SearchBang
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Bang parsing and the URLs bangs open, including the scheme guard for imported custom bangs. */
@RunWith(RobolectricTestRunner::class)
class BangDispatchTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bangs = SearchBangManager(context, SettingsManager(context))

    @Test
    fun prefixAndSuffixBangsExtractTheQuery() {
        val prefix = bangs.parseBangQuery("!g maps")
        assertNotNull(prefix)
        assertEquals("!g", prefix!!.bang.prefix)
        assertEquals("maps", prefix.extractedQuery)

        val suffix = bangs.parseBangQuery("cute cats !yt")
        assertEquals("!yt", suffix!!.bang.prefix)
        assertEquals("cute cats", suffix.extractedQuery)

        assertNull(bangs.parseBangQuery("no bang here"))
    }

    @Test
    fun webDispatchEncodesTheQuery() {
        val parsed = bangs.parseBangQuery("!g a&b c")!!
        val url = bangs.dispatchBangSearch(parsed).data.toString()
        assertTrue(url, url.startsWith("https://"))
        assertTrue(url, url.contains("a%26b%20c"))
    }

    @Test
    fun customBangWithNonWebSchemeIsForcedOntoHttps() {
        val evil = SearchBang(prefix = "!ev", name = "Evil", urlTemplate = "javascript:alert(%s)")
        val parsed = bangs.parseBangQuery("!ev x", availableBangs = listOf(evil))!!
        val uri = bangs.dispatchBangSearch(parsed).data!!
        assertEquals("https", uri.scheme)
    }
}
