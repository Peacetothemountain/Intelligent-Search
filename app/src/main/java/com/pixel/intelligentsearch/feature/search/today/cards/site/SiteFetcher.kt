package com.pixel.intelligentsearch.feature.search.today.cards.site

import com.pixel.intelligentsearch.core.net.HttpsCall
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/*
 * NG Designs
 */

/** How a fetch ended. */
internal sealed interface SiteFetch {
    /** The answer at [address] (after any redirects): its lowercase [headers] and the [body] read, at most the limit. */
    class Ok(val address: SiteAddress, val headers: Map<String, String>, val body: ByteArray, val complete: Boolean) : SiteFetch

    /** The address, or one it redirected to, was refused by the rules ([SiteRefusal]). */
    data class Refused(val reason: SiteRefusal) : SiteFetch

    /** The site couldn't be reached, didn't answer in time, or answered with an error. */
    data object Unreachable : SiteFetch
}

/**
 * The website reader's one way onto the network: a plain HTTPS GET, written by hand over a socket so every rule the
 * reader promises holds by construction.
 *
 * Why not [HttpsURLConnection] (or the app's [com.pixel.intelligentsearch.core.net.HttpsClient], which wraps it): it
 * looks the host up itself when it connects, so the address checked here and the address it connects to could differ
 * (a DNS rebind: a name that answers with a public address for the check and a private one a moment later). Pinning it
 * to an address means connecting to an IP and faking the Host header and the TLS server name, which the platform's
 * connection doesn't reliably allow. So this reader does it the plain way:
 * 1. The host is looked up once, within the time limit, and every address it returns must be public
 *    ([isPublicAddress]); one private address and the site is refused.
 * 2. A socket connects to one of those checked addresses directly, so nothing is looked up twice. No proxy is used.
 * 3. TLS runs over that socket with the platform's default trust (the app's network security config: system
 *    certificates), with the real host name for SNI and certificate checks (`HTTPS` endpoint identification), and the
 *    platform's own host name verifier checks the certificate again after the handshake.
 * 4. One HTTP/1.1 GET goes out: a fixed, generic [USER_AGENT] the same on every phone, no cookies (nothing stores
 *    them), no Referer, no language or account, `Connection: close`.
 * 5. Redirects are followed by hand, at most [MAX_REDIRECTS], each target put through [resolveSiteAddress] and the
 *    address check again; an http target is refused.
 *
 * Every step is bounded by one total deadline ([TOTAL_TIMEOUT_MS]): the lookup waits at most the time left, the
 * connect and every read have the time left as their timeout, and the read loop stops once the deadline passes, so a
 * server dripping a byte at a time can't hold the reader. The body is read only up to its limit (or, for a page, its
 * `</head>`). Cancelling the [HttpsCall] closes the socket (or abandons the lookup) at once, through the same
 * cancellation the app's other HTTPS work uses ([com.pixel.intelligentsearch.core.net.withHttpsCall]).
 *
 * Blocking: call off the main thread.
 */
internal class SiteFetcher(
    private val resolve: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) },
    private val nanoTime: () -> Long = System::nanoTime
) {

    /** What one fetch is for: a page (read to its `</head>`, at most [PAGE_MAX_BYTES]) or an image. */
    enum class Kind(val accept: String, val maxBytes: Int) {
        PAGE("text/html,application/xhtml+xml;q=0.9,*/*;q=0.1", PAGE_MAX_BYTES),
        IMAGE("image/webp,image/png,image/jpeg,image/*;q=0.8", IMAGE_MAX_BYTES)
    }

    /** Fetches [start] for [kind], following redirects by the rules above, within [TOTAL_TIMEOUT_MS]. */
    fun fetch(start: SiteAddress, kind: Kind, call: HttpsCall): SiteFetch {
        val deadline = nanoTime() + TimeUnit.MILLISECONDS.toNanos(TOTAL_TIMEOUT_MS)
        val session = Session()
        call.onCancel(session::cancel)
        var address = start
        try {
            repeat(MAX_REDIRECTS + 1) {
                hostRefusal(address.host)?.let { return SiteFetch.Refused(it) }
                val answer = exchange(address, kind, session, deadline) ?: return SiteFetch.Unreachable
                when (answer) {
                    is Exchange.Refused -> return SiteFetch.Refused(answer.reason)
                    is Exchange.Redirect -> {
                        address = when (val next = resolveSiteAddress(address, answer.location)) {
                            is SiteAddressResult.Ok -> next.address
                            is SiteAddressResult.Refused -> return SiteFetch.Refused(next.reason)
                        }
                    }
                    is Exchange.Done -> return answer.fetch
                }
            }
            return SiteFetch.Unreachable // too many redirects
        } catch (_: Exception) {
            return SiteFetch.Unreachable
        } finally {
            session.cancel()
        }
    }

    private sealed interface Exchange {
        data class Refused(val reason: SiteRefusal) : Exchange
        data class Redirect(val location: String) : Exchange
        data class Done(val fetch: SiteFetch) : Exchange
    }

    /** One request to [address]: null when it couldn't be made or answered in time. */
    private fun exchange(address: SiteAddress, kind: Kind, session: Session, deadline: Long): Exchange? {
        val addresses = lookUp(address.host, session, deadline) ?: return null
        if (addresses.isEmpty()) return null
        if (!addresses.all(::isPublicAddress)) return Exchange.Refused(SiteRefusal.NOT_PUBLIC)

        val socket = connect(addresses, session, deadline) ?: return null
        socket.use { raw ->
            val tls = startTls(raw, address.host, deadline) ?: return null
            session.attach(tls)
            tls.use { ssl ->
                val request = buildString {
                    append("GET ").append(address.pathAndQuery).append(" HTTP/1.1\r\n")
                    append("Host: ").append(address.host).append("\r\n")
                    append("User-Agent: ").append(USER_AGENT).append("\r\n")
                    append("Accept: ").append(kind.accept).append("\r\n")
                    append("Accept-Encoding: gzip, identity\r\n")
                    append("Connection: close\r\n\r\n")
                }
                ssl.outputStream.apply { write(request.toByteArray(Charsets.US_ASCII)); flush() }

                val input = DeadlineInputStream(BufferedInputStream(ssl.inputStream), ssl, deadline, nanoTime)
                val (code, headers) = readHead(input) ?: return null
                if (code in REDIRECTS) {
                    val location = headers["location"] ?: return Exchange.Done(SiteFetch.Unreachable)
                    return Exchange.Redirect(location)
                }
                if (code != 200) return Exchange.Done(SiteFetch.Unreachable)
                val declared = headers["content-length"]?.trim()?.toLongOrNull()
                if (kind == Kind.IMAGE && declared != null && declared > kind.maxBytes) return Exchange.Done(SiteFetch.Unreachable)

                var body: InputStream = when {
                    headers["transfer-encoding"]?.lowercase(Locale.ROOT)?.contains("chunked") == true -> ChunkedInputStream(input)
                    declared != null -> LimitedInputStream(input, declared)
                    else -> input
                }
                body = when (headers["content-encoding"]?.trim()?.lowercase(Locale.ROOT)) {
                    null, "", "identity" -> body
                    "gzip", "x-gzip" -> GZIPInputStream(body)
                    "deflate" -> InflaterInputStream(body, Inflater())
                    else -> return Exchange.Done(SiteFetch.Unreachable)
                }
                val (bytes, complete) = readBody(body, kind, deadline) ?: return null
                if (kind == Kind.IMAGE && !complete) return Exchange.Done(SiteFetch.Unreachable)
                return Exchange.Done(SiteFetch.Ok(address, headers, bytes, complete))
            }
        }
    }

    /** Every address of [host], or null when the lookup failed, was cancelled or outlived the deadline. */
    private fun lookUp(host: String, session: Session, deadline: Long): List<InetAddress>? {
        val future = LOOKUPS.submit<Array<InetAddress>> { resolve(host) }
        if (!session.attach(future)) return null
        return try {
            future.get(remainingMs(deadline).coerceAtLeast(1), TimeUnit.MILLISECONDS).toList()
        } catch (_: TimeoutException) {
            future.cancel(true); null
        } catch (_: ExecutionException) {
            null
        } catch (_: CancellationException) {
            null
        }
    }

    /** A socket connected to the first of [addresses] that answers within the time left; null when none does. */
    private fun connect(addresses: List<InetAddress>, session: Session, deadline: Long): Socket? {
        for (candidate in addresses) {
            val remaining = remainingMs(deadline)
            if (remaining <= 0) return null
            val socket = Socket(Proxy.NO_PROXY)
            if (!session.attach(socket)) return null
            try {
                // At most half the time left per address, so a dead first address leaves time for the next.
                val budget = if (candidate == addresses.last()) remaining else (remaining / 2).coerceAtLeast(1)
                socket.connect(InetSocketAddress(candidate, HTTPS_PORT), budget.toInt())
                return socket
            } catch (_: IOException) {
                runCatching { socket.close() }
                if (session.cancelled) return null
            }
        }
        return null
    }

    /** TLS over [raw] for [host], its certificate checked against [host]; null when the handshake or the check fails. */
    private fun startTls(raw: Socket, host: String, deadline: Long): SSLSocket? {
        raw.soTimeout = remainingMs(deadline).coerceAtLeast(1).toInt()
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = factory.createSocket(raw, host, HTTPS_PORT, true) as SSLSocket
        ssl.sslParameters = ssl.sslParameters.apply {
            serverNames = listOf(SNIHostName(host))
            endpointIdentificationAlgorithm = "HTTPS"
        }
        ssl.startHandshake()
        // The platform's own check, as HttpsURLConnection makes it, whatever the endpoint identification did.
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ssl.session)) {
            runCatching { ssl.close() }
            return null
        }
        return ssl
    }

    /** The status code and the lowercase headers (first of each), or null when they don't arrive or run too long. */
    private fun readHead(input: InputStream): Pair<Int, Map<String, String>>? {
        val statusLine = readLine(input) ?: return null
        val parts = statusLine.split(' ')
        if (parts.size < 2 || !parts[0].startsWith("HTTP/1.")) return null
        val code = parts[1].toIntOrNull() ?: return null
        val headers = mutableMapOf<String, String>()
        var total = statusLine.length
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            total += line.length
            if (total > MAX_HEADER_BYTES) return null
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            headers.putIfAbsent(name, line.substring(colon + 1).trim())
        }
        return code to headers
    }

    /** One CRLF (or LF) ended line as Latin-1, or null at the end of the stream or past [MAX_HEADER_BYTES]. */
    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) break
            if (out.size() >= MAX_HEADER_BYTES) return null
            out.write(b)
        }
        return out.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r")
    }

    /**
     * The body, at most [Kind.maxBytes] bytes, and whether it was read to its end. A page stops early at `</head>` (its
     * end counted as complete): the reader wants only the header. Null when the deadline passed first.
     */
    private fun readBody(input: InputStream, kind: Kind, deadline: Long): Pair<ByteArray, Boolean>? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var scanned = 0
        while (true) {
            if (remainingMs(deadline) <= 0) return if (kind == Kind.PAGE && out.size() > 0) out.toByteArray() to false else null
            val room = kind.maxBytes - out.size()
            // A page cut at its limit is still read for what it has; an image must have ended exactly there.
            if (room <= 0) return out.toByteArray() to (kind == Kind.IMAGE && input.read() < 0)
            val read = try {
                input.read(buffer, 0, minOf(buffer.size, room))
            } catch (e: IOException) {
                // A page cut off after some of its header still has something to show.
                if (kind == Kind.PAGE && out.size() > 0) return out.toByteArray() to false
                throw e
            }
            if (read < 0) return out.toByteArray() to true
            out.write(buffer, 0, read)
            if (kind == Kind.PAGE) {
                val bytes = out.toByteArray()
                val end = indexOfHeadEnd(bytes, (scanned - HEAD_END.size).coerceAtLeast(0))
                if (end >= 0) return bytes.copyOf(end) to true
                scanned = bytes.size
            }
        }
    }

    private fun remainingMs(deadline: Long): Long = TimeUnit.NANOSECONDS.toMillis(deadline - nanoTime())

    /**
     * The open parts of one fetch, so a cancel from another thread closes whichever is open: the lookup, the socket,
     * the TLS socket. Anything attached after the cancel is closed at once.
     */
    private class Session {
        private val open = mutableListOf<AutoCloseable>()

        @Volatile
        var cancelled = false
            private set

        fun attach(socket: Socket): Boolean = attach(AutoCloseable { socket.close() })

        fun attach(future: Future<*>): Boolean = attach(AutoCloseable { future.cancel(true) })

        private fun attach(closeable: AutoCloseable): Boolean {
            synchronized(this) {
                if (!cancelled) {
                    open += closeable
                    return true
                }
            }
            runCatching { closeable.close() }
            return false
        }

        fun cancel() {
            val toClose = synchronized(this) {
                cancelled = true
                open.toList().also { open.clear() }
            }
            for (closeable in toClose) runCatching { closeable.close() }
        }
    }

    /** Gives every read of [input] the time left before [deadline] as its socket timeout, and fails once it has passed. */
    private class DeadlineInputStream(
        private val input: InputStream,
        private val socket: Socket,
        private val deadline: Long,
        private val nanoTime: () -> Long
    ) : InputStream() {
        private fun arm() {
            val left = TimeUnit.NANOSECONDS.toMillis(deadline - nanoTime())
            if (left <= 0) throw IOException("Out of time")
            socket.soTimeout = left.toInt()
        }

        override fun read(): Int {
            arm(); return input.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            arm(); return input.read(b, off, len)
        }

        override fun close() = input.close()
    }

    /** At most [length] bytes of [input], then the end. */
    private class LimitedInputStream(private val input: InputStream, private var length: Long) : InputStream() {
        override fun read(): Int {
            if (length <= 0) return -1
            return input.read().also { if (it >= 0) length-- }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (length <= 0) return -1
            val read = input.read(b, off, minOf(len.toLong(), length).toInt())
            if (read > 0) length -= read
            return read
        }
    }

    /** A chunked body (`Transfer-Encoding: chunked`) as one stream; a malformed chunk size ends it with an error. */
    private inner class ChunkedInputStream(private val input: InputStream) : InputStream() {
        private var left = 0L
        private var done = false

        private fun nextChunk(): Boolean {
            if (done) return false
            if (left == 0L) {
                val line = readLine(input) ?: throw IOException("Cut off")
                left = line.substringBefore(';').trim().toLongOrNull(16)?.takeIf { it >= 0 } ?: throw IOException("Bad chunk")
                if (left == 0L) {
                    done = true
                    return false
                }
            }
            return true
        }

        override fun read(): Int {
            if (!nextChunk()) return -1
            val b = input.read()
            if (b < 0) throw IOException("Cut off")
            if (--left == 0L) readLine(input)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (!nextChunk()) return -1
            val read = input.read(b, off, minOf(len.toLong(), left).toInt())
            if (read < 0) throw IOException("Cut off")
            left -= read
            if (left == 0L) readLine(input)
            return read
        }
    }

    companion object {
        /** A generic, browser-style link-preview agent: the same from every phone, naming no user, phone or account. */
        const val USER_AGENT = "Mozilla/5.0 (compatible; LinkPreview/1.0)"

        /** The whole of one fetch, redirects included. */
        const val TOTAL_TIMEOUT_MS = 10_000L

        const val MAX_REDIRECTS = 3
        const val PAGE_MAX_BYTES = 256 * 1024
        const val IMAGE_MAX_BYTES = 2 * 1024 * 1024
        private const val MAX_HEADER_BYTES = 32 * 1024
        private const val HTTPS_PORT = 443
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private val HEAD_END = "</head".toByteArray(Charsets.US_ASCII)

        /** Host lookups, which can't be interrupted, run here so a fetch can stop waiting for one. Daemon threads, ended while idle. */
        private val LOOKUPS = Executors.newCachedThreadPool { task -> Thread(task, "SiteLookup").apply { isDaemon = true } }

        /** Where `</head` starts in [bytes] (any case), searching from [from]; -1 when it isn't there. */
        internal fun indexOfHeadEnd(bytes: ByteArray, from: Int): Int {
            outer@ for (i in from..bytes.size - HEAD_END.size) {
                for (j in HEAD_END.indices) {
                    val b = bytes[i + j].toInt()
                    val lower = if (b in 'A'.code..'Z'.code) b + 32 else b
                    if (lower != HEAD_END[j].toInt()) continue@outer
                }
                return i
            }
            return -1
        }
    }
}
