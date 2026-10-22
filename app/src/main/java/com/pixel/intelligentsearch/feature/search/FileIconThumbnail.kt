package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.clearThemedIconCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Frees the in-memory icon and thumbnail caches (called when the system asks for memory). */
fun clearAllUiMemoryCaches() {
    clearThemedIconCache()
    ResultThumbnails.clear()
}

/** What a file row shows a picture of: images and videos; other files keep their file icon. */
internal fun fileThumbnailSource(uri: String, mimeType: String): ThumbnailSource? =
    if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) ThumbnailSource.MediaFile(uri, mimeType) else null

/** A file result's picture: the image or a video frame in a matte circle, else the file icon. */
@Composable
fun FileIconThumbnail(uri: String, mimeType: String, size: Dp = 40.dp) {
    ResultThumbnail(fileThumbnailSource(uri, mimeType), size) {
        Box(modifier = Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
