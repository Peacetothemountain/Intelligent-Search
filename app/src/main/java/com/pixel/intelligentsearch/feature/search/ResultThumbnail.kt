package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import kotlin.math.cos
import kotlin.math.sin

/** Test tag on a result row's loaded picture (an entity, contact photo or media thumbnail in its matte ring). */
internal const val RESULT_THUMBNAIL_TAG = "result_thumbnail"

/** Where a result row's picture comes from; [key] identifies it in the cache. */
internal sealed interface ThumbnailSource {
    val key: String

    /** A web suggestion's entity image: an https address, or an inline data: image. */
    data class Web(val url: String) : ThumbnailSource {
        override val key get() = "web:$url"
    }

    /** The photo of the contact at [uri] (its lookup URI), read the way the Contacts app reads it. */
    data class ContactPhoto(val uri: String) : ThumbnailSource {
        override val key get() = "contact:$uri"
    }

    /** An image or video file from the user's chosen folders, picked photos or MediaStore. */
    data class MediaFile(val uri: String, val mimeType: String) : ThumbnailSource {
        override val key get() = "file:$uri"
    }
}

/**
 * Loads and caches result-row pictures, decoded at the size they are shown. A picture that fails to load (offline,
 * removed, unreadable) is not tried again for [RETRY_AFTER_MS], so a row going in and out of view doesn't refetch
 * it on every pass; the row shows its usual icon meanwhile.
 */
internal object ResultThumbnails {
    private val cache = com.pixel.intelligentsearch.core.ui.imageBitmapLruCache<String>(heapDivisor = 32)
    private val failedAt = android.util.LruCache<String, Long>(256)

    private const val RETRY_AFTER_MS = 60_000L
    private const val TIMEOUT_MS = 2_500
    /** Entity thumbnails are a few KB; anything far larger isn't a thumbnail. */
    private const val MAX_DOWNLOAD_BYTES = 1 shl 20

    /** Fetches an https image's bytes; tests stand in for the network. */
    @VisibleForTesting
    internal var download: (String) -> ByteArray? = ::httpsBytes

    private fun cacheKey(source: ThumbnailSource, px: Int) = "${source.key}@$px"

    fun peek(source: ThumbnailSource, px: Int): ImageBitmap? = cache.get(cacheKey(source, px))

    @VisibleForTesting
    internal fun put(source: ThumbnailSource, px: Int, image: ImageBitmap) {
        cache.put(cacheKey(source, px), image)
    }

    fun clear() {
        cache.evictAll()
        failedAt.evictAll()
    }

    /** The picture for [source] at [px] square, or null when there is none to show. */
    suspend fun load(context: Context, source: ThumbnailSource, px: Int): ImageBitmap? {
        val key = cacheKey(source, px)
        cache.get(key)?.let { return it }
        failedAt.get(key)?.let { if (android.os.SystemClock.elapsedRealtime() - it < RETRY_AFTER_MS) return null }
        val image = withContext(Dispatchers.IO) {
            try {
                when (source) {
                    is ThumbnailSource.Web -> webImage(source.url, px)
                    // The full-size display photo when there is one (the 96 px thumbnail is soft at 40 dp on a
                    // high-density screen), else the thumbnail; null for a contact without a photo.
                    is ThumbnailSource.ContactPhoto -> android.provider.ContactsContract.Contacts
                        .openContactPhotoInputStream(context.contentResolver, source.uri.toUri(), true)
                        ?.use { com.pixel.intelligentsearch.core.ui.decodeSampledBitmap(it.readBytes(), px) }
                    is ThumbnailSource.MediaFile -> mediaThumbnail(context, source, px)
                }?.asImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        if (image != null) cache.put(key, image) else failedAt.put(key, android.os.SystemClock.elapsedRealtime())
        return image
    }

    private fun webImage(url: String, px: Int): Bitmap? {
        val bytes = when {
            url.startsWith("data:image/") -> {
                val comma = url.indexOf(',')
                if (comma < 0 || !url.substring(0, comma).endsWith(";base64")) return null
                android.util.Base64.decode(url.substring(comma + 1), android.util.Base64.DEFAULT)
            }
            url.startsWith("https://") -> download(url)
            else -> null
        } ?: return null
        return com.pixel.intelligentsearch.core.ui.decodeSampledBitmap(bytes, px)
    }

    private fun httpsBytes(url: String): ByteArray? {
        val connection = java.net.URI.create(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_DOWNLOAD_BYTES) return null
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * A thumbnail for an image or video file. The provider's own thumbnail comes first; a video in a chosen folder
     * has none (the storage provider only thumbnails images), so a frame is taken from the video itself.
     */
    private fun mediaThumbnail(context: Context, source: ThumbnailSource.MediaFile, px: Int): Bitmap? {
        val parsed = source.uri.toUri()
        val isVideo = source.mimeType.startsWith("video/")
        // Only MediaStore file ids map onto the Images/Video tables. Folder documents and photo-picker items serve
        // thumbnails for their own URI (their trailing id would otherwise point at an unrelated MediaStore row).
        val thumbnailUri = if (!isMediaStoreUri(parsed)) {
            parsed
        } else {
            val id = android.content.ContentUris.parseId(parsed)
            val table = if (isVideo) android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            android.content.ContentUris.withAppendedId(table, id)
        }
        val fromProvider = try {
            context.contentResolver.loadThumbnail(thumbnailUri, android.util.Size(px, px), null)
        } catch (e: Exception) {
            if (!isVideo) throw e
            null
        }
        if (fromProvider != null || !isVideo) return fromProvider
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, parsed)
            retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px, px)
        } finally {
            retriever.release()
        }
    }
}

/**
 * A result row's picture: [source] loaded into a circle with a thin matte ring, or [fallback] (the row's usual icon)
 * while it loads, when there is none, and when it can't be loaded. Pictures already cached show at once; others
 * fade in as they arrive.
 */
@Composable
internal fun ResultThumbnail(
    source: ThumbnailSource?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    if (source == null) {
        fallback()
        return
    }
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    var image by remember(source.key, px) { mutableStateOf(ResultThumbnails.peek(source, px)) }
    val appear = remember(source.key, px) { Animatable(if (image != null) 1f else 0f) }
    val animate by com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled()
    LaunchedEffect(source.key, px) {
        if (image == null) image = ResultThumbnails.load(context, source, px)
    }
    LaunchedEffect(image != null) {
        if (image != null) {
            if (animate) appear.animateTo(1f, tween(durationMillis = 180)) else appear.snapTo(1f)
        }
    }
    val loaded = image
    if (loaded == null) {
        fallback()
    } else {
        Image(
            bitmap = loaded,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .testTag(RESULT_THUMBNAIL_TAG)
                .graphicsLayer { alpha = appear.value }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .clip(CircleShape)
                .matteRing()
        )
    }
}

/** A contact's photo in the matte circle, or [fallback] (the person icon) when the contact has none. */
@Composable
internal fun ContactAvatar(contact: com.pixel.intelligentsearch.core.data.ContactItem, size: Dp, fallback: @Composable () -> Unit) {
    ResultThumbnail(contactPhotoSource(contact), size, fallback = fallback)
}

/** Where a contact's photo is read from: its lookup URI (the contacts provider serves `<lookup uri>/photo`). */
internal fun contactPhotoSource(contact: com.pixel.intelligentsearch.core.data.ContactItem): ThumbnailSource? =
    contact.lookupUri.takeIf { it.startsWith("content://") }?.let { ThumbnailSource.ContactPhoto(it) }

/**
 * The thin matte ring around a result picture: a surface-coloured band tinted toward the theme's primary (as
 * Material You tints surfaces), a soft rim lit from above inside it, and a fine fixed grain so the band reads as a
 * matte finish rather than a flat line. Drawn over the clipped picture, inside its bounds.
 */
@Composable
internal fun Modifier.matteRing(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val band = lerp(scheme.surfaceContainerHighest, scheme.primary, if (dark) 0.16f else 0.10f)
    val highlight = Color.White.copy(alpha = if (dark) 0.14f else 0.45f)
    val shade = Color.Black.copy(alpha = if (dark) 0.30f else 0.12f)
    val grainLight = Color.White.copy(alpha = if (dark) 0.10f else 0.22f)
    val grainDark = Color.Black.copy(alpha = if (dark) 0.18f else 0.08f)
    return drawWithCache {
        val bandWidth = 1.75.dp.toPx()
        val rimWidth = 0.75.dp.toPx()
        val radius = size.minDimension / 2f
        val bandRadius = radius - bandWidth / 2f
        val rimRadius = radius - bandWidth - rimWidth / 2f
        val rimBrush = Brush.verticalGradient(listOf(highlight, Color.Transparent, shade))
        // A fixed scatter of specks along the band: the same on every frame and every screenshot.
        val specks = ringGrain(size.width / 2f, size.height / 2f, bandRadius, bandWidth * 0.8f, count = 72)
        val grainStroke = (0.6.dp.toPx()).coerceAtLeast(1f)
        onDrawWithContent {
            drawContent()
            drawCircle(band, radius = bandRadius, style = Stroke(bandWidth))
            drawPoints(specks.first, PointMode.Points, grainLight, strokeWidth = grainStroke, cap = StrokeCap.Round)
            drawPoints(specks.second, PointMode.Points, grainDark, strokeWidth = grainStroke, cap = StrokeCap.Round)
            drawCircle(rimBrush, radius = rimRadius, style = Stroke(rimWidth))
        }
    }
}

/** [count] specks spread around a ring, split into light and dark ones, from a fixed seed. */
private fun ringGrain(cx: Float, cy: Float, radius: Float, spread: Float, count: Int): Pair<List<Offset>, List<Offset>> {
    val light = ArrayList<Offset>(count / 2)
    val darkSpecks = ArrayList<Offset>(count / 2)
    var seed = 0x2545F491
    fun next(): Float {
        seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
        return (seed ushr 8) / 16_777_216f
    }
    for (i in 0 until count) {
        val angle = (i + next()) / count * (2.0 * Math.PI)
        val r = radius + (next() - 0.5f) * spread
        val point = Offset(cx + (r * cos(angle)).toFloat(), cy + (r * sin(angle)).toFloat())
        if (next() < 0.5f) light += point else darkSpecks += point
    }
    return light to darkSpecks
}
