package com.pixel.intelligentsearch.core.backup

/**
 * Discover's part of the app's own backup. Core's [BackupManager] depends on this interface; the Discover feature
 * provides the implementation, so `core` never reaches into `feature`.
 */
interface DiscoverBackupPart {
    /** Discover's choices as one JSON object for the backup file; empty when the user never changed any. Off the main thread. */
    fun export(): String

    /** Restores what [export] wrote; returns how many parts were restored (0 for an empty or unreadable [json]). */
    fun restore(json: String): Int

    /** Outside Hilt (tests): no Discover to back up. */
    object None : DiscoverBackupPart {
        override fun export(): String = ""
        override fun restore(json: String): Int = 0
    }
}
