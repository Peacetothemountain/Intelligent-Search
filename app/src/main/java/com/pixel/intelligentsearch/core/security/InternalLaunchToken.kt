package com.pixel.intelligentsearch.core.security

import android.content.Intent
import java.util.UUID

/**
 * Marks intents this app sends to its own exported activities. The token is random per process and never
 * leaves it, so an extra carrying it can only come from this app; other apps' copies of the same extras are
 * ignored. (After the process restarts, an old intent no longer matches and falls back to the default page.)
 */
object InternalLaunchToken {
    private const val EXTRA = "com.pixel.intelligentsearch.extra.INTERNAL_TOKEN"
    private val token: String = UUID.randomUUID().toString()

    fun stamp(intent: Intent): Intent = intent.putExtra(EXTRA, token)

    fun isInternal(intent: Intent?): Boolean = intent?.getStringExtra(EXTRA) == token
}
