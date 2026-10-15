package com.pixel.intelligentsearch.core.system

import android.content.BroadcastReceiver

/**
 * Redraws every home-screen widget. Core code (backup restore, boot, unlock) depends on this interface;
 * the widget feature provides the implementation, so `core` never reaches into `feature`.
 */
fun interface WidgetUpdater {
    /** [pendingResult] is finished once the redraw completes, for callers inside a goAsync() receiver. */
    fun updateAll(pendingResult: BroadcastReceiver.PendingResult?)

    fun updateAll() = updateAll(null)
}
