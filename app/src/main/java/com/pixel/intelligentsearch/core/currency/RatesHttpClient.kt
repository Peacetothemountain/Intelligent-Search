package com.pixel.intelligentsearch.core.currency

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/** Fetches a small document over HTTPS. Tests replace it, so they never touch the network. */
fun interface RatesHttpClient {
    /** The body of [url] when it answers 200 OK with at most [maxBytes] bytes; null on anything else. */
    fun get(url: String, maxBytes: Int): ByteArray?
}

/**
 * [RatesHttpClient] over HttpsURLConnection: https addresses only, redirects never followed (a 3xx is a failure),
 * nothing cached, and the body read only up to the limit. No cookies, account or identifier are sent.
 */
class HttpsRatesClient(private val timeoutMs: Int = 15_000) : RatesHttpClient {

    override fun get(url: String, maxBytes: Int): ByteArray? {
        val address = runCatching { URI(url).toURL() }.getOrNull() ?: return null
        if (!address.protocol.equals("https", ignoreCase = true)) return null
        val connection = runCatching { address.openConnection() as? HttpsURLConnection }.getOrNull() ?: return null
        return try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Accept", "application/xml, text/xml")
            when {
                connection.responseCode != HttpURLConnection.HTTP_OK -> null
                connection.contentLengthLong > maxBytes -> null
                else -> connection.inputStream.use { readCapped(it, maxBytes) }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** All of [input], or null as soon as it runs past [maxBytes] (the rest is never read). */
        fun readCapped(input: InputStream, maxBytes: Int): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return out.toByteArray()
                if (out.size() + read > maxBytes) return null
                out.write(buffer, 0, read)
            }
        }
    }
}
