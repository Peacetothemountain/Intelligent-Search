package com.pixel.intelligentsearch.core.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** How a provider answered: its HTTP status, and for anything but 200 the start of its error body. */
data class AiHttpResponse(val code: Int, val errorBody: String = "")

/** Posts an [AiRequest] and hands each line of the streamed answer to a callback. Tests replace it. */
fun interface AiTransport {
    /** Throws [IOException] when the provider can't be reached or takes longer than allowed. */
    @Throws(IOException::class)
    fun post(request: AiRequest, onLine: (String) -> Unit): AiHttpResponse
}

/** Raised when the whole answer takes longer than [HttpsAiTransport.totalTimeoutMs]. */
class AiTimeoutException : IOException("The answer took too long")

/**
 * [AiTransport] over HttpsURLConnection: https only, redirects never followed, nothing cached, and the whole
 * exchange limited to [totalTimeoutMs] (30 seconds) however slowly the answer streams. No cookies, no identifiers; the
 * User-Agent is the app's fixed one instead of Android's (which names the phone model).
 */
class HttpsAiTransport internal constructor(
    private val totalTimeoutMs: Long,
    private val open: (URL) -> HttpURLConnection?
) : AiTransport {

    constructor(totalTimeoutMs: Long = TOTAL_TIMEOUT_MS) : this(totalTimeoutMs, { it.openConnection() as? HttpsURLConnection })

    override fun post(request: AiRequest, onLine: (String) -> Unit): AiHttpResponse {
        val address = runCatching { URI(request.url).toURL() }.getOrNull() ?: throw IOException("Not an address")
        if (!address.protocol.equals("https", ignoreCase = true)) throw IOException("Only https is allowed")
        val connection = open(address) ?: throw IOException("Couldn't connect")
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs)
        // A read blocked past the deadline ends when the connection is closed under it.
        val watchdog = WATCHDOG.schedule(connection::disconnect, totalTimeoutMs, TimeUnit.MILLISECONDS)
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = true
            connection.connectTimeout = totalTimeoutMs.toInt()
            connection.readTimeout = totalTimeoutMs.toInt()
            connection.setRequestProperty("User-Agent", com.pixel.intelligentsearch.core.currency.HttpsRatesClient.USER_AGENT)
            for ((name, value) in request.headers) connection.setRequestProperty(name, value)
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val error = runCatching {
                    connection.errorStream?.use { com.pixel.intelligentsearch.core.currency.HttpsRatesClient.readCapped(it, MAX_ERROR_BYTES) }
                }.getOrNull()
                return AiHttpResponse(code, error?.toString(Charsets.UTF_8).orEmpty())
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var answered = 0
                while (true) {
                    if (System.nanoTime() - deadline > 0) throw AiTimeoutException()
                    val line = reader.readLine() ?: break
                    answered += line.length
                    // An answer of 400 tokens is a few kilobytes; a stream far past that isn't one.
                    if (answered > MAX_STREAM_CHARS) break
                    onLine(line)
                }
            }
            return AiHttpResponse(code)
        } catch (e: SocketTimeoutException) {
            throw AiTimeoutException()
        } catch (e: IOException) {
            if (System.nanoTime() - deadline > 0) throw AiTimeoutException()
            throw e
        } finally {
            watchdog.cancel(false)
            connection.disconnect()
        }
    }

    companion object {
        const val TOTAL_TIMEOUT_MS = 30_000L
        private const val MAX_ERROR_BYTES = 8 * 1024
        private const val MAX_STREAM_CHARS = 512 * 1024

        /** Closes requests that outlive their deadline. One daemon thread, ended while idle. */
        private val WATCHDOG = ScheduledThreadPoolExecutor(1) { task -> Thread(task, "AiAnswerDeadline").apply { isDaemon = true } }.apply {
            removeOnCancelPolicy = true
            setKeepAliveTime(10, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
        }
    }
}
