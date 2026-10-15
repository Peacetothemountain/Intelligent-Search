package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.clearThemedIconCache
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.*

/** Decoded file thumbnails for the results list. */
private val fileThumbnailCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(128)

/** Frees the in-memory icon and thumbnail caches (called when the system asks for memory). */
fun clearAllUiMemoryCaches() {
    clearThemedIconCache()
    fileThumbnailCache.evictAll()
}

@Composable
fun FileIconThumbnail(uri: String, mimeType: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by androidx.compose.runtime.remember(uri) { 
        androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(fileThumbnailCache.get(uri)) 
    }

    androidx.compose.runtime.LaunchedEffect(uri) {
        if (bitmap == null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val parsedUri = android.net.Uri.parse(uri)
                    // Only MediaStore file ids map onto the Images/Video tables. Folder documents and
                    // photo-picker items serve thumbnails for their own URI (their trailing id would
                    // otherwise point at an unrelated MediaStore row).
                    val specificUri = if (!isMediaStoreUri(parsedUri)) {
                        parsedUri
                    } else {
                        val id = android.content.ContentUris.parseId(parsedUri)
                        if (mimeType.startsWith("image/")) {
                            android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        } else {
                            android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        }
                    }
                    val img = context.contentResolver.loadThumbnail(specificUri, android.util.Size(96, 96), null).asImageBitmap()
                    fileThumbnailCache.put(uri, img)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        bitmap = img
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Ignore, fallback to standard icon
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
