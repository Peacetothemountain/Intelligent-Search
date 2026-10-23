package com.pixel.intelligentsearch.core.net

import com.pixel.intelligentsearch.core.currency.CurrencyRateRepository
import com.pixel.intelligentsearch.core.currency.EcbRatesParser
import com.pixel.intelligentsearch.core.currency.EcbRatesParserTest

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * The HTTPS client against a stand-in connection, never the network: what it sends, and which answers it refuses
 * without reading them.
 */
class HttpsClientTest {

    private val url = CurrencyRateRepository.ECB_DAILY_URL
    private val body = EcbRatesParserTest.ecbFile().toByteArray()

    /** A connection that answers [code] with [body], saying it is [length] bytes long (-1: not said). */
    private class FakeConnection(
        url: URL,
        private val code: Int = HTTP_OK,
        private val length: Long = -1,
        private val body: InputStream = ByteArrayInputStream(ByteArray(0)),
        private val location: String? = null
    ) : HttpURLConnection(url) {
        var bodyOpened = false
        val disconnected = CountDownLatch(1)

        override fun connect() {}
        override fun disconnect() = disconnected.countDown()
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getContentLengthLong() = length
        override fun getInputStream(): InputStream = body.also { bodyOpened = true }
        override fun getHeaderField(name: String?): String? = location.takeIf { name.equals("Location", ignoreCase = true) }
    }

    private fun client(connection: HttpURLConnection, totalTimeoutMs: Long = 30_000L, nanoTime: () -> Long = System::nanoTime) =
        HttpsClient(timeoutMs = 15_000, totalTimeoutMs = totalTimeoutMs, open = { connection }, nanoTime = nanoTime)

    @Test
    fun theRequestIsTheSameFromEveryPhone() {
        val connection = FakeConnection(URL(url), body = ByteArrayInputStream(body))
        assertArrayEquals(body, client(connection).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))

        // A fixed User-Agent instead of Android's default, which names the phone model and build, and nothing else.
        assertEquals("IntelligentSearch/1.0", HttpsClient.USER_AGENT)
        assertEquals(HttpsClient.USER_AGENT, connection.getRequestProperty("User-Agent"))
        assertEquals(setOf("User-Agent", "Accept"), connection.requestProperties.keys)
        assertFalse(connection.useCaches)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals(15_000, connection.connectTimeout)
        assertEquals(15_000, connection.readTimeout)
        assertEquals(0L, connection.disconnected.count)
    }

    @Test
    fun aRedirectIsRefusedNotFollowed() {
        for (code in listOf(301, 302, 303, 307, 308)) {
            val connection = FakeConnection(URL(url), code = code, body = ByteArrayInputStream(body))
            assertNull("$code", client(connection).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))
            assertFalse("$code", connection.instanceFollowRedirects)
            assertFalse("$code was read", connection.bodyOpened)
        }
    }

    @Test
    fun anyAnswerButOkIsRefusedUnread() {
        for (code in listOf(201, 202, 203, 204, 206, 304, 400, 403, 404, 429, 500, 503)) {
            val connection = FakeConnection(URL(url), code = code, body = ByteArrayInputStream(body))
            assertNull("$code", client(connection).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))
            assertFalse("$code was read", connection.bodyOpened)
        }
    }

    @Test
    fun aBodySaidToBeTooLongIsRefusedUnread() {
        val tooLong = FakeConnection(URL(url), length = EcbRatesParser.MAX_BYTES + 1L, body = ByteArrayInputStream(body))
        assertNull(client(tooLong).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))
        assertFalse(tooLong.bodyOpened)

        val atTheLimit = FakeConnection(URL(url), length = EcbRatesParser.MAX_BYTES.toLong(), body = ByteArrayInputStream(body))
        assertArrayEquals(body, client(atTheLimit).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))
        val unsaid = FakeConnection(URL(url), length = -1, body = ByteArrayInputStream(body))
        assertArrayEquals(body, client(unsaid).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))
    }

    @Test
    fun aBodyLongerThanItSaidIsStillCutOff() {
        val endless = object : InputStream() {
            override fun read(): Int = 'x'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { b.fill('x'.code.toByte(), off, off + len) }
        }
        val connection = FakeConnection(URL(url), length = 100, body = endless)
        assertNull(client(connection).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))
    }

    @Test
    fun aDownloadTrickledOutPastTheOverallDeadlineIsAbandoned() {
        // Each read comes well within the per-read timeout, but together they take longer than the whole download may.
        var now = 0L
        var reads = 0
        val trickle = object : InputStream() {
            override fun read(): Int = throw AssertionError("reads are buffered")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                now += TimeUnit.SECONDS.toNanos(10)
                reads++
                if (reads > 20) return -1
                b[off] = 'x'.code.toByte()
                return 1
            }
        }
        val connection = FakeConnection(URL(url), body = trickle)
        assertNull(client(connection, totalTimeoutMs = 30_000L, nanoTime = { now }).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))
        assertTrue("read $reads times", reads <= 4)
    }

    @Test(timeout = 10_000)
    fun aReadBlockedPastTheDeadlineIsCutOff() {
        lateinit var connection: FakeConnection
        // A server that sends nothing and keeps the connection open: the read only ends when it is closed.
        val stalled = object : InputStream() {
            override fun read(): Int = read(ByteArray(1), 0, 1)
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                connection.disconnected.await()
                throw IOException("Socket closed")
            }
        }
        connection = FakeConnection(URL(url), body = stalled)
        val started = System.nanoTime()
        assertNull(client(connection, totalTimeoutMs = 200L).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
    }

    @Test
    fun aConnectionThatFailsIsNoBody() {
        val failing = object : HttpURLConnection(URL(url)) {
            override fun connect() = throw IOException("No route to host")
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode(): Int = throw IOException("No route to host")
        }
        assertNull(client(failing).get(url, EcbRatesParser.MAX_BYTES, HttpsCall()))
        assertNull(HttpsClient(timeoutMs = 1, totalTimeoutMs = 1, open = { null }, nanoTime = System::nanoTime).get(url, 10, HttpsCall()))
    }

    @Test(timeout = 10_000)
    fun cancellingTheCallEndsABlockedReadAtOnce() {
        lateinit var connection: FakeConnection
        val reading = CountDownLatch(1)
        val stalled = object : InputStream() {
            override fun read(): Int = read(ByteArray(1), 0, 1)
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reading.countDown()
                connection.disconnected.await()
                throw IOException("Socket closed")
            }
        }
        connection = FakeConnection(URL(url), body = stalled)
        val call = HttpsCall()
        val result = java.util.concurrent.atomic.AtomicReference<ByteArray?>(byteArrayOf())
        val thread = Thread { result.set(client(connection).get(url, EcbRatesParser.MAX_BYTES, call)) }.apply { start() }
        assertTrue(reading.await(5, TimeUnit.SECONDS))
        call.cancel()
        thread.join(5_000)
        assertFalse("the read was still blocked", thread.isAlive)
        assertNull(result.get())
    }

    @Test
    fun aCallCancelledBeforehandNeverConnects() {
        val connection = FakeConnection(URL(url), body = ByteArrayInputStream(body))
        val call = HttpsCall().apply { cancel() }
        assertNull(client(connection).get(url, EcbRatesParser.MAX_BYTES, call))
        assertFalse(connection.bodyOpened)
        assertEquals(0L, connection.disconnected.count)
    }

    @Test(timeout = 10_000)
    fun cancellingTheCoroutineCancelsTheCall() {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        // Blocks without heeding interrupts, as a socket read does; only cancelling the call lets it go.
        val stalled = HttpsGet { _, _, call ->
            call.onCancel { cancelled.countDown() }
            started.countDown()
            while (!call.isCancelled) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000)
            null
        }
        kotlinx.coroutines.runBlocking {
            val job = launch(kotlinx.coroutines.Dispatchers.Default) { stalled.fetch(url, 10) }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            job.cancel()
            assertTrue("the call wasn't cancelled", cancelled.await(5, TimeUnit.SECONDS))
            job.join()
        }
    }
}
