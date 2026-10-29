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
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Google's own search box asks `/complete/search` with a `gws-wiz` client, whose answer names entities and links their
 * pictures. Asking with `client=chrome` and keeping only the strings is why no suggestion ever had a thumbnail to
 * show, whatever the Show Thumbnails switch said.
 */
@RunWith(RobolectricTestRunner::class)
class WebSearchProviderEntityTest {

    private val requests = AtomicInteger()
    /** The header lines of every request the server was sent. */
    private val headers = CopyOnWriteArrayList<String>()
    /** The addresses the requests were for, before the test sends them to its server. */
    private val asked = CopyOnWriteArrayList<String>()
    /** The server's answer's status line and extra headers, when not the recorded 200. */
    @Volatile private var status: String? = null
    private lateinit var server: ServerSocket

    @Before fun serveTheRecordedAnswer() {
        WebSearchProvider.clearCacheForTests()
        val body = requireNotNull(javaClass.classLoader?.getResource("websuggest/google_entity_obama.txt")).readBytes()
        server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            runCatching {
                while (true) {
                    server.accept().use { socket ->
                        requests.incrementAndGet()
                        val reader = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine()
                            if (line.isNullOrEmpty()) break
                            headers += line
                        }
                        val out = socket.getOutputStream()
                        val head = status ?: "HTTP/1.1 200 OK\r\nContent-Type: text/javascript; charset=UTF-8"
                        out.write("$head\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(body)
                        out.flush()
                    }
                }
            }
        }
        WebSearchProvider.openForTests = { url ->
            asked += url.toString()
            URL("http://127.0.0.1:${server.localPort}/complete/search?client=mobile-gws-wiz-hp&xssi=t&q=obama").openConnection() as HttpURLConnection
        }
    }

    @After fun useTheEngineAgain() {
        WebSearchProvider.openForTests = null
        WebSearchProvider.clearCacheForTests()
        server.close()
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
        assertEquals(1, requests.get())
        WebSearchProvider.getWebSuggestions("obama", timeoutMs = 5_000)
        assertEquals(1, requests.get())
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
        status = "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${server.localPort}/elsewhere"
        assertTrue(WebSearchProvider.getWebSuggestions("obama", timeoutMs = 5_000).isEmpty())
        assertEquals(1, requests.get())
    }
}
