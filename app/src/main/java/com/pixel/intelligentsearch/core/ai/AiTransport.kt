package com.pixel.intelligentsearch.core.ai

import com.pixel.intelligentsearch.core.net.HttpsCall
import com.pixel.intelligentsearch.core.net.HttpsClient
import com.pixel.intelligentsearch.core.net.HttpsResponse
import java.io.IOException

/** Posts an [AiRequest] and hands each line of the streamed answer to a callback. Tests replace it. */
fun interface AiTransport {
    /**
     * Throws [IOException] when the provider can't be reached, [call] is cancelled, or the answer takes longer than
     * [AiRequest.timeoutMs] allows ([com.pixel.intelligentsearch.core.net.HttpsTimeoutException]).
     */
    @Throws(IOException::class)
    fun post(request: AiRequest, call: HttpsCall, onLine: (String) -> Unit): HttpsResponse
}

/**
 * [AiTransport] over the app's [HttpsClient]: https only, redirects never followed, nothing cached, connecting within
 * [CONNECT_TIMEOUT_MS] (30 seconds), and the whole exchange, like any silence in it, limited to the request's own
 * [AiRequest.timeoutMs], which grows with how long the model is asked to think, however slowly the answer streams.
 * No cookies, no identifiers; the User-Agent is the app's fixed one instead of Android's (which names the phone model).
 */
class HttpsAiTransport internal constructor(private val client: HttpsClient) : AiTransport {

    constructor() : this(HttpsClient(timeoutMs = CONNECT_TIMEOUT_MS.toInt(), totalTimeoutMs = CONNECT_TIMEOUT_MS))

    override fun post(request: AiRequest, call: HttpsCall, onLine: (String) -> Unit): HttpsResponse =
        client.postStreaming(
            request.url, request.headers, request.body, MAX_STREAM_CHARS, call,
            readTimeoutMs = request.timeoutMs.toInt(), totalTimeoutMs = request.timeoutMs, onLine = onLine
        )

    companion object {
        const val CONNECT_TIMEOUT_MS = 30_000L

        /** A full answer is a few kilobytes; a stream far past that isn't one. */
        private const val MAX_STREAM_CHARS = 512 * 1024
    }
}
