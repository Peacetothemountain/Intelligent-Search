package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.system.GoogleApps
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.pixel.intelligentsearch.core.util.resolveActivityCompat
import java.text.DateFormat
import java.util.Date

/**
 * Search with Lens for an image on the clipboard: one of the empty overlay's Smart Clipboard Suggestions.
 *
 * Offering it reads only the clip's description (its MIME types and when it was copied), which Android shows no
 * "pasted from your clipboard" message for; the image's address is read only when the suggestion is tapped, while
 * the overlay has focus, and only that address is handed on: the image itself never passes through this app. Lens
 * gets it as a share (ACTION_SEND) with read access granted for the one address: the Lens app if installed, otherwise
 * the Google app's Lens share target, otherwise anything the Google app shares images to.
 */
internal object ClipboardLens {
    /** The [DirectAction.iconType] of the suggestion. */
    const val ICON_TYPE = "lens"
    const val TITLE = "Search with Lens"

    /** The type a probe intent asks about when the image's own type isn't known yet. */
    private const val ANY_IMAGE = "image/*"
    private const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /** Whether the clip [description] describes was marked sensitive (password managers do): never shown or searched. */
    fun isSensitive(description: ClipDescription?): Boolean = description?.extras?.getBoolean(IS_SENSITIVE, false) == true

    /** Whether the clip [description] describes is an image that may be offered (not marked sensitive). */
    fun isImage(description: ClipDescription?): Boolean =
        description != null && description.hasMimeType(ANY_IMAGE) && !isSensitive(description)

    /** The clip's first exact image type, e.g. "image/png", or [ANY_IMAGE] when it names none. */
    fun imageType(description: ClipDescription): String =
        (0 until description.mimeTypeCount).map(description::getMimeType).firstOrNull { it.startsWith("image/") && it != ANY_IMAGE }
            ?: ANY_IMAGE

    /** The shares that can send an image of [mimeType] at [uri] (null: a probe, no image) to Lens, best first. */
    fun candidates(uri: Uri?, mimeType: String): List<Intent> {
        fun send(configure: Intent.() -> Unit) = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                // ClipData too, so the read grant below reaches the address (EXTRA_STREAM alone isn't granted).
                clipData = ClipData.newRawUri(TITLE, uri)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            configure()
        }
        return listOf(
            send { setPackage(GoogleApps.LENS_APP) },
            send { setClassName(GoogleApps.GOOGLE_APP, GoogleApps.LENS_SHARE_ACTIVITY) },
            send { setPackage(GoogleApps.GOOGLE_APP) }
        )
    }

    /** The first of [candidates] that [resolves], or null when neither Lens nor the Google app can take it. */
    fun intentFor(uri: Uri?, mimeType: String, resolves: (Intent) -> Boolean): Intent? =
        candidates(uri, mimeType).firstOrNull(resolves)

    /** Whether Lens (or the Google app) is here to take an image at all. */
    fun available(context: Context): Boolean =
        intentFor(null, ANY_IMAGE) { context.packageManager.resolveActivityCompat(it) != null } != null

    /**
     * The suggestion for the image [description] describes, or null when it isn't an image or nothing can search it.
     * Its subtitle says when the image was copied, which also keeps a dismissed image's suggestion apart from the next.
     */
    fun suggestionFor(context: Context, description: ClipDescription?): DirectAction? {
        if (description == null || !isImage(description) || !available(context)) return null
        val copied = description.timestamp.takeIf { it > 0L }
            ?.let { "Image Copied at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}" }
            ?: "Copied Image"
        return DirectAction(TITLE, copied, ICON_TYPE, null)
    }

    /**
     * Sends the clipboard's image to Lens. Called from a tap in the overlay, so the overlay has focus and Android hands
     * over the clip. Returns whether Lens opened; says so when it couldn't.
     */
    fun searchClipboardImage(context: Context): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val description = clipboard?.primaryClipDescription
        val clip = if (isImage(description)) clipboard?.primaryClip else null
        val uri = clip?.let { c -> (0 until c.itemCount).firstNotNullOfOrNull { c.getItemAt(it).uri } }
        val intent = if (uri != null && description != null) {
            intentFor(uri, imageType(description)) { context.packageManager.resolveActivityCompat(it) != null }
        } else null
        val opened = intent != null && launchSafeIntent(context, intent)
        if (!opened) Toast.makeText(context, "Couldn't open Lens with the copied image.", Toast.LENGTH_SHORT).show()
        return opened
    }
}
