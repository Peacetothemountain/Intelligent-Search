package com.pixel.intelligentsearch.core.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.pixel.intelligentsearch.core.net.HttpsClient
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Google's own search box asks `/complete/search` with a `gws-wiz` client, whose answer names entities and links their
 * pictures. Asking with `client=chrome` and keeping only the strings is why no suggestion ever had a thumbnail to
 * show, whatever the Show Thumbnails switch said.
 *
 * The recorded answer comes from a connection in memory, not a server on the PC's loopback: a real socket read could
 * fail under load (the JDK's silent retry of a GET, then an empty result), which made this test fail on a busy PC
 * for reasons that weren't the app's.
 */
@RunWith(RobolectricTestRunner::class)
class WebSearchProviderEntityTest {

    /** Requests made: each connection the app connected. */
    private val requests = AtomicInteger()
    /** The header lines of every request, "Name: value", and its request line ("GET /path"). */
    private val headers = CopyOnWriteArrayList<String>()
    /** The addresses the requests were for. */
    private val asked = CopyOnWriteArrayList<String>()
    /** The connections the app was handed, to check how it set them up. */
    private val connections = CopyOnWriteArrayList<RecordedConnection>()
    /** Where the answer redirects, when it is a redirect instead of the recorded 200. */
    @Volatile private var redirect: String? = null
    private lateinit var body: ByteArray

    /** Answers with the recorded body (or [redirect]'s 302), recording the request as a server would see it. */
    private inner class RecordedConnection(url: URL) : HttpURLConnection(url) {
        override fun connect() {
            requests.incrementAndGet()
            headers += "GET ${url.path}"
        }
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun setRequestProperty(key: String?, value: String?) {
            super.setRequestProperty(key, value)
            headers += "$key: $value"
        }
        override fun getResponseCode() = if (redirect != null) HTTP_MOVED_TEMP else HTTP_OK
        override fun getContentLengthLong() = body.size.toLong()
        override fun getContentType() = "text/javascript; charset=UTF-8"
        override fun getHeaderField(name: String?): String? = redirect.takeIf { name.equals("Location", ignoreCase = true) }
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    }

    @Before fun serveTheRecordedAnswer() {
        WebSearchProvider.clearCacheForTests()
        body = requireNotNull(javaClass.classLoader?.getResource("websuggest/google_entity_obama.txt")).readBytes()
        WebSearchProvider.openForTests = { url ->
            asked += url.toString()
            RecordedConnection(url).also { connections += it }
        }
    }

    @After fun useTheEngineAgain() {
        WebSearchProvider.openForTests = null
        WebSearchProvider.clearCacheForTests()
    }

    @Test
    fun anEntitySuggestionKeepsItsPictureAndDescription() = runBlocking {
        val suggestions = WebSearchProvider.getWebSuggestions("obama", timeoutMs = 5_000)
        val obama = suggestions.first()
        assertEquals("Barack Obama", obama.title)
        assertEquals("44th U.S. President", obama.subtitle)
        assertTrue(obama.imageUrl.orEmpty().startsWith("https://encrypted-tbn0.gstatic.com/"))
        assertEquals(listOf(" presidential center"), suggestions[1].boldRanges.map { suggestions[1].query.substring(it.first, it.last + 1) })
    }

    @Test
    fun theAnswerIsCachedWithItsPictures() = runBlocking {
        WebSearchProvider.getWebSuggestions("obama", timeoutMs = 5_000)
        val cached = WebSearchProvider.getCachedSuggestions("Obama ")
        assertEquals("Barack Obama", cached?.first()?.title)
        assertEquals(1, asked.size)
        // Asked again, the cache answers: the app makes no second request. (Counted where the app asks, not in the
        // connections the server accepts, which include the JDK's own silent retry of a GET.)
        WebSearchProvider.getWebSuggestions("obama", timeoutMs = 5_000)
        assertEquals(1, asked.size)
    }

    @Test
    fun googleIsAskedTheWayItsOwnSearchBoxAsks() {
        val url = WebSearchProvider.suggestUrl("Google", "obama", Locale.forLanguageTag("en-CA"))
        assertTrue(url, url.startsWith("https://suggestqueries.google.com/complete/search?client=mobile-gws-wiz-hp&xssi=t"))
        assertTrue(url, url.contains("&hl=en-CA&") && url.endsWith("&q=obama"))
        // The other engines keep their own OpenSearch endpoints.
        assertTrue(WebSearchProvider.suggestUrl("DuckDuckGo", "obama").startsWith("https://duckduckgo.com/ac/"))
        assertTrue(WebSearchProvider.suggestUrl("Bing", "obama").startsWith("https://api.bing.com/osjson.aspx"))
    }

    @Test
    fun torAndCustomEnginesNeverAsk() = runBlocking {
        assertTrue(WebSearchProvider.getWebSuggestions("obama", engine = "Tor Browser").isEmpty())
        assertTrue(WebSearchProvider.getWebSuggestions("obama", engine = "Custom").isEmpty())
        assertEquals(0, asked.size)
        assertEquals(0, requests.get())
    }

    @Test
    fun theRequestGoesThroughTheAppsHttpsClient() = runBlocking {
        WebSearchProvider.getWebSuggestions("obama", timeoutMs = 5_000)
        assertTrue(asked.single(), asked.single().startsWith("https://suggestqueries.google.com/complete/search?"))
        // The same User-Agent from every phone, not one naming the browser or phone.
        assertTrue("$headers", "User-Agent: ${HttpsClient.USER_AGENT}" in headers)
    }

    @Test
    fun aRedirectIsRefusedNotFollowed() = runBlocking {
        redirect = "https://suggestqueries.google.com/elsewhere"
        assertTrue(WebSearchProvider.getWebSuggestions("obama", timeoutMs = 5_000).isEmpty())
        // One request from the app, and the address it was sent to never asked for.
        assertEquals(1, asked.size)
        assertTrue("$headers", headers.none { it.startsWith("GET /elsewhere") })
        // Nor could the platform follow it on the app's behalf.
        assertTrue(connections.none { it.instanceFollowRedirects })
    }
}
