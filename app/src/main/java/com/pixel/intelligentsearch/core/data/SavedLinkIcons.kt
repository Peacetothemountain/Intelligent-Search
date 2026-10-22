package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.net.HttpsClient
import com.pixel.intelligentsearch.core.net.HttpsGet
import com.pixel.intelligentsearch.core.net.fetch
import com.pixel.intelligentsearch.core.security.NetworkPolicy
import com.pixel.intelligentsearch.core.ui.decodeSampledBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved Links' icons. A link shows a letter monogram until the user taps Fetch Icon, the only time anything is
 * downloaded: the site's own /favicon.ico (or /apple-touch-icon.png), over HTTPS, nothing sent but the request itself,
 * and never while Tor is the search engine. A site that moved its icon within the same host is followed once. The icon
 * is kept as a small PNG in the app's own files (not in settings or backups, so a restore shows monograms until the
 * icons are fetched again), with the ones shown recently in memory so the overlay draws them at once.
 */
@Singleton
class SavedLinkIcons internal constructor(
    private val directory: File,
    private val client: HttpsGet,
    private val settingsManager: SettingsManager
) {
    @Inject constructor(@ApplicationContext context: Context, settingsManager: SettingsManager) : this(
        File(context.filesDir, "saved_link_icons"),
        HttpsClient(timeoutMs = 10_000, totalTimeoutMs = 15_000L, accept = HttpsClient.ACCEPT_IMAGE, sameHostRedirects = 1),
        settingsManager
    )

    private val memory = LruCache<String, Bitmap>(MEMORY_ENTRIES)
    private val _changes = MutableStateFlow(0)

    /** Bumps whenever an icon is fetched or deleted, so whatever shows icons reads them again. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return File(directory, digest.take(16).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) } + ".png")
    }

    /** [url]'s icon if it is in memory; never touches the disk, so it is safe on the main thread. */
    fun peek(url: String): Bitmap? = memory.get(url)

    /** The saved icon for [url], or null (the row draws a monogram). Reads the disk on the IO dispatcher. */
    suspend fun load(url: String): Bitmap? = peek(url) ?: withContext(Dispatchers.IO) {
        fileFor(url).takeIf { it.isFile }
            ?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
            ?.also { memory.put(url, it) }
    }

    /**
     * Fetch Icon: downloads [url]'s site icon and saves it, scaled to [ICON_SIZE]. False when Tor is the engine, the
     * site has no icon, or it isn't an image. Cancelling the caller ends the download.
     */
    suspend fun fetch(url: String): Boolean {
        if (!NetworkPolicy.userFetchAllowed(settingsManager.prefs)) return false
        val site = url.toUri()
        if (site.scheme != "https" || site.host.isNullOrBlank()) return false
        val origin = "https://${site.encodedAuthority}"
        for (path in ICON_PATHS) {
            val bytes = client.fetch(origin + path, MAX_ICON_BYTES) ?: continue
            // Decoded no larger than needed: an icon file may declare a huge picture.
            val bitmap = decodeSampledBitmap(bytes, ICON_SIZE) ?: continue
            val scaled = bitmap.scale(ICON_SIZE, ICON_SIZE)
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    directory.mkdirs()
                    fileFor(url).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }.getOrDefault(false)
            }
            if (saved) {
                memory.put(url, scaled)
                _changes.value++
            }
            return saved
        }
        return false
    }

    /** Forgets [url]'s icon, when its link is deleted or its address changed. */
    suspend fun delete(url: String) {
        memory.remove(url)
        withContext(Dispatchers.IO) { runCatching { fileFor(url).delete() } }
        _changes.value++
    }

    companion object {
        const val ICON_SIZE = 96
        private const val MAX_ICON_BYTES = 256 * 1024
        private const val MEMORY_ENTRIES = 64
        private val ICON_PATHS = listOf("/favicon.ico", "/apple-touch-icon.png")
    }
}
