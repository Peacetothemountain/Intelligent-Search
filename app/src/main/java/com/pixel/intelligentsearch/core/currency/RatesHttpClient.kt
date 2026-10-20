package com.pixel.intelligentsearch.core.currency

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** Fetches a small document over HTTPS. Tests replace it, so they never touch the network. */
fun interface RatesHttpClient {
    /** The body of [url] when it answers 200 OK with at most [maxBytes] bytes; null on anything else. */
    fun get(url: String, maxBytes: Int): ByteArray?
}

/**
 * [RatesHttpClient] over HttpsURLConnection: https addresses only, redirects never followed (a 3xx is a failure),
 * nothing cached, and the body read only up to the limit. Each read may take [timeoutMs], and the whole download
 * [totalTimeoutMs], so a server sending a byte at a time can't hold it open. The request is the same from every
 * phone: a fixed [USER_AGENT] in place of Android's default (which names the phone model and build), and no
 * cookies, account or identifier.
 */
class HttpsRatesClient internal constructor(
    private val timeoutMs: Int,
    private val totalTimeoutMs: Long,
    private val open: (URL) -> HttpURLConnection?,
    private val nanoTime: () -> Long
) : RatesHttpClient {

    constructor(timeoutMs: Int = 15_000, totalTimeoutMs: Long = 30_000L) :
        this(timeoutMs, totalTimeoutMs, { it.openConnection() as? HttpsURLConnection }, System::nanoTime)

    override fun get(url: String, maxBytes: Int): ByteArray? {
        val address = runCatching { URI(url).toURL() }.getOrNull() ?: return null
        if (!address.protocol.equals("https", ignoreCase = true)) return null
        val connection = runCatching { open(address) }.getOrNull() ?: return null
        val deadline = nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs)
        // A read blocked past the deadline ends when the connection is closed under it.
        val watchdog = runCatching { WATCHDOG.schedule(connection::disconnect, totalTimeoutMs, TimeUnit.MILLISECONDS) }.getOrNull()
        return try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/xml, text/xml")
            when {
                connection.responseCode != HttpURLConnection.HTTP_OK -> null
                connection.contentLengthLong > maxBytes -> null
                else -> connection.inputStream.use { readCapped(it, maxBytes) { nanoTime() - deadline > 0 } }
            }
        } catch (_: Exception) {
            null
        } finally {
            watchdog?.cancel(false)
            connection.disconnect()
        }
    }

    companion object {
        /** Sent instead of Android's default User-Agent; the same for every user and version. */
        const val USER_AGENT = "IntelligentSearch/1.0"

        /** Closes downloads that outlive their deadline. One daemon thread, ended while idle. */
        private val WATCHDOG = ScheduledThreadPoolExecutor(1) { task -> Thread(task, "RatesDeadline").apply { isDaemon = true } }.apply {
            removeOnCancelPolicy = true
            setKeepAliveTime(10, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
        }

        /** All of [input], or null as soon as it runs past [maxBytes] (the rest is never read). */
        fun readCapped(input: InputStream, maxBytes: Int): ByteArray? = readCapped(input, maxBytes) { false }

        /** As [readCapped], also giving up (null) once [pastDeadline] says the download has taken too long. */
        internal fun readCapped(input: InputStream, maxBytes: Int, pastDeadline: () -> Boolean): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                if (pastDeadline()) return null
                val read = input.read(buffer)
                if (read < 0) return out.toByteArray()
                if (out.size() + read > maxBytes) return null
                out.write(buffer, 0, read)
            }
        }
    }
}
