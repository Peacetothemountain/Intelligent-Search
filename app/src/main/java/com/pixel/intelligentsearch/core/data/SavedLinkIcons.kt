package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.currency.HttpsRatesClient
import com.pixel.intelligentsearch.core.currency.RatesHttpClient
import com.pixel.intelligentsearch.core.security.SafeWebLaunch
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved Links' icons. A link shows a letter monogram until the user taps Fetch Icon, the only time anything is
 * downloaded: the site's own /favicon.ico (or /apple-touch-icon.png), over HTTPS, nothing sent but the request itself,
 * and never while Tor is the search engine. The icon is kept as a small PNG in the app's own files (not in settings or
 * backups, so a restore shows monograms until the icons are fetched again).
 */
@Singleton
class SavedLinkIcons internal constructor(
    private val directory: File,
    private val client: RatesHttpClient,
    private val settingsManager: SettingsManager
) {
    @Inject constructor(@ApplicationContext context: Context, settingsManager: SettingsManager) :
        this(File(context.filesDir, "saved_link_icons"), HttpsRatesClient(timeoutMs = 10_000, totalTimeoutMs = 15_000L, accept = "image/*"), settingsManager)

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return File(directory, digest.take(16).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) } + ".png")
    }

    /** The saved icon for [url], or null (the row draws a monogram). Disk: call off the main thread. */
    fun load(url: String): Bitmap? = fileFor(url).takeIf { it.isFile }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    /**
     * Fetch Icon: downloads [url]'s site icon and saves it, scaled to [ICON_SIZE]. Network and disk: call off the
     * main thread. False when Tor is the engine, the site has no icon, or it isn't an image.
     */
    fun fetch(url: String): Boolean {
        if (SafeWebLaunch.isTorEngine(settingsManager[SettingsManager.SEARCH_ENGINE])) return false
        val site = url.toUri()
        if (site.scheme != "https" || site.host.isNullOrBlank()) return false
        val origin = "https://${site.encodedAuthority}"
        for (path in ICON_PATHS) {
            val bytes = client.get(origin + path, MAX_ICON_BYTES) ?: continue
            val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: continue
            val scaled = bitmap.scale(ICON_SIZE, ICON_SIZE)
            return runCatching {
                directory.mkdirs()
                fileFor(url).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.getOrDefault(false)
        }
        return false
    }

    /** Forgets [url]'s icon, when its link is deleted. */
    fun delete(url: String) {
        runCatching { fileFor(url).delete() }
    }

    companion object {
        const val ICON_SIZE = 96
        private const val MAX_ICON_BYTES = 256 * 1024
        private val ICON_PATHS = listOf("/favicon.ico", "/apple-touch-icon.png")
    }
}
