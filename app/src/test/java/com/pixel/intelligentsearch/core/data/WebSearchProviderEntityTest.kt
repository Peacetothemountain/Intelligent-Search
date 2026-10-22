package com.pixel.intelligentsearch.core.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Locale
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
                        while (reader.readLine()?.isNotEmpty() == true) Unit
                        val out = socket.getOutputStream()
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/javascript; charset=UTF-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(body)
                        out.flush()
                    }
                }
            }
        }
        WebSearchProvider.endpointForTests = "http://127.0.0.1:${server.localPort}/complete/search?client=mobile-gws-wiz-hp&xssi=t&q="
    }

    @After fun useTheEngineAgain() {
        WebSearchProvider.endpointForTests = null
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
}
