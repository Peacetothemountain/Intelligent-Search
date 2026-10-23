package com.pixel.intelligentsearch.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import com.pixel.intelligentsearch.core.data.FileTypeGroup

/** The outlined icon for a file type, in File Search settings and on the results' filter chip. */
fun fileTypeIcon(group: FileTypeGroup): ImageVector = when (group) {
    FileTypeGroup.PDF -> Icons.Outlined.PictureAsPdf
    FileTypeGroup.IMAGES -> Icons.Outlined.Image
    FileTypeGroup.VIDEO -> Icons.Outlined.VideoFile
    FileTypeGroup.AUDIO -> Icons.Outlined.AudioFile
    FileTypeGroup.DOCUMENTS -> Icons.Outlined.Description
    FileTypeGroup.SPREADSHEETS -> Icons.Outlined.TableChart
    FileTypeGroup.PRESENTATIONS -> Icons.Outlined.Slideshow
    FileTypeGroup.ARCHIVES -> Icons.Outlined.FolderZip
    FileTypeGroup.APKS -> Icons.Outlined.Android
    FileTypeGroup.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}
