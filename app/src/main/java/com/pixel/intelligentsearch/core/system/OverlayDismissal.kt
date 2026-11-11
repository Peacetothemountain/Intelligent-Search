package com.pixel.intelligentsearch.core.system

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Settings asking every open search overlay to close: when the overlay is turned off, and when Settings leaves for the
 * home screen with the overlay still behind it. Both run in the app's one process, so this is a call within it, not a
 * broadcast: Android asks apps not to broadcast to themselves, which costs a trip through the system and, from Android
 * 17 QPR2, arrives on the main thread anyway.
 *
 * Each overlay listens for its whole life (it can be stopped, behind Settings, when asked). With none open, a request
 * is simply dropped.
 *
 * NG Designs
 */
object OverlayDismissal {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once for each request; an overlay that sees one closes. */
    val asked: SharedFlow<Unit> = requests.asSharedFlow()

    /** Asks every open overlay to close. */
    fun request() {
        requests.tryEmit(Unit)
    }
}
