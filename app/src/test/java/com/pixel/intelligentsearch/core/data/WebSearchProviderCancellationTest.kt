package com.pixel.intelligentsearch.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

/**
 * Each keystroke replaces the last query's web suggestion request. The replaced request must stop when cancelled, or
 * the next query's suggestions wait behind it until it times out.
 */
@RunWith(RobolectricTestRunner::class)
class WebSearchProviderCancellationTest {

    @After fun useTheEngineAgain() {
        WebSearchProvider.openForTests = null
    }

    @Test
    fun aCancelledRequestStopsAtOnce() = runBlocking {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            // A server that takes the connection and never answers.
            val held = CopyOnWriteArrayList<Socket>()
            thread(isDaemon = true) { runCatching { while (true) held += server.accept() } }
            // Every request goes to the stalled server, through the app's HTTPS client.
            WebSearchProvider.openForTests = { URL("http://127.0.0.1:${server.localPort}/complete").openConnection() as HttpURLConnection }

            val request = launch(Dispatchers.IO) { WebSearchProvider.getWebSuggestions("stalled ${System.nanoTime()}", timeoutMs = 5_000) }
            delay(100)
            val took = measureTimeMillis { request.cancelAndJoin() }

            held.forEach { it.close() }
            assertTrue("cancelling took $took ms", took < 500)
        }
    }
}
