package com.pixel.intelligentsearch.core.net

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * One HTTPS exchange that another thread may end. [cancel] closes its connection, which ends a blocked connect or
 * read at once rather than at its timeout; a call cancelled before it connects never opens one.
 */
class HttpsCall {
    private var connection: HttpURLConnection? = null
    private var onCancel: (() -> Unit)? = null

    @Volatile
    var isCancelled: Boolean = false
        private set

    /** Ties [connection] to this call; false (and the connection closed) when the call was already cancelled. */
    internal fun attach(connection: HttpURLConnection): Boolean {
        synchronized(this) {
            if (!isCancelled) {
                this.connection = connection
                return true
            }
        }
        connection.disconnect()
        return false
    }

    /** Runs [action] once the call is cancelled (at once when it already is), for a stand-in transport in a test. */
    fun onCancel(action: () -> Unit) {
        synchronized(this) {
            if (!isCancelled) {
                onCancel = action
                return
            }
        }
        action()
    }

    fun cancel() {
        val (open, action) = synchronized(this) {
            if (isCancelled) return
            isCancelled = true
            connection to onCancel
        }
        open?.disconnect()
        action?.invoke()
    }
}

/**
 * Runs [block], blocking HTTPS work over an [HttpsCall], on the IO dispatcher. Cancelling the caller (the next
 * keystroke, a closed overlay) cancels the call, so the request stops at once instead of running on to its timeout.
 */
suspend fun <T> withHttpsCall(block: (HttpsCall) -> T): T = withContext(Dispatchers.IO) {
    val call = HttpsCall()
    val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            call.cancel()
        }
    }
    try {
        block(call)
    } finally {
        canceller.cancel()
    }
}

/** Fetches a small document over HTTPS. Tests replace it, so they never touch the network. */
fun interface HttpsGet {
    /**
     * The body of [url] when it answers 200 OK with at most [maxBytes] bytes; null on anything else, including a
     * cancelled [call]. Blocking: call off the main thread.
     */
    fun get(url: String, maxBytes: Int, call: HttpsCall): ByteArray?
}

/** [HttpsGet.get] from a coroutine: cancelling it ends the download (and throws, so nothing is made of the null). */
suspend fun HttpsGet.fetch(url: String, maxBytes: Int): ByteArray? = withHttpsCall { get(url, maxBytes, it) }

/** How a server answered a POST: its HTTP status, and for anything but 200 the start of its error body. */
data class HttpsResponse(val code: Int, val errorBody: String = "")

/** Raised when a whole exchange takes longer than its client's total timeout. */
class HttpsTimeoutException : IOException("The request took too long")

/**
 * The app's one HTTPS client, over HttpsURLConnection: https addresses only, redirects never followed (a 3xx is a
 * failure), nothing cached, and bodies read only up to their limit. Each read may take [timeoutMs], and the whole
 * exchange [totalTimeoutMs], so a server sending a byte at a time can't hold it open. The request is the same from
 * every phone: a fixed [USER_AGENT] in place of Android's default (which names the phone model and build), and no
 * cookies, account or identifier. [accept] names the kind of document a GET wants (the rates are XML; instant
 * answers ask for JSON; a Saved Link's icon is an image). [sameHostRedirects] lets a GET follow that many redirects
 * that stay on https and on the same host (a site that moved its icon); any other redirect is a failure.
 */
class HttpsClient internal constructor(
    private val timeoutMs: Int,
    private val totalTimeoutMs: Long,
    private val open: (URL) -> HttpURLConnection?,
    private val nanoTime: () -> Long,
    private val accept: String = ACCEPT_XML,
    private val sameHostRedirects: Int = 0
) : HttpsGet {

    constructor(timeoutMs: Int = 15_000, totalTimeoutMs: Long = 30_000L, accept: String = ACCEPT_XML, sameHostRedirects: Int = 0) :
        this(timeoutMs, totalTimeoutMs, { it.openConnection() as? HttpsURLConnection }, System::nanoTime, accept, sameHostRedirects)

    override fun get(url: String, maxBytes: Int, call: HttpsCall): ByteArray? {
        val deadline = nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs)
        var address = httpsAddress(url) ?: return null
        var redirectsLeft = sameHostRedirects
        while (true) {
            when (val answer = getOnce(address, maxBytes, call, deadline)) {
                is GetAnswer.Body -> return answer.bytes
                GetAnswer.Failed -> return null
                is GetAnswer.Redirect -> {
                    val next = answer.to?.takeIf { it.protocol.equals("https", ignoreCase = true) && it.host.equals(address.host, ignoreCase = true) }
                    if (next == null || redirectsLeft-- <= 0) return null
                    address = next
                }
            }
        }
    }

    private sealed interface GetAnswer {
        class Body(val bytes: ByteArray) : GetAnswer
        class Redirect(val to: URL?) : GetAnswer
        data object Failed : GetAnswer
    }

    private fun getOnce(address: URL, maxBytes: Int, call: HttpsCall, deadline: Long): GetAnswer {
        val connection = runCatching { open(address) }.getOrNull() ?: return GetAnswer.Failed
        if (!call.attach(connection)) return GetAnswer.Failed
        val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - nanoTime())
        if (remainingMs <= 0) return GetAnswer.Failed.also { connection.disconnect() }
        // A read blocked past the deadline ends when the connection is closed under it.
        val watchdog = runCatching { WATCHDOG.schedule(connection::disconnect, remainingMs, TimeUnit.MILLISECONDS) }.getOrNull()
        return try {
            connection.prepare(timeoutMs)
            connection.setRequestProperty("Accept", accept)
            val code = connection.responseCode
            when {
                code in REDIRECTS -> GetAnswer.Redirect(
                    connection.getHeaderField("Location")?.let { runCatching { URL(address, it) }.getOrNull() }
                )
                code != HttpURLConnection.HTTP_OK -> GetAnswer.Failed
                connection.contentLengthLong > maxBytes -> GetAnswer.Failed
                else -> connection.inputStream.use { readCapped(it, maxBytes) { call.isCancelled || nanoTime() - deadline > 0 } }
                    ?.let(GetAnswer::Body) ?: GetAnswer.Failed
            }
        } catch (_: Exception) {
            GetAnswer.Failed
        } finally {
            watchdog?.cancel(false)
            connection.disconnect()
        }
    }

    /**
     * POSTs [body] (UTF-8) with [headers] to [url] and hands each line of the streamed answer to [onLine], stopping
     * after [maxStreamChars] characters. Throws [IOException] when the server can't be reached or [call] is
     * cancelled, and [HttpsTimeoutException] once the whole exchange outlives the total timeout. Blocking.
     */
    @Throws(IOException::class)
    fun postStreaming(
        url: String,
        headers: Map<String, String>,
        body: String,
        maxStreamChars: Int,
        call: HttpsCall,
        onLine: (String) -> Unit
    ): HttpsResponse {
        val address = httpsAddress(url) ?: throw IOException("Only https addresses are allowed")
        val connection = open(address) ?: throw IOException("Couldn't connect")
        if (!call.attach(connection)) throw IOException("Cancelled")
        val deadline = nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs)
        val watchdog = runCatching { WATCHDOG.schedule(connection::disconnect, totalTimeoutMs, TimeUnit.MILLISECONDS) }.getOrNull()
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.prepare(timeoutMs)
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val error = runCatching { connection.errorStream?.use { readCapped(it, MAX_ERROR_BYTES) } }.getOrNull()
                return HttpsResponse(code, error?.toString(Charsets.UTF_8).orEmpty())
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var answered = 0
                while (true) {
                    if (nanoTime() - deadline > 0) throw HttpsTimeoutException()
                    if (call.isCancelled) throw IOException("Cancelled")
                    val line = reader.readLine() ?: break
                    answered += line.length
                    if (answered > maxStreamChars) break
                    onLine(line)
                }
            }
            return HttpsResponse(code)
        } catch (e: SocketTimeoutException) {
            throw HttpsTimeoutException()
        } catch (e: IOException) {
            if (!call.isCancelled && nanoTime() - deadline > 0) throw HttpsTimeoutException()
            throw e
        } finally {
            watchdog?.cancel(false)
            connection.disconnect()
        }
    }

    private fun httpsAddress(url: String): URL? =
        runCatching { URI(url).toURL() }.getOrNull()?.takeIf { it.protocol.equals("https", ignoreCase = true) }

    private fun HttpURLConnection.prepare(timeoutMs: Int) {
        instanceFollowRedirects = false
        useCaches = false
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        setRequestProperty("User-Agent", USER_AGENT)
    }

    companion object {
        /** Sent instead of Android's default User-Agent; the same for every user and version. */
        const val USER_AGENT = "IntelligentSearch/1.0"

        const val ACCEPT_XML = "application/xml, text/xml"
        const val ACCEPT_JSON = "application/json"
        const val ACCEPT_IMAGE = "image/*"

        private const val MAX_ERROR_BYTES = 8 * 1024
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        /** Closes exchanges that outlive their deadline. One daemon thread, ended while idle. */
        private val WATCHDOG = ScheduledThreadPoolExecutor(1) { task -> Thread(task, "HttpsDeadline").apply { isDaemon = true } }.apply {
            removeOnCancelPolicy = true
            setKeepAliveTime(10, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
        }

        /** All of [input], or null as soon as it runs past [maxBytes] (the rest is never read). */
        fun readCapped(input: InputStream, maxBytes: Int): ByteArray? = readCapped(input, maxBytes) { false }

        /** As [readCapped], also giving up (null) once [stop] says the download has taken too long or was cancelled. */
        internal fun readCapped(input: InputStream, maxBytes: Int, stop: () -> Boolean): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                if (stop()) return null
                val read = input.read(buffer)
                if (read < 0) return out.toByteArray()
                if (out.size() + read > maxBytes) return null
                out.write(buffer, 0, read)
            }
        }
    }
}
