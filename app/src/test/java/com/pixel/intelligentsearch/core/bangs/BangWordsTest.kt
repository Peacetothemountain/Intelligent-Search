package com.pixel.intelligentsearch.core.bangs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which bang a query with more than one, or with a tab after it, calls, and what that bang searches for. */
@RunWith(RobolectricTestRunner::class)
class BangWordsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bangs = SearchBangManager(context, SettingsManager(context))

    @Test
    fun theBangTypedFirstWinsAndASecondIsDropped() {
        // Google is listed before YouTube, which used to make the trailing "!g" win with "!yt cats" as its query.
        val parsed = bangs.parseBangQuery("!yt cats !g")!!
        assertEquals("!yt", parsed.bang.prefix)
        assertEquals("cats", parsed.extractedQuery)

        val trailing = bangs.parseBangQuery("cats !yt !g")!!
        assertEquals("!g", trailing.bang.prefix)
        assertEquals("cats", trailing.extractedQuery)
    }

    @Test
    fun aTabOrSeveralSpacesEndTheBangAsASpaceDoes() {
        val tabbed = bangs.parseBangQuery("!g\tcats")!!
        assertEquals("!g", tabbed.bang.prefix)
        assertEquals("cats", tabbed.extractedQuery)
        assertEquals("cats", bangs.parseBangQuery("!g   cats")!!.extractedQuery)
        assertEquals("!yt", bangs.parseBangQuery("cats\t!yt")!!.bang.prefix)
    }

    @Test
    fun theQueryKeepsItsOwnWordsAndPunctuation() {
        assertEquals("a  b!", bangs.parseBangQuery("!g a  b!")!!.extractedQuery)
        assertEquals("", bangs.parseBangQuery("!g")!!.extractedQuery)
        // An exclamation mark inside a word, or a bang that isn't one, calls nothing.
        assertNull(bangs.parseBangQuery("wow!g cats"))
        assertNull(bangs.parseBangQuery("!notabang cats"))
    }
}
